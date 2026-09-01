package by.iposdev.visorlink.ui.screens.saved

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.VlSurface
import by.iposdev.visorlink.ui.components.VlTextField
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMessagesSettingsScreen(
    onNavigateBack: () -> Unit,
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val viewModel: SavedMessagesViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val haptic = rememberHaptic()

    val pinEnabled = uiState.settings?.pinEnabled == true
    val lockTimeout = uiState.settings?.lockTimeout ?: 5

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            VlAmbientGlow()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(padding.calculateTopPadding() + 8.dp))

                SectionHeader("PIN-защита и шифрование")

                VlSurface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Column {
                        ListItem(
                            modifier = Modifier.clickable {
                                haptic.perform(HapticType.SELECTION, hapticEnabled)
                                if (!pinEnabled) showSetPinDialog = true
                                else showDisablePinDialog = true
                            },
                            headlineContent = { Text("PIN-блокировка") },
                            supportingContent = {
                                Text(if (pinEnabled) "Включена · шифрование текстов активно" else "Отключена · данные не шифруются")
                            },
                            leadingContent = {
                                Icon(if (pinEnabled) Icons.Default.Lock else Icons.Default.LockOpen, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingContent = {
                                Switch(checked = pinEnabled, onCheckedChange = {
                                    haptic.perform(HapticType.SELECTION, hapticEnabled)
                                    if (it) showSetPinDialog = true
                                    else showDisablePinDialog = true
                                })
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        if (pinEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            ListItem(
                                headlineContent = { Text("Автоблокировка") },
                                supportingContent = {
                                    Text(
                                        when (lockTimeout) {
                                            0    -> "Немедленно при выходе"
                                            1    -> "Через 1 минуту"
                                            else -> "Через $lockTimeout минут"
                                        }
                                    )
                                },
                                leadingContent = { Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            // Chips выбора времени
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val options = listOf(0 to "Сразу", 5 to "5 мин", 15 to "15 мин", 60 to "1 ч")
                                options.forEach { (value, label) ->
                                    FilterChip(
                                        selected = lockTimeout == value,
                                        onClick = {
                                            haptic.perform(HapticType.SELECTION, hapticEnabled)
                                            viewModel.updateLockTimeout(value)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = pinEnabled) {
                    WarningBanner()
                }

                Spacer(modifier = Modifier.height(padding.calculateBottomPadding() + 32.dp))
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
private fun WarningBanner() {
    Surface(
        shape = VlTheme.tokens.shapes.button,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
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
                VlTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    label = "PIN (4–8 цифр)",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                VlTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPin = it },
                    label = "Повторите PIN",
                    isError = mismatch,
                    supportingText = if (mismatch) "PIN не совпадает" else null,
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
    )
}