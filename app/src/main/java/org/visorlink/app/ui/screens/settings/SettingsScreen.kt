package org.visorlink.app.ui.screens.settings

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.BuildConfig
import org.visorlink.app.R
import org.visorlink.app.data.model.*
import org.visorlink.app.data.repository.AuthRepository
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.components.*
import org.visorlink.app.ui.components.settings.*
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.ThemeViewModel
import org.visorlink.app.utils.AppLanguage
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.StealthManager
import org.visorlink.app.utils.UpdateManager
import org.visorlink.app.utils.rememberHaptic
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
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
    onOpenStatus: () -> Unit = {},
    onOpenCustomization: () -> Unit = {},
    onOpenAegisDebug: () -> Unit = {},
    onOpenFlagFlipper: () -> Unit = {},
    onOpenAnimationTest: () -> Unit = {},
    onNavigateToAppCheckDiagnostic: () -> Unit = {},
    themeViewModel: ThemeViewModel = koinViewModel(),
    userRepository: UserRepository = koinInject(),
    authRepository: AuthRepository = koinInject(),
    flagsRepository: FlagsRepository = koinInject(),
    proViewModel: ProViewModel = koinViewModel(),
    authViewModel: org.visorlink.app.ui.screens.auth.AuthViewModel = koinViewModel()
) {
    val currentTheme by themeViewModel.appTheme.collectAsState()
    val currentMode by themeViewModel.themeMode.collectAsState()
    val currentPreset by themeViewModel.colorPreset.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val notifEnabled by themeViewModel.notificationsEnabled.collectAsState()
    val currentLang by themeViewModel.language.collectAsState()
    val dynamicInput by themeViewModel.dynamicChatInput.collectAsState()
    val compactChatList by themeViewModel.compactChatList.collectAsState()
    val showDebugIds by themeViewModel.showDebugIds.collectAsState()

    val profileFlow = remember(userRepository) { userRepository.currentUserFlow() }
    val profile by profileFlow.collectAsState(initial = null)
    val proState by proViewModel.uiState.collectAsState()
    val flags by flagsRepository.flags.collectAsState()

    val context = LocalContext.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()

    val stealthManager = remember { StealthManager(context) }
    var isStealthEnabled by remember { mutableStateOf(stealthManager.isEnabled()) }
    var hasStealthPin by remember { mutableStateOf(stealthManager.hasPin()) }
    var isStealthBiometricEnabled by remember { mutableStateOf(stealthManager.isBiometricUnlockEnabled()) }

    var showStealthSetup by remember { mutableStateOf(false) }
    var showStealthDisable by remember { mutableStateOf(false) }
    var showStealthChangePin by remember { mutableStateOf(false) }

    var showTgBindingDialog by remember { mutableStateOf(false) }
    var tgCode by remember { mutableStateOf<String?>(null) }
    var isGeneratingTgCode by remember { mutableStateOf(false) }
    var tgError by remember { mutableStateOf<String?>(null) }

    var showDevMenuSheet by remember { mutableStateOf(false) }
    var devTapCount by remember { mutableIntStateOf(0) }
    var lastDevTapTime by remember { mutableLongStateOf(0L) }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showLegalDialog by remember { mutableStateOf(false) }
    var showBugReportSheet by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf(false) }

    val buildDate = remember { SimpleDateFormat("yyyyMMdd.HHmm", Locale.getDefault()).format(Date(BuildConfig.BUILD_TIMESTAMP)) }
    val commitHash = BuildConfig.CommitID.takeIf { it.isNotBlank() } ?: "unknown"
    val versionString = "${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}.$buildDate [$commitHash]"

    val colorNotif = Color(0xFFF59E0B)
    val colorVibro = Color(0xFFEC4899)
    val colorDynInput = Color(0xFF10B981)
    val colorCompact = Color(0xFF3B82F6)
    val colorStealth = Color(0xFF8B5CF6)
    val colorStealthPin = Color(0xFF6366F1)
    val colorStealthBiometric = Color(0xFF14B8A6)
    val colorStorage = Color(0xFF3B82F6)
    val colorBots = Color(0xFF14B8A6)
    val colorEmail = Color(0xFF64748B)
    val colorPassword = Color(0xFFF43F5E)
    val colorSecurity = Color(0xFF10B981)
    val colorUpdateChan = Color(0xFF6366F1)
    val colorUpdateCheck = Color(0xFF10B981)

    val isLiquidEnabled = flags.isEnabled("animation_test")
    val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)

    LaunchedEffect(Unit) {
        if (isLiquidEnabled) {
            topBarJelly.pulse(0.06f)
        }
    }

    LaunchedEffect(proState.successMessage) { proState.successMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); proViewModel.clearMessages() } }
    LaunchedEffect(proState.error) { proState.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); proViewModel.clearMessages() } }

    LaunchedEffect(profile?.tg_username) {
        if (showTgBindingDialog && profile?.tg_username != null) {
            showTgBindingDialog = false
            isGeneratingTgCode = false
            tgCode = null
            Toast.makeText(context, "Telegram успешно привязан!", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VlAmbientGlow()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                VlTopAppBar(
                    modifier = Modifier.liquidJelly(topBarJelly, enabled = isLiquidEnabled),
                    title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            if (isLiquidEnabled) topBarJelly.press(0.06f)
                            onNavigateBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .progressiveEdgeBlur(
                        topBlur = 10.dp,
                        bottomBlur = 10.dp,
                        enabled = !VlTheme.tokens.reduceMotion
                    )
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(padding.calculateTopPadding() + 8.dp))

                profile?.let { p ->
                    val tokens = VlTheme.tokens
                    val cs = MaterialTheme.colorScheme
                    val accountShape = if (isLiquidEnabled) RoundedCornerShape(32.dp) else tokens.shapes.card
                    val isDark = cs.surface.luminance() < 0.5f

                    val accountBrush = remember(isLiquidEnabled, isDark, cs) {
                        if (isLiquidEnabled) {
                            val top = if (isDark) cs.surfaceContainer.copy(alpha = 0.95f) else cs.surfaceContainerLow.copy(alpha = 0.98f)
                            val bottom = if (isDark) cs.surfaceContainerLow.copy(alpha = 0.88f) else cs.surfaceContainer.copy(alpha = 0.92f)
                            Brush.verticalGradient(listOf(top, bottom))
                        } else null
                    }
                    val accountBorder = remember(isLiquidEnabled, isDark, cs) {
                        if (isLiquidEnabled) {
                            val topHighlight = if (isDark) cs.outlineVariant.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.60f)
                            val bottomShadow = if (isDark) cs.outlineVariant.copy(alpha = 0.04f) else cs.outlineVariant.copy(alpha = 0.12f)
                            BorderStroke(1.dp, Brush.verticalGradient(listOf(topHighlight, bottomShadow)))
                        } else null
                    }

                    Box(
                        modifier = Modifier
                            .liquidPillCardSlideOut(index = 0, enabled = isLiquidEnabled)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .then(
                                if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, accountShape)
                                else Modifier
                            )
                            .clip(accountShape)
                            .then(
                                if (accountBrush != null) Modifier.background(accountBrush, accountShape)
                                else Modifier.background(
                                    if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerLow,
                                    accountShape
                                )
                            )
                            .then(
                                if (accountBorder != null) Modifier.border(accountBorder, accountShape)
                                else if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, accountShape)
                                else Modifier
                            )
                    ) {
                        CompositionLocalProvider(LocalContentColor provides cs.onSurface) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarWithPresence(
                                    avatarUrl = p.avatarUrl,
                                    displayName = p.displayName.ifEmpty { p.username },
                                    isOnline = true,
                                    size = 64.dp
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = p.displayName.ifEmpty { p.username },
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = cs.onSurface,
                                            maxLines = 1
                                        )
                                        if (p.isProActive()) {
                                            Spacer(Modifier.width(8.dp))
                                            ProBadge()
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "@${p.username}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (p.bio?.isNotBlank() == true) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = p.bio,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.liquidPillCardSlideOut(index = 1, enabled = isLiquidEnabled)) {
                        ProStatusBanner(profile = p, proViewModel = proViewModel, proState = proState, hapticEnabled = hapticEnabled)
                    }
                }

                VlSettingsSection(
                    title = stringResource(R.string.settings_custom_title),
                    modifier = Modifier.liquidPillCardSlideOut(index = 2, enabled = isLiquidEnabled),
                    isPremium = true
                ) {
                    VlSettingsItem(icon = Icons.Default.Brush, title = stringResource(R.string.settings_custom_design_title), subtitle = stringResource(R.string.settings_custom_design_sub), onClick = { if (profile?.isProActive() == true) onOpenCustomization() else Toast.makeText(context, context.getString(R.string.settings_custom_pro_only), Toast.LENGTH_SHORT).show() })
                    VlSettingsItem(icon = Icons.Default.HideImage, title = stringResource(R.string.settings_custom_hide_title), subtitle = stringResource(R.string.settings_custom_hide_sub), trailing = { VlSwitch(checked = profile?.ignoreCustomizations ?: false, onCheckedChange = { v -> scope.launch { userRepository.updateIgnoreCustomizations(v) } }) })
                }

                VlSettingsSection(
                    title = stringResource(R.string.settings_section_theme),
                    modifier = Modifier.liquidPillCardSlideOut(index = 3, enabled = isLiquidEnabled)
                ) {
                    VlThemeSelector(
                        selected = currentTheme,
                        onSelect = { themeViewModel.setTheme(it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        themeMode = currentMode,
                        colorPreset = currentPreset,
                    )
                }

                VlSettingsSection(
                    title = stringResource(R.string.settings_section_accent),
                    modifier = Modifier.liquidPillCardSlideOut(index = 4, enabled = isLiquidEnabled)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        ColorPreset.entries.forEach { preset ->
                            ColorPresetCircle(preset = preset, isSelected = currentPreset == preset, onClick = { themeViewModel.setColorPreset(preset) })
                        }
                    }
                }

                VlSettingsSection(
                    title = stringResource(R.string.settings_dark_title),
                    modifier = Modifier.liquidPillCardSlideOut(index = 5, enabled = isLiquidEnabled)
                ) {
                    VlOptionRow(icon = Icons.Default.SettingsBrightness, label = stringResource(R.string.settings_dark_system), desc = stringResource(R.string.settings_dark_system_desc), selected = currentMode == ThemeMode.SYSTEM, index = 0, total = 3, onClick = { themeViewModel.setThemeMode(ThemeMode.SYSTEM) })
                    VlOptionRow(icon = Icons.Default.LightMode, label = stringResource(R.string.settings_dark_light), desc = stringResource(R.string.settings_dark_light_desc), selected = currentMode == ThemeMode.LIGHT, index = 1, total = 3, onClick = { themeViewModel.setThemeMode(ThemeMode.LIGHT) })
                    VlOptionRow(icon = Icons.Default.DarkMode, label = stringResource(R.string.settings_dark_dark), desc = stringResource(R.string.settings_dark_dark_desc), selected = currentMode == ThemeMode.DARK, index = 2, total = 3, onClick = { themeViewModel.setThemeMode(ThemeMode.DARK) })
                }

                VlSettingsSection(
                    title = stringResource(R.string.settings_section_language),
                    modifier = Modifier.liquidPillCardSlideOut(index = 6, enabled = isLiquidEnabled)
                ) {
                    VlOptionRow(icon = Icons.Default.Language, label = stringResource(R.string.settings_language_system), selected = currentLang == AppLanguage.SYSTEM, index = 0, total = 3, onClick = { themeViewModel.setLanguage(AppLanguage.SYSTEM) })
                    VlOptionRow(icon = Icons.Default.Translate, label = stringResource(R.string.settings_language_en), selected = currentLang == AppLanguage.EN, index = 1, total = 3, onClick = { themeViewModel.setLanguage(AppLanguage.EN) })
                    VlOptionRow(icon = Icons.Default.GTranslate, label = stringResource(R.string.settings_language_ru), selected = currentLang == AppLanguage.RU, index = 2, total = 3, onClick = { themeViewModel.setLanguage(AppLanguage.RU) })
                }

                VlSettingsSection(
                    title = stringResource(R.string.settings_section_management),
                    modifier = Modifier.liquidPillCardSlideOut(index = 7, enabled = isLiquidEnabled)
                ) {
                    VlSettingsItem(icon = Icons.Default.NotificationsActive, iconColor = colorNotif, title = stringResource(R.string.settings_push_title), trailing = { VlSwitch(checked = notifEnabled, onCheckedChange = { themeViewModel.setNotifications(it) }) })
                    VlSettingsItem(icon = Icons.Default.Vibration, iconColor = colorVibro, title = stringResource(R.string.settings_haptic_title), trailing = { VlSwitch(checked = hapticEnabled, onCheckedChange = { themeViewModel.setHaptic(it) }) })
                    VlSettingsItem(icon = Icons.Default.KeyboardHide, iconColor = colorDynInput, title = "Динамическое поле ввода", trailing = { VlSwitch(checked = dynamicInput, onCheckedChange = { themeViewModel.setDynamicChatInput(it) }) })
                    VlSettingsItem(icon = Icons.Default.ViewAgenda, iconColor = colorCompact, title = "Компактный список чатов", trailing = { VlSwitch(checked = compactChatList, onCheckedChange = { themeViewModel.setCompactChatList(it) }) })
                    VlSettingsItem(icon = Icons.Default.Explore, iconColor = Color(0xFF10B981), title = "Discover (Лента)", subtitle = "Показывать вкладку с глобальной лентой", trailing = { VlSwitch(checked = themeViewModel.discoverEnabled.collectAsState().value, onCheckedChange = { themeViewModel.setDiscoverEnabled(it) }) })
                    val musicEnabled by themeViewModel.musicEnabled.collectAsState()
                    VlSettingsItem(
                        icon = Icons.Default.MusicNote,
                        iconColor = Color(0xFFEC4899),
                        title = stringResource(R.string.music_settings_title),
                        subtitle = stringResource(R.string.music_settings_desc),
                        trailing = {
                            VlSwitch(
                                checked = musicEnabled,
                                onCheckedChange = { themeViewModel.setMusicEnabled(it) }
                            )
                        }
                    )
                }

                VlSettingsSection(
                    title = stringResource(R.string.diary_title),
                    modifier = Modifier.liquidPillCardSlideOut(index = 8, enabled = isLiquidEnabled)
                ) {
                    VlSettingsItem(
                        icon = Icons.Default.Book,
                        iconColor = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.diary_enable_title),
                        subtitle = stringResource(R.string.diary_enable_sub),
                        trailing = {
                            VlSwitch(
                                checked = profile?.diaryEnabled ?: false,
                                onCheckedChange = { v ->
                                    haptic.perform(HapticType.SELECTION, hapticEnabled)
                                    val uid = profile?.uid ?: return@VlSwitch
                                    userRepository.updateDiaryEnabled(uid, v)
                                }
                            )
                        }
                    )
                    if (profile?.diaryEnabled == true) {
                        VlSettingsItem(
                            icon = Icons.Default.Notifications,
                            iconColor = Color(0xFFF59E0B),
                            title = stringResource(R.string.diary_reminders_title),
                            subtitle = stringResource(R.string.diary_reminders_sub, profile?.diaryReminderTime ?: "21:00"),
                            onClick = {
                                val parts = (profile?.diaryReminderTime ?: "21:00").split(":")
                                val picker = TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        val time = String.format(Locale.US, "%02d:%02d", h, m)
                                        scope.launch {
                                            val currentReminders = profile?.diaryRemindersEnabled ?: false
                                            userRepository.updateDiaryReminders(currentReminders, time)
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
                                    checked = profile?.diaryRemindersEnabled ?: false,
                                    onCheckedChange = { v ->
                                        haptic.perform(HapticType.SELECTION, hapticEnabled)
                                        scope.launch {
                                            val currentTime = profile?.diaryReminderTime ?: "21:00"
                                            userRepository.updateDiaryReminders(v, currentTime)
                                        }
                                    }
                                )
                            }
                        )
                    }
                }

                VlSettingsSection(
                    title = stringResource(R.string.settings_section_privacy),
                    modifier = Modifier.liquidPillCardSlideOut(index = 9, enabled = isLiquidEnabled)
                ) {
                    VlSettingsItem(icon = Icons.Default.VisibilityOff, iconColor = colorStealth, title = stringResource(R.string.settings_stealth_title), subtitle = if (isStealthEnabled) stringResource(R.string.settings_stealth_sub_on) else stringResource(R.string.settings_stealth_sub_off), trailing = { VlSwitch(checked = isStealthEnabled, onCheckedChange = { if (it) { if (hasStealthPin) { stealthManager.setEnabled(true); isStealthEnabled = true } else showStealthSetup = true } else showStealthDisable = true }) })
                    if (hasStealthPin) {
                        VlSettingsItem(icon = Icons.Default.Password, iconColor = colorStealthPin, title = stringResource(R.string.settings_stealth_change_pin), subtitle = stringResource(R.string.settings_stealth_change_pin_sub), onClick = { showStealthChangePin = true })
                    }
                    if (hasStealthPin && isStealthEnabled) {
                        VlSettingsItem(
                            icon = Icons.Default.Fingerprint,
                            iconColor = colorStealthBiometric,
                            title = stringResource(R.string.settings_stealth_biometric),
                            subtitle = stringResource(R.string.settings_stealth_biometric_sub),
                            trailing = {
                                VlSwitch(
                                    checked = isStealthBiometricEnabled,
                                    onCheckedChange = { enable ->
                                        if (enable) {
                                            val canAuth = BiometricManager.from(context)
                                                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                                                stealthManager.setBiometricUnlockEnabled(true)
                                                isStealthBiometricEnabled = true
                                                haptic.perform(HapticType.SUCCESS, hapticEnabled)
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.stealth_biometric_unavailable), Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            stealthManager.setBiometricUnlockEnabled(false)
                                            isStealthBiometricEnabled = false
                                        }
                                    }
                                )
                            }
                        )
                    }
                }

                VlSettingsSection(
                    title = stringResource(R.string.settings_section_storage),
                    modifier = Modifier.liquidPillCardSlideOut(index = 10, enabled = isLiquidEnabled)
                ) {
                    VlSettingsItem(icon = Icons.Default.Storage, iconColor = colorStorage, title = stringResource(R.string.settings_cache_title), onClick = onOpenCacheSettings)
                    VlSettingsItem(icon = Icons.Default.CloudQueue, iconColor = colorStorage, title = stringResource(R.string.storage_title), onClick = onOpenStorageManager)
                    VlSettingsItem(icon = Icons.Default.HealthAndSafety, iconColor = colorStorage, title = "Статус системы", onClick = onOpenStatus)
                }

                VlSettingsSection(
                    title = stringResource(R.string.settings_section_account),
                    modifier = Modifier.liquidPillCardSlideOut(index = 11, enabled = isLiquidEnabled)
                ) {
                    VlSettingsItem(icon = Icons.Default.Email, iconColor = colorEmail, title = "Email", subtitle = profile?.email ?: "")
                    VlSettingsItem(icon = Icons.Default.Security, iconColor = Color(0xFF10B981), title = stringResource(R.string.settings_tfa_title), subtitle = if (profile?.tfaEnabled == true) stringResource(R.string.settings_tfa_sub_on) else stringResource(R.string.settings_tfa_sub_off), trailing = { VlSwitch(checked = profile?.tfaEnabled ?: false, onCheckedChange = { v -> scope.launch { userRepository.updateTfaEnabled(v) } }) })
                    VlSettingsItem(
                        icon = Icons.AutoMirrored.Filled.Send,
                        iconColor = Color(0xFF2AABEE),
                        title = if (profile?.tg_username != null) stringResource(R.string.settings_tg_linked, profile?.tg_username ?: "") else stringResource(R.string.settings_tg_link),
                        subtitle = if (profile?.tg_username != null) stringResource(R.string.settings_tg_linked_sub) else stringResource(R.string.settings_tg_binding_subtitle),
                        onClick = {
                            if (profile?.tg_username != null) {
                                scope.launch {
                                    try {
                                        Firebase.functions("europe-west1").getHttpsCallable("unlinkTelegram").call().await()
                                        Toast.makeText(context, context.getString(R.string.settings_tg_unlinked_toast), Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) { Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show() }
                                }
                            } else {
                                showTgBindingDialog = true; isGeneratingTgCode = true; tgCode = null; tgError = null
                                scope.launch {
                                    try {
                                        val result = Firebase.functions("europe-west1").getHttpsCallable("generateTgCode").call().await()
                                        val data = result.data as Map<*, *>
                                        if (data["success"] == true) tgCode = data["code"] as String
                                        else tgError = "Ошибка: ${data["error"]}"
                                    } catch (e: Exception) { tgError = e.message ?: "Ошибка сети" } finally { isGeneratingTgCode = false }
                                }
                            }
                        }
                    )
                    VlSettingsItem(icon = Icons.Default.Password, iconColor = colorPassword, title = stringResource(R.string.settings_password_change), onClick = { showPasswordDialog = true })
                    VlSettingsItem(icon = Icons.AutoMirrored.Filled.Logout, iconColor = MaterialTheme.colorScheme.error, title = stringResource(R.string.settings_logout), isDestructive = true, onClick = { showLogoutDialog = true })
                    VlSettingsItem(
                        icon = Icons.Default.DeleteForever,
                        iconColor = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.settings_delete_account_title),
                        subtitle = stringResource(R.string.settings_delete_account_subtitle),
                        isDestructive = true,
                        onClick = { showDeleteAccountDialog = true }
                    )
                }

                if (UpdateManager.isSupported) {
                    VlSettingsSection(
                        title = stringResource(R.string.settings_section_updates),
                        modifier = Modifier.liquidPillCardSlideOut(index = 11, enabled = isLiquidEnabled)
                    ) {
                        VlSettingsItem(
                            icon = Icons.Default.Storefront,
                            iconColor = colorUpdateChan,
                            title = "Ipos Store",
                            subtitle = if (UpdateManager.isStoreInstalled()) "Клиент установлен (фоновые обновления)" else "Не установлен (нажмите для скачивания)",
                            onClick = {
                                if (!UpdateManager.isStoreInstalled()) {
                                    UpdateManager.openStoreDownload()
                                }
                            }
                        )
                        val channelSubtitle = when (UpdateManager.getSavedChannel(context)) {
                            "release" -> stringResource(R.string.settings_update_channel_release)
                            "beta" -> stringResource(R.string.settings_update_channel_beta)
                            "nightly" -> stringResource(R.string.settings_update_channel_nightly)
                            else -> UpdateManager.getSavedChannel(context)
                        }
                        VlSettingsItem(
                            icon = Icons.Default.Tune,
                            iconColor = colorUpdateChan,
                            title = stringResource(R.string.settings_update_channel),
                            subtitle = channelSubtitle,
                            onClick = {
                                showChannelDialog = true
                            }
                        )
                        VlSettingsItem(
                            icon = Icons.Default.Sync,
                            iconColor = colorUpdateCheck,
                            title = stringResource(R.string.settings_check_updates),
                            subtitle = stringResource(R.string.settings_check_updates_sub),
                            onClick = {
                                haptic.perform(HapticType.SUCCESS, hapticEnabled)
                                Toast.makeText(context, context.getString(R.string.settings_checking_updates), Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    UpdateManager.checkUpdate(context) { isAvailable, msg ->
                                        if (!isAvailable && UpdateManager.isStoreInstalled()) {
                                            Toast.makeText(context, context.getString(R.string.settings_up_to_date), Toast.LENGTH_SHORT).show()
                                        } else if (msg != null) {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                VlSettingsSection(
                    title = "О приложении",
                    modifier = Modifier.liquidPillCardSlideOut(index = 12, enabled = isLiquidEnabled)
                ) {
                    val uriHandler = LocalUriHandler.current
                    VlSettingsItem(
                        icon = Icons.Default.BugReport,
                        iconColor = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.settings_bug_report_title),
                        subtitle = stringResource(R.string.settings_bug_report_subtitle),
                        onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            showBugReportSheet = true
                        }
                    )
                    VlSettingsItem(
                        icon = Icons.Default.Info, 
                        title = "VisorLink", 
                        subtitle = "Версия $versionString",
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastDevTapTime > 1500L) {
                                devTapCount = 1
                            } else {
                                devTapCount++
                            }
                            lastDevTapTime = now

                            if (devTapCount >= 5) {
                                devTapCount = 0
                                haptic.perform(HapticType.SUCCESS, hapticEnabled)
                                showDevMenuSheet = true
                            }
                        }
                    )
                    VlSettingsItem(
                        icon = Icons.Default.Gavel,
                        iconColor = MaterialTheme.colorScheme.primary,
                        title = "Правовая информация",
                        subtitle = "Условия использования и конфиденциальность",
                        onClick = { showLegalDialog = true }
                    )
                    VlSettingsItem(
                        icon = Icons.Default.OpenInBrowser,
                        iconColor = MaterialTheme.colorScheme.primary,
                        title = "Политика конфиденциальности онлайн",
                        subtitle = "https://visorlink.org",
                        onClick = { uriHandler.openUri("https://visorlink.org") }
                    )
                }

                Spacer(Modifier.height(padding.calculateBottomPadding() + 32.dp))
            }
        }
    }

    if (showDevMenuSheet) {
        SecretDevMenuSheet(
            onDismiss = { showDevMenuSheet = false },
            onNavigateToAppCheckDiagnostic = onNavigateToAppCheckDiagnostic,
            onOpenAnimationTest = onOpenAnimationTest,
            onOpenAegisDebug = onOpenAegisDebug,
            onOpenFlagFlipper = onOpenFlagFlipper,
            showDebugIds = showDebugIds,
            onToggleDebugIds = { themeViewModel.setShowDebugIds(it) },
            versionString = versionString,
            clientFlagsId = remember(flags) { flagsRepository.getClientFlagsId() },
            hapticEnabled = hapticEnabled
        )
    }
    if (showBugReportSheet) BugReportSheet(onDismiss = { showBugReportSheet = false })
    if (showLogoutDialog) AlertDialog(onDismissRequest = { showLogoutDialog = false }, title = { Text("Выйти?") }, confirmButton = { TextButton(onClick = { showLogoutDialog = false; authViewModel.logout() }) { Text("Выйти") } }, dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена") } })

    if (showDeleteAccountDialog) {
        var password by remember { mutableStateOf("") }
        var isDeleting by remember { mutableStateOf(false) }
        var deleteError by remember { mutableStateOf<String?>(null) }
        val uriHandler = LocalUriHandler.current

        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteAccountDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.delete_account_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.delete_account_dialog_warning), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "https://visorlink.org/delete-account",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                        modifier = Modifier.clickable { uriHandler.openUri("https://visorlink.org/delete-account") }
                    )
                    VlTextField(
                        value = password,
                        onValueChange = { password = it; deleteError = null },
                        label = stringResource(R.string.delete_account_password_label),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = deleteError != null,
                        supportingText = deleteError,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.isNotBlank() && !isDeleting) {
                            isDeleting = true
                            deleteError = null
                            authViewModel.deleteAccount(
                                password = password,
                                onSuccess = {
                                    isDeleting = false
                                    showDeleteAccountDialog = false
                                    org.visorlink.app.utils.ChatDataCache.clearAll(context)
                                    Toast.makeText(context, "Аккаунт успешно удален", Toast.LENGTH_LONG).show()
                                },
                                onError = { err ->
                                    isDeleting = false
                                    deleteError = err
                                }
                            )
                        }
                    },
                    enabled = password.isNotBlank() && !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_account_in_progress))
                    } else {
                        Text(stringResource(R.string.delete_account_confirm_button))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false },
                    enabled = !isDeleting
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }



    if (showStealthSetup) {
        StealthSetupDialog(
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
            stealthManager = stealthManager,
            onDismiss = { showStealthDisable = false },
            onSuccess = {
                stealthManager.setEnabled(false)
                stealthManager.setBiometricUnlockEnabled(false)
                isStealthEnabled = false
                isStealthBiometricEnabled = false
                showStealthDisable = false
                Toast.makeText(context, "Режим скрытия отключён", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showStealthChangePin) {
        StealthChangePinDialog(
            stealthManager = stealthManager,
            onDismiss = { showStealthChangePin = false },
            onSuccess = { newPin ->
                stealthManager.setPin(newPin)
                showStealthChangePin = false
                Toast.makeText(context, "PIN-код обновлён", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showTgBindingDialog) {
        VlAlertDialog(
            onDismissRequest = { showTgBindingDialog = false },
            title = { Text("Привязка Telegram") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (isGeneratingTgCode) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
                    } else if (tgError != null) {
                        Text(tgError!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    } else if (tgCode != null) {
                        Text(text = tgCode!!, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp), color = Color(0xFF2AABEE), modifier = Modifier.padding(vertical = 16.dp))
                        Text("Отправьте нашему боту в Telegram команду:", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                            Text("/start ${tgCode!!}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
                        }
                        Text("Срок действия кода — 5 минут.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                    }
                }
            },
            actions = { VlDialogButton(onClick = { showTgBindingDialog = false }) { Text(stringResource(R.string.action_close)) } }
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { current, newPass ->
                scope.launch {
                    try {
                        val user = FirebaseAuth.getInstance().currentUser
                        val credential = EmailAuthProvider.getCredential(user?.email!!, current)
                        user.reauthenticate(credential).await()
                        user.updatePassword(newPass).await()
                        showPasswordDialog = false
                        Toast.makeText(context, context.getString(R.string.dialog_password_success), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) { Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        )
    }

    if (showLegalDialog) {
        val legalRepo: org.visorlink.app.data.repository.LegalRepository = koinInject()
        val latestVer by legalRepo.observeLatestVersion().collectAsState(initial = legalRepo.loadBundledVersion())
        org.visorlink.app.ui.screens.legal.LegalConsentDialog(
            currentVersion = latestVer,
            legalRepository = legalRepo,
            isReadOnly = true,
            onDismissReadOnly = { showLegalDialog = false }
        )
    }

    if (showChannelDialog && UpdateManager.isSupported) {
        val channels = UpdateManager.getAvailableChannels(context)
        VlAlertDialog(
            onDismissRequest = { showChannelDialog = false },
            title = { Text(stringResource(R.string.settings_update_channel_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    channels.forEach { (chKey, label) ->
                        val isSelected = UpdateManager.getSavedChannel(context) == chKey
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    UpdateManager.setChannel(context, chKey)
                                    showChannelDialog = false
                                    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            actions = {
                VlDialogButton(onClick = { showChannelDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun StealthSetupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    VlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_stealth_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.dialog_stealth_setup_text), fontSize = 13.sp)
                VlTextField(value = pin, onValueChange = { pin = it; error = null }, label = stringResource(R.string.dialog_stealth_setup_pin), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                VlTextField(value = confirm, onValueChange = { confirm = it; error = null }, label = stringResource(R.string.dialog_stealth_setup_confirm), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        actions = {
            VlDialogButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            VlDialogButton(isPrimary = true, onClick = {
                if (pin.length < 4) error = "Минимум 4 цифры"
                else if (pin != confirm) error = "PIN-коды не совпадают"
                else onConfirm(pin)
            }) { Text(stringResource(R.string.action_accept)) }
        }
    )
}

@Composable
private fun StealthDisableDialog(stealthManager: StealthManager, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    VlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отключить режим скрытия?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Введите текущий PIN-код для подтверждения.", fontSize = 13.sp)
                VlTextField(value = pin, onValueChange = { pin = it; error = null }, label = "Текущий PIN-код", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        actions = {
            VlDialogButton(onClick = onDismiss) { Text("Отмена") }
            VlDialogButton(isDestructive = true, onClick = {
                if (stealthManager.verifyPin(pin)) onSuccess()
                else error = "Неверный PIN-код"
            }) { Text("Отключить") }
        }
    )
}

@Composable
private fun StealthChangePinDialog(stealthManager: StealthManager, onDismiss: () -> Unit, onSuccess: (String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    VlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смена PIN-кода") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VlTextField(value = current, onValueChange = { current = it; error = null }, label = "Текущий PIN-код", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                VlTextField(value = newPin, onValueChange = { newPin = it; error = null }, label = "Новый PIN-код", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                VlTextField(value = confirm, onValueChange = { confirm = it; error = null }, label = "Повторите новый PIN-код", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        actions = {
            VlDialogButton(onClick = onDismiss) { Text("Отмена") }
            VlDialogButton(isPrimary = true, onClick = {
                if (!stealthManager.verifyPin(current)) error = "Неверный текущий PIN-код"
                else if (newPin.length < 4) error = "Минимум 4 цифры"
                else if (newPin != confirm) error = "Новые PIN-коды не совпадают"
                else onSuccess(newPin)
            }) { Text("Сохранить") }
        }
    )
}

@Composable
fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    VlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_password_change_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VlTextField(value = current, onValueChange = { current = it; error = null }, label = stringResource(R.string.dialog_password_current), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                VlTextField(value = newPass, onValueChange = { newPass = it; error = null }, label = stringResource(R.string.dialog_password_new), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                VlTextField(value = confirm, onValueChange = { confirm = it; error = null }, label = stringResource(R.string.dialog_password_confirm), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        actions = {
            VlDialogButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            VlDialogButton(isPrimary = true, onClick = {
                if (newPass.length < 6) error = "Min 6 characters"
                else if (newPass != confirm) error = "Passwords don't match"
                else onConfirm(current, newPass)
            }) { Text(stringResource(R.string.action_save)) }
        }
    )
}

@Composable
private fun ProStatusBanner(profile: UserProfile, proViewModel: ProViewModel, proState: ProUiState, hapticEnabled: Boolean) {
    VlSettingsSection(title = stringResource(R.string.pro_title), isPremium = true) {
        val expirySubtitle = if (profile.isProActive()) {
            val until = profile.proUntil
            val isEternal = until == null || (until.toDate().time - System.currentTimeMillis() > 365L * 24 * 3600 * 1000)
            if (isEternal) {
                "бесконечный PRO"
            } else {
                val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(until.toDate())
                "Активен до $dateStr"
            }
        } else null

        VlSettingsItem(
            icon = Icons.Default.WorkspacePremium,
            title = if (profile.isProActive()) stringResource(R.string.pro_status_active) else stringResource(R.string.pro_status_inactive),
            subtitle = expirySubtitle
        )
        VlSettingsItem(
            icon = Icons.Default.Toll,
            title = stringResource(R.string.pro_bits),
            trailing = { Text(profile.bits.toString(), fontWeight = FontWeight.Bold, color = Color(0xFFC5A059)) }
        )

        if (!profile.isProActive()) {
            val isLiquid = rememberLiquidEnabled()
            val buttonShape = if (isLiquid) RoundedCornerShape(20.dp) else RoundedCornerShape(12.dp)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (!profile.trialUsed) {
                    OutlinedButton(
                        onClick = { proViewModel.buyPro(true) },
                        enabled = !proState.isLoading,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = buttonShape
                    ) {
                        Text("Попробовать PRO бесплатно (1 день)")
                    }
                }
                Button(
                    onClick = { proViewModel.buyPro(false) },
                    enabled = !proState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = buttonShape
                ) {
                    if (proState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.pro_action_buy, 100))
                    }
                }
            }
        }

        if (proState.error != null) {
            Text(
                text = proState.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (proState.successMessage != null) {
            Text(
                text = proState.successMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecretDevMenuSheet(
    onDismiss: () -> Unit,
    onNavigateToAppCheckDiagnostic: () -> Unit,
    onOpenAnimationTest: () -> Unit,
    onOpenAegisDebug: () -> Unit,
    onOpenFlagFlipper: () -> Unit,
    showDebugIds: Boolean,
    onToggleDebugIds: (Boolean) -> Unit,
    versionString: String,
    clientFlagsId: String,
    hapticEnabled: Boolean,
) {
    val context = LocalContext.current
    val haptic = rememberHaptic()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Режим разработчика",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            VlSettingsItem(
                icon = Icons.Default.VerifiedUser,
                iconColor = Color(0xFF10B981),
                title = "App Check Диагностика",
                subtitle = "Проверка целостности и токенов Play Integrity / Debug",
                onClick = {
                    onDismiss()
                    onNavigateToAppCheckDiagnostic()
                }
            )

            VlSettingsItem(
                icon = Icons.Default.AutoAwesome,
                iconColor = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.settings_animation_test_title),
                subtitle = stringResource(R.string.settings_animation_test_subtitle),
                onClick = {
                    onDismiss()
                    onOpenAnimationTest()
                }
            )

            VlSettingsItem(
                icon = Icons.Default.Code,
                iconColor = MaterialTheme.colorScheme.tertiary,
                title = stringResource(R.string.settings_debug_show_ids_title),
                subtitle = stringResource(R.string.settings_debug_show_ids_desc),
                trailing = {
                    VlSwitch(
                        checked = showDebugIds,
                        onCheckedChange = { onToggleDebugIds(it) }
                    )
                }
            )

            VlSettingsItem(
                icon = Icons.Default.Terminal,
                iconColor = Color(0xFF6366F1),
                title = "Aegis Project Debug",
                subtitle = "Отладка локальной базы данных и логов Aegis",
                onClick = {
                    onDismiss()
                    onOpenAegisDebug()
                }
            )

            VlSettingsItem(
                icon = Icons.Default.ToggleOn,
                iconColor = Color(0xFFEC4899),
                title = "Flag Flipper",
                subtitle = "Управление Feature Flags в реальном времени",
                onClick = {
                    onDismiss()
                    onOpenFlagFlipper()
                }
            )

            VlSettingsItem(
                icon = Icons.Default.Fingerprint,
                iconColor = MaterialTheme.colorScheme.secondary,
                title = "ID клиента",
                subtitle = clientFlagsId,
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Client Flags ID", clientFlagsId))
                    haptic.perform(HapticType.CLICK, hapticEnabled)
                    Toast.makeText(context, "ID клиента скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
                }
            )

            VlSettingsItem(
                icon = Icons.Default.Info,
                title = "Копировать данные сборки",
                subtitle = versionString,
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Version", versionString))
                    haptic.perform(HapticType.CLICK, hapticEnabled)
                    Toast.makeText(context, "Версия скопирована", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
