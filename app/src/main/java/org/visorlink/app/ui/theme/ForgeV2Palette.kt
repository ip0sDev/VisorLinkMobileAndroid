package org.visorlink.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Forge v2 — киберпанк-терминал особых режимов ID-карты. Пользователь его не выбирает:
 * тему включает режим Protogen / Beast (см. ModeThemeRules), поэтому в селекторе её нет.
 *
 * Палитра, формы (скругления 2–8 dp, у Beast до 10) и контур панелей `primary` 16 % — из веба
 * (блок FORGE v2 в src/styles/variables.css). Поверх них Android-слой света, а не геометрии:
 *  - заголовки — Unbounded с мягким неоновым свечением (см. forgeV2Typography);
 *  - заголовки разделов с линией до края, неоновая линия под верхней панелью;
 *  - основная кнопка светится и в покое, тумблер — светящаяся «риска» ([ForgeV2Switch]);
 *  - неоновая дымка сверху экрана, у Protogen ещё сканлайны.
 * Только тёмная. Protogen — холодный неон, Beast — тёплый янтарь, спокойнее.
 */
object ForgeV2 {

    data class Flavor(
        val background: Color,
        val surface: Color,
        val containerLow: Color,
        val container: Color,
        val containerHigh: Color,
        val containerHighest: Color,
        val onSurface: Color,
        val onSurfaceVariant: Color,
        val primary: Color,
        val onPrimary: Color,
        val secondary: Color,
        val onSecondary: Color,
        val tertiary: Color,
        val onTertiary: Color,
        val success: Color,
        val warning: Color,
        val error: Color,
        val onError: Color,
        /** --radius-lg / --radius-full: карточки и «круглые» элементы. */
        val radiusLg: Dp,
        /** --radius-2xl: потолок любого скругления в теме. */
        val radius2xl: Dp,
        /** Сила неона: свечение заголовков и кнопки. Beast спокойнее. */
        val neon: Float,
        /** Оттенок Beast — по признаку, а не по ссылке: подкрашенная копия ([tintedBy]) — уже другой объект. */
        val beast: Boolean = false,
    )

    val Protogen = Flavor(
        background = Color(0xFF06080C),
        surface = Color(0xFF0A0E13),
        containerLow = Color(0xFF0D1218),
        container = Color(0xFF10161E),
        containerHigh = Color(0xFF151D27),
        containerHighest = Color(0xFF1C2633),
        onSurface = Color(0xFFE2F4FA),
        onSurfaceVariant = Color(0xFF8DA3B3),
        primary = Color(0xFF3DF2FF),
        onPrimary = Color(0xFF00191D),
        secondary = Color(0xFFFF4FD8),
        onSecondary = Color(0xFF2A0022),
        tertiary = Color(0xFF7C9BFF),
        onTertiary = Color(0xFF050B26),
        success = Color(0xFF5CFFA8),
        warning = Color(0xFFFFD23D),
        error = Color(0xFFFF4F6B),
        onError = Color(0xFF2A0008),
        radiusLg = 6.dp,
        radius2xl = 8.dp,
        neon = 1f,
    )

    val Beast = Flavor(
        background = Color(0xFF0B0907),
        surface = Color(0xFF110E0B),
        containerLow = Color(0xFF15110D),
        container = Color(0xFF1A1511),
        containerHigh = Color(0xFF221C16),
        containerHighest = Color(0xFF2B241D),
        onSurface = Color(0xFFF4ECE2),
        onSurfaceVariant = Color(0xFFAE9F8E),
        primary = Color(0xFFFFB04D),
        onPrimary = Color(0xFF2A1600),
        secondary = Color(0xFFE7C27A),
        onSecondary = Color(0xFF2A1E05),
        tertiary = Color(0xFFFF8A65),
        onTertiary = Color(0xFF2A0E05),
        success = Color(0xFFA8DB6E),
        warning = Color(0xFFFFD36B),
        error = Color(0xFFFF6B5E),
        onError = Color(0xFF2A0705),
        radiusLg = 8.dp,
        radius2xl = 10.dp,
        neon = 0.7f,
        beast = true,
    )

    /** `color-mix(in srgb, a p%, b)` для непрозрачного b. */
    internal fun mix(a: Color, fraction: Float, b: Color): Color = a.copy(alpha = fraction).compositeOver(b)
}

/**
 * Цвет профиля PRO **заменяет** оттенок Protogen / Beast (веб `uiAccentVars(…, { forge: true })`):
 * перекрашиваются только primary и onPrimary. Неоновые линии, свечение, пузыри, выделение,
 * контейнеры и дымка Forge выводит из primary своими формулами — форма темы остаётся Forge.
 * Вторичный цвет, ошибки, статусы и поверхности — цвета режима.
 *
 * Основной цвет здесь ещё и цвет подписей (@ник, заголовки разделов) — на фоне и на панелях:
 * тон подстраивается под тёмную тему как в вебе ([ProAccent.readable]) и на всякий случай
 * доводится до 4.5:1 к самой светлой панели с текстом (`containerHigh`).
 */
internal fun ForgeV2.Flavor.tintedBy(accent: Color): ForgeV2.Flavor {
    val neon = ProAccent.readable(accent, light = false).withContrastAgainst(containerHigh, min = 4.5f, towards = Color.White)
    return copy(primary = neon, onPrimary = ProAccent.onColor(neon))
}

/**
 * Контейнеры M3 — непрозрачные смеси (как `--selection-fill` в вебе), а не primary
 * с альфой: полупрозрачный `*Container` на поверхности контейнера не читается.
 */
fun forgeV2ColorScheme(f: ForgeV2.Flavor): ColorScheme = darkColorScheme(
    primary = f.primary,
    onPrimary = f.onPrimary,
    primaryContainer = ForgeV2.mix(f.primary, 0.16f, f.container),
    onPrimaryContainer = f.primary,
    inversePrimary = f.primary,

    secondary = f.secondary,
    onSecondary = f.onSecondary,
    secondaryContainer = ForgeV2.mix(f.secondary, 0.14f, f.container),
    onSecondaryContainer = f.secondary,

    tertiary = f.tertiary,
    onTertiary = f.onTertiary,
    tertiaryContainer = ForgeV2.mix(f.tertiary, 0.14f, f.container),
    onTertiaryContainer = f.tertiary,

    error = f.error,
    onError = f.onError,
    errorContainer = ForgeV2.mix(f.error, 0.16f, f.container),
    onErrorContainer = f.error,

    background = f.background,
    onBackground = f.onSurface,
    surface = f.surface,
    onSurface = f.onSurface,
    surfaceVariant = f.containerHigh,
    onSurfaceVariant = f.onSurfaceVariant,
    surfaceTint = f.primary,

    surfaceContainerLowest = f.background,
    surfaceContainerLow = f.containerLow,
    surfaceContainer = f.container,
    surfaceContainerHigh = f.containerHigh,
    surfaceContainerHighest = f.containerHighest,
    surfaceDim = f.background,
    surfaceBright = f.containerHighest,

    // --forge-line-strong / --outline-variant
    outline = f.primary.copy(alpha = 0.34f),
    outlineVariant = f.primary.copy(alpha = 0.10f),

    inverseSurface = f.onSurface,
    inverseOnSurface = f.surface,
    scrim = Color.Black,
)

/** Шкала скруглений: xs 2 · sm 3 · md 4 · lg 6/8 · 2xl 8/10 (ограничивает любую форму). */
fun forgeV2Shapes(f: ForgeV2.Flavor): VlShapeTokens {
    val lg = RoundedCornerShape(f.radiusLg)
    return VlShapeTokens(
        button = lg,
        buttonPressed = RoundedCornerShape(4.dp),
        card = lg,
        field = RoundedCornerShape(4.dp),
        chip = lg,
        fab = lg,
        bar = RoundedCornerShape(f.radius2xl),
        inputPanel = lg,
        pill = lg,
        // Точки статуса и бегунок остаются круглыми: на 8–10 dp это и есть --radius-full
        indicator = CircleShape,
        avatar = lg,
        cardRadius = f.radiusLg,
        buttonRadius = f.radiusLg,
        section = lg,
        // Строка отступает от края секции на 8 dp — концентрично это почти прямой угол
        row = RoundedCornerShape(3.dp),
        maxRadius = f.radius2xl,
    )
}

/**
 * Структура: контур `primary` 16 % вместо неоморфного рельефа (--neo-shadow-out / -in),
 * под «поднятым» — короткая мягкая тень вниз.
 */
internal fun forgeV2Structure(f: ForgeV2.Flavor) = VlStructureTokens(
    enabled = true,
    shadowDark = Color.Black.copy(alpha = 0.55f),
    shadowLight = Color.Transparent,
    outline = f.primary.copy(alpha = 0.16f),
    raisedOffset = 6.dp,
    raisedBlur = 18.dp,
    insetOffset = 2.dp,
    insetBlur = 8.dp,
)

/** Неон: --glow-primary, у Beast мягче. Основная кнопка светится и в покое. */
internal fun forgeV2Signal(f: ForgeV2.Flavor) = VlSignalTokens(
    enabled = true,
    glowBlur = if (f.beast) 12.dp else 14.dp,
    glowAlpha = if (f.beast) 0.22f else 0.35f,
    fabRestAlpha = if (f.beast) 0.14f else 0.2f,
    focusBorder = 1.dp,
    pulsePeriodMs = 2000,
    buttonRestAlpha = 0.2f * f.neon,
)

/** Дисплей терминала: неоновая дымка основного цвета сверху, у Protogen ещё сканлайны. */
internal fun forgeV2Terminal(f: ForgeV2.Flavor) = if (f.beast) {
    VlTerminalTokens(
        labelPrefix = "",
        neon = f.primary,
        neonAlt = f.secondary,
        haze = f.primary.copy(alpha = 0.05f),
    )
} else {
    VlTerminalTokens(
        labelPrefix = "> ",
        scanlineAlpha = 0.018f,
        neon = f.primary,
        neonAlt = f.secondary,
        haze = f.primary.copy(alpha = 0.06f),
    )
}

/**
 * Тумблер «риска»: тонкая светящаяся планка вместо круглого бегунка — круг не вписывался
 * в трек со скруглением 6–8 dp. Включённый трек подсвечен контуром и заливкой.
 */
internal val ForgeV2Switch = VlSwitchTokens(
    thumb = RoundedCornerShape(2.dp),
    thumbWidth = 8.dp,
    thumbHeight = 22.dp,
    thumbGlow = 0.6f,
    litTrack = true,
)

internal fun forgeV2Status(f: ForgeV2.Flavor) = VlStatusTokens(
    success = f.success,
    onSuccess = f.background,
    warning = f.warning,
    onWarning = f.background,
)

internal fun forgeV2Bubbles(f: ForgeV2.Flavor) = VlBubbleTokens(
    mineBg = ForgeV2.mix(f.primary, 0.16f, f.container),
    mineFg = f.onSurface,
    otherBg = f.container,
    otherFg = f.onSurface,
)

/** Терминал: короткие переходы без пружины, нажатие — лёгкое сжатие. */
internal val ForgeV2Motion = VlMotionTokens(
    pressStyle = VlPressStyle.SCALE,
    pressScale = 0.98f,
    useSpring = false,
    dampingRatio = 1f,
    stiffness = 0f,
    durationMs = 120,
    glitch = true,
)
