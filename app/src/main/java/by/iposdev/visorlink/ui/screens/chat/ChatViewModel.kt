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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val otherUser: UserProfile? = null,
    val isOnline: Boolean = false,
    val lastSeen: Timestamp? = null,
    val replyingTo: Message? = null,
    val isUploading: Boolean = false,
    val error: String? = null,
    val isRecording: Boolean = false,
    val isReady: Boolean = false  // чат существует и готов
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

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStart = 0L

    init {
        // Загружаем профиль собеседника сразу
        viewModelScope.launch {
            _uiState.update { it.copy(otherUser = userRepository.getUserProfile(otherUid)) }
        }
        // Получаем свой username
        viewModelScope.launch {
            userRepository.currentUserFlow().collect { currentUsername = it?.username ?: "" }
        }
        // Ждём готовности чата, потом запускаем listeners
        viewModelScope.launch {
            waitForChatAndStartListeners()
        }
    }

    private suspend fun waitForChatAndStartListeners() {
        // Ждём пока чат появится в Firestore (макс 10 сек)
        var chatReady = false
        repeat(20) {
            if (chatReady) return@repeat
            chatReady = chatRepository.chatExists(chatId)
            if (!chatReady) delay(500)
        }

        if (!chatReady) {
            _uiState.update { it.copy(error = "Failed to open chat. Please try again.") }
            return
        }

        _uiState.update { it.copy(isReady = true) }

        // Теперь безопасно запускаем listeners
        viewModelScope.launch {
            chatRepository.messagesFlow(chatId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            chatRepository.onlineStatusFlow(otherUid).collect { (online, lastSeen) ->
                _uiState.update { it.copy(isOnline = online, lastSeen = lastSeen) }
            }
        }
    }

    fun sendText(text: String) {
        val trimmed = text.trim().ifEmpty { return }
        val reply = _uiState.value.replyingTo?.toReplyData()
        viewModelScope.launch {
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

    fun setReplyTo(message: Message) = _uiState.update { it.copy(replyingTo = message) }
    fun clearReply() = _uiState.update { it.copy(replyingTo = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun Message.toReplyData() = ReplyData(id, type, text, url, senderUsername)
}