package org.visorlink.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.visorlink.app.data.idcard.IdCardPosition
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.IdCardException
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.data.repository.IdCardState
import org.visorlink.app.data.repository.UserRepository

data class IdCardSettingsUiState(
    val busy: Boolean = false,
    /** Ошибка действия; текст по `reason` подбирает экран (кулдаун, прочее). */
    val error: Throwable? = null,
    /** Кулдаун, о котором сказал сервер (мс эпохи), — если часы устройства отстают. */
    val serverWaitUntil: Long = 0,
)

/** Настройки ID-карты (веб: IdCardTab.jsx): режим, вид, место в профиле. Инвентарь скинов — IdSkinInventory. */
class IdCardSettingsViewModel(
    private val repo: IdCardRepository,
    private val users: UserRepository,
) : ViewModel() {

    val profile: StateFlow<UserProfile?> = users.currentUserFlow().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val card: StateFlow<IdCardState> = profile.map { it?.uid }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(IdCardState.Loading) else repo.cardFlow(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, IdCardState.Loading)

    private val _ui = MutableStateFlow(IdCardSettingsUiState())
    val ui: StateFlow<IdCardSettingsUiState> = _ui.asStateFlow()

    fun clearError() = _ui.update { it.copy(error = null) }

    private fun run(onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                action()
                onSuccess()
            } catch (e: IdCardException) {
                if (e.reason == "cooldown" && e.waitMs > 0) {
                    _ui.update { it.copy(serverWaitUntil = System.currentTimeMillis() + e.waitMs) }
                }
                _ui.update { it.copy(error = e) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    /** Смена режима: вид сохраняется только при том же режиме, при переходе — сбрасывается. */
    fun setMode(mode: IdMode, currentMode: IdMode, speciesDraft: String, onDone: () -> Unit) = run(onDone) {
        val keep = if (mode.isSpecial && mode == currentMode) speciesDraft.trim().ifEmpty { null } else null
        repo.setMode(mode, keep)
    }

    fun saveSpecies(mode: IdMode, species: String, onDone: () -> Unit) = run(onDone) {
        repo.setMode(mode, species.trim().ifEmpty { null })
    }

    /** Место карты в профиле — не PRO, пишется в customization, чужие ключи сохраняются. */
    fun setPosition(position: IdCardPosition) {
        val p = profile.value ?: return
        val next = p.customization.toMutableMap().apply { put(IdCardPosition.KEY, position.id) }
        viewModelScope.launch {
            try {
                users.updateCustomization(next)
            } catch (e: Exception) {
                _ui.update { it.copy(error = e) }
            }
        }
    }
}
