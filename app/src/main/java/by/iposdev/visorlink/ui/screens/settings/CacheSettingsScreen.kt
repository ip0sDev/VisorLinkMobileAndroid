package by.iposdev.visorlink.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.CacheSizeInfo
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: CacheViewModel = koinViewModel(),
    themeViewModel: ThemeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    val currentTheme by themeViewModel.appTheme.collectAsState()
    val isExthru = currentTheme == AppTheme.EXTHRU
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.1f
    val haptic = rememberHaptic()
    val hapticEnabled by themeViewModel.hapticEnabled.collectAsState()

    // Авто-сброс сообщения об успехе
    LaunchedEffect(state.successMessageRes) {
        if (state.successMessageRes != null) {
            delay(2500)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        containerColor = if (isExthru) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
        topBar = {
            Surface(
                color = if (isExthru) MaterialTheme.colorScheme.surface else Color.Transparent,
                modifier = if (isExthru) Modifier.nmDividerBottom(isDark) else Modifier
            ) {
                TopAppBar(
                    title = {
                        if (isExthru) {
                            Text(
                                text = stringResource(R.string.cache_title),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        } else {
                            Text(stringResource(R.string.cache_title))
                        }
                    },
                    navigationIcon = {
                        val btnModifier = if (isExthru) Modifier
                            .padding(start = 12.dp, end = 4.dp)
                            .size(42.dp)
                            .exthruSmallRaisedShadow(isDark)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                        else Modifier

                        IconButton(
                            onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                onNavigateBack()
                            },
                            modifier = btnModifier
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.action_back),
                                modifier = if (isExthru) Modifier.size(20.dp) else Modifier
                            )
                        }
                    },
                    actions = {
                        val btnModifier = if (isExthru) Modifier
                            .padding(end = 12.dp)
                            .size(42.dp)
                            .exthruSmallRaisedShadow(isDark)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                        else Modifier

                        IconButton(
                            onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                viewModel.refreshSizes()
                            },
                            modifier = btnModifier
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                stringResource(R.string.action_refresh),
                                modifier = if (isExthru) Modifier.size(20.dp) else Modifier
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isExthru) Color.Transparent else MaterialTheme.colorScheme.surface
                    )
                )
            }
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
            SectionHeader(stringResource(R.string.cache_section_usage), isExthru)

            CacheUsageCard(
                sizes     = state.sizes,
                isLoading = state.isLoading,
                isExthru  = isExthru,
                isDark    = isDark
            )

            // ── Очистка ───────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.cache_section_clear), isExthru)

            ClearActionsCard(
                isClearing    = state.isClearing,
                isExthru      = isExthru,
                isDark        = isDark,
                onClearImages = { haptic.perform(HapticType.CLICK, hapticEnabled); viewModel.clearImages() },
                onClearVoice  = { haptic.perform(HapticType.CLICK, hapticEnabled); viewModel.clearVoice() },
                onClearAll    = { haptic.perform(HapticType.CLICK, hapticEnabled); viewModel.clearAll() }
            )

            // ── Лимиты ────────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.cache_section_limits), isExthru)

            LimitsCard(
                imageLimitMb  = state.config.maxImageMb,
                voiceLimitMb  = state.config.maxVoiceMb,
                cacheDays     = state.config.chatCacheDays,
                isExthru      = isExthru,
                isDark        = isDark,
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
private fun CacheUsageCard(sizes: CacheSizeInfo?, isLoading: Boolean, isExthru: Boolean, isDark: Boolean) {
    SettingsCard(isExthru = isExthru, isDark = isDark) {
        if (isLoading || sizes == null) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
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
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isExthru) 0.5f else 0.3f))
            }

            Spacer(Modifier.height(16.dp))

            // Полоска общего прогресса
            val totalFraction = (sizes.totalMb / 500f).coerceIn(0f, 1f)

            val progressModifier = if (isExthru) {
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .nmInsetShadow(isDark, cornerRadius = 5.dp)
                    .clip(RoundedCornerShape(5.dp))
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            }

            LinearProgressIndicator(
                progress = { totalFraction },
                modifier = progressModifier,
                color    = MaterialTheme.colorScheme.primary,
                trackColor = if (isExthru) Color.Transparent else MaterialTheme.colorScheme.primaryContainer
            )

            if (!isExthru) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                Spacer(Modifier.height(8.dp))
            } else {
                Spacer(Modifier.height(24.dp))
            }

            // Детализация по типам
            UsageRow(Icons.Default.Image, stringResource(R.string.cache_row_images), sizes.imagesMb, isExthru, isDark)
            UsageRow(Icons.Default.Mic, stringResource(R.string.cache_row_voice), sizes.voiceMb, isExthru, isDark)
            UsageRow(Icons.Default.GraphicEq, stringResource(R.string.cache_row_waveforms), sizes.waveformMb, isExthru, isDark)
            UsageRow(Icons.Default.ChatBubble, stringResource(R.string.cache_row_chat), sizes.chatMb, isExthru, isDark)
        }
    }
}

@Composable
private fun UsageRow(icon: ImageVector, label: String, mb: Float, isExthru: Boolean, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isExthru) 8.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isExthru) {
            ExthruIconTray(icon = icon, isDark = isDark)
        } else {
            Icon(icon, null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text(label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))

        Text(
            formatMb(mb),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (mb > 50f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Секция: кнопки очистки ───────────────────────────────────────────────────

@Composable
private fun ClearActionsCard(
    isClearing: Boolean,
    isExthru: Boolean,
    isDark: Boolean,
    onClearImages: () -> Unit,
    onClearVoice: () -> Unit,
    onClearAll: () -> Unit
) {
    var showConfirmAll by remember { mutableStateOf(false) }

    SettingsCard(isExthru = isExthru, isDark = isDark, contentPadding = PaddingValues(if (isExthru) 0.dp else 8.dp)) {
        ClearButton(
            icon       = Icons.Default.Image,
            label      = stringResource(R.string.cache_clear_images),
            sublabel   = stringResource(R.string.cache_clear_images_sub),
            isLoading  = isClearing,
            isExthru   = isExthru,
            isDark     = isDark,
            onClick    = onClearImages
        )
        if (!isExthru) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))

        ClearButton(
            icon       = Icons.Default.Mic,
            label      = stringResource(R.string.cache_clear_voice),
            sublabel   = stringResource(R.string.cache_clear_voice_sub),
            isLoading  = isClearing,
            isExthru   = isExthru,
            isDark     = isDark,
            onClick    = onClearVoice
        )
        if (!isExthru) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))

        // Кнопка «Clear all» с подтверждением
        if (showConfirmAll) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isExthru) 20.dp else 12.dp, vertical = if (isExthru) 14.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isExthru) {
                    NmButton(
                        text = stringResource(R.string.action_cancel),
                        isDestructive = false, isDark = isDark,
                        modifier = Modifier.weight(1f)
                    ) { showConfirmAll = false }

                    NmButton(
                        text = stringResource(R.string.cache_clear_all_confirm),
                        isDestructive = true, isDark = isDark,
                        modifier = Modifier.weight(1f)
                    ) { showConfirmAll = false; onClearAll() }
                } else {
                    OutlinedButton(
                        onClick  = { showConfirmAll = false },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_cancel)) }

                    Button(
                        onClick  = { showConfirmAll = false; onClearAll() },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.cache_clear_all_confirm)) }
                }
            }
        } else {
            ClearButton(
                icon       = Icons.Default.DeleteSweep,
                label      = stringResource(R.string.cache_clear_all),
                sublabel   = stringResource(R.string.cache_clear_all_sub),
                isLoading  = isClearing,
                destructive = true,
                isExthru   = isExthru,
                isDark     = isDark,
                onClick    = { showConfirmAll = true }
            )
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
    isExthru: Boolean = false,
    isDark: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isExthru && isPressed -> MaterialTheme.colorScheme.surfaceVariant
            isExthru -> Color.Transparent
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(100), label = "clear_btn_bg"
    )

    Surface(
        onClick   = onClick,
        color     = bgColor,
        shape     = if (isExthru) RoundedCornerShape(0.dp) else MaterialTheme.shapes.medium,
        modifier  = Modifier.fillMaxWidth(),
        enabled   = !isLoading,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isExthru) 20.dp else 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isExthru) {
                ExthruIconTray(icon = icon, isDark = isDark, isError = destructive)
            } else {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }

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
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                    modifier = Modifier.size(22.dp))
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
    isExthru: Boolean,
    isDark: Boolean,
    onImageLimit: (Int) -> Unit,
    onVoiceLimit: (Int) -> Unit,
    onCacheDays:  (Int) -> Unit
) {
    SettingsCard(isExthru = isExthru, isDark = isDark) {
        SliderRow(
            icon     = Icons.Default.Image,
            label    = stringResource(R.string.cache_limit_images),
            value    = imageLimitMb.toFloat(),
            min      = 50f, max = 500f, steps = 8,
            isExthru = isExthru, isDark = isDark,
            format   = { "${it.toInt()} MB" },
            onChange = { onImageLimit(it.toInt()) }
        )

        if (!isExthru) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(16.dp))
        }

        SliderRow(
            icon     = Icons.Default.Mic,
            label    = stringResource(R.string.cache_limit_voice),
            value    = voiceLimitMb.toFloat(),
            min      = 50f, max = 500f, steps = 8,
            isExthru = isExthru, isDark = isDark,
            format   = { "${it.toInt()} MB" },
            onChange = { onVoiceLimit(it.toInt()) }
        )

        if (!isExthru) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(16.dp))
        }

        SliderRow(
            icon     = Icons.Default.History,
            label    = stringResource(R.string.cache_limit_history),
            value    = cacheDays.toFloat(),
            min      = 1f, max = 30f, steps = 28,
            isExthru = isExthru, isDark = isDark,
            format   = { "${it.toInt()} days" },
            onChange = { onCacheDays(it.toInt()) }
        )
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
    isExthru: Boolean,
    isDark: Boolean,
    format: (Float) -> String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isExthru) {
                ExthruIconTray(icon = icon, isDark = isDark)
            } else {
                Icon(icon, null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }

            Text(label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isExthru) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f))

            Text(
                format(value),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value         = value,
            onValueChange = onChange,
            valueRange    = min..max,
            steps         = steps,
            modifier      = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor            = MaterialTheme.colorScheme.primary,
                activeTrackColor      = MaterialTheme.colorScheme.primary,
                inactiveTrackColor    = if (isExthru) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

// ─── Helpers & Primitives ─────────────────────────────────────────────────────

@Composable
private fun SettingsCard(
    isExthru: Boolean,
    isDark: Boolean,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    if (isExthru) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .exthruRaisedShadow(isDark)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .padding(contentPadding),
            content = content
        )
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape  = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
private fun SectionHeader(title: String, isExthru: Boolean) {
    if (isExthru) {
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
private fun ExthruIconTray(icon: ImageVector, isDark: Boolean, isError: Boolean = false) {
    val bgColor = MaterialTheme.colorScheme.surface
    val iconColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(38.dp)
            .exthruSmallRaisedShadow(isDark)
            .background(bgColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NmButton(text: String, isDestructive: Boolean, isDark: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "btn_scale")

    val bgColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface
    val textColor = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .scale(scale)
            .exthruSmallRaisedShadow(isDark)
            .background(bgColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.Bold, color = textColor)
    }
}

private fun formatMb(mb: Float): String = when {
    mb < 0.1f  -> "< 0.1 MB"
    mb >= 1000 -> "${"%.1f".format(mb / 1024)} GB"
    mb >= 1f   -> "${"%.1f".format(mb)} MB"
    else       -> "${"%.0f".format(mb * 1024)} KB"
}