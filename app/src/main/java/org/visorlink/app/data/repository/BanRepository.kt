package org.visorlink.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Статус апелляции в `bans/{uid}.appealStatus`. */
enum class AppealStatus { NONE, PENDING, REJECTED }

/** Запись о блокировке `bans/{uid}` (читает только сам заблокированный). */
data class BanInfo(
    val reason: String? = null,
    val appealStatus: AppealStatus = AppealStatus.NONE,
    val appealText: String? = null,
    val resolutionNote: String? = null,
)

/** Причины отказа `submitBanAppeal` (`details.reason`). */
enum class AppealError { EMPTY, TOO_LONG, ALREADY_SENT, NOT_BANNED, OTHER }

class AppealException(val error: AppealError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * Блокировка аккаунта сервисом и единственная апелляция (веб: `BannedScreen.jsx`).
 * Сервер считает длину в символах (code points), не в UTF-16 — [appealLength].
 */
class BanRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) {
    /**
     * Подписка на `bans/{uid}`. `PERMISSION_DENIED` и любая ошибка — как «записи нет»: форму всё
     * равно покажем, а сервер проверит. Слушатель при ошибке завершается, повторов нет.
     */
    fun banFlow(uid: String): Flow<BanInfo?> = callbackFlow {
        val reg = db.collection("bans").document(uid).addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            val appeal = snap.get("appeal") as? Map<*, *>
            trySend(
                BanInfo(
                    reason = snap.getString("reason"),
                    appealStatus = when (snap.getString("appealStatus")) {
                        "pending" -> AppealStatus.PENDING
                        "rejected" -> AppealStatus.REJECTED
                        else -> AppealStatus.NONE
                    },
                    appealText = appeal?.get("text") as? String,
                    resolutionNote = snap.getString("resolutionNote"),
                )
            )
        }
        awaitClose { reg.remove() }
    }

    suspend fun submitAppeal(text: String) {
        try {
            functions.getHttpsCallable("submitBanAppeal").call(mapOf("text" to text)).await()
        } catch (e: Exception) {
            throw AppealException(mapAppealError(e), e)
        }
    }

    companion object {
        const val APPEAL_MAX = 300

        /** Длина в символах (code points): эмодзи — один символ, как считает сервер. */
        fun appealLength(text: String): Int = text.codePointCount(0, text.length)

        /** Обрезка до [APPEAL_MAX] символов без разрыва суррогатной пары. */
        fun clampAppeal(text: String): String {
            if (appealLength(text) <= APPEAL_MAX) return text
            return text.substring(0, text.offsetByCodePoints(0, APPEAL_MAX))
        }

        internal fun mapAppealError(e: Throwable): AppealError {
            val fe = e as? FirebaseFunctionsException ?: return AppealError.OTHER
            val reason = (fe.details as? Map<*, *>)?.get("reason")?.toString()
            return when {
                fe.code == FirebaseFunctionsException.Code.INVALID_ARGUMENT && reason == "empty" -> AppealError.EMPTY
                fe.code == FirebaseFunctionsException.Code.INVALID_ARGUMENT && reason == "too-long" -> AppealError.TOO_LONG
                fe.code == FirebaseFunctionsException.Code.ALREADY_EXISTS && reason == "already-sent" -> AppealError.ALREADY_SENT
                fe.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION && reason == "not-banned" -> AppealError.NOT_BANNED
                else -> AppealError.OTHER
            }
        }

        /** `permission-denied` с `reason == "banned"` — это блокировка, а не «нет сети». */
        fun isBannedError(e: Throwable): Boolean {
            val fe = e as? FirebaseFunctionsException ?: return false
            return fe.code == FirebaseFunctionsException.Code.PERMISSION_DENIED &&
                (fe.details as? Map<*, *>)?.get("reason")?.toString() == "banned"
        }
    }
}
