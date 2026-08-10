package by.iposdev.visorlink.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import by.iposdev.visorlink.utils.ImageCache
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun CachedImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    loading: @Composable (() -> Unit)? = null,
    error: @Composable (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var modelSource by remember(model) { mutableStateOf<Any?>(model) }

    LaunchedEffect(model) {
        if (model is String && model.isNotEmpty()) {
            val cached = ImageCache.getCachedPath(context, model)
            if (cached != null) {
                modelSource = cached
            } else {
                try {
                    val file = ImageCache.getOrDownload(context, model)
                    modelSource = file
                } catch (e: Exception) {
                    modelSource = model
                }
            }
        } else {
            modelSource = model
        }
    }

    if (modelSource == null) {
        error?.invoke() ?: Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error)
        }
    } else {
        var isLoading by remember { mutableStateOf(true) }
        var isError by remember { mutableStateOf(false) }

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(modelSource)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            onLoading = { isLoading = true; isError = false },
            onSuccess = { isLoading = false; isError = false },
            onError = { isLoading = false; isError = true }
        )

        if (isLoading) {
            loading?.invoke() ?: Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (isError) {
            error?.invoke() ?: Box(modifier, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
