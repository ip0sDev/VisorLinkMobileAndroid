package org.visorlink.app.ui.components.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.visorlink.app.R
import org.visorlink.app.data.model.CuratedChannel
import org.visorlink.app.data.model.FeedMedia
import org.visorlink.app.data.model.FeedPost
import org.visorlink.app.data.model.MessageType
import org.visorlink.app.ui.components.CachedImage
import org.visorlink.app.ui.components.LinkifiedText
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Действия с постом из меню «⋮» и из «Поделиться». */
enum class FeedPostAction { OPEN_CHANNEL, FORWARD, SHARE, COPY_TEXT, SAVE_IMAGE, REPORT }

/** Свёрнутый пост показывает столько строк текста, дальше — «Показать полностью». */
private const val COLLAPSED_LINES = 8

/**
 * Пост канала в ленте «Каналы». Сама карточка не реагирует на тап — раньше тап по ней
 * засчитывал просмотр; просмотры считает экран по времени на экране.
 */
@Composable
fun FeedPostCard(
    post: FeedPost,
    currentUid: String,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenImage: (String) -> Unit,
    onAction: (FeedPostAction) -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberHaptic()
    val message = post.message
    val liked = post.isLikedBy(currentUid)

    VlCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) {
            // Шапка: канал и время
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(VlTheme.tokens.shapes.rounded(12.dp))
                        .clickable(onClick = onOpenChannel)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelAvatar(name = post.channel.name, avatarUrl = post.channel.avatarUrl, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            post.channel.name.ifBlank { stringResource(R.string.feed_channel_fallback) },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val time = message.createdAt?.toDate()?.let(::formatPostTime) ?: stringResource(R.string.feed_just_now)
                        Text(
                            if (message.isPublished == false) "$time · ${stringResource(R.string.feed_unpublished)}" else time,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                PostMenu(post = post, currentUid = currentUid, onAction = onAction)
            }

            post.body?.let { body ->
                ExpandableBody(body, Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp))
            }

            post.media?.let { media ->
                PostMedia(
                    media = media,
                    aspect = message.width?.let { w -> message.height?.takeIf { it > 0 }?.let { h -> w.toFloat() / h } },
                    onOpenImage = onOpenImage,
                    onOpenChannel = onOpenChannel,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp),
                )
            }

            // Действия
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 14.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (post.likesEnabled) {
                    PostAction(
                        icon = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        label = post.likeCount.takeIf { it > 0 }?.let(::formatCount),
                        contentDescription = stringResource(R.string.feed_like),
                        active = liked,
                        onClick = {
                            haptic.perform(if (liked) HapticType.CLICK else HapticType.SUCCESS, hapticEnabled)
                            onLike()
                        },
                    )
                }
                if (post.commentsEnabled) {
                    PostAction(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        label = message.commentsCount.takeIf { it > 0 }?.let(::formatCount),
                        contentDescription = stringResource(R.string.feed_comments),
                        onClick = {
                            haptic.perform(HapticType.CLICK, hapticEnabled)
                            onComments()
                        },
                    )
                }
                if (post.shareable) ShareButton(onAction = onAction)
                Spacer(Modifier.weight(1f))
                if (message.viewsCount > 0) {
                    Icon(
                        Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.feed_views),
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(formatCount(message.viewsCount), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ExpandableBody(body: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember(body) { mutableStateOf(false) }
    var overflowing by remember(body) { mutableStateOf(false) }
    Column(modifier) {
        LinkifiedText(
            text = body,
            color = cs.onSurface,
            linkColor = cs.primary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
            onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow },
        )
        if (overflowing && !expanded) {
            Text(
                stringResource(R.string.feed_show_more),
                style = MaterialTheme.typography.labelLarge,
                color = cs.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(VlTheme.tokens.shapes.rounded(8.dp))
                    .clickable { expanded = true }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PostMedia(
    media: FeedMedia,
    aspect: Float?,
    onOpenImage: (String) -> Unit,
    onOpenChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = VlTheme.tokens.shapes.rounded(12.dp)
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    when (media) {
        is FeedMedia.Images -> if (media.urls.size == 1) {
            val url = media.urls.first()
            CachedImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .fillMaxWidth()
                    // Пропорции из сообщения, в разумных пределах: без них карточка прыгала бы при загрузке
                    .aspectRatio((aspect ?: (4f / 3f)).coerceIn(0.66f, 1.9f))
                    .clip(shape)
                    .background(placeholder)
                    .clickable { onOpenImage(url) },
            )
        } else {
            // Альбом: до четырёх плиток 2×2, на последней — сколько ещё
            val shown = media.urls.take(4)
            Column(modifier.fillMaxWidth().clip(shape), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                shown.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEachIndexed { i, url ->
                            val index = rowIndex * 2 + i
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(if (shown.size == 2) 0.8f else 1f)
                                    .background(placeholder)
                                    .clickable { onOpenImage(url) },
                            ) {
                                CachedImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                                val more = media.urls.size - shown.size
                                if (index == shown.lastIndex && more > 0) {
                                    Box(
                                        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("+$more", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        is FeedMedia.Attachment -> AttachmentRow(attachmentLabel(media.type), onClick = onOpenChannel, modifier = modifier)
        FeedMedia.Archived -> AttachmentRow(stringResource(R.string.feed_media_archived), onClick = null, modifier = modifier)
    }
}

/** Вложение, которое лента не показывает сама: подпись и переход в канал. */
@Composable
private fun AttachmentRow(label: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(VlTheme.tokens.shapes.rounded(12.dp))
            .background(cs.surfaceContainerHighest)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
        if (onClick != null) {
            Text(stringResource(R.string.feed_open_in_channel), style = MaterialTheme.typography.labelMedium, color = cs.primary)
        }
    }
}

@Composable
private fun attachmentLabel(type: String): String = when (type.lowercase()) {
    MessageType.VIDEO -> stringResource(R.string.feed_media_video)
    MessageType.GIF -> stringResource(R.string.feed_media_gif)
    MessageType.VOICE -> stringResource(R.string.voice_message)
    MessageType.AUDIO -> stringResource(R.string.feed_media_audio)
    MessageType.STICKER, MessageType.LOTTIE -> stringResource(R.string.sticker)
    MessageType.GIFT -> stringResource(R.string.feed_media_gift)
    MessageType.ID_TRADE -> stringResource(R.string.preview_id_trade)
    FeedMedia.SPOILER -> stringResource(R.string.feed_media_spoiler)
    "file" -> stringResource(R.string.feed_media_file)
    else -> stringResource(R.string.feed_media_other)
}

@Composable
private fun PostAction(
    icon: ImageVector,
    label: String?,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val color = if (active) cs.primary else cs.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(VlTheme.tokens.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(20.dp))
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun ShareButton(onAction: (FeedPostAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PostAction(icon = Icons.Outlined.Share, label = null, contentDescription = stringResource(R.string.feed_share), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem(Icons.AutoMirrored.Filled.Forward, stringResource(R.string.feed_forward)) { open = false; onAction(FeedPostAction.FORWARD) }
            MenuItem(Icons.Outlined.Share, stringResource(R.string.feed_share_external)) { open = false; onAction(FeedPostAction.SHARE) }
        }
    }
}

@Composable
private fun PostMenu(post: FeedPost, currentUid: String, onAction: (FeedPostAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.feed_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun pick(action: FeedPostAction) { open = false; onAction(action) }
            MenuItem(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.feed_open_channel)) { pick(FeedPostAction.OPEN_CHANNEL) }
            if (post.shareable && post.body != null) {
                MenuItem(Icons.Default.ContentCopy, stringResource(R.string.feed_copy_text)) { pick(FeedPostAction.COPY_TEXT) }
            }
            if (post.shareable && post.media is FeedMedia.Images) {
                MenuItem(Icons.Default.Download, stringResource(R.string.feed_save_image)) { pick(FeedPostAction.SAVE_IMAGE) }
            }
            if (post.message.senderId != currentUid) {
                MenuItem(Icons.Default.Flag, stringResource(R.string.action_report), destructive = true) { pick(FeedPostAction.REPORT) }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(text, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        onClick = onClick,
    )
}

/** Канал курируемого каталога: тап по карточке — открыть канал, кнопка — подписаться. */
@Composable
fun CuratedChannelCard(
    channel: CuratedChannel,
    subscribed: Boolean,
    joining: Boolean,
    onSubscribe: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    VlCard(modifier = modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelAvatar(name = channel.name, avatarUrl = channel.avatarUrl, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (channel.tag.isNotBlank()) {
                    Text("@${channel.tag}", style = MaterialTheme.typography.labelMedium, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (channel.description.isNotBlank()) {
                    Text(
                        channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (channel.memberCount > 0) {
                    Text(
                        pluralStringResource(R.plurals.feed_subscribers, channel.memberCount, formatCount(channel.memberCount)),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            PillButton(
                text = stringResource(if (subscribed) R.string.feed_open else R.string.feed_subscribe),
                filled = !subscribed,
                loading = joining,
                onClick = if (subscribed) onOpen else onSubscribe,
            )
        }
    }
}

@Composable
private fun PillButton(text: String, filled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = tokens.shapes.pill
    val container = if (filled) cs.primary else tokens.selectionFill.takeIf { it != Color.Unspecified } ?: cs.secondaryContainer
    val content = if (filled) cs.onPrimary else cs.onSurface
    Box(
        modifier = Modifier
            .clip(shape)
            .background(container, shape)
            .clickable(enabled = !loading, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = content)
        else Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1)
    }
}

@Composable
fun ChannelAvatar(name: String, avatarUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(VlTheme.tokens.shapes.avatar)
            .background(cs.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            CachedImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                (name.trim().firstOrNull() ?: '#').uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onPrimaryContainer,
            )
        }
    }
}

/** 999 → «999», 12 345 → «12K», 1 250 000 → «1.2M». */
internal fun formatCount(n: Int): String = when {
    n < 10_000 -> n.toString()
    n < 1_000_000 -> "${n / 1000}K"
    else -> String.format(Locale.US, "%.1fM", n / 1_000_000f).replace(".0M", "M")
}

private fun formatPostTime(date: Date): String {
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { time = date }
    val sameDay = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    val sameYear = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
    val pattern = when {
        sameDay -> "HH:mm"
        sameYear -> "d MMM, HH:mm"
        else -> "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}
