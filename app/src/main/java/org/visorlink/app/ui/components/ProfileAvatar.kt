package org.visorlink.app.ui.components

import android.util.Size
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import coil.compose.AsyncImage

@Composable
fun ProfileAvatar(avatarUrl: String, size: Dp) {
    AsyncImage(
        model = avatarUrl,
        contentDescription = "Аватар пользователя",
        modifier = Modifier
            .size(size)
            .clip(CircleShape),

        contentScale = ContentScale.Crop
    )
}