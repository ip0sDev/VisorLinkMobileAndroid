package org.visorlink.app.ui.screens.saved

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.components.*
import org.visorlink.app.ui.theme.ThemeViewModel
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMessagesSettingsScreen(
    onNavigateBack: () -> Unit,
    themeViewModel: ThemeViewModel = koinViewModel(),
    flagsRepository: FlagsRepository = koinInject()
) {
    val viewModel: SavedMessagesViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val haptic = rememberHaptic()

    val flags by flagsRepository.flags.collectAsState()
    val isLiquidEnabled = flags.isEnabled("animation_test")
    val topBarJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f)

    LaunchedEffect(Unit) {
        if (isLiquidEnabled) {
            topBarJelly.pulse(0.06f)
        }
    }

    val pinEnabled = uiState.settings?.pinEnabled == true
    val lockTimeout = uiState.settings?.lockTimeout ?: 5

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        VlAmbientGlow()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                VlTopAppBar(
                    modifier = Modifier.liquidJelly(topBarJelly, enabled = isLiquidEnabled),
                    title = { Text("Настройки Избранного", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            if (isLiquidEnabled) topBarJelly.press(0.06f)
                            onNavigateBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(padding.calculateTopPadding() + 8.dp))

                VlSettingsSection(
                    title = "PIN-защита и шифрование",
                    modifier = Modifier.liquidPillCardSlideOut(index = 0, enabled = isLiquidEnabled)
                ) {
                    VlSettingsItem(
                        icon = if (pinEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                        iconColor = if (pinEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        title = "PIN-блокировка",
                        subtitle = if (pinEnabled) "Включена · шифрование текстов активно" else "Отключена · данные не шифруются",
                        onClick = {
                            haptic.perform(HapticType.SELECTION, hapticEnabled)
                            if (!pinEnabled) showSetPinDialog = true
                            else showDisablePinDialog = true
                        },
                        trailing = {
                            VlSwitch(
                                checked = pinEnabled,
                                onCheckedChange = {
                                    haptic.perform(HapticType.SELECTION, hapticEnabled)
                                    if (it) showSetPinDialog = true
                                    else showDisablePinDialog = true
                                }
                            )
                        }
                    )

                    if (pinEnabled) {
                        VlSettingsItem(
                            icon = Icons.Default.Timer,
                            iconColor = MaterialTheme.colorScheme.primary,
                            title = "Автоблокировка",
                            subtitle = when (lockTimeout) {
                                0    -> "Немедленно при выходе"
                                1    -> "Через 1 минуту"
                                else -> "Через $lockTimeout минут"
                            }
                        )

                        // Селектор интервала времени в современном стиле
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val options = listOf(0 to "Сразу", 5 to "5 мин", 15 to "15 мин", 60 to "1 ч")
                            val cs = MaterialTheme.colorScheme
                            options.forEach { (value, label) ->
                                val selected = lockTimeout == value
                                val chipShape = RoundedCornerShape(16.dp)
                                val bg = if (selected) cs.primaryContainer else cs.surfaceContainerHigh.copy(alpha = 0.5f)
                                val contentColor = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
                                val border = if (selected) BorderStroke(1.dp, cs.primary.copy(alpha = 0.35f)) else null

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(chipShape)
                                        .then(if (border != null) Modifier.border(border, chipShape) else Modifier)
                                        .background(bg, chipShape)
                                        .clickable {
                                            haptic.perform(HapticType.SELECTION, hapticEnabled)
                                            viewModel.updateLockTimeout(value)
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = pinEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    WarningBanner(
                        modifier = Modifier.liquidPillCardSlideOut(index = 1, enabled = isLiquidEnabled),
                        isLiquidEnabled = isLiquidEnabled
                    )
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
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Отключить PIN-код?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Шифрование будет отключено. Все зашифрованные сообщения из Избранного будут безвозвратно удалены, чтобы не оставалось поврежденных данных.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.perform(HapticType.CLICK, hapticEnabled)
                        viewModel.disablePin()
                        showDisablePinDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Удалить и отключить", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisablePinDialog = false },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun WarningBanner(
    modifier: Modifier = Modifier,
    isLiquidEnabled: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val shape = if (isLiquidEnabled) RoundedCornerShape(28.dp) else RoundedCornerShape(20.dp)

    Surface(
        shape = shape,
        color = cs.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(cs.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Шифрование включено",
                    fontWeight = FontWeight.Bold,
                    color = cs.primary,
                    fontSize = 15.sp
                )
            }
            Text(
                text = "Текстовые сообщения зашифрованы на устройстве (AES-GCM-256). Сервер не может их прочитать — только вы, зная PIN.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface
            )
            HorizontalDivider(color = cs.primary.copy(alpha = 0.15f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = cs.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Фото и голосовые НЕ шифруются.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = cs.error
                )
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
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text("Установить PIN-код", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "PIN используется для блокировки Избранного. Текстовые сообщения будут зашифрованы на устройстве.",
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
                enabled = pin.length >= 4 && !mismatch && confirmPin == pin,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Установить", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Отмена")
            }
        }
    )
}