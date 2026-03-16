package by.iposdev.visorlink.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import by.iposdev.visorlink.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File

class ChatRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    private val currentUid get() = auth.currentUser!!.uid

    fun getChatId(uid1: String, uid2: String) =
        listOf(uid1, uid2).sorted().joinToString("_")

    fun chatsFlow(uid: String): Flow<List<Chat>> = callbackFlow {
        val reg = db.collection("chats")
            .whereArrayContains("participants", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val chats = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Chat::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(chats)
            }
        awaitClose { reg.remove() }
    }

    fun messagesFlow(chatId: String): Flow<List<Message>> = callbackFlow {
        val reg = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val messages = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { reg.remove() }
    }

    fun onlineStatusFlow(uid: String): Flow<Pair<Boolean, com.google.firebase.Timestamp?>> = callbackFlow {
        val reg = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                val online = snap?.getBoolean("online") ?: false
                val lastSeen = snap?.getTimestamp("lastSeen")
                trySend(Pair(online, lastSeen))
            }
        awaitClose { reg.remove() }
    }

    suspend fun findOrCreateChat(currentUserProfile: UserProfile, targetUid: String): String {
        val targetDoc = db.collection("users").document(targetUid).get().await()
        val targetUser = targetDoc.toObject(UserProfile::class.java)!!
        val chatId = getChatId(currentUserProfile.uid, targetUid)
        val chatDoc = db.collection("chats").document(chatId).get().await()
        if (!chatDoc.exists()) {
            db.collection("chats").document(chatId).set(mapOf(
                "participants" to listOf(currentUserProfile.uid, targetUid),
                "participantData" to mapOf(
                    currentUserProfile.uid to mapOf(
                        "username" to currentUserProfile.username,
                        "displayName" to currentUserProfile.displayName
                    ),
                    targetUid to mapOf(
                        "username" to targetUser.username,
                        "displayName" to targetUser.displayName
                    )
                ),
                "createdAt" to FieldValue.serverTimestamp(),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessage" to null
            )).await()
        }
        return chatId
    }

    suspend fun sendText(chatId: String, text: String, senderUsername: String, replyTo: ReplyData?) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document()
        val batch = db.batch()
        batch.set(msgRef, mutableMapOf<String, Any?>(
            "senderId" to currentUid,
            "senderUsername" to senderUsername,
            "type" to MessageType.TEXT,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp(),
            "deleted" to false,
            "reactions" to emptyList<Any>(),
            "replyTo" to replyTo?.toMap()
        ))
        batch.update(db.collection("chats").document(chatId), mapOf(
            "lastMessage" to text,
            "lastMessageAt" to FieldValue.serverTimestamp()
        ))
        batch.commit().await()
    }

    suspend fun sendImage(chatId: String, uri: Uri, senderUsername: String, replyTo: ReplyData?) {
        val fileName = "${System.currentTimeMillis()}_${uri.lastPathSegment}"
        val ref = storage.reference.child("chats/$chatId/$fileName")
        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()
        sendExtra(chatId, mapOf("type" to MessageType.IMAGE, "url" to url,
            "fileName" to (uri.lastPathSegment ?: "image")), "📷 Image", senderUsername, replyTo)
    }

    suspend fun sendVoice(chatId: String, file: File, durationSec: Int, senderUsername: String, replyTo: ReplyData?) {
        val path = "chats/$chatId/voice_${System.currentTimeMillis()}.webm"
        val ref = storage.reference.child(path)
        ref.putFile(Uri.fromFile(file)).await()
        val url = ref.downloadUrl.await().toString()
        sendExtra(chatId, mapOf("type" to MessageType.VOICE, "url" to url,
            "duration" to durationSec), "🎤 Voice message", senderUsername, replyTo)
    }

    suspend fun sendSticker(chatId: String, sticker: Sticker, senderUsername: String, replyTo: ReplyData?) {
        sendExtra(chatId, mapOf("type" to MessageType.STICKER, "url" to sticker.url,
            "stickerId" to sticker.id), "🎭 Sticker", senderUsername, replyTo)
    }

    private suspend fun sendExtra(chatId: String, extra: Map<String, Any?>, preview: String,
                                  senderUsername: String, replyTo: ReplyData?) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document()
        val msg = mutableMapOf<String, Any?>(
            "senderId" to currentUid,
            "senderUsername" to senderUsername,
            "createdAt" to FieldValue.serverTimestamp(),
            "deleted" to false,
            "reactions" to emptyList<Any>(),
            "replyTo" to replyTo?.toMap()
        )
        msg.putAll(extra)
        val batch = db.batch()
        batch.set(msgRef, msg)
        batch.update(db.collection("chats").document(chatId), mapOf(
            "lastMessage" to preview,
            "lastMessageAt" to FieldValue.serverTimestamp()
        ))
        batch.commit().await()
    }

    suspend fun deleteMessage(chatId: String, messageId: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update(mapOf("deleted" to true, "deletedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun toggleReaction(chatId: String, messageId: String, emoji: String, currentReactions: List<Reaction>) {
        val ref = db.collection("chats").document(chatId).collection("messages").document(messageId)
        val existing = currentReactions.find { it.emoji == emoji }
        val updated = if (existing != null) {
            if (currentUid in existing.uids) {
                val newUids = existing.uids - currentUid
                if (newUids.isEmpty()) currentReactions.filter { it.emoji != emoji }
                else currentReactions.map { if (it.emoji == emoji) it.copy(uids = newUids, count = newUids.size) else it }
            } else {
                currentReactions.map { if (it.emoji == emoji) it.copy(uids = it.uids + currentUid, count = it.count + 1) else it }
            }
        } else {
            currentReactions + Reaction(emoji, listOf(currentUid), 1)
        }
        ref.update("reactions", updated.map { it.toMap() }).await()
    }
}