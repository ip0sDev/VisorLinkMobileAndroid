package org.visorlink.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Блокировки пользователь→пользователь (спека «Блокировки, баны, Forge», §2).
 *
 * Списки приватные и пишутся только сервером (`setUserBlock`): `blocks/{я}/users/{они}` — кого
 * заблокировал я, `blockedBy/{я}/users/{они}` — кто заблокировал меня. Два слушателя на всё
 * приложение, не по документу на собеседника. `PERMISSION_DENIED` слушателя — не сбой: список
 * остаётся пустым и запрос не повторяется.
 */
class BlockRepository(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _blocks = MutableStateFlow<Set<String>>(emptySet())
    /** Кого заблокировал я. */
    val blocks: StateFlow<Set<String>> = _blocks.asStateFlow()

    private val _blockedBy = MutableStateFlow<Set<String>>(emptySet())
    /** Кто заблокировал меня. */
    val blockedBy: StateFlow<Set<String>> = _blockedBy.asStateFlow()

    private var uid: String? = null
    private var blocksReg: ListenerRegistration? = null
    private var blockedByReg: ListenerRegistration? = null

    @Synchronized
    fun start(uid: String) {
        if (this.uid == uid && blocksReg != null) return
        stop()
        this.uid = uid
        // Последний известный список — пока не пришёл снимок (офлайн-старт)
        _blocks.value = prefs.getStringSet(key(uid), emptySet()).orEmpty().toSet()

        blocksReg = db.collection("blocks").document(uid).collection("users")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (org.visorlink.app.BuildConfig.DEBUG) Log.w(TAG, "blocks listener: ${err.code}")
                    return@addSnapshotListener
                }
                val ids = snap?.documents?.map { it.id }?.toSet() ?: return@addSnapshotListener
                _blocks.value = ids
                prefs.edit().putStringSet(key(uid), ids).apply()
            }
        blockedByReg = db.collection("blockedBy").document(uid).collection("users")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (org.visorlink.app.BuildConfig.DEBUG) Log.w(TAG, "blockedBy listener: ${err.code}")
                    return@addSnapshotListener
                }
                _blockedBy.value = snap?.documents?.map { it.id }?.toSet() ?: return@addSnapshotListener
            }
    }

    @Synchronized
    fun stop() {
        blocksReg?.remove()
        blockedByReg?.remove()
        blocksReg = null
        blockedByReg = null
        uid = null
        _blocks.value = emptySet()
        _blockedBy.value = emptySet()
    }

    /**
     * `setUserBlock { targetUid, blocked }`. Поле `users.blockedUsers` клиенту писать нельзя —
     * правила запрещают. Список обновляется оптимистично, слушатель потом подтвердит.
     */
    suspend fun setBlocked(targetUid: String, blocked: Boolean) {
        functions.getHttpsCallable("setUserBlock")
            .call(mapOf("targetUid" to targetUid, "blocked" to blocked))
            .await()
        _blocks.value = if (blocked) _blocks.value + targetUid else _blocks.value - targetUid
    }

    companion object {
        private const val TAG = "BlockRepository"
        private const val PREFS = "visorlink_blocks_prefs"

        private fun key(uid: String) = "blocks_$uid"

        /** Для процесса, поднятого пушем (FCM): без слушателей, из последнего снимка. */
        fun cachedBlocks(context: Context, uid: String): Set<String> =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(key(uid), emptySet()).orEmpty().toSet()
    }
}
