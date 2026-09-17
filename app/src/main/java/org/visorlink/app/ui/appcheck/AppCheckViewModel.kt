package org.visorlink.app.ui.appcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.utils.AppCheckManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppCheckViewModel : ViewModel() {

    val state = AppCheckManager.state
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AppCheckManager.State.Idle
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            AppCheckManager.validate(forceRefresh = true)
        }
    }
}