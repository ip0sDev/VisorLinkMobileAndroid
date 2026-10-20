package org.visorlink.app.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.data.model.ChatType
import org.visorlink.app.data.model.Message
import org.visorlink.app.data.model.SendStatus
import org.visorlink.app.ui.components.terminalData
import org.visorlink.app.ui.theme.VlTheme
import java.text.SimpleDateFormat
import java.util.Locale

/** Максимальная ширина медиа-пузыря (спека подписей §2). */
internal val MEDIA_BUBBLE_MAX_WIDTH = 320.dp

/**
 * Медиа-пузырь без рамок, как в Telegram (ANDROID_CAPTIONS_AND_ANIMATED_STICKERS_SPEC.md §2):
 * фото/видео/сетка во всю ширину без отступов, скругляет сам пузырь; имя, ответ и
 * «Переслано» — с отступом 12 dp над медиа, подпись — под ним. Без подписи время
 * лежит плашкой на нижнем правом углу медиа.
 *
 * Шапка и подпись не шире медиа: [MediaColumn] меряет медиа первым.
 */
@Composable
internal fun MediaBubbleFrame(
    message: Message,
    isMine: Boolean,
    isReadByOther: Boolean,
    chatType: ChatType,
    showSenderName: Boolean,
    hapticEnabled: Boolean,
    onTap: (() -> Unit)?,
    onDoubleTap: () -> Unit,
    onLongPressStart: (Offset) -> Unit,
    onLongPressDrag: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onReplyClick: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    onCancelUpload: ((String) -> Unit)?,
    media: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textColor = resolveBubbleTextColor(isMine)
    val caption = message.caption?.takeIf { it.isNotBlank() }
    val forward = message.parsedForwardFrom
    val reply = message.replyData
    val senderName = showSenderName && !isMine
    val hasHeader = senderName || reply != null || forward != null

    Surface(
        modifier = Modifier
            .widthIn(max = MEDIA_BUBBLE_MAX_WIDTH)
            .messageGestures(
                messageId = message.id, interactionSource = interactionSource,
                onTap = onTap,
                onDoubleTap = onDoubleTap,
                hapticEnabled = hapticEnabled,
                onLongPressStart = onLongPressStart, onLongPressDrag = onLongPressDrag, onLongPressEnd = onLongPressEnd
            ),
        shape = VlTheme.tokens.shapes.card,
        // Фон виден только под шапкой и подписью; у голого медиа его нет совсем
        color = if (hasHeader || caption != null) resolveBubbleColor(isMine) else Color.Transparent,
    ) {
        MediaColumn(
            header = {
                if (hasHeader) {
                    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                        if (senderName) {
                            Text(
                                "@${message.senderUsername}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                        reply?.let { ReplyPreview(reply = it, isMine = isMine, onClick = { it.id?.let(onReplyClick) }) }
                        forward?.let { ForwardBanner(forwardFrom = it, isMine = isMine, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            media = {
                Box {
                    media()
                    if (caption == null) {
                        MediaTimePill(
                            message = message, isMine = isMine, isReadByOther = isReadByOther, chatType = chatType,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        )
                    }
                    message.uploadProgress?.let { progress ->
                        UploadProgressOverlay(
                            progress = progress,
                            modifier = Modifier.matchParentSize(),
                            onCancel = onCancelUpload?.let { { it(message.id) } }
                        )
                    }
                }
            },
            footer = {
                if (caption != null) {
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp)) {
                            LinkifiedText(
                                text = caption, color = textColor, linkColor = resolveLinkColor(isMine),
                                onMentionClick = onMentionClick, onDoubleTap = onDoubleTap
                            )
                        }
                        DebugMessageBadge(message = message, textColor = textColor, modifier = Modifier.padding(horizontal = 12.dp))
                        MediaTimeRow(
                            message = message, isMine = isMine, isReadByOther = isReadByOther, chatType = chatType,
                            color = textColor,
                            modifier = Modifier.align(Alignment.End).padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 6.dp)
                        )
                    }
                }
            }
        )
    }
}

/** Медиа задаёт ширину; шапка и подпись растягиваются ровно до неё. */
@Composable
private fun MediaColumn(
    header: @Composable () -> Unit,
    media: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    Layout(contents = listOf(header, media, footer)) { (headerM, mediaM, footerM), constraints ->
        val mediaPlaceables = mediaM.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = mediaPlaceables.maxOfOrNull { it.width } ?: constraints.minWidth
        val fixed = Constraints(minWidth = width, maxWidth = width)
        val headerPlaceables = headerM.map { it.measure(fixed) }
        val footerPlaceables = footerM.map { it.measure(fixed) }
        val height = (headerPlaceables + mediaPlaceables + footerPlaceables).sumOf { it.height }
        layout(width, height) {
            var y = 0
            (headerPlaceables + mediaPlaceables + footerPlaceables).forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

private fun Message.timeText(): String =
    createdAt?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: ""

/** Время + статус обычной строкой под подписью. */
@Composable
private fun MediaTimeRow(
    message: Message, isMine: Boolean, isReadByOther: Boolean, chatType: ChatType,
    color: Color, modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (message.lastEdited != null) {
            Text("(изменено)", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.5f), fontSize = 10.sp)
        }
        Text(
            message.timeText(),
            style = MaterialTheme.typography.labelSmall.terminalData(), color = color.copy(alpha = 0.6f), fontSize = 10.sp,
        )
        if (isMine) MediaStatus(message, isReadByOther, chatType)
    }
}

/** Плашка времени поверх медиа: 20 dp, скругление 10 dp, полупрозрачная подложка, белый текст. */
@Composable
private fun MediaTimePill(
    message: Message, isMine: Boolean, isReadByOther: Boolean, chatType: ChatType,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(20.dp)
            .background(Color.Black.copy(alpha = 0.45f), VlTheme.tokens.shapes.rounded(10.dp))
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            message.timeText(),
            style = MaterialTheme.typography.labelSmall.terminalData(), color = Color.White, fontSize = 10.sp,
        )
        if (isMine) MediaStatus(message, isReadByOther, chatType)
    }
}

@Composable
private fun MediaStatus(message: Message, isReadByOther: Boolean, chatType: ChatType) {
    if (chatType == ChatType.DIRECT && message.status != SendStatus.QUEUED) ReadReceipt(isRead = isReadByOther)
    else MessageStatusIcon(status = message.status)
}
