package by.iposdev.visorlink.ui.update

import androidx.lifecycle.ViewModel
import by.iposdev.visorlink.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class UpdateState {
    object Loading : UpdateState()
    object None : UpdateState()
    data class Recommended(val url: String) : UpdateState()
    data class Required(val url: String) : UpdateState()
}

class AppUpdateViewModel : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Loading)
    val updateState = _updateState.asStateFlow()

    init {
        checkForUpdates()
    }

    private fun checkForUpdates() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()

        // Настройки кэша (0 сек для тестирования, чтобы изменения прилетали сразу)
        // В релизе поставь 3600 (1 час) или больше.
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Значения по умолчанию, если нет интернета
        remoteConfig.setDefaultsAsync(mapOf(
            "min_version_code" to 1L,
            "latest_version_code" to 1L,
            "update_apk_url" to ""
        ))

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            val minVersion = remoteConfig.getLong("min_version_code").toInt()
            val latestVersion = remoteConfig.getLong("latest_version_code").toInt()
            val url = remoteConfig.getString("update_apk_url")

            val currentVersion = BuildConfig.VERSION_CODE

            when {
                currentVersion < minVersion && url.isNotBlank() -> {
                    _updateState.value = UpdateState.Required(url)
                }
                currentVersion < latestVersion && url.isNotBlank() -> {
                    _updateState.value = UpdateState.Recommended(url)
                }
                else -> {
                    _updateState.value = UpdateState.None
                }
            }
        }
    }

    fun dismissRecommendedUpdate() {
        _updateState.value = UpdateState.None
    }
}