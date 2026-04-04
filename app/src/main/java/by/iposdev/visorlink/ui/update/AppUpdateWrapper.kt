package by.iposdev.visorlink.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import by.iposdev.visorlink.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import by.iposdev.visorlink.utils.ApkDownloader

@Composable
fun AppUpdateWrapper(
    viewModel: AppUpdateViewModel,
    content: @Composable () -> Unit
) {
    val updateState by viewModel.updateState.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val context = LocalContext.current

    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf(false) }

    LaunchedEffect(updateState) {
        downloadProgress = 0f
        isDownloading = false
        downloadError = false
    }

    content()

    when (val state = updateState) {
        is UpdateState.Required -> UpdateDialog(
            title = "${stringResource(R.string.update_required_title)} ${state.versionName}",
            body = stringResource(R.string.update_required_body),
            changelog = state.changelog,
            dismissible = false,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            downloadError = downloadError,
            onUpdate = {
                isDownloading = true
                downloadError = false
                ApkDownloader.downloadAndInstall(
                    context = context,
                    url = state.url,
                    fileName = currentChannel.fileName,
                    onProgress = { p ->
                        if (p < 0f) { downloadError = true; isDownloading = false }
                        else downloadProgress = p
                    },
                    onComplete = { isDownloading = false }
                )
            },
            onDismiss = null
        )

        is UpdateState.Recommended -> UpdateDialog(
            title = "${stringResource(R.string.update_recommended_title)} ${state.versionName}",
            body = stringResource(R.string.update_recommended_body),
            changelog = state.changelog,
            dismissible = true,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            downloadError = downloadError,
            onUpdate = {
                isDownloading = true
                downloadError = false
                ApkDownloader.downloadAndInstall(
                    context = context,
                    url = state.url,
                    fileName = currentChannel.fileName,
                    onProgress = { p ->
                        if (p < 0f) { downloadError = true; isDownloading = false }
                        else downloadProgress = p
                    },
                    onComplete = { isDownloading = false }
                )
            },
            onDismiss = { viewModel.dismissRecommendedUpdate() }
        )

        else -> {}
    }
}

// ─── Dialog ───────────────────────────────────────────────────────────────────

@Composable
private fun UpdateDialog(
    title: String,
    body: String,
    changelog: ChangelogInfo,
    dismissible: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadError: Boolean,
    onUpdate: () -> Unit,
    onDismiss: (() -> Unit)?
) {
    val animatedProgress by animateFloatAsState(
        targetValue = downloadProgress,
        animationSpec = tween(300),
        label = "dl_progress"
    )
    val showProgress = isDownloading || downloadProgress > 0f
    var changelogExpanded by remember { mutableStateOf(false) }
    val hasChangelog = changelog.entries != null || changelog.tooOld

    AlertDialog(
        onDismissRequest = {
            if (dismissible && !isDownloading) onDismiss?.invoke()
        },
        properties = DialogProperties(
            dismissOnBackPress = dismissible && !isDownloading,
            dismissOnClickOutside = dismissible && !isDownloading
        ),
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(body, style = MaterialTheme.typography.bodyMedium)

                // ── Changelog секция ──────────────────────────────────────────
                if (hasChangelog) {
                    ChangelogSection(
                        info = changelog,
                        expanded = changelogExpanded,
                        onToggle = { changelogExpanded = !changelogExpanded }
                    )
                }

                // ── Прогресс загрузки ─────────────────────────────────────────
                AnimatedVisibility(
                    visible = showProgress,
                    enter = fadeIn(tween(200)) + expandVertically(),
                    exit = fadeOut(tween(200)) + shrinkVertically()
                ) {
                    DownloadProgressBlock(
                        progress = downloadProgress,
                        animatedProgress = animatedProgress,
                        downloadError = downloadError
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !isDownloading && downloadProgress < 1f
            ) {
                Text(
                    when {
                        isDownloading -> stringResource(R.string.update_btn_downloading)
                        downloadProgress >= 1f -> stringResource(R.string.update_btn_installing)
                        downloadError -> stringResource(R.string.update_btn_retry)
                        else -> stringResource(R.string.update_btn_update)
                    }
                )
            }
        },
        dismissButton = if (onDismiss != null && !isDownloading) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_btn_later)) } }
        } else null,
        modifier = Modifier.fillMaxWidth()
    )
}

// ─── Changelog section ────────────────────────────────────────────────────────

@Composable
private fun ChangelogSection(
    info: ChangelogInfo,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.update_whats_new),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp)
                )
                if (!info.tooOld) {
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess
                            else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) stringResource(R.string.update_changelog_collapse) else stringResource(R.string.update_changelog_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Контент
            AnimatedVisibility(
                visible = expanded || info.tooOld,
                enter = expandVertically() + fadeIn(tween(200)),
                exit = shrinkVertically() + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 12.dp, end = 12.dp, bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (info.tooOld) {
                        Text(
                            stringResource(R.string.update_changelog_too_old),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 10.dp, vertical = 6.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    info.channelTag,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        textDecoration = TextDecoration.Underline
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    } else {
                        info.entries?.forEach { entry ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    entry.removePrefix("•").removePrefix("–")
                                        .removePrefix("-").trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Download progress block ──────────────────────────────────────────────────

@Composable
private fun DownloadProgressBlock(
    progress: Float,
    animatedProgress: Float,
    downloadError: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    downloadError -> stringResource(R.string.update_dl_error)
                    progress >= 1f -> stringResource(R.string.update_dl_complete)
                    progress > 0f -> stringResource(R.string.update_dl_progress)
                    else -> stringResource(R.string.update_dl_starting)
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    downloadError -> MaterialTheme.colorScheme.error
                    progress >= 1f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (progress > 0f && progress < 1f && !downloadError) {
                Text(
                    stringResource(R.string.update_dl_percent, (progress * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }

        if (!downloadError) {
            if (progress <= 0f) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (progress >= 1f) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    }
}