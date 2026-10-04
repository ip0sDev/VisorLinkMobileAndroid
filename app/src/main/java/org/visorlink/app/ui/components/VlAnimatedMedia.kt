package org.visorlink.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Универсальный компонент отображения анимированного медиа (Lottie, GIF) и обычных изображений.
 * Автоматически распознает тип медиа по URL, расширению или явным флагам.
 */
@Composable
fun VlAnimatedMedia(
    url: String?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    isLottie: Boolean? = null,
    placeholder: @Composable (() -> Unit)? = null,
    error: @Composable (() -> Unit)? = null,
) {
    if (url.isNullOrBlank()) {
        error?.invoke() ?: DefaultErrorIcon(modifier)
        return
    }

    val cleanUrl = url.substringBefore("?")
    val effectiveIsLottie = isLottie ?: (
        cleanUrl.endsWith(".json", ignoreCase = true) ||
        cleanUrl.endsWith(".lottie", ignoreCase = true)
    )

    if (effectiveIsLottie) {
        VlLottieView(
            url = url,
            modifier = modifier,
            contentScale = contentScale,
            placeholder = placeholder,
            error = error
        )
    } else {
        // GIF или статичное изображение через Coil (CachedImage с поддержкой GifDecoder)
        CachedImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            loading = placeholder,
            error = error
        )
    }
}

@Composable
fun VlLottieView(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: @Composable (() -> Unit)? = null,
    error: @Composable (() -> Unit)? = null,
) {
    val compositionResult = rememberLottieComposition(
        spec = LottieCompositionSpec.Url(url)
    )
    val composition = compositionResult.value
    val isLoading = compositionResult.isLoading
    val isFailed = compositionResult.isFailure

    when {
        isFailed -> {
            error?.invoke() ?: DefaultErrorIcon(modifier)
        }
        isLoading || composition == null -> {
            placeholder?.invoke() ?: Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        }
        else -> {
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever
            )
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = modifier,
                contentScale = contentScale
            )
        }
    }
}

@Composable
private fun DefaultErrorIcon(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(28.dp)
        )
    }
}
