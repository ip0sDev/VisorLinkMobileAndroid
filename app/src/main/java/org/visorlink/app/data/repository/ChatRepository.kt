package org.visorlink.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import org.visorlink.app.data.model.*
import org.visorlink.app.utils.ChatDataCache
import org.visorlink.app.utils.DiagnosticLogBuffer
import org.visorlink.app.utils.ImageCache
import org.visorlink.app.utils.VoiceCache
import org.visorlink.app.utils.NetworkMonitor
import org.visorlink.app.utils.deriveKey
import org.visorlink.app.utils.encryptText
import org.visorlink.app.utils.decryptText
import com.google.firebase.Timestamp
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val PAGE_SIZE = 20L
private const val CHATS_LIST_LIMIT = 50L

data class ChatDiagInfo(
    val uid: String = "",
    val directCount: Int = 0,
    val groupCount: Int = 0,
    val emerCount: Int = 0,
    val totalCount: Int = 0,
    val lastDirectError: String? = null,
    val lastGroupError: String? = null,
    val lastSyncTs: Long = 0L
)

class ChatRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val googleDriveAuthManager: org.visorlink.app.utils.GoogleDriveAuthManager? = null,
    private val googleDriveMediaService: org.visorlink.app.data.remote.GoogleDriveMediaService? = null,
    private val contentKeyManager: org.visorlink.app.utils.ContentKeyManager
) {
    val currentUid: String get() = auth.currentUser?.uid ?: ""

    private val _diagInfo = MutableStateFlow(ChatDiagInfo())
    val diagInfo: StateFlow<ChatDiagInfo> = _diagInfo.asStateFlow()

    fun getChatId(uid1: String, uid2: String) =
        listOf(uid1, uid2).sorted().joinToString("_")

    suspend fun chatExists(chatId: String): Boolean = withContext(Dispatchers.IO) {
        if (chatId.startsWith("emer_")) {
            val cached = ChatDataCache.loadChat(context, chatId)
            return@withContext cached != null
        }
        // 1. Сначала мгновенно проверяем наш локальный SQLite-кэш
        val cached = ChatDataCache.loadChat(context, chatId)
        if (cached != null) return@withContext true
        val cachedChats = ChatDataCache.loadChatList(context, currentUid)
        if (cachedChats.any { it.id == chatId }) return@withContext true

        // 2. Быстрая проверка Firestore кэша без ожидания сети
        try {
            val cacheDoc = db.collection("chats").document(chatId).get(Source.CACHE).await()
            if (cacheDoc.exists()) return@withContext true
        } catch (_: Exception) {}

        // 3. Сетевой фоллбэк
        try {
            db.collection("chats").document(chatId).get().await().exists()
        } catch (e: Exception) {
            false
        }
    }

    fun allChatsFlow(uid: String): Flow<List<Chat>> = channelFlow {
        DiagnosticLogBuffer.i("ChatRepo", "allChatsFlow started for uid=$uid, limit=$CHATS_LIST_LIMIT")
        _diagInfo.update { it.copy(uid = uid) }

        var emerList: List<Chat> = emptyList()
        var directList: List<Chat> = emptyList()
        var groupList: List<Chat> = emptyList()
        val listLock = Any()

        fun merge() {
            val all: List<Chat>
            val dCount: Int
            val gCount: Int
            val eCount: Int
            synchronized(listLock) {
                dCount = directList.size
                gCount = groupList.size
                eCount = emerList.size
                all = (directList + groupList + emerList)
                    .distinctBy { it.id }
                    .sortedByDescending { it.lastMessageAt?.seconds ?: 0 }
            }

            DiagnosticLogBuffer.i(
                "ChatRepo",
                "allChatsFlow merged: direct=$dCount, group=$gCount, emer=$eCount, total=${all.size}"
            )
            _diagInfo.update {
                it.copy(
                    uid = uid,
                    directCount = dCount,
                    groupCount = gCount,
                    emerCount = eCount,
                    totalCount = all.size,
                    lastSyncTs = System.currentTimeMillis()
                )
            }
            trySend(all)

            launch(Dispatchers.IO) {
                try {
                    ChatDataCache.saveChatList(context, uid, all)
                } catch (e: Throwable) {
                    DiagnosticLogBuffer.e("ChatRepo", "Failed to cache chat list: ${e.message}", e)
                }
            }
        }

        launch(Dispatchers.IO) {
            try {
                val cached = ChatDataCache.loadChatList(context, uid)
                synchronized(listLock) {
                    emerList = cached.filter { it.isEmergency }
                }
                if (cached.isNotEmpty()) {
                    DiagnosticLogBuffer.i("ChatRepo", "allChatsFlow loaded ${cached.size} cached chats from SQLite")
                    trySend(cached)
                }
            } catch (e: Throwable) {
                DiagnosticLogBuffer.e("ChatRepo", "Failed to load cached chats: ${e.message}", e)
            }
        }

        val reg1 = db.collection("chats").whereArrayContains("participants", uid)
            .limit(CHATS_LIST_LIMIT)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    val errMsg = "${error.code}: ${error.message}"
                    Log.e("ChatRepository", "allChatsFlow direct listener error: $errMsg", error)
                    DiagnosticLogBuffer.e("ChatRepo", "allChatsFlow direct listener error: $errMsg", error)
                    _diagInfo.update { it.copy(lastDirectError = errMsg) }
                    return@addSnapshotListener
                }
                _diagInfo.update { it.copy(lastDirectError = null) }
                val docs = snap?.documents.orEmpty()
                DiagnosticLogBuffer.i("ChatRepo", "allChatsFlow direct snapshot: ${docs.size} docs")
                val parsed = docs.mapNotNull { doc ->
                    doc.toChatOrNull() ?: run {
                        DiagnosticLogBuffer.w("ChatRepo", "direct chat doc ${doc.id} parse failed")
                        null
                    }
                }
                synchronized(listLock) {
                    directList = parsed
                }
                merge()
            }

        val reg2 = db.collection("chats").whereArrayContains("memberIds", uid)
            .limit(CHATS_LIST_LIMIT)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    val errMsg = "${error.code}: ${error.message}"
                    Log.e("ChatRepository", "allChatsFlow group listener error: $errMsg", error)
                    DiagnosticLogBuffer.e("ChatRepo", "allChatsFlow group listener error: $errMsg", error)
                    _diagInfo.update { it.copy(lastGroupError = errMsg) }
                    return@addSnapshotListener
                }
                _diagInfo.update { it.copy(lastGroupError = null) }
                val docs = snap?.documents.orEmpty()
                DiagnosticLogBuffer.i("ChatRepo", "allChatsFlow group snapshot: ${docs.size} docs")
                val parsed = docs.mapNotNull { doc ->
                    doc.toChatOrNull() ?: run {
                        DiagnosticLogBuffer.w("ChatRepo", "group chat doc ${doc.id} parse failed")
                        null
                    }
                }
                synchronized(listLock) {
                    groupList = parsed
                }
                merge()
            }

        awaitClose { reg1.remove(); reg2.remove() }
    }

    fun chatsFlow(uid: String) = allChatsFlow(uid)

    fun latestMessagesFlow(chatId: String, onUpdate: (List<Message>, DocumentSnapshot?) -> Unit): Flow<Unit> = channelFlow {
        DiagnosticLogBuffer.i("ChatRepo", "latestMessagesFlow started for chatId=$chatId")
        var currentFirestore = emptyList<Message>()
        var currentOutbox = emptyList<ChatDataCache.QueuedAction>()
        var currentLastDoc: DocumentSnapshot? = null
        val msgLock = Any()

        fun rebuild() {
            val firestoreIds: Set<String>
            val combined: List<Message>
            val lastDoc: DocumentSnapshot?
            val outboxCopy: List<ChatDataCache.QueuedAction>
            synchronized(msgLock) {
                firestoreIds = currentFirestore.map { it.id }.toSet()
                outboxCopy = currentOutbox
                val outboxMsgs = currentOutbox.map { action ->
                    val type = action.data.optString("type", action.type)
                    if (action.progress > 0f) {
                        Log.d("ChatRepo", "Outbox message ${action.id} progress: ${action.progress}")
                    }
                    Message(
                        id = action.id,
                        seq = action.data.optLong("nextSeq").takeIf { it > 0 },
                        senderId = currentUid,
                        senderUsername = action.data.optString("senderUsername"),
                        type = type,
                        text = action.data.optString("text"),
                        // Подпись видна с момента нажатия «Отправить»
                        caption = action.data.optString("caption").ifEmpty { null },
                        localFile = action.data.optString("localPath").takeIf { it.isNotEmpty() }?.let { File(it) },
                        uploadProgress = if (action.status == 0) action.progress else null,
                        createdAt = Timestamp(Date(action.ts)),
                        status = when (action.status) {
                            1 -> SendStatus.SENT
                            2 -> SendStatus.ERROR
                            else -> SendStatus.QUEUED
                        }
                    )
                }

                val outboxMap = outboxMsgs.associateBy { it.id }
                val firestoreMap = currentFirestore.associateBy { it.id }

                val combinedMap = outboxMap.toMutableMap()
                firestoreMap.forEach { (id, fsMsg) ->
                    val obMsg = outboxMap[id]
                    combinedMap[id] = if (obMsg != null) {
                        fsMsg.copy(
                            localFile = obMsg.localFile ?: fsMsg.localFile,
                            status = SendStatus.SENT
                        )
                    } else {
                        fsMsg
                    }
                }

                combined = combinedMap.values.distinctBy { it.id }
                lastDoc = currentLastDoc
            }
            onUpdate(combined, lastDoc)

            val confirmedIds = outboxCopy.map { it.id }.filter { it in firestoreIds }
            if (confirmedIds.isNotEmpty()) {
                launch(Dispatchers.IO) {
                    try {
                        ChatDataCache.cleanupOutbox(context, confirmedIds)
                    } catch (e: Throwable) {
                        DiagnosticLogBuffer.e("ChatRepo", "Failed to cleanup outbox: ${e.message}", e)
                    }
                }
            }
        }

        // 1. Outbox listener (отображение отправляемых сообщений)
        launch(Dispatchers.Default) {
            try {
                ChatDataCache.outboxFlow(context, chatId).collect {
                    synchronized(msgLock) {
                        currentOutbox = it
                    }
                    rebuild()
                }
            } catch (e: Throwable) {
                DiagnosticLogBuffer.e("ChatRepo", "Outbox flow error: ${e.message}", e)
            }
        }

        // 2. Initial Cache load + Signal listener (для мгновенного обновления при сохранении в SQLite)
        launch(Dispatchers.IO) {
            try {
                val cached = ChatDataCache.loadMessages(context, chatId)
                synchronized(msgLock) {
                    if (currentFirestore.isEmpty() && cached.isNotEmpty()) {
                        currentFirestore = cached
                        rebuild()
                    }
                }

                ChatDataCache.cacheUpdateSignal.collect { updatedChatId ->
                    if (updatedChatId == chatId) {
                        try {
                            val updated = ChatDataCache.loadMessages(context, chatId)
                            synchronized(msgLock) {
                                currentFirestore = updated
                            }
                            rebuild()
                        } catch (e: Throwable) {
                            DiagnosticLogBuffer.e("ChatRepo", "Cache reload error: ${e.message}", e)
                        }
                    }
                }
            } catch (e: Throwable) {
                DiagnosticLogBuffer.e("ChatRepo", "Failed to load cached messages: ${e.message}", e)
            }
        }

        // 3. Firestore Snapshot Listener (только для обычных чатов)
        val reg = if (!chatId.startsWith("emer_")) {
            db.collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(PAGE_SIZE)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                    if (error != null) {
                        val errMsg = "${error.code}: ${error.message}"
                        Log.e("ChatRepository", "latestMessagesFlow error: $errMsg", error)
                        DiagnosticLogBuffer.e("ChatRepo", "latestMessagesFlow $chatId error: $errMsg", error)
                        return@addSnapshotListener
                    }
                    if (snap == null) return@addSnapshotListener
                    val parsed = snap.documents.mapNotNull { it.toMessageOrNull() }
                    DiagnosticLogBuffer.i("ChatRepo", "latestMessagesFlow $chatId: ${snap.documents.size} docs, ${parsed.size} parsed")
                    val messages = parsed.reversed()

                    synchronized(msgLock) {
                        currentFirestore = messages
                        currentLastDoc = snap.documents.lastOrNull()
                    }
                    rebuild()

                    launch(Dispatchers.IO) {
                        try {
                            ChatDataCache.saveMessages(context, chatId, messages)
                            // Prefetch media
                            messages.forEach { msg ->
                                try {
                                    val url = msg.url
                                    if (!url.isNullOrEmpty()) {
                                        if (msg.type == MessageType.VOICE) VoiceCache.getOrDownload(context, url)
                                        else ImageCache.getOrDownload(context, url)
                                    }
                                } catch (_: Exception) {}
                            }
                        } catch (e: Throwable) {
                            DiagnosticLogBuffer.e("ChatRepo", "Failed to save messages to cache: ${e.message}", e)
                        }
                    }
                }
        } else null

        awaitClose {
            reg?.remove()
        }
        send(Unit)
    }

    suspend fun loadOlderMessages(chatId: String, startAfterDoc: DocumentSnapshot): Pair<List<Message>, DocumentSnapshot?> = try {
        val snap = db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING).startAfter(startAfterDoc).limit(PAGE_SIZE).get().await()
        val messages = snap.documents.mapNotNull { it.toMessageOrNull() }.reversed()
        Pair(sortMessages(messages), if (snap.documents.size >= PAGE_SIZE) snap.documents.lastOrNull() else null)
    } catch (e: Exception) {
        Pair(emptyList(), null)
    }

    fun membersFlow(chatId: String): Flow<List<Member>> = callbackFlow {
        val reg = db.collection("chats").document(chatId).collection("members")
            .addSnapshotListener { snap, _ ->
                val members = snap?.documents?.mapNotNull { doc ->
                    try { doc.toObject(Member::class.java)?.copy(uid = doc.id) } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(members)
            }
        awaitClose { reg.remove() }
    }

    suspend fun getMyMemberData(chatId: String): Member? = try {
        val snap = db.collection("chats").document(chatId).collection("members").document(currentUid).get().await()
        snap.toObject(Member::class.java)?.copy(uid = snap.id)
    } catch (e: Exception) { null }

    fun pendingInvitesFlow(uid: String): Flow<List<GroupInvite>> = callbackFlow {
        val reg = db.collection("invites").whereEqualTo("invitedUid", uid).whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                val invites = snap?.documents?.mapNotNull { doc ->
                    try { doc.toObject(GroupInvite::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(invites)
            }
        awaitClose { reg.remove() }
    }

    private val dismissedNotificationIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun notificationsFlow(uid: String): Flow<List<AppNotification>> = callbackFlow {
        val reg = db.collection("users").document(uid).collection("notifications").whereEqualTo("read", false)
            .addSnapshotListener { snap, _ ->
                val notifs = snap?.documents?.mapNotNull { doc ->
                    if (dismissedNotificationIds.contains(doc.id)) return@mapNotNull null
                    try { doc.toObject(AppNotification::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(notifs)
            }
        awaitClose { reg.remove() }
    }

    suspend fun findOrCreateChat(currentUserProfile: UserProfile, targetUid: String): String = try {
        // Чат создаёт сервер: возвращает существующий или новый (NOT_FOUND — нет пользователя, INVALID_ARGUMENT — сам с собой)
        val result = functions.getHttpsCallable("createDirectChat").call(mapOf("targetUid" to targetUid)).await()
        (result.data as? Map<*, *>)?.get("chatId") as? String ?: throw Exception("createDirectChat: пустой ответ")
    } catch (e: Exception) {
        // Fallback for offline: if we already have this chat in cache, return it
        val chatId = getChatId(currentUserProfile.uid, targetUid)
        val cached = ChatDataCache.loadChatList(context, currentUid)
        if (cached.any { it.id == chatId }) chatId else throw e
    }

    suspend fun createChat(type: String, name: String, tag: String, description: String = ""): Pair<String, String> {
        val result = functions.getHttpsCallable("createChat").call(mapOf("type" to type, "name" to name, "tag" to tag, "description" to description)).await()
        val data = result.data as Map<*, *>
        return Pair(data["chatId"] as String, data["inviteLink"] as String)
    }

    suspend fun findByTag(tag: String): TagSearchResult {
        val result = functions.getHttpsCallable("findByTag").call(mapOf("tag" to tag)).await()
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

    suspend fun joinChannel(chatId: String, tag: String? = null): Boolean {
        return try {
            val res = functions.getHttpsCallable("joinChannel").call(mapOf("chatId" to chatId)).await()
            val data = res.data as? Map<*, *>
            (data?.get("success") as? Boolean) == true || (data?.get("alreadyMember") as? Boolean) == true
        } catch (e: Exception) {
            val cleanTag = tag?.removePrefix("@")?.trim()
            if (!cleanTag.isNullOrBlank()) {
                try {
                    joinByTag(cleanTag)
                    true
                } catch (_: Exception) {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    suspend fun joinByTag(tag: String): String {
        val result = functions.getHttpsCallable("joinByTag").call(mapOf("tag" to tag)).await()
        return (result.data as Map<*, *>)["chatId"] as String
    }

    suspend fun joinByInvite(token: String): Pair<String, String> {
        val result = functions.getHttpsCallable("joinByInvite").call(mapOf("token" to token)).await()
        val data = result.data as Map<*, *>
        return Pair(data["chatId"] as String, data["chatType"] as? String ?: "group")
    }

    suspend fun inviteUser(chatId: String, username: String) {
        functions.getHttpsCallable("inviteUser").call(mapOf("chatId" to chatId, "targetUsername" to username)).await()
    }

    suspend fun dismissNotification(notificationId: String, uid: String? = null, inviteId: String? = null) {
        dismissedNotificationIds.add(notificationId)
        val effectiveUid = uid?.ifEmpty { currentUid } ?: currentUid

        if (effectiveUid.isNotBlank()) {
            val userNotifs = db.collection("users").document(effectiveUid).collection("notifications")
            try {
                userNotifs.document(notificationId).update("read", true).await()
            } catch (_: Exception) {
                try {
                    userNotifs.document(notificationId).delete().await()
                } catch (_: Exception) {}
            }

            if (!inviteId.isNullOrBlank()) {
                try {
                    val query = userNotifs
                        .whereEqualTo("inviteId", inviteId)
                        .whereEqualTo("read", false)
                        .get().await()
                    for (doc in query.documents) {
                        dismissedNotificationIds.add(doc.id)
                        try { doc.reference.update("read", true).await() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun dismissNotificationByInviteId(inviteId: String, uid: String) {
        if (inviteId.isBlank() || uid.isBlank()) return
        try {
            val userNotifs = db.collection("users").document(uid).collection("notifications")
            val query = userNotifs
                .whereEqualTo("inviteId", inviteId)
                .whereEqualTo("read", false)
                .get().await()
            for (doc in query.documents) {
                dismissedNotificationIds.add(doc.id)
                try { doc.reference.update("read", true).await() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    suspend fun respondToInvite(
        inviteId: String,
        accept: Boolean,
        notificationId: String? = null,
        uid: String? = null
    ): String? {
        val effectiveUid = uid?.ifEmpty { currentUid } ?: currentUid
        if (!notificationId.isNullOrBlank()) {
            dismissNotification(notificationId, effectiveUid, inviteId)
        }
        val result = functions.getHttpsCallable("respondToInvite").call(mapOf("inviteId" to inviteId, "accept" to accept)).await()
        if (effectiveUid.isNotBlank()) {
            dismissNotificationByInviteId(inviteId, effectiveUid)
        }
        return (result.data as Map<*, *>)["chatId"] as? String
    }

    suspend fun leaveChat(chatId: String) {
        functions.getHttpsCallable("leaveChat").call(mapOf("chatId" to chatId)).await()
    }

    suspend fun moderateUser(chatId: String, targetUid: String, action: String, durationMinutes: Int? = null) {
        functions.getHttpsCallable("moderateUser").call(mapOf("chatId" to chatId, "targetUid" to targetUid, "action" to action, "durationMinutes" to durationMinutes)).await()
    }

    suspend fun setMemberRole(chatId: String, targetUid: String, role: String) {
        functions.getHttpsCallable("setMemberRole").call(mapOf("chatId" to chatId, "targetUid" to targetUid, "role" to role)).await()
    }

    suspend fun updateChatSettings(chatId: String, params: Map<String, Any?>) {
        functions.getHttpsCallable("updateChatSettings").call(params + mapOf("chatId" to chatId)).await()
    }

    suspend fun regenerateInviteLink(chatId: String): String {
        val result = functions.getHttpsCallable("regenerateInviteLink").call(mapOf("chatId" to chatId)).await()
        return (result.data as Map<*, *>)["inviteLink"] as String
    }


    suspend fun setForumMode(chatId: String, isForum: Boolean) {
        try {
            db.collection("chats").document(chatId).update("settings.isForum", isForum).await()
        } catch (_: Exception) {
            try {
                db.collection("chats").document(chatId).update("isForum", isForum).await()
            } catch (_: Exception) {}
        }
    }

    private fun buildChatUpdates(
        previewText: String,
        currentUid: String,
        senderUsername: String,
        nextSeq: Long?,
        isDirect: Boolean,
        otherUserId: String?,
        // lastMessage.type: по нему клиенты строят локализованное превью (спека подписей §1.2)
        type: String? = null
    ): Map<String, Any> {
        val lastMessageMap = hashMapOf<String, Any>(
            "text" to previewText,
            "senderId" to currentUid,
            "senderUsername" to senderUsername,
            "readBy" to listOf(currentUid)
        )
        if (type != null) lastMessageMap["type"] = type
        val updates = hashMapOf<String, Any>(
            "lastMessage" to lastMessageMap,
            "lastMessageSenderId" to currentUid,
            "lastMessageAt" to FieldValue.serverTimestamp()
        )
        if (nextSeq != null && nextSeq > 0) {
            updates["lastSeq"] = nextSeq
        }
        if (isDirect && !otherUserId.isNullOrBlank()) {
            updates["unreadCount.$otherUserId"] = FieldValue.increment(1)
        }
        return updates
    }

    suspend fun sendText(chatId: String, text: String, senderUsername: String, replyTo: ReplyData?, topicId: String? = null, nextSeq: Long? = null, isDirect: Boolean = false, otherUserId: String? = null): Message? = withContext(Dispatchers.IO) {
        val data = JSONObject().apply {
            put("text", text)
            put("senderUsername", senderUsername)
            if (topicId != null) put("topicId", topicId)
            replyTo?.let { put("replyTo", JSONObject(it.toMap())) }
            if (nextSeq != null) put("nextSeq", nextSeq)
            put("isDirect", isDirect)
            if (otherUserId != null) put("otherUserId", otherUserId)
        }
        val outboxId = ChatDataCache.addToOutbox(context, chatId, "text", data)
        null
    }

    suspend fun sendTextNow(id: String, chatId: String, text: String, senderUsername: String, replyTo: ReplyData?, topicId: String? = null, nextSeq: Long? = null, isDirect: Boolean = false, otherUserId: String? = null) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(id)
        val userRef = db.collection("users").document(currentUid)

        val msgData = mutableMapOf<String, Any?>(
            "senderId"       to currentUid,
            "senderUsername" to senderUsername,
            "type"           to MessageType.TEXT,
            "text"           to text,
            "createdAt"      to FieldValue.serverTimestamp(),
            "deleted"        to false,
            "reactions"      to emptyList<Any>(),
            "readBy"         to listOf(currentUid),
            "replyTo"        to replyTo?.toMap()
        )
        if (nextSeq != null && nextSeq > 0) {
            msgData["seq"] = nextSeq
        }
        if (topicId != null) {
            msgData["topicId"] = topicId
        }

        val batch  = db.batch()
        batch.set(msgRef, msgData)
        batch.update(db.collection("chats").document(chatId), buildChatUpdates(text, currentUid, senderUsername, nextSeq, isDirect, otherUserId))
        if (topicId != null) {
            val topicRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            batch.update(topicRef, mapOf(
                "lastMessage" to mapOf("text" to text, "senderUsername" to senderUsername),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.set(userRef, mapOf("lastMessageAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        batch.commit().await()
    }

    suspend fun sendImage(chatId: String, uri: Uri, senderUsername: String, replyTo: ReplyData?, isSpoiler: Boolean = false, caption: String? = null, topicId: String? = null, nextSeq: Long? = null, isDirect: Boolean = false, otherUserId: String? = null) = withContext(Dispatchers.IO) {
        val rawName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "image"
        val safeName = "img_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}_${rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.jpg"
        val tempFile = File(context.cacheDir, safeName)
        val bytesCopied = context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: 0L
        if (bytesCopied == 0L || !tempFile.exists()) {
            throw IllegalStateException("Failed to read image stream from $uri")
        }
        
        val data = JSONObject().apply {
            put("localPath", tempFile.absolutePath)
            put("senderUsername", senderUsername)
            put("isSpoiler", isSpoiler)
            if (caption != null) put("caption", caption)
            if (topicId != null) put("topicId", topicId)
            replyTo?.let { put("replyTo", JSONObject(it.toMap())) }
            if (nextSeq != null) put("nextSeq", nextSeq)
            put("isDirect", isDirect)
            if (otherUserId != null) put("otherUserId", otherUserId)
        }
        ChatDataCache.addToOutbox(context, chatId, "image", data)
    }

    suspend fun sendImageNow(
        id: String,
        chatId: String,
        mediaId: String,
        fileName: String,
        senderUsername: String,
        replyTo: ReplyData?,
        isSpoiler: Boolean,
        caption: String? = null,
        topicId: String? = null,
        nextSeq: Long? = null,
        isDirect: Boolean = false,
        otherUserId: String? = null,
        driveFileId: String? = null,
        driveUrl: String? = null,
        previewUrl: String? = null
    ) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(id)
        val userRef = db.collection("users").document(currentUid)

        val extra = mutableMapOf<String, Any?>(
            "type"       to MessageType.IMAGE,
            "fileName"   to fileName
        )
        if (!mediaId.isNullOrEmpty()) extra["cdnMediaId"] = mediaId
        if (!driveFileId.isNullOrEmpty()) extra["driveFileId"] = driveFileId
        if (!driveUrl.isNullOrEmpty()) {
            extra["driveUrl"] = driveUrl
            extra["url"] = driveUrl
        }
        if (!previewUrl.isNullOrEmpty()) extra["previewUrl"] = previewUrl
        if (isSpoiler) extra["spoiler"] = true
        if (!caption.isNullOrBlank()) extra["caption"] = caption

        val msg = mutableMapOf<String, Any?>(
            "senderId"       to currentUid,
            "senderUsername" to senderUsername,
            "createdAt"      to FieldValue.serverTimestamp(),
            "deleted"        to false,
            "reactions"      to emptyList<Any>(),
            "readBy"         to listOf(currentUid),
            "replyTo"        to replyTo?.toMap()
        )
        if (nextSeq != null && nextSeq > 0) msg["seq"] = nextSeq
        if (topicId != null) msg["topicId"] = topicId
        msg.putAll(extra)

        val batch = db.batch()
        batch.set(msgRef, msg)
        val preview = caption?.takeIf { it.isNotBlank() } ?: "📷 Фото"
        batch.update(db.collection("chats").document(chatId), buildChatUpdates(preview, currentUid, senderUsername, nextSeq, isDirect, otherUserId, MessageType.IMAGE))
        if (topicId != null) {
            val topicRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            batch.update(topicRef, mapOf(
                "lastMessage" to mapOf("text" to preview, "senderUsername" to senderUsername),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.set(userRef, mapOf("lastMessageAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        batch.commit().await()
    }

    suspend fun sendVoice(
        chatId: String,
        file: File,
        durationSec: Int,
        senderUsername: String,
        replyTo: ReplyData?,
        topicId: String? = null,
        nextSeq: Long? = null,
        isDirect: Boolean = false,
        otherUserId: String? = null
    ) = withContext(Dispatchers.IO) {
        val data = JSONObject().apply {
            put("localPath", file.absolutePath)
            put("duration", durationSec)
            put("senderUsername", senderUsername)
            if (topicId != null) put("topicId", topicId)
            replyTo?.let { put("replyTo", JSONObject(it.toMap())) }
            if (nextSeq != null) put("nextSeq", nextSeq)
            put("isDirect", isDirect)
            if (otherUserId != null) put("otherUserId", otherUserId)
        }
        ChatDataCache.addToOutbox(context, chatId, "voice", data)
    }

    suspend fun sendVoiceNow(
        id: String,
        chatId: String,
        mediaId: String,
        durationSec: Int,
        senderUsername: String,
        replyTo: ReplyData?,
        topicId: String? = null,
        nextSeq: Long? = null,
        isDirect: Boolean = false,
        otherUserId: String? = null,
        driveFileId: String? = null,
        driveUrl: String? = null
    ) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(id)
        val userRef = db.collection("users").document(currentUid)

        val extra = mutableMapOf<String, Any?>(
            "type"       to MessageType.VOICE,
            "duration"   to durationSec
        )
        if (!mediaId.isNullOrEmpty()) extra["cdnMediaId"] = mediaId
        if (!driveFileId.isNullOrEmpty()) extra["driveFileId"] = driveFileId
        if (!driveUrl.isNullOrEmpty()) {
            extra["driveUrl"] = driveUrl
            extra["url"] = driveUrl
        }

        val msg = mutableMapOf<String, Any?>(
            "senderId"       to currentUid,
            "senderUsername" to senderUsername,
            "createdAt"      to FieldValue.serverTimestamp(),
            "deleted"        to false,
            "reactions"      to emptyList<Any>(),
            "readBy"         to listOf(currentUid),
            "replyTo"        to replyTo?.toMap()
        )
        if (nextSeq != null && nextSeq > 0) msg["seq"] = nextSeq
        if (topicId != null) msg["topicId"] = topicId
        msg.putAll(extra)

        val batch = db.batch()
        batch.set(msgRef, msg)
        batch.update(db.collection("chats").document(chatId), buildChatUpdates("🎤 Voice message", currentUid, senderUsername, nextSeq, isDirect, otherUserId))
        if (topicId != null) {
            val topicRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            batch.update(topicRef, mapOf(
                "lastMessage" to mapOf("text" to "🎤 Voice message", "senderUsername" to senderUsername),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.set(userRef, mapOf("lastMessageAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        batch.commit().await()
    }

    suspend fun sendAudio(
        chatId: String,
        file: File,
        title: String,
        performer: String,
        durationSec: Int,
        coverFile: File?,
        senderUsername: String,
        replyTo: ReplyData?,
        topicId: String? = null,
        nextSeq: Long? = null,
        isDirect: Boolean = false,
        otherUserId: String? = null
    ) = withContext(Dispatchers.IO) {
        val data = JSONObject().apply {
            put("localPath", file.absolutePath)
            put("title", title)
            put("performer", performer)
            put("duration", durationSec)
            coverFile?.let { put("coverLocalPath", it.absolutePath) }
            put("senderUsername", senderUsername)
            if (topicId != null) put("topicId", topicId)
            replyTo?.let { put("replyTo", JSONObject(it.toMap())) }
            if (nextSeq != null) put("nextSeq", nextSeq)
            put("isDirect", isDirect)
            if (otherUserId != null) put("otherUserId", otherUserId)
        }
        ChatDataCache.addToOutbox(context, chatId, "audio", data)
    }

    suspend fun sendAudioNow(
        id: String,
        chatId: String,
        mediaId: String,
        fileName: String,
        fileSize: Long,
        title: String,
        performer: String,
        durationSec: Int,
        coverMediaId: String?,
        senderUsername: String,
        replyTo: ReplyData?,
        topicId: String? = null,
        nextSeq: Long? = null,
        isDirect: Boolean = false,
        otherUserId: String? = null,
        driveFileId: String? = null,
        driveUrl: String? = null
    ) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(id)
        val userRef = db.collection("users").document(currentUid)

        val extra = mutableMapOf<String, Any?>(
            "type" to MessageType.AUDIO,
            "fileName" to fileName,
            "fileSize" to fileSize,
            "title" to title,
            "performer" to performer,
            "duration" to durationSec
        )
        if (!mediaId.isNullOrEmpty()) extra["cdnMediaId"] = mediaId
        if (!driveFileId.isNullOrEmpty()) extra["driveFileId"] = driveFileId
        if (!driveUrl.isNullOrEmpty()) {
            extra["driveUrl"] = driveUrl
            extra["url"] = driveUrl
        }
        if (coverMediaId != null) {
            extra["coverCdnMediaId"] = coverMediaId
        }

        val msg = mutableMapOf<String, Any?>(
            "senderId" to currentUid,
            "senderUsername" to senderUsername,
            "createdAt" to FieldValue.serverTimestamp(),
            "deleted" to false,
            "reactions" to emptyList<Any>(),
            "readBy" to listOf(currentUid),
            "replyTo" to replyTo?.toMap()
        )
        if (nextSeq != null && nextSeq > 0) msg["seq"] = nextSeq
        if (topicId != null) msg["topicId"] = topicId
        msg.putAll(extra)

        val batch = db.batch()
        batch.set(msgRef, msg)
        val snippet = if (title.isNotBlank()) "🎵 $title" else "🎵 Аудиозапись"
        batch.update(db.collection("chats").document(chatId), buildChatUpdates(snippet, currentUid, senderUsername, nextSeq, isDirect, otherUserId))
        if (topicId != null) {
            val topicRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            batch.update(topicRef, mapOf(
                "lastMessage" to mapOf("text" to snippet, "senderUsername" to senderUsername),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.set(userRef, mapOf("lastMessageAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        batch.commit().await()
    }

    suspend fun sendVideo(
        chatId: String,
        uri: Uri,
        senderUsername: String,
        replyTo: ReplyData?,
        topicId: String? = null,
        nextSeq: Long? = null,
        isDirect: Boolean = false,
        otherUserId: String? = null
    ) = withContext(Dispatchers.IO) {
        val fileName = "${System.currentTimeMillis()}_${uri.lastPathSegment ?: "video.mp4"}"
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }

        val data = JSONObject().apply {
            put("localPath", tempFile.absolutePath)
            put("senderUsername", senderUsername)
            if (topicId != null) put("topicId", topicId)
            replyTo?.let { put("replyTo", JSONObject(it.toMap())) }
            if (nextSeq != null) put("nextSeq", nextSeq)
            put("isDirect", isDirect)
            if (otherUserId != null) put("otherUserId", otherUserId)
        }
        ChatDataCache.addToOutbox(context, chatId, "video", data)
    }

    suspend fun sendVideoNow(
        id: String,
        chatId: String,
        mediaId: String,
        fileName: String,
        senderUsername: String,
        replyTo: ReplyData?,
        topicId: String? = null,
        nextSeq: Long? = null,
        isDirect: Boolean = false,
        otherUserId: String? = null,
        duration: Int? = null,
        width: Int? = null,
        height: Int? = null,
        thumbUrl: String? = null,
        driveFileId: String? = null,
        driveUrl: String? = null,
        previewUrl: String? = null
    ) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(id)
        val userRef = db.collection("users").document(currentUid)

        val extra = mutableMapOf<String, Any?>(
            "type"       to MessageType.VIDEO,
            "fileName"   to fileName
        )
        if (!mediaId.isNullOrEmpty()) extra["cdnMediaId"] = mediaId
        if (!driveFileId.isNullOrEmpty()) extra["driveFileId"] = driveFileId
        if (!driveUrl.isNullOrEmpty()) {
            extra["driveUrl"] = driveUrl
            extra["url"] = driveUrl
        }
        if (!previewUrl.isNullOrEmpty()) extra["previewUrl"] = previewUrl
        if (duration != null && duration > 0) extra["duration"] = duration
        if (width != null && width > 0) extra["width"] = width
        if (height != null && height > 0) extra["height"] = height
        if (!thumbUrl.isNullOrBlank()) extra["thumbUrl"] = thumbUrl

        val msg = mutableMapOf<String, Any?>(
            "senderId"       to currentUid,
            "senderUsername" to senderUsername,
            "createdAt"      to FieldValue.serverTimestamp(),
            "deleted"        to false,
            "reactions"      to emptyList<Any>(),
            "readBy"         to listOf(currentUid),
            "replyTo"        to replyTo?.toMap()
        )
        if (nextSeq != null && nextSeq > 0) msg["seq"] = nextSeq
        if (topicId != null) msg["topicId"] = topicId
        msg.putAll(extra)

        val batch = db.batch()
        batch.set(msgRef, msg)
        batch.update(db.collection("chats").document(chatId), buildChatUpdates("🎥 Video", currentUid, senderUsername, nextSeq, isDirect, otherUserId))
        if (topicId != null) {
            val topicRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            batch.update(topicRef, mapOf(
                "lastMessage" to mapOf("text" to "🎥 Video", "senderUsername" to senderUsername),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.set(userRef, mapOf("lastMessageAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        batch.commit().await()
    }

    suspend fun sendSticker(
        chatId: String,
        sticker: StickerItem,
        packId: String,
        packName: String,
        packEmoji: String,
        senderUsername: String,
        replyTo: ReplyData?,
        topicId: String? = null,
        nextSeq: Long? = null,
        isDirect: Boolean = false,
        otherUserId: String? = null
    ) {
        val data = JSONObject().apply {
            put("stickerId", sticker.id)
            put("url", sticker.url)
            put("packId", packId)
            put("packName", packName)
            put("packEmoji", packEmoji)
            StickerFormat.forMessage(sticker.effectiveFormat)?.let { put("stickerFormat", it) }
            sticker.previewUrl?.let { put("stickerPreviewUrl", it) }
            put("senderUsername", senderUsername)
            if (topicId != null) put("topicId", topicId)
            replyTo?.let { put("replyTo", JSONObject(it.toMap())) }
            if (nextSeq != null) put("nextSeq", nextSeq)
            put("isDirect", isDirect)
            if (otherUserId != null) put("otherUserId", otherUserId)
        }
        ChatDataCache.addToOutbox(context, chatId, "sticker", data)
    }

    suspend fun sendStickerNow(
        id: String,
        chatId: String,
        stickerId: String,
        url: String,
        packId: String,
        packName: String,
        packEmoji: String,
        stickerFormat: String?,
        stickerPreviewUrl: String?,
        senderUsername: String,
        replyTo: ReplyData?,
        topicId: String? = null,
        nextSeq: Long? = null,
        isDirect: Boolean = false,
        otherUserId: String? = null
    ) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(id)
        val userRef = db.collection("users").document(currentUid)

        val extra = mapOf(
            "type"      to MessageType.STICKER,
            "url"       to url,
            "stickerId" to stickerId,
            "packId"    to packId,
            "packName"  to packName,
            "packEmoji" to packEmoji,
            "stickerFormat" to stickerFormat,
            "stickerPreviewUrl" to stickerPreviewUrl
        )

        val msg = mutableMapOf<String, Any?>(
            "senderId"       to currentUid,
            "senderUsername" to senderUsername,
            "createdAt"      to FieldValue.serverTimestamp(),
            "deleted"        to false,
            "reactions"      to emptyList<Any>(),
            "readBy"         to listOf(currentUid),
            "replyTo"        to replyTo?.toMap()
        )
        if (nextSeq != null && nextSeq > 0) msg["seq"] = nextSeq
        if (topicId != null) msg["topicId"] = topicId
        msg.putAll(extra)

        val batch = db.batch()
        batch.set(msgRef, msg)
        batch.update(db.collection("chats").document(chatId), buildChatUpdates("$packEmoji Sticker", currentUid, senderUsername, nextSeq, isDirect, otherUserId))
        if (topicId != null) {
            val topicRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            batch.update(topicRef, mapOf(
                "lastMessage" to mapOf("text" to "$packEmoji Sticker", "senderUsername" to senderUsername),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.set(userRef, mapOf("lastMessageAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        batch.commit().await()
    }

    suspend fun uploadAlbumImages(
        chatId: String,
        items: List<AlbumImageLocal>,
        onProgress: ((Float) -> Unit)? = null
    ): List<AlbumImage> = coroutineScope {
        val total = items.size.coerceAtLeast(1)
        val progressMap = java.util.concurrent.ConcurrentHashMap<Int, Float>()

        val token = googleDriveAuthManager?.getValidAccessToken()
            ?: throw org.visorlink.app.utils.DriveNotConnectedException()
        val folderId = googleDriveMediaService?.getOrCreateVisorLinkFolder(token)
            ?: throw IllegalStateException("Не удалось получить папку Google Drive.")

        items.mapIndexed { index, item ->
            async(Dispatchers.IO) {
                val rawName = item.uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "photo"
                val safeName = "album_${System.currentTimeMillis()}_${index}_${rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.jpg"
                val tempFile = File(context.cacheDir, safeName)

                try {
                    val bytesCopied = context.contentResolver.openInputStream(item.uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    } ?: 0L

                    if (bytesCopied == 0L || !tempFile.exists()) {
                        throw IllegalStateException("Failed to read image from uri: ${item.uri}")
                    }

                    val uploadResult = googleDriveMediaService.uploadMediaFile(
                        accessToken = token,
                        folderId = folderId,
                        file = tempFile,
                        mimeType = "image/jpeg",
                        onProgress = { p ->
                            progressMap[index] = p
                            val sum = progressMap.values.sum()
                            onProgress?.invoke((sum / total).coerceIn(0f, 1f))
                        }
                    )
                    AlbumImage(
                        url = uploadResult.directUrl,
                        driveFileId = uploadResult.fileId,
                        previewUrl = uploadResult.previewUrl,
                        fileName = safeName,
                        spoiler = item.spoiler
                    )
                } finally {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            }
        }.awaitAll()
    }

    suspend fun sendAlbum(chatId: String, images: List<AlbumImage>, caption: String?, replyTo: ReplyData?): String {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document()
        val userRef = db.collection("users").document(currentUid)

        val msg = mutableMapOf<String, Any?>(
            "senderId"       to currentUid,
            "senderUsername" to (auth.currentUser?.displayName ?: ""),
            "type"           to MessageType.ALBUM,
            "images"         to images.map { it.toMap() },
            "caption"        to caption,
            "createdAt"      to FieldValue.serverTimestamp(),
            "deleted"        to false,
            "reactions"      to emptyList<Any>(),
            "readBy"         to listOf(currentUid),
            "replyTo"        to replyTo?.toMap()
        )

        val batch = db.batch()
        batch.set(msgRef, msg)
        batch.update(db.collection("chats").document(chatId), mapOf(
            "lastMessage" to mapOf(
                "type" to MessageType.ALBUM,
                "text" to (caption?.ifBlank { null } ?: "📷 ${images.size} фото"),
                "senderId" to currentUid,
                "senderUsername" to (auth.currentUser?.displayName ?: ""),
                "readBy" to listOf(currentUid)
            ),
            "lastMessageSenderId" to currentUid,
            "lastMessageAt" to FieldValue.serverTimestamp()
        ))
        batch.set(userRef, mapOf("lastMessageAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        batch.commit().await()
        return msgRef.id
    }

    private suspend fun sendExtra(chatId: String, extra: Map<String, Any?>, preview: String, senderUsername: String, replyTo: ReplyData?) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document()
        val userRef = db.collection("users").document(currentUid)

        val msg = mutableMapOf<String, Any?>(
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
        batch.update(db.collection("chats").document(chatId), mapOf("lastMessage" to preview, "lastMessageAt" to FieldValue.serverTimestamp()))
        batch.set(userRef, mapOf("lastMessageAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        batch.commit().await()
    }

    suspend fun cancelSending(id: String) {
        withContext(Dispatchers.IO) {
            org.visorlink.app.utils.OutboxManager.cancelInFlight(id)
            ChatDataCache.cleanupOutbox(context, listOf(id))
        }
    }

    suspend fun markMessagesAsRead(chatId: String, messages: List<Message>, uid: String, hideReads: Boolean = false) {
        if (hideReads) {
            resetUnreadCount(chatId, uid, hideReads = true)
            return
        }
        val unread = messages.filter { it.senderId != uid && !it.readBy.contains(uid) && !it.deleted }
        if (unread.isEmpty()) return
        val batch = db.batch()
        for (msg in unread) {
            val ref = db.collection("chats").document(chatId).collection("messages").document(msg.id)
            batch.update(ref, "readBy", FieldValue.arrayUnion(uid))
        }
        val chatRef = db.collection("chats").document(chatId)
        val chatUpdates = hashMapOf<String, Any>(
            "unreadCount.$uid" to 0L,
            "lastMessage.readBy" to FieldValue.arrayUnion(uid)
        )
        batch.update(chatRef, chatUpdates)
        batch.commit().await()
    }

    suspend fun resetUnreadCount(chatId: String, uid: String, hideReads: Boolean = false) {
        if (uid.isEmpty() || chatId.isEmpty()) return
        try {
            val chatUpdates = hashMapOf<String, Any>(
                "unreadCount.$uid" to 0L
            )
            if (!hideReads) {
                chatUpdates["lastMessage.readBy"] = FieldValue.arrayUnion(uid)
            }
            db.collection("chats").document(chatId).update(chatUpdates).await()
        } catch (_: Exception) {}
    }

    suspend fun deleteMessage(chatId: String, messageId: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update(mapOf("deleted" to true, "deletedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun editMessage(chatId: String, messageId: String, newText: String, oldText: String, isCaption: Boolean = false) {
        val field = if (isCaption) "caption" else "text"

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        
        val historyItem = mapOf(
            // Как в вебе: прошлое значение всегда под ключом text, и для подписи тоже
            "text" to oldText,
            "editedAt" to isoFormat.format(Date())
        )

        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update(mapOf(
                field to newText,
                "lastEdited" to FieldValue.serverTimestamp(),
                "editHistory" to FieldValue.arrayUnion(historyItem)
            )).await()
    }

    suspend fun toggleReaction(chatId: String, messageId: String, emoji: String, currentReactions: List<Reaction>) {
        val ref = db.collection("chats").document(chatId).collection("messages").document(messageId)
        val existing = currentReactions.find { it.emoji == emoji }
        val updated  = if (existing != null) {
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

    fun listenComments(chatId: String, messageId: String, onUpdate: (List<Comment>) -> Unit): ListenerRegistration {
        return db.collection("chats").document(chatId).collection("messages").document(messageId)
            .collection("comments").orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val comments = snap.documents.mapNotNull { doc -> try { doc.toObject(Comment::class.java)?.copy(id = doc.id) } catch (_: Exception) { null } }
                onUpdate(comments)
            }
    }

    fun listenPost(chatId: String, messageId: String, onUpdate: (Message) -> Unit): ListenerRegistration {
        return db.collection("chats").document(chatId).collection("messages").document(messageId)
            .addSnapshotListener { snap, _ -> snap?.toMessageOrNull()?.let(onUpdate) }
    }

    suspend fun addComment(chatId: String, messageId: String, type: String, text: String? = null, cdnMediaId: String? = null, url: String? = null, fileName: String? = null, duration: Int? = null, spoiler: Boolean = false, replyTo: CommentReplyData? = null): String {
        val data = buildMap<String, Any?> {
            put("chatId", chatId); put("messageId", messageId); put("type", type)
            if (text != null) put("text", text)
            if (cdnMediaId != null) put("cdnMediaId", cdnMediaId)
            if (url != null) put("url", url)
            if (fileName != null) put("fileName", fileName)
            if (duration != null) put("duration", duration)
            put("spoiler", spoiler)
            if (replyTo != null) put("replyTo", replyTo.toMap())
        }
        val result = functions.getHttpsCallable("addComment").call(data).await()
        try { db.collection("users").document(currentUid).update("lastMessageAt", FieldValue.serverTimestamp()).await() } catch (_: Exception) {}
        return (result.data as Map<*, *>)["commentId"] as String
    }

    suspend fun togglePostComments(chatId: String, messageId: String, enabled: Boolean) {
        functions.getHttpsCallable("togglePostComments").call(mapOf("chatId" to chatId, "messageId" to messageId, "enabled" to enabled)).await()
    }


    suspend fun toggleCommentReaction(chatId: String, messageId: String, commentId: String, emoji: String, currentReactions: List<Reaction>) {
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
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .collection("comments").document(commentId)
            .update("reactions", updated.map { it.toMap() }).await()
    }

    suspend fun deleteComment(chatId: String, messageId: String, commentId: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .collection("comments").document(commentId)
            .update(mapOf("deleted" to true, "deletedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun uploadAndCommentImage(chatId: String, messageId: String, file: Uri, spoiler: Boolean, replyTo: CommentReplyData? = null) = withContext(Dispatchers.IO) {
        val compressed = org.visorlink.app.utils.ImageCompressor.compress(context, file)
        val fileName = "${System.currentTimeMillis()}_image.${compressed.extension}"
        val tempFile = File(context.cacheDir, fileName)
        tempFile.writeBytes(compressed.bytes)
        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("comments/$chatId/$fileName")
        storageRef.putFile(Uri.fromFile(tempFile)).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()
        tempFile.delete()
        addComment(chatId = chatId, messageId = messageId, type = MessageType.IMAGE, url = downloadUrl, fileName = fileName, spoiler = spoiler, replyTo = replyTo)
    }

    suspend fun uploadAndCommentVoice(chatId: String, messageId: String, audioFile: File, durationSeconds: Int, replyTo: CommentReplyData? = null) = withContext(Dispatchers.IO) {
        val fileName = "${System.currentTimeMillis()}_voice.webm"
        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("comments/$chatId/$fileName")
        storageRef.putFile(Uri.fromFile(audioFile)).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()
        addComment(chatId = chatId, messageId = messageId, type = MessageType.VOICE, url = downloadUrl, duration = durationSeconds, replyTo = replyTo)
    }

    /**
     * Зашифрованная синхронизация истории аварийного чата в защищенный Vault пользователя в Firestore.
     * Коллекция users/{uid}/emergency_vault/{chatId} защищена правилами Firestore (доступ только auth.uid == uid).
     * Шифрование — ключом контента (CK, TODO §14.3); старые v1-документы тихо пере-шифровываются.
     */
    suspend fun syncEmergencyVault(chatId: String): Boolean = withContext(Dispatchers.IO) {
        if (!chatId.startsWith("emer_") || currentUid.isBlank()) return@withContext false
        try {
            val messages = ChatDataCache.loadMessages(context, chatId)
            if (messages.isEmpty()) return@withContext false

            val jsonArray = org.json.JSONArray()
            for (msg in messages) {
                jsonArray.put(ChatDataCache.messageToJson(msg))
            }

            // Тихая миграция v1 → v2: старый ключ вычислим в приложении (TODO §14.4)
            val vaultRef = db.collection("users").document(currentUid)
                .collection("emergency_vault").document(chatId)
            val existing = try { vaultRef.get().await() } catch (_: Exception) { null }
            if (existing != null && existing.exists() && existing.getLong("keyVersion") != 2L) {
                val oldPayload = existing.getString("payload")
                val oldIv = existing.getString("iv")
                if (oldPayload != null && oldIv != null) {
                    val legacyVaultKey = deriveKey("vl_vault_user_salt", currentUid)
                    val plain = decryptText(oldPayload, oldIv, legacyVaultKey)
                    if (plain != null) {
                        val ck = contentKeyManager.getOrCreateCK(currentUid, org.visorlink.app.utils.ContentKeyManager.DOMAIN_VAULT)
                        val (ct, iv) = encryptText(plain, ck)
                        vaultRef.set(
                            mapOf(
                                "chatId" to chatId, "payload" to ct, "iv" to iv, "keyVersion" to 2L,
                                "messageCount" to (existing.getLong("messageCount") ?: 0L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }

            // Шифруем данные ключом контента (CK)
            val vaultKey = contentKeyManager.getOrCreateCK(currentUid, org.visorlink.app.utils.ContentKeyManager.DOMAIN_VAULT)
            val (encryptedPayload, iv) = encryptText(jsonArray.toString(), vaultKey)

            val vaultDoc = hashMapOf<String, Any>(
                "chatId" to chatId,
                "payload" to encryptedPayload,
                "iv" to iv,
                "keyVersion" to 2L,
                "messageCount" to messages.size,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            vaultRef
                .set(vaultDoc)
                .await()
            Log.d("ChatRepo", "Successfully synced emergency vault for $chatId (${messages.size} msgs)")
            true
        } catch (e: Exception) {
            Log.w("ChatRepo", "Failed to sync emergency vault for $chatId: ${e.message}")
            false
        }
    }

    /**
     * Восстановление аварийных чатов и сообщений из персонального зашифрованного Vault в Firestore
     * (например, после чистки кэша или смены устройства).
     */
    suspend fun restoreEmergencyVault(): Int = withContext(Dispatchers.IO) {
        if (currentUid.isBlank()) return@withContext 0
        try {
            val snapshot = db.collection("users").document(currentUid)
                .collection("emergency_vault")
                .get()
                .await()

            if (snapshot.isEmpty) return@withContext 0

            // v2 — ключ контента; v1 — legacy-ключ (окно миграции, тихо обновится при следующем sync)
            val ck = contentKeyManager.getOrCreateCK(currentUid, org.visorlink.app.utils.ContentKeyManager.DOMAIN_VAULT)
            val legacyVaultKey = deriveKey("vl_vault_user_salt", currentUid)
            var restoredCount = 0

            for (doc in snapshot.documents) {
                val chatId = doc.id
                val payload = doc.getString("payload") ?: continue
                val iv = doc.getString("iv") ?: continue
                val vaultKey = if (doc.getLong("keyVersion") == 2L) ck else legacyVaultKey

                val decryptedJson = decryptText(payload, iv, vaultKey) ?: continue
                val jsonArray = org.json.JSONArray(decryptedJson)
                val messages = mutableListOf<Message>()

                for (i in 0 until jsonArray.length()) {
                    val msgJson = jsonArray.getJSONObject(i)
                    messages.add(ChatDataCache.jsonToMessage(msgJson))
                }

                if (messages.isNotEmpty()) {
                    ChatDataCache.saveMessages(context, chatId, messages)
                    val lastMsg = messages.maxByOrNull { it.createdAt?.seconds ?: 0L }
                    if (lastMsg != null) {
                        val text = lastMsg.text ?: lastMsg.caption ?: "Аварийное сообщение"
                        ChatDataCache.updateChatLastMessage(
                            context = context,
                            uid = currentUid,
                            chatId = chatId,
                            text = text,
                            senderId = lastMsg.senderId,
                            tsSeconds = lastMsg.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L)
                        )
                    }
                    restoredCount += messages.size
                }
            }
            Log.d("ChatRepo", "Restored $restoredCount messages from emergency vault")
            restoredCount
        } catch (e: Exception) {
            Log.w("ChatRepo", "Failed to restore emergency vault: ${e.message}")
            0
        }
    }
}

