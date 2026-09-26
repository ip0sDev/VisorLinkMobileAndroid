package org.visorlink.app.data.remote.yandex

import android.content.Context
import org.visorlink.app.data.repository.FlagsRepository

/**
 * Менеджер конфигурации аварийного транспорта сообщений через Яндекс.Диск.
 * Доступен только при активном фича-флаге `enable_alternative_outbox`.
 */
class YandexRelayConfigManager(
    private val context: Context,
    private val flagsRepository: FlagsRepository
) {
    companion object {
        private const val PREFS_NAME = "visorlink_yandex_relay"
        private const val KEY_CUSTOM_TOKEN = "custom_token"
        private const val KEY_CUSTOM_CLIENT_ID = "custom_client_id"
        private const val KEY_CUSTOM_BASE_PATH = "custom_base_path"
        private const val KEY_USER_ENABLED = "is_enabled_by_user"
        private const val DEFAULT_BASE_PATH = "app:/Relay"

        /**
         * Публичный Client ID приложения VisorLink в Яндекс OAuth.
         * Зарегистрирован для Implicit Flow (response_type=token) с Callback URL `visorlink://yandex-auth`.
         * Может быть переопределен через Remote Config ("yandex_client_id") или пользователем в настройках.
         */
        const val DEFAULT_CLIENT_ID = "745e0bd40a884511bd39cc5dff798b67"
        const val REDIRECT_URI = "visorlink://yandex-auth"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Проверяет, активирован ли фича-флаг на уровне системы/клиента.
     */
    fun isFeatureAvailable(): Boolean {
        return flagsRepository.flags.value.isEnabled("enable_alternative_outbox")
    }

    /**
     * Проверяет, включена ли опция пользователем (при доступности флага).
     */
    fun isRelayEnabled(): Boolean {
        if (!isFeatureAvailable()) return false
        return prefs.getBoolean(KEY_USER_ENABLED, true)
    }

    fun setRelayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USER_ENABLED, enabled).apply()
    }

    /**
     * Возвращает активный OAuth-токен Яндекс.Диска.
     * Приоритет:
     * 1. Персональный токен пользователя (BYOS) из настроек.
     * 2. Сервисный токен из серверных клеймов флагов ("yandex_relay_token").
     */
    fun getActiveToken(): String? {
        val userToken = prefs.getString(KEY_CUSTOM_TOKEN, null)
        if (!userToken.isNullOrBlank()) {
            return userToken
        }
        val serverToken = flagsRepository.flags.value.serverClaims["yandex_relay_token"] as? String
        if (!serverToken.isNullOrBlank()) {
            return serverToken
        }
        return null
    }

    fun setCustomToken(token: String?) {
        prefs.edit().putString(KEY_CUSTOM_TOKEN, token?.trim()).apply()
    }

    fun getCustomToken(): String? {
        return prefs.getString(KEY_CUSTOM_TOKEN, null)
    }

    fun getClientId(): String {
        val customId = prefs.getString(KEY_CUSTOM_CLIENT_ID, null)
        if (!customId.isNullOrBlank()) return customId
        val serverClaimId = flagsRepository.flags.value.serverClaims["yandex_client_id"] as? String
        if (!serverClaimId.isNullOrBlank()) return serverClaimId
        return DEFAULT_CLIENT_ID
    }

    fun setCustomClientId(clientId: String?) {
        prefs.edit().putString(KEY_CUSTOM_CLIENT_ID, clientId?.trim()).apply()
    }

    fun getCustomClientId(): String? {
        return prefs.getString(KEY_CUSTOM_CLIENT_ID, null)
    }

    /**
     * Формирует URL для авторизации через OAuth 2.0 (Implicit Grant).
     * Браузер вернет токен в URL fragment: `visorlink://yandex-auth#access_token=...`
     */
    fun buildOAuthUrl(): String {
        val clientId = getClientId()
        return "https://oauth.yandex.ru/authorize?response_type=token&client_id=$clientId&redirect_uri=$REDIRECT_URI"
    }

    fun disconnect() {
        setCustomToken(null)
    }

    fun getBasePath(): String {
        val path = prefs.getString(KEY_CUSTOM_BASE_PATH, DEFAULT_BASE_PATH) ?: DEFAULT_BASE_PATH
        if (path.startsWith("app:/") || path.startsWith("disk:/")) {
            return path
        }
        val clean = path.trimStart('/')
        return if (clean.isBlank()) "app:/Relay" else "app:/$clean"
    }

    fun setBasePath(path: String) {
        prefs.edit().putString(KEY_CUSTOM_BASE_PATH, path.trim()).apply()
    }
}

