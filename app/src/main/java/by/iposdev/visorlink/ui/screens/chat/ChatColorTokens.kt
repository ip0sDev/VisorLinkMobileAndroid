package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.ui.graphics.Color
import by.iposdev.visorlink.ui.theme.Biolume

// ── One UI Chat color tokens ───────────────────────────────────────────────────

internal object OneUiChat {
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

// ── Exthru Chat color tokens ──────────────────────────────────────────────────

internal object ExthruChat {
    // ── Акценты ──
    val Accent      = Biolume.CyanGlow
    val AccentLight = Biolume.TealLight
    val AccentDark  = Biolume.TealPulse
    val Destructive = Biolume.PinkFlash
    val Online      = Biolume.MangoGlow

    // ── Light (Daylight) поверхности ──
    val PageBg    = Biolume.MidWater
    val BarBg     = Biolume.DeepWater
    val CardBg    = Biolume.ShallowWater
    val InputBg   = Biolume.AbyssSurface

    // ── Dark (Midnight) поверхности ──
    val DarkPageBg  = Biolume.DarkMidWater
    val DarkBarBg   = Biolume.DarkDeepWater
    val DarkCardBg  = Biolume.DarkShallowWater
    val DarkInputBg = Biolume.DarkAbyssSurface

    // ── Light пузыри (сделаны чуть более прозрачными) ──
    val BubbleMine  = Biolume.BubbleMine.copy(alpha = 0.85f)
    val BubbleOther = Biolume.BubbleOther.copy(alpha = 0.85f)

    // ── Dark пузыри (сделаны чуть более прозрачными) ──
    val DarkBubbleMine  = Biolume.DarkBubbleMine.copy(alpha = 0.65f)
    val DarkBubbleOther = Biolume.DarkBubbleOther.copy(alpha = 0.65f)

    // ── Light текст ──
    val TextPrimary   = Biolume.TextPrimary
    val TextSecondary = Biolume.TextSecondary
    val TextHint      = Biolume.TextHint

    // ── Dark текст ──
    val DarkTextPrimary   = Biolume.DarkTextPrimary
    val DarkTextSecondary = Biolume.DarkTextSecondary
    val DarkTextHint      = Biolume.DarkTextHint

    // ── Тени ──
    val ShadowDark  = Biolume.ShadowDark
    val ShadowLight = Biolume.ShadowLight
    val DarkShadowDark  = Biolume.DarkShadowDark
    val DarkShadowLight = Biolume.DarkShadowLight

    // ── Хелперы ──
    fun pageBg(isDark: Boolean)        = if (isDark) DarkPageBg  else PageBg
    fun barBg(isDark: Boolean)         = if (isDark) DarkBarBg   else BarBg
    fun cardBg(isDark: Boolean)        = if (isDark) DarkCardBg  else CardBg
    fun inputBg(isDark: Boolean)       = if (isDark) DarkInputBg else InputBg

    fun bubbleMine(isDark: Boolean)    = if (isDark) DarkBubbleMine  else BubbleMine
    fun bubbleOther(isDark: Boolean)   = if (isDark) DarkBubbleOther else BubbleOther

    fun textPrimary(isDark: Boolean)   = if (isDark) DarkTextPrimary   else TextPrimary
    fun textSecondary(isDark: Boolean) = if (isDark) DarkTextSecondary else TextSecondary
    fun textHint(isDark: Boolean)      = if (isDark) DarkTextHint      else TextHint

    fun shadowDark(isDark: Boolean)    = if (isDark) DarkShadowDark  else ShadowDark
    fun shadowLight(isDark: Boolean)   = if (isDark) DarkShadowLight else ShadowLight
}

internal val QUICK_REACTIONS = listOf(
    "👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏",
    "🥰", "😍", "🤩", "😭", "🤔", "👀", "💯", "✅",
    "🙏", "😎", "🤣", "😅", "😡", "💀", "🎊", "⚡"
)