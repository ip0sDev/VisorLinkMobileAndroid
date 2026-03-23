package by.iposdev.visorlink.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import by.iposdev.visorlink.R
import by.iposdev.visorlink.utils.CacheSizeInfo
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: CacheViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Авто-сброс сообщения об успехе
    LaunchedEffect(state.successMessageRes) {
        if (state.successMessageRes != null) {
            delay(2500)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cache_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshSizes() }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh))
                    }
                }
            )
        },
        snackbarHost = {
            // Success toast через Snackbar
            AnimatedVisibility(
                visible = state.successMessageRes != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.size(18.dp))
                            Text(state.successMessageRes?.let { stringResource(it) } ?: "",
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Использование кэша ────────────────────────────────────────────
            SectionHeader(stringResource(R.string.cache_section_usage))

            CacheUsageCard(
                sizes     = state.sizes,
                isLoading = state.isLoading
            )

            // ── Очистка ───────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.cache_section_clear))

            ClearActionsCard(
                isClearing = state.isClearing,
                onClearImages = { viewModel.clearImages() },
                onClearVoice  = { viewModel.clearVoice() },
                onClearAll    = { viewModel.clearAll() }
            )

            // ── Лимиты ────────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.cache_section_limits))

            LimitsCard(
                imageLimitMb  = state.config.maxImageMb,
                voiceLimitMb  = state.config.maxVoiceMb,
                cacheDays     = state.config.chatCacheDays,
                onImageLimit  = { viewModel.setMaxImageMb(it) },
                onVoiceLimit  = { viewModel.setMaxVoiceMb(it) },
                onCacheDays   = { viewModel.setChatCacheDays(it) }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Секция: текущий размер ───────────────────────────────────────────────────

@Composable
private fun CacheUsageCard(sizes: CacheSizeInfo?, isLoading: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape  = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isLoading || sizes == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier   = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            } else {
                // Суммарный размер — крупно
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.cache_total_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatMb(sizes.totalMb),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(Icons.Default.Storage, null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                }

                Spacer(Modifier.height(16.dp))

                // Полоска общего прогресса (до 500 МБ — условный максимум)
                val totalFraction = (sizes.totalMb / 500f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { totalFraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color    = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                Spacer(Modifier.height(8.dp))

                // Детализация по типам
                UsageRow(Icons.Default.Image,       stringResource(R.string.cache_row_images), sizes.imagesMb)
                UsageRow(Icons.Default.Mic,          stringResource(R.string.cache_row_voice), sizes.voiceMb)
                UsageRow(Icons.Default.GraphicEq,    stringResource(R.string.cache_row_waveforms), sizes.waveformMb)
                UsageRow(Icons.Default.ChatBubble,   stringResource(R.string.cache_row_chat), sizes.chatMb)
            }
        }
    }
}

@Composable
private fun UsageRow(icon: ImageVector, label: String, mb: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(
            formatMb(mb),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (mb > 50f) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Секция: кнопки очистки ───────────────────────────────────────────────────

@Composable
private fun ClearActionsCard(
    isClearing: Boolean,
    onClearImages: () -> Unit,
    onClearVoice: () -> Unit,
    onClearAll: () -> Unit
) {
    var showConfirmAll by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape  = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            ClearButton(
                icon       = Icons.Default.Image,
                label      = stringResource(R.string.cache_clear_images),
                sublabel   = stringResource(R.string.cache_clear_images_sub),
                isLoading  = isClearing,
                onClick    = onClearImages
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(0.15f))
            ClearButton(
                icon       = Icons.Default.Mic,
                label      = stringResource(R.string.cache_clear_voice),
                sublabel   = stringResource(R.string.cache_clear_voice_sub),
                isLoading  = isClearing,
                onClick    = onClearVoice
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(0.15f))

            // Кнопка «Clear all» с подтверждением
            if (showConfirmAll) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = { showConfirmAll = false },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick  = { showConfirmAll = false; onClearAll() },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(stringResource(R.string.cache_clear_all_confirm)) }
                }
            } else {
                ClearButton(
                    icon       = Icons.Default.DeleteSweep,
                    label      = stringResource(R.string.cache_clear_all),
                    sublabel   = stringResource(R.string.cache_clear_all_sub),
                    isLoading  = isClearing,
                    destructive = true,
                    onClick    = { showConfirmAll = true }
                )
            }
        }
    }
}

@Composable
private fun ClearButton(
    icon: ImageVector,
    label: String,
    sublabel: String,
    isLoading: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick   = onClick,
        color     = MaterialTheme.colorScheme.surfaceVariant,
        shape     = MaterialTheme.shapes.medium,
        modifier  = Modifier.fillMaxWidth(),
        enabled   = !isLoading
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color, fontWeight = FontWeight.Medium)
                Text(sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier   = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else {
                Icon(Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ─── Секция: лимиты хранения ──────────────────────────────────────────────────

@Composable
private fun LimitsCard(
    imageLimitMb: Int,
    voiceLimitMb: Int,
    cacheDays: Int,
    onImageLimit: (Int) -> Unit,
    onVoiceLimit: (Int) -> Unit,
    onCacheDays:  (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape  = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SliderRow(
                icon     = Icons.Default.Image,
                label    = stringResource(R.string.cache_limit_images),
                value    = imageLimitMb.toFloat(),
                min      = 50f,
                max      = 500f,
                steps    = 8,
                format   = { "${it.toInt()} MB" },
                onChange = { onImageLimit(it.toInt()) }
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
            Spacer(Modifier.height(8.dp))
            SliderRow(
                icon     = Icons.Default.Mic,
                label    = stringResource(R.string.cache_limit_voice),
                value    = voiceLimitMb.toFloat(),
                min      = 50f,
                max      = 500f,
                steps    = 8,
                format   = { "${it.toInt()} MB" },
                onChange = { onVoiceLimit(it.toInt()) }
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
            Spacer(Modifier.height(8.dp))
            SliderRow(
                icon     = Icons.Default.History,
                label    = stringResource(R.string.cache_limit_history),
                value    = cacheDays.toFloat(),
                min      = 1f,
                max      = 30f,
                steps    = 28,
                format   = { "${it.toInt()} days" },
                onChange = { onCacheDays(it.toInt()) }
            )
        }
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    label: String,
    value: Float,
    min: Float,
    max: Float,
    steps: Int,
    format: (Float) -> String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text(label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            Text(
                format(value),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
        }
        Slider(
            value         = value,
            onValueChange = onChange,
            valueRange    = min..max,
            steps         = steps,
            modifier      = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor            = MaterialTheme.colorScheme.primary,
                activeTrackColor      = MaterialTheme.colorScheme.primary,
                inactiveTrackColor    = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 4.dp)
    )
}

private fun formatMb(mb: Float): String = when {
    mb < 0.1f  -> "< 0.1 MB"
    mb >= 1000 -> "${"%.1f".format(mb / 1024)} GB"
    mb >= 1f   -> "${"%.1f".format(mb)} MB"
    else       -> "${"%.0f".format(mb * 1024)} KB"
}