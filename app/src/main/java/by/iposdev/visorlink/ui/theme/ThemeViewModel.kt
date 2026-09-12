package by.iposdev.visorlink.ui.theme

import androidx.lifecycle.ViewModel
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ColorPreset
import by.iposdev.visorlink.data.model.ThemeMode
import by.iposdev.visorlink.data.repository.SettingsRepository
import by.iposdev.visorlink.utils.AppLanguage
import kotlinx.coroutines.flow.StateFlow

class ThemeViewModel(private val repository: SettingsRepository) : ViewModel() {
    val appTheme: StateFlow<AppTheme> = repository.appTheme
    val themeMode: StateFlow<ThemeMode> = repository.themeMode
    val colorPreset: StateFlow<ColorPreset> = repository.colorPreset
    val hapticEnabled: StateFlow<Boolean> = repository.hapticEnabled
    val notificationsEnabled: StateFlow<Boolean> = repository.notificationsEnabled
    val dynamicChatInput: StateFlow<Boolean> = repository.dynamicChatInput
    val compactChatList: StateFlow<Boolean> = repository.compactChatList
    val discoverEnabled: StateFlow<Boolean> = repository.discoverEnabled
    val musicEnabled: StateFlow<Boolean> = repository.musicEnabled
    val showMusicOnboarding: StateFlow<Boolean> = repository.showMusicOnboarding
    val showOnboarding: StateFlow<Boolean> = repository.showOnboarding
    val language: StateFlow<AppLanguage> = repository.language
    val showDebugIds: StateFlow<Boolean> = repository.showDebugIds

    fun setTheme(theme: AppTheme) = repository.setTheme(theme)
    fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)
    fun setColorPreset(preset: ColorPreset) = repository.setColorPreset(preset)
    fun setHaptic(enabled: Boolean) = repository.setHaptic(enabled)
    fun setNotifications(enabled: Boolean) = repository.setNotifications(enabled)
    fun setDynamicChatInput(enabled: Boolean) = repository.setDynamicChatInput(enabled)
    fun setCompactChatList(enabled: Boolean) = repository.setCompactChatList(enabled)
    fun setDiscoverEnabled(enabled: Boolean) = repository.setDiscoverEnabled(enabled)
    fun setMusicEnabled(enabled: Boolean) = repository.setMusicEnabled(enabled)
    fun setShowDebugIds(enabled: Boolean) = repository.setShowDebugIds(enabled)
    fun completeMusicOnboarding(enableMusic: Boolean) = repository.completeMusicOnboarding(enableMusic)
    fun completeOnboarding() = repository.completeOnboarding()
    fun setLanguage(language: AppLanguage) = repository.setLanguage(language)
}