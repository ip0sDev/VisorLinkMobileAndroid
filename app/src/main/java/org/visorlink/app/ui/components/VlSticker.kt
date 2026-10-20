package org.visorlink.app.ui.components

import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.visorlink.app.R
import org.visorlink.app.data.model.StickerFormat
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.ImageCache

/** Распакованный Lottie JSON больше этого — не парсим, остаётся постер (спека §3.5). */
private const val MAX_LOTTIE_BYTES = 1L shl 20

/**
 * Стикер любого формата (ANDROID_CAPTIONS_AND_ANIMATED_STICKERS_SPEC.md §3.4).
 *
 * - Играет только то, что скомпоновано: в LazyColumn/LazyGrid элемент вне экрана
 *   уничтожается вместе с плеером/композицией.
 * - «Убрать анимацию» → только постер (у gif/webp без постера — первый кадр).
 * - Пока Lottie/видео грузится — постер; ошибка → постер остаётся, без заглушки.
 */
@Composable
fun VlSticker(
    url: String?,
    format: String,
    previewUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    if (url.isNullOrBlank()) return
    var failed by remember(url) { mutableStateOf(false) }
    val failedText = stringResource(R.string.sticker_load_failed)
    val semanticsModifier = modifier.semantics {
        (if (failed) failedText else contentDescription)?.let { this.contentDescription = it }
    }

    if (format == StickerFormat.STATIC) {
        CachedImage(model = url, contentDescription = null, modifier = semanticsModifier, contentScale = ContentScale.Fit)
        return
    }

    val poster: @Composable () -> Unit = {
        if (!previewUrl.isNullOrBlank()) {
            CachedImage(
                model = previewUrl, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit, loading = {}, error = {}
            )
        }
    }

    Box(semanticsModifier, contentAlignment = Alignment.Center) {
        when {
            VlTheme.tokens.reduceMotion -> {
                if (previewUrl.isNullOrBlank() && (format == StickerFormat.GIF || format == StickerFormat.WEBP)) {
                    StaticFirstFrame(url)
                } else poster()
            }
            failed -> poster()
            format == StickerFormat.LOTTIE -> LottieSticker(url, poster) { failed = true }
            format == StickerFormat.WEBM -> WebmSticker(url, poster) { failed = true }
            // webp / gif: декодеры Coil из AppImageLoader анимируют сами
            else -> CachedImage(
                model = url, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit, loading = poster,
                error = { LaunchedEffect(Unit) { failed = true } }
            )
        }
    }
}

/** Первый кадр gif/webp без анимации: BitmapFactory анимацию не декодирует. */
@Composable
private fun StaticFirstFrame(url: String) {
    val context = LocalContext.current
    AsyncImage(
        model = remember(url) {
            ImageRequest.Builder(context).data(url).decoderFactory(BitmapFactoryDecoder.Factory()).build()
        },
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun LottieSticker(url: String, poster: @Composable () -> Unit, onFailed: () -> Unit) {
    val context = LocalContext.current
    // Файл с диска (ImageCache, по url — файлы стикеров неизменяемы); композиции Lottie
    // держит в своём LRU на 20 штук по cacheKey
    val json by produceState<String?>(null, url) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = ImageCache.getOrDownload(context, url)
                if (file.length() > MAX_LOTTIE_BYTES) return@withContext null
                val bytes = file.readBytes()
                // gzip (1F 8B) — TGS не распакован ботом: это баг импорта, показываем постер
                if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) null
                else String(bytes, Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }.also { if (it == null) onFailed() }
    }
    val spec = json?.let { LottieCompositionSpec.JsonString(it) }
    if (spec == null) {
        poster()
        return
    }
    val result = rememberLottieComposition(spec, cacheKey = "sticker:$url")
    val composition = result.value
    LaunchedEffect(result.isFailure) { if (result.isFailure) onFailed() }
    if (composition == null) {
        poster()
        return
    }
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
        enableMergePaths = false
    )
}

/** Видеостикер VP9: без звука и аудиофокуса, по кругу; постер до первого кадра. */
@Composable
private fun WebmSticker(url: String, poster: @Composable () -> Unit, onFailed: () -> Unit) {
    val context = LocalContext.current
    val file by produceState<java.io.File?>(null, url) {
        value = try { ImageCache.getOrDownload(context, url) } catch (_: Exception) { onFailed(); null }
    }
    var firstFrame by remember(url) { mutableStateOf(false) }
    if (!firstFrame) poster()
    val source = file ?: return

    val player = remember(source) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(source)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { firstFrame = true }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { onFailed() }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                player.setVideoTextureView(this)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
