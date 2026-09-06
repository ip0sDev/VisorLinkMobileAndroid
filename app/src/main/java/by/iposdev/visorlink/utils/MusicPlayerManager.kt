package by.iposdev.visorlink.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import by.iposdev.visorlink.MainActivity
import by.iposdev.visorlink.R
import by.iposdev.visorlink.data.model.MusicRepeatMode
import by.iposdev.visorlink.data.model.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "MusicPlayerManager"

data class MusicPlayerState(
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLoading: Boolean = false,
    val speed: Float = 1.0f,
    val repeatMode: MusicRepeatMode = MusicRepeatMode.OFF,
    val isShuffle: Boolean = false,
    val playlist: List<MusicTrack> = emptyList(),
    val currentIndex: Int = -1,
    val error: String? = null
)

class MusicPlayerManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSessionCompat? = null

    private val _state = MutableStateFlow(MusicPlayerState())
    val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

    private val _showFullscreenPlayer = MutableStateFlow(false)
    val showFullscreenPlayer: StateFlow<Boolean> = _showFullscreenPlayer.asStateFlow()

    private var originalQueue: List<MusicTrack> = emptyList()
    private var progressRunnable: Runnable? = null

    companion object {
        private const val CHANNEL_ID = "music_playback"
        private const val NOTIF_ID = 9002
        private const val SESSION_TAG = "VisorLinkMusic"

        const val ACTION_TOGGLE = "by.iposdev.visorlink.MUSIC_TOGGLE"
        const val ACTION_PREV = "by.iposdev.visorlink.MUSIC_PREV"
        const val ACTION_NEXT = "by.iposdev.visorlink.MUSIC_NEXT"
        const val ACTION_STOP = "by.iposdev.visorlink.MUSIC_STOP"
    }

    init {
        createNotificationChannel()
        setupMediaSession()
        initExoPlayer()
    }

    private fun initExoPlayer() {
        if (exoPlayer != null) return
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            _state.value = _state.value.copy(isLoading = true)
                        }
                        Player.STATE_READY -> {
                            val duration = duration.coerceAtLeast(0L)
                            _state.value = _state.value.copy(
                                isLoading = false,
                                durationMs = duration
                            )
                            updateMediaSession()
                            showNotification()
                        }
                        Player.STATE_ENDED -> {
                            onTrackEnded()
                        }
                        Player.STATE_IDLE -> {
                            _state.value = _state.value.copy(isLoading = false)
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.value = _state.value.copy(isPlaying = isPlaying)
                    if (isPlaying) {
                        startProgressUpdates()
                    } else {
                        stopProgressUpdates()
                    }
                    updateMediaSession()
                    showNotification()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isPlaying = false,
                        error = error.localizedMessage ?: "Playback error"
                    )
                    stopProgressUpdates()
                    updateMediaSession()
                }
            })
        }
    }

    fun openFullscreenPlayer() {
        _showFullscreenPlayer.value = true
    }

    fun closeFullscreenPlayer() {
        _showFullscreenPlayer.value = false
    }

    fun playTrack(track: MusicTrack, queue: List<MusicTrack> = listOf(track), startIndex: Int = -1) {
        val currentQueue = if (queue.isNotEmpty()) queue else listOf(track)
        originalQueue = currentQueue
        val actualQueue = if (_state.value.isShuffle) currentQueue.shuffled() else currentQueue
        val resolvedIndex = if (startIndex in actualQueue.indices) {
            startIndex
        } else {
            actualQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        }

        _state.value = _state.value.copy(
            currentTrack = track,
            playlist = actualQueue,
            currentIndex = resolvedIndex,
            isLoading = true,
            error = null,
            progress = 0f,
            currentMs = 0L,
            durationMs = (track.duration * 1000).toLong()
        )

        prepareAndPlay(track)
    }

    private fun prepareAndPlay(track: MusicTrack) {
        initExoPlayer()
        val player = exoPlayer ?: return

        val uriString = when {
            !track.localPath.isNullOrBlank() && File(track.localPath).exists() -> {
                Uri.fromFile(File(track.localPath)).toString()
            }
            !track.url.isNullOrBlank() -> track.url
            else -> null
        }

        if (uriString == null) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = "Не найден источник для воспроизведения"
            )
            return
        }

        try {
            val mediaItem = MediaItem.fromUri(Uri.parse(uriString))
            player.setMediaItem(mediaItem)
            player.playbackParameters = PlaybackParameters(_state.value.speed)
            player.prepare()
            player.play()
            mediaSession?.isActive = true
            showNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing track: ${track.id}", e)
            _state.value = _state.value.copy(isLoading = false, error = e.localizedMessage)
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        val player = exoPlayer ?: return
        if (player.playbackState == Player.STATE_IDLE && _state.value.currentTrack != null) {
            prepareAndPlay(_state.value.currentTrack!!)
        } else {
            player.play()
        }
    }

    fun stop() {
        stopProgressUpdates()
        exoPlayer?.stop()
        _state.value = _state.value.copy(isPlaying = false, progress = 0f, currentMs = 0L)
        hideNotification()
        mediaSession?.isActive = false
    }

    fun seekTo(fraction: Float) {
        val player = exoPlayer ?: return
        val duration = player.duration.coerceAtLeast(0L)
        if (duration > 0) {
            val targetMs = (fraction * duration).toLong()
            player.seekTo(targetMs)
            _state.value = _state.value.copy(
                progress = fraction,
                currentMs = targetMs
            )
            updateMediaSession()
        }
    }

    fun seekToMs(positionMs: Long) {
        val player = exoPlayer ?: return
        player.seekTo(positionMs)
        val duration = player.duration.coerceAtLeast(0L)
        val fraction = if (duration > 0) positionMs.toFloat() / duration else 0f
        _state.value = _state.value.copy(
            progress = fraction,
            currentMs = positionMs
        )
        updateMediaSession()
    }

    fun playNext() {
        val queue = _state.value.playlist
        if (queue.isEmpty()) return
        val nextIndex = _state.value.currentIndex + 1
        if (nextIndex in queue.indices) {
            val nextTrack = queue[nextIndex]
            _state.value = _state.value.copy(currentIndex = nextIndex, currentTrack = nextTrack)
            prepareAndPlay(nextTrack)
        } else if (_state.value.repeatMode == MusicRepeatMode.ALL) {
            val nextTrack = queue[0]
            _state.value = _state.value.copy(currentIndex = 0, currentTrack = nextTrack)
            prepareAndPlay(nextTrack)
        } else {
            stop()
        }
    }

    fun playPrevious() {
        val queue = _state.value.playlist
        if (queue.isEmpty()) return

        // Если уже играет дольше 3 секунд, предыдущий трек просто начинает сначала
        if (_state.value.currentMs > 3000L) {
            seekToMs(0L)
            return
        }

        val prevIndex = _state.value.currentIndex - 1
        if (prevIndex in queue.indices) {
            val prevTrack = queue[prevIndex]
            _state.value = _state.value.copy(currentIndex = prevIndex, currentTrack = prevTrack)
            prepareAndPlay(prevTrack)
        } else if (_state.value.repeatMode == MusicRepeatMode.ALL) {
            val lastIndex = queue.lastIndex
            val prevTrack = queue[lastIndex]
            _state.value = _state.value.copy(currentIndex = lastIndex, currentTrack = prevTrack)
            prepareAndPlay(prevTrack)
        } else {
            seekToMs(0L)
        }
    }

    fun cycleSpeed(): Float {
        val currentSpeed = _state.value.speed
        val newSpeed = when {
            currentSpeed < 1.25f -> 1.5f
            currentSpeed < 1.75f -> 2.0f
            else -> 1.0f
        }
        exoPlayer?.playbackParameters = PlaybackParameters(newSpeed)
        _state.value = _state.value.copy(speed = newSpeed)
        return newSpeed
    }

    fun toggleShuffle() {
        val newShuffle = !_state.value.isShuffle
        val currentTrack = _state.value.currentTrack
        val newPlaylist = if (newShuffle) {
            val shuffled = originalQueue.toMutableList()
            if (currentTrack != null) {
                shuffled.remove(currentTrack)
                shuffled.shuffle()
                shuffled.add(0, currentTrack)
            } else {
                shuffled.shuffle()
            }
            shuffled
        } else {
            originalQueue
        }
        val newIndex = if (currentTrack != null) newPlaylist.indexOfFirst { it.id == currentTrack.id } else 0
        _state.value = _state.value.copy(
            isShuffle = newShuffle,
            playlist = newPlaylist,
            currentIndex = newIndex.coerceAtLeast(0)
        )
    }

    fun cycleRepeatMode(): MusicRepeatMode {
        val nextMode = when (_state.value.repeatMode) {
            MusicRepeatMode.OFF -> MusicRepeatMode.ALL
            MusicRepeatMode.ALL -> MusicRepeatMode.ONE
            MusicRepeatMode.ONE -> MusicRepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = nextMode)
        return nextMode
    }

    fun updateFavoriteStatus(trackId: String, isFav: Boolean) {
        if (_state.value.currentTrack?.id == trackId) {
            _state.value = _state.value.copy(
                currentTrack = _state.value.currentTrack?.copy(isFavorite = isFav)
            )
        }
        val updatedPlaylist = _state.value.playlist.map {
            if (it.id == trackId) it.copy(isFavorite = isFav) else it
        }
        _state.value = _state.value.copy(playlist = updatedPlaylist)
    }

    private fun onTrackEnded() {
        when (_state.value.repeatMode) {
            MusicRepeatMode.ONE -> {
                seekToMs(0L)
                exoPlayer?.play()
            }
            MusicRepeatMode.ALL, MusicRepeatMode.OFF -> {
                playNext()
            }
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressRunnable = object : Runnable {
            override fun run() {
                val player = exoPlayer
                if (player != null && player.isPlaying) {
                    val current = player.currentPosition.coerceAtLeast(0L)
                    val duration = player.duration.coerceAtLeast(0L)
                    val frac = if (duration > 0) (current.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    _state.value = _state.value.copy(
                        currentMs = current,
                        durationMs = duration,
                        progress = frac
                    )
                    handler.postDelayed(this, 250L)
                }
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    // ── MediaSession & Notifications ──────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName("Воспроизведение музыки")
            .setDescription("Управление воспроизведением музыки")
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(context, SESSION_TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = playNext()
                override fun onSkipToPrevious() = playPrevious()
                override fun onStop() = stop()
                override fun onSeekTo(pos: Long) = seekToMs(pos)
            })
            isActive = false
        }
    }

    private fun updateMediaSession() {
        val session = mediaSession ?: return
        val track = _state.value.currentTrack ?: return
        val isPlaying = _state.value.isPlaying

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                _state.value.currentMs,
                _state.value.speed
            )
            .build()
        session.setPlaybackState(playbackState)

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.displayTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.displayPerformer)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, _state.value.durationMs)
            .build()
        session.setMetadata(metadata)
    }

    private fun showNotification() {
        val track = _state.value.currentTrack ?: return
        val session = mediaSession ?: return

        // Content intent: Открыть приложение и полноэкранный плеер
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_music_player", true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            200,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Broadcast actions
        val prevPending = PendingIntent.getBroadcast(
            context, 201, Intent(ACTION_PREV).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val togglePending = PendingIntent.getBroadcast(
            context, 202, Intent(ACTION_TOGGLE).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextPending = PendingIntent.getBroadcast(
            context, 203, Intent(ACTION_NEXT).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getBroadcast(
            context, 204, Intent(ACTION_STOP).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (_state.value.isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(track.displayTitle)
            .setContentText(track.displayPerformer)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(stopPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(_state.value.isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseIcon, if (_state.value.isPlaying) "Pause" else "Play", togglePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing: ${e.message}")
        }
    }

    private fun hideNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    fun release() {
        stop()
        exoPlayer?.release()
        exoPlayer = null
        mediaSession?.release()
        mediaSession = null
        scope.cancel()
    }
}
