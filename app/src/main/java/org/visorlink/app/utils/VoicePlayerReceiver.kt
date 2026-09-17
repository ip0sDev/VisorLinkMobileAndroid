package org.visorlink.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class VoicePlayerReceiver : BroadcastReceiver(), KoinComponent {

    private val player: VoicePlayerManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "org.visorlink.app.VOICE_TOGGLE" -> {
                player.togglePlayPause()
            }
            "org.visorlink.app.VOICE_SEEK" -> {
                val offsetMs = intent.getIntExtra("offset_ms", 0)
                val state = player.state.value
                val player2 = player
                val currentMs = state.currentMs
                val durationMs = state.durationMs.takeIf { it > 0 } ?: 1
                val newFraction = ((currentMs + offsetMs).coerceIn(0, durationMs)).toFloat() / durationMs
                player2.seekTo(newFraction)
            }
        }
    }
}