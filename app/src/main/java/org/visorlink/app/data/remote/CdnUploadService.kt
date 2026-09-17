package org.visorlink.app.data.remote

import android.content.Context
import android.net.Uri
import org.visorlink.app.data.model.bugreport.ScreenshotAttachment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/**
 * Сервис загрузки скриншотов на CDN VisorLink с публичным доступом.
 */
class CdnUploadService(private val okHttpClient: OkHttpClient) {

    suspend fun uploadScreenshot(file: File): ScreenshotAttachment = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Пользователь не авторизован")
        val token = user.getIdToken(false).await().token
            ?: throw IllegalStateException("Не удалось получить токен авторизации")

        val fileReqBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileReqBody)
            .addFormDataPart("zone", "public")
            .build()

        val request = Request.Builder()
            .url("https://api.visorlink.org/upload")
            .header("Authorization", "Bearer $token")
            .post(multipartBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("CDN Upload Failed with HTTP ${response.code}: ${response.message}")
        }

        val responseBody = response.body?.string() ?: throw RuntimeException("Empty CDN response")
        val json = JSONObject(responseBody)

        val mediaId = json.getString("media_id")
        val fileUrl = json.getString("url")

        ScreenshotAttachment(
            url = fileUrl,
            cdnMediaId = mediaId,
            fileName = file.name,
            size = file.length()
        )
    }

    suspend fun uploadScreenshotUri(context: Context, uri: Uri): ScreenshotAttachment = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Пользователь не авторизован")
        val token = user.getIdToken(false).await().token
            ?: throw IllegalStateException("Не удалось получить токен авторизации")

        val contentResolver = context.contentResolver
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Не удалось прочитать файл скриншота")

        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val fileName = "screenshot_${System.currentTimeMillis()}.jpg"

        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, body)
            .addFormDataPart("zone", "public")
            .build()

        val request = Request.Builder()
            .url("https://api.visorlink.org/upload")
            .header("Authorization", "Bearer $token")
            .post(multipartBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("CDN Upload Failed with HTTP ${response.code}: ${response.message}")
        }

        val responseBody = response.body?.string() ?: throw RuntimeException("Empty CDN response")
        val json = JSONObject(responseBody)

        val mediaId = json.getString("media_id")
        val fileUrl = json.getString("url")

        ScreenshotAttachment(
            url = fileUrl,
            cdnMediaId = mediaId,
            fileName = fileName,
            size = bytes.size.toLong()
        )
    }
}
