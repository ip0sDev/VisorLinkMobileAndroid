package org.visorlink.app.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import org.visorlink.app.ui.maintenance.ForceUpdateScreen

/**
 * Обновления сборки Google Play (play) — через Play In-App Updates: APK в обход магазина по
 * правилам Play (Device and Network Abuse) не ставим, только предлагаем обновиться из Play.
 *
 * Обычный режим: попап «Доступно обновление» всегда закрывается («Позже», «назад», тап мимо) и
 * после этого не возвращается сутки для той же версии. Обновление гибкое, если Play его
 * разрешает: качается в фоне, затем — «Перезапустить». **Начатое обновление запоминается по
 * версии:** пока оно качается (даже если окно Play свернули или приложение перезапустили),
 * попап не показывается — следующим будет «Перезапустить», когда загрузка закончится.
 *
 * Принудительный режим (`force_update_min_version` больше текущей сборки, см. `AppFlags`):
 * приложение закрыто экраном «Требуется обновление», запускается немедленное обновление Play
 * (его экран Play держит сам), загруженное ставится без вопросов.
 *
 * Вне Play (sideload, эмулятор без аккаунта) API недоступен — попапа нет; в принудительном
 * режиме экран ведёт на страницу приложения в Google Play.
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PREFS = "visorlink_update_prefs"
    private const val KEY_SNOOZED_VERSION = "play_snoozed_version"
    private const val KEY_SNOOZED_UNTIL = "play_snoozed_until"
    private const val KEY_STARTED_VERSION = "play_started_version"
    private const val KEY_STARTED_AT = "play_started_at"
    private const val KEY_STARTED_TYPE = "play_started_type"
    /** Версия, для которой открыто окно Play (до ответа пользователя). */
    private const val KEY_PENDING_VERSION = "play_pending_version"
    private const val SNOOZE_MS = 24L * 3600 * 1000
    /** Сколько считать начатое обновление «в пути»: дольше — Play его явно потерял. */
    private const val STARTED_TTL_MS = 12L * 3600 * 1000

    /** Раздел «Обновления» в настройках — про Ipos Store; в Play-сборке его нет. */
    val isSupported: Boolean = false

    private sealed interface Prompt {
        data class Available(val info: AppUpdateInfo) : Prompt
        data object Ready : Prompt
    }

    private val prompt = MutableStateFlow<Prompt?>(null)

    /** Прогресс гибкой загрузки 0…1; `null` — не качается. */
    private val progress = MutableStateFlow<Float?>(null)

    /** Последний ответ Play — для кнопки на экране принудительного обновления. */
    private val lastInfo = MutableStateFlow<AppUpdateInfo?>(null)

    @Volatile private var manager: AppUpdateManager? = null
    private var appContext: Context? = null
    private var listening = false

    /** «Позже» на загруженном обновлении — до перезапуска процесса. */
    private var readyDismissed = false

    /** Принудительное обновление уже запускали сами в этом процессе — дальше по кнопке, без цикла. */
    private var forceAutoStarted = false

    private val installListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.PENDING -> progress.value = 0f
            InstallStatus.DOWNLOADING -> {
                val total = state.totalBytesToDownload()
                progress.value = if (total > 0) state.bytesDownloaded().toFloat() / total else 0f
            }
            InstallStatus.DOWNLOADED -> {
                progress.value = null
                readyDismissed = false
                prompt.value = Prompt.Ready
                stopListening()
            }
            InstallStatus.FAILED -> {
                progress.value = null
                appContext?.let {
                    clearStarted(it)
                    Toast.makeText(it, R.string.update_play_failed, Toast.LENGTH_SHORT).show()
                }
                stopListening()
            }
            InstallStatus.CANCELED -> {
                progress.value = null
                appContext?.let { clearStarted(it) }
                stopListening()
            }
            InstallStatus.INSTALLED -> {
                progress.value = null
                appContext?.let { clearStarted(it) }
                stopListening()
            }
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

    /**
     * @param force обновление обязательно (`AppFlags.forceUpdateRequired`): приложение закрыто
     *   экраном обновления, запускается немедленное обновление Play.
     */
    @Composable
    fun UpdateHost(force: Boolean = false) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val state by prompt.collectAsState()
        val downloading by progress.collectAsState()
        val forceNow by rememberUpdatedState(force)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            when (result.resultCode) {
                // Пользователь согласился: обновление «в пути», попап не возвращается до конца загрузки
                Activity.RESULT_OK -> {
                    markStarted(context)
                    if (startedType(context) == AppUpdateType.FLEXIBLE) {
                        Toast.makeText(context, R.string.update_status_toast, Toast.LENGTH_SHORT).show()
                    }
                }
                // Отказ в окне Play — то же «Позже» (в принудительном режиме откладывать нельзя)
                Activity.RESULT_CANCELED -> {
                    clearStarted(context)
                    if (!forceNow) snooze(context, pendingVersion(context))
                }
                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                    clearStarted(context)
                    Toast.makeText(context, R.string.update_play_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Проверка на каждом возврате в приложение (и при старте: observer получает ON_RESUME сразу)
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) scope.launch { onResume(context, launcher, forceNow) }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        // Флаг пришёл, пока приложение открыто — проверяем сразу, не дожидаясь возврата
        LaunchedEffect(force) { if (force) onResume(context, launcher, true) }

        if (force) {
            // Загружено — ставим без вопросов; иначе экран обновления поверх всего приложения
            LaunchedEffect(state) { if (state == Prompt.Ready) manager(context).completeUpdate() }
            ForceUpdateScreen(
                progress = downloading,
                onUpdate = {
                    val info = lastInfo.value
                    if (info != null && info.updateAvailability() != UpdateAvailability.UPDATE_NOT_AVAILABLE &&
                        (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) || info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE))
                    ) {
                        startFlow(context, info, launcher, forced = true)
                    } else {
                        openPlayListing(context)
                    }
                },
                secondaryLabel = stringResource(R.string.update_force_open_play),
                onSecondary = { openPlayListing(context) },
            )
            return
        }

        when (val p = state) {
            is Prompt.Available -> UpdateAvailableDialog(
                sizeText = p.info.totalBytesToDownload().takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
                onUpdate = {
                    prompt.value = null
                    startFlow(context, p.info, launcher, forced = false)
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
        manager(context).requestAppUpdateInfo().also { lastInfo.value = it }
    } catch (e: Exception) {
        // Приложение не из Play или нет сервисов Play — это не ошибка
        if (BuildConfig.DEBUG) Log.d(TAG, "In-app updates unavailable: ${e.message}")
        null
    }

    private suspend fun onResume(context: Context, launcher: ActivityResultLauncher<IntentSenderRequest>, force: Boolean) {
        val info = requestInfo(context) ?: return
        val status = info.installStatus()
        val version = info.availableVersionCode()
        when {
            status == InstallStatus.DOWNLOADED -> {
                clearStarted(context)
                if (force || !readyDismissed) prompt.value = Prompt.Ready
            }
            // Гибкая загрузка идёт (возможно, начата в прошлом запуске) — ждём её конца, попап не нужен
            status == InstallStatus.PENDING || status == InstallStatus.DOWNLOADING -> {
                prompt.value = null
                listen(context)
            }
            // Вернулись посреди немедленного обновления — Play просит показать его экран снова
            info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                (startedType(context) == AppUpdateType.IMMEDIATE || force) ->
                startFlow(context, info, launcher, forced = force, type = AppUpdateType.IMMEDIATE)
            info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE || status == InstallStatus.INSTALLING -> {
                prompt.value = null
                if (info.updateAvailability() == UpdateAvailability.UPDATE_NOT_AVAILABLE) clearStarted(context)
            }
            // Обновление этой версии уже начато (окно Play свернули, статус ещё не дошёл) — не предлагаем снова
            isStarted(context, version) -> {
                prompt.value = null
                listen(context)
            }
            force -> {
                prompt.value = null
                // Немедленное обновление запускаем сами один раз за процесс; дальше — кнопкой на экране
                if (!forceAutoStarted) {
                    forceAutoStarted = true
                    startFlow(context, info, launcher, forced = true)
                }
            }
            (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) || info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) &&
                !isSnoozed(context, version) -> prompt.value = Prompt.Available(info)
        }
    }

    private fun startFlow(
        context: Context,
        info: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        forced: Boolean,
        type: Int? = null,
    ) {
        // Принудительно — немедленное (экран Play), если Play его разрешает; обычно — гибкое
        val chosen = type ?: when {
            forced && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
            else -> AppUpdateType.IMMEDIATE
        }
        prefs(context).edit()
            .putInt(KEY_STARTED_TYPE, chosen)
            .putInt(KEY_PENDING_VERSION, info.availableVersionCode())
            .apply()
        if (chosen == AppUpdateType.FLEXIBLE) listen(context)
        try {
            manager(context).startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(chosen).build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start update flow", e)
            Toast.makeText(context, R.string.update_play_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPlayListing(context: Context) {
        val pkg = context.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: Exception) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
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

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pendingVersion(context: Context) = prefs(context).getInt(KEY_PENDING_VERSION, 0)

    private fun startedType(context: Context) = prefs(context).getInt(KEY_STARTED_TYPE, AppUpdateType.FLEXIBLE)

    /** Пользователь согласился в окне Play — запоминаем, что эта версия уже в пути. */
    private fun markStarted(context: Context) {
        val version = pendingVersion(context)
        if (version <= 0) return
        prefs(context).edit()
            .putInt(KEY_STARTED_VERSION, version)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun isStarted(context: Context, versionCode: Int): Boolean {
        val p = prefs(context)
        return p.getInt(KEY_STARTED_VERSION, 0) == versionCode &&
            System.currentTimeMillis() - p.getLong(KEY_STARTED_AT, 0) < STARTED_TTL_MS
    }

    private fun clearStarted(context: Context) {
        prefs(context).edit().remove(KEY_STARTED_VERSION).remove(KEY_STARTED_AT).apply()
    }

    private fun isSnoozed(context: Context, versionCode: Int): Boolean {
        val p = prefs(context)
        return p.getInt(KEY_SNOOZED_VERSION, 0) == versionCode &&
            System.currentTimeMillis() < p.getLong(KEY_SNOOZED_UNTIL, 0)
    }

    private fun snooze(context: Context, versionCode: Int) {
        if (versionCode <= 0) return
        prefs(context).edit()
            .putInt(KEY_SNOOZED_VERSION, versionCode)
            .putLong(KEY_SNOOZED_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
            .apply()
    }
}
