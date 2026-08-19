package by.iposdev.visorlink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.data.model.AppTheme

/**
 * Порт `ExthruStyle` из lib/theme/design_system.dart.
 */
data class ExthruStyle(
    val darkShadow: Color,
    val lightShadow: Color,
    val cardBg: Color,
    val inputBg: Color,
    val accent: Color,
    val destructive: Color,
    val radius: Dp,
    val isForge: Boolean,
    val myBubbleBg: Color,
    val myBubbleFg: Color,
    val otherBubbleBg: Color,
    val otherBubbleFg: Color,
)

val LocalAppThemeOverride = staticCompositionLocalOf<AppTheme?> { null }

@Suppress("DEPRECATION")
@Composable
fun rememberExthruStyle(appTheme: AppTheme): ExthruStyle {
    val effectiveTheme = LocalAppThemeOverride.current ?: appTheme
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f
    val primary = cs.primary

    val isForgeFamily = effectiveTheme == AppTheme.FORGE || 
                        effectiveTheme == AppTheme.FORGE_INDUSTRIAL || 
                        effectiveTheme == AppTheme.FORGE_TERMINAL || 
                        effectiveTheme == AppTheme.FORGE_COMICS

    return if (isForgeFamily) {
        val otherFg = if (isDark) Color.White else Color.Black
        ExthruStyle(
            isForge = true, 
            radius = if (effectiveTheme == AppTheme.FORGE_COMICS) 8.dp else 2.dp,
            darkShadow = if (isDark) Color(0xFF000000) else Color(0xFF404040),
            lightShadow = if (isDark) Color.White.copy(0.05f) else Color.White.copy(0.3f),
            cardBg = cs.surface,
            inputBg = cs.surfaceVariant,
            accent = primary,
            destructive = Color(0xFFE50027),
            myBubbleBg = primary,
            myBubbleFg = if (effectiveTheme == AppTheme.FORGE_TERMINAL) cs.onPrimary else Color.White,
            otherBubbleBg = cs.surfaceContainerHighest,
            otherBubbleFg = otherFg,
        )
    } else {
        val bg = cs.background
        val dShadow = if (isDark) Color.Black.copy(alpha = 0.4f) else Color(0xFFA3B1C6).copy(alpha = 0.20f)
        val lShadow = if (isDark) Color.White.copy(alpha = 0.02f) else Color.White.copy(alpha = 0.8f)

        val myBg = lerp(bg, primary, if (isDark) 0.25f else 0.15f)
        val otherBg = if (isDark) lerp(bg, Color.White, 0.06f) else lerp(bg, Color.Black, 0.06f)

        val themeFg = if (isDark) Color.White.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.87f)
        val cardBgColor = if (isDark) bg else Color.White

        ExthruStyle(
            isForge = false, radius = 24.dp,
            darkShadow = dShadow,
            lightShadow = lShadow,
            cardBg = cardBgColor,
            inputBg = bg,
            accent = primary,
            destructive = Color(0xFFE50027),
            myBubbleBg = myBg,
            myBubbleFg = themeFg,
            otherBubbleBg = otherBg,
            otherBubbleFg = themeFg,
        )
    }
}
