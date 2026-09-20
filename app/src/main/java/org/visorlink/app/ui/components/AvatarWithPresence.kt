package org.visorlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlBiopulse

/**
 * Аватар с индикатором присутствия.
 *
 * Статус «онлайн» — live-состояние, поэтому в Biolume точка пульсирует
 * («биопульс», §6); в MATERIAL3 остаётся статичной. Цвет берётся из
 * `tokens.status.success`, а не хардкодится: success — не роль M3.
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
            Surface(
                shape = shape,
                color = cs.primaryContainer,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val isFaulty = isFaultyWireBot || displayName.contains("FaultyWire", ignoreCase = true)
                    if (isFaulty) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "FaultyWire Bot",
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(size * 0.55f)
                        )
                    } else {
                        Text(
                            text = displayName.firstOrNull()?.uppercase() ?: "?",
                            color = cs.onPrimaryContainer,
                            fontSize = (size.value * 0.38f).sp,
                        )
                    }
                }
            }
        }

        if (isOnline) {
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
