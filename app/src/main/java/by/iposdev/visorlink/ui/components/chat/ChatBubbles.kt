package by.iposdev.visorlink.ui.components.chat

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.components.VlSurface
import by.iposdev.visorlink.ui.components.CachedImage
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.MusicPlayerState
import by.iposdev.visorlink.utils.VoicePlaybackState
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.launch
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlInset
import by.iposdev.visorlink.ui.theme.vlRaised
import by.iposdev.visorlink.ui.theme.LocalShowDebugIds
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
    onLongPressStart: (Offset) -> Unit,
    onLongPressDrag: (Offset) -> Unit,
    onLongPressEnd: () -> Unit
) = composed {
    var globalPos by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    this
        .onGloballyPositioned { globalPos = it.positionInWindow() }
        .pointerInput(messageId + "_tap") {
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
                onTap = { onTap?.invoke() }
            )
        }
        .pointerInput(messageId + "_drag") {
            detectDragGesturesAfterLongPress(
                onDragStart = { local -> onLongPressStart(globalPos + local) },
                onDrag = { change, amount ->
                    change.consume()
                    onLongPressDrag(amount)
                },
                onDragEnd = onLongPressEnd,
                onDragCancel = onLongPressEnd
            )
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
    onClick: (Int) -> Unit
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
    modifier: Modifier = Modifier,
    useCardShape: Boolean = true
) {
    val resolvedUrl = resolveCdnUrl(image.cdnMediaId, image.url) ?: image.url
    val showBlur = image.spoiler && !isRevealed

    Box(modifier
        .then(if (useCardShape) Modifier.clip(VlTheme.tokens.shapes.card) else Modifier)
        .clickable { if (showBlur) onReveal() else onClick() }
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
) {
    val haptic = rememberHaptic()
    val isReadByOther = otherUid in message.readBy

    val uploadProgressModifier = if (message.uploadProgress != null) {
        Modifier.alpha(0.6f)
    } else Modifier

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(modifier = uploadProgressModifier) {
            when {
                message.type == MessageType.GIFT && !message.deleted -> {
                    GiftMessage(message = message, chatId = chat?.id ?: "")
                    return@Box
                }
                (message.type == MessageType.VIDEO || message.type == MessageType.GIF) && !message.deleted -> {
                    VideoBubble(
                        message = message, isMine = isMine, isReadByOther = isReadByOther,
                        chatType = chatType, currentUid = currentUid, hapticEnabled = hapticEnabled,
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd,
                        onMediaTap = onMediaTap,
                        onReact = onReact, onReplyClick = onReplyClick, onOpenComments = onOpenComments, chat = chat,
                        onCancelUpload = onCancelUpload
                    )
                    return@Box
                }
                message.type.equals(MessageType.ALBUM, ignoreCase = true) && !message.deleted -> {
                    AlbumBubble(
                        message = message, isMine = isMine, currentUid = currentUid,
                        chatType = chatType, hapticEnabled = hapticEnabled,
                        onAlbumTap = onAlbumTap,
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd,
                        onReact = onReact, onReplyClick = onReplyClick, onOpenComments = onOpenComments, chat = chat,
                        onCancelUpload = onCancelUpload
                    )
                    return@Box
                }
                message.type == MessageType.IMAGE && !message.deleted -> {
                    ImageBubble(
                        message = message, isMine = isMine, isReadByOther = isReadByOther,
                        chatType = chatType, currentUid = currentUid, hapticEnabled = hapticEnabled,
                        onTap = { url -> onMediaTap(url, MessageType.IMAGE) },
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd,
                        onReact = onReact, onReplyClick = onReplyClick, onOpenComments = onOpenComments, chat = chat,
                        onCancelUpload = onCancelUpload
                    )
                    return@Box
                }
                message.type == MessageType.STICKER && !message.deleted -> {
                    StickerBubble(
                        message = message, isMine = isMine, currentUid = currentUid,
                        hapticEnabled = hapticEnabled,
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd, onReact = onReact,
                        onStickerClick = onStickerClick,
                    )
                    return@Box
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
                        onCancelUpload = onCancelUpload
                    )
                }
            }
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
) {
    val bubbleColor = resolveBubbleColor(isMine)
    val textColor   = resolveBubbleTextColor(isMine)
    val linkColor   = resolveLinkColor(isMine)

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
                    MessageType.TEXT  -> LinkifiedText(text = message.text ?: "", color = textColor, linkColor = linkColor, onMentionClick = onMentionClick)
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val resolvedUrl = resolveCdnUrl(message.cdnMediaId, message.url)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp).messageGestures(
                messageId = message.id, interactionSource = interactionSource,
                onTap = { resolvedUrl?.let { onMediaTap(it, message.type) } },
                onLongPressStart = onLongPressStart, onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd
            ),
            shape = VlTheme.tokens.shapes.card,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box {
                CdnMediaViewer(
                    mediaId = message.cdnMediaId,
                    type = message.type,
                    localFile = message.localFile,
                    thumbUrl = message.thumbUrl,
                    modifier = Modifier.sizeIn(minWidth = 120.dp, minHeight = 120.dp, maxWidth = 280.dp, maxHeight = 500.dp),
                    onClick = { (resolvedUrl ?: message.localFile?.let { Uri.fromFile(it).toString() })?.let { onMediaTap(it, message.type) } }
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
) {
    val interactionSource = remember { MutableInteractionSource() }
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val revealedIndices = remember { mutableStateOf(setOf<Int>()) }
    val isReadByOther = (chat?.participants?.find { it != currentUid } ?: "") in message.readBy

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp).messageGestures(
                messageId = message.id, interactionSource = interactionSource,
                onTap = null, onLongPressStart = onLongPressStart, onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd
            ),
            shape = VlTheme.tokens.shapes.card,
            color = Color.Transparent // Прозрачный, так как AlbumGrid сам рисует фон/рамку если нужно
        ) {
            Box {
                AlbumGrid(
                    images = message.images, revealedIndices = revealedIndices.value,
                    onReveal = { idx -> revealedIndices.value = revealedIndices.value + idx },
                    onClick = { idx -> onAlbumTap(message.images, idx) }
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
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier.size(160.dp).messageGestures(
                messageId = message.id, interactionSource = interactionSource,
                onTap = { onStickerClick?.invoke(message.packId, message.stickerId) },
                onLongPressStart = onLongPressStart, onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd
            ),
            contentAlignment = Alignment.Center
        ) {
            CachedImage(model = message.url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
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
    text: String, color: Color, linkColor: Color, onMentionClick: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val (urlList, mentionList) = remember(text) {
        val urls = mutableListOf<Triple<String, Int, Int>>()
        val urlMatcher = Patterns.WEB_URL.matcher(text)
        while (urlMatcher.find()) {
            var url = urlMatcher.group() ?: continue
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            urls.add(Triple(url, urlMatcher.start(), urlMatcher.end()))
        }
        val mentions = mutableListOf<Triple<String, Int, Int>>()
        val mentionRegex = Regex("(?<!\\w)@[a-zA-Z0-9_]+")
        mentionRegex.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            val isInsideUrl = urls.any { start >= it.second && end <= it.third }
            if (!isInsideUrl) mentions.add(Triple(match.value, start, end))
        }
        Pair(urls, mentions)
    }

    val annotatedString = remember(text, color, linkColor) {
        buildAnnotatedString {
            append(text)
            urlList.forEach { (url, start, end) ->
                addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, end)
                addStringAnnotation("URL", url, start, end)
            }
            mentionList.forEach { (mention, start, end) ->
                addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold), start, end)
                addStringAnnotation("MENTION", mention, start, end)
            }
        }
    }

    Text(
        text = annotatedString, color = color, style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { layoutResult.value = it },
        modifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var upEvent: PointerInputChange? = null
                var isTap = true

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (change.isConsumed) { isTap = false }
                    if (!change.pressed) { upEvent = change; break }
                }

                if (isTap && upEvent != null) {
                    val pos = upEvent.position
                    layoutResult.value?.let { layout ->
                        if (pos.x >= 0 && pos.x <= layout.size.width && pos.y >= 0 && pos.y <= layout.size.height) {
                            val offset = layout.getOffsetForPosition(pos)
                            annotatedString.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    try { uriHandler.openUri(annotation.item) } catch (_: Exception) {}
                                    upEvent.consume()
                                    return@awaitEachGesture
                                }
                            annotatedString.getStringAnnotations("MENTION", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    onMentionClick(annotation.item.removePrefix("@"))
                                    upEvent.consume()
                                    return@awaitEachGesture
                                }
                        }
                    }
                }
            }
        },
    )
}
