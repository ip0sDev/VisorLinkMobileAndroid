package org.visorlink.app.ui.components.chat

import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import org.visorlink.app.utils.VideoCache
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.model.*
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.CachedImage
import org.visorlink.app.ui.components.VlAnimatedMedia
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.rememberLiquidJellyState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.sin
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.MusicPlayerState
import org.visorlink.app.utils.VoicePlaybackState
import org.visorlink.app.utils.rememberHaptic
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlInset
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.ui.theme.LocalShowDebugIds
import java.text.SimpleDateFormat
import java.util.*

// ── Helpers ──────────────────────────────────────────────────────────────────

@Composable
internal fun DebugMessageBadge(
    message: Message,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    if (LocalShowDebugIds.current) {
        val seqText = message.seq?.toString() ?: "legacy"
        Text(
            text = "ID: ${message.id.take(8)} | seq: $seqText",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = textColor.copy(alpha = 0.65f),
            maxLines = 1,
            softWrap = false,
            modifier = modifier.padding(vertical = 1.dp)
        )
    }
}

// Цвета бабблов идут через VlTokens: в Biolume полупрозрачный primaryContainer
// (alpha .12) для баббла слишком бледный, нужен плотный подмешанный тон.
// См. VlBubbleTokens — там же объяснено, почему у бабблов нет рельефа.

@Composable
internal fun resolveBubbleColor(isMine: Boolean): Color {
    val bubbles = VlTheme.tokens.bubbles
    return if (isMine) bubbles.mineBg else bubbles.otherBg
}

@Composable
internal fun resolveBubbleTextColor(isMine: Boolean): Color {
    val bubbles = VlTheme.tokens.bubbles
    return if (isMine) bubbles.mineFg else bubbles.otherFg
}

@Composable
internal fun resolveLinkColor(isMine: Boolean): Color {
    return MaterialTheme.colorScheme.primary
}

fun Modifier.messageGestures(
    messageId: String,
    interactionSource: MutableInteractionSource,
    onTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    hapticEnabled: Boolean = true,
    onLongPressStart: (Offset) -> Unit,
    onLongPressDrag: (Offset) -> Unit,
    onLongPressEnd: () -> Unit
) = composed {
    var globalPos by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()

    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentHapticEnabled by rememberUpdatedState(hapticEnabled)
    val currentOnLongPressStart by rememberUpdatedState(onLongPressStart)
    val currentOnLongPressDrag by rememberUpdatedState(onLongPressDrag)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)

    this
        .onGloballyPositioned { globalPos = it.positionInWindow() }
        .pointerInput(messageId, currentOnDoubleTap != null, currentOnTap != null) {
            detectTapGestures(
                onPress = {
                    val press = PressInteraction.Press(it)
                    scope.launch { interactionSource.emit(press) }
                    val released = tryAwaitRelease()
                    scope.launch {
                        if (released) interactionSource.emit(PressInteraction.Release(press))
                        else interactionSource.emit(PressInteraction.Cancel(press))
                    }
                },
                onDoubleTap = if (currentOnDoubleTap != null) {
                    { _ ->
                        haptic.perform(HapticType.CLICK, currentHapticEnabled)
                        currentOnDoubleTap?.invoke()
                    }
                } else null,
                onTap = if (currentOnTap != null) { { currentOnTap?.invoke() } } else null
            )
        }
        .pointerInput(messageId) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local -> currentOnLongPressStart(globalPos + local) },
                onDrag = { change, amount ->
                    change.consume()
                    currentOnLongPressDrag(amount)
                },
                onDragEnd = { currentOnLongPressEnd() },
                onDragCancel = { currentOnLongPressEnd() }
            )
        }
}

/**
 * Жидкая анимация сердечка при двойном тапе по сообщению.
 * Воспроизводит эффект пульсации/отскока с последующим мягким затуханием,
 * желейной деформацией Squash & Stretch и всплеском микро-капель.
 */
@Composable
internal fun DoubleTapHeartAnimation(
    triggerKey: Int,
    modifier: Modifier = Modifier,
    liquidEnabled: Boolean = true
) {
    var isPlaying by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(triggerKey) {
        if (triggerKey > 0) {
            isPlaying = true
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 720, easing = LinearEasing)
            )
            isPlaying = false
        }
    }

    if (!isPlaying) return

    val p = progress.value

    // Фаза 1 (0..0.30): взрывной рост капли сердца с вытягиванием по X
    // Фаза 2 (0.30..0.65): упругие затухающие колебания (Squash & Stretch)
    // Фаза 3 (0.65..1.0): всплывание вверх и растворение
    val (scaleX, scaleY, alpha, offsetY) = remember(p, liquidEnabled) {
        if (!liquidEnabled) {
            val s = if (p < 0.4f) (p / 0.4f) * 1.15f else 1.15f + (p - 0.4f) * 0.2f
            val a = if (p > 0.65f) (1f - (p - 0.65f) / 0.35f).coerceIn(0f, 1f) else 1f
            listOf(s, s, a, 0f)
        } else {
            when {
                p < 0.30f -> {
                    val sub = p / 0.30f
                    val sX = 0.2f + 1.15f * sub
                    val sY = 0.2f + 0.95f * sub
                    listOf(sX, sY, sub.coerceIn(0f, 1f), 0f)
                }
                p < 0.65f -> {
                    val sub = (p - 0.30f) / 0.35f
                    val bounce = kotlin.math.sin(sub * Math.PI.toFloat() * 2f) * 0.12f * (1f - sub)
                    listOf(1.10f + bounce, 1.10f - bounce * 0.8f, 1f, 0f)
                }
                else -> {
                    val sub = (p - 0.65f) / 0.35f
                    val sX = 1.10f + sub * 0.15f
                    val sY = 1.10f + sub * 0.15f
                    val a = (1f - sub).coerceIn(0f, 1f)
                    val y = -sub * 28f
                    listOf(sX, sY, a, y)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Микро-капли (Metaball Droplets Splash) при liquid-режиме
        if (liquidEnabled && p in 0.12f..0.85f) {
            val splashProgress = ((p - 0.12f) / 0.73f).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.size(110.dp)) {
                val dropletCount = 6
                val center = Offset(size.width / 2f, size.height / 2f + offsetY)
                val baseDistance = 22.dp.toPx() + splashProgress * 26.dp.toPx()
                val dropAlpha = (1f - splashProgress) * 0.85f

                for (i in 0 until dropletCount) {
                    val angle = (i * (360f / dropletCount) + 15f) * (Math.PI.toFloat() / 180f)
                    val dist = baseDistance * (0.85f + (i % 3) * 0.15f)
                    val radius = (3.5f.dp.toPx() * (1f - splashProgress * 0.7f)).coerceAtLeast(0.5f)
                    val dropCenter = Offset(
                        x = center.x + kotlin.math.cos(angle) * dist,
                        y = center.y + kotlin.math.sin(angle) * dist
                    )
                    drawCircle(
                        color = Color(0xFFFF2D55).copy(alpha = dropAlpha),
                        radius = radius,
                        center = dropCenter
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    this.alpha = alpha
                    this.translationY = offsetY
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.30f * alpha),
                modifier = Modifier
                    .size(66.dp)
                    .offset(y = 2.dp)
            )
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF2D55),
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

// ── Components ───────────────────────────────────────────────────────────────

@Composable
internal fun ReplyPreview(
    reply: ReplyData,
    isMine: Boolean,
    onMedia: Boolean = false,
    onClick: () -> Unit
) {
    val accentColor = if (onMedia) Color.White else MaterialTheme.colorScheme.primary
    val secondaryColor = if (onMedia) Color.White.copy(0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VlTheme.tokens.shapes.indicator)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .background(accentColor, VlTheme.tokens.shapes.indicator)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "@${reply.senderUsername}",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = reply.text ?: "Медиа",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AlbumGrid(
    images: List<AlbumImage>,
    revealedIndices: Set<Int>,
    onReveal: (Int) -> Unit,
    onClick: (Int) -> Unit,
    onDoubleTap: (() -> Unit)? = null
) {
    val count = images.size
    if (count == 0) return

    val maxBubbleWidth = 280.dp
    val spacing = 2.dp

    if (count == 1) {
        // Одиночное фото: адаптируем под естественное соотношение сторон
        Box(
            Modifier
                .widthIn(max = maxBubbleWidth)
                .heightIn(max = 450.dp)
                .clip(VlTheme.tokens.shapes.card)
        ) {
            AlbumImageItem(
                image = images[0],
                isRevealed = 0 in revealedIndices,
                onReveal = { onReveal(0) },
                onClick = { onClick(0) },
                onDoubleTap = onDoubleTap,
                modifier = Modifier.wrapContentSize(),
                useCardShape = false
            )
        }
    } else if (count == 2) {
        // Два фото: в ряд, каждое с его естественным соотношением
        Box(
            Modifier
                .width(maxBubbleWidth)
                .clip(VlTheme.tokens.shapes.card)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                images.forEachIndexed { index, image ->
                    AlbumImageItem(
                        image = image,
                        isRevealed = index in revealedIndices,
                        onReveal = { onReveal(index) },
                        onClick = { onClick(index) },
                        onDoubleTap = onDoubleTap,
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentHeight(),
                        useCardShape = false
                    )
                }
            }
        }
    } else {
        // 3+ фото: сетка с квадратными ячейками
        val columns = if (count == 4) 2 else 3
        val itemSize = (maxBubbleWidth - (spacing * (columns - 1))) / columns
        val rows = (minOf(count, 10) + columns - 1) / columns
        val gridHeight = (itemSize * rows) + (spacing * (rows - 1))

        Box(Modifier.width(maxBubbleWidth).height(gridHeight).clip(VlTheme.tokens.shapes.card)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                userScrollEnabled = false
            ) {
                itemsIndexed(images.take(10)) { index, image ->
                    Box(Modifier.aspectRatio(1f)) {
                        AlbumImageItem(
                            image = image,
                            isRevealed = index in revealedIndices,
                            onReveal = { onReveal(index) },
                            onClick = { onClick(index) },
                            onDoubleTap = onDoubleTap,
                            modifier = Modifier.fillMaxSize(),
                            useCardShape = false
                        )
                        if (index == 9 && images.size > 10) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
                                Text("+${images.size - 9}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumImageItem(
    image: AlbumImage,
    isRevealed: Boolean,
    onReveal: () -> Unit,
    onClick: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    useCardShape: Boolean = true
) {
    val resolvedUrl = resolveCdnUrl(image.cdnMediaId, image.url) ?: image.url
    val showBlur = image.spoiler && !isRevealed
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnReveal by rememberUpdatedState(onReveal)

    Box(modifier
        .then(if (useCardShape) Modifier.clip(VlTheme.tokens.shapes.card) else Modifier)
        .pointerInput(image.url, showBlur, currentOnDoubleTap != null) {
            detectTapGestures(
                onTap = {
                    if (showBlur) currentOnReveal() else currentOnClick()
                },
                onDoubleTap = if (currentOnDoubleTap != null) {
                    { _ -> currentOnDoubleTap?.invoke() }
                } else null
            )
        }
    ) {
        CachedImage(
            model = resolvedUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().then(if (showBlur) Modifier.blur(20.dp) else Modifier),
            contentScale = ContentScale.Crop
        )
        if (showBlur) {
            Icon(Icons.Default.VisibilityOff, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    otherUid: String,
    currentUid: String,
    chatType: ChatType,
    hapticEnabled: Boolean,
    showSenderName: Boolean,
    voicePlayback: VoicePlaybackState,
    musicPlayback: MusicPlayerState = MusicPlayerState(),
    musicDownloadProgress: Map<String, Float> = emptyMap(),
    onPlayVoice: (url: String, durationSec: Int) -> Unit,
    onSeekVoice: (Float) -> Unit,
    onPlayAudio: ((Message) -> Unit)? = null,
    onToggleAudioPlayback: (() -> Unit)? = null,
    onSeekAudio: ((Float) -> Unit)? = null,
    onCycleAudioSpeed: (() -> Unit)? = null,
    onSaveTrackToLibrary: ((MusicTrack) -> Unit)? = null,
    onOpenFullscreenAudio: (() -> Unit)? = null,
    onLongPressStart: (Offset) -> Unit,
    onLongPressDrag: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onMediaTap: (url: String, type: String) -> Unit,
    onAlbumTap: (images: List<AlbumImage>, startIndex: Int) -> Unit,
    onReact: (String) -> Unit,
    onReplyClick: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    onOpenComments: () -> Unit = {},
    chat: Chat? = null,
    onStickerClick: ((packId: String?, stickerId: String?) -> Unit)? = null,
    onCancelUpload: ((String) -> Unit)? = null,
    liquidEnabled: Boolean = true,
) {
    val haptic = rememberHaptic()
    val isReadByOther = otherUid in message.readBy
    var heartAnimKey by remember { mutableIntStateOf(0) }
    val bubbleJelly = rememberLiquidJellyState(softness = 0.08f, damping = 0.55f, stiffness = 320f)
    val onDoubleTapLike = {
        heartAnimKey++
        haptic.perform(HapticType.REACTION, hapticEnabled)
        if (liquidEnabled) {
            bubbleJelly.pulse(0.08f)
        }
        onReact("❤️")
    }

    val uploadProgressModifier = if (message.uploadProgress != null) {
        Modifier.alpha(0.6f)
    } else Modifier

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(modifier = uploadProgressModifier.liquidJelly(bubbleJelly, enabled = liquidEnabled)) {
            when {
                !message.deleted && isLegacyMediaMessage(message) -> {
                    LegacyMediaPlaceholder(
                        message = message,
                        isMine = isMine,
                        chatType = chatType,
                        showSenderName = showSenderName,
                        isReadByOther = isReadByOther,
                        hapticEnabled = hapticEnabled,
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag,
                        onLongPressEnd = onLongPressEnd,
                        onReact = onReact,
                        onReplyClick = onReplyClick,
                        onDoubleTap = onDoubleTapLike,
                    )
                }
                message.type == MessageType.GIFT && !message.deleted -> {
                    GiftMessage(message = message, chatId = chat?.id ?: "")
                }
                // Универсальная отрисовка Lottie-анимаций и GIF (даже при неизвестном типе сообщения)
                (message.isLottieMedia || (message.isGifMedia && message.url?.substringBefore("?")?.endsWith(".gif", ignoreCase = true) == true) || message.type == MessageType.STICKER) && !message.deleted -> {
                    StickerBubble(
                        message = message, isMine = isMine, currentUid = currentUid,
                        hapticEnabled = hapticEnabled,
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd, onReact = onReact,
                        onStickerClick = onStickerClick,
                        onDoubleTap = onDoubleTapLike,
                    )
                }
                (message.type == MessageType.VIDEO || message.type == MessageType.GIF) && !message.deleted -> {
                    VideoBubble(
                        message = message, isMine = isMine, isReadByOther = isReadByOther,
                        chatType = chatType, currentUid = currentUid, hapticEnabled = hapticEnabled,
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd,
                        onMediaTap = onMediaTap,
                        onReact = onReact, onReplyClick = onReplyClick, onOpenComments = onOpenComments, chat = chat,
                        onCancelUpload = onCancelUpload,
                        onDoubleTap = onDoubleTapLike,
                    )
                }
                message.type.equals(MessageType.ALBUM, ignoreCase = true) && !message.deleted -> {
                    AlbumBubble(
                        message = message, isMine = isMine, currentUid = currentUid,
                        chatType = chatType, hapticEnabled = hapticEnabled,
                        onAlbumTap = onAlbumTap,
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd,
                        onReact = onReact, onReplyClick = onReplyClick, onOpenComments = onOpenComments, chat = chat,
                        onCancelUpload = onCancelUpload,
                        onDoubleTap = onDoubleTapLike,
                    )
                }
                message.type == MessageType.IMAGE && !message.deleted -> {
                    ImageBubble(
                        message = message, isMine = isMine, isReadByOther = isReadByOther,
                        chatType = chatType, currentUid = currentUid, hapticEnabled = hapticEnabled,
                        onTap = { url -> onMediaTap(url, MessageType.IMAGE) },
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd,
                        onReact = onReact, onReplyClick = onReplyClick, onOpenComments = onOpenComments, chat = chat,
                        onCancelUpload = onCancelUpload,
                        onDoubleTap = onDoubleTapLike,
                    )
                }
                else -> {
                    TextBubble(
                        message = message, isMine = isMine, currentUid = currentUid,
                        chatType = chatType, hapticEnabled = hapticEnabled, showSenderName = showSenderName,
                        voicePlayback = voicePlayback,
                        musicPlayback = musicPlayback,
                        musicDownloadProgress = musicDownloadProgress,
                        isReadByOther = isReadByOther,
                        onPlayVoice = onPlayVoice, onSeekVoice = onSeekVoice,
                        onPlayAudio = onPlayAudio, onToggleAudioPlayback = onToggleAudioPlayback,
                        onSeekAudio = onSeekAudio, onCycleAudioSpeed = onCycleAudioSpeed,
                        onSaveTrackToLibrary = onSaveTrackToLibrary, onOpenFullscreenAudio = onOpenFullscreenAudio,
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd,
                        onReact = onReact, onReplyClick = onReplyClick, onMentionClick = onMentionClick,
                        onOpenComments = onOpenComments, chat = chat,
                        onCancelUpload = onCancelUpload,
                        onDoubleTap = onDoubleTapLike,
                    )
                }
            }

            DoubleTapHeartAnimation(
                triggerKey = heartAnimKey,
                modifier = Modifier.matchParentSize(),
                liquidEnabled = liquidEnabled
            )
        }
    }
}

@Composable
internal fun TextBubble(
    message: Message, isMine: Boolean, currentUid: String, chatType: ChatType, hapticEnabled: Boolean,
    showSenderName: Boolean, voicePlayback: VoicePlaybackState,
    musicPlayback: MusicPlayerState = MusicPlayerState(),
    musicDownloadProgress: Map<String, Float> = emptyMap(),
    isReadByOther: Boolean = false,
    onPlayVoice: (String, Int) -> Unit, onSeekVoice: (Float) -> Unit,
    onPlayAudio: ((Message) -> Unit)? = null,
    onToggleAudioPlayback: (() -> Unit)? = null,
    onSeekAudio: ((Float) -> Unit)? = null,
    onCycleAudioSpeed: (() -> Unit)? = null,
    onSaveTrackToLibrary: ((MusicTrack) -> Unit)? = null,
    onOpenFullscreenAudio: (() -> Unit)? = null,
    onLongPressStart: (Offset) -> Unit, onLongPressDrag: (Offset) -> Unit, onLongPressEnd: () -> Unit,
    onReact: (String) -> Unit, onReplyClick: (String) -> Unit, onMentionClick: (String) -> Unit,
    onOpenComments: () -> Unit = {}, chat: Chat? = null,
    onCancelUpload: ((String) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
) {
    val bubbleColor = resolveBubbleColor(isMine)
    val textColor   = resolveBubbleTextColor(isMine)
    val linkColor   = resolveLinkColor(isMine)
    val currentDoubleTap = onDoubleTap ?: { onReact("❤️") }

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        val bubbleModifier = Modifier
            .widthIn(max = 270.dp)
            .messageGestures(
                messageId = message.id,
                interactionSource = interactionSource,
                onTap = null,
                onDoubleTap = currentDoubleTap,
                hapticEnabled = hapticEnabled,
                onLongPressStart = onLongPressStart,
                onLongPressDrag = onLongPressDrag,
                onLongPressEnd = onLongPressEnd
            )

        Surface(
            modifier = bubbleModifier,
            shape = VlTheme.tokens.shapes.card,
            color = bubbleColor
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                if (showSenderName && !isMine) {
                    Text(
                        "@${message.senderUsername}",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(bottom = 2.dp),
                    )
                }

                message.replyData?.let { reply ->
                    ReplyPreview(reply = reply, isMine = isMine, onClick = { reply.id?.let { id -> onReplyClick(id) } })
                    Spacer(Modifier.height(4.dp))
                }

                message.parsedForwardFrom?.let { fwd ->
                    ForwardBanner(forwardFrom = fwd, isMine = isMine, modifier = Modifier.fillMaxWidth())
                }

                if (message.deleted) {
                    Text(stringResource(R.string.chat_message_deleted), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = textColor.copy(alpha = 0.6f))
                } else when (message.type) {
                    MessageType.TEXT  -> LinkifiedText(text = message.text ?: "", color = textColor, linkColor = linkColor, onMentionClick = onMentionClick, onDoubleTap = currentDoubleTap)
                    MessageType.VOICE -> {
                        val resolvedUrl = resolveCdnUrl(message.cdnMediaId, message.url)
                        VoiceBubble(
                            messageId = message.id,
                            url = resolvedUrl ?: message.url ?: "",
                            durationSec = message.duration ?: 0,
                            tint = textColor,
                            playback = voicePlayback,
                            uploadProgress = message.uploadProgress,
                            onPlay = onPlayVoice,
                            onSeek = onSeekVoice
                        )
                    }
                    MessageType.AUDIO -> {
                        AudioMessageBubble(
                            message = message,
                            isMine = isMine,
                            tint = textColor,
                            musicPlayback = musicPlayback,
                            uploadProgress = message.uploadProgress,
                            downloadProgress = musicDownloadProgress[message.id],
                            onPlay = { onPlayAudio?.invoke(it) },
                            onTogglePlayPause = { onToggleAudioPlayback?.invoke() },
                            onSeek = { onSeekAudio?.invoke(it) },
                            onCycleSpeed = { onCycleAudioSpeed?.invoke() },
                            onSaveToLibrary = { onSaveTrackToLibrary?.invoke(it) },
                            onOpenFullscreen = { onOpenFullscreenAudio?.invoke() },
                            onCancelUpload = onCancelUpload?.let { { it(message.id) } }
                        )
                    }
                    else -> {
                        if (!message.text.isNullOrBlank()) {
                            LinkifiedText(text = message.text, color = textColor, linkColor = linkColor, onMentionClick = onMentionClick, onDoubleTap = currentDoubleTap)
                        } else {
                            Text(
                                text = stringResource(R.string.msg_unknown_type, message.type),
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                DebugMessageBadge(
                    message = message,
                    textColor = textColor,
                    modifier = Modifier.align(Alignment.Start)
                )

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    AnimatedVisibility(visible = message.createdAt != null, enter = fadeIn(tween(300))) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (message.lastEdited != null) {
                                Text(
                                    "(изменено)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                )
                            }
                            Text(
                                message.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "",
                                style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f), fontSize = 10.sp,
                            )
                        }
                    }
                    if (isMine && !message.deleted) {
                        if (chatType == ChatType.DIRECT && message.status != SendStatus.QUEUED) {
                            AnimatedContent(
                                targetState = isReadByOther,
                                transitionSpec = { scaleIn(initialScale = 0.5f, animationSpec = spring(Spring.DampingRatioLowBouncy)) + fadeIn() togetherWith scaleOut(targetScale = 0.5f) + fadeOut() },
                                label = "read_receipt",
                            ) { read -> ReadReceipt(isRead = read) }
                        } else {
                            MessageStatusIcon(status = message.status)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = message.parsedReactions.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150)),
        ) {
            InlinedReactionRow(
                reactions = message.parsedReactions, currentUid = currentUid, isMine = isMine,
                hapticEnabled = hapticEnabled,
                onReact = onReact, onShowPicker = { /* no-op for now */ },
            )
        }

        if (chatType == ChatType.CHANNEL && !message.deleted && chat != null) {
            CommentsButton(post = message, channelAllowsComments = chat.settings.allowComments, onClick = onOpenComments)
        }
    }
}

@Composable
internal fun VideoBubble(
    message: Message, isMine: Boolean, isReadByOther: Boolean, chatType: ChatType, currentUid: String, hapticEnabled: Boolean, 
    onLongPressStart: (Offset) -> Unit, onLongPressDrag: (Offset) -> Unit, onLongPressEnd: () -> Unit,
    onMediaTap: (String, String) -> Unit,
    onReact: (String) -> Unit, onReplyClick: (String) -> Unit, onOpenComments: () -> Unit = {}, chat: Chat? = null,
    onCancelUpload: ((String) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val currentDoubleTap = onDoubleTap ?: { onReact("❤️") }
    val interactionSource = remember { MutableInteractionSource() }
    val resolvedUrl = resolveCdnUrl(message.cdnMediaId, message.url)
    val streamableUrl = remember(resolvedUrl, message.driveFileId) {
        resolvedUrl?.let { VideoCache.getStreamableVideoUrl(it, message.driveFileId) }
    }
    val cachedFile = remember(streamableUrl) {
        streamableUrl?.let { VideoCache.getCachedPath(context, it) }
    }
    val effectiveLocalFile = message.localFile ?: cachedFile
    val effectiveThumbUrl = message.thumbUrl?.takeIf { it.isNotBlank() } ?: message.previewUrl?.takeIf { it.isNotBlank() }
    val playTargetUrl = effectiveLocalFile?.let { Uri.fromFile(it).toString() } ?: streamableUrl ?: resolvedUrl

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp).messageGestures(
                messageId = message.id, interactionSource = interactionSource,
                onTap = { playTargetUrl?.let { onMediaTap(it, message.type) } },
                onDoubleTap = currentDoubleTap,
                hapticEnabled = hapticEnabled,
                onLongPressStart = onLongPressStart, onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd
            ),
            shape = VlTheme.tokens.shapes.card,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box {
                ChatVideoViewer(
                    url = streamableUrl ?: resolvedUrl,
                    thumbUrl = effectiveThumbUrl,
                    localFile = effectiveLocalFile,
                    modifier = Modifier.sizeIn(minWidth = 120.dp, minHeight = 120.dp, maxWidth = 280.dp, maxHeight = 500.dp),
                    onClick = null
                )

                Column(modifier = Modifier.matchParentSize()) {
                    message.replyData?.let { reply ->
                        ReplyPreview(reply = reply, isMine = isMine, onMedia = true, onClick = { reply.id?.let { id -> onReplyClick(id) } })
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f)))).padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            DebugMessageBadge(message = message, textColor = Color.White)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        message.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "",
                                        style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp,
                                    )
                                }
                                if (isMine) ReadReceipt(isRead = isReadByOther)
                            }
                        }
                    }
                }

                if (message.uploadProgress != null) {
                    Log.d("VlUI", "Bubble ${message.id} (type=${message.type}) progress: ${message.uploadProgress}")
                    UploadProgressOverlay(
                        progress = message.uploadProgress!!,
                        modifier = Modifier.matchParentSize(),
                        onCancel = onCancelUpload?.let { { it(message.id) } }
                    )
                }
            }
        }

        InlinedReactionRow(
            reactions = message.parsedReactions, currentUid = currentUid, isMine = isMine,
            hapticEnabled = hapticEnabled,
            onReact = onReact, onShowPicker = { /* no-op */ }
        )
    }
}

@Composable
internal fun ImageBubble(
    message: Message, isMine: Boolean, isReadByOther: Boolean, chatType: ChatType, currentUid: String, hapticEnabled: Boolean, 
    onTap: (String) -> Unit, onLongPressStart: (Offset) -> Unit, onLongPressDrag: (Offset) -> Unit, onLongPressEnd: () -> Unit,
    onReact: (String) -> Unit, onReplyClick: (String) -> Unit, onOpenComments: () -> Unit = {}, chat: Chat? = null,
    onCancelUpload: ((String) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentDoubleTap = onDoubleTap ?: { onReact("❤️") }
    val resolvedUrl = resolveCdnUrl(message.cdnMediaId, message.url)
    val modelSource = message.localFile ?: resolvedUrl

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp).messageGestures(
                messageId = message.id, interactionSource = interactionSource,
                onTap = { (resolvedUrl ?: message.localFile?.let { Uri.fromFile(it).toString() })?.let { onTap(it) } },
                onDoubleTap = currentDoubleTap,
                hapticEnabled = hapticEnabled,
                onLongPressStart = onLongPressStart, onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd
            ),
            shape = VlTheme.tokens.shapes.card,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box {
                CachedImage(
                    model = modelSource,
                    contentDescription = null,
                    modifier = Modifier.sizeIn(minWidth = 120.dp, minHeight = 120.dp, maxWidth = 280.dp, maxHeight = 500.dp),
                    contentScale = ContentScale.Crop
                )

                Column(modifier = Modifier.matchParentSize()) {
                    message.replyData?.let { reply ->
                        ReplyPreview(reply = reply, isMine = isMine, onMedia = true, onClick = { reply.id?.let { id -> onReplyClick(id) } })
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f)))).padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            DebugMessageBadge(message = message, textColor = Color.White)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        message.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "",
                                        style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp,
                                    )
                                }
                                if (isMine) ReadReceipt(isRead = isReadByOther)
                            }
                        }
                    }
                }

                if (message.uploadProgress != null) {
                    Log.d("VlUI", "Bubble ${message.id} (type=${message.type}) progress: ${message.uploadProgress}")
                    UploadProgressOverlay(
                        progress = message.uploadProgress!!,
                        modifier = Modifier.matchParentSize(),
                        onCancel = onCancelUpload?.let { { it(message.id) } }
                    )
                }
            }
        }

        InlinedReactionRow(
            reactions = message.parsedReactions, currentUid = currentUid, isMine = isMine,
            hapticEnabled = hapticEnabled,
            onReact = onReact, onShowPicker = { /* no-op */ }
        )
    }
}

@Composable
internal fun AlbumBubble(
    message: Message, isMine: Boolean, currentUid: String, chatType: ChatType, hapticEnabled: Boolean,
    onAlbumTap: (List<AlbumImage>, Int) -> Unit, onLongPressStart: (Offset) -> Unit, onLongPressDrag: (Offset) -> Unit, onLongPressEnd: () -> Unit,
    onReact: (String) -> Unit, onReplyClick: (String) -> Unit, onOpenComments: () -> Unit = {}, chat: Chat? = null,
    onCancelUpload: ((String) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentDoubleTap = onDoubleTap ?: { onReact("❤️") }
    val revealedIndices = remember { mutableStateOf(setOf<Int>()) }
    val isReadByOther = (chat?.participants?.find { it != currentUid } ?: "") in message.readBy

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp).messageGestures(
                messageId = message.id, interactionSource = interactionSource,
                onTap = null,
                onDoubleTap = currentDoubleTap,
                hapticEnabled = hapticEnabled,
                onLongPressStart = onLongPressStart, onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd
            ),
            shape = VlTheme.tokens.shapes.card,
            color = Color.Transparent // Прозрачный, так как AlbumGrid сам рисует фон/рамку если нужно
        ) {
            Box {
                AlbumGrid(
                    images = message.images, revealedIndices = revealedIndices.value,
                    onReveal = { idx -> revealedIndices.value = revealedIndices.value + idx },
                    onClick = { idx -> onAlbumTap(message.images, idx) },
                    onDoubleTap = currentDoubleTap,
                )

                Column(modifier = Modifier.matchParentSize()) {
                    message.replyData?.let { reply ->
                        ReplyPreview(reply = reply, isMine = isMine, onMedia = true, onClick = { reply.id?.let { id -> onReplyClick(id) } })
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f)))).padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            DebugMessageBadge(message = message, textColor = Color.White)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        message.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "",
                                        style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp,
                                    )
                                    if (isMine) {
                                        Spacer(Modifier.width(3.dp))
                                        ReadReceipt(isRead = isReadByOther)
                                    }
                                }
                            }
                        }
                    }
                }

                if (message.uploadProgress != null) {
                    UploadProgressOverlay(
                        progress = message.uploadProgress!!,
                        modifier = Modifier.matchParentSize(),
                        onCancel = onCancelUpload?.let { { it(message.id) } }
                    )
                }
            }
        }

        InlinedReactionRow(
            reactions = message.parsedReactions, currentUid = currentUid, isMine = isMine,
            hapticEnabled = hapticEnabled,
            onReact = onReact, onShowPicker = { /* no-op */ }
        )
    }
}

@Composable
internal fun StickerBubble(
    message: Message, isMine: Boolean, currentUid: String,
    hapticEnabled: Boolean,
    onLongPressStart: (Offset) -> Unit, onLongPressDrag: (Offset) -> Unit, onLongPressEnd: () -> Unit,
    onReact: (String) -> Unit,
    onStickerClick: ((packId: String?, stickerId: String?) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentDoubleTap = onDoubleTap ?: { onReact("❤️") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        val isLegacySticker = message.url?.contains("api.visorlink.org") == true || (message.url?.contains("/f/") == true && message.url.contains("googleusercontent.com") != true && message.url.contains("firebasestorage") != true)
        Box(
            modifier = Modifier.size(160.dp).messageGestures(
                messageId = message.id, interactionSource = interactionSource,
                onTap = { onStickerClick?.invoke(message.packId, message.stickerId) },
                onDoubleTap = currentDoubleTap,
                hapticEnabled = hapticEnabled,
                onLongPressStart = onLongPressStart, onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd
            ),
            contentAlignment = Alignment.Center
        ) {
            if (isLegacySticker || message.url.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(120.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.msg_sticker_unavailable),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                VlAnimatedMedia(
                    url = message.url,
                    contentDescription = message.packName ?: message.text,
                    isLottie = message.isLottieMedia,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        DebugMessageBadge(
            message = message,
            textColor = resolveBubbleTextColor(isMine),
            modifier = Modifier.align(if (isMine) Alignment.End else Alignment.Start)
        )

        InlinedReactionRow(
            reactions = message.parsedReactions, currentUid = currentUid, isMine = isMine,
            hapticEnabled = hapticEnabled,
            onReact = onReact, onShowPicker = { /* no-op */ }
        )
    }
}

@Composable
internal fun InlinedReactionRow(
    reactions: List<Reaction>, currentUid: String, isMine: Boolean, hapticEnabled: Boolean, onReact: (String) -> Unit, onShowPicker: () -> Unit,
) {
    val haptic = rememberHaptic()

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        reactions.forEach { reaction ->
            key(reaction.emoji) {
                val iReacted = currentUid in reaction.uids
                val interactionSource = remember { MutableInteractionSource() }
                val cs = MaterialTheme.colorScheme
                val tokens = VlTheme.tokens
                // §7 чип: raised в покое → inset + плотная заливка при выборе, без glow.
                val chipShape = VlTheme.tokens.shapes.chip

                Box(
                    modifier = Modifier
                        .then(
                            if (tokens.structure.enabled && !iReacted) Modifier.vlRaised(tokens.structure, chipShape)
                            else Modifier
                        )
                        .clip(chipShape)
                        .background(
                            when {
                                iReacted && tokens.structure.enabled -> tokens.selectionFill
                                iReacted -> cs.primary.copy(alpha = 0.2f)
                                else -> cs.surfaceVariant
                            },
                            chipShape,
                        )
                        .then(
                            if (tokens.structure.enabled && iReacted) Modifier.vlInset(tokens.structure, chipShape)
                            else Modifier
                        )
                        .clickable(interactionSource = interactionSource, indication = null) {
                            haptic.perform(HapticType.REACTION, hapticEnabled)
                            onReact(reaction.emoji)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(reaction.emoji, fontSize = 14.sp)
                        Text(
                            // Счётчик — data-роль (§1.5): цифры моноширинным.
                            reaction.count.toString(),
                            style = tokens.data.dataSmall,
                            fontWeight = if (iReacted) FontWeight.Bold else FontWeight.Medium,
                            color = if (iReacted) cs.primary else cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}


@Composable
internal fun LinkifiedText(
    text: String,
    color: Color,
    linkColor: Color,
    onMentionClick: (String) -> Unit,
    onDoubleTap: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val annotatedString = remember(text, linkColor) {
        org.visorlink.app.utils.MarkdownTextParser.parse(text, linkColor)
    }

    val hasInteractiveAnnotations = remember(annotatedString) {
        annotatedString.getStringAnnotations("URL", 0, annotatedString.length).isNotEmpty() ||
            annotatedString.getStringAnnotations("MENTION", 0, annotatedString.length).isNotEmpty()
    }

    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnMentionClick by rememberUpdatedState(onMentionClick)

    val textModifier = if (hasInteractiveAnnotations) {
        Modifier.pointerInput(annotatedString, currentOnDoubleTap != null) {
            detectTapGestures(
                onDoubleTap = if (currentOnDoubleTap != null) {
                    { _ -> currentOnDoubleTap?.invoke() }
                } else null,
                onTap = { pos ->
                    layoutResult.value?.let { layout ->
                        if (pos.x >= 0 && pos.x <= layout.size.width && pos.y >= 0 && pos.y <= layout.size.height) {
                            val offset = layout.getOffsetForPosition(pos)
                            annotatedString.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    try { uriHandler.openUri(annotation.item) } catch (_: Exception) {}
                                    return@detectTapGestures
                                }
                            annotatedString.getStringAnnotations("MENTION", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    currentOnMentionClick(annotation.item.removePrefix("@"))
                                    return@detectTapGestures
                                }
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Text(
        text = annotatedString,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { layoutResult.value = it },
        modifier = textModifier,
    )
}
