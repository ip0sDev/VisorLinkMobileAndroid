package by.iposdev.visorlink.ui.screens.saved

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.components.LocalHazeState
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.theme.exthruRaisedShadow
import by.iposdev.visorlink.ui.theme.exthruSmallRaisedShadow
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMessagesSettingsScreen(
    onNavigateBack: () -> Unit,
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val viewModel: SavedMessagesViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val currentTheme by themeViewModel.appTheme.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val isExthru = currentTheme == AppTheme.EXTHRU || currentTheme == AppTheme.BIOLUME
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val haptic = rememberHaptic()

    val pinEnabled = uiState.settings?.pinEnabled == true
    val lockTimeout = uiState.settings?.lockTimeout ?: 5

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }

    val hazeState = remember { HazeState() }
    val scaffoldBg = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize().background(scaffoldBg)) {
            Box(modifier = Modifier.fillMaxSize().haze(state = hazeState)) {
                VlAmbientGlow(appTheme = currentTheme)

                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        if (isExthru) {
                            TopAppBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .hazeChild(state = hazeState, style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.55f)),
                                title = {
                                    Text(
                                        "Настройки Избранного",
                                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                    )
                                },
                                navigationIcon = {
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "back_scale")
                                    val shadowMod = if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if(isDark) 0.6f else 0.35f) else Modifier.exthruSmallRaisedShadow(isDark)

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
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
                            )
                        } else {
                            TopAppBar(
                                title = { Text("Настройки Избранного") },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        haptic.perform(HapticType.CLICK, hapticEnabled)
                                        onNavigateBack()
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader("PIN-защита и шифрование", isExthru)

                        SettingsCard(isExthru = isExthru, isDark = isDark) {
                            ExthruSwitchRow(
                                icon = if (pinEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                title = "PIN-блокировка",
                                subtitle = if (pinEnabled) "Включена · шифрование текстов активно" else "Отключена · данные не шифруются",
                                checked = pinEnabled,
                                isExthru = isExthru,
                                isDark = isDark,
                                hapticEnabled = hapticEnabled,
                                onCheckedChange = {
                                    if (it) showSetPinDialog = true
                                    else showDisablePinDialog = true
                                }
                            )

                            if (pinEnabled) {
                                if (!isExthru) HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ExthruIconTray(Icons.Default.Timer, isExthru, isDark)
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Автоблокировка", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            when (lockTimeout) {
                                                0    -> "Немедленно при выходе"
                                                1    -> "Через 1 минуту"
                                                else -> "Через $lockTimeout минут"
                                            },
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Chips выбора времени
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val options = listOf(0 to "Сразу", 5 to "5 мин", 15 to "15 мин", 60 to "1 ч")
                                    options.forEach { (value, label) ->
                                        NmChip(
                                            label = label,
                                            selected = lockTimeout == value,
                                            isExthru = isExthru,
                                            isDark = isDark,
                                            onClick = {
                                                haptic.perform(HapticType.SELECTION, hapticEnabled)
                                                viewModel.updateLockTimeout(value)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = pinEnabled) {
                            WarningBanner(isExthru = isExthru, isDark = isDark)
                        }
                    }
                }
            }
        }
    }

    if (showSetPinDialog) {
        SetPinDialog(
            onConfirm = { pin -> viewModel.setPin(pin); showSetPinDialog = false },
            onDismiss = { showSetPinDialog = false }
        )
    }

    if (showDisablePinDialog) {
        AlertDialog(
            onDismissRequest = { showDisablePinDialog = false },
            icon = { Icon(Icons.Default.LockOpen, null) },
            title = { Text("Отключить PIN?") },
            text  = { Text("Шифрование будет отключено. Уже сохранённые сообщения останутся зашифрованными до удаления.") },
            confirmButton = {
                TextButton(onClick = { viewModel.disablePin(); showDisablePinDialog = false }) {
                    Text("Отключить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisablePinDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun WarningBanner(isExthru: Boolean, isDark: Boolean) {
    if (isExthru) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).nmInsetShadow(isDark, cornerRadius = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.1f else 0.3f),
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(38.dp).exthruSmallRaisedShadow(isDark).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text("Шифрование включено", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                }
                Text("Текстовые сообщения зашифрованы на устройстве (AES-GCM). Сервер не может их прочитать.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Фото и голосовые НЕ шифруются.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Шифрование включено", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
                Text("Текстовые сообщения зашифрованы на устройстве (AES-GCM-256). Сервер не может их прочитать — только вы, зная PIN.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Фото и голосовые НЕ шифруются.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SetPinDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val mismatch = confirmPin.isNotEmpty() && pin != confirmPin

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Lock, null) },
        title = { Text("Установить PIN-код") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "PIN используется для блокировки Избранного. " +
                            "Текстовые сообщения будут зашифрованы на устройстве.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN (4–8 цифр)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("Повторите PIN") },
                    isError = mismatch,
                    supportingText = if (mismatch) { { Text("PIN не совпадает", color = MaterialTheme.colorScheme.error) } } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pin) },
                enabled = pin.length >= 4 && !mismatch && confirmPin == pin
            ) { Text("Установить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, isExthru: Boolean) {
    if (isExthru) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, top = 26.dp, bottom = 8.dp)
        )
    } else {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun SettingsCard(isExthru: Boolean, isDark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (isExthru) {
        val hazeState = LocalHazeState.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .exthruRaisedShadow(isDark)
                .clip(RoundedCornerShape(20.dp))
                .hazeChild(state = hazeState, style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.55f))
                .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (isDark) 0.15f else 0.5f), Color.Transparent, Color.Black.copy(alpha = if (isDark) 0.4f else 0.05f))), RoundedCornerShape(20.dp))
                .padding(vertical = 6.dp),
            content = content
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun ExthruIconTray(icon: androidx.compose.ui.graphics.vector.ImageVector, isExthru: Boolean, isDark: Boolean, isError: Boolean = false) {
    if (isExthru) {
        val bgColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.7f)
        val iconColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier.size(38.dp).exthruSmallRaisedShadow(isDark).background(bgColor, CircleShape).border(1.dp, Color.White.copy(alpha = if(isDark) 0.05f else 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
    } else {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ExthruSwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, isExthru: Boolean, isDark: Boolean, hapticEnabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptic = rememberHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, spring(dampingRatio = 0.6f), label = "row_scale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = { haptic.perform(HapticType.SELECTION, hapticEnabled); onCheckedChange(!checked) })
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ExthruIconTray(icon = icon, isExthru = isExthru, isDark = isDark)
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isExthru) {
            NmSwitch(checked = checked, isDark = isDark) { haptic.perform(HapticType.SELECTION, hapticEnabled); onCheckedChange(it) }
        } else {
            Switch(checked = checked, onCheckedChange = { haptic.perform(HapticType.SELECTION, hapticEnabled); onCheckedChange(it) })
        }
    }
}

@Composable
private fun NmSwitch(checked: Boolean, isDark: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val thumbOffset by animateFloatAsState(targetValue = if (checked) 26f else 4f, animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "nm_thumb_offset")
    val thumbScale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f), label = "nm_thumb_scale")
    val dotColor by animateColorAsState(targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), animationSpec = tween(200), label = "nm_thumb_dot_color")

    Box(
        modifier = Modifier
            .width(54.dp).height(28.dp)
            .nmInsetShadow(isDark, cornerRadius = 14.dp, darkAlpha = if (isDark) 0.7f else 0.35f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.2f else 0.4f), RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = { onCheckedChange(!checked) })
    ) {
        Box(
            modifier = Modifier.offset(x = thumbOffset.dp, y = 2.dp).size(24.dp).scale(thumbScale).then(if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 12.dp) else Modifier.exthruSmallRaisedShadow(isDark)).background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        }
    }
}

@Composable
private fun NmChip(label: String, selected: Boolean, isExthru: Boolean, isDark: Boolean, onClick: () -> Unit) {
    if (isExthru) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "chip_scale")
        val shadowMod = if (selected || isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp, darkAlpha = if(isDark) 0.6f else 0.35f) else Modifier.exthruSmallRaisedShadow(isDark)

        Box(
            modifier = Modifier
                .scale(scale)
                .then(shadowMod)
                .background(if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = if(isDark) 0.4f else 0.6f), RoundedCornerShape(16.dp))
                .border(1.dp, if (selected || isPressed) Color.Transparent else Color.White.copy(alpha = if(isDark) 0.05f else 0.3f), RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    } else {
        FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
    }
}