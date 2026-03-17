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
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val messageListItems: List<MessageListItem> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val lastDoc: DocumentSnapshot? = null,
    val otherUser: UserProfile? = null,
    val topbarStatus: TopbarStatus = TopbarStatus.Offline,
    val replyingTo: Message? = null,
    val isUploading: Boolean = false,
    val error: String? = null,
    val isRecording: Boolean = false
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val context: Context,
    val chatId: String,
    val otherUid: String
) : ViewModel() {

    val currentUid: String get() = auth.currentUser!!.uid
    private var currentUsername = ""

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // ВОТ ИСПРАВЛЕНИЕ: Вынесли stickers из блока init, теперь ChatScreen видит эту переменную!
    val stickers: StateFlow<List<Sticker>> = userRepository.stickersFlow(currentUid)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStart = 0L
    private var typingManager: TypingManager? = null

    init {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(otherUser = userRepository.getUserProfile(otherUid)) }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            userRepository.currentUserFlow()
                .catch { }
                .collect { currentUsername = it?.username ?: "" }
        }

        viewModelScope.launch {
            val exists = waitForChat()
            if (!exists) {
                _uiState.update { it.copy(error = "Failed to open chat") }
                return@launch
            }

            // Инициализируем TypingManager
            typingManager = TypingManager(chatId, currentUid)

            // Слушаем последние сообщения
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
                    // Помечаем прочитанными
                    viewModelScope.launch {
                        try { chatRepository.markMessagesAsRead(chatId, messages, currentUid) }
                        catch (_: Exception) {}
                    }
                }.catch { }.collect()
            }

            // Presence + Typing → TopbarStatus
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
        var lastDate: java.time.LocalDate? = null
        val today = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)

        for (message in messages) {
            val msgDate = message.createdAt?.toDate()
                ?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
                ?: continue
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
        val reply = _uiState.value.replyingTo?.toReplyData()
        viewModelScope.launch {
            clearReply()
            try { chatRepository.sendSticker(chatId, sticker, currentUsername, reply) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ─── Voice ────────────────────────────────────────────────────────────────

    fun startRecording() {
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
        viewModelScope.launch {
            try { chatRepository.toggleReaction(chatId, messageId, emoji, currentReactions) }
            catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ─── Reply ────────────────────────────────────────────────────────────────

    fun setReplyTo(message: Message) = _uiState.update { it.copy(replyingTo = message) }
    fun clearReply() = _uiState.update { it.copy(replyingTo = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        typingManager?.cleanup()
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
}