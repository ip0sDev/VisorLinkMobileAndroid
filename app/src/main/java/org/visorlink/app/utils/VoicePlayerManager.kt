package org.visorlink.app.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VoicePlaybackState(
    val playingMessageId: String? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,           // 0f..1f
    val currentMs: Int = 0,
    val durationMs: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class VoicePlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackJob: Job? = null

    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    private var currentMessageId: String? = null
    private var progressRunnable: Runnable? = null

    companion object {
        private const val CHANNEL_ID = "voice_playback"
        private const val NOTIF_ID = 9001
        private const val SESSION_TAG = "VisorLinkVoice"
    }

    init {
        createNotificationChannel()
        setupMediaSession()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun play(messageId: String, url: String, durationSec: Int) {
        if (currentMessageId == messageId && mediaPlayer != null) {
            togglePlayPause()
            return
        }
        stopAndReset()
        currentMessageId = messageId
        _state.value = VoicePlaybackState(
            playingMessageId = messageId,
            isLoading = true,
            durationMs = durationSec * 1000
        )
        startPlayback(messageId, url, durationSec)
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) pause() else resume()
    }

    fun seekTo(fraction: Float) {
        val player = mediaPlayer ?: return
        val ms = (fraction * player.duration).toInt()
        player.seekTo(ms)
        _state.value = _state.value.copy(
            progress = fraction,
            currentMs = ms
        )
        updateMediaSession()
    }

    fun stop() {
        stopAndReset()
        hideNotification()
    }

    fun release() {
        playbackJob?.cancel()
        playbackJob = null
        scope.cancel()
        stopProgressUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
        hideNotification()
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun startPlayback(messageId: String, url: String, durationSec: Int) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    VoiceCache.getOrDownload(context, url)
                }

                if (!isActive || currentMessageId != messageId) return@launch

                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(context, Uri.fromFile(file))
                    setOnPreparedListener { mp ->
                        if (currentMessageId != messageId) {
                            try { mp.release() } catch (_: Exception) {}
                            return@setOnPreparedListener
                        }
                        _state.value = _state.value.copy(
                            isLoading = false,
                            isPlaying = true,
                            durationMs = mp.duration.takeIf { it > 0 } ?: (durationSec * 1000)
                        )
                        mp.start()
                        startProgressUpdates()
                        showNotification()
                        updateMediaSession(playing = true)
                    }
                    setOnCompletionListener {
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            progress = 0f,
                            currentMs = 0
                        )
                        stopProgressUpdates()
                        updateMediaSession(playing = false)
                        hideNotification()
                        currentMessageId = null
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e("VoicePlayerManager", "MediaPlayer error: what=$what, extra=$extra")
                        _state.value = _state.value.copy(
                            isLoading = false,
                            isPlaying = false,
                            error = "Playback error"
                        )
                        hideNotification()
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                Log.e("VoicePlayerManager", "Playback failed for $url", e)
                if (currentMessageId == messageId) {
                    _state.value = _state.value.copy(isLoading = false, isPlaying = false, error = e.message)
                }
            }
        }
    }

    private fun pause() {
        mediaPlayer?.pause()
        stopProgressUpdates()
        _state.value = _state.value.copy(isPlaying = false)
        updateMediaSession(playing = false)
        showNotification()
    }

    private fun resume() {
        mediaPlayer?.start()
        startProgressUpdates()
        _state.value = _state.value.copy(isPlaying = true)
        updateMediaSession(playing = true)
        showNotification()
    }

    private fun stopAndReset() {
        playbackJob?.cancel()
        playbackJob = null
        stopProgressUpdates()
        try {
            mediaPlayer?.apply { if (isPlaying) stop(); reset(); release() }
        } catch (_: Exception) {}
        mediaPlayer = null
        currentMessageId = null
        _state.value = VoicePlaybackState()
    }

    // ── Progress ticker ───────────────────────────────────────────────────────

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressRunnable = object : Runnable {
            override fun run() {
                val player = mediaPlayer ?: return
                try {
                    if (player.isPlaying) {
                        val current = player.currentPosition
                        val duration = player.duration.takeIf { it > 0 } ?: 1
                        _state.value = _state.value.copy(
                            currentMs = current,
                            progress = current.toFloat() / duration
                        )
                        handler.postDelayed(this, 100)
                    }
                } catch (_: Exception) {}
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    // ── MediaSession ──────────────────────────────────────────────────────────

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(context, SESSION_TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resume() }
                override fun onPause() { pause() }
                override fun onStop() { stop() }
                override fun onSeekTo(pos: Long) {
                    mediaPlayer?.let { player ->
                        player.seekTo(pos.toInt())
                        _state.value = _state.value.copy(
                            currentMs = pos.toInt(),
                            progress = pos.toFloat() / player.duration
                        )
                    }
                }
            })
            isActive = true
        }
    }

    private fun updateMediaSession(playing: Boolean = _state.value.isPlaying) {
        val session = mediaSession ?: return
        val player = mediaPlayer

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                player?.currentPosition?.toLong() ?: 0L,
                1f
            )
        session.setPlaybackState(stateBuilder.build())

        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Voice Message")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "VisorLink")
            .putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                player?.duration?.toLong() ?: _state.value.durationMs.toLong()
            )
        session.setMetadata(metaBuilder.build())
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName("Voice Playback")
            .setDescription("Voice message player controls")
            .setSound(null, null)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun showNotification() {
        val session = mediaSession ?: return
        val isPlaying = _state.value.isPlaying
        val currentSec = _state.value.currentMs / 1000
        val totalSec = _state.value.durationMs / 1000

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Voice Message")
            .setContentText(
                "${formatTime(currentSec)} / ${formatTime(totalSec)}"
            )
            .setOngoing(isPlaying)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Кнопки управления
            .addAction(
                android.R.drawable.ic_media_previous,
                "Rewind",
                buildSeekPendingIntent(-10_000)
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                buildPlayPausePendingIntent()
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Forward",
                buildSeekPendingIntent(10_000)
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {}
    }

    private fun hideNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    private fun buildPlayPausePendingIntent(): android.app.PendingIntent {
        val intent = android.content.Intent("org.visorlink.app.VOICE_TOGGLE")
            .setPackage(context.packageName)
        return android.app.PendingIntent.getBroadcast(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildSeekPendingIntent(offsetMs: Int): android.app.PendingIntent {
        val intent = android.content.Intent("org.visorlink.app.VOICE_SEEK")
            .setPackage(context.packageName)
            .putExtra("offset_ms", offsetMs)
        return android.app.PendingIntent.getBroadcast(
            context, offsetMs, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatTime(sec: Int): String =
        "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
}