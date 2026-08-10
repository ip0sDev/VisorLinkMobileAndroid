package by.iposdev.visorlink.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProUiState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null
)

class ProViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProUiState())
    val uiState: StateFlow<ProUiState> = _uiState.asStateFlow()

    fun buyPro(useTrial: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            try {
                userRepository.buyPro(useTrial)
                val msg = if (useTrial) "PRO триал активирован на 1 день!" else "PRO активирован на 30 дней!"
                _uiState.update { it.copy(isLoading = false, successMessage = msg) }
            } catch (e: Exception) {
                var msg = e.message ?: "Неизвестная ошибка"

                // Парсим ошибки от Cloud Function
                if (msg.contains("Not enough bits", ignoreCase = true) || msg.contains("bits", ignoreCase = true)) {
                    msg = "Недостаточно Битов для покупки PRO."
                } else if (msg.contains("already pro", ignoreCase = true) || msg.contains("Already active", ignoreCase = true)) {
                    msg = "У вас уже активна подписка PRO."
                } else if (msg.contains("trial already used", ignoreCase = true)) {
                    msg = "Вы уже использовали пробную версию."
                }

                _uiState.update { it.copy(isLoading = false, error = msg) }
            }
        }
    }

    fun toggleShowStreak(show: Boolean) {
        viewModelScope.launch {
            try {
                userRepository.updateShowStreak(show)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Ошибка обновления стрик-статуса") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}