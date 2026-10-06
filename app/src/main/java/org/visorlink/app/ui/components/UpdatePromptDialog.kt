package org.visorlink.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.visorlink.app.R

/**
 * Попап обновления из Google Play (play-сборка, `UpdateManager.UpdateHost`). Всегда закрывается:
 * «Позже», «назад» или тап мимо — обновление предлагается, но не навязывается.
 *
 * @param sizeText размер загрузки («12 МБ»), если Play его сообщил.
 */
@Composable
fun UpdateAvailableDialog(sizeText: String?, onUpdate: () -> Unit, onLater: () -> Unit) {
    VlAlertDialog(
        onDismissRequest = onLater,
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.update_recommended_body))
                sizeText?.let {
                    Text(stringResource(R.string.update_play_size, it), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { VlDialogButton(onClick = onUpdate, isPrimary = true) { Text(stringResource(R.string.update_action_update)) } },
        dismissButton = { VlDialogButton(onClick = onLater) { Text(stringResource(R.string.update_action_later)) } },
    )
}

/** Гибкое обновление загружено в фоне: перезапуск ставит его, «Позже» — до следующего запуска. */
@Composable
fun UpdateReadyDialog(onRestart: () -> Unit, onLater: () -> Unit) {
    VlAlertDialog(
        onDismissRequest = onLater,
        icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
        title = { Text(stringResource(R.string.update_play_ready_title)) },
        text = { Text(stringResource(R.string.update_play_ready_body)) },
        confirmButton = { VlDialogButton(onClick = onRestart, isPrimary = true) { Text(stringResource(R.string.update_play_restart)) } },
        dismissButton = { VlDialogButton(onClick = onLater) { Text(stringResource(R.string.update_action_later)) } },
    )
}
