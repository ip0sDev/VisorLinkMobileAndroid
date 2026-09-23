package org.visorlink.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MusicPlayerReceiver : BroadcastReceiver(), KoinComponent {

    private val player: MusicPlayerManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MusicPlayerManager.ACTION_TOGGLE -> player.togglePlayPause()
            MusicPlayerManager.ACTION_PREV -> player.playPrevious()
            MusicPlayerManager.ACTION_NEXT -> player.playNext()
            // Крестик в шторке закрывает плеер целиком, а не ставит его на паузу
            MusicPlayerManager.ACTION_STOP -> player.dismiss()
        }
    }
}
