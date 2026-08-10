package by.iposdev.visorlink.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomizationUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val uploadingBg: Boolean = false,
    val uploadingGif: Boolean = false
)

class CustomizationViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomizationUiState())
    val uiState: StateFlow<CustomizationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userRepository.currentUserFlow().collect { profile ->
                _uiState.update { it.copy(profile = profile, isLoading = false) }
            }
        }
    }

    fun updateCustomization(key: String, value: Any?) {
        val currentCust = _uiState.value.profile?.customization ?: emptyMap()
        val newCust = currentCust.toMutableMap()
        if (value == null) newCust.remove(key) else newCust[key] = value
        
        viewModelScope.launch {
            try {
                userRepository.updateCustomization(newCust)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun uploadCustomBackground(uri: Uri, field: String) {
        viewModelScope.launch {
            if (field == "bgUrl") _uiState.update { it.copy(uploadingBg = true) }
            else _uiState.update { it.copy(uploadingGif = true) }
            
            try {
                val url = userRepository.uploadFile(uri)
                updateCustomization(field, url)
                _uiState.update { it.copy(successMessage = "Uploaded!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                if (field == "bgUrl") _uiState.update { it.copy(uploadingBg = false) }
                else _uiState.update { it.copy(uploadingGif = false) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
