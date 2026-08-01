package by.iposdev.visorlink.ui.screens.chat

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import by.iposdev.visorlink.utils.CdnService
import kotlinx.coroutines.delay
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun CdnMediaViewer(
    mediaId: String?,
    type: String,
    localFile: File? = null,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var hasError by remember { mutableStateOf(false) }

    val isGif = type == "gif"
    var wantsToLoad by remember { mutableStateOf(isFullscreen || isGif) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var videoProgress by remember { mutableFloatStateOf(0f) }

    // ─── 1. Резолвим URL (CDN или локальный) ──────────────────────────────────
    LaunchedEffect(mediaId) {
        if (mediaId != null && (type == "video" || type == "gif")) {
            try {
                resolvedUrl = CdnService.getFileUrl(mediaId)
            } catch (e: Exception) {
                hasError = true
            }
        }
    }

    // ─── 2. Инициализация ExoPlayer ───────────────────────────────────────────
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = if (isGif) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    // ─── 3. Управление Жизненным Циклом (Lifecycle) ───────────────────────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // ─── 4. Подписка на состояние плеера ──────────────────────────────────────
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    showControls = true
                    isPlaying = false
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                hasError = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // ─── 5. Обновление прогресс-бара ──────────────────────────────────────────
    LaunchedEffect(isPlaying, exoPlayer) {
        while (isPlaying) {
            val duration = exoPlayer.duration.coerceAtLeast(1)
            videoProgress = (exoPlayer.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            delay(50) // Плавное обновление
        }
    }

    // ─── 6. Автоматическое скрытие контролов ──────────────────────────────────
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(2500)
            showControls = false
        }
    }

    // ─── 7. Загрузка медиа при нажатии ────────────────────────────────────────
    LaunchedEffect(wantsToLoad, resolvedUrl, localFile) {
        if (wantsToLoad) {
            val uri = when {
                localFile != null -> Uri.fromFile(localFile)
                resolvedUrl != null -> Uri.parse(resolvedUrl)
                else -> null
            }
            if (uri != null && exoPlayer.mediaItemCount == 0) {
                exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        }
    }

    // ─── UI: ОШИБКА ───────────────────────────────────────────────────────────
    if (hasError) {
        Box(
            modifier = modifier
                .sizeIn(minWidth = 120.dp, minHeight = 120.dp, maxWidth = 280.dp, maxHeight = 400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.VideocamOff, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(4.dp))
                Text("Медиа недоступно", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
        return
    }

    // ─── UI: ПЛЕЕР ────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            // Плеер сам подгонит пропорции внутри этих рамок!
            .sizeIn(minWidth = 120.dp, minHeight = 120.dp, maxWidth = 280.dp, maxHeight = 400.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.2f)) // Плейсхолдер до старта
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!wantsToLoad) {
                    wantsToLoad = true
                    showControls = true
                } else if (!isGif) {
                    showControls = !showControls
                }
            }
    ) {
        // Сам ExoPlayer View
        if (wantsToLoad) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // Отключаем уродливые нативные контролы
                        // RESIZE_MODE_FIT гарантирует, что видео не обрежется и сохранит пропорции
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ─── КАСТОМНЫЙ ОВЕРЛЕЙ УПРАВЛЕНИЯ НА COMPOSE ──────────────────────────
        if (!isGif) {
            AnimatedVisibility(
                visible = showControls || !wantsToLoad,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.matchParentSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering && wantsToLoad) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                    } else {
                        // Кнопка Play/Pause
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    if (!wantsToLoad) {
                                        wantsToLoad = true
                                    } else {
                                        if (isPlaying) exoPlayer.pause() else {
                                            if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0)
                                            exoPlayer.play()
                                        }
                                    }
                                    showControls = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Полоса прогресса внизу
                    if (wantsToLoad) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { videoProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}