package org.visorlink.app.ui.screens.signal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.visorlink.app.data.model.SignalRules
import org.visorlink.app.data.model.SignalStats
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.remote.EconomyException
import org.visorlink.app.data.repository.BlockRepository
import org.visorlink.app.data.repository.SignalRepository
import org.visorlink.app.data.repository.UserRepository

data class SignalUiState(
    val profile: UserProfile? = null,
    val stats: SignalStats = SignalStats.EMPTY,
    val achievements: Map<String, Long> = emptyMap(),
    val peerProfiles: Map<String, UserProfile> = emptyMap(),
    val isBuyingFreeze: Boolean = false,
    val freezeError: String? = null,
    val now: Long = System.currentTimeMillis()
)

class SignalViewModel(
    private val signalRepository: SignalRepository,
    private val userRepository: UserRepository,
    private val blockRepository: BlockRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignalUiState())
    val uiState: StateFlow<SignalUiState> = _uiState.asStateFlow()

    private val currentUid: String
        get() = auth.currentUser?.uid.orEmpty()

    init {
        val uid = currentUid
        if (uid.isNotEmpty()) {
            viewModelScope.launch {
                userRepository.currentUserFlow().collect { p ->
                    _uiState.update { it.copy(profile = p, now = System.currentTimeMillis()) }
                }
            }

            viewModelScope.launch {
                signalRepository.stats(uid).collect { stats ->
                    _uiState.update { it.copy(stats = stats, now = System.currentTimeMillis()) }
                    loadPeerProfiles(stats.peers.keys)
                }
            }

            viewModelScope.launch {
                signalRepository.achievements(uid).collect { achs ->
                    _uiState.update { it.copy(achievements = achs, now = System.currentTimeMillis()) }
                }
            }
        }
    }

    private fun loadPeerProfiles(uids: Set<String>) {
        val currentPeers = _uiState.value.peerProfiles
        val missing = uids.filter { it !in currentPeers && it != currentUid }
        if (missing.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            for (pUid in missing) {
                val p = userRepository.getUserProfile(pUid)
                if (p != null) {
                    _uiState.update {
                        it.copy(peerProfiles = it.peerProfiles + (pUid to p))
                    }
                }
            }
        }
    }

    fun updateNow() {
        _uiState.update { it.copy(now = System.currentTimeMillis()) }
    }

    fun buyStreakFreeze(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBuyingFreeze = true, freezeError = null) }
            try {
                signalRepository.buyStreakFreeze()
                _uiState.update { it.copy(isBuyingFreeze = false) }
                onSuccess()
            } catch (e: Exception) {
                val reason = (e as? EconomyException)?.reason
                val errorMsg = when (reason) {
                    "max" -> "В запасе не больше ${SignalRules.FREEZE_MAX} заморозок."
                    "not-enough-bits" -> "Нужно ${SignalRules.FREEZE_PRICE} Bits."
                    else -> (e as? EconomyException)?.message ?: "Не удалось купить заморозку. Попробуйте ещё раз."
                }
                _uiState.update { it.copy(isBuyingFreeze = false, freezeError = errorMsg) }
            }
        }
    }

    fun setShowStreak(show: Boolean) {
        viewModelScope.launch {
            try {
                userRepository.updateShowStreak(show)
            } catch (_: Exception) {}
        }
    }

    fun clearError() {
        _uiState.update { it.copy(freezeError = null) }
    }
}
