package by.iposdev.visorlink.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.repository.AuthRepository
import by.iposdev.visorlink.data.repository.AuthState
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

// ─── ViewModel ────────────────────────────────────────────────────────────────

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    // Three-state auth stream — consumed by the root nav guard
    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.NoSession)

    // ── Auth (login / register) ───────────────────────────────────────────────

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(error = "Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            runCatching { authRepository.login(email.trim(), password) }
                .onSuccess { _uiState.value = AuthUiState(success = true) }
                .onFailure { _uiState.value = AuthUiState(error = friendlyMessage(it)) }
        }
    }

    /**
     * On success → success = true signals UI to navigate to VerifyEmailScreen.
     * The Auth account exists but email_verified == false; main app is still gated.
     */
    fun register(email: String, password: String, username: String) {
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            _uiState.value = AuthUiState(error = "Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            runCatching { authRepository.register(email.trim(), password, username.trim()) }
                .onSuccess { _uiState.value = AuthUiState(success = true) }
                .onFailure { _uiState.value = AuthUiState(error = friendlyMessage(it)) }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
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
        authRepository.logout()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun friendlyMessage(t: Throwable): String = t.message ?: "Something went wrong"
}