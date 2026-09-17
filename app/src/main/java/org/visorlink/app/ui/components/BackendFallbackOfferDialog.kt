package org.visorlink.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.visorlink.app.R

@Composable
fun BackendFallbackOfferDialog(
    onDismissRequest: () -> Unit,
    onConfirmFallback: () -> Unit
) {
    VlAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(R.string.backend_fallback_dialog_title))
        },
        text = {
            Text(stringResource(R.string.backend_fallback_dialog_desc))
        },
        actions = {
            VlDialogButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.backend_fallback_action_stay))
            }
            VlDialogButton(onClick = onConfirmFallback, isPrimary = true) {
                Text(stringResource(R.string.backend_fallback_action_switch))
            }
        }
    )
}
