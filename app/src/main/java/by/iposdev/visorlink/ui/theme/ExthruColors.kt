package by.iposdev.visorlink.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ── Biolume Neomorphic Teal — Surface Levels ─────────────────────────────────

object Biolume {
    // ── LIGHT (Daylight) ──
    // Сделано намного светлее, чтобы белый был основным цветом
    val Abyss         = Color(0xFFE2EFED)
    val AbyssSurface  = Color(0xFFEBF5F3)
    val DeepWater     = Color(0xFFF2F9F8)
    val MidWater      = Color(0xFFF7FBFB)
    val ShallowWater  = Color(0xFFFBFEFE)
    val LightSurface  = Color(0xFFFFFFFF)

    val ShadowDark    = Color(0xFF94B1AF)
    val ShadowLight   = Color(0xFFFFFFFF)

    val TextPrimary   = Color(0xFF0F2B2B)
    val TextSecondary = Color(0xFF3D6363)
    val TextHint      = Color(0xFF8DB6B4)

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

    // ── УНИВЕРСАЛЬНЫЕ АКЦЕНТЫ (Premium Biolume) ──
    val CyanGlow      = Color(0xFF4FD1C5) // Soft Mint/Cyan
    val TealLight     = Color(0xFF63B3ED) // Soft Azure
    val TealPulse     = Color(0xFF3182CE) // Muted Navy
    val MangoGlow     = Color(0xFFF687B3) // Soft Rose
    val PinkFlash     = Color(0xFFB794F4) // Soft Lavender
    
    val IridescentStart = Color(0xFF4FD1C5).copy(alpha = 0.4f)
    val IridescentMid   = Color(0xFF63B3ED).copy(alpha = 0.2f)
    val IridescentEnd   = Color(0xFFB794F4).copy(alpha = 0.4f)
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
    // ── LIGHT (Industrial Silver) ──
    val Background    = Color(0xFF8A8D91) // Battleship Gray
    val Surface       = Color(0xFFC0C0C0) // Classic 90s Silver
    val InputBg       = Color(0xFFA0A4A8)
    val TextPrimary   = Color(0xFF0F1115)
    val TextSecondary = Color(0xFF4A4D52)

    val BubbleOther   = Color(0xFFD1D5DB)

    // ── DARK (Gunmetal Terminal) ──
    val DarkBackground    = Color(0xFF0A0A0C) // Deepest charcoal
    val DarkSurface       = Color(0xFF16161B) // Gunmetal steel
    val DarkInputBg       = Color(0xFF0F0F12)
    val DarkTextPrimary   = Color(0xFFE0E0E5) // Slightly cool white
    val DarkTextSecondary = Color(0xFF8E9299)

    val DarkBubbleOther   = Color(0xFF1C1C24)

    // ── УНИВЕРСАЛЬНЫЙ АКЦЕНТ (Industrial Red) ──
    val RedLight = Color(0xFFCC0000) // Solid warning red
    val RedDark  = Color(0xFFFF1A1A) // Glowing CRT red
    val Destructive = Color(0xFF8B0000)
}

val ForgeLightColorScheme = lightColorScheme(
    primary              = Forge.RedLight,
    onPrimary            = Color.White,
    primaryContainer     = Forge.Surface,
    onPrimaryContainer   = Forge.RedLight,

    secondary            = Forge.TextSecondary,
    onSecondary          = Color.White,
    secondaryContainer   = Forge.InputBg,
    onSecondaryContainer = Forge.TextPrimary,

    tertiary             = Forge.RedLight,
    onTertiary           = Color.White,
    tertiaryContainer    = Forge.Surface,
    onTertiaryContainer  = Forge.TextPrimary,

    error                = Forge.Destructive,
    onError              = Color.White,
    errorContainer       = Color(0xFFFFDAD6),
    onErrorContainer     = Color(0xFF410002),

    background           = Forge.Background,
    onBackground         = Forge.TextPrimary,

    surface              = Forge.Surface,
    onSurface            = Forge.TextPrimary,

    surfaceVariant       = Forge.InputBg,
    onSurfaceVariant     = Forge.TextSecondary,

    outline              = Forge.TextSecondary.copy(alpha = 0.5f),
    outlineVariant       = Forge.TextSecondary.copy(alpha = 0.2f),

    surfaceContainerLowest  = Color(0xFFE5E7EB),
    surfaceContainerLow     = Forge.Background,
    surfaceContainer        = Forge.Surface,
    surfaceContainerHigh    = Color(0xFFA1A1AA),
    surfaceContainerHighest = Color(0xFF71717A),

    inverseSurface         = Forge.TextPrimary,
    inverseOnSurface       = Forge.Surface,
    inversePrimary         = Forge.RedDark,

    scrim                  = Color.Black.copy(alpha = 0.4f),
)

val ForgeDarkColorScheme = darkColorScheme(
    primary              = Forge.RedDark,
    onPrimary            = Color.Black,
    primaryContainer     = Color(0xFF400000),
    onPrimaryContainer   = Forge.RedDark,

    secondary            = Forge.DarkTextSecondary,
    onSecondary          = Color.Black,
    secondaryContainer   = Forge.DarkInputBg,
    onSecondaryContainer = Forge.DarkTextPrimary,

    tertiary             = Forge.RedDark,
    onTertiary           = Color.Black,
    tertiaryContainer    = Forge.DarkSurface,
    onTertiaryContainer  = Forge.DarkTextPrimary,

    error                = Forge.RedDark,
    onError              = Color.White,
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),

    background           = Forge.DarkBackground,
    onBackground         = Forge.DarkTextPrimary,

    surface              = Forge.DarkSurface,
    onSurface            = Forge.DarkTextPrimary,

    surfaceVariant       = Forge.DarkInputBg,
    onSurfaceVariant     = Forge.DarkTextSecondary,

    outline              = Color(0xFF404040),
    outlineVariant       = Color(0xFF303030),

    surfaceContainerLowest  = Color(0xFF000000),
    surfaceContainerLow     = Forge.DarkBackground,
    surfaceContainer        = Forge.DarkSurface,
    surfaceContainerHigh    = Color(0xFF1E1E24),
    surfaceContainerHighest = Color(0xFF2D2D35),

    inverseSurface         = Forge.DarkTextPrimary,
    inverseOnSurface       = Forge.DarkSurface,
    inversePrimary         = Forge.RedLight,

    scrim                  = Color.Black.copy(alpha = 0.7f),
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