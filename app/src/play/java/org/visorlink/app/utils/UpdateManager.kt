package org.visorlink.app.utils

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable

/**
 * Реализация менеджера обновлений для сборки Google Play (play).
 * В соответствии с политиками Google Play (Device and Network Abuse policy),
 * прямое скачивание и установка APK в обход магазина отключены.
 */
object UpdateManager {

    val isSupported: Boolean = false

    fun init(app: Application) {
        // No-op: обновления управляются сервисами Google Play
    }

    fun onAppForegroundCheck(context: Context) {
        // No-op
    }

    fun isStoreInstalled(): Boolean = false

    fun openStoreDownload() {
        // No-op
    }

    fun getSavedChannel(context: Context): String = "play"

    fun setChannel(context: Context, channel: String) {
        // No-op
    }

    fun getAvailableChannels(context: Context): List<Pair<String, String>> = emptyList()

    suspend fun checkUpdate(
        context: Context,
        onResult: (isUpdateAvailable: Boolean, message: String?) -> Unit
    ) {
        onResult(false, null)
    }

    @Composable
    fun UpdateHost() {
        // No-op: интерфейс обновления не отображается
    }
}
