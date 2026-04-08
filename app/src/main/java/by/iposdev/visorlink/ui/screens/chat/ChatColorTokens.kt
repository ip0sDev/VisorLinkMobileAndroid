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
// Все токены адаптированы под Light (Daylight) и Dark (Midnight).
// Используйте ExthruChat.tokens(isDark) для получения нужного набора.

internal object ExthruChat {
    // ── Акценты (универсальны, одинаковы в обеих темах) ──────────────────────
    val Accent      = Biolume.CyanGlow          // #2DA89A
    val AccentLight = Biolume.TealLight          // #3DBFB0
    val AccentDark  = Biolume.TealPulse          // #1D8B7E
    val Destructive = Biolume.PinkFlash          // #C0392B
    val Online      = Biolume.MangoGlow          // #27AE60

    // ── Light (Daylight) поверхности ─────────────────────────────────────────
    val PageBg    = Biolume.MidWater             // #D4E8E6 — основной фон экрана
    val BarBg     = Biolume.DeepWater            // #CCE2E0 — топ-бар и боттом-бар
    val CardBg    = Biolume.ShallowWater         // #DDF0EE — карточки
    val InputBg   = Biolume.AbyssSurface         // #C0DADA — поле ввода (inset)

    // ── Dark (Midnight) поверхности ──────────────────────────────────────────
    val DarkPageBg  = Biolume.DarkMidWater       // #152E2D — основной фон экрана
    val DarkBarBg   = Biolume.DarkDeepWater      // #102423 — топ-бар и боттом-бар
    val DarkCardBg  = Biolume.DarkShallowWater   // #1B3B3A — карточки
    val DarkInputBg = Biolume.DarkAbyssSurface   // #0B1A1A — поле ввода (inset)

    // ── Light пузыри ─────────────────────────────────────────────────────────
    val BubbleMine  = Biolume.BubbleMine         // #C2DBD8
    val BubbleOther = Biolume.BubbleOther        // #CDE4E2

    // ── Dark пузыри ──────────────────────────────────────────────────────────
    val DarkBubbleMine  = Biolume.DarkBubbleMine  // #1C403E
    val DarkBubbleOther = Biolume.DarkBubbleOther // #15302F

    // ── Light текст ──────────────────────────────────────────────────────────
    val TextPrimary   = Biolume.TextPrimary       // #1A4040
    val TextSecondary = Biolume.TextSecondary     // #2D6060
    val TextHint      = Biolume.TextHint          // #7AACAA

    // ── Dark текст ───────────────────────────────────────────────────────────
    val DarkTextPrimary   = Biolume.DarkTextPrimary    // #E8F5F3
    val DarkTextSecondary = Biolume.DarkTextSecondary  // #9CBDBA
    val DarkTextHint      = Biolume.DarkTextHint       // #5A8582

    // ── Light тени ───────────────────────────────────────────────────────────
    val ShadowDark  = Biolume.ShadowDark          // #5C8C88
    val ShadowLight = Biolume.ShadowLight         // #FFFFFF

    // ── Dark тени ────────────────────────────────────────────────────────────
    val DarkShadowDark  = Biolume.DarkShadowDark  // #000000
    val DarkShadowLight = Biolume.DarkShadowLight // #FFFFFF

    // ── Хелперы для получения адаптивного цвета ───────────────────────────────

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

// ── Quick reactions list ──────────────────────────────────────────────────────

internal val QUICK_REACTIONS = listOf(
    "👍", "❤️", "😂", "😮", "😢", "🔥", "🎉", "👏",
    "🥰", "😍", "🤩", "😭", "🤔", "👀", "💯", "✅",
    "🙏", "😎", "🤣", "😅", "😡", "💀", "🎊", "⚡"
)