package by.iposdev.visorlink.ui.screens.settings

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import by.iposdev.visorlink.ui.update.AppUpdateViewModel
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
    onOpenCustomization: () -> Unit = {},
    onOpenAegisDebug: () -> Unit = {},
    onOpenFlagFlipper: () -> Unit = {},
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

    val stealthManager = remember { StealthManager(context) }
    var isStealthEnabled by remember { mutableStateOf(stealthManager.isEnabled()) }
    var hasStealthPin by remember { mutableStateOf(stealthManager.hasPin()) }

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

    var showChannelDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val buildDate = remember { SimpleDateFormat("yyyyMMdd.HHmm", Locale.getDefault()).format(Date(BuildConfig.BUILD_TIMESTAMP)) }
    val commitHash = BuildConfig.CommitID.takeIf { it.isNotBlank() } ?: "unknown"
    val versionString = "${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}.$buildDate [$commitHash]"
    val deviceId = remember { flagsRepository.getDeviceId() ?: "Not paired" }


    LaunchedEffect(proState.successMessage) { proState.successMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); proViewModel.clearMessages() } }
    LaunchedEffect(proState.error) { proState.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); proViewModel.clearMessages() } }

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
                    VlSettingsItem(icon = Icons.Default.NotificationsActive, title = stringResource(R.string.settings_push_title), trailing = { VlSwitch(checked = notifEnabled, onCheckedChange = { themeViewModel.setNotifications(it) }) })
                    VlSettingsItem(icon = Icons.Default.Vibration, title = stringResource(R.string.settings_haptic_title), trailing = { VlSwitch(checked = hapticEnabled, onCheckedChange = { themeViewModel.setHaptic(it) }) })
                    VlSettingsItem(icon = Icons.Default.KeyboardHide, title = "Динамическое поле ввода", trailing = { VlSwitch(checked = dynamicInput, onCheckedChange = { themeViewModel.setDynamicChatInput(it) }) })
                    VlSettingsItem(icon = Icons.Default.ViewAgenda, title = "Компактный список чатов", trailing = { VlSwitch(checked = compactChatList, onCheckedChange = { themeViewModel.setCompactChatList(it) }) })
                }

                VlSettingsSection(title = stringResource(R.string.settings_section_privacy)) {
                    VlSettingsItem(icon = Icons.Default.VisibilityOff, title = stringResource(R.string.settings_stealth_title), trailing = { VlSwitch(checked = isStealthEnabled, onCheckedChange = { if (it) { if (hasStealthPin) { stealthManager.setEnabled(true); isStealthEnabled = true } else showStealthSetup = true } else showStealthDisable = true }) })
                }

                VlSettingsSection(title = stringResource(R.string.settings_section_storage)) {
                    VlSettingsItem(icon = Icons.Default.Storage, title = stringResource(R.string.settings_cache_title), onClick = onOpenCacheSettings)
                    VlSettingsItem(icon = Icons.Default.CloudQueue, title = stringResource(R.string.storage_title), onClick = onOpenStorageManager)
                }

                VlSettingsSection(title = "О приложении") {
                    if (flags.isEnabled("aegis_debug_mode_enabled")) VlSettingsItem(icon = Icons.Default.Terminal, title = "Aegis Project Debug", onClick = onOpenAegisDebug)
                    if (flags.isFlipperEnabled) VlSettingsItem(icon = Icons.Default.ToggleOn, title = "Flag Flipper", onClick = onOpenFlagFlipper)
                    VlSettingsItem(icon = Icons.Default.Info, title = "VisorLink", subtitle = "Версия $versionString\nDevice ID: $deviceId")
                    VlSettingsItem(icon = Icons.AutoMirrored.Filled.Logout, title = stringResource(R.string.settings_logout), isDestructive = true, onClick = { showLogoutDialog = true })
                }

                Spacer(Modifier.height(padding.calculateBottomPadding() + 32.dp))
            }
        }
    }

    if (showAdminPanel) AdminPanelSheet { showAdminPanel = false }
    if (showBotsManager) BotsManagerSheet { showBotsManager = false }
    if (showChannelDialog) ChannelSelectionDialog(currentChannel = currentChannel, onDismiss = { showChannelDialog = false }, onSelect = { appUpdateViewModel.setChannel(it) { showChannelDialog = false } })
    if (showLogoutDialog) AlertDialog(onDismissRequest = { showLogoutDialog = false }, title = { Text("Выйти?") }, confirmButton = { TextButton(onClick = { authRepository.logout() }) { Text("Выйти") } }, dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена") } })
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
