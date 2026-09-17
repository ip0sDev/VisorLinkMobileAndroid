package org.visorlink.app.data.model.bugreport

import com.google.gson.annotations.SerializedName

/**
 * Запрос на отправку баг-репорта
 */
data class BugReportRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("stepsToReproduce") val stepsToReproduce: String? = null,
    @SerializedName("expectedBehavior") val expectedBehavior: String? = null,
    @SerializedName("actualBehavior") val actualBehavior: String? = null,
    @SerializedName("category") val category: String = "other", // ui, chat, calls, media, auth, other
    @SerializedName("severity") val severity: String = "medium", // low, medium, high, critical
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("appVersion") val appVersion: String,
    @SerializedName("buildNumber") val buildNumber: Long,
    @SerializedName("deviceInfo") val deviceInfo: DeviceInfo,
    @SerializedName("screenshots") val screenshots: List<ScreenshotAttachment> = emptyList(),
    @SerializedName("logs") val logs: String? = null
)

/**
 * Системные метаданные устройства
 */
data class DeviceInfo(
    @SerializedName("manufacturer") val manufacturer: String,
    @SerializedName("model") val model: String,
    @SerializedName("osVersion") val osVersion: String,
    @SerializedName("sdkInt") val sdkInt: Int,
    @SerializedName("screenResolution") val screenResolution: String,
    @SerializedName("densityDpi") val densityDpi: Int,
    @SerializedName("language") val language: String,
    @SerializedName("timeZone") val timeZone: String,
    @SerializedName("freeMemoryMb") val freeMemoryMb: Long? = null
)

/**
 * Прикрепленный скриншот с CDN
 */
data class ScreenshotAttachment(
    @SerializedName("url") val url: String,
    @SerializedName("cdnMediaId") val cdnMediaId: String,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("size") val size: Long
)

/**
 * Ответ сервера
 */
data class BugReportResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("reportId") val reportId: String,
    @SerializedName("number") val number: Long
)
