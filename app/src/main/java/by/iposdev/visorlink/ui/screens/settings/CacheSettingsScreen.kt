package by.iposdev.visorlink.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.data.model.isExthruFamily
import by.iposdev.visorlink.ui.components.ExthruIconTray
import by.iposdev.visorlink.ui.components.LocalHazeState
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.theme.*
import by.iposdev.visorlink.utils.CacheSizeInfo
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
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
    val isExthru = currentTheme.isExthruFamily
    val style = rememberExthruStyle(currentTheme)
    val isForge = style.isForge

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
                            else Modifier.hazeChild(
                                state = hazeState,
                                style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = null)
                            ).background(topBarBg)
                        )

                    TopAppBar(
                        modifier = topBarMod,
                        title = {
                            Text(
                                text = stringResource(R.string.cache_title),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (isForge) FontFamily.Monospace else null
                                )
                            )
                        },
                        navigationIcon = {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "back_btn_scale")

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
                                    .padding(start = 12.dp, end = 4.dp)
                                    .size(42.dp)
                                    .scale(if (isForge) 1f else scale)
                                    .then(shadowMod)
                                    .background(if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), shape)
                                    .then(if (isForge) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                                    .clip(shape)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = {
                                            haptic.perform(HapticType.CLICK, hapticEnabled)
                                            onNavigateBack()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        actions = {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "refresh_btn_scale")

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
                                    .padding(end = 12.dp)
                                    .size(42.dp)
                                    .scale(if (isForge) 1f else scale)
                                    .then(shadowMod)
                                    .background(if (isForge) style.cardBg else MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), shape)
                                    .then(if (isForge) Modifier else Modifier.border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), shape))
                                    .clip(shape)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = {
                                            haptic.perform(HapticType.CLICK, hapticEnabled)
                                            viewModel.refreshSizes()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Refresh, "Refresh", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.cache_title)) },
                        navigationIcon = {
                            IconButton(onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                onNavigateBack()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                viewModel.refreshSizes()
                            }) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.action_refresh))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            },
            snackbarHost = {
                AnimatedVisibility(
                    visible = state.successMessageRes != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.inverseSurface, tonalElevation = 4.dp) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
                                Text(state.successMessageRes?.let { stringResource(it) } ?: "", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            // Обертка-источник для размытия
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (isExthru && !isForge) it.haze(state = hazeState) else it }
                    .background(scaffoldBg)
            ) {
                VlAmbientGlow(appTheme = currentTheme)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(padding.calculateTopPadding() + 8.dp))

                    // ── Использование кэша ────────────────────────────────────────────
                    SectionHeader(stringResource(R.string.cache_section_usage), currentTheme)

                    CacheUsageCard(
                        sizes     = state.sizes,
                        isLoading = state.isLoading,
                        appTheme  = currentTheme,
                        isDark    = isDark
                    )

                    // ── Очистка ───────────────────────────────────────────────────────
                    SectionHeader(stringResource(R.string.cache_section_clear), currentTheme)

                    ClearActionsCard(
                        isClearing    = state.isClearing,
                        appTheme      = currentTheme,
                        isDark        = isDark,
                        onClearImages = { haptic.perform(HapticType.CLICK, hapticEnabled); viewModel.clearImages() },
                        onClearVoice  = { haptic.perform(HapticType.CLICK, hapticEnabled); viewModel.clearVoice() },
                        onClearAll    = { haptic.perform(HapticType.CLICK, hapticEnabled); viewModel.clearAll() }
                    )

                    // ── Лимиты ────────────────────────────────────────────────────────
                    SectionHeader(stringResource(R.string.cache_section_limits), currentTheme)

                    LimitsCard(
                        imageLimitMb  = state.config.maxImageMb,
                        voiceLimitMb  = state.config.maxVoiceMb,
                        cacheDays     = state.config.chatCacheDays,
                        appTheme      = currentTheme,
                        isDark        = isDark,
                        onImageLimit  = { viewModel.setMaxImageMb(it) },
                        onVoiceLimit  = { viewModel.setMaxVoiceMb(it) },
                        onCacheDays   = { viewModel.setChatCacheDays(it) }
                    )

                    Spacer(modifier = Modifier.height(padding.calculateBottomPadding() + 32.dp))
                }
            }
        }
    }
}

// ─── Секция: текущий размер ───────────────────────────────────────────────────

@Composable
private fun CacheUsageCard(sizes: CacheSizeInfo?, isLoading: Boolean, appTheme: AppTheme, isDark: Boolean) {
    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge
    val isExthru = appTheme.isExthruFamily

    SettingsCard(appTheme = appTheme, isDark = isDark) {
        if (isLoading || sizes == null) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier   = Modifier.size(32.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.cache_total_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = if (isForge) FontFamily.Monospace else null)
                    Text(
                        formatMb(sizes.totalMb),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = if (isForge) FontFamily.Monospace else null
                    )
                }
                Icon(Icons.Default.Storage, null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isExthru) 0.5f else 0.3f))
            }

            Spacer(Modifier.height(16.dp))

            val totalFraction = (sizes.totalMb / 500f).coerceIn(0f, 1f)

            val progressModifier = if (isForge) {
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .padding(horizontal = 8.dp)
                    .forgeNeuBrutalism(false, isDark, 2.dp)
                    .background(style.inputBg)
            } else if (isExthru) {
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .padding(horizontal = 8.dp)
                    .nmInsetShadow(isDark, cornerRadius = 5.dp, darkAlpha = if(isDark) 0.6f else 0.35f)
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

            UsageRow(Icons.Default.Image, stringResource(R.string.cache_row_images), sizes.imagesMb, appTheme, isDark)
            UsageRow(Icons.Default.Mic, stringResource(R.string.cache_row_voice), sizes.voiceMb, appTheme, isDark)
            UsageRow(Icons.Default.GraphicEq, stringResource(R.string.cache_row_waveforms), sizes.waveformMb, appTheme, isDark)
            UsageRow(Icons.Default.ChatBubble, stringResource(R.string.cache_row_chat), sizes.chatMb, appTheme, isDark)

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun UsageRow(icon: ImageVector, label: String, mb: Float, appTheme: AppTheme, isDark: Boolean) {
    val isExthru = appTheme.isExthruFamily
    val isForge = appTheme == AppTheme.FORGE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if(isExthru) 4.dp else 0.dp, vertical = if (isExthru) 10.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isExthru) {
            ExthruIconTray(appTheme = appTheme, icon = icon)
        } else {
            Icon(icon, null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text(label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface, // Исправлено: белый/черный цвет текста
            fontFamily = if (isForge) FontFamily.Monospace else null,
            modifier = Modifier.weight(1f))

        Text(
            formatMb(mb),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isForge) FontFamily.Monospace else null,
            color = if (mb > 50f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Секция: кнопки очистки ───────────────────────────────────────────────────

@Composable
private fun ClearActionsCard(
    isClearing: Boolean,
    appTheme: AppTheme,
    isDark: Boolean,
    onClearImages: () -> Unit,
    onClearVoice: () -> Unit,
    onClearAll: () -> Unit
) {
    var showConfirmAll by remember { mutableStateOf(false) }
    val isExthru = appTheme.isExthruFamily

    SettingsCard(appTheme = appTheme, isDark = isDark, contentPadding = PaddingValues(if (isExthru) 0.dp else 8.dp)) {
        if (isExthru) Spacer(Modifier.height(8.dp))

        ClearButton(
            icon       = Icons.Default.Image,
            label      = stringResource(R.string.cache_clear_images),
            sublabel   = stringResource(R.string.cache_clear_images_sub),
            isLoading  = isClearing,
            appTheme   = appTheme,
            isDark     = isDark,
            onClick    = onClearImages
        )
        if (!isExthru) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))

        ClearButton(
            icon       = Icons.Default.Mic,
            label      = stringResource(R.string.cache_clear_voice),
            sublabel   = stringResource(R.string.cache_clear_voice_sub),
            isLoading  = isClearing,
            appTheme   = appTheme,
            isDark     = isDark,
            onClick    = onClearVoice
        )
        if (!isExthru) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outline.copy(0.15f))

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
                        isPrimary = false, isDark = isDark, isForge = appTheme == AppTheme.FORGE,
                        modifier = Modifier.weight(1f)
                    ) { showConfirmAll = false }

                    NmButton(
                        text = stringResource(R.string.cache_clear_all_confirm),
                        isPrimary = true, isDark = isDark, isForge = appTheme == AppTheme.FORGE,
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
                appTheme   = appTheme,
                isDark     = isDark,
                onClick    = { showConfirmAll = true }
            )
        }

        if (isExthru) Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ClearButton(
    icon: ImageVector,
    label: String,
    sublabel: String,
    isLoading: Boolean,
    destructive: Boolean = false,
    appTheme: AppTheme,
    isDark: Boolean = false,
    onClick: () -> Unit
) {
    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge
    val isExthru = appTheme.isExthruFamily
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isLoading) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "clear_btn_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isExthru && isPressed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            isExthru -> Color.Transparent
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(100), label = "clear_btn_bg"
    )

    val shape = if (isForge) RectangleShape else if (isExthru) RoundedCornerShape(0.dp) else MaterialTheme.shapes.medium

    Surface(
        onClick   = onClick,
        color     = bgColor,
        shape     = shape,
        modifier  = Modifier.fillMaxWidth().scale(if (isForge) 1f else scale),
        enabled   = !isLoading,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isExthru) 20.dp else 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isExthru) {
                ExthruIconTray(appTheme = appTheme, icon = icon, isError = destructive)
            } else {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (isForge) FontFamily.Monospace else null,
                    color = color, fontWeight = FontWeight.Medium) // Цвет уже был передан верно
                Text(sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (isForge) FontFamily.Monospace else null,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier   = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
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
    appTheme: AppTheme,
    isDark: Boolean,
    onImageLimit: (Int) -> Unit,
    onVoiceLimit: (Int) -> Unit,
    onCacheDays:  (Int) -> Unit
) {
    val isExthru = appTheme.isExthruFamily
    SettingsCard(appTheme = appTheme, isDark = isDark) {
        SliderRow(
            icon     = Icons.Default.Image,
            label    = stringResource(R.string.cache_limit_images),
            value    = imageLimitMb.toFloat(),
            min      = 50f, max = 500f, steps = 8,
            appTheme = appTheme, isDark = isDark,
            format   = { "${it.toInt()} MB" },
            onChange = { onImageLimit(it.toInt()) }
        )

        if (!isExthru) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(24.dp))
        }

        SliderRow(
            icon     = Icons.Default.Mic,
            label    = stringResource(R.string.cache_limit_voice),
            value    = voiceLimitMb.toFloat(),
            min      = 50f, max = 500f, steps = 8,
            appTheme = appTheme, isDark = isDark,
            format   = { "${it.toInt()} MB" },
            onChange = { onVoiceLimit(it.toInt()) }
        )

        if (!isExthru) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(24.dp))
        }

        SliderRow(
            icon     = Icons.Default.History,
            label    = stringResource(R.string.cache_limit_history),
            value    = cacheDays.toFloat(),
            min      = 1f, max = 30f, steps = 28,
            appTheme = appTheme, isDark = isDark,
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
    appTheme: AppTheme,
    isDark: Boolean,
    format: (Float) -> String,
    onChange: (Float) -> Unit
) {
    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge
    val isExthru = appTheme.isExthruFamily

    Column(modifier = if (isExthru) Modifier.padding(horizontal = 4.dp, vertical = 4.dp) else Modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isExthru) {
                ExthruIconTray(appTheme = appTheme, icon = icon)
            } else {
                Icon(icon, null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }

            Text(label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface, // Исправлено: белый текст в темной теме
                fontFamily = if (isForge) FontFamily.Monospace else null,
                fontWeight = if (isExthru) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f))

            Text(
                format(value),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = if (isForge) FontFamily.Monospace else null,
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
    appTheme: AppTheme,
    isDark: Boolean,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val style = rememberExthruStyle(appTheme)
    val isForge = style.isForge
    val isExthru = appTheme.isExthruFamily

    if (isForge) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .forgeNeuBrutalism(isPressed = false, isDark = isDark, offsetDp = 4.dp)
                .background(style.cardBg)
                .padding(contentPadding),
            content = content
        )
    } else if (isExthru) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .exthruRaisedShadow(isDark)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.55f))
                .border(
                    1.5.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.15f else 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (isDark) 0.4f else 0.05f)
                        )
                    ),
                    RoundedCornerShape(20.dp)
                )
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
private fun NmButton(
    text: String,
    isPrimary: Boolean,
    isDark: Boolean,
    isForge: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && enabled) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "btn_scale")

    val bgColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

    val shape = if (isForge) RectangleShape else RoundedCornerShape(16.dp)

    val shadowMod = if (isForge) {
        Modifier.forgeNeuBrutalism(isPressed, isDark, 3.dp)
    } else if (isPressed && enabled) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp)
    } else if (enabled) {
        Modifier.exthruSmallRaisedShadow(isDark)
    } else Modifier

    Box(
        modifier = modifier
            .scale(if (isForge) 1f else scale)
            .then(shadowMod)
            .background(if (enabled) bgColor else MaterialTheme.colorScheme.surfaceVariant, shape)
            .then(if (isForge) Modifier else Modifier.border(
                1.dp,
                if (isPressed || !enabled) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f),
                shape
            ))
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = if (isForge) FontFamily.Monospace else FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 14.sp),
            color = if (enabled) textColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

private fun formatMb(mb: Float): String = when {
    mb < 0.1f  -> "< 0.1 MB"
    mb >= 1000 -> "${"%.1f".format(mb / 1024)} GB"
    mb >= 1f   -> "${"%.1f".format(mb)} MB"
    else       -> "${"%.0f".format(mb * 1024)} KB"
}