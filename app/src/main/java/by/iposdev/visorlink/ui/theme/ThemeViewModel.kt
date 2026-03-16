package by.iposdev.visorlink.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ThemeMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("theme_prefs")
private val THEME_KEY = stringPreferencesKey("app_theme")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
private val HAPTIC_KEY = booleanPreferencesKey("haptic_feedback")
private val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")

class ThemeViewModel(private val context: Context) : ViewModel() {

    val appTheme: StateFlow<AppTheme> = context.dataStore.data
        .map { prefs ->
            when (prefs[THEME_KEY]) {
                AppTheme.ONE_UI.name -> AppTheme.ONE_UI
                else -> AppTheme.MATERIAL3_EXPRESSIVE
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.MATERIAL3_EXPRESSIVE)

    val themeMode: StateFlow<ThemeMode> = context.dataStore.data
        .map { prefs ->
            when (prefs[THEME_MODE_KEY]) {
                ThemeMode.LIGHT.name -> ThemeMode.LIGHT
                ThemeMode.DARK.name -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val hapticEnabled: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[HAPTIC_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val notificationsEnabled: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[NOTIFICATIONS_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setTheme(theme: AppTheme) = viewModelScope.launch {
        context.dataStore.edit { it[THEME_KEY] = theme.name }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }

    fun setHaptic(enabled: Boolean) = viewModelScope.launch {
        context.dataStore.edit { it[HAPTIC_KEY] = enabled }
    }

    fun setNotifications(enabled: Boolean) = viewModelScope.launch {
        context.dataStore.edit { it[NOTIFICATIONS_KEY] = enabled }
    }
}