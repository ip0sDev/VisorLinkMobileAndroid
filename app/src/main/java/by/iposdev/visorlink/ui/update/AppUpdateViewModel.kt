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

sealed class UpdateState {
    object Loading : UpdateState()
    object None : UpdateState()
    data class Recommended(val url: String, val changelog: ChangelogInfo) : UpdateState()
    data class Required(val url: String, val changelog: ChangelogInfo) : UpdateState()
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

    init { checkForUpdates() }

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
                        "min_version_code"    to 1L,
                        "latest_version_code" to 1L,
                        "update_apk_url"      to "",
                        // JSON-массив строк: ["• Fixed crash", "• New dark theme"]
                        // Если пустая строка — чейнджлог не показываем
                        "changelog"           to ""
                    )
                ).await()

                val activated = rc.fetchAndActivate().await()
                Log.d("AppUpdate", "fetched, activated=$activated")

                val minVersion    = rc.getLong("min_version_code").toInt()
                val latestVersion = rc.getLong("latest_version_code").toInt()
                val url           = rc.getString("update_apk_url")
                val changelogRaw  = rc.getString("changelog")
                val current       = BuildConfig.VERSION_CODE

                Log.d("AppUpdate",
                    "current=$current min=$minVersion latest=$latestVersion url=$url")

                val changelog = buildChangelog(
                    current = current,
                    latest = latestVersion,
                    raw = changelogRaw
                )

                _updateState.value = when {
                    current < minVersion && url.isNotBlank() ->
                        UpdateState.Required(url, changelog)
                    current < latestVersion && url.isNotBlank() ->
                        UpdateState.Recommended(url, changelog)
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

    // ── Changelog logic ───────────────────────────────────────────────────────

    /**
     * Если current == latest - 1  → парсим JSON и показываем список
     * Если current <  latest - 1  → пишем что нужно смотреть канал
     * Иначе                       → не показываем ничего
     */
    private fun buildChangelog(current: Int, latest: Int, raw: String): ChangelogInfo {
        if (latest <= current) return ChangelogInfo()

        val gap = latest - current
        return if (gap == 1) {
            // Одна версия — парсим чейнджлог из Remote Config
            val entries = parseChangelog(raw)
            ChangelogInfo(entries = entries.ifEmpty { null })
        } else {
            // Несколько версий — отправляем в канал
            ChangelogInfo(tooOld = true)
        }
    }

    /**
     * Парсим JSON-массив строк из Remote Config.
     * Формат: ["• Fixed crash on startup", "• Added voice messages"]
     * Fallback: просто сплит по \n если не JSON.
     */
    private fun parseChangelog(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                // JSON array
                val org = org.json.JSONArray(trimmed)
                (0 until org.length()).map { org.getString(it) }.filter { it.isNotBlank() }
            } else {
                // Plain text, split by newlines
                raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w("AppUpdate", "Failed to parse changelog: ${e.message}")
            emptyList()
        }
    }
}