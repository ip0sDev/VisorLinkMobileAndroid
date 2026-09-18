package org.visorlink.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.data.model.bugreport.BugReportRequest
import org.visorlink.app.data.model.bugreport.BugReportResponse
import org.visorlink.app.data.model.bugreport.ScreenshotAttachment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import org.visorlink.app.data.repository.BugReportRepository
import org.visorlink.app.utils.DeviceInfoProvider
import org.visorlink.app.utils.DiagnosticLogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class BugReportUiState(
    val title: String = "",
    val description: String = "",
    val stepsToReproduce: String = "",
    val category: String = "other",
    val severity: String = "medium",
    val attachLogs: Boolean = true,
    val screenshots: List<ScreenshotAttachment> = emptyList(),
    val isUploadingScreenshot: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successReport: BugReportResponse? = null
)

class BugReportViewModel(
    private val bugReportRepository: BugReportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BugReportUiState())
    val uiState: StateFlow<BugReportUiState> = _uiState.asStateFlow()

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title, error = null) }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(description = description, error = null) }
    }

    fun onStepsChange(steps: String) {
        _uiState.update { it.copy(stepsToReproduce = steps) }
    }

    fun onCategoryChange(category: String) {
        _uiState.update { it.copy(category = category) }
    }

    fun onSeverityChange(severity: String) {
        _uiState.update { it.copy(severity = severity) }
    }

    fun onAttachLogsChange(attach: Boolean) {
        _uiState.update { it.copy(attachLogs = attach) }
    }

    fun uploadScreenshot(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingScreenshot = true, error = null) }
            try {
                val user = FirebaseAuth.getInstance().currentUser
                    ?: throw IllegalStateException("Пользователь не авторизован")
                val contentResolver = context.contentResolver
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw IllegalArgumentException("Не удалось прочитать файл скриншота")

                val fileName = "screenshot_${System.currentTimeMillis()}.jpg"
                val storageRef = FirebaseStorage.getInstance().reference.child("bugreports/${user.uid}/$fileName")
                val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
                storageRef.putBytes(bytes, metadata).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val attachment = ScreenshotAttachment(
                    url = downloadUrl,
                    fileName = fileName,
                    size = bytes.size.toLong()
                )
                _uiState.update { state ->
                    state.copy(
                        screenshots = state.screenshots + attachment,
                        isUploadingScreenshot = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploadingScreenshot = false,
                        error = "Ошибка загрузки скриншота: ${e.message ?: "неизвестная ошибка"}"
                    )
                }
            }
        }
    }

    fun removeScreenshot(attachment: ScreenshotAttachment) {
        _uiState.update { state ->
            state.copy(screenshots = state.screenshots.filter { it.url != attachment.url })
        }
    }

    fun submit(context: Context, onSuccess: (BugReportResponse) -> Unit = {}) {
        val state = _uiState.value
        val titleTrimmed = state.title.trim()
        val descTrimmed = state.description.trim()

        if (titleTrimmed.length < 3) {
            _uiState.update { it.copy(error = "Заголовок должен содержать минимум 3 символа") }
            return
        }
        if (descTrimmed.length < 5) {
            _uiState.update { it.copy(error = "Описание должно содержать минимум 5 символов") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            val (vName, vCode) = DeviceInfoProvider.getAppVersion(context)
            val deviceInfo = DeviceInfoProvider.getDeviceInfo(context)
            val logs = if (state.attachLogs) {
                DiagnosticLogBuffer.getFormattedLogs().takeIf { it.isNotBlank() }
            } else null

            val request = BugReportRequest(
                title = titleTrimmed,
                description = descTrimmed,
                stepsToReproduce = state.stepsToReproduce.trim().takeIf { it.isNotBlank() },
                category = state.category,
                severity = state.severity,
                platform = "android",
                appVersion = vName,
                buildNumber = vCode,
                deviceInfo = deviceInfo,
                screenshots = state.screenshots,
                logs = logs
            )

            val result = bugReportRepository.sendReport(request)
            result.onSuccess { response ->
                _uiState.update { it.copy(isSubmitting = false, successReport = response, error = null) }
                onSuccess(response)
            }.onFailure { ex ->
                _uiState.update { it.copy(isSubmitting = false, error = ex.message ?: "Ошибка отправки баг-репорта") }
            }
        }
    }

    fun reset() {
        _uiState.value = BugReportUiState()
    }
}
