package by.iposdev.visorlink.ui.screens.settings

import android.content.Context
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.utils.AppImageLoader
import by.iposdev.visorlink.utils.CacheConfig
import by.iposdev.visorlink.utils.CacheManager
import by.iposdev.visorlink.utils.CacheSizeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CacheUiState(
    val config: CacheConfig = CacheConfig(),
    val sizes: CacheSizeInfo? = null,
    val isLoading: Boolean = false,
    val isClearing: Boolean = false,
    val successMessageRes: Int? = null   // resource ID, локализуется в UI
)

class CacheViewModel(
    private val manager: CacheManager,
    private val context: Application
) : AndroidViewModel(context) {

    private val _state = MutableStateFlow(CacheUiState(config = manager.loadConfig()))
    val state: StateFlow<CacheUiState> = _state.asStateFlow()

    init { refreshSizes() }

    fun refreshSizes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val sizes = manager.getCacheSizes()
            _state.update { it.copy(sizes = sizes, isLoading = false) }
        }
    }

    fun setMaxImageMb(mb: Int) {
        val newConfig = _state.value.config.copy(maxImageMb = mb)
        _state.update { it.copy(config = newConfig) }
        manager.saveConfig(newConfig)
        AppImageLoader.reinit(context, newConfig)
    }

    fun setMaxVoiceMb(mb: Int) {
        val newConfig = _state.value.config.copy(maxVoiceMb = mb)
        _state.update { it.copy(config = newConfig) }
        manager.saveConfig(newConfig)
    }

    fun setChatCacheDays(days: Int) {
        val newConfig = _state.value.config.copy(chatCacheDays = days)
        _state.update { it.copy(config = newConfig) }
        manager.saveConfig(newConfig)
    }

    fun setUnlimited(enabled: Boolean) {
        val newConfig = _state.value.config.copy(isUnlimited = enabled)
        _state.update { it.copy(config = newConfig) }
        manager.saveConfig(newConfig)
        // If we switched to limited, we might need to evict right now
        if (!enabled) {
            viewModelScope.launch {
                manager.evictIfNeeded()
                refreshSizes()
            }
        }
    }

    fun clearImages() = clearWith(by.iposdev.visorlink.R.string.cache_toast_images_cleared) { manager.clearImages() }
    fun clearVoice()  = clearWith(by.iposdev.visorlink.R.string.cache_toast_voice_cleared)  { manager.clearVoice() }
    fun clearAll()    = clearWith(by.iposdev.visorlink.R.string.cache_toast_all_cleared)   { manager.clearAll() }

    private fun clearWith(messageRes: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isClearing = true) }
            block()
            val sizes = manager.getCacheSizes()
            _state.update { it.copy(isClearing = false, sizes = sizes, successMessageRes = messageRes) }
        }
    }

    fun clearSuccessMessage() = _state.update { it.copy(successMessageRes = null) }
}