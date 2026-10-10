package org.visorlink.app.data.model.flags

import com.google.gson.annotations.SerializedName
import org.visorlink.app.BuildConfig

data class PairRequest(
    @SerializedName("public_key_pem") val publicKey: String,
    @SerializedName("install_id") val installId: String? = null
)

data class PairResponse(
    @SerializedName("device_id") val deviceId: String
)

data class ConfigRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("nonce") val nonce: String
)

data class ConfigResponse(
    @SerializedName("config_jwt") val token: String
)


/**
 * Флаги Aegis: [serverClaims] — claims последнего JWT (кэшируются для офлайна),
 * [localOverrides] — переключатели Flag Flipper на этом устройстве.
 *
 * В релизе локально флаг можно только выключить: если сервер его не включил,
 * переопределение не действует. В Debug-сборке доступны все флаги приложения,
 * и локальные переопределения могут как включать, так и выключать их.
 */
data class AppFlags(
    val serverClaims: Map<String, Any?> = emptyMap(),
    val localOverrides: Map<String, Boolean> = emptyMap(),
    val isDebug: Boolean = BuildConfig.DEBUG
) {
    /** `test_flag` с сервера: открывает Flag Flipper. Переопределение не учитывается в релизе — иначе им можно запереть себя снаружи. */
    val testFlag: Boolean get() = if (isDebug) (overrideOf(TEST_FLAG) ?: serverValue(TEST_FLAG)) else serverValue(TEST_FLAG)

    /** Режим обслуживания: в релизе только сервер, локально не переопределяется. В debug можно переопределить для проверки экрана. */
    val serviceMode: Boolean get() = if (isDebug) isEnabled(SERVICE_MODE) else serverClaims[SERVICE_MODE] == true

    val heuristicDictUrl: String? get() = serverClaims["heuristic_dict_url"] as? String

    /**
     * Минимальный `versionCode`, с которым можно работать (`force_update_min_version`, число).
     * Сборки старше обязаны обновиться: Play — немедленным обновлением из Google Play,
     * standalone — полноэкранным обновлением Ipos Store. Не переопределяется локально.
     */
    val forceUpdateMinVersion: Int?
        get() = when (val v = serverClaims[FORCE_UPDATE_MIN_VERSION]) {
            is Number -> v.toInt()
            is String -> v.trim().toIntOrNull()
            else -> null
        }

    /** Текущая сборка ниже минимальной — обновление обязательно. */
    fun forceUpdateRequired(versionCode: Int = BuildConfig.VERSION_CODE): Boolean =
        (forceUpdateMinVersion ?: 0) > versionCode

    /** Профиль — вкладка нижней навигации с ID-картой вместо кнопки-аватара в шапке списка чатов. */
    val profileNavbar: Boolean get() = isEnabled(PROFILE_NAVBAR)

    /** Значение с сервера без учёта переопределений. */
    fun serverValue(key: String): Boolean =
        if (canonical(key) == AEGIS_DEBUG) AEGIS_DEBUG_ALIASES.any { serverClaims[it] == true }
        else serverClaims[key] == true

    /** Локальное переопределение ключа или `null`, если флаг идёт как на сервере. */
    fun overrideOf(key: String): Boolean? =
        if (canonical(key) == AEGIS_DEBUG) AEGIS_DEBUG_ALIASES.firstNotNullOfOrNull { localOverrides[it] }
        else localOverrides[key]

    fun isEnabled(key: String, debug: Boolean = isDebug): Boolean {
        val override = overrideOf(key)
        if (debug) {
            if (override != null) return override
            return serverValue(key)
        }
        if (!serverValue(key)) return false
        if (key in NOT_OVERRIDABLE) return true
        return override ?: true
    }

    /** Ключи для Flag Flipper: в debug — все флаги приложения + серверные, в релизе — только то, что сервер включил (кроме непереключаемых). */
    val flippableKeys: List<String>
        get() = flippableKeys(isDebug)

    fun flippableKeys(debug: Boolean = isDebug): List<String> {
        return if (debug) {
            (KNOWN_FLAGS + serverClaims.filterValues { it is Boolean }.keys)
                .map(::canonical)
                .distinct()
                .sorted()
        } else {
            serverClaims.filterValues { it == true }.keys
                .map(::canonical)
                .filter { it !in NOT_OVERRIDABLE }
                .distinct()
                .sorted()
        }
    }

    companion object {
        const val TEST_FLAG = "test_flag"
        const val FORCE_UPDATE_MIN_VERSION = "force_update_min_version"
        const val SERVICE_MODE = "service_mode_enabled"
        const val PROFILE_NAVBAR = "enable_profile_navbar"
        const val ALTERNATIVE_OUTBOX = "enable_alternative_outbox"

        /** У флага отладки Aegis два имени — в Flipper показывается одно. */
        const val AEGIS_DEBUG = "aegis_debug_mode_enabled"
        private val AEGIS_DEBUG_ALIASES = listOf(AEGIS_DEBUG, "is_aegis_debug_mode")

        private val NOT_OVERRIDABLE = setOf(SERVICE_MODE)

        /** Все известные приложению флаги, отображаемые во Flipper в Debug-режиме независимо от ответа сервера. */
        val KNOWN_FLAGS = listOf(
            TEST_FLAG,
            SERVICE_MODE,
            PROFILE_NAVBAR,
            AEGIS_DEBUG,
            ALTERNATIVE_OUTBOX
        )

        /** Удалённые флаги: их переопределения вычищаются из настроек при загрузке. */
        val REMOVED_KEYS = setOf("animation_test", "test_backend_enabled", "id_cards_enabled")

        fun canonical(key: String): String = if (key in AEGIS_DEBUG_ALIASES) AEGIS_DEBUG else key
    }
}
