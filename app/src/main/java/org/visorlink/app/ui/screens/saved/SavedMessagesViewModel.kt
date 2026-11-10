// ui/screens/saved/SavedMessagesViewModel.kt
package org.visorlink.app.ui.screens.saved

import android.app.Application
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.util.Log
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
    val editingMessage: SavedMessage?        = null,
    // ── Миграция шифрования (TODO §14.4): полноэкранный флоу с прогрессом ──
    val migrationActive: Boolean             = false,
    val migrationRollback: Boolean           = false,
    // Ротация CK (сброс ключа восстановления) — тот же экран, другой набор стадий
    val migrationRotate: Boolean             = false,
    val migrationStage: String               = "",
    val migrationTexts: Int                  = -1,   // done; -1 = ещё не начато
    val migrationTextsTotal: Int             = 0,
    val migrationMedia: Int                  = -1,
    val migrationMediaTotal: Int             = 0,
    val migrationFinished: Boolean           = false,
    val migrationFailed: String?             = null,
    // Recovery-код миграции v2 (показывается один раз; TODO §14.4)
    val recoveryCode: String?                = null,
    // Ошибка проверки PIN в диалоге сброса ключа восстановления
    val recoveryResetError: String?          = null
)

class SavedMessagesViewModel(
    private val repository: SavedMessagesRepository,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val voicePlayer: VoicePlayerManager,
    private val context: Application,
    private val draftManager: DraftManager,
    private val forwardRepository: ForwardRepository,
    private val biometricPinManager: BiometricPinManager,
    private val contentKeyManager: ContentKeyManager
) : AndroidViewModel(context) {

    val currentUid: String get() = auth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(SavedMessagesUiState())
    val uiState: StateFlow<SavedMessagesUiState> = _uiState.asStateFlow()

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
    // Текущая подписка на сообщения: перезапускается при смене ключа (ротация CK)
    private var messagesJob: kotlinx.coroutines.Job? = null

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
                cryptoV2 = settings?.cryptoV2 == true
                _uiState.update { it.copy(isEncryptionEnabled = pinEnabled) }

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
                    startMessagesFlow()
                } else {
                    if (!_uiState.value.isUnlocked) {
                        _uiState.update { it.copy(showPinInput = true, isLoading = false) }
                    } else if (settings?.cryptoV2 == true) {
                        // Ротация CK завершилась в другом экземпляре VM (настройки/дневник):
                        // подтягиваем новый ключ из Keystore-обёртки (TODO §14.4)
                        encryptionKey = contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
                        legacyKey = null
                        startMessagesFlow()
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
        // Перезапуск при смене ключа: старая подписка расшифровывала прежним ключом
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.messagesFlow(currentUid, encryptionKey, includeDiary = false, legacyKey = legacyKey).collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }
    }

    fun onPinEntered(pin: String, enableBiometrics: Boolean, activity: FragmentActivity? = null) {
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
                // v2: проверка PIN = разворот PIN-обёртки CK (локально на Default диспетчере без блокировки UI)
                ck = withContext(Dispatchers.Default) {
                    contentKeyManager.ckByPin(currentUid, ContentKeyManager.DOMAIN_SAVED, pin)
                }
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
                    legacyKey = withContext(Dispatchers.Default) { deriveKey(pin, currentUid) }
                    startMigrationFlow(rollback = true, pin = pin, ck = ck, enableBiometrics = enableBiometrics, activity = activity)
                    return@launch
                }
                legacyKey = null
            } else {
                // v1: проверка по серверному хэшу (до миграции).
                // После стирания данных (забытый recovery-код, TODO §14.4) серверного хэша нет,
                // а PIN включён заново под свежим CK — проверяем разворотом локальной PIN-обёртки
                val freshCK = if (settings?.pinHash == null) {
                    withContext(Dispatchers.Default) {
                        contentKeyManager.ckByPin(currentUid, ContentKeyManager.DOMAIN_SAVED, pin)
                    }
                } else null
                val valid = freshCK != null || withContext(Dispatchers.Default) {
                    repository.verifyPin(currentUid, pin)
                }
                if (!valid) {
                    _uiState.update { it.copy(pinError = true) }
                    return@launch
                }
                if (freshCK != null) {
                    // Свежий CK после стирания: миграционный флоу выдаст новый recovery-код
                    ck = freshCK
                    if (contentKeyManager.migrationEnabled()) {
                        startMigrationFlow(rollback = false, pin = pin, ck = freshCK, enableBiometrics = enableBiometrics, activity = activity)
                        return@launch
                    }
                    legacyKey = null
                } else {
                    val derived = withContext(Dispatchers.Default) { deriveKey(pin, currentUid) }
                    legacyKey = derived
                    ck = derived

                    // Миграция v1 → v2 — полноэкранный флоу с прогрессом и recovery-кодом (TODO §14.4)
                    if (contentKeyManager.migrationEnabled()) {
                        startMigrationFlow(rollback = false, pin = pin, ck = null, enableBiometrics = enableBiometrics, activity = activity)
                        return@launch
                    }
                }
            }

            if (enableBiometrics) {
                if (legacyKey == null) {
                    // v2: bio-обёртка ключа контента через CryptoObject (настоящая привязка к биометрии)
                    saveBiometricAccess(activity, ck)
                } else {
                    // v1 (до миграции): прежнее хранение PIN под Keystore-ключом
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
            _uiState.update { it.copy(isUnlocked = true, showPinInput = false, pinError = false) }
            startMessagesFlow()
        }
    }

    /**
     * Полноэкранный миграционный флоу (TODO §14.4): стадии с прогрессом,
     * в конце — recovery-код с копированием. rollback=true — обратная миграция v2 → v1.
     */
    private fun startMigrationFlow(rollback: Boolean, pin: String, ck: SecretKey?, enableBiometrics: Boolean, activity: FragmentActivity?) {
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
                val contentKey = ck ?: withContext(Dispatchers.Default) {
                    contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
                }
                if (!rollback) {
                    val legacy = withContext(Dispatchers.Default) { deriveKey(pin, currentUid) }

                    stage("Пере-шифровка текстов")
                    repository.migrateContentToV2(currentUid, legacy, contentKey) { done, total ->
                        _uiState.update { it.copy(migrationTexts = done, migrationTextsTotal = total) }
                    }

                    stage("Обновление настроек")
                    withContext(Dispatchers.Default) {
                        contentKeyManager.enablePin(currentUid, ContentKeyManager.DOMAIN_SAVED, contentKey, pin)
                    }
                    repository.markCryptoV2(currentUid)
                    // Старые биометрические данные (хранение PIN) больше не нужны
                    biometricPinManager.clearSavedPin(currentUid)
                    cryptoV2 = true
                    encryptionKey = contentKey
                    legacyKey = legacy // остатки v1 читаются в этой сессии

                    stage("Ключ восстановления")
                    if (!contentKeyManager.recoveryWrapExists(currentUid, ContentKeyManager.DOMAIN_SAVED)) {
                        newCode = withContext(Dispatchers.Default) {
                            contentKeyManager.setupRecovery(
                                currentUid,
                                mapOf(ContentKeyManager.DOMAIN_SAVED to contentKey)
                            )
                        }
                    }

                    // Фоновая до-миграция медиа в IO, чтобы не блокировать интерфейс приложения
                    viewModelScope.launch(Dispatchers.IO) {
                        try { repository.migrateMediaToV2(currentUid, legacy, contentKey) } catch (_: Exception) {}
                    }
                } else {
                    stage("Возврат текстов к прежней схеме")
                    repository.rollbackToV1(currentUid, contentKey, pin) { done, total ->
                        _uiState.update { it.copy(migrationTexts = done, migrationTextsTotal = total) }
                    }

                    stage("Обновление настроек")
                    repository.markCryptoV1(currentUid, pin)
                    cryptoV2 = false
                    encryptionKey = withContext(Dispatchers.Default) { deriveKey(pin, currentUid) }
                    legacyKey = encryptionKey

                    if (enableBiometrics) {
                        // v1-биометрия: прежнее хранение PIN под Keystore-ключом
                        when (val res = biometricPinManager.savePinSecurely(currentUid, pin)) {
                            is BiometricSaveResult.Success ->
                                _uiState.update { it.copy(keystoreSecurityLevel = res.securityLevel) }
                            else -> {}
                        }
                    }
                }

                _uiState.update { it.copy(migrationFinished = true, migrationStage = "Готово", recoveryCode = newCode) }
            } catch (e: Exception) {
                // Данные не тронуты: недошедшие документы повторятся при следующем запуске
                _uiState.update { it.copy(migrationFailed = e.message ?: "Ошибка миграции") }
            }
        }
    }

    private fun stage(s: String) = _uiState.update { it.copy(migrationStage = s) }

    // ─── Сброс ключа восстановления (ротация CK, TODO §14.4) ─────────────────

    /**
     * Сброс ключа восстановления доступен только когда ключ сохранён локально
     * (Keystore-обёртка) — иначе восстанавливаемся recovery-кодом, а не ротацией.
     */
    fun canResetRecoveryKey(): Boolean =
        contentKeyManager.hasCK(currentUid, ContentKeyManager.DOMAIN_SAVED)

    /**
     * Точка входа сброса ключа восстановления (TODO §14.4): проверка текущего PIN
     * разворотом PIN-обёртки (с учётом lockout), затем ротация CK — контент
     * пере-шифруется под НОВЫЙ случайный ключ, старый recovery-код станет бесполезным.
     */
    fun startRecoveryResetFlow(pin: String) {
        viewModelScope.launch {
            // Lockout: экспоненциальная задержка после серии неудач (TODO §14.3)
            val remaining = contentKeyManager.lockoutRemainingMs(currentUid, ContentKeyManager.DOMAIN_SAVED)
            if (remaining > 0) {
                _uiState.update { it.copy(recoveryResetError = "Слишком много попыток, подождите ${remaining / 1000} с") }
                return@launch
            }
            // Текущий PIN обязателен: проверка разворотом PIN-обёртки (TODO §14.3)
            if (contentKeyManager.ckByPin(currentUid, ContentKeyManager.DOMAIN_SAVED, pin) == null) {
                contentKeyManager.registerFailedAttempt(currentUid, ContentKeyManager.DOMAIN_SAVED)
                _uiState.update { it.copy(recoveryResetError = "Неверный PIN-код") }
                return@launch
            }
            contentKeyManager.clearFailedAttempts(currentUid, ContentKeyManager.DOMAIN_SAVED)

            // Незавершённая ротация — продолжаем её, НЕ генерируя второй новый ключ
            val pendingCK = contentKeyManager.pendingRotationCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
            val newCK = if (contentKeyManager.resumeKeyRotationPending(currentUid, ContentKeyManager.DOMAIN_SAVED) && pendingCK != null) {
                pendingCK
            } else {
                contentKeyManager.beginKeyRotation(currentUid, ContentKeyManager.DOMAIN_SAVED)
            }
            runKeyRotationPipeline(pin, newCK)
        }
    }

    fun clearRecoveryResetError() = _uiState.update { it.copy(recoveryResetError = null) }

    /**
     * Конвейер ротации CK (TODO §14.4): пере-шифровка контента под новый ключ →
     * новый recovery-код (перезаписывает старую обёртку) → замена локальных обёрток.
     * Порядок выбран так, чтобы сбой на любом шаге оставлял ротацию возобновимой:
     * старый CK и PIN-обёртка живут до completeKeyRotation.
     */
    private fun runKeyRotationPipeline(pin: String, newCK: SecretKey) {
        _uiState.update {
            it.copy(
                migrationActive = true, migrationRollback = false, migrationRotate = true,
                migrationStage = "Готовим сброс ключа восстановления…",
                migrationTexts = -1, migrationTextsTotal = 0,
                migrationMedia = -1, migrationMediaTotal = 0,
                migrationFinished = false, migrationFailed = null,
                recoveryCode = null, showPinInput = false, recoveryResetError = null
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
                // Триггер settingsFlow: другие экземпляры VM (Избранное/дневник) подтянут новый ключ
                repository.updateLockTimeout(currentUid, _uiState.value.settings?.lockTimeout ?: 5)

                _uiState.update { it.copy(migrationFinished = true, migrationStage = "Готово", recoveryCode = newCode) }
            } catch (e: Exception) {
                // Контент не пострадал: старый CK и PIN-обёртка на месте, ротация возобновится
                Log.w("SavedVM", "Ротация CK не завершена: ${e.message}")
                _uiState.update { it.copy(migrationFailed = e.message ?: "Ошибка сброса ключа") }
            }
        }
    }

    // ─── Стирание «Избранного» (забытый recovery-код, TODO §14.4) ────────────

    /**
     * Escape-hatch: пользователь навсегда заперт (нет локального ключа, код не помнит) —
     * стираем всё и начинаем заново. После стирания PIN включается существующим
     * setPin-путём, миграционный флоу создаст новый CK и новый recovery-код.
     */
    fun wipeForgotCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.wipeAllSavedData(currentUid)
                encryptionKey = null
                legacyKey = null
                resumingRotation = false
                _uiState.update {
                    it.copy(
                        isUnlocked = true, showPinInput = false, pinError = false,
                        messages = emptyList(), isLoading = false,
                        infoMessage = "«Избранное» и дневник стёрты. Включите PIN заново — будет создан новый ключ шифрования."
                    )
                }
                startMessagesFlow()
            } catch (e: Exception) {
                Log.w("SavedVM", "Стирание не удалось: ${e.message}")
                _uiState.update { it.copy(isLoading = false, error = "Не удалось стереть данные: ${e.message}") }
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
            _uiState.update { it.copy(isUnlocked = true, showPinInput = false, pinError = false) }
            startMessagesFlow()
        } else if (!st.migrationFinished && resumingRotation) {
            // Возобновление ротации не удалось — вход не блокируем: пускаем со старым ключом
            resumingRotation = false
            encryptionKey = contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
            lastUnlockTime = System.currentTimeMillis()
            _uiState.update { it.copy(isUnlocked = true, showPinInput = false, pinError = false) }
            startMessagesFlow()
        } else if (!st.migrationFinished) {
            // Прерванная миграция/ротация: возвращаем PIN-гейт — контент не пострадал
            _uiState.update { it.copy(showPinInput = true, pinError = false) }
        }
    }

    /**
     * Включение биометрии (v2): bio-обёртка CK через BiometricPrompt.CryptoObject —
     * без живой биометрии ключ недоступен (TODO §14.3).
     */
    private fun saveBiometricAccess(activity: FragmentActivity?, ck: SecretKey?) {
        if (activity == null || ck == null) return
        contentKeyManager.bioWrapCKWithPrompt(
            activity,
            currentUid,
            ContentKeyManager.DOMAIN_SAVED,
            ck,
            "Биометрия для «Избранного»",
            "Подтвердите, чтобы разрешить разблокировку по биометрии"
        ) { ok, err ->
            if (!ok) {
                _uiState.update { it.copy(error = err ?: "Не удалось включить биометрию") }
            }
        }
    }

    fun lock() {
        encryptionKey = null
        legacyKey = null
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
        viewModelScope.launch {
            val migrated = runCatching { repository.loadSettings(currentUid)?.cryptoV2 == true }.getOrDefault(false)
            if (migrated) {
                // v2: разворот bio-обёртки CK — расшифровка физически требует живой биометрии
                contentKeyManager.bioUnwrapCKWithPrompt(
                    activity,
                    currentUid,
                    ContentKeyManager.DOMAIN_SAVED,
                    "Разблокировка «Избранного»",
                    "Подтвердите биометрию"
                ) { ck, err ->
                    if (ck != null) {
                        encryptionKey = ck
                        legacyKey = null
                        lastUnlockTime = System.currentTimeMillis()
                        // Откат v2 → v1 требует PIN (v1-ключ выводится из него) — подсказываем
                        if (!contentKeyManager.migrationEnabled()) {
                            _uiState.update { it.copy(infoMessage = "Схема шифрования устарела: войдите по PIN, чтобы вернуть прежнюю") }
                        }
                        _uiState.update { it.copy(isUnlocked = true, showPinInput = false, pinError = false) }
                        startMessagesFlow()
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
    }

    fun hasBiometricPinSaved(): Boolean {
        return if (cryptoV2) {
            contentKeyManager.bioWrapExists(currentUid, ContentKeyManager.DOMAIN_SAVED)
        } else {
            biometricPinManager.hasSavedPin(currentUid)
        }
    }

    fun getKeystoreSecurityLevel(): KeystoreSecurityLevel = biometricPinManager.getKeystoreSecurityLevel(currentUid)

    suspend fun getDecryptedFile(message: SavedMessage): File? {
        return repository.getDecryptedMediaFile(message, encryptionKey, legacyKey)
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

    /**
     * Включение PIN-защиты (CK-схема, TODO §14.3). Требует разблокированного состояния:
     * PIN-обёртка создаётся под текущий ключ контента, серверный pinHash не пишется.
     */
    fun setPin(pin: String) {
        // TODO §14.3: PIN короче 6 цифр не принимается (4 цифры = перебор за минуты)
        if (pin.length < 6) {
            _uiState.update { it.copy(error = "PIN должен быть не короче 6 цифр") }
            return
        }
        viewModelScope.launch {
            val ck = encryptionKey ?: contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
            contentKeyManager.enablePin(currentUid, ContentKeyManager.DOMAIN_SAVED, ck, pin)
            repository.enablePinProtection(currentUid)
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Новая семантика (TODO §14.4): снимаем только гейт — данные не удаляются
            val ck = encryptionKey ?: contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
            repository.disablePin(currentUid, ck)
            encryptionKey = contentKeyManager.getOrCreateCK(currentUid, ContentKeyManager.DOMAIN_SAVED)
            legacyKey = null
            biometricPinManager.clearSavedPin(currentUid)
            contentKeyManager.clearBioWrap(currentUid, ContentKeyManager.DOMAIN_SAVED)
            _uiState.update { it.copy(
                isEncryptionEnabled = false,
                keystoreSecurityLevel = KeystoreSecurityLevel.UNKNOWN,
                isLoading = false
            ) }
            startMessagesFlow()
        }
    }

    fun clearInfoMessage() = _uiState.update { it.copy(infoMessage = null) }

    /** Recovery-код показан и записан пользователем. */
    fun dismissRecoveryCode() = _uiState.update { it.copy(recoveryCode = null) }

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