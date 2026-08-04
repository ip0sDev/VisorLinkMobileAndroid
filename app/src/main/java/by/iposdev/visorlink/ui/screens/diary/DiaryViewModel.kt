package by.iposdev.visorlink.ui.screens.diary

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.SavedMessage
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.repository.SavedMessagesRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.utils.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class DiaryUiState(
    val entries: List<SavedMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isUnlocked: Boolean = false,
    val showPinInput: Boolean = false,
    val pinError: Boolean = false,
    val isEncryptionEnabled: Boolean = false,
    val userProfile: UserProfile? = null,
    val selectedDate: Calendar = Calendar.getInstance(),
    val stats: DiaryStats = DiaryStats(),
    val error: String? = null
)

data class DiaryStats(
    val totalEntries: Int = 0,
    val monthlyEntries: Int = 0,
    val currentStreak: Int = 0
)

class DiaryViewModel(
    private val repository: SavedMessagesRepository,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val context: Context,
    private val reminderManager: DiaryReminderManager
) : ViewModel() {

    val currentUid: String get() = auth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private var encryptionKey: SecretKey? = null
    private var lastUnlockTime: Long = 0L

    init {
        viewModelScope.launch {
            userRepository.currentUserFlow().collect { profile ->
                _uiState.update { it.copy(userProfile = profile) }
                if (profile?.diaryEnabled == true && profile.diaryRemindersEnabled) {
                    reminderManager.scheduleReminder(profile.diaryReminderTime)
                } else {
                    reminderManager.cancelReminder()
                }
            }
        }

        viewModelScope.launch {
            repository.settingsFlow(currentUid).collect { settings ->
                val pinEnabled = settings?.pinEnabled == true
                _uiState.update { it.copy(isEncryptionEnabled = pinEnabled) }

                if (!pinEnabled) {
                    encryptionKey = null
                    _uiState.update { it.copy(isUnlocked = true, showPinInput = false) }
                    startDiaryFlow()
                } else {
                    if (!_uiState.value.isUnlocked) {
                        _uiState.update { it.copy(showPinInput = true, isLoading = false) }
                    }
                }
            }
        }
    }

    private fun startDiaryFlow() {
        viewModelScope.launch {
            repository.diaryFlow(currentUid, encryptionKey)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { entries ->
                    val stats = calculateStats(entries)
                    _uiState.update { it.copy(entries = entries, isLoading = false, stats = stats) }
                }
        }
    }

    private fun calculateStats(entries: List<SavedMessage>): DiaryStats {
        val total = entries.size
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        
        val monthly = entries.count { 
            val d = it.createdAt?.toDate() ?: return@count false
            cal.time = d
            cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
        }

        // Simple streak calculation (daily)
        var streak = 0
        if (entries.isNotEmpty()) {
            val sorted = entries.mapNotNull { it.createdAt?.toDate() }.sortedDescending()
            val dayMs = 24 * 60 * 60 * 1000L
            val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            
            var lastDay = today
            for (date in sorted) {
                val d = Calendar.getInstance().apply { time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                if (d == lastDay) {
                    streak++
                    lastDay -= dayMs
                } else if (d < lastDay) {
                    break
                }
            }
        }

        return DiaryStats(total, monthly, streak)
    }

    fun onPinEntered(pin: String) {
        viewModelScope.launch {
            val valid = repository.verifyPin(currentUid, pin)
            if (valid) {
                encryptionKey = deriveKey(pin, currentUid)
                lastUnlockTime = System.currentTimeMillis()
                _uiState.update { it.copy(isUnlocked = true, showPinInput = false, pinError = false) }
                startDiaryFlow()
            } else {
                _uiState.update { it.copy(pinError = true) }
            }
        }
    }

    fun onDateSelected(calendar: Calendar) {
        _uiState.update { it.copy(selectedDate = calendar) }
    }

    fun saveEntry(text: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                repository.saveText(currentUid, text, encryptionKey, isDiary = true)
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    fun uploadDiaryImage(uri: Uri, onUploaded: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val mediaId = repository.saveImage(currentUid, uri, key = encryptionKey, isDiary = true)
                onUploaded(mediaId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            repository.deleteMessage(currentUid, id)
        }
    }

    fun exportToXml(): String {
        val entries = _uiState.value.entries
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<diary>\n")
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        entries.forEach { entry ->
            sb.append("  <entry>\n")
            sb.append("    <id>${entry.id}</id>\n")
            sb.append("    <date>${entry.createdAt?.toDate()?.let { df.format(it) } ?: ""}</date>\n")
            sb.append("    <content><![CDATA[${entry.text ?: ""}]]></content>\n")
            sb.append("  </entry>\n")
        }
        sb.append("</diary>")
        return sb.toString()
    }

    // Biometric reuse from SavedMessages
    fun launchBiometricUnlock(activity: FragmentActivity) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModelScope.launch {
                    val storedPin = getPinFromKeystoreSecurely() ?: return@launch
                    encryptionKey = deriveKey(storedPin, currentUid)
                    lastUnlockTime = System.currentTimeMillis()
                    _uiState.update { it.copy(isUnlocked = true, showPinInput = false) }
                    startDiaryFlow()
                }
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Diary")
            .setSubtitle("Unlock with biometrics")
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(info)
    }

    fun hasBiometricPinSaved(): Boolean {
        val prefs = context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
        return prefs.contains("pin_enc_$currentUid")
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
            null
        }
    }

    fun clearPinError() = _uiState.update { it.copy(pinError = false) }
}