package by.iposdev.visorlink.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.repository.AuthRepository
import by.iposdev.visorlink.data.repository.AuthState
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.utils.TfaManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── UI state ─────────────────────────────────────────────────────────────────

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

data class VerifyEmailUiState(
    val isPolling: Boolean = false,
    val resendCooldown: Int = 0,       // seconds remaining on resend cooldown
    val resendLoading: Boolean = false,
    val error: String? = null,
    val verified: Boolean = false
)

data class TfaUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val tfaPassed: Boolean = false
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val tfaManager: TfaManager,
    private val fcmManager: by.iposdev.visorlink.utils.FcmManager
) : ViewModel() {

    // Three-state auth stream — consumed by the root nav guard
    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.NoSession)

    // Reactive user profile that updates when auth state changes
    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentUserProfile: Flow<UserProfile?> = authRepository.authState.flatMapLatest { state ->
        val uid = when (state) {
            is AuthState.Verified -> state.user.uid
            is AuthState.Unverified -> state.user.uid
            else -> null
        }
        if (uid != null) userRepository.userProfileFlow(uid) else flowOf(null)
    }

    // ── 2FA state ─────────────────────────────────────────────────────────────

    private val _tfaPassed = MutableStateFlow(false)
    val tfaPassed: StateFlow<Boolean> = _tfaPassed.asStateFlow()

    private val _tfaUiState = MutableStateFlow(TfaUiState())
    val tfaUiState: StateFlow<TfaUiState> = _tfaUiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val isTfaRequired: StateFlow<Boolean> = combine(
        authState,
        currentUserProfile,
        _tfaPassed
    ) { state, profile, passed ->
        Triple(state, profile, passed)
    }.flatMapLatest { (state, profile, passed) ->
        flow {
            if (state is AuthState.Verified && profile?.tfaEnabled == true && !passed) {
                // Check cache
                val authTime = authRepository.getAuthTime()
                if (authTime != null && tfaManager.isTfaPassed(authTime)) {
                    _tfaPassed.value = true
                    emit(false)
                } else {
                    emit(true)
                }
            } else {
                emit(false)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Флаг полной готовности пользовательской сессии.
     * true ТОЛЬКО если:
     * 1. Пользователь верифицирован (AuthState.Verified).
     * 2. Профиль пользователя загружен (profile != null).
     * 3. 2FA либо подтверждена, либо отключена.
     *
     * Пока false — переход в чаты и регистрация FCM токена СТРОГО ЗАБЛОКИРОВАНЫ.
     */
    val isSessionReady: StateFlow<Boolean> = combine(
        authState,
        currentUserProfile,
        _tfaPassed
    ) { state, profile, passed ->
        if (state !is AuthState.Verified) return@combine false
        if (profile == null) return@combine false
        if (profile.tfaEnabled) {
            passed
        } else {
            true
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            authState.collect { state ->
                if (state is AuthState.NoSession) {
                    // При отсутствии сессии / выходе на экран логина токен FCM должен быть полностью аннулирован
                    fcmManager.revokeToken()
                }
            }
        }
    }

    fun request2FA(method: String) {
        viewModelScope.launch {
            _tfaUiState.value = TfaUiState(isLoading = true)
            runCatching { authRepository.request2FA(method) }
                .onSuccess { _tfaUiState.value = TfaUiState(isLoading = false) }
                .onFailure { _tfaUiState.value = TfaUiState(error = friendlyMessage(it)) }
        }
    }

    fun verify2FA(code: String) {
        viewModelScope.launch {
            _tfaUiState.value = TfaUiState(isLoading = true)
            runCatching { authRepository.verify2FA(code) }
                .onSuccess {
                    val authTime = authRepository.getAuthTime()
                    if (authTime != null) {
                        tfaManager.setTfaPassed(authTime)
                    }
                    _tfaPassed.value = true
                    _tfaUiState.value = TfaUiState(tfaPassed = true)
                    viewModelScope.launch {
                        fcmManager.syncTokenAfter2FA()
                    }
                }
                .onFailure { _tfaUiState.value = TfaUiState(error = "Invalid code or expired") }
        }
    }

    /**
     * Вызывается когда сессия верифицирована и 2FA пройдена (или не требуется).
     * Настраивает FCM токен строго после 2FA.
     */
    fun onSessionReadyAfter2FA() {
        viewModelScope.launch {
            if (isSessionReady.value) {
                fcmManager.syncTokenAfter2FA()
            } else {
                android.util.Log.w("AuthViewModel", "Cannot sync FCM: session is NOT ready yet (2FA in progress or profile loading)")
            }
        }
    }

    fun clearTfaError() {
        _tfaUiState.value = _tfaUiState.value.copy(error = null)
    }

    // ── Auth (login / register) ───────────────────────────────────────────────

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _pendingInviteCode = MutableStateFlow<String?>(null)
    val pendingInviteCode: StateFlow<String?> = _pendingInviteCode.asStateFlow()

    fun setPendingInviteCode(code: String?) {
        _pendingInviteCode.value = code
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(error = "Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            // Перед логином убеждаемся, что старый FCM токен отозван,
            // чтобы 2FA код не пришёл в пуше на ещё не аутентифицированное устройство
            fcmManager.revokeToken()
            runCatching { authRepository.login(email.trim(), password) }
                .onSuccess { _uiState.value = AuthUiState(success = true) }
                .onFailure { _uiState.value = AuthUiState(error = friendlyMessage(it)) }
        }
    }

    /**
     * On success → success = true signals UI to navigate to VerifyEmailScreen.
     * The Auth account exists but email_verified == false; main app is still gated.
     */
    fun register(email: String, password: String, username: String, inviteCode: String? = null) {
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            _uiState.value = AuthUiState(error = "Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            val codeToUse = inviteCode?.trim()?.ifBlank { null } ?: _pendingInviteCode.value?.trim()?.ifBlank { null }
            runCatching { authRepository.register(email.trim(), password, username.trim(), codeToUse) }
                .onSuccess { _uiState.value = AuthUiState(success = true) }
                .onFailure { _uiState.value = AuthUiState(error = friendlyMessage(it)) }
        }
    }

    suspend fun checkRegistrationCode(code: String): Boolean {
        return runCatching { authRepository.checkRegistrationCode(code) }.getOrDefault(false)
    }

    fun requestAccess(
        email: String,
        username: String,
        note: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (email.isBlank() || username.isBlank()) {
            onResult(false, "Пожалуйста, заполните email и имя пользователя")
            return
        }
        viewModelScope.launch {
            runCatching { authRepository.requestAccess(email, username, note) }
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, friendlyMessage(it)) }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank()) {
            onResult(false, "Укажите email для сброса пароля")
            return
        }
        viewModelScope.launch {
            runCatching { authRepository.sendPasswordResetEmail(email) }
                .onSuccess { onResult(true, null) }
                .onFailure { onResult(false, friendlyMessage(it)) }
        }
    }

    // ── Email verification screen ─────────────────────────────────────────────

    private val _verifyState = MutableStateFlow(VerifyEmailUiState())
    val verifyState: StateFlow<VerifyEmailUiState> = _verifyState.asStateFlow()

    private var pollingJob: Job? = null
    private var cooldownJob: Job? = null

    /** Start 5-second polling loop per guideline §5.1. Call from VerifyEmailScreen. */
    fun startVerificationPolling() {
        if (pollingJob?.isActive == true) return
        _verifyState.value = _verifyState.value.copy(isPolling = true)
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                runCatching { authRepository.reloadAndCheckVerified() }
                    .onSuccess { verified ->
                        if (verified) {
                            _verifyState.value = _verifyState.value.copy(
                                isPolling = false,
                                verified = true
                            )
                            return@launch
                        }
                    }
                    .onFailure { /* silent — keep polling */ }
            }
        }
    }

    fun stopVerificationPolling() {
        pollingJob?.cancel()
        _verifyState.value = _verifyState.value.copy(isPolling = false)
    }

    /**
     * Resend verification email with a 60-second UX cooldown per guideline §5.2.
     * Firebase enforces its own server-side rate limit; we catch too-many-requests.
     */
    fun resendVerificationEmail() {
        if (_verifyState.value.resendCooldown > 0) return
        viewModelScope.launch {
            _verifyState.value = _verifyState.value.copy(resendLoading = true, error = null)
            runCatching { authRepository.resendVerificationEmail() }
                .onSuccess { startResendCooldown() }
                .onFailure { e ->
                    val msg = friendlyMessage(e)
                    _verifyState.value = _verifyState.value.copy(
                        resendLoading = false,
                        error = msg
                    )
                }
        }
    }

    private fun startResendCooldown(seconds: Int = 60) {
        _verifyState.value = _verifyState.value.copy(
            resendLoading = false,
            resendCooldown = seconds
        )
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in (seconds - 1) downTo 0) {
                delay(1_000)
                _verifyState.value = _verifyState.value.copy(resendCooldown = remaining)
            }
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    fun logout() {
        stopVerificationPolling()
        tfaManager.clearCache()
        _tfaPassed.value = false
        viewModelScope.launch {
            // FCM токены автоматически отзываются при сбросе приложения / логауте
            fcmManager.revokeToken()
            authRepository.logout()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun friendlyMessage(t: Throwable): String = t.message ?: "Something went wrong"
}