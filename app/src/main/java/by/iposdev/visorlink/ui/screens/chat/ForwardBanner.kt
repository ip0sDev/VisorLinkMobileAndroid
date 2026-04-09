package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.ForwardFrom

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