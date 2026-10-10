package org.visorlink.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.visorlink.app.BuildConfig

data class SessionInfo(
    val id: String,
    val userAgent: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    /** Сессия прошла 2FA (есть tfaVerifiedAt). Иначе — «ожидает 2FA». */
    val tfaVerified: Boolean,
    val isCurrent: Boolean,
    /** `android` | `ios` | `web` — сервер ставит по App Check; у старых сессий нет. */
    val client: String? = null,
    /** App Check подтвердил официальное приложение. */
    val appVerified: Boolean = false,
    /** Только у Android: «Google Pixel 8», «14», «4.3.00». */
    val deviceModel: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
)

/**
 * Менеджер сессий. Id сессии — `auth_time` из ID-токена. Пишет только сервер
 * (callable'ы registerSession / terminateSession / terminateOtherSessions), клиент читает.
 *
 * Платформу сервер определяет по токену App Check, а не по словам клиента: callable из SDK
 * прикладывает его сам (провайдер ставится в VisorLinkApp). Только тогда сервер принимает
 * сведения об устройстве и пишет сессию как Android, иначе она считается веб-сессией.
 */
class SessionRepository(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val db: FirebaseFirestore,
) {
    private val _revoked = MutableStateFlow(false)
    /** true, когда текущую сессию завершили с другого устройства: подписчик показывает причину и выходит. */
    val revoked: StateFlow<Boolean> = _revoked.asStateFlow()

    fun consumeRevoked() { _revoked.value = false }

    suspend fun currentSessionId(): String? {
        val user = auth.currentUser ?: return null
        return user.getIdToken(false).await().claims["auth_time"]?.toString()
    }

    /** Регистрирует вход (при каждом запуске). Возвращает id сессии; при отзыве поднимает [revoked]. */
    suspend fun register(): String? = try {
        val res = functions.getHttpsCallable("registerSession").call(deviceInfo()).await()
        (res.data as? Map<*, *>)?.get("sessionId")?.toString()
            .also { registeredId = it ?: currentSessionId() }
    } catch (e: Exception) {
        if (!handleError(e) && BuildConfig.DEBUG) Log.w(TAG, "registerSession failed: ${e.message}")
        null
    }

    /** id сессии, которую уже зарегистрировали в этом процессе. */
    @Volatile private var registeredId: String? = null
    private val registerLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Карточка текущей сессии существует на сервере. Сообщения 2FA и «новый вход» описывают
     * устройство по ней (модель, версии, App Check), поэтому её создают сразу после входа и
     * **до** `request2FA` — иначе в сообщении будет просто «VisorLink для Android».
     * Повторно не регистрирует: тот же auth_time уже записан.
     */
    suspend fun ensureRegistered() {
        registerLock.withLock {
            val id = currentSessionId() ?: return
            if (registeredId == id) return
            register()
        }
    }

    /** Завершает свою сессию (при выходе); вызывающий ограничивает время ожиданием. */
    suspend fun terminateCurrent() {
        val id = currentSessionId() ?: return
        functions.getHttpsCallable("terminateSession").call(mapOf("sessionId" to id)).await()
    }

    suspend fun terminate(sessionId: String) {
        try {
            functions.getHttpsCallable("terminateSession").call(mapOf("sessionId" to sessionId)).await()
        } catch (e: Exception) { handleError(e); throw e }
    }

    suspend fun terminateOthers(): Int = try {
        val res = functions.getHttpsCallable("terminateOtherSessions").call().await()
        ((res.data as? Map<*, *>)?.get("terminated") as? Number)?.toInt() ?: 0
    } catch (e: Exception) { handleError(e); throw e }

    /** Живой список устройств, новые сверху. Читать можно только из сессии с пройденной 2FA. */
    fun sessions(uid: String): Flow<List<SessionInfo>> = callbackFlow {
        val current = currentSessionId()
        val reg = db.collection("users").document(uid).collection("sessions")
            .orderBy("lastSeenAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { if (BuildConfig.DEBUG) Log.w(TAG, "sessions listen: ${err.message}"); return@addSnapshotListener }
                trySend(snap?.documents?.map { d ->
                    SessionInfo(
                        id = d.id,
                        userAgent = d.getString("userAgent").orEmpty(),
                        createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                        lastSeenAt = d.getTimestamp("lastSeenAt")?.toDate()?.time ?: 0L,
                        tfaVerified = d.get("tfaVerifiedAt") != null,
                        isCurrent = d.id == current,
                        client = d.getString("client"),
                        appVerified = d.getBoolean("appVerified") == true,
                        deviceModel = d.getString("deviceModel"),
                        osVersion = d.getString("osVersion"),
                        appVersion = d.getString("appVersion"),
                    )
                }.orEmpty())
            }
        awaitClose { reg.remove() }
    }

    private var revocationReg: com.google.firebase.firestore.ListenerRegistration? = null
    private var revocationUid: String? = null

    /** Живая подписка на users/{uid}: отзыв текущего id (revokedSessions / sessionsValidAfter). */
    fun watchRevocation(uid: String) {
        if (revocationUid == uid) return
        stopWatching()
        revocationUid = uid
        revocationReg = db.collection("users").document(uid).addSnapshotListener { snap, _ ->
            val data = snap?.data ?: return@addSnapshotListener
            val revokedList = (data["revokedSessions"] as? List<*>)?.map { it.toString() }.orEmpty()
            val validAfter = (data["sessionsValidAfter"] as? Number)?.toLong() ?: 0L
            val user = auth.currentUser ?: return@addSnapshotListener
            user.getIdToken(false).addOnSuccessListener { token ->
                val id = token.claims["auth_time"]?.toString() ?: return@addOnSuccessListener
                val idNum = id.toLongOrNull() ?: Long.MAX_VALUE
                if (id in revokedList || idNum < validAfter) _revoked.value = true
            }
        }
    }

    fun stopWatching() {
        revocationReg?.remove()
        revocationReg = null
        revocationUid = null
    }

    /** Разбирает отказ callable; true — это отзыв сессии (поднят [revoked]). */
    fun handleError(e: Throwable): Boolean {
        val fe = e as? FirebaseFunctionsException ?: return false
        if (fe.code != FirebaseFunctionsException.Code.PERMISSION_DENIED) return false
        val reason = (fe.details as? Map<*, *>)?.get("reason")?.toString()
        if (reason == "session-revoked") { _revoked.value = true; return true }
        return false
    }

    companion object {
        private const val TAG = "SessionRepo"
        const val REASON_TFA_REQUIRED = "tfa-required"

        /**
         * Сведения об устройстве для `registerSession` (сервер обрезает их до 60 / 20 / 20 символов
         * и принимает только при подтверждённом App Check).
         */
        fun deviceInfo(): Map<String, String> = mapOf(
            "deviceModel" to deviceModel(android.os.Build.MANUFACTURER, android.os.Build.MODEL),
            "osVersion" to android.os.Build.VERSION.RELEASE.orEmpty().take(20),
            "appVersion" to BuildConfig.VERSION_NAME.take(20),
        )

        /** «Google Pixel 8»; производитель не повторяется, если модель уже с него начинается. */
        internal fun deviceModel(manufacturer: String?, model: String?): String {
            val maker = manufacturer.orEmpty().trim().replaceFirstChar { it.titlecase() }
            val name = model.orEmpty().trim()
            val full = when {
                maker.isEmpty() -> name
                name.isEmpty() -> maker
                name.startsWith(maker, ignoreCase = true) -> name
                else -> "$maker $name"
            }
            return full.take(60)
        }

        fun isTfaRequired(e: Throwable): Boolean {
            val fe = e as? FirebaseFunctionsException ?: return false
            return fe.code == FirebaseFunctionsException.Code.PERMISSION_DENIED &&
                (fe.details as? Map<*, *>)?.get("reason")?.toString() == REASON_TFA_REQUIRED
        }
    }
}
