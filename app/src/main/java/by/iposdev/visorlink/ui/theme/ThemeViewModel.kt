package by.iposdev.visorlink.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.AppTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("theme_prefs")
private val THEME_KEY = stringPreferencesKey("app_theme")

class ThemeViewModel(private val context: Context) : ViewModel() {

    val appTheme: StateFlow<AppTheme> = context.dataStore.data
        .map { prefs ->
            when (prefs[THEME_KEY]) {
                AppTheme.ONE_UI.name -> AppTheme.ONE_UI
                else -> AppTheme.MATERIAL3_EXPRESSIVE
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.MATERIAL3_EXPRESSIVE)

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            context.dataStore.edit { it[THEME_KEY] = theme.name }
        }
    }
}