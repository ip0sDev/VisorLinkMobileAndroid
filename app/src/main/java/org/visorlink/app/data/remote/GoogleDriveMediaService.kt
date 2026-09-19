package org.visorlink.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.visorlink.app.utils.GoogleDriveAuthManager
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GoogleDriveUploadResult(
    val fileId: String,
    val directUrl: String,
    val viewUrl: String,
    val previewUrl: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String
)

class GoogleDriveMediaService(
    private val authManager: GoogleDriveAuthManager
) {
    companion object {
        private const val TAG = "GoogleDriveMedia"
        private const val FOLDER_NAME = "VisorLink Media"
    }

    suspend fun isFolderValid(accessToken: String, folderId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://www.googleapis.com/drive/v3/files/$folderId?fields=id,trashed")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                !json.optBoolean("trashed", false)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to validate folder $folderId: ${e.message}")
            false
        }
    }

    suspend fun getOrCreateVisorLinkFolder(accessToken: String): String = withContext(Dispatchers.IO) {
        // 1. Проверяем кэшированный ID папки
        val cachedFolderId = authManager.getCachedFolderId()
        if (!cachedFolderId.isNullOrBlank() && isFolderValid(accessToken, cachedFolderId)) {
            return@withContext cachedFolderId
        }

        // 2. Ищем существующую папку по названию
        val query = "name = '$FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = URL("https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name)")
        val searchConn = (searchUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        if (searchConn.responseCode == 200) {
            val resp = JSONObject(searchConn.inputStream.bufferedReader().readText())
            val files = resp.optJSONArray("files")
            if (files != null && files.length() > 0) {
                val existingId = files.getJSONObject(0).getString("id")
                authManager.setCachedFolderId(existingId)
                return@withContext existingId
            }
        }

        // 3. Создаем новую папку в корне
        val createUrl = URL("https://www.googleapis.com/drive/v3/files")
        val createConn = (createUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        val createPayload = JSONObject().apply {
            put("name", FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }

        createConn.outputStream.use { it.write(createPayload.toString().toByteArray(Charsets.UTF_8)) }

        if (createConn.responseCode !in 200..299) {
            val err = try { createConn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            throw Exception("Failed to create Google Drive folder (${createConn.responseCode}): $err")
        }

        val createResp = JSONObject(createConn.inputStream.bufferedReader().readText())
        val newFolderId = createResp.getString("id")
        authManager.setCachedFolderId(newFolderId)
        newFolderId
    }

    suspend fun setFilePublicPermission(accessToken: String, fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val payload = JSONObject().apply {
                put("role", "reader")
                put("type", "anyone")
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                true
            } else {
                val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                Log.w(TAG, "Failed to set public permission on file $fileId ($code): $err")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting file public permission", e)
            false
        }
    }

    suspend fun uploadMediaFile(
        accessToken: String,
        folderId: String,
        file: File,
        mimeType: String,
        onProgress: ((Float) -> Unit)? = null
    ): GoogleDriveUploadResult = withContext(Dispatchers.IO) {
        val boundary = "boundary_vl_${System.currentTimeMillis()}"
        val crlf = "\r\n"
        val twoHyphens = "--"

        val metadataJson = JSONObject().apply {
            put("name", file.name)
            put("parents", org.json.JSONArray().put(folderId))
        }.toString()

        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 120000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }

        val metadataHeader = "$twoHyphens$boundary$crlf" +
                "Content-Type: application/json; charset=UTF-8$crlf$crlf" +
                metadataJson + crlf

        val fileHeader = "$twoHyphens$boundary$crlf" +
                "Content-Type: $mimeType$crlf$crlf"

        val endBoundary = "$crlf$twoHyphens$boundary$twoHyphens$crlf"

        val totalBytes = file.length()

        conn.outputStream.use { out ->
            out.write(metadataHeader.toByteArray(Charsets.UTF_8))
            out.write(fileHeader.toByteArray(Charsets.UTF_8))

            val buffer = ByteArray(16384)
            var bytesRead: Int
            var totalRead = 0L

            FileInputStream(file).use { input ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    val progress = if (totalBytes > 0) (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 1f
                    onProgress?.invoke(progress)
                }
            }

            out.write(endBoundary.toByteArray(Charsets.UTF_8))
            out.flush()
        }

        if (conn.responseCode !in 200..299) {
            val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            throw Exception("Google Drive upload failed (${conn.responseCode}): $err")
        }

        val respText = conn.inputStream.bufferedReader().readText()
        val respJson = JSONObject(respText)
        val fileId = respJson.getString("id")

        // Делаем файл доступным по ссылке (reader / anyone)
        setFilePublicPermission(accessToken, fileId)

        val directUrl = if (mimeType.startsWith("video/")) {
            "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        } else {
            "https://lh3.googleusercontent.com/d/$fileId"
        }
        val viewUrl = "https://drive.google.com/file/d/$fileId/view?usp=sharing"
        val previewUrl = "https://lh3.googleusercontent.com/d/$fileId=s400"

        GoogleDriveUploadResult(
            fileId = fileId,
            directUrl = directUrl,
            viewUrl = viewUrl,
            previewUrl = previewUrl,
            fileName = file.name,
            fileSize = totalBytes,
            mimeType = mimeType
        )
    }
}
