package org.visorlink.app.ui.components.saved

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.utils.CkHandshakeManager
import org.visorlink.app.utils.ContentKeyManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMessageActionSheet(onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.action_edit)) },
                leadingContent = { Icon(Icons.Default.Edit, null) },
                modifier = Modifier.clickable(onClick = onEdit)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.saved_action_delete), color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(onClick = onDelete)
            )
        }
    }
}

@Composable
fun SavedEmptyPlaceholder(modifier: Modifier, isEncrypted: Boolean) {
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface

    val infiniteTransition = rememberInfiniteTransition(label = "empty_breath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(breathScale)
                        .background(accentColor.copy(alpha = 0.2f), VlTheme.tokens.shapes.indicator)
                )
                Text("⭐", fontSize = 48.sp)
            }
            Text(stringResource(R.string.saved_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = textColor)
            if (isEncrypted) {
                Surface(shape = VlTheme.tokens.shapes.button, color = accentColor.copy(alpha = 0.1f), modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp), tint = accentColor)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.saved_empty_footer), fontSize = 13.sp, color = accentColor, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun PinInputDialog(
    pinError: Boolean,
    hasBiometric: Boolean,
    keystoreSecurityLevel: org.visorlink.app.utils.KeystoreSecurityLevel = org.visorlink.app.utils.KeystoreSecurityLevel.UNKNOWN,
    onPinEntered: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onBiometric: () -> Unit,
    clearError: () -> Unit,
    // «Забыли код?» — escape-hatch полного стирания (TODO §14.4). null = ссылка не показывается
    // (дневник её не передаёт: стирание общее, одной точки входа достаточно)
    onForgotCode: (() -> Unit)? = null
) {
    var pin by remember { mutableStateOf("") }
    var useBiometrics by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_pin_prompt)) },
        text = {
            Column {
                VlTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                            pin = it
                            clearError()
                        }
                    },
                    label = stringResource(R.string.saved_pin_hint),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = pinError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (pinError) {
                    Text(stringResource(R.string.saved_pin_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }

                if (hasBiometric && keystoreSecurityLevel != org.visorlink.app.utils.KeystoreSecurityLevel.UNKNOWN) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(VlTheme.tokens.shapes.adapt(RoundedCornerShape(8.dp)))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = keystoreSecurityLevel.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!hasBiometric) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(VlTheme.tokens.shapes.indicator)
                            .clickable { useBiometrics = !useBiometrics }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(checked = useBiometrics, onCheckedChange = { useBiometrics = it })
                        Text(stringResource(R.string.saved_biometric_enable), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (onForgotCode != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onForgotCode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Забыли код?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPinEntered(pin, useBiometrics) },
                enabled = pin.length in 4..8
            ) { Text(stringResource(R.string.saved_action_unlock)) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasBiometric) {
                    IconButton(onClick = onBiometric) {
                        Icon(Icons.Default.Fingerprint, tint = MaterialTheme.colorScheme.primary, contentDescription = stringResource(R.string.saved_biometric_subtitle))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

/**
 * Подтверждение полного стирания «Избранного» и дневника (забытый recovery-код, TODO §14.4):
 * для активации нужно ввести слово УДАЛИТЬ — защита от случайного нажатия.
 */
@Composable
fun WipeAllConfirmDialog(
    inProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var word by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text("Стереть всё?") },
        text = {
            Column {
                Text(
                    "Будут удалены все Избранное и записи дневника, медиа и ключи шифрования. Отменить это нельзя.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                VlTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = "Введите УДАЛИТЬ",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (inProgress) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Стираем…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !inProgress && word == "УДАЛИТЬ",
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Стереть навсегда") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * Ввод текущего PIN для сброса ключа восстановления (TODO §14.4): проверка выполняется
 * в ViewModel разворотом PIN-обёртки, ошибка показывается здесь же (диалог не закрывается).
 */
@Composable
fun RecoveryResetPinDialog(
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    clearError: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text("Сбросить ключ восстановления?") },
        text = {
            Column {
                Text(
                    "Введите текущий PIN. Контент будет пере-шифрован под новый ключ, старый код восстановления перестанет действовать — новый будет показан один раз.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                VlTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { char -> char.isDigit() }) {
                            pin = it
                            clearError()
                        }
                    },
                    label = stringResource(R.string.saved_pin_hint),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin) },
                enabled = pin.length in 4..8
            ) { Text("Продолжить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * Диалог одобрения выдачи ключа контента новому браузеру (E2EE-хендшейк, TODO §14.3–14.4).
 * Показываем код сверки — «Разрешить» только если он совпадает с кодом в браузере:
 * конверт AES-GCM(CK) на одноразовом ECDH-секрете кладётся в заявку (сервер видит только
 * ciphertext). «Отклонить»/закрытие — статус rejected, веб откатится к recovery-коду.
 */
@Composable
fun CkHandshakeDialog(
    uid: String,
    requestId: String,
    pushClient: String?,
    handshake: CkHandshakeManager,
    contentKeyManager: ContentKeyManager,
    onDone: () -> Unit
) {
    var info by remember { mutableStateOf<CkHandshakeManager.CkRequestInfo?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }
    var inProgress by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Загрузка заявки: статус/свежесть + код сверки из ephemeralPub (тот же расчёт, что на вебе)
    LaunchedEffect(uid, requestId) {
        val loaded = handshake.loadRequest(uid, requestId)
        when {
            loaded == null -> loadFailed = true
            loaded.expired -> {
                info = loaded
                resultMessage = "Заявка устарела. Обновите запрос в браузере."
            }
            else -> {
                info = loaded
                code = try {
                    CkHandshakeManager.verificationCode(
                        android.util.Base64.decode(loaded.ephemeralPubB64, android.util.Base64.NO_WRAP)
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    // На этом телефоне CK ещё не заведён — одобрять нечего (веб пойдёт через recovery-код)
    val hasCK = contentKeyManager.hasCK(uid, ContentKeyManager.DOMAIN_SAVED)

    fun rejectAndClose() {
        if (inProgress) return
        inProgress = true
        scope.launch {
            handshake.rejectRequest(uid, requestId)
            inProgress = false
            onDone()
        }
    }

    fun approve() {
        val current = info ?: return
        if (inProgress || code == null || !hasCK) return
        inProgress = true
        scope.launch {
            val outcome = handshake.approveRequest(
                uid, requestId, current,
                contentKeyManager.getOrCreateCK(uid, ContentKeyManager.DOMAIN_SAVED)
            )
            when (outcome) {
                is CkHandshakeManager.ApproveResult.Approved -> onDone()
                is CkHandshakeManager.ApproveResult.Stale -> {
                    resultMessage = "Заявка устарела. Обновите запрос в браузере."
                    inProgress = false
                }
                is CkHandshakeManager.ApproveResult.Error -> {
                    resultMessage = "Не удалось передать ключ. Попробуйте ещё раз."
                    inProgress = false
                }
            }
        }
    }

    // Имя браузера: короткое из пуша, иначе обрезанный UA из заявки
    val clientName = pushClient?.takeIf { it.isNotBlank() }
        ?: info?.client?.take(60)?.takeIf { it.isNotBlank() }
        ?: "браузер"

    AlertDialog(
        onDismissRequest = {
            if (resultMessage != null || loadFailed) onDone() else rejectAndClose()
        },
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text("Запрос ключа шифрования") },
        text = {
            Column {
                when {
                    loadFailed -> {
                        Text(
                            "Заявка не найдена или недоступна.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inProgress && code == null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Загрузка…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        Text(
                            "Браузер $clientName запрашивает ключ шифрования «Избранного».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // Код сверки — крупно, пользователь сверяет его с браузером
                        Surface(
                            shape = VlTheme.tokens.shapes.adapt(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 10.dp)) {
                                Text(
                                    "Код подтверждения:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    code ?: "…",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Разрешайте только если код совпадает с показанным в браузере.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!hasCK && code != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Сначала обновите шифрование в приложении",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (inProgress) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Передаём ключ…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (resultMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                resultMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (resultMessage != null || loadFailed) {
                TextButton(onClick = onDone) { Text("Закрыть") }
            } else {
                TextButton(
                    onClick = { approve() },
                    enabled = !inProgress && code != null && hasCK
                ) { Text("Разрешить") }
            }
        },
        dismissButton = {
            if (resultMessage == null && !loadFailed) {
                TextButton(onClick = { rejectAndClose() }, enabled = !inProgress) { Text("Отклонить") }
            }
        }
    )
}
