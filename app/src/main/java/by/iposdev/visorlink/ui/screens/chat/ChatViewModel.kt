package by.iposdev.visorlink.ui.screens.chat

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.utils.PresenceManager
import by.iposdev.visorlink.utils.TypingManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

data class ChatUiState(
    // Messages
    val messages: List<Message> = emptyList(),
    val messageListItems: List<MessageListItem> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val lastDoc: DocumentSnapshot? = null,
    // Chat info
    val chat: Chat? = null,
    val chatType: ChatType = ChatType.DIRECT,
    val otherUser: UserProfile? = null,
    // Member state
    val myMember: Member? = null,
    val members: List<Member> = emptyList(),
    // Topbar
    val topbarStatus: TopbarStatus = TopbarStatus.Offline,
    val onlineCount: Int = 0,
    // Input
    val replyingTo: Message? = null,
    val isUploading: Boolean = false,
    val error: String? = null,
    val isRecording: Boolean = false,
    // Stickers
    val stickers: List<Sticker> = emptyList()
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
    private val db: com.google.firebase.firestore.FirebaseFirestore,  // ← добавили
    private val context: Context,
    val chatId: String,
    val otherUid: String
) : ViewModel() {

    val currentUid: String get() = auth.currentUser!!.uid
    private var currentUsername = ""

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStart = 0L
    private var typingManager: TypingManager? = null
    private var onlineCountListener: ValueEventListener? = null

    init {
        viewModelScope.launch {
            userRepository.currentUserFlow().catch { }
                .collect { currentUsername = it?.username ?: "" }
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

            // ── Слушаем документ чата ─────────────────────────────────────────
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

            // ── Members ───────────────────────────────────────────────────────
            launch {
                chatRepository.membersFlow(chatId).collect { members ->
                    _uiState.update { it.copy(members = members) }
                    val mine = members.find { it.uid == currentUid }
                    if (mine != null) _uiState.update { it.copy(myMember = mine) }
                }
            }

            // ── Начальные данные myMember ─────────────────────────────────────
            launch {
                try {
                    val myMember = chatRepository.getMyMemberData(chatId)
                    if (myMember != null) _uiState.update { it.copy(myMember = myMember) }
                } catch (_: Exception) {}
            }

            // ── DIRECT: presence + typing + профиль собеседника ───────────────
            if (otherUid != chatId) {
                _uiState.update { it.copy(chatType = ChatType.DIRECT) }

                launch {
                    val profile = try { userRepository.getUserProfile(otherUid) }
                    catch (_: Exception) { null }
                    _uiState.update { it.copy(otherUser = profile) }
                }

                launch {
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

            // ── Messages ──────────────────────────────────────────────────────
            launch {
                chatRepository.latestMessagesFlow(chatId) { messages, lastDoc ->
                    val items = buildMessageList(messages)
                    _uiState.update {
                        it.copy(
                            messages = messages,
                            messageListItems = items,
                            lastDoc = lastDoc,
                            hasMore = lastDoc != null
                        )
                    }
                    if (_uiState.value.chatType == ChatType.DIRECT) {
                        viewModelScope.launch {
                            try { chatRepository.markMessagesAsRead(chatId, messages, currentUid) }
                            catch (_: Exception) {}
                        }
                    }
                }.catch { }.collect()
            }
        }
    }

    private suspend fun loadChatData() {
        try {
            val myMember = chatRepository.getMyMemberData(chatId)
            _uiState.update { it.copy(myMember = myMember) }
        } catch (_: Exception) {}

        viewModelScope.launch {
            chatRepository.membersFlow(chatId).collect { members ->
                _uiState.update { it.copy(members = members) }
                val mine = members.find { it.uid == currentUid }
                if (mine != null) _uiState.update { it.copy(myMember = mine) }
            }
        }

        if (otherUid != chatId) {
            _uiState.update { it.copy(chatType = ChatType.DIRECT) }
            viewModelScope.launch {
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

            viewModelScope.launch {
                val profile = userRepository.getUserProfile(otherUid)
                _uiState.update { it.copy(otherUser = profile) }
            }
        }
    }

    // ─── Определяем тип чата из Firestore ────────────────────────────────────

    fun setChatInfo(chat: Chat) {
        val type = chat.chatType()
        _uiState.update { it.copy(chat = chat, chatType = type) }

        if (type != ChatType.DIRECT) {
            startGroupOnlineCount(chat.memberIds)
        }
    }

    private fun startGroupOnlineCount(memberIds: List<String>) {
        val presenceRef = Firebase.database.getReference("presence")
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

    // ─── Pagination ───────────────────────────────────────────────────────────

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingMore || state.lastDoc == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val (older, newLastDoc) = chatRepository.loadOlderMessages(chatId, state.lastDoc)
                val combined = older + state.messages
                _uiState.update {
                    it.copy(
                        messages = combined,
                        messageListItems = buildMessageList(combined),
                        isLoadingMore = false,
                        hasMore = newLastDoc != null,
                        lastDoc = newLastDoc ?: it.lastDoc
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    // ─── Date separators ─────────────────────────────────────────────────────

    private fun buildMessageList(messages: List<Message>): List<MessageListItem> {
        val result = mutableListOf<MessageListItem>()
        var lastDate: LocalDate? = null
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        for (message in messages) {
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

    // ─── Typing ───────────────────────────────────────────────────────────────

    fun onTextChanged(text: String) {
        if (text.isNotEmpty()) typingManager?.onTyping()
        else typingManager?.stopTyping()
    }

    // ─── Send ─────────────────────────────────────────────────────────────────

    fun sendText(text: String) {
        if (!_uiState.value.canSendMessage) return
        val trimmed = text.trim().ifEmpty { return }
        val reply = _uiState.value.replyingTo?.toReplyData()
        viewModelScope.launch {
            typingManager?.stopTyping()
            clearReply()
            try { chatRepository.sendText(chatId, trimmed, currentUsername, reply) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun sendImage(uri: Uri) {
        if (!_uiState.value.canSendMedia) return
        val reply = _uiState.value.replyingTo?.toReplyData()
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            clearReply()
            try { chatRepository.sendImage(chatId, uri, currentUsername, reply) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
            finally { _uiState.update { it.copy(isUploading = false) } }
        }
    }

    fun sendSticker(sticker: Sticker) {
        if (!_uiState.value.canSendMessage) return
        val reply = _uiState.value.replyingTo?.toReplyData()
        viewModelScope.launch {
            clearReply()
            try { chatRepository.sendSticker(chatId, sticker, currentUsername, reply) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ─── Voice ────────────────────────────────────────────────────────────────

    fun startRecording() {
        if (!_uiState.value.canSendMedia) return
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.webm")
        recordingFile = file
        recordingStart = System.currentTimeMillis()
        @Suppress("DEPRECATION")
        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(context) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.WEBM)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
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

    // ─── Delete / React ───────────────────────────────────────────────────────

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try { chatRepository.deleteMessage(chatId, messageId) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun toggleReaction(messageId: String, emoji: String, currentReactions: List<Reaction>) {
        if (!_uiState.value.canReact) return
        viewModelScope.launch {
            try { chatRepository.toggleReaction(chatId, messageId, emoji, currentReactions) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ─── Moderation ───────────────────────────────────────────────────────────

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
            try {
                chatRepository.leaveChat(chatId)
                onLeft()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ─── Reply ────────────────────────────────────────────────────────────────

    fun setReplyTo(message: Message) = _uiState.update { it.copy(replyingTo = message) }
    fun clearReply() = _uiState.update { it.copy(replyingTo = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        typingManager?.cleanup()
        onlineCountListener?.let {
            Firebase.database.getReference("presence").removeEventListener(it)
        }
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

    // Koin inject workaround для firestore
    private val com.google.firebase.firestore.CollectionReference.document
        get() = this
}
