package by.iposdev.visorlink.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ColorPreset
import by.iposdev.visorlink.data.model.ThemeMode

// ── M3 Expressive ─────────────────────────────────────────────────────────────

private val LightM3 = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF2E1065),
    tertiary = Color(0xFFDB2777),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCE7F3),
    onTertiaryContainer = Color(0xFF831843),
    background = Color(0xFFFAFAFF),
    onBackground = Color(0xFF0F0A1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B2E),
    surfaceVariant = Color(0xFFEEF0FF),
    onSurfaceVariant = Color(0xFF4A4660),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F4FF),
    surfaceContainer = Color(0xFFEFEEFF),
    surfaceContainerHigh = Color(0xFFE9E8FA),
    surfaceContainerHighest = Color(0xFFE4E3F5),
)

private val DarkM3 = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = Color(0xFF4C1D95),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = Color(0xFFF472B6),
    onTertiary = Color(0xFF831843),
    tertiaryContainer = Color(0xFF9D174D),
    onTertiaryContainer = Color(0xFFFCE7F3),
    background = Color(0xFF0F0E17),
    onBackground = Color(0xFFE8E4FF),
    surface = Color(0xFF1A1825),
    onSurface = Color(0xFFE0DCF5),
    surfaceVariant = Color(0xFF252336),
    onSurfaceVariant = Color(0xFFB0ACCC),
    outline = Color(0xFF6E6A84),
    outlineVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFF0B0A14),
    surfaceContainerLow = Color(0xFF1C1B2E),
    surfaceContainer = Color(0xFF201F32),
    surfaceContainerHigh = Color(0xFF2B293C),
    surfaceContainerHighest = Color(0xFF353347),
)

// ── OneUI 8.5 ─────────────────────────────────────────────────────────────────

private val LightOneUI = lightColorScheme(
    primary = Color(0xFF006FFD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001C45),
    secondary = Color(0xFF0381FE),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCDFFF),
    onSecondaryContainer = Color(0xFF00174A),
    tertiary = Color(0xFF5B5EA6),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E0FF),
    onTertiaryContainer = Color(0xFF17175E),
    error = Color(0xFFFF3B30),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F4F4),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFECECEC),
    surfaceContainerHighest = Color(0xFFE6E6E6),
)

private val DarkOneUI = darkColorScheme(
    primary = Color(0xFF5B9BFF),
    onPrimary = Color(0xFF00285C),
    primaryContainer = Color(0xFF003E8D),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF63A0FF),
    onSecondary = Color(0xFF002D6A),
    secondaryContainer = Color(0xFF004498),
    onSecondaryContainer = Color(0xFFCCDFFF),
    tertiary = Color(0xFFC3C2FF),
    onTertiary = Color(0xFF2D2D75),
    tertiaryContainer = Color(0xFF44448D),
    onTertiaryContainer = Color(0xFFE2E0FF),
    error = Color(0xFFFF453A),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF161616),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF373737),
)

// ── Shapes ────────────────────────────────────────────────────────────────────

val ShapesM3 = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

val ShapesOneUI = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val ShapesExthru = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Forge — neo-brutalist, острые углы везде (радиус = 0 во Flutter-версии)
val ShapesForge = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

// ── Typography ────────────────────────────────────────────────────────────────

val TypographyM3 = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

val TypographyOneUI = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
)

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
@Suppress("DEPRECATION")
fun VisorLinkTheme(
    appTheme: AppTheme = AppTheme.BIOLUME,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorPreset: ColorPreset = ColorPreset.DEFAULT,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> systemDark
    }

    // EXTHRU оставлен как алиас BIOLUME для экранов, которые ещё не мигрировали на новое имя.
    val resolvedTheme = if (appTheme == AppTheme.EXTHRU) AppTheme.BIOLUME else appTheme

    val baseColorScheme = when (resolvedTheme) {
        AppTheme.MATERIAL3_EXPRESSIVE -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colorPreset == ColorPreset.DEFAULT -> {
                val ctx = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            }
            darkTheme -> DarkM3
            else      -> LightM3
        }
        AppTheme.ONE_UI -> if (darkTheme) DarkOneUI else LightOneUI
        AppTheme.BIOLUME -> if (darkTheme) BiolumeDarkColorScheme else BiolumeLightColorScheme
        AppTheme.FORGE -> if (darkTheme) ForgeDarkColorScheme else ForgeLightColorScheme
        AppTheme.EXTHRU -> if (darkTheme) BiolumeDarkColorScheme else BiolumeLightColorScheme // недостижимо, resolvedTheme выше уже разрешил
    }

    // Кастомный акцентный цвет (пресет) перекрашивает схему поверх любой из 3 тем.
    // Для M3 с включённым Material You (preset == DEFAULT) пресет не применяется — используется системный динамический цвет.
    val colorScheme = baseColorScheme.withColorPreset(resolvedTheme, darkTheme, colorPreset)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = when (resolvedTheme) {
            AppTheme.ONE_UI  -> ShapesOneUI
            AppTheme.BIOLUME -> ShapesExthru
            AppTheme.FORGE   -> ShapesForge
            else             -> ShapesM3
        },
        typography = when (resolvedTheme) {
            AppTheme.ONE_UI  -> TypographyOneUI
            AppTheme.BIOLUME -> ExthruTypography
            AppTheme.FORGE   -> ForgeTypography
            else             -> TypographyM3
        },
        content = content
    )
}