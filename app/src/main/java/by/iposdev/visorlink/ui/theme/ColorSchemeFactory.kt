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
 * tertiary и сильно подмешиваем акцент в фон/поверхность через lerp —
 * это позволяет перебить базовые изумрудные тона темы Biolume, если
 * пользователь выбрал, например, красный (Crimson) или фиолетовый (Purple).
 */
fun ColorScheme.withColorPreset(appTheme: AppTheme, isDark: Boolean, preset: ColorPreset): ColorScheme {
    val seed = preset.seedColor ?: return this
    val onSeed = if (seed.luminance() > 0.5f) Color.Black else Color.White

    // Повышенный уровень "вмешивания" (tint) акцентного цвета в фон.
    // Для темных тем Biolume нужно больше акцента, чтобы перебить родной темно-зеленый.
    val bgTintAlpha = when (appTheme) {
        AppTheme.FORGE, AppTheme.FORGE_TERMINAL -> 0.12f
        AppTheme.BIOLUME, AppTheme.EXTHRU -> if (isDark) 0.28f else 0.18f
        else -> 0.08f
    }

    // Поля ввода (surfaceVariant) и контейнеры выделяем еще сильнее,
    // чтобы они явно брали на себя выбранный оттенок
    val variantTintAlpha = bgTintAlpha + 0.12f

    val tintedBackground = lerp(background, seed, bgTintAlpha)
    val tintedSurface = lerp(surface, seed, bgTintAlpha)
    val tintedSurfaceVariant = lerp(surfaceVariant, seed, variantTintAlpha)
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
        onSurface = onSurface,

        // ВАЖНО: Именно из-за отсутствия тонирования этого цвета поля ввода
        // оставались зелеными. Теперь мы перекрашиваем и их!
        surfaceVariant = tintedSurfaceVariant,
        onSurfaceVariant = onSurfaceVariant,

        surfaceContainerLowest = lerp(surfaceContainerLowest, seed, bgTintAlpha),
        surfaceContainerLow = lerp(surfaceContainerLow, seed, bgTintAlpha),
        surfaceContainer = tintedContainer,
        surfaceContainerHigh = lerp(surfaceContainerHigh, seed, variantTintAlpha),
        surfaceContainerHighest = lerp(surfaceContainerHighest, seed, variantTintAlpha),

        inversePrimary = seed,
    )
}