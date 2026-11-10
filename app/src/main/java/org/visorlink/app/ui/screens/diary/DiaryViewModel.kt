package org.visorlink.app.ui.screens.diary

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.R
import org.visorlink.app.data.model.SavedMessage
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.SavedMessagesRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.utils.*
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
import javax.crypto.KeyGenerator
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
    val error: String? = null,
    val infoMessage: String? = null,
    val keystoreSecurityLevel: KeystoreSecurityLevel = KeystoreSecurityLevel.UNKNOWN,
    val isBiometricEnabled: Boolean = false,
    // ── Миграция шифрования (TODO §14.4): полноэкранный флоу с прогрессом ──
    val migrationActive: Boolean = false,
    val migrationRollback: Boolean = false,
    // Ротация CK (сброс ключа восстановления) — общий с «Избранным» экран миграции
    val migrationRotate: Boolean = false,
    val migrationStage: String = "",
    val migrationTexts: Int = -1,
    val migrationTextsTotal: Int = 0,
    val migrationMedia: Int = -1,
    val migrationMediaTotal: Int = 0,
    val migrationFinished: Boolean = false,
    val migrationFailed: String? = null,
    // Recovery-код миграции v2 (показывается один раз; TODO §14.4)
    val recoveryCode: String? = null
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
    private val application: Application,
    private val reminderManager: DiaryReminderManager,
    private val biometricPinManager: BiometricPinManager,
    private val contentKeyManager: ContentKeyManager
) : AndroidViewModel(application) {

    val currentUid: String get() = auth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private var encryptionKey: SecretKey? = null
    // Legacy-ключ v1 (PBKDF2 от PIN) — только в окне миграции; после неё null
    private var legacyKey: SecretKey? = null
    private var lastUnlockTime: Long = 0L
    // Кэш флага миграции из настроек (обновляется settingsFlow)
    private var cryptoV2: Boolean = false
    // Тихий откат (PIN снят, kill-switch выключен) запускается один раз
    private var quietRollbackStarted = false
    // Идёт возобновление прерванной ротации CK — при сбое не блокируем вход
    private var resumingRotation = false
    // Текущая подписка на записи: перезапускается при смене ключа (ротация CK)
    private var diaryJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            userRepository.currentUserFlow().collect { profile ->
                _uiState.update { it.copy(userProfile = profile) }
            }
        }

        viewModelScope.launch {
            repository.settingsFlow(currentUid).collect { settings ->
                val pinEnabled = settings?.pinEnabled == true
                cryptoV2 = settings?.cryptoV2 == true
                _uiState.update { it.copy(isEncryptionEnabled = pinEnabled, isBiometricEnabled = hasBiometricPinSaved()) }

                if (!pinEnabled) {
                    // Тихий откат, если kill-switch выключили, а PIN-гейта нет (CK доступен через Keystore)
                    if (settings?.cryptoV2 == true && !contentKeyManager.migrationEnabled() && !quietRollbackStarted) {
                        quietRollbackStarted = true
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val ck = contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
                                repository.rollbackToV1(currentUid, ck, null)
                                repository.markCryptoV1(currentUid, null)
                                cryptoV2 = false
                            } catch (_: Exception) {
                                quietRollbackStarted = false
                            }
                        }
                    }
                    // Без PIN-гейта контент всё равно зашифрован CK (если миграция включена)
                    encryptionKey = if (settings?.cryptoV2 == true || contentKeyManager.migrationEnabled()) {
                        contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
                    } else null
                    legacyKey = null
                    _uiState.update { it.copy(isUnlocked = true, showPinInput = false) }
                    startDiaryFlow()
                } else {
                    if (!_uiState.value.isUnlocked) {
                        _uiState.update { it.copy(showPinInput = true, isLoading = false) }
                    } else if (settings?.cryptoV2 == true) {
                        // Ротация CK завершилась в другом экземпляре VM (настройки/Избранное):
                        // подтягиваем новый ключ из Keystore-обёртки (TODO §14.4)
                        encryptionKey = contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
                        legacyKey = null
                        startDiaryFlow()
                    }
                }
            }
        }
    }

    private fun startDiaryFlow() {
        // Перезапуск при смене ключа: старая подписка расшифровывала прежним ключом
        diaryJob?.cancel()
        diaryJob = viewModelScope.launch {
            repository.diaryFlow(currentUid, encryptionKey, legacyKey)
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

    fun onPinEntered(pin: String, saveBiometrics: Boolean = false, activity: FragmentActivity? = null) {
        viewModelScope.launch {
            // Lockout: экспоненциальная задержка после серии неудач (TODO §14.3)
            val remaining = contentKeyManager.lockoutRemainingMs(currentUid, ContentKeyManager.DOMAIN_SAVED)
            if (remaining > 0) {
                _uiState.update { it.copy(pinError = true, infoMessage = "Слишком много попыток, подождите ${remaining / 1000} с") }
                return@launch
            }

            val settings = repository.loadSettings(currentUid)
            var ck: SecretKey?

            if (settings?.cryptoV2 == true) {
                // v2: проверка PIN = разворот PIN-обёртки CK (локально, без серверного хэша)
                ck = contentKeyManager.ckByPin(currentUid, ContentKeyManager.DOMAIN_SAVED, pin)
                if (ck == null) {
                    contentKeyManager.registerFailedAttempt(currentUid, ContentKeyManager.DOMAIN_SAVED)
                    _uiState.update { it.copy(pinError = true) }
                    return@launch
                }
                contentKeyManager.clearFailedAttempts(currentUid, ContentKeyManager.DOMAIN_SAVED)

                // Возобновление прерванной ротации CK (сброс recovery-кода) — до обычной разблокировки.
                // При сбое вход не блокируем: finishMigration пустит со старым ключом (TODO §14.4)
                val pendingCK = contentKeyManager.pendingRotationCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
                if (contentKeyManager.resumeKeyRotationPending(currentUid, ContentKeyManager.DOMAIN_SAVED) && pendingCK != null) {
                    resumingRotation = true
                    runKeyRotationPipeline(pin, pendingCK)
                    return@launch
                }

                if (!contentKeyManager.migrationEnabled()) {
                    // Kill-switch выключили → обратная миграция v2 → v1 с экраном прогресса (TODO §14.4)
                    legacyKey = deriveKey(pin, currentUid)
                    startMigrationFlow(rollback = true, pin = pin, ck = ck, saveBiometrics = saveBiometrics, activity = activity)
                    return@launch
                }
                legacyKey = null
            } else {
                // v1: проверка по серверному хэшу (до миграции).
                // После стирания данных (забытый recovery-код, TODO §14.4) серверного хэша нет,
                // а PIN включён заново под свежим CK — проверяем разворотом локальной PIN-обёртки
                val freshCK = if (settings?.pinHash == null) {
                    contentKeyManager.ckByPin(currentUid, ContentKeyManager.DOMAIN_SAVED, pin)
                } else null
                val valid = freshCK != null || repository.verifyPin(currentUid, pin)
                if (!valid) {
                    _uiState.update { it.copy(pinError = true) }
                    return@launch
                }
                if (freshCK != null) {
                    // Свежий CK после стирания: миграционный флоу выдаст новый recovery-код
                    ck = freshCK
                    if (contentKeyManager.migrationEnabled()) {
                        startMigrationFlow(rollback = false, pin = pin, ck = freshCK, saveBiometrics = saveBiometrics, activity = activity)
                        return@launch
                    }
                    legacyKey = null
                } else {
                    legacyKey = deriveKey(pin, currentUid)
                    ck = legacyKey

                    // Миграция v1 → v2 — полноэкранный флоу с прогрессом и recovery-кодом (TODO §14.4)
                    if (contentKeyManager.migrationEnabled()) {
                        startMigrationFlow(rollback = false, pin = pin, ck = null, saveBiometrics = saveBiometrics, activity = activity)
                        return@launch
                    }
                }
            }

            if (saveBiometrics) {
                if (legacyKey == null) {
                    // v2: bio-обёртка ключа контента через CryptoObject
                    if (activity != null && ck != null) {
                        contentKeyManager.bioWrapCKWithPrompt(
                            activity, currentUid, ContentKeyManager.DOMAIN_SAVED, ck,
                            "Биометрия для дневника",
                            "Подтвердите, чтобы разрешить разблокировку по биометрии"
                        ) { ok, err -> if (!ok) _uiState.update { it.copy(error = err ?: "Не удалось включить биометрию") } }
                    }
                } else {
                    // v1 (до миграции): прежнее хранение PIN
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
            }

            encryptionKey = ck
            lastUnlockTime = System.currentTimeMillis()
            _uiState.update { it.copy(
                isUnlocked = true,
                showPinInput = false,
                pinError = false,
                isBiometricEnabled = hasBiometricPinSaved()
            ) }
            startDiaryFlow()
        }
    }

    /**
     * Полноэкранный миграционный флоу (TODO §14.4): стадии с прогрессом,
     * в конце — recovery-код с копированием. rollback=true — обратная миграция v2 → v1.
     * Идемпотентен: мигрирует тот, кто первым разблокировал «Избранное» или дневник.
     */
    private fun startMigrationFlow(rollback: Boolean, pin: String, ck: SecretKey?, saveBiometrics: Boolean, activity: FragmentActivity?) {
        _uiState.update {
            it.copy(
                migrationActive = true, migrationRollback = rollback,
                migrationRotate = false, // прямая миграция или откат — не ротация
                migrationStage = if (rollback) "Готовим возврат к прежней схеме…" else "Готовим обновление шифрования…",
                migrationTexts = -1, migrationTextsTotal = 0,
                migrationMedia = -1, migrationMediaTotal = 0,
                migrationFinished = false, migrationFailed = null,
                showPinInput = false
            )
        }
        viewModelScope.launch {
            var newCode: String? = null
            try {
                val contentKey = ck ?: contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
                if (!rollback) {
                    val legacy = deriveKey(pin, currentUid)

                    stage("Пере-шифровка текстов")
                    repository.migrateContentToV2(currentUid, legacy, contentKey) { done, total ->
                        _uiState.update { it.copy(migrationTexts = done, migrationTextsTotal = total) }
                    }

                    stage("Пере-шифровка медиа")
                    repository.migrateMediaToV2(currentUid, legacy, contentKey) { done, total ->
                        _uiState.update { it.copy(migrationMedia = done, migrationMediaTotal = total) }
                    }

                    stage("Обновление настроек")
                    contentKeyManager.enablePin(currentUid, ContentKeyManager.DOMAIN_SAVED, contentKey, pin)
                    repository.markCryptoV2(currentUid)
                    biometricPinManager.clearSavedPin(currentUid)
                    cryptoV2 = true
                    encryptionKey = contentKey
                    legacyKey = deriveKey(pin, currentUid)

                    stage("Ключ восстановления")
                    if (!contentKeyManager.recoveryWrapExists(currentUid, ContentKeyManager.DOMAIN_SAVED)) {
                        newCode = contentKeyManager.setupRecovery(
                            currentUid,
                            mapOf(ContentKeyManager.DOMAIN_SAVED to contentKey)
                        )
                    }

                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try { repository.migrateMediaToV2(currentUid, legacyKey!!, contentKey) } catch (_: Exception) {}
                    }
                } else {
                    stage("Возврат текстов к прежней схеме")
                    repository.rollbackToV1(currentUid, contentKey, pin) { done, total ->
                        _uiState.update { it.copy(migrationTexts = done, migrationTextsTotal = total) }
                    }

                    stage("Обновление настроек")
                    repository.markCryptoV1(currentUid, pin)
                    cryptoV2 = false
                    encryptionKey = deriveKey(pin, currentUid)
                    legacyKey = encryptionKey

                    if (saveBiometrics) {
                        when (val res = biometricPinManager.savePinSecurely(currentUid, pin)) {
                            is BiometricSaveResult.Success ->
                                _uiState.update { it.copy(keystoreSecurityLevel = res.securityLevel) }
                            else -> {}
                        }
                    }
                }

                _uiState.update { it.copy(migrationFinished = true, migrationStage = "Готово", recoveryCode = newCode) }
            } catch (e: Exception) {
                _uiState.update { it.copy(migrationFailed = e.message ?: "Ошибка миграции") }
            }
        }
    }

    private fun stage(s: String) = _uiState.update { it.copy(migrationStage = s) }

    /**
     * Конвейер ротации CK (TODO §14.4) — тот же, что в SavedMessagesViewModel:
     * пере-шифровка под новый ключ → новый recovery-код → замена локальных обёрток.
     * Вызывается при возобновлении прерванной ротации из дневника.
     */
    private fun runKeyRotationPipeline(pin: String, newCK: SecretKey) {
        _uiState.update {
            it.copy(
                migrationActive = true, migrationRollback = false, migrationRotate = true,
                migrationStage = "Готовим сброс ключа восстановления…",
                migrationTexts = -1, migrationTextsTotal = 0,
                migrationMedia = -1, migrationMediaTotal = 0,
                migrationFinished = false, migrationFailed = null,
                recoveryCode = null, showPinInput = false
            )
        }
        viewModelScope.launch {
            try {
                val oldCK = contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)

                stage("Пере-шифровка текстов")
                repository.rotateContentKey(currentUid, oldCK, newCK) { s, done, total ->
                    if (s.startsWith("Пере-шифровка текстов")) {
                        _uiState.update { it.copy(migrationStage = s, migrationTexts = done, migrationTextsTotal = total) }
                    } else {
                        _uiState.update { it.copy(migrationStage = s, migrationMedia = done, migrationMediaTotal = total) }
                    }
                }

                stage("Ключ восстановления")
                val newCode = contentKeyManager.setupRecovery(
                    currentUid, mapOf(ContentKeyManager.DOMAIN_SAVED to newCK)
                )

                stage("Обновление настроек")
                contentKeyManager.completeKeyRotation(currentUid, ContentKeyManager.DOMAIN_SAVED, pin)
                encryptionKey = newCK
                legacyKey = null
                cryptoV2 = true
                // Триггер settingsFlow: другие экземпляры VM подтянут новый ключ
                val timeout = runCatching { repository.loadSettings(currentUid)?.lockTimeout }.getOrNull() ?: 5
                repository.updateLockTimeout(currentUid, timeout)

                _uiState.update { it.copy(migrationFinished = true, migrationStage = "Готово", recoveryCode = newCode) }
            } catch (e: Exception) {
                // Контент не пострадал: старый CK и PIN-обёртка на месте, ротация возобновится
                Log.w("DiaryVM", "Ротация CK не завершена: ${e.message}")
                _uiState.update { it.copy(migrationFailed = e.message ?: "Ошибка сброса ключа") }
            }
        }
    }

    /** Экран миграции закрыт: разблокируем обычный режим. */
    fun finishMigration() {
        val st = _uiState.value
        _uiState.update { it.copy(migrationActive = false, recoveryCode = null) }
        if (st.migrationFinished && !st.isUnlocked) {
            resumingRotation = false
            lastUnlockTime = System.currentTimeMillis()
            _uiState.update {
                it.copy(isUnlocked = true, showPinInput = false, pinError = false, isBiometricEnabled = hasBiometricPinSaved())
            }
            startDiaryFlow()
        } else if (!st.migrationFinished && resumingRotation) {
            // Возобновление ротации не удалось — вход не блокируем: пускаем со старым ключом
            resumingRotation = false
            encryptionKey = contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
            lastUnlockTime = System.currentTimeMillis()
            _uiState.update {
                it.copy(isUnlocked = true, showPinInput = false, pinError = false, isBiometricEnabled = hasBiometricPinSaved())
            }
            startDiaryFlow()
        } else if (!st.migrationFinished) {
            // Прерванная миграция/ротация: возвращаем PIN-гейт — контент не пострадал
            _uiState.update { it.copy(showPinInput = true, pinError = false) }
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

    // Biometric logic
    fun launchBiometricUnlock(activity: FragmentActivity, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val migrated = runCatching { repository.loadSettings(currentUid)?.cryptoV2 == true }.getOrDefault(false)
            if (migrated) {
                // v2: разворот bio-обёртки CK — расшифровка физически требует живой биометрии
                contentKeyManager.bioUnwrapCKWithPrompt(
                    activity, currentUid, ContentKeyManager.DOMAIN_SAVED,
                    "Дневник", "Подтвердите биометрию"
                ) { ck, err ->
                    if (ck != null) {
                        encryptionKey = ck
                        legacyKey = null
                        lastUnlockTime = System.currentTimeMillis()
                        // Откат v2 → v1 требует PIN — подсказываем
                        if (!contentKeyManager.migrationEnabled()) {
                            _uiState.update { it.copy(infoMessage = "Схема шифрования устарела: войдите по PIN, чтобы вернуть прежнюю") }
                        }
                        _uiState.update {
                            it.copy(
                                isUnlocked = true, showPinInput = false, pinError = false,
                                isBiometricEnabled = true
                            )
                        }
                        startDiaryFlow()
                        onResult(true)
                    } else {
                        if (err != null) _uiState.update { it.copy(error = err) }
                        onResult(false)
                    }
                }
                return@launch
            }

            // v1 (до миграции): прежний путь — расшифровка сохранённого PIN
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
                                infoMessage = "Дневник разблокирован (${unlockResult.securityLevel.badge})"
                            ) }
                            startDiaryFlow()
                            onResult(true)
                        }
                        is BiometricUnlockResult.KeyPermanentlyInvalidated -> {
                            _uiState.update { it.copy(
                                error = unlockResult.message,
                                isUnlocked = false,
                                showPinInput = true,
                                isBiometricEnabled = false
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

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onResult(false)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onResult(false)
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.diary_title))
            .setSubtitle(activity.getString(R.string.diary_bio_unlock))
            .setNegativeButtonText(activity.getString(R.string.btn_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
        }
    }

    fun hasBiometricPinSaved(): Boolean {
        return if (cryptoV2) {
            contentKeyManager.bioWrapExists(currentUid, ContentKeyManager.DOMAIN_SAVED)
        } else {
            biometricPinManager.hasSavedPin(currentUid)
        }
    }

    fun getKeystoreSecurityLevel(): KeystoreSecurityLevel = biometricPinManager.getKeystoreSecurityLevel(currentUid)

    fun clearInfoMessage() = _uiState.update { it.copy(infoMessage = null) }

    /** Recovery-код показан и записан пользователем. */
    fun dismissRecoveryCode() = _uiState.update { it.copy(recoveryCode = null) }

    fun clearPinError() = _uiState.update { it.copy(pinError = false) }
}