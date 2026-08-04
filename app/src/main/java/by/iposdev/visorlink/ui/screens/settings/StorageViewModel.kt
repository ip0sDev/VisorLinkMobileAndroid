package by.iposdev.visorlink.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.utils.CdnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageUiState(
    val stats: Map<String, Any> = emptyMap(),
    val files: List<Map<String, Any>> = emptyList(),
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val error: String? = null
)

class StorageViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val stats = CdnService.getStats()
            val files = CdnService.listFiles()
            _uiState.update { it.copy(stats = stats, files = files, isLoading = false) }
        }
    }

    fun deleteFile(mediaId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            val success = CdnService.deleteFile(mediaId)
            if (success) {
                refresh()
            } else {
                _uiState.update { it.copy(error = "Failed to delete file", isDeleting = false) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}