package by.iposdev.visorlink.data.remote

import android.util.Log
import by.iposdev.visorlink.utils.GoogleDriveAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okio.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder

data class GoogleDriveUploadResult(
    val fileId: String,
    val directUrl: String,
    val driveUrl: String,
    val thumbnailUrl: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String
)

/**
 * REST API сервис Google Drive v3 (Секция 3 ANDROID_GOOGLE_DRIVE_AND_STORAGE_SPEC).
 */
class GoogleDriveService(
    private val authManager: GoogleDriveAuthManager,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val TAG = "GoogleDriveService"
        private const val FOLDER_NAME = "VisorLink Media"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
    }

    /**
     * Проверяет, валидна ли папка и не удалена ли она в корзину.
     */
    private suspend fun isFolderValid(accessToken: String, folderId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$DRIVE_API_BASE/files/$folderId?fields=id,trashed")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body?.string() ?: return@withContext false
                val json = JSONObject(body)
                !json.optBoolean("trashed", false)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Поиск папки по имени в корневом каталоге.
     */
    private suspend fun queryDriveFolder(accessToken: String, query: String): String? = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("$DRIVE_API_BASE/files?q=$encodedQuery&fields=files(id,name,trashed)&spaces=drive")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    files.getJSONObject(0).getString("id")
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query drive folder", e)
            null
        }
    }

    /**
     * Создание новой папки «VisorLink Media».
     */
    private suspend fun createDriveFolder(accessToken: String, name: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("name", name)
            put("mimeType", FOLDER_MIME)
        }

        val body = RequestBody.create("application/json; charset=UTF-8".toMediaType(), payload.toString())
        val request = Request.Builder()
            .url("$DRIVE_API_BASE/files")
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string()
                throw IOException("Failed to create Google Drive folder (${response.code}): $err")
            }
            val respBody = response.body?.string() ?: throw IOException("Empty folder creation response")
            JSONObject(respBody).getString("id")
        }
    }

    /**
     * Получает или создает папку «VisorLink Media» (Секция 3.1 спецификации).
     */
    suspend fun getOrCreateVisorLinkFolder(accessToken: String): String = withContext(Dispatchers.IO) {
        // 1. Проверяем кэшированный ID папки
        val cachedFolderId = authManager.getCachedFolderId()
        if (cachedFolderId != null && isFolderValid(accessToken, cachedFolderId)) {
            return@withContext cachedFolderId
        }

        // 2. Ищем существующую папку по названию
        val searchQuery = "name = '$FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false"
        val existingId = queryDriveFolder(accessToken, searchQuery)
        if (existingId != null) {
            authManager.saveCachedFolderId(existingId)
            return@withContext existingId
        }

        // 3. Создаем новую папку в корне
        val newFolderId = createDriveFolder(accessToken, FOLDER_NAME)
        authManager.saveCachedFolderId(newFolderId)
        newFolderId
    }

    /**
     * Загрузка файла через Multipart Upload Google Drive API v3 (Секция 3.2 спецификации).
     */
    suspend fun uploadMedia(
        accessToken: String,
        folderId: String,
        file: File,
        mimeType: String,
        onProgress: ((Float) -> Unit)? = null
    ): GoogleDriveUploadResult = withContext(Dispatchers.IO) {
        val boundary = "boundary_vl_${System.currentTimeMillis()}"

        // Метаданные файла
        val metadataJson = JSONObject().apply {
            put("name", file.name)
            put("parents", org.json.JSONArray().put(folderId))
        }

        // Построение multipart/related вручную для точного соответствия Google Drive API v3
        val metadataPart = "--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                metadataJson.toString() + "\r\n"

        val mediaHeader = "--$boundary\r\n" +
                "Content-Type: $mimeType\r\n\r\n"

        val closingBoundary = "\r\n--$boundary--\r\n"

        val totalLength = metadataPart.toByteArray(Charsets.UTF_8).size.toLong() +
                mediaHeader.toByteArray(Charsets.UTF_8).size.toLong() +
                file.length() +
                closingBoundary.toByteArray(Charsets.UTF_8).size.toLong()

        val customRequestBody = object : RequestBody() {
            override fun contentType(): MediaType = "multipart/related; boundary=$boundary".toMediaType()

            override fun contentLength(): Long = totalLength

            override fun writeTo(sink: BufferedSink) {
                var bytesWritten = 0L
                val countingSink = object : ForwardingSink(sink) {
                    override fun write(source: Buffer, byteCount: Long) {
                        super.write(source, byteCount)
                        bytesWritten += byteCount
                        if (totalLength > 0 && onProgress != null) {
                            val progress = (bytesWritten.toFloat() / totalLength).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                }
                val bufferedCountingSink = countingSink.buffer()

                bufferedCountingSink.writeUtf8(metadataPart)
                bufferedCountingSink.writeUtf8(mediaHeader)
                file.source().use { fileSource ->
                    bufferedCountingSink.writeAll(fileSource)
                }
                bufferedCountingSink.writeUtf8(closingBoundary)
                bufferedCountingSink.flush()
            }
        }

        val uploadRequest = Request.Builder()
            .url("$DRIVE_UPLOAD_BASE/files?uploadType=multipart")
            .header("Authorization", "Bearer $accessToken")
            .post(customRequestBody)
            .build()

        val fileId = okHttpClient.newCall(uploadRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string()
                throw IOException("Google Drive upload failed (${response.code}): $err")
            }
            val respBody = response.body?.string() ?: throw IOException("Empty Google Drive upload response")
            JSONObject(respBody).getString("id")
        }

        // 2. Публикация прав доступа (role: reader, type: anyone)
        publishFile(accessToken, fileId)

        // 3. Формирование URLs по спецификации (Секция 3.2 п.3)
        val directUrl = "https://lh3.googleusercontent.com/d/$fileId"
        val driveUrl = "https://drive.google.com/file/d/$fileId/view?usp=sharing"
        val thumbnailUrl = "https://lh3.googleusercontent.com/d/$fileId=s400"

        GoogleDriveUploadResult(
            fileId = fileId,
            directUrl = directUrl,
            driveUrl = driveUrl,
            thumbnailUrl = thumbnailUrl,
            fileName = file.name,
            fileSize = file.length(),
            mimeType = mimeType
        )
    }

    /**
     * Публикация прав доступа на чтение для любого пользователя по ссылке (anyone / reader).
     */
    suspend fun publishFile(accessToken: String, fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val permissionJson = JSONObject().apply {
                put("role", "reader")
                put("type", "anyone")
            }
            val body = RequestBody.create("application/json; charset=UTF-8".toMediaType(), permissionJson.toString())
            val request = Request.Builder()
                .url("$DRIVE_API_BASE/files/$fileId/permissions")
                .header("Authorization", "Bearer $accessToken")
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish Google Drive file $fileId", e)
            false
        }
    }
}
