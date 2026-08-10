package by.iposdev.visorlink.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.ui.components.LocalHazeState
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.VlSurface
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.CdnService
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagerScreen(
    onNavigateBack: () -> Unit,
    onViewMedia: (String, String) -> Unit,
    viewModel: StorageViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTheme by themeViewModel.appTheme.collectAsState()
    val isExthru = currentTheme.isExthruFamily
    val style = rememberExthruStyle(currentTheme)
    val isForge = style.isForge
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileToDelete by remember { mutableStateOf<Map<String, Any>?>(null) }

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(stringResource(R.string.storage_delete_title)) },
            text = { Text(stringResource(R.string.storage_delete_confirm, fileToDelete!!["original_name"] as String)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFile(fileToDelete!!["media_id"] as String)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    val scaffoldBg = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface
    val hazeState = remember { HazeState() }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                if (isExthru) {
                    val topBarBg = if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.55f)
                    val topBarMod = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isForge) Modifier.background(topBarBg)
                            else Modifier.hazeEffect(
                                state = hazeState,
                                style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null)
                            ).background(topBarBg)
                        )

                    TopAppBar(
                        modifier = topBarMod,
                        title = {
                            Text(
                                text = stringResource(R.string.storage_title),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (isForge) FontFamily.Monospace else null
                                )
                            )
                        },
                        navigationIcon = {
                            StorageTopBarButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                isDark = isDark,
                                isForge = isForge,
                                hapticEnabled = hapticEnabled,
                                style = style
                            ) { onNavigateBack() }
                        },
                        actions = {
                            StorageTopBarButton(
                                icon = Icons.Default.Refresh,
                                isDark = isDark,
                                isForge = isForge,
                                hapticEnabled = hapticEnabled,
                                style = style
                            ) { viewModel.refresh() }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.storage_title), fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); onNavigateBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                            }
                        },
                        actions = {
                            IconButton(onClick = { haptic.perform(HapticType.CLICK, hapticEnabled); viewModel.refresh() }) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (isExthru && !isForge) it.hazeSource(state = hazeState) else it }
                    .background(scaffoldBg)
            ) {
                VlAmbientGlow(appTheme = currentTheme)

                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        item { Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp)) }

                        item {
                            StorageStatsCard(
                                stats = uiState.stats,
                                appTheme = currentTheme,
                                isDark = isDark
                            )
                        }

                        item {
                            SectionHeader(stringResource(R.string.storage_section_files), currentTheme)
                        }

                        if (uiState.files.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                        Spacer(Modifier.height(8.dp))
                                        Text(stringResource(R.string.storage_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton(onClick = { viewModel.refresh() }) {
                                            Text(stringResource(R.string.action_refresh))
                                        }
                                    }
                                }
                            }
                        } else {
                            items(uiState.files) { file ->
                                FileItem(
                                    file = file,
                                    appTheme = currentTheme,
                                    isDark = isDark,
                                    onView = {
                                        scope.launch {
                                            val url = CdnService.getFileUrl(file["media_id"] as String)
                                            onViewMedia(url, file["mime_type"] as String)
                                        }
                                    },
                                    onDownload = {
                                        scope.launch {
                                            val url = CdnService.getFileUrl(file["media_id"] as String)
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        }
                                    },
                                    onDelete = { fileToDelete = file }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageTopBarButton(
    icon: ImageVector,
    isDark: Boolean,
    isForge: Boolean,
    hapticEnabled: Boolean,
    style: ExthruStyle,
    onClick: () -> Unit
) {
    val haptic = rememberHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "btn_scale")

    val shape = if (isForge) RectangleShape else CircleShape
    val shadowMod = if (isForge) {
        Modifier.forgeNeuBrutalism(isPressed, isDark, 3.dp)
    } else if (isPressed) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 21.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
    } else {
        Modifier.exthruSmallRaisedShadow(isDark)
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(42.dp)
            .scale(if (isForge) 1f else scale)
            .then(shadowMod)
            .background(if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), shape)
            .then(if (isForge) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SectionHeader(title: String, appTheme: AppTheme) {
    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge
    val isExthru = appTheme.isExthruFamily

    if (isForge) {
        Text(
            text = "> ${title.uppercase()}_",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = style.accent,
            modifier = Modifier.padding(start = 28.dp, top = 26.dp, bottom = 8.dp)
        )
    } else if (isExthru) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, top = 26.dp, bottom = 8.dp)
        )
    } else {
        Text(
            text     = title.uppercase(),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 4.dp)
        )
    }
}

@Composable
fun StorageStatsCard(stats: Map<String, Any>, appTheme: AppTheme, isDark: Boolean) {
    val used = stats["used_bytes"] as? Long ?: 0L
    val quota = stats["quota_bytes"] as? Long ?: 2147483648L
    val filesCount = stats["files_count"] as? Int ?: 0
    val fraction = (used.toFloat() / quota.toFloat()).coerceIn(0f, 1f)
    val isExthru = appTheme.isExthruFamily
    val isForge = appTheme.name == "FORGE"
    val style = rememberExthruStyle(appTheme)

    VlSurface(
        appTheme = appTheme,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.storage_usage_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = if (isForge) FontFamily.Monospace else null
                    )
                    Text(
                        stringResource(R.string.storage_usage_of, formatBytes(used), formatBytes(quota)),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = if (isForge) FontFamily.Monospace else null
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            val progressModifier = if (isForge) {
                Modifier.fillMaxWidth().height(14.dp).forgeNeuBrutalism(false, isDark, 2.dp).background(style.inputBg)
            } else if (isExthru) {
                Modifier.fillMaxWidth().height(10.dp).nmInsetShadow(isDark, cornerRadius = 5.dp).clip(RoundedCornerShape(5.dp))
            } else {
                Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
            }

            LinearProgressIndicator(
                progress = { fraction },
                modifier = progressModifier,
                color = if (fraction > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = if (isExthru) Color.Transparent else MaterialTheme.colorScheme.primaryContainer
            )

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.storage_usage_files_count, filesCount),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = if (isForge) FontFamily.Monospace else null
            )
        }
    }
}

@Composable
fun FileItem(
    file: Map<String, Any>,
    appTheme: AppTheme,
    isDark: Boolean,
    onView: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val name = file["original_name"] as String
    val size = file["size"] as Long
    val mime = file["mime_type"] as String
    val isImage = mime.startsWith("image/")
    val id = file["media_id"] as String
    val zone = file["zone"] as? String ?: "public"
    val isForge = appTheme.name == "FORGE"

    var thumbUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        thumbUrl = CdnService.getFileUrl(id)
    }

    VlSurface(
        appTheme = appTheme,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        onClick = { if (isImage) onView() else onDownload() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(if (isForge) RectangleShape else RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (isImage && thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (isImage) Icons.Default.Image else Icons.AutoMirrored.Filled.InsertDriveFile,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = if (isForge) FontFamily.Monospace else null
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatBytes(size),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = if (isForge) FontFamily.Monospace else null
                    )
                    Text(" • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (zone == "vault") stringResource(R.string.storage_zone_vault) else stringResource(R.string.storage_zone_public),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = if (isForge) FontFamily.Monospace else null
                    )
                }
            }

            Row {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return "%.1f %sB".format(bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
