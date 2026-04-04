package by.iposdev.visorlink.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.BuildConfig
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ThemeMode
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.update.AppUpdateViewModel
import by.iposdev.visorlink.ui.update.UpdateChannel
import by.iposdev.visorlink.ui.update.UpdateState
import by.iposdev.visorlink.utils.AppLanguage
import by.iposdev.visorlink.utils.HapticHelper
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════════════════
//  One UI colour tokens
// ════════════════════════════════════════════════════════════════════════════

private object OneUi {
    val Blue        = Color(0xFF1259C3)
    val BlueDark    = Color(0xFF4D90F0)

    val PageBg      = Color(0xFFF4F4F4)
    val PageBgDark  = Color(0xFF1A1A1A)
    val CardBg      = Color(0xFFFFFFFF)
    val CardBgDark  = Color(0xFF2C2C2C)

    val TextPrimary       = Color(0xFF1A1A1A)
    val TextPrimaryDark   = Color(0xFFEEEEEE)
    val TextSecondary     = Color(0xFF888888)
    val TextSecondaryDark = Color(0xFF999999)

    val Divider     = Color(0xFFE8E8E8)
    val DividerDark = Color(0xFF3A3A3A)

    val SwitchOn    = Blue
    val SwitchOnDark= BlueDark
    val SwitchOff   = Color(0xFFD0D0D0)
    val SwitchOffDk = Color(0xFF555555)

    val SectionColor     = Blue
    val SectionColorDark = BlueDark

    val IconBgBlue    = Color(0xFFEBF1FD)
    val IconBlueDark  = Color(0xFF1E3356)
    val IconBgRed     = Color(0xFFFFF0F0)
    val IconRedDark   = Color(0xFF3D1515)
    val IconBgGreen   = Color(0xFFF0FFF4)
    val IconGreenDark = Color(0xFF0D2E14)
    val IconBgGray    = Color(0xFFF0F0F0)
    val IconGrayDark  = Color(0xFF3A3A3A)

    val IconBlue  = Blue
    val IconRed   = Color(0xFFE53935)
    val IconGreen = Color(0xFF2E7D32)
    val IconGray  = Color(0xFF777777)
}

// ════════════════════════════════════════════════════════════════════════════
//  M3E shape system
// ════════════════════════════════════════════════════════════════════════════

private val M3E_BIG   = 20.dp
private val M3E_SMALL = 4.dp
private val M3E_GAP   = 2.dp

private fun shapeAt(index: Int, total: Int) = when {
    total == 1         -> RoundedCornerShape(M3E_BIG)
    index == 0         -> RoundedCornerShape(topStart = M3E_BIG, topEnd = M3E_BIG,
        bottomStart = M3E_SMALL, bottomEnd = M3E_SMALL)
    index == total - 1 -> RoundedCornerShape(topStart = M3E_SMALL, topEnd = M3E_SMALL,
        bottomStart = M3E_BIG, bottomEnd = M3E_BIG)
    else               -> RoundedCornerShape(M3E_SMALL)
}

private val OUI_CARD_SHAPE = RoundedCornerShape(24.dp)

// ════════════════════════════════════════════════════════════════════════════
//  Screen
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenCacheSettings: () -> Unit = {},
    themeViewModel: ThemeViewModel = koinViewModel(),
    appUpdateViewModel: AppUpdateViewModel = koinViewModel()
) {
    val currentTheme  by themeViewModel.appTheme.collectAsState()
    val currentMode   by themeViewModel.themeMode.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val notifEnabled  by themeViewModel.notificationsEnabled.collectAsState()
    val currentLang   by themeViewModel.language.collectAsState()
    val currentChannel by appUpdateViewModel.currentChannel.collectAsState()
    val haptic = rememberHaptic()

    val isOneUi = currentTheme == AppTheme.ONE_UI
    val isDark  = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val isCanary = BuildConfig.CHANNEL.equals("canary", ignoreCase = true)

    val buildDate = remember {
        SimpleDateFormat("yyyyMMdd.HHmm", Locale.getDefault())
            .format(Date(BuildConfig.BUILD_TIMESTAMP))
    }
    val commitHash = BuildConfig.CommitID.takeIf { it.isNotBlank() } ?: "unknown"
    val versionString = "${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}.$buildDate [$commitHash]"

    val context = LocalContext.current
    val updateState by appUpdateViewModel.updateState.collectAsState()
    var isManualCheck by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(updateState) {
        if (isManualCheck) {
            when (updateState) {
                is UpdateState.None -> {
                    Toast.makeText(context, context.getString(R.string.settings_up_to_date), Toast.LENGTH_SHORT).show()
                    isManualCheck = false
                }
                is UpdateState.Required, is UpdateState.Recommended -> {
                    isManualCheck = false
                }
                UpdateState.Loading -> { }
            }
        }
    }

    val onCheckUpdates = {
        haptic.perform(HapticType.CLICK, hapticEnabled)
        isManualCheck = true
        appUpdateViewModel.checkForUpdates()
        Toast.makeText(context, context.getString(R.string.settings_checking_updates), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = if (isOneUi)
            if (isDark) OneUi.PageBgDark else OneUi.PageBg
        else
            MaterialTheme.colorScheme.surface,
        topBar = {
            if (isOneUi) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.settings_title),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            color = if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary)
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                                    tint = if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isDark) OneUi.PageBgDark else OneUi.PageBg,
                        scrolledContainerColor = if (isDark) OneUi.PageBgDark else OneUi.PageBg
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            if (isOneUi) {
                OuiSettingsContent(
                    currentTheme, currentMode, hapticEnabled, notifEnabled,
                    currentLang, currentChannel, isCanary, versionString, isDark, haptic, themeViewModel,
                    onOpenCacheSettings, onCheckUpdates, onChannelClick = { showChannelDialog = true }
                )
            } else {
                M3eSettingsContent(
                    currentTheme, currentMode, hapticEnabled, notifEnabled,
                    currentLang, currentChannel, isCanary, versionString, haptic, themeViewModel,
                    onOpenCacheSettings, onCheckUpdates, onChannelClick = { showChannelDialog = true }
                )
            }
        }
    }

    if (showChannelDialog) {
        ChannelSelectionDialog(
            currentChannel = currentChannel,
            onDismiss = { showChannelDialog = false },
            onSelect = {
                appUpdateViewModel.setChannel(it)
                showChannelDialog = false
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ONE UI CONTENT
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun OuiSettingsContent(
    currentTheme: AppTheme,
    currentMode: ThemeMode,
    hapticEnabled: Boolean,
    notifEnabled: Boolean,
    currentLang: AppLanguage,
    currentChannel: UpdateChannel,
    isCanary: Boolean,
    versionString: String,
    isDark: Boolean,
    haptic: HapticHelper,
    vm: ThemeViewModel,
    onOpenCacheSettings: () -> Unit,
    onCheckUpdates: () -> Unit,
    onChannelClick: () -> Unit
) {
    OuiSectionLabel(stringResource(R.string.settings_section_appearance), isDark)

    OuiCard(isDark) {
        OuiOptionRow(
            label       = stringResource(R.string.settings_theme_m3_name),
            desc        = stringResource(R.string.settings_theme_m3_desc),
            icon        = Icons.Default.AutoAwesome,
            iconBg      = if (isDark) OneUi.IconBlueDark  else OneUi.IconBgBlue,
            iconTint    = if (isDark) OneUi.BlueDark       else OneUi.IconBlue,
            selected    = currentTheme == AppTheme.MATERIAL3_EXPRESSIVE,
            showDivider = false, isDark = isDark
        ) { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setTheme(AppTheme.MATERIAL3_EXPRESSIVE) }

        OuiDivider(isDark)

        OuiOptionRow(
            label       = stringResource(R.string.settings_theme_oneui_name),
            desc        = stringResource(R.string.settings_theme_oneui_desc),
            icon        = Icons.Default.PhoneAndroid,
            iconBg      = if (isDark) OneUi.IconGrayDark  else OneUi.IconBgGray,
            iconTint    = if (isDark) OneUi.TextSecondaryDark else OneUi.IconGray,
            selected    = currentTheme == AppTheme.ONE_UI,
            showDivider = false, isDark = isDark
        ) { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setTheme(AppTheme.ONE_UI) }
    }

    OuiSectionLabel(stringResource(R.string.settings_dark_title), isDark)

    OuiCard(isDark) {
        listOf(
            Triple(ThemeMode.SYSTEM, stringResource(R.string.settings_dark_system),
                stringResource(R.string.settings_dark_system_desc)) to Icons.Default.SettingsBrightness,
            Triple(ThemeMode.LIGHT,  stringResource(R.string.settings_dark_light),
                stringResource(R.string.settings_dark_light_desc))  to Icons.Default.LightMode,
            Triple(ThemeMode.DARK,   stringResource(R.string.settings_dark_dark),
                stringResource(R.string.settings_dark_dark_desc))   to Icons.Default.DarkMode,
        ).forEachIndexed { i, (triple, icon) ->
            val (mode, label, desc) = triple
            if (i > 0) OuiDivider(isDark)
            OuiOptionRow(
                label = label, desc = desc, icon = icon,
                iconBg   = if (currentMode == mode)
                    (if (isDark) OneUi.IconBlueDark else OneUi.IconBgBlue)
                else (if (isDark) OneUi.IconGrayDark else OneUi.IconBgGray),
                iconTint = if (currentMode == mode)
                    (if (isDark) OneUi.BlueDark else OneUi.IconBlue)
                else (if (isDark) OneUi.TextSecondaryDark else OneUi.IconGray),
                selected = currentMode == mode,
                showDivider = false, isDark = isDark
            ) { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setThemeMode(mode) }
        }
    }

    OuiSectionLabel(stringResource(R.string.settings_section_language), isDark)

    OuiCard(isDark) {
        listOf(
            AppLanguage.SYSTEM to stringResource(R.string.settings_language_system),
            AppLanguage.EN     to stringResource(R.string.settings_language_en),
            AppLanguage.RU     to stringResource(R.string.settings_language_ru),
        ).forEachIndexed { i, (lang, label) ->
            if (i > 0) OuiDivider(isDark)
            OuiLangRow(
                label    = label,
                selected = currentLang == lang,
                isDark   = isDark
            ) { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setLanguage(lang) }
        }
    }

    OuiSectionLabel(stringResource(R.string.settings_section_notifications), isDark)

    OuiCard(isDark) {
        OuiSwitchRow(
            icon     = Icons.Default.Notifications,
            iconBg   = if (isDark) OneUi.IconRedDark else OneUi.IconBgRed,
            iconTint = OneUi.IconRed,
            title    = stringResource(R.string.settings_push_title),
            sub      = stringResource(R.string.settings_push_sub),
            checked  = notifEnabled,
            isDark   = isDark
        ) { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setNotifications(it) }

        OuiDivider(isDark)

        OuiSwitchRow(
            icon     = Icons.Default.Vibration,
            iconBg   = if (isDark) OneUi.IconBlueDark else OneUi.IconBgBlue,
            iconTint = if (isDark) OneUi.BlueDark else OneUi.IconBlue,
            title    = stringResource(R.string.settings_haptic_title),
            sub      = stringResource(R.string.settings_haptic_sub),
            checked  = hapticEnabled,
            isDark   = isDark
        ) { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setHaptic(it) }
    }

    OuiSectionLabel(stringResource(R.string.settings_section_storage), isDark)

    OuiCard(isDark) {
        OuiNavRow(
            icon     = Icons.Default.Storage,
            iconBg   = if (isDark) OneUi.IconGreenDark else OneUi.IconBgGreen,
            iconTint = OneUi.IconGreen,
            title    = stringResource(R.string.settings_cache_title),
            sub      = stringResource(R.string.settings_cache_subtitle),
            isDark   = isDark
        ) { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenCacheSettings() }
    }

    OuiSectionLabel(stringResource(R.string.settings_section_about), isDark)

    OuiCard(isDark) {
        OuiInfoRow(
            icon     = Icons.Default.Info,
            iconBg   = if (isDark) OneUi.IconGrayDark else OneUi.IconBgGray,
            iconTint = if (isDark) OneUi.TextSecondaryDark else OneUi.IconGray,
            title    = stringResource(R.string.settings_version),
            subtitle = versionString,
            isDark   = isDark
        )

        OuiDivider(isDark)

        if (isCanary) {
            OuiInfoRow(
                icon     = Icons.Default.Science,
                iconBg   = if (isDark) OneUi.IconRedDark else OneUi.IconBgRed,
                iconTint = OneUi.IconRed,
                title    = "Канал обновлений",
                subtitle = "Переключение недоступно на сборке Canary",
                isDark   = isDark
            )
        } else {
            OuiNavRow(
                icon     = Icons.Default.Science,
                iconBg   = if (isDark) OneUi.IconBlueDark else OneUi.IconBgBlue,
                iconTint = if (isDark) OneUi.BlueDark else OneUi.IconBlue,
                title    = "Канал обновлений",
                sub      = "Текущий: ${currentChannel.title}",
                isDark   = isDark,
                onClick  = {
                    haptic.perform(HapticType.CLICK, hapticEnabled)
                    onChannelClick()
                }
            )
        }

        OuiDivider(isDark)

        OuiNavRow(
            icon     = Icons.Default.Sync,
            iconBg   = if (isDark) OneUi.IconBlueDark else OneUi.IconBgBlue,
            iconTint = if (isDark) OneUi.BlueDark else OneUi.IconBlue,
            title    = stringResource(R.string.settings_check_updates),
            sub      = stringResource(R.string.settings_check_updates_sub),
            isDark   = isDark,
            onClick  = onCheckUpdates
        )
    }

    if (BuildConfig.InternalBuild) {
        Spacer(Modifier.height(24.dp))
        OuiWarningCard(isDark)
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  One UI primitives
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun OuiSectionLabel(text: String, isDark: Boolean) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.5.sp,
        color = if (isDark) OneUi.SectionColorDark else OneUi.SectionColor,
        modifier = Modifier.padding(start = 28.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun OuiCard(isDark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = OUI_CARD_SHAPE,
        color = if (isDark) OneUi.CardBgDark else OneUi.CardBg,
        shadowElevation = 0.dp,
        content = { Column(content = content) }
    )
}

@Composable
private fun OuiDivider(isDark: Boolean) {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 72.dp),
        thickness = 0.5.dp,
        color     = if (isDark) OneUi.DividerDark else OneUi.Divider
    )
}

@Composable
private fun OuiIconTray(bg: Color, tint: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun OuiSwitch(checked: Boolean, isDark: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 22f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "oui_thumb"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val thumbWidth by animateFloatAsState(
        targetValue = if (isPressed) 28f else 24f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
        label = "thumb_w"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked)
            (if (isDark) OneUi.SwitchOnDark else OneUi.SwitchOn)
        else
            (if (isDark) OneUi.SwitchOffDk else OneUi.SwitchOff),
        animationSpec = tween(200),
        label = "track_color"
    )

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .padding(start = 3.dp + thumbOffset.dp, top = 3.dp)
                .width(thumbWidth.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun OuiRadio(selected: Boolean, isDark: Boolean) {
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "oui_radio_dot"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected)
            (if (isDark) OneUi.BlueDark else OneUi.Blue)
        else
            (if (isDark) Color(0xFF666666) else Color(0xFFD0D0D0)),
        animationSpec = tween(200),
        label = "oui_radio_border"
    )
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Transparent),
        )
        Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = Color.Transparent,
            border = ButtonDefaults.outlinedButtonBorder.copy(
                width = 2.dp,
                brush = SolidColor(borderColor)
            )
        ) {}
        Box(
            modifier = Modifier
                .size((11 * dotScale).dp)
                .clip(CircleShape)
                .background(if (isDark) OneUi.BlueDark else OneUi.Blue)
        )
    }
}

@Composable
private fun OuiOptionRow(
    label: String,
    desc: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    selected: Boolean,
    showDivider: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgColor by animateColorAsState(
        targetValue = if (isPressed)
            (if (isDark) Color(0xFF383838) else Color(0xFFF0F0F0))
        else Color.Transparent,
        animationSpec = tween(100),
        label = "oui_opt_press"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        color = bgColor,
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OuiIconTray(bg = iconBg, tint = iconTint, icon = icon)
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected)
                        (if (isDark) OneUi.BlueDark else OneUi.Blue)
                    else
                        (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary)
                )
                Text(
                    desc,
                    fontSize = 12.sp,
                    color = if (isDark) OneUi.TextSecondaryDark else OneUi.TextSecondary,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            OuiRadio(selected = selected, isDark = isDark)
        }
    }
}

@Composable
private fun OuiLangRow(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) (if (isDark) Color(0xFF383838) else Color(0xFFF0F0F0))
        else Color.Transparent,
        animationSpec = tween(100), label = "lang_press"
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        color = bgColor,
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) (if (isDark) OneUi.BlueDark else OneUi.Blue)
                else (if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary),
                modifier = Modifier.weight(1f)
            )
            OuiRadio(selected = selected, isDark = isDark)
        }
    }
}

@Composable
private fun OuiSwitchRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    sub: String,
    checked: Boolean,
    isDark: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OuiIconTray(bg = iconBg, tint = iconTint, icon = icon)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary)
            Text(sub, fontSize = 12.sp,
                color = if (isDark) OneUi.TextSecondaryDark else OneUi.TextSecondary,
                modifier = Modifier.padding(top = 1.dp))
        }
        OuiSwitch(checked = checked, isDark = isDark, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OuiNavRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    sub: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) (if (isDark) Color(0xFF383838) else Color(0xFFF0F0F0))
        else Color.Transparent,
        animationSpec = tween(100), label = "nav_press"
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        color = bgColor,
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OuiIconTray(bg = iconBg, tint = iconTint, icon = icon)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary)
                Text(sub, fontSize = 12.sp,
                    color = if (isDark) OneUi.TextSecondaryDark else OneUi.TextSecondary,
                    modifier = Modifier.padding(top = 1.dp))
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = if (isDark) Color(0xFF666666) else Color(0xFFC0C0C0),
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OuiInfoRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OuiIconTray(bg = iconBg, tint = iconTint, icon = icon)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) OneUi.TextPrimaryDark else OneUi.TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = if (isDark) OneUi.TextSecondaryDark else OneUi.TextSecondary,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  M3E CONTENT
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun M3eSettingsContent(
    currentTheme: AppTheme,
    currentMode: ThemeMode,
    hapticEnabled: Boolean,
    notifEnabled: Boolean,
    currentLang: AppLanguage,
    currentChannel: UpdateChannel,
    isCanary: Boolean,
    versionString: String,
    haptic: HapticHelper,
    vm: ThemeViewModel,
    onOpenCacheSettings: () -> Unit,
    onCheckUpdates: () -> Unit,
    onChannelClick: () -> Unit
) {
    SectionHeader(stringResource(R.string.settings_section_appearance))
    GroupLabel(Icons.Default.Palette, stringResource(R.string.settings_theme_title))
    Spacer(Modifier.height(4.dp))
    OptionGroup {
        ThemeOption(stringResource(R.string.settings_theme_m3_name),
            stringResource(R.string.settings_theme_m3_desc), Icons.Default.AutoAwesome,
            currentTheme == AppTheme.MATERIAL3_EXPRESSIVE, 0, 2, true)
        { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setTheme(AppTheme.MATERIAL3_EXPRESSIVE) }
        Spacer(Modifier.height(M3E_GAP))
        ThemeOption(stringResource(R.string.settings_theme_oneui_name),
            stringResource(R.string.settings_theme_oneui_desc), Icons.Default.PhoneAndroid,
            currentTheme == AppTheme.ONE_UI, 1, 2, true)
        { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setTheme(AppTheme.ONE_UI) }
    }

    SectionHeader(stringResource(R.string.settings_dark_title))
    OptionGroup {
        ThemeOption(stringResource(R.string.settings_dark_system), stringResource(R.string.settings_dark_system_desc),
            Icons.Default.SettingsBrightness, currentMode == ThemeMode.SYSTEM, 0, 3, false)
        { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setThemeMode(ThemeMode.SYSTEM) }
        Spacer(Modifier.height(M3E_GAP))
        ThemeOption(stringResource(R.string.settings_dark_light), stringResource(R.string.settings_dark_light_desc),
            Icons.Default.LightMode, currentMode == ThemeMode.LIGHT, 1, 3, false)
        { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setThemeMode(ThemeMode.LIGHT) }
        Spacer(Modifier.height(M3E_GAP))
        ThemeOption(stringResource(R.string.settings_dark_dark), stringResource(R.string.settings_dark_dark_desc),
            Icons.Default.DarkMode, currentMode == ThemeMode.DARK, 2, 3, false)
        { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setThemeMode(ThemeMode.DARK) }
    }

    SectionHeader(stringResource(R.string.settings_section_language))
    GroupLabel(Icons.Default.Language, stringResource(R.string.settings_language_title))
    Spacer(Modifier.height(4.dp))
    OptionGroup {
        LanguageOption(stringResource(R.string.settings_language_system), currentLang == AppLanguage.SYSTEM, 0, 3)
        { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setLanguage(AppLanguage.SYSTEM) }
        Spacer(Modifier.height(M3E_GAP))
        LanguageOption(stringResource(R.string.settings_language_en), currentLang == AppLanguage.EN, 1, 3)
        { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setLanguage(AppLanguage.EN) }
        Spacer(Modifier.height(M3E_GAP))
        LanguageOption(stringResource(R.string.settings_language_ru), currentLang == AppLanguage.RU, 2, 3)
        { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setLanguage(AppLanguage.RU) }
    }

    SectionHeader(stringResource(R.string.settings_section_notifications))
    OptionGroup {
        SwitchRow(Icons.Default.Notifications, stringResource(R.string.settings_push_title),
            stringResource(R.string.settings_push_sub), notifEnabled, 0, 2)
        { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setNotifications(it) }
        Spacer(Modifier.height(M3E_GAP))
        SwitchRow(Icons.Default.Vibration, stringResource(R.string.settings_haptic_title),
            stringResource(R.string.settings_haptic_sub), hapticEnabled, 1, 2)
        { haptic.perform(HapticType.SELECTION, hapticEnabled); vm.setHaptic(it) }
    }

    SectionHeader(stringResource(R.string.settings_section_storage))
    OptionGroup {
        NavRow(Icons.Default.Storage, stringResource(R.string.settings_cache_title),
            stringResource(R.string.settings_cache_subtitle), 0, 1)
        { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenCacheSettings() }
    }

    SectionHeader(stringResource(R.string.settings_section_about))
    OptionGroup {
        InfoRow(Icons.Default.Info, stringResource(R.string.settings_version), versionString, 0, 3)
        Spacer(Modifier.height(M3E_GAP))

        if (isCanary) {
            InfoRow(Icons.Default.Science, "Канал обновлений", "Переключение недоступно на сборке Canary", 1, 3)
        } else {
            NavRow(Icons.Default.Science, "Канал обновлений", "Текущий: ${currentChannel.title}", 1, 3) {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                onChannelClick()
            }
        }
        Spacer(Modifier.height(M3E_GAP))

        NavRow(Icons.Default.Sync, stringResource(R.string.settings_check_updates),
            stringResource(R.string.settings_check_updates_sub), 2, 3)
        { onCheckUpdates() }
    }

    if (BuildConfig.InternalBuild) {
        Spacer(Modifier.height(24.dp))
        M3eWarningCard()
    }
}

// ─── Dialog Channel Selection ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSelectionDialog(
    currentChannel: UpdateChannel,
    onDismiss: () -> Unit,
    onSelect: (UpdateChannel) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Канал обновлений",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                UpdateChannel.entries.forEach { channel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(channel) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentChannel == channel,
                            onClick = { onSelect(channel) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = channel.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Файл: ${channel.fileName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

// ─── M3E helpers ─────────────────────────────────────────────────────────────

@Composable private fun SectionHeader(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 28.dp, top = 22.dp, bottom = 4.dp))
}

@Composable private fun GroupLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp, bottom = 2.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun OptionGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp), content = content)
}

@Composable private fun ThemeOption(
    label: String, description: String, icon: ImageVector,
    selected: Boolean, index: Int, total: Int, primaryColor: Boolean, onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh), label = "sc")
    val selectedBg = if (primaryColor) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val selectedFg = if (primaryColor) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSecondaryContainer
    val checkColor = if (primaryColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val bgColor by animateColorAsState(if (selected) selectedBg else MaterialTheme.colorScheme.surfaceContainerLow, tween(220), label = "bg")
    val iconBg  by animateColorAsState(if (selected) checkColor.copy(.15f) else MaterialTheme.colorScheme.surfaceContainerHigh, tween(220), label = "ibg")
    Surface(onClick = onClick, interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale), shape = shapeAt(index, total), color = bgColor) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg), Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (selected) checkColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (selected) selectedFg else MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = if (selected) selectedFg.copy(.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CheckIcon(selected, checkColor)
        }
    }
}

@Composable private fun LanguageOption(label: String, selected: Boolean, index: Int, total: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh), label = "sc")
    val bgColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, tween(220), label = "bg")
    Surface(onClick = onClick, interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale), shape = shapeAt(index, total), color = bgColor) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            CheckIcon(selected, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun SwitchRow(
    icon: ImageVector, title: String, sub: String,
    checked: Boolean, index: Int, total: Int, onCheckedChange: (Boolean) -> Unit
) {
    val bgColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow, tween(200), label = "bg")
    val iconBg by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary.copy(.12f)
        else MaterialTheme.colorScheme.surfaceContainerHighest, tween(200), label = "ibg")
    Surface(shape = shapeAt(index, total), color = bgColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg), Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline))
        }
    }
}

@Composable private fun NavRow(icon: ImageVector, title: String, subtitle: String, index: Int, total: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh), label = "sc")
    Surface(onClick = onClick, interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale), shape = shapeAt(index, total),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(.1f)), Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f)
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String, index: Int, total: Int) {
    Surface(
        shape = shapeAt(index, total),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable private fun CheckIcon(selected: Boolean, color: Color) {
    Box(Modifier.size(22.dp)) {
        AnimatedVisibility(selected, enter = scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(tween(150)),
            exit = scaleOut(tween(100)) + fadeOut(tween(80))) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = color
            )
        }
        AnimatedVisibility(!selected, enter = scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(tween(150)),
            exit = scaleOut(tween(100)) + fadeOut(tween(80))) {
            Icon(
                imageVector = Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun OuiWarningCard(isDark: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = OUI_CARD_SHAPE,
        color = if (isDark) Color(0xFF3D2A1D) else Color(0xFFFFF3E0),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF5D4037) else Color(0xFFFFE0B2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFFFFB74D) else Color(0xFFF57C00),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Внутренняя сборка",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color(0xFFFFEECC) else Color(0xFFE65100)
                )
                Text(
                    text = "Эта версия предназначена для тестирования и может быть нестабильной.",
                    fontSize = 13.sp,
                    color = if (isDark) Color(0xFFFFB74D) else Color(0xFFF57C00),
                    modifier = Modifier.padding(top = 2.dp),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun M3eWarningCard() {
    OptionGroup {
        Surface(
            shape = RoundedCornerShape(M3E_BIG),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                    Alignment.Center
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Внутренняя сборка",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "Эта версия предназначена для тестирования и может быть нестабильной.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}