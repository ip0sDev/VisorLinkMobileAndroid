package by.iposdev.visorlink.ui.update

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class UpdateChannel(val title: String, val fileName: String) {
    BETA("Beta", "app-beta.apk"),
    NIGHTLY("Nightly", "app-nightly.apk")
}

sealed class UpdateState {
    object Loading : UpdateState()
    object None : UpdateState()
    data class Recommended(
        val url: String,
        val changelog: ChangelogInfo,
        val versionName: String
    ) : UpdateState()

    data class Required(
        val url: String,
        val changelog: ChangelogInfo,
        val versionName: String
    ) : UpdateState()
}

/**
 * @param entries  список строк чейнджлога (null = не показывать)
 * @param tooOld   true = версия старше чем N-1, показываем ссылку на канал
 */
data class ChangelogInfo(
    val entries: List<String>? = null,
    val tooOld: Boolean = false,
    val channelTag: String = "@VisorLink"
)

class AppUpdateViewModel : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Loading)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    // TODO: В будущем можно сохранять/загружать выбор из DataStore
    private val _currentChannel = MutableStateFlow(UpdateChannel.BETA)
    val currentChannel: StateFlow<UpdateChannel> = _currentChannel.asStateFlow()

    init { checkForUpdates() }

    fun setChannel(channel: UpdateChannel) {
        _currentChannel.value = channel
        checkForUpdates()
    }

    fun checkForUpdates() {
        _updateState.value = UpdateState.Loading
        viewModelScope.launch {
            try {
                val rc = Firebase.remoteConfig

                rc.setConfigSettingsAsync(
                    FirebaseRemoteConfigSettings.Builder()
                        .setMinimumFetchIntervalInSeconds(
                            if (BuildConfig.DEBUG) 0L else 3600L
                        )
                        .build()
                ).await()

                rc.setDefaultsAsync(
                    mapOf(
                        // Beta ключи
                        "min_version_code"    to 1L,
                        "latest_version_code" to 1L,
                        "update_apk_url"      to "",
                        "changelog"           to "",

                        // Nightly ключи
                        "nightly_min_version_code"    to 1L,
                        "nightly_latest_version_code" to 1L,
                        "nightly_update_apk_url"      to "",
                        "nightly_changelog"           to ""
                    )
                ).await()

                val activated = rc.fetchAndActivate().await()
                Log.d("AppUpdate", "fetched, activated=$activated")

                val channel = _currentChannel.value
                val isNightly = channel == UpdateChannel.NIGHTLY
                val prefix = if (isNightly) "nightly_" else ""

                val minVersion    = rc.getLong("${prefix}min_version_code").toInt()
                val latestVersion = rc.getLong("${prefix}latest_version_code").toInt()
                val url           = rc.getString("${prefix}update_apk_url")
                val changelogRaw  = rc.getString("${prefix}changelog")
                val current       = BuildConfig.VERSION_CODE

                Log.d("AppUpdate", "current=$current min=$minVersion latest=$latestVersion url=$url channel=${channel.name}")

                val versionName = if (isNightly) "Nightly v$latestVersion" else "v$latestVersion"
                var changelog = buildChangelog(current = current, latest = latestVersion, raw = changelogRaw)

                // Добавляем префикс [Nightly] к строкам, если нужно
                if (isNightly && changelog.entries != null) {
                    changelog = changelog.copy(
                        entries = changelog.entries.map { entry ->
                            if (!entry.contains("Nightly", ignoreCase = true)) "[Nightly] $entry" else entry
                        }
                    )
                }

                _updateState.value = when {
                    current < minVersion && url.isNotBlank() ->
                        UpdateState.Required(url, changelog, versionName)
                    current < latestVersion && url.isNotBlank() ->
                        UpdateState.Recommended(url, changelog, versionName)
                    else ->
                        UpdateState.None
                }
            } catch (e: Exception) {
                Log.e("AppUpdate", "fetch failed: ${e.message}")
                _updateState.value = UpdateState.None
            }
        }
    }

    fun dismissRecommendedUpdate() {
        _updateState.value = UpdateState.None
    }

    private fun buildChangelog(current: Int, latest: Int, raw: String): ChangelogInfo {
        if (latest <= current) return ChangelogInfo()

        val gap = latest - current
        return if (gap == 1) {
            val entries = parseChangelog(raw)
            ChangelogInfo(entries = entries.ifEmpty { null })
        } else {
            ChangelogInfo(tooOld = true)
        }
    }

    private fun parseChangelog(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                val org = org.json.JSONArray(trimmed)
                (0 until org.length()).map { org.getString(it) }.filter { it.isNotBlank() }
            } else {
                raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w("AppUpdate", "Failed to parse changelog: ${e.message}")
            emptyList()
        }
    }
}