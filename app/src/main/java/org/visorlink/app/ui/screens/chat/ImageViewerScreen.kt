package org.visorlink.app.ui.screens.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.visorlink.app.utils.ImageCache
import org.visorlink.app.utils.VideoCache
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.visorlink.app.R
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.common.VideoSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    url: String,
    type: String = "image",
    isSecure: Boolean = false,
    onNavigateBack: () -> Unit
) {
    org.visorlink.app.ui.components.SecureScreen(enabled = isSecure) {
        val isVideo = type == org.visorlink.app.data.model.MessageType.VIDEO ||
                type == org.visorlink.app.data.model.MessageType.GIF ||
                url.endsWith(".mp4", ignoreCase = true) ||
                url.endsWith(".webm", ignoreCase = true)

        if (isVideo) {
            FullscreenVideoPlayer(url = url, type = type, onNavigateBack = onNavigateBack)
        } else {
            FullscreenImageViewer(url = url, onNavigateBack = onNavigateBack)
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FullscreenVideoPlayer(url: String, type: String, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }

    val offsetY = remember { androidx.compose.animation.core.Animatable(0f) }
    val maxDragDistance = 320.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val maxDragPx = with(density) { maxDragDistance.toPx() }

    var isSaving by remember { mutableStateOf(false) }

    val streamableUrl = remember(url) { VideoCache.getStreamableVideoUrl(url) }
    val cachedFile = remember(streamableUrl) { VideoCache.getCachedPath(context, streamableUrl) }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(30000)

        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(defaultDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                repeatMode = if (type == "gif") Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            }
    }

    val handleBack: () -> Unit = {
        try {
            exoPlayer.stop()
        } catch (_: Exception) {}
        onNavigateBack()
    }

    androidx.activity.compose.BackHandler(onBack = handleBack)

    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        exoPlayer.release()
                    } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    DisposableEffect(streamableUrl, cachedFile) {
        val playUri = if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            Uri.fromFile(cachedFile)
        } else {
            Uri.parse(streamableUrl)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(playUri)
            .apply {
                if (playUri.scheme == "http" || playUri.scheme == "https") {
                    setMimeType(MimeTypes.VIDEO_MP4)
                }
            }
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) hasError = false
                if (state == Player.STATE_ENDED) {
                    showControls = true
                    isPlaying = false
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                isBuffering = false
            }
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val unappliedRotation = videoSize.unappliedRotationDegrees
                    val width = if (unappliedRotation == 90 || unappliedRotation == 270) videoSize.height else videoSize.width
                    val height = if (unappliedRotation == 90 || unappliedRotation == 270) videoSize.width else videoSize.height
                    videoAspectRatio = width.toFloat() / height.toFloat()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Опрашиваем прогресс ТОЛЬКО когда видны элементы управления и идет воспроизведение
    LaunchedEffect(isPlaying, isBuffering, showControls) {
        if (!showControls && !isPlaying) return@LaunchedEffect
        while (isActive && isPlaying && showControls) {
            if (exoPlayer.duration > 0) {
                duration = exoPlayer.duration
                position = exoPlayer.currentPosition
                progress = (position.toFloat() / duration).coerceIn(0f, 1f)
            }
            delay(200)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    val currentOffset = offsetY.value
    val dragFraction = (currentOffset / maxDragPx).coerceIn(0f, 1f)
    val backgroundAlpha = (1f - dragFraction * 0.9f).coerceIn(0f, 1f)
    val videoScale = (1f - dragFraction * 0.25f).coerceIn(0.75f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetY.value > maxDragPx * 0.45f) {
                                try {
                                    exoPlayer.stop()
                                } catch (_: Exception) {}
                                launch {
                                    offsetY.animateTo(
                                        maxDragPx * 1.5f,
                                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                                    )
                                }
                                delay(100)
                                onNavigateBack()
                            } else {
                                offsetY.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            offsetY.animateTo(0f)
                        }
                    },
                    onVerticalDrag = { change: PointerInputChange, dragAmount: Float ->
                        if (dragAmount > 0 || offsetY.value > 0) {
                            change.consume()
                            val newOffset = (offsetY.value + dragAmount).coerceAtLeast(0f)
                            coroutineScope.launch {
                                offsetY.snapTo(newOffset)
                            }
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = currentOffset
                    scaleX = videoScale
                    scaleY = videoScale
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        exoPlayer.setVideoTextureView(this)
                    }
                },
                onRelease = { view ->
                    exoPlayer.clearVideoTextureView(view)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspectRatio)
            )

            // Кликабельный оверлей переключения контролов
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (offsetY.value == 0f) {
                            showControls = !showControls
                        }
                    }
            )

            // Анимация полной загрузки перед показом (Оверлей)
            AnimatedVisibility(visible = isBuffering, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp, modifier = Modifier.size(64.dp))
                }
            }

            // Ошибка с кнопкой "Повторить попытку"
            if (hasError) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Ошибка загрузки видео", color = Color.White, fontSize = 16.sp)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = {
                            hasError = false
                            isBuffering = true
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        }) {
                            Text("Повторить попытку")
                        }
                    }
                }
            }

            // Контроллы видео
            AnimatedVisibility(
                visible = showControls && !hasError && offsetY.value < 40f,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.matchParentSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(0.6f), Color.Transparent))))
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f)))))

                    TopAppBar(
                        title = {},
                        navigationIcon = {
                            IconButton(onClick = handleBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                            }
                        },
                        actions = {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp).padding(end = 16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        isSaving = true
                                        val success = VideoCache.saveVideoToGallery(context, streamableUrl)
                                        isSaving = false
                                        Toast.makeText(
                                            context,
                                            if (success) "Видео сохранено в галерею" else "Ошибка сохранения",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Download, "Сохранить", tint = Color.White)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )

                    if (!isBuffering && type != "gif") {
                        Box(
                            modifier = Modifier.align(Alignment.Center).size(64.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).clickable {
                                if (isPlaying) exoPlayer.pause() else {
                                    if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0)
                                    exoPlayer.play()
                                }
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    if (type != "gif") {
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatVideoTime(position), color = Color.White, fontSize = 12.sp)
                            Slider(
                                value = progress,
                                onValueChange = { p ->
                                    progress = p
                                    val newPos = (p * duration).toLong()
                                    exoPlayer.seekTo(newPos)
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            Text(formatVideoTime(duration), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatVideoTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullscreenImageViewer(url: String, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    var retryHash by remember { mutableIntStateOf(0) }
    var modelSource by remember(url, retryHash) { mutableStateOf<Any?>(url) }

    LaunchedEffect(url, retryHash) {
        val cached = ImageCache.getCachedPath(context, url)
        if (cached != null) {
            modelSource = cached
        } else {
            try {
                modelSource = ImageCache.getOrDownload(context, url)
            } catch (e: Exception) {
                modelSource = url
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(modelSource)
                .setParameter("retry_hash", retryHash)
                .crossfade(true)
                .build()
        )

        val painterState = painter.state

        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }
                            when {
                                pointers.size >= 2 -> {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                    scale = newScale
                                    offset = if (newScale > 1f) Offset(offset.x + panChange.x, offset.y + panChange.y) else Offset.Zero
                                    event.changes.forEach { it.consume() }
                                }
                                pointers.size == 1 && scale > 1f -> {
                                    val panChange = event.calculatePan()
                                    offset = Offset(offset.x + panChange.x, offset.y + panChange.y)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300L) {
                            scale = if (scale > 1.5f) 1f else 2.5f
                            if (scale <= 1f) offset = Offset.Zero
                        } else {
                            showControls = !showControls
                        }
                        lastTapTime = now
                    }
                }
        )

        when (painterState) {
            is AsyncImagePainter.State.Loading -> {
                CircularProgressIndicator(
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.2f), strokeWidth = 3.dp, modifier = Modifier.size(48.dp)
                )
            }
            is AsyncImagePainter.State.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.image_viewer_load_error), color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { retryHash++ }) {
                        Text("Повторить попытку")
                    }
                }
            }
            else -> {}
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = Color.White) }
                },
                title = {},
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            scope.launch {
                                isSaving = true
                                val success = ImageCache.saveImageToGallery(context, url)
                                isSaving = false
                                Toast.makeText(context, if (success) "Saved to gallery" else "Failed to save", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Download, stringResource(R.string.image_viewer_save), tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f))
            )
        }
    }
}
