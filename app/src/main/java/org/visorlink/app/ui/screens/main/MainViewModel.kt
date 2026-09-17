package org.visorlink.app.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.utils.NetworkMonitor
import org.visorlink.app.data.repository.BackendFallbackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    private val userRepository: UserRepository,
    private val networkMonitor: NetworkMonitor,
    private val fallbackManager: BackendFallbackManager? = null
) : ViewModel() {
    val userProfile = userRepository.currentUserFlow()
        .filterNotNull()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isOnline = networkMonitor.isOnline

    val showFallbackPrompt: StateFlow<Boolean> = fallbackManager?.showFallbackPrompt
        ?: MutableStateFlow(false).asStateFlow()

    fun confirmFallback() {
        fallbackManager?.enableManualFallback()
    }

    fun dismissFallbackPrompt() {
        fallbackManager?.dismissPrompt()
    }
}
