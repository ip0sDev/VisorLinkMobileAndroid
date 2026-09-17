package org.visorlink.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ColorPreset
import org.visorlink.app.data.model.ThemeMode
import org.visorlink.app.utils.AppLanguage
import org.visorlink.app.utils.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val PREFS_NAME = "visorlink_settings"
    private val KEY_THEME = "app_theme"
    private val KEY_THEME_MODE = "theme_mode"
    private val KEY_COLOR_PRESET = "color_preset"
    private val KEY_HAPTIC = "haptic_feedback"
    private val KEY_NOTIF = "notifications_enabled"
    private val KEY_LANGUAGE = "app_language"
    private val KEY_DYNAMIC_INPUT = "dynamic_chat_input"
    private val KEY_COMPACT_LIST = "compact_chat_list"
    private val KEY_DISCOVER_ENABLED = "discover_enabled"
    private val KEY_MUSIC_ENABLED = "music_enabled"
    private val KEY_MUSIC_ONBOARDING_SHOWN = "music_onboarding_shown"
    private val KEY_ONBOARDING_VER = "onboarding_version"
    private val KEY_DEBUG_SHOW_IDS = "debug_show_ids"
    private val CURRENT_ONBOARDING_VERSION = 1

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _appTheme = MutableStateFlow(
        try { AppTheme.valueOf(prefs.getString(KEY_THEME, AppTheme.MATERIAL3_EXPRESSIVE.name)!!) }
        catch (e: Exception) { AppTheme.MATERIAL3_EXPRESSIVE }
    )
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorPreset = MutableStateFlow(ColorPreset.valueOf(prefs.getString(KEY_COLOR_PRESET, ColorPreset.DEFAULT.name)!!))
    val colorPreset: StateFlow<ColorPreset> = _colorPreset.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC, true))
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIF, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _dynamicChatInput = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_INPUT, true))
    val dynamicChatInput: StateFlow<Boolean> = _dynamicChatInput.asStateFlow()

    private val _compactChatList = MutableStateFlow(prefs.getBoolean(KEY_COMPACT_LIST, true))
    val compactChatList: StateFlow<Boolean> = _compactChatList.asStateFlow()

    private val _discoverEnabled = MutableStateFlow(prefs.getBoolean(KEY_DISCOVER_ENABLED, true))
    val discoverEnabled: StateFlow<Boolean> = _discoverEnabled.asStateFlow()

    private val _musicEnabled = MutableStateFlow(prefs.getBoolean(KEY_MUSIC_ENABLED, true))
    val musicEnabled: StateFlow<Boolean> = _musicEnabled.asStateFlow()

    private val _showMusicOnboarding = MutableStateFlow(!prefs.getBoolean(KEY_MUSIC_ONBOARDING_SHOWN, false))
    val showMusicOnboarding: StateFlow<Boolean> = _showMusicOnboarding.asStateFlow()

    private val _showOnboarding = MutableStateFlow(prefs.getInt(KEY_ONBOARDING_VER, 0) < CURRENT_ONBOARDING_VERSION)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, null) ?: AppLanguage.SYSTEM.code))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _showDebugIds = MutableStateFlow(prefs.getBoolean(KEY_DEBUG_SHOW_IDS, false))
    val showDebugIds: StateFlow<Boolean> = _showDebugIds.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        LocaleHelper.applyLanguage(_language.value)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        when (key) {
            KEY_THEME -> {
                val themeStr = sharedPreferences.getString(KEY_THEME, AppTheme.MATERIAL3_EXPRESSIVE.name)
                _appTheme.value = try { AppTheme.valueOf(themeStr!!) } catch (e: Exception) { AppTheme.MATERIAL3_EXPRESSIVE }
            }
            KEY_THEME_MODE -> _themeMode.value = ThemeMode.valueOf(sharedPreferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
            KEY_COLOR_PRESET -> _colorPreset.value = ColorPreset.valueOf(sharedPreferences.getString(KEY_COLOR_PRESET, ColorPreset.DEFAULT.name)!!)
            KEY_HAPTIC -> _hapticEnabled.value = sharedPreferences.getBoolean(KEY_HAPTIC, true)
            KEY_NOTIF -> _notificationsEnabled.value = sharedPreferences.getBoolean(KEY_NOTIF, true)
            KEY_DYNAMIC_INPUT -> _dynamicChatInput.value = sharedPreferences.getBoolean(KEY_DYNAMIC_INPUT, true)
            KEY_COMPACT_LIST -> _compactChatList.value = sharedPreferences.getBoolean(KEY_COMPACT_LIST, true)
            KEY_DISCOVER_ENABLED -> _discoverEnabled.value = sharedPreferences.getBoolean(KEY_DISCOVER_ENABLED, true)
            KEY_MUSIC_ENABLED -> _musicEnabled.value = sharedPreferences.getBoolean(KEY_MUSIC_ENABLED, true)
            KEY_MUSIC_ONBOARDING_SHOWN -> _showMusicOnboarding.value = !sharedPreferences.getBoolean(KEY_MUSIC_ONBOARDING_SHOWN, false)
            KEY_ONBOARDING_VER -> _showOnboarding.value = sharedPreferences.getInt(KEY_ONBOARDING_VER, 0) < CURRENT_ONBOARDING_VERSION
            KEY_DEBUG_SHOW_IDS -> _showDebugIds.value = sharedPreferences.getBoolean(KEY_DEBUG_SHOW_IDS, false)
            KEY_LANGUAGE -> {
                val langStr = sharedPreferences.getString(KEY_LANGUAGE, null)
                if (langStr != null) _language.value = AppLanguage.fromCode(langStr)
            }
        }
    }

    fun setTheme(theme: AppTheme) = prefs.edit().putString(KEY_THEME, theme.name).apply()
    fun setThemeMode(mode: ThemeMode) = prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    fun setColorPreset(preset: ColorPreset) = prefs.edit().putString(KEY_COLOR_PRESET, preset.name).apply()
    fun setHaptic(enabled: Boolean) = prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()
    fun setNotifications(enabled: Boolean) = prefs.edit().putBoolean(KEY_NOTIF, enabled).apply()
    fun setDynamicChatInput(enabled: Boolean) = prefs.edit().putBoolean(KEY_DYNAMIC_INPUT, enabled).apply()
    fun setCompactChatList(enabled: Boolean) = prefs.edit().putBoolean(KEY_COMPACT_LIST, enabled).apply()
    fun setDiscoverEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_DISCOVER_ENABLED, enabled).apply()
    fun setMusicEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_MUSIC_ENABLED, enabled).apply()
    fun setShowDebugIds(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_SHOW_IDS, enabled).apply()
        _showDebugIds.value = enabled
    }
    fun completeMusicOnboarding(enableMusic: Boolean) {
        prefs.edit()
            .putBoolean(KEY_MUSIC_ONBOARDING_SHOWN, true)
            .putBoolean(KEY_MUSIC_ENABLED, enableMusic)
            .apply()
        _showMusicOnboarding.value = false
        _musicEnabled.value = enableMusic
    }
    fun completeOnboarding() = prefs.edit().putInt(KEY_ONBOARDING_VER, CURRENT_ONBOARDING_VERSION).apply()
    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
        LocaleHelper.applyLanguage(language)
    }

    fun release() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }
}
