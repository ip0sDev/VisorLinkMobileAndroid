package by.iposdev.visorlink.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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

private const val PAGE_SIZE = 20L

class ChatRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    private val currentUid get() = auth.currentUser!!.uid

    fun getChatId(uid1: String, uid2: String) =
        listOf(uid1, uid2).sorted().joinToString("_")

    suspend fun chatExists(chatId: String): Boolean = try {
        db.collection("chats").document(chatId).get().await().exists()
    } catch (e: Exception) { false }

    // ─── Chat list ────────────────────────────────────────────────────────────

    fun chatsFlow(uid: String): Flow<List<Chat>> = callbackFlow {
        val reg = db.collection("chats")
            .whereArrayContains("participants", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val chats = snap?.documents?.mapNotNull { doc ->
                    try { doc.toObject(Chat::class.java)?.copy(id = doc.id) }
                    catch (e: Exception) { null }
                } ?: emptyList()
                trySend(chats)
            }
        awaitClose { reg.remove() }
    }

    // ─── Messages (paginated) ─────────────────────────────────────────────────

    /**
     * Слушает последние PAGE_SIZE сообщений в реальном времени.
     * Возвращает пару: список сообщений + последний документ (для пагинации).
     */
    fun latestMessagesFlow(
        chatId: String,
        onUpdate: (List<Message>, DocumentSnapshot?) -> Unit
    ): Flow<Unit> = callbackFlow {
        val reg = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val messages = snap.documents
                    .mapNotNull { doc ->
                        try { doc.toObject(Message::class.java)?.copy(id = doc.id) }
                        catch (e: Exception) { null }
                    }
                    .reversed() // отображаем старые первыми
                val lastDoc = snap.documents.lastOrNull()
                onUpdate(messages, lastDoc)
                trySend(Unit)
            }
        awaitClose { reg.remove() }
    }

    suspend fun loadOlderMessages(
        chatId: String,
        startAfterDoc: DocumentSnapshot
    ): Pair<List<Message>, DocumentSnapshot?> {
        val snap = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(startAfterDoc)
            .limit(PAGE_SIZE)
            .get().await()

        val messages = snap.documents
            .mapNotNull { doc ->
                try { doc.toObject(Message::class.java)?.copy(id = doc.id) }
                catch (e: Exception) { null }
            }
            .reversed()
        val newLastDoc = snap.documents.lastOrNull()
        val hasMore = snap.documents.size >= PAGE_SIZE
        return Pair(messages, if (hasMore) newLastDoc else null)
    }

    // ─── Online status (теперь из RTDB через PresenceManager, этот метод устарел)
    // Оставляем пустышку для совместимости, реальная логика в PresenceManager

    fun onlineStatusFlow(uid: String): Flow<Pair<Boolean, com.google.firebase.Timestamp?>> = callbackFlow {
        trySend(Pair(false, null))
        awaitClose {}
    }

    // ─── Create / find chat ───────────────────────────────────────────────────

    suspend fun findOrCreateChat(currentUserProfile: UserProfile, targetUid: String): String {
        val targetDoc = db.collection("users").document(targetUid).get().await()
        val targetUser = targetDoc.toObject(UserProfile::class.java)
            ?: throw Exception("User not found")
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

    // ─── Send messages ────────────────────────────────────────────────────────

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
            "readBy" to listOf(currentUid),  // ← v2
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
        sendExtra(chatId, mapOf(
            "type" to MessageType.IMAGE, "url" to url,
            "fileName" to (uri.lastPathSegment ?: "image")
        ), "📷 Image", senderUsername, replyTo)
    }

    suspend fun sendVoice(chatId: String, file: File, durationSec: Int, senderUsername: String, replyTo: ReplyData?) {
        val path = "chats/$chatId/voice_${System.currentTimeMillis()}.webm"
        val ref = storage.reference.child(path)
        ref.putFile(Uri.fromFile(file)).await()
        val url = ref.downloadUrl.await().toString()
        sendExtra(chatId, mapOf(
            "type" to MessageType.VOICE, "url" to url, "duration" to durationSec
        ), "🎤 Voice message", senderUsername, replyTo)
    }

    suspend fun sendSticker(chatId: String, sticker: Sticker, senderUsername: String, replyTo: ReplyData?) {
        sendExtra(chatId, mapOf(
            "type" to MessageType.STICKER, "url" to sticker.url, "stickerId" to sticker.id
        ), "🎭 Sticker", senderUsername, replyTo)
    }

    private suspend fun sendExtra(
        chatId: String, extra: Map<String, Any?>, preview: String,
        senderUsername: String, replyTo: ReplyData?
    ) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document()
        val msg = mutableMapOf<String, Any?>(
            "senderId" to currentUid,
            "senderUsername" to senderUsername,
            "createdAt" to FieldValue.serverTimestamp(),
            "deleted" to false,
            "reactions" to emptyList<Any>(),
            "readBy" to listOf(currentUid),  // ← v2
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

    // ─── Read receipts ────────────────────────────────────────────────────────

    suspend fun markMessagesAsRead(chatId: String, messages: List<Message>, uid: String) {
        val unread = messages.filter { msg ->
            msg.senderId != uid && !msg.readBy.contains(uid) && !msg.deleted
        }
        if (unread.isEmpty()) return
        val batch = db.batch()
        for (msg in unread) {
            val ref = db.collection("chats").document(chatId)
                .collection("messages").document(msg.id)
            batch.update(ref, "readBy", FieldValue.arrayUnion(uid))
        }
        batch.commit().await()
    }

    // ─── Delete / React ───────────────────────────────────────────────────────

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
                else currentReactions.map {
                    if (it.emoji == emoji) it.copy(uids = newUids, count = newUids.size) else it
                }
            } else {
                currentReactions.map {
                    if (it.emoji == emoji) it.copy(uids = it.uids + currentUid, count = it.count + 1)
                    else it
                }
            }
        } else {
            currentReactions + Reaction(emoji, listOf(currentUid), 1)
        }
        ref.update("reactions", updated.map { it.toMap() }).await()
    }
}