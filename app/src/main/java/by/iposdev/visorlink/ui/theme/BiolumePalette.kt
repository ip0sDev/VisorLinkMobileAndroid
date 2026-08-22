package by.iposdev.visorlink.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Biolume — палитра из гайдлайна v2 (§3). Значения перенесены буквально из таблиц.
 *
 * Роли, которых в таблицах нет (inverse*, `*Container` для tertiary/error, подписи
 * на контейнерах), достроены по правилу «полупрозрачная заливка + акцент как
 * подпись» — так §7 описывает выбранный чип. Там, где акцент при этом не дотягивал
 * до требуемых §8 4.5:1, взят сдвинутый по светлоте тон того же тона; все такие
 * случаи помечены и покрыты `ThemeContrastTest`.
 *
 * Две темы — это два разных «физических режима» освещения (§0):
 *  - [AbyssColorScheme] — тёмная, свет производится точечно самим интерфейсом;
 *  - [TidepoolColorScheme] — светлая, свет внешний, неон почти не нужен.
 */
object Biolume {

    // ── Abyss (тёмная) — §3.1 ────────────────────────────────────────────────

    /** Чуть светлее чистого чёрного — иначе неоморфным теням не на чем читаться (§10). */
    val AbyssBackground = Color(0xFF0E1214)
    val AbyssSurface = Color(0xFF141A1D)
    val AbyssContainerLowest = Color(0xFF0A0E10)
    val AbyssContainerLow = Color(0xFF171E21)
    val AbyssContainer = Color(0xFF1A2126)
    val AbyssContainerHigh = Color(0xFF212930)
    val AbyssContainerHighest = Color(0xFF28313A)
    val AbyssOnSurface = Color(0xFFECEFEE)
    val AbyssOnSurfaceVariant = Color(0xFF93A0A0)

    /** Синяя биолюминесценция: hue ~193°, специально сдвинута от зелёного (§3.1). */
    val AbyssPrimary = Color(0xFF35C7E8)
    val AbyssOnPrimary = Color(0xFF00212B)

    /** Фиолетовый — медузы/сифонофоры. */
    val AbyssSecondary = Color(0xFF8C6BFF)
    val AbyssOnSecondary = Color(0xFF1B1033)

    /** «Лунный» голубой — редкий, для информационных акцентов, не для действий. */
    val AbyssTertiary = Color(0xFF6FC6FF)
    val AbyssOnTertiary = Color(0xFF012538)

    /** Приглушённый, НЕ кислотный лайм — иначе «плюс к балансу» вайб (§3.1). */
    val AbyssSuccess = Color(0xFFA8DB6E)
    val AbyssWarning = Color(0xFFFFC24E)
    val AbyssError = Color(0xFFFF4D6A)

    /**
     * Цвет текста на полупрозрачных `*Container`.
     *
     * Гайдлайн задаёт заливку контейнеров, но не подпись на них, а §7 предлагает
     * брать сам акцент. Для `secondary` это даёт 4.01:1 — ниже требуемых §8
     * 4.5:1 (проверяется `ThemeContrastTest`), поэтому здесь взят осветлённый
     * тон того же тона. Остальные акценты Abyss проходят как есть.
     */
    val AbyssOnSecondaryContainer = Color(0xFFA88FFF)

    // ── Tidepool (светлая) — §3.2 ────────────────────────────────────────────

    /** Тёплый нейтральный камень; в v2 заметно менее жёлтый, чем в v1. */
    val TidepoolBackground = Color(0xFFF2F0E9)
    val TidepoolSurface = Color(0xFFF8F6F0)
    val TidepoolContainerLowest = Color(0xFFFBFAF6)
    val TidepoolContainerLow = Color(0xFFF1EFE6)
    val TidepoolContainer = Color(0xFFECEAE0)
    val TidepoolContainerHigh = Color(0xFFE2DFD1)
    val TidepoolContainerHighest = Color(0xFFD8D4C3)
    val TidepoolOnSurface = Color(0xFF1C1F1E)
    val TidepoolOnSurfaceVariant = Color(0xFF5C625E)

    /** Глубокий синий-тил, а НЕ осветлённая копия dark-primary (§3.2). */
    val TidepoolPrimary = Color(0xFF0E7FA3)
    val TidepoolSecondary = Color(0xFF6A4FE0)
    val TidepoolTertiary = Color(0xFF1B76B0)

    val TidepoolSuccess = Color(0xFF4C9A2A)
    val TidepoolWarning = Color(0xFFB8790A)
    val TidepoolError = Color(0xFFD6304A)

    /**
     * Тот же случай, что и в Abyss, но в обратную сторону: на светлом фоне акценты
     * Tidepool — средние по светлоте, и как подпись на своём `*Container` дают
     * 3.7–4.4:1. Здесь взяты затемнённые тона того же тона, чтобы выдержать §8.
     */
    val TidepoolOnPrimaryContainer = Color(0xFF0A5D77)
    val TidepoolOnSecondaryContainer = Color(0xFF5A3FC8)
    val TidepoolOnTertiaryContainer = Color(0xFF15608F)
    val TidepoolOnErrorContainer = Color(0xFFB01F36)

    // ── Слой структуры — §4.1 ────────────────────────────────────────────────

    val AbyssShadowDark = Color.Black.copy(alpha = 0.45f)
    val AbyssShadowLight = Color.White.copy(alpha = 0.025f)
    val TidepoolShadowDark = Color.Black.copy(alpha = 0.08f)
    val TidepoolShadowLight = Color.White.copy(alpha = 0.90f)
}

// ── ColorScheme ──────────────────────────────────────────────────────────────

val AbyssColorScheme: ColorScheme = darkColorScheme(
    primary = Biolume.AbyssPrimary,
    onPrimary = Biolume.AbyssOnPrimary,
    primaryContainer = Biolume.AbyssPrimary.copy(alpha = 0.12f),
    onPrimaryContainer = Biolume.AbyssPrimary,
    inversePrimary = Biolume.TidepoolPrimary,

    secondary = Biolume.AbyssSecondary,
    onSecondary = Biolume.AbyssOnSecondary,
    secondaryContainer = Biolume.AbyssSecondary.copy(alpha = 0.14f),
    onSecondaryContainer = Biolume.AbyssOnSecondaryContainer,

    tertiary = Biolume.AbyssTertiary,
    onTertiary = Biolume.AbyssOnTertiary,
    tertiaryContainer = Biolume.AbyssTertiary.copy(alpha = 0.12f),
    onTertiaryContainer = Biolume.AbyssTertiary,

    error = Biolume.AbyssError,
    onError = Color(0xFF3A0009),
    errorContainer = Biolume.AbyssError.copy(alpha = 0.14f),
    onErrorContainer = Biolume.AbyssError,

    background = Biolume.AbyssBackground,
    onBackground = Biolume.AbyssOnSurface,
    surface = Biolume.AbyssSurface,
    onSurface = Biolume.AbyssOnSurface,
    surfaceVariant = Biolume.AbyssContainerHigh,
    onSurfaceVariant = Biolume.AbyssOnSurfaceVariant,
    surfaceTint = Biolume.AbyssPrimary,

    surfaceContainerLowest = Biolume.AbyssContainerLowest,
    surfaceContainerLow = Biolume.AbyssContainerLow,
    surfaceContainer = Biolume.AbyssContainer,
    surfaceContainerHigh = Biolume.AbyssContainerHigh,
    surfaceContainerHighest = Biolume.AbyssContainerHighest,
    surfaceDim = Biolume.AbyssBackground,
    surfaceBright = Biolume.AbyssContainerHighest,

    // Нейтральная, некрасящая грань (§3.1) — не путать с сигнальным контуром (§10).
    outline = Color.White.copy(alpha = 0.14f),
    outlineVariant = Color.White.copy(alpha = 0.06f),

    inverseSurface = Biolume.AbyssOnSurface,
    inverseOnSurface = Biolume.AbyssSurface,
    scrim = Color.Black,
)

val TidepoolColorScheme: ColorScheme = lightColorScheme(
    primary = Biolume.TidepoolPrimary,
    onPrimary = Color.White,
    primaryContainer = Biolume.TidepoolPrimary.copy(alpha = 0.10f),
    onPrimaryContainer = Biolume.TidepoolOnPrimaryContainer,
    inversePrimary = Biolume.AbyssPrimary,

    secondary = Biolume.TidepoolSecondary,
    onSecondary = Color.White,
    secondaryContainer = Biolume.TidepoolSecondary.copy(alpha = 0.10f),
    onSecondaryContainer = Biolume.TidepoolOnSecondaryContainer,

    tertiary = Biolume.TidepoolTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Biolume.TidepoolTertiary.copy(alpha = 0.10f),
    onTertiaryContainer = Biolume.TidepoolOnTertiaryContainer,

    error = Biolume.TidepoolError,
    onError = Color.White,
    errorContainer = Biolume.TidepoolError.copy(alpha = 0.10f),
    onErrorContainer = Biolume.TidepoolOnErrorContainer,

    background = Biolume.TidepoolBackground,
    onBackground = Biolume.TidepoolOnSurface,
    surface = Biolume.TidepoolSurface,
    onSurface = Biolume.TidepoolOnSurface,
    surfaceVariant = Biolume.TidepoolContainerHigh,
    onSurfaceVariant = Biolume.TidepoolOnSurfaceVariant,
    surfaceTint = Biolume.TidepoolPrimary,

    surfaceContainerLowest = Biolume.TidepoolContainerLowest,
    surfaceContainerLow = Biolume.TidepoolContainerLow,
    surfaceContainer = Biolume.TidepoolContainer,
    surfaceContainerHigh = Biolume.TidepoolContainerHigh,
    surfaceContainerHighest = Biolume.TidepoolContainerHighest,
    surfaceDim = Biolume.TidepoolContainerHighest,
    surfaceBright = Biolume.TidepoolSurface,

    outline = Color.Black.copy(alpha = 0.22f),
    outlineVariant = Color.Black.copy(alpha = 0.06f),

    inverseSurface = Biolume.TidepoolOnSurface,
    inverseOnSurface = Biolume.TidepoolSurface,
    scrim = Color.Black,
)

// ── Формы Biolume — §2, §7 ───────────────────────────────────────────────────

/**
 * Stadium-кнопки — дефолт M3 с 2023 года; асимметричный FAB — часть shape-набора
 * M3 Expressive. Морфинг формы при нажатии — тоже паттерн M3E, а не наша выдумка.
 */
val BiolumeShapes = VlShapeTokens(
    button = RoundedCornerShape(percent = 50),
    buttonPressed = RoundedCornerShape(percent = 34),
    card = RoundedCornerShape(16.dp),
    field = RoundedCornerShape(16.dp),
    chip = RoundedCornerShape(percent = 50),
    fab = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 14.dp,
        bottomEnd = 28.dp,
        bottomStart = 14.dp,
    ),
    bar = RoundedCornerShape(percent = 50),
    inputPanel = RoundedCornerShape(28.dp),
    pill = RoundedCornerShape(percent = 50),
    indicator = CircleShape,
    avatar = CircleShape,
    cardRadius = 16.dp,
    buttonRadius = 28.dp,
)

/** M3E оставляем как было: скругления 16/24dp, без stadium и без асимметрии. */
val Material3Shapes = VlShapeTokens(
    button = RoundedCornerShape(16.dp),
    buttonPressed = RoundedCornerShape(16.dp),
    card = RoundedCornerShape(24.dp),
    field = RoundedCornerShape(16.dp),
    chip = RoundedCornerShape(50),
    fab = RoundedCornerShape(16.dp),
    bar = RoundedCornerShape(32.dp),
    inputPanel = RoundedCornerShape(32.dp),
    pill = RoundedCornerShape(percent = 50),
    indicator = CircleShape,
    avatar = CircleShape,
    cardRadius = 24.dp,
    // 20dp — прежнее значение кнопок в M3E, сохраняем вид темы без изменений.
    buttonRadius = 20.dp,
)

/** M3E: лёгкое сжатие пружиной — прежнее поведение. */
internal val Material3Motion = VlMotionTokens(
    pressStyle = VlPressStyle.SCALE,
    pressScale = 0.97f,
    pressOffset = 0.dp,
    useSpring = true,
    dampingRatio = 1f,
    stiffness = 1500f,
    durationMs = 200,
)

/** Biolume: нажатие сообщает рельеф, движение мягкое и «органическое». */
internal val BiolumeMotion = VlMotionTokens(
    pressStyle = VlPressStyle.INSET,
    pressScale = 1f,
    pressOffset = 0.dp,
    useSpring = true,
    dampingRatio = 0.75f,
    stiffness = 400f,
    durationMs = 220,
)

// ── Сборка токенов ───────────────────────────────────────────────────────────

internal fun biolumeStructure(isDark: Boolean) = VlStructureTokens(
    enabled = true,
    shadowDark = if (isDark) Biolume.AbyssShadowDark else Biolume.TidepoolShadowDark,
    shadowLight = if (isDark) Biolume.AbyssShadowLight else Biolume.TidepoolShadowLight,
)

internal fun biolumeSignal(isDark: Boolean) = VlSignalTokens(
    enabled = true,
    glowBlur = if (isDark) 12.dp else 10.dp,
    glowAlpha = if (isDark) 0.28f else 0.18f,
    fabRestAlpha = if (isDark) 0.16f else 0.12f,
)

/**
 * «Мой» баббл — подмешанный к поверхности primary: заметно, но не как filled-CTA,
 * иначе каждое сообщение читалось бы как кнопка. «Чужой» — нейтральный контейнер.
 */
internal fun biolumeBubbles(isDark: Boolean, primary: Color) = VlBubbleTokens(
    mineBg = lerp(
        if (isDark) Biolume.AbyssContainer else Biolume.TidepoolContainer,
        primary,
        if (isDark) 0.22f else 0.14f,
    ),
    mineFg = if (isDark) Biolume.AbyssOnSurface else Biolume.TidepoolOnSurface,
    otherBg = if (isDark) Biolume.AbyssContainer else Biolume.TidepoolContainer,
    otherFg = if (isDark) Biolume.AbyssOnSurface else Biolume.TidepoolOnSurface,
)

internal fun biolumeSelectionFill(isDark: Boolean, primary: Color): Color = lerp(
    if (isDark) Biolume.AbyssContainer else Biolume.TidepoolContainer,
    primary,
    if (isDark) 0.20f else 0.14f,
)

internal fun biolumeStatus(isDark: Boolean) = VlStatusTokens(
    success = if (isDark) Biolume.AbyssSuccess else Biolume.TidepoolSuccess,
    onSuccess = if (isDark) Color(0xFF14250A) else Color.White,
    warning = if (isDark) Biolume.AbyssWarning else Biolume.TidepoolWarning,
    onWarning = if (isDark) Color(0xFF2B1B00) else Color.White,
)

/**
 * Пресеты акцента перекрашивают только **сигнальный** слой (primary/secondary/
 * tertiary + цвет glow), а поверхности Abyss/Tidepool остаются каноничными —
 * они и есть идентичность темы (§0, §1.1). `DEFAULT` = цвета гайдлайна без правок.
 *
 * Вместе с `primary` обязательно пересчитываются `onPrimary` и
 * `onPrimaryContainer`: базовые значения подобраны под каноничный акцент, и если
 * их не тронуть, тёмный пресет на тёмной теме даёт нечитаемый текст на кнопках
 * (PURPLE в Abyss давал 2.45:1 — поймано `ThemeContrastTest`).
 */
internal fun ColorScheme.withSignalAccent(accent: Color?, isDark: Boolean): ColorScheme {
    if (accent == null) return this
    return copy(
        primary = accent,
        onPrimary = onColorFor(accent),
        primaryContainer = accent.copy(alpha = primaryContainer.alpha),
        onPrimaryContainer = accentOnContainer(accent, isDark),
        surfaceTint = accent,
    )
}

/**
 * Чёрный или белый поверх заливки — берём тот, что реально даёт больший контраст.
 *
 * Порог по светлоте здесь не работает: у `#0EA5E9` (пресет BLUE) светлота 0.33,
 * «на глаз» это светлый цвет, но белый текст на нём даёт 2.8:1, а тёмный — 6.8:1.
 * Поэтому считаем оба варианта по WCAG и выбираем лучший.
 */
private fun onColorFor(accent: Color): Color {
    val dark = Color(0xFF06131A)
    return if (wcagContrast(accent, dark) >= wcagContrast(accent, Color.White)) dark else Color.White
}

private fun wcagContrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/**
 * Подпись цветом акцента на его же полупрозрачном контейнере: тон сдвигается к
 * фону-антиподу, иначе тёмный акцент теряется на тёмном контейнере (и наоборот).
 */
private fun accentOnContainer(accent: Color, isDark: Boolean): Color =
    if (isDark) lerp(accent, Color.White, 0.55f) else lerp(accent, Color.Black, 0.35f)
