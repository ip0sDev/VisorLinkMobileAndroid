// ui/screens/settings/SettingsScreen.kt
package by.iposdev.visorlink.ui.screens.settings

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.BuildConfig
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ColorPreset
import by.iposdev.visorlink.data.model.ThemeMode
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.data.repository.AuthRepository
import by.iposdev.visorlink.data.repository.BotRepository
import by.iposdev.visorlink.data.repository.DmBot
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.data.repository.FlagsRepository
import by.iposdev.visorlink.ui.components.*
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.theme.exthruSmallRaisedShadow
import by.iposdev.visorlink.ui.theme.forgeNeuBrutalism
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.ui.theme.rememberExthruStyle
import by.iposdev.visorlink.ui.update.AppUpdateViewModel
import by.iposdev.visorlink.ui.update.UpdateChannel
import by.iposdev.visorlink.utils.AppLanguage
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.StealthManager
import by.iposdev.visorlink.utils.rememberHaptic
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenCacheSettings: () -> Unit = {},
    onOpenStorageManager: () -> Unit = {},
    onOpenCustomization: () -> Unit = {},
    onOpenAegisDebug: () -> Unit = {},
    themeViewModel: ThemeViewModel = koinViewModel(),
    appUpdateViewModel: AppUpdateViewModel,
    userRepository: UserRepository = koinInject(),
    authRepository: AuthRepository = koinInject(),
    flagsRepository: FlagsRepository = koinInject(),
    proViewModel: ProViewModel = koinViewModel()
) {
    val currentTheme by themeViewModel.appTheme.collectAsState()
    val currentMode by themeViewModel.themeMode.collectAsState()
    val currentPreset by themeViewModel.colorPreset.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val notifEnabled by themeViewModel.notificationsEnabled.collectAsState()
    val currentLang by themeViewModel.language.collectAsState()
    val currentChannel by appUpdateViewModel.currentChannel.collectAsState()
    val dynamicInput by themeViewModel.dynamicChatInput.collectAsState()
    val compactChatList by themeViewModel.compactChatList.collectAsState()

    val profile by userRepository.currentUserFlow().collectAsState(initial = null)
    val proState by proViewModel.uiState.collectAsState()
    val flags by flagsRepository.flags.collectAsState()

    val context = LocalContext.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val style = rememberExthruStyle(currentTheme)
    val isForge = style.isForge

    // ── Стелс-режим ──
    val stealthManager = remember { StealthManager(context) }
    var isStealthEnabled by remember { mutableStateOf(stealthManager.isEnabled()) }
    var hasStealthPin by remember { mutableStateOf(stealthManager.hasPin()) }

    var showStealthSetup by remember { mutableStateOf(false) }
    var showStealthDisable by remember { mutableStateOf(false) }
    var showStealthChangePin by remember { mutableStateOf(false) }

    // ── Telegram Account Binding ──
    var showTgBindingDialog by remember { mutableStateOf(false) }
    var tgCode by remember { mutableStateOf<String?>(null) }
    var isGeneratingTgCode by remember { mutableStateOf(false) }
    var tgError by remember { mutableStateOf<String?>(null) }

    // ── Админка и боты ──
    var showAdminPanel by remember { mutableStateOf(false) }
    var showBotsManager by remember { mutableStateOf(false) }

    // ── Диалоги ──
    var showChannelDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val buildDate = remember {
        SimpleDateFormat("yyyyMMdd.HHmm", Locale.getDefault()).format(Date(BuildConfig.BUILD_TIMESTAMP))
    }
    val commitHash = BuildConfig.CommitID.takeIf { it.isNotBlank() } ?: "unknown"
    val versionString = "${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}.$buildDate [$commitHash]"
    val deviceId = remember { flagsRepository.getDeviceId() ?: "Not paired" }

    // Семантические цвета для иконок
    val colorNotif = Color(0xFFF59E0B)
    val colorVibro = Color(0xFFEC4899)
    val colorDynInput = Color(0xFF10B981)
    val colorCompact = Color(0xFF3B82F6)
    val colorStealth = Color(0xFF8B5CF6)
    val colorStealthPin = Color(0xFF6366F1)
    val colorStorage = Color(0xFF3B82F6)
    val colorBots = Color(0xFF14B8A6)
    val colorUpdateChan = Color(0xFFF59E0B)
    val colorUpdateCheck = Color(0xFF10B981)
    val colorEmail = Color(0xFF64748B)
    val colorPassword = Color(0xFFF43F5E)

    val hazeState = remember { HazeState() }
    val scaffoldBg = if (currentTheme.isExthruFamily) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface

    // Обработка уведомлений PRO
    LaunchedEffect(proState.successMessage) {
        proState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            proViewModel.clearMessages()
        }
    }
    LaunchedEffect(proState.error) {
        proState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            proViewModel.clearMessages()
        }
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                if (currentTheme.isExthruFamily) {
                    val topBarBg = if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.55f)

                    val topBarMod = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isForge) Modifier.background(topBarBg)
                            else Modifier.hazeChild(
                                state = hazeState,
                                style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null)
                            ).background(topBarBg)
                        )

                    TopAppBar(
                        modifier = topBarMod,
                        title = {
                            Text(
                                text = stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (isForge) FontFamily.Monospace else null
                                )
                            )
                        },
                        navigationIcon = {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "back_btn_scale")

                            val shape = if (isForge) RectangleShape else CircleShape
                            val shadowMod = if (isForge) {
                                Modifier.forgeNeuBrutalism(isPressed, isDark, 3.dp)
                            } else if (isPressed) {
                                Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
                            } else {
                                Modifier.exthruSmallRaisedShadow(isDark)
                            }

                            Box(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 4.dp)
                                    .size(42.dp)
                                    .scale(if(isForge) 1f else scale)
                                    .then(shadowMod)
                                    .background(if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), shape)
                                    .then(if (isForge) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                                    .clip(shape)
                                    .clickable(interactionSource = interactionSource, indication = null) {
                                        haptic.perform(HapticType.CLICK, hapticEnabled)
                                        onNavigateBack()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                onNavigateBack()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (currentTheme.isExthruFamily && !isForge) it.haze(state = hazeState) else it }
                    .background(scaffoldBg)
            ) {
                VlAmbientGlow(appTheme = currentTheme)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(padding.calculateTopPadding() + 8.dp))

                    // ── Профиль ──
                    profile?.let { p ->
                        VlSurface(
                            appTheme = currentTheme,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                            onClick = { /* To Profile (can be implemented later) */ }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarWithPresence(
                                    avatarUrl = p.avatarUrl,
                                    displayName = p.displayName.ifEmpty { p.username },
                                    isOnline = false,
                                    size = 64.dp
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = p.displayName.ifEmpty { p.username },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = if (isForge) FontFamily.Monospace else null
                                    )
                                    Text(
                                        text = "@${p.username}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = if (isForge) FontFamily.Monospace else null
                                    )
                                }
                            }
                        }
                    }

                    // ── PRO ──
                    profile?.let { p ->
                        ProStatusBanner(
                            profile = p,
                            proViewModel = proViewModel,
                            proState = proState,
                            appTheme = currentTheme,
                            isDark = isDark,
                            hapticEnabled = hapticEnabled
                        )
                    }

                    // ── Кастомизация ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_custom_title), isPremium = true) {
                        VlSettingsItem(
                            appTheme = currentTheme,
                            iconColor = Color(0xFFC5A059),
                            icon = Icons.Default.Brush,
                            title = stringResource(R.string.settings_custom_design_title),
                            subtitle = stringResource(R.string.settings_custom_design_sub),
                            index = 0, total = 2,
                            onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                if (profile?.isProActive() == true) {
                                    onOpenCustomization()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.settings_custom_pro_only), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        VlSettingsItem(
                            appTheme = currentTheme,
                            icon = Icons.Default.HideImage,
                            title = stringResource(R.string.settings_custom_hide_title),
                            subtitle = stringResource(R.string.settings_custom_hide_sub),
                            index = 1, total = 2,
                            trailing = {
                                VlSwitch(
                                    appTheme = currentTheme,
                                    checked = profile?.ignoreCustomizations ?: false,
                                    onCheckedChange = { v ->
                                        haptic.perform(HapticType.SELECTION, hapticEnabled)
                                        scope.launch {
                                            userRepository.updateIgnoreCustomizations(v)
                                        }
                                    }
                                )
                            }
                        )
                    }

                    // ── Акцент ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_section_accent)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ColorPreset.entries.forEach { preset ->
                                ColorPresetCircle(
                                    preset = preset,
                                    isSelected = currentPreset == preset,
                                    appTheme = currentTheme,
                                    isDark = isDark,
                                    onClick = {
                                        haptic.perform(HapticType.CLICK, hapticEnabled)
                                        themeViewModel.setColorPreset(preset)
                                    }
                                )
                            }
                        }
                    }

                    // ── Внешний вид ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_section_appearance)) {
                        VlOptionRow(appTheme = currentTheme, icon = Icons.Default.Layers, label = "Biolume", desc = "Органичный неоморфизм", selected = currentTheme == AppTheme.BIOLUME || currentTheme == AppTheme.EXTHRU, index = 0, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setTheme(AppTheme.BIOLUME) })
                        VlOptionRow(appTheme = currentTheme, icon = Icons.Default.AutoAwesome, label = stringResource(R.string.settings_theme_m3_name), desc = stringResource(R.string.settings_theme_m3_desc), selected = currentTheme == AppTheme.MATERIAL3_EXPRESSIVE, index = 1, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setTheme(AppTheme.MATERIAL3_EXPRESSIVE) })
                        VlOptionRow(appTheme = currentTheme, icon = Icons.Default.Shield, label = "Forge", desc = "Квадратный киберпанк, Arasaka", selected = currentTheme == AppTheme.FORGE, index = 2, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setTheme(AppTheme.FORGE) })
                    }

                    // ── Тёмный режим ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_dark_title)) {
                        VlOptionRow(appTheme = currentTheme, icon = Icons.Default.SettingsBrightness, label = stringResource(R.string.settings_dark_system), desc = stringResource(R.string.settings_dark_system_desc), selected = currentMode == ThemeMode.SYSTEM, index = 0, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setThemeMode(ThemeMode.SYSTEM) })
                        VlOptionRow(appTheme = currentTheme, icon = Icons.Default.LightMode, label = stringResource(R.string.settings_dark_light), desc = stringResource(R.string.settings_dark_light_desc), selected = currentMode == ThemeMode.LIGHT, index = 1, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setThemeMode(ThemeMode.LIGHT) })
                        VlOptionRow(appTheme = currentTheme, icon = Icons.Default.DarkMode, label = stringResource(R.string.settings_dark_dark), desc = stringResource(R.string.settings_dark_dark_desc), selected = currentMode == ThemeMode.DARK, index = 2, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setThemeMode(ThemeMode.DARK) })
                    }

                    // ── Язык ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_section_language)) {
                        VlOptionRow(appTheme = currentTheme, icon = Icons.Default.Language, label = stringResource(R.string.settings_language_system), selected = currentLang == AppLanguage.SYSTEM, index = 0, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setLanguage(AppLanguage.SYSTEM) })
                        VlOptionRow(appTheme = currentTheme, icon = Icons.Default.Translate, label = stringResource(R.string.settings_language_en), selected = currentLang == AppLanguage.EN, index = 1, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setLanguage(AppLanguage.EN) })
                        VlOptionRow(appTheme = currentTheme, icon = Icons.Default.GTranslate, label = stringResource(R.string.settings_language_ru), selected = currentLang == AppLanguage.RU, index = 2, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setLanguage(AppLanguage.RU) })
                    }

                    // ── Управление ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_section_management)) {
                        VlSettingsItem(appTheme = currentTheme, iconColor = colorNotif, icon = Icons.Default.NotificationsActive, title = stringResource(R.string.settings_push_title), trailing = { VlSwitch(appTheme = currentTheme, checked = notifEnabled, onCheckedChange = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setNotifications(it) }) }, index = 0, total = 5)
                        VlSettingsItem(appTheme = currentTheme, iconColor = colorVibro, icon = Icons.Default.Vibration, title = stringResource(R.string.settings_haptic_title), trailing = { VlSwitch(appTheme = currentTheme, checked = hapticEnabled, onCheckedChange = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setHaptic(it) }) }, index = 1, total = 5)
                        VlSettingsItem(appTheme = currentTheme, iconColor = colorDynInput, icon = Icons.Default.KeyboardHide, title = "Динамическое поле ввода", subtitle = "Стиль Flutter. Скрывает меню при наборе.", trailing = { VlSwitch(appTheme = currentTheme, checked = dynamicInput, onCheckedChange = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setDynamicChatInput(it) }) }, index = 2, total = 5)
                        VlSettingsItem(appTheme = currentTheme, iconColor = colorCompact, icon = Icons.Default.ViewAgenda, title = "Компактный список чатов", subtitle = "Объединяет чаты в единую карточку", trailing = { VlSwitch(appTheme = currentTheme, checked = compactChatList, onCheckedChange = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setCompactChatList(it) }) }, index = 3, total = 5)
                        VlSettingsItem(
                            appTheme = currentTheme,
                            iconColor = Color(0xFF10B981),
                            icon = Icons.Default.Explore,
                            title = "Discover (Лента)",
                            subtitle = "Показывать вкладку с глобальной лентой",
                            index = 4, total = 5,
                            trailing = {
                                VlSwitch(
                                    appTheme = currentTheme,
                                    checked = themeViewModel.discoverEnabled.collectAsState().value,
                                    onCheckedChange = { themeViewModel.setDiscoverEnabled(it) }
                                )
                            }
                        )
                    }

                    // ── Дневник ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.diary_title)) {
                        VlSettingsItem(
                            appTheme = currentTheme,
                            icon = Icons.Default.Book,
                            iconColor = MaterialTheme.colorScheme.primary,
                            title = stringResource(R.string.diary_enable_title),
                            subtitle = stringResource(R.string.diary_enable_sub),
                            index = 0, total = 2,
                            trailing = {
                                VlSwitch(
                                    appTheme = currentTheme,
                                    checked = profile?.diaryEnabled ?: false,
                                    onCheckedChange = { v ->
                                        haptic.perform(HapticType.SELECTION, hapticEnabled)
                                        scope.launch {
                                            val uid = profile?.uid ?: return@launch
                                            Firebase.firestore.collection("users").document(uid)
                                                .update("diaryEnabled", v)
                                        }
                                    }
                                )
                            }
                        )
                        if (profile?.diaryEnabled == true) {
                            VlSettingsItem(
                                appTheme = currentTheme,
                                icon = Icons.Default.Notifications,
                                iconColor = Color(0xFFF59E0B),
                                title = stringResource(R.string.diary_reminders_title),
                                subtitle = stringResource(R.string.diary_reminders_sub, profile?.diaryReminderTime ?: "21:00"),
                                index = 1, total = 2,
                                onClick = {
                                    val parts = (profile?.diaryReminderTime ?: "21:00").split(":")
                                    val picker = TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            val time = String.format(Locale.US, "%02d:%02d", h, m)
                                            scope.launch {
                                                val uid = profile?.uid ?: return@launch
                                                Firebase.firestore.collection("users").document(uid)
                                                    .update("diaryReminderTime", time)
                                            }
                                        },
                                        parts[0].toInt(),
                                        parts[1].toInt(),
                                        true
                                    )
                                    picker.show()
                                },
                                trailing = {
                                    VlSwitch(
                                        appTheme = currentTheme,
                                        checked = profile?.diaryRemindersEnabled ?: false,
                                        onCheckedChange = { v ->
                                            haptic.perform(HapticType.SELECTION, hapticEnabled)
                                            scope.launch {
                                                val uid = profile?.uid ?: return@launch
                                                Firebase.firestore.collection("users").document(uid)
                                                    .update("diaryRemindersEnabled", v)
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }

                    // ── Приватность ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_section_privacy)) {
                        VlSettingsItem(
                            appTheme = currentTheme,
                            iconColor = colorStealth,
                            icon = Icons.Default.VisibilityOff,
                            title = stringResource(R.string.settings_stealth_title),
                            subtitle = if (isStealthEnabled) stringResource(R.string.settings_stealth_sub_on) else stringResource(R.string.settings_stealth_sub_off),
                            index = 0, total = if (hasStealthPin) 2 else 1,
                            trailing = {
                                VlSwitch(
                                    appTheme = currentTheme,
                                    checked = isStealthEnabled,
                                    onCheckedChange = { checked ->
                                        haptic.perform(HapticType.SELECTION, hapticEnabled)
                                        if (checked) {
                                            if (hasStealthPin) {
                                                stealthManager.setEnabled(true)
                                                isStealthEnabled = true
                                                Toast.makeText(context, context.getString(R.string.settings_stealth_sub_on), Toast.LENGTH_SHORT).show()
                                            } else {
                                                showStealthSetup = true
                                            }
                                        } else {
                                            showStealthDisable = true
                                        }
                                    }
                                )
                            }
                        )

                        if (hasStealthPin) {
                            VlSettingsItem(
                                appTheme = currentTheme,
                                iconColor = colorStealthPin,
                                icon = Icons.Default.Password,
                                title = stringResource(R.string.settings_stealth_change_pin),
                                subtitle = stringResource(R.string.settings_stealth_change_pin_sub),
                                index = 1, total = 2,
                                onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    showStealthChangePin = true
                                }
                            )
                        }
                    }

                    // ── Память ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_section_storage)) {
                        VlSettingsItem(appTheme = currentTheme, iconColor = colorStorage, icon = Icons.Default.Storage, title = stringResource(R.string.settings_cache_title), subtitle = stringResource(R.string.settings_cache_subtitle), onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenCacheSettings() }, index = 0, total = 2)
                        VlSettingsItem(appTheme = currentTheme, iconColor = colorStorage, icon = Icons.Default.CloudQueue, title = stringResource(R.string.storage_title), subtitle = stringResource(R.string.storage_subtitle), onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenStorageManager() }, index = 1, total = 2)
                    }

                    // ── Боты ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.stickers_title)) {
                        VlSettingsItem(appTheme = currentTheme, iconColor = colorBots, icon = Icons.Default.SmartToy, title = stringResource(R.string.settings_bots_title), subtitle = stringResource(R.string.settings_bots_subtitle), onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); showBotsManager = true }, index = 0, total = 1)
                    }

                    // ── Администрирование ──
                    if (profile?.isAdmin == true) {
                        VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_admin_panel)) {
                            VlSettingsItem(appTheme = currentTheme, icon = Icons.Default.AdminPanelSettings, iconColor = MaterialTheme.colorScheme.error, title = "Admin Panel", onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); showAdminPanel = true }, index = 0, total = 1)
                        }
                    }

                    // ── Обновления ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_section_updates)) {
                        VlSettingsItem(appTheme = currentTheme, iconColor = colorUpdateChan, icon = Icons.Default.Science, title = stringResource(R.string.settings_update_channel), subtitle = stringResource(R.string.settings_update_channel_current, currentChannel.title), onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); showChannelDialog = true }, index = 0, total = 2)
                        VlSettingsItem(
                            appTheme = currentTheme,
                            iconColor = colorUpdateCheck,
                            icon = Icons.Default.Sync,
                            title = stringResource(R.string.settings_check_updates),
                            subtitle = stringResource(R.string.settings_check_updates_sub),
                            onClick = {
                                haptic.perform(HapticType.SUCCESS, hapticEnabled)
                                Toast.makeText(context, context.getString(R.string.settings_checking_updates), Toast.LENGTH_SHORT).show()
                                appUpdateViewModel.checkForUpdates(isManual = true) { hasUpdate ->
                                    if (!hasUpdate) {
                                        Toast.makeText(context, context.getString(R.string.settings_up_to_date), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            index = 1,
                            total = 2
                        )
                    }

                    // ── Аккаунт ──
                    VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_section_account)) {
                        VlSettingsItem(appTheme = currentTheme, iconColor = colorEmail, icon = Icons.Default.Email, title = "Email", subtitle = profile?.email ?: "", index = 0, total = 4)
                        VlSettingsItem(
                            appTheme = currentTheme,
                            iconColor = Color(0xFF2AABEE),
                            icon = Icons.Default.Send,
                            title = if (profile?.tg_username != null) stringResource(R.string.settings_tg_linked, profile?.tg_username ?: "") else stringResource(R.string.settings_tg_link),
                            subtitle = if (profile?.tg_username != null) stringResource(R.string.settings_tg_linked_sub) else stringResource(R.string.settings_tg_binding_subtitle),
                            index = 1, total = 4,
                            onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                if (profile?.tg_username != null) {
                                    scope.launch {
                                        try {
                                            Firebase.functions("europe-west1").getHttpsCallable("unlinkTelegram").call().await()
                                            Toast.makeText(context, context.getString(R.string.settings_tg_unlinked_toast), Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "${context.getString(R.string.toast_save_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    showTgBindingDialog = true
                                    isGeneratingTgCode = true
                                    tgCode = null
                                    tgError = null
                                    scope.launch {
                                        try {
                                            val result = Firebase.functions("europe-west1").getHttpsCallable("generateTgCode").call().await()
                                            val data = result.data as Map<*, *>
                                            if (data["success"] == true) {
                                                tgCode = data["code"] as String
                                            } else {
                                                tgError = "Ошибка: ${data["error"]}"
                                            }
                                        } catch (e: Exception) {
                                            tgError = e.message ?: "Неизвестная ошибка сети"
                                        } finally {
                                            isGeneratingTgCode = false
                                        }
                                    }
                                }
                            }
                        )
                        VlSettingsItem(appTheme = currentTheme, iconColor = colorPassword, icon = Icons.Default.Password, title = stringResource(R.string.settings_password_change), onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); showPasswordDialog = true }, index = 2, total = 4)
                        VlSettingsItem(appTheme = currentTheme, iconColor = MaterialTheme.colorScheme.error, icon = Icons.AutoMirrored.Filled.Logout, title = stringResource(R.string.settings_logout), isDestructive = true, onClick = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); showLogoutDialog = true }, index = 3, total = 4)
                    }

                    // ── About ──
                    VlSettingsSection(appTheme = currentTheme, title = "О приложении") {
                        if (flags.isAegisDebugMode && flags.testFlag) {
                            VlSettingsItem(
                                appTheme = currentTheme,
                                iconColor = Color(0xFFF43F5E),
                                icon = Icons.Default.Terminal,
                                title = "Aegis Project Debug",
                                subtitle = "Доступ к внутренним тестам эвристики Линка",
                                index = 0, total = 2,
                                onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    onOpenAegisDebug()
                                }
                            )
                        }

                        VlSettingsItem(
                            appTheme = currentTheme,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = Icons.Default.Info,
                            title = "VisorLink",
                            subtitle = "Версия $versionString\nDevice ID: $deviceId",
                            index = if (flags.isAegisDebugMode && flags.testFlag) 1 else 0,
                            total = if (flags.isAegisDebugMode && flags.testFlag) 2 else 1,
                            onClick = {
                                if (deviceId != "Not paired") {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Device ID", deviceId)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Device ID скопирован", Toast.LENGTH_SHORT).show()
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(padding.calculateBottomPadding() + 32.dp))
                }
            }
        }
    }

    // ── ДИАЛОГИ STEALTH MODE ──
    if (showStealthSetup) {
        StealthSetupDialog(
            appTheme = currentTheme,
            onDismiss = { showStealthSetup = false },
            onConfirm = { pin ->
                stealthManager.setPin(pin)
                stealthManager.setEnabled(true)
                isStealthEnabled = true
                hasStealthPin = true
                showStealthSetup = false
                Toast.makeText(context, "Режим скрытия включён", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showStealthDisable) {
        StealthDisableDialog(
            appTheme = currentTheme,
            stealthManager = stealthManager,
            onDismiss = { showStealthDisable = false },
            onSuccess = {
                stealthManager.setEnabled(false)
                isStealthEnabled = false
                showStealthDisable = false
                Toast.makeText(context, "Режим скрытия отключён", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showStealthChangePin) {
        StealthChangePinDialog(
            appTheme = currentTheme,
            stealthManager = stealthManager,
            onDismiss = { showStealthChangePin = false },
            onSuccess = { newPin ->
                stealthManager.setPin(newPin)
                showStealthChangePin = false
                Toast.makeText(context, "PIN-код обновлён", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ── ДИАЛОГ TELEGRAM ACCOUNT BINDING ──
    if (showTgBindingDialog) {
        VlAlertDialog(
            appTheme = currentTheme,
            onDismissRequest = { showTgBindingDialog = false },
            title = { Text("Привязка Telegram") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGeneratingTgCode) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
                    } else if (tgError != null) {
                        Text(tgError!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    } else if (tgCode != null) {
                        Text(
                            text = tgCode!!,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 8.sp
                            ),
                            color = Color(0xFF2AABEE),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        Text(
                            "Отправьте нашему боту в Telegram команду:",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Text(
                                "/start ${tgCode!!}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            "Срок действия кода — 5 минут.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                VlDialogButton(onClick = { showTgBindingDialog = false }, appTheme = currentTheme) {
                    Text("Закрыть", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    // ── ДРУГИЕ ДИАЛОГИ ──
    if (showAdminPanel) { AdminPanelSheet { showAdminPanel = false } }
    if (showBotsManager) { BotsManagerSheet { showBotsManager = false } }

    if (showChannelDialog) {
        ChannelSelectionDialog(
            currentChannel = currentChannel,
            onDismiss = { showChannelDialog = false },
            onSelect = { channel ->
                appUpdateViewModel.setChannel(channel) { success ->
                    if (!success) {
                        Toast.makeText(context, "Отказано в доступе к каналу (возможно Canary?)", Toast.LENGTH_SHORT).show()
                    }
                }
                showChannelDialog = false
            }
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            appTheme = currentTheme,
            onDismiss = { showPasswordDialog = false },
            onConfirm = { current, newPass ->
                scope.launch {
                    try {
                        val user = FirebaseAuth.getInstance().currentUser ?: throw Exception("Not logged in")
                        val cred = EmailAuthProvider.getCredential(user.email!!, current)
                        user.reauthenticate(cred).await()
                        user.updatePassword(newPass).await()
                        Toast.makeText(context, "Пароль изменён", Toast.LENGTH_SHORT).show()
                        showPasswordDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showLogoutDialog) {
        VlAlertDialog(
            appTheme = currentTheme,
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            actions = {
                VlDialogButton(appTheme = currentTheme, onClick = { showLogoutDialog = false }) { Text("Отмена") }
                VlDialogButton(appTheme = currentTheme, isDestructive = true, onClick = {
                    showLogoutDialog = false
                    authRepository.logout()
                }) { Text("Выйти") }
            }
        )
    }
}

// ── PRO Status Banner ─────────────────────────────────────────────────────────

@Composable
private fun ProStatusBanner(
    profile: UserProfile,
    proViewModel: ProViewModel,
    proState: ProUiState,
    appTheme: AppTheme,
    isDark: Boolean,
    hapticEnabled: Boolean
) {
    val haptic = rememberHaptic()
    val isActive = profile.isProActive()
    val canAfford = profile.bits >= 1000

    val cal = Calendar.getInstance()
    profile.proUntil?.toDate()?.let { cal.time = it }
    val isEternalPro = profile.proUntil != null && cal.get(Calendar.YEAR) > 2090

    val goldColor = Color(0xFFC5A059)
    val cs = MaterialTheme.colorScheme
    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge

    fun formatDate(date: java.util.Date?): String {
        if (date == null) return ""
        return java.text.SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date)
    }

    VlSettingsSection(appTheme = appTheme, title = stringResource(R.string.pro_title), isPremium = true) {
        VlSettingsItem(
            appTheme = appTheme,
            icon = if (isActive) Icons.Default.WorkspacePremium else Icons.Default.Stars,
            iconColor = if (isActive) goldColor else cs.onSurfaceVariant,
            title = if (isActive) stringResource(R.string.pro_status_active) else stringResource(R.string.pro_status_inactive),
            subtitle = if (isEternalPro) stringResource(R.string.pro_eternal) else (if (isActive) stringResource(R.string.pro_until, formatDate(profile.proUntil?.toDate())) else stringResource(R.string.pro_unlock_hint)),
            index = 0, total = if (isActive) 3 else 4
        )

        VlSettingsItem(
            appTheme = appTheme,
            icon = Icons.Default.Toll,
            iconColor = goldColor,
            title = stringResource(R.string.pro_bits),
            trailing = {
                Text(
                    profile.bits.toString(),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = goldColor)
                )
            },
            index = 1, total = if (isActive) 3 else 4
        )

        VlSettingsItem(
            appTheme = appTheme,
            icon = Icons.Default.LocalFireDepartment,
            iconColor = Color(0xFFE11D48),
            title = stringResource(R.string.pro_streak, profile.streak),
            subtitle = stringResource(R.string.pro_streak_sub),
            trailing = {
                VlSwitch(
                    appTheme = appTheme,
                    checked = profile.showStreak,
                    onCheckedChange = { v ->
                        haptic.perform(HapticType.SELECTION, hapticEnabled)
                        proViewModel.toggleShowStreak(v)
                    }
                )
            },
            index = 2, total = if (isActive) 3 else 4
        )

        if (!isActive && !isEternalPro) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.pro_description),
                    style = MaterialTheme.typography.bodySmall.copy(color = cs.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE || appTheme == AppTheme.ONE_UI) {
                    Button(
                        onClick = {
                            if (canAfford) {
                                haptic.perform(HapticType.SUCCESS, hapticEnabled)
                                proViewModel.buyPro(useTrial = false)
                            }
                        },
                        enabled = canAfford && !proState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = goldColor, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (proState.isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text(stringResource(R.string.pro_action_buy, 1000), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                } else {
                    VlSurface(
                        appTheme = appTheme,
                        isButton = true,
                        customRadius = if (isForge) 0.dp else 16.dp,
                        overrideColor = style.cardBg,
                        onClick = if (canAfford && !proState.isLoading) {
                            {
                                haptic.perform(HapticType.SUCCESS, hapticEnabled)
                                proViewModel.buyPro(useTrial = false)
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        if (proState.isLoading) {
                            CircularProgressIndicator(color = goldColor, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                stringResource(R.string.pro_action_buy, 1000),
                                color = if (canAfford) goldColor else cs.onSurfaceVariant.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                fontFamily = if (isForge) FontFamily.Monospace else null
                            )
                        }
                    }
                }

                if (!profile.trialUsed) {
                    Spacer(Modifier.height(12.dp))
                    if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE || appTheme == AppTheme.ONE_UI) {
                        OutlinedButton(
                            onClick = {
                                if (!proState.isLoading) {
                                    haptic.perform(HapticType.SUCCESS, hapticEnabled)
                                    proViewModel.buyPro(useTrial = true)
                                }
                            },
                            enabled = !proState.isLoading,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = goldColor),
                            border = BorderStroke(1.dp, goldColor),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(stringResource(R.string.pro_action_trial), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        VlSurface(
                            appTheme = appTheme,
                            isButton = true,
                            customRadius = if (isForge) 0.dp else 16.dp,
                            overrideColor = style.cardBg,
                            onClick = if (!proState.isLoading) {
                                {
                                    haptic.perform(HapticType.SUCCESS, hapticEnabled)
                                    proViewModel.buyPro(useTrial = true)
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(
                                stringResource(R.string.pro_action_trial),
                                color = cs.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = if (isForge) FontFamily.Monospace else null
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Stealth Dialogs ──────────────────────────────────────────────────────────

@Composable
private fun StealthSetupDialog(appTheme: AppTheme, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    VlAlertDialog(
        appTheme = appTheme,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_stealth_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.dialog_stealth_setup_text), fontSize = 13.sp)
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it; error = null },
                    label = { Text(stringResource(R.string.dialog_stealth_setup_pin)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it; error = null },
                    label = { Text(stringResource(R.string.dialog_stealth_setup_confirm)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        actions = {
            VlDialogButton(appTheme = appTheme, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            VlDialogButton(appTheme = appTheme, isPrimary = true, onClick = {
                if (pin.length < 4) error = "Минимум 4 цифры"
                else if (pin != confirm) error = "PIN-коды не совпадают"
                else onConfirm(pin)
            }) { Text(stringResource(R.string.action_accept)) }
        }
    )
}

@Composable
private fun StealthDisableDialog(appTheme: AppTheme, stealthManager: StealthManager, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    VlAlertDialog(
        appTheme = appTheme,
        onDismissRequest = onDismiss,
        title = { Text("Отключить режим скрытия?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Введите текущий PIN-код для подтверждения.", fontSize = 13.sp)
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it; error = null },
                    label = { Text("Текущий PIN-код") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        actions = {
            VlDialogButton(appTheme = appTheme, onClick = onDismiss) { Text("Отмена") }
            VlDialogButton(appTheme = appTheme, isDestructive = true, onClick = {
                if (stealthManager.verifyPin(pin)) onSuccess()
                else error = "Неверный PIN-код"
            }) { Text("Отключить") }
        }
    )
}

@Composable
private fun StealthChangePinDialog(appTheme: AppTheme, stealthManager: StealthManager, onDismiss: () -> Unit, onSuccess: (String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    VlAlertDialog(
        appTheme = appTheme,
        onDismissRequest = onDismiss,
        title = { Text("Смена PIN-кода") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = current, onValueChange = { current = it; error = null },
                    label = { Text("Текущий PIN-код") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin, onValueChange = { newPin = it; error = null },
                    label = { Text("Новый PIN-код") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it; error = null },
                    label = { Text("Повторите новый PIN-код") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        actions = {
            VlDialogButton(appTheme = appTheme, onClick = onDismiss) { Text("Отмена") }
            VlDialogButton(appTheme = appTheme, isPrimary = true, onClick = {
                if (!stealthManager.verifyPin(current)) error = "Неверный текущий PIN-код"
                else if (newPin.length < 4) error = "Минимум 4 цифры"
                else if (newPin != confirm) error = "Новые PIN-коды не совпадают"
                else onSuccess(newPin)
            }) { Text("Сохранить") }
        }
    )
}

@Composable
fun ChangePasswordDialog(appTheme: AppTheme, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    VlAlertDialog(
        appTheme = appTheme,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_password_change_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = current, onValueChange = { current = it; error = null },
                    label = { Text(stringResource(R.string.dialog_password_current)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPass, onValueChange = { newPass = it; error = null },
                    label = { Text(stringResource(R.string.dialog_password_new)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it; error = null },
                    label = { Text(stringResource(R.string.dialog_password_confirm)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        actions = {
            VlDialogButton(appTheme = appTheme, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            VlDialogButton(appTheme = appTheme, isPrimary = true, onClick = {
                if (newPass.length < 6) error = "Min 6 characters"
                else if (newPass != confirm) error = "Passwords don't match"
                else onConfirm(current, newPass)
            }) { Text(stringResource(R.string.action_save)) }
        }
    )
}

// ── Admin & Bot Sheets ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    var botUid by remember { mutableStateOf("") }
    var userUid by remember { mutableStateOf("") }
    var channelId by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Админ-панель", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("🤖 Управление ботами", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = botUid, onValueChange = { botUid = it }, label = { Text("UID бота") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { isSaving=true; try { Firebase.functions("europe-west1").getHttpsCallable("adminBanBot").call(mapOf("botUid" to botUid, "banned" to true)).await(); Toast.makeText(context, "Забанен", Toast.LENGTH_SHORT).show() } catch(e:Exception){Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()} finally{isSaving=false} } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Ban") }
                Button(onClick = { scope.launch { isSaving=true; try { Firebase.functions("europe-west1").getHttpsCallable("adminBanBot").call(mapOf("botUid" to botUid, "banned" to false)).await(); Toast.makeText(context, "Разбанен", Toast.LENGTH_SHORT).show() } catch(e:Exception){} finally{isSaving=false} } }, modifier = Modifier.weight(1f)) { Text("Unban") }
            }

            HorizontalDivider(Modifier.padding(vertical = 24.dp))

            Text("📢 Каналы", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = channelId, onValueChange = { channelId = it }, label = { Text("Chat ID канала") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { isSaving=true; try { Firebase.functions("europe-west1").getHttpsCallable("adminVerifyChannel").call(mapOf("chatId" to channelId, "badge" to "official")).await(); Toast.makeText(context, "Верифицирован", Toast.LENGTH_SHORT).show() } catch(e:Exception){} finally{isSaving=false} } }, modifier = Modifier.fillMaxWidth()) { Text("Верифицировать") }

            HorizontalDivider(Modifier.padding(vertical = 24.dp))

            Text("👤 Пользователи", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = userUid, onValueChange = { userUid = it }, label = { Text("UID пользователя (пусто = себе)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { isSaving=true; try { Firebase.functions("europe-west1").getHttpsCallable("adminGrantEternalPro").call(mapOf("targetUid" to userUid)).await(); Toast.makeText(context, "Вечный PRO выдан", Toast.LENGTH_SHORT).show() } catch(e:Exception){} finally{isSaving=false} } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC5A059))) { Text("Выдать Вечный PRO") }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotsManagerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val botRepository: BotRepository = koinInject()

    var bots by remember { mutableStateOf<List<DmBot>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var botName by remember { mutableStateOf("") }
    var botUsername by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            isLoading = true
            try {
                bots = botRepository.listBots()
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_bots_manager_title), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            if (isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else {
                Text(stringResource(R.string.settings_bots_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (bots.isEmpty()) {
                    Text(stringResource(R.string.settings_bots_empty), modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    bots.forEach { bot ->
                        BotItem(
                            bot = bot,
                            onRegenerate = {
                                scope.launch {
                                    try {
                                        val newToken = botRepository.regenerateToken(bot.uid)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("bot token", newToken)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, context.getString(R.string.settings_bots_token_copied), Toast.LENGTH_LONG).show()
                                        refresh()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "${context.getString(R.string.toast_save_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    try {
                                        botRepository.deleteBot(bot.uid)
                                        refresh()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "${context.getString(R.string.action_delete)} ${context.getString(R.string.toast_save_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Text(stringResource(R.string.settings_bots_create_title), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = botName, onValueChange = { botName = it }, label = { Text(stringResource(R.string.settings_bots_field_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = botUsername, onValueChange = { botUsername = it }, label = { Text(stringResource(R.string.settings_bots_field_username)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (botName.isBlank() || botUsername.isBlank()) return@Button
                        scope.launch {
                            isSaving = true
                            try {
                                val token = botRepository.createBot(botName, botUsername)
                                Toast.makeText(context, "${context.getString(R.string.settings_info_saved)} Token: $token", Toast.LENGTH_LONG).show()
                                botName = ""; botUsername = ""
                                refresh()
                            } catch(e: Exception) {
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.action_accept))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun BotItem(bot: DmBot, onRegenerate: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(bot.name, fontWeight = FontWeight.Bold)
                    Text("@${bot.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }
            if (bot.token != null) {
                Spacer(Modifier.height(8.dp))
                Text("Token: ${bot.token}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            TextButton(onClick = onRegenerate) {
                Text(stringResource(R.string.settings_bots_token_regenerate))
            }
        }
    }
}

// ── Utils ─────────────────────────────────────────────────────────────────────

@Composable
fun ChannelSelectionDialog(
    currentChannel: UpdateChannel,
    onDismiss: () -> Unit,
    onSelect: (UpdateChannel) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Канал обновлений", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                UpdateChannel.entries.forEach { channel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(channel) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentChannel == channel, onClick = { onSelect(channel) })
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(channel.title, fontWeight = FontWeight.Medium)
                            Text("Файл: ${channel.fileName}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}