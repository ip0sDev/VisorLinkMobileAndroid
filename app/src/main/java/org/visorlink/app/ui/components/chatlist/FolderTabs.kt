package org.visorlink.app.ui.components.chatlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.data.model.ChatFolder
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlInset

/**
 * Горизонтальная полоска папок чатов над списком диалогов (PRO-возможность).
 * Вкладка «Все», папки с эмодзи и счётчиками непрочитанных, кнопка настройки.
 */
@Composable
fun FolderTabs(
    folders: List<ChatFolder>,
    activeFolderId: String?,
    unreadByFolder: Map<String, Int>,
    totalUnread: Int,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FolderTabChip(
            title = "Все",
            emoji = "",
            badgeCount = totalUnread,
            selected = activeFolderId == null,
            onClick = { onSelect(null) }
        )

        for (folder in folders) {
            val count = unreadByFolder[folder.id] ?: 0
            FolderTabChip(
                title = folder.name,
                emoji = folder.emoji,
                badgeCount = count,
                selected = activeFolderId == folder.id,
                onClick = { onSelect(folder.id) }
            )
        }

        FolderManageChip(
            hasFolders = folders.isNotEmpty(),
            onClick = onManage
        )
    }
}

@Composable
private fun FolderTabChip(
    title: String,
    emoji: String,
    badgeCount: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val dark = tokens.isDark
    val pill = tokens.shapes.pill

    val bgColor by animateColorAsState(
        targetValue = when {
            selected -> tokens.selectionFill
            tokens.structure.enabled -> if (dark) cs.surfaceContainer.copy(alpha = 0.82f) else cs.surfaceContainerLow.copy(alpha = 0.92f)
            else -> cs.surfaceContainerHighest.copy(alpha = 0.45f)
        },
        animationSpec = tween(200),
        label = "tab_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.onSurfaceVariant,
        animationSpec = tween(200),
        label = "tab_content_color"
    )

    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(pill)
            .background(bgColor, pill)
            .vlInset(tokens.structure, pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (emoji.isNotEmpty()) {
            Text(text = emoji, fontSize = 14.sp)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )

        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) cs.primary else cs.surfaceContainerHighest)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) cs.onPrimary else cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolderManageChip(
    hasFolders: Boolean,
    onClick: () -> Unit
) {
    val tokens = VlTheme.tokens
    val cs = MaterialTheme.colorScheme
    val pill = tokens.shapes.pill

    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(pill)
            .background(cs.surfaceContainerHighest.copy(alpha = 0.35f), pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (hasFolders) Icons.Default.Settings else Icons.Default.Add,
            contentDescription = "Управление папками",
            modifier = Modifier.size(16.dp),
            tint = cs.onSurfaceVariant
        )
        if (!hasFolders) {
            Text(
                text = "Папка",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}
