package by.iposdev.visorlink.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── Biolume Neomorphic Teal — Surface Levels ─────────────────────────────────

object Biolume {
    // ── LIGHT (Daylight) ──
    val Abyss         = Color(0xFFB8D4D2)
    val AbyssSurface  = Color(0xFFC0DADA)
    val DeepWater     = Color(0xFFCCE2E0)
    val MidWater      = Color(0xFFD4E8E6)
    val ShallowWater  = Color(0xFFDDF0EE)
    val LightSurface  = Color(0xFFE8F5F3)

    val ShadowDark    = Color(0xFF5C8C88)
    val ShadowLight   = Color(0xFFFFFFFF)

    val TextPrimary   = Color(0xFF1A4040)
    val TextSecondary = Color(0xFF2D6060)
    val TextHint      = Color(0xFF7AACAA)

    val BubbleMine    = Color(0xFFC2DBD8)
    val BubbleOther   = Color(0xFFCDE4E2)

    // ── DARK (Midnight) ──
    val DarkAbyss         = Color(0xFF071212)
    val DarkAbyssSurface  = Color(0xFF0B1A1A)
    val DarkDeepWater     = Color(0xFF102423)
    val DarkMidWater      = Color(0xFF152E2D)
    val DarkShallowWater  = Color(0xFF1B3B3A)
    val DarkLightSurface  = Color(0xFF224A49)

    val DarkShadowDark    = Color(0xFF000000)
    val DarkShadowLight   = Color(0xFFFFFFFF)

    val DarkTextPrimary   = Color(0xFFE8F5F3)
    val DarkTextSecondary = Color(0xFF9CBDBA)
    val DarkTextHint      = Color(0xFF5A8582)

    val DarkBubbleMine    = Color(0xFF1C403E)
    val DarkBubbleOther   = Color(0xFF15302F)

    // ── УНИВЕРСАЛЬНЫЕ АКЦЕНТЫ ──
    val CyanGlow      = Color(0xFF2DA89A)
    val TealLight     = Color(0xFF3DBFB0)
    val TealPulse     = Color(0xFF1D8B7E)
    val MangoGlow     = Color(0xFF27AE60)
    val PinkFlash     = Color(0xFFC0392B)
}

val ExthruLightColorScheme = lightColorScheme(
    primary              = Biolume.CyanGlow,
    onPrimary            = Color.White,
    primaryContainer     = Biolume.LightSurface,
    onPrimaryContainer   = Biolume.TextPrimary,

    secondary            = Biolume.TealLight,
    onSecondary          = Color.White,
    secondaryContainer   = Biolume.ShallowWater,
    onSecondaryContainer = Biolume.TextPrimary,

    tertiary             = Biolume.MangoGlow,
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFDFF5E8),
    onTertiaryContainer  = Color(0xFF0D3020),

    error                = Biolume.PinkFlash,
    onError              = Color.White,
    errorContainer       = Color(0xFFFFDDD8),
    onErrorContainer     = Color(0xFF3A0A06),

    background           = Biolume.MidWater,
    onBackground         = Biolume.TextPrimary,

    surface              = Biolume.DeepWater,
    onSurface            = Biolume.TextPrimary,

    surfaceVariant       = Biolume.ShallowWater,
    onSurfaceVariant     = Biolume.TextSecondary,

    outline              = Biolume.ShadowDark.copy(alpha = 0.35f),
    outlineVariant       = Biolume.ShadowDark.copy(alpha = 0.18f),

    surfaceContainerLowest  = Biolume.Abyss,
    surfaceContainerLow     = Biolume.AbyssSurface,
    surfaceContainer        = Biolume.DeepWater,
    surfaceContainerHigh    = Biolume.MidWater,
    surfaceContainerHighest = Biolume.ShallowWater,

    inverseSurface         = Biolume.TextPrimary,
    inverseOnSurface       = Biolume.LightSurface,
    inversePrimary         = Biolume.TealLight,

    scrim                  = Biolume.TextPrimary.copy(alpha = 0.3f),
)

// ── Forge — Neo-Brutalist Red — Surface Levels ────────────────────────────────

object Forge {
    // ── LIGHT ──
    val Background    = Color(0xFFDCDFE5)
    val Surface       = Color(0xFFF0F0F0)
    val InputBg       = Color(0xFFD0D0D0)
    val TextPrimary   = Color(0xFF0A0B0D)
    val TextSecondary = Color(0xFF6B7280)

    val BubbleOther   = Color(0xFFE4E4E7)

    // ── DARK ──
    val DarkBackground    = Color(0xFF0D0E12)
    val DarkSurface       = Color(0xFF1A1B22)
    val DarkInputBg       = Color(0xFF0B0C10)
    val DarkTextPrimary   = Color(0xFFF2F2F2)
    val DarkTextSecondary = Color(0xFFA3A7B0)

    val DarkBubbleOther   = Color(0xFF16181D)

    // ── УНИВЕРСАЛЬНЫЙ АКЦЕНТ ──
    val RedLight = Color(0xFFD90025)
    val RedDark  = Color(0xFFE50027)
    val Destructive = Color(0xFF99001A)
}

val ForgeLightColorScheme = lightColorScheme(
    primary              = Forge.RedLight,
    onPrimary            = Color.White,
    primaryContainer     = Forge.Surface,
    onPrimaryContainer   = Forge.TextPrimary,

    secondary            = Forge.RedLight,
    onSecondary          = Color.White,
    secondaryContainer   = Forge.Surface,
    onSecondaryContainer = Forge.TextPrimary,

    tertiary             = Forge.RedLight,
    onTertiary           = Color.White,
    tertiaryContainer    = Forge.Surface,
    onTertiaryContainer  = Forge.TextPrimary,

    error                = Forge.Destructive,
    onError              = Color.White,
    errorContainer       = Color(0xFFFFDDD8),
    onErrorContainer     = Color(0xFF3A0A06),

    background           = Forge.Background,
    onBackground         = Forge.TextPrimary,

    surface              = Forge.Surface,
    onSurface            = Forge.TextPrimary,

    surfaceVariant       = Forge.InputBg,
    onSurfaceVariant     = Forge.TextSecondary,

    outline              = Color.Black.copy(alpha = 0.38f),
    outlineVariant       = Color.Black.copy(alpha = 0.18f),

    surfaceContainerLowest  = Color.White,
    surfaceContainerLow     = Forge.Background,
    surfaceContainer        = Forge.Surface,
    surfaceContainerHigh    = Forge.InputBg,
    surfaceContainerHighest = Color(0xFFC4C4C4),

    inverseSurface         = Forge.TextPrimary,
    inverseOnSurface       = Forge.Surface,
    inversePrimary         = Forge.RedDark,

    scrim                  = Color.Black.copy(alpha = 0.4f),
)

val ForgeDarkColorScheme = darkColorScheme(
    primary              = Forge.RedDark,
    onPrimary            = Color.White,
    primaryContainer     = Forge.DarkSurface,
    onPrimaryContainer   = Forge.DarkTextPrimary,

    secondary            = Forge.RedDark,
    onSecondary          = Color.White,
    secondaryContainer   = Forge.DarkSurface,
    onSecondaryContainer = Forge.DarkTextPrimary,

    tertiary             = Forge.RedDark,
    onTertiary           = Color.White,
    tertiaryContainer    = Forge.DarkSurface,
    onTertiaryContainer  = Forge.DarkTextPrimary,

    error                = Forge.RedDark,
    onError              = Color.White,
    errorContainer       = Color(0xFF521510),
    onErrorContainer     = Color(0xFFFFDDD8),

    background           = Forge.DarkBackground,
    onBackground         = Forge.DarkTextPrimary,

    surface              = Forge.DarkSurface,
    onSurface            = Forge.DarkTextPrimary,

    surfaceVariant       = Forge.DarkInputBg,
    onSurfaceVariant     = Forge.DarkTextSecondary,

    outline              = Color.White.copy(alpha = 0.24f),
    outlineVariant       = Color.White.copy(alpha = 0.12f),

    surfaceContainerLowest  = Color(0xFF000000),
    surfaceContainerLow     = Forge.DarkBackground,
    surfaceContainer        = Forge.DarkSurface,
    surfaceContainerHigh    = Forge.DarkInputBg,
    surfaceContainerHighest = Color(0xFF26272E),

    inverseSurface         = Forge.DarkTextPrimary,
    inverseOnSurface       = Forge.DarkSurface,
    inversePrimary         = Forge.RedLight,

    scrim                  = Color.Black.copy(alpha = 0.55f),
)

val ExthruDarkColorScheme = darkColorScheme(
    primary              = Biolume.CyanGlow,
    onPrimary            = Color.White,
    primaryContainer     = Biolume.DarkLightSurface,
    onPrimaryContainer   = Biolume.DarkTextPrimary,

    secondary            = Biolume.TealLight,
    onSecondary          = Color.White,
    secondaryContainer   = Biolume.DarkShallowWater,
    onSecondaryContainer = Biolume.DarkTextPrimary,

    tertiary             = Biolume.MangoGlow,
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFF143021),
    onTertiaryContainer  = Color(0xFFDFF5E8),

    error                = Biolume.PinkFlash,
    onError              = Color.White,
    errorContainer       = Color(0xFF521510),
    onErrorContainer     = Color(0xFFFFDDD8),

    background           = Biolume.DarkMidWater,
    onBackground         = Biolume.DarkTextPrimary,

    surface              = Biolume.DarkDeepWater,
    onSurface            = Biolume.DarkTextPrimary,

    surfaceVariant       = Biolume.DarkShallowWater,
    onSurfaceVariant     = Biolume.DarkTextSecondary,

    outline              = Biolume.DarkShadowDark.copy(alpha = 0.6f),
    outlineVariant       = Biolume.DarkShadowDark.copy(alpha = 0.3f),

    surfaceContainerLowest  = Biolume.DarkAbyss,
    surfaceContainerLow     = Biolume.DarkAbyssSurface,
    surfaceContainer        = Biolume.DarkDeepWater,
    surfaceContainerHigh    = Biolume.DarkMidWater,
    surfaceContainerHighest = Biolume.DarkShallowWater,

    inverseSurface         = Biolume.DarkTextPrimary,
    inverseOnSurface       = Biolume.DarkLightSurface,
    inversePrimary         = Biolume.TealLight,

    scrim                  = Color.Black.copy(alpha = 0.5f),
)

// ── Алиасы под новые имена темы (BIOLUME) — сама схема не менялась ───────────
val BiolumeLightColorScheme = ExthruLightColorScheme
val BiolumeDarkColorScheme  = ExthruDarkColorScheme