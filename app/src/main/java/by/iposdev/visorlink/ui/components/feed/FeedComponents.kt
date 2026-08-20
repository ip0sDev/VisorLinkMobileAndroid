package by.iposdev.visorlink.ui.components.feed

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.components.VlCard
import by.iposdev.visorlink.data.model.FeedItem
import by.iposdev.visorlink.ui.components.CachedImage
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.ImageCache
import by.iposdev.visorlink.utils.rememberHaptic
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedCard(
    item: FeedItem,
    currentUid: String,
    hapticEnabled: Boolean,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onOpenImageViewer: (String, String) -> Unit,
    onClick: () -> Unit,
    onChannelClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val isLiked = item.displayLikedUids.contains(currentUid)
    val cs = MaterialTheme.colorScheme

    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(Offset.Zero) }

    VlCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = VlTheme.tokens.shapes.card,
        containerColor = cs.surfaceContainerLow,
        onClick = onClick
    ) {
        Column {
            // Header: Author
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(VlTheme.tokens.shapes.avatar)
                        .background(cs.primary.copy(alpha = 0.1f))
                        .clickable { onChannelClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (item.displayAuthorAvatarUrl != null) {
                        CachedImage(
                            model = item.displayAuthorAvatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            (item.displayAuthorName.firstOrNull() ?: item.displayChatId?.firstOrNull() ?: "?").toString().uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).clickable { onChannelClick() }) {
                    Text(
                        item.displayAuthorName.ifEmpty { item.displayChatId ?: stringResource(R.string.feed_unknown_channel) },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.createdAt?.let { formatTime(it.toDate()) } ?: stringResource(R.string.feed_just_now),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = { /* More actions */ }) {
                    Icon(Icons.Default.MoreVert, null, tint = cs.onSurfaceVariant)
                }
            }

            // Media (if any)
            val imageUrl = item.displayImageUrl
            if (imageUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onOpenImageViewer(imageUrl, "image") },
                                onLongPress = { offset ->
                                    haptic.perform(HapticType.LONG_PRESS, hapticEnabled)
                                    menuOffset = offset
                                    showMenu = true
                                }
                            )
                        }
                ) {
                    CachedImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                            .clip(VlTheme.tokens.shapes.button),
                        contentScale = ContentScale.FillWidth
                    )

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.image_viewer_save)) },
                            leadingIcon = { Icon(Icons.Default.Download, null) },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    val success = ImageCache.saveImageToGallery(context, imageUrl)
                                    Toast.makeText(
                                        context,
                                        if (success) "Saved to gallery" else "Failed to save",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }

            // Content
            Column(Modifier.padding(16.dp)) {
                if (!item.title.isNullOrEmpty()) {
                    Text(
                        item.title!!,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        color = cs.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                val displayText = item.text?.takeIf { it.isNotBlank() } ?: item.caption
                if (!displayText.isNullOrEmpty()) {
                    Text(
                        displayText,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = cs.onSurface,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Tags
                if (item.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.tags.forEach { tag ->
                            Surface(
                                color = cs.primary.copy(alpha = 0.1f),
                                shape = VlTheme.tokens.shapes.indicator
                            ) {
                                Text(
                                    "#$tag",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    color = cs.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.3f))

                // Actions (Like, Comment, etc)
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActionButton(
                            icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            label = item.displayLikesCount.toString(),
                            active = isLiked,
                            activeColor = Color(0xFFE11D48),
                            onClick = {
                                haptic.perform(if (isLiked) HapticType.CLICK else HapticType.SUCCESS, hapticEnabled)
                                onLike()
                            }
                        )
                        Spacer(Modifier.width(16.dp))
                        ActionButton(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            label = item.displayCommentsCount.toString(),
                            onClick = {
                                haptic.perform(HapticType.CLICK, hapticEnabled)
                                onComments()
                            }
                        )
                        Spacer(Modifier.width(16.dp))
                        ActionButton(
                            icon = Icons.Default.RemoveRedEye,
                            label = item.displayViewsCount.toString(),
                            enabled = false,
                            onClick = {}
                        )
                    }
                    IconButton(onClick = { /* Share */ }) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.85f else 1f, label = "btn_scale")

    Row(
        modifier = Modifier
            .scale(scale)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(22.dp),
            tint = contentColor
        )
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

private fun formatTime(date: Date): String {
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { time = date }
    return when {
        now.get(Calendar.DATE) == cal.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(date)
    }
}
