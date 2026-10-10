package org.visorlink.app.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.visorlink.app.R
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlDialogButton

/**
 * Медиа хранятся на Google Диске отправителя, а он не подключён: предлагаем подключить прямо
 * из чата. После подключения отложенная отправка уходит сама.
 */
@Composable
fun DriveRequiredDialog(
    connecting: Boolean,
    error: String?,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    VlAlertDialog(
        onDismissRequest = { if (!connecting) onDismiss() },
        icon = { Icon(Icons.Default.AddToDrive, contentDescription = null) },
        title = { Text(stringResource(R.string.drive_required_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.drive_required_body))
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            VlDialogButton(onClick = onConnect, isPrimary = true, isLoading = connecting) {
                Text(stringResource(R.string.drive_required_connect))
            }
        },
        dismissButton = {
            VlDialogButton(onClick = onDismiss, enabled = !connecting) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
