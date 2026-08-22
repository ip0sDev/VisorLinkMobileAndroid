package by.iposdev.visorlink.ui.theme

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Forge — индустриальная тема: прямые углы, жёсткая тень, металл и сильный красный.
 *
 * Отличие от Biolume принципиальное, а не косметическое. Biolume строится на
 * рассеянном свете и мягком рельефе; Forge — на одном жёстком источнике света,
 * прямых углах и материале, который не светится сам. Поэтому здесь:
 *  - все формы прямоугольные ([ForgeShapes]);
 *  - рельеф без размытия ([VlStructureTokens.hardEdge]);
 *  - сигнальный слой ведёт себя как индикаторная лампа, а не как биолюминесценция:
 *    свечение туже, плотнее и пульсирует вдвое быстрее.
 *
 * Два режима — «Steel» (тёмный, графит и окалина) и «Concrete» (светлый, бетон и
 * оцинковка).
 */
object Forge {

    // ── Steel (тёмная) ───────────────────────────────────────────────────────

    /** Графит, а не чёрный: жёсткой тени нужен тон, на котором она читается. */
    val SteelBackground = Color(0xFF121212)
    val SteelSurface = Color(0xFF181818)
    val SteelContainerLowest = Color(0xFF0C0C0C)
    val SteelContainerLow = Color(0xFF1C1C1C)
    val SteelContainer = Color(0xFF222222)
    val SteelContainerHigh = Color(0xFF2C2C2C)
    val SteelContainerHighest = Color(0xFF383838)
    val SteelOnSurface = Color(0xFFEDEDED)
    val SteelOnSurfaceVariant = Color(0xFFA6A6A6)

    /**
     * Сильный красный — основное действие. Взят светлый и горячий, чтобы на
     * графите держать контраст с тёмной подписью.
     */
    val SteelPrimary = Color(0xFFFF5233)
    val SteelOnPrimary = Color(0xFF250600)

    /** Предупреждающий янтарь — вторая половина индустриальной пары «красный/жёлтый». */
    val SteelSecondary = Color(0xFFFFB300)
    val SteelOnSecondary = Color(0xFF2A1C00)

    /** Оцинкованная сталь: холодный нейтральный акцент для информации. */
    val SteelTertiary = Color(0xFF9FB3C0)
    val SteelOnTertiary = Color(0xFF10202A)

    val SteelSuccess = Color(0xFF7CB342)
    val SteelWarning = Color(0xFFFFB300)

    /**
     * Ошибка вынужденно живёт рядом с primary: тема требует красный как основной
     * акцент. Разводим их по светлоте — primary горячий и светлый, error глухой и
     * тёмный, — но полагаться только на цвет здесь нельзя: критические состояния в
     * Forge обязаны иметь иконку или подпись. Это ограничение самой темы, см. TODO.
     */
    val SteelError = Color(0xFFD4183A)

    // ── Concrete (светлая) ───────────────────────────────────────────────────

    val ConcreteBackground = Color(0xFFE6E6E3)
    val ConcreteSurface = Color(0xFFEFEFEC)
    val ConcreteContainerLowest = Color(0xFFF7F7F5)
    val ConcreteContainerLow = Color(0xFFE2E2DF)
    val ConcreteContainer = Color(0xFFDADAD6)
    val ConcreteContainerHigh = Color(0xFFCECECA)
    val ConcreteContainerHighest = Color(0xFFC0C0BB)
    val ConcreteOnSurface = Color(0xFF171717)
    val ConcreteOnSurfaceVariant = Color(0xFF4F4F4C)

    /** На бетоне красный должен быть глубоким, иначе «плывёт» и теряет вес. */
    val ConcretePrimary = Color(0xFFC0271D) // Slightly lighter for contrast/lightness delta
    val ConcreteSecondary = Color(0xFF8A5A00)
    val ConcreteTertiary = Color(0xFF44606F)

    val ConcreteSuccess = Color(0xFF3F7A1F)
    val ConcreteWarning = Color(0xFF8A5A00)
    val ConcreteError = Color(0xFF8E1220)

    // ── Слой структуры: жёсткий свет ─────────────────────────────────────────

    /** Плотная тень без размытия — силуэт, а не рассеивание. */
    val SteelShadowDark = Color.Black.copy(alpha = 0.85f)
    val SteelShadowLight = Color.White.copy(alpha = 0.10f)
    val ConcreteShadowDark = Color.Black.copy(alpha = 0.32f)
    val ConcreteShadowLight = Color.White.copy(alpha = 0.75f)
}

// ── ColorScheme ──────────────────────────────────────────────────────────────

val ForgeSteelColorScheme: ColorScheme = darkColorScheme(
    primary = Forge.SteelPrimary,
    onPrimary = Forge.SteelOnPrimary,
    primaryContainer = Forge.SteelPrimary.copy(alpha = 0.16f), // Lowered alpha for better contrast
    onPrimaryContainer = Forge.SteelPrimary,
    inversePrimary = Forge.ConcretePrimary,

    secondary = Forge.SteelSecondary,
    onSecondary = Forge.SteelOnSecondary,
    secondaryContainer = Forge.SteelSecondary.copy(alpha = 0.18f),
    onSecondaryContainer = Forge.SteelSecondary,

    tertiary = Forge.SteelTertiary,
    onTertiary = Forge.SteelOnTertiary,
    tertiaryContainer = Forge.SteelTertiary.copy(alpha = 0.16f),
    onTertiaryContainer = Forge.SteelTertiary,

    error = Forge.SteelError,
    onError = Color(0xFFFFFFFF),
    errorContainer = Forge.SteelError.copy(alpha = 0.20f),
    onErrorContainer = Color(0xFFFF8FA0),

    background = Forge.SteelBackground,
    onBackground = Forge.SteelOnSurface,
    surface = Forge.SteelSurface,
    onSurface = Forge.SteelOnSurface,
    surfaceVariant = Forge.SteelContainerHigh,
    onSurfaceVariant = Forge.SteelOnSurfaceVariant,
    surfaceTint = Forge.SteelPrimary,

    surfaceContainerLowest = Forge.SteelContainerLowest,
    surfaceContainerLow = Forge.SteelContainerLow,
    surfaceContainer = Forge.SteelContainer,
    surfaceContainerHigh = Forge.SteelContainerHigh,
    surfaceContainerHighest = Forge.SteelContainerHighest,
    surfaceDim = Forge.SteelBackground,
    surfaceBright = Forge.SteelContainerHighest,

    // Грань в Forge видимая, а не намёк: у металла есть кромка.
    outline = Color.White.copy(alpha = 0.30f),
    outlineVariant = Color.White.copy(alpha = 0.16f),

    inverseSurface = Forge.SteelOnSurface,
    inverseOnSurface = Forge.SteelSurface,
    scrim = Color.Black,
)

val ForgeConcreteColorScheme: ColorScheme = lightColorScheme(
    primary = Forge.ConcretePrimary,
    onPrimary = Color.White,
    primaryContainer = Forge.ConcretePrimary.copy(alpha = 0.08f), // Lowered alpha for better contrast
    onPrimaryContainer = Forge.ConcretePrimary,
    inversePrimary = Forge.SteelPrimary,

    secondary = Forge.ConcreteSecondary,
    onSecondary = Color.White,
    secondaryContainer = Forge.ConcreteSecondary.copy(alpha = 0.10f), // Lowered alpha for better contrast
    onSecondaryContainer = Forge.ConcreteSecondary,

    tertiary = Forge.ConcreteTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Forge.ConcreteTertiary.copy(alpha = 0.11f), // Lowered alpha for better contrast
    onTertiaryContainer = Forge.ConcreteTertiary,

    error = Forge.ConcreteError,
    onError = Color.White,
    errorContainer = Forge.ConcreteError.copy(alpha = 0.12f),
    onErrorContainer = Forge.ConcreteError,

    background = Forge.ConcreteBackground,
    onBackground = Forge.ConcreteOnSurface,
    surface = Forge.ConcreteSurface,
    onSurface = Forge.ConcreteOnSurface,
    surfaceVariant = Forge.ConcreteContainerHigh,
    onSurfaceVariant = Forge.ConcreteOnSurfaceVariant,
    surfaceTint = Forge.ConcretePrimary,

    surfaceContainerLowest = Forge.ConcreteContainerLowest,
    surfaceContainerLow = Forge.ConcreteContainerLow,
    surfaceContainer = Forge.ConcreteContainer,
    surfaceContainerHigh = Forge.ConcreteContainerHigh,
    surfaceContainerHighest = Forge.ConcreteContainerHighest,
    surfaceDim = Forge.ConcreteContainerHighest,
    surfaceBright = Forge.ConcreteContainerLowest,

    outline = Color.Black.copy(alpha = 0.42f),
    outlineVariant = Color.Black.copy(alpha = 0.20f),

    inverseSurface = Forge.ConcreteOnSurface,
    inverseOnSurface = Forge.ConcreteSurface,
    scrim = Color.Black,
)

// ── Формы: всё прямоугольное ─────────────────────────────────────────────────

/**
 * Ни одного скругления. `cardRadius`/`buttonRadius` = 0 — от них считается
 * геометрия рельефа и сигнального контура, поэтому «квадратность» должна быть
 * согласованной, а не только на уровне заливки.
 */
val ForgeShapes = VlShapeTokens(
    button = RectangleShape,
    buttonPressed = RectangleShape,
    card = RectangleShape,
    field = RectangleShape,
    chip = RectangleShape,
    fab = RectangleShape,
    bar = RectangleShape,
    inputPanel = RectangleShape,
    pill = RectangleShape,
    indicator = RectangleShape,
    avatar = RectangleShape,
    cardRadius = 0.dp,
    buttonRadius = 0.dp,
)

// ── Сборка токенов ───────────────────────────────────────────────────────────

internal fun forgeStructure(isDark: Boolean) = VlStructureTokens(
    enabled = true,
    shadowDark = if (isDark) Forge.SteelShadowDark else Forge.ConcreteShadowDark,
    shadowLight = if (isDark) Forge.SteelShadowLight else Forge.ConcreteShadowLight,
    hardEdge = true,
    // Смещение совпадает с ForgeMotion.pressOffset: при нажатии элемент садится
    // ровно в свою тень, и это читается как ход механизма.
    raisedOffset = 4.dp,
    insetOffset = 2.dp,
)

/**
 * Сигнал ведёт себя как индикаторная лампа: туже по радиусу, плотнее по
 * непрозрачности и вдвое быстрее биолюминесцентного «дыхания».
 */
internal fun forgeSignal(isDark: Boolean) = VlSignalTokens(
    enabled = true,
    glowBlur = if (isDark) 8.dp else 6.dp,
    glowAlpha = if (isDark) 0.42f else 0.26f,
    fabRestAlpha = if (isDark) 0.22f else 0.14f,
    focusBorder = 2.dp,
    pulsePeriodMs = 1200,
)

internal fun forgeStatus(isDark: Boolean) = VlStatusTokens(
    success = if (isDark) Forge.SteelSuccess else Forge.ConcreteSuccess,
    onSuccess = if (isDark) Color(0xFF10240A) else Color.White,
    warning = if (isDark) Forge.SteelWarning else Forge.ConcreteWarning,
    onWarning = if (isDark) Color(0xFF2A1C00) else Color.White,
)

/** Выделение — плотная красная подложка. */
internal fun forgeSelectionFill(isDark: Boolean, primary: Color): Color =
    primary.copy(alpha = if (isDark) 0.26f else 0.18f)

internal fun forgeBubbles(isDark: Boolean, primary: Color) = VlBubbleTokens(
    mineBg = primary.copy(alpha = if (isDark) 0.24f else 0.16f),
    mineFg = if (isDark) Forge.SteelOnSurface else Forge.ConcreteOnSurface,
    otherBg = if (isDark) Forge.SteelContainer else Forge.ConcreteContainer,
    otherFg = if (isDark) Forge.SteelOnSurface else Forge.ConcreteOnSurface,
)

/** Механика не пружинит: линейный переход, «штампующее» нажатие. */
internal val ForgeMotion = VlMotionTokens(
    pressStyle = VlPressStyle.STAMP,
    pressScale = 1f,
    pressOffset = 4.dp,
    useSpring = false,
    dampingRatio = 1f,
    stiffness = 0f,
    durationMs = 80,
)
