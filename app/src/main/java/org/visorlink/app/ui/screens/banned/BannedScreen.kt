package org.visorlink.app.ui.screens.banned

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.repository.AppealError
import org.visorlink.app.data.repository.AppealException
import org.visorlink.app.data.repository.AppealStatus
import org.visorlink.app.data.repository.BanRepository
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.screens.auth.AuthViewModel

private const val TERMS_URL = "https://visorlink.org/terms-of-service"
private const val DELETE_ACCOUNT_URL = "https://visorlink.org/delete-account"

/**
 * Блокировка аккаунта сервисом: пока `users/{uid}.banned == true`, вместо всего приложения
 * показывается [BannedScreen]. Профиль читается и у заблокированного (правила пускают к своему
 * документу), поэтому при снятии блокировки приложение возвращается само.
 */
@Composable
fun BanGuard(
    profile: UserProfile?,
    authViewModel: AuthViewModel,
    content: @Composable () -> Unit,
) {
    if (profile?.banned == true) {
        BannedScreen(profile = profile, onSignOut = { authViewModel.logout() })
    } else {
        content()
    }
}

@Composable
fun BannedScreen(profile: UserProfile, onSignOut: () -> Unit) {
    val repo: BanRepository = koinInject()
    val uid = profile.uid
    val ban by remember(uid) { if (uid.isEmpty()) flowOf(null) else repo.banFlow(uid) }
        .collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var text by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    // Сразу после успешной отправки прячем форму, не дожидаясь снимка bans/{uid}
    var justSent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AppealError?>(null) }

    val length = BanRepository.appealLength(text)
    val overLimit = length > BanRepository.APPEAL_MAX
    val status = ban?.appealStatus ?: AppealStatus.NONE
    val showPending = status == AppealStatus.PENDING || (justSent && status == AppealStatus.NONE)
    val reason = ban?.reason?.takeIf { it.isNotBlank() }
        ?: profile.banReason?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.ban_reason_default)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.ban_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ban_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            VlSurface(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.ban_reason_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(reason, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))

            val shownText = ban?.appealText
            when {
                status == AppealStatus.REJECTED -> AppealResult(
                    title = stringResource(R.string.ban_appeal_rejected_title),
                    note = ban?.resolutionNote?.takeIf { it.isNotBlank() },
                    appealText = shownText,
                    isError = true,
                )
                showPending -> AppealResult(
                    title = stringResource(R.string.ban_appeal_pending_title),
                    note = stringResource(R.string.ban_appeal_pending_body),
                    appealText = shownText ?: text.takeIf { justSent && it.isNotBlank() },
                    isError = false,
                )
                else -> {
                    VlTextField(
                        value = text,
                        // Длина считается в символах, не в UTF-16: ограничивать по length нельзя,
                        // иначе обрежется эмодзи. Лишнее подсвечиваем и не даём отправить.
                        onValueChange = { text = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.ban_appeal_label),
                        singleLine = false,
                        maxLines = 6,
                        enabled = !sending,
                        isError = overLimit || error != null,
                        supportingText = error?.let { appealErrorText(it) }
                            ?: stringResource(R.string.ban_appeal_hint),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.ban_appeal_counter, length, BanRepository.APPEAL_MAX),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overLimit) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End),
                    )
                    Spacer(Modifier.height(12.dp))
                    VlButton(
                        onClick = {
                            if (sending) return@VlButton
                            sending = true
                            error = null
                            scope.launch {
                                try {
                                    repo.submitAppeal(text)
                                    justSent = true
                                } catch (e: AppealException) {
                                    // Апелляция уже есть — подписка на bans/{uid} сама покажет статус
                                    if (e.error == AppealError.ALREADY_SENT) justSent = true
                                    error = e.error
                                } finally {
                                    sending = false
                                }
                            }
                        },
                        enabled = !sending && text.isNotBlank() && !overLimit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(if (sending) R.string.ban_appeal_sending else R.string.ban_appeal_submit))
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            VlButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ban_sign_out))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                VlDialogButton(onClick = { runCatching { uriHandler.openUri(TERMS_URL) } }) {
                    Text(stringResource(R.string.ban_terms))
                }
                VlDialogButton(onClick = { runCatching { uriHandler.openUri(DELETE_ACCOUNT_URL) } }, isDestructive = true) {
                    Text(stringResource(R.string.ban_delete_account))
                }
            }
        }
    }
}

@Composable
private fun AppealResult(title: String, note: String?, appealText: String?, isError: Boolean) {
    VlSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            if (note != null) {
                Spacer(Modifier.height(6.dp))
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }
            if (!appealText.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.ban_appeal_your_text),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(appealText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun appealErrorText(e: AppealError): String = stringResource(
    when (e) {
        AppealError.EMPTY -> R.string.ban_err_empty
        AppealError.TOO_LONG -> R.string.ban_err_too_long
        AppealError.ALREADY_SENT -> R.string.ban_err_already_sent
        AppealError.NOT_BANNED -> R.string.ban_err_not_banned
        AppealError.OTHER -> R.string.ban_err_other
    }
)
