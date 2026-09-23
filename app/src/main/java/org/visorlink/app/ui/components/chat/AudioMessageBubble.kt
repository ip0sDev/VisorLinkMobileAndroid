package org.visorlink.app.ui.components.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.visorlink.app.data.model.Message
import org.visorlink.app.data.model.MusicTrack
import org.visorlink.app.ui.components.CachedImage
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.MusicPlayerState

@Composable
fun AudioMessageBubble(
    message: Message,
    isMine: Boolean,
    tint: Color,
    musicPlayback: MusicPlayerState,
    uploadProgress: Float? = null,
    downloadProgress: Float? = null,
    onPlay: (Message) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    onSaveToLibrary: (MusicTrack) -> Unit,
    onOpenFullscreen: () -> Unit,
    onCancelUpload: (() -> Unit)? = null
) {
    val isThisTrack = musicPlayback.currentTrack?.id == message.id
    val isPlaying = isThisTrack && musicPlayback.isPlaying
    val isLoading = isThisTrack && musicPlayback.isLoading
    val progress = if (isThisTrack) musicPlayback.progress else 0f
    val currentSec = if (isThisTrack) (musicPlayback.currentMs / 1000).toInt() else 0
    val totalSec = if (isThisTrack && musicPlayback.durationMs > 0) {
        (musicPlayback.durationMs / 1000).toInt()
    } else {
        message.duration ?: 0
    }

    val title = message.title?.ifBlank { null } ?: message.fileName ?: "Аудиозапись"
    val performer = message.performer?.ifBlank { null } ?: "Неизвестный исполнитель"

    // Виниловое вращение
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing)
        ),
        label = "spin_angle"
    )
    val vinylRotation = if (isPlaying) spinAngle else 0f

    Box(contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .then(if (uploadProgress != null) Modifier.alpha(0.6f) else Modifier)
        ) {
            // Верхняя строка: Виниловая пластинка + Название/Исполнитель + Кнопка Play/Pause
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Виниловая пластинка
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (isThisTrack) onTogglePlayPause() else onPlay(message)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Основа винила с бороздками
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(vinylRotation)
                    ) {
                        val radius = size.minDimension / 2f
                        drawCircle(color = Color(0xFF1C1C1E), radius = radius)
                        // Бороздки
                        drawCircle(color = Color(0xFF2C2C2E), radius = radius * 0.85f, style = Stroke(width = 1.dp.toPx()))
                        drawCircle(color = Color(0xFF2C2C2E), radius = radius * 0.70f, style = Stroke(width = 1.dp.toPx()))
                        drawCircle(color = Color(0xFF2C2C2E), radius = radius * 0.55f, style = Stroke(width = 1.dp.toPx()))
                    }

                    // Обложка или центральный лейбл
                    val cover = message.coverUrl
                    if (!cover.isNullOrBlank()) {
                        CachedImage(
                            model = cover,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .rotate(vinylRotation),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎵", fontSize = 10.sp)
                        }
                    }

                    // Наложение статуса воспроизведения поверх винила
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (isPlaying || isLoading) 0.35f else 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))

                // Название трека и исполнитель
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onOpenFullscreen()
                        }
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = performer,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Кнопка перехода в полноэкранный плеер
                IconButton(
                    onClick = onOpenFullscreen,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Fullscreen",
                        tint = tint.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Полоса перемотки (Scrubber)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .pointerInput(message.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val frac = (down.position.x / size.width).coerceIn(0f, 1f)
                            onSeek(frac)
                            if (!isThisTrack) onPlay(message)
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                // Фоновая линия
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.2f))
                )
                // Заполненная линия прогресса
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(tint)
                )
            }

            // Нижняя строка: Время + Скорость + Добавление в Избранное
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatAudioTime(currentSec)} / ${formatAudioTime(totalSec)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tint.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Кнопка изменения скорости (если трек играет)
                    if (isThisTrack) {
                        TextButton(
                            onClick = onCycleSpeed,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(22.dp)
                        ) {
                            Text(
                                text = "${musicPlayback.speed}x",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tint,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Кнопка сохранения трека в библиотеку или индикатор скачивания
                    if (downloadProgress != null && downloadProgress in 0.0f..1.0f) {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = tint
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val rawUrl = when {
                                    !message.url.isNullOrBlank() -> message.url
                                    !message.driveUrl.isNullOrBlank() -> message.driveUrl
                                    !message.driveFileId.isNullOrBlank() -> "https://drive.usercontent.google.com/download?id=${message.driveFileId}&export=download&confirm=t"
                                    else -> null
                                }
                                val resolvedUrl = org.visorlink.app.data.model.resolveStreamableAudioUrl(rawUrl, message.driveFileId ?: message.cdnMediaId) ?: rawUrl
                                val resolvedLocalPath = when {
                                    message.localFile?.exists() == true -> message.localFile.absolutePath
                                    else -> null
                                }
                                val track = MusicTrack(
                                    id = message.id,
                                    title = title,
                                    performer = performer,
                                    duration = totalSec,
                                    fileSize = message.fileSize ?: 0L,
                                    url = resolvedUrl,
                                    localPath = resolvedLocalPath,
                                    cdnMediaId = message.cdnMediaId,
                                    coverUrl = message.coverUrl,
                                    coverCdnMediaId = message.coverCdnMediaId,
                                    isFavorite = true,
                                    sourceType = MusicTrack.SOURCE_CHAT,
                                    chatId = null,
                                    messageId = message.id
                                )
                                onSaveToLibrary(track)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FavoriteBorder,
                                contentDescription = "Save to library",
                                tint = tint.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (uploadProgress != null) {
            UploadProgressOverlay(
                progress = uploadProgress,
                modifier = Modifier.matchParentSize(),
                onCancel = onCancelUpload
            )
        }
    }
}

private fun formatAudioTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}
