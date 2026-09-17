package org.visorlink.app.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TypingRepository(
    private val rtdb: FirebaseDatabase
) {
    fun observeBotTyping(chatId: String, botUid: String): Flow<Boolean> = callbackFlow {
        val typingRef = rtdb.getReference("typing").child(chatId).child(botUid)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isTyping = snapshot.child("isTyping").getValue(Boolean::class.java)
                    ?: (snapshot.child("uid").getValue(String::class.java) != null)
                val ts = snapshot.child("ts").getValue(Long::class.java) ?: 0L
                val isFresh = (System.currentTimeMillis() - ts) < 4000L
                trySend(isTyping && isFresh)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(false)
            }
        }

        typingRef.addValueEventListener(listener)
        awaitClose { typingRef.removeEventListener(listener) }
    }
}
