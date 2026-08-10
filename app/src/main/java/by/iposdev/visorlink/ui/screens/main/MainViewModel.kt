package by.iposdev.visorlink.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.utils.NetworkMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    private val userRepository: UserRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    val userProfile = userRepository.currentUserFlow()
        .filterNotNull()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isOnline = networkMonitor.isOnline
}
