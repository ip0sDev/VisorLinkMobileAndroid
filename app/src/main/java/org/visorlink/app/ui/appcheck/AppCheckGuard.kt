package org.visorlink.app.ui.appcheck

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.BuildConfig
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.theme.*
import org.visorlink.app.utils.AppCheckManager
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic

/**
 * Wrapper for app content.
 *
 * 1. Before initial setup / onboarding (when user is not logged in and initial attestation is not done):
 *    Blocks content with a security attestation loading screen. If attestation fails, shows
 *    an explicit warning with "Retry" and "Continue at Own Risk" buttons.
 *
 * 2. Normal launch (after initial setup):
 *    Renders content immediately without blocking. Checks in the background. If attestation fails,
 *    shows an unofficial client alert dialog ONCE per app version.
 */
@Composable
fun AppCheckGuard(
    viewModel: AppCheckViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsStateWithLifecycle()
    val haptic = rememberHaptic()

    val isLoggedIn = remember { FirebaseAuth.getInstance().currentUser != null }
    var initialDone by remember {
        mutableStateOf(isLoggedIn || AppCheckManager.isInitialAttestationDone(context))
    }

    // Запуск проверки до первоначальной настройки при первом входе
    LaunchedEffect(initialDone) {
        if (!initialDone) {
            viewModel.check(forceRefresh = false)
        }
    }

    LaunchedEffect(state) {
        if (!initialDone && state is AppCheckManager.State.Valid) {
            AppCheckManager.setInitialAttestationDone(context, true)
            initialDone = true
        }
    }

    val isDebug = BuildConfig.DEBUG

    // Сценарий 1: Первичная аттестация перед начальной настройкой (блокирующий экран)
    if (!initialDone && !isDebug) {
        PreSetupAttestationScreen(
            state = state,
            onRetry = {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                viewModel.retry()
            },
            onProceedAnyway = {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                viewModel.proceedAnyway()
                initialDone = true
            }
        )
        return
    }

    // Сценарий 2: Обычный запуск — отображаем контент сразу
    content()

    // Фоновое разовое предупреждение на текущую версию приложения
    var dialogDismissed by rememberSaveable { mutableStateOf(false) }
    val shouldShowWarning = remember(state, dialogDismissed) {
        state is AppCheckManager.State.Invalid &&
                !dialogDismissed &&
                !isDebug &&
                AppCheckManager.shouldShowWarningForCurrentVersion(context)
    }

    if (shouldShowWarning) {
        UnofficialClientDialog(
            onConfirm = {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                AppCheckManager.markWarningShownForCurrentVersion(context)
                dialogDismissed = true
            }
        )
    }
}

@Composable
private fun PreSetupAttestationScreen(
    state: AppCheckManager.State,
    onRetry: () -> Unit,
    onProceedAnyway: () -> Unit
) {
    val isInvalid = state is AppCheckManager.State.Invalid

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isInvalid) Icons.Default.Warning else Icons.Default.Security,
                contentDescription = null,
                tint = if (isInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(
                    if (isInvalid) R.string.appcheck_precheck_failed_title
                    else R.string.appcheck_precheck_title
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(
                    if (isInvalid) R.string.appcheck_precheck_failed_desc
                    else R.string.appcheck_precheck_desc
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            if (!isInvalid) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                VlButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.appcheck_retry))
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onProceedAnyway,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.appcheck_action_continue_anyway))
                }
            }
        }
    }
}

@Composable
private fun UnofficialClientDialog(
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.unofficial_client_detected),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.this_build_of_visorlink_has_not_been_verified_by_our_security_system),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.you_may_be_using_a_modified_or_unofficial_version_of_the_app) +
                            stringResource(R.string.for_your_security_and_the_security_of_your_conversations) +
                            stringResource(R.string.please_use_the_official_build),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.some_features_may_be_unavailable_or_behave_unexpectedly) +
                                stringResource(R.string.until_you_switch_to_the_official_app),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.i_understand))
            }
        },
        dismissButton = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    )
}
