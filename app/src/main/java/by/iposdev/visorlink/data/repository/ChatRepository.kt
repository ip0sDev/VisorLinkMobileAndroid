package by.iposdev.visorlink.data.repository

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
import com.google.firebase.functions.FirebaseFunctions
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.data.remote.chat.*
import by.iposdev.visorlink.utils.ChatDataCache
import by.iposdev.visorlink.utils.CdnService
import by.iposdev.visorlink.utils.ImageCache
import by.iposdev.visorlink.utils.VoiceCache
import by.iposdev.visorlink.utils.NetworkMonitor
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

class ChatRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val api: VisorLinkApi,
    private val wsClient: ChatWebSocketClient,
    private val flagsRepository: FlagsRepository
) {
    private val currentUid get() = auth.currentUser?.uid ?: ""
    private val backendPrefs = context.getSharedPreferences("visorlink_backend_settings", Context.MODE_PRIVATE)

    fun isBackendEnabled(): Boolean {
        val serverV2 = flagsRepository.isBackendV2Enabled()
        val serverFlag = flagsRepository.flags.value.isEnabled("test_backend_enabled")
        val userSetting = backendPrefs.getBoolean("use_custom_backend", false)
        return serverV2 || (serverFlag && userSetting)
    }

    fun isFirestoreDisabled(): Boolean {
        return flagsRepository.isBackendV2Enabled() || (flagsRepository.flags.value.isEnabled("test_backend_enabled") && 
                backendPrefs.getBoolean("disable_firestore_completely", false))
    }

    private fun MessageDto.toDomain(): Message {
        val effectiveCdnId = cdnMediaId
            ?: url?.substringAfter("/f/", "")?.substringBefore("?")?.takeIf { it.isNotEmpty() && !it.contains("/") }
            ?: url?.substringAfter("/p/", "")?.substringBefore("?")?.takeIf { it.isNotEmpty() && !it.contains("/") }

        return Message(
            id = id,
            senderId = senderId,
            senderUsername = senderUsername,
            type = type,
            text = text,
            url = url,
            cdnMediaId = effectiveCdnId,
            fileName = fileName,
            duration = duration,
            stickerId = stickerId,
            packId = packId,
            packName = packName,
            packEmoji = packEmoji,
            createdAt = Timestamp(Date(createdAt)),
            deleted = deleted,
            replyTo = replyTo?.let { mapOf("id" to it.id, "type" to it.type, "text" to it.text, "url" to it.url, "senderUsername" to it.senderUsername) },
            reactions = reactions?.map { mapOf("emoji" to it.emoji, "uids" to it.uids, "count" to it.count) } ?: emptyList(),
            readBy = readBy ?: emptyList(),
            spoiler = spoiler,
            caption = caption,
            images = images?.map { 
                val imgCdnId = it.cdnMediaId
                    ?: it.url?.substringAfter("/f/", "")?.substringBefore("?")?.takeIf { u -> u.isNotEmpty() && !u.contains("/") }
                    ?: it.url?.substringAfter("/p/", "")?.substringBefore("?")?.takeIf { u -> u.isNotEmpty() && !u.contains("/") }
                AlbumImage(url = it.url, cdnMediaId = imgCdnId, fileName = it.fileName, spoiler = it.spoiler) 
            } ?: emptyList()
        )
    }

    private fun ChatDto.toDomain() = Chat(
        id = id,
        type = type,
        participants = participants ?: emptyList(),
        participantData = participantData?.mapValues { (_, v) -> mapOf("username" to v.username, "displayName" to v.displayName) } ?: emptyMap(),
        name = name ?: "",
        createdAt = Timestamp(Date(createdAt)),
        lastMessage = lastMessageText,
        lastMessageAt = lastMessageAt?.let { Timestamp(Date(it)) }
    )

    fun getChatId(uid1: String, uid2: String) =
        listOf(uid1, uid2).sorted().joinToString("_")

    suspend fun chatExists(chatId: String): Boolean = try {
        if (isFirestoreDisabled() && !isBackendEnabled()) return false
        db.collection("chats").document(chatId).get().await().exists()
    } catch (e: Exception) {
        // Если оффлайн, проверяем наличие чата в нашем локальном кэше (SQLite)
        val cachedChats = ChatDataCache.loadChatList(context, currentUid)
        cachedChats.any { it.id == chatId }
    }

    fun allChatsFlow(uid: String): Flow<List<Chat>> = channelFlow {
        val cacheUid = if (isBackendEnabled()) uid + "_backend" else uid
        
        if (isBackendEnabled()) {
            var currentChats = ChatDataCache.loadChatList(context, cacheUid)
            if (currentChats.isNotEmpty()) send(currentChats)

            try {
                val chats = api.getChats().map { it.toDomain() }
                    .sortedByDescending { it.lastMessageAt?.seconds ?: 0 }
                currentChats = chats
                send(currentChats)
                withContext(Dispatchers.IO) { ChatDataCache.saveChatList(context, cacheUid, chats) }
            } catch (e: Exception) {
                if (currentChats.isEmpty()) send(emptyList())
            }

            launch {
                wsClient.connect()
                wsClient.subscribeUser(uid)

                launch {
                    wsClient.updatedChats.collect { updatedDto ->
                        val updated = updatedDto.toDomain()
                        currentChats = (currentChats.filter { it.id != updated.id } + updated)
                            .sortedByDescending { it.lastMessageAt?.seconds ?: 0 }
                        send(currentChats)
                        withContext(Dispatchers.IO) { ChatDataCache.saveChatList(context, cacheUid, currentChats) }
                    }
                }

                launch {
                    wsClient.incomingMessages.collect { msgDto ->
                        val chatId = msgDto.chatId ?: return@collect
                        val existing = currentChats.find { it.id == chatId }
                        if (existing != null) {
                            val updated = existing.copy(
                                lastMessage = msgDto.text ?: msgDto.caption ?: "Message",
                                lastMessageAt = Timestamp(Date(msgDto.createdAt))
                            )
                            currentChats = (currentChats.filter { it.id != chatId } + updated)
                                .sortedByDescending { it.lastMessageAt?.seconds ?: 0 }
                            send(currentChats)
                            withContext(Dispatchers.IO) { ChatDataCache.saveChatList(context, cacheUid, currentChats) }
                        }
                    }
                }
            }

            awaitClose { }
            return@channelFlow
        }


        if (isFirestoreDisabled()) {
            val cached = ChatDataCache.loadChatList(context, cacheUid)
            if (cached.isNotEmpty()) send(cached) else send(emptyList())
            awaitClose { }
            return@channelFlow
        }

        launch(Dispatchers.IO) {
            val cached = ChatDataCache.loadChatList(context, cacheUid)
            if (cached.isNotEmpty()) trySend(cached)
        }

        val directChats = mutableListOf<Chat>()
        val groupChats  = mutableListOf<Chat>()

        fun merge() {
            val all = (directChats + groupChats)
                .distinctBy { it.id }
                .sortedByDescending { it.lastMessageAt?.seconds ?: 0 }
            trySend(all)
            launch(Dispatchers.IO) { ChatDataCache.saveChatList(context, cacheUid, all) }
        }

        val reg1 = db.collection("chats").whereArrayContains("participants", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                directChats.clear()
                snap?.documents?.forEach { doc ->
                    doc.toChatOrNull()?.let { directChats.add(it) }
                }
                merge()
            }

        val reg2 = db.collection("chats").whereArrayContains("memberIds", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                groupChats.clear()
                snap?.documents?.forEach { doc ->
                    doc.toChatOrNull()?.let { groupChats.add(it) }
                }
                merge()
            }

        awaitClose { reg1.remove(); reg2.remove() }
    }

    fun chatsFlow(uid: String) = allChatsFlow(uid)

    fun latestMessagesFlow(chatId: String, onUpdate: (List<Message>, DocumentSnapshot?) -> Unit): Flow<Unit> = channelFlow {
        if (isBackendEnabled()) {
            var currentMessages = emptyList<Message>()

            // 1. Initial Cache load
            launch(Dispatchers.IO) {
                currentMessages = ChatDataCache.loadMessages(context, chatId)
                onUpdate(currentMessages, null)
            }

            // 2. Load from API
            launch {
                try {
                    val messages = api.getMessages(chatId).map { it.toDomain() }
                    currentMessages = messages
                    onUpdate(currentMessages, null)
                    withContext(Dispatchers.IO) { ChatDataCache.saveMessages(context, chatId, messages) }
                } catch (e: Exception) {
                    Log.e("ChatRepo", "Error loading messages from API", e)
                }
            }

            // 3. Listen to WebSocket
            launch {
                try {
                    val customUrl = backendPrefs.getString("custom_backend_url", "https://backend.visorlink.org") ?: "https://backend.visorlink.org"
                    wsClient.connect(customUrl)
                    wsClient.subscribeChat(chatId)

                    launch {
                        wsClient.incomingMessages.collect { msgDto ->
                            if (msgDto.chatId == chatId || chatId == "all") {
                                val domainMsg = msgDto.toDomain()
                                currentMessages = (currentMessages.filter { it.id != domainMsg.id } + domainMsg)
                                    .sortedBy { it.createdAt?.seconds ?: 0L }
                                onUpdate(currentMessages, null)
                                launch(Dispatchers.IO) { ChatDataCache.saveMessages(context, chatId, currentMessages) }
                            }
                        }
                    }

                    launch {
                        wsClient.updatedMessages.collect { msgDto ->
                            if (msgDto.chatId == chatId || chatId == "all") {
                                val domainMsg = msgDto.toDomain()
                                currentMessages = currentMessages.map { if (it.id == domainMsg.id) domainMsg else it }
                                    .sortedBy { it.createdAt?.seconds ?: 0L }
                                onUpdate(currentMessages, null)
                                launch(Dispatchers.IO) { ChatDataCache.saveMessages(context, chatId, currentMessages) }
                            }
                        }
                    }

                    launch {
                        wsClient.deletedMessages.collect { delPayload ->
                            if (delPayload.chatId == chatId || delPayload.chatId == null) {
                                currentMessages = currentMessages.filter { it.id != delPayload.id }
                                onUpdate(currentMessages, null)
                                launch(Dispatchers.IO) { ChatDataCache.saveMessages(context, chatId, currentMessages) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepo", "❌ WS Error in latestMessagesFlow", e)
                }
            }

            awaitClose {
                wsClient.unsubscribeChat(chatId)
            }
            return@channelFlow
        }

        if (isFirestoreDisabled()) {
            launch(Dispatchers.IO) {
                val cached = ChatDataCache.loadMessages(context, chatId)
                onUpdate(cached, null)
            }
            awaitClose { }
            return@channelFlow
        }

        var currentFirestore = emptyList<Message>()
        var currentOutbox = emptyList<ChatDataCache.QueuedAction>()
        var currentLastDoc: DocumentSnapshot? = null

        fun rebuild() {
            val firestoreIds = currentFirestore.map { it.id }.toSet()
            val outboxMsgs = currentOutbox.map { action ->
                val type = action.data.optString("type", action.type)
                if (action.progress > 0f) {
                    Log.d("ChatRepo", "Outbox message ${action.id} progress: ${action.progress}")
                }
                Message(
                    id = action.id,
                    senderId = currentUid,
                    type = type,
                    text = action.data.optString("text"),
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
                    // Если сообщение есть и в Firestore, и в Outbox — значит оно уже прилетело по сети,
                    // но Outbox еще не почищен. В этом случае прогресс уже не нужен.
                    fsMsg.copy(
                        localFile = obMsg.localFile ?: fsMsg.localFile,
                        status = SendStatus.SENT
                    )
                } else {
                    fsMsg
                }
            }
            
            val combined = combinedMap.values.toList()
            onUpdate(combined.sortedBy { it.createdAt?.seconds ?: 0L }, currentLastDoc)
            
            // Clean up outbox items that are now present in the Firestore snapshot
            val confirmedIds = currentOutbox.map { it.id }.filter { it in firestoreIds }
            if (confirmedIds.isNotEmpty()) {
                launch(Dispatchers.IO) { ChatDataCache.cleanupOutbox(context, confirmedIds) }
            }
        }

        // 1. Initial Outbox load and listener
        launch {
            ChatDataCache.outboxFlow(context, chatId).collect {
                currentOutbox = it
                rebuild()
            }
        }

        // 2. Initial Cache load
        launch(Dispatchers.IO) {
            val cached = ChatDataCache.loadMessages(context, chatId)
            currentFirestore = cached
            rebuild()
        }

        // 3. Firestore Snapshot Listener
        val reg = db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(PAGE_SIZE)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                val messages = snap.documents.mapNotNull { doc ->
                    try { doc.toObject(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.copy(id = doc.id) } catch (_: Exception) { null }
                }.reversed()
                
                currentFirestore = messages
                currentLastDoc = snap.documents.lastOrNull()
                rebuild()

                launch(Dispatchers.IO) {
                    ChatDataCache.saveMessages(context, chatId, messages)
                    // Prefetch media
                    messages.forEach { msg ->
                        try {
                            val url = if (msg.cdnMediaId != null) CdnService.getFileUrl(msg.cdnMediaId) else msg.url
                            if (!url.isNullOrEmpty()) {
                                if (msg.type == MessageType.VOICE) VoiceCache.getOrDownload(context, url)
                                else ImageCache.getOrDownload(context, url)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

        awaitClose { reg.remove() }
        send(Unit)
    }

    suspend fun loadOlderMessages(chatId: String, startAfterDoc: DocumentSnapshot): Pair<List<Message>, DocumentSnapshot?> = try {
        if (isBackendEnabled()) {
            val msgs = api.getMessages(chatId, limit = PAGE_SIZE.toInt(), before = startAfterDoc.id).map { it.toDomain() }
            Pair(msgs, null)
        } else {
            val snap = db.collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING).startAfter(startAfterDoc).limit(PAGE_SIZE).get().await()
            val messages = snap.documents.mapNotNull { doc ->
                try { doc.toObject(Message::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
            }.reversed()
            Pair(messages, if (snap.documents.size >= PAGE_SIZE) snap.documents.lastOrNull() else null)
        }
    } catch (e: Exception) {
        Pair(emptyList(), null)
    }

    fun membersFlow(chatId: String): Flow<List<Member>> = callbackFlow {
        if (isBackendEnabled()) {
            try {
                val dtos = api.getChatMembers(chatId)
                val members = dtos.map {
                    Member(
                        uid = it.userId,
                        role = it.role,
                        joinedAt = it.joinedAt?.let { ts -> Timestamp(Date(ts)) } ?: Timestamp.now(),
                        muted = it.muted,
                        banned = it.banned
                    )
                }
                trySend(members)
            } catch (_: Exception) {
                trySend(emptyList())
            }
            awaitClose { }
            return@callbackFlow
        }

        if (isFirestoreDisabled()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
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
        if (isBackendEnabled()) {
            val members = api.getChatMembers(chatId)
            members.find { it.userId == currentUid }?.let {
                Member(
                    uid = it.userId,
                    role = it.role,
                    joinedAt = it.joinedAt?.let { ts -> Timestamp(Date(ts)) } ?: Timestamp.now(),
                    muted = it.muted,
                    banned = it.banned
                )
            }
        } else if (isFirestoreDisabled()) null
        else {
            val snap = db.collection("chats").document(chatId).collection("members").document(currentUid).get().await()
            snap.toObject(Member::class.java)?.copy(uid = snap.id)
        }
    } catch (e: Exception) { null }

    fun pendingInvitesFlow(uid: String): Flow<List<GroupInvite>> = callbackFlow {
        if (isBackendEnabled()) {
            try {
                val invites = api.getInvites().map {
                    GroupInvite(
                        id = it.id,
                        chatId = it.chatId,
                        invitedBy = it.inviterUsername ?: it.inviterId ?: "",
                        createdAt = Timestamp.now(),
                        status = it.status
                    )
                }
                trySend(invites)
            } catch (_: Exception) {
                trySend(emptyList())
            }
            awaitClose { }
            return@callbackFlow
        }

        if (isFirestoreDisabled()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = db.collection("invites").whereEqualTo("invitedUid", uid).whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                val invites = snap?.documents?.mapNotNull { doc ->
                    try { doc.toObject(GroupInvite::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(invites)
            }
        awaitClose { reg.remove() }
    }

    fun notificationsFlow(uid: String): Flow<List<AppNotification>> = callbackFlow {
        if (isBackendEnabled()) {
            try {
                val notifs = api.getNotifications().map {
                    AppNotification(
                        id = it.id,
                        type = it.type,
                        chatId = (it.data?.get("chatId") as? String) ?: "",
                        inviteId = (it.data?.get("inviteId") as? String) ?: "",
                        invitedBy = it.title ?: "",
                        createdAt = Timestamp(Date(it.createdAt)),
                        read = it.read
                    )
                }
                trySend(notifs)
            } catch (_: Exception) {
                trySend(emptyList())
            }
            awaitClose { }
            return@callbackFlow
        }

        if (isFirestoreDisabled()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = db.collection("users").document(uid).collection("notifications").whereEqualTo("read", false)
            .addSnapshotListener { snap, _ ->
                val notifs = snap?.documents?.mapNotNull { doc ->
                    try { doc.toObject(AppNotification::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(notifs)
            }
        awaitClose { reg.remove() }
    }

    suspend fun findOrCreateChat(currentUserProfile: UserProfile, targetUid: String): String = if (isBackendEnabled()) {
        try {
            val response = api.createChat(CreateChatRequest(
                type = "direct",
                targetUid = targetUid
            ))
            response.id
        } catch (e: Exception) {
            val chatId = getChatId(currentUserProfile.uid, targetUid)
            val cached = ChatDataCache.loadChatList(context, currentUid + "_backend")
            if (cached.any { it.id == chatId }) chatId else throw e
        }
    } else try {
        val targetDoc = db.collection("users").document(targetUid).get().await()
        val targetUser = targetDoc.toObject(UserProfile::class.java) ?: throw Exception("User not found")
        val chatId = getChatId(currentUserProfile.uid, targetUid)
        val chatDoc = db.collection("chats").document(chatId).get().await()
        if (!chatDoc.exists()) {
            db.collection("chats").document(chatId).set(mapOf(
                "type"            to "direct",
                "participants"    to listOf(currentUserProfile.uid, targetUid),
                "participantData" to mapOf(
                    currentUserProfile.uid to mapOf("username" to currentUserProfile.username, "displayName" to currentUserProfile.displayName),
                    targetUid to mapOf("username" to targetUser.username, "displayName" to targetUser.displayName)
                ),
                "createdAt"      to FieldValue.serverTimestamp(),
                "lastMessageAt"  to FieldValue.serverTimestamp(),
                "lastMessage"    to null
            )).await()
        }
        chatId
    } catch (e: Exception) {
        // Fallback for offline: if we already have this chat in cache, return it
        val chatId = getChatId(currentUserProfile.uid, targetUid)
        val cached = ChatDataCache.loadChatList(context, currentUid)
        if (cached.any { it.id == chatId }) chatId else throw e
    }

    suspend fun createChat(type: String, name: String, tag: String, description: String = ""): Pair<String, String> {
        if (isBackendEnabled()) {
            val res = api.createChat(CreateChatRequest(
                type = type,
                name = name,
                tag = tag,
                description = description
            ))
            return Pair(res.id, res.inviteLink ?: res.id)
        }
        if (isFirestoreDisabled()) throw Exception("Firestore is disabled")
        val result = functions.getHttpsCallable("createChat").call(mapOf("type" to type, "name" to name, "tag" to tag, "description" to description)).await()
        val data = result.data as Map<*, *>
        return Pair(data["chatId"] as String, data["inviteLink"] as String)
    }

    suspend fun findByTag(tag: String): TagSearchResult {
        if (isBackendEnabled()) {
            return try {
                val res = api.findByTag(tag)
                TagSearchResult(
                    found = true,
                    chatId = res.id,
                    name = res.name ?: "",
                    tag = res.tag ?: tag,
                    description = res.description ?: "",
                    avatarUrl = res.avatarUrl,
                    memberCount = res.memberCount ?: 0,
                    type = res.type,
                    joinByTag = res.joinByTag ?: false
                )
            } catch (e: Exception) {
                TagSearchResult(found = false)
            }
        }
        if (isFirestoreDisabled()) return TagSearchResult(found = false)
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
        if (isBackendEnabled()) {
            return try {
                val cleanTag = tag?.removePrefix("@")?.trim()
                if (!cleanTag.isNullOrBlank()) {
                    api.joinByTag(JoinByTagRequest(tag = cleanTag))
                }
                true
            } catch (e: Exception) {
                false
            }
        }
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
        if (isBackendEnabled()) {
            val res = api.joinByTag(JoinByTagRequest(tag = tag))
            return res.id
        }
        if (isFirestoreDisabled()) throw Exception("Firestore is disabled")
        val result = functions.getHttpsCallable("joinByTag").call(mapOf("tag" to tag)).await()
        return (result.data as Map<*, *>)["chatId"] as String
    }

    suspend fun joinByInvite(token: String): Pair<String, String> {
        if (isBackendEnabled()) {
            val res = api.joinByInvite(JoinByInviteRequest(inviteCode = token))
            return Pair(res.id, res.type)
        }
        val result = functions.getHttpsCallable("joinByInvite").call(mapOf("token" to token)).await()
        val data = result.data as Map<*, *>
        return Pair(data["chatId"] as String, data["chatType"] as? String ?: "group")
    }

    suspend fun inviteUser(chatId: String, username: String) {
        if (isBackendEnabled()) {
            api.inviteUser(chatId, InviteUserRequest(targetUsername = username))
            return
        }
        functions.getHttpsCallable("inviteUser").call(mapOf("chatId" to chatId, "targetUsername" to username)).await()
    }

    suspend fun respondToInvite(inviteId: String, accept: Boolean): String? {
        if (isBackendEnabled()) {
            api.respondToInvite(inviteId, RespondInviteRequest(action = if (accept) "accept" else "decline"))
            return null
        }
        val result = functions.getHttpsCallable("respondToInvite").call(mapOf("inviteId" to inviteId, "accept" to accept)).await()
        return (result.data as Map<*, *>)["chatId"] as? String
    }

    suspend fun leaveChat(chatId: String) {
        if (isBackendEnabled()) {
            api.leaveChat(chatId)
            return
        }
        functions.getHttpsCallable("leaveChat").call(mapOf("chatId" to chatId)).await()
    }

    suspend fun moderateUser(chatId: String, targetUid: String, action: String, durationMinutes: Int? = null) {
        if (isBackendEnabled()) {
            api.moderateUser(chatId, ModerateUserRequest(
                targetUid = targetUid,
                action = action,
                durationSeconds = durationMinutes?.times(60)
            ))
            return
        }
        functions.getHttpsCallable("moderateUser").call(mapOf("chatId" to chatId, "targetUid" to targetUid, "action" to action, "durationMinutes" to durationMinutes)).await()
    }

    suspend fun setMemberRole(chatId: String, targetUid: String, role: String) {
        if (isBackendEnabled()) {
            api.setMemberRole(chatId, targetUid, SetRoleRequest(targetUid = targetUid, role = role))
            return
        }
        functions.getHttpsCallable("setMemberRole").call(mapOf("chatId" to chatId, "targetUid" to targetUid, "role" to role)).await()
    }

    suspend fun updateChatSettings(chatId: String, params: Map<String, Any?>) {
        if (isBackendEnabled()) {
            api.updateChatSettings(chatId, UpdateChatSettingsRequest(
                name = params["name"] as? String,
                tag = params["tag"] as? String,
                description = params["description"] as? String,
                avatarUrl = params["avatarUrl"] as? String,
                joinByLink = params["joinByLink"] as? Boolean,
                joinByTag = params["joinByTag"] as? Boolean,
                allowReactions = params["allowReactions"] as? Boolean,
                allowComments = params["allowComments"] as? Boolean,
                noForwards = params["noForwards"] as? Boolean,
                botAllowed = params["botAllowed"] as? Boolean
            ))
            return
        }
        functions.getHttpsCallable("updateChatSettings").call(params + mapOf("chatId" to chatId)).await()
    }

    suspend fun regenerateInviteLink(chatId: String): String {
        if (isBackendEnabled()) {
            return api.regenerateInviteLink(chatId).inviteLink
        }
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

    suspend fun sendText(chatId: String, text: String, senderUsername: String, replyTo: ReplyData?, topicId: String? = null): Message? {
        if (isBackendEnabled()) {
            return try {
                val response = api.sendMessage(SendMessageRequest(
                    chatId = chatId,
                    type = MessageType.TEXT,
                    text = text,
                    replyToId = replyTo?.id
                ))
                Log.d("ChatRepo", "✅ Message sent to backend: ${response.id}")
                response.toDomain()
            } catch (e: Exception) {
                Log.e("ChatRepo", "❌ Error sending message to backend", e)
                null
            }
        }
        if (isFirestoreDisabled()) return null
        val data = JSONObject().apply {
            put("text", text)
            put("senderUsername", senderUsername)
            if (topicId != null) put("topicId", topicId)
            replyTo?.let { put("replyTo", JSONObject(it.toMap())) }
        }
        ChatDataCache.addToOutbox(context, chatId, "text", data)
        return null
    }

    suspend fun sendTextNow(id: String, chatId: String, text: String, senderUsername: String, replyTo: ReplyData?, topicId: String? = null) {
        if (isBackendEnabled()) {
            api.sendMessage(SendMessageRequest(
                chatId = chatId,
                type = MessageType.TEXT,
                text = text,
                replyToId = replyTo?.id
            ))
            return
        }
        if (isFirestoreDisabled()) return
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
        if (topicId != null) {
            msgData["topicId"] = topicId
        }

        val batch  = db.batch()
        batch.set(msgRef, msgData)
        batch.update(db.collection("chats").document(chatId), mapOf(
            "lastMessage"   to text,
            "lastMessageAt" to FieldValue.serverTimestamp()
        ))
        if (topicId != null) {
            val topicRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            batch.update(topicRef, mapOf(
                "lastMessage" to mapOf("text" to text, "senderUsername" to senderUsername),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.update(userRef, "lastMessageAt", FieldValue.serverTimestamp())
        batch.commit().await()
    }

    suspend fun sendImage(chatId: String, uri: Uri, senderUsername: String, replyTo: ReplyData?, isSpoiler: Boolean = false, topicId: String? = null) = withContext(Dispatchers.IO) {
        val fileName = "${System.currentTimeMillis()}_${uri.lastPathSegment ?: "image.jpg"}"
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        
        val data = JSONObject().apply {
            put("localPath", tempFile.absolutePath)
            put("senderUsername", senderUsername)
            put("isSpoiler", isSpoiler)
            if (topicId != null) put("topicId", topicId)
            replyTo?.let { put("replyTo", JSONObject(it.toMap())) }
        }
        ChatDataCache.addToOutbox(context, chatId, "image", data)
    }

    suspend fun sendImageNow(id: String, chatId: String, mediaId: String, fileName: String, senderUsername: String, replyTo: ReplyData?, isSpoiler: Boolean, topicId: String? = null) {
        if (isBackendEnabled()) {
            api.sendMessage(SendMessageRequest(
                chatId = chatId,
                type = MessageType.IMAGE,
                cdnMediaId = mediaId,
                fileName = fileName,
                replyToId = replyTo?.id
            ))
            return
        }
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(id)
        val userRef = db.collection("users").document(currentUid)

        val extra = mutableMapOf<String, Any?>(
            "type"       to MessageType.IMAGE,
            "cdnMediaId" to mediaId,
            "fileName"   to fileName
        )
        if (isSpoiler) extra["spoiler"] = true

        val msg = mutableMapOf<String, Any?>(
            "senderId"       to currentUid,
            "senderUsername" to senderUsername,
            "createdAt"      to FieldValue.serverTimestamp(),
            "deleted"        to false,
            "reactions"      to emptyList<Any>(),
            "readBy"         to listOf(currentUid),
            "replyTo"        to replyTo?.toMap()
        )
        if (topicId != null) msg["topicId"] = topicId
        msg.putAll(extra)

        val batch = db.batch()
        batch.set(msgRef, msg)
        batch.update(db.collection("chats").document(chatId), mapOf("lastMessage" to "📷 Image", "lastMessageAt" to FieldValue.serverTimestamp()))
        if (topicId != null) {
            val topicRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            batch.update(topicRef, mapOf(
                "lastMessage" to mapOf("text" to "📷 Image", "senderUsername" to senderUsername),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.update(userRef, "lastMessageAt", FieldValue.serverTimestamp())
        batch.commit().await()
    }

    suspend fun sendVoice(chatId: String, file: File, durationSec: Int, senderUsername: String, replyTo: ReplyData?, topicId: String? = null) = withContext(Dispatchers.IO) {
        val data = JSONObject().apply {
            put("localPath", file.absolutePath)
            put("duration", durationSec)
            put("senderUsername", senderUsername)
            if (topicId != null) put("topicId", topicId)
            replyTo?.let { put("replyTo", JSONObject(it.toMap())) }
        }
        ChatDataCache.addToOutbox(context, chatId, "voice", data)
    }

    suspend fun sendVoiceNow(id: String, chatId: String, mediaId: String, durationSec: Int, senderUsername: String, replyTo: ReplyData?, topicId: String? = null) {
        if (isBackendEnabled()) {
            api.sendMessage(SendMessageRequest(
                chatId = chatId,
                type = MessageType.VOICE,
                cdnMediaId = mediaId,
                duration = durationSec,
                replyToId = replyTo?.id
            ))
            return
        }
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(id)
        val userRef = db.collection("users").document(currentUid)

        val extra = mapOf(
            "type"       to MessageType.VOICE,
            "cdnMediaId" to mediaId,
            "duration"   to durationSec
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
        if (topicId != null) msg["topicId"] = topicId
        msg.putAll(extra)

        val batch = db.batch()
        batch.set(msgRef, msg)
        batch.update(db.collection("chats").document(chatId), mapOf("lastMessage" to "🎤 Voice message", "lastMessageAt" to FieldValue.serverTimestamp()))
        if (topicId != null) {
            val topicRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            batch.update(topicRef, mapOf(
                "lastMessage" to mapOf("text" to "🎤 Voice message", "senderUsername" to senderUsername),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.update(userRef, "lastMessageAt", FieldValue.serverTimestamp())
        batch.commit().await()
    }

    suspend fun sendVideo(chatId: String, uri: Uri, senderUsername: String, replyTo: ReplyData?, topicId: String? = null) = withContext(Dispatchers.IO) {
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
        }
        ChatDataCache.addToOutbox(context, chatId, "video", data)
    }

    suspend fun sendVideoNow(id: String, chatId: String, mediaId: String, fileName: String, senderUsername: String, replyTo: ReplyData?, topicId: String? = null) {
        if (isBackendEnabled()) {
            api.sendMessage(SendMessageRequest(
                chatId = chatId,
                type = MessageType.VIDEO,
                cdnMediaId = mediaId,
                fileName = fileName,
                replyToId = replyTo?.id
            ))
            return
        }
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(id)
        val userRef = db.collection("users").document(currentUid)

        val extra = mapOf(
            "type"       to MessageType.VIDEO,
            "cdnMediaId" to mediaId,
            "fileName"   to fileName
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
        if (topicId != null) msg["topicId"] = topicId
        msg.putAll(extra)

        val batch = db.batch()
        batch.set(msgRef, msg)
        batch.update(db.collection("chats").document(chatId), mapOf("lastMessage" to "🎥 Video", "lastMessageAt" to FieldValue.serverTimestamp()))
        if (topicId != null) {
            val topicRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            batch.update(topicRef, mapOf(
                "lastMessage" to mapOf("text" to "🎥 Video", "senderUsername" to senderUsername),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.update(userRef, "lastMessageAt", FieldValue.serverTimestamp())
        batch.commit().await()
    }

    suspend fun sendSticker(chatId: String, sticker: StickerItem, packId: String, packName: String, packEmoji: String, senderUsername: String, replyTo: ReplyData?, topicId: String? = null) {
        val data = JSONObject().apply {
            put("stickerId", sticker.id)
            put("url", sticker.url)
            put("packId", packId)
            put("packName", packName)
            put("packEmoji", packEmoji)
            put("senderUsername", senderUsername)
            if (topicId != null) put("topicId", topicId)
            replyTo?.let { put("replyTo", JSONObject(it.toMap())) }
        }
        ChatDataCache.addToOutbox(context, chatId, "sticker", data)
    }

    suspend fun sendStickerNow(id: String, chatId: String, stickerId: String, url: String, packId: String, packName: String, packEmoji: String, senderUsername: String, replyTo: ReplyData?, topicId: String? = null) {
        if (isBackendEnabled()) {
            api.sendMessage(SendMessageRequest(
                chatId = chatId,
                type = MessageType.STICKER,
                stickerId = stickerId,
                packId = packId,
                packName = packName,
                packEmoji = packEmoji,
                replyToId = replyTo?.id
            ))
            return
        }
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(id)
        val userRef = db.collection("users").document(currentUid)

        val extra = mapOf(
            "type"      to MessageType.STICKER,
            "url"       to url,
            "stickerId" to stickerId,
            "packId"    to packId,
            "packName"  to packName,
            "packEmoji" to packEmoji
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
        if (topicId != null) msg["topicId"] = topicId
        msg.putAll(extra)

        val batch = db.batch()
        batch.set(msgRef, msg)
        batch.update(db.collection("chats").document(chatId), mapOf("lastMessage" to "$packEmoji Sticker", "lastMessageAt" to FieldValue.serverTimestamp()))
        if (topicId != null) {
            val topicRef = db.collection("chats").document(chatId).collection("topics").document(topicId)
            batch.update(topicRef, mapOf(
                "lastMessage" to mapOf("text" to "$packEmoji Sticker", "senderUsername" to senderUsername),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.update(userRef, "lastMessageAt", FieldValue.serverTimestamp())
        batch.commit().await()
    }

    suspend fun uploadAlbumImages(chatId: String, items: List<AlbumImageLocal>): List<AlbumImage> = coroutineScope {
        items.map { item ->
            async(Dispatchers.IO) {
                val fileName = "${System.currentTimeMillis()}_${item.uri.lastPathSegment ?: "photo.jpg"}"
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                val mediaId = CdnService.uploadFile(tempFile, "image/jpeg")
                tempFile.delete()
                AlbumImage(cdnMediaId = mediaId, fileName = fileName, spoiler = item.spoiler)
            }
        }.awaitAll()
    }

    suspend fun sendAlbum(chatId: String, images: List<AlbumImage>, caption: String?, replyTo: ReplyData?): String {
        if (isBackendEnabled()) {
            val res = api.sendAlbum(chatId, SendAlbumRequest(
                images = images.map { AlbumImageDto(url = it.url, cdnMediaId = it.cdnMediaId, fileName = it.fileName, spoiler = it.spoiler) },
                caption = caption,
                replyTo = replyTo?.let { ReplyDto(id = it.id, type = it.type, text = it.text, url = it.url, senderUsername = it.senderUsername) }
            ))
            return res.id
        }
        val data = buildMap<String, Any?> {
            put("chatId",  chatId)
            put("images",  images.map { it.toMap() })
            if (!caption.isNullOrBlank()) put("caption", caption.trim())
            if (replyTo != null) put("replyTo", replyTo.toMap())
        }
        val result = functions.getHttpsCallable("sendAlbum").call(data).await()
        try { db.collection("users").document(currentUid).update("lastMessageAt", FieldValue.serverTimestamp()).await() } catch (_: Exception) {}
        return (result.data as Map<*, *>)["messageId"] as String
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
        batch.update(userRef, "lastMessageAt", FieldValue.serverTimestamp())
        batch.commit().await()
    }

    suspend fun cancelSending(id: String) {
        withContext(Dispatchers.IO) {
            ChatDataCache.cleanupOutbox(context, listOf(id))
        }
    }

    suspend fun markMessagesAsRead(chatId: String, messages: List<Message>, uid: String) {
        val unread = messages.filter { it.senderId != uid && !it.readBy.contains(uid) && !it.deleted }
        if (unread.isEmpty()) return
        if (isBackendEnabled()) {
            for (msg in unread) {
                try { api.markMessageAsRead(chatId, msg.id) } catch (_: Exception) {}
            }
            return
        }
        val batch = db.batch()
        for (msg in unread) {
            val ref = db.collection("chats").document(chatId).collection("messages").document(msg.id)
            batch.update(ref, "readBy", FieldValue.arrayUnion(uid))
        }
        batch.update(db.collection("chats").document(chatId), "unreadCount.$uid", 0)
        batch.commit().await()
    }

    suspend fun resetUnreadCount(chatId: String, uid: String) {
        if (isFirestoreDisabled() || uid.isEmpty() || chatId.isEmpty()) return
        try {
            db.collection("chats").document(chatId).update("unreadCount.$uid", 0).await()
        } catch (_: Exception) {}
    }

    suspend fun deleteMessage(chatId: String, messageId: String) {
        if (isBackendEnabled()) {
            try { api.deleteMessage(chatId, messageId) } catch (_: Exception) {}
            return
        }
        if (isFirestoreDisabled()) return
        db.collection("chats").document(chatId).collection("messages").document(messageId)
            .update(mapOf("deleted" to true, "deletedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun editMessage(chatId: String, messageId: String, newText: String, oldText: String, isCaption: Boolean = false) {
        if (isBackendEnabled()) {
            try {
                api.editMessage(chatId, messageId, EditMessageRequest(
                    text = newText,
                    caption = if (isCaption) newText else null
                ))
            } catch (_: Exception) {}
            return
        }
        if (isFirestoreDisabled()) return
        val field = if (isCaption) "caption" else "text"
        val historyField = if (isCaption) "caption" else "text" // Prompt uses "text" in history for both? "text" to oldText.
        
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        
        val historyItem = mapOf(
            historyField to oldText,
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
        if (isBackendEnabled()) {
            try {
                api.toggleReaction(chatId, messageId, ReactionRequest(emoji = emoji))
            } catch (_: Exception) {}
            return
        }
        if (isFirestoreDisabled()) return
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
        if (isBackendEnabled()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val comments = api.getComments(chatId, messageId).map { dto ->
                        Comment(
                            id = dto.id,
                            senderId = dto.senderId,
                            senderUsername = dto.senderUsername,
                            type = dto.type,
                            text = dto.text,
                            url = dto.url,
                            fileName = dto.fileName,
                            duration = dto.duration,
                            reactions = dto.reactions?.map {
                                mapOf<String, Any>("emoji" to it.emoji, "uids" to it.uids, "count" to it.count.toLong())
                            } ?: emptyList(),
                            spoiler = dto.spoiler,
                            replyTo = dto.replyTo?.let { CommentReplyData(it.id, it.type, it.text, it.url, it.senderUsername) },
                            createdAt = Timestamp(Date(dto.createdAt))
                        )
                    }
                    onUpdate(comments)
                } catch (_: Exception) {}
            }
            return ListenerRegistration { }
        }
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
            .addSnapshotListener { snap, _ -> snap?.toObject(Message::class.java)?.copy(id = snap.id)?.let(onUpdate) }
    }

    suspend fun addComment(chatId: String, messageId: String, type: String, text: String? = null, cdnMediaId: String? = null, url: String? = null, fileName: String? = null, duration: Int? = null, spoiler: Boolean = false, replyTo: CommentReplyData? = null): String {
        if (isBackendEnabled()) {
            val res = api.addComment(chatId, messageId, AddCommentRequest(
                type = type,
                text = text,
                url = url,
                fileName = fileName,
                duration = duration,
                stickerId = null,
                replyToId = replyTo?.id,
                spoiler = spoiler
            ))
            return res.id
        }
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
        if (isBackendEnabled()) {
            try { api.toggleFeedComments(messageId) } catch (_: Exception) {}
            return
        }
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
        val fileName = "${System.currentTimeMillis()}_${file.lastPathSegment ?: "image.jpg"}"
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(file)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        val mediaId = CdnService.uploadFile(tempFile, "image/jpeg")
        tempFile.delete()
        addComment(chatId = chatId, messageId = messageId, type = MessageType.IMAGE, cdnMediaId = mediaId, fileName = fileName, spoiler = spoiler, replyTo = replyTo)
    }

    suspend fun uploadAndCommentVoice(chatId: String, messageId: String, audioFile: File, durationSeconds: Int, replyTo: CommentReplyData? = null) = withContext(Dispatchers.IO) {
        val mediaId = CdnService.uploadFile(audioFile, "audio/webm")
        addComment(chatId = chatId, messageId = messageId, type = MessageType.VOICE, cdnMediaId = mediaId, duration = durationSeconds, replyTo = replyTo)
    }
}
