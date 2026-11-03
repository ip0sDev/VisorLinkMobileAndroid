package org.visorlink.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Forge v2 — киберпанк особых режимов ID-карты, язык «жёсткого блока». Пользователь его не выбирает:
 * тему включает режим Protogen / Beast (см. ModeThemeRules), поэтому в селекторе её нет.
 *
 * Палитра — из веба (блок FORGE v2 в src/styles/variables.css); формы и структура уже свои, Android-овские:
 *  - прямые углы 2–3 dp, плотные плиты без размытия вместо теней (панель — тусклая плита, кнопка и FAB — яркая
 *    плита второго цвета, при нажатии вдавливаются), крепкий контур 1,5 dp;
 *  - выбор — инвертированный блок (сплошной primary, тёмный текст), опасное — косая штриховка, выключенное — пунктир;
 *  - заголовки — Unbounded без свечения, подписи кнопок — жирный моно; тумблер — «риска»;
 *  - никаких сканлайнов, дымки и свечения в покое — они не давали характера.
 * Только тёмная. Protogen — холодный неон + маджента, Beast — тёплый янтарь.
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
        val proAccent: Color? = null,
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
        radiusLg = 2.dp,
        radius2xl = 3.dp,
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
        radiusLg = 2.dp,
        radius2xl = 3.dp,
        neon = 0.7f,
        beast = true,
    )

    // ── Clean Room (светлая): фарфор и молочное стекло ──
    val CleanBackground = Color(0xFFEEEFEE)
    val CleanSurface = Color(0xFFF8F8F6)
    val CleanContainerLowest = Color(0xFFFFFFFF)
    val CleanContainerLow = Color(0xFFF5F5F4)
    val CleanContainer = Color(0xFFF1F1F0)
    val CleanContainerHigh = Color(0xFFE9EAE9)
    val CleanContainerHighest = Color(0xFFE1E2E3)
    val CleanOnSurface = Color(0xFF17181B)
    val CleanOnSurfaceVariant = Color(0xFF55585F)
    val CleanKey = Color(0xFFFF5A1F) // Международный оранжевый: клавиши и светодиоды
    val CleanGraphite = Color(0xFF26272B) // Графит: «мои» пузыри и второй цвет
    val CleanTertiary = Color(0xFF0F7A6A)
    val CleanError = Color(0xFFC8243C)
    val CleanScreen = Color(0xFFFAFAF9) // Светлое стекло LED-экрана

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
    return copy(primary = neon, onPrimary = ProAccent.onColor(neon), proAccent = accent)
}

/**
 * Контейнеры M3 — непрозрачные смеси (как `--selection-fill` в вебе), а не primary
 * с альфой: полупрозрачный `*Container` на поверхности контейнера не читается.
 */
fun forgeV2ColorScheme(f: ForgeV2.Flavor, dark: Boolean = true): ColorScheme = if (dark) darkColorScheme(
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
) else forgeV2CleanRoomColorScheme(f)

private fun forgeV2CleanRoomColorScheme(f: ForgeV2.Flavor): ColorScheme {
    val cleanKey = if (f.proAccent != null) {
        ProAccent.readable(f.proAccent, light = true).withContrastAgainst(ForgeV2.CleanBackground, min = 3f, towards = Color.Black)
    } else {
        ForgeV2.CleanKey
    }
    val container = ForgeV2.CleanContainer
    return lightColorScheme(
        primary = cleanKey,
        onPrimary = Color.White,
        primaryContainer = ForgeV2.mix(cleanKey, 0.14f, container),
        onPrimaryContainer = cleanKey,
        inversePrimary = Color(0xFF3DF2FF),

        secondary = ForgeV2.CleanGraphite,
        onSecondary = Color(0xFFF4F4F2),
        secondaryContainer = ForgeV2.mix(ForgeV2.CleanGraphite, 0.10f, container),
        onSecondaryContainer = ForgeV2.CleanGraphite,

        tertiary = ForgeV2.CleanTertiary,
        onTertiary = Color.White,
        tertiaryContainer = ForgeV2.mix(ForgeV2.CleanTertiary, 0.12f, container),
        onTertiaryContainer = ForgeV2.CleanTertiary,

        error = ForgeV2.CleanError,
        onError = Color.White,
        errorContainer = ForgeV2.mix(ForgeV2.CleanError, 0.12f, container),
        onErrorContainer = ForgeV2.CleanError,

        background = ForgeV2.CleanBackground,
        onBackground = ForgeV2.CleanOnSurface,
        surface = ForgeV2.CleanSurface,
        onSurface = ForgeV2.CleanOnSurface,
        surfaceVariant = ForgeV2.CleanContainerHigh,
        onSurfaceVariant = ForgeV2.CleanOnSurfaceVariant,
        surfaceTint = cleanKey,

        surfaceContainerLowest = ForgeV2.CleanContainerLowest,
        surfaceContainerLow = ForgeV2.CleanContainerLow,
        surfaceContainer = container,
        surfaceContainerHigh = ForgeV2.CleanContainerHigh,
        surfaceContainerHighest = ForgeV2.CleanContainerHighest,
        surfaceDim = ForgeV2.CleanBackground,
        surfaceBright = ForgeV2.CleanContainerHighest,

        outline = Color.Black.copy(alpha = 0.16f),
        outlineVariant = Color.Black.copy(alpha = 0.08f),

        inverseSurface = ForgeV2.CleanOnSurface,
        inverseOnSurface = ForgeV2.CleanSurface,
        scrim = Color.Black,
    )
}


/** Шкала скруглений: xs 2 · sm 3 · md 4 · lg 6/8 · 2xl 8/10 (ограничивает любую форму). */
fun forgeV2Shapes(f: ForgeV2.Flavor): VlShapeTokens {
    // Силуэт: диагональная пара срезанных углов. Скругления нет вообще, потолок — для чужих форм
    val cut = ChamferShape(8.dp)
    val small = ChamferShape(5.dp)
    return VlShapeTokens(
        button = cut,
        buttonPressed = cut,
        card = cut,
        field = ChamferShape(6.dp),
        chip = small,
        fab = cut,
        bar = cut,
        inputPanel = cut,
        pill = small,
        // Точки статуса остаются мелкими блоками: у 8 dp срез и круг неразличимы
        indicator = CircleShape,
        avatar = ChamferShape(6.dp),
        cardRadius = f.radiusLg,
        buttonRadius = f.radiusLg,
        section = cut,
        row = small,
        maxRadius = f.radius2xl,
        chamfer = 6.dp,
    )
}

/**
 * Структура: контур `primary` 16 % вместо неоморфного рельефа (--neo-shadow-out / -in),
 * под «поднятым» — короткая мягкая тень вниз.
 */
internal fun forgeV2Structure(f: ForgeV2.Flavor, dark: Boolean = true) = if (dark) {
    VlStructureTokens(
        enabled = true,
        shadowDark = Color.Black.copy(alpha = 0.55f),
        shadowLight = Color.Transparent,
        outline = f.primary.copy(alpha = 0.30f),
        // Жёсткие плиты: панель — тусклая плита под цвет основного, кнопка — яркая второго цвета
        hardShadow = ForgeV2.mix(f.primary, 0.26f, f.background),
        hardShadowAlt = ForgeV2.mix(f.secondary, 0.85f, f.background),
        hardOffset = 4.dp,
        raisedOffset = 6.dp,
        raisedBlur = 18.dp,
        insetOffset = 2.dp,
        insetBlur = 8.dp,
    )
} else {
    VlStructureTokens(
        enabled = true,
        shadowDark = Color.Black.copy(alpha = 0.12f),
        shadowLight = Color.White.copy(alpha = 0.80f),
        outline = Color(0xFF17181B).copy(alpha = 0.16f),
        hardShadow = Color(0xFFD8D9D8),
        hardShadowAlt = ForgeV2.mix(ForgeV2.CleanKey, 0.30f, ForgeV2.CleanBackground),
        hardOffset = 4.dp,
        raisedOffset = 4.dp,
        raisedBlur = 10.dp,
        insetOffset = 2.dp,
        insetBlur = 6.dp,
    )
}

/** Неон: --glow-primary, у Beast мягче. Основная кнопка светится и в покое. */
internal fun forgeV2Signal(f: ForgeV2.Flavor, dark: Boolean = true) = if (dark) {
    VlSignalTokens(
        enabled = true,
        glowBlur = if (f.beast) 12.dp else 14.dp,
        glowAlpha = if (f.beast) 0.22f else 0.35f,
        fabRestAlpha = 0f,
        focusBorder = 2.dp,
        pulsePeriodMs = 2000,
        buttonRestAlpha = 0f,
    )
} else {
    VlSignalTokens(
        enabled = true,
        glowBlur = 8.dp,
        glowAlpha = 0.16f,
        fabRestAlpha = 0f,
        focusBorder = 2.dp,
        pulsePeriodMs = 2000,
        buttonRestAlpha = 0f,
    )
}

/** Дисплей терминала: неоновая дымка основного цвета сверху, у Protogen ещё сканлайны. */
internal fun forgeV2Terminal(f: ForgeV2.Flavor, dark: Boolean = true) = if (dark) {
    if (f.beast) {
        VlTerminalTokens(
            labelPrefix = "",
            tag = "BEAST",
            neon = f.primary,
            neonAlt = f.secondary,
        )
    } else {
        VlTerminalTokens(
            labelPrefix = "> ",
            tag = "PROTOGEN",
            neon = f.primary,
            neonAlt = f.secondary,
        )
    }
} else {
    VlTerminalTokens(
        labelPrefix = if (f.beast) "" else "> ",
        tag = if (f.beast) "BEAST" else "PROTOGEN",
        neon = ForgeV2.CleanKey,
        neonAlt = ForgeV2.CleanGraphite,
    )
}

/**
 * Тумблер «риска»: тонкая светящаяся планка вместо круглого бегунка — круг не вписывался
 * в трек со скруглением 6–8 dp. Включённый трек подсвечен контуром и заливкой.
 */
internal fun forgeV2Switch(f: ForgeV2.Flavor, dark: Boolean = true) = VlSwitchTokens(
    thumb = RoundedCornerShape(2.dp),
    thumbWidth = 10.dp,
    thumbHeight = 22.dp,
    thumbGlow = if (dark) (if (f.beast) 0.5f else 0.8f) else 0f,
    litTrack = true,
    ledDots = true,
)

internal val ForgeV2Switch = forgeV2Switch(ForgeV2.Protogen, true)

internal fun forgeV2Status(f: ForgeV2.Flavor, dark: Boolean = true) = if (dark) {
    VlStatusTokens(
        success = f.success,
        onSuccess = f.background,
        warning = f.warning,
        onWarning = f.background,
    )
} else {
    VlStatusTokens(
        success = Color(0xFF1B8556),
        onSuccess = Color.White,
        warning = Color(0xFFC77700),
        onWarning = Color.White,
    )
}

internal fun forgeV2Bubbles(f: ForgeV2.Flavor, dark: Boolean = true) = if (dark) {
    VlBubbleTokens(
        mineBg = ForgeV2.mix(f.primary, 0.22f, f.container),
        mineFg = f.onSurface,
        otherBg = f.container,
        otherFg = f.onSurface,
        mineBorder = f.primary.copy(alpha = 0.45f),
        otherBorder = f.primary.copy(alpha = 0.22f),
    )
} else {
    VlBubbleTokens(
        mineBg = ForgeV2.CleanGraphite,
        mineFg = Color(0xFFF4F4F2),
        otherBg = ForgeV2.CleanContainer,
        otherFg = ForgeV2.CleanOnSurface,
        mineBorder = ForgeV2.CleanKey.copy(alpha = 0.30f),
        otherBorder = Color.Black.copy(alpha = 0.08f),
    )
}

fun forgeV2Led(f: ForgeV2.Flavor, dark: Boolean = true): VlLedTokens = if (dark) {
    VlLedTokens(
        color = f.primary,
        screen = if (f.beast) Color(0xFF080605) else Color(0xFF050507),
        offAlpha = 0.08f,
        glowBlur = 6.dp,
    )
} else {
    VlLedTokens(
        color = if (f.proAccent != null) ProAccent.readable(f.proAccent, light = true) else ForgeV2.CleanKey,
        screen = ForgeV2.CleanScreen,
        offAlpha = 0.10f,
        glowBlur = 0.dp,
    )
}

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

