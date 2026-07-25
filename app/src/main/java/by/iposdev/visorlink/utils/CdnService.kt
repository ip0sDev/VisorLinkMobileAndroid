package by.iposdev.visorlink.utils

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

    suspend fun uploadFile(file: File, isVault: Boolean = false, onProgress: ((Float) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: throw Exception("No auth token")
        val zone = if (isVault) "vault" else "public"

        // 1. Считаем хэш
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }

        // 2. Формируем Multipart-запрос
        val boundary = "----VisorLinkBoundary${System.currentTimeMillis()}"
        val connection = URL("$BASE_URL/upload").openConnection() as HttpURLConnection
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

            val mimeType = when {
                file.name.endsWith(".mp4", true) -> "video/mp4"
                file.name.endsWith(".webm", true) -> "video/webm"
                file.name.endsWith(".gif", true) -> "image/gif"
                else -> "image/jpeg"
            }
            writeField("mime_type", mimeType)

            // Запись файла с прогрессом
            out.writeBytes("$twoHyphens$boundary$crlf")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"$crlf")
            out.writeBytes("Content-Type: $mimeType$crlf$crlf")

            FileInputStream(file).use { input ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var totalRead = 0L
                val fileLength = file.length()

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    onProgress?.invoke(totalRead.toFloat() / fileLength)
                }
            }
            out.writeBytes(crlf)
            out.writeBytes("$twoHyphens$boundary--$crlf")
            out.flush()
        }

        if (connection.responseCode == 200) {
            val responseJson = JSONObject(connection.inputStream.bufferedReader().readText())
            responseJson.getString("media_id")
        } else {
            val errorText = connection.errorStream?.bufferedReader()?.readText() ?: ""
            throw Exception(JSONObject(errorText).optString("error", "Ошибка загрузки"))
        }
    }
}