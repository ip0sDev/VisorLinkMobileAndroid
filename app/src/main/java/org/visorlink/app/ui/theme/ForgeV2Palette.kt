package org.visorlink.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Forge — приборная панель VisorLink: «Clean Room» (светлая: фарфор, графит, оранжевая клавиша) и
 * «Blackout LED» (тёмная: матовый графит, цветная клавиша, светодиоды на чёрном стекле).
 *
 * Поверхности у обоих режимов (Protogen и Beast) общие — различается оттенок светодиодов и клавиши
 * в тёмной теме. Скруглённые клавиши 16/24/26 dp со светом сверху и LED-экраном.
 */
object ForgeV2 {

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

    // ── Blackout LED (тёмная): матовый графит и чёрное стекло матрицы ──
    val BlackoutBackground = Color(0xFF131418)
    val BlackoutSurface = Color(0xFF1A1B1F)
    val BlackoutContainerLowest = Color(0xFF0A0A0C)
    val BlackoutContainerLow = Color(0xFF1D1E22)
    val BlackoutContainer = Color(0xFF222327)
    val BlackoutContainerHigh = Color(0xFF2A2B30)
    val BlackoutContainerHighest = Color(0xFF33353B)
    val BlackoutOnSurface = Color(0xFFF2F2F4)
    val BlackoutOnSurfaceVariant = Color(0xFFA3A6AE)
    val BlackoutKey = Color(0xFFFF3D9A) // Розовая клавиша Protogen
    val BlackoutKeyBeast = Color(0xFFFF8A3D) // Тёплая клавиша Beast
    val BlackoutError = Color(0xFFFF4F6B)
    val BlackoutScreen = Color(0xFF050507) // Чёрное стекло LED-экрана
    val BlackoutThumb = Color(0xFF3A3C43) // Графитовая клавиша-бегунок

    data class Flavor(
        val led: Color,
        val beast: Boolean = false,
        val proAccent: Color? = null,
    ) {
        val background: Color get() = BlackoutBackground
        val surface: Color get() = BlackoutSurface
        val containerLow: Color get() = BlackoutContainerLow
        val container: Color get() = BlackoutContainer
        val containerHigh: Color get() = BlackoutContainerHigh
        val containerHighest: Color get() = BlackoutContainerHighest
        val onSurface: Color get() = BlackoutOnSurface
        val onSurfaceVariant: Color get() = BlackoutOnSurfaceVariant
        val primary: Color get() = forgeKey(this, dark = true)
        val onPrimary: Color get() = onColorFor(primary)
        val secondary: Color get() = led
        val onSecondary: Color get() = Color(0xFF00191D)
        val tertiary: Color get() = if (beast) Color(0xFFFFD36B) else Color(0xFFB6FF3B)
        val onTertiary: Color get() = Color(0xFF192400)
        val success: Color get() = if (beast) Color(0xFFA8DB6E) else Color(0xFF5CFFA8)
        val warning: Color get() = if (beast) Color(0xFFFFD36B) else Color(0xFFFFD23D)
        val error: Color get() = BlackoutError
        val onError: Color get() = Color(0xFF2A0008)
        val radiusLg: Dp get() = 24.dp
        val radius2xl: Dp get() = 26.dp
        val neon: Float get() = if (beast) 0.7f else 1f
    }

    val Protogen = Flavor(
        led = Color(0xFF3DF2FF),
        beast = false,
    )

    val Beast = Flavor(
        led = Color(0xFFFFB04D),
        beast = true,
    )

    /** `color-mix(in srgb, a p%, b)` для непрозрачного b. */
    internal fun mix(a: Color, fraction: Float, b: Color): Color = a.copy(alpha = fraction).compositeOver(b)
}

/**
 * Цвет профиля PRO перекрашивает клавиши ([forgeKey]), оставляя поверхности и LED-слой режима.
 */
internal fun ForgeV2.Flavor.tintedBy(accent: Color): ForgeV2.Flavor =
    copy(proAccent = accent)

/**
 * Цвет клавиши (primary): в светлой теме — международный оранжевый, в тёмной — розовый Protogen
 * или янтарный Beast; с PRO-акцентом — сам акцент, доведённый до читаемости.
 */
fun forgeKey(f: ForgeV2.Flavor, dark: Boolean): Color {
    if (f.proAccent != null) {
        return ProAccent.readable(f.proAccent, light = !dark).withContrastAgainst(
            bg = if (dark) ForgeV2.BlackoutContainerHigh else ForgeV2.CleanBackground,
            min = if (dark) 4.5f else 3f,
            towards = if (dark) Color.White else Color.Black,
        )
    }
    if (!dark) {
        return ForgeV2.CleanKey
    }
    return if (f.beast) ForgeV2.BlackoutKeyBeast else ForgeV2.BlackoutKey
}

/**
 * Цветовая схема Forge: тёмная — Blackout LED, светлая — Clean Room.
 */
fun forgeV2ColorScheme(f: ForgeV2.Flavor, dark: Boolean = true): ColorScheme {
    val primary = forgeKey(f, dark)
    val onPrimary = onColorFor(primary)
    if (dark) {
        val sec = f.proAccent ?: f.led
        val tert = if (f.beast) Color(0xFFFFD36B) else Color(0xFFB6FF3B)
        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = ForgeV2.mix(primary, 0.18f, ForgeV2.BlackoutContainer),
            onPrimaryContainer = lerp(primary, Color.White, 0.55f),
            inversePrimary = ForgeV2.CleanKey,

            secondary = sec,
            onSecondary = Color(0xFF00191D),
            secondaryContainer = ForgeV2.mix(sec, 0.14f, ForgeV2.BlackoutContainer),
            onSecondaryContainer = lerp(sec, Color.White, 0.55f),

            tertiary = tert,
            onTertiary = Color(0xFF192400),
            tertiaryContainer = ForgeV2.mix(tert, 0.14f, ForgeV2.BlackoutContainer),
            onTertiaryContainer = lerp(tert, Color.White, 0.55f),

            error = ForgeV2.BlackoutError,
            onError = Color(0xFF2A0008),
            errorContainer = ForgeV2.mix(ForgeV2.BlackoutError, 0.16f, ForgeV2.BlackoutContainer),
            onErrorContainer = lerp(ForgeV2.BlackoutError, Color.White, 0.55f),

            background = ForgeV2.BlackoutBackground,
            onBackground = ForgeV2.BlackoutOnSurface,
            surface = ForgeV2.BlackoutSurface,
            onSurface = ForgeV2.BlackoutOnSurface,
            surfaceVariant = ForgeV2.BlackoutContainerHigh,
            onSurfaceVariant = ForgeV2.BlackoutOnSurfaceVariant,
            surfaceTint = primary,

            surfaceContainerLowest = ForgeV2.BlackoutContainerLowest,
            surfaceContainerLow = ForgeV2.BlackoutContainerLow,
            surfaceContainer = ForgeV2.BlackoutContainer,
            surfaceContainerHigh = ForgeV2.BlackoutContainerHigh,
            surfaceContainerHighest = ForgeV2.BlackoutContainerHighest,
            surfaceDim = ForgeV2.BlackoutBackground,
            surfaceBright = ForgeV2.BlackoutContainerHighest,

            outline = Color.White.copy(alpha = 0.12f),
            outlineVariant = Color.White.copy(alpha = 0.06f),

            inverseSurface = ForgeV2.BlackoutOnSurface,
            inverseOnSurface = ForgeV2.BlackoutSurface,
            scrim = Color.Black,
        )
    }

    val primaryContainer = ForgeV2.mix(primary, 0.14f, ForgeV2.CleanContainer)
    val tertiaryContainer = ForgeV2.mix(ForgeV2.CleanTertiary, 0.12f, ForgeV2.CleanContainer)

    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = lerp(primary, Color.Black, 0.35f),
        inversePrimary = ForgeV2.BlackoutKey,

        secondary = ForgeV2.CleanGraphite,
        onSecondary = Color(0xFFF4F4F2),
        secondaryContainer = ForgeV2.mix(ForgeV2.CleanGraphite, 0.10f, ForgeV2.CleanContainer),
        onSecondaryContainer = ForgeV2.CleanGraphite,

        tertiary = ForgeV2.CleanTertiary,
        onTertiary = Color.White,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = ForgeV2.CleanTertiary,

        error = ForgeV2.CleanError,
        onError = Color.White,
        errorContainer = ForgeV2.mix(ForgeV2.CleanError, 0.12f, ForgeV2.CleanContainer),
        onErrorContainer = ForgeV2.CleanError,

        background = ForgeV2.CleanBackground,
        onBackground = ForgeV2.CleanOnSurface,
        surface = ForgeV2.CleanSurface,
        onSurface = ForgeV2.CleanOnSurface,
        surfaceVariant = ForgeV2.CleanContainerHigh,
        onSurfaceVariant = ForgeV2.CleanOnSurfaceVariant,
        surfaceTint = primary,

        surfaceContainerLowest = ForgeV2.CleanContainerLowest,
        surfaceContainerLow = ForgeV2.CleanContainerLow,
        surfaceContainer = ForgeV2.CleanContainer,
        surfaceContainerHigh = ForgeV2.CleanContainerHigh,
        surfaceContainerHighest = ForgeV2.CleanContainerHighest,
        surfaceDim = ForgeV2.CleanContainerHighest,
        surfaceBright = ForgeV2.CleanContainerLowest,

        outline = Color.Black.copy(alpha = 0.16f),
        outlineVariant = Color.Black.copy(alpha = 0.06f),

        inverseSurface = ForgeV2.CleanOnSurface,
        inverseOnSurface = ForgeV2.CleanSurface,
        scrim = Color.Black,
    )
}

/**
 * Формы Forge — приборные клавиши: скруглённые прямоугольники 16/24/26 dp, круглые индикаторы и аватары.
 */
val ForgeShapes = VlShapeTokens(
    button = RoundedCornerShape(16.dp),
    buttonPressed = RoundedCornerShape(16.dp),
    card = RoundedCornerShape(24.dp),
    field = RoundedCornerShape(16.dp),
    chip = RoundedCornerShape(12.dp),
    fab = RoundedCornerShape(26.dp),
    bar = RoundedCornerShape(26.dp),
    inputPanel = RoundedCornerShape(26.dp),
    pill = RoundedCornerShape(percent = 50),
    indicator = CircleShape,
    avatar = CircleShape,
    cardRadius = 24.dp,
    buttonRadius = 16.dp,
    section = RoundedCornerShape(24.dp),
    row = RoundedCornerShape(16.dp),
)

fun forgeV2Shapes(@Suppress("UNUSED_PARAMETER") f: ForgeV2.Flavor): VlShapeTokens = ForgeShapes

/**
 * Рельеф приборных клавиш: свет сверху, тень вниз, верхняя кромка подсвечена изнутри (`keycapSheen > 0`).
 */
internal fun forgeV2Structure(@Suppress("UNUSED_PARAMETER") f: ForgeV2.Flavor, dark: Boolean = true) = VlStructureTokens(
    enabled = true,
    shadowDark = if (dark) Color.Black.copy(alpha = 0.70f) else ForgeV2.CleanOnSurface.copy(alpha = 0.22f),
    shadowLight = if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.95f),
    raisedOffset = if (dark) 5.dp else 4.dp,
    raisedBlur = if (dark) 12.dp else 10.dp,
    insetOffset = 2.dp,
    insetBlur = if (dark) 6.dp else 4.dp,
    keycapSheen = if (dark) 0.07f else 0.60f,
)

/** Подсветка клавиш: у основной кнопки и FAB — тихое свечение в покое, усиливается при нажатии. */
internal fun forgeV2Signal(@Suppress("UNUSED_PARAMETER") f: ForgeV2.Flavor, dark: Boolean = true) = VlSignalTokens(
    enabled = true,
    glowBlur = if (dark) 16.dp else 12.dp,
    glowAlpha = if (dark) 0.45f else 0.30f,
    fabRestAlpha = if (dark) 0.45f else 0.35f,
    focusBorder = 1.5.dp,
    buttonRestAlpha = if (dark) 0.30f else 0.22f,
)

/** Метаданные оттенка Forge (для совместимости со служебными вызовами). */
internal fun forgeV2Terminal(f: ForgeV2.Flavor, dark: Boolean = true) = VlTerminalTokens(
    labelPrefix = "",
    tag = if (f.beast) "BEAST" else "PROTOGEN",
    neon = if (dark) f.led else forgeKey(f, dark = false),
    neonAlt = if (dark) forgeKey(f, dark = true) else ForgeV2.CleanGraphite,
)

/**
 * Тумблер Forge: круглая клавиша-бегунок в утопленном треке. В Blackout включённый трек —
 * чёрное стекло с горящими LED-точками, в Clean Room — оранжевая дорожка и белая клавиша.
 */
internal fun forgeV2Switch(f: ForgeV2.Flavor, dark: Boolean = true): VlSwitchTokens {
    val primary = forgeKey(f, dark)
    return VlSwitchTokens(
        thumbWidth = 26.dp,
        thumbHeight = 26.dp,
        thumbColor = if (dark) ForgeV2.BlackoutThumb else Color.White,
        trackOnColor = if (dark) ForgeV2.BlackoutScreen else primary,
        litTrack = dark,
        ledDots = dark,
    )
}

internal val ForgeV2Switch = forgeV2Switch(ForgeV2.Protogen, true)

internal fun forgeV2Status(f: ForgeV2.Flavor, dark: Boolean = true) = if (dark) {
    VlStatusTokens(
        success = f.success,
        onSuccess = Color(0xFF002114),
        warning = f.warning,
        onWarning = Color(0xFF261900),
    )
} else {
    VlStatusTokens(
        success = Color(0xFF1B8556),
        onSuccess = Color.White,
        warning = Color(0xFFC77700),
        onWarning = Color.White,
    )
}

/**
 * Пузыри Forge: в Clean Room «мой» — графитовый блок со светлым текстом, «чужой» — белая клавиша;
 * в Blackout «мой» — тёмный графит с оттенком клавиши и цветным кантом.
 */
internal fun forgeV2Bubbles(f: ForgeV2.Flavor, dark: Boolean = true): VlBubbleTokens {
    val primary = forgeKey(f, dark)
    return if (dark) {
        VlBubbleTokens(
            mineBg = lerp(ForgeV2.BlackoutContainerHigh, primary, 0.24f),
            mineFg = ForgeV2.BlackoutOnSurface,
            otherBg = ForgeV2.BlackoutContainer,
            otherFg = ForgeV2.BlackoutOnSurface,
            mineBorder = primary.copy(alpha = 0.38f),
            otherBorder = Color.White.copy(alpha = 0.08f),
        )
    } else {
        VlBubbleTokens(
            mineBg = ForgeV2.CleanGraphite,
            mineFg = Color(0xFFF4F4F2),
            otherBg = ForgeV2.CleanContainerLowest,
            otherFg = ForgeV2.CleanOnSurface,
            mineBorder = Color.Black.copy(alpha = 0.18f),
            otherBorder = Color.Black.copy(alpha = 0.08f),
        )
    }
}

/** Выбранный сегмент/чип в Forge: оттенок primary поверх surfaceContainer (как в Flutter `forgeSelectionFill`). */
fun forgeSelectionFill(primary: Color, dark: Boolean): Color =
    if (dark) ForgeV2.mix(primary, 0.18f, ForgeV2.BlackoutContainer) else ForgeV2.mix(primary, 0.14f, ForgeV2.CleanContainer)

/**
 * LED-слой Forge: в Blackout точки горят цветом режима (голубой Protogen / янтарный Beast) на
 * чёрном стекле; в Clean Room — оранжевые точки на светлом стекле.
 */
fun forgeV2Led(f: ForgeV2.Flavor, dark: Boolean = true): VlLedTokens = if (dark) {
    VlLedTokens(
        color = f.proAccent ?: f.led,
        screen = if (f.beast) Color(0xFF080605) else ForgeV2.BlackoutScreen,
        offAlpha = 0.08f,
        glowBlur = 6.dp,
    )
} else {
    VlLedTokens(
        color = f.proAccent ?: ForgeV2.CleanKey,
        screen = ForgeV2.CleanScreen,
        offAlpha = 0.06f,
        glowBlur = 0.dp,
    )
}

/** Клавиша при нажатии утапливается (raised → inset), переход — быстрый ход клавиши без пружины. */
internal val ForgeV2Motion = VlMotionTokens(
    pressStyle = VlPressStyle.INSET,
    pressScale = 1f,
    useSpring = false,
    dampingRatio = 1f,
    stiffness = 0f,
    durationMs = 160,
    glitch = false,
)
