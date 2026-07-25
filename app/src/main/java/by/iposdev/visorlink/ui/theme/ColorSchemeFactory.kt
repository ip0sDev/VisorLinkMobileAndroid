package by.iposdev.visorlink.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ColorPreset

/**
 * Кастомизация акцентного цвета поверх любой из 3 тем — порт логики
 * ColorPreset из lib/theme/app_theme.dart (`_buildTheme`).
 *
 * В отличие от Flutter (который перестраивает всю тональную палитру через
 * `ColorScheme.fromSeed`), здесь мы точечно перекрашиваем primary/secondary/
 * tertiary и слегка подмешиваем акцент в фон/поверхность через lerp —
 * это даёт тот же визуальный эффект без завязки на Material Color Utilities.
 *
 * Если [preset] == DEFAULT — схема возвращается как есть (родной акцент темы
 * или динамический Material You цвет на Android 12+).
 */
fun ColorScheme.withColorPreset(appTheme: AppTheme, isDark: Boolean, preset: ColorPreset): ColorScheme {
    val seed = preset.seedColor ?: return this
    val onSeed = if (seed.luminance() > 0.5f) Color.Black else Color.White

    val bgTintAlpha = when (appTheme) {
        AppTheme.FORGE -> 0.05f
        AppTheme.BIOLUME, AppTheme.EXTHRU -> if (isDark) 0.08f else 0.04f
        else -> 0.03f
    }

    val tintedBackground = lerp(background, seed, bgTintAlpha)
    val tintedSurface = lerp(surface, seed, bgTintAlpha)
    val tintedContainer = lerp(surfaceContainer, seed, bgTintAlpha)

    return copy(
        primary = seed,
        onPrimary = onSeed,
        primaryContainer = lerp(seed, if (isDark) Color.Black else Color.White, if (isDark) 0.6f else 0.82f),
        onPrimaryContainer = if (isDark) lerp(seed, Color.White, 0.7f) else lerp(seed, Color.Black, 0.55f),
        secondary = seed,
        onSecondary = onSeed,
        tertiary = seed,
        onTertiary = onSeed,
        background = tintedBackground,
        onBackground = onBackground,
        surface = tintedSurface,
        surfaceContainer = tintedContainer,
        surfaceContainerLow = lerp(surfaceContainerLow, seed, bgTintAlpha),
        surfaceContainerHigh = lerp(surfaceContainerHigh, seed, bgTintAlpha),
        surfaceContainerHighest = lerp(surfaceContainerHighest, seed, bgTintAlpha),
        inversePrimary = seed,
    )
}