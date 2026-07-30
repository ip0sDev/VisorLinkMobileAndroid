package by.iposdev.visorlink.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ColorPreset
import by.iposdev.visorlink.data.model.ThemeMode
import by.iposdev.visorlink.utils.AppLanguage
import by.iposdev.visorlink.utils.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME      = "visorlink_settings"
private const val KEY_THEME       = "app_theme"
private const val KEY_THEME_MODE  = "theme_mode"
private const val KEY_COLOR_PRESET = "color_preset"
private const val KEY_HAPTIC      = "haptic_feedback"
private const val KEY_NOTIF       = "notifications_enabled"
private const val KEY_LANGUAGE    = "app_language"
private const val KEY_DYNAMIC_INPUT = "dynamic_chat_input"
private const val KEY_COMPACT_LIST  = "compact_chat_list"

class ThemeViewModel(private val context: Context) : ViewModel(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Theme ──────────────────────────────────────────────────────────────────

    private val _appTheme = MutableStateFlow(
        AppTheme.valueOf(prefs.getString(KEY_THEME, AppTheme.MATERIAL3_EXPRESSIVE.name)!!)
    )
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorPreset = MutableStateFlow(
        ColorPreset.valueOf(prefs.getString(KEY_COLOR_PRESET, ColorPreset.DEFAULT.name)!!)
    )
    val colorPreset: StateFlow<ColorPreset> = _colorPreset.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC, true))
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIF, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _dynamicChatInput = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_INPUT, true))
    val dynamicChatInput: StateFlow<Boolean> = _dynamicChatInput.asStateFlow()

    // Компактный режим теперь включен по умолчанию
    private val _compactChatList = MutableStateFlow(prefs.getBoolean(KEY_COMPACT_LIST, true))
    val compactChatList: StateFlow<Boolean> = _compactChatList.asStateFlow()

    // ── Language ───────────────────────────────────────────────────────────────

    private val _language = MutableStateFlow(
        AppLanguage.fromCode(
            // Если язык не был сохранен ранее, ставим SYSTEM по умолчанию
            prefs.getString(KEY_LANGUAGE, null) ?: AppLanguage.SYSTEM.code
        )
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    init {
        // Подписываемся на изменения в настройках, чтобы мгновенно обновлять StateFlow
        // во ВСЕХ инстансах ThemeViewModel, на любых экранах.
        prefs.registerOnSharedPreferenceChangeListener(this)

        // Применяем сохраненный язык сразу при создании ViewModel
        LocaleHelper.applyLanguage(_language.value)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        when (key) {
            KEY_THEME -> _appTheme.value = AppTheme.valueOf(sharedPreferences.getString(KEY_THEME, AppTheme.MATERIAL3_EXPRESSIVE.name)!!)
            KEY_THEME_MODE -> _themeMode.value = ThemeMode.valueOf(sharedPreferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
            KEY_COLOR_PRESET -> _colorPreset.value = ColorPreset.valueOf(sharedPreferences.getString(KEY_COLOR_PRESET, ColorPreset.DEFAULT.name)!!)
            KEY_HAPTIC -> _hapticEnabled.value = sharedPreferences.getBoolean(KEY_HAPTIC, true)
            KEY_NOTIF -> _notificationsEnabled.value = sharedPreferences.getBoolean(KEY_NOTIF, true)
            KEY_DYNAMIC_INPUT -> _dynamicChatInput.value = sharedPreferences.getBoolean(KEY_DYNAMIC_INPUT, true)
            KEY_COMPACT_LIST -> _compactChatList.value = sharedPreferences.getBoolean(KEY_COMPACT_LIST, true)
            KEY_LANGUAGE -> {
                val langStr = sharedPreferences.getString(KEY_LANGUAGE, null)
                if (langStr != null) {
                    _language.value = AppLanguage.fromCode(langStr)
                }
            }
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onCleared()
    }

    // ── Setters ────────────────────────────────────────────────────────────────

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setColorPreset(preset: ColorPreset) {
        prefs.edit().putString(KEY_COLOR_PRESET, preset.name).apply()
    }

    fun setHaptic(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()
    }

    fun setNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF, enabled).apply()
    }

    fun setDynamicChatInput(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_INPUT, enabled).apply()
    }

    fun setCompactChatList(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COMPACT_LIST, enabled).apply()
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
        // Даем команду системе сменить язык приложения (Android 13+ сам обновит конфиг)
        LocaleHelper.applyLanguage(language)
    }
}