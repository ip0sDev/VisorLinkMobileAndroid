package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.theme.rememberExthruStyle

// ── One UI Chat color tokens ───────────────────────────────────────────────────

object OneUiChat {
    val Blue              = Color(0xFF1259C3)
    val BlueDark          = Color(0xFF4D90F0)
    val PageBg            = Color(0xFFF4F4F4)
    val PageBgDark        = Color(0xFF1A1A1A)
    val CardBg            = Color(0xFFFFFFFF)
    val CardBgDark        = Color(0xFF2C2C2C)
    val TextPrimary       = Color(0xFF1A1A1A)
    val TextPrimaryDark   = Color(0xFFEEEEEE)
    val TextSecondary     = Color(0xFF888888)
    val TextSecondaryDark = Color(0xFF999999)
    val BubbleMine        = Color(0xFF1259C3)
    val BubbleMineDark    = Color(0xFF4D90F0)
    val BubbleOther       = Color(0xFFFFFFFF)
    val BubbleOtherDark   = Color(0xFF2C2C2C)
    val InputBg           = Color(0xFFF0F0F0)
    val InputBgDark       = Color(0xFF333333)
    val TopBar            = Color(0xFFFFFFFF)
    val TopBarDark        = Color(0xFF1E1E1E)
}

// ── Exthru Chat color tokens (Dynamic via MaterialTheme) ─────────────────────

object ExthruChat {
    // ── Акценты ──
    val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    val AccentLight: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val AccentDark: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    val Destructive: Color @Composable get() = MaterialTheme.colorScheme.error
    val Online: Color @Composable get() = Color(0xFF22C55E) // Стандартный зелёный онлайн

    // ── Поверхности ──
    @Composable fun pageBg(isDark: Boolean) = MaterialTheme.colorScheme.background
    @Composable fun barBg(isDark: Boolean) = MaterialTheme.colorScheme.surface
    @Composable fun cardBg(isDark: Boolean) = MaterialTheme.colorScheme.surfaceVariant
    @Composable fun inputBg(isDark: Boolean) = MaterialTheme.colorScheme.surfaceContainerHighest

    // ── Пузыри (Стиль Exthru уже подмешивает Accent в фон) ──
    @Composable fun bubbleMine(isDark: Boolean): Color {
        val style = rememberExthruStyle(AppTheme.BIOLUME)
        return style.myBubbleBg.copy(alpha = if (isDark) 0.65f else 0.85f)
    }

    @Composable fun bubbleOther(isDark: Boolean): Color {
        val style = rememberExthruStyle(AppTheme.BIOLUME)
        return style.otherBubbleBg.copy(alpha = if (isDark) 0.65f else 0.85f)
    }

    // ── Текст ──
    @Composable fun textPrimary(isDark: Boolean) = MaterialTheme.colorScheme.onSurface
    @Composable fun textSecondary(isDark: Boolean) = MaterialTheme.colorScheme.onSurfaceVariant
    @Composable fun textHint(isDark: Boolean) = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    // ── Тени ──
    @Composable fun shadowDark(isDark: Boolean): Color {
        val style = rememberExthruStyle(AppTheme.BIOLUME)
        return style.darkShadow
    }

    @Composable fun shadowLight(isDark: Boolean): Color {
        val style = rememberExthruStyle(AppTheme.BIOLUME)
        return style.lightShadow
    }
}

internal val QUICK_REACTIONS = listOf(
    "👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏",
    "🥰", "😍", "🤩", "😭", "🤔", "👀", "💯", "✅",
    "🙏", "😎", "🤣", "😅", "😡", "💀", "🎊", "⚡"
)