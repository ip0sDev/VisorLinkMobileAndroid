package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.ui.screens.stickers.AddStickerPackBanner
import by.iposdev.visorlink.ui.theme.Biolume
import by.iposdev.visorlink.ui.theme.ExthruSenderNameStyle
import by.iposdev.visorlink.ui.theme.bubbleInnerHighlight
import by.iposdev.visorlink.ui.theme.exthruRaisedShadow
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.VoicePlaybackState
import by.iposdev.visorlink.utils.rememberHaptic
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════════════════════
//  MessageBubble — роутер по типу сообщения
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
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
    onLongPress: () -> Unit,
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

    when {
        message.type == MessageType.ALBUM && !message.deleted -> {
            AlbumBubble(
                message = message, isMine = isMine, currentUid = currentUid,
                chatType = chatType, isOneUi = isOneUi, isExthru = isExthru, isDark = isDark,
                onAlbumTap = onAlbumTap,
                onLongPress = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPress() },
                onReact = onReact, onReplyClick = onReplyClick,
                onOpenComments = onOpenComments, chat = chat,
            )
            return
        }
        message.type == MessageType.IMAGE && !message.deleted -> {
            ImageBubble(
                message = message, isMine = isMine, isReadByOther = isReadByOther,
                chatType = chatType, currentUid = currentUid, hapticEnabled = hapticEnabled,
                isOneUi = isOneUi, isExthru = isExthru, isDark = isDark,
                hasWallpaper = hasWallpaper,
                onTap = { message.url?.let { onImageTap(it) } },
                onLongPress = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPress() },
                onReact = onReact, onReplyClick = onReplyClick,
                onOpenComments = onOpenComments, chat = chat,
            )
            return
        }
        message.type == MessageType.STICKER && !message.deleted -> {
            StickerBubble(
                message = message, isMine = isMine, currentUid = currentUid,
                isOneUi = isOneUi, isExthru = isExthru, isDark = isDark,
                hapticEnabled = hapticEnabled, showPackBanner = showPackBanner,
                onTogglePackBanner = { showPackBanner = !showPackBanner },
                onLongPress = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPress() },
                onReact = onReact,
            )
            return
        }
    }

    TextBubble(
        message = message, isMine = isMine, currentUid = currentUid,
        chatType = chatType, hapticEnabled = hapticEnabled, showSenderName = showSenderName,
        voicePlayback = voicePlayback, isOneUi = isOneUi, isExthru = isExthru, isDark = isDark,
        isReadByOther = isReadByOther, hasWallpaper = hasWallpaper,
        onPlayVoice = onPlayVoice, onSeekVoice = onSeekVoice,
        onLongPress = { haptic.perform(HapticType.LONG_PRESS, hapticEnabled); onLongPress() },
        onReact = onReact, onReplyClick = onReplyClick, onMentionClick = onMentionClick,
        onOpenComments = onOpenComments, chat = chat,
    )
}

// ════════════════════════════════════════════════════════════════════════════════
//  TextBubble
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TextBubble(
    message: Message,
    isMine: Boolean,
    currentUid: String,
    chatType: ChatType,
    hapticEnabled: Boolean,
    showSenderName: Boolean,
    voicePlayback: VoicePlaybackState,
    isOneUi: Boolean = false,
    isExthru: Boolean = false,
    isDark: Boolean = false,
    isReadByOther: Boolean = false,
    hasWallpaper: Boolean = false,
    onPlayVoice: (url: String, durationSec: Int) -> Unit,
    onSeekVoice: (Float) -> Unit,
    onLongPress: () -> Unit,
    onReact: (String) -> Unit,
    onReplyClick: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    onOpenComments: () -> Unit = {},
    chat: Chat? = null,
) {
    val bubbleColor = resolveBubbleColor(isMine, isOneUi, isExthru, isDark)
    val textColor   = resolveBubbleTextColor(isMine, isOneUi, isExthru, isDark)
    val linkColor   = resolveLinkColor(isMine, isOneUi, isExthru, isDark)
    val bubbleShape = resolveBubbleShape(isMine, isOneUi)

    // Цвет тени border — адаптируем под тёмную тему
    val borderColor = ExthruChat.shadowLight(isDark).copy(
        alpha = when {
            hasWallpaper -> 0.30f
            isDark       -> 0.12f   // тёмная: тусклый блик (guideline §2.2)
            else         -> 0.55f   // светлая
        }
    )

    val pressScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        val bubbleModifier = if (isExthru) {
            Modifier
                .widthIn(max = 260.dp)
                .scale(pressScale.value)
                .then(
                    if (!hasWallpaper)
                        Modifier.exthruRaisedShadow(isDark = isDark)
                    else
                        Modifier
                )
                .bubbleInnerHighlight(shape = bubbleShape, isDark = isDark)
                .border(0.5.dp, borderColor, bubbleShape)
        } else {
            Modifier
                .widthIn(max = 280.dp)
                .scale(pressScale.value)
        }

        Surface(
            modifier        = bubbleModifier,
            shape           = bubbleShape,
            color           = if (hasWallpaper && isExthru)
                bubbleColor.copy(alpha = if (isDark) 0.90f else 0.85f)
            else bubbleColor,
            shadowElevation = if (isOneUi && !isMine) 1.dp else 0.dp,
            tonalElevation  = 0.dp,
        ) {
            Box(modifier = Modifier.combinedClickable(
                onClick     = { },
                onLongClick = {
                    scope.launch {
                        pressScale.animateTo(0.91f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh))
                        pressScale.animateTo(1.04f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                        pressScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                    }
                    onLongPress()
                },
            )) {
                Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 4.dp)) {
                    if (showSenderName && !isMine) {
                        val senderColor = when {
                            isExthru -> ExthruChat.Accent
                            isOneUi  -> if (isDark) OneUiChat.BlueDark else OneUiChat.Blue
                            else     -> MaterialTheme.colorScheme.primary
                        }
                        Text(
                            "@${message.senderUsername}",
                            style      = if (isExthru) ExthruSenderNameStyle
                            else MaterialTheme.typography.labelSmall,
                            color      = senderColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = Modifier.padding(bottom = 2.dp),
                        )
                    }

                    message.replyData?.let { reply ->
                        ReplyPreview(
                            reply   = reply, isMine = isMine, isExthru = isExthru, isDark = isDark,
                            onClick = { reply.id?.let { id -> onReplyClick(id) } },
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    message.parsedForwardFrom?.let { fwd ->
                        ForwardBanner(
                            forwardFrom = fwd,
                            isMine      = isMine,
                            isExthru    = isExthru,
                            isDark      = isDark
                        )
                    }

                    if (message.deleted) {
                        Text(
                            stringResource(R.string.chat_message_deleted),
                            style     = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color     = textColor.copy(alpha = 0.6f),
                        )
                    } else when (message.type) {
                        MessageType.TEXT  -> LinkifiedText(
                            text           = message.text ?: "",
                            color          = textColor,
                            linkColor      = linkColor,
                            onLongPress    = onLongPress,
                            onMentionClick = onMentionClick,
                        )
                        MessageType.VOICE -> VoiceBubble(
                            messageId   = message.id,
                            url         = message.url ?: "",
                            durationSec = message.duration ?: 0,
                            tint        = textColor,
                            playback    = voicePlayback,
                            onPlay      = onPlayVoice,
                            onSeek      = onSeekVoice,
                        )
                        else -> {}
                    }

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        AnimatedVisibility(visible = message.createdAt != null, enter = fadeIn(tween(300))) {
                            Text(
                                message.createdAt?.toDate()?.let {
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                                } ?: "",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = textColor.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                            )
                        }
                        if (isMine && !message.deleted && chatType == ChatType.DIRECT) {
                            AnimatedContent(
                                targetState  = isReadByOther,
                                transitionSpec = {
                                    scaleIn(initialScale = 0.5f, animationSpec = spring(Spring.DampingRatioLowBouncy)) +
                                            fadeIn(tween(200)) togetherWith
                                            scaleOut(targetScale = 0.5f) + fadeOut(tween(100))
                                },
                                label = "read_receipt",
                            ) { read -> ReadReceipt(isRead = read, isExthru = isExthru, isDark = isDark) }
                        }
                    }

                    AnimatedVisibility(
                        visible = message.parsedReactions.isNotEmpty(),
                        enter = slideInVertically(initialOffsetY = { -it / 2 },
                            animationSpec = spring(Spring.DampingRatioMediumBouncy)) +
                                scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) +
                                fadeIn(),
                        exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150)),
                    ) {
                        InlinedReactionRow(
                            reactions     = message.parsedReactions,
                            currentUid    = currentUid,
                            isMine        = isMine,
                            isOneUi       = isOneUi,
                            isExthru      = isExthru,
                            isDark        = isDark,
                            hapticEnabled = hapticEnabled,
                            onReact       = onReact,
                            onShowPicker  = onLongPress,
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  StickerBubble
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StickerBubble(
    message: Message,
    isMine: Boolean,
    currentUid: String,
    isOneUi: Boolean = false,
    isExthru: Boolean = false,
    isDark: Boolean = false,
    hapticEnabled: Boolean,
    showPackBanner: Boolean,
    onTogglePackBanner: () -> Unit,
    onLongPress: () -> Unit,
    onReact: (String) -> Unit,
) {
    val timeColor = when {
        isExthru -> ExthruChat.textHint(isDark)
        else     -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Box(modifier = Modifier.combinedClickable(
            onClick     = { if (!message.packId.isNullOrBlank()) onTogglePackBanner() },
            onLongClick = onLongPress,
        )) {
            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                message.replyData?.let { reply ->
                    ReplyPreview(reply = reply, isMine = isMine, isExthru = isExthru, isDark = isDark,
                        onClick = { reply.id?.let { } })
                    Spacer(Modifier.height(4.dp))
                }
                AsyncImage(
                    model = message.url, contentDescription = null,
                    modifier = Modifier.size(120.dp),
                )
                Text(
                    message.createdAt?.toDate()?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                    } ?: "",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = timeColor,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (!message.packId.isNullOrBlank() && showPackBanner) {
                    AddStickerPackBanner(
                        packId    = message.packId,
                        packName  = message.packName ?: "",
                        packEmoji = message.packEmoji ?: "🎭",
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = message.parsedReactions.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it / 2 },
                animationSpec = spring(Spring.DampingRatioMediumBouncy)) +
                    scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) +
                    fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150)),
        ) {
            InlinedReactionRow(
                reactions     = message.parsedReactions,
                currentUid    = currentUid,
                isMine        = isMine,
                isOneUi       = isOneUi,
                isExthru      = isExthru,
                isDark        = isDark,
                hapticEnabled = hapticEnabled,
                onReact       = onReact,
                onShowPicker  = onLongPress,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  ImageBubble
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImageBubble(
    message: Message,
    isMine: Boolean,
    isReadByOther: Boolean,
    chatType: ChatType,
    currentUid: String,
    hapticEnabled: Boolean,
    isOneUi: Boolean = false,
    isExthru: Boolean = false,
    isDark: Boolean = false,
    hasWallpaper: Boolean = false,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onReact: (String) -> Unit,
    onReplyClick: (String) -> Unit,
    onOpenComments: () -> Unit = {},
    chat: Chat? = null,
) {
    val imageShape = if (isMine)
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    else
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

    val pressScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()

    val isSpoiler = message.spoiler == true
    var spoilerRevealed by remember(message.id) { mutableStateOf(false) }
    val blurRadius by animateDpAsState(
        targetValue   = if (isSpoiler && !spoilerRevealed) 20.dp else 0.dp,
        animationSpec = tween(300), label = "spoiler_blur",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        val containerModifier = if (isExthru) {
            Modifier
                .widthIn(min = 160.dp, max = 260.dp)
                .scale(pressScale.value)
                .then(if (!hasWallpaper) Modifier.exthruRaisedShadow(isDark = isDark) else Modifier)
                .clip(imageShape)
        } else {
            Modifier
                .widthIn(min = 160.dp, max = 260.dp)
                .scale(pressScale.value)
                .clip(imageShape)
        }

        Box(
            modifier = containerModifier.combinedClickable(
                onClick = {
                    if (isSpoiler && !spoilerRevealed) spoilerRevealed = true else onTap()
                },
                onLongClick = {
                    scope.launch {
                        pressScale.animateTo(0.93f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh))
                        pressScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                    }
                    haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                    onLongPress()
                },
            ),
        ) {
            AsyncImage(
                model = message.url, contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
                contentScale = ContentScale.Crop,
            )

            val overlayAlpha by animateFloatAsState(
                targetValue   = if (isSpoiler && !spoilerRevealed) 1f else 0f,
                animationSpec = tween(300), label = "spoiler_alpha",
            )
            if (overlayAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(overlayAlpha)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.tap_to_reveal), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            message.replyData?.let { reply ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { reply.id?.let { id -> onReplyClick(id) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(28.dp).background(Color.White, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("@${reply.senderUsername}", style = MaterialTheme.typography.labelSmall,
                                color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(reply.text ?: stringResource(R.string.photo),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        message.createdAt?.toDate()?.let {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                        } ?: "",
                        style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 9.sp,
                    )
                    if (isMine && chatType == ChatType.DIRECT) {
                        Icon(
                            imageVector = if (isReadByOther) Icons.Default.DoneAll else Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (isReadByOther) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = message.parsedReactions.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it / 2 },
                animationSpec = spring(Spring.DampingRatioMediumBouncy)) +
                    scaleIn(initialScale = 0.7f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 0.7f) + fadeOut(tween(150)),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                message.parsedReactions.forEach { reaction ->
                    key(reaction.emoji) {
                        val iReacted = currentUid in reaction.uids
                        val chipScale = remember { Animatable(1f) }
                        val chipScope = rememberCoroutineScope()
                        val accentColor = when {
                            isExthru -> ExthruChat.Accent
                            isOneUi  -> if (isDark) OneUiChat.BlueDark else OneUiChat.Blue
                            else     -> MaterialTheme.colorScheme.primary
                        }
                        Box(
                            modifier = Modifier
                                .scale(chipScale.value)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (iReacted) accentColor.copy(0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable {
                                    chipScope.launch {
                                        chipScale.animateTo(1.3f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh))
                                        chipScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                                    }
                                    onReact(reaction.emoji)
                                }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(reaction.emoji, fontSize = 13.sp)
                                Text(
                                    reaction.count.toString(), fontSize = 11.sp,
                                    color = if (iReacted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (message.parsedReactions.size < 3) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onLongPress() }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("＋", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Bubble color resolvers — полная поддержка Dark Mode для Exthru
// ════════════════════════════════════════════════════════════════════════════════

internal fun resolveBubbleColor(isMine: Boolean, isOneUi: Boolean, isExthru: Boolean, isDark: Boolean): Color {
    return when {
        isExthru && isMine  -> ExthruChat.bubbleMine(isDark)
        isExthru && !isMine -> ExthruChat.bubbleOther(isDark)
        isOneUi && isMine && isDark   -> OneUiChat.BubbleMineDark
        isOneUi && isMine             -> OneUiChat.BubbleMine
        isOneUi && !isMine && isDark  -> OneUiChat.BubbleOtherDark
        isOneUi && !isMine            -> OneUiChat.BubbleOther
        isMine -> Color(0xFF4F46E5)
        else   -> Color(0xFFEEF0FF)
    }
}

internal fun resolveBubbleTextColor(isMine: Boolean, isOneUi: Boolean, isExthru: Boolean, isDark: Boolean): Color {
    return when {
        // Exthru: текст не белый и не чёрный — из палитры Biolume
        isExthru        -> ExthruChat.textPrimary(isDark)
        isOneUi && isMine           -> Color.White
        isOneUi && !isMine && isDark -> OneUiChat.TextPrimaryDark
        isOneUi && !isMine          -> OneUiChat.TextPrimary
        else -> Color.Unspecified
    }
}

internal fun resolveLinkColor(isMine: Boolean, isOneUi: Boolean, isExthru: Boolean, isDark: Boolean): Color {
    return when {
        // Accent универсален для обеих тем Exthru
        isExthru        -> ExthruChat.Accent
        isOneUi && isDark -> OneUiChat.BlueDark
        isOneUi         -> OneUiChat.Blue
        isMine          -> Color.White
        else            -> Color.Unspecified
    }
}

internal fun resolveBubbleShape(isMine: Boolean, isOneUi: Boolean) =
    if (isMine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    else        RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)

// ════════════════════════════════════════════════════════════════════════════════
//  ReplyPreview — добавлен isDark
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ReplyPreview(
    reply: ReplyData,
    isMine: Boolean,
    isExthru: Boolean = false,
    isDark: Boolean = false,
    onClick: () -> Unit,
) {
    val accentColor = when {
        isExthru -> ExthruChat.Accent
        isMine   -> Color.White.copy(alpha = 0.25f)
        else     -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    val nameColor = when {
        isExthru -> ExthruChat.Accent
        isMine   -> Color.White.copy(alpha = 0.9f)
        else     -> MaterialTheme.colorScheme.primary
    }
    val textColor = when {
        isExthru -> ExthruChat.textSecondary(isDark)
        isMine   -> Color.White.copy(alpha = 0.7f)
        else     -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(accentColor)
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(Modifier.width(3.dp).height(32.dp).background(nameColor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                "@${reply.senderUsername}",
                style      = if (isExthru) ExthruSenderNameStyle else MaterialTheme.typography.labelSmall,
                color      = nameColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                reply.text ?: when (reply.type) {
                    MessageType.IMAGE   -> stringResource(R.string.image)
                    MessageType.VOICE   -> stringResource(R.string.voice_message)
                    MessageType.STICKER -> stringResource(R.string.sticker)
                    MessageType.ALBUM   -> "📷 Фото"
                    else -> ""
                },
                style    = MaterialTheme.typography.bodySmall,
                color    = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  InlinedReactionRow — адаптирован для Exthru Dark
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun InlinedReactionRow(
    reactions: List<Reaction>,
    currentUid: String,
    isMine: Boolean,
    isOneUi: Boolean,
    isExthru: Boolean,
    isDark: Boolean,
    hapticEnabled: Boolean,
    onReact: (String) -> Unit,
    onShowPicker: () -> Unit,
) {
    val haptic = rememberHaptic()
    val accentColor = when {
        isExthru -> ExthruChat.Accent
        isOneUi  -> if (isDark) OneUiChat.BlueDark else OneUiChat.Blue
        else     -> MaterialTheme.colorScheme.primary
    }
    // Exthru: фон пилюли реакции — из палитры Biolume
    val chipBgSelected = when {
        isExthru        -> accentColor.copy(alpha = 0.18f)
        isMine          -> Color.White.copy(alpha = 0.25f)
        else            -> accentColor.copy(alpha = 0.15f)
    }
    val chipBgDefault = when {
        isExthru && isDark -> Biolume.DarkShallowWater.copy(alpha = 0.60f)   // <-- Исправлено здесь
        isExthru           -> Biolume.ShadowLight.copy(alpha = 0.30f)        // <-- Заодно поправил и для светлой темы
        isMine             -> Color.White.copy(alpha = 0.12f)
        else               -> Color.Black.copy(alpha = 0.06f)
    }
    val chipTextSelected = if (isMine && !isExthru) Color.White else accentColor
    val chipTextDefault  = when {
        isExthru -> ExthruChat.textHint(isDark)
        isMine   -> Color.White.copy(0.8f)
        isDark   -> OneUiChat.TextSecondaryDark
        else     -> Color(0xFF444444)
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        reactions.forEach { reaction ->
            key(reaction.emoji) {
                val iReacted = currentUid in reaction.uids
                val chipScale = remember { Animatable(1f) }
                val chipScope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    chipScale.snapTo(0f)
                    chipScale.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
                }
                Box(
                    modifier = Modifier
                        .scale(chipScale.value)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (iReacted) chipBgSelected else chipBgDefault)
                        .clickable {
                            chipScope.launch {
                                chipScale.animateTo(1.3f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessHigh))
                                chipScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                            }
                            haptic.perform(HapticType.REACTION, hapticEnabled)
                            onReact(reaction.emoji)
                        }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(reaction.emoji, fontSize = 13.sp)
                        Text(
                            reaction.count.toString(), fontSize = 11.sp,
                            fontWeight = if (iReacted) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (iReacted) chipTextSelected else chipTextDefault,
                        )
                    }
                }
            }
        }
        if (reactions.size < 3) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(chipBgDefault)
                    .clickable { onShowPicker() }
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "＋", fontSize = 12.sp,
                    color = if (isExthru) ExthruChat.textHint(isDark)
                    else if (isMine) Color.White.copy(0.7f)
                    else if (isDark) OneUiChat.TextSecondaryDark
                    else Color(0xFF888888),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  ReadReceipt — добавлен isDark (акцент одинаков в обеих темах Exthru)
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ReadReceipt(
    isRead: Boolean,
    isExthru: Boolean = false,
    isDark: Boolean = false,
) {
    val readColor = when {
        isExthru -> ExthruChat.Accent   // CyanGlow универсален
        else     -> Color(0xFF00D4FF)
    }
    Icon(
        imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Done,
        contentDescription = if (isRead) stringResource(R.string.chat_read) else stringResource(R.string.chat_sent),
        modifier = Modifier.size(14.dp),
        tint = if (isRead) readColor else ExthruChat.textHint(isDark),
    )
}

// ════════════════════════════════════════════════════════════════════════════════
//  DateSeparator — добавлена поддержка Exthru Dark
// ════════════════════════════════════════════════════════════════════════════════

@Composable
internal fun DateSeparator(
    label: String,
    isOneUi: Boolean = false,
    isExthru: Boolean = false,
    isDark: Boolean = false,
    hasWallpaper: Boolean = false,
) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val bgColor = when {
            hasWallpaper -> Color.Black.copy(alpha = 0.4f)
            isExthru     -> ExthruChat.barBg(isDark)      // DeepWater / DarkDeepWater
            isOneUi      -> if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8)
            else         -> MaterialTheme.colorScheme.surfaceVariant
        }
        val textColor = when {
            hasWallpaper -> Color.White
            isExthru     -> ExthruChat.textSecondary(isDark)
            isDark       -> OneUiChat.TextPrimaryDark
            else         -> ExthruChat.TextPrimary
        }
        Surface(color = bgColor, shape = RoundedCornerShape(12.dp)) {
            Text(
                text     = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style    = MaterialTheme.typography.labelSmall,
                color    = textColor,
            )
        }
    }
}