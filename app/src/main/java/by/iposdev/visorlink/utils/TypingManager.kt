package by.iposdev.visorlink.utils

import android.os.Handler
import android.os.Looper
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TypingManager(private val chatId: String, private val uid: String) {

    private val rtdb = Firebase.database
    private val typingRef = rtdb.getReference("typing/$chatId/$uid")
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopTyping() }

    fun onTyping() {
        typingRef.setValue(mapOf("uid" to uid, "ts" to System.currentTimeMillis()))
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, 3000)
    }

    fun stopTyping() {
        handler.removeCallbacks(stopRunnable)
        typingRef.removeValue()
    }

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
                        val uid = child.child("uid").getValue(String::class.java)
                        val ts = child.child("ts").getValue(Long::class.java) ?: 0L
                        uid != currentUid && (now - ts) < 4000
                    }
                    trySend(someoneTyping)
                }
                override fun onCancelled(e: DatabaseError) { trySend(false) }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }
    }
}