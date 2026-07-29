package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.ForwardFrom
import by.iposdev.visorlink.data.model.Message

/**
 * Плашка «Переслано от @username · ChatName» для отображения в MessageBubble.
 * Размещается перед контентом сообщения.
 */
@Composable
fun ForwardBanner(
    forwardFrom: ForwardFrom,
    isMine: Boolean,
    isExthru: Boolean = false,
    isDark: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val accentColor = when {
        isExthru -> ExthruChat.Accent
        isMine   -> Color.White.copy(alpha = 0.85f)
        else     -> MaterialTheme.colorScheme.primary
    }
    val bgColor = when {
        isExthru -> accentColor.copy(alpha = 0.12f)
        isMine   -> Color.White.copy(alpha = 0.15f)
        else     -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }

    val label = buildString {
        append("↩ Переслано")
        if (forwardFrom.senderUsername.isNotBlank()) append(" от @${forwardFrom.senderUsername}")
        if (!forwardFrom.chatName.isNullOrBlank()) append(" · ${forwardFrom.chatName}")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(2.dp).height(14.dp)
                .background(accentColor, RoundedCornerShape(1.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    Spacer(Modifier.height(4.dp))
}

/**
 * Специальная плашка для сообщений, пересланных из Telegram бота.
 */
@Composable
fun TelegramForwardBanner(
    message: Message,
    modifier: Modifier = Modifier
) {
    val fallback = message.tg_forwarded_from_fallback ?: ""
    val username = message.tg_forwarded_from
    val name = fallback.ifEmpty { username ?: "Неизвестно" }
    val userPart = if (!username.isNullOrEmpty()) " (@$username)" else ""
    val label = "Переслано от $name$userPart"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .drawBehind {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        asFrameworkPaint().apply {
                            color = android.graphics.Color.TRANSPARENT
                            // Тень: 0px 4px 12px rgba(42, 171, 238, 0.35)
                            setShadowLayer(
                                12.dp.toPx(), 0f, 4.dp.toPx(),
                                android.graphics.Color.argb((0.35f * 255).toInt(), 42, 171, 238)
                            )
                        }
                    }
                    canvas.drawRoundRect(
                        0f, 0f, size.width, size.height,
                        6.dp.toPx(), 6.dp.toPx(), paint
                    )
                }
            }
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF2AABEE), Color(0xFF229ED9)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = TelegramIcon,
            contentDescription = "Telegram",
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// SVG иконка Telegram
private var _telegramIcon: ImageVector? = null
val TelegramIcon: ImageVector
    get() {
        if (_telegramIcon != null) return _telegramIcon!!
        _telegramIcon = ImageVector.Builder(
            name = "Telegram",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(16.64f, 8.8f)
                curveToRelative(-0.15f, 1.58f, -0.8f, 5.42f, -1.13f, 7.19f)
                curveToRelative(-0.14f, 0.75f, -0.42f, 1f, -0.68f, 1.03f)
                curveToRelative(-0.58f, 0.05f, -1.02f, -0.38f, -1.58f, -0.75f)
                curveToRelative(-0.88f, -0.58f, -1.38f, -0.94f, -2.23f, -1.5f)
                curveToRelative(-0.99f, -0.65f, -0.35f, -1.01f, 0.22f, -1.59f)
                curveToRelative(0.15f, -0.15f, 2.71f, -2.48f, 2.76f, -2.69f)
                arcToRelative(0.2f, 0.2f, 0f, false, false, -0.05f, -0.18f)
                curveToRelative(-0.06f, -0.05f, -0.14f, -0.03f, -0.21f, -0.02f)
                curveToRelative(-0.09f, 0.02f, -1.49f, 0.95f, -4.22f, 2.79f)
                curveToRelative(-0.4f, 0.27f, -0.76f, 0.41f, -1.08f, 0.4f)
                curveToRelative(-0.36f, -0.01f, -1.04f, -0.2f, -1.55f, -0.37f)
                curveToRelative(-0.63f, -0.2f, -1.12f, -0.31f, -1.08f, -0.66f)
                curveToRelative(0.02f, -0.18f, 0.27f, -0.36f, 0.74f, -0.55f)
                curveToRelative(2.92f, -1.27f, 4.86f, -2.11f, 5.83f, -2.51f)
                curveToRelative(2.78f, -1.16f, 3.35f, -1.36f, 3.73f, -1.36f)
                curveToRelative(0.08f, 0f, 0.27f, 0.02f, 0.39f, 0.12f)
                curveToRelative(0.1f, 0.08f, 0.13f, 0.19f, 0.14f, 0.27f)
                curveToRelative(-0.01f, 0.04f, 0.01f, 0.12f, 0f, 0.2f)
                close()
            }
        }.build()
        return _telegramIcon!!
    }