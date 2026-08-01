package by.iposdev.visorlink.ui.screens.saved

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.SavedMessage
import by.iposdev.visorlink.data.model.SavedMessagesSettings
import by.iposdev.visorlink.data.repository.SavedMessagesRepository
import by.iposdev.visorlink.utils.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class SavedMessagesUiState(
    val messages: List<SavedMessage>         = emptyList(),
    val settings: SavedMessagesSettings?     = null,
    val isLoading: Boolean                   = true,
    val isUnlocked: Boolean                  = false,
    val showPinInput: Boolean                = false,
    val pinError: Boolean                    = false,
    val error: String?                       = null,
    val isEncryptionEnabled: Boolean         = false,
    val isRecording: Boolean                 = false,
    val isUploading: Boolean                 = false,
    val voicePlayback: VoicePlaybackState    = VoicePlaybackState(),
    val initialDraft: String                 = ""
)

class SavedMessagesViewModel(
    private val repository: SavedMessagesRepository,
    private val auth: FirebaseAuth,
    private val voicePlayer: VoicePlayerManager,
    private val context: Context,
    private val draftManager: DraftManager
) : ViewModel() {

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
            repository.messagesFlow(currentUid, encryptionKey).collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }
    }

    fun onPinEntered(pin: String, enableBiometrics: Boolean) {
        viewModelScope.launch {
            val valid = repository.verifyPin(currentUid, pin)
            if (valid) {
                if (enableBiometrics) {
                    savePinToKeystoreSecurely(pin)
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
                    val storedPin = getPinFromKeystoreSecurely() ?: repository.getBiometricPin(currentUid) ?: return@launch

                    encryptionKey = deriveKey(storedPin, currentUid)
                    lastUnlockTime = System.currentTimeMillis()
                    _uiState.update { it.copy(isUnlocked = true, showPinInput = false) }
                    startMessagesFlow()
                    onResult(true)
                }
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) { onResult(false) }
            override fun onAuthenticationFailed() { onResult(false) }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Избранное")
            .setSubtitle("Войдите с помощью биометрии")
            .setNegativeButtonText("Отмена")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }

    fun hasBiometricPinSaved(): Boolean {
        val prefs = context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
        return prefs.contains("pin_enc_$currentUid")
    }

    private fun savePinToKeystoreSecurely(pin: String) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = "visorlink_bio_key_$currentUid"

            if (!keyStore.containsAlias(alias)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }

            val secretKey = keyStore.getKey(alias, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(pin.toByteArray(Charsets.UTF_8))

            val prefs = context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("pin_iv_$currentUid", Base64.encodeToString(iv, Base64.DEFAULT))
                .putString("pin_enc_$currentUid", Base64.encodeToString(encrypted, Base64.DEFAULT))
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getPinFromKeystoreSecurely(): String? {
        return try {
            val prefs = context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
            val ivStr = prefs.getString("pin_iv_$currentUid", null) ?: return null
            val encStr = prefs.getString("pin_enc_$currentUid", null) ?: return null

            val iv = Base64.decode(ivStr, Base64.DEFAULT)
            val encrypted = Base64.decode(encStr, Base64.DEFAULT)

            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secretKey = keyStore.getKey("visorlink_bio_key_$currentUid", null) as? SecretKey ?: return null

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decoded = cipher.doFinal(encrypted)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun decryptMediaToCache(message: SavedMessage): ByteArray? = withContext(Dispatchers.IO) {
        if (message.encrypted != true || encryptionKey == null || message.cdnMediaId == null) return@withContext null
        try {
            val url = CdnService.getFileUrl(message.cdnMediaId)
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            val encryptedBytes = connection.inputStream.readBytes()
            decryptBytes(encryptedBytes, message.iv ?: return@withContext null, encryptionKey!!)
        } catch (e: Exception) { null }
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
        val file = File(context.cacheDir, "saved_voice_${System.currentTimeMillis()}.webm")
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
            _uiState.update { it.copy(isEncryptionEnabled = false) }
            val prefs = context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("pin_iv_$currentUid").remove("pin_enc_$currentUid").apply()
        }
    }

    fun updateLockTimeout(minutes: Int) {
        viewModelScope.launch { repository.updateLockTimeout(currentUid, minutes) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun getIsEncryptionEnabled() = _uiState.value.isEncryptionEnabled

    override fun onCleared() {
        super.onCleared()
        try { recorder?.apply { stop(); release() } } catch (_: Exception) {}
        voicePlayer.release()
    }
}