package org.visorlink.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlAmbientGlow
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlTextField

/**
 * «Завершите регистрацию»: вошли через Google, а профиля нет. Регистрация по-прежнему только
 * по инвайту — Google заменяет почту и пароль, но не код приглашения. Пока профиля нет,
 * NavGraph никуда, кроме этого шага, не пускает.
 */
@Composable
fun GoogleSignupScreen(viewModel: AuthViewModel) {
    val state by viewModel.googleSignup.collectAsState()
    val pendingInvite by viewModel.pendingInviteCode.collectAsState()
    val cs = MaterialTheme.colorScheme
    val email = viewModel.currentEmail.orEmpty()

    var username by rememberSaveable { mutableStateOf(AuthViewModel.defaultUsername(email)) }
    var invite by rememberSaveable { mutableStateOf(pendingInvite.orEmpty()) }
    LaunchedEffect(pendingInvite) { if (invite.isBlank() && !pendingInvite.isNullOrBlank()) invite = pendingInvite!! }

    Box(Modifier.fillMaxSize().background(cs.surface)) {
      CompositionLocalProvider(LocalContentColor provides cs.onSurface) {
        VlAmbientGlow()
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Text(
                stringResource(R.string.auth_google_complete_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.auth_google_complete_lead, email),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            VlTextField(
                value = username,
                onValueChange = {
                    username = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(24)
                    viewModel.clearGoogleSignupError()
                },
                label = stringResource(R.string.auth_google_username_label),
                placeholder = "username",
                leading = { Icon(Icons.Default.AlternateEmail, null, tint = cs.primary) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            VlTextField(
                value = invite,
                onValueChange = { invite = it.trim(); viewModel.clearGoogleSignupError() },
                label = stringResource(R.string.auth_google_invite_label),
                placeholder = stringResource(R.string.register_invite_code_hint),
                leading = { Icon(Icons.Default.ConfirmationNumber, null, tint = cs.primary) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let {
                Text(it.asString(), color = cs.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            VlButton(
                onClick = { viewModel.completeGoogleSignup(username, invite) },
                enabled = !state.isLoading && username.length >= 3 && invite.isNotBlank(),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                } else {
                    Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.auth_google_complete_btn), fontWeight = FontWeight.Bold)
            }
            VlDialogButton(onClick = { viewModel.cancelGoogleSignup() }, enabled = !state.isLoading, isDestructive = true) {
                Text(stringResource(R.string.auth_google_cancel))
            }
        }
      }
    }
}
