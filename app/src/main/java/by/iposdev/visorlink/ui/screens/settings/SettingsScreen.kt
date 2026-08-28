package by.iposdev.visorlink.ui.screens.settings

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import by.iposdev.visorlink.BuildConfig
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.data.repository.AuthRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.data.repository.FlagsRepository
import by.iposdev.visorlink.ui.components.*
import by.iposdev.visorlink.ui.components.settings.*
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import com.ipos.store.sdk.IposStoreUpdates
import by.iposdev.visorlink.utils.AppLanguage
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.StealthManager
import by.iposdev.visorlink.utils.rememberHaptic
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
    themeViewModel: ThemeViewModel = koinViewModel(),
    userRepository: UserRepository = koinInject(),
    authRepository: AuthRepository = koinInject(),
    flagsRepository: FlagsRepository = koinInject(),
    proViewModel: ProViewModel = koinViewModel(),
    authViewModel: by.iposdev.visorlink.ui.screens.auth.AuthViewModel = koinViewModel()
) {
    val currentTheme by themeViewModel.appTheme.collectAsState()
    val currentMode by themeViewModel.themeMode.collectAsState()
    val currentPreset by themeViewModel.colorPreset.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val notifEnabled by themeViewModel.notificationsEnabled.collectAsState()
    val currentLang by themeViewModel.language.collectAsState()
    val dynamicInput by themeViewModel.dynamicChatInput.collectAsState()
    val compactChatList by themeViewModel.compactChatList.collectAsState()

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

    var showAdminPanel by remember { mutableStateOf(false) }
    var showBotsManager by remember { mutableStateOf(false) }

    val backendPrefs = remember { context.getSharedPreferences("visorlink_backend_settings", Context.MODE_PRIVATE) }
    var useBackend by remember { mutableStateOf(backendPrefs.getBoolean("use_custom_backend", false)) }
    var useBackendProfile by remember { mutableStateOf(backendPrefs.getBoolean("use_backend_profile", false)) }
    var useBackendFeed by remember { mutableStateOf(backendPrefs.getBoolean("use_backend_feed", false)) }
    var disableFirestore by remember { mutableStateOf(backendPrefs.getBoolean("disable_firestore_completely", false)) }
    var customBackendUrl by remember { mutableStateOf(backendPrefs.getString("custom_backend_url", "10.0.2.2:8080") ?: "10.0.2.2:8080") }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLegalDialog by remember { mutableStateOf(false) }

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
    val colorUpdateChan = Color(0xFFF59E0B)
    val colorUpdateCheck = Color(0xFF10B981)
    val colorEmail = Color(0xFF64748B)
    val colorPassword = Color(0xFFF43F5E)

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            VlAmbientGlow()
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(modifier = Modifier.height(padding.calculateTopPadding() + 8.dp))

                profile?.let { p ->
                    Surface(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                        onClick = { },
                        shape = VlTheme.tokens.shapes.card,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            AvatarWithPresence(avatarUrl = p.avatarUrl, displayName = p.displayName.ifEmpty { p.username }, isOnline = false, size = 64.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(text = p.displayName.ifEmpty { p.username }, style = MaterialTheme.typography.titleLarge)
                                Text(text = "@${p.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    ProStatusBanner(profile = p, proViewModel = proViewModel, proState = proState, hapticEnabled = hapticEnabled)
                }

                VlSettingsSection(title = stringResource(R.string.settings_custom_title), isPremium = true) {
                    VlSettingsItem(icon = Icons.Default.Brush, title = stringResource(R.string.settings_custom_design_title), subtitle = stringResource(R.string.settings_custom_design_sub), onClick = { if (profile?.isProActive() == true) onOpenCustomization() else Toast.makeText(context, context.getString(R.string.settings_custom_pro_only), Toast.LENGTH_SHORT).show() })
                    VlSettingsItem(icon = Icons.Default.HideImage, title = stringResource(R.string.settings_custom_hide_title), subtitle = stringResource(R.string.settings_custom_hide_sub), trailing = { VlSwitch(checked = profile?.ignoreCustomizations ?: false, onCheckedChange = { v -> scope.launch { userRepository.updateIgnoreCustomizations(v) } }) })
                }

                VlSettingsSection(title = stringResource(R.string.settings_section_theme)) {
                    VlThemeSelector(
                        selected = currentTheme,
                        onSelect = { themeViewModel.setTheme(it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        themeMode = currentMode,
                        colorPreset = currentPreset,
                    )
                }

                VlSettingsSection(title = stringResource(R.string.settings_section_accent)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        ColorPreset.entries.forEach { preset ->
                            ColorPresetCircle(preset = preset, isSelected = currentPreset == preset, onClick = { themeViewModel.setColorPreset(preset) })
                        }
                    }
                }

                VlSettingsSection(title = stringResource(R.string.settings_dark_title)) {
                    VlOptionRow(icon = Icons.Default.SettingsBrightness, label = stringResource(R.string.settings_dark_system), desc = stringResource(R.string.settings_dark_system_desc), selected = currentMode == ThemeMode.SYSTEM, onClick = { themeViewModel.setThemeMode(ThemeMode.SYSTEM) })
                    VlOptionRow(icon = Icons.Default.LightMode, label = stringResource(R.string.settings_dark_light), desc = stringResource(R.string.settings_dark_light_desc), selected = currentMode == ThemeMode.LIGHT, onClick = { themeViewModel.setThemeMode(ThemeMode.LIGHT) })
                    VlOptionRow(icon = Icons.Default.DarkMode, label = stringResource(R.string.settings_dark_dark), desc = stringResource(R.string.settings_dark_dark_desc), selected = currentMode == ThemeMode.DARK, onClick = { themeViewModel.setThemeMode(ThemeMode.DARK) })
                }

                VlSettingsSection(title = stringResource(R.string.settings_section_language)) {
                    VlOptionRow(icon = Icons.Default.Language, label = stringResource(R.string.settings_language_system), selected = currentLang == AppLanguage.SYSTEM, onClick = { themeViewModel.setLanguage(AppLanguage.SYSTEM) })
                    VlOptionRow(icon = Icons.Default.Translate, label = stringResource(R.string.settings_language_en), selected = currentLang == AppLanguage.EN, onClick = { themeViewModel.setLanguage(AppLanguage.EN) })
                    VlOptionRow(icon = Icons.Default.GTranslate, label = stringResource(R.string.settings_language_ru), selected = currentLang == AppLanguage.RU, onClick = { themeViewModel.setLanguage(AppLanguage.RU) })
                }

                VlSettingsSection(title = stringResource(R.string.settings_section_management)) {
                    VlSettingsItem(icon = Icons.Default.NotificationsActive, iconColor = colorNotif, title = stringResource(R.string.settings_push_title), trailing = { VlSwitch(checked = notifEnabled, onCheckedChange = { themeViewModel.setNotifications(it) }) })
                    VlSettingsItem(icon = Icons.Default.Vibration, iconColor = colorVibro, title = stringResource(R.string.settings_haptic_title), trailing = { VlSwitch(checked = hapticEnabled, onCheckedChange = { themeViewModel.setHaptic(it) }) })
                    VlSettingsItem(icon = Icons.Default.KeyboardHide, iconColor = colorDynInput, title = "Динамическое поле ввода", trailing = { VlSwitch(checked = dynamicInput, onCheckedChange = { themeViewModel.setDynamicChatInput(it) }) })
                    VlSettingsItem(icon = Icons.Default.ViewAgenda, iconColor = colorCompact, title = "Компактный список чатов", trailing = { VlSwitch(checked = compactChatList, onCheckedChange = { themeViewModel.setCompactChatList(it) }) })
                    VlSettingsItem(icon = Icons.Default.Explore, iconColor = Color(0xFF10B981), title = "Discover (Лента)", subtitle = "Показывать вкладку с глобальной лентой", trailing = { VlSwitch(checked = themeViewModel.discoverEnabled.collectAsState().value, onCheckedChange = { themeViewModel.setDiscoverEnabled(it) }) })
                }

                VlSettingsSection(title = stringResource(R.string.diary_title)) {
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
                                    scope.launch {
                                        val uid = profile?.uid ?: return@launch
                                        Firebase.firestore.collection("users").document(uid).update("diaryEnabled", v)
                                    }
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
                                            val uid = profile?.uid ?: return@launch
                                            Firebase.firestore.collection("users").document(uid).update("diaryReminderTime", time)
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
                                            val uid = profile?.uid ?: return@launch
                                            Firebase.firestore.collection("users").document(uid).update("diaryRemindersEnabled", v)
                                        }
                                    }
                                )
                            }
                        )
                    }
                }

                VlSettingsSection(title = stringResource(R.string.settings_section_privacy)) {
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

                VlSettingsSection(title = stringResource(R.string.settings_section_storage)) {
                    VlSettingsItem(icon = Icons.Default.Storage, iconColor = colorStorage, title = stringResource(R.string.settings_cache_title), onClick = onOpenCacheSettings)
                    VlSettingsItem(icon = Icons.Default.CloudQueue, iconColor = colorStorage, title = stringResource(R.string.storage_title), onClick = onOpenStorageManager)
                    VlSettingsItem(icon = Icons.Default.HealthAndSafety, iconColor = colorStorage, title = "Статус системы", onClick = onOpenStatus)
                }

                VlSettingsSection(title = stringResource(R.string.stickers_title)) {
                    VlSettingsItem(icon = Icons.Default.SmartToy, iconColor = colorBots, title = stringResource(R.string.settings_bots_title), subtitle = stringResource(R.string.settings_bots_subtitle), onClick = { showBotsManager = true })
                }

                if (profile?.isAdmin == true) {
                    VlSettingsSection(title = stringResource(R.string.settings_admin_panel)) {
                        VlSettingsItem(icon = Icons.Default.AdminPanelSettings, iconColor = MaterialTheme.colorScheme.error, title = "Admin Panel", onClick = { showAdminPanel = true })
                    }
                }

                VlSettingsSection(title = stringResource(R.string.settings_section_updates)) {
                    VlSettingsItem(
                        icon = Icons.Default.Storefront,
                        iconColor = colorUpdateChan,
                        title = "Ipos Store",
                        subtitle = if (IposStoreUpdates.isStoreInstalled()) "Клиент установлен (фоновые обновления)" else "Не установлен (нажмите для скачивания)",
                        onClick = {
                            if (!IposStoreUpdates.isStoreInstalled()) {
                                IposStoreUpdates.openStoreDownload()
                            }
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
                                try {
                                    val update = IposStoreUpdates.checkUpdate()
                                    if (update == null && IposStoreUpdates.isStoreInstalled()) {
                                        Toast.makeText(context, context.getString(R.string.settings_up_to_date), Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка проверки: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                if (flags.isEnabled("test_backend_enabled")) {
                    VlSettingsSection(title = "Тестирование") {
                        VlSettingsItem(icon = Icons.Default.BugReport, iconColor = MaterialTheme.colorScheme.tertiary, title = "Бэкенд: Чаты", subtitle = "Ktor + Redis для сообщений", trailing = { VlSwitch(checked = useBackend, onCheckedChange = { useBackend = it; backendPrefs.edit().putBoolean("use_custom_backend", it).apply() }) })
                        VlSettingsItem(icon = Icons.Default.PersonSearch, iconColor = MaterialTheme.colorScheme.primary, title = "Бэкенд: Профили", subtitle = "Поиск и данные пользователей", trailing = { VlSwitch(checked = useBackendProfile, onCheckedChange = { useBackendProfile = it; backendPrefs.edit().putBoolean("use_backend_profile", it).apply() }) })
                        VlSettingsItem(icon = Icons.Default.RssFeed, iconColor = MaterialTheme.colorScheme.error, title = "Бэкенд: Лента", subtitle = "Discover Feed через API", trailing = { VlSwitch(checked = useBackendFeed, onCheckedChange = { useBackendFeed = it; backendPrefs.edit().putBoolean("use_backend_feed", it).apply() }) })
                        VlSettingsItem(icon = Icons.Default.CloudOff, iconColor = Color.Gray, title = "Железно отключить Firestore", subtitle = "Полная блокировка Firebase БД", trailing = { VlSwitch(checked = disableFirestore, onCheckedChange = { disableFirestore = it; backendPrefs.edit().putBoolean("disable_firestore_completely", it).apply() }) })
                        VlSettingsItem(icon = Icons.Default.Dns, iconColor = MaterialTheme.colorScheme.secondary, title = "Адрес бэкенда", subtitle = customBackendUrl, onClick = { showUrlDialog = true })
                    }
                }

                VlSettingsSection(title = stringResource(R.string.settings_section_account)) {
                    VlSettingsItem(icon = Icons.Default.Email, iconColor = colorEmail, title = "Email", subtitle = profile?.email ?: "")
                    if (flags.isEnabled("2fa_enabled")) {
                        VlSettingsItem(icon = Icons.Default.Security, iconColor = Color(0xFF10B981), title = stringResource(R.string.settings_tfa_title), subtitle = if (profile?.tfaEnabled == true) stringResource(R.string.settings_tfa_sub_on) else stringResource(R.string.settings_tfa_sub_off), trailing = { VlSwitch(checked = profile?.tfaEnabled ?: false, onCheckedChange = { v -> scope.launch { val uid = profile?.uid ?: return@launch; Firebase.firestore.collection("users").document(uid).update("tfaEnabled", v) } }) })
                    }
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
                }

                VlSettingsSection(title = "О приложении") {
                    if (flags.isEnabled("aegis_debug_mode_enabled")) VlSettingsItem(icon = Icons.Default.Terminal, title = "Aegis Project Debug", onClick = onOpenAegisDebug)
                    if (flags.isFlipperEnabled) VlSettingsItem(icon = Icons.Default.ToggleOn, title = "Flag Flipper", onClick = onOpenFlagFlipper)
                    VlSettingsItem(
                        icon = Icons.Default.Info, 
                        title = "VisorLink", 
                        subtitle = "Версия $versionString",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Version", versionString))
                            Toast.makeText(context, "Версия скопирована", Toast.LENGTH_SHORT).show()
                        }
                    )
                    VlSettingsItem(
                        icon = Icons.Default.Gavel,
                        iconColor = MaterialTheme.colorScheme.primary,
                        title = "Правовая информация",
                        subtitle = "Условия использования и конфиденциальность",
                        onClick = { showLegalDialog = true }
                    )
                    VlSettingsItem(icon = Icons.AutoMirrored.Filled.Logout, title = stringResource(R.string.settings_logout), isDestructive = true, onClick = { showLogoutDialog = true })
                }

                Spacer(Modifier.height(padding.calculateBottomPadding() + 32.dp))
            }
        }
    }

    if (showAdminPanel) AdminPanelSheet { showAdminPanel = false }
    if (showBotsManager) BotsManagerSheet { showBotsManager = false }
    if (showLogoutDialog) AlertDialog(onDismissRequest = { showLogoutDialog = false }, title = { Text("Выйти?") }, confirmButton = { TextButton(onClick = { showLogoutDialog = false; authViewModel.logout() }) { Text("Выйти") } }, dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена") } })

    if (showUrlDialog) {
        var url by remember { mutableStateOf(customBackendUrl) }
        VlAlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Адрес бэкенда") },
            text = { VlTextField(value = url, onValueChange = { url = it }, label = "URL (напр. 10.0.2.2:8080)", modifier = Modifier.fillMaxWidth()) },
            actions = {
                VlDialogButton(onClick = { showUrlDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                VlDialogButton(isPrimary = true, onClick = {
                    customBackendUrl = url
                    backendPrefs.edit().putString("custom_backend_url", url).apply()
                    showUrlDialog = false
                }) { Text(stringResource(R.string.action_save)) }
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
        val legalRepo: by.iposdev.visorlink.data.repository.LegalRepository = koinInject()
        val latestVer by legalRepo.observeLatestVersion().collectAsState(initial = legalRepo.loadBundledVersion())
        by.iposdev.visorlink.ui.screens.legal.LegalConsentDialog(
            currentVersion = latestVer,
            legalRepository = legalRepo,
            isReadOnly = true,
            onDismissReadOnly = { showLegalDialog = false }
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
        VlSettingsItem(icon = Icons.Default.WorkspacePremium, title = if (profile.isProActive()) stringResource(R.string.pro_status_active) else stringResource(R.string.pro_status_inactive))
        VlSettingsItem(icon = Icons.Default.Toll, title = stringResource(R.string.pro_bits), trailing = { Text(profile.bits.toString(), fontWeight = FontWeight.Bold, color = Color(0xFFC5A059)) })
        if (!profile.isProActive()) {
            Button(onClick = { proViewModel.buyPro(false) }, modifier = Modifier.padding(16.dp).fillMaxWidth()) { Text(stringResource(R.string.pro_action_buy, 1000)) }
        }
    }
}
