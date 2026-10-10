package org.visorlink.app.utils

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ipos.store.sdk.UpdateState
import com.ipos.store.sdk.UpdateType
import org.visorlink.app.ui.maintenance.ForceUpdateScreen
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

    /**
     * @param force обновление обязательно (`AppFlags.forceUpdateRequired`): приложение закрыто
     *   экраном «Требуется обновление», Ipos Store показывает полноэкранное обновление
     *   (`UpdateType.IMMEDIATE`, без «Позже»). Сервер Ipos Store ещё не отдал версию — экран
     *   остаётся с «Проверить снова».
     */
    @Composable
    fun UpdateHost(force: Boolean = false) {
        if (!force) {
            IposStoreUpdates.IposUpdateHost()
            return
        }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val state by IposStoreUpdates.updateState.collectAsState()
        // Флаг пришёл — сразу проверяем обновление (иначе SDK узнал бы о нём при следующем запуске)
        LaunchedEffect(Unit) { runCatching { IposStoreUpdates.checkUpdate(channel = parseChannel(getSavedChannel(context))) } }
        val progress = (state as? UpdateState.Downloading)?.progress?.let { if (it > 1f) it / 100f else it }
        ForceUpdateScreen(
            progress = progress,
            onUpdate = {
                val activity = context.findActivity()
                val channel = parseChannel(getSavedChannel(context))
                if (activity != null) {
                    runCatching { IposStoreUpdates.showUpdateIfAvailable(activity, UpdateType.IMMEDIATE, channel) }
                        .onFailure { Log.e(TAG, "Force update failed", it) }
                }
            },
            secondaryLabel = stringResource(R.string.service_mode_retry),
            onSecondary = {
                scope.launch {
                    val found = runCatching { IposStoreUpdates.checkUpdate(channel = parseChannel(getSavedChannel(context))) }.getOrNull()
                    if (found == null) Toast.makeText(context, R.string.update_force_not_found, Toast.LENGTH_SHORT).show()
                }
            },
        )
        // Полноэкранное обновление SDK — поверх экрана, как только версия найдена
        IposStoreUpdates.IposUpdateHost(UpdateType.IMMEDIATE)
    }

    private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private fun parseChannel(channelStr: String): UpdateChannel {
        return try {
            UpdateChannel.fromString(channelStr)
        } catch (_: IllegalArgumentException) {
            UpdateChannel.RELEASE
        }
    }
}
