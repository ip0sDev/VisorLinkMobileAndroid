package by.iposdev.visorlink.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ColorPreset
import by.iposdev.visorlink.data.model.ThemeMode
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.utils.CustomizationHelper
import org.koin.compose.viewmodel.koinViewModel

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

// ── User Profile Theme Wrapper ─────────────────────────────────────────────

@Composable
fun UserProfileTheme(
    profile: UserProfile?,
    currentUser: UserProfile?,
    content: @Composable () -> Unit
) {
    val themeVm: ThemeViewModel = koinViewModel()
    val currentThemeMode by themeVm.themeMode.collectAsState()
    val globalPreset by themeVm.colorPreset.collectAsState()

    VisorLinkTheme(
        themeMode = currentThemeMode,
        colorPreset = globalPreset
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}

// ── VisorLink Theme Composable ────────────────────────────────────────────────

@Composable
fun VisorLinkTheme(
    appTheme: AppTheme = AppTheme.MATERIAL3_EXPRESSIVE,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorPreset: ColorPreset = ColorPreset.DEFAULT,
    setStatusBarColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colorPreset == ColorPreset.DEFAULT -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkM3
        else      -> LightM3
    }

    val view = LocalView.current
    if (!view.isInEditMode && setStatusBarColor) {
        SideEffect {
            var context = view.context
            while (context is ContextWrapper) {
                if (context is Activity) break
                context = context.baseContext
            }
            val activity = context as? Activity
            val window = activity?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes(medium = RoundedCornerShape(16.dp)),
        typography = MaterialTheme.typography,
        content = content
    )
}
