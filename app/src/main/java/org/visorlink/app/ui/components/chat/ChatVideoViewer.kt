package org.visorlink.app.ui.components.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlTheme
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import java.io.File

@Composable
fun ChatVideoViewer(
    url: String?,
    thumbUrl: String? = null,
    localFile: File? = null,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val modelSource: Any? = localFile ?: thumbUrl ?: url

    Box(
        modifier = if (isFullscreen) {
            modifier
                .fillMaxSize()
                .background(Color.Black)
        } else {
            modifier
                .sizeIn(minWidth = 120.dp, minHeight = 120.dp, maxWidth = 280.dp, maxHeight = 500.dp)
                .clip(VlTheme.tokens.shapes.card)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        }.clickable { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        if (modelSource != null) {
            var isLoading by remember { mutableStateOf(true) }
            var isError by remember { mutableStateOf(false) }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(modelSource)
                    .apply {
                        if (thumbUrl == null && (localFile != null || (url != null && url.contains(".mp4")))) {
                            decoderFactory(VideoFrameDecoder.Factory())
                        }
                    }
                    .crossfade(true)
                    .build(),
                contentDescription = "Video Preview",
                contentScale = if (isFullscreen) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onLoading = { isLoading = true; isError = false },
                onSuccess = { isLoading = false; isError = false },
                onError = { isLoading = false; isError = true }
            )

            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(32.dp)
                )
            } else if (isError) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(32.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), VlTheme.tokens.shapes.indicator),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
