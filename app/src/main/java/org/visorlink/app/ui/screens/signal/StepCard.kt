package org.visorlink.app.ui.screens.signal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.R
import org.visorlink.app.data.activity.StepCounterModel
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlCard
import java.util.Locale

private fun groupDigits(n: Int): String =
    String.format(Locale.US, "%,d", n).replace(',', ' ')

/**
 * Карточка шагов во «Сигнале» (этап 1 активностей, флаг enable_step_activities).
 * Выключена → согласие + ACTIVITY_RECOGNITION; включена → шаги дня, прогресс до
 * следующего порога, начисленные за день Bits (сервер — источник правды).
 */
@Composable
fun StepCard(
    viewModel: StepsViewModel = koinViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    var showConsent by remember { mutableStateOf(false) }
    var showDisable by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRequested = true
        if (granted) {
            viewModel.enable()
        }
    }

    VlCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.DirectionsWalk,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.step_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (ui.enabled) {
                    VlButton(onClick = { showDisable = true }) {
                        Text(stringResource(R.string.step_disable))
                    }
                }
            }

            if (!ui.enabled) {
                Text(
                    text = stringResource(R.string.step_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                VlButton(onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACTIVITY_RECOGNITION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.enable()
                    } else {
                        showConsent = true
                    }
                }) {
                    Text(stringResource(R.string.step_enable))
                }
            } else {
                Text(
                    text = groupDigits(ui.steps),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                Text(
                    text = stringResource(R.string.step_today),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )

                val next = StepCounterModel.nextThreshold(ui.steps, ui.thresholds)
                val prevThreshold = ui.thresholds.lastOrNull { it <= ui.steps }
                val progress = when {
                    next == null -> 1f
                    prevThreshold == null -> ui.steps.toFloat() / next
                    else -> (ui.steps - prevThreshold).toFloat() / (next - prevThreshold)
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (next != null) {
                    val perThreshold =
                        if (ui.boost) StepCounterModel.AWARD_BOOST else StepCounterModel.AWARD_BASE
                    Text(
                        text = stringResource(R.string.step_to_next, groupDigits(next - ui.steps), perThreshold),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.step_all_reached),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.primary
                    )
                }

                if (ui.serverAwarded > 0) {
                    Text(
                        text = stringResource(R.string.step_earned, ui.serverAwarded),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                }
                if (ui.boost) {
                    Text(
                        text = stringResource(R.string.step_boost_hint, StepCounterModel.AWARD_BOOST),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.primary
                    )
                }
                if (ui.trustReason != null) {
                    Text(
                        text = if (ui.trustDaysLeft > 0) stringResource(R.string.step_trust_pending, ui.trustDaysLeft)
                        else stringResource(R.string.step_trust_pending_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                when (ui.errorReason) {
                    "rebind-wait" -> Text(
                        text = stringResource(R.string.step_error_rebind),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error
                    )
                    "device-unknown", "bad-signature" -> Text(
                        text = stringResource(R.string.step_error_device),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error
                    )
                    else -> if (ui.errorReason != null) Text(
                        text = stringResource(R.string.step_error_generic),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error
                    )
                }
                if (ui.sensorMissing) {
                    Text(
                        text = stringResource(R.string.step_sensor_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.step_reboot_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (showConsent) {
        StepConsentDialog(
            onAllow = {
                showConsent = false
                permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            },
            onDismiss = { showConsent = false }
        )
    }

    if (showDisable) {
        AlertDialog(
            onDismissRequest = { showDisable = false },
            title = { Text(stringResource(R.string.step_disable_title)) },
            text = { Text(stringResource(R.string.step_disable_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisable = false
                    viewModel.disable(deleteServerData = true)
                }) { Text(stringResource(R.string.step_disable_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisable = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
