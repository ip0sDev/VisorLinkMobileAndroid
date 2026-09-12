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
    private var lastSentTypingTime = 0L

    fun onTyping() {
        val now = System.currentTimeMillis()
        if (now - lastSentTypingTime >= 2500L) {
            lastSentTypingTime = now
            val typingUpdate = mapOf(
                "uid" to uid,
                "ts" to ServerValue.TIMESTAMP
            )
            typingRef.onDisconnect().removeValue()
            typingRef.setValue(typingUpdate)
        }

        // Эта логика с Handler абсолютно правильная, оставляем ее
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, 3000)
    }

    fun stopTyping() {
        lastSentTypingTime = 0L
        handler.removeCallbacks(stopRunnable)
        typingRef.removeValue()
    }

    // Этот метод будет вызван в onCleared() у ViewModel
    fun cleanup() {
        stopTyping()
    }

    companion object {
        @Volatile
        private var serverTimeOffset: Long = 0L

        init {
            try {
                val offsetRef = Firebase.database.getReference(".info/serverTimeOffset")
                offsetRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        serverTimeOffset = snapshot.getValue(Long::class.java) ?: 0L
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            } catch (_: Exception) {}
        }

        fun currentServerTime(): Long = System.currentTimeMillis() + serverTimeOffset

        fun observeTyping(chatId: String, currentUid: String): Flow<Boolean> = callbackFlow {
            val ref = Firebase.database.getReference("typing/$chatId")
            val listener = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val now = currentServerTime()
                    val someoneTyping = snap.children.any { child ->
                        val uid = child.child("uid").getValue(String::class.java) ?: child.key
                        val isTyping = child.child("isTyping").getValue(Boolean::class.java) ?: true
                        val ts = child.child("ts").getValue(Long::class.java) ?: 0L
                        // Логика проверки с учетом serverTimeOffset надежна против расхождений локального времени
                        uid != currentUid && isTyping && (now - ts) in 0..4000
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