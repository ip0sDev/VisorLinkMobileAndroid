package by.iposdev.visorlink.ui.update

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.BuildConfig
import by.iposdev.visorlink.utils.UpdateApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

enum class UpdateChannel(val id: String, val title: String, val fileName: String) {
    RELEASE("release", "Release", "app-release.apk"),
    BETA("beta", "Beta", "app-beta.apk"),
    NIGHTLY("nightly", "Nightly", "app-nightly.apk"),
    CANARY("canary", "Canary (Requires Admin)", "app-canary.apk")
}

sealed class UpdateState {
    object Loading : UpdateState()
    object None : UpdateState()
    data class Recommended(
        val url: String,
        val changelog: ChangelogInfo,
        val versionName: String,
        val expectedSha256: String
    ) : UpdateState()

    data class Required(
        val url: String,
        val changelog: ChangelogInfo,
        val versionName: String,
        val expectedSha256: String
    ) : UpdateState()
}

data class ChangelogInfo(
    val entries: List<String>? = null,
    val tooOld: Boolean = false,
    val channelTag: String = "@VisorLink"
)

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("visorlink_update_prefs", Context.MODE_PRIVATE)

    private val installId: String
        get() {
            var id = prefs.getString("install_id", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString("install_id", id).apply()
            }
            return id
        }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Loading)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _currentChannel = MutableStateFlow(
        UpdateChannel.entries.find {
            it.name == prefs.getString("selected_channel", UpdateChannel.RELEASE.name)
        } ?: UpdateChannel.RELEASE
    )
    val currentChannel: StateFlow<UpdateChannel> = _currentChannel.asStateFlow()

    init {
        // При старте приложения регистрируем устройство и проверяем обновления
        viewModelScope.launch {
            UpdateApiClient.register(installId, _currentChannel.value.id)
            checkForUpdates(isManual = false)

            // Запускаем периодическую проверку каждые 2 часа, пока ViewModel жива
            while(true) {
                kotlinx.coroutines.delay((2 * 60 * 1000L).milliseconds)
                checkForUpdates(isManual = false)
            }
        }
    }

    fun setChannel(channel: UpdateChannel, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = UpdateApiClient.setChannel(installId, channel.id)
            if (success) {
                _currentChannel.value = channel
                prefs.edit().putString("selected_channel", channel.name).apply()
                // После смены канала сразу принудительно проверяем обновления
                checkForUpdates(isManual = true)
            }
            onResult(success)
        }
    }

    fun checkForUpdates(isManual: Boolean = false, onResult: ((Boolean) -> Unit)? = null) {
        if (isManual) {
            _updateState.value = UpdateState.Loading
        }
        viewModelScope.launch {
            try {
                val packageName = getApplication<Application>().packageName
                val versionCode = BuildConfig.VERSION_CODE
                val updateInfo = UpdateApiClient.checkUpdate(installId, packageName, versionCode)

                if (updateInfo != null && updateInfo.isAvailable) {
                    val entries = updateInfo.changelog.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                    val changelog = ChangelogInfo(entries = entries.ifEmpty { null })

                    val url = if (updateInfo.downloadUrl.startsWith("http")) {
                        updateInfo.downloadUrl
                    } else {
                        "https://update-android.visorlink.org" + updateInfo.downloadUrl
                    }

                    _updateState.value = UpdateState.Recommended(
                        url = url,
                        changelog = changelog,
                        versionName = updateInfo.versionName,
                        expectedSha256 = updateInfo.sha256
                    )
                    onResult?.invoke(true)
                } else {
                    if (isManual || _updateState.value is UpdateState.Loading) {
                        _updateState.value = UpdateState.None
                    }
                    onResult?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e("AppUpdate", "Check update failed", e)
                if (isManual || _updateState.value is UpdateState.Loading) {
                    _updateState.value = UpdateState.None
                }
                onResult?.invoke(false)
            }
        }
    }

    fun dismissRecommendedUpdate() {
        _updateState.value = UpdateState.None
    }
}