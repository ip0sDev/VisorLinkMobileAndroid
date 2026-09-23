package org.visorlink.app.ui.components.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.R
import org.visorlink.app.data.model.SelectedMediaItem
import org.visorlink.app.data.model.StickerItem
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.mediapicker.VlMediaPickerViewContent
import org.visorlink.app.ui.components.rememberLiquidJellyState
import org.visorlink.app.ui.screens.chat.ChatUiState
import org.visorlink.app.ui.screens.stickers.StickerPackViewModel
import org.visorlink.app.ui.screens.stickers.StickerPickerContent
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic

/**
 * Режим жидкостной нижней панели в чате:
 * - INPUT: компактная строка ввода текста, аудио и вложений
 * - STICKERS: органично выросшая панель выбора стикеров
 * - MEDIA: выросшая медиа-галерея для прикрепления фото и видео
 */
enum class LiquidBottomBarMode {
    INPUT,
    STICKERS,
    MEDIA
}

/**
 * Неоморфная нижняя панель чата с жидкостным морфингом (Liquid Morphing) в стиле Biolume.
 * Превращает компактную строку ввода в полноценное поле стикеров или медиа-галерею
 * с эффектом желейного вытягивания и возврата.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LiquidMorphingChatBottomBar(
    uiState: ChatUiState,
    inputText: String,
    canSendMessage: Boolean,
    canSendMedia: Boolean,
    hapticEnabled: Boolean,
    showStickerSheet: Boolean,
    showMediaPicker: Boolean,
    audioPermission: PermissionState,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onStickerClick: () -> Unit,
    onCloseStickers: () -> Unit,
    onCloseMediaPicker: () -> Unit,
    onSend: () -> Unit,
    onStartRecord: () -> Unit,
    onRequestAudioPerm: () -> Unit,
    onCancelRecord: () -> Unit,
    onSendRecord: () -> Unit,
    onClearReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onJoinChannel: () -> Unit,
    onStickerSelected: (packId: String, sticker: StickerItem) -> Unit,
    onOpenAudioPicker: () -> Unit,
    onOpenEditor: (Uri) -> Unit,
    onMediaSelected: (List<SelectedMediaItem>) -> Unit,
    onPhotoTaken: (Uri) -> Unit,
    onVideoRecorded: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val haptic = rememberHaptic()

    val currentMode = when {
        showStickerSheet -> LiquidBottomBarMode.STICKERS
        showMediaPicker -> LiquidBottomBarMode.MEDIA
        else -> LiquidBottomBarMode.INPUT
    }
    val isExpanded = currentMode != LiquidBottomBarMode.INPUT

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val mediaTargetHeight = remember(screenHeight) { minOf(480.dp, screenHeight * 0.65f) }

    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 28.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquid_panel_corners"
    )

    val panelJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.70f, stiffness = 300f)

    LaunchedEffect(currentMode) {
        if (currentMode != LiquidBottomBarMode.INPUT) {
            panelJelly.pulse(0.10f)
        } else {
            panelJelly.pulse(-0.06f)
        }
    }

    // Перехват системной кнопки "Назад" для плавного схлопывания панели
    BackHandler(enabled = isExpanded) {
        if (showStickerSheet) onCloseStickers()
        if (showMediaPicker) onCloseMediaPicker()
    }

    val panelShape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .liquidJelly(panelJelly, enabled = true)
            .then(
                if (isExpanded) {
                    Modifier
                        .clip(panelShape)
                        .background(cs.surfaceContainerLow)
                        .then(
                            if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, panelShape)
                            else Modifier
                        )
                        .then(
                            if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.5f), panelShape)
                            else Modifier
                        )
                } else Modifier
            )
    ) {
        AnimatedContent(
            targetState = currentMode,
            modifier = Modifier.graphicsLayer { clip = false },
            transitionSpec = {
                val springSpec = spring<IntSize>(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow
                )
                if (targetState != LiquidBottomBarMode.INPUT) {
                    // Плавное вырастание панели вверх с органичным появлением
                    (slideInVertically(
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 300f),
                        initialOffsetY = { it / 4 }
                    ) + fadeIn(tween(160, delayMillis = 40)))
                        .togetherWith(fadeOut(tween(80)))
                        .using(SizeTransform(clip = false) { _, _ -> springSpec })
                } else {
                    // Плавное схлопывание обратно в строку ввода
                    (fadeIn(tween(140, delayMillis = 40)))
                        .togetherWith(slideOutVertically(
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
                            targetOffsetY = { it / 4 }
                        ) + fadeOut(tween(90)))
                        .using(SizeTransform(clip = false) { _, _ -> springSpec })
                }
            },
            label = "liquid_bottom_bar_morph"
        ) { mode ->
            when (mode) {
                LiquidBottomBarMode.INPUT -> {
                    ChatBottomBar(
                        uiState = uiState,
                        inputText = inputText,
                        canSendMessage = canSendMessage,
                        canSendMedia = canSendMedia,
                        hapticEnabled = hapticEnabled,
                        showStickerSheet = false,
                        audioPermission = audioPermission,
                        focusRequester = focusRequester,
                        onInputChange = onInputChange,
                        onAttach = onAttach,
                        onStickerClick = onStickerClick,
                        onSend = onSend,
                        onStartRecord = onStartRecord,
                        onRequestAudioPerm = onRequestAudioPerm,
                        onCancelRecord = onCancelRecord,
                        onSendRecord = onSendRecord,
                        onClearReply = onClearReply,
                        onCancelEdit = onCancelEdit,
                        onJoinChannel = onJoinChannel
                    )
                }

                LiquidBottomBarMode.STICKERS -> {
                    val stickerVm: StickerPackViewModel = koinViewModel()
                    val stickerUiState by stickerVm.uiState.collectAsState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .navigationBarsPadding()
                    ) {
                        // Верхняя панель управления панели стикеров
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.stickers_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            IconButton(
                                onClick = {
                                    haptic.perform(HapticType.CLICK, hapticEnabled)
                                    onCloseStickers()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = cs.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            StickerPickerContent(
                                userPacks = stickerUiState.userPacks,
                                storePacks = stickerUiState.storePacks,
                                isLoading = stickerUiState.isLoading,
                                currentUid = stickerVm.currentUid,
                                onStickerSelected = { packId, sticker ->
                                    stickerVm.recordPackUsage(packId)
                                    onStickerSelected(packId, sticker)
                                    onCloseStickers()
                                },
                                onDeletePack = { packId, isOwner ->
                                    stickerVm.deletePack(packId, isOwner)
                                },
                                onInstallPack = { packId -> stickerVm.addForeignPack(packId) {} }
                            )
                        }
                    }
                }

                LiquidBottomBarMode.MEDIA -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(mediaTargetHeight)
                            .navigationBarsPadding()
                    ) {
                        VlMediaPickerViewContent(
                            maxSelection = 10,
                            onClose = onCloseMediaPicker,
                            onOpenAudioPicker = onOpenAudioPicker,
                            onOpenEditor = onOpenEditor,
                            onMediaSelected = { items ->
                                onMediaSelected(items)
                                onCloseMediaPicker()
                            },
                            onPhotoTaken = { uri ->
                                onPhotoTaken(uri)
                                onCloseMediaPicker()
                            },
                            onVideoRecorded = { uri ->
                                onVideoRecorded(uri)
                                onCloseMediaPicker()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
