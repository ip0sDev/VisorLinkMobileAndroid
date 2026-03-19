package by.iposdev.visorlink.ui.update

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import by.iposdev.visorlink.R
import by.iposdev.visorlink.utils.ApkDownloader

@Composable
fun AppUpdateWrapper(
    viewModel: AppUpdateViewModel,
    content: @Composable () -> Unit
) {
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current

    // Блокируем кнопку, чтобы не запускать 10 скачиваний подряд
    var isDownloading by remember { mutableStateOf(false) }

    content()

    when (val state = updateState) {
        is UpdateState.Required -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text(stringResource(R.string.update_title)) },
                text = { Text(stringResource(R.string.update_required_body)) },
                confirmButton = {
                    Button(
                        onClick = {
                            isDownloading = true
                            ApkDownloader.downloadAndInstall(context, state.url)
                        },
                        enabled = !isDownloading
                    ) {
                        Text(if (isDownloading) "Загрузка..." else stringResource(R.string.update_action_update))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            )
        }
        is UpdateState.Recommended -> {
            AlertDialog(
                onDismissRequest = { if (!isDownloading) viewModel.dismissRecommendedUpdate() },
                title = { Text(stringResource(R.string.update_title)) },
                text = { Text(stringResource(R.string.update_recommended_body)) },
                confirmButton = {
                    Button(
                        onClick = {
                            isDownloading = true
                            ApkDownloader.downloadAndInstall(context, state.url)
                        },
                        enabled = !isDownloading
                    ) {
                        Text(if (isDownloading) "Загрузка..." else stringResource(R.string.update_action_update))
                    }
                },
                dismissButton = {
                    if (!isDownloading) {
                        TextButton(onClick = { viewModel.dismissRecommendedUpdate() }) {
                            Text(stringResource(R.string.update_action_later))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        else -> {}
    }
}