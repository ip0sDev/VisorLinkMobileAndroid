package org.visorlink.app.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import org.visorlink.app.BuildConfig
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ColorPreset
import org.visorlink.app.data.model.ProfileAppearance
import org.visorlink.app.data.model.ThemeMode
import org.visorlink.app.data.model.UserProfile
import org.koin.compose.viewmodel.koinViewModel
import java.util.concurrent.atomic.AtomicInteger

// ── M3 Expressive ─────────────────────────────────────────────────────────────

internal val LightM3 = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF2E1065),
    tertiary = Color(0xFFDB2777),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCE7F3),
    onTertiaryContainer = Color(0xFF831843),
    background = Color(0xFFFAFAFF),
    onBackground = Color(0xFF0F0A1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B2E),
    surfaceVariant = Color(0xFFEEF0FF),
    onSurfaceVariant = Color(0xFF4A4660),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F4FF),
    surfaceContainer = Color(0xFFEFEEFF),
    surfaceContainerHigh = Color(0xFFE9E8FA),
    surfaceContainerHighest = Color(0xFFE4E3F5),
)

internal val DarkM3 = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = Color(0xFF4C1D95),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = Color(0xFFF472B6),
    onTertiary = Color(0xFF831843),
    tertiaryContainer = Color(0xFF9D174D),
    onTertiaryContainer = Color(0xFFFCE7F3),
    background = Color(0xFF0F0E17),
    onBackground = Color(0xFFE8E4FF),
    surface = Color(0xFF1A1825),
    onSurface = Color(0xFFE0DCF5),
    surfaceVariant = Color(0xFF252336),
    onSurfaceVariant = Color(0xFFB0ACCC),
    outline = Color(0xFF6E6A84),
    outlineVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFF0B0A14),
    surfaceContainerLow = Color(0xFF1C1B2E),
    surfaceContainer = Color(0xFF201F32),
    surfaceContainerHigh = Color(0xFF2B293C),
    surfaceContainerHighest = Color(0xFF353347),
)

private val Material3ShapeScale = Shapes(medium = RoundedCornerShape(16.dp))

/** Сетка 4dp (§8): 8 · 16 · 24 · 28. */
private val BiolumeShapeScale = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Forge: ни одного скругления, включая M3-компоненты со своей шкалой форм.
 * `Shapes` принимает только `CornerBasedShape`, поэтому здесь нулевой радиус,
 * а не `RectangleShape`.
 */
private val ForgeShapeScale = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

// ── User Profile Theme Wrapper ─────────────────────────────────────────────

/**
 * Оформление владельца [profile], применяемое при просмотре его профиля или чата.
 *
 * Что показывать, решает [ProfileAppearance.resolve]: своё — всегда, чужое —
 * только при активном PRO владельца и если зритель не отключил чужое оформление.
 * Всё незаданное берётся из глобальных настроек зрителя.
 */
@Composable
fun UserProfileTheme(
    profile: UserProfile?,
    currentUser: UserProfile?,
    content: @Composable () -> Unit
) {
    ProfileAppearanceTheme(
        appearance = ProfileAppearance.resolve(owner = profile, viewer = currentUser),
        content = content,
    )
}

/**
 * Применяет [appearance] поверх настроек зрителя из [ThemeViewModel].
 * Режим светлая/тёмная всегда зрителя: оформление профиля его не меняет.
 */
@Composable
fun ProfileAppearanceTheme(
    appearance: ProfileAppearance,
    content: @Composable () -> Unit,
) {
    val themeVm: ThemeViewModel = koinViewModel()
    val viewerTheme by themeVm.appTheme.collectAsState()
    val viewerMode by themeVm.themeMode.collectAsState()
    val viewerPreset by themeVm.colorPreset.collectAsState()
    val showDebugIds by themeVm.showDebugIds.collectAsState()

    ProfileAppearanceTheme(
        appearance = appearance,
        viewerTheme = viewerTheme,
        viewerMode = viewerMode,
        viewerPreset = viewerPreset,
        showDebugIds = showDebugIds,
        content = content,
    )
}

/** Чистая версия без ViewModel — для превью и скриншот-тестов. */
@Composable
fun ProfileAppearanceTheme(
    appearance: ProfileAppearance,
    viewerTheme: AppTheme,
    viewerMode: ThemeMode,
    viewerPreset: ColorPreset,
    showDebugIds: Boolean = LocalShowDebugIds.current,
    content: @Composable () -> Unit,
) {
    val theme = appearance.theme ?: viewerTheme
    VisorLinkTheme(
        appTheme = theme,
        themeMode = viewerMode,
        colorPreset = appearance.accent ?: viewerPreset,
        showDebugIds = showDebugIds,
        // Статус-бар остаётся за внешней темой: режим светлая/тёмная тот же,
        // а при уходе с экрана внешняя тема не перезапустила бы свой SideEffect
        setStatusBarColor = false,
        typographyOverride = appearance.font?.let { baseTypography(theme).withProfileFont(it) },
        content = content,
    )
}

// ── VisorLink Theme Composable ────────────────────────────────────────────────

val LocalShowDebugIds = compositionLocalOf { false }

/**
 * Единственная точка, где решается «как выглядит приложение».
 *
 * Помимо [MaterialTheme] раздаёт [LocalVlTokens] — расширение токенов, которого в
 * M3 нет (неоморфный рельеф, сигнальное свечение, success/warning, data-роли).
 * Благодаря этому компоненты в `ui/components` сами подстраиваются под тему, а
 * экраны остаются тема-независимыми и НЕ получают `appTheme` параметром.
 */
@Composable
fun VisorLinkTheme(
    appTheme: AppTheme = AppTheme.MATERIAL3_EXPRESSIVE,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorPreset: ColorPreset = ColorPreset.DEFAULT,
    showDebugIds: Boolean = LocalShowDebugIds.current,
    setStatusBarColor: Boolean = true,
    /** Подмена гарнитур для PRO-кастомизации; шкала кеглей при этом сохраняется. */
    typographyOverride: androidx.compose.material3.Typography? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val context = LocalContext.current

    // Системная настройка «убрать анимации» (§6, §8): читаем один раз и раздаём
    // вниз через токены, чтобы каждый компонент не лез в Settings сам.
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    val colorScheme = when (appTheme) {
        AppTheme.BIOLUME -> {
            val base = if (darkTheme) AbyssColorScheme else TidepoolColorScheme
            base.withSignalAccent(colorPreset.seedColor, darkTheme)
        }

        AppTheme.FORGE -> {
            val base = if (darkTheme) ForgeSteelColorScheme else ForgeConcreteColorScheme
            base.withSignalAccent(colorPreset.seedColor, darkTheme)
        }

        AppTheme.MATERIAL3_EXPRESSIVE -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colorPreset == ColorPreset.DEFAULT ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> (if (darkTheme) DarkM3 else LightM3).withMaterialAccent(colorPreset.seedColor, darkTheme)
        }
    }

    val tokens = remember(appTheme, darkTheme, reduceMotion, colorScheme) {
        when (appTheme) {
            AppTheme.BIOLUME -> VlTokens(
                style = VlStyle.BIOLUME,
                isDark = darkTheme,
                structure = biolumeStructure(darkTheme),
                signal = biolumeSignal(darkTheme),
                shapes = BiolumeShapes,
                motion = BiolumeMotion,
                status = biolumeStatus(darkTheme),
                selectionFill = biolumeSelectionFill(darkTheme, colorScheme.primary),
                bubbles = biolumeBubbles(darkTheme, colorScheme.primary),
                data = BiolumeDataTypography,
                reduceMotion = reduceMotion,
            )

            AppTheme.FORGE -> VlTokens(
                style = VlStyle.FORGE,
                isDark = darkTheme,
                structure = forgeStructure(darkTheme),
                signal = forgeSignal(darkTheme),
                shapes = ForgeShapes,
                motion = ForgeMotion,
                status = forgeStatus(darkTheme),
                selectionFill = forgeSelectionFill(darkTheme, colorScheme.primary),
                bubbles = forgeBubbles(darkTheme, colorScheme.primary),
                data = ForgeDataTypography,
                reduceMotion = reduceMotion,
            )

            AppTheme.MATERIAL3_EXPRESSIVE -> VlTokens(
                style = VlStyle.MATERIAL3,
                isDark = darkTheme,
                structure = VlStructureTokens.Disabled,
                signal = VlSignalTokens.Disabled,
                shapes = Material3Shapes,
                motion = Material3Motion,
                status = VlStatusTokens(
                    success = if (darkTheme) Color(0xFF7BD88F) else Color(0xFF2E7D32),
                    onSuccess = if (darkTheme) Color(0xFF0A2E12) else Color.White,
                    warning = if (darkTheme) Color(0xFFFFC24E) else Color(0xFF8F6200),
                    onWarning = if (darkTheme) Color(0xFF2B1B00) else Color.White,
                ),
                // M3E: непрозрачный secondaryContainer — штатный цвет active
                // indicator в M3 Navigation Bar, ничего изобретать не нужно.
                selectionFill = colorScheme.secondaryContainer,
                bubbles = VlBubbleTokens(
                    mineBg = colorScheme.primaryContainer,
                    mineFg = colorScheme.onSurface,
                    otherBg = colorScheme.surfaceVariant,
                    otherFg = colorScheme.onSurface,
                ),
                data = Material3DataTypography,
                reduceMotion = reduceMotion,
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode && setStatusBarColor) {
        SideEffect {
            var ctx = view.context
            while (ctx is ContextWrapper) {
                if (ctx is Activity) break
                ctx = ctx.baseContext
            }
            val window = (ctx as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalVlTokens provides tokens,
        LocalSignalCounter provides remember { AtomicInteger(0) },
        LocalShowDebugIds provides showDebugIds
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            // Шкала форм для M3-компонентов, которые берут форму из темы, а не из
            // наших токенов (BottomSheet, Menu, Snackbar и т.п.).
            shapes = when (appTheme) {
                AppTheme.BIOLUME -> BiolumeShapeScale
                AppTheme.FORGE -> ForgeShapeScale
                AppTheme.MATERIAL3_EXPRESSIVE -> Material3ShapeScale
            },
            typography = typographyOverride ?: baseTypography(appTheme),
            content = content
        )
    }
}
