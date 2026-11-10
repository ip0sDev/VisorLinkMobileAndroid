package org.visorlink.app.ui.appcheck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.visorlink.app.utils.AppCheckManager

class AppCheckViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val functions: FirebaseFunctions by inject()

    val state = AppCheckManager.state
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AppCheckManager.State.Idle
        )

    init {
        val app = getApplication<Application>()
        val isInitialDone = AppCheckManager.isInitialAttestationDone(app)
        val isUpdated = AppCheckManager.isAppUpdated(app)
        val isLoggedIn = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null

        // При обновлении приложения forceRefresh = true; при обычном старте — false (использует кэш/рефреш по истечении)
        if (isInitialDone || isLoggedIn) {
            check(forceRefresh = isUpdated)
        }
    }

    fun check(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            AppCheckManager.validate(app, forceRefresh = forceRefresh)
            if (AppCheckManager.state.value is AppCheckManager.State.Valid) {
                AppCheckManager.verifyOfficialClientIfNeeded(app, functions)
            }
        }
    }

    fun refresh() {
        check(forceRefresh = true)
    }

    fun retry() {
        check(forceRefresh = true)
    }

    fun proceedAnyway() {
        val app = getApplication<Application>()
        AppCheckManager.setInitialAttestationDone(app, true)
        AppCheckManager.markWarningShownForCurrentVersion(app)
    }
}
