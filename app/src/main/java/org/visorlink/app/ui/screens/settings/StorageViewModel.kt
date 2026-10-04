package org.visorlink.app.ui.screens.settings

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.visorlink.app.data.repository.GoogleDriveConfigRepository
import org.visorlink.app.utils.GoogleDriveAuthManager

data class StorageUiState(
    val isConnected: Boolean = false,
    val accountEmail: String? = null,
    val folderId: String? = null,
    val isConnecting: Boolean = false,
    val isLoadingConfig: Boolean = false,
    val error: String? = null,
    val isYandexRelayAvailable: Boolean = false,
    val isYandexRelayEnabled: Boolean = true,
    val yandexRelayCustomToken: String? = null,
    val yandexRelayHasActiveToken: Boolean = false,
    val yandexClientId: String = "",
    val yandexOAuthUrl: String = ""
)

class StorageViewModel(
    private val googleDriveAuthManager: GoogleDriveAuthManager,
    private val googleDriveConfigRepository: GoogleDriveConfigRepository,
    private val yandexRelayConfigManager: org.visorlink.app.data.remote.yandex.YandexRelayConfigManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StorageUiState(
            isConnected = googleDriveAuthManager.isConnected(),
            accountEmail = googleDriveAuthManager.getConnectedEmail(),
            folderId = googleDriveAuthManager.getCachedFolderId(),
            isYandexRelayAvailable = yandexRelayConfigManager?.isFeatureAvailable() == true,
            isYandexRelayEnabled = yandexRelayConfigManager?.isRelayEnabled() == true,
            yandexRelayCustomToken = yandexRelayConfigManager?.getCustomToken(),
            yandexRelayHasActiveToken = !yandexRelayConfigManager?.getActiveToken().isNullOrBlank(),
            yandexClientId = yandexRelayConfigManager?.getClientId() ?: "",
            yandexOAuthUrl = yandexRelayConfigManager?.buildOAuthUrl() ?: ""
        )
    )
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            googleDriveAuthManager.isConnectedFlow.collect { connected ->
                _uiState.update {
                    it.copy(
                        isConnected = connected,
                        accountEmail = googleDriveAuthManager.getConnectedEmail(),
                        folderId = googleDriveAuthManager.getCachedFolderId()
                    )
                }
            }
        }
    }

    fun refresh() {
        _uiState.update {
            it.copy(
                isConnected = googleDriveAuthManager.isConnected(),
                accountEmail = googleDriveAuthManager.getConnectedEmail(),
                folderId = googleDriveAuthManager.getCachedFolderId(),
                isYandexRelayAvailable = yandexRelayConfigManager?.isFeatureAvailable() == true,
                isYandexRelayEnabled = yandexRelayConfigManager?.isRelayEnabled() == true,
                yandexRelayCustomToken = yandexRelayConfigManager?.getCustomToken(),
                yandexRelayHasActiveToken = !yandexRelayConfigManager?.getActiveToken().isNullOrBlank(),
                yandexClientId = yandexRelayConfigManager?.getClientId() ?: "",
                yandexOAuthUrl = yandexRelayConfigManager?.buildOAuthUrl() ?: ""
            )
        }
    }

    fun toggleYandexRelay(enabled: Boolean) {
        yandexRelayConfigManager?.setRelayEnabled(enabled)
        refresh()
    }

    fun setYandexCustomToken(token: String?) {
        yandexRelayConfigManager?.setCustomToken(token)
        refresh()
    }

    fun setYandexClientId(clientId: String?) {
        yandexRelayConfigManager?.setCustomClientId(clientId)
        refresh()
    }

    fun disconnectYandex() {
        yandexRelayConfigManager?.disconnect()
        refresh()
    }

    fun connectGoogleDrive(onResolutionRequired: (PendingIntent) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }
            try {
                val authResult = googleDriveAuthManager.authorize()
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent != null) {
                        onResolutionRequired(pendingIntent)
                    } else {
                        _uiState.update { it.copy(isConnecting = false, error = "Не удалось открыть окно авторизации Google") }
                    }
                } else {
                    val result = googleDriveAuthManager.handleAuthorizationResult(authResult)
                    if (result.isSuccess) {
                        _uiState.update {
                            it.copy(
                                isConnected = true,
                                accountEmail = googleDriveAuthManager.getConnectedEmail(),
                                isConnecting = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isConnecting = false,
                                error = result.exceptionOrNull()?.message ?: "Ошибка получения токена Google Drive"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isConnecting = false, error = "Ошибка авторизации Google: ${e.message}") }
            }
        }
    }

    fun handleAuthorizationResult(resultCode: Int, data: Intent?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }
            Log.d("StorageViewModel", "handleAuthorizationResult: resultCode=$resultCode, data=$data")
            if (resultCode != android.app.Activity.RESULT_OK) {
                try {
                    if (data != null) {
                        val authResult = googleDriveAuthManager.getAuthorizationResultFromIntent(data)
                        val result = googleDriveAuthManager.handleAuthorizationResult(authResult)
                        if (result.isSuccess) {
                            _uiState.update {
                                it.copy(
                                    isConnected = true,
                                    accountEmail = googleDriveAuthManager.getConnectedEmail(),
                                    isConnecting = false
                                )
                            }
                            return@launch
                        }
                    }
                } catch (e: com.google.android.gms.common.api.ApiException) {
                    val currentSha1 = googleDriveAuthManager.getAppSignatureSha1()
                    val msg = when (e.statusCode) {
                        8 ->
                            "Ошибка 8 (UNREGISTERED_ON_API_CONSOLE): SHA-1 установленного APK не зарегистрирован в Google Cloud Console.\nТекущий SHA-1: $currentSha1\nПакет: org.visorlink.app"
                        com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR ->
                            "Ошибка 10 (DEVELOPER_ERROR): в Google Cloud Console отсутствует клиент для org.visorlink.app.\nТекущий SHA-1: $currentSha1"
                        com.google.android.gms.common.api.CommonStatusCodes.CANCELED ->
                            "Авторизация отменена пользователем или отклонена (код 16)."
                        com.google.android.gms.common.api.CommonStatusCodes.SIGN_IN_REQUIRED ->
                            "Требуется повторный вход в аккаунт Google (код 4)."
                        com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR ->
                            "Ошибка сети при связи с сервисами Google (код 7)."
                        else ->
                            "Ошибка авторизации Google (код ${e.statusCode}): ${e.statusMessage ?: e.message}\nSHA-1: $currentSha1"
                    }
                    Log.e("StorageViewModel", "Authorization ApiException: statusCode=${e.statusCode}, message=${e.message}, currentSha1=$currentSha1", e)
                    _uiState.update { it.copy(isConnecting = false, error = msg) }
                    return@launch
                } catch (e: Exception) {
                    Log.e("StorageViewModel", "Error inspecting non-OK result: ${e.message}", e)
                }

                val cancelMsg = if (resultCode == android.app.Activity.RESULT_CANCELED) {
                    val currentSha1 = googleDriveAuthManager.getAppSignatureSha1()
                    "Авторизация отклонена Google (RESULT_CANCELED).\nТекущий SHA-1: $currentSha1\nПроверьте добавление этого отпечатка в Google Cloud Console."
                } else {
                    "Авторизация Google завершилась с кодом $resultCode"
                }
                Log.w("StorageViewModel", cancelMsg)
                _uiState.update { it.copy(isConnecting = false, error = cancelMsg) }
                return@launch
            }

            try {
                val authResult = googleDriveAuthManager.getAuthorizationResultFromIntent(data)
                val result = googleDriveAuthManager.handleAuthorizationResult(authResult)
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isConnected = true,
                            accountEmail = googleDriveAuthManager.getConnectedEmail(),
                            isConnecting = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            error = result.exceptionOrNull()?.message ?: "Ошибка получения токена Google Drive"
                        )
                    }
                }
            } catch (e: com.google.android.gms.common.api.ApiException) {
                val currentSha1 = googleDriveAuthManager.getAppSignatureSha1()
                val msg = when (e.statusCode) {
                    8 ->
                        "Ошибка 8 (UNREGISTERED_ON_API_CONSOLE): SHA-1 установленного APK не зарегистрирован в Google Cloud Console.\nТекущий SHA-1: $currentSha1\nПакет: org.visorlink.app"
                    com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR ->
                        "Ошибка 10 (DEVELOPER_ERROR): в Google Cloud Console отсутствует клиент для org.visorlink.app.\nТекущий SHA-1: $currentSha1"
                    else ->
                        "Ошибка авторизации Google (код ${e.statusCode}): ${e.statusMessage ?: e.message}\nSHA-1: $currentSha1"
                }
                Log.e("StorageViewModel", "Authorization failed with ApiException: ${e.statusCode}, currentSha1=$currentSha1", e)
                _uiState.update { it.copy(isConnecting = false, error = msg) }
            } catch (e: Exception) {
                Log.e("StorageViewModel", "Authorization failed: ${e.message}", e)
                _uiState.update { it.copy(isConnecting = false, error = "Ошибка авторизации Google: ${e.message}") }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            googleDriveAuthManager.disconnect()
            _uiState.update {
                it.copy(
                    isConnected = false,
                    accountEmail = null,
                    folderId = null
                )
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}