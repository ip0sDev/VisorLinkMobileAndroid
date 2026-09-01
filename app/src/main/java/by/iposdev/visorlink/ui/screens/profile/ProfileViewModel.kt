package by.iposdev.visorlink.ui.screens.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.repository.AuthRepository
import by.iposdev.visorlink.data.repository.UserRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: UserProfile? = null,
    val isEditing: Boolean = false,
    val editDisplayName: String = "",
    val editBio: String = "",
    val editUsername: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val usernameAvailable: Boolean? = null,
    val checkingUsername: Boolean = false,
    val tgCode: String? = null,
    val isGeneratingCode: Boolean = false
)

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val fcmManager: by.iposdev.visorlink.utils.FcmManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.currentUserFlow().collect { user ->
                _uiState.update {
                    it.copy(
                        user = user,
                        editDisplayName = user?.displayName ?: "",
                        editBio = user?.bio ?: "",
                        editUsername = user?.username ?: ""
                    )
                }
            }
        }
    }

    fun startEditing() = _uiState.update { it.copy(isEditing = true) }

    fun cancelEditing() = _uiState.update {
        it.copy(
            isEditing = false,
            editDisplayName = it.user?.displayName ?: "",
            editBio = it.user?.bio ?: "",
            editUsername = it.user?.username ?: "",
            error = null
        )
    }

    fun onDisplayNameChange(v: String) = _uiState.update { it.copy(editDisplayName = v) }
    fun onBioChange(v: String) = _uiState.update { it.copy(editBio = v.take(160)) }
    fun onUsernameChange(v: String) {
        val clean = v.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }
        _uiState.update { it.copy(editUsername = clean, usernameAvailable = null) }
    }

    fun checkUsername() {
        val uname = _uiState.value.editUsername
        if (uname == _uiState.value.user?.username) {
            _uiState.update { it.copy(usernameAvailable = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(checkingUsername = true) }
            try {
                val (available, _) = userRepository.checkUsername(uname)
                _uiState.update { it.copy(usernameAvailable = available, checkingUsername = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(checkingUsername = false) }
            }
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val state = _uiState.value
                val user = state.user ?: return@launch
                if (state.editDisplayName != user.displayName || state.editBio != user.bio)
                    userRepository.updateProfile(state.editDisplayName, state.editBio)
                if (state.editUsername != user.username)
                    userRepository.changeUsername(state.editUsername, state.editDisplayName)
                _uiState.update {
                    it.copy(isLoading = false, isEditing = false, successMessage = "Profile updated!")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                userRepository.uploadAvatar(uri)
                _uiState.update { it.copy(isLoading = false, successMessage = "Avatar updated!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { fcmManager.revokeToken() }
            authRepository.logout()
        }
    }

    fun clearMessages() = _uiState.update { it.copy(error = null, successMessage = null, tgCode = null) }

    fun generateTgCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingCode = true, error = null) }
            try {
                val code = userRepository.generateTgCode()
                _uiState.update { it.copy(tgCode = code, isGeneratingCode = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isGeneratingCode = false) }
            }
        }
    }

    fun unbindTelegram() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                userRepository.unbindTelegram()
                _uiState.update { it.copy(isLoading = false, successMessage = "Telegram unlinked!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
