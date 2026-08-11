package by.iposdev.visorlink.ui.screens.chat

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.utils.ActiveChatTracker
import by.iposdev.visorlink.utils.CdnService
import by.iposdev.visorlink.utils.PresenceManager
import by.iposdev.visorlink.utils.DraftManager
import by.iposdev.visorlink.utils.NotificationHelper
import by.iposdev.visorlink.utils.TypingManager
import by.iposdev.visorlink.utils.VoicePlayerManager
import by.iposdev.visorlink.utils.VoicePlaybackState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
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
    val wallpaperUrl: String? = null,
    val showUnofficialClientWarning: Boolean = false,
    val hasDismissedUnofficialWarning: Boolean = false,
    val albumDraft: List<AlbumImageLocal> = emptyList(),
    val albumCaption: String = "",
    val showAlbumPreview: Boolean = false,
    val singlePickedUri: Uri? = null,
    val initialDraft: String = "",
    val currentUser: UserProfile? = null
) {
    val canSendMessage get() = canSendMessage(myMember, chatType)
    val canSendMedia get() = canSendMedia(myMember, chatType)
    val canReact get() = chat?.let { canReact(it, chatType) } ?: true
    val isAdmin get() = myMember?.isAdmin() ?: false
    val isOwner get() = myMember?.isOwner() ?: false
}

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val context: Context,
    private val draftManager: DraftManager,
    val chatId: String,
    val otherUid: String
) : ViewModel() {

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

    init {
        viewModelScope.launch {
            voicePlayer.state.collect { playbackState ->
                _uiState.update { it.copy(voicePlayback = playbackState) }
            }
        }

        viewModelScope.launch {
            userRepository.currentUserFlow().catch { }
                .collect { profile ->
                    currentUsername = profile?.username ?: ""
                    _uiState.update { it.copy(currentUser = profile) }
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

            launch {
                db.collection("chats").document(chatId)
                    .addSnapshotListener { snap, error ->
                        if (error != null || snap == null) return@addSnapshotListener
                        val chat = try {
                            snap.toObject(Chat::class.java)?.copy(id = chatId)
                        } catch (e: Exception) { null } ?: return@addSnapshotListener

                        val type = chat.chatType()
                        _uiState.update { it.copy(chat = chat, chatType = type) }

                        if (type != ChatType.DIRECT) {
                            startGroupOnlineCount(chat.memberIds)
                        }
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

            if (otherUid != chatId) {
                _uiState.update { it.copy(chatType = ChatType.DIRECT) }

                launch {
                    userRepository.userProfileFlow(otherUid).collect { profile ->
                        _uiState.update { it.copy(otherUser = profile) }
                    }
                }

                launch {
                    launch {
                        userRepository.clientStatusFlow(otherUid).collect { isOfficial ->
                            val state = _uiState.value
                            if (!isOfficial && !state.hasDismissedUnofficialWarning) {
                                _uiState.update { it.copy(showUnofficialClientWarning = true) }
                            } else if (isOfficial) {
                                _uiState.update { it.copy(showUnofficialClientWarning = false) }
                            }
                        }
                    }
                    combine(
                        PresenceManager.observePresence(otherUid).filterNotNull(),
                        TypingManager.observeTyping(chatId, currentUid)
                    ) { presence, typing ->
                        when {
                            typing -> TopbarStatus.Typing
                            presence.online -> TopbarStatus.Online
                            else -> TopbarStatus.LastSeen(presence.lastSeen)
                        }
                    }
                        .catch { emit(TopbarStatus.Offline) }
                        .collect { status -> _uiState.update { it.copy(topbarStatus = status) } }
                }
            }

            launch {
                chatRepository.latestMessagesFlow(chatId) { latestMessages, latestLastDoc ->
                    _uiState.update { state ->
                        val latestMap = latestMessages.associateBy { it.id }
                        val olderMessages = state.messages.filter { it.id !in latestMap }
                        val combined = (olderMessages + latestMessages).sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }

                        val allMessages = combined + state.tempMessages
                        val items = buildMessageList(allMessages)

                        // ИСПРАВЛЕНИЕ: Восстанавливаем курсор из сети (latestLastDoc), если кэш (state.lastDoc) пустой
                        val newLastDoc = if (olderMessages.isNotEmpty() && state.lastDoc != null) state.lastDoc else latestLastDoc
                        val newHasMore = if (olderMessages.isNotEmpty() && state.lastDoc != null) state.hasMore else (latestLastDoc != null)

                        state.copy(
                            messages = combined,
                            messageListItems = items,
                            lastDoc = newLastDoc,
                            hasMore = newHasMore
                        )
                    }

                    if (ActiveChatTracker.activeChatId == chatId) {
                        viewModelScope.launch {
                            try {
                                chatRepository.markMessagesAsRead(chatId, latestMessages, currentUid)
                                NotificationHelper.clearNotification(context, chatId)
                            }
                            catch (_: Exception) {}
                        }
                    }
                }.catch { }.collect()
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

                val tempFile = java.io.File(context.cacheDir, "wallpaper_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }

                val mediaId = CdnService.uploadFile(tempFile, "image/jpeg", isVault = false)
                tempFile.delete()

                val downloadUrl = "${CdnService.BASE_URL}/p/$mediaId"
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

        _uiState.update { it.copy(isUploading = true, showAlbumPreview = false) }
        clearReply()

        viewModelScope.launch {
            try {
                val uploaded = chatRepository.uploadAlbumImages(chatId, draft)
                chatRepository.sendAlbum(chatId, uploaded, caption, reply)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to send album: ${e.message}") }
            } finally {
                _uiState.update {
                    it.copy(isUploading = false, albumDraft = emptyList(), albumCaption = "")
                }
            }
        }
    }

    fun sendVideoOrGif(file: java.io.File, isGif: Boolean) {
        if (!_uiState.value.canSendMedia) return
        if (!startCooldown()) return

        val type = if (isGif) MessageType.GIF else MessageType.VIDEO
        val tempId = "temp_${System.currentTimeMillis()}"
        val reply = _uiState.value.replyingTo?.toReplyData()

        val tempMsg = Message(
            id = tempId,
            senderId = currentUid,
            senderUsername = currentUsername,
            type = type,
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

        viewModelScope.launch {
            try {
                val mediaId = CdnService.uploadFile(file, if (isGif) "image/gif" else "video/mp4", isVault = false) { progress ->
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

                val msgRef = db.collection("chats").document(chatId).collection("messages").document()
                val batch = db.batch()

                batch.set(msgRef, mapOf(
                    "senderId" to currentUid,
                    "senderUsername" to currentUsername,
                    "type" to type,
                    "cdnMediaId" to mediaId,
                    "fileName" to file.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "readBy" to listOf(currentUid),
                    "deleted" to false,
                    "replyTo" to tempMsg.replyTo
                ))

                val preview = if (isGif) "🖼️ GIF" else "🎥 Видео"
                batch.update(db.collection("chats").document(chatId), mapOf(
                    "lastMessage" to preview,
                    "lastMessageAt" to FieldValue.serverTimestamp()
                ))

                batch.update(db.collection("users").document(currentUid), "lastMessageAt", FieldValue.serverTimestamp())
                batch.commit().await()

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка CDN: ${e.message}") }
            } finally {
                _uiState.update { state ->
                    val cleanTemp = state.tempMessages.filter { it.id != tempId }
                    state.copy(
                        tempMessages = cleanTemp,
                        messageListItems = buildMessageList(state.messages + cleanTemp)
                    )
                }
            }
        }
    }

    fun playVoice(messageId: String, url: String, durationSec: Int) {
        voicePlayer.play(messageId, url, durationSec)
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
                        currentState.messages.none { it.id == oldMsg.id }
                    }
                    val combined = (olderFiltered + currentState.messages).sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }

                    currentState.copy(
                        messages = combined,
                        messageListItems = buildMessageList(combined + currentState.tempMessages),
                        isLoadingMore = false,
                        hasMore = newLastDoc != null,
                        lastDoc = newLastDoc ?: currentState.lastDoc
                    )
                }

                if (ActiveChatTracker.activeChatId == chatId) {
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

    private fun buildMessageList(allMessages: List<Message>): List<MessageListItem> {
        val result = mutableListOf<MessageListItem>()
        var lastDate: LocalDate? = null
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        for (message in allMessages) {
            val msgDate = message.createdAt?.toDate()
                ?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: continue
            if (msgDate != lastDate) {
                val label = when (msgDate) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> msgDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
                }
                result.add(MessageListItem.DateHeader(label))
                lastDate = msgDate
            }
            result.add(MessageListItem.MessageItem(message))
        }
        return result
    }

    fun onTextChanged(text: String) {
        if (text.isNotEmpty()) typingManager?.onTyping()
        else typingManager?.stopTyping()
        draftManager.saveDraft(chatId, text)
    }

    fun sendText(text: String) {
        if (!_uiState.value.canSendMessage) return
        val trimmed = text.trim().ifEmpty { return }

        if (trimmed.length > 2000) return
        if (!startCooldown()) return

        val reply = _uiState.value.replyingTo?.toReplyData()
        viewModelScope.launch {
            typingManager?.stopTyping()
            clearReply()
            draftManager.clearDraft(chatId)
            try { chatRepository.sendText(chatId, trimmed, currentUsername, reply) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun sendImage(uri: Uri, isSpoiler: Boolean = false) {
        if (!_uiState.value.canSendMedia) return
        if (!startCooldown()) return

        val reply = _uiState.value.replyingTo?.toReplyData()
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            clearReply()
            try { chatRepository.sendImage(chatId, uri, currentUsername, reply, isSpoiler) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
            finally { _uiState.update { it.copy(isUploading = false) } }
        }
    }

    fun sendSticker(sticker: StickerItem, packId: String, packName: String, packEmoji: String) {
        if (!_uiState.value.canSendMessage) return
        if (!startCooldown()) return

        val reply = _uiState.value.replyingTo?.toReplyData()
        viewModelScope.launch {
            clearReply()
            try {
                chatRepository.sendSticker(chatId, sticker, packId, packName, packEmoji, currentUsername, reply)
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

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            clearReply()
            try { chatRepository.sendVoice(chatId, file, duration, currentUsername, reply) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
            finally { _uiState.update { it.copy(isUploading = false) }; file.delete() }
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

    fun setReplyTo(message: Message) = _uiState.update { it.copy(replyingTo = message) }
    fun clearReply() = _uiState.update { it.copy(replyingTo = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        typingManager?.cleanup()
        onlineCountListener?.let {
            FirebaseDatabase.getInstance().getReference("presence").removeEventListener(it)
        }
        wallpaperListener?.remove()
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
}