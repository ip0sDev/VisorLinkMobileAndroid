package org.visorlink.app.ui.components.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.data.model.Chat
import org.visorlink.app.data.model.Message
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic

@Composable
fun CommentsButton(
    post: Message,
    channelAllowsComments: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allowed = channelAllowsComments && post.commentsEnabled != false
    val contentAlpha = if (allowed) 1f else 0.4f

    val haptic = rememberHaptic()
    val label = when {
        !allowed -> "Comments off"
        post.commentsCount == 0 -> "Leave a comment"
        post.commentsCount == 1 -> "1 comment"
        else -> "${post.commentsCount} comments"
    }

    // Forge v2: не отдельная плашка под постом, а строка-ссылка терминала. Коробка под каждым
    // постом спорила с контуром самого пузыря
    if (VlTheme.tokens.structure.hardShadow != null) {
        val color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
        Row(
            modifier = modifier
                .padding(top = 6.dp, start = 4.dp)
                .clickable(enabled = allowed) {
                    haptic.perform(HapticType.CLICK, true)
                    onClick()
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("> " + label.uppercase(), style = org.visorlink.app.ui.theme.ForgeV2SectionLabel, color = color)
            if (allowed) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(14.dp), tint = color)
            }
        }
        return
    }

    Surface(
        onClick = {
            haptic.perform(HapticType.CLICK, true)
            onClick()
        },
        enabled = allowed,
        shape = VlTheme.tokens.shapes.chip,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (allowed) 0.6f else 0.2f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .padding(top = 4.dp)
            .widthIn(max = 270.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ChatBubbleOutline, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    fontWeight = FontWeight.Bold
                )
            }
            if (allowed) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
