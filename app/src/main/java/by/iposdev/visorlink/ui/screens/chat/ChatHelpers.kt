package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.Message
import by.iposdev.visorlink.data.model.MessageType
import by.iposdev.visorlink.ui.theme.Biolume
import by.iposdev.visorlink.ui.theme.exthruSmallRaisedShadow
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.VoicePlaybackState
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TypingDots(primaryColor: Color = Color.Unspecified) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    val color = if (primaryColor == Color.Unspecified) MaterialTheme.colorScheme.primary else primaryColor
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.chat_typing),
            style = MaterialTheme.typography.labelSmall, color = color, fontSize = 11.sp)
        (0..2).forEach { i ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = i * 130, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot_$i",
            )
            Box(Modifier.size(4.dp).background(color.copy(alpha = alpha), CircleShape))
        }
    }
}

@Composable
fun VoiceBubble(
    messageId: String, url: String, durationSec: Int, tint: Color,
    playback: VoicePlaybackState, onPlay: (url: String, durationSec: Int) -> Unit, onSeek: (Float) -> Unit,
) {
    val isThisMessage = playback.playingMessageId == messageId
    val isPlaying  = isThisMessage && playback.isPlaying
    val isLoading  = isThisMessage && playback.isLoading
    val progress   = if (isThisMessage) playback.progress else 0f
    val currentSec = if (isThisMessage) playback.currentMs / 1000 else 0
    val totalSec   = if (isThisMessage && playback.durationMs > 0) playback.durationMs / 1000 else durationSec

    val waveform = remember(url) { List(40) { kotlin.random.Random.nextFloat() * 0.8f + 0.2f } }

    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "pulse_phase",
    )

    Column(modifier = Modifier.width(220.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(38.dp)
                    .background(tint.copy(alpha = 0.15f), CircleShape)
                    .clickable { onPlay(url, durationSec) },
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = tint)
                } else {
                    Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Canvas(
                modifier = Modifier.weight(1f).height(36.dp).pointerInput(messageId) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val press = event.changes.firstOrNull() ?: continue
                            if (press.pressed) {
                                val fraction = (press.position.x / size.width).coerceIn(0f, 1f)
                                press.consume()
                                onSeek(fraction)
                                onPlay(url, durationSec)
                            }
                        }
                    }
                },
            ) {
                drawWaveform(waveform, progress, isThisMessage, isPlaying, pulsePhase, tint)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 46.dp, top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatVoiceTime(currentSec), style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.8f), fontSize = 10.sp)
            Text(formatVoiceTime(totalSec), style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.5f), fontSize = 10.sp)
        }
    }
}

private fun DrawScope.drawWaveform(
    waveform: List<Float>, progress: Float, isThisMessage: Boolean, isPlaying: Boolean, pulsePhase: Float, tint: Color,
) {
    val barCount = waveform.size
    val gap = size.width * 0.018f
    val barWidth = (size.width - gap * (barCount - 1)) / barCount
    val centerY = size.height / 2f

    waveform.forEachIndexed { index, amp ->
        val barProgress = (index + 0.5f) / barCount
        val isPassed = barProgress <= progress && isThisMessage
        val pulse = if (isPlaying && isPassed) 1f + 0.12f * sin(pulsePhase + index * 0.4f) else 1f
        val barHeight = (amp * size.height * 0.88f * pulse).coerceAtLeast(3f)
        val x = index * (barWidth + gap)
        drawRoundRect(
            color = if (isPassed) tint else tint.copy(alpha = 0.30f),
            topLeft = Offset(x, centerY - barHeight / 2f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f),
        )
    }
    if (isThisMessage && progress > 0f) {
        drawLine(color = tint.copy(alpha = 0.6f),
            start = Offset(progress * size.width, 0f),
            end = Offset(progress * size.width, size.height),
            strokeWidth = 1.5f)
    }
}

fun formatVoiceTime(sec: Int) = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

@Composable
fun SwipeableMessage(
    message: Message,
    isMine: Boolean,
    hapticEnabled: Boolean,
    isOneUi: Boolean = false,
    isExthru: Boolean = false,
    isDark: Boolean = false,
    onReply: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptic = rememberHaptic()
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val triggerThreshold = 80f
    val maxOffset = 110f
    var didTrigger by remember { mutableStateOf(false) }

    val replyIconAlpha by animateFloatAsState(
        targetValue = if (abs(offsetX.value) > 20f) (abs(offsetX.value) / triggerThreshold).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(80), label = "reply_icon_alpha",
    )
    val replyIconScale by animateFloatAsState(
        targetValue = if (abs(offsetX.value) >= triggerThreshold) 1.15f else if (abs(offsetX.value) > 20f) (0.6f + 0.4f * (abs(offsetX.value) / triggerThreshold)).coerceIn(0.6f, 1.15f) else 0.6f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy), label = "reply_icon_scale",
    )

    val replyIconColor = when {
        isExthru -> ExthruChat.Accent
        isOneUi  -> if (isDark) OneUiChat.BlueDark else OneUiChat.Blue
        else     -> MaterialTheme.colorScheme.primary
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(if (isMine) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 16.dp).size(36.dp).scale(replyIconScale)
                .background(replyIconColor.copy(alpha = replyIconAlpha * 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Reply, stringResource(R.string.chat_reply), tint = replyIconColor.copy(alpha = replyIconAlpha), modifier = Modifier.size(20.dp))
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(message.id) {
                    var totalDrag = 0f
                    var totalDragY = 0f
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        totalDrag = 0f
                        totalDragY = 0f
                        didTrigger = false
                        var isDragging = false
                        var isVertical = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break

                            // Если нажатие было перехвачено жестовым меню сообщений - игнорируем
                            if (change.isConsumed) {
                                isDragging = false
                                break
                            }

                            val dragDeltaX = change.position.x - change.previousPosition.x
                            val dragDeltaY = change.position.y - change.previousPosition.y
                            totalDrag += dragDeltaX
                            totalDragY += dragDeltaY

                            if (!isDragging && abs(totalDragY) > abs(totalDrag)) {
                                isVertical = true; break
                            }
                            if (isVertical) break

                            if (abs(totalDrag) > 10f) {
                                isDragging = true
                                change.consume()
                                val target = if (isMine) (offsetX.value + dragDeltaX).coerceIn(-maxOffset, 0f) else (offsetX.value + dragDeltaX).coerceIn(0f, maxOffset)
                                scope.launch { offsetX.snapTo(target) }

                                if (abs(offsetX.value) >= triggerThreshold && !didTrigger) {
                                    didTrigger = true
                                    haptic.perform(HapticType.SELECTION, hapticEnabled)
                                    onReply()
                                    scope.launch {
                                        offsetX.animateTo(if (isMine) -triggerThreshold * 0.5f else triggerThreshold * 0.5f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh))
                                        delay(100)
                                        offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                    }
                                }
                            }
                        }

                        if (isDragging || offsetX.value != 0f) {
                            scope.launch { offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                        }
                    }
                },
        ) { content() }
    }
}

@Composable
fun EmptyChatPlaceholder(modifier: Modifier = Modifier, isExthru: Boolean = false, isDark: Boolean = false) {
    val emptyTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "empty")
    val emptyScale by emptyTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "empty_scale",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(64.dp).scale(emptyScale), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.25f))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.chat_empty_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
        }
    }
}

@Composable
fun RecordingBar(
    isExthru: Boolean = false,
    isDark: Boolean = false,
    hapticEnabled: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val haptic = rememberHaptic()
    var elapsed by remember { mutableIntStateOf(0) }
    val dotAlpha by androidx.compose.animation.core.rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot_alpha",
    )

    val timerColor = if (isExthru) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error
    val labelColor = if (isExthru) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(Unit) {
        while (true) { delay(1000); elapsed++; haptic.perform(HapticType.CLICK, hapticEnabled) }
    }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isExthru) {
            val intCancel = remember { MutableInteractionSource() }
            val isCancelPressed by intCancel.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isCancelPressed) 0.85f else 1f, spring(dampingRatio = 0.5f), label = "")
            val shadowMod = if (isCancelPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 24.dp) else Modifier.exthruSmallRaisedShadow(isDark)

            Box(
                modifier = Modifier.size(48.dp).scale(scale).then(shadowMod).background(MaterialTheme.colorScheme.surface, CircleShape).clip(CircleShape)
                    .clickable(interactionSource = intCancel, indication = null) { haptic.perform(HapticType.ERROR, hapticEnabled); onCancel() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Delete, "Cancel", tint = MaterialTheme.colorScheme.error) }
        } else {
            IconButton(onClick = { haptic.perform(HapticType.ERROR, hapticEnabled); onCancel() }) {
                Icon(Icons.Default.Delete, "Cancel", tint = MaterialTheme.colorScheme.error)
            }
        }

        Row(Modifier.weight(1f).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Color.Red.copy(alpha = dotAlpha), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodyMedium, color = timerColor)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.chat_recording_label), style = MaterialTheme.typography.bodySmall, color = labelColor)
        }

        if (isExthru) {
            val intSend = remember { MutableInteractionSource() }
            val isSendPressed by intSend.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isSendPressed) 0.85f else 1f, spring(dampingRatio = 0.5f), label = "")
            val shadowMod = if (isSendPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 24.dp, darkAlpha = if(isDark)0.8f else 0.5f) else Modifier.exthruSmallRaisedShadow(isDark)

            Box(
                modifier = Modifier.size(48.dp).scale(scale).then(shadowMod)
                    .background(Brush.radialGradient(listOf(Biolume.TealLight, Biolume.TealPulse)), CircleShape).clip(CircleShape)
                    .clickable(interactionSource = intSend, indication = null) { haptic.perform(HapticType.SUCCESS, hapticEnabled); onSend() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White) }
        } else {
            IconButton(
                onClick = { haptic.perform(HapticType.SUCCESS, hapticEnabled); onSend() },
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send), tint = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

@Composable
fun ReplyBanner(message: Message, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("@${message.senderUsername}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(
                when (message.type) {
                    MessageType.IMAGE   -> stringResource(R.string.image)
                    MessageType.VOICE   -> stringResource(R.string.voice_message)
                    MessageType.STICKER -> stringResource(R.string.sticker)
                    MessageType.ALBUM   -> message.caption ?: "📷 ${message.images.size} фото"
                    else -> message.text ?: "Media"
                },
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.chat_cancel_reply), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}