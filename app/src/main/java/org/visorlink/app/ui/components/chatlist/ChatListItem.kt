package org.visorlink.app.ui.components.chatlist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.data.model.Chat
import org.visorlink.app.data.model.ChatType
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.model.isLastMessageRead
import org.visorlink.app.ui.components.AvatarWithPresence
import org.visorlink.app.ui.components.CachedImage
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageStatusIcon(
    isRead: Boolean,
    modifier: Modifier = Modifier
) {
    if (isRead) {
        // Двойная галочка (✓✓)
        Icon(
            painter = painterResource(id = R.drawable.ic_check_double),
            contentDescription = "Прочитано",
            tint = MaterialTheme.colorScheme.primary, // Акцентный цвет
            modifier = modifier.size(16.dp, 11.dp)
        )
    } else {
        // Одинарная галочка (✓)
        Icon(
            painter = painterResource(id = R.drawable.ic_check_single),
            contentDescription = "Отправлено",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = modifier.size(13.dp, 11.dp)
        )
    }
}

@Composable
fun ChatListItem(
    chat: Chat,
    chatType: ChatType,
    currentUid: String,
    otherProfile: UserProfile?,
    draftText: String?,
    unreadCount: Int = 0,
    isSavedMessages: Boolean = false,
    isCompactList: Boolean = false,
    isTyping: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val itemScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "item_press"
    )

    val shape = VlTheme.tokens.shapes.card
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val isDark = cs.surface.luminance() < 0.5f

    val titleColor = cs.onSurface
    val subColor = cs.onSurfaceVariant

    val isOwnLast = !isSavedMessages && (chat.lastMessageSenderId == currentUid ||
            chat.lastMessageInfo()?.senderId == currentUid)
    val isRead = remember(chat, currentUid) {
        isLastMessageRead(chat, currentUid)
    }

    val cardColor = remember(tokens.isBiolume, isDark, cs) {
        if (tokens.isBiolume) {
            if (isDark) cs.surfaceContainer.copy(alpha = 0.70f)
            else cs.surfaceContainerLow.copy(alpha = 0.90f)
        } else {
            if (isDark) cs.surfaceVariant.copy(alpha = 0.45f) else cs.surface
        }
    }

    val cardBorder = remember(tokens.isBiolume, isDark, cs) {
        if (tokens.isBiolume) {
            val topColor = if (isDark) cs.outlineVariant.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.45f)
            val bottomColor = if (isDark) cs.outlineVariant.copy(alpha = 0.04f) else cs.outlineVariant.copy(alpha = 0.10f)
            BorderStroke(1.dp, Brush.verticalGradient(listOf(topColor, bottomColor)))
        } else {
            BorderStroke(
                1.dp,
                if (isDark) cs.outlineVariant.copy(alpha = 0.15f) else cs.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(vertical = if (!isCompactList) 6.dp else 0.dp)
            .scale(itemScale)
            .then(
                if (tokens.structure.enabled && !isCompactList) {
                    Modifier.vlRaised(tokens.structure, shape)
                } else if (tokens.structure.enabled) {
                    Modifier.vlHairline(cs.outlineVariant, shape)
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = shape,
        color = cardColor,
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (tokens.isBiolume) {
                        val gTop = if (isDark) cs.surfaceContainerHigh.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.18f)
                        val gBottom = Color.Transparent
                        Modifier.background(Brush.verticalGradient(listOf(gTop, gBottom)))
                    } else Modifier
                )
                .padding(horizontal = 14.dp, vertical = if (!isCompactList) 12.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(52.dp)) {
                if (isSavedMessages) {
                    SavedMessagesIcon(size = 52.dp)
                } else {
                    when (chatType) {
                        ChatType.DIRECT -> {
                            val otherUsername = chat.otherUsername(currentUid)
                            val isFaulty = otherProfile?.uid == "bot_faultywire" || otherUsername == "faultywire"
                            AvatarWithPresence(
                                avatarUrl = otherProfile?.avatarUrl,
                                displayName = chat.otherDisplayName(currentUid),
                                isOnline = otherProfile?.online ?: false,
                                size = 52.dp,
                                isFaultyWireBot = isFaulty
                            )
                        }
                        ChatType.GROUP, ChatType.CHANNEL -> GroupChannelAvatar(
                            avatarUrl = chat.avatarUrl,
                            name = chat.name,
                            isChannel = chatType == ChatType.CHANNEL,
                            size = 52.dp
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (chatType != ChatType.DIRECT && !isSavedMessages) {
                            Text(if (chatType == ChatType.CHANNEL) "📢" else if (chat.isForumActive) "💬" else "👥", fontSize = 11.sp)
                        }
                        val otherUsername = if (chatType == ChatType.DIRECT) chat.otherUsername(currentUid) else ""
                        val isOfficial = chatType == ChatType.DIRECT && (
                            otherProfile?.botBadge == "official" ||
                            otherProfile?.uid == "bot_faultywire" ||
                            otherUsername == "faultywire"
                        )
                        Text(
                            text = when {
                                isSavedMessages -> stringResource(R.string.saved_messages_title)
                                chatType == ChatType.DIRECT -> chat.otherDisplayName(currentUid).ifEmpty { "@$otherUsername" }
                                else -> chat.name
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isOfficial) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Official Bot",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    chat.lastMessageAt?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatTime(it.toDate()),
                            style = MaterialTheme.typography.labelSmall,
                            color = subColor,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (isTyping) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = "✍️", fontSize = 12.sp)
                                Text(
                                    text = stringResource(R.string.chat_typing),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (!draftText.isNullOrEmpty()) {
                            Row {
                                Text(
                                    stringResource(R.string.chatlist_draft_prefix),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.error,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = draftText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isOwnLast) {
                                    MessageStatusIcon(isRead = isRead)
                                    Spacer(Modifier.width(5.dp))
                                }
                                val messageText = remember(chat.lastMessage) {
                                    org.visorlink.app.utils.MarkdownTextParser.stripMarkdown(chat.lastMessageText())
                                }
                                Text(
                                    text = if (messageText.isNotEmpty()) messageText else stringResource(R.string.chatlist_no_messages),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                    }
                    if (unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        ChatUnreadBadge(count = unreadCount)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListItemCompact(
    chat: Chat,
    chatType: ChatType,
    currentUid: String,
    otherProfile: UserProfile?,
    draftText: String?,
    unreadCount: Int = 0,
    isSavedMessages: Boolean = false,
    isTyping: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgHighlight = if (isPressed) MaterialTheme.colorScheme.onSurface.copy(0.05f) else Color.Transparent

    val titleColor = MaterialTheme.colorScheme.onSurface
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cs = MaterialTheme.colorScheme

    val isOwnLast = !isSavedMessages && (chat.lastMessageSenderId == currentUid ||
            chat.lastMessageInfo()?.senderId == currentUid)
    val isRead = remember(chat, currentUid) {
        isLastMessageRead(chat, currentUid)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgHighlight)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(54.dp)) {
            if (isSavedMessages) {
                SavedMessagesIcon(size = 54.dp)
            } else {
                when (chatType) {
                    ChatType.DIRECT -> {
                        val otherUsername = chat.otherUsername(currentUid)
                        val isFaulty = otherProfile?.uid == "bot_faultywire" || otherUsername == "faultywire"
                        AvatarWithPresence(
                            avatarUrl = otherProfile?.avatarUrl,
                            displayName = chat.otherDisplayName(currentUid),
                            isOnline = otherProfile?.online ?: false,
                            size = 54.dp,
                            isFaultyWireBot = isFaulty
                        )
                    }
                    ChatType.GROUP, ChatType.CHANNEL -> GroupChannelAvatar(
                        avatarUrl = chat.avatarUrl,
                        name = chat.name,
                        isChannel = chatType == ChatType.CHANNEL,
                        size = 54.dp
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (chatType != ChatType.DIRECT && !isSavedMessages) {
                        Text(if (chatType == ChatType.CHANNEL) "📢" else if (chat.isForumActive) "💬" else "👥", fontSize = 11.sp)
                    }
                    val otherUsername = if (chatType == ChatType.DIRECT) chat.otherUsername(currentUid) else ""
                    val isOfficial = chatType == ChatType.DIRECT && (
                        otherProfile?.botBadge == "official" ||
                        otherProfile?.uid == "bot_faultywire" ||
                        otherUsername == "faultywire"
                    )
                    Text(
                        text = when {
                            isSavedMessages -> stringResource(R.string.saved_messages_title)
                            chatType == ChatType.DIRECT -> chat.otherDisplayName(currentUid).ifEmpty { "@$otherUsername" }
                            else -> chat.name
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isOfficial) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Official Bot",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                chat.lastMessageAt?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatTime(it.toDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = subColor,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (isTyping) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "✍️", fontSize = 12.sp)
                            Text(
                                text = stringResource(R.string.chat_typing),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (!draftText.isNullOrEmpty()) {
                        Row {
                            Text(
                                stringResource(R.string.chatlist_draft_prefix),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = draftText,
                                style = MaterialTheme.typography.bodySmall,
                                color = subColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isOwnLast) {
                                MessageStatusIcon(isRead = isRead)
                                Spacer(Modifier.width(5.dp))
                            }
                            val messageText = remember(chat.lastMessage) {
                                org.visorlink.app.utils.MarkdownTextParser.stripMarkdown(chat.lastMessageText())
                            }
                            Text(
                                text = if (messageText.isNotEmpty()) messageText else stringResource(R.string.chatlist_no_messages),
                                style = MaterialTheme.typography.bodySmall,
                                color = subColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
                if (unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    ChatUnreadBadge(count = unreadCount)
                }
            }
        }
    }
}

@Composable
fun ChatUnreadBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val text = if (count > 99) "99+" else count.toString()

    Surface(
        modifier = modifier
            .height(20.dp)
            .widthIn(min = 20.dp),
        shape = tokens.shapes.pill,
        color = cs.primary,
        contentColor = cs.onPrimary
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = cs.onPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun GroupChannelAvatar(
    avatarUrl: String?,
    name: String,
    isChannel: Boolean,
    size: Dp = 48.dp
) {
    val bgBrush = if (isChannel) Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.tertiaryContainer))

    val textColor = if (isChannel) Color.White else MaterialTheme.colorScheme.onSecondaryContainer

    val shape = VlTheme.tokens.shapes.avatar

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(bgBrush),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrEmpty()) {
            CachedImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = if (isChannel) "📢" else (name.firstOrNull()?.uppercase() ?: "G"),
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
fun SavedMessagesIcon(size: Dp) {
    val accent = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bgGradient = Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = if (isDark) 0.35f else 0.25f),
            accent.copy(alpha = if (isDark) 0.15f else 0.05f)
        )
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.3f else 0.6f),
            Color.Transparent,
            accent.copy(alpha = 0.4f)
        )
    )

    val shape = VlTheme.tokens.shapes.avatar

    Box(
        modifier = Modifier
            .size(size)
            .background(bgGradient, shape)
            .border(1.5.dp, borderBrush, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bookmark,
            contentDescription = null,
            modifier = Modifier.size((size.value * 0.45f).dp),
            tint = accent
        )
    }
}

private fun formatTime(date: Date): String {
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { time = date }
    return when {
        now.get(Calendar.DATE) == cal.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        now.get(Calendar.WEEK_OF_YEAR) == cal.get(Calendar.WEEK_OF_YEAR) ->
            SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
    }
}
