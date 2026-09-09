package by.iposdev.visorlink.utils

import android.os.Handler
import android.os.Looper
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TypingManager(private val chatId: String, private val uid: String) {

    // ИЗМЕНЕНИЕ 1: Получаем инстанс базы данных один раз для чистоты кода
    private val rtdb = Firebase.database.reference
    private val typingRef = rtdb.child("typing/$chatId/$uid")

    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopTyping() }

    fun onTyping() {
        // ИЗМЕНЕНИЕ 2 (КЛЮЧЕВОЕ): Используем серверное время вместо системного
        val typingUpdate = mapOf(
            "uid" to uid,
            "ts" to ServerValue.TIMESTAMP
        )
        typingRef.onDisconnect().removeValue()
        typingRef.setValue(typingUpdate)

        // Эта логика с Handler абсолютно правильная, оставляем ее
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, 3000)
    }

    fun stopTyping() {
        handler.removeCallbacks(stopRunnable)
        typingRef.removeValue()
    }

    // Этот метод будет вызван в onCleared() у ViewModel
    fun cleanup() {
        stopTyping()
    }

    companion object {
        fun observeTyping(chatId: String, currentUid: String): Flow<Boolean> = callbackFlow {
            val ref = Firebase.database.getReference("typing/$chatId")
            val listener = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val now = System.currentTimeMillis()
                    val someoneTyping = snap.children.any { child ->
                        val uid = child.child("uid").getValue(String::class.java) ?: child.key
                        val isTyping = child.child("isTyping").getValue(Boolean::class.java) ?: true
                        val ts = child.child("ts").getValue(Long::class.java) ?: 0L
                        // Логика проверки остается той же, но теперь 'ts' надежный
                        uid != currentUid && isTyping && (now - ts) < 4000
                    }
                    trySend(someoneTyping)
                }
                override fun onCancelled(e: DatabaseError) {
                    // В случае ошибки лучше отправить false
                    trySend(false)
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }
    }
}