package org.visorlink.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.visorlink.app.data.repository.AuthRepository
import org.visorlink.app.data.repository.SessionInfo
import org.visorlink.app.data.repository.SessionRepository

data class SessionsUiState(
    val sessions: List<SessionInfo> = emptyList(),
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val error: String? = null,
)

class SessionsViewModel(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    init {
        val uid = authRepository.currentUid
        if (uid != null) {
            viewModelScope.launch {
                sessionRepository.sessions(uid)
                    .catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
                    .collect { list -> _state.update { it.copy(sessions = list, isLoading = false) } }
            }
        } else {
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun terminate(sessionId: String) = runAction { sessionRepository.terminate(sessionId) }

    fun terminateOthers() = runAction { sessionRepository.terminateOthers() }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            runCatching { block() }
                .onSuccess { _state.update { it.copy(isBusy = false) } }
                .onFailure { e ->
                    val msg = if (SessionRepository.isTfaRequired(e)) "Нужна 2FA в этой сессии" else e.message
                    _state.update { it.copy(isBusy = false, error = msg) }
                }
        }
    }
}
