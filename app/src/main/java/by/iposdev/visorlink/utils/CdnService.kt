// utils/CdnService.kt
package by.iposdev.visorlink.utils

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
        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: ""
        return "$BASE_URL/f/$cdnMediaId?token=$token"
    }

    suspend fun uploadFile(
        file: File,
        mimeType: String,
        isVault: Boolean = false,
        forceRefreshAuth: Boolean = false,
        onProgress: ((Float) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val token = auth.currentUser?.getIdToken(forceRefreshAuth)?.await()?.token ?: throw Exception("No auth token")
        val zone = if (isVault) "vault" else "public"

        // 1. Считаем хэш
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }

        // 2. Формируем Multipart-запрос
        val boundary = "----VisorLinkBoundary${System.currentTimeMillis()}"
        val connection = URL("$BASE_URL/upload").openConnection() as HttpURLConnection
        connection.connectTimeout = 30000 // 30s for connect
        connection.readTimeout = 90000    // 90s for large uploads
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
                    onProgress?.invoke(totalRead.toFloat() / fileLength)
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
                responseJson.getString("media_id")
            }
            401 -> {
                if (!forceRefreshAuth) {
                    // Ретрай с обновлением токена
                    uploadFile(file, mimeType, isVault, forceRefreshAuth = true, onProgress)
                } else {
                    throw Exception("Unauthorized: Ошибка авторизации")
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
}