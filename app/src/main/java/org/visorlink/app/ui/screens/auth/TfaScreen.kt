package org.visorlink.app.ui.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.R
import org.visorlink.app.data.auth.TfaCodeInfo
import org.visorlink.app.data.auth.TfaGate
import org.visorlink.app.data.auth.TfaMethod
import org.visorlink.app.data.auth.tfaMethods
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlSegmentedControl
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.theme.VlTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран кода 2FA при входе (веб `TwoFactorLock.jsx`): статус отправки виден всегда и
 * озвучивается TalkBack, код проверяется сам при вводе 6 цифр, способ отправки — чипами.
 * Закрывается не сам, а воротами 2FA в NavGraph, когда сессия попала в белый список.
 */
@Composable
fun TfaScreen(
    onTfaPassed: () -> Unit,
    viewModel: AuthViewModel,
    lock: TfaLockViewModel = koinViewModel(),
) {
    val state by lock.state.collectAsState()
    val profile by lock.profile.collectAsState()
    val gate by viewModel.tfaGate.collectAsState()
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens

    var code by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val shakeX = remember { Animatable(0f) }

    LaunchedEffect(Unit) { lock.start() }
    LaunchedEffect(state.verify) { if (state.verify == TfaVerifyStatus.OK) onTfaPassed() }
    // Код ушёл — фокус в поле
    LaunchedEffect(state.send) { if (state.send == TfaSendStatus.SENT) runCatching { focus.requestFocus() } }
    // Неверный код: поле очищается и «встряхивается», фокус остаётся
    LaunchedEffect(state.shake) {
        if (state.shake == 0) return@LaunchedEffect
        code = ""
        runCatching { focus.requestFocus() }
        shakeX.animateTo(0f, keyframes {
            durationMillis = 360
            -14f at 50; 12f at 110; -8f at 170; 6f at 230; -3f at 290
        })
    }

    // Часы для обратных отсчётов
    val info = state.info
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(info) {
        while (info != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val resendIn = info?.let { (it.resendAt - now).coerceAtLeast(0) } ?: 0L
    val expiresIn = info?.let { (it.expiresAt - now).coerceAtLeast(0) } ?: 0L
    val expired = info != null && expiresIn == 0L
    val needNewCode = expired || state.needsNewCodeByError
    val ok = state.verify == TfaVerifyStatus.OK
    val methods = profile.tfaMethods()
    val account = profile?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: profile?.email.orEmpty()

    // Экран рисуется в Box, а не в Surface: без этого Text берёт чёрный цвет по умолчанию,
    // и в тёмной теме заголовок пропадает
    Box(Modifier.fillMaxSize().background(cs.surface)) {
      CompositionLocalProvider(LocalContentColor provides cs.onSurface) {
        VlAmbientGlow()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            // ── Щит / галочка ──
            Box(
                Modifier
                    .size(72.dp)
                    .clip(tokens.shapes.adapt(CircleShape))
                    .background(if (ok) tokens.status.success.copy(alpha = 0.18f) else cs.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ok) Icons.Default.Check else Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = if (ok) tokens.status.success else cs.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                stringResource(if (ok) R.string.tfa_lock_done else R.string.tfa_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.tfa_lock_sub, account),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if ((gate as? TfaGate.Required)?.slow == true && !ok) {
                TfaStatusRow(Icons.Default.CloudOff, tokens.status.warning, stringResource(R.string.tfa_lock_check_slow))
            }

            // ── Статус отправки (озвучивается) ──
            Box(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                when {
                    state.send == TfaSendStatus.SENDING -> TfaStatusRow(
                        icon = null, tint = cs.primary,
                        text = stringResource(sendingRes(state.method)),
                    )
                    state.send == TfaSendStatus.FAILED -> TfaStatusRow(
                        Icons.Default.Warning, cs.error, state.sendError?.asString().orEmpty(),
                    )
                    info != null -> TfaStatusRow(
                        icon = if (expired) Icons.Default.Warning else Icons.Default.Check,
                        tint = if (expired) tokens.status.warning else tokens.status.success,
                        title = stringResource(if (info.alreadySent) R.string.tfa_lock_already_sent else R.string.tfa_lock_sent),
                        text = whereText(info),
                        note = if (expired) stringResource(R.string.tfa_lock_code_expired)
                        else stringResource(R.string.tfa_lock_valid_until, hhmm(info.expiresAt), TfaTexts.mmss(expiresIn)),
                    )
                }
            }

            // ── Поле кода: автопроверка при 6 цифрах ──
            VlTextField(
                value = code,
                onValueChange = { raw ->
                    val digits = raw.filter(Char::isDigit).take(6)
                    code = digits
                    lock.onCodeEdited()
                    if (digits.length == 6) lock.verify(digits)
                },
                label = stringResource(R.string.tfa_lock_code_label),
                enabled = state.verify != TfaVerifyStatus.CHECKING && !ok,
                isError = state.verify == TfaVerifyStatus.FAILED,
                supportingText = state.verifyError?.asString(),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { lock.verify(code) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = shakeX.value * density }
                    .focusRequester(focus),
            )

            VlButton(
                onClick = { lock.verify(code) },
                enabled = code.length == 6 && !state.busy && !needNewCode,
            ) {
                when (state.verify) {
                    TfaVerifyStatus.CHECKING -> {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tfa_lock_checking), fontWeight = FontWeight.Bold)
                    }
                    TfaVerifyStatus.OK -> {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tfa_lock_done), fontWeight = FontWeight.Bold)
                    }
                    else -> Text(stringResource(R.string.tfa_lock_confirm), fontWeight = FontWeight.Bold)
                }
            }

            // ── Повторная отправка ──
            val waitResend = resendIn > 0 && !needNewCode && state.send != TfaSendStatus.FAILED
            VlDialogButton(onClick = { lock.sendCode(state.method) }, enabled = !state.busy && !waitResend) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (waitResend) stringResource(R.string.tfa_lock_resend_in, TfaTexts.mmss(resendIn))
                    else stringResource(R.string.tfa_lock_resend)
                )
            }

            // ── Куда отправить код ──
            Text(
                stringResource(R.string.tfa_lock_methods),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
            )
            val active = info?.method ?: state.method
            VlSegmentedControl(
                labels = methods.map { stringResource(methodRes(it)) },
                selectedIndex = methods.indexOf(active).coerceAtLeast(0),
                onSelected = { i ->
                    val m = methods[i]
                    // Тот же способ, живой код уже в пути — нажатие ничего не шлёт
                    val sameLive = info?.method == m && state.send == TfaSendStatus.SENT && !needNewCode
                    if (!state.busy && !sameLive) lock.sendCode(m)
                },
            )
            if (info?.alreadySent == true && state.requested != null && info.method != state.requested && state.send == TfaSendStatus.SENT) {
                Text(
                    stringResource(R.string.tfa_lock_switch_hint, TfaTexts.mmss(resendIn)),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(4.dp))
            VlDialogButton(onClick = { viewModel.logout() }) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.tfa_lock_logout))
            }
        }
      }
    }
}

/** Строка статуса: иконка (или спиннер, если [icon] == null), жирный заголовок, текст и пометка. */
@Composable
internal fun TfaStatusRow(
    icon: ImageVector?,
    tint: Color,
    text: String,
    title: String? = null,
    note: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(VlTheme.tokens.shapes.rounded(14.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon == null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = tint)
        else Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (title != null) "$title $text" else text,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
            )
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
internal fun whereText(info: TfaCodeInfo): String = when (info.method) {
    TfaMethod.EMAIL -> stringResource(R.string.tfa_lock_where_email, info.destination.orEmpty())
    TfaMethod.TELEGRAM -> stringResource(R.string.tfa_lock_where_telegram, info.destination.orEmpty())
    TfaMethod.BOT -> stringResource(R.string.tfa_lock_where_bot)
}

internal fun sendingRes(m: TfaMethod): Int = when (m) {
    TfaMethod.BOT -> R.string.tfa_lock_sending_bot
    TfaMethod.EMAIL -> R.string.tfa_lock_sending_email
    TfaMethod.TELEGRAM -> R.string.tfa_lock_sending_telegram
}

internal fun methodRes(m: TfaMethod): Int = when (m) {
    TfaMethod.BOT -> R.string.tfa_lock_via_bot
    TfaMethod.EMAIL -> R.string.tfa_lock_via_email
    TfaMethod.TELEGRAM -> R.string.tfa_lock_via_telegram
}

internal fun hhmm(ts: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

/** Экран «Проверяем сессию…» — пока ворота 2FA ждут ответа сервера. */
@Composable
fun SessionCheckScreen() {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.surface), contentAlignment = Alignment.Center) {
        VlAmbientGlow()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = cs.primary)
            Text(
                stringResource(R.string.tfa_lock_checking_session),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
