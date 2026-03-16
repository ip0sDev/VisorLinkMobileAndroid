package by.iposdev.visorlink.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.repository.AuthRepository
import by.iposdev.visorlink.utils.PresenceManager
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val presenceManager: PresenceManager
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.currentUser)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState(error = "Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                authRepository.login(email.trim(), password)
                authRepository.currentUid?.let { presenceManager.attach(it) }
                _uiState.value = AuthUiState(success = true)
            } catch (e: Exception) {
                _uiState.value = AuthUiState(error = e.message ?: "Login failed")
            }
        }
    }

    fun register(email: String, password: String, username: String) {
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            _uiState.value = AuthUiState(error = "Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                authRepository.register(email.trim(), password, username.trim())
                authRepository.currentUid?.let { presenceManager.attach(it) }
                _uiState.value = AuthUiState(success = true)
            } catch (e: Exception) {
                _uiState.value = AuthUiState(error = e.message ?: "Registration failed")
            }
        }
    }

    fun logout() {
        presenceManager.detach()
        authRepository.logout()
    }

    fun initPresenceIfLoggedIn() {
        authRepository.currentUid?.let { presenceManager.attach(it) }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}