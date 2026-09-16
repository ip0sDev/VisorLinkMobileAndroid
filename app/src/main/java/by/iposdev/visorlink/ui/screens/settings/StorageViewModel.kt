package by.iposdev.visorlink.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.repository.GoogleDriveConfigRepository
import by.iposdev.visorlink.utils.GoogleDriveAuthManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageUiState(
    val isDriveConnected: Boolean = false,
    val driveAccountEmail: String? = null,
    val driveFolderName: String = "VisorLink Media",
    val isLoading: Boolean = false,
    val error: String? = null
)

class StorageViewModel(
    val driveAuthManager: GoogleDriveAuthManager,
    val driveConfigRepository: GoogleDriveConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            driveAuthManager.state.collect { authState ->
                _uiState.update {
                    it.copy(
                        isDriveConnected = authState.isConnected,
                        driveAccountEmail = authState.accountEmail,
                        isLoading = authState.isLoading,
                        error = authState.error
                    )
                }
            }
        }
    }

    suspend fun getGoogleDriveClientId(): String {
        return driveConfigRepository.getGoogleDriveClientId()
    }

    fun handleSignInResult(account: GoogleSignInAccount, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val res = driveAuthManager.handleSignInResult(account)
            if (res.isSuccess) {
                onSuccess()
            } else {
                onError(res.exceptionOrNull()?.message ?: "Google Drive connection error")
            }
        }
    }

    fun disconnectDrive() {
        viewModelScope.launch {
            val clientId = driveConfigRepository.getGoogleDriveClientId()
            driveAuthManager.disconnect(clientId)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}