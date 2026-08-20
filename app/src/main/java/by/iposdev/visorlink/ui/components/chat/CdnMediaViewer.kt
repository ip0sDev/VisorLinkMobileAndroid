package by.iposdev.visorlink.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.iposdev.visorlink.data.model.MessageType
import by.iposdev.visorlink.utils.CdnService
import by.iposdev.visorlink.utils.ImageCache
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import java.io.File

@Composable
fun CdnMediaViewer(
    mediaId: String?,
    type: String,
    localFile: File? = null,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var resolvedUrl by remember { mutableStateOf<String?>(null) }

    // Резолвим URL для получения превью
    LaunchedEffect(mediaId) {
        if (mediaId != null) {
            try {
                val url = CdnService.getFileUrl(mediaId)
                val cached = ImageCache.getCachedPath(context, url)
                if (cached != null) {
                    resolvedUrl = cached.absolutePath
                } else if (type == MessageType.VIDEO || type == MessageType.GIF || type == MessageType.IMAGE) {
                    // Для всех медиа пытаемся подтянуть через наш кэш
                    resolvedUrl = try {
                        ImageCache.getOrDownload(context, url).absolutePath
                    } catch (e: Exception) {
                        url
                    }
                } else {
                    resolvedUrl = url
                }
            } catch (e: Exception) {
                // Ошибка резолва
            }
        }
    }

    val modelSource = localFile ?: resolvedUrl

    Box(
        modifier = modifier
            .sizeIn(minWidth = 120.dp, minHeight = 120.dp, maxWidth = 280.dp, maxHeight = 400.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        if (modelSource != null) {
            var isLoading by remember { mutableStateOf(true) }
            var isError by remember { mutableStateOf(false) }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(modelSource)
                    .apply {
                        if (type == MessageType.VIDEO) {
                            decoderFactory(VideoFrameDecoder.Factory())
                        }
                    }
                    .crossfade(true)
                    .build(),
                contentDescription = "Media Preview",
                contentScale = ContentScale.Crop,
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

        if (type == MessageType.VIDEO) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else if (type == MessageType.GIF) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "GIF",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
