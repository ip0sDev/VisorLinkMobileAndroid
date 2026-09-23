package org.visorlink.app.ui.components.music

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.visorlink.app.R
import org.visorlink.app.data.model.MusicRepeatMode
import org.visorlink.app.data.model.MusicTrack
import org.visorlink.app.data.repository.MusicRepository
import org.visorlink.app.ui.components.CachedImage
import org.visorlink.app.ui.components.liquidDragStretch
import org.visorlink.app.ui.components.liquidJelly
import org.visorlink.app.ui.components.liquidPopIn
import org.visorlink.app.ui.components.rememberLiquidEnabled
import org.visorlink.app.ui.components.rememberLiquidJellyState
import org.visorlink.app.ui.components.rememberLiquidPopProgress
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlInset
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.utils.MusicPlayerManager
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Режимы визуализации экрана плеера:
 * - [COVER]: 3D-арт карточка альбома с плавающей физикой и цветной авророй.
 * - [VINYL]: Реалистичный виниловый диск с бликами microgrooves и поворотным тонармом.
 * - [WAVE]: Мультиспектральный динамический эквалайзер с зеркальным отражением.
 */
enum class PlayerVisualMode {
    COVER, VINYL, WAVE
}

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
    val tokens = VlTheme.tokens
    val isLiquidEnabled = rememberLiquidEnabled()
    val popProgress = rememberLiquidPopProgress(isLiquidEnabled)

    // Линейка состояний шторок
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Текущий визуальный режим (по умолчанию – 3D Обложка)
    var visualMode by remember { mutableStateOf(PlayerVisualMode.COVER) }

    val playlists by musicRepository.getPlaylistsFlow().collectAsState(initial = emptyList())
    val downloadProgressMap by musicRepository.downloadProgress.collectAsState()
    val trackDownloadProgress = downloadProgressMap[track.id]

    // Таймер сна
    val sleepMinutesLeft by playerManager.sleepTimerMinutesLeft.collectAsState()
    val isSleepAtEnd by playerManager.isSleepAtEndOfTrackFlow.collectAsState()

    // Желейные тактильные пружины
    val collapseJelly = rememberLiquidJellyState(softness = 0.16f)
    val modeJelly = rememberLiquidJellyState(softness = 0.12f)
    val queueJelly = rememberLiquidJellyState(softness = 0.14f)
    val sleepJelly = rememberLiquidJellyState(softness = 0.14f)
    val favJelly = rememberLiquidJellyState(softness = 0.22f, damping = 0.5f, stiffness = 380f)
    val shuffleJelly = rememberLiquidJellyState(softness = 0.14f)
    val prevJelly = rememberLiquidJellyState(softness = 0.18f)
    val playPauseJelly = rememberLiquidJellyState(softness = 0.22f, damping = 0.55f, stiffness = 320f)
    val nextJelly = rememberLiquidJellyState(softness = 0.18f)
    val repeatJelly = rememberLiquidJellyState(softness = 0.14f)
    val speedJelly = rememberLiquidJellyState(softness = 0.12f)
    val saveJelly = rememberLiquidJellyState(softness = 0.14f)
    val playlistAddJelly = rememberLiquidJellyState(softness = 0.14f)

    // Локальное состояние скраббера
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderProgress by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (isDraggingSlider) sliderProgress else state.progress
    val currentMs = if (isDraggingSlider) (sliderProgress * state.durationMs).toLong() else state.currentMs
    val totalMs = state.durationMs
    val remainingMs = (totalMs - currentMs).coerceAtLeast(0L)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .liquidPopIn(popProgress, enabled = isLiquidEnabled),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Живая аврора на заднем плане
                PlayerAuraBackdrop(
                    track = track,
                    isPlaying = state.isPlaying,
                    isLiquidEnabled = isLiquidEnabled
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // ── 1. ВЕРХНЯЯ ПАНЕЛЬ НАВИГАЦИИ И РЕЖИМОВ ──────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Свернуть плеер
                        IconButton(
                            onClick = {
                                collapseJelly.press()
                                onDismiss()
                            },
                            modifier = Modifier.liquidJelly(collapseJelly, enabled = isLiquidEnabled)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse",
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Капсульный переключатель режимов визуализации (Обложка / Винил / Волна)
                        PlayerVisualModeSelector(
                            currentMode = visualMode,
                            onModeSelected = { visualMode = it },
                            isLiquidEnabled = isLiquidEnabled,
                            modifier = Modifier.liquidJelly(modeJelly, enabled = isLiquidEnabled)
                        )

                        // Правые быстрые действия (Таймер сна + Очередь)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Кнопка Таймера сна
                            IconButton(
                                onClick = {
                                    sleepJelly.press()
                                    showSleepTimerSheet = true
                                },
                                modifier = Modifier.liquidJelly(sleepJelly, enabled = isLiquidEnabled)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = stringResource(R.string.music_sleep_timer),
                                        tint = if (sleepMinutesLeft != null || isSleepAtEnd) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    if (sleepMinutesLeft != null) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 6.dp, y = (-4).dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "${sleepMinutesLeft}m",
                                                style = tokens.data.dataSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            // Кнопка Очереди воспроизведения
                            IconButton(
                                onClick = {
                                    queueJelly.press()
                                    showQueueSheet = true
                                },
                                modifier = Modifier.liquidJelly(queueJelly, enabled = isLiquidEnabled)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = stringResource(R.string.music_queue_title),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    if (state.playlist.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 4.dp, y = 4.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "${state.playlist.size}",
                                                style = tokens.data.dataSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── 2. ГЕРОИЧЕСКАЯ ИНТЕРАКТИВНАЯ ЗОНА ──────────────────────────────────
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = visualMode,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)))
                                    .togetherWith(fadeOut(animationSpec = tween(200)))
                            },
                            label = "hero_visual_mode"
                        ) { mode ->
                            when (mode) {
                                PlayerVisualMode.COVER -> {
                                    PlayerArtCard(
                                        track = track,
                                        isPlaying = state.isPlaying,
                                        isLiquidEnabled = isLiquidEnabled,
                                        onTap = { visualMode = PlayerVisualMode.VINYL }
                                    )
                                }
                                PlayerVisualMode.VINYL -> {
                                    PlayerVinylTurntable(
                                        track = track,
                                        isPlaying = state.isPlaying,
                                        isLiquidEnabled = isLiquidEnabled,
                                        onTap = { visualMode = PlayerVisualMode.WAVE }
                                    )
                                }
                                PlayerVisualMode.WAVE -> {
                                    PlayerAudioVisualizer(
                                        isPlaying = state.isPlaying,
                                        isLiquidEnabled = isLiquidEnabled,
                                        onTap = { visualMode = PlayerVisualMode.COVER }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── 3. ИНФОРМАЦИЯ О ТРЕКЕ И БЕЙДЖИ ─────────────────────────────────────
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // Название трека с плавной бегущей строкой (Marquee)
                                Text(
                                    text = track.displayTitle,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 22.sp
                                    ),
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                )
                                Spacer(Modifier.height(3.dp))
                                // Исполнитель
                                Text(
                                    text = track.displayPerformer,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            // Кнопка «Избранное» со взрывным желейным импульсом
                            var favBurst by remember { mutableStateOf(false) }
                            val favScale by animateFloatAsState(
                                targetValue = if (favBurst) 1.32f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
                                finishedListener = { favBurst = false },
                                label = "fav_scale"
                            )

                            IconButton(
                                onClick = {
                                    favBurst = true
                                    favJelly.pulse()
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
                                },
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = favScale
                                        scaleY = favScale
                                    }
                                    .liquidJelly(favJelly, enabled = isLiquidEnabled)
                            ) {
                                Icon(
                                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (track.isFavorite) Color(0xFFFF3366) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Ряд технических бейджей (Качество звука + Источник трека)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Бейдж качества HQ AUDIO с живым индикатором
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (state.isPlaying) Color(0xFF00E676) else MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "HQ AUDIO",
                                        style = tokens.data.dataSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Бейдж источника аудиофайла
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .padding(horizontal = 8.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    text = when (track.sourceType) {
                                        MusicTrack.SOURCE_CHAT -> "Из чата"
                                        MusicTrack.SOURCE_DEVICE -> "Устройство"
                                        else -> "Медиатека"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // ── 4. НЕОНОВЫЙ СКРАББЕР ВРЕМЕНИ ───────────────────────────────────────
                    Column(modifier = Modifier.fillMaxWidth()) {
                        FluidAudioScrubber(
                            progress = currentProgress.coerceIn(0f, 1f),
                            durationMs = totalMs,
                            onValueChange = {
                                isDraggingSlider = true
                                sliderProgress = it
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                playerManager.seekTo(sliderProgress)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(4.dp))

                        // Метки времени: слева текущее, справа оставшееся со знаком минус
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatMs(currentMs),
                                style = tokens.data.dataSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "-${formatMs(remainingMs)}",
                                style = tokens.data.dataSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── 5. ГЛАВНЫЙ ПАPЯЩИЙ ПУЛЬТ УПРАВЛЕНИЯ (MASTER DECK) ───────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Перемешивание (Shuffle)
                        IconButton(
                            onClick = {
                                shuffleJelly.pulse()
                                playerManager.toggleShuffle()
                            },
                            modifier = Modifier
                                .liquidJelly(shuffleJelly, enabled = isLiquidEnabled)
                                .then(
                                    if (state.isShuffle) {
                                        Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                    } else Modifier
                                )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (state.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.size(24.dp)
                                )
                                if (state.isShuffle) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }

                        // Предыдущий трек (54dp)
                        FilledTonalIconButton(
                            onClick = {
                                prevJelly.pulse()
                                playerManager.playPrevious()
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .liquidJelly(prevJelly, enabled = isLiquidEnabled)
                                .then(if (tokens.isBiolume) Modifier.vlRaised(tokens.structure, CircleShape) else Modifier),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Центральная кнопка Play / Pause (80dp с гидродинамической аурой)
                        Box(
                            modifier = Modifier.size(114.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Расходящиеся кольца пульсации при воспроизведении
                            if (isLiquidEnabled && state.isPlaying) {
                                val pulseTransition = rememberInfiniteTransition(label = "pulse_rings")
                                val pulseRadius1 by pulseTransition.animateFloat(
                                    initialValue = 76f, targetValue = 114f,
                                    animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
                                    label = "r1"
                                )
                                val pulseAlpha1 by pulseTransition.animateFloat(
                                    initialValue = 0.40f, targetValue = 0.0f,
                                    animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
                                    label = "a1"
                                )

                                Box(
                                    modifier = Modifier
                                        .size(pulseRadius1.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha1), CircleShape)
                                )
                            }

                            FilledIconButton(
                                onClick = {
                                    playPauseJelly.pulse()
                                    playerManager.togglePlayPause()
                                },
                                modifier = Modifier
                                    .size(80.dp)
                                    .liquidJelly(playPauseJelly, enabled = isLiquidEnabled)
                                    .then(
                                        if (tokens.isBiolume) Modifier.vlRaised(tokens.structure, CircleShape)
                                        else Modifier.shadow(elevation = 12.dp, shape = CircleShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                                    ),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(34.dp),
                                        strokeWidth = 3.5.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            }
                        }

                        // Следующий трек (54dp)
                        FilledTonalIconButton(
                            onClick = {
                                nextJelly.pulse()
                                playerManager.playNext()
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .liquidJelly(nextJelly, enabled = isLiquidEnabled)
                                .then(if (tokens.isBiolume) Modifier.vlRaised(tokens.structure, CircleShape) else Modifier),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        // Режим повтора (Repeat)
                        IconButton(
                            onClick = {
                                repeatJelly.pulse()
                                playerManager.cycleRepeatMode()
                            },
                            modifier = Modifier
                                .liquidJelly(repeatJelly, enabled = isLiquidEnabled)
                                .then(
                                    if (state.repeatMode != MusicRepeatMode.OFF) {
                                        Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                    } else Modifier
                                )
                        ) {
                            val isRepeatActive = state.repeatMode != MusicRepeatMode.OFF
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = when (state.repeatMode) {
                                        MusicRepeatMode.ONE -> Icons.Default.RepeatOne
                                        else -> Icons.Default.Repeat
                                    },
                                    contentDescription = "Repeat",
                                    tint = if (isRepeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.size(24.dp)
                                )
                                if (isRepeatActive) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── 6. НИЖНЯЯ СТРОКА БЫСТРЫХ УТИЛИТ ────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Переключатель скорости воспроизведения (чип)
                        OutlinedButton(
                            onClick = {
                                speedJelly.pulse()
                                playerManager.cycleSpeed()
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .liquidJelly(speedJelly, enabled = isLiquidEnabled)
                                .then(if (tokens.isBiolume) Modifier.vlRaised(tokens.structure, RoundedCornerShape(20.dp)) else Modifier),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${state.speed}x",
                                style = tokens.data.dataSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.speed != 1.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        // Быстрое добавление в плейлист
                        OutlinedButton(
                            onClick = {
                                playlistAddJelly.pulse()
                                showPlaylistPicker = true
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .liquidJelly(playlistAddJelly, enabled = isLiquidEnabled)
                                .then(if (tokens.isBiolume) Modifier.vlRaised(tokens.structure, RoundedCornerShape(20.dp)) else Modifier),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.music_add_to_playlist),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        // Скачивание трека в постоянную память (если это аудиосообщение из чата)
                        val isTrackDownloaded = track.localPath != null && File(track.localPath).exists()
                        if (track.sourceType == MusicTrack.SOURCE_CHAT && !isTrackDownloaded) {
                            if (trackDownloadProgress != null && trackDownloadProgress in 0.0f..1.0f) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { trackDownloadProgress },
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${(trackDownloadProgress * 100).toInt()}%",
                                        style = tokens.data.dataSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        saveJelly.pulse()
                                        scope.launch {
                                            Toast.makeText(context, R.string.music_downloading, Toast.LENGTH_SHORT).show()
                                            val downloaded = musicRepository.downloadTrackFile(track)
                                            if (downloaded.localPath != null && File(downloaded.localPath).exists()) {
                                                Toast.makeText(context, R.string.music_download_complete, Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Не удалось скачать аудиофайл. Проверьте сеть или ссылку.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .liquidJelly(saveJelly, enabled = isLiquidEnabled)
                                        .then(if (tokens.isBiolume) Modifier.vlRaised(tokens.structure, RoundedCornerShape(20.dp)) else Modifier),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Save",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.music_save_to_library), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 7. МОДАЛЬНЫЕ ШТОРКИ (ОЧЕРЕДЬ, ТАЙМЕР СНА, ПЛЕЙЛИСТЫ) ─────────────────────────

    // Шторка очереди воспроизведения (Queue / Up Next)
    if (showQueueSheet) {
        QueueBottomSheet(
            playlist = state.playlist,
            currentTrack = track,
            isPlaying = state.isPlaying,
            isLiquidEnabled = isLiquidEnabled,
            onTrackClick = { selectedTrack, index ->
                playerManager.playTrack(selectedTrack, state.playlist, index)
            },
            onDismiss = { showQueueSheet = false }
        )
    }

    // Шторка таймера сна
    if (showSleepTimerSheet) {
        SleepTimerBottomSheet(
            currentMinutesLeft = sleepMinutesLeft,
            isSleepAtEnd = isSleepAtEnd,
            isLiquidEnabled = isLiquidEnabled,
            onSetTimer = { minutes ->
                playerManager.setSleepTimer(minutes)
                Toast.makeText(context, context.getString(R.string.music_sleep_timer_set), Toast.LENGTH_SHORT).show()
                showSleepTimerSheet = false
            },
            onSetEndOfTrack = {
                playerManager.setSleepAtEndOfTrack(true)
                Toast.makeText(context, context.getString(R.string.music_sleep_timer_set), Toast.LENGTH_SHORT).show()
                showSleepTimerSheet = false
            },
            onCancelTimer = {
                playerManager.cancelSleepTimer()
                Toast.makeText(context, context.getString(R.string.music_sleep_timer_cancelled), Toast.LENGTH_SHORT).show()
                showSleepTimerSheet = false
            },
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    // Шторка добавления в плейлист
    if (showPlaylistPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistPicker = false },
            shape = if (isLiquidEnabled) RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp) else BottomSheetDefaults.ExpandedShape,
            containerColor = MaterialTheme.colorScheme.surface
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
                    val createJelly = rememberLiquidJellyState(softness = 0.12f)
                    TextButton(
                        onClick = {
                            createJelly.pulse()
                            showCreatePlaylistDialog = true
                        },
                        modifier = Modifier.liquidJelly(createJelly, enabled = isLiquidEnabled)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.music_playlist_create))
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(playlists) { _, playlist ->
                        val rowJelly = rememberLiquidJellyState(softness = 0.08f)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidJelly(rowJelly, enabled = isLiquidEnabled)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    rowJelly.press()
                                    scope.launch {
                                        val added = musicRepository.addTrackToPlaylist(playlist.id, track)
                                        if (added) {
                                            Toast.makeText(context, "Добавлено в \"${playlist.title}\"", Toast.LENGTH_SHORT).show()
                                        }
                                        showPlaylistPicker = false
                                    }
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            ListItem(
                                headlineContent = { Text(playlist.title, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("${playlist.trackCount} треков") },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(playlist.icon, fontSize = 22.sp)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }

    // Диалог создания нового плейлиста
    if (showCreatePlaylistDialog) {
        val dialogPop = rememberLiquidPopProgress(isLiquidEnabled)
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.liquidPopIn(dialogPop, enabled = isLiquidEnabled),
            title = { Text(stringResource(R.string.music_playlist_create)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.music_playlist_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                val confirmJelly = rememberLiquidJellyState(softness = 0.12f)
                TextButton(
                    onClick = {
                        confirmJelly.pulse()
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
                    },
                    modifier = Modifier.liquidJelly(confirmJelly, enabled = isLiquidEnabled)
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

// ─────────────────────────────────────────────────────────────────────────────
// ВИЗУАЛЬНЫЕ КОМПОНЕНТЫ И РЕЖИМЫ ПЛЕЕРА
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Капсульный переключатель трех режимов визуализации (Обложка, Винил, Волна).
 */
@Composable
private fun PlayerVisualModeSelector(
    currentMode: PlayerVisualMode,
    onModeSelected: (PlayerVisualMode) -> Unit,
    isLiquidEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val tokens = VlTheme.tokens
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .then(if (tokens.isBiolume) Modifier.vlInset(tokens.structure, RoundedCornerShape(20.dp)) else Modifier)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val modes = listOf(
            PlayerVisualMode.COVER to stringResource(R.string.music_mode_cover),
            PlayerVisualMode.VINYL to stringResource(R.string.music_mode_vinyl),
            PlayerVisualMode.WAVE to stringResource(R.string.music_mode_visualizer)
        )

        modes.forEach { (mode, title) ->
            val isSelected = currentMode == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else Color.Transparent
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * РЕЖИМ 1: 3D-арт карточка с мягким цветным ореолом, плавающей высотой и стеклянным бликом.
 */
@Composable
private fun PlayerArtCard(
    track: MusicTrack,
    isPlaying: Boolean,
    isLiquidEnabled: Boolean,
    onTap: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "art_motion")

    // Дыхание ореола и карточки
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.20f, targetValue = 0.48f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo_pulse"
    )

    // Мягкое плавание по вертикали (translationY)
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float_offset"
    )

    // Масштаб при паузе / воспроизведении
    val animatedScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.89f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 280f),
        label = "art_card_scale"
    )

    Box(
        modifier = Modifier
            .size(285.dp)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        // Фоновый амбиентный ореол с мягким градиентом
        if (isLiquidEnabled && isPlaying) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(38.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = haloPulse),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = haloPulse * 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Сама 3D-карточка обложки
        Box(
            modifier = Modifier
                .size(265.dp)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    translationY = if (isPlaying && isLiquidEnabled) floatOffset else 0f
                }
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.40f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                Color.Transparent
                            )
                        )
                    ),
                    RoundedCornerShape(32.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val cover = track.coverUrl
            if (!cover.isNullOrBlank()) {
                CachedImage(
                    model = cover,
                    contentDescription = track.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                GenerativeCoverArt(track)
            }

            // Диагональный стеклянный блик (Gloss Sheen)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width * 0.70f, 0f)
                    lineTo(0f, size.height * 0.70f)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.5f, size.height * 0.5f)
                    )
                )
            }
        }
    }
}

/**
 * РЕЖИМ 2: Аудиофильский виниловый турнтабл со световыми sweep-бликами и анимированным тонармом.
 */
@Composable
private fun PlayerVinylTurntable(
    track: MusicTrack,
    isPlaying: Boolean,
    isLiquidEnabled: Boolean,
    onTap: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotate")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "spin_angle"
    )
    val currentRotation = if (isPlaying) spinAngle else 0f

    // Анимация угла тонарма (опускается на пластинку при старте и отводится на паузе)
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 24f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 220f),
        label = "tonearm_angle"
    )

    Box(
        modifier = Modifier
            .size(290.dp)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        // Виниловый диск 255dp
        Box(
            modifier = Modifier
                .size(255.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(currentRotation)
            ) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Внешний виниловый массив
                drawCircle(color = Color(0xFF141416), radius = radius)

                // Световые sweep-блики на микро-бороздках
                val sheenColors = listOf(
                    Color(0xFF141416),
                    Color(0xFF2C2C30),
                    Color(0xFF141416),
                    Color(0xFF2A2A2E),
                    Color(0xFF141416)
                )
                drawCircle(
                    brush = Brush.sweepGradient(sheenColors, center = center),
                    radius = radius * 0.96f
                )

                // Звуковые концентрические бороздки
                val grooveColor = Color(0xFF202024)
                val grooveLight = Color(0xFF2E2E34)
                for (i in 1..8) {
                    val r = radius * (0.46f + (i * 0.06f))
                    drawCircle(
                        color = if (i % 2 == 0) grooveLight else grooveColor,
                        radius = r,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }

            // Центральный яблоко-лейбл с обложкой трека и золотым кольцом
            Box(
                modifier = Modifier
                    .size(105.dp)
                    .clip(CircleShape)
                    .rotate(currentRotation)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, Color(0xFFC5A059), CircleShape),
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
                    GenerativeCoverArt(track, isMini = true)
                }

                // Центральный шпиндель
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1C))
                        .border(1.dp, Color(0xFF88888E), CircleShape)
                )
            }
        }

        // Анимированный Тонарм (звукосниматель / игла) в правом верхнем углу
        Canvas(
            modifier = Modifier
                .size(110.dp)
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    rotationZ = tonearmAngle
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0.15f)
                }
        ) {
            val pivotX = size.width * 0.85f
            val pivotY = size.height * 0.15f

            // База тонарма (хромированная основа)
            drawCircle(color = Color(0xFF424248), radius = 10.dp.toPx(), center = Offset(pivotX, pivotY))
            drawCircle(color = Color(0xFF8A8A94), radius = 6.dp.toPx(), center = Offset(pivotX, pivotY))

            // Трубка тонарма (рычаг)
            val armPath = Path().apply {
                moveTo(pivotX, pivotY)
                lineTo(size.width * 0.35f, size.height * 0.65f)
                lineTo(size.width * 0.20f, size.height * 0.90f)
            }
            drawPath(
                path = armPath,
                color = Color(0xFFC8C8D2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Картридж звукоснимателя и игла
            val headX = size.width * 0.20f
            val headY = size.height * 0.90f
            drawRoundRect(
                color = Color(0xFFE53935),
                topLeft = Offset(headX - 6.dp.toPx(), headY - 3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(14.dp.toPx(), 8.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
        }
    }
}

/**
 * РЕЖИМ 3: Мультиспектральный динамический эквалайзер (28 неоновых полос) с зеркальным отражением.
 */
@Composable
private fun PlayerAudioVisualizer(
    isPlaying: Boolean,
    isLiquidEnabled: Boolean,
    onTap: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "viz_motion")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "viz_time"
    )

    val cs = MaterialTheme.colorScheme
    val barCount = 26

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerY = h * 0.52f
            val totalBarsW = w * 0.85f
            val barW = (totalBarsW / barCount) * 0.68f
            val spacing = (totalBarsW / barCount) * 0.32f
            val startX = (w - totalBarsW) / 2f

            for (i in 0 until barCount) {
                val phase = i * 0.25f
                val normX = i.toFloat() / barCount

                // Симуляция спектрального отклика
                val rawH = if (isPlaying) {
                    val w1 = (sin(time * 3.2f + phase) + 1f) * 0.5f
                    val w2 = (sin(time * 5.4f - phase * 1.8f) + 1f) * 0.5f
                    val w3 = (cos(time * 2.1f + phase * 0.9f) + 1f) * 0.5f
                    (w1 * 0.50f + w2 * 0.35f + w3 * 0.15f).coerceIn(0.12f, 1.0f)
                } else {
                    0.08f
                }

                // Параболическое распределение высоты (бас по центру, спад к краям)
                val curve = 1f - (2f * (normX - 0.5f)).let { it * it * 0.4f }
                val barH = (rawH * 95.dp.toPx() * curve).coerceAtLeast(8.dp.toPx())
                val x = startX + i * (barW + spacing)

                // Верхняя спектральная полоса
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(cs.primary, cs.tertiary),
                        startY = centerY - barH,
                        endY = centerY
                    ),
                    topLeft = Offset(x, centerY - barH),
                    size = androidx.compose.ui.geometry.Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f)
                )

                // Зеркальное водное отражение снизу
                val reflectH = barH * 0.40f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(cs.primary.copy(alpha = 0.30f), Color.Transparent),
                        startY = centerY,
                        endY = centerY + reflectH
                    ),
                    topLeft = Offset(x, centerY + 2.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(barW, reflectH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f)
                )
            }
        }
    }
}

/**
 * Процедурная генеративная обложка трека (когда у аудиозаписи нет картинки).
 */
@Composable
private fun GenerativeCoverArt(
    track: MusicTrack,
    isMini: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFF2C1E4A),
                        Color(0xFF140F24)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            for (i in 1..4) {
                drawCircle(
                    color = cs.primary.copy(alpha = 0.15f * i),
                    radius = r * (0.25f * i),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(if (isMini) 28.dp else 72.dp)
        )
    }
}

/**
 * Красивый размытый фон обложки альбома с градиентным затемнением.
 */
@Composable
private fun PlayerAuraBackdrop(
    track: MusicTrack,
    isPlaying: Boolean,
    isLiquidEnabled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "backdrop_blur")
    val blurRadius by infiniteTransition.animateFloat(
        initialValue = 60f, targetValue = 85f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blur_anim"
    )
    val currentBlur = if (isPlaying && isLiquidEnabled) blurRadius.dp else 80.dp
    val coverUrl = track.coverUrl

    Box(modifier = Modifier.fillMaxSize()) {
        if (!coverUrl.isNullOrBlank()) {
            CachedImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(currentBlur),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(currentBlur)
            ) {
                GenerativeCoverArt(track)
            }
        }

        // Плавный градиентный скраб для читаемости текста и элементов управления
        val cs = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            cs.background.copy(alpha = 0.65f),
                            cs.background.copy(alpha = 0.85f),
                            cs.background.copy(alpha = 0.95f)
                        )
                    )
                )
        )
    }
}

/**
 * Неоновый жидкостный скраббер аудио с капсульной дорожкой и всплывающим тултипом времени.
 */
@Composable
private fun FluidAudioScrubber(
    progress: Float,
    durationMs: Long,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    var isDragging by remember { mutableStateOf(false) }
    var dragDeltaPx by remember { mutableFloatStateOf(0f) }
    val jellyState = rememberLiquidJellyState(softness = 0.18f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbDiameter = 20.dp
        val thumbRadius = 10.dp
        val density = LocalDensity.current
        val thumbRadiusPx = with(density) { thumbRadius.toPx() }
        val scrubWidth = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)

        val animatedThumbScale by animateFloatAsState(
            targetValue = if (isDragging) 1.30f else 1.0f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
            label = "scrubber_thumb_scale"
        )

        // Подложка трека (капсула 8.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(cs.onSurface.copy(alpha = 0.16f))
        ) {
            // Активная полоса прогресса с градиентом и свечением
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                cs.primary.copy(alpha = 0.85f),
                                cs.primary,
                                cs.tertiary
                            )
                        )
                    )
            )
        }

        // Бегунок (Thumb) с вытягиванием при перетаскивании
        val thumbCenterPx = thumbRadiusPx + (progress.coerceIn(0f, 1f) * scrubWidth)
        val thumbOffsetDp = with(density) { (thumbCenterPx - thumbRadiusPx).toDp() }

        // Всплывающий тултип времени при скраббинге
        if (isDragging) {
            val scrubbedMs = (progress * durationMs).toLong()
            Box(
                modifier = Modifier
                    .offset(x = (thumbOffsetDp - 18.dp).coerceAtLeast(0.dp), y = (-32).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceContainerHighest)
                    .border(1.dp, cs.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatMs(scrubbedMs),
                    style = tokens.data.dataSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    color = cs.primary
                )
            }
        }

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp)
                .size(thumbDiameter)
                .liquidDragStretch(
                    dragPx = dragDeltaPx,
                    referencePx = 120f,
                    enabled = isDragging,
                    maxStretch = 0.18f
                )
                .graphicsLayer {
                    scaleX = animatedThumbScale
                    scaleY = animatedThumbScale
                }
                .liquidJelly(jellyState)
                .clip(CircleShape)
                .background(cs.primary)
                .border(2.5.dp, cs.surface, CircleShape)
        )

        // Обработка жестов
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        jellyState.press()
                        val initialProgress = ((down.position.x - thumbRadiusPx) / scrubWidth).coerceIn(0f, 1f)
                        onValueChange(initialProgress)

                        var curProg = initialProgress
                        drag(down.id) { change ->
                            change.consume()
                            val delta = change.position.x - change.previousPosition.x
                            dragDeltaPx = delta
                            curProg = ((change.position.x - thumbRadiusPx) / scrubWidth).coerceIn(0f, 1f)
                            onValueChange(curProg)
                        }

                        isDragging = false
                        dragDeltaPx = 0f
                        jellyState.release()
                        onValueChangeFinished()
                    }
                }
        )
    }
}

/**
 * Шторка очереди воспроизведения (Queue / Up Next).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueBottomSheet(
    playlist: List<MusicTrack>,
    currentTrack: MusicTrack,
    isPlaying: Boolean,
    isLiquidEnabled: Boolean,
    onTrackClick: (MusicTrack, Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface
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
                    text = stringResource(R.string.music_queue_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${playlist.size} треков",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            if (playlist.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.music_queue_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(playlist) { index, itemTrack ->
                        val isCurrent = itemTrack.id == currentTrack.id
                        val rowJelly = rememberLiquidJellyState(softness = 0.08f)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidJelly(rowJelly, enabled = isLiquidEnabled)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    rowJelly.press()
                                    onTrackClick(itemTrack, index)
                                },
                            color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                   else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp),
                            border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = itemTrack.displayTitle,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = itemTrack.displayPerformer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = {
                                    if (isCurrent && isPlaying) {
                                        MiniEqualizerWaves(color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                },
                                trailingContent = {
                                    Text(
                                        text = formatMs((itemTrack.duration * 1000).toLong()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Шторка настройки таймера сна (Sleep Timer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerBottomSheet(
    currentMinutesLeft: Int?,
    isSleepAtEnd: Boolean,
    isLiquidEnabled: Boolean,
    onSetTimer: (Int) -> Unit,
    onSetEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.music_sleep_timer),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(Modifier.height(14.dp))

            val options = listOf(
                15 to stringResource(R.string.music_sleep_15m),
                30 to stringResource(R.string.music_sleep_30m),
                45 to stringResource(R.string.music_sleep_45m),
                60 to stringResource(R.string.music_sleep_60m)
            )

            options.forEach { (minutes, label) ->
                val isSelected = currentMinutesLeft == minutes
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSetTimer(minutes) }
                        .padding(vertical = 4.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // До конца текущего трека
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSetEndOfTrack() }
                    .padding(vertical = 4.dp),
                color = if (isSleepAtEnd) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.music_sleep_end_of_track),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isSleepAtEnd) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSleepAtEnd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (isSleepAtEnd) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Отключить таймер
            if (currentMinutesLeft != null || isSleepAtEnd) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancelTimer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.music_sleep_off))
                }
            }
        }
    }
}

/**
 * 3-полосный анимированный мини-эквалайзер для активного трека в очереди.
 */
@Composable
private fun MiniEqualizerWaves(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "mini_eq")
    val b1 by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.90f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val b2 by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(580, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val b3 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Row(
        modifier = Modifier.size(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(b1)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(b2)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(b3)
                .clip(CircleShape)
                .background(color)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
