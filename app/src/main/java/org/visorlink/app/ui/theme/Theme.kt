package org.visorlink.app.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.idcard.ModeStyle
import org.visorlink.app.data.idcard.ModeTheme
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

/** Forge v2: малые скругления и для M3-компонентов со своей шкалой форм. */
private fun forgeV2ShapeScale(f: ForgeV2.Flavor) = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(f.radiusLg),
    large = RoundedCornerShape(f.radiusLg),
    extraLarge = RoundedCornerShape(f.radius2xl),
)

private fun forgeV2Flavor(theme: AppTheme): ForgeV2.Flavor? = when (theme) {
    AppTheme.FORGE_PROTOGEN -> ForgeV2.Protogen
    AppTheme.FORGE_BEAST -> ForgeV2.Beast
    else -> null
}

// ── Действующая тема зрителя ─────────────────────────────────────────────────

/**
 * Тема, которую зритель видит сейчас: после входа её решает режим ID-карты
 * (Biolume / Forge v2), а не выбор в настройках. Задаёт MainActivity; обёртки профиля
 * и чата накладывают оформление владельца поверх неё, а не поверх настроек.
 *
 * @property modeDriven тему решает режим ID-карты — `customization.theme` владельца не читается.
 * @property proAccent цвет профиля PRO — акцент всего интерфейса ([ProAccent]): в Biolume, пока
 *   на устройстве выбран акцент «По умолчанию», в Forge v2 — всегда (заменяет оттенок режима,
 *   [ForgeV2.Flavor.tintedBy]); `null` — без PRO или без цвета.
 */
@Immutable
data class ViewerTheme(
    val appTheme: AppTheme,
    val themeMode: ThemeMode,
    val colorPreset: ColorPreset,
    val modeDriven: Boolean = false,
    val proAccent: Color? = null,
)

/**
 * Цвет профиля PRO как акцент интерфейса: только при активном PRO — истёкшая подписка оставляет
 * в профиле цвет, но тема его больше не носит. См. [ProAccent].
 */
fun UserProfile.proAccent(): Color? = ProAccent.of(this)

val LocalViewerTheme = staticCompositionLocalOf<ViewerTheme?> { null }

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
    /** Произвольный HEX-акцент применяется только на экранах профиля, не в чате. */
    applyAccentHex: Boolean = false,
    /**
     * Тема режима владельца поверх темы зрителя (спека §7, `ownerProfileThemeAttrs`):
     * смотрящий с особым режимом видит чужой профиль в гамме владельца. Только для
     * экрана чужого профиля — у чата свои правила (контекст чата).
     */
    ownerModeTheme: ModeTheme? = null,
    content: @Composable () -> Unit
) {
    val resolved = ProfileAppearance.resolve(owner = profile, viewer = currentUser)
    val appearance = if (applyAccentHex) resolved else resolved.copy(accentHex = null)
    // Цвет профиля владельца с PRO — акцент его профиля и чата (важнее акцента смотрящего),
    // если смотрящий видит чужое оформление
    val ownerProAccent = if (resolved.isEmpty) null else profile?.proAccent()
    if (ownerModeTheme == null) {
        ProfileAppearanceTheme(appearance = appearance, proAccent = ownerProAccent, content = content)
        return
    }
    val viewer = LocalViewerTheme.current ?: ViewerTheme(AppTheme.BIOLUME, ThemeMode.DARK, ColorPreset.DEFAULT)
    CompositionLocalProvider(
        // В гамме владельца — его акцент, а не акцент смотрящего
        LocalViewerTheme provides viewer.copy(
            appTheme = ownerModeTheme.toAppTheme(), themeMode = ThemeMode.DARK, modeDriven = true, proAccent = ownerProAccent,
        ),
    ) {
        ProfileAppearanceTheme(appearance = appearance, proAccent = ownerProAccent, content = content)
    }
}

/** Тема интерфейса для правил режима: Forge своего оттенка или Biolume. */
fun ModeTheme.toAppTheme(): AppTheme = when {
    style != ModeStyle.FORGE -> AppTheme.BIOLUME
    flavor == IdMode.BEAST -> AppTheme.FORGE_BEAST
    else -> AppTheme.FORGE_PROTOGEN
}

/**
 * Применяет [appearance] поверх настроек зрителя из [ThemeViewModel].
 * Режим светлая/тёмная всегда зрителя: оформление профиля его не меняет.
 */
@Composable
fun ProfileAppearanceTheme(
    appearance: ProfileAppearance,
    /** Цвет профиля PRO владельца; `null` — остаётся акцент смотрящего. */
    proAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    val themeVm: ThemeViewModel = koinViewModel()
    val settingsTheme by themeVm.appTheme.collectAsState()
    val settingsMode by themeVm.themeMode.collectAsState()
    val settingsPreset by themeVm.colorPreset.collectAsState()
    val showDebugIds by themeVm.showDebugIds.collectAsState()
    val viewer = LocalViewerTheme.current ?: ViewerTheme(settingsTheme, settingsMode, settingsPreset)

    ProfileAppearanceTheme(
        // Тему решает режим ID-карты — выбор темы владельцем не читается (спека §7)
        appearance = if (viewer.modeDriven) appearance.copy(theme = null) else appearance,
        viewerTheme = viewer.appTheme,
        viewerMode = viewer.themeMode,
        viewerPreset = viewer.colorPreset,
        showDebugIds = showDebugIds,
        // Тема по режиму меняется при входе в чат — внутренняя тема чата перетекает так же
        animateColors = viewer.modeDriven,
        proAccent = proAccent ?: viewer.proAccent,
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
    animateColors: Boolean = false,
    proAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    val theme = appearance.theme ?: viewerTheme
    VisorLinkTheme(
        appTheme = theme,
        themeMode = viewerMode,
        colorPreset = appearance.accent ?: viewerPreset,
        accentOverride = appearance.accentHexColor,
        showDebugIds = showDebugIds,
        // Статус-бар остаётся за внешней темой: режим светлая/тёмная тот же,
        // а при уходе с экрана внешняя тема не перезапустила бы свой SideEffect
        setStatusBarColor = false,
        typographyOverride = appearance.font?.let { baseTypography(theme).withProfileFont(it) },
        animateColors = animateColors,
        proAccent = proAccent,
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
    /** Произвольный цвет акцента поверх пресета (HEX из оформления профиля). */
    accentOverride: androidx.compose.ui.graphics.Color? = null,
    showDebugIds: Boolean = LocalShowDebugIds.current,
    setStatusBarColor: Boolean = true,
    /** Подмена гарнитур для PRO-кастомизации; шкала кеглей при этом сохраняется. */
    typographyOverride: androidx.compose.material3.Typography? = null,
    /** Плавная смена палитры (~200 мс) — когда тема меняется по режиму (вход в чат и выход). */
    animateColors: Boolean = false,
    /**
     * Цвет профиля PRO — акцент всего интерфейса ([ProAccent], веб `ProfileAccentSync`).
     * Biolume: действует, пока [colorPreset] — «По умолчанию» и нет [accentOverride] (явный выбор
     * важнее). Forge v2: всегда, заменяет оттенок режима ([ForgeV2.Flavor.tintedBy]). M3E не читает.
     */
    proAccent: Color? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val forgeV2 = remember(appTheme, proAccent) {
        forgeV2Flavor(appTheme)?.let { f -> if (proAccent != null) f.tintedBy(proAccent) else f }
    }
    // Biolume: явно выбранный пресет (или акцент чата / профиля) важнее цвета профиля
    val biolumePro = appTheme == AppTheme.BIOLUME && proAccent != null &&
        colorPreset == ColorPreset.DEFAULT && accentOverride == null
    // Forge v2 — только тёмная, светлой версии нет
    val darkTheme = forgeV2 != null || when (themeMode) {
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

    val seed = accentOverride ?: colorPreset.seedColor
    val targetScheme = when (appTheme) {
        AppTheme.BIOLUME -> {
            val base = if (darkTheme) AbyssColorScheme else TidepoolColorScheme
            if (biolumePro) base.withProAccent(proAccent, darkTheme) else base.withSignalAccent(seed, darkTheme)
        }

        AppTheme.MATERIAL3_EXPRESSIVE -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colorPreset == ColorPreset.DEFAULT && accentOverride == null ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> (if (darkTheme) DarkM3 else LightM3).withMaterialAccent(seed, darkTheme)
        }

        // Пресеты акцента — только в Biolume: палитра режима фиксирована
        AppTheme.FORGE_PROTOGEN, AppTheme.FORGE_BEAST -> forgeV2ColorScheme(forgeV2!!)
    }
    // Цвета перетекают только внутри одного стиля (акцент и т. п.). При смене самой темы
    // (Biolume ↔ Forge, светлая ↔ тёмная) — сразу целевые: переход делает растворение снимка
    // (ThemeCrossfade), а анимация поверх давала кадры «формы Biolume, цвета Forge».
    val colorScheme = if (animateColors) key(appTheme, darkTheme) { animateColorScheme(targetScheme) } else targetScheme

    val tokens = remember(appTheme, darkTheme, reduceMotion, colorScheme, biolumePro) {
        when (appTheme) {
            AppTheme.BIOLUME -> VlTokens(
                style = VlStyle.BIOLUME,
                isDark = darkTheme,
                structure = biolumeStructure(darkTheme),
                signal = if (biolumePro) biolumeProSignal(darkTheme) else biolumeSignal(darkTheme),
                shapes = BiolumeShapes,
                motion = BiolumeMotion,
                status = biolumeStatus(darkTheme),
                selectionFill = biolumeSelectionFill(darkTheme, colorScheme.primary, proAccent = biolumePro),
                bubbles = biolumeBubbles(darkTheme, colorScheme.primary),
                data = BiolumeDataTypography,
                reduceMotion = reduceMotion,
            )

            AppTheme.FORGE_PROTOGEN, AppTheme.FORGE_BEAST -> VlTokens(
                style = VlStyle.FORGE_V2,
                isDark = true,
                structure = forgeV2Structure(forgeV2!!),
                signal = forgeV2Signal(forgeV2),
                shapes = forgeV2Shapes(forgeV2),
                motion = ForgeV2Motion,
                status = forgeV2Status(forgeV2),
                selectionFill = ForgeV2.mix(colorScheme.primary, 0.16f, colorScheme.surfaceContainer),
                bubbles = forgeV2Bubbles(forgeV2),
                data = ForgeDataTypography,
                reduceMotion = reduceMotion,
                terminal = forgeV2Terminal(forgeV2),
                switch = ForgeV2Switch,
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
                AppTheme.FORGE_PROTOGEN, AppTheme.FORGE_BEAST -> forgeV2ShapeScale(forgeV2!!)
                AppTheme.MATERIAL3_EXPRESSIVE -> Material3ShapeScale
            },
            // Свечение заголовков Forge v2 — цвета подкрашенного неона
            typography = typographyOverride
                ?: if (forgeV2 != null && proAccent != null) remember(forgeV2) { forgeV2Typography(forgeV2) }
                else baseTypography(appTheme),
            content = content
        )
    }
}

// ── Плавная смена палитры ────────────────────────────────────────────────────

/**
 * Палитра, перетекающая к [target] за 200 мс (веб: view-transition 200 ms при смене
 * Biolume ↔ Forge у входа в чат). Анимируются все роли ColorScheme.
 */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    @Composable
    fun a(c: Color): Color = animateColorAsState(c, tween(200), label = "scheme").value
    return target.copy(
        primary = a(target.primary), onPrimary = a(target.onPrimary),
        primaryContainer = a(target.primaryContainer), onPrimaryContainer = a(target.onPrimaryContainer),
        inversePrimary = a(target.inversePrimary),
        secondary = a(target.secondary), onSecondary = a(target.onSecondary),
        secondaryContainer = a(target.secondaryContainer), onSecondaryContainer = a(target.onSecondaryContainer),
        tertiary = a(target.tertiary), onTertiary = a(target.onTertiary),
        tertiaryContainer = a(target.tertiaryContainer), onTertiaryContainer = a(target.onTertiaryContainer),
        background = a(target.background), onBackground = a(target.onBackground),
        surface = a(target.surface), onSurface = a(target.onSurface),
        surfaceVariant = a(target.surfaceVariant), onSurfaceVariant = a(target.onSurfaceVariant),
        surfaceTint = a(target.surfaceTint),
        inverseSurface = a(target.inverseSurface), inverseOnSurface = a(target.inverseOnSurface),
        error = a(target.error), onError = a(target.onError),
        errorContainer = a(target.errorContainer), onErrorContainer = a(target.onErrorContainer),
        outline = a(target.outline), outlineVariant = a(target.outlineVariant), scrim = a(target.scrim),
        surfaceBright = a(target.surfaceBright), surfaceDim = a(target.surfaceDim),
        surfaceContainer = a(target.surfaceContainer), surfaceContainerHigh = a(target.surfaceContainerHigh),
        surfaceContainerHighest = a(target.surfaceContainerHighest), surfaceContainerLow = a(target.surfaceContainerLow),
        surfaceContainerLowest = a(target.surfaceContainerLowest),
    )
}
