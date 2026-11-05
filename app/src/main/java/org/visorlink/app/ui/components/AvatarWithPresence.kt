package org.visorlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.ui.components.led.VlLedDot
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlBiopulse

/**
 * Аватар с индикатором присутствия (порт Flutter `_AvatarWithPresence` в `vl_chat_list_item.dart`).
 *
 * Статус «онлайн» — live-состояние:
 *  - В Forge (`tokens.led != null`) — яркий светодиод `VlLedDot` с ореолом;
 *  - В Biolume — точка пульсирует («биопульс», §6);
 *  - В MATERIAL3 — статичная точка с обводкой.
 */
@Composable
fun AvatarWithPresence(
    avatarUrl: String?,
    displayName: String,
    isOnline: Boolean,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    isFaultyWireBot: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = tokens.shapes.avatar

    Box(modifier = modifier.size(size)) {
        if (!avatarUrl.isNullOrEmpty()) {
            CachedImage(
                model = avatarUrl,
                contentDescription = "$displayName avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                contentScale = ContentScale.Crop
            )
        } else {
            val bgBrush = Brush.linearGradient(
                listOf(cs.primaryContainer, cs.secondaryContainer)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(bgBrush),
                contentAlignment = Alignment.Center
            ) {
                val isFaulty = isFaultyWireBot || displayName.contains("FaultyWire", ignoreCase = true)
                if (isFaulty) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "FaultyWire Bot",
                        tint = cs.onSecondaryContainer,
                        modifier = Modifier.size(size * 0.55f)
                    )
                } else {
                    Text(
                        text = displayName.firstOrNull()?.uppercase() ?: "?",
                        color = cs.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.38f).sp,
                    )
                }
            }
        }

        if (isOnline) {
            if (tokens.led != null) {
                val dotSize = size * 0.25f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 1.dp, y = 1.dp)
                ) {
                    VlLedDot(
                        size = dotSize,
                        color = tokens.status.success,
                    )
                }
            } else {
                val dotSize = size * 0.28f
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .align(Alignment.BottomEnd)
                        // Пульс рисуется до обводки, чтобы свечение шло из-под точки.
                        .vlBiopulse(
                            tokens = tokens,
                            color = tokens.status.success,
                            shape = tokens.shapes.indicator,
                        )
                        .border(width = 2.dp, color = cs.surface, shape = tokens.shapes.indicator)
                        .background(tokens.status.success, tokens.shapes.indicator)
                )
            }
        }
    }
}
