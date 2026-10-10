package org.visorlink.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.visorlink.app.data.model.UserProfile
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Цвет профиля VisorLink PRO как акцент **всего** интерфейса (веб `src/utils/profileAccent.js`:
 * `resolveProfileAccent`, `readableAccent`, `uiAccentVars`; `ProfileAccentSync.jsx`).
 *
 * Цвет — `customization.accentHex`, иначе старый пресет `customization.accent`. Основной тон
 * подстраивается под тему: на тёмной светлеет, на светлой темнеет шагами по 2 %, пока контраст
 * с **каждым** фоном темы ([DARK_SURFACES] / [LIGHT_SURFACES], замерены в вебе) не станет ≥ 4.5.
 * Расчёт — копия веба (тот же WCAG, те же шаги и округление), чтобы цвет совпадал до бита.
 */
object ProAccent {

    /** Старые пресеты: тот же оттенок, что давал data-accent в вебе (не `ColorPreset.seedColor`). */
    val LEGACY_HEX: Map<String, String> = mapOf(
        "purple" to "#8C6BFF",
        "blue" to "#35C7E8",
        "emerald" to "#2DD4BF",
        "crimson" to "#FF4D6A",
    )

    private val HEX = Regex("^#[0-9a-fA-F]{6}$")

    /** Фоны панелей во всех стилях оформления веба — акцент должен читаться на каждом. */
    internal val DARK_SURFACES = listOf("#0E1214", "#141A1D", "#1A2126", "#1A0D11", "#2C191F", "#0F0F15", "#1D1E27", "#252632").map(::rgb)
    internal val LIGHT_SURFACES = listOf("#F2F0E9", "#F8F6F0", "#ECEAE0", "#DBD0D5", "#EDDCDF", "#EBEBF4", "#F4F4FD", "#E9EAF7").map(::rgb)
    private const val MIN_CONTRAST = 4.5
    private val INK = Color(0xFF0B0F12)

    /** Цвет профиля из customization: точный `accentHex` или старый пресет; `null` — своего цвета нет. */
    fun hexOf(customization: Map<String, Any?>?): String? {
        if (customization == null) return null
        (customization["accentHex"] as? String)?.trim()?.takeIf { HEX.matches(it) }?.let { return it.uppercase() }
        return LEGACY_HEX[(customization["accent"] as? String)?.trim()]
    }

    /** Акцент интерфейса из профиля: только при активном PRO и выбранном цвете. */
    fun of(profile: UserProfile?): Color? =
        if (profile?.isProActive() == true) hexOf(profile.customization)?.let(::parse) else null

    /** Основной тон, подстроенный под тему ([light] — светлая): ≥ 4.5 к каждому фону. */
    fun readable(accent: Color, light: Boolean): Color {
        val base = doubleArrayOf(accent.red * 255.0, accent.green * 255.0, accent.blue * 255.0).map { it.roundToInt().toDouble() }
        val surfaces = if (light) LIGHT_SURFACES else DARK_SURFACES
        val target = if (light) 0.0 else 255.0
        // Шаги как в JS: t += 0.02 с накоплением ошибки — до 1.0001
        var t = 0.0
        while (t <= 1.0001) {
            val c = base.map { v -> v + (target - v) * t }
            if (surfaces.minOf { contrast(c, it) } >= MIN_CONTRAST) return toColor(c)
            t += 0.02
        }
        return if (light) Color.Black else Color.White
    }

    /** Текст поверх акцента — белый или `#0B0F12`, что контрастнее. */
    fun onColor(primary: Color): Color {
        val p = listOf(primary.red * 255.0, primary.green * 255.0, primary.blue * 255.0)
        return if (contrast(p, listOf(255.0, 255.0, 255.0)) >= contrast(p, listOf(11.0, 15.0, 18.0))) Color.White else INK
    }

    // ── WCAG, как в profileAccent.js ─────────────────────────────────────────

    private fun rgb(hex: String): List<Double> = listOf(1, 3, 5).map { hex.substring(it, it + 2).toInt(16).toDouble() }

    private fun parse(hex: String): Color = rgb(hex).let { Color(it[0].toInt(), it[1].toInt(), it[2].toInt()) }

    private fun toColor(c: List<Double>): Color {
        val v = c.map { it.coerceIn(0.0, 255.0).roundToInt() }
        return Color(v[0], v[1], v[2])
    }

    private fun luminance(c: List<Double>): Double {
        fun f(x: Double): Double { val v = x / 255; return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2])
    }

    internal fun contrast(a: List<Double>, b: List<Double>): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }
}
