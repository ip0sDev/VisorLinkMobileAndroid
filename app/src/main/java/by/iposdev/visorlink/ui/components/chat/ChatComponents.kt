package by.iposdev.visorlink.ui.components.chat

import android.util.Log
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
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import by.iposdev.visorlink.data.model.SendStatus
import by.iposdev.visorlink.utils.CdnService
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.VoicePlaybackState
import by.iposdev.visorlink.utils.rememberHaptic
import by.iposdev.visorlink.ui.theme.VlTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun resolveCdnUrl(cdnMediaId: String?, fallbackUrl: String?): String? {
    // Сторонний CDN api.visorlink.org выведен из эксплуатации. Не делаем HTTP-запросов (Секция 1.1).
    if (fallbackUrl?.contains("api.visorlink.org") == true) return null
    if (fallbackUrl?.contains("/f/") == true && fallbackUrl.contains("googleusercontent.com") != true) return null
    if (!cdnMediaId.isNullOrEmpty()) return null
    return fallbackUrl
}

@Composable
fun TypingDots(primaryColor: Color = Color.Unspecified) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
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
            Box(Modifier.size(4.dp).background(color.copy(alpha = alpha), VlTheme.tokens.shapes.indicator))
        }
    }
}

@Composable
fun VoiceBubble(
    messageId: String, url: String, durationSec: Int, tint: Color,
    playback: VoicePlaybackState,
    uploadProgress: Float? = null,
    onPlay: (url: String, durationSec: Int) -> Unit, onSeek: (Float) -> Unit,
) {
    val isThisMessage = playback.playingMessageId == messageId
    val isPlaying  = isThisMessage && playback.isPlaying
    val isLoading  = isThisMessage && playback.isLoading
    val progress   = if (isThisMessage) playback.progress else 0f
    val currentSec = if (isThisMessage) playback.currentMs / 1000 else 0
    val totalSec   = if (isThisMessage && playback.durationMs > 0) playback.durationMs / 1000 else durationSec

    val waveform = remember(url) { List(40) { Random.nextFloat() * 0.8f + 0.2f } }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "pulse_phase",
    )

    Box(contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(220.dp).then(if (uploadProgress != null) Modifier.alpha(0.6f) else Modifier)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(38.dp)
                        .background(tint.copy(alpha = 0.15f), VlTheme.tokens.shapes.indicator)
                        .clickable { onPlay(url, durationSec) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = tint)
                    } else {
                        val playDesc = if (isPlaying) stringResource(R.string.chat_voice_pause) else stringResource(R.string.chat_voice_play)
                        Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = playDesc, tint = tint, modifier = Modifier.size(22.dp))
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

        if (uploadProgress != null) {
            UploadProgressOverlay(
                progress = uploadProgress,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
fun UploadProgressOverlay(
    progress: Float,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null
) {
    // В логах проверяем, что компонент вообще живой
    SideEffect {
        if (progress > 0f) {
            Log.d("VlUI", "Drawing progress overlay: $progress")
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f)), // Чуть темнее для контраста
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp) // Чуть больше
                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(48.dp),
                color = Color.White,
                strokeWidth = 4.dp, // Толще
                trackColor = Color.White.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .then(if (onCancel != null) Modifier.clickable { onCancel() } else Modifier)
            )
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

    val replyIconColor = MaterialTheme.colorScheme.primary

    val align = if (offsetX.value > 0) Alignment.CenterStart else if (offsetX.value < 0) Alignment.CenterEnd else (if (isMine) Alignment.CenterEnd else Alignment.CenterStart)

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(align)
                .padding(horizontal = 16.dp).size(36.dp).scale(replyIconScale)
                .background(replyIconColor.copy(alpha = replyIconAlpha * 0.12f), VlTheme.tokens.shapes.indicator),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Reply, stringResource(R.string.chat_reply), tint = replyIconColor.copy(alpha = replyIconAlpha), modifier = Modifier.size(20.dp))
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(message.id) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        totalDragX = 0f
                        totalDragY = 0f
                        didTrigger = false
                        var isDragging = false
                        var isVertical = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break

                            if (change.isConsumed) {
                                isDragging = false
                                break
                            }

                            val dragDeltaX = change.position.x - change.previousPosition.x
                            val dragDeltaY = change.position.y - change.previousPosition.y
                            totalDragX += dragDeltaX
                            totalDragY += dragDeltaY

                            if (!isDragging && abs(totalDragY) > abs(totalDragX)) {
                                isVertical = true; break
                            }
                            if (isVertical) break

                            if (abs(totalDragX) > 10f) {
                                isDragging = true
                                change.consume()

                                val target = (offsetX.value + dragDeltaX).coerceIn(-maxOffset, maxOffset)
                                scope.launch { offsetX.snapTo(target) }

                                if (abs(offsetX.value) >= triggerThreshold && !didTrigger) {
                                    didTrigger = true
                                    haptic.perform(HapticType.SELECTION, hapticEnabled)
                                    onReply()

                                    val bounceBackTarget = if (offsetX.value > 0) triggerThreshold * 0.5f else -triggerThreshold * 0.5f
                                    scope.launch {
                                        offsetX.animateTo(bounceBackTarget, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh))
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
fun EmptyChatPlaceholder(modifier: Modifier = Modifier) {
    val emptyTransition = rememberInfiniteTransition(label = "empty")
    val emptyScale by emptyTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "empty_scale",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        val cs = MaterialTheme.colorScheme
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(64.dp).scale(emptyScale), tint = cs.onSurfaceVariant.copy(0.25f))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant.copy(0.5f))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.chat_empty_subtitle), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(0.35f))
        }
    }
}

@Composable
fun RecordingBar(
    hapticEnabled: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val haptic = rememberHaptic()
    var elapsed by remember { mutableIntStateOf(0) }
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot_alpha",
    )

    val timerColor = MaterialTheme.colorScheme.error
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(Unit) {
        while (true) { delay(1000); elapsed++; haptic.perform(HapticType.CLICK, hapticEnabled) }
    }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { haptic.perform(HapticType.ERROR, hapticEnabled); onCancel() }) {
            Icon(Icons.Default.Delete, "Cancel", tint = MaterialTheme.colorScheme.error)
        }

        Row(Modifier.weight(1f).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Color.Red.copy(alpha = dotAlpha), VlTheme.tokens.shapes.indicator))
            Spacer(Modifier.width(8.dp))
            Text("${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodyMedium, color = timerColor)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.chat_recording_label), style = MaterialTheme.typography.bodySmall, color = labelColor)
        }

        IconButton(
            onClick = { haptic.perform(HapticType.SUCCESS, hapticEnabled); onSend() },
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, VlTheme.tokens.shapes.fab),
        ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send), tint = MaterialTheme.colorScheme.onPrimary) }
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

@Composable
fun MessageStatusIcon(status: String) {
    val icon = when (status) {
        SendStatus.SENDING -> Icons.Default.Schedule
        SendStatus.QUEUED  -> Icons.Default.Schedule
        SendStatus.ERROR   -> Icons.Default.ErrorOutline
        else               -> Icons.Default.Done
    }
    val tint = when (status) {
        SendStatus.SENDING, SendStatus.QUEUED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        SendStatus.ERROR   -> MaterialTheme.colorScheme.error
        else               -> MaterialTheme.colorScheme.primary
    }

    val desc = when (status) {
        SendStatus.SENDING, SendStatus.QUEUED -> "Sending"
        SendStatus.ERROR -> "Error sending"
        else -> stringResource(R.string.chat_sent)
    }
    Icon(imageVector = icon, contentDescription = desc, modifier = Modifier.size(13.dp), tint = tint)
}

@Composable
fun ReadReceipt(isRead: Boolean) {
    val desc = if (isRead) stringResource(R.string.chat_read) else stringResource(R.string.chat_sent)
    Icon(imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Done, contentDescription = desc, modifier = Modifier.size(15.dp), tint = if (isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
}

@Composable
fun DateSeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = VlTheme.tokens.shapes.chip
        ) {
            Text(
                text     = label,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

val QUICK_REACTIONS = listOf(
    "👍","❤️","😂","😮","😢","🔥",
    "🎉","👏","🥰","😍","🤩","😭",
    "🤔","👀","💯","✅","🙏","😎",
    "🤣","😅","😡","💀","🎊","⚡"
)
