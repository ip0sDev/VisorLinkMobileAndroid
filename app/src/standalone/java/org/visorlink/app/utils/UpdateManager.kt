package org.visorlink.app.utils

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import com.ipos.store.sdk.IposStoreUpdates
import com.ipos.store.sdk.UpdateChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.visorlink.app.R

/**
 * Реализация менеджера обновлений для автономной сборки (standalone).
 * Использует Ipos Store SDK для автоматической проверки, скачивания и установки обновлений.
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PREFS_NAME = "visorlink_settings"
    private const val KEY_CHANNEL = "update_channel"

    val isSupported: Boolean = true

    fun init(app: Application) {
        try {
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val channelStr = prefs.getString(KEY_CHANNEL, "release") ?: "release"
            val channel = parseChannel(channelStr)
            IposStoreUpdates.init(app, channel = channel)
            Log.d(TAG, "IposStoreUpdates initialized with channel: $channel")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize IposStoreUpdates", e)
        }
    }

    fun onAppForegroundCheck(context: Context) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val channel = parseChannel(getSavedChannel(context))
                IposStoreUpdates.checkUpdate(channel = channel)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking update on foreground", e)
            }
        }
    }

    fun isStoreInstalled(): Boolean {
        return try {
            IposStoreUpdates.isStoreInstalled()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking isStoreInstalled", e)
            false
        }
    }

    fun openStoreDownload() {
        try {
            IposStoreUpdates.openStoreDownload()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening store download", e)
        }
    }

    fun getSavedChannel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CHANNEL, "release") ?: "release"
    }

    fun setChannel(context: Context, channelKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CHANNEL, channelKey).apply()
        val channel = parseChannel(channelKey)
        try {
            IposStoreUpdates.init(context, channel = channel)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating channel in SDK", e)
        }
    }

    fun getAvailableChannels(context: Context): List<Pair<String, String>> {
        return listOf(
            "release" to context.getString(R.string.settings_update_channel_release),
            "beta" to context.getString(R.string.settings_update_channel_beta),
            "nightly" to context.getString(R.string.settings_update_channel_nightly)
        )
    }

    suspend fun checkUpdate(
        context: Context,
        onResult: (isUpdateAvailable: Boolean, message: String?) -> Unit
    ) {
        try {
            val channel = parseChannel(getSavedChannel(context))
            val update = IposStoreUpdates.checkUpdate(channel = channel)
            if (update != null) {
                onResult(true, null)
            } else {
                onResult(false, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check update failed", e)
            onResult(false, e.localizedMessage ?: "Check failed")
        }
    }

    @Composable
    fun UpdateHost() {
        IposStoreUpdates.IposUpdateHost()
    }

    private fun parseChannel(channelStr: String): UpdateChannel {
        return try {
            UpdateChannel.fromString(channelStr)
        } catch (_: IllegalArgumentException) {
            UpdateChannel.RELEASE
        }
    }
}
