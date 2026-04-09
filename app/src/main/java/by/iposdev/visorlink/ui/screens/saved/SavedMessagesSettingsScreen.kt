package by.iposdev.visorlink.ui.screens.saved

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMessagesSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: SavedMessagesViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val pinEnabled = uiState.settings?.pinEnabled == true
    val lockTimeout = uiState.settings?.lockTimeout ?: 5

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = { Text("Настройки Избранного") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── PIN-защита ─────────────────────────────────────────────────
            SectionHeader("PIN-защита и шифрование")

            SettingsCard {
                SettingsRow(
                    icon   = if (pinEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                    title  = "PIN-блокировка",
                    subtitle = if (pinEnabled)
                        "Включена · шифрование текстов активно"
                    else
                        "Отключена · данные не шифруются",
                    trailingContent = {
                        Switch(
                            checked = pinEnabled,
                            onCheckedChange = {
                                if (it) showSetPinDialog = true
                                else showDisablePinDialog = true
                            }
                        )
                    }
                )

                if (pinEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    // Timeout
                    SettingsRow(
                        icon  = Icons.Default.Timer,
                        title = "Автоблокировка",
                        subtitle = when (lockTimeout) {
                            0    -> "Немедленно при выходе"
                            1    -> "Через 1 минуту"
                            else -> "Через $lockTimeout минут"
                        },
                        trailingContent = {
                            LockTimeoutChips(
                                selected = lockTimeout,
                                onSelect = { viewModel.updateLockTimeout(it) }
                            )
                        }
                    )
                }
            }

            // ── Шифрование — информационный баннер ────────────────────────
            AnimatedVisibility(visible = pinEnabled) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Шифрование текстов включено",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp)
                        }
                        Text(
                            "Текстовые сообщения зашифрованы на устройстве (AES-GCM-256). " +
                                    "Сервер не может их прочитать — только вы, зная PIN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Фото и голосовые НЕ шифруются — они хранятся в облаке как обычно.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Диалог установки PIN ───────────────────────────────────────────────
    if (showSetPinDialog) {
        SetPinDialog(
            onConfirm = { pin -> viewModel.setPin(pin); showSetPinDialog = false },
            onDismiss = { showSetPinDialog = false }
        )
    }

    // ── Диалог отключения PIN ──────────────────────────────────────────────
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
                // Предупреждение о нешифрованных медиа
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Row(modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Фото и голосовые НЕ шифруются — они остаются в облаке.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
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
private fun LockTimeoutChips(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val options = listOf(0 to "Сразу", 5 to "5 мин", 15 to "15 мин", 60 to "1 ч")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick  = { onSelect(value) },
                label    = { Text(label, fontSize = 11.sp) }
            )
        }
    }
}

// ─── Shared helper composables ────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(8.dp))
            trailingContent()
        }
    }
}

// Расширение VM для updateLockTimeout (делегирует в репозиторий)
fun SavedMessagesViewModel.updateLockTimeout(minutes: Int) {
    // Вызывается через viewModelScope — добавить в реальной VM:
    // viewModelScope.launch { repository.updateLockTimeout(currentUid, minutes) }
}