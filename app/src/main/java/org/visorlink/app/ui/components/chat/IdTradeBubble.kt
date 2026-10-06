package org.visorlink.app.ui.components.chat

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import org.visorlink.app.data.model.ChatType
import org.visorlink.app.data.model.Message
import org.visorlink.app.data.model.SendStatus
import org.visorlink.app.ui.idcard.IdTradeMessage
import org.visorlink.app.ui.theme.VlTheme
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Пузырь сообщения «ID-карта на обмен» (`id_trade`): ширина ~320 dp, но не шире экрана; внутри —
 * карточка обмена [IdTradeMessage]. Жесты (реакции, ответ) — как у остальных пузырей.
 */
@Composable
internal fun IdTradeBubble(
    message: Message,
    isMine: Boolean,
    currentUid: String,
    chatId: String,
    chatType: ChatType,
    showSenderName: Boolean,
    isReadByOther: Boolean,
    hapticEnabled: Boolean,
    onLongPressStart: (Offset) -> Unit,
    onLongPressDrag: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onReact: (String) -> Unit,
    onDoubleTap: (() -> Unit)?,
) {
    val textColor = resolveBubbleTextColor(isMine)
    val interactionSource = remember { MutableInteractionSource() }
    val width = min(320.dp, LocalConfiguration.current.screenWidthDp.dp - 96.dp)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier
                .width(width)
                .messageGestures(
                    messageId = message.id,
                    interactionSource = interactionSource,
                    onDoubleTap = onDoubleTap ?: { onReact("❤️") },
                    hapticEnabled = hapticEnabled,
                    onLongPressStart = onLongPressStart,
                    onLongPressDrag = onLongPressDrag,
                    onLongPressEnd = onLongPressEnd,
                ),
            shape = VlTheme.tokens.shapes.card,
            color = resolveBubbleColor(isMine),
            contentColor = textColor,
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showSenderName && !isMine) {
                    Text(
                        "@${message.senderUsername}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IdTradeMessage(tradeId = message.tradeId, messageId = message.id, chatId = chatId)
                DebugMessageBadge(message = message, textColor = textColor)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        message.createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                    )
                    if (isMine) {
                        if (chatType == ChatType.DIRECT && message.status != SendStatus.QUEUED) ReadReceipt(isRead = isReadByOther)
                        else MessageStatusIcon(status = message.status)
                    }
                }
            }
        }
        if (message.parsedReactions.isNotEmpty()) {
            InlinedReactionRow(
                reactions = message.parsedReactions, currentUid = currentUid, isMine = isMine,
                hapticEnabled = hapticEnabled, onReact = onReact, onShowPicker = {},
            )
        }
    }
}
