package by.iposdev.visorlink.ui.theme

import android.content.Context
import androidx.lifecycle.ViewModel
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ThemeMode
import by.iposdev.visorlink.utils.AppLanguage
import by.iposdev.visorlink.utils.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME      = "visorlink_settings"
private const val KEY_THEME       = "app_theme"
private const val KEY_THEME_MODE  = "theme_mode"
private const val KEY_HAPTIC      = "haptic_feedback"
private const val KEY_NOTIF       = "notifications_enabled"
private const val KEY_LANGUAGE    = "app_language"

class ThemeViewModel(private val context: Context) : ViewModel() {

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

    private val _hapticEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC, true))
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIF, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    // ── Language ───────────────────────────────────────────────────────────────

    private val _language = MutableStateFlow(
        AppLanguage.fromCode(
            // Если язык не был сохранен ранее, ставим SYSTEM по умолчанию
            prefs.getString(KEY_LANGUAGE, null) ?: AppLanguage.SYSTEM.code
        )
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    init {
        // Применяем сохраненный язык сразу при создании ViewModel
        LocaleHelper.applyLanguage(_language.value)
    }

    // ── Setters ────────────────────────────────────────────────────────────────

    fun setTheme(theme: AppTheme) {
        _appTheme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setHaptic(enabled: Boolean) {
        _hapticEnabled.value = enabled
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()
    }

    fun setNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_NOTIF, enabled).apply()
    }

    fun setLanguage(language: AppLanguage) {
        // 1. Обновляем StateFlow (для галочек в UI)
        _language.value = language

        // 2. Сохраняем в настройки (чтобы восстановить при следующем запуске)
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()

        // 3. Даем команду системе сменить язык приложения.
        // Это заставит Activity автоматически пересоздаться (на старых Android)
        // или обновить конфигурацию (на Android 13+), и Compose мгновенно перерисует все stringResource().
        LocaleHelper.applyLanguage(language)
    }
}