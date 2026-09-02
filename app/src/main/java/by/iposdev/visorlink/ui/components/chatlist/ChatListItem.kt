package by.iposdev.visorlink.ui.components.chatlist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlHairline
import by.iposdev.visorlink.ui.theme.vlRaised
import by.iposdev.visorlink.data.model.Chat
import by.iposdev.visorlink.data.model.ChatType
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.ui.components.AvatarWithPresence
import by.iposdev.visorlink.ui.components.CachedImage
import java.text.SimpleDateFormat
import java.util.*

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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(vertical = if (!isCompactList) 6.dp else 0.dp)
            .scale(itemScale)
            // Рельеф требует воздуха вокруг элемента: в компактном режиме
            // вертикальных отступов нет, тени соседних строк наложились бы друг
            // на друга грязными полосами — там остаётся только грань.
            .then(
                if (tokens.structure.enabled && !isCompactList) {
                    Modifier.vlRaised(tokens.structure, shape)
                } else {
                    Modifier
                }
            )
            .then(
                if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, shape) else Modifier
            ),
        shape = shape,
        color = if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerLow,
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(54.dp)) {
                if (isSavedMessages) {
                    SavedMessagesIcon(size = 54.dp)
                } else {
                    when (chatType) {
                        ChatType.DIRECT -> AvatarWithPresence(
                            avatarUrl = otherProfile?.avatarUrl,
                            displayName = chat.otherDisplayName(currentUid),
                            isOnline = otherProfile?.online ?: false,
                            size = 54.dp
                        )
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (chatType != ChatType.DIRECT && !isSavedMessages) {
                        Text(if (chatType == ChatType.CHANNEL) "📢" else if (chat.isForumActive) "💬" else "👥", fontSize = 11.sp)
                    }
                    Text(
                        text = when {
                            isSavedMessages -> stringResource(R.string.saved_messages_title)
                            chatType == ChatType.DIRECT -> chat.otherDisplayName(currentUid).ifEmpty { "@${chat.otherUsername(currentUid)}" }
                            else -> chat.name
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    chat.lastMessageAt?.let {
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
                        if (!draftText.isNullOrEmpty()) {
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
                            val messageText = chat.lastMessageText()
                            Text(
                                text = if (messageText.isNotEmpty()) messageText else stringResource(R.string.chatlist_no_messages),
                                style = MaterialTheme.typography.bodySmall,
                                color = subColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgHighlight = if (isPressed) MaterialTheme.colorScheme.onSurface.copy(0.05f) else Color.Transparent

    val titleColor = MaterialTheme.colorScheme.onSurface
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant

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
                    ChatType.DIRECT -> AvatarWithPresence(
                        avatarUrl = otherProfile?.avatarUrl,
                        displayName = chat.otherDisplayName(currentUid),
                        isOnline = otherProfile?.online ?: false,
                        size = 54.dp
                    )
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (chatType != ChatType.DIRECT && !isSavedMessages) {
                    Text(if (chatType == ChatType.CHANNEL) "📢" else if (chat.isForumActive) "💬" else "👥", fontSize = 11.sp)
                }
                Text(
                    text = when {
                        isSavedMessages -> stringResource(R.string.saved_messages_title)
                        chatType == ChatType.DIRECT -> chat.otherDisplayName(currentUid).ifEmpty { "@${chat.otherUsername(currentUid)}" }
                        else -> chat.name
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                chat.lastMessageAt?.let {
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
                    if (!draftText.isNullOrEmpty()) {
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
                        val messageText = chat.lastMessageText()
                        Text(
                            text = if (messageText.isNotEmpty()) messageText else stringResource(R.string.chatlist_no_messages),
                            style = MaterialTheme.typography.bodySmall,
                            color = subColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
