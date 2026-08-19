package by.iposdev.visorlink.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ── Biolume Neomorphic Teal — Surface Levels ─────────────────────────────────

object Biolume {
    // ── LIGHT (Daylight) ──
    val Abyss         = Color(0xFFD5E5E2)
    val AbyssSurface  = Color(0xFFDCEAE8)
    val DeepWater     = Color(0xFFE8F2F0)
    val MidWater      = Color(0xFFEAF4F2)
    val ShallowWater  = Color(0xFFF2F9F8)
    val LightSurface  = Color(0xFFFFFFFF)

    val ShadowDark    = Color(0xFF7B9D9A)
    val ShadowLight   = Color(0xFFFFFFFF)

    val TextPrimary   = Color(0xFF0F2B2B)
    val TextSecondary = Color(0xFF3D6363)

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

    // ── УНИВЕРСАЛЬНЫЕ АКЦЕНТЫ (Premium Biolume) ──
    val CyanGlow      = Color(0xFF4FD1C5)
    val TealLight     = Color(0xFF63B3ED)
    val TealPulse     = Color(0xFF3182CE)
    val MangoGlow     = Color(0xFFF687B3)
    val PinkFlash     = Color(0xFFB794F4)
    
    val IridescentStart = Color(0xFF4FD1C5).copy(alpha = 0.4f)
    val IridescentMid   = Color(0xFF63B3ED).copy(alpha = 0.2f)
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
    outline              = Biolume.ShadowDark.copy(alpha = 0.45f),
    outlineVariant       = Biolume.ShadowDark.copy(alpha = 0.28f),
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

// ── Forge Industrial — Premium Cyberpunk (Arasaka) ──────────────────────────

object ForgeIndustrial {
    val RedAccent     = Color(0xFFFF0033) // Arasaka Red
    val DeepBlack     = Color(0xFF050505)
    val SurfaceCard   = Color(0xFF0A0A0A)
    val SurfaceRaised = Color(0xFF101010)
    val BorderSubtle  = Color(0xFF1A1A1A)
    val TextPure      = Color(0xFFFFFFFF)
    val TextMuted     = Color(0xFF888888)
    val TextDim       = Color(0xFF444444)
}

val ForgeIndustrialDarkColorScheme = darkColorScheme(
    primary              = ForgeIndustrial.RedAccent,
    onPrimary            = Color.White,
    primaryContainer     = ForgeIndustrial.RedAccent.copy(alpha = 0.15f),
    onPrimaryContainer   = ForgeIndustrial.RedAccent,
    secondary            = ForgeIndustrial.TextMuted,
    onSecondary          = Color.White,
    secondaryContainer   = ForgeIndustrial.SurfaceRaised,
    onSecondaryContainer = ForgeIndustrial.TextPure,
    tertiary             = ForgeIndustrial.TextDim,
    onTertiary           = Color.White,
    background           = ForgeIndustrial.DeepBlack,
    onBackground         = ForgeIndustrial.TextPure,
    surface              = ForgeIndustrial.SurfaceCard,
    onSurface            = ForgeIndustrial.TextPure,
    surfaceVariant       = ForgeIndustrial.SurfaceRaised,
    onSurfaceVariant     = ForgeIndustrial.TextMuted,
    outline              = ForgeIndustrial.BorderSubtle,
    outlineVariant       = ForgeIndustrial.BorderSubtle.copy(alpha = 0.5f),
    surfaceContainerLowest  = ForgeIndustrial.DeepBlack,
    surfaceContainerLow     = ForgeIndustrial.DeepBlack,
    surfaceContainer        = ForgeIndustrial.SurfaceCard,
    surfaceContainerHigh    = ForgeIndustrial.SurfaceRaised,
    surfaceContainerHighest = ForgeIndustrial.SurfaceRaised,
    inverseSurface         = ForgeIndustrial.TextPure,
    inverseOnSurface       = ForgeIndustrial.DeepBlack,
    inversePrimary         = ForgeIndustrial.RedAccent,
    scrim                  = Color.Black.copy(alpha = 0.8f),
)

val ForgeIndustrialLightColorScheme = ForgeIndustrialDarkColorScheme

// ── Forge Terminal — Windows 95 Authentic ───────────────────────────────────

object ForgeTerminal {
    val Face         = Color(0xFFC0C0C0)
    val Highlight    = Color(0xFFFFFFFF)
    val Shadow       = Color(0xFF808080)
    val BlackShadow  = Color(0xFF000000)
    val NavyTitle    = Color(0xFF000080)
    val NavyTitleEnd = Color(0xFF1084D0)
    val DesktopTeal  = Color(0xFF008080)

    val DarkFace       = Color(0xFF1A1A1A)
    val DarkHighlight  = Color(0xFF333333)
    val DarkShadowHC   = Color(0xFF000000)
    val DarkTitle      = Color(0xFF003366)
}

val ForgeTerminalLightColorScheme = lightColorScheme(
    primary              = ForgeTerminal.NavyTitle,
    onPrimary            = Color.White,
    primaryContainer     = ForgeTerminal.Face,
    onPrimaryContainer   = Color.Black,
    secondary            = ForgeTerminal.Shadow,
    onSecondary          = Color.White,
    background           = ForgeTerminal.DesktopTeal,
    onBackground         = Color.Black,
    surface              = ForgeTerminal.Face,
    onSurface            = Color.Black,
    surfaceVariant       = ForgeTerminal.Highlight,
    onSurfaceVariant     = Color.Black,
    outline              = ForgeTerminal.Shadow,
    outlineVariant       = ForgeTerminal.BlackShadow,
    surfaceContainerLowest  = Color.White,
    surfaceContainerLow     = ForgeTerminal.Face,
    surfaceContainer        = ForgeTerminal.Face,
    surfaceContainerHigh    = ForgeTerminal.Face,
    surfaceContainerHighest = ForgeTerminal.Highlight,
)

val ForgeTerminalDarkColorScheme = darkColorScheme(
    primary              = Color(0xFF0066CC),
    onPrimary            = Color.White,
    primaryContainer     = ForgeTerminal.DarkFace,
    onPrimaryContainer   = Color.White,
    background           = Color.Black,
    onBackground         = Color.White,
    surface              = ForgeTerminal.DarkFace,
    onSurface            = Color.White,
    outline              = Color(0xFF808080),
    surfaceContainerLowest  = Color.Black,
    surfaceContainerLow     = ForgeTerminal.DarkFace,
    surfaceContainer        = ForgeTerminal.DarkFace,
    surfaceContainerHigh    = ForgeTerminal.DarkFace,
    surfaceContainerHighest = ForgeTerminal.DarkHighlight,
)

// ── Forge Comics — Comic Book (Fixed Noir) ──────────────────────────────────

interface ComicsPaper {
    val Paper: Color
    val Panel: Color
    val PanelRaised: Color
    val InkPrimary: Color
    val InkSecondary: Color
    val Stroke: Color
}

data class ComicPalette(
    val accent: Color,
    val accentDark: Color,
    val accentLight: Color,
    val energy: Color,
    val energyDark: Color
)

object ForgeComics {
    object Noir : ComicsPaper {
        override val Paper        = Color(0xFF0D0D0D)
        override val Panel        = Color(0xFF1A1A1A)
        override val PanelRaised  = Color(0xFF222222)
        override val InkPrimary   = Color(0xFFFFFFFF)
        override val InkSecondary = Color(0xFFCCCCCC)
        override val Stroke       = Color(0xFF1A1A1A)
    }
    object Newsprint : ComicsPaper {
        override val Paper        = Color(0xFFF5F0E8)
        override val Panel        = Color(0xFFFFFFFF)
        override val PanelRaised  = Color(0xFFFAF5EB)
        override val InkPrimary   = Color(0xFF1A1A1A)
        override val InkSecondary = Color(0xFF4A4A4A)
        override val Stroke       = Color(0xFF1A1A1A)
    }

    object Palettes {
        val Red = ComicPalette(
            accent = Color(0xFFFF3333),
            accentDark = Color(0xFFCC0000),
            accentLight = Color(0xFFFF6666),
            energy = Color(0xFFFFCC00),
            energyDark = Color(0xFFCC9900)
        )
        val Blue = ComicPalette(
            accent = Color(0xFF3399FF),
            accentDark = Color(0xFF0066CC),
            accentLight = Color(0xFF66CCFF),
            energy = Color(0xFF00FFCC),
            energyDark = Color(0xFF00CC99)
        )
    }
}

val BiolumeLightColorScheme = ExthruLightColorScheme
val BiolumeDarkColorScheme  = ExthruDarkColorScheme
val ForgeLightColorScheme = ForgeIndustrialLightColorScheme
val ForgeDarkColorScheme  = ForgeIndustrialDarkColorScheme
