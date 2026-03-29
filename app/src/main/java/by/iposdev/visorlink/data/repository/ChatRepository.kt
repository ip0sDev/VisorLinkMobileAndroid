package by.iposdev.visorlink.data.repository

import android.net.Uri
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import by.iposdev.visorlink.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File

private const val PAGE_SIZE = 20L

class ChatRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val functions: FirebaseFunctions
) {
    private val currentUid get() = auth.currentUser!!.uid

    fun getChatId(uid1: String, uid2: String) =
        listOf(uid1, uid2).sorted().joinToString("_")

    suspend fun chatExists(chatId: String): Boolean = try {
        db.collection("chats").document(chatId).get().await().exists()
    } catch (e: Exception) { false }

    // ─── All chats (direct + group + channel) ────────────────────────────────

    fun allChatsFlow(uid: String): Flow<List<Chat>> = callbackFlow {
        val directChats = mutableListOf<Chat>()
        val groupChats  = mutableListOf<Chat>()

        fun merge() {
            val all = (directChats + groupChats)
                .distinctBy { it.id }
                .sortedByDescending { it.lastMessageAt?.seconds ?: 0 }
            trySend(all)
        }

        val reg1 = db.collection("chats")
            .whereArrayContains("participants", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                directChats.clear()
                snap?.documents?.forEach { doc ->
                    try {
                        doc.toObject(Chat::class.java)?.copy(id = doc.id)
                            ?.let { directChats.add(it) }
                    } catch (_: Exception) {}
                }
                merge()
            }

        val reg2 = db.collection("chats")
            .whereArrayContains("memberIds", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                groupChats.clear()
                snap?.documents?.forEach { doc ->
                    try {
                        doc.toObject(Chat::class.java)?.copy(id = doc.id)
                            ?.let { groupChats.add(it) }
                    } catch (_: Exception) {}
                }
                merge()
            }

        awaitClose { reg1.remove(); reg2.remove() }
    }

    fun chatsFlow(uid: String) = allChatsFlow(uid)

    // ─── Messages ─────────────────────────────────────────────────────────────

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
                        catch (_: Exception) { null }
                    }
                    .reversed()
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
                catch (_: Exception) { null }
            }
            .reversed()
        val newLastDoc = snap.documents.lastOrNull()
        return Pair(messages, if (snap.documents.size >= PAGE_SIZE) newLastDoc else null)
    }

    // ─── Members ──────────────────────────────────────────────────────────────

    fun membersFlow(chatId: String): Flow<List<Member>> = callbackFlow {
        val reg = db.collection("chats").document(chatId)
            .collection("members")
            .addSnapshotListener { snap, _ ->
                val members = snap?.documents?.mapNotNull { doc ->
                    try { doc.toObject(Member::class.java)?.copy(uid = doc.id) }
                    catch (_: Exception) { null }
                } ?: emptyList()
                trySend(members)
            }
        awaitClose { reg.remove() }
    }

    suspend fun getMyMemberData(chatId: String): Member? {
        val snap = db.collection("chats").document(chatId)
            .collection("members").document(currentUid).get().await()
        return snap.toObject(Member::class.java)?.copy(uid = snap.id)
    }

    // ─── Invites / Notifications ──────────────────────────────────────────────

    fun pendingInvitesFlow(uid: String): Flow<List<GroupInvite>> = callbackFlow {
        val reg = db.collection("invites")
            .whereEqualTo("invitedUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                val invites = snap?.documents?.mapNotNull { doc ->
                    try { doc.toObject(GroupInvite::class.java)?.copy(id = doc.id) }
                    catch (_: Exception) { null }
                } ?: emptyList()
                trySend(invites)
            }
        awaitClose { reg.remove() }
    }

    fun notificationsFlow(uid: String): Flow<List<AppNotification>> = callbackFlow {
        val reg = db.collection("users").document(uid)
            .collection("notifications")
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, _ ->
                val notifs = snap?.documents?.mapNotNull { doc ->
                    try { doc.toObject(AppNotification::class.java)?.copy(id = doc.id) }
                    catch (_: Exception) { null }
                } ?: emptyList()
                trySend(notifs)
            }
        awaitClose { reg.remove() }
    }

    fun onlineStatusFlow(uid: String): Flow<Pair<Boolean, com.google.firebase.Timestamp?>> =
        callbackFlow {
            trySend(Pair(false, null))
            awaitClose {}
        }

    // ─── Create / find direct chat ────────────────────────────────────────────

    suspend fun findOrCreateChat(currentUserProfile: UserProfile, targetUid: String): String {
        val targetDoc = db.collection("users").document(targetUid).get().await()
        val targetUser = targetDoc.toObject(UserProfile::class.java)
            ?: throw Exception("User not found")
        val chatId = getChatId(currentUserProfile.uid, targetUid)
        val chatDoc = db.collection("chats").document(chatId).get().await()
        if (!chatDoc.exists()) {
            db.collection("chats").document(chatId).set(mapOf(
                "type"            to "direct",
                "participants"    to listOf(currentUserProfile.uid, targetUid),
                "participantData" to mapOf(
                    currentUserProfile.uid to mapOf(
                        "username"    to currentUserProfile.username,
                        "displayName" to currentUserProfile.displayName
                    ),
                    targetUid to mapOf(
                        "username"    to targetUser.username,
                        "displayName" to targetUser.displayName
                    )
                ),
                "createdAt"      to FieldValue.serverTimestamp(),
                "lastMessageAt"  to FieldValue.serverTimestamp(),
                "lastMessage"    to null
            )).await()
        }
        return chatId
    }

    // ─── Cloud Functions ──────────────────────────────────────────────────────

    suspend fun createChat(
        type: String,
        name: String,
        tag: String,
        description: String = ""
    ): Pair<String, String> {
        val result = functions.getHttpsCallable("createChat")
            .call(mapOf("type" to type, "name" to name, "tag" to tag, "description" to description))
            .await()
        val data = result.data as Map<*, *>
        return Pair(data["chatId"] as String, data["inviteLink"] as String)
    }

    suspend fun findByTag(tag: String): TagSearchResult {
        val result = functions.getHttpsCallable("findByTag")
            .call(mapOf("tag" to tag)).await()
        val data = result.data as Map<*, *>
        if (data["found"] != true) return TagSearchResult(found = false)
        return TagSearchResult(
            found       = true,
            chatId      = data["chatId"] as? String ?: "",
            name        = data["name"] as? String ?: "",
            tag         = data["tag"] as? String ?: "",
            description = data["description"] as? String ?: "",
            avatarUrl   = data["avatarUrl"] as? String,
            memberCount = (data["memberCount"] as? Long)?.toInt() ?: 0,
            type        = data["type"] as? String ?: "",
            joinByTag   = data["joinByTag"] as? Boolean ?: false
        )
    }

    suspend fun joinByTag(tag: String): String {
        val result = functions.getHttpsCallable("joinByTag")
            .call(mapOf("tag" to tag)).await()
        return (result.data as Map<*, *>)["chatId"] as String
    }

    suspend fun joinByInvite(token: String): Pair<String, String> {
        val result = functions.getHttpsCallable("joinByInvite")
            .call(mapOf("token" to token)).await()
        val data = result.data as Map<*, *>
        return Pair(data["chatId"] as String, data["chatType"] as? String ?: "group")
    }

    suspend fun inviteUser(chatId: String, username: String) {
        functions.getHttpsCallable("inviteUser")
            .call(mapOf("chatId" to chatId, "targetUsername" to username)).await()
    }

    suspend fun respondToInvite(inviteId: String, accept: Boolean): String? {
        val result = functions.getHttpsCallable("respondToInvite")
            .call(mapOf("inviteId" to inviteId, "accept" to accept)).await()
        return (result.data as Map<*, *>)["chatId"] as? String
    }

    suspend fun leaveChat(chatId: String) {
        functions.getHttpsCallable("leaveChat")
            .call(mapOf("chatId" to chatId)).await()
    }

    suspend fun moderateUser(
        chatId: String,
        targetUid: String,
        action: String,
        durationMinutes: Int? = null
    ) {
        functions.getHttpsCallable("moderateUser")
            .call(mapOf(
                "chatId"          to chatId,
                "targetUid"       to targetUid,
                "action"          to action,
                "durationMinutes" to durationMinutes
            )).await()
    }

    suspend fun setMemberRole(chatId: String, targetUid: String, role: String) {
        functions.getHttpsCallable("setMemberRole")
            .call(mapOf("chatId" to chatId, "targetUid" to targetUid, "role" to role)).await()
    }

    suspend fun updateChatSettings(chatId: String, params: Map<String, Any?>) {
        functions.getHttpsCallable("updateChatSettings")
            .call(params + mapOf("chatId" to chatId)).await()
    }

    suspend fun regenerateInviteLink(chatId: String): String {
        val result = functions.getHttpsCallable("regenerateInviteLink")
            .call(mapOf("chatId" to chatId)).await()
        return (result.data as Map<*, *>)["inviteLink"] as String
    }

    // ─── Send messages ────────────────────────────────────────────────────────

    suspend fun sendText(
        chatId: String,
        text: String,
        senderUsername: String,
        replyTo: ReplyData?
    ) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document()
        val batch  = db.batch()
        batch.set(msgRef, mutableMapOf<String, Any?>(
            "senderId"       to currentUid,
            "senderUsername" to senderUsername,
            "type"           to MessageType.TEXT,
            "text"           to text,
            "createdAt"      to FieldValue.serverTimestamp(),
            "deleted"        to false,
            "reactions"      to emptyList<Any>(),
            "readBy"         to listOf(currentUid),
            "replyTo"        to replyTo?.toMap()
        ))
        batch.update(db.collection("chats").document(chatId), mapOf(
            "lastMessage"   to text,
            "lastMessageAt" to FieldValue.serverTimestamp()
        ))
        batch.commit().await()
    }

    suspend fun sendImage(
        chatId: String,
        uri: Uri,
        senderUsername: String,
        replyTo: ReplyData?,
        isSpoiler: Boolean = false
    ) {
        val fileName = "${System.currentTimeMillis()}_${uri.lastPathSegment}"
        val ref      = storage.reference.child("chats/$chatId/$fileName")
        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()

        val extra = mutableMapOf<String, Any?>(
            "type"     to MessageType.IMAGE,
            "url"      to url,
            "fileName" to (uri.lastPathSegment ?: "image")
        )
        if (isSpoiler) extra["spoiler"] = true

        sendExtra(chatId, extra, "📷 Image", senderUsername, replyTo)
    }

    suspend fun sendVoice(
        chatId: String,
        file: File,
        durationSec: Int,
        senderUsername: String,
        replyTo: ReplyData?
    ) {
        val path = "chats/$chatId/voice_${System.currentTimeMillis()}.webm"
        val ref  = storage.reference.child(path)
        ref.putFile(Uri.fromFile(file)).await()
        val url = ref.downloadUrl.await().toString()
        sendExtra(chatId, mapOf(
            "type"     to MessageType.VOICE,
            "url"      to url,
            "duration" to durationSec
        ), "🎤 Voice message", senderUsername, replyTo)
    }

    /**
     * Отправка стикера с привязкой к паку.
     * [sticker]   — StickerItem из StickerPickerBottomSheet
     * [packId]    — ID пака (сохраняется в сообщении для баннера AddStickerPackBanner)
     * [packName]  — название пака (отображается в баннере)
     * [packEmoji] — эмодзи пака (отображается в баннере)
     */
    suspend fun sendSticker(
        chatId: String,
        sticker: StickerItem,
        packId: String,
        packName: String,
        packEmoji: String,
        senderUsername: String,
        replyTo: ReplyData?
    ) {
        sendExtra(
            chatId,
            mapOf(
                "type"      to MessageType.STICKER,
                "url"       to sticker.url,
                "stickerId" to sticker.id,
                "packId"    to packId,
                "packName"  to packName,
                "packEmoji" to packEmoji
            ),
            "$packEmoji Sticker",
            senderUsername,
            replyTo
        )
    }

    // ─── Album ────────────────────────────────────────────────────────────────

    suspend fun uploadAlbumImages(
        chatId: String,
        items: List<AlbumImageLocal>
    ): List<AlbumImage> = coroutineScope {
        items.map { item ->
            async {
                val fileName = "${System.currentTimeMillis()}_${item.uri.lastPathSegment ?: "photo.jpg"}"
                val ref = storage.reference.child("chats/$chatId/$fileName")
                ref.putFile(item.uri).await()
                val url = ref.downloadUrl.await().toString()
                AlbumImage(url = url, fileName = fileName, spoiler = item.spoiler)
            }
        }.awaitAll()
    }

    suspend fun sendAlbum(
        chatId: String,
        images: List<AlbumImage>,
        caption: String?,
        replyTo: ReplyData?
    ): String {
        val data = buildMap<String, Any?> {
            put("chatId",  chatId)
            put("images",  images.map { it.toMap() })
            if (!caption.isNullOrBlank()) put("caption", caption.trim())
            if (replyTo != null) put("replyTo", replyTo.toMap())
        }
        val result = functions.getHttpsCallable("sendAlbum").call(data).await()
        return (result.data as Map<*, *>)["messageId"] as String
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private suspend fun sendExtra(
        chatId: String,
        extra: Map<String, Any?>,
        preview: String,
        senderUsername: String,
        replyTo: ReplyData?
    ) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document()
        val msg    = mutableMapOf<String, Any?>(
            "senderId"       to currentUid,
            "senderUsername" to senderUsername,
            "createdAt"      to FieldValue.serverTimestamp(),
            "deleted"        to false,
            "reactions"      to emptyList<Any>(),
            "readBy"         to listOf(currentUid),
            "replyTo"        to replyTo?.toMap()
        )
        msg.putAll(extra)
        val batch = db.batch()
        batch.set(msgRef, msg)
        batch.update(db.collection("chats").document(chatId), mapOf(
            "lastMessage"   to preview,
            "lastMessageAt" to FieldValue.serverTimestamp()
        ))
        batch.commit().await()
    }

    suspend fun markMessagesAsRead(chatId: String, messages: List<Message>, uid: String) {
        val unread = messages.filter {
            it.senderId != uid && !it.readBy.contains(uid) && !it.deleted
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

    suspend fun deleteMessage(chatId: String, messageId: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update(mapOf(
                "deleted"   to true,
                "deletedAt" to FieldValue.serverTimestamp()
            )).await()
    }

    suspend fun toggleReaction(
        chatId: String,
        messageId: String,
        emoji: String,
        currentReactions: List<Reaction>
    ) {
        val ref      = db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
        val existing = currentReactions.find { it.emoji == emoji }
        val updated  = if (existing != null) {
            if (currentUid in existing.uids) {
                val newUids = existing.uids - currentUid
                if (newUids.isEmpty()) currentReactions.filter { it.emoji != emoji }
                else currentReactions.map {
                    if (it.emoji == emoji) it.copy(uids = newUids, count = newUids.size) else it
                }
            } else {
                currentReactions.map {
                    if (it.emoji == emoji)
                        it.copy(uids = it.uids + currentUid, count = it.count + 1)
                    else it
                }
            }
        } else {
            currentReactions + Reaction(emoji, listOf(currentUid), 1)
        }
        ref.update("reactions", updated.map { it.toMap() }).await()
    }

    // ─── v4: Comments Firestore listeners ─────────────────────────────────────

    fun listenComments(
        chatId: String,
        messageId: String,
        onUpdate: (List<Comment>) -> Unit
    ): ListenerRegistration {
        return db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val comments = snap.documents.mapNotNull { doc ->
                    try { doc.toObject(Comment::class.java)?.copy(id = doc.id) }
                    catch (_: Exception) { null }
                }
                onUpdate(comments)
            }
    }

    fun listenPost(
        chatId: String,
        messageId: String,
        onUpdate: (Message) -> Unit
    ): ListenerRegistration {
        return db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .addSnapshotListener { snap, _ ->
                snap?.toObject(Message::class.java)
                    ?.copy(id = snap.id)
                    ?.let(onUpdate)
            }
    }

    // ─── v4: Cloud Function wrappers for comments ─────────────────────────────

    suspend fun addComment(
        chatId: String,
        messageId: String,
        type: String,
        text: String? = null,
        url: String? = null,
        fileName: String? = null,
        duration: Int? = null,
        spoiler: Boolean = false,
        replyTo: CommentReplyData? = null
    ): String {
        val data = buildMap<String, Any?> {
            put("chatId", chatId)
            put("messageId", messageId)
            put("type", type)
            if (text != null) put("text", text)
            if (url != null) put("url", url)
            if (fileName != null) put("fileName", fileName)
            if (duration != null) put("duration", duration)
            put("spoiler", spoiler)
            if (replyTo != null) put("replyTo", replyTo.toMap())
        }
        val result = functions.getHttpsCallable("addComment").call(data).await()
        return (result.data as Map<*, *>)["commentId"] as String
    }

    suspend fun togglePostComments(chatId: String, messageId: String, enabled: Boolean) {
        functions.getHttpsCallable("togglePostComments").call(mapOf(
            "chatId"    to chatId,
            "messageId" to messageId,
            "enabled"   to enabled
        )).await()
    }

    suspend fun toggleCommentReaction(
        chatId: String,
        messageId: String,
        commentId: String,
        emoji: String,
        currentReactions: List<Reaction>
    ) {
        val existing = currentReactions.find { it.emoji == emoji }
        val updated  = if (existing == null) {
            currentReactions + Reaction(emoji, listOf(currentUid), 1)
        } else {
            val hasMe = currentUid in existing.uids
            currentReactions.map { r ->
                if (r.emoji != emoji) r
                else if (hasMe) r.copy(uids = r.uids - currentUid, count = r.count - 1)
                else r.copy(uids = r.uids + currentUid, count = r.count + 1)
            }.filter { it.count > 0 }
        }
        db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .collection("comments").document(commentId)
            .update("reactions", updated.map { it.toMap() })
            .await()
    }

    suspend fun deleteComment(chatId: String, messageId: String, commentId: String) {
        db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .collection("comments").document(commentId)
            .update(mapOf(
                "deleted"   to true,
                "deletedAt" to FieldValue.serverTimestamp()
            )).await()
    }

    suspend fun uploadAndCommentImage(
        chatId: String,
        messageId: String,
        file: Uri,
        spoiler: Boolean,
        replyTo: CommentReplyData? = null
    ) {
        val filename   = "${System.currentTimeMillis()}_${file.lastPathSegment}"
        val storageRef = storage.reference.child("chats/$chatId/comments/$filename")
        storageRef.putFile(file).await()
        val url = storageRef.downloadUrl.await().toString()
        addComment(
            chatId    = chatId,
            messageId = messageId,
            type      = MessageType.IMAGE,
            url       = url,
            fileName  = filename,
            spoiler   = spoiler,
            replyTo   = replyTo
        )
    }

    suspend fun uploadAndCommentVoice(
        chatId: String,
        messageId: String,
        audioFile: File,
        durationSeconds: Int,
        replyTo: CommentReplyData? = null
    ) {
        val path       = "chats/$chatId/comments/voice_${System.currentTimeMillis()}.webm"
        val storageRef = storage.reference.child(path)
        storageRef.putFile(audioFile.toUri()).await()
        val url = storageRef.downloadUrl.await().toString()
        addComment(
            chatId    = chatId,
            messageId = messageId,
            type      = MessageType.VOICE,
            url       = url,
            duration  = durationSeconds,
            replyTo   = replyTo
        )
    }
}