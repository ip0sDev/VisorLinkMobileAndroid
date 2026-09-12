package by.iposdev.visorlink.ui.screens.comments

import android.Manifest
import android.app.Application
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.utils.VoicePlayerManager
import by.iposdev.visorlink.utils.VoicePlaybackState
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class CommentsUiState(
    val post: Message? = null,
    val comments: List<Comment> = emptyList(),
    val tempComments: List<Comment> = emptyList(),
    val revealedSpoilers: Set<String> = emptySet(),
    val replyingTo: Comment? = null,
    val isUploading: Boolean = false,
    val isRecording: Boolean = false,
    val error: String? = null,
    val voicePlayback: VoicePlaybackState = VoicePlaybackState(),
    val myMember: Member? = null
) {
    /** Comments are allowed when both the channel-level and post-level flags permit. */
    fun commentsAllowed(channel: Chat?): Boolean {
        if (channel?.settings?.allowComments == false) return false
        if (post?.commentsEnabled == false) return false
        return true
    }

    val isAdmin get() = myMember?.isAdmin() ?: false
    val commentCount get() = post?.commentsCount ?: (comments.size + tempComments.size)
}

class CommentsViewModel(
    private val chatId: String,
    private val messageId: String,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val application: Application,
    private val usageRankManager: by.iposdev.visorlink.utils.UsageRankManager? = null
) : AndroidViewModel(application) {

    val currentUid: String get() = auth.currentUser!!.uid
    private var currentUsername = ""

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    val voicePlayer = VoicePlayerManager(application)

    private var postListener: ListenerRegistration? = null
    private var commentsListener: ListenerRegistration? = null

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStart = 0L

    init {
        viewModelScope.launch {
            voicePlayer.state.collect { playbackState ->
                _uiState.update { it.copy(voicePlayback = playbackState) }
            }
        }

        viewModelScope.launch {
            userRepository.currentUserFlow().catch { }
                .collect { currentUsername = it?.username ?: "" }
        }

        viewModelScope.launch {
            try {
                val myMember = chatRepository.getMyMemberData(chatId)
                _uiState.update { it.copy(myMember = myMember) }
            } catch (_: Exception) {}
        }

        startListening()
    }

    // ─── Real-time listeners ──────────────────────────────────────────────────

    private fun startListening() {
        // Listen to the post document for live commentsCount / commentsEnabled
        postListener = chatRepository.listenPost(chatId, messageId) { post ->
            _uiState.update { it.copy(post = post) }
        }
        // Listen to comments subcollection
        commentsListener = chatRepository.listenComments(chatId, messageId) { comments ->
            _uiState.update { state ->
                // Очищаем временные комментарии, которые уже появились в основном списке
                val filteredTemp = state.tempComments.filter { temp ->
                    comments.none { real ->
                        val isSameUser = real.senderId == temp.senderId
                        val isSameType = real.type == temp.type
                        val timeDiff = Math.abs((real.createdAt?.seconds ?: 0) - (temp.createdAt?.seconds ?: 0))
                        
                        isSameUser && isSameType && timeDiff < 30 && (
                            real.text == temp.text || 
                            (real.duration != null && real.duration == temp.duration) ||
                            (real.type == MessageType.IMAGE) // Для картинок просто по типу и времени
                        )
                    }
                }
                state.copy(comments = comments, tempComments = filteredTemp)
            }
        }
    }

    // ─── Spoiler reveal (local, per-session) ──────────────────────────────────

    fun revealSpoiler(id: String) {
        _uiState.update { it.copy(revealedSpoilers = it.revealedSpoilers + id) }
    }

    // ─── Reply ────────────────────────────────────────────────────────────────

    fun setReplyTo(comment: Comment?) = _uiState.update { it.copy(replyingTo = comment) }
    fun clearReply() = _uiState.update { it.copy(replyingTo = null) }

    // ─── Send text comment ────────────────────────────────────────────────────

    fun sendText(text: String) {
        val trimmed = text.trim().ifEmpty { return }
        val reply = _uiState.value.replyingTo?.toCommentReplyData()
        val tempId = "temp_${System.currentTimeMillis()}"

        val tempComment = Comment(
            id = tempId,
            senderId = currentUid,
            senderUsername = currentUsername,
            type = MessageType.TEXT,
            text = trimmed,
            replyTo = reply,
            status = SendStatus.SENDING,
            createdAt = Timestamp.now()
        )

        _uiState.update { it.copy(tempComments = it.tempComments + tempComment) }

        viewModelScope.launch {
            clearReply()
            try {
                chatRepository.addComment(
                    chatId = chatId, messageId = messageId,
                    type = MessageType.TEXT, text = trimmed,
                    replyTo = reply
                )
                _uiState.update { it.copy(tempComments = it.tempComments.filter { c -> c.id != tempId }) }
            } catch (e: Exception) {
                _uiState.update { state ->
                    val updated = state.tempComments.map {
                        if (it.id == tempId) it.copy(status = SendStatus.ERROR) else it
                    }
                    state.copy(tempComments = updated, error = e.message)
                }
            }
        }
    }

    // ─── Send image comment ───────────────────────────────────────────────────

    fun sendImage(uri: Uri, isSpoiler: Boolean = false) {
        val reply = _uiState.value.replyingTo?.toCommentReplyData()
        val tempId = "temp_img_${System.currentTimeMillis()}"

        val tempComment = Comment(
            id = tempId,
            senderId = currentUid,
            senderUsername = currentUsername,
            type = MessageType.IMAGE,
            url = uri.toString(),
            spoiler = isSpoiler,
            replyTo = reply,
            status = SendStatus.SENDING,
            createdAt = Timestamp.now()
        )

        _uiState.update { it.copy(tempComments = it.tempComments + tempComment, isUploading = true) }

        viewModelScope.launch {
            clearReply()
            try {
                chatRepository.uploadAndCommentImage(
                    chatId = chatId, messageId = messageId,
                    file = uri, spoiler = isSpoiler, replyTo = reply
                )
                _uiState.update { it.copy(tempComments = it.tempComments.filter { c -> c.id != tempId }) }
            } catch (e: Exception) {
                _uiState.update { state ->
                    val updated = state.tempComments.map {
                        if (it.id == tempId) it.copy(status = SendStatus.ERROR) else it
                    }
                    state.copy(tempComments = updated, error = e.message)
                }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    // ─── Voice recording ──────────────────────────────────────────────────────

    fun startRecording() {
        voicePlayer.stop()
        val file = File(getApplication<Application>().cacheDir, "comment_voice_${System.currentTimeMillis()}.webm")
        recordingFile = file
        recordingStart = System.currentTimeMillis()
        @Suppress("DEPRECATION")
        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(getApplication()) else MediaRecorder()).apply {
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
        val reply = _uiState.value.replyingTo?.toCommentReplyData()
        val tempId = "temp_voice_${System.currentTimeMillis()}"

        val tempComment = Comment(
            id = tempId,
            senderId = currentUid,
            senderUsername = currentUsername,
            type = MessageType.VOICE,
            duration = duration,
            replyTo = reply,
            status = SendStatus.SENDING,
            createdAt = Timestamp.now()
        )

        _uiState.update { it.copy(isRecording = false, tempComments = it.tempComments + tempComment) }
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            clearReply()
            try {
                chatRepository.uploadAndCommentVoice(
                    chatId = chatId, messageId = messageId,
                    audioFile = file, durationSeconds = duration, replyTo = reply
                )
                _uiState.update { it.copy(tempComments = it.tempComments.filter { c -> c.id != tempId }) }
            } catch (e: Exception) {
                _uiState.update { state ->
                    val updated = state.tempComments.map {
                        if (it.id == tempId) it.copy(status = SendStatus.ERROR) else it
                    }
                    state.copy(tempComments = updated, error = e.message)
                }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
                file.delete()
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

    // ─── Reactions & delete ───────────────────────────────────────────────────

    fun toggleReaction(commentId: String, emoji: String, currentReactions: List<Reaction>) {
        val existing = currentReactions.find { it.emoji == emoji }
        val isAdding = existing == null || currentUid !in existing.uids
        if (isAdding) {
            usageRankManager?.recordReactionUsage(emoji)
        }
        viewModelScope.launch {
            try {
                chatRepository.toggleCommentReaction(chatId, messageId, commentId, emoji, currentReactions)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteComment(chatId, messageId, commentId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ─── Admin: toggle comments on this post ─────────────────────────────────

    fun toggleComments(currentlyEnabled: Boolean) {
        viewModelScope.launch {
            try {
                chatRepository.togglePostComments(chatId, messageId, !currentlyEnabled)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ─── Voice playback ───────────────────────────────────────────────────────

    fun playVoice(commentId: String, url: String, durationSec: Int) =
        voicePlayer.play(commentId, url, durationSec)

    fun seekVoice(fraction: Float) = voicePlayer.seekTo(fraction)

    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        // Both listeners MUST be removed to avoid memory leaks
        postListener?.remove()
        commentsListener?.remove()
        voicePlayer.release()
        super.onCleared()
    }
}