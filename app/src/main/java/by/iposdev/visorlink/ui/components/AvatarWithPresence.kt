package by.iposdev.visorlink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AvatarWithPresence(
    avatarUrl: String?,
    displayName: String,
    isOnline: Boolean,
    size: Dp = 48.dp,
    isForge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = if (isForge) RectangleShape else CircleShape

    Box(modifier = modifier.size(size)) {
        if (!avatarUrl.isNullOrEmpty()) {
            CachedImage(
                model = avatarUrl,
                contentDescription = "$displayName avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .then(if (isForge) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isForge) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = displayName.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = (size.value * 0.38f).sp,
                        fontFamily = if (isForge) FontFamily.Monospace else null
                    )
                }
            }
        }
        if (isOnline) {
            val indicatorShape = if (isForge) RectangleShape else CircleShape
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .border(
                        width = if (isForge) 1.5.dp else 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = indicatorShape
                    )
                    .background(Color(0xFF22C55E), indicatorShape)
            )
        }
    }
}