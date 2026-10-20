package org.visorlink.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import org.visorlink.app.BuildConfig
import org.visorlink.app.data.model.Surprise
import org.visorlink.app.data.model.SurpriseGiftResult

/**
 * Личный сюрприз `surprises/{uid}` (документ кладёт админ-скрипт). «Уже показан» хранится
 * локально по `id` сюрприза и на сервере (`finishedAt`), чтобы не повторяться на втором устройстве.
 */
class SurpriseRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _demo = MutableStateFlow(false)
    /**
     * Демо из настроек разработчика: показ целиком на встроенных данных, без Firestore —
     * ничего не выдаётся и не отмечается.
     */
    val demo: StateFlow<Boolean> = _demo.asStateFlow()

    fun startDemo() { _demo.value = true }
    fun stopDemo() { _demo.value = false }

    /**
     * Вскрыть подарок: сервер выдаёт его один раз, повторный вызов возвращает то же самое.
     * Ошибка — [IdCardException] с `details.reason` (`not-yet`, `no-gift`, `no-slot`, …).
     */
    suspend fun claimGift(): SurpriseGiftResult = try {
        val data = functions.getHttpsCallable(CLAIM).call(emptyMap<String, Any>()).await().getData() as? Map<*, *>
        SurpriseGiftResult.parse(data) ?: throw IdCardException(null, 0, "Empty gift", null)
    } catch (e: FirebaseFunctionsException) {
        throw IdCardException((e.details as? Map<*, *>)?.get("reason") as? String, 0, e.message, e)
    }

    /**
     * Ошибка чтения (2FA ещё не пройдена, правила не задеплоены, нет сети) — не «сюрприза нет»:
     * подписка переоткрывается раз в минуту и подхватит документ, когда доступ появится.
     */
    fun surpriseFlow(uid: String): Flow<Surprise?> = callbackFlow {
        val reg = db.collection("surprises").document(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            trySend(if (snap.exists()) Surprise.parse(snap.data) else null)
        }
        awaitClose { reg.remove() }
    }.retryWhen { cause, _ ->
        if (BuildConfig.DEBUG) Log.w(TAG, "surprises/$uid failed, retry in 60s", cause)
        delay(60_000)
        true
    }

    fun isSeen(uid: String, id: String): Boolean = prefs.getBoolean(key(uid, id), false)

    fun markSeen(uid: String, id: String) {
        prefs.edit().putBoolean(key(uid, id), true).apply()
    }

    /** Отметка для отправителя: открыл / досмотрел. Ошибка не мешает показу. */
    suspend fun mark(uid: String, field: String) {
        runCatching {
            db.collection("surprises").document(uid).update(field, FieldValue.serverTimestamp()).await()
        }.onFailure { if (BuildConfig.DEBUG) Log.w(TAG, "mark $field failed", it) }
    }

    private fun key(uid: String, id: String) = "seen_${uid}_$id"

    companion object {
        private const val TAG = "SurpriseRepository"
        private const val PREFS = "visorlink_surprise_prefs"
        private const val CLAIM = "claimSurpriseGift"
        const val FIELD_OPENED = "openedAt"
        const val FIELD_FINISHED = "finishedAt"
    }
}
