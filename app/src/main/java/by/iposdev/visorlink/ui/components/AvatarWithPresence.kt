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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun AvatarWithPresence(
    avatarUrl: String?,
    displayName: String,
    isOnline: Boolean,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size)) {
        if (!avatarUrl.isNullOrEmpty()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "$displayName avatar",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = displayName.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = (size.value * 0.38f).sp
                    )
                }
            }
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(Color(0xFF22C55E), CircleShape)
            )
        }
    }
}