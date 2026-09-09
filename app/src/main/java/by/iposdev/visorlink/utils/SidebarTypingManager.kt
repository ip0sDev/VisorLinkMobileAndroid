package by.iposdev.visorlink.utils

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Единый глобальный слушатель статусов «Печатает...» для списка чатов (Секция 4 спецификации).
 * Слушает корень `/typing` в Firebase RTDB с гарантией минимального сетевого трафика
 * и нулевого оверхеда на количество чатов.
 */
class SidebarTypingManager(
    private val rtdb: FirebaseDatabase
) {
    private val _typingMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingMap: StateFlow<Map<String, Boolean>> = _typingMap.asStateFlow()

    private var typingListener: ValueEventListener? = null
    private val typingRef = rtdb.getReference("typing")

    fun startListening(currentUserId: String) {
        if (typingListener != null) return

        typingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val resultMap = mutableMapOf<String, Boolean>()

                for (chatSnap in snapshot.children) {
                    val chatId = chatSnap.key ?: continue
                    var isSomeoneTyping = false

                    for (userSnap in chatSnap.children) {
                        val uid = userSnap.child("uid").getValue(String::class.java) ?: userSnap.key
                        val ts = userSnap.child("ts").getValue(Long::class.java) ?: 0L

                        // Если печатает НЕ текущий пользователь и событие свежее (< 4 сек)
                        if (uid != null && uid != currentUserId && (now - ts < 4000L)) {
                            isSomeoneTyping = true
                            break
                        }
                    }

                    if (isSomeoneTyping) {
                        resultMap[chatId] = true
                    }
                }
                _typingMap.value = resultMap
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("SidebarTyping", "RTDB typing cancelled: ${error.message}")
            }
        }

        typingRef.addValueEventListener(typingListener!!)
    }

    fun stopListening() {
        typingListener?.let { typingRef.removeEventListener(it) }
        typingListener = null
        _typingMap.value = emptyMap()
    }
}
