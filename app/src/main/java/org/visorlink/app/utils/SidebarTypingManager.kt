package org.visorlink.app.utils

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Статусы «Печатает...» для списка чатов. Правила RTDB закрывают чтение корня `/typing`,
 * поэтому слушаем только `typing/{chatId}` для чатов пользователя: подписки дедуплицируются
 * и снимаются, когда чат пропал из списка.
 */
class SidebarTypingManager(
    private val rtdb: FirebaseDatabase
) {
    private val _typingMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingMap: StateFlow<Map<String, Boolean>> = _typingMap.asStateFlow()

    private val listeners = mutableMapOf<String, ValueEventListener>()
    private var currentUserId: String = ""

    fun startListening(currentUserId: String) {
        this.currentUserId = currentUserId
    }

    /** Приводит набор подписок в соответствие с текущим списком чатов. */
    @Synchronized
    fun syncChats(chatIds: Collection<String>) {
        if (currentUserId.isEmpty()) return
        val wanted = chatIds.filter { it.isNotEmpty() && it.length <= 128 }.toSet()

        (listeners.keys - wanted).toList().forEach { removeChat(it) }
        (wanted - listeners.keys).forEach { addChat(it) }
    }

    private fun addChat(chatId: String) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = TypingManager.currentServerTime()
                var isSomeoneTyping = false
                for (userSnap in snapshot.children) {
                    val uid = userSnap.child("uid").getValue(String::class.java) ?: userSnap.key
                    val ts = userSnap.child("ts").getValue(Long::class.java) ?: 0L
                    // Печатает НЕ текущий пользователь и событие свежее (< 4 сек)
                    if (uid != null && uid != currentUserId && (now - ts) in 0..4000L) {
                        isSomeoneTyping = true
                        break
                    }
                }
                _typingMap.value = if (isSomeoneTyping) _typingMap.value + (chatId to true) else _typingMap.value - chatId
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("SidebarTyping", "RTDB typing/$chatId cancelled: ${error.message}")
            }
        }
        listeners[chatId] = listener
        rtdb.getReference("typing").child(chatId).addValueEventListener(listener)
    }

    private fun removeChat(chatId: String) {
        listeners.remove(chatId)?.let { rtdb.getReference("typing").child(chatId).removeEventListener(it) }
        _typingMap.value = _typingMap.value - chatId
    }

    @Synchronized
    fun stopListening() {
        listeners.keys.toList().forEach { removeChat(it) }
        _typingMap.value = emptyMap()
    }
}
