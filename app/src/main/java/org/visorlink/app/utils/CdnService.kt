// utils/CdnService.kt
package org.visorlink.app.utils

import android.util.Log
import org.visorlink.app.data.model.AlbumImage
import org.visorlink.app.data.model.ReplyData
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class CdnUploadResult(
    val mediaId: String,
    val duration: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val thumbId: String? = null,
    val thumbUrl: String? = null,
    val size: Long? = null
)

object CdnService {
    const val BASE_URL = "https://api.visorlink.org"

    suspend fun getStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: throw Exception("No token")
            val connection = URL("$BASE_URL/user_stats").openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Authorization", "Bearer $token")

            if (connection.responseCode == 200) {
                val json = JSONObject(connection.inputStream.bufferedReader().readText())
                mapOf(
                    "status" to "online",
                    "used_bytes" to json.optLong("used_bytes", 0),
                    "quota_bytes" to json.optLong("quota_bytes", 2147483648),
                    "files_count" to json.optInt("files_count", 0),
                    "is_pro" to json.optBoolean("is_pro", false)
                )
            } else throw Exception("CDN offline")
        } catch (e: Exception) {
            mapOf("status" to "offline", "used_bytes" to 0L, "quota_bytes" to 2147483648L, "files_count" to 0)
        }
    }

    suspend fun listFiles(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return@withContext emptyList()
            val connection = URL("$BASE_URL/user_media").openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Authorization", "Bearer $token")

            if (connection.responseCode == 200) {
                val text = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(text)
                val array = json.getJSONArray("media")
                List(array.length()) { i ->
                    val obj = array.getJSONObject(i)
                    mapOf(
                        "media_id" to obj.getString("id"),
                        "original_name" to obj.optString("original_name", "unknown"),
                        "mime_type" to obj.optString("mime_type", "application/octet-stream"),
                        "size" to obj.optLong("size", 0L),
                        "zone" to obj.optString("zone", "public"),
                        "is_read" to obj.optBoolean("is_read", false),
                        "created_at" to obj.optLong("uploaded_at", 0L)
                    )
                }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun deleteFile(mediaId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return@withContext false
            val connection = URL("$BASE_URL/f/$mediaId").openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.responseCode == 200
        } catch (e: Exception) { false }
    }

    suspend fun getFileUrl(cdnMediaId: String): String {
        val token = try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        } catch (_: Exception) { null }
        return if (!token.isNullOrEmpty()) {
            "$BASE_URL/f/$cdnMediaId?token=$token"
        } else {
            "$BASE_URL/p/$cdnMediaId"
        }
    }

    suspend fun authenticateUrl(url: String): String {
        val normalizedUrl = if (url.startsWith("/")) "$BASE_URL$url" else url
        if (!normalizedUrl.contains("/f/") || normalizedUrl.contains("token=")) return normalizedUrl
        val token = try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        } catch (_: Exception) { null }
        return if (!token.isNullOrEmpty()) {
            if (normalizedUrl.contains("?")) "$normalizedUrl&token=$token" else "$normalizedUrl?token=$token"
        } else {
            normalizedUrl
        }
    }

    suspend fun abortChunkedUpload(uploadId: String) = withContext(Dispatchers.IO) {
        try {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return@withContext
            val connection = URL("$BASE_URL/upload/chunked/$uploadId").openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.responseCode
        } catch (e: Exception) {
            Log.w("CdnService", "Failed to abort chunked upload $uploadId", e)
        }
    }

    /**
     * Чанковая загрузка файла с поддержкой отмены и извлечения видео-превью.
     */
    suspend fun uploadFileChunked(
        file: File,
        mimeType: String,
        isVault: Boolean = false,
        chunkSize: Int = 5 * 1024 * 1024, // 5 MB
        onProgress: ((Float) -> Unit)? = null
    ): CdnUploadResult = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: throw Exception("No auth token")
        val zone = if (isVault) "vault" else "public"
        val fileSize = file.length()
        val totalChunks = if (fileSize == 0L) 1 else ((fileSize + chunkSize - 1) / chunkSize).toInt()

        // 1. Инициализация сессии загрузки
        val initUrl = URL("$BASE_URL/upload/chunked/init")
        val initConn = (initUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        val initPayload = JSONObject().apply {
            put("file_name", file.name)
            put("file_size", fileSize)
            put("mime_type", mimeType)
            put("total_chunks", totalChunks)
            put("chunk_size", chunkSize)
            put("zone", zone)
        }

        initConn.outputStream.use { it.write(initPayload.toString().toByteArray()) }

        if (initConn.responseCode !in 200..299) {
            val err = try { initConn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            throw Exception("Failed to init chunked upload (${initConn.responseCode}): $err")
        }

        val initResponse = JSONObject(initConn.inputStream.bufferedReader().readText())
        val uploadId = initResponse.getString("upload_id")
        var isCompleted = false

        try {
            var bytesUploaded = 0L
            val buffer = ByteArray(chunkSize)

            java.io.RandomAccessFile(file, "r").use { raf ->
                for (chunkIndex in 0 until totalChunks) {
                    kotlinx.coroutines.yield()

                    val offset = chunkIndex.toLong() * chunkSize
                    raf.seek(offset)
                    val bytesToRead = minOf(chunkSize.toLong(), fileSize - offset).toInt()
                    raf.readFully(buffer, 0, bytesToRead)

                    val chunkUrl = URL("$BASE_URL/upload/chunked/$uploadId")
                    val chunkConn = (chunkUrl.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 30000
                        readTimeout = 60000
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("Content-Type", "application/octet-stream")
                        setRequestProperty("X-Chunk-Index", chunkIndex.toString())
                        setRequestProperty("X-Chunk-Offset", offset.toString())
                        setFixedLengthStreamingMode(bytesToRead)
                    }

                    chunkConn.outputStream.use { out ->
                        out.write(buffer, 0, bytesToRead)
                        out.flush()
                    }

                    if (chunkConn.responseCode !in 200..299) {
                        val err = try { chunkConn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                        throw Exception("Chunk $chunkIndex upload failed (${chunkConn.responseCode}): $err")
                    }

                    val respText = chunkConn.inputStream.bufferedReader().readText()
                    val chunkResp = JSONObject(respText)
                    bytesUploaded += bytesToRead
                    val progress = if (fileSize > 0) (bytesUploaded.toFloat() / fileSize).coerceIn(0f, 1f) else 1f
                    onProgress?.invoke(progress)

                    if (chunkResp.optBoolean("is_completed", false) || chunkIndex == totalChunks - 1) {
                        isCompleted = true
                        return@withContext CdnUploadResult(
                            mediaId = chunkResp.optString("media_id").ifEmpty { uploadId },
                            duration = chunkResp.optInt("duration").takeIf { it > 0 },
                            width = chunkResp.optInt("width").takeIf { it > 0 },
                            height = chunkResp.optInt("height").takeIf { it > 0 },
                            thumbId = chunkResp.optString("thumb_id").takeIf { it.isNotBlank() },
                            thumbUrl = chunkResp.optString("thumb_url").takeIf { it.isNotBlank() },
                            size = chunkResp.optLong("size", bytesUploaded)
                        )
                    }
                }
            }

            throw Exception("Chunked upload finished without completion flag")
        } catch (e: Exception) {
            if (!isCompleted) {
                // Если корутина отменена или произошла ошибка — удаляем временный файл на сервере
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    abortChunkedUpload(uploadId)
                }
            }
            throw e
        }
    }

    /**
     * Прямая отправка альбома в чат через CDN/Backend без использования Cloud Functions.
     */
    suspend fun sendAlbumViaCdn(
        chatId: String,
        images: List<AlbumImage>,
        caption: String?,
        replyTo: ReplyData?
    ): Pair<String, Long> = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: throw Exception("No auth token")

        val url = URL("$BASE_URL/chats/$chatId/messages/album")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 20000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }

        val payload = JSONObject().apply {
            val imgArray = JSONArray()
            images.forEach { img ->
                val imgObj = JSONObject().apply {
                    put("cdnMediaId", img.cdnMediaId)
                    put("fileName", img.fileName)
                    put("spoiler", img.spoiler)
                }
                imgArray.put(imgObj)
            }
            put("images", imgArray)
            if (!caption.isNullOrBlank()) put("caption", caption.trim())
            if (replyTo != null) {
                val rObj = JSONObject().apply {
                    put("id", replyTo.id)
                    put("type", replyTo.type)
                    put("text", replyTo.text)
                    put("url", replyTo.url)
                    put("senderUsername", replyTo.senderUsername)
                }
                put("replyTo", rObj)
            }
        }

        Log.d("CdnService", "Sending album to $url: ${payload.toString().take(200)}")
        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

        if (conn.responseCode !in 200..299) {
            val err = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            Log.e("CdnService", "Failed to send album via CDN (${conn.responseCode}): $err")
            throw Exception("Failed to send album via CDN (${conn.responseCode}): $err")
        }

        val resp = JSONObject(conn.inputStream.bufferedReader().readText())
        val messageId = resp.getString("messageId")
        val seq = resp.optLong("seq", 0L)
        Pair(messageId, seq)
    }

    suspend fun uploadFileWithDetails(
        file: File,
        mimeType: String,
        isVault: Boolean = false,
        forceRefreshAuth: Boolean = false,
        onProgress: ((Float) -> Unit)? = null
    ): CdnUploadResult = withContext(Dispatchers.IO) {
        // Для видео и файлов больше 5 МБ используем устойчивую чанковую загрузку
        if (file.length() > 5 * 1024 * 1024 || mimeType.startsWith("video/")) {
            return@withContext uploadFileChunked(file, mimeType, isVault, onProgress = onProgress)
        }

        val auth = FirebaseAuth.getInstance()
        val token = auth.currentUser?.getIdToken(forceRefreshAuth)?.await()?.token ?: throw Exception("No auth token")
        val zone = if (isVault) "vault" else "public"

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }

        val boundary = "----VisorLinkBoundary${System.currentTimeMillis()}"
        val connection = URL("$BASE_URL/upload").openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 90000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        DataOutputStream(connection.outputStream).use { out ->
            val crlf = "\r\n"
            val twoHyphens = "--"

            fun writeField(name: String, value: String) {
                out.writeBytes("$twoHyphens$boundary$crlf")
                out.writeBytes("Content-Disposition: form-data; name=\"$name\"$crlf$crlf")
                out.writeBytes("$value$crlf")
            }

            writeField("zone", zone)
            writeField("hash", hash)
            writeField("original_name", file.name)
            writeField("mime_type", mimeType)

            out.writeBytes("$twoHyphens$boundary$crlf")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"$crlf")
            out.writeBytes("Content-Type: $mimeType$crlf$crlf")

            FileInputStream(file).use { input ->
                val ioBuffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                val fileLength = file.length()

                while (input.read(ioBuffer).also { bytesRead = it } != -1) {
                    out.write(ioBuffer, 0, bytesRead)
                    totalRead += bytesRead
                    val progress = if (fileLength > 0) totalRead.toFloat() / fileLength else 1f
                    onProgress?.invoke(progress)
                }
            }
            out.writeBytes(crlf)
            out.writeBytes("$twoHyphens$boundary--$crlf")
            out.flush()
        }

        val code = connection.responseCode
        when (code) {
            200 -> {
                val responseJson = JSONObject(connection.inputStream.bufferedReader().readText())
                CdnUploadResult(
                    mediaId = responseJson.getString("media_id"),
                    duration = responseJson.optInt("duration").takeIf { it > 0 },
                    width = responseJson.optInt("width").takeIf { it > 0 },
                    height = responseJson.optInt("height").takeIf { it > 0 },
                    thumbId = responseJson.optString("thumb_id").takeIf { it.isNotBlank() },
                    thumbUrl = responseJson.optString("thumb_url").takeIf { it.isNotBlank() },
                    size = responseJson.optLong("size", file.length())
                )
            }
            401 -> {
                val err = try { connection.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                Log.e("CdnService", "CDN 401 Unauthorized: $err")
                if (!forceRefreshAuth) {
                    uploadFileWithDetails(file, mimeType, isVault, forceRefreshAuth = true, onProgress)
                } else {
                    throw Exception("Unauthorized: ${err ?: "Ошибка авторизации"}")
                }
            }
            413 -> throw Exception("Превышена квота облака. Удалите старые файлы или приобретите PRO")
            503 -> throw Exception("Сервер временно перегружен. Повторите попытку позже")
            else -> {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: ""
                throw Exception(JSONObject(errorText).optString("error", "Ошибка загрузки ($code)"))
            }
        }
    }

    suspend fun uploadFile(
        file: File,
        mimeType: String,
        isVault: Boolean = false,
        forceRefreshAuth: Boolean = false,
        onProgress: ((Float) -> Unit)? = null
    ): String = uploadFileWithDetails(file, mimeType, isVault, forceRefreshAuth, onProgress).mediaId
}