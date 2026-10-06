package org.visorlink.app.utils

import org.visorlink.app.data.repository.SessionInfo

/** Подпись устройства в списке сессий: «браузер · ОС» или «VisorLink 4.3 · модель · Android 14». */
data class SessionDescription(val browser: String?, val os: String?, val isMobile: Boolean) {
    /** `null` — ничего не распознано: экран пишет «Неизвестное устройство». */
    val label: String? get() = listOfNotNull(browser, os).joinToString(" · ").ifEmpty { null }
}

/** Порт `describeSession` / `describeUserAgent` из src/utils/sessions.js. */
object SessionDescriber {

    private val BROWSERS = listOf(
        Regex("""Edg(?:e|A|iOS)?/""") to "Edge",
        Regex("""OPR/|Opera""") to "Opera",
        Regex("""YaBrowser/""") to "Яндекс Браузер",
        Regex("""SamsungBrowser/""") to "Samsung Internet",
        Regex("""Firefox/|FxiOS/""") to "Firefox",
        Regex("""Chrome/|CriOS/""") to "Chrome",
        Regex("""Safari/""") to "Safari",
    )

    private val SYSTEMS = listOf(
        Regex("iPhone") to "iPhone",
        Regex("iPad") to "iPad",
        Regex("Android") to "Android",
        Regex("Mac OS X|Macintosh") to "macOS",
        Regex("Windows") to "Windows",
        Regex("CrOS") to "ChromeOS",
        Regex("Linux") to "Linux",
    )

    /** User-Agent HTTP-библиотеки Android-приложения: так записывались входы до App Check. */
    private val ANDROID_APP_UA = Regex("""^(okhttp|Dalvik)/""", RegexOption.IGNORE_CASE)

    fun isAndroidApp(session: SessionInfo): Boolean =
        session.client == "android" || ANDROID_APP_UA.containsMatchIn(session.userAgent)

    /**
     * Сессия официального Android-приложения (`client: android` — сервер ставит по App Check;
     * у старых — User-Agent okhttp/Dalvik): «VisorLink {версия} · {модель} · Android {версия ОС}»,
     * отсутствующие части опускаются. Остальное — по User-Agent.
     */
    fun describe(session: SessionInfo): SessionDescription {
        if (!isAndroidApp(session)) return describeUserAgent(session.userAgent)
        val os = session.osVersion?.takeIf { it.isNotBlank() }?.let { "Android $it" } ?: "Android"
        val app = session.appVersion?.takeIf { it.isNotBlank() }?.let { "VisorLink $it" } ?: "VisorLink"
        val model = session.deviceModel?.takeIf { it.isNotBlank() }
        return SessionDescription(browser = app, os = if (model != null) "$model · $os" else os, isMobile = true)
    }

    fun describeUserAgent(ua: String?): SessionDescription {
        val s = ua.orEmpty()
        val browser = BROWSERS.firstOrNull { it.first.containsMatchIn(s) }?.second
        val os = SYSTEMS.firstOrNull { it.first.containsMatchIn(s) }?.second
        val isMobile = Regex("""Mobi|iPhone|Android(?!.*Tablet)""").containsMatchIn(s) && !s.contains("iPad")
        return SessionDescription(browser, os, isMobile)
    }
}
