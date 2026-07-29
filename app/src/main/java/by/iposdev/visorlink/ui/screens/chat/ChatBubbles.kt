package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.screens.stickers.AddStickerPackBanner
import by.iposdev.visorlink.ui.theme.ExthruSenderNameStyle
import by.iposdev.visorlink.ui.theme.bubbleInnerHighlight
import by.iposdev.visorlink.ui.theme.exthruRaisedShadow
import by.iposdev.visorlink.ui.theme.exthruSmallRaisedShadow
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.VoicePlaybackState
import by.iposdev.visorlink.utils.rememberHaptic
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════════════════════
//  Кастомный модификатор жестов (Идеальная синхронизация Tap + Drag)
// ════════════════════════════════════════════════════════════════════════════════

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

// ════════════════════════════════════════════════════════════════════════════════
//  MessageBubble — роутер
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun MessageBubble(
    message: Message,
    isMine: Boolean,
    otherUid: String,
    currentUid: String,
    chatType: ChatType,
    hapticEnabled: Boolean,
    showSenderName: Boolean,
    voicePlayback: VoicePlaybackState,
    isOneUi: Boolean = false,
    isExthru: Boolean = false,
    isDark: Boolean = false,
    hasWallpaper: Boolean = false,
    onPlayVoice: (url: String, durationSec: Int) -> Unit,
    onSeekVoice: (Float) -> Unit,
    onLongPressStart: (Offset) -> Unit,
    onLongPressDrag: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onImageTap: (url: String) -> Unit,
    onAlbumTap: (images: List<AlbumImage>, startIndex: Int) -> Unit,
    onReact: (String) -> Unit,
    onReplyClick: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    onOpenComments: () -> Unit = {},
    chat: Chat? = null,
) {
    val haptic = rememberHaptic()
    val isReadByOther = otherUid in message.readBy
    var showPackBanner by remember(message.id) { mutableStateOf(false) }

    // Визуальное оформление для прогресса загрузки
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
                        isOneUi = isOneUi, isExthru = isExthru, isDark = isDark, hasWallpaper = hasWallpaper,
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd,
                        onReact = onReact, onReplyClick = onReplyClick, onOpenComments = onOpenComments, chat = chat
                    )
                    return@Box
                }
                message.type == MessageType.ALBUM && !message.deleted -> {
                    AlbumBubble(
                        message = message, isMine = isMine, currentUid = currentUid,
                        chatType = chatType, isOneUi = isOneUi, isExthru = isExthru, isDark = isDark,
                        onAlbumTap = onAlbumTap,
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd,
                        onReact = onReact, onReplyClick = onReplyClick, onOpenComments = onOpenComments, chat = chat,
                    )
                    return@Box
                }
                message.type == MessageType.IMAGE && !message.deleted -> {
                    ImageBubble(
                        message = message, isMine = isMine, isReadByOther = isReadByOther,
                        chatType = chatType, currentUid = currentUid, hapticEnabled = hapticEnabled,
                        isOneUi = isOneUi, isExthru = isExthru, isDark = isDark, hasWallpaper = hasWallpaper,
                        onTap = { message.url?.let { onImageTap(it) } },
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd,
                        onReact = onReact, onReplyClick = onReplyClick, onOpenComments = onOpenComments, chat = chat,
                    )
                    return@Box
                }
                message.type == MessageType.STICKER && !message.deleted -> {
                    StickerBubble(
                        message = message, isMine = isMine, currentUid = currentUid,
                        isOneUi = isOneUi, isExthru = isExthru, isDark = isDark,
                        hapticEnabled = hapticEnabled, showPackBanner = showPackBanner,
                        onTogglePackBanner = { showPackBanner = !showPackBanner },
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd, onReact = onReact,
                    )
                    return@Box
                }
                else -> {
                    TextBubble(
                        message = message, isMine = isMine, currentUid = currentUid,
                        chatType = chatType, hapticEnabled = hapticEnabled, showSenderName = showSenderName,
                        voicePlayback = voicePlayback, isOneUi = isOneUi, isExthru = isExthru, isDark = isDark,
                        isReadByOther = isReadByOther, hasWallpaper = hasWallpaper,
                        onPlayVoice = onPlayVoice, onSeekVoice = onSeekVoice,
                        onLongPressStart = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPressStart(it) },
                        onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd,
                        onReact = onReact, onReplyClick = onReplyClick, onMentionClick = onMentionClick,
                        onOpenComments = onOpenComments, chat = chat,
                    )
                }
            }
        }

        // Оверлей загрузки
        if (message.uploadProgress != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.3f), resolveBubbleShape(isMine, isOneUi)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { message.uploadProgress ?: 0f },
                    modifier = Modifier.size(36.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  TextBubble
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun TextBubble(
    message: Message, isMine: Boolean, currentUid: String, chatType: ChatType, hapticEnabled: Boolean,
    showSenderName: Boolean, voicePlayback: VoicePlaybackState, isOneUi: Boolean = false,
    isExthru: Boolean = false, isDark: Boolean = false, isReadByOther: Boolean = false,
    hasWallpaper: Boolean = false, onPlayVoice: (String, Int) -> Unit, onSeekVoice: (Float) -> Unit,
    onLongPressStart: (Offset) -> Unit, onLongPressDrag: (Offset) -> Unit, onLongPressEnd: () -> Unit,
    onReact: (String) -> Unit, onReplyClick: (String) -> Unit, onMentionClick: (String) -> Unit,
    onOpenComments: () -> Unit = {}, chat: Chat? = null,
) {
    val bubbleColor = resolveBubbleColor(isMine, isOneUi, isExthru, isDark)
    val textColor   = resolveBubbleTextColor(isMine, isOneUi, isExthru, isDark)
    val linkColor   = resolveLinkColor(isMine, isOneUi, isExthru, isDark)
    val bubbleShape = resolveBubbleShape(isMine, isOneUi)

    val borderColor = ExthruChat.shadowLight(isDark).copy(
        alpha = when {
            hasWallpaper -> 0.30f
            isDark       -> 0.08f
            else         -> 0.45f
        }
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "bubble_scale"
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        val shadowMod = if (isExthru && !hasWallpaper) {
            if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 18.dp) else Modifier.exthruRaisedShadow(isDark)
        } else Modifier

        val bubbleModifier = Modifier
            .widthIn(max = 270.dp)
            .scale(scale)
            .then(shadowMod)
            .then(if (isExthru) Modifier.bubbleInnerHighlight(shape = bubbleShape, isDark = isDark) else Modifier)
            .then(if (isExthru) Modifier.border(0.5.dp, if (isPressed) Color.Transparent else borderColor, bubbleShape) else Modifier)
            .clip(bubbleShape)
            .messageGestures(
                messageId = message.id,
                interactionSource = interactionSource,
                onTap = null,
                onLongPressStart = onLongPressStart,
                onLongPressDrag = onLongPressDrag,
                onLongPressEnd = onLongPressEnd
            )

        Box(
            modifier = bubbleModifier.background(if (hasWallpaper && isExthru) bubbleColor.copy(alpha = if (isDark) 0.85f else 0.75f) else bubbleColor)
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                if (showSenderName && !isMine) {
                    val senderColor = if (isExthru) ExthruChat.Accent else MaterialTheme.colorScheme.primary
                    Text(
                        "@${message.senderUsername}",
                        style      = if (isExthru) ExthruSenderNameStyle else MaterialTheme.typography.labelSmall,
                        color      = senderColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(bottom = 2.dp),
                    )
                }

                message.replyData?.let { reply ->
                    ReplyPreview(reply = reply, isMine = isMine, isExthru = isExthru, isDark = isDark, onClick = { reply.id?.let { id -> onReplyClick(id) } })
                    Spacer(Modifier.height(4.dp))
                }

                if (message.tg_forwarded == true) {
                    TelegramForwardBanner(message = message)
                } else {
                    message.parsedForwardFrom?.let { fwd ->
                        ForwardBanner(forwardFrom = fwd, isMine = isMine, isExthru = isExthru, isDark = isDark)
                    }
                }

                if (message.deleted) {
                    Text(stringResource(R.string.chat_message_deleted), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = textColor.copy(alpha = 0.6f))
                } else when (message.type) {
                    MessageType.TEXT  -> LinkifiedText(text = message.text ?: "", color = textColor, linkColor = linkColor, onMentionClick = onMentionClick)
                    MessageType.VOICE -> VoiceBubble(messageId = message.id, url = message.url ?: "", durationSec = message.duration ?: 0, tint = textColor, playback = voicePlayback, onPlay = onPlayVoice, onSeek = onSeekVoice)
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    androidx.compose.animation.AnimatedVisibility(visible = message.createdAt != null, enter = fadeIn(tween(300))) {
                        Text(
                            message.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "",
                            style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f), fontSize = 10.sp,
                        )
                    }
                    if (isMine && !message.deleted && chatType == ChatType.DIRECT) {
                        AnimatedContent(
                            targetState  = isReadByOther,
                            transitionSpec = { scaleIn(initialScale = 0.5f, animationSpec = spring(Spring.DampingRatioLowBouncy)) + fadeIn() togetherWith scaleOut(targetScale = 0.5f) + fadeOut() },
                            label = "read_receipt",
                        ) { read -> ReadReceipt(isRead = read, isExthru = isExthru, isDark = isDark) }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = message.parsedReactions.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150)),
        ) {
            InlinedReactionRow(
                reactions = message.parsedReactions, currentUid = currentUid, isMine = isMine,
                isOneUi = isOneUi, isExthru = isExthru, isDark = isDark, hapticEnabled = hapticEnabled,
                onReact = onReact, onShowPicker = { /* no-op for now */ },
            )
        }

        if (chatType == ChatType.CHANNEL && !message.deleted && chat != null) {
            CommentsButton(post = message, channelAllowsComments = chat.settings.allowComments, onClick = onOpenComments)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Video / GIF Bubble
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun VideoBubble(
    message: Message, isMine: Boolean, isReadByOther: Boolean, chatType: ChatType, currentUid: String, hapticEnabled: Boolean, isOneUi: Boolean = false, isExthru: Boolean = false, isDark: Boolean = false, hasWallpaper: Boolean = false,
    onLongPressStart: (Offset) -> Unit, onLongPressDrag: (Offset) -> Unit, onLongPressEnd: () -> Unit,
    onReact: (String) -> Unit, onReplyClick: (String) -> Unit, onOpenComments: () -> Unit = {}, chat: Chat? = null,
) {
    val imageShape = if (isMine) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    else RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, spring(dampingRatio = 0.5f), label = "video_scale")

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        val containerModifier = if (isExthru) {
            val shadow = if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 18.dp) else Modifier.exthruRaisedShadow(isDark)
            Modifier.widthIn(min = 160.dp, max = 260.dp).scale(scale).then(if (!hasWallpaper) shadow else Modifier).clip(imageShape)
        } else {
            Modifier.widthIn(min = 160.dp, max = 260.dp).scale(scale).clip(imageShape)
        }

        Box(
            modifier = containerModifier.messageGestures(
                messageId = message.id,
                interactionSource = interactionSource,
                onLongPressStart = onLongPressStart,
                onLongPressDrag = onLongPressDrag,
                onLongPressEnd = onLongPressEnd
            ),
        ) {
            // Подключаем CdnMediaViewer
            CdnMediaViewer(
                mediaId = message.cdnMediaId,
                type = message.type,
                localFile = message.localFile
            )

            Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)) {
                if (message.tg_forwarded == true) {
                    TelegramForwardBanner(message = message)
                } else {
                    message.parsedForwardFrom?.let { fwd ->
                        ForwardBanner(forwardFrom = fwd, isMine = isMine, isExthru = isExthru, isDark = isDark)
                    }
                }

                message.replyData?.let { reply ->
                    Box(
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).clickable { reply.id?.let { id -> onReplyClick(id) } }.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(28.dp).background(Color.White, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text("@${reply.senderUsername}", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(reply.text ?: "Медиа", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(message.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "", style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp)
                    if (isMine && chatType == ChatType.DIRECT) {
                        Icon(imageVector = if (isReadByOther) Icons.Default.DoneAll else Icons.Default.Done, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (isReadByOther) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = message.parsedReactions.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150)),
        ) {
            InlinedReactionRow(
                reactions = message.parsedReactions, currentUid = currentUid, isMine = isMine,
                isOneUi = isOneUi, isExthru = isExthru, isDark = isDark, hapticEnabled = hapticEnabled,
                onReact = onReact, onShowPicker = { },
            )
        }

        if (chatType == ChatType.CHANNEL && !message.deleted && chat != null) {
            CommentsButton(post = message, channelAllowsComments = chat.settings.allowComments, onClick = onOpenComments)
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
//  StickerBubble
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun StickerBubble(
    message: Message, isMine: Boolean, currentUid: String, isOneUi: Boolean = false, isExthru: Boolean = false, isDark: Boolean = false, hapticEnabled: Boolean, showPackBanner: Boolean, onTogglePackBanner: () -> Unit,
    onLongPressStart: (Offset) -> Unit, onLongPressDrag: (Offset) -> Unit, onLongPressEnd: () -> Unit, onReact: (String) -> Unit,
) {
    val timeColor = if (isExthru) ExthruChat.textHint(isDark) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, spring(dampingRatio = 0.5f), label = "sticker_scale")

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .messageGestures(
                    messageId = message.id,
                    interactionSource = interactionSource,
                    onTap = { if (!message.packId.isNullOrBlank()) onTogglePackBanner() },
                    onLongPressStart = onLongPressStart,
                    onLongPressDrag = onLongPressDrag,
                    onLongPressEnd = onLongPressEnd
                )
        ) {
            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                message.replyData?.let { reply ->
                    ReplyPreview(reply = reply, isMine = isMine, isExthru = isExthru, isDark = isDark, onClick = { })
                    Spacer(Modifier.height(4.dp))
                }

                if (message.tg_forwarded == true) {
                    TelegramForwardBanner(message = message)
                } else {
                    message.parsedForwardFrom?.let { fwd ->
                        ForwardBanner(forwardFrom = fwd, isMine = isMine, isExthru = isExthru, isDark = isDark)
                    }
                }

                AsyncImage(model = message.url, contentDescription = null, modifier = Modifier.size(130.dp))
                Text(
                    message.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "",
                    style = MaterialTheme.typography.labelSmall, color = timeColor, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp),
                )
                if (!message.packId.isNullOrBlank() && showPackBanner) {
                    AddStickerPackBanner(packId = message.packId, packName = message.packName ?: "", packEmoji = message.packEmoji ?: "🎭")
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = message.parsedReactions.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150)),
        ) {
            InlinedReactionRow(
                reactions = message.parsedReactions, currentUid = currentUid, isMine = isMine,
                isOneUi = isOneUi, isExthru = isExthru, isDark = isDark, hapticEnabled = hapticEnabled,
                onReact = onReact, onShowPicker = { },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  ImageBubble
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ImageBubble(
    message: Message, isMine: Boolean, isReadByOther: Boolean, chatType: ChatType, currentUid: String, hapticEnabled: Boolean, isOneUi: Boolean = false, isExthru: Boolean = false, isDark: Boolean = false, hasWallpaper: Boolean = false, onTap: () -> Unit,
    onLongPressStart: (Offset) -> Unit, onLongPressDrag: (Offset) -> Unit, onLongPressEnd: () -> Unit,
    onReact: (String) -> Unit, onReplyClick: (String) -> Unit, onOpenComments: () -> Unit = {}, chat: Chat? = null,
) {
    val imageShape = if (isMine) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    else RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, spring(dampingRatio = 0.5f), label = "image_scale")

    val isSpoiler = message.spoiler == true
    var spoilerRevealed by remember(message.id) { mutableStateOf(false) }
    val blurRadius by animateDpAsState(targetValue = if (isSpoiler && !spoilerRevealed) 20.dp else 0.dp, animationSpec = tween(300), label = "spoiler_blur")

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        val containerModifier = if (isExthru) {
            val shadow = if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 18.dp) else Modifier.exthruRaisedShadow(isDark)
            Modifier.widthIn(min = 160.dp, max = 260.dp).scale(scale).then(if (!hasWallpaper) shadow else Modifier).clip(imageShape)
        } else {
            Modifier.widthIn(min = 160.dp, max = 260.dp).scale(scale).clip(imageShape)
        }

        Box(
            modifier = containerModifier.messageGestures(
                messageId = message.id,
                interactionSource = interactionSource,
                onTap = { if (isSpoiler && !spoilerRevealed) spoilerRevealed = true else onTap() },
                onLongPressStart = onLongPressStart,
                onLongPressDrag = onLongPressDrag,
                onLongPressEnd = onLongPressEnd
            ),
        ) {
            if (message.localBytes != null) {
                AsyncImage(
                    model = message.localBytes, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp).then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
                )
            } else if (message.localFile != null) {
                AsyncImage(
                    model = message.localFile, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp).then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
                )
            } else {
                AsyncImage(
                    model = message.url, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp).then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
                )
            }

            val overlayAlpha by animateFloatAsState(targetValue = if (isSpoiler && !spoilerRevealed) 1f else 0f, animationSpec = tween(300), label = "spoiler_alpha")
            if (overlayAlpha > 0f) {
                Box(
                    modifier = Modifier.matchParentSize().alpha(overlayAlpha).background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.tap_to_reveal), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)) {
                if (message.tg_forwarded == true) {
                    TelegramForwardBanner(message = message)
                } else {
                    message.parsedForwardFrom?.let { fwd ->
                        ForwardBanner(forwardFrom = fwd, isMine = isMine, isExthru = isExthru, isDark = isDark)
                    }
                }

                message.replyData?.let { reply ->
                    Box(
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).clickable { reply.id?.let { id -> onReplyClick(id) } }.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(28.dp).background(Color.White, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text("@${reply.senderUsername}", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(reply.text ?: stringResource(R.string.photo), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(message.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "", style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 10.sp)
                    if (isMine && chatType == ChatType.DIRECT) {
                        Icon(imageVector = if (isReadByOther) Icons.Default.DoneAll else Icons.Default.Done, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (isReadByOther) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = message.parsedReactions.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150)),
        ) {
            InlinedReactionRow(
                reactions = message.parsedReactions, currentUid = currentUid, isMine = isMine,
                isOneUi = isOneUi, isExthru = isExthru, isDark = isDark, hapticEnabled = hapticEnabled,
                onReact = onReact, onShowPicker = { },
            )
        }

        if (chatType == ChatType.CHANNEL && !message.deleted && chat != null) {
            CommentsButton(post = message, channelAllowsComments = chat.settings.allowComments, onClick = onOpenComments)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  AlbumBubble
// ════════════════════════════════════════════════════════════════════════════════

@Composable
fun AlbumBubble(
    message: Message, isMine: Boolean, currentUid: String, chatType: ChatType, isOneUi: Boolean = false, isDark: Boolean = false, isExthru: Boolean = false, onAlbumTap: (List<AlbumImage>, Int) -> Unit,
    onLongPressStart: (Offset) -> Unit, onLongPressDrag: (Offset) -> Unit, onLongPressEnd: () -> Unit,
    onReact: (String) -> Unit, onReplyClick: (String) -> Unit, onOpenComments: () -> Unit = {}, chat: Chat? = null
) {
    val images = message.images
    if (images.isEmpty()) return

    val revealedIndices = remember(message.id) { mutableStateOf(setOf<Int>()) }

    val bubbleColor = resolveBubbleColor(isMine, isOneUi, isExthru, isDark)
    val textColor   = resolveBubbleTextColor(isMine, isOneUi, isExthru, isDark)
    val bubbleShape = resolveBubbleShape(isMine, isOneUi)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, spring(dampingRatio = 0.5f), label = "album_scale")

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        val shadowMod = if (isExthru) {
            if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 18.dp) else Modifier.exthruRaisedShadow(isDark)
        } else Modifier

        val bubbleModifier = Modifier
            .widthIn(max = 280.dp)
            .scale(scale)
            .then(shadowMod)
            .then(if (isExthru) Modifier.bubbleInnerHighlight(shape = bubbleShape, isDark = isDark) else Modifier)
            .clip(bubbleShape)
            .messageGestures(
                messageId = message.id,
                interactionSource = interactionSource,
                onTap = null,
                onLongPressStart = onLongPressStart,
                onLongPressDrag = onLongPressDrag,
                onLongPressEnd = onLongPressEnd
            )

        Box(modifier = bubbleModifier.background(bubbleColor)) {
            Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                message.replyData?.let { reply ->
                    Box(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp)) {
                        ReplyPreview(reply = reply, isMine = isMine, isExthru = isExthru, isDark = isDark, onClick = { reply.id?.let { id -> onReplyClick(id) } })
                    }
                    Spacer(Modifier.height(4.dp))
                }

                if (message.tg_forwarded == true) {
                    TelegramForwardBanner(message = message)
                } else {
                    message.parsedForwardFrom?.let { fwd ->
                        ForwardBanner(forwardFrom = fwd, isMine = isMine, isExthru = isExthru, isDark = isDark)
                    }
                }

                AlbumGrid(
                    images = images, revealedIndices = revealedIndices.value,
                    onReveal = { idx -> revealedIndices.value = revealedIndices.value + idx },
                    onTap = { idx -> onAlbumTap(images, idx) }
                )

                if (!message.caption.isNullOrBlank()) {
                    Text(
                        text = message.caption, color = textColor, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                        maxLines = 3, overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.End).padding(end = 6.dp, bottom = 2.dp, top = if (message.caption.isNullOrBlank()) 4.dp else 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        message.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "",
                        style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f), fontSize = 10.sp
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = message.parsedReactions.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150))
        ) {
            InlinedReactionRow(
                reactions = message.parsedReactions, currentUid = currentUid, isMine = isMine,
                isOneUi = isOneUi, isDark = isDark, hapticEnabled = true, onReact = onReact, onShowPicker = { }, isExthru = isExthru
            )
        }

        if (chatType == ChatType.CHANNEL && !message.deleted && chat != null) {
            CommentsButton(post = message, channelAllowsComments = chat.settings.allowComments, onClick = onOpenComments)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Вспомогательные визуальные модули
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun AlbumGrid(images: List<AlbumImage>, revealedIndices: Set<Int>, onReveal: (Int) -> Unit, onTap: (Int) -> Unit) {
    val gap = 2.dp
    val maxWidth = 272.dp

    when (images.size) {
        1 -> AlbumCell(image = images[0], revealed = 0 in revealedIndices, modifier = Modifier.width(maxWidth).height(220.dp), onTap = { onTap(0) }, onReveal = { onReveal(0) })
        2 -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            images.forEachIndexed { i, img ->
                AlbumCell(image = img, revealed = i in revealedIndices, modifier = Modifier.width((maxWidth - gap) / 2).height(160.dp), onTap = { onTap(i) }, onReveal = { onReveal(i) })
            }
        }
        3 -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            AlbumCell(image = images[0], revealed = 0 in revealedIndices, modifier = Modifier.width((maxWidth - gap) / 2).height(200.dp), onTap = { onTap(0) }, onReveal = { onReveal(0) })
            Column(modifier = Modifier.width((maxWidth - gap) / 2), verticalArrangement = Arrangement.spacedBy(gap)) {
                for (i in 1..2) AlbumCell(image = images[i], revealed = i in revealedIndices, modifier = Modifier.fillMaxWidth().height((200.dp - gap) / 2), onTap = { onTap(i) }, onReveal = { onReveal(i) })
            }
        }
        4 -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in 0..1) {
                    val i = row * 2 + col
                    AlbumCell(image = images[i], revealed = i in revealedIndices, modifier = Modifier.width((maxWidth - gap) / 2).height(130.dp), onTap = { onTap(i) }, onReveal = { onReveal(i) })
                }
            }
        }
        else -> {
            val visibleCount = minOf(images.size, 6)
            val extraCount = images.size - visibleCount
            val shown = images.take(visibleCount)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                shown.chunked(3).forEachIndexed { rowIdx, rowImages ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        rowImages.forEachIndexed { colIdx, img ->
                            val globalIdx = rowIdx * 3 + colIdx
                            val isLast = rowIdx == 1 && colIdx == rowImages.size - 1 && extraCount > 0
                            Box(modifier = Modifier.width((maxWidth - gap * 2) / 3).height(110.dp)) {
                                AlbumCell(image = img, revealed = globalIdx in revealedIndices, modifier = Modifier.fillMaxSize(), onTap = { onTap(globalIdx) }, onReveal = { onReveal(globalIdx) })
                                if (isLast && extraCount > 0) {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { onTap(globalIdx) }, contentAlignment = Alignment.Center) {
                                        Text("+$extraCount", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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

@Composable
private fun AlbumCell(image: AlbumImage, revealed: Boolean, modifier: Modifier, onTap: () -> Unit, onReveal: () -> Unit) {
    val isSpoiler = image.spoiler && !revealed
    val blurRadius by animateDpAsState(targetValue = if (isSpoiler) 10.dp else 0.dp, animationSpec = tween(300), label = "cell_blur")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = { if (isSpoiler) onReveal() else onTap() })
    ) {
        AsyncImage(model = image.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier))
        androidx.compose.animation.AnimatedVisibility(visible = isSpoiler, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🙈", fontSize = 20.sp)
                    Text("SPOILER", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        if (isSpoiler) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(4.dp).background(Color(0xFF1259C3).copy(alpha = 0.85f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                Text("Spoiler", color = Color.White, fontSize = 8.sp)
            }
        }
    }
}

@Composable
internal fun resolveBubbleColor(isMine: Boolean, isOneUi: Boolean, isExthru: Boolean, isDark: Boolean): Color {
    return when {
        isExthru && isMine  -> ExthruChat.bubbleMine(isDark)
        isExthru && !isMine -> ExthruChat.bubbleOther(isDark)
        isOneUi && isMine && isDark   -> OneUiChat.BubbleMineDark
        isOneUi && isMine             -> OneUiChat.BubbleMine
        isOneUi && !isMine && isDark  -> OneUiChat.BubbleOtherDark
        isOneUi && !isMine            -> OneUiChat.BubbleOther
        isMine -> MaterialTheme.colorScheme.primary
        else   -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
internal fun resolveBubbleTextColor(isMine: Boolean, isOneUi: Boolean, isExthru: Boolean, isDark: Boolean): Color {
    return when {
        isExthru        -> ExthruChat.textPrimary(isDark)
        isOneUi && isMine           -> Color.White
        isOneUi && !isMine && isDark -> OneUiChat.TextPrimaryDark
        isOneUi && !isMine          -> OneUiChat.TextPrimary
        isMine -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
internal fun resolveLinkColor(isMine: Boolean, isOneUi: Boolean, isExthru: Boolean, isDark: Boolean): Color {
    return when {
        isExthru        -> ExthruChat.Accent
        isOneUi && isDark -> OneUiChat.BlueDark
        isOneUi         -> OneUiChat.Blue
        isMine          -> Color.White
        else            -> MaterialTheme.colorScheme.primary
    }
}

internal fun resolveBubbleShape(isMine: Boolean, isOneUi: Boolean) =
    if (isMine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    else        RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)

@Composable
internal fun ReplyPreview(
    reply: ReplyData, isMine: Boolean, isExthru: Boolean = false, isDark: Boolean = false, onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "reply_scale")

    val bgModifier = if (isExthru) {
        Modifier.nmInsetShadow(isDark, cornerRadius = 10.dp, darkAlpha = if(isDark) 0.5f else 0.2f)
            .background(Color.Black.copy(alpha = if (isDark) 0.15f else 0.04f), RoundedCornerShape(10.dp))
    } else {
        Modifier.background(if (isMine) Color.White.copy(0.25f) else MaterialTheme.colorScheme.primary.copy(0.12f), RoundedCornerShape(6.dp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(bgModifier)
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(8.dp),
    ) {
        Box(Modifier.width(3.dp).height(32.dp).background(if (isExthru) ExthruChat.Accent else if (isMine) Color.White.copy(0.9f) else MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "@${reply.senderUsername}",
                style = if (isExthru) ExthruSenderNameStyle else MaterialTheme.typography.labelSmall,
                color = if (isExthru) ExthruChat.Accent else if (isMine) Color.White.copy(0.9f) else MaterialTheme.colorScheme.primary,
            )
            Text(
                reply.text ?: "Медиа",
                style = MaterialTheme.typography.bodySmall,
                color = if (isExthru) ExthruChat.textSecondary(isDark) else if (isMine) Color.White.copy(0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun InlinedReactionRow(
    reactions: List<Reaction>, currentUid: String, isMine: Boolean, isOneUi: Boolean, isExthru: Boolean, isDark: Boolean, hapticEnabled: Boolean, onReact: (String) -> Unit, onShowPicker: () -> Unit,
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
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(if (isPressed) 0.85f else 1f, spring(dampingRatio = 0.5f), label = "react_scale")

                val shadowMod = if (isExthru) {
                    if (isPressed || iReacted) Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp, darkAlpha = if(isDark) 0.6f else 0.35f)
                    else Modifier.exthruSmallRaisedShadow(isDark)
                } else Modifier

                val bgColor = if (isExthru) {
                    if (iReacted) ExthruChat.Accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface.copy(alpha = if(isDark) 0.3f else 0.6f)
                } else {
                    if (iReacted) MaterialTheme.colorScheme.primary.copy(0.2f) else MaterialTheme.colorScheme.surfaceVariant
                }

                Box(
                    modifier = Modifier
                        .scale(scale)
                        .then(shadowMod)
                        .background(bgColor, RoundedCornerShape(16.dp))
                        .border(1.dp, if(isExthru && !isPressed && !iReacted) Color.White.copy(if(isDark)0.05f else 0.3f) else Color.Transparent, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(interactionSource = interactionSource, indication = null) {
                            haptic.perform(HapticType.REACTION, hapticEnabled)
                            onReact(reaction.emoji)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(reaction.emoji, fontSize = 14.sp)
                        Text(
                            reaction.count.toString(), fontSize = 12.sp,
                            fontWeight = if (iReacted) FontWeight.Bold else FontWeight.Medium,
                            color = if (isExthru) (if (iReacted) ExthruChat.Accent else ExthruChat.textSecondary(isDark)) else (if (iReacted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                }
            }
        }

        if (reactions.size < 3) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isPressed) 0.85f else 1f, spring(dampingRatio = 0.5f), label = "add_scale")

            val shadowMod = if (isExthru) {
                if (isPressed) Modifier.nmInsetShadow(isDark, cornerRadius = 16.dp) else Modifier.exthruSmallRaisedShadow(isDark)
            } else Modifier

            Box(
                modifier = Modifier
                    .scale(scale)
                    .then(shadowMod)
                    .background(if (isExthru) MaterialTheme.colorScheme.surface.copy(alpha = if(isDark) 0.3f else 0.6f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .border(1.dp, if(isExthru && !isPressed) Color.White.copy(if(isDark)0.05f else 0.3f) else Color.Transparent, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onShowPicker)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("＋", fontSize = 14.sp, color = if (isExthru) ExthruChat.textHint(isDark) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun DateSeparator(label: String, isOneUi: Boolean = false, isExthru: Boolean = false, isDark: Boolean = false, hasWallpaper: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        val bgMod = if (isExthru) {
            Modifier.nmInsetShadow(isDark, cornerRadius = 14.dp, darkAlpha = if(isDark) 0.6f else 0.35f)
                .background(Color.Black.copy(alpha = if(isDark) 0.3f else 0.1f), RoundedCornerShape(14.dp))
        } else {
            Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
        }

        Box(modifier = bgMod.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(
                text     = label,
                style    = MaterialTheme.typography.labelSmall,
                color    = if (isExthru) ExthruChat.textSecondary(isDark) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun ReadReceipt(isRead: Boolean, isExthru: Boolean = false, isDark: Boolean = false) {
    Icon(imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Done, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (isRead) ExthruChat.Accent else ExthruChat.textHint(isDark))
}

@Composable
internal fun LinkifiedText(
    text: String, color: Color, linkColor: Color, onMentionClick: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val (urlList, mentionList) = remember(text) {
        val urls = mutableListOf<Triple<String, Int, Int>>()
        val urlMatcher = android.util.Patterns.WEB_URL.matcher(text)
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
                var upEvent: androidx.compose.ui.input.pointer.PointerInputChange? = null
                var isTap = true

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break

                    if (change.isConsumed) {
                        isTap = false
                    }

                    if (!change.pressed) {
                        upEvent = change
                        break
                    }
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