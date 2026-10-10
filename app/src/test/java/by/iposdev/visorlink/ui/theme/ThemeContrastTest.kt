package org.visorlink.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ColorPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Тесты палитр и идентификаторов тем.
 *
 * Контраст считается своей реализацией WCAG, а не `Color.luminance()`, специально:
 * формула здесь — часть проверяемого утверждения, и подменять её реализацией из
 * той же библиотеки, на которой построен продакшн-код, смысла нет.
 */
class ThemeContrastTest {

    // ── WCAG 2.1 ─────────────────────────────────────────────────────────────

    private fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        val r = (color.red * 255f + 0.5f).toInt()
        val g = (color.green * 255f + 0.5f).toInt()
        val b = (color.blue * 255f + 0.5f).toInt()
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun contrast(fg: Color, bg: Color): Double {
        val l1 = luminance(fg)
        val l2 = luminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    /**
     * Полупрозрачные роли (`primaryContainer` и т.п.) сначала накладываем на фон —
     * иначе контраст считался бы против несуществующего цвета.
     */
    private fun composite(fg: Color, bg: Color): Color {
        val a = fg.alpha
        return Color(
            red = fg.red * a + bg.red * (1 - a),
            green = fg.green * a + bg.green * (1 - a),
            blue = fg.blue * a + bg.blue * (1 - a),
            alpha = 1f,
        )
    }

    // ── §8: контраст текста ≥ 4.5:1 в обеих темах ────────────────────────────

    private fun assertReadable(name: String, fg: Color, bg: Color, min: Double = 4.5) {
        val ratio = contrast(composite(fg, bg), bg)
        assertTrue(
            "$name: контраст ${"%.2f".format(ratio)}:1 ниже требуемых $min:1 (§8)",
            ratio >= min,
        )
    }

    private fun assertSchemeReadable(label: String, cs: ColorScheme) {
        assertReadable("$label onSurface/surface", cs.onSurface, cs.surface)
        assertReadable("$label onBackground/background", cs.onBackground, cs.background)
        assertReadable("$label onPrimary/primary", cs.onPrimary, cs.primary)
        assertReadable("$label onSecondary/secondary", cs.onSecondary, cs.secondary)
        assertReadable("$label onTertiary/tertiary", cs.onTertiary, cs.tertiary)
        assertReadable("$label onError/error", cs.onError, cs.error)
        assertReadable("$label onSurface/surfaceContainer", cs.onSurface, cs.surfaceContainer)
        assertReadable("$label onSurface/surfaceContainerHigh", cs.onSurface, cs.surfaceContainerHigh)

        // Полупрозрачные *Container сначала накладываются на surface: контраст
        // считается против того, что реально видит глаз.
        assertReadable("$label onPrimaryContainer/primaryContainer", cs.onPrimaryContainer, composite(cs.primaryContainer, cs.surface))
        assertReadable("$label onSecondaryContainer/secondaryContainer", cs.onSecondaryContainer, composite(cs.secondaryContainer, cs.surface))
        assertReadable("$label onTertiaryContainer/tertiaryContainer", cs.onTertiaryContainer, composite(cs.tertiaryContainer, cs.surface))
        assertReadable("$label onErrorContainer/errorContainer", cs.onErrorContainer, composite(cs.errorContainer, cs.surface))
    }

    @Test
    fun `abyss palette meets text contrast requirements`() {
        assertSchemeReadable("Abyss", AbyssColorScheme)
    }

    @Test
    fun `tidepool palette meets text contrast requirements`() {
        assertSchemeReadable("Tidepool", TidepoolColorScheme)
    }

    @Test
    fun `forge v2 palettes meet text contrast requirements`() {
        assertSchemeReadable("Forge v2 Protogen", forgeV2ColorScheme(ForgeV2.Protogen))
        assertSchemeReadable("Forge v2 Beast", forgeV2ColorScheme(ForgeV2.Beast))
    }

    /**
     * Цвет профиля PRO заменяет оттенок Forge v2 ([tintedBy]) — любой HEX, от белого до чёрного.
     * Неон остаётся цветом подписей, фоны не меняются: тема узнаётся.
     */
    @Test
    fun `forge v2 tinted by any pro accent stays readable and recognizable`() {
        val accents = listOf(Color(0xFFFFE45C), Color(0xFFE11D48), Color(0xFF0EA5E9), Color(0xFF1A1A1A), Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF10B981))
        listOf("Protogen" to ForgeV2.Protogen, "Beast" to ForgeV2.Beast).forEach { (name, base) ->
            accents.forEach { accent ->
                val f = base.tintedBy(accent)
                val cs = forgeV2ColorScheme(f)
                val p = "Forge v2 $name/$accent"
                assertEquals("$p: оттенок режима сохраняется", base.beast, f.beast)
                assertReadable("$p primary/background", cs.primary, cs.background)
                assertReadable("$p primary/surfaceContainer", cs.primary, cs.surfaceContainer)
                assertReadable("$p onPrimary/primary", cs.onPrimary, cs.primary)
                assertReadable("$p onSurface/surfaceContainerHigh", cs.onSurface, cs.surfaceContainerHigh)
                assertReadable("$p onSurfaceVariant/surface", cs.onSurfaceVariant, cs.surface)
                val fill = ForgeV2.mix(cs.primary, 0.16f, cs.surfaceContainer)
                assertReadable("$p primary/selectionFill", cs.primary, fill, min = 3.0)
                // «Лёгкая» подкраска: фон темнее/светлее исходного не больше чем на пару процентов
                val shift = kotlin.math.abs(luminance(f.background) - luminance(base.background))
                assertTrue("$p: фон сдвинулся слишком сильно (Δ=$shift)", shift < 0.02)
            }
        }
    }

    /**
     * Цвет профиля PRO как акцент Biolume ([withProAccent], веб `uiAccentVars`): любой HEX
     * читается как подпись на каждом фоне темы, текст на кнопке — тоже.
     */
    @Test
    fun `biolume with any pro accent stays readable`() {
        val accents = listOf(Color(0xFFFFE45C), Color(0xFFE11D48), Color(0xFF0EA5E9), Color(0xFF1A1A1A), Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF94A3B8))
        listOf(true to AbyssColorScheme, false to TidepoolColorScheme).forEach { (dark, base) ->
            accents.forEach { accent ->
                val cs = base.withProAccent(accent, dark)
                val p = "Biolume ${if (dark) "Abyss" else "Tidepool"}/$accent"
                assertReadable("$p primary/background", cs.primary, cs.background)
                assertReadable("$p primary/surface", cs.primary, cs.surface)
                assertReadable("$p primary/surfaceContainer", cs.primary, cs.surfaceContainer)
                assertReadable("$p onPrimary/primary", cs.onPrimary, cs.primary)
                assertEquals("$p: поверхности темы не меняются", base.background, cs.background)
            }
        }
    }

    @Test
    fun `forge v2 selection fill and secondary text stay readable`() {
        listOf("Protogen" to ForgeV2.Protogen, "Beast" to ForgeV2.Beast).forEach { (name, f) ->
            val cs = forgeV2ColorScheme(f)
            val fill = ForgeV2.mix(cs.primary, 0.16f, cs.surfaceContainer)
            assertReadable("Forge v2 $name primary/selectionFill", cs.primary, fill, min = 3.0)
            assertReadable("Forge v2 $name onSurfaceVariant/surface", cs.onSurfaceVariant, cs.surface)
            assertReadable("Forge v2 $name onSurfaceVariant/surfaceContainerHigh", cs.onSurfaceVariant, cs.surfaceContainerHigh)
        }
    }

    /**
     * onSurfaceVariant в Biolume несёт подписи, таймштампы и метаданные — то есть
     * мелкий текст, поэтому требуем полные 4.5:1, а не послабление для крупного.
     */
    @Test
    fun `secondary text stays legible in both palettes`() {
        assertReadable(
            "Abyss onSurfaceVariant/surface",
            AbyssColorScheme.onSurfaceVariant, AbyssColorScheme.surface, min = 4.5,
        )
        assertReadable(
            "Tidepool onSurfaceVariant/surface",
            TidepoolColorScheme.onSurfaceVariant, TidepoolColorScheme.surface, min = 4.5,
        )
    }

    /** §10: рельеф не читается на чистом чёрном/белом фоне. */
    @Test
    fun `biolume backgrounds are not pure black or white`() {
        assertTrue(
            "Abyss background не должен быть чистым чёрным (§10)",
            AbyssColorScheme.background != Color(0xFF000000),
        )
        assertTrue(
            "Tidepool background не должен быть чистым белым (§10)",
            TidepoolColorScheme.background != Color(0xFFFFFFFF),
        )
    }

    /** §3.1: primary сдвинут в синюю зону — зелёного компонента быть больше не должно. */
    @Test
    fun `abyss primary is blue-shifted not acid teal`() {
        val p = AbyssColorScheme.primary
        assertTrue(
            "primary должен быть синее, чем зеленее: blue=${p.blue} green=${p.green}",
            p.blue > p.green,
        )
    }

    // ── Статусные токены ─────────────────────────────────────────────────────

    @Test
    fun `status tokens are readable on their own foreground`() {
        listOf(true, false).forEach { isDark ->
            val status = biolumeStatus(isDark)
            val label = if (isDark) "Abyss" else "Tidepool"
            assertReadable("$label onSuccess/success", status.onSuccess, status.success, min = 3.0)
            assertReadable("$label onWarning/warning", status.onWarning, status.warning, min = 3.0)
        }
    }

    // ── Пресеты акцента ──────────────────────────────────────────────────────

    /**
     * `withSignalAccent` подменяет primary цветом пресета, а `onPrimary` остаётся
     * из базовой схемы — значит контраст надо проверять для каждого пресета, иначе
     * выбор акцента может втихую сделать текст на кнопках нечитаемым.
     */
    /**
     * PRO-оформление даёт любой HEX, а не только пресет: акцент — это ещё и цвет @ника,
     * ссылок и заголовков секций. Светлый акцент на Tidepool и тёмный на Abyss раньше
     * давали подписи 1.1–1.5:1 — их не было видно.
     */
    @Test
    fun `arbitrary profile accents keep primary text visible in Biolume`() {
        val accents = listOf(Color(0xFFFFF59D), Color(0xFFFFFFFF), Color(0xFF0EA5E9), Color(0xFF1A1A1A), Color(0xFF000000), Color(0xFF7FFFD4))
        listOf(Triple("Abyss", AbyssColorScheme, true), Triple("Tidepool", TidepoolColorScheme, false))
            .forEach { (label, base, isDark) ->
                accents.forEach { accent ->
                    val cs = base.withSignalAccent(accent, isDark)
                    assertReadable("$label/$accent primary/background", cs.primary, cs.background, min = 3.0)
                    assertReadable("$label/$accent onPrimary/primary", cs.onPrimary, cs.primary, min = 4.5)
                }
            }
    }

    @Test
    fun `every color preset keeps primary readable in both palettes`() {
        listOf(Triple("Abyss", AbyssColorScheme, true), Triple("Tidepool", TidepoolColorScheme, false))
            .forEach { (label, base, isDark) ->
                ColorPreset.entries.forEach { preset ->
                    val cs = base.withSignalAccent(preset.seedColor, isDark)
                    // Текст на filled-кнопке.
                    assertReadable("$label/${preset.name} onPrimary/primary", cs.onPrimary, cs.primary, min = 4.5)
                    // Подпись на выбранном чипе и pill-индикаторе навигации (§7).
                    assertReadable(
                        "$label/${preset.name} onPrimaryContainer/primaryContainer",
                        cs.onPrimaryContainer,
                        composite(cs.primaryContainer, cs.surface),
                        min = 4.5,
                    )
                }
            }
    }

    /**
     * M3E: пресет тонирует primary и непрозрачные контейнеры. secondaryContainer —
     * это selectionFill M3E (pill навигации), primaryContainer — фон моих пузырей,
     * на котором текст рисуется onSurface (см. VlBubbleTokens в Theme.kt).
     */
    @Test
    fun `every color preset keeps M3E readable`() {
        listOf(Triple("M3E dark", DarkM3, true), Triple("M3E light", LightM3, false))
            .forEach { (label, base, isDark) ->
                ColorPreset.entries.forEach { preset ->
                    val cs = base.withMaterialAccent(preset.seedColor, isDark)
                    val p = "$label/${preset.name}"
                    assertReadable("$p onPrimary/primary", cs.onPrimary, cs.primary, min = 4.5)
                    assertReadable("$p onPrimaryContainer/primaryContainer", cs.onPrimaryContainer, cs.primaryContainer, min = 4.5)
                    assertReadable("$p onSecondaryContainer/secondaryContainer", cs.onSecondaryContainer, cs.secondaryContainer, min = 4.5)
                    assertReadable("$p bubble text onSurface/primaryContainer", cs.onSurface, cs.primaryContainer, min = 4.5)
                    // Акцентная подпись (@username, заголовок секции) на фоне экрана
                    assertReadable("$p primary/background", cs.primary, cs.background, min = 3.0)
                }
            }
    }

    @Test
    fun `selection fill is readable in Biolume`() {
        listOf(true, false).forEach { isDark ->
            val cs = if (isDark) AbyssColorScheme else TidepoolColorScheme
            val selectionFill = biolumeSelectionFill(isDark, cs.primary)

            val label = "biolume ${if (isDark) "dark" else "light"}"
            // Контраст текста (primary) на фоне выделения (selectionFill)
            // Накладываем selectionFill на surfaceContainer (где обычно живут чипы/навбар)
            val background = composite(selectionFill, cs.surfaceContainer)
            assertReadable("$label primary/selectionFill", cs.primary, background, min = 3.0)
        }
    }
}

/** Персист темы: id пишется в prefs и в профиль PRO-кастомизации. */
class AppThemeIdTest {

    @Test
    fun `ids are unique and stable`() {
        val ids = AppTheme.entries.map { it.id }
        assertEquals("id тем должны быть уникальны", ids.size, ids.distinct().size)
        // Значения зафиксированы: их меняют только вместе с миграцией prefs.
        assertEquals("m3e", AppTheme.MATERIAL3_EXPRESSIVE.id)
        assertEquals("biolume", AppTheme.BIOLUME.id)
    }

    @Test
    fun `removed Forge id falls back to default`() {
        // Старый Forge удалён: у тех, кто его выбрал, prefs читаются как тема по умолчанию
        assertSame(AppTheme.Default, AppTheme.fromId("forge"))
    }

    @Test
    fun `fromId round-trips every selectable theme`() {
        AppTheme.selectableEntries.forEach { theme ->
            assertSame(theme, AppTheme.fromId(theme.id))
        }
    }

    @Test
    fun `forge v2 is not selectable and never read from prefs or profile`() {
        // Forge v2 включает режим ID-карты, а не выбор: из настроек и профиля он не читается
        assertSame(AppTheme.Default, AppTheme.fromId(AppTheme.FORGE_PROTOGEN.id))
        assertSame(AppTheme.Default, AppTheme.fromId(AppTheme.FORGE_BEAST.id))
        assertTrue(AppTheme.FORGE_PROTOGEN !in AppTheme.selectableEntries)
        assertTrue(AppTheme.FORGE_BEAST !in AppTheme.selectableEntries)
    }

    @Test
    fun `fromId falls back to default on unknown or null input`() {
        assertSame(AppTheme.Default, AppTheme.fromId(null))
        assertSame(AppTheme.Default, AppTheme.fromId(""))
        assertSame(AppTheme.Default, AppTheme.fromId("forge_industrial"))
        assertSame(AppTheme.Default, AppTheme.fromId("BIOLUME"))
    }

    @Test
    fun `structure and signal layers are off for material3 and on for biolume`() {
        // Инвариант, на котором держится «один код компонента на две темы»:
        // в M3E модификаторы глубины обязаны быть no-op.
        assertTrue(VlStructureTokens.Disabled.enabled.not())
        assertTrue(VlSignalTokens.Disabled.enabled.not())
        assertTrue(biolumeStructure(isDark = true).enabled)
        assertTrue(biolumeSignal(isDark = true).enabled)
    }
}
