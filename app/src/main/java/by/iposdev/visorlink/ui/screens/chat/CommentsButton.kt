package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.Chat
import by.iposdev.visorlink.data.model.Message
import by.iposdev.visorlink.data.model.commentsAllowed

/**
 * Button shown below a channel post bubble.
 * Displays the live comment count and is greyed out when comments are off.
 *
 * Usage in MessageBubble / ImageBubble (channel posts):
 *
 *   if (chatType == ChatType.CHANNEL && channel != null) {
 *       CommentsButton(
 *           post = message,
 *           channelAllowsComments = channel.settings.allowComments,
 *           onClick = { onOpenComments(chatId, message.id) }
 *       )
 *   }
 */
@Composable
fun CommentsButton(
    post: Message,
    channelAllowsComments: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allowed = channelAllowsComments && post.commentsEnabled != false
    val contentAlpha = if (allowed) 1f else 0.4f

    Row(
        modifier = modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = contentAlpha),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            Icons.Default.ChatBubbleOutline, null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
        )
        Text(
            text = when {
                !allowed -> "Comments off"
                post.commentsCount == 0 -> "Comment"
                post.commentsCount == 1 -> "1 comment"
                else -> "${post.commentsCount} comments"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            fontSize = 12.sp
        )
    }
}