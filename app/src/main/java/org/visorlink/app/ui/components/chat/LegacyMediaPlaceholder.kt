package org.visorlink.app.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.R
import org.visorlink.app.data.model.ChatType
import org.visorlink.app.data.model.Message
import org.visorlink.app.data.model.SendStatus
import org.visorlink.app.ui.theme.VlTheme
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun LegacyMediaPlaceholder(
    message: Message,
    isMine: Boolean,
    chatType: ChatType = ChatType.DIRECT,
    showSenderName: Boolean = false,
    isReadByOther: Boolean = false,
    hapticEnabled: Boolean = true,
    onLongPressStart: (Offset) -> Unit = {},
    onLongPressDrag: (Offset) -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onReact: (String) -> Unit = {},
    onReplyClick: (String) -> Unit = {},
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bubbleColor = resolveBubbleColor(isMine)
    val textColor = resolveBubbleTextColor(isMine)
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        val bubbleModifier = Modifier
            .widthIn(max = 280.dp)
            .messageGestures(
                messageId = message.id,
                interactionSource = interactionSource,
                onTap = null,
                onDoubleTap = onDoubleTap ?: { onReact("❤️") },
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
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
            ) {
                if (showSenderName && !isMine) {
                    Text(
                        text = "@${message.senderUsername}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                message.replyData?.let { reply ->
                    ReplyPreview(reply = reply, isMine = isMine, onClick = { reply.id?.let { id -> onReplyClick(id) } })
                    Spacer(Modifier.height(4.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                shape = VlTheme.tokens.shapes.indicator
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.msg_unsupported_media),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.9f)
                    )
                }

                // Time and status
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    AnimatedVisibility(visible = message.createdAt != null) {
                        Text(
                            text = message.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                    if (isMine && !message.deleted) {
                        if (chatType == ChatType.DIRECT && message.status != SendStatus.QUEUED) {
                            ReadReceipt(isRead = isReadByOther)
                        } else {
                            MessageStatusIcon(status = message.status)
                        }
                    }
                }
            }
        }
    }
}
