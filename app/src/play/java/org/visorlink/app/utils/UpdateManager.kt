package org.visorlink.app.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.visorlink.app.BuildConfig
import org.visorlink.app.R
import org.visorlink.app.ui.components.UpdateAvailableDialog
import org.visorlink.app.ui.components.UpdateReadyDialog

/**
 * Обновления сборки Google Play (play) — через Play In-App Updates: APK в обход магазина по
 * правилам Play (Device and Network Abuse) не ставим, только предлагаем обновиться из Play.
 *
 * Попап «Доступно обновление» всегда закрывается («Позже», «назад», тап мимо) и после этого не
 * возвращается сутки для той же версии. Обновление гибкое, если Play его разрешает: качается в
 * фоне, затем — «Перезапустить». Иначе немедленное: экран Play, который тоже можно отменить.
 * Вне Play (sideload, эмулятор без аккаунта) API недоступен — попапа нет.
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PREFS = "visorlink_update_prefs"
    private const val KEY_SNOOZED_VERSION = "play_snoozed_version"
    private const val KEY_SNOOZED_UNTIL = "play_snoozed_until"
    private const val SNOOZE_MS = 24L * 3600 * 1000

    /** Раздел «Обновления» в настройках — про Ipos Store; в Play-сборке его нет. */
    val isSupported: Boolean = false

    private sealed interface Prompt {
        data class Available(val info: AppUpdateInfo) : Prompt
        data object Ready : Prompt
    }

    private val prompt = MutableStateFlow<Prompt?>(null)

    @Volatile private var manager: AppUpdateManager? = null
    private var appContext: Context? = null
    private var listening = false

    /** «Позже» на загруженном обновлении — до перезапуска процесса. */
    private var readyDismissed = false

    /** Какой поток запущен последним и для какой версии — для отмены и возврата в немедленный. */
    private var lastFlowType: Int? = null
    private var pendingVersion = 0

    private val installListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                readyDismissed = false
                prompt.value = Prompt.Ready
                stopListening()
            }
            InstallStatus.FAILED -> {
                appContext?.let { Toast.makeText(it, R.string.update_play_failed, Toast.LENGTH_SHORT).show() }
                stopListening()
            }
            InstallStatus.CANCELED, InstallStatus.INSTALLED -> stopListening()
            else -> Unit
        }
    }

    private fun manager(context: Context): AppUpdateManager = manager ?: synchronized(this) {
        manager ?: AppUpdateManagerFactory.create(context.applicationContext).also {
            manager = it
            appContext = context.applicationContext
        }
    }

    fun init(app: Application) {
        // Менеджер создаётся лениво, при первой проверке в UpdateHost
    }

    fun onAppForegroundCheck(context: Context) {
        // Проверку делает UpdateHost на каждом onResume — там есть лаунчер для экрана Play
    }

    fun isStoreInstalled(): Boolean = false

    fun openStoreDownload() {
        // No-op: в Play-сборке Ipos Store не нужен
    }

    fun getSavedChannel(context: Context): String = "play"

    fun setChannel(context: Context, channel: String) {
        // No-op: канал — дорожка Google Play
    }

    fun getAvailableChannels(context: Context): List<Pair<String, String>> = emptyList()

    suspend fun checkUpdate(
        context: Context,
        onResult: (isUpdateAvailable: Boolean, message: String?) -> Unit
    ) {
        val info = requestInfo(context)
        onResult(info?.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE, null)
    }

    @Composable
    fun UpdateHost() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val state by prompt.collectAsState()
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> if (lastFlowType == AppUpdateType.FLEXIBLE) {
                    Toast.makeText(context, R.string.update_status_toast, Toast.LENGTH_SHORT).show()
                }
                // Отказ в окне Play — то же «Позже»
                Activity.RESULT_CANCELED -> snooze(context, pendingVersion)
                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> Toast.makeText(context, R.string.update_play_failed, Toast.LENGTH_SHORT).show()
            }
        }

        // Проверка на каждом возврате в приложение (и при старте: observer получает ON_RESUME сразу)
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) scope.launch { onResume(context, launcher) }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        when (val p = state) {
            is Prompt.Available -> UpdateAvailableDialog(
                sizeText = p.info.totalBytesToDownload().takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
                onUpdate = {
                    prompt.value = null
                    startFlow(context, p.info, launcher)
                },
                onLater = {
                    prompt.value = null
                    snooze(context, p.info.availableVersionCode())
                },
            )
            Prompt.Ready -> UpdateReadyDialog(
                onRestart = {
                    prompt.value = null
                    manager(context).completeUpdate()
                },
                onLater = {
                    readyDismissed = true
                    prompt.value = null
                },
            )
            null -> Unit
        }
    }

    private suspend fun requestInfo(context: Context): AppUpdateInfo? = try {
        manager(context).requestAppUpdateInfo()
    } catch (e: Exception) {
        // Приложение не из Play или нет сервисов Play — это не ошибка
        if (BuildConfig.DEBUG) Log.d(TAG, "In-app updates unavailable: ${e.message}")
        null
    }

    private suspend fun onResume(context: Context, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val info = requestInfo(context) ?: return
        val status = info.installStatus()
        when {
            status == InstallStatus.DOWNLOADED -> if (!readyDismissed) prompt.value = Prompt.Ready
            // Гибкая загрузка идёт (возможно, начата в прошлом запуске) — ждём её конца
            status == InstallStatus.PENDING || status == InstallStatus.DOWNLOADING -> listen(context)
            // Вернулись посреди немедленного обновления — Play просит показать его экран снова
            info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                lastFlowType == AppUpdateType.IMMEDIATE -> startFlow(context, info, launcher, AppUpdateType.IMMEDIATE)
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                status != InstallStatus.INSTALLING &&
                (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) || info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) &&
                !isSnoozed(context, info.availableVersionCode()) -> prompt.value = Prompt.Available(info)
        }
    }

    private fun startFlow(
        context: Context,
        info: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        forcedType: Int? = null,
    ) {
        val type = forcedType ?: if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) AppUpdateType.FLEXIBLE else AppUpdateType.IMMEDIATE
        lastFlowType = type
        pendingVersion = info.availableVersionCode()
        if (type == AppUpdateType.FLEXIBLE) listen(context)
        try {
            manager(context).startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start update flow", e)
            Toast.makeText(context, R.string.update_play_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun listen(context: Context) {
        if (listening) return
        listening = true
        manager(context).registerListener(installListener)
    }

    private fun stopListening() {
        if (!listening) return
        listening = false
        manager?.unregisterListener(installListener)
    }

    private fun isSnoozed(context: Context, versionCode: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SNOOZED_VERSION, 0) == versionCode &&
            System.currentTimeMillis() < prefs.getLong(KEY_SNOOZED_UNTIL, 0)
    }

    private fun snooze(context: Context, versionCode: Int) {
        if (versionCode <= 0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SNOOZED_VERSION, versionCode)
            .putLong(KEY_SNOOZED_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
            .apply()
    }
}
