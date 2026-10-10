package org.visorlink.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.visorlink.app.R
import org.visorlink.app.data.auth.TfaMethod
import org.visorlink.app.data.auth.telegramCodesReady
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlOptionRow
import org.visorlink.app.ui.components.VlSettingsItem
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.screens.auth.TfaStatusRow
import org.visorlink.app.ui.screens.auth.TfaTexts
import org.visorlink.app.ui.screens.auth.sendingRes
import org.visorlink.app.ui.screens.auth.whereText
import org.visorlink.app.ui.theme.VlCategoryTint
import org.visorlink.app.ui.theme.VlTheme

/**
 * «Способы входа» (веб `SignInMethodsSection.jsx`): почта с паролем и Google. Google отвязывается
 * только при наличии пароля — иначе войти было бы нечем; аккаунту из Google можно задать пароль.
 */
@Composable
fun SignInMethodsItems(
    vm: AccountSecurityViewModel,
    /** «Изменить пароль» — строкой сразу под паролем, только если пароль есть. */
    onChangePassword: () -> Unit,
) {
    val methods by vm.methods.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.signInError.collectAsState()
    val context = LocalContext.current
    var showSetPassword by remember { mutableStateOf(false) }
    var confirmUnlink by remember { mutableStateOf(false) }

    // providerData меняется на месте — перечитываем, когда экран снова виден
    LaunchedEffect(Unit) { vm.refreshMethods() }

    VlSettingsItem(
        icon = Icons.Default.Mail,
        iconColor = VlCategoryTint.Slate,
        title = stringResource(R.string.signin_password),
        subtitle = when {
            methods.hasPassword -> methods.email
            methods.email.isNotBlank() -> stringResource(R.string.signin_password_not_set_for, methods.email)
            else -> stringResource(R.string.signin_password_not_set)
        },
        trailing = {
            if (methods.hasPassword) EnabledMark()
            else VlDialogButton(onClick = { vm.clearSignInError(); showSetPassword = true }, isPrimary = true) {
                Text(stringResource(R.string.signin_set_password))
            }
        },
    )
    if (methods.hasPassword) {
        VlSettingsItem(
            icon = Icons.Default.Password,
            iconColor = VlCategoryTint.Rose,
            title = stringResource(R.string.settings_password_change),
            onClick = { vm.clearSignInError(); onChangePassword() },
        )
    }
    val google = methods.google
    VlSettingsItem(
        icon = ImageVector.vectorResource(R.drawable.ic_google),
        iconColor = VlCategoryTint.Blue,
        title = stringResource(R.string.signin_google),
        subtitle = when {
            google == null -> stringResource(R.string.signin_google_not_linked)
            !methods.hasPassword -> listOf(google, stringResource(R.string.signin_unlink_needs_password)).filter { it.isNotBlank() }.joinToString("\n")
            else -> google
        },
        trailing = {
            when {
                google == null -> VlDialogButton(
                    onClick = { vm.linkGoogle(context) }, isPrimary = true, isLoading = busy == SignInBusy.LINK,
                ) { Text(stringResource(R.string.signin_link)) }
                methods.hasPassword -> VlDialogButton(onClick = { vm.clearSignInError(); confirmUnlink = true }) {
                    Text(stringResource(R.string.signin_unlink))
                }
                else -> EnabledMark()
            }
        },
    )
    // Ошибка привязки (диалога у неё нет)
    if (error != null && !showSetPassword && !confirmUnlink) {
        Text(
            error!!.asString(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }

    if (confirmUnlink) VlAlertDialog(
        onDismissRequest = { confirmUnlink = false },
        title = { Text(stringResource(R.string.signin_unlink)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.signin_unlink_confirm))
                error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        actions = {
            VlDialogButton(onClick = { confirmUnlink = false }) { Text(stringResource(R.string.action_cancel)) }
            VlDialogButton(
                onClick = { vm.unlinkGoogle { confirmUnlink = false } },
                isDestructive = true, isLoading = busy == SignInBusy.UNLINK,
            ) { Text(stringResource(R.string.signin_unlink)) }
        },
    )

    if (showSetPassword) {
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        VlAlertDialog(
            onDismissRequest = { if (busy == null) showSetPassword = false },
            title = { Text(stringResource(R.string.signin_set_password)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.signin_set_password_hint, methods.email),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VlTextField(
                        value = pw, onValueChange = { pw = it; vm.clearSignInError() },
                        label = stringResource(R.string.dialog_password_new),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VlTextField(
                        value = pw2, onValueChange = { pw2 = it; vm.clearSignInError() },
                        label = stringResource(R.string.dialog_password_confirm),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            actions = {
                VlDialogButton(onClick = { showSetPassword = false }, enabled = busy == null) { Text(stringResource(R.string.action_cancel)) }
                VlDialogButton(
                    onClick = { vm.setPassword(context, pw, pw2) { showSetPassword = false } },
                    isPrimary = true, isLoading = busy == SignInBusy.PASSWORD, enabled = pw.isNotEmpty() && pw2.isNotEmpty(),
                ) { Text(stringResource(R.string.signin_set_password)) }
            },
        )
    }
}

/**
 * Двухфакторная защита (веб `TwoFactorSection.jsx`): включение и выключение только кодом
 * (`set2FAEnabled`), со статусом отправки, сроком кода и таймером повтора; под ней — «Коды в Telegram».
 */
@Composable
fun TwoFactorItems(profile: UserProfile?, vm: AccountSecurityViewModel) {
    val tfa by vm.tfa.collectAsState()
    val tgBusy by vm.telegramBusy.collectAsState()
    val enabled = profile?.tfaEnabled == true
    val telegramLinked = profile?.hasTelegram == true
    val telegramOn = profile?.telegramCodesReady() == true

    VlSettingsItem(
        icon = Icons.Default.Security,
        iconColor = VlCategoryTint.Emerald,
        title = stringResource(R.string.settings_tfa_title),
        subtitle = stringResource(if (enabled) R.string.settings_tfa_sub_on else R.string.settings_tfa_sub_off),
        trailing = {
            VlDialogButton(onClick = { vm.startTfa(enabled) }, isPrimary = !enabled, isDestructive = enabled) {
                Text(stringResource(if (enabled) R.string.tfa_manage_disable else R.string.tfa_manage_enable))
            }
        },
    )
    // Без привязки — без кнопки: сначала «Связка с Telegram»
    val telegramTrailing: (@Composable () -> Unit)? = if (!telegramLinked) null else {
        {
            VlDialogButton(
                onClick = { vm.setTelegramCodes(!telegramOn) },
                isPrimary = !telegramOn, isDestructive = telegramOn, isLoading = tgBusy,
            ) { Text(stringResource(if (telegramOn) R.string.tfa_manage_disable else R.string.tfa_manage_enable)) }
        }
    }
    VlSettingsItem(
        icon = Icons.AutoMirrored.Filled.Send,
        iconColor = VlCategoryTint.Telegram,
        title = stringResource(R.string.tfa_tg_title),
        subtitle = when {
            !telegramLinked -> stringResource(R.string.tfa_tg_need_link)
            telegramOn -> stringResource(R.string.tfa_tg_on, profile?.telegramAccount.orEmpty())
            else -> stringResource(R.string.tfa_tg_off)
        },
        trailing = telegramTrailing,
    )

    if (tfa.step != TfaManageStep.IDLE) TfaManageDialog(tfa, telegramOn, vm)
}

@Composable
private fun TfaManageDialog(tfa: TfaManageState, telegramOn: Boolean, vm: AccountSecurityViewModel) {
    val cs = MaterialTheme.colorScheme
    var code by remember { mutableStateOf("") }
    val info = tfa.info
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(info) {
        code = ""
        while (info != null) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val resendIn = info?.let { (it.resendAt - now).coerceAtLeast(0) } ?: 0L
    val expiresIn = info?.let { (it.expiresAt - now).coerceAtLeast(0) } ?: 0L
    val busy = tfa.sending || tfa.confirming

    VlAlertDialog(
        onDismissRequest = { if (!busy) vm.cancelTfa() },
        title = { Text(stringResource(if (tfa.enabling) R.string.tfa_manage_title_enable else R.string.tfa_manage_title_disable)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tfa.step == TfaManageStep.METHOD) {
                    Text(
                        stringResource(if (tfa.enabling) R.string.tfa_manage_confirm_enable else R.string.tfa_manage_confirm_disable),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val methods = buildList {
                        add(TfaMethod.BOT); add(TfaMethod.EMAIL)
                        if (telegramOn) add(TfaMethod.TELEGRAM)
                    }
                    methods.forEachIndexed { i, m ->
                        VlOptionRow(
                            icon = methodIcon(m),
                            label = stringResource(manageMethodRes(m)),
                            selected = tfa.sending && tfa.method == m,
                            index = i, total = methods.size,
                            onClick = { if (!busy) vm.sendTfaCode(m) },
                        )
                    }
                    if (tfa.sending) TfaStatusRow(null, cs.primary, stringResource(sendingRes(tfa.method)))
                } else if (info != null) {
                    TfaStatusRow(
                        icon = if (expiresIn > 0) Icons.Default.Check else Icons.Default.Warning,
                        tint = if (expiresIn > 0) VlTheme.tokens.status.success else VlTheme.tokens.status.warning,
                        title = stringResource(if (info.alreadySent) R.string.tfa_lock_already_sent else R.string.tfa_lock_sent),
                        text = whereText(info),
                        note = if (expiresIn > 0) stringResource(R.string.tfa_lock_valid_for, TfaTexts.mmss(expiresIn))
                        else stringResource(R.string.tfa_lock_code_expired),
                    )
                    VlTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        placeholder = "123456",
                        label = stringResource(R.string.tfa_lock_code_label),
                        isError = tfa.error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val waitResend = resendIn > 0 && expiresIn > 0
                    VlDialogButton(onClick = { vm.sendTfaCode(tfa.method) }, enabled = !busy && !waitResend) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (waitResend) stringResource(R.string.tfa_lock_resend_in, TfaTexts.mmss(resendIn))
                            else stringResource(R.string.tfa_manage_resend)
                        )
                    }
                }
                tfa.error?.let { Text(it.asString(), color = cs.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        actions = {
            VlDialogButton(onClick = { vm.cancelTfa() }, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
            if (tfa.step == TfaManageStep.CODE) VlDialogButton(
                onClick = { vm.confirmTfa(code) },
                isPrimary = true, isDestructive = !tfa.enabling, isLoading = tfa.confirming,
                enabled = code.length == 6 && !busy,
            ) { Text(stringResource(if (tfa.enabling) R.string.tfa_manage_enable else R.string.tfa_manage_disable)) }
        },
    )
}

@Composable
private fun EnabledMark() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
        Icon(Icons.Default.Check, null, tint = VlTheme.tokens.status.success, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.signin_on), style = MaterialTheme.typography.labelLarge, color = VlTheme.tokens.status.success)
    }
}

private fun methodIcon(m: TfaMethod): ImageVector = when (m) {
    TfaMethod.BOT -> Icons.Default.SmartToy
    TfaMethod.EMAIL -> Icons.Default.Email
    TfaMethod.TELEGRAM -> Icons.AutoMirrored.Filled.Send
}

private fun manageMethodRes(m: TfaMethod): Int = when (m) {
    TfaMethod.BOT -> R.string.tfa_manage_via_bot
    TfaMethod.EMAIL -> R.string.tfa_manage_via_email
    TfaMethod.TELEGRAM -> R.string.tfa_lock_via_telegram
}
