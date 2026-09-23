package org.visorlink.app.ui.components.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.visorlink.app.R
import org.visorlink.app.data.model.MusicTrack
import org.visorlink.app.ui.components.CachedImage
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.liquidPour
import org.visorlink.app.ui.components.rememberLiquidEnabled
import org.visorlink.app.ui.components.rememberLiquidJellyState
import org.visorlink.app.ui.components.rememberLiquidPourProgress
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.utils.MusicPlayerState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin

/** Край, к которому пришвартован док: задаёт направление перелива и отступы. */
enum class DockAnchor { Top, Bottom }

/**
 * Мини-плеер: плавающая капсула, одинаковая в чате и на вкладке музыки.
 *
 * Появляется «вливанием» из своего края и уходит «выливанием» обратно — на
 * время ухода последний трек удерживается в [retained], иначе состояние
 * обнуляется раньше, чем успевает проиграться анимация, и док просто мигает.
 *
 * Смахивание вбок — второй способ закрыть, кроме крестика.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioPlaybackDockBar(
    musicPlayback: MusicPlayerState,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: DockAnchor = DockAnchor.Top,
    visible: Boolean = true,
    onSeek: ((Float) -> Unit)? = null,
    onNext: (() -> Unit)? = null
) {
    val track = musicPlayback.currentTrack
    var retained by remember { mutableStateOf(track) }
    LaunchedEffect(track) { if (track != null) retained = track }
    val shown = track ?: retained

    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val isLiquidEnabled = rememberLiquidEnabled()
    // Волна — жидкостный эффект, Forge с его прямыми углами и линейной механикой её не принимает
    val wavy = isLiquidEnabled && !tokens.isForge
    val fromTop = anchor == DockAnchor.Top

    val isVisible = visible && track != null
    val pour = rememberLiquidPourProgress(visible = isVisible, liquid = wavy)

    if (shown == null) return
    if (!isVisible && pour <= 0.002f) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismissPx = remember(density) { with(density) { 110.dp.toPx() } }
    val dragX = remember { Animatable(0f) }

    val dockJelly = rememberLiquidJellyState(softness = 0.05f)
    val playJelly = rememberLiquidJellyState(softness = 0.16f)
    val nextJelly = rememberLiquidJellyState(softness = 0.14f)
    val closeJelly = rememberLiquidJellyState(softness = 0.14f)

    val isPlaying = musicPlayback.isPlaying
    val isDark = cs.surface.luminance() < 0.5f

    val dockShape: Shape = if (tokens.isForge) tokens.shapes.card else RoundedCornerShape(26.dp)

    val dockBrush = remember(isDark, cs, tokens.isForge) {
        if (tokens.isForge) {
            null
        } else {
            Brush.verticalGradient(
                listOf(
                    if (isDark) cs.surfaceContainerHigh.copy(alpha = 0.97f) else cs.surfaceContainerLowest.copy(alpha = 0.99f),
                    if (isDark) cs.surfaceContainer.copy(alpha = 0.94f) else cs.surfaceContainerHigh.copy(alpha = 0.96f)
                )
            )
        }
    }

    val dockBorder = remember(isDark, cs, tokens.isForge) {
        if (tokens.isForge) {
            null
        } else {
            BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        if (isDark) cs.outlineVariant.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.7f),
                        if (isDark) cs.outlineVariant.copy(alpha = 0.05f) else cs.outlineVariant.copy(alpha = 0.16f)
                    )
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidPour(progress = pour, fromTop = fromTop, accent = cs.primary, waves = wavy)
            .padding(
                horizontal = if (tokens.isForge) 8.dp else 12.dp,
                vertical = 6.dp
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val drag = dragX.value
                    translationX = drag
                    // Капсула тянется вдоль пальца, как капля перед отрывом
                    val stretch = (abs(drag) / dismissPx).coerceIn(0f, 1f) * 0.06f
                    scaleX = 1f + stretch
                    scaleY = 1f - stretch * 0.55f
                    alpha = 1f - (abs(drag) / (dismissPx * 2.6f)).coerceIn(0f, 0.55f)
                }
                .liquidJelly(dockJelly, enabled = isLiquidEnabled)
                .then(
                    if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, dockShape)
                    else Modifier
                )
                .clip(dockShape)
                .then(if (dockBrush != null) Modifier.background(dockBrush, dockShape) else Modifier.background(cs.surfaceContainerHigh, dockShape))
                .then(
                    when {
                        dockBorder != null -> Modifier.border(dockBorder, dockShape)
                        tokens.structure.enabled -> Modifier.vlHairline(cs.outlineVariant, dockShape)
                        else -> Modifier
                    }
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(dragX.value) > dismissPx) {
                                    dragX.animateTo(sign(dragX.value) * size.width * 1.1f, tween(170))
                                    onClose()
                                    dragX.snapTo(0f)
                                } else {
                                    dragX.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 420f))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.6f)) } }
                    ) { change, drag ->
                        change.consume()
                        val limit = dismissPx * 1.7f
                        scope.launch { dragX.snapTo((dragX.value + drag).coerceIn(-limit, limit)) }
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isLiquidEnabled) dockJelly.pulse(0.06f)
                    onOpenFullscreen()
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockArtwork(
                        track = shown,
                        isPlaying = isPlaying,
                        accent = cs.primary,
                        shape = if (tokens.isForge) tokens.shapes.avatar else RoundedCornerShape(15.dp)
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shown.displayTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(iterations = if (isPlaying) Int.MAX_VALUE else 0)
                        )
                        Spacer(Modifier.height(1.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPlaying) {
                                DockEqualizer(color = cs.primary, animated = !tokens.reduceMotion)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = shown.displayPerformer,
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (musicPlayback.durationMs > 0) {
                                Text(
                                    text = "  ${formatDockTime(musicPlayback.currentMs)}",
                                    style = tokens.data.dataSmall,
                                    color = cs.onSurfaceVariant.copy(alpha = 0.55f),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Play / Pause — единственная заливка акцентом в доке
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .liquidJelly(playJelly, enabled = isLiquidEnabled)
                            .clip(if (tokens.isForge) tokens.shapes.button else CircleShape)
                            .background(cs.primary)
                            .clickable {
                                if (isLiquidEnabled) playJelly.pulse(0.18f)
                                onTogglePlayPause()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (musicPlayback.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = cs.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.music_mini_pause else R.string.music_mini_play
                                ),
                                tint = cs.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (onNext != null && musicPlayback.playlist.size > 1) {
                        Spacer(Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .liquidJelly(nextJelly, enabled = isLiquidEnabled)
                                .clip(CircleShape)
                                .clickable {
                                    if (isLiquidEnabled) nextJelly.pulse(0.16f)
                                    onNext()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = stringResource(R.string.music_mini_next),
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(2.dp))

                    // Крестик закрывает плеер целиком; фон-кружок делает цель очевидной
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .liquidJelly(closeJelly, enabled = isLiquidEnabled)
                            .clip(if (tokens.isForge) tokens.shapes.button else CircleShape)
                            .background(cs.onSurface.copy(alpha = 0.07f))
                            .clickable {
                                if (isLiquidEnabled) closeJelly.pulse(0.16f)
                                onClose()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.music_mini_close),
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                DockProgressTrack(
                    progress = musicPlayback.progress,
                    isPlaying = isPlaying,
                    wavy = wavy && !tokens.reduceMotion,
                    accent = cs.primary,
                    accentTail = cs.tertiary,
                    trackColor = cs.onSurface.copy(alpha = 0.10f),
                    onSeek = onSeek,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 4.dp)
                )
            }
        }
    }
}

/** Обложка с виниловым диском, который выезжает из-за неё на время воспроизведения. */
@Composable
private fun DockArtwork(
    track: MusicTrack,
    isPlaying: Boolean,
    accent: Color,
    shape: Shape
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens

    val spin = rememberInfiniteTransition(label = "dock_vinyl")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "dock_vinyl_angle"
    )
    val rotation = if (isPlaying && !tokens.reduceMotion) angle else 0f
    val peek by animateDpAsState(
        targetValue = if (isPlaying) 17.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 260f),
        label = "dock_vinyl_peek"
    )

    Box(modifier = Modifier.size(width = 64.dp, height = 48.dp)) {
        Canvas(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.CenterStart)
                .offset(x = peek)
                .rotate(rotation)
        ) {
            val radius = size.minDimension / 2f
            drawCircle(color = Color(0xFF17171A), radius = radius)
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius * 0.78f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = radius * 0.58f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(color = accent.copy(alpha = 0.85f), radius = radius * 0.22f)
            drawCircle(color = Color(0xFF17171A), radius = radius * 0.07f)
        }

        val cover = track.coverUrl
        Box(
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterStart)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.35f),
                            cs.tertiary.copy(alpha = 0.30f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!cover.isNullOrBlank()) {
                CachedImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = cs.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** Три столбика эквалайзера рядом с исполнителем — маркер «сейчас звучит». */
@Composable
private fun DockEqualizer(color: Color, animated: Boolean) {
    val transition = rememberInfiniteTransition(label = "dock_eq")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "dock_eq_phase"
    )
    Canvas(modifier = Modifier.size(width = 11.dp, height = 10.dp)) {
        val barWidth = 2.dp.toPx()
        val gap = (size.width - barWidth * 3f) / 2f
        repeat(3) { i ->
            val wave = if (animated) (sin(phase + i * 1.1f) + 1f) / 2f else 0.55f
            val height = size.height * (0.3f + wave * 0.7f)
            val x = i * (barWidth + gap)
            drawRoundRect(
                color = color.copy(alpha = 0.85f),
                topLeft = Offset(x, size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

/**
 * Прогресс как текущая по капилляру жидкость: волна в проигранной части,
 * капля-головка на позиции. Перемотка тапом и протяжкой.
 */
@Composable
private fun DockProgressTrack(
    progress: Float,
    isPlaying: Boolean,
    wavy: Boolean,
    accent: Color,
    accentTail: Color,
    trackColor: Color,
    onSeek: ((Float) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "dock_flow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "dock_flow_phase"
    )

    var scrub by remember { mutableStateOf<Float?>(null) }
    val shownProgress = (scrub ?: progress).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(16.dp)
            .then(
                if (onSeek == null) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            onSeek((offset.x / size.width).coerceIn(0f, 1f))
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> scrub = (offset.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = {
                                scrub?.let(onSeek)
                                scrub = null
                            },
                            onDragCancel = { scrub = null }
                        ) { change, drag ->
                            change.consume()
                            scrub = ((scrub ?: progress) + drag / size.width).coerceIn(0f, 1f)
                        }
                    }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val stroke = 4.dp.toPx()
            val startX = stroke / 2f
            val endX = size.width - stroke / 2f
            if (endX <= startX) return@Canvas

            drawLine(
                color = trackColor,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            val headX = startX + (endX - startX) * shownProgress
            val brush = Brush.horizontalGradient(
                colors = listOf(accentTail.copy(alpha = 0.85f), accent),
                startX = startX,
                endX = headX.coerceAtLeast(startX + 1f)
            )

            if (headX > startX + 0.5f) {
                if (wavy) {
                    val amplitude = if (isPlaying) 2.2.dp.toPx() else 0.7.dp.toPx()
                    val path = Path()
                    val step = 3f
                    var x = startX
                    path.moveTo(startX, centerY)
                    while (x < headX) {
                        path.lineTo(x, centerY + amplitude * sin(x / 13f + phase))
                        x += step
                    }
                    path.lineTo(headX, centerY + amplitude * sin(headX / 13f + phase))
                    drawPath(
                        path = path,
                        brush = brush,
                        style = Stroke(
                            width = stroke,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                } else {
                    drawLine(
                        brush = brush,
                        start = Offset(startX, centerY),
                        end = Offset(headX, centerY),
                        strokeWidth = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }

            // Головка потока: капля на текущей позиции
            if (isPlaying || scrub != null) {
                drawCircle(
                    color = accent.copy(alpha = 0.16f),
                    radius = 7.dp.toPx(),
                    center = Offset(headX, centerY)
                )
            }
            drawCircle(
                color = accent,
                radius = if (scrub != null) 5.dp.toPx() else 3.5.dp.toPx(),
                center = Offset(headX, centerY)
            )
        }
    }
}

private fun formatDockTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
