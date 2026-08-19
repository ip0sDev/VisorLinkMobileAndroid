package by.iposdev.visorlink.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ColorPreset

/**
 * Customizes accent colors for any theme.
 */
fun ColorScheme.withColorPreset(appTheme: AppTheme, isDark: Boolean, preset: ColorPreset): ColorScheme {
    // Industrial is fixed to Arasaka Red
    if (appTheme == AppTheme.FORGE_INDUSTRIAL || appTheme == AppTheme.FORGE) {
        return this
    }

    val seed = preset.seedColor ?: return this
    val onSeed = if (seed.luminance() > 0.5f) Color.Black else Color.White

    val bgTintAlpha = when (appTheme) {
        AppTheme.FORGE_TERMINAL, AppTheme.FORGE_COMICS -> 0.12f
        AppTheme.BIOLUME, AppTheme.EXTHRU -> if (isDark) 0.28f else 0.18f
        else -> 0.08f
    }

    val variantTintAlpha = bgTintAlpha + 0.12f

    return copy(
        primary = seed,
        onPrimary = onSeed,
        primaryContainer = lerp(seed, if (isDark) Color.Black else Color.White, if (isDark) 0.6f else 0.82f),
        onPrimaryContainer = if (isDark) lerp(seed, Color.White, 0.7f) else lerp(seed, Color.Black, 0.55f),
        secondary = seed,
        onSecondary = onSeed,
        tertiary = seed,
        onTertiary = onSeed,
        background = lerp(background, seed, bgTintAlpha),
        surface = lerp(surface, seed, bgTintAlpha),
        surfaceVariant = lerp(surfaceVariant, seed, variantTintAlpha),
        surfaceContainerLowest = lerp(surfaceContainerLowest, seed, bgTintAlpha),
        surfaceContainerLow = lerp(surfaceContainerLow, seed, bgTintAlpha),
        surfaceContainer = lerp(surfaceContainer, seed, bgTintAlpha),
        surfaceContainerHigh = lerp(surfaceContainerHigh, seed, variantTintAlpha),
        surfaceContainerHighest = lerp(surfaceContainerHighest, seed, variantTintAlpha),
        inversePrimary = seed,
    )
}

/**
 * Windows 95 Authentic Color Scheme
 */
fun forgeTerminalColorScheme(isDark: Boolean): ColorScheme {
    return if (isDark) ForgeTerminalDarkColorScheme else ForgeTerminalLightColorScheme
}

/**
 * Comic Book Color Scheme
 */
fun forgeComicsColorScheme(isDark: Boolean): ColorScheme {
    val paper = if (isDark) ForgeComics.Noir else ForgeComics.Newsprint
    val palette = ForgeComics.Palettes.Red // Default to Red for now

    return if (isDark) darkColorScheme(
        primary = palette.accent,
        onPrimary = paper.Paper,
        primaryContainer = paper.Panel,
        onPrimaryContainer = paper.InkPrimary,
        secondary = palette.accentDark,
        onSecondary = paper.Paper,
        secondaryContainer = paper.PanelRaised,
        onSecondaryContainer = paper.InkPrimary,
        tertiary = palette.energy,
        onTertiary = paper.Paper,
        background = paper.Paper,
        onBackground = paper.InkPrimary,
        surface = paper.Panel,
        onSurface = paper.InkPrimary,
        surfaceVariant = paper.PanelRaised,
        onSurfaceVariant = paper.InkSecondary,
        outline = paper.Stroke,
        outlineVariant = paper.Stroke.copy(alpha = 0.5f),
        surfaceContainerLowest = paper.Paper,
        surfaceContainerLow = paper.Panel,
        surfaceContainer = paper.PanelRaised,
        surfaceContainerHigh = paper.InkSecondary,
        surfaceContainerHighest = paper.InkPrimary,
    ) else lightColorScheme(
        primary = palette.accentDark,
        onPrimary = Color.White,
        primaryContainer = paper.Panel,
        onPrimaryContainer = paper.InkPrimary,
        secondary = palette.accent,
        onSecondary = Color.White,
        background = paper.Paper,
        onBackground = paper.InkPrimary,
        surface = paper.Panel,
        onSurface = paper.InkPrimary,
        surfaceVariant = paper.PanelRaised,
        onSurfaceVariant = paper.InkSecondary,
        outline = paper.Stroke,
        outlineVariant = paper.Stroke.copy(alpha = 0.5f),
        surfaceContainerLowest = paper.Paper,
        surfaceContainerLow = paper.Panel,
        surfaceContainer = paper.PanelRaised,
        surfaceContainerHigh = paper.InkSecondary,
        surfaceContainerHighest = paper.InkPrimary,
    )
}
