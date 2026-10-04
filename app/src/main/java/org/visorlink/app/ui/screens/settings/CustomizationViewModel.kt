package org.visorlink.app.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.visorlink.app.data.model.ProfileAppearance
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.UserRepository

/** Какое из медиа сейчас загружается. */
enum class AppearanceUpload { BACKGROUND, BANNER }

data class CustomizationUiState(
    val profile: UserProfile? = null,
    /** То, что видит редактор: применяется мгновенно, до ответа сервера. */
    val appearance: ProfileAppearance = ProfileAppearance.None,
    val uploading: AppearanceUpload? = null,
    val error: String? = null,
)

class CustomizationViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomizationUiState())
    val uiState: StateFlow<CustomizationUiState> = _uiState.asStateFlow()

    /**
     * Записи идут строго по очереди, и каждая строится из локального
     * [CustomizationUiState.appearance], а не из профиля из потока: раньше два
     * быстрых нажатия читали один и тот же старый профиль, и второе затирало первое.
     */
    private val writeLock = Mutex()
    private var pendingWrites = 0

    init {
        viewModelScope.launch {
            userRepository.currentUserFlow().collect { profile ->
                _uiState.update { state ->
                    state.copy(
                        profile = profile,
                        // Пока есть неподтверждённые записи, поток может принести
                        // устаревшее значение — локальное не перетираем
                        appearance = if (pendingWrites > 0) state.appearance
                        else ProfileAppearance.parse(profile?.customization),
                    )
                }
            }
        }
    }

    fun update(transform: (ProfileAppearance) -> ProfileAppearance) {
        val next = transform(_uiState.value.appearance)
        if (next == _uiState.value.appearance) return
        _uiState.update { it.copy(appearance = next, error = null) }
        pendingWrites++
        viewModelScope.launch {
            writeLock.withLock {
                try {
                    val base = _uiState.value.profile?.customization.orEmpty()
                    // Пишем самое свежее локальное состояние: промежуточные шаги
                    // очереди схлопываются в одну запись
                    userRepository.updateCustomization(_uiState.value.appearance.writeTo(base))
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
                } finally {
                    pendingWrites--
                }
            }
        }
    }

    fun upload(uri: Uri, target: AppearanceUpload) {
        if (_uiState.value.uploading != null) return
        _uiState.update { it.copy(uploading = target, error = null) }
        viewModelScope.launch {
            try {
                val url = userRepository.uploadFile(uri)
                update {
                    when (target) {
                        AppearanceUpload.BACKGROUND -> it.copy(backgroundUrl = url)
                        AppearanceUpload.BANNER -> it.copy(bannerUrl = url)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            } finally {
                _uiState.update { it.copy(uploading = null) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
