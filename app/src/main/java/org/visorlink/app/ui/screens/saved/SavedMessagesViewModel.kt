// ui/screens/saved/SavedMessagesViewModel.kt
package org.visorlink.app.ui.screens.saved

import android.app.Application
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.R
import org.visorlink.app.data.model.SavedMessage
import org.visorlink.app.data.model.SavedMessagesSettings
import org.visorlink.app.data.model.MessageType
import org.visorlink.app.data.repository.ForwardRepository
import org.visorlink.app.data.repository.SavedMessagesRepository
import org.visorlink.app.utils.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedMessagesUiState(
    val messages: List<SavedMessage>         = emptyList(),
    val settings: SavedMessagesSettings?     = null,
    val isLoading: Boolean                   = true,
    val isUnlocked: Boolean                  = false,
    val showPinInput: Boolean                = false,
    val pinError: Boolean                    = false,
    val error: String?                       = null,
    val infoMessage: String?                 = null,
    val keystoreSecurityLevel: KeystoreSecurityLevel = KeystoreSecurityLevel.UNKNOWN,
    val isEncryptionEnabled: Boolean         = false,
    val isRecording: Boolean                 = false,
    val isUploading: Boolean                 = false,
    val voicePlayback: VoicePlaybackState    = VoicePlaybackState(),
    val initialDraft: String                 = "",
    val editingMessage: SavedMessage?        = null
)

class SavedMessagesViewModel(
    private val repository: SavedMessagesRepository,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val voicePlayer: VoicePlayerManager,
    private val context: Application,
    private val draftManager: DraftManager,
    private val forwardRepository: ForwardRepository,
    private val biometricPinManager: BiometricPinManager
) : AndroidViewModel(context) {

    val currentUid: String get() = auth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(SavedMessagesUiState())
    val uiState: StateFlow<SavedMessagesUiState> = _uiState.asStateFlow()

    private var encryptionKey: SecretKey? = null
    private var lastUnlockTime: Long = 0L

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStart = 0L

    init {
        val draft = draftManager.getDraft("saved_$currentUid")
        if (draft.isNotEmpty()) {
            _uiState.update { it.copy(initialDraft = draft) }
        }

        viewModelScope.launch {
            repository.settingsFlow(currentUid).collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                val pinEnabled = settings?.pinEnabled == true
                _uiState.update { it.copy(isEncryptionEnabled = pinEnabled) }

                if (!pinEnabled) {
                    encryptionKey = null
                    _uiState.update { it.copy(isUnlocked = true, showPinInput = false) }
                    startMessagesFlow()
                } else {
                    if (!_uiState.value.isUnlocked) {
                        _uiState.update { it.copy(showPinInput = true, isLoading = false) }
                    }
                }
            }
        }

        viewModelScope.launch {
            voicePlayer.state.collect { playback ->
                _uiState.update { it.copy(voicePlayback = playback) }
            }
        }
    }

    private fun startMessagesFlow() {
        viewModelScope.launch {
            repository.messagesFlow(currentUid, encryptionKey, includeDiary = false).collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }
    }

    fun onPinEntered(pin: String, enableBiometrics: Boolean) {
        viewModelScope.launch {
            val valid = repository.verifyPin(currentUid, pin)
            if (valid) {
                if (enableBiometrics) {
                    when (val res = biometricPinManager.savePinSecurely(currentUid, pin)) {
                        is BiometricSaveResult.Success -> {
                            _uiState.update { it.copy(
                                keystoreSecurityLevel = res.securityLevel,
                                infoMessage = res.message
                            ) }
                        }
                        is BiometricSaveResult.Error -> {
                            _uiState.update { it.copy(error = res.message) }
                        }
                    }
                }
                encryptionKey = deriveKey(pin, currentUid)
                lastUnlockTime = System.currentTimeMillis()
                _uiState.update { it.copy(isUnlocked = true, showPinInput = false, pinError = false) }
                startMessagesFlow()
            } else {
                _uiState.update { it.copy(pinError = true) }
            }
        }
    }

    fun lock() {
        encryptionKey = null
        _uiState.update {
            it.copy(
                isUnlocked  = false,
                showPinInput = true,
                messages    = emptyList(),
                isLoading   = false
            )
        }
    }

    fun lockIfConfigured() {
        val settings = _uiState.value.settings ?: return
        if (!settings.pinEnabled) return
        val timeout = settings.lockTimeout * 60_000L
        if (timeout == 0L || System.currentTimeMillis() - lastUnlockTime > timeout) {
            lock()
        }
    }

    fun clearPinError() = _uiState.update { it.copy(pinError = false) }

    fun launchBiometricUnlock(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModelScope.launch {
                    when (val unlockResult = biometricPinManager.getPinSecurely(currentUid)) {
                        is BiometricUnlockResult.Success -> {
                            encryptionKey = deriveKey(unlockResult.pin, currentUid)
                            lastUnlockTime = System.currentTimeMillis()
                            _uiState.update { it.copy(
                                isUnlocked = true,
                                showPinInput = false,
                                keystoreSecurityLevel = unlockResult.securityLevel,
                                infoMessage = "Разблокировано через биометрию (${unlockResult.securityLevel.badge})"
                            ) }
                            startMessagesFlow()
                            onResult(true)
                        }
                        is BiometricUnlockResult.KeyPermanentlyInvalidated -> {
                            _uiState.update { it.copy(
                                error = unlockResult.message,
                                isUnlocked = false,
                                showPinInput = true
                            ) }
                            onResult(false)
                        }
                        is BiometricUnlockResult.Error -> {
                            _uiState.update { it.copy(error = unlockResult.message) }
                            onResult(false)
                        }
                    }
                }
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) { onResult(false) }
            override fun onAuthenticationFailed() { onResult(false) }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.saved_biometric_title))
            .setSubtitle(activity.getString(R.string.saved_biometric_subtitle))
            .setNegativeButtonText(activity.getString(R.string.btn_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }

    fun hasBiometricPinSaved(): Boolean = biometricPinManager.hasSavedPin(currentUid)

    fun getKeystoreSecurityLevel(): KeystoreSecurityLevel = biometricPinManager.getKeystoreSecurityLevel(currentUid)

    suspend fun getDecryptedFile(message: SavedMessage): File? {
        return repository.getDecryptedMediaFile(message, encryptionKey)
    }

    fun onTextChanged(text: String) {
        draftManager.saveDraft("saved_$currentUid", text)
    }

    fun saveText(text: String) {
        viewModelScope.launch {
            try {
                repository.saveText(currentUid, text, encryptionKey)
                draftManager.clearDraft("saved_$currentUid")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Не удалось сохранить: ${e.message}") }
            }
        }
    }

    fun saveImage(uri: Uri, isSpoiler: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            try {
                repository.saveImage(
                    uid        = currentUid,
                    uri        = uri,
                    key        = encryptionKey,
                    isSpoiler  = isSpoiler
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка загрузки: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    fun startRecording() {
        voicePlayer.stop()
        val file = File(getApplication<Application>().cacheDir, "saved_voice_${System.currentTimeMillis()}.webm")
        recordingFile = file
        recordingStart = System.currentTimeMillis()

        @Suppress("DEPRECATION")
        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(getApplication()) else MediaRecorder()).apply {
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
        _uiState.update { it.copy(isRecording = false) }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true) }
            try {
                repository.saveVoice(
                    uid         = currentUid,
                    uri         = Uri.fromFile(file),
                    durationSec = duration,
                    key         = encryptionKey
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка записи: ${e.message}") }
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

    fun playVoice(messageId: String, url: String, durationSec: Int) {
        viewModelScope.launch { voicePlayer.play(messageId, url, durationSec) }
    }

    fun seekVoice(fraction: Float) {
        voicePlayer.seekTo(fraction)
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try { repository.deleteMessage(currentUid, messageId) }
            catch (e: Exception) { _uiState.update { it.copy(error = "Ошибка удаления: ${e.message}") } }
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch { repository.setPin(currentUid, pin) }
    }

    fun disablePin() {
        viewModelScope.launch {
            repository.disablePin(currentUid)
            encryptionKey = null
            biometricPinManager.clearSavedPin(currentUid)
            _uiState.update { it.copy(
                isEncryptionEnabled = false,
                keystoreSecurityLevel = KeystoreSecurityLevel.UNKNOWN
            ) }
        }
    }

    fun clearInfoMessage() = _uiState.update { it.copy(infoMessage = null) }

    fun updateLockTimeout(minutes: Int) {
        viewModelScope.launch { repository.updateLockTimeout(currentUid, minutes) }
    }

    fun startEditing(message: SavedMessage) {
        val text = if (message.type != MessageType.TEXT) message.caption ?: "" else message.text ?: ""
        _uiState.update { it.copy(editingMessage = message, initialDraft = text) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editingMessage = null, initialDraft = "") }
    }

    fun saveEdit(newText: String) {
        val msg = _uiState.value.editingMessage ?: return
        if (newText.trim() == (msg.text ?: msg.caption ?: "")) {
            cancelEditing()
            return
        }

        viewModelScope.launch {
            try {
                repository.editMessage(currentUid, msg, newText.trim(), encryptionKey)
                _uiState.update { it.copy(editingMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка редактирования: ${e.message}") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun getIsEncryptionEnabled() = _uiState.value.isEncryptionEnabled

    override fun onCleared() {
        super.onCleared()
        try { recorder?.apply { stop(); release() } } catch (_: Exception) {}
        voicePlayer.release()
    }
}