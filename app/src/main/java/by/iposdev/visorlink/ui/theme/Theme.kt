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

// ── M3 Expressive colours ────────────────────────────────────────────────────

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
    background = Color(0xFFFAFAFF),
    onBackground = Color(0xFF0F0A1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B2E),
    surfaceVariant = Color(0xFFEEF0FF),
    onSurfaceVariant = Color(0xFF4A4660),
    outline = Color(0xFF79747E),
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
    background = Color(0xFF0F0E17),
    onBackground = Color(0xFFE8E4FF),
    surface = Color(0xFF1A1825),
    onSurface = Color(0xFFE0DCF5),
    surfaceVariant = Color(0xFF252336),
    onSurfaceVariant = Color(0xFFB0ACCC),
    outline = Color(0xFF6E6A84),
)

// ── OneUI colours ────────────────────────────────────────────────────────────

private val LightOneUI = lightColorScheme(
    primary = Color(0xFF0381FE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6EAFF),
    onPrimaryContainer = Color(0xFF00234A),
    secondary = Color(0xFF007BFF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE5FF),
    onSecondaryContainer = Color(0xFF00274D),
    tertiary = Color(0xFF53B1FD),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF606060),
    outline = Color(0xFFD0D0D0),
)

private val DarkOneUI = darkColorScheme(
    primary = Color(0xFF5AC8FA),
    onPrimary = Color(0xFF003A5C),
    primaryContainer = Color(0xFF00527F),
    onPrimaryContainer = Color(0xFFD6EAFF),
    secondary = Color(0xFF53B1FD),
    onSecondary = Color(0xFF00274D),
    secondaryContainer = Color(0xFF003D6E),
    onSecondaryContainer = Color(0xFFCCE5FF),
    tertiary = Color(0xFF90D2FA),
    onTertiary = Color(0xFF003A5C),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF3A3A3A),
)

// ── Shapes ───────────────────────────────────────────────────────────────────

val ShapesM3 = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

val ShapesOneUI = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

// ── Typography ───────────────────────────────────────────────────────────────

val TypographyM3 = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

val TypographyOneUI = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
)

// ── Entry point ──────────────────────────────────────────────────────────────

@Composable
fun VisorLinkTheme(
    appTheme: AppTheme = AppTheme.MATERIAL3_EXPRESSIVE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.MATERIAL3_EXPRESSIVE -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val ctx = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            }
            darkTheme -> DarkM3
            else -> LightM3
        }
        AppTheme.ONE_UI -> if (darkTheme) DarkOneUI else LightOneUI
    }

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
        shapes = if (appTheme == AppTheme.ONE_UI) ShapesOneUI else ShapesM3,
        typography = if (appTheme == AppTheme.ONE_UI) TypographyOneUI else TypographyM3,
        content = content
    )
}