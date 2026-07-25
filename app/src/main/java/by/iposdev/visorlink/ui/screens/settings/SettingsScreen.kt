package by.iposdev.visorlink.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.BuildConfig
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.ColorPreset
import by.iposdev.visorlink.data.model.ThemeMode
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.data.repository.AuthRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.ui.components.*
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.theme.exthruSmallRaisedShadow
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.ui.update.AppUpdateViewModel
import by.iposdev.visorlink.ui.update.UpdateChannel
import by.iposdev.visorlink.utils.AppLanguage
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.StealthManager
import by.iposdev.visorlink.utils.rememberHaptic
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
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
    themeViewModel: ThemeViewModel = koinViewModel(),
    appUpdateViewModel: AppUpdateViewModel = koinViewModel(),
    userRepository: UserRepository = koinInject(),
    authRepository: AuthRepository = koinInject()
) {
    val currentTheme by themeViewModel.appTheme.collectAsState()
    val currentMode by themeViewModel.themeMode.collectAsState()
    val currentPreset by themeViewModel.colorPreset.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val notifEnabled by themeViewModel.notificationsEnabled.collectAsState()
    val currentLang by themeViewModel.language.collectAsState()
    val currentChannel by appUpdateViewModel.currentChannel.collectAsState()

    val profile by userRepository.currentUserFlow().collectAsState(initial = null)

    val context = LocalContext.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f

    // ── Стелс-режим ──
    val stealthManager = remember { StealthManager(context) }
    var isStealthEnabled by remember { mutableStateOf(stealthManager.isEnabled()) }
    var hasStealthPin by remember { mutableStateOf(stealthManager.hasPin()) }

    var showStealthSetup by remember { mutableStateOf(false) }
    var showStealthDisable by remember { mutableStateOf(false) }
    var showStealthChangePin by remember { mutableStateOf(false) }

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

    val hazeState = remember { HazeState() }
    val scaffoldBg = if (currentTheme.isExthruFamily) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize().background(scaffoldBg)) {
            Box(modifier = Modifier.fillMaxSize().haze(state = hazeState)) {
                VlAmbientGlow(appTheme = currentTheme)

                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        if (currentTheme.isExthruFamily) {
                            TopAppBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .hazeChild(state = hazeState, style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.55f)),
                                title = {
                                    Text(
                                        text = stringResource(R.string.settings_title),
                                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold)
                                    )
                                },
                                navigationIcon = {
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "back_btn_scale")
                                    val shadowMod = if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if (isDark) 0.6f else 0.35f) else Modifier.exthruSmallRaisedShadow(isDark)

                                    Box(
                                        modifier = Modifier
                                            .padding(start = 12.dp, end = 4.dp)
                                            .size(42.dp)
                                            .scale(scale)
                                            .then(shadowMod)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), CircleShape)
                                            .border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), CircleShape)
                                            .clip(CircleShape)
                                            .clickable(interactionSource = interactionSource, indication = null) {
                                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                                onNavigateBack()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 32.dp)
                    ) {
                        // ── Профиль ────────────────────────────────────────────────────────
                        profile?.let { p ->
                            VlSurface(
                                appTheme = currentTheme,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                                contentPadding = PaddingValues(20.dp),
                                onClick = { /* To Profile */ }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AvatarWithPresence(
                                        avatarUrl = p.avatarUrl,
                                        displayName = p.displayName.ifEmpty { p.username },
                                        isOnline = false,
                                        size = 64.dp
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.displayName.ifEmpty { p.username }, style = MaterialTheme.typography.titleLarge)
                                        Text("@${p.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        // ── Акцент ────────────────────────────────────────────────────────
                        VlSettingsSection(appTheme = currentTheme, title = "Цветовой акцент") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ColorPreset.entries.forEach { preset ->
                                    ColorPresetCircle(
                                        preset = preset,
                                        isSelected = currentPreset == preset,
                                        isExthru = currentTheme.isExthruFamily,
                                        isDark = isDark,
                                        onClick = {
                                            haptic.perform(HapticType.CLICK, hapticEnabled)
                                            themeViewModel.setColorPreset(preset)
                                        }
                                    )
                                }
                            }
                        }

                        // ── Внешний вид ────────────────────────────────────────────────────
                        VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_section_appearance)) {
                            VlOptionRow(appTheme = currentTheme, icon = Icons.Default.Layers, label = "Biolume", desc = "Органичный неоморфизм", selected = currentTheme == AppTheme.BIOLUME || currentTheme == AppTheme.EXTHRU, index = 0, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setTheme(AppTheme.BIOLUME) })
                            VlOptionRow(appTheme = currentTheme, icon = Icons.Default.AutoAwesome, label = stringResource(R.string.settings_theme_m3_name), desc = stringResource(R.string.settings_theme_m3_desc), selected = currentTheme == AppTheme.MATERIAL3_EXPRESSIVE, index = 1, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setTheme(AppTheme.MATERIAL3_EXPRESSIVE) })
                            VlOptionRow(appTheme = currentTheme, icon = Icons.Default.Shield, label = "Forge", desc = "Квадратный киберпанк, Arasaka", selected = currentTheme == AppTheme.FORGE, index = 2, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setTheme(AppTheme.FORGE) })
                        }

                        // ── Тёмный режим ───────────────────────────────────────────────────
                        VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_dark_title)) {
                            VlOptionRow(appTheme = currentTheme, icon = Icons.Default.SettingsBrightness, label = stringResource(R.string.settings_dark_system), desc = stringResource(R.string.settings_dark_system_desc), selected = currentMode == ThemeMode.SYSTEM, index = 0, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setThemeMode(ThemeMode.SYSTEM) })
                            VlOptionRow(appTheme = currentTheme, icon = Icons.Default.LightMode, label = stringResource(R.string.settings_dark_light), desc = stringResource(R.string.settings_dark_light_desc), selected = currentMode == ThemeMode.LIGHT, index = 1, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setThemeMode(ThemeMode.LIGHT) })
                            VlOptionRow(appTheme = currentTheme, icon = Icons.Default.DarkMode, label = stringResource(R.string.settings_dark_dark), desc = stringResource(R.string.settings_dark_dark_desc), selected = currentMode == ThemeMode.DARK, index = 2, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setThemeMode(ThemeMode.DARK) })
                        }

                        // ── Язык ──────────────────────────────────────────────────────────
                        VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_section_language)) {
                            VlOptionRow(appTheme = currentTheme, icon = Icons.Default.Language, label = stringResource(R.string.settings_language_system), selected = currentLang == AppLanguage.SYSTEM, index = 0, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setLanguage(AppLanguage.SYSTEM) })
                            VlOptionRow(appTheme = currentTheme, icon = Icons.Default.Translate, label = stringResource(R.string.settings_language_en), selected = currentLang == AppLanguage.EN, index = 1, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setLanguage(AppLanguage.EN) })
                            VlOptionRow(appTheme = currentTheme, icon = Icons.Default.GTranslate, label = stringResource(R.string.settings_language_ru), selected = currentLang == AppLanguage.RU, index = 2, total = 3, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setLanguage(AppLanguage.RU) })
                        }

                        // ── Управление ─────────────────────────────────────────────────────
                        VlSettingsSection(appTheme = currentTheme, title = "Управление") {
                            VlSettingsItem(appTheme = currentTheme, icon = Icons.Default.NotificationsActive, title = "Push-уведомления", trailing = { VlSwitch(appTheme = currentTheme, checked = notifEnabled, onCheckedChange = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setNotifications(it) }) }, index = 0, total = 2)
                            VlSettingsItem(appTheme = currentTheme, icon = Icons.Default.Vibration, title = "Вибрация", trailing = { VlSwitch(appTheme = currentTheme, checked = hapticEnabled, onCheckedChange = { haptic.perform(HapticType.SELECTION, hapticEnabled); themeViewModel.setHaptic(it) }) }, index = 1, total = 2)
                        }

                        // ── ПРИВАТНОСТЬ (STEALTH MODE) ─────────────────────────────────────
                        VlSettingsSection(appTheme = currentTheme, title = "Приватность") {
                            VlSettingsItem(
                                appTheme = currentTheme,
                                icon = Icons.Default.VisibilityOff,
                                title = "Режим скрытия",
                                subtitle = if (isStealthEnabled) "Включён — при запуске откроется маскировочный экран" else "Маскирует мессенджер под другое приложение",
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
                                                    Toast.makeText(context, "Режим скрытия включён", Toast.LENGTH_SHORT).show()
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
                                    icon = Icons.Default.Password,
                                    title = "Сменить PIN-код скрытия",
                                    subtitle = "Обновить код доступа для разблокировки",
                                    index = 1, total = 2,
                                    onClick = {
                                        haptic.perform(HapticType.CLICK, hapticEnabled)
                                        showStealthChangePin = true
                                    }
                                )
                            }
                        }

                        // ── Память ────────────────────────────────────────────────────────
                        VlSettingsSection(appTheme = currentTheme, title = stringResource(R.string.settings_section_storage)) {
                            VlSettingsItem(appTheme = currentTheme, icon = Icons.Default.Storage, title = stringResource(R.string.settings_cache_title), subtitle = stringResource(R.string.settings_cache_subtitle), onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onOpenCacheSettings() }, index = 0, total = 1)
                        }

                        // ── Боты ───────────────────────────────────────────────────────────
                        VlSettingsSection(appTheme = currentTheme, title = "Боты") {
                            VlSettingsItem(appTheme = currentTheme, icon = Icons.Default.SmartToy, title = "Мои боты", subtitle = "Управление API-ботами", onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); showBotsManager = true }, index = 0, total = 1)
                        }

                        // ── Администрирование ──────────────────────────────────────────────
                        if (profile?.isAdmin == true) {
                            VlSettingsSection(appTheme = currentTheme, title = "Администрирование") {
                                VlSettingsItem(appTheme = currentTheme, icon = Icons.Default.AdminPanelSettings, iconColor = MaterialTheme.colorScheme.error, title = "Admin Panel", onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); showAdminPanel = true }, index = 0, total = 1)
                            }
                        }

                        // ── Обновления ─────────────────────────────────────────────────────
                        VlSettingsSection(appTheme = currentTheme, title = "Обновления") {
                            VlSettingsItem(appTheme = currentTheme, icon = Icons.Default.Science, title = "Канал обновлений", subtitle = "Текущий: ${currentChannel.title}", onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); showChannelDialog = true }, index = 0, total = 2)
                            VlSettingsItem(appTheme = currentTheme, icon = Icons.Default.Sync, title = stringResource(R.string.settings_check_updates), subtitle = stringResource(R.string.settings_check_updates_sub), onClick = { haptic.perform(HapticType.SUCCESS, hapticEnabled); Toast.makeText(context, "Проверка обновлений...", Toast.LENGTH_SHORT).show(); appUpdateViewModel.checkForUpdates() }, index = 1, total = 2)
                        }

                        // ── Аккаунт ────────────────────────────────────────────────────────
                        VlSettingsSection(appTheme = currentTheme, title = "Аккаунт") {
                            VlSettingsItem(appTheme = currentTheme, icon = Icons.Default.Email, title = "Email", subtitle = profile?.email ?: "", index = 0, total = 3)
                            VlSettingsItem(appTheme = currentTheme, icon = Icons.Default.Password, title = "Изменить пароль", onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); showPasswordDialog = true }, index = 1, total = 3)
                            VlSettingsItem(appTheme = currentTheme, icon = Icons.AutoMirrored.Filled.Logout, title = "Выйти из аккаунта", isDestructive = true, onClick = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); showLogoutDialog = true }, index = 2, total = 3)
                        }

                        // ── About ──────────────────────────────────────────────────────────
                        Spacer(Modifier.height(16.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text("VisorLink $versionString", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
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

// ── Stealth Dialogs ──────────────────────────────────────────────────────────

@Composable
private fun StealthSetupDialog(appTheme: AppTheme, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    VlAlertDialog(
        appTheme = appTheme,
        onDismissRequest = onDismiss,
        title = { Text("Настройка режима скрытия") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Придумайте PIN-код (от 4 цифр). Он потребуется, чтобы открыть настоящий мессенджер из режима скрытия.", fontSize = 13.sp)
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it; error = null },
                    label = { Text("PIN-код") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it; error = null },
                    label = { Text("Повторите PIN-код") },
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
                if (pin.length < 4) error = "Минимум 4 цифры"
                else if (pin != confirm) error = "PIN-коды не совпадают"
                else onConfirm(pin)
            }) { Text("Включить") }
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
private fun ChangePasswordDialog(appTheme: AppTheme, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    VlAlertDialog(
        appTheme = appTheme,
        onDismissRequest = onDismiss,
        title = { Text("Смена пароля") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = current, onValueChange = { current = it; error = null },
                    label = { Text("Текущий пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPass, onValueChange = { newPass = it; error = null },
                    label = { Text("Новый пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it; error = null },
                    label = { Text("Подтвердите пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        actions = {
            VlDialogButton(appTheme = appTheme, onClick = onDismiss) { Text("Отмена") }
            VlDialogButton(appTheme = appTheme, isPrimary = true, onClick = {
                if (newPass.length < 6) error = "Минимум 6 символов"
                else if (newPass != confirm) error = "Пароли не совпадают"
                else onConfirm(current, newPass)
            }) { Text("Сохранить") }
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
    var isSaving by remember { mutableStateOf(false) }
    var botName by remember { mutableStateOf("") }
    var botUsername by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Управление ботами", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Создать нового Webhook бота", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = botName, onValueChange = { botName = it }, label = { Text("Имя бота") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = botUsername, onValueChange = { botUsername = it }, label = { Text("Username (без @)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (botName.isBlank() || botUsername.isBlank()) return@Button
                    scope.launch {
                        isSaving = true
                        try {
                            val res = Firebase.functions("europe-west1").getHttpsCallable("createDmBot").call(mapOf("name" to botName, "username" to botUsername)).await()
                            val data = res.data as Map<*, *>
                            Toast.makeText(context, "Создан! Токен: ${data["botToken"]}", Toast.LENGTH_LONG).show()
                            onDismiss()
                        } catch(e: Exception) {
                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Создать")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Utils ─────────────────────────────────────────────────────────────────────

@Composable
private fun ColorPresetCircle(preset: ColorPreset, isSelected: Boolean, isExthru: Boolean, isDark: Boolean = false, onClick: () -> Unit) {
    val isDefault = preset == ColorPreset.DEFAULT
    val color = preset.seedColor ?: Color.Transparent
    val cs = MaterialTheme.colorScheme

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else if (isSelected) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    val bgModifier = if (isDefault) {
        Modifier.background(Brush.sweepGradient(listOf(Color.Blue, Color.Magenta, Color.Red, Color(0xFFFFA500), Color.Blue)), CircleShape)
    } else {
        Modifier.background(color, CircleShape)
    }

    val exthruMod = if (isExthru) {
        if (isSelected || isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 22.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
        else Modifier.exthruSmallRaisedShadow(isDark)
    } else Modifier

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .then(exthruMod)
            .then(bgModifier)
            .border(
                width = if (isSelected && !isExthru) 3.dp else 1.dp,
                color = if (isSelected && !isExthru) cs.onSurface else if (isExthru) Color.White.copy(alpha = if (isDark) 0.05f else 0.3f) else cs.outlineVariant.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isDefault) Icon(Icons.Default.Palette, null, tint = Color.White, modifier = Modifier.size(20.dp))
        else if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

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