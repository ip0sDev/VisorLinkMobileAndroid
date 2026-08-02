package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.components.LocalHazeState
import by.iposdev.visorlink.ui.theme.Biolume
import by.iposdev.visorlink.ui.theme.exthruSmallRaisedShadow
import by.iposdev.visorlink.ui.theme.nmDividerTop
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.ui.theme.rememberExthruStyle
import by.iposdev.visorlink.utils.HapticHelper
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DynamicChatInputBar(
    uiState: ChatUiState,
    inputText: String,
    isDark: Boolean,
    appTheme: AppTheme,
    canSendMessage: Boolean,
    canSendMedia: Boolean,
    hapticEnabled: Boolean,
    showStickerSheet: Boolean,
    audioPermission: PermissionState,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onStickerClick: () -> Unit,
    onSend: () -> Unit,
    onStartRecord: () -> Unit,
    onRequestAudioPerm: () -> Unit,
    onCancelRecord: () -> Unit,
    onSendRecord: () -> Unit,
    onClearReply: () -> Unit,
    haptic: HapticHelper,
) {
    val cs = MaterialTheme.colorScheme
    val isForge = appTheme == AppTheme.FORGE
    val isOneUi = appTheme == AppTheme.ONE_UI
    val isExthru = appTheme.isExthruFamily

    val style = rememberExthruStyle(if(isExthru) appTheme else AppTheme.BIOLUME)

    val outerShape = RoundedCornerShape(if (isForge) 0.dp else 28.dp)
    val innerShape = RoundedCornerShape(if (isForge) 0.dp else 20.dp)

    val outerBg = when {
        isExthru -> if (isForge) style.inputBg else cs.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.5f)
        isOneUi -> if (isDark) OneUiChat.TopBarDark else OneUiChat.TopBar
        else -> cs.surfaceContainerHighest.copy(alpha = 0.85f)
    }

    val innerBg = when {
        isExthru -> if (isForge) cs.surfaceVariant else cs.surface.copy(alpha = if(isDark) 0.35f else 0.6f)
        isOneUi -> if (isDark) OneUiChat.InputBgDark else OneUiChat.InputBg
        else -> cs.surface
    }

    val accentColor = when {
        isExthru -> style.accent
        isOneUi -> if (isDark) OneUiChat.BlueDark else OneUiChat.Blue
        else -> cs.primary
    }

    val iconColor = if (isExthru) cs.onSurfaceVariant else if (isOneUi) (if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary) else cs.onSurfaceVariant

    val hazeState = LocalHazeState.current

    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
    ) {
        val restriction = when {
            uiState.myMember?.banned == true -> stringResource(R.string.you_are_banned_from_this_chat)
            uiState.myMember?.muted == true  -> stringResource(R.string.you_are_muted)
            uiState.chatType == ChatType.CHANNEL && !canSendMessage -> stringResource(R.string.only_admins_can_post_in_channels)
            else -> null
        }
        AnimatedVisibility(visible = restriction != null) {
            Surface(color = cs.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(restriction ?: "", style = MaterialTheme.typography.bodySmall, color = cs.onErrorContainer, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
        ) {
            val bgMod = if (isExthru && !isForge) {
                Modifier.hazeEffect(state = hazeState, style = HazeStyle(blurRadius = 24.dp, tint = HazeTint(outerBg), noiseFactor = 0.03f))
            } else {
                Modifier.background(outerBg)
            }

            Column(
                modifier = Modifier
                    .clip(outerShape)
                    .then(bgMod)
                    .then(if (isExthru && !isForge) Modifier.border(1.dp, Color.White.copy(0.1f), outerShape) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                AnimatedVisibility(visible = uiState.replyingTo != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    uiState.replyingTo?.let { msg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(innerShape)
                                .background(innerBg)
                                .then(if (isExthru && !isForge) Modifier.border(1.dp, cs.outlineVariant.copy(0.2f), innerShape) else Modifier)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(3.dp).height(32.dp).background(accentColor, RoundedCornerShape(2.dp)))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("@${msg.senderUsername}", style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.SemiBold)
                                    Text(msg.text ?: "Медиа", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = onClearReply, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, "Cancel", tint = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.isUploading, enter = expandVertically(), exit = shrinkVertically()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(4.dp)), color = accentColor, trackColor = Color.Transparent)
                }

                if (canSendMessage) {
                    if (uiState.isRecording) {
                        var elapsed by remember { mutableIntStateOf(0) }
                        val dotAlpha by rememberInfiniteTransition(label = "").animateFloat(
                            initialValue = 1f, targetValue = 0.2f,
                            animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = ""
                        )
                        LaunchedEffect(Unit) { while (true) { delay(1000); elapsed++; haptic.perform(HapticType.CLICK, hapticEnabled) } }

                        Row(
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { haptic.perform(HapticType.ERROR, hapticEnabled); onCancelRecord() }) {
                                Icon(Icons.Default.Delete, "Cancel", tint = style.destructive)
                            }
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(style.destructive.copy(alpha = dotAlpha), CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text("${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), fontFamily = if(isForge) FontFamily.Monospace else null)
                            }

                            val sendScale by animateFloatAsState(if (uiState.isCooldown) 0.9f else 1f, label = "")
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .scale(sendScale)
                                    .then(if (isExthru && !isForge) Modifier.exthruSmallRaisedShadow(isDark) else Modifier)
                                    .background(if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE) accentColor else style.cardBg, CircleShape)
                                    .clip(CircleShape)
                                    .clickable(enabled = !uiState.isCooldown) { haptic.perform(HapticType.SUCCESS, hapticEnabled); onSendRecord() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE) Color.White else accentColor, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            AnimatedVisibility(
                                visible = inputText.isBlank(),
                                enter = expandHorizontally(expandFrom = Alignment.End, clip = true) + fadeIn(tween(200)),
                                exit = shrinkHorizontally(shrinkTowards = Alignment.End, clip = true) + fadeOut(tween(200))
                            ) {
                                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)) {
                                    if (canSendMedia) {
                                        Box(modifier = Modifier.size(38.dp, 44.dp).clickable(enabled = !uiState.isCooldown, indication = null, interactionSource = remember { MutableInteractionSource() }) { haptic.perform(HapticType.CLICK, hapticEnabled); onAttach() }, contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.AttachFile, null, tint = iconColor, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    Box(modifier = Modifier.size(38.dp, 44.dp).clickable(enabled = !uiState.isCooldown, indication = null, interactionSource = remember { MutableInteractionSource() }) { haptic.perform(HapticType.CLICK, hapticEnabled); onStickerClick() }, contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.EmojiEmotions, null, tint = if (showStickerSheet) accentColor else iconColor, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(innerShape)
                                    .background(innerBg)
                                    .then(if (isExthru && !isForge) Modifier.nmInsetShadow(isDark, cornerRadius = 20.dp, darkAlpha = if(isDark) 0.6f else 0.35f) else Modifier)
                                    .padding(start = 16.dp, end = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = onInputChange,
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester)
                                            .padding(top = 12.dp, bottom = 12.dp, end = 4.dp),
                                        maxLines = 5,
                                        textStyle = TextStyle(color = if (isDark) Color.White else Color.Black, fontSize = 15.sp, fontFamily = if(isForge) FontFamily.Monospace else null),
                                        decorationBox = { inner ->
                                            if (inputText.isEmpty()) {
                                                Text(stringResource(R.string.chat_input_placeholder), fontSize = 15.sp, color = iconColor, fontFamily = if(isForge) FontFamily.Monospace else null)
                                            }
                                            inner()
                                        }
                                    )

                                    if (inputText.isNotEmpty()) {
                                        Text(
                                            "${inputText.length}/2000", fontSize = 11.sp,
                                            color = if (inputText.length >= 2000) style.destructive else iconColor,
                                            modifier = Modifier.padding(bottom = 14.dp, end = 6.dp),
                                        )
                                    }

                                    Box(modifier = Modifier.padding(bottom = 2.dp)) {
                                        AnimatedContent(
                                            targetState = inputText.isNotBlank(),
                                            transitionSpec = {
                                                scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn() togetherWith scaleOut(spring(stiffness = Spring.StiffnessHigh)) + fadeOut()
                                            },
                                            label = "send_mic",
                                        ) { hasText ->
                                            if (hasText) {
                                                Box(modifier = Modifier.size(40.dp).clickable(enabled = !uiState.isCooldown, indication = null, interactionSource = remember { MutableInteractionSource() }) { haptic.perform(HapticType.CLICK, hapticEnabled); onSend() }, contentAlignment = Alignment.Center) {
                                                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = accentColor, modifier = Modifier.size(24.dp))
                                                }
                                            } else {
                                                if (canSendMedia) {
                                                    Box(modifier = Modifier.size(40.dp).clickable(enabled = !uiState.isCooldown, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                                        if (audioPermission.status.isGranted) { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onStartRecord() }
                                                        else onRequestAudioPerm()
                                                    }, contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.Mic, "Record", tint = iconColor, modifier = Modifier.size(24.dp))
                                                    }
                                                } else {
                                                    Spacer(Modifier.size(40.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    RecordingBar(
                        isExthru      = true,
                        isDark        = isDark,
                        hapticEnabled = hapticEnabled,
                        onCancel      = onCancelRecord,
                        onSend        = onSendRecord,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun ExthruChatBottomBar(
    uiState: ChatUiState,
    inputText: String,
    isDark: Boolean = false,
    canSendMessage: Boolean,
    canSendMedia: Boolean,
    hapticEnabled: Boolean,
    showStickerSheet: Boolean,
    audioPermission: PermissionState,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onStickerClick: () -> Unit,
    onSend: () -> Unit,
    onStartRecord: () -> Unit,
    onRequestAudioPerm: () -> Unit,
    onCancel: () -> Unit,
    onSendRecord: () -> Unit,
    onClearReply: () -> Unit,
    haptic: HapticHelper,
) {
    val inputShape = RoundedCornerShape(24.dp)

    val barBgBase     = ExthruChat.barBg(isDark)
    val barBg         = barBgBase.copy(alpha = if (isDark) 0.4f else 0.55f)
    val inputBg       = ExthruChat.inputBg(isDark).copy(alpha = if (isDark) 0.6f else 0.3f)
    val textPrimary   = ExthruChat.textPrimary(isDark)
    val textHint      = ExthruChat.textHint(isDark)

    val hazeState = LocalHazeState.current

    Column(
        modifier = Modifier
            .nmDividerTop(isDark = isDark)
            .hazeEffect(state = hazeState, style = HazeStyle(blurRadius = 24.dp, noiseFactor = 0.03f, tint = HazeTint(barBg)))
            .navigationBarsPadding()
            .imePadding(),
    ) {
        val restriction = when {
            uiState.myMember?.banned == true -> stringResource(R.string.you_are_banned_from_this_chat)
            uiState.myMember?.muted == true  -> stringResource(R.string.you_are_muted)
            uiState.chatType == ChatType.CHANNEL && !canSendMessage -> stringResource(R.string.only_admins_can_post_in_channels)
            else -> null
        }
        AnimatedVisibility(visible = restriction != null) {
            Surface(
                color = ExthruChat.Destructive.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    restriction ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExthruChat.Destructive,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.replyingTo != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
            exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
        ) {
            uiState.replyingTo?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .nmInsetShadow(isDark, cornerRadius = 16.dp, darkAlpha = if (isDark) 0.6f else 0.2f)
                        .background(ExthruChat.cardBg(isDark).copy(alpha = if (isDark) 0.7f else 0.8f), RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    ReplyBanner(message = msg, onDismiss = onClearReply)
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isUploading,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ExthruChat.Accent, trackColor = Color.Transparent)
        }

        if (canSendMessage) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!uiState.isRecording) {
                    if (canSendMedia) {
                        InteractiveExthruButton(
                            icon = Icons.Default.AttachFile,
                            isDark = isDark,
                            hapticEnabled = hapticEnabled,
                            enabled = !uiState.isCooldown,
                            onClick = onAttach
                        )
                        Spacer(Modifier.width(6.dp))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(inputShape)
                            .background(inputBg, inputShape)
                            .nmInsetShadow(isDark = isDark, cornerRadius = 24.dp, darkAlpha = if(isDark) 0.7f else 0.35f)
                            .padding(end = 4.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value         = inputText,
                                onValueChange = onInputChange,
                                modifier      = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                                maxLines  = 5,
                                textStyle = TextStyle(color = textPrimary, fontSize = 15.sp),
                                decorationBox = { inner ->
                                    if (inputText.isEmpty()) {
                                        Text(stringResource(R.string.chat_input_placeholder), fontSize = 15.sp, color = textHint)
                                    }
                                    inner()
                                },
                            )
                            if (inputText.isNotEmpty()) {
                                Text(
                                    "${inputText.length}/2000", fontSize = 11.sp,
                                    color = if (inputText.length >= 2000) ExthruChat.Destructive else textHint,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }

                            val intEmoji = remember { MutableInteractionSource() }
                            val isEmojiPressed by intEmoji.collectIsPressedAsState()
                            val emojiScale by animateFloatAsState(if (isEmojiPressed) 0.85f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "")
                            val emojiShadow = if (isEmojiPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 18.dp) else Modifier

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .scale(emojiScale)
                                    .then(emojiShadow)
                                    .clip(CircleShape)
                                    .clickable(interactionSource = intEmoji, indication = null, enabled = !uiState.isCooldown) {
                                        haptic.perform(HapticType.CLICK, hapticEnabled)
                                        onStickerClick()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.EmojiEmotions, null,
                                    tint = if (showStickerSheet) ExthruChat.Accent else textHint,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    AnimatedContent(
                        targetState = inputText.isNotBlank(),
                        transitionSpec = {
                            scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(tween(150)) togetherWith
                                    scaleOut(spring(stiffness = Spring.StiffnessHigh)) + fadeOut(tween(80))
                        },
                        label = "exthru_send_mic",
                    ) { hasText ->
                        if (hasText) {
                            InteractiveExthruSendButton(
                                enabled = !uiState.isCooldown,
                                isDark = isDark,
                                hapticEnabled = hapticEnabled,
                                onClick = onSend
                            )
                        } else {
                            if (canSendMedia) {
                                InteractiveExthruMicButton(
                                    isDark = isDark,
                                    enabled = !uiState.isCooldown,
                                    hapticEnabled = hapticEnabled,
                                    onRequestAudioPerm = onRequestAudioPerm,
                                    audioPermission = audioPermission,
                                    onStartRecord = onStartRecord
                                )
                            } else Spacer(Modifier.size(44.dp))
                        }
                    }
                } else {
                    RecordingBar(
                        isExthru      = true,
                        isDark        = isDark,
                        hapticEnabled = hapticEnabled,
                        onCancel      = onCancel,
                        onSend        = onSendRecord,
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveExthruButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    enabled: Boolean,
    hapticEnabled: Boolean,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val haptic = rememberHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "btn_scale"
    )

    val shadowMod = if (isPressed) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 22.dp, darkAlpha = if (isDark) 0.6f else 0.35f)
    } else {
        Modifier.exthruSmallRaisedShadow(isDark)
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .then(shadowMod)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), CircleShape)
            .border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), CircleShape)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
                haptic.perform(HapticType.CLICK, hapticEnabled)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint ?: ExthruChat.textSecondary(isDark), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InteractiveExthruSendButton(
    enabled: Boolean,
    isDark: Boolean,
    hapticEnabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "send_scale"
    )

    val shadowMod = if (isPressed) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 22.dp, darkAlpha = if (isDark) 0.8f else 0.5f)
    } else {
        Modifier.exthruSmallRaisedShadow(isDark)
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .then(shadowMod)
            .background(
                brush = if (enabled) Brush.radialGradient(listOf(Biolume.TealLight, Biolume.TealPulse))
                else Brush.radialGradient(listOf(Biolume.TealLight.copy(0.5f), Biolume.TealPulse.copy(0.5f))),
                shape = CircleShape,
            )
            .border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = 0.2f), CircleShape)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
                haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun InteractiveExthruMicButton(
    isDark: Boolean,
    enabled: Boolean,
    hapticEnabled: Boolean,
    audioPermission: PermissionState,
    onRequestAudioPerm: () -> Unit,
    onStartRecord: () -> Unit
) {
    val haptic = rememberHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "mic_scale"
    )

    val shadowMod = if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 22.dp, darkAlpha = if (isDark) 0.6f else 0.35f) else Modifier.exthruSmallRaisedShadow(isDark)

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .then(shadowMod)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.5f else 0.8f), CircleShape)
            .border(1.dp, if (isPressed) Color.Transparent else Color.White.copy(alpha = if (isDark) 0.05f else 0.3f), CircleShape)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
                if (audioPermission.status.isGranted) {
                    haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                    onStartRecord()
                } else onRequestAudioPerm()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Mic, null, tint = ExthruChat.Accent, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun OneUiChatBottomBar(
    uiState: ChatUiState,
    inputText: String,
    isDark: Boolean,
    canSendMessage: Boolean,
    canSendMedia: Boolean,
    hapticEnabled: Boolean,
    showStickerSheet: Boolean,
    audioPermission: PermissionState,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onStickerClick: () -> Unit,
    onSend: () -> Unit,
    onStartRecord: () -> Unit,
    onRequestAudioPerm: () -> Unit,
    onCancel: () -> Unit,
    onSendRecord: () -> Unit,
    onClearReply: () -> Unit,
    haptic: HapticHelper,
) {
    val barBg       = if (isDark) OneUiChat.TopBarDark else OneUiChat.TopBar
    val accentColor = if (isDark) OneUiChat.BlueDark else OneUiChat.Blue
    val inputBg     = if (isDark) OneUiChat.InputBgDark else OneUiChat.InputBg
    val iconTint    = if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary

    Column(modifier = Modifier.background(barBg).navigationBarsPadding().imePadding()) {
        val restriction = when {
            uiState.myMember?.banned == true -> stringResource(R.string.you_are_banned_from_this_chat)
            uiState.myMember?.muted == true  -> stringResource(R.string.you_are_muted)
            uiState.chatType == ChatType.CHANNEL && !canSendMessage -> stringResource(R.string.only_admins_can_post_in_channels)
            else -> null
        }
        AnimatedVisibility(visible = restriction != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    restriction ?: "", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center,
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.replyingTo != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
            exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
        ) {
            uiState.replyingTo?.let { msg ->
                ReplyBanner(message = msg, onDismiss = onClearReply)
            }
        }

        AnimatedVisibility(
            visible = uiState.isUploading,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            LinearProgressIndicator(
                modifier   = Modifier.fillMaxWidth(),
                color      = accentColor,
                trackColor = if (isDark) Color(0xFF232B3A) else Color(0xFFEBF1FD),
            )
        }

        if (canSendMessage) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!uiState.isRecording) {
                    if (canSendMedia) {
                        IconButton(onClick = onAttach, enabled = !uiState.isCooldown) {
                            Icon(Icons.Default.AttachFile, null, tint = iconTint, modifier = Modifier.size(22.dp))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(inputBg)
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = inputText, onValueChange = onInputChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                maxLines  = 4,
                                textStyle = TextStyle(
                                    color    = if (isDark) OneUiChat.TextPrimaryDark else OneUiChat.TextPrimary,
                                    fontSize = 15.sp,
                                ),
                                decorationBox = { inner ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            stringResource(R.string.chat_input_placeholder), fontSize = 15.sp,
                                            color = if (isDark) OneUiChat.TextSecondaryDark else OneUiChat.TextSecondary,
                                        )
                                    }
                                    inner()
                                },
                            )
                            if (inputText.isNotEmpty()) {
                                Text(
                                    "${inputText.length}/2000", fontSize = 11.sp,
                                    color = if (inputText.length >= 2000) MaterialTheme.colorScheme.error else iconTint,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                            IconButton(
                                onClick  = { haptic.perform(HapticType.CLICK, hapticEnabled); onStickerClick() },
                                modifier = Modifier.size(32.dp),
                                enabled  = !uiState.isCooldown,
                            ) {
                                Icon(
                                    Icons.Default.EmojiEmotions, null,
                                    tint     = if (showStickerSheet) accentColor else iconTint,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    AnimatedContent(
                        targetState = inputText.isNotBlank(),
                        transitionSpec = {
                            scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(tween(150)) togetherWith
                                    scaleOut(spring(stiffness = Spring.StiffnessHigh)) + fadeOut(tween(80))
                        },
                        label = "oui_send_mic",
                    ) { hasText ->
                        if (hasText) {
                            val sendScale = remember { Animatable(1f) }
                            val sendScope = rememberCoroutineScope()
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .scale(sendScale.value)
                                    .background(if (uiState.isCooldown) accentColor.copy(alpha = 0.5f) else accentColor, CircleShape)
                                    .clickable(
                                        enabled           = !uiState.isCooldown,
                                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                                    ) {
                                        sendScope.launch {
                                            sendScale.animateTo(0.80f, spring(stiffness = Spring.StiffnessHigh))
                                            sendScale.animateTo(1.10f, spring(Spring.DampingRatioLowBouncy))
                                            sendScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                                        }
                                        haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled)
                                        onSend()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            if (canSendMedia) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(accentColor.copy(alpha = 0.1f), CircleShape)
                                        .clickable(enabled = !uiState.isCooldown) {
                                            if (audioPermission.status.isGranted) {
                                                haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                                onStartRecord()
                                            } else onRequestAudioPerm()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Mic, null, tint = accentColor, modifier = Modifier.size(22.dp))
                                }
                            } else Spacer(Modifier.size(44.dp))
                        }
                    }
                } else {
                    RecordingBar(hapticEnabled = hapticEnabled, onCancel = onCancel, onSend = onSendRecord)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun DefaultChatBottomBar(
    uiState: ChatUiState,
    inputText: String,
    canSendMessage: Boolean,
    canSendMedia: Boolean,
    hapticEnabled: Boolean,
    showStickerSheet: Boolean,
    audioPermission: PermissionState,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onAttach: () -> Unit,
    onStickerClick: () -> Unit,
    onSend: () -> Unit,
    onStartRecord: () -> Unit,
    onRequestAudioPerm: () -> Unit,
    onCancel: () -> Unit,
    onSendRecord: () -> Unit,
    onClearReply: () -> Unit,
    haptic: HapticHelper,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding().imePadding()) {
        val restriction = when {
            uiState.myMember?.banned == true -> stringResource(R.string.you_are_banned_from_this_chat)
            uiState.myMember?.muted == true  -> stringResource(R.string.you_are_muted)
            uiState.chatType == ChatType.CHANNEL && !canSendMessage -> stringResource(R.string.only_admins_can_post_in_channels)
            else -> null
        }
        AnimatedVisibility(visible = restriction != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(restriction ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
            }
        }

        AnimatedVisibility(
            visible = uiState.replyingTo != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec  = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180, easing = FastOutLinearInEasing)) + fadeOut(tween(150)),
        ) {
            uiState.replyingTo?.let { msg ->
                ReplyBanner(message = msg, onDismiss = onClearReply)
            }
        }

        AnimatedVisibility(
            visible = uiState.isUploading,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
        }

        if (canSendMessage) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!uiState.isRecording) {
                    if (canSendMedia) {
                        val attachScale = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { attachScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
                        IconButton(onClick  = onAttach, modifier = Modifier.scale(attachScale.value), enabled  = !uiState.isCooldown) {
                            Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val emojiScale = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(40)
                        emojiScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
                    }
                    IconButton(
                        onClick  = { haptic.perform(HapticType.CLICK, hapticEnabled); onStickerClick() },
                        modifier = Modifier.scale(emojiScale.value),
                        enabled  = !uiState.isCooldown,
                    ) {
                        Icon(
                            Icons.Default.EmojiEmotions, null,
                            tint = if (showStickerSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value         = inputText,
                        onValueChange = onInputChange,
                        placeholder   = { Text(stringResource(R.string.chat_input_placeholder)) },
                        modifier      = Modifier.weight(1f).focusRequester(focusRequester).animateContentSize(animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)),
                        maxLines      = 4,
                        shape         = MaterialTheme.shapes.extraLarge,
                        supportingText = if (inputText.isNotEmpty()) {
                            {
                                Text(
                                    "${inputText.length}/2000",
                                    color = if (inputText.length >= 2000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                                )
                            }
                        } else null,
                    )
                    Spacer(Modifier.width(4.dp))
                    AnimatedContent(
                        targetState = inputText.isNotBlank(),
                        transitionSpec = {
                            (scaleIn(initialScale = 0.6f, animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) + fadeIn(tween(150))) togetherWith
                                    (scaleOut(targetScale = 0.6f, animationSpec = spring(stiffness = Spring.StiffnessHigh)) + fadeOut(tween(80)))
                        },
                        label = "send_mic",
                    ) { hasText ->
                        if (hasText) {
                            val sendScale = remember { Animatable(1f) }
                            val sendScope = rememberCoroutineScope()
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .scale(sendScale.value)
                                    .background(if (uiState.isCooldown) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable(enabled = !uiState.isCooldown, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        sendScope.launch {
                                            sendScale.animateTo(0.82f, spring(stiffness = Spring.StiffnessHigh))
                                            sendScale.animateTo(1.12f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                                            sendScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                                        }
                                        haptic.perform(HapticType.MESSAGE_SENT, hapticEnabled)
                                        onSend()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                            }
                        } else {
                            if (canSendMedia) {
                                IconButton(
                                    onClick  = {
                                        if (audioPermission.status.isGranted) { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onStartRecord() }
                                        else onRequestAudioPerm()
                                    },
                                    modifier = Modifier.size(48.dp), enabled  = !uiState.isCooldown,
                                ) {
                                    Icon(Icons.Default.Mic, stringResource(R.string.chat_input_record), tint = MaterialTheme.colorScheme.primary)
                                }
                            } else Spacer(Modifier.size(48.dp))
                        }
                    }
                } else {
                    RecordingBar(hapticEnabled = hapticEnabled, onCancel = onCancel, onSend = onSendRecord)
                }
            }
        }
    }
}