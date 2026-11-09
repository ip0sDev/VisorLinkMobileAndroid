package org.visorlink.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest

/**
 * Размер (px) стороны, до которого декодируется скрытое (spoiler) изображение. Тихая версия
 * растягивается на весь размер с билинейной фильтрацией и выглядит размытой, при этом не нужен
 * RenderEffect-блюр: он тяжёл в ленте, а на Android 11 (minSdk 30) Modifier.blur не работает совсем —
 * спойлер там показывался бы чётким. Полная картинка в память не попадает, пока её не раскрыли.
 */
const val SPOILER_DECODE_PX = 24

@Composable
fun CachedImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    loading: @Composable (() -> Unit)? = null,
    error: @Composable (() -> Unit)? = null,
    /** Если задан — изображение декодируется в квадрат этой стороны (см. [SPOILER_DECODE_PX]). */
    decodeSize: Int? = null,
) {
    val context = LocalContext.current
    val isModelEmpty = model == null || (model is String && model.isBlank())

    if (isModelEmpty) {
        error?.invoke() ?: Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error)
        }
    } else {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .apply { if (decodeSize != null) size(decodeSize) }
                .build(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            filterQuality = if (decodeSize != null) FilterQuality.Low else FilterQuality.Medium,
            loading = {
                loading?.invoke() ?: Box(modifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            },
            success = {
                SubcomposeAsyncImageContent()
            },
            error = {
                error?.invoke() ?: Box(modifier, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}
