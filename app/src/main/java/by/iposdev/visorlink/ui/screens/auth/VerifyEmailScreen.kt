package by.iposdev.visorlink.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.R
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shown after registration when email_verified == false.
 * Per guideline §5: polls every 5 s, provides Resend with UX cooldown.
 * Per guideline §6: user cannot proceed to the main app from here —
 * navigation is controlled by the root nav guard (AuthState).
 */
@Composable
fun VerifyEmailScreen(
    onVerified: () -> Unit,           // called when polling detects email_verified == true
    onLogout: () -> Unit,             // "Use different account" escape hatch
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.verifyState.collectAsState()

    // Start / stop polling tied to composition lifecycle
    DisposableEffect(Unit) {
        viewModel.startVerificationPolling()
        onDispose { viewModel.stopVerificationPolling() }
    }

    // Navigate out as soon as verified
    LaunchedEffect(state.verified) {
        if (state.verified) onVerified()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Icon ────────────────────────────────────────────────────────
            Icon(
                imageVector = Icons.Default.MarkEmailRead,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(24.dp))

            // ── Headline ────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.verify_email_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.verify_email_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Polling indicator
            if (state.isPolling) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = stringResource(R.string.verify_email_checking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Resend button ────────────────────────────────────────────────
            Button(
                onClick = { viewModel.resendVerificationEmail() },
                enabled = state.resendCooldown == 0 && !state.resendLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                when {
                    state.resendLoading -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    state.resendCooldown > 0 -> {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(
                            text = stringResource(
                                R.string.verify_email_resend_cooldown,
                                state.resendCooldown
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(
                            text = stringResource(R.string.verify_email_resend),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // ── Error message ────────────────────────────────────────────────
            AnimatedVisibility(visible = state.error != null) {
                state.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Use different account ────────────────────────────────────────
            TextButton(onClick = {
                viewModel.logout()
                onLogout()
            }) {
                Text(stringResource(R.string.verify_email_use_different_account))
            }
        }
    }
}