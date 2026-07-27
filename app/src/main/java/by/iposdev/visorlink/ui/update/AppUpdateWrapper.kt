package by.iposdev.visorlink.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.theme.exthruRaisedShadow
import by.iposdev.visorlink.ui.theme.exthruSmallRaisedShadow
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.utils.ApkDownloader
import java.util.Locale

@Composable
fun AppUpdateWrapper(
    viewModel: AppUpdateViewModel,
    content: @Composable () -> Unit
) {
    val updateState by viewModel.updateState.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val context = LocalContext.current

    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadSpeed by remember { mutableLongStateOf(0L) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf(false) }

    LaunchedEffect(updateState) {
        downloadProgress = 0f
        downloadSpeed = 0L
        isDownloading = false
        downloadError = false
    }

    content()

    when (val state = updateState) {
        is UpdateState.Required -> BiolumeUpdateDialog(
            title = "${stringResource(R.string.update_required_title)} ${state.versionName}",
            body = stringResource(R.string.update_required_body),
            changelog = state.changelog,
            dismissible = false,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            downloadSpeed = downloadSpeed,
            downloadError = downloadError,
            onUpdate = {
                isDownloading = true
                downloadError = false
                ApkDownloader.downloadAndInstall(
                    context = context,
                    url = state.url,
                    fileName = currentChannel.fileName,
                    expectedSha256 = state.expectedSha256,
                    onProgress = { p, speed ->
                        if (p < 0f) { downloadError = true; isDownloading = false; downloadSpeed = 0L }
                        else { downloadProgress = p; downloadSpeed = speed }
                    },
                    onComplete = { isDownloading = false }
                )
            },
            onDismiss = null
        )

        is UpdateState.Recommended -> BiolumeUpdateDialog(
            title = "${stringResource(R.string.update_recommended_title)} ${state.versionName}",
            body = stringResource(R.string.update_recommended_body),
            changelog = state.changelog,
            dismissible = true,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            downloadSpeed = downloadSpeed,
            downloadError = downloadError,
            onUpdate = {
                isDownloading = true
                downloadError = false
                ApkDownloader.downloadAndInstall(
                    context = context,
                    url = state.url,
                    fileName = currentChannel.fileName,
                    expectedSha256 = state.expectedSha256,
                    onProgress = { p, speed ->
                        if (p < 0f) { downloadError = true; isDownloading = false; downloadSpeed = 0L }
                        else { downloadProgress = p; downloadSpeed = speed }
                    },
                    onComplete = { isDownloading = false }
                )
            },
            onDismiss = { viewModel.dismissRecommendedUpdate() }
        )

        else -> {}
    }
}

// ─── Biolume Dialog ───────────────────────────────────────────────────────────

@Composable
private fun BiolumeUpdateDialog(
    title: String,
    body: String,
    changelog: ChangelogInfo,
    dismissible: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadSpeed: Long,
    downloadError: Boolean,
    onUpdate: () -> Unit,
    onDismiss: (() -> Unit)?
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val animatedProgress by animateFloatAsState(
        targetValue = downloadProgress,
        animationSpec = tween(300),
        label = "dl_progress"
    )
    val showProgress = isDownloading || downloadProgress > 0f
    val hasChangelog = changelog.entries != null || changelog.tooOld

    Dialog(
        onDismissRequest = {
            if (dismissible && !isDownloading) onDismiss?.invoke()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible && !isDownloading,
            dismissOnClickOutside = dismissible && !isDownloading
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .exthruRaisedShadow(isDark)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Заголовок (Кастомный жирный шрифт) ──
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = (-0.5).sp
                    )
                )

                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

                // ── Changelog ──
                if (hasChangelog) {
                    BiolumeChangelogSection(info = changelog, isDark = isDark)
                }

                // ── Прогресс и скорость ──
                AnimatedVisibility(
                    visible = showProgress,
                    enter = fadeIn(tween(200)) + expandVertically(),
                    exit = fadeOut(tween(200)) + shrinkVertically()
                ) {
                    BiolumeDownloadProgress(
                        progress = downloadProgress,
                        animatedProgress = animatedProgress,
                        speedBps = downloadSpeed,
                        downloadError = downloadError,
                        isDark = isDark
                    )
                }

                // ── Кнопки ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onDismiss != null && !isDownloading) {
                        NmButton(
                            text = stringResource(R.string.update_btn_later),
                            isPrimary = false,
                            isDark = isDark,
                            modifier = Modifier.weight(1f),
                            onClick = onDismiss
                        )
                    }
                    NmButton(
                        text = when {
                            isDownloading -> stringResource(R.string.update_btn_downloading)
                            downloadProgress >= 1f -> stringResource(R.string.update_btn_installing)
                            downloadError -> stringResource(R.string.update_btn_retry)
                            else -> stringResource(R.string.update_btn_update)
                        },
                        isPrimary = true,
                        isDark = isDark,
                        enabled = !isDownloading && downloadProgress < 1f,
                        modifier = Modifier.weight(1f),
                        onClick = onUpdate
                    )
                }
            }
        }
    }
}

// ─── Changelog section ────────────────────────────────────────────────────────

@Composable
private fun BiolumeChangelogSection(
    info: ChangelogInfo,
    isDark: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nmInsetShadow(isDark, cornerRadius = 16.dp, darkAlpha = if (isDark) 0.5f else 0.2f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.2f else 0.5f), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.update_whats_new),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (!info.tooOld) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = expanded || info.tooOld,
            enter = expandVertically() + fadeIn(tween(200)),
            exit = shrinkVertically() + fadeOut(tween(150))
        ) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                info.channelTag,
                                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(13.dp))
                        }
                    }
                } else {
                    info.entries?.forEach { entry ->
                        Row(verticalAlignment = Alignment.Top) { // Исправлено на verticalAlignment
                            Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 6.dp))
                            Text(
                                entry.removePrefix("•").removePrefix("-").trim(),
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

// ─── Download progress block ──────────────────────────────────────────────────

@Composable
private fun BiolumeDownloadProgress(
    progress: Float,
    animatedProgress: Float,
    speedBps: Long,
    downloadError: Boolean,
    isDark: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    when {
                        downloadError -> stringResource(R.string.update_dl_error)
                        progress >= 1f -> stringResource(R.string.update_dl_complete)
                        progress > 0f -> stringResource(R.string.update_dl_progress)
                        else -> stringResource(R.string.update_dl_starting)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (downloadError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (progress > 0f && progress < 1f && !downloadError) {
                    Text(
                        formatSpeed(speedBps),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            if (progress > 0f && progress < 1f && !downloadError) {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // ── Неоморфный трек ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .nmInsetShadow(isDark, cornerRadius = 7.dp, darkAlpha = if (isDark) 0.6f else 0.3f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp))
        ) {
            if (!downloadError && progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .exthruSmallRaisedShadow(isDark)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    MaterialTheme.colorScheme.primary
                                )
                            ),
                            RoundedCornerShape(7.dp)
                        )
                )
            }
        }
    }
}

// ─── Неоморфная кнопка ────────────────────────────────────────────────────────

@Composable
private fun NmButton(
    text: String,
    isPrimary: Boolean,
    isDark: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && enabled) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "btn_scale")

    val bgColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

    val shadowMod = if (isPressed && enabled) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp)
    } else if (enabled) {
        Modifier.exthruSmallRaisedShadow(isDark)
    } else Modifier

    Box(
        modifier = modifier
            .scale(scale)
            .then(shadowMod)
            .background(if (enabled) bgColor else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (isPressed || !enabled) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f),
                RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 14.sp),
            color = if (enabled) textColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 KB/s"
    val kb = bytesPerSec / 1024f
    if (kb < 1024f) return String.format(Locale.US, "%.1f KB/s", kb)
    val mb = kb / 1024f
    return String.format(Locale.US, "%.1f MB/s", mb)
}