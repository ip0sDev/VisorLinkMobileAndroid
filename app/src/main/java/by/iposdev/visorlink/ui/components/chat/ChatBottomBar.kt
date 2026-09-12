package by.iposdev.visorlink.ui.components.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlHairline
import by.iposdev.visorlink.ui.theme.vlInset
import by.iposdev.visorlink.ui.theme.vlRaised
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.screens.chat.ChatUiState
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatBottomBar(
    uiState: ChatUiState,
    inputText: String,
    canSendMessage: Boolean,
    canSendMedia: Boolean,
    canSendStickers: Boolean = true,
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
    onCancelEdit: () -> Unit = {},
    onJoinChannel: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val haptic = rememberHaptic()

    // ── 1. Канал: Неподписанный гость ─────────────────────────────────────────
    if (uiState.chatType == ChatType.CHANNEL && !uiState.isChannelMember) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            color = cs.surfaceContainerLow,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.chat?.name ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.channel_subscribers_count, uiState.chat?.memberCount ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { onJoinChannel?.invoke() },
                    enabled = !uiState.isJoiningChannel,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
                ) {
                    if (uiState.isJoiningChannel) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = cs.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.channel_subscribe))
                    }
                }
            }
        }
        return
    }

    // ── 2. Канал: Подписчик без прав на публикацию ─────────────────────────────
    if (uiState.chatType == ChatType.CHANNEL && !canSendMessage) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            color = cs.surfaceContainerLow
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.channel_only_admins_post),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
    ) {
        val restriction = when {
            uiState.myMember?.banned == true -> stringResource(R.string.you_are_banned_from_this_chat)
            uiState.myMember?.muted == true  -> stringResource(R.string.you_are_muted)
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
                .padding(8.dp)
        ) {
            val panelShape = tokens.shapes.inputPanel
            Column(
                modifier = Modifier
                    .then(
                        if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, panelShape)
                        else Modifier
                    )
                    .clip(panelShape)
                    .background(cs.surfaceContainerLow)
                    .then(
                        if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, panelShape)
                        else Modifier
                    )
                    .padding(4.dp)
            ) {
                AnimatedVisibility(visible = uiState.replyingTo != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    uiState.replyingTo?.let { msg ->
                        // Цитата «принимает» контент чужого сообщения → inset (§4.1).
                        val quoteShape = tokens.shapes.card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .clip(quoteShape)
                                .background(
                                    if (tokens.structure.enabled) cs.surfaceContainer
                                    else cs.surfaceContainerHighest
                                )
                                .vlInset(tokens.structure, quoteShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(3.dp).height(32.dp).background(cs.primary, tokens.shapes.indicator))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("@${msg.senderUsername}", style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.SemiBold)
                                    Text(msg.text ?: "Медиа", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = onClearReply, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, "Cancel", tint = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.editingMessage != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    uiState.editingMessage?.let { msg ->
                        val quoteShape = tokens.shapes.card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .clip(quoteShape)
                                .background(cs.surfaceContainer)
                                .vlInset(tokens.structure, quoteShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Редактирование", style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.Bold)
                                    Text(msg.text ?: msg.caption ?: "Медиа", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = onCancelEdit, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, "Cancel", tint = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.isUploading, enter = expandVertically(), exit = shrinkVertically()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(tokens.shapes.indicator), color = cs.primary, trackColor = Color.Transparent)
                }

                // ── AI Bot Concurrency Banner ─────────────────────────────────
                AnimatedVisibility(
                    visible = uiState.isBotGenerating,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = cs.primaryContainer.copy(alpha = 0.4f),
                        shape = tokens.shapes.card,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = cs.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.bot_generating_banner),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.primary
                            )
                        }
                    }
                }

                if (canSendMessage) {
                    if (uiState.isRecording) {
                        RecordingBar(
                            hapticEnabled = hapticEnabled,
                            onCancel = onCancelRecord,
                            onSend = onSendRecord
                        )
                    } else {
                        val isBot = uiState.otherUser?.isBot == true
                        val maxChars = if (isBot) 600 else 2000

                        Row(verticalAlignment = Alignment.Bottom) {
                            if (canSendMedia) {
                                IconButton(
                                    onClick = onAttach,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    enabled = !uiState.isCooldown && !uiState.isBotGenerating
                                ) {
                                    Icon(Icons.Default.Add, null, tint = if (uiState.isBotGenerating) cs.onSurfaceVariant.copy(alpha = 0.38f) else cs.primary)
                                }
                            }

                            // §4.1: поле ввода «принимает» → в Biolume врезано.
                            // Счётчик символов — data-роль (§1.5, §5), поэтому моноширинный.
                            val inputShape = tokens.shapes.field
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(inputShape)
                                    .background(
                                        if (tokens.structure.enabled) cs.surfaceContainer
                                        else cs.surfaceContainerHighest
                                    )
                                    .vlInset(tokens.structure, inputShape)
                                    .padding(start = 16.dp, end = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = { if (!isBot || it.length <= 600) onInputChange(it) else onInputChange(it.take(600)) },
                                        enabled = !uiState.isBotGenerating,
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester)
                                            .padding(top = 14.dp, bottom = 14.dp, end = 4.dp),
                                        maxLines = 5,
                                        textStyle = TextStyle(color = if (uiState.isBotGenerating) cs.onSurface.copy(alpha = 0.5f) else cs.onSurface, fontSize = 15.sp),
                                        decorationBox = { inner ->
                                            if (inputText.isEmpty()) {
                                                Text(
                                                    if (uiState.isBotGenerating) stringResource(R.string.bot_generating_placeholder)
                                                    else stringResource(R.string.chat_input_placeholder),
                                                    fontSize = 15.sp,
                                                    color = cs.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                            inner()
                                        }
                                    )

                                    if (inputText.isNotEmpty() || isBot) {
                                        Text(
                                            "${inputText.length}/$maxChars",
                                            style = tokens.data.dataSmall,
                                            color = if (inputText.length >= maxChars) cs.error else cs.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 16.dp, end = 6.dp),
                                        )
                                    }

                                    if (canSendStickers) {
                                        IconButton(
                                            onClick = onStickerClick,
                                            enabled = !uiState.isBotGenerating,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        ) {
                                            Icon(Icons.Default.EmojiEmotions, null, tint = if (uiState.isBotGenerating) cs.onSurfaceVariant.copy(alpha = 0.38f) else if (showStickerSheet) cs.primary else cs.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.width(8.dp))
                            AnimatedContent(
                                targetState = inputText.isNotBlank() || uiState.editingMessage != null,
                                transitionSpec = {
                                    scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn() togetherWith scaleOut(spring(stiffness = Spring.StiffnessHigh)) + fadeOut()
                                },
                                label = "send_mic",
                                modifier = Modifier.padding(bottom = 2.dp)
                            ) { hasTextOrEdit ->
                                val sendEnabled = !uiState.isCooldown && !uiState.isBotGenerating && (!isBot || inputText.length <= 600)
                                if (hasTextOrEdit) {
                                    val sendScale by animateFloatAsState(if (!sendEnabled) 0.85f else 1f, label = "")
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .scale(sendScale)
                                            .background(if (sendEnabled) cs.primary else cs.surfaceVariant, tokens.shapes.indicator)
                                            .clip(tokens.shapes.indicator)
                                            .clickable(enabled = sendEnabled) { 
                                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                                onSend() 
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(if (uiState.editingMessage != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send, "Send", tint = if (sendEnabled) cs.onPrimary else cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(if (!uiState.isBotGenerating) cs.primaryContainer else cs.surfaceVariant, tokens.shapes.indicator)
                                            .clip(tokens.shapes.indicator)
                                            .clickable(enabled = !uiState.isCooldown && !uiState.isBotGenerating) {
                                                if (audioPermission.status.isGranted) { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onStartRecord() }
                                                else onRequestAudioPerm()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Mic, "Record", tint = if (!uiState.isBotGenerating) cs.onPrimaryContainer else cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
