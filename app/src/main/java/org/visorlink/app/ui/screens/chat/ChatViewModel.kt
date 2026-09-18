package org.visorlink.app.ui.screens.chat

import android.app.Application
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.data.model.*
import org.visorlink.app.data.repository.ChatRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.utils.ActiveChatTracker
import org.visorlink.app.utils.PresenceManager
import org.visorlink.app.utils.DraftManager
import org.visorlink.app.utils.NotificationHelper
import org.visorlink.app.utils.TypingManager
import org.visorlink.app.utils.VoicePlayerManager
import org.visorlink.app.utils.VoicePlaybackState
import org.visorlink.app.data.repository.MusicRepository
import org.visorlink.app.utils.MusicPlayerManager
import org.visorlink.app.utils.MusicPlayerState
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.visorlink.app.utils.ChatDataCache
import org.visorlink.app.utils.OutboxManager
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val tempMessages: List<Message> = emptyList(),
    val messageListItems: List<MessageListItem> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val lastDoc: DocumentSnapshot? = null,
    val chat: Chat? = null,
    val chatType: ChatType = ChatType.DIRECT,
    val otherUser: UserProfile? = null,
    val myMember: Member? = null,
    val members: List<Member> = emptyList(),
    val topbarStatus: TopbarStatus = TopbarStatus.Offline,
    val onlineCount: Int = 0,
    val replyingTo: Message? = null,
    val isUploading: Boolean = false,
    val isCooldown: Boolean = false,
    val error: String? = null,
    val isRecording: Boolean = false,
    val stickers: List<Sticker> = emptyList(),
    val voicePlayback: VoicePlaybackState = VoicePlaybackState(),
    val musicPlayback: MusicPlayerState = MusicPlayerState(),
    val musicDownloadProgress: Map<String, Float> = emptyMap(),
    val wallpaperUrl: String? = null,
    val showUnofficialClientWarning: Boolean = false,
    val hasDismissedUnofficialWarning: Boolean = false,
    val albumDraft: List<AlbumImageLocal> = emptyList(),
    val albumCaption: String = "",
    val showAlbumPreview: Boolean = false,
    val singlePickedUri: Uri? = null,
    val initialDraft: String = "",
    val editingMessage: Message? = null,
    val currentUser: UserProfile? = null,
    val currentTopic: Topic? = null,
    val topicId: String? = null,
    val isBotGenerating: Boolean = false,
    val isJoiningChannel: Boolean = false,
    val availableChats: List<Chat> = emptyList()
) {
    val isChannelMember get() = chat?.let { isChannelMember(it, myMember?.role, currentUser?.uid ?: "") } ?: true
    val canPostToChannel get() = chat?.let { canPostToChannel(it, myMember?.role, currentUser?.uid ?: "") } ?: true
    val canSendMessage get() = if (chatType == ChatType.CHANNEL) canPostToChannel else canSendMessage(myMember, chatType)
    val canSendMedia get() = if (chatType == ChatType.CHANNEL) canPostToChannel else canSendMedia(myMember, chatType)
    val canReact get() = chat?.let { canReact(it, chatType) } ?: true
    val isAdmin get() = myMember?.isAdmin() ?: false
    val isOwner get() = myMember?.isOwner() ?: false
}

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val context: Application,
    private val draftManager: DraftManager,
    val chatId: String,
    val otherUid: String,
    val initialTopicId: String? = null,
    private val typingRepository: org.visorlink.app.data.repository.TypingRepository? = null,
    val musicPlayerManager: MusicPlayerManager,
    val musicRepository: MusicRepository,
    private val networkMonitor: org.visorlink.app.utils.NetworkMonitor? = null,
    private val usageRankManager: org.visorlink.app.utils.UsageRankManager? = null
) : AndroidViewModel(context) {

    val currentUid: String get() = auth.currentUser!!.uid
    private var currentUsername = ""

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val voicePlayer = VoicePlayerManager(context)

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStart = 0L
    private var typingManager: TypingManager? = null
    private var onlineCountListener: ValueEventListener? = null
    private var wallpaperListener: ListenerRegistration? = null
    private var chatDocListener: ListenerRegistration? = null
    private var topicListener: ListenerRegistration? = null
    private var rawMessages = listOf<Message>()
    private val lastOtherActiveTimeFlow = MutableStateFlow(0L)
    private var otherUserObservationJob: Job? = null
    private var observedOtherUid: String? = null
    private val activeUploadJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    val effectiveOtherUid: String
        get() = observedOtherUid
            ?: (if (otherUid != chatId && otherUid.isNotBlank()) otherUid else null)
            ?: _uiState.value.chat?.otherParticipantId(currentUid)
            ?: otherUid

    private var pendingMaxSeq = 0L

    private fun getSendMeta(): Triple<Long, Boolean, String?> {
        val chat = _uiState.value.chat
        val messages = if (rawMessages.isNotEmpty()) rawMessages else _uiState.value.messages
        val calculated = calculateNextSeq(chat, messages)
        val nextSeq = maxOf(calculated, pendingMaxSeq + 1L)
        pendingMaxSeq = nextSeq
        val isDirect = _uiState.value.chatType == ChatType.DIRECT || chat?.chatType() == ChatType.DIRECT
        val targetUserId = if (isDirect) effectiveOtherUid.takeIf { it.isNotBlank() && it != chatId } else null
        return Triple(nextSeq, isDirect, targetUserId)
    }

    fun startObservingOtherUser(targetUid: String) {
        if (targetUid.isBlank() || targetUid == currentUid || observedOtherUid == targetUid) return
        observedOtherUid = targetUid
        otherUserObservationJob?.cancel()
        otherUserObservationJob = viewModelScope.launch {
            _uiState.update { it.copy(chatType = ChatType.DIRECT) }

            launch {
                userRepository.userProfileFlow(targetUid).collect { profile ->
                    _uiState.update { it.copy(otherUser = profile) }
                }
            }

            launch {
                userRepository.clientStatusFlow(targetUid).collect { isOfficial ->
                    val state = _uiState.value
                    if (!isOfficial && !state.hasDismissedUnofficialWarning) {
                        _uiState.update { it.copy(showUnofficialClientWarning = true) }
                    } else if (isOfficial) {
                        _uiState.update { it.copy(showUnofficialClientWarning = false) }
                    }
                }
            }

            if (typingRepository != null) {
                launch {
                    typingRepository.observeBotTyping(chatId, targetUid).collect { isTyping ->
                        _uiState.update { state ->
                            if (state.otherUser?.isBot == true) {
                                state.copy(
                                    isBotGenerating = isTyping,
                                    topbarStatus = if (isTyping) TopbarStatus.Typing else state.topbarStatus
                                )
                            } else {
                                state
                            }
                        }
                    }
                }
            }

            val tickerFlow = flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    kotlinx.coroutines.delay(5000L)
                }
            }

            launch {
                val isOnlineFlow = networkMonitor?.isOnline ?: flowOf(true)
                combine(
                    PresenceManager.observePresence(targetUid).onStart { emit(PresenceData(online = false, lastSeen = null)) },
                    TypingManager.observeTyping(chatId, currentUid).onStart { emit(false) },
                    lastOtherActiveTimeFlow,
                    tickerFlow,
                    isOnlineFlow
                ) { presence, typing, activeTime, now, isDeviceOnline ->
                    if (!isDeviceOnline) {
                        TopbarStatus.WaitingForNetwork
                    } else if (typing) {
                        lastOtherActiveTimeFlow.update { maxOf(it, now) }
                        TopbarStatus.Typing
                    } else {
                        val isPresenceOnline = presence?.online == true
                        if (isPresenceOnline) {
                            lastOtherActiveTimeFlow.update { maxOf(it, now) }
                        }
                        val effectiveActiveTime = maxOf(activeTime, if (isPresenceOnline) now else 0L)
                        val isRecentlyActive = effectiveActiveTime > 0L && (now - effectiveActiveTime < 60_000L)

                        if (isPresenceOnline || isRecentlyActive) {
                            TopbarStatus.Online
                        } else {
                            val effectiveLastSeen = when {
                                presence?.lastSeen != null && effectiveActiveTime > 0L -> maxOf(presence.lastSeen, effectiveActiveTime)
                                presence?.lastSeen != null -> presence.lastSeen
                                effectiveActiveTime > 0L -> effectiveActiveTime
                                else -> null
                            }
                            TopbarStatus.LastSeen(effectiveLastSeen)
                        }
                    }
                }
                    .catch { e ->
                        Log.e("ChatViewModel", "Error in presence flow: ${e.message}", e)
                        emit(TopbarStatus.Offline)
                    }
                    .collect { status -> _uiState.update { it.copy(topbarStatus = status) } }
            }
        }
    }

    private fun filterByTopic(msgs: List<Message>, topic: Topic?, topicId: String?): List<Message> {
        val tid = topicId ?: topic?.id
        if (tid == null) return msgs
        val isGeneral = topic?.isGeneral == true || tid == "general"
        return msgs.filter { msg ->
            if (isGeneral) {
                msg.topicId.isNullOrEmpty() || msg.topicId == tid || msg.topicId == "general"
            } else {
                msg.topicId == tid
            }
        }
    }

    init {
        viewModelScope.launch {
            voicePlayer.state.collect { playbackState ->
                _uiState.update { it.copy(voicePlayback = playbackState) }
            }
        }

        viewModelScope.launch {
            musicPlayerManager.state.collect { musicState ->
                _uiState.update { it.copy(musicPlayback = musicState) }
            }
        }

        viewModelScope.launch {
            musicRepository.downloadProgress.collect { progressMap ->
                _uiState.update { it.copy(musicDownloadProgress = progressMap) }
            }
        }

        viewModelScope.launch {
            userRepository.currentUserFlow().catch { }
                .collect { profile ->
                    currentUsername = profile?.username ?: ""
                    _uiState.update { state ->
                        val updatedState = state.copy(currentUser = profile)
                        val filtered = filterByTopic(rawMessages, updatedState.currentTopic, updatedState.topicId ?: initialTopicId)
                        val all = filtered + filterByTopic(updatedState.tempMessages, updatedState.currentTopic, updatedState.topicId ?: initialTopicId)
                        updatedState.copy(messageListItems = buildMessageList(all, isAlreadySorted = false, currentUserProfile = profile))
                    }
                }
        }

        val draft = draftManager.getDraft(chatId)
        if (draft.isNotEmpty()) {
            _uiState.update { it.copy(initialDraft = draft) }
        }

        viewModelScope.launch {
            userRepository.stickersFlow(currentUid).collect { stickers ->
                _uiState.update { it.copy(stickers = stickers) }
            }
        }

        viewModelScope.launch {
            chatRepository.chatsFlow(currentUid).collect { chats ->
                _uiState.update { it.copy(availableChats = chats) }
            }
        }

        if (initialTopicId != null) {
            _uiState.update { it.copy(topicId = initialTopicId) }
            topicListener?.remove()
            topicListener = db.collection("chats").document(chatId).collection("topics").document(initialTopicId)
                .addSnapshotListener { snap, _ ->
                    if (snap != null && snap.exists()) {
                        val topic = try { snap.toObject(Topic::class.java)?.copy(id = snap.id) } catch (_: Exception) { null }
                        _uiState.update { state ->
                            val filtered = filterByTopic(rawMessages, topic, initialTopicId)
                            val items = buildMessageList(filtered + filterByTopic(state.tempMessages, topic, initialTopicId))
                            state.copy(currentTopic = topic, messages = filtered, messageListItems = items)
                        }
                    }
                }
        }

        viewModelScope.launch {
            val cachedChat = ChatDataCache.loadChat(context, chatId)
            if (cachedChat != null) {
                val type = cachedChat.chatType()
                _uiState.update { it.copy(chat = cachedChat, chatType = type) }
                if (type == ChatType.DIRECT) {
                    val resolvedUid = cachedChat.otherParticipantId(currentUid)
                    if (resolvedUid.isNotBlank()) {
                        startObservingOtherUser(resolvedUid)
                    }
                }
            }
        }

        if (otherUid != chatId && otherUid.isNotBlank()) {
            startObservingOtherUser(otherUid)
        }

        viewModelScope.launch {
            val exists = waitForChat()
            if (!exists) {
                _uiState.update { it.copy(error = "Failed to open chat") }
                return@launch
            }

            typingManager = TypingManager(chatId, currentUid)

            launch {
                _uiState.map { it.chatType }.distinctUntilChanged().collect { type ->
                    startWallpaperListener(type)
                }
            }

            chatDocListener?.remove()
            chatDocListener = db.collection("chats").document(chatId)
                .addSnapshotListener { snap, error ->
                    if (error != null || snap == null) return@addSnapshotListener
                    val chat = snap.toChatOrNull() ?: return@addSnapshotListener

                    val type = chat.chatType()
                    _uiState.update { it.copy(chat = chat, chatType = type) }

                    if (type == ChatType.DIRECT) {
                        val resolvedUid = chat.otherParticipantId(currentUid)
                        if (resolvedUid.isNotBlank()) {
                            startObservingOtherUser(resolvedUid)
                        }
                    } else {
                        startGroupOnlineCount(chat.memberIds)
                    }
                }

            launch {
                chatRepository.membersFlow(chatId).collect { members ->
                    _uiState.update { it.copy(members = members) }
                    val mine = members.find { it.uid == currentUid }
                    if (mine != null) _uiState.update { it.copy(myMember = mine) }
                }
            }

            launch {
                try {
                    val myMember = chatRepository.getMyMemberData(chatId)
                    if (myMember != null) _uiState.update { it.copy(myMember = myMember) }
                } catch (_: Exception) {}
            }

            val currentTarget = effectiveOtherUid
            if (currentTarget != chatId && currentTarget.isNotBlank()) {
                startObservingOtherUser(currentTarget)
            }

            launch(Dispatchers.Default) {
                chatRepository.latestMessagesFlow(chatId) { latestMessages, latestLastDoc ->
                    val targetOtherUid = effectiveOtherUid
                    if (targetOtherUid != chatId && targetOtherUid.isNotBlank()) {
                        val otherLatestMsg = latestMessages.filter { it.senderId == targetOtherUid }
                            .maxOfOrNull { (it.createdAt?.seconds ?: 0L) * 1000L } ?: 0L
                        if (otherLatestMsg > 0L) {
                            lastOtherActiveTimeFlow.update { maxOf(it, otherLatestMsg) }
                        }
                    }
                    val latestMap = latestMessages.associateBy { it.id }
                    val olderMessages = rawMessages.filter { it.id !in latestMap }
                    val combined = sortMessages((olderMessages + latestMessages).distinctBy { it.id })
                    rawMessages = combined
                    pendingMaxSeq = maxOf(pendingMaxSeq, combined.maxOfOrNull { it.seq ?: 0L } ?: 0L)

                    val currentState = _uiState.value
                    val filtered = filterByTopic(combined, currentState.currentTopic, currentState.topicId ?: initialTopicId)
                    val allMessages = filtered + filterByTopic(currentState.tempMessages, currentState.currentTopic, currentState.topicId ?: initialTopicId)
                    val items = buildMessageList(allMessages, isAlreadySorted = false)

                    val newLastDoc = if (olderMessages.isNotEmpty() && currentState.lastDoc != null) currentState.lastDoc else latestLastDoc
                    val newHasMore = if (olderMessages.isNotEmpty() && currentState.lastDoc != null) currentState.hasMore else (latestLastDoc != null)

                    _uiState.update { state ->
                        state.copy(
                            messages = filtered,
                            messageListItems = items,
                            lastDoc = newLastDoc,
                            hasMore = newHasMore
                        )
                    }

                    if (ActiveChatTracker.isChatActive(chatId)) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                chatRepository.markMessagesAsRead(chatId, latestMessages, currentUid)
                                chatRepository.resetUnreadCount(chatId, currentUid)
                                NotificationHelper.clearNotification(context, chatId)
                            }
                            catch (_: Exception) {}
                        }
                    }
                }.catch { }.collect()
            }

            launch {
                try {
                    chatRepository.resetUnreadCount(chatId, currentUid)
                    NotificationHelper.clearNotification(context, chatId)
                } catch (_: Exception) {}
            }
        }
    }

    private fun startCooldown(): Boolean {
        if (_uiState.value.isCooldown) return false
        viewModelScope.launch {
            _uiState.update { it.copy(isCooldown = true) }
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(isCooldown = false) }
        }
        return true
    }

    private fun startWallpaperListener(type: ChatType) {
        wallpaperListener?.remove()
        val docId = if (type == ChatType.GROUP || type == ChatType.CHANNEL) "shared" else currentUid
        wallpaperListener = db.collection("chats").document(chatId)
            .collection("wallpapers").document(docId)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                val url = snap?.getString("url")
                _uiState.update { it.copy(wallpaperUrl = url) }
            }
    }

    fun setWallpaper(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isUploading = true) }
                val type = _uiState.value.chatType
                val docId = if (type == ChatType.GROUP || type == ChatType.CHANNEL) "shared" else currentUid

                val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("chats/$chatId/wallpapers/$docId.jpg")
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                val data = hashMapOf(
                    "url" to downloadUrl,
                    "setBy" to currentUid,
                    "setAt" to FieldValue.serverTimestamp()
                )
                db.collection("chats").document(chatId)
                    .collection("wallpapers").document(docId)
                    .set(data).await()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to set wallpaper: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    fun removeWallpaper() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isUploading = true) }
                val type = _uiState.value.chatType
                val docId = if (type == ChatType.GROUP || type == ChatType.CHANNEL) "shared" else currentUid
                db.collection("chats").document(chatId).collection("wallpapers").document(docId).delete().await()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to remove wallpaper: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            _uiState.update { it.copy(singlePickedUri = uris.first()) }
            return
        }
        val items = uris.take(10).map { AlbumImageLocal(uri = it) }
        _uiState.update {
            it.copy(
                albumDraft = items,
                albumCaption = "",
                showAlbumPreview = true
            )
        }
    }

    fun onAlbumSpoilerToggle(index: Int) {
        val current = _uiState.value.albumDraft.toMutableList()
        if (index !in current.indices) return
        current[index] = current[index].copy(spoiler = !current[index].spoiler)
        _uiState.update { it.copy(albumDraft = current) }
    }

    fun onAlbumCaptionChange(text: String) {
        if (text.length <= 500) _uiState.update { it.copy(albumCaption = text) }
    }

    fun dismissAlbumPreview() {
        _uiState.update { it.copy(showAlbumPreview = false, albumDraft = emptyList(), albumCaption = "") }
    }

    fun clearSinglePickedUri() {
        _uiState.update { it.copy(singlePickedUri = null) }
    }

    fun sendAlbum() {
        val draft = _uiState.value.albumDraft
        val caption = _uiState.value.albumCaption.trim().ifEmpty { null }
        val reply = _uiState.value.replyingTo?.toReplyData()
        if (draft.isEmpty() || !_uiState.value.canSendMedia) return
        if (!startCooldown()) return

        val tempId = "temp_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val activeTopicId = _uiState.value.topicId ?: initialTopicId

        val tempMsg = Message(
            id = tempId,
            senderId = currentUid,
            senderUsername = currentUsername,
            type = MessageType.ALBUM,
            topicId = activeTopicId,
            caption = caption,
            images = draft.map { AlbumImage(url = it.uri.toString(), spoiler = it.spoiler) },
            uploadProgress = 0.01f,
            createdAt = com.google.firebase.Timestamp.now(),
            readBy = listOf(currentUid),
            deleted = false,
            replyTo = reply?.let { mapOf("id" to it.id, "type" to it.type, "text" to it.text, "url" to it.url, "senderUsername" to it.senderUsername) },
            status = SendStatus.SENDING
        )

        _uiState.update { state ->
            val newTemp = state.tempMessages + tempMsg
            state.copy(
                tempMessages = newTemp,
                messageListItems = buildMessageList(state.messages + newTemp),
                isUploading = true,
                showAlbumPreview = false,
                albumDraft = emptyList(),
                albumCaption = ""
            )
        }
        clearReply()

        val job = viewModelScope.launch {
            try {
                val uploaded = chatRepository.uploadAlbumImages(chatId, draft) { progress ->
                    _uiState.update { state ->
                        val updatedTemp = state.tempMessages.map {
                            if (it.id == tempId) it.copy(uploadProgress = progress) else it
                        }
                        state.copy(
                            tempMessages = updatedTemp,
                            messageListItems = buildMessageList(state.messages + updatedTemp)
                        )
                    }
                }
                chatRepository.sendAlbum(chatId, uploaded, caption, reply)
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("ChatViewModel", "Failed to send album", e)
                    _uiState.update { it.copy(error = "Ошибка отправки альбома: ${e.message}") }
                }
            } finally {
                activeUploadJobs.remove(tempId)
                _uiState.update { state ->
                    val cleanTemp = state.tempMessages.filter { it.id != tempId }
                    state.copy(
                        isUploading = false,
                        tempMessages = cleanTemp,
                        messageListItems = buildMessageList(state.messages + cleanTemp)
                    )
                }
            }
        }
        activeUploadJobs[tempId] = job
    }

    fun sendVideoOrGif(file: java.io.File, isGif: Boolean) {
        if (!_uiState.value.canSendMedia) return
        if (!startCooldown()) return

        val type = if (isGif) MessageType.GIF else MessageType.VIDEO
        val tempId = "temp_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val reply = _uiState.value.replyingTo?.toReplyData()

        val activeTopicId = _uiState.value.topicId ?: initialTopicId
        val tempMsg = Message(
            id = tempId,
            senderId = currentUid,
            senderUsername = currentUsername,
            type = type,
            topicId = activeTopicId,
            localFile = file,
            uploadProgress = 0.01f,
            createdAt = com.google.firebase.Timestamp.now(),
            readBy = listOf(currentUid),
            deleted = false,
            replyTo = reply?.let { mapOf("id" to it.id, "type" to it.type, "text" to it.text, "url" to it.url, "senderUsername" to it.senderUsername) },
            status = SendStatus.SENDING
        )

        _uiState.update { state ->
            val newTemp = state.tempMessages + tempMsg
            state.copy(
                tempMessages = newTemp,
                messageListItems = buildMessageList(state.messages + newTemp)
            )
        }

        clearReply()

        val job = viewModelScope.launch {
            try {
                chatRepository.sendVideo(
                    chatId = chatId,
                    uri = Uri.fromFile(file),
                    senderUsername = currentUsername,
                    replyTo = reply,
                    topicId = activeTopicId
                )
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _uiState.update { it.copy(error = "Ошибка отправки видео: ${e.message}") }
                }
            } finally {
                activeUploadJobs.remove(tempId)
                _uiState.update { state ->
                    val cleanTemp = state.tempMessages.filter { it.id != tempId }
                    state.copy(
                        tempMessages = cleanTemp,
                        messageListItems = buildMessageList(state.messages + cleanTemp)
                    )
                }
            }
        }
        activeUploadJobs[tempId] = job
    }

    fun playVoice(messageId: String, url: String, durationSec: Int) {
        viewModelScope.launch {
            val effectiveUrl = if (url.isNotBlank()) {
                url
            } else {
                val msg = _uiState.value.messages.firstOrNull { it.id == messageId }
                msg?.url ?: ""
            }
            if (effectiveUrl.isNotBlank()) {
                voicePlayer.play(messageId, effectiveUrl, durationSec)
            }
        }
    }

    fun toggleVoice() {
        voicePlayer.togglePlayPause()
    }

    fun seekVoice(fraction: Float) {
        voicePlayer.seekTo(fraction)
    }

    fun stopVoice() {
        voicePlayer.stop()
    }

    private fun startGroupOnlineCount(memberIds: List<String>) {
        onlineCountListener?.let {
            FirebaseDatabase.getInstance().getReference("presence").removeEventListener(it)
        }
        val presenceRef = FirebaseDatabase.getInstance().getReference("presence")
        onlineCountListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val count = memberIds.count { uid ->
                    uid != currentUid &&
                            snap.child(uid).child("online").getValue(Boolean::class.java) == true
                }
                _uiState.update { it.copy(onlineCount = count) }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        presenceRef.addValueEventListener(onlineCountListener!!)
    }

    fun loadMore() {
        var currentLastDoc: DocumentSnapshot? = null

        // Защита от дублей: блокируем дальнейшие вызовы пока грузим
        _uiState.update { state ->
            if (!state.hasMore || state.isLoadingMore || state.lastDoc == null) {
                return@update state
            }
            currentLastDoc = state.lastDoc
            state.copy(isLoadingMore = true)
        }

        if (currentLastDoc == null) return

        viewModelScope.launch {
            try {
                val (older, newLastDoc) = chatRepository.loadOlderMessages(chatId, currentLastDoc!!)

                _uiState.update { currentState ->
                    val olderFiltered = older.filter { oldMsg ->
                        rawMessages.none { it.id == oldMsg.id }
                    }
                    val combined = sortMessages(olderFiltered + rawMessages)
                    rawMessages = combined

                    val filtered = filterByTopic(combined, currentState.currentTopic, currentState.topicId ?: initialTopicId)
                    val allMessages = filtered + filterByTopic(currentState.tempMessages, currentState.currentTopic, currentState.topicId ?: initialTopicId)

                    currentState.copy(
                        messages = filtered,
                        messageListItems = buildMessageList(allMessages),
                        isLoadingMore = false,
                        hasMore = newLastDoc != null,
                        lastDoc = newLastDoc ?: currentState.lastDoc
                    )
                }

                if (ActiveChatTracker.isChatActive(chatId)) {
                    try {
                        chatRepository.markMessagesAsRead(chatId, older, currentUid)
                        NotificationHelper.clearNotification(context, chatId)
                    }
                    catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Pagination error", e)
                _uiState.update { it.copy(isLoadingMore = false, error = "Ошибка загрузки: ${e.message}") }
            }
        }
    }

    private fun buildMessageList(
        allMessages: List<Message>,
        isAlreadySorted: Boolean = false,
        currentUserProfile: UserProfile? = _uiState.value.currentUser
    ): List<MessageListItem> {
        val blockedUids = currentUserProfile?.blockedUserIds ?: emptyList()
        val visibleMessages = if (blockedUids.isEmpty()) {
            allMessages
        } else {
            allMessages.filter { it.senderUid !in blockedUids }
        }
        val deduplicated = visibleMessages.distinctBy { it.id }
        val sorted = if (isAlreadySorted) deduplicated else sortMessages(deduplicated)
        val result = ArrayList<MessageListItem>(sorted.size + 10)
        var lastDate: LocalDate? = null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())

        for (message in sorted) {
            val createdAt = message.createdAt ?: continue
            val epochMilli = (createdAt.seconds * 1000L) + (createdAt.nanoseconds / 1_000_000L)
            val msgDate = Instant.ofEpochMilli(epochMilli).atZone(zone).toLocalDate()
            if (msgDate != lastDate) {
                val label = when (msgDate) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> msgDate.format(dateFormatter)
                }
                result.add(MessageListItem.DateHeader(label))
                lastDate = msgDate
            }
            result.add(MessageListItem.MessageItem(message))
        }
        return result
    }

    fun blockUser(targetUid: String) {
        viewModelScope.launch {
            try {
                userRepository.blockUser(targetUid)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to block user $targetUid", e)
            }
        }
    }

    fun unblockUser(targetUid: String) {
        viewModelScope.launch {
            try {
                userRepository.unblockUser(targetUid)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to unblock user $targetUid", e)
            }
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            OutboxManager.retry(messageId)
        }
    }

    fun onTextChanged(text: String) {
        if (text.isNotEmpty()) typingManager?.onTyping()
        else typingManager?.stopTyping()
        if (_uiState.value.editingMessage == null) {
            draftManager.saveDraft(chatId, text)
        }
    }

    fun sendText(text: String) {
        if (!_uiState.value.canSendMessage) return
        val trimmed = text.trim().ifEmpty { return }

        if (trimmed.length > 2000) return
        if (!startCooldown()) return

        val reply = _uiState.value.replyingTo?.toReplyData()
        val activeTopicId = _uiState.value.topicId ?: initialTopicId
        val (nextSeq, isDirect, targetUserId) = getSendMeta()

        viewModelScope.launch {
            typingManager?.stopTyping()
            clearReply()
            draftManager.clearDraft(chatId)
            try {
                chatRepository.sendText(
                    chatId = chatId,
                    text = trimmed,
                    senderUsername = currentUsername,
                    replyTo = reply,
                    topicId = activeTopicId,
                    nextSeq = nextSeq,
                    isDirect = isDirect,
                    otherUserId = targetUserId
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun sendImage(uri: Uri, isSpoiler: Boolean = false) {
        if (!_uiState.value.canSendMedia) return
        if (!startCooldown()) return

        val reply = _uiState.value.replyingTo?.toReplyData()
        val activeTopicId = _uiState.value.topicId ?: initialTopicId
        val (nextSeq, isDirect, targetUserId) = getSendMeta()
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            clearReply()
            try {
                chatRepository.sendImage(
                    chatId = chatId,
                    uri = uri,
                    senderUsername = currentUsername,
                    replyTo = reply,
                    isSpoiler = isSpoiler,
                    topicId = activeTopicId,
                    nextSeq = nextSeq,
                    isDirect = isDirect,
                    otherUserId = targetUserId
                )
            }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
            finally { _uiState.update { it.copy(isUploading = false) } }
        }
    }

    fun sendVideo(uri: Uri) {
        if (!_uiState.value.canSendMedia) return
        if (!startCooldown()) return

        val reply = _uiState.value.replyingTo?.toReplyData()
        val activeTopicId = _uiState.value.topicId ?: initialTopicId
        val (nextSeq, isDirect, targetUserId) = getSendMeta()
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            clearReply()
            try {
                chatRepository.sendVideo(
                    chatId = chatId,
                    uri = uri,
                    senderUsername = currentUsername,
                    replyTo = reply,
                    topicId = activeTopicId,
                    nextSeq = nextSeq,
                    isDirect = isDirect,
                    otherUserId = targetUserId
                )
            }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
            finally { _uiState.update { it.copy(isUploading = false) } }
        }
    }

    fun sendSticker(sticker: StickerItem, packId: String, packName: String, packEmoji: String) {
        if (!_uiState.value.canSendMessage) return
        if (!startCooldown()) return

        usageRankManager?.recordPackUsage(packId)

        val reply = _uiState.value.replyingTo?.toReplyData()
        val activeTopicId = _uiState.value.topicId ?: initialTopicId
        val (nextSeq, isDirect, targetUserId) = getSendMeta()
        viewModelScope.launch {
            clearReply()
            try {
                chatRepository.sendSticker(
                    chatId = chatId,
                    sticker = sticker,
                    packId = packId,
                    packName = packName,
                    packEmoji = packEmoji,
                    senderUsername = currentUsername,
                    replyTo = reply,
                    topicId = activeTopicId,
                    nextSeq = nextSeq,
                    isDirect = isDirect,
                    otherUserId = targetUserId
                )
            } catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun startRecording() {
        if (!_uiState.value.canSendMedia) return
        voicePlayer.stop()

        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.webm")
        recordingFile = file
        recordingStart = System.currentTimeMillis()
        @Suppress("DEPRECATION")
        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(context) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.WEBM)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)

            setAudioChannels(1)
            setAudioSamplingRate(48000)
            setAudioEncodingBitRate(96000)

            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        _uiState.update { it.copy(isRecording = true) }
    }

    fun stopRecordingAndSend() {
        val file = recordingFile ?: return
        val duration = ((System.currentTimeMillis() - recordingStart) / 1000).toInt().coerceAtLeast(1)
        try { recorder?.apply { stop(); release() } } catch (_: Exception) {}
        recorder = null
        val reply = _uiState.value.replyingTo?.toReplyData()
        _uiState.update { it.copy(isRecording = false) }

        if (!startCooldown()) return

        val activeTopicId = _uiState.value.topicId ?: initialTopicId
        val (nextSeq, isDirect, targetUserId) = getSendMeta()
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            clearReply()
            try {
                chatRepository.sendVoice(
                    chatId = chatId,
                    file = file,
                    durationSec = duration,
                    senderUsername = currentUsername,
                    replyTo = reply,
                    topicId = activeTopicId,
                    nextSeq = nextSeq,
                    isDirect = isDirect,
                    otherUserId = targetUserId
                )
            }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
            finally { _uiState.update { it.copy(isUploading = false) } }
        }
    }

    fun sendAudio(file: File, title: String, performer: String, durationSec: Int, coverFile: File?) {
        val reply = _uiState.value.replyingTo?.toReplyData()
        val activeTopicId = _uiState.value.topicId ?: initialTopicId
        val (nextSeq, isDirect, targetUserId) = getSendMeta()
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            clearReply()
            try {
                chatRepository.sendAudio(
                    chatId = chatId,
                    file = file,
                    title = title,
                    performer = performer,
                    durationSec = durationSec,
                    coverFile = coverFile,
                    senderUsername = currentUsername,
                    replyTo = reply,
                    topicId = activeTopicId,
                    nextSeq = nextSeq,
                    isDirect = isDirect,
                    otherUserId = targetUserId
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    fun cancelRecording() {
        try { recorder?.apply { stop(); release() } } catch (_: Exception) {}
        recorder = null
        recordingFile?.delete()
        recordingFile = null
        _uiState.update { it.copy(isRecording = false) }
    }

    fun cancelSending(messageId: String) {
        activeUploadJobs.remove(messageId)?.cancel()
        _uiState.update { state ->
            val cleanTemp = state.tempMessages.filter { it.id != messageId }
            state.copy(
                tempMessages = cleanTemp,
                messageListItems = buildMessageList(state.messages + cleanTemp)
            )
        }
        viewModelScope.launch {
            try {
                chatRepository.cancelSending(messageId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteMessage(chatId, messageId)

                // Optimistic Local Update
                _uiState.update { state ->
                    val newMessages = state.messages.map {
                        if (it.id == messageId) it.copy(deleted = true) else it
                    }
                    state.copy(
                        messages = newMessages,
                        messageListItems = buildMessageList(newMessages + state.tempMessages)
                    )
                }
            }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun toggleReaction(messageId: String, emoji: String, currentReactions: List<Reaction>) {
        if (!_uiState.value.canReact) return
        val existingReaction = currentReactions.find { it.emoji == emoji }
        val isAdding = existingReaction == null || currentUid !in existingReaction.uids
        if (isAdding) {
            usageRankManager?.recordReactionUsage(emoji)
        }
        viewModelScope.launch {
            try {
                chatRepository.toggleReaction(chatId, messageId, emoji, currentReactions)

                // Optimistic Local Update
                _uiState.update { state ->
                    val updatedReactions = currentReactions.toMutableList()
                    val existing = updatedReactions.find { it.emoji == emoji }
                    if (existing != null) {
                        if (currentUid in existing.uids) {
                            val newUids = existing.uids - currentUid
                            if (newUids.isEmpty()) updatedReactions.remove(existing)
                            else updatedReactions[updatedReactions.indexOf(existing)] = existing.copy(uids = newUids, count = newUids.size)
                        } else {
                            updatedReactions[updatedReactions.indexOf(existing)] = existing.copy(uids = existing.uids + currentUid, count = existing.count + 1)
                        }
                    } else {
                        updatedReactions.add(Reaction(emoji, listOf(currentUid), 1))
                    }

                    val newMessages = state.messages.map { msg ->
                        if (msg.id == messageId) {
                            val newRaw = updatedReactions.map { it.toMap() }
                            msg.copy(reactions = newRaw)
                        } else msg
                    }
                    state.copy(
                        messages = newMessages,
                        messageListItems = buildMessageList(newMessages + state.tempMessages)
                    )
                }
            }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun moderateUser(targetUid: String, action: String, durationMinutes: Int? = null) {
        viewModelScope.launch {
            try { chatRepository.moderateUser(chatId, targetUid, action, durationMinutes) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun setMemberRole(targetUid: String, role: String) {
        viewModelScope.launch {
            try { chatRepository.setMemberRole(chatId, targetUid, role) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun leaveChat(onLeft: () -> Unit) {
        viewModelScope.launch {
            try { chatRepository.leaveChat(chatId); onLeft() }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun joinChannel(onJoined: (() -> Unit)? = null) {
        val chat = _uiState.value.chat ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isJoiningChannel = true) }
            try {
                chatRepository.joinChannel(chat.id, chat.tag)
                onJoined?.invoke()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isJoiningChannel = false) }
            }
        }
    }

    fun setReplyTo(message: Message) = _uiState.update { it.copy(replyingTo = message, editingMessage = null) }
    fun clearReply() = _uiState.update { it.copy(replyingTo = null) }

    fun startEditing(message: Message) {
        val text = if (message.type != MessageType.TEXT) message.caption ?: "" else message.text ?: ""
        _uiState.update { it.copy(editingMessage = message, replyingTo = null, initialDraft = text) }
    }

    fun cancelEditing() {
        draftManager.clearDraft(chatId)
        _uiState.update { it.copy(editingMessage = null, initialDraft = "") }
    }

    fun saveEdit(newText: String) {
        val msg = _uiState.value.editingMessage ?: return
        val isCaption = msg.type != MessageType.TEXT
        val oldText = if (isCaption) msg.caption ?: "" else msg.text ?: ""
        
        draftManager.clearDraft(chatId)
        if (newText.trim() == oldText.trim()) {
            cancelEditing()
            return
        }

        viewModelScope.launch {
            try {
                chatRepository.editMessage(chatId, msg.id, newText.trim(), oldText, isCaption)
                _uiState.update { state ->
                    val updated = state.messages.map {
                        if (it.id == msg.id) {
                            if (isCaption) it.copy(caption = newText.trim(), lastEdited = Timestamp.now())
                            else it.copy(text = newText.trim(), lastEdited = Timestamp.now())
                        } else it
                    }
                    state.copy(
                        messages = updated,
                        messageListItems = buildMessageList(updated + state.tempMessages),
                        editingMessage = null,
                        initialDraft = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        otherUserObservationJob?.cancel()
        typingManager?.cleanup()
        onlineCountListener?.let {
            FirebaseDatabase.getInstance().getReference("presence").removeEventListener(it)
        }
        onlineCountListener = null
        wallpaperListener?.remove()
        wallpaperListener = null
        topicListener?.remove()
        topicListener = null
        chatDocListener?.remove()
        chatDocListener = null
        voicePlayer.release()
        super.onCleared()
    }

    private suspend fun waitForChat(): Boolean {
        repeat(12) {
            try { if (chatRepository.chatExists(chatId)) return true }
            catch (_: Exception) {}
            kotlinx.coroutines.delay(500)
        }
        return false
    }

    private fun Message.toReplyData() = ReplyData(id, type, text, url, senderUsername)
    fun dismissUnofficialWarning() {
        _uiState.update {
            it.copy(
                showUnofficialClientWarning = false,
                hasDismissedUnofficialWarning = true
            )
        }
    }

    fun playAudio(message: Message) {
        viewModelScope.launch {
            val resolvedUrl = message.url ?: ""
            val resolvedCoverUrl = message.coverUrl
            val track = MusicTrack(
                id = message.id,
                title = message.title?.ifBlank { message.fileName ?: "Аудиозапись" } ?: message.fileName ?: "Аудиозапись",
                performer = message.performer?.ifBlank { "Неизвестный исполнитель" } ?: "Неизвестный исполнитель",
                duration = message.duration ?: 0,
                fileSize = message.fileSize ?: 0L,
                url = resolvedUrl,
                cdnMediaId = message.cdnMediaId,
                coverUrl = resolvedCoverUrl,
                coverCdnMediaId = message.coverCdnMediaId,
                sourceType = MusicTrack.SOURCE_CHAT,
                chatId = chatId,
                messageId = message.id
            )
            musicPlayerManager.playTrack(track)
        }
    }

    fun toggleAudioPlayback() {
        musicPlayerManager.togglePlayPause()
    }

    fun seekAudio(fraction: Float) {
        musicPlayerManager.seekTo(fraction)
    }

    fun cycleAudioSpeed() {
        musicPlayerManager.cycleSpeed()
    }

    fun stopAudio() {
        musicPlayerManager.stop()
    }

    fun openFullscreenAudio() {
        musicPlayerManager.openFullscreenPlayer()
    }

    fun saveTrackToLibrary(track: MusicTrack) {
        viewModelScope.launch {
            musicRepository.saveTrack(track)
        }
    }
}