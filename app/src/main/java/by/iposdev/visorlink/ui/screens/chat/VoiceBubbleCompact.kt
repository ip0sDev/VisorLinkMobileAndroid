package by.iposdev.visorlink.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import by.iposdev.visorlink.ui.components.chat.VoiceBubble
import by.iposdev.visorlink.utils.VoicePlaybackState

/**
 * Public alias for the internal VoiceBubble composable in ChatScreen.kt.
 * CommentsScreen uses this to render voice messages inside comment bubbles
 * without duplicating the waveform + playback logic.
 *
 * Simply delegates to the existing VoiceBubble that lives in ChatScreen.kt.
 * If VoiceBubble is already `internal`, change its visibility to `internal` → keep it,
 * and expose this thin wrapper instead.
 */
@Composable
fun VoiceBubbleCompact(
    messageId: String,
    url: String,
    durationSec: Int,
    tint: Color,
    playback: VoicePlaybackState,
    onPlay: (url: String, durationSec: Int) -> Unit,
    onSeek: (Float) -> Unit
) {
    // VoiceBubble is a private composable in ChatScreen.kt — make it internal
    // (change `private fun VoiceBubble` → `internal fun VoiceBubble`) and call it here.
    VoiceBubble(
        messageId = messageId,
        url = url,
        durationSec = durationSec,
        tint = tint,
        playback = playback,
        onPlay = onPlay,
        onSeek = onSeek
    )
}