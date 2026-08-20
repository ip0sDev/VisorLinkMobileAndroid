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
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberHaptic()

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
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(cs.surfaceContainerLow)
                    .padding(4.dp)
            ) {
                AnimatedVisibility(visible = uiState.replyingTo != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    uiState.replyingTo?.let { msg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(cs.surfaceContainerHighest)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(3.dp).height(32.dp).background(cs.primary, RoundedCornerShape(2.dp)))
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

                AnimatedVisibility(visible = uiState.isUploading, enter = expandVertically(), exit = shrinkVertically()) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(4.dp)), color = cs.primary, trackColor = Color.Transparent)
                }

                if (canSendMessage) {
                    if (uiState.isRecording) {
                        RecordingBar(
                            hapticEnabled = hapticEnabled,
                            onCancel = onCancelRecord,
                            onSend = onSendRecord
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (canSendMedia) {
                                IconButton(
                                    onClick = onAttach,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                    enabled = !uiState.isCooldown
                                ) {
                                    Icon(Icons.Default.Add, null, tint = cs.primary)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(cs.surfaceContainerHighest)
                                    .padding(start = 16.dp, end = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = onInputChange,
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester)
                                            .padding(top = 14.dp, bottom = 14.dp, end = 4.dp),
                                        maxLines = 5,
                                        textStyle = TextStyle(color = cs.onSurface, fontSize = 15.sp),
                                        decorationBox = { inner ->
                                            if (inputText.isEmpty()) {
                                                Text(stringResource(R.string.chat_input_placeholder), fontSize = 15.sp, color = cs.onSurfaceVariant.copy(alpha = 0.7f))
                                            }
                                            inner()
                                        }
                                    )

                                    if (inputText.isNotEmpty()) {
                                        Text(
                                            "${inputText.length}/2000", fontSize = 11.sp,
                                            color = if (inputText.length >= 2000) cs.error else cs.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 16.dp, end = 6.dp),
                                        )
                                    }

                                    if (canSendStickers) {
                                        IconButton(onClick = onStickerClick, modifier = Modifier.padding(bottom = 2.dp)) {
                                            Icon(Icons.Default.EmojiEmotions, null, tint = if (showStickerSheet) cs.primary else cs.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.width(8.dp))
                            AnimatedContent(
                                targetState = inputText.isNotBlank(),
                                transitionSpec = {
                                    scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn() togetherWith scaleOut(spring(stiffness = Spring.StiffnessHigh)) + fadeOut()
                                },
                                label = "send_mic",
                                modifier = Modifier.padding(bottom = 2.dp)
                            ) { hasText ->
                                if (hasText) {
                                    val sendScale by animateFloatAsState(if (uiState.isCooldown) 0.85f else 1f, label = "")
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .scale(sendScale)
                                            .background(cs.primary, CircleShape)
                                            .clip(CircleShape)
                                            .clickable(enabled = !uiState.isCooldown) { 
                                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                                onSend() 
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = cs.onPrimary, modifier = Modifier.size(22.dp))
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(cs.primaryContainer, CircleShape)
                                            .clip(CircleShape)
                                            .clickable(enabled = !uiState.isCooldown) {
                                                if (audioPermission.status.isGranted) { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onStartRecord() }
                                                else onRequestAudioPerm()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Mic, "Record", tint = cs.onPrimaryContainer, modifier = Modifier.size(22.dp))
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
