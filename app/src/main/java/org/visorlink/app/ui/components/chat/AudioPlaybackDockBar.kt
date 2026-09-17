package org.visorlink.app.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.ui.components.CachedImage
import org.visorlink.app.ui.theme.VlTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.utils.MusicPlayerState

@Composable
fun AudioPlaybackDockBar(
    musicPlayback: MusicPlayerState,
    onTogglePlayPause: () -> Unit,
    onClose: () -> Unit,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    isFloating: Boolean = false
) {
    val track = musicPlayback.currentTrack

    AnimatedVisibility(
        visible = track != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        if (track == null) return@AnimatedVisibility

        val cs = MaterialTheme.colorScheme
        val tokens = VlTheme.tokens
        val dockShape: Shape = if (isFloating) {
            if (tokens.isForge) tokens.shapes.card else RoundedCornerShape(24.dp)
        } else {
            tokens.shapes.card
        }

        val isPlaying = musicPlayback.isPlaying
        val isLoading = musicPlayback.isLoading

        val infiniteTransition = rememberInfiniteTransition(label = "dock_vinyl")
        val spinAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3500, easing = LinearEasing)
            ),
            label = "spin_angle"
        )
        val vinylRotation = if (isPlaying) spinAngle else 0f

        val surfaceModifier = if (isFloating) {
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (tokens.isForge) 0.dp else 24.dp,
                    end = if (tokens.isForge) 0.dp else 24.dp,
                    bottom = 8.dp
                )
                .then(
                    if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, dockShape)
                    else Modifier
                )
                .clip(dockShape)
                .then(
                    if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, dockShape)
                    else Modifier
                )
        } else {
            Modifier.fillMaxWidth()
        }

        Surface(
            shape = if (isFloating) dockShape else androidx.compose.ui.graphics.RectangleShape,
            color = if (isFloating) {
                if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
            },
            tonalElevation = 6.dp,
            modifier = surfaceModifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onOpenFullscreen()
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Мини-винил
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(vinylRotation)
                        ) {
                            val radius = size.minDimension / 2f
                            drawCircle(color = Color(0xFF1C1C1E), radius = radius)
                            drawCircle(color = Color(0xFF2C2C2E), radius = radius * 0.8f, style = Stroke(width = 1.dp.toPx()))
                            drawCircle(color = Color(0xFF2C2C2E), radius = radius * 0.6f, style = Stroke(width = 1.dp.toPx()))
                        }

                        val cover = track.coverUrl
                        if (!cover.isNullOrBlank()) {
                            CachedImage(
                                model = cover,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .rotate(vinylRotation),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text("🎵", fontSize = 10.sp)
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Название и исполнитель
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = track.displayTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.displayPerformer,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Кнопка Play / Pause
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Кнопка закрыть / остановить
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Полоска прогресса
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(musicPlayback.progress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
