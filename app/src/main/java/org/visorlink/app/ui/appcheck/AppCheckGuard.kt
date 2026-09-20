package org.visorlink.app.ui.appcheck

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.visorlink.app.BuildConfig
import org.visorlink.app.R
import org.visorlink.app.ui.theme.*
import org.visorlink.app.utils.AppCheckManager
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import org.koin.compose.viewmodel.koinViewModel

/**
 * Wrapper for app content.
 * Shows a dialog if App Check failed to verify client authenticity,
 * but allows to dismiss it and continue (at own risk).
 */
@Composable
fun AppCheckGuard(
    viewModel: AppCheckViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel(),
    content: @Composable () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    var isDismissed by rememberSaveable { mutableStateOf(false) }

    content()

    val isDebug = BuildConfig.DEBUG

    if (state is AppCheckManager.State.Invalid && !isDismissed && !isDebug) {
        val haptic = rememberHaptic()

        UnofficialClientDialog(
            onConfirm = {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                isDismissed = true
            }
        )
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
