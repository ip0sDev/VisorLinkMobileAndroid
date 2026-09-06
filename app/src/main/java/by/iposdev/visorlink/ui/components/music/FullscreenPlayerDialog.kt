package by.iposdev.visorlink.ui.components.music

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.MusicPlaylist
import by.iposdev.visorlink.data.model.MusicRepeatMode
import by.iposdev.visorlink.data.model.MusicTrack
import by.iposdev.visorlink.data.repository.MusicRepository
import by.iposdev.visorlink.ui.components.CachedImage
import by.iposdev.visorlink.ui.components.VlAmbientGlow
import by.iposdev.visorlink.ui.components.VlTextField
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.utils.MusicPlayerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenPlayerDialog(
    playerManager: MusicPlayerManager,
    musicRepository: MusicRepository,
    onDismiss: () -> Unit
) {
    val state by playerManager.state.collectAsState()
    val track = state.currentTrack ?: run {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val playlists by musicRepository.getPlaylistsFlow().collectAsState(initial = emptyList())
    val downloadProgressMap by musicRepository.downloadProgress.collectAsState()
    val trackDownloadProgress = downloadProgressMap[track.id]

    // Вращение винила
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotate")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing)
        ),
        label = "spin_angle"
    )
    val vinylRotation = if (state.isPlaying) spinAngle else 0f

    // Локальный скраббер
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderProgress by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (isDraggingSlider) sliderProgress else state.progress
    val currentMs = if (isDraggingSlider) (sliderProgress * state.durationMs).toLong() else state.currentMs
    val totalMs = state.durationMs

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                VlAmbientGlow()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Верхняя панель
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.music_title).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = when (track.sourceType) {
                                    MusicTrack.SOURCE_CHAT -> "Из чата"
                                    MusicTrack.SOURCE_DEVICE -> "Устройство"
                                    else -> "Медиатека"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        IconButton(onClick = { showPlaylistPicker = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add to playlist",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Большая виниловая пластинка
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(vinylRotation)
                        ) {
                            val radius = size.minDimension / 2f
                            // Внешний виниловый диск
                            drawCircle(color = Color(0xFF161618), radius = radius)
                            // Звуковые бороздки
                            drawCircle(color = Color(0xFF28282B), radius = radius * 0.90f, style = Stroke(width = 1.5.dp.toPx()))
                            drawCircle(color = Color(0xFF222224), radius = radius * 0.80f, style = Stroke(width = 1.5.dp.toPx()))
                            drawCircle(color = Color(0xFF28282B), radius = radius * 0.70f, style = Stroke(width = 1.5.dp.toPx()))
                            drawCircle(color = Color(0xFF222224), radius = radius * 0.60f, style = Stroke(width = 1.5.dp.toPx()))
                        }

                        // Центральный яблоко-лейбл с обложкой трека
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .rotate(vinylRotation)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            val cover = track.coverUrl
                            if (!cover.isNullOrBlank()) {
                                CachedImage(
                                    model = cover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text("🎵", fontSize = 36.sp)
                            }
                        }

                        // Центральное отверстие винила
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Название трека, исполнитель и кнопка "Избранное"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.displayTitle,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = track.displayPerformer,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    val newFav = musicRepository.toggleFavorite(track)
                                    playerManager.updateFavoriteStatus(track.id, newFav)
                                    val msg = if (newFav) {
                                        context.getString(R.string.music_added_to_favorites)
                                    } else {
                                        context.getString(R.string.music_removed_from_favorites)
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Скраббер (Ползунок перемотки)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = currentProgress.coerceIn(0f, 1f),
                            onValueChange = {
                                isDraggingSlider = true
                                sliderProgress = it
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                playerManager.seekTo(sliderProgress)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatMs(currentMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatMs(totalMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Основные элементы управления (Shuffle, Prev, Play/Pause, Next, Repeat)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопка Перемешать
                        IconButton(onClick = { playerManager.toggleShuffle() }) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (state.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        // Предыдущий трек
                        IconButton(
                            onClick = { playerManager.playPrevious() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Воспроизведение / Пауза
                        FilledIconButton(
                            onClick = { playerManager.togglePlayPause() },
                            modifier = Modifier.size(68.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        // Следующий трек
                        IconButton(
                            onClick = { playerManager.playNext() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Режим повтора
                        IconButton(onClick = { playerManager.cycleRepeatMode() }) {
                            val repeatIcon = when (state.repeatMode) {
                                MusicRepeatMode.ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            }
                            val repeatTint = when (state.repeatMode) {
                                MusicRepeatMode.OFF -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Icon(
                                imageVector = repeatIcon,
                                contentDescription = "Repeat",
                                tint = repeatTint
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Нижняя строка дополнительных действий: Скорость + Скачивание в кэш
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Переключатель скорости
                        OutlinedButton(
                            onClick = { playerManager.cycleSpeed() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${state.speed}x",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        // Сохранить в постоянную память приложения
                        if (track.sourceType == MusicTrack.SOURCE_CHAT && track.localPath == null) {
                            if (trackDownloadProgress != null && trackDownloadProgress in 0.0f..1.0f) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { trackDownloadProgress },
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${(trackDownloadProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            Toast.makeText(context, R.string.music_downloading, Toast.LENGTH_SHORT).show()
                                            val downloaded = musicRepository.downloadTrackFile(track)
                                            if (downloaded.localPath != null) {
                                                Toast.makeText(context, R.string.music_download_complete, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Save",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.music_save_to_library))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог выбора плейлиста
    if (showPlaylistPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistPicker = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.music_add_to_playlist),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = {
                        showCreatePlaylistDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.music_playlist_create))
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.title) },
                            supportingContent = { Text("${playlist.trackCount} треков") },
                            leadingContent = {
                                Text(playlist.icon, fontSize = 24.sp)
                            },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val added = musicRepository.addTrackToPlaylist(playlist.id, track)
                                    if (added) {
                                        Toast.makeText(context, "Добавлено в \"${playlist.title}\"", Toast.LENGTH_SHORT).show()
                                    }
                                    showPlaylistPicker = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Диалог создания нового плейлиста
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(stringResource(R.string.music_playlist_create)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.music_playlist_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                val playlist = musicRepository.createPlaylist(name)
                                musicRepository.addTrackToPlaylist(playlist.id, track)
                                Toast.makeText(context, "Добавлено в \"$name\"", Toast.LENGTH_SHORT).show()
                                showCreatePlaylistDialog = false
                                showPlaylistPicker = false
                                newPlaylistName = ""
                            }
                        }
                    }
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
