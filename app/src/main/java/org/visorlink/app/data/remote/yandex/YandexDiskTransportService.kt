package org.visorlink.app.data.remote.yandex

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Сетевой транспорт для обмена сообщениями через Яндекс.Диск.
 * Поддерживает гибридный режим: быстрый WebDAV с автоматическим откатом на REST API.
 */
class YandexDiskTransportService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "YandexDiskTransport"
        private const val WEBDAV_BASE = "https://webdav.yandex.ru"
        private const val REST_BASE = "https://cloud-api.yandex.net/v1/disk/resources"
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }

    /**
     * Создает папку (и родительские директории при необходимости).
     */
    suspend fun ensureFolder(token: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val cleanPath = normalizePath(remotePath)
        val isApp = cleanPath.startsWith("app:")
        val prefix = if (isApp) "app:" else "disk:"
        val relative = cleanPath.removePrefix("app:/").removePrefix("app:").removePrefix("disk:/").removePrefix("disk:").trim('/')
        val segments = relative.split("/").filter { it.isNotEmpty() }
        var current = prefix
        for (segment in segments) {
            current += "/$segment"
            createSingleFolder(token, current)
        }
        true
    }

    private fun createSingleFolder(token: String, path: String) {
        // Корень папки приложения app:/ или диска создавать не требуется
        if (path == "app:" || path == "app:/" || path == "disk:" || path == "disk:/") return

        // 1. Для app: путей всегда используем REST API (WebDAV не поддерживает app_folder)
        if (path.startsWith("app:")) {
            try {
                val encoded = URLEncoder.encode(path, "UTF-8")
                val restRequest = Request.Builder()
                    .url("$REST_BASE?path=$encoded")
                    .put("".toRequestBody(OCTET_STREAM))
                    .header("Authorization", "OAuth $token")
                    .build()
                client.newCall(restRequest).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 409) { // 409 = Resource already exists
                        Log.w(TAG, "Create folder $path returned HTTP ${resp.code}: ${resp.body?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create folder $path via REST", e)
            }
            return
        }

        // 2. Для disk:/ пробуем WebDAV MKCOL
        try {
            val webdavPath = path.removePrefix("disk:")
            val webdavRequest = Request.Builder()
                .url("$WEBDAV_BASE$webdavPath")
                .method("MKCOL", null)
                .header("Authorization", "OAuth $token")
                .build()
            client.newCall(webdavRequest).execute().use { resp ->
                if (resp.code in 200..207 || resp.code == 405 || resp.code == 409) {
                    return
                }
            }
        } catch (_: Exception) {}

        // Fallback на REST API
        try {
            val encoded = URLEncoder.encode(path, "UTF-8")
            val restRequest = Request.Builder()
                .url("$REST_BASE?path=$encoded")
                .put("".toRequestBody(OCTET_STREAM))
                .header("Authorization", "OAuth $token")
                .build()
            client.newCall(restRequest).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create folder $path via REST", e)
        }
    }

    /**
     * Загружает зашифрованный конверт сообщения на Яндекс.Диск.
     */
    suspend fun uploadEnvelope(
        token: String,
        remoteFolder: String,
        fileName: String,
        data: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanFolder = normalizePath(remoteFolder)
        val filePath = "$cleanFolder/$fileName"

        // Если путь на обычном Диске (не app:), пробуем сначала быстрый WebDAV PUT
        if (!cleanFolder.startsWith("app:")) {
            try {
                val webdavPath = cleanFolder.removePrefix("disk:") + "/$fileName"
                val request = Request.Builder()
                    .url("$WEBDAV_BASE$webdavPath")
                    .put(data.toRequestBody(OCTET_STREAM))
                    .header("Authorization", "OAuth $token")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Log.d(TAG, "Uploaded envelope $fileName via WebDAV (${data.size} bytes)")
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "WebDAV upload failed for $fileName, attempting REST fallback", e)
            }
        }

        // REST API (2 шага: получение upload URL -> загрузка данных)
        try {
            val encodedPath = URLEncoder.encode(filePath, "UTF-8")
            val getUrlReq = Request.Builder()
                .url("$REST_BASE/upload?path=$encodedPath&overwrite=true")
                .get()
                .header("Authorization", "OAuth $token")
                .build()

            val uploadHref = client.newCall(getUrlReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string()
                    Log.e(TAG, "Failed to get upload URL for $filePath: HTTP ${resp.code}, $err")
                    return@withContext false
                }
                val body = resp.body?.string() ?: return@withContext false
                JSONObject(body).optString("href")
            }

            if (uploadHref.isBlank()) {
                Log.e(TAG, "Upload href is blank for $filePath")
                return@withContext false
            }

            val putDataReq = Request.Builder()
                .url(uploadHref)
                .put(data.toRequestBody(OCTET_STREAM))
                .build()

            client.newCall(putDataReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    Log.d(TAG, "Uploaded envelope $fileName via REST (${data.size} bytes)")
                    return@withContext true
                } else {
                    Log.e(TAG, "PUT data failed: HTTP ${resp.code}, ${resp.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "REST upload failed for $fileName", e)
        }

        false
    }

    /**
     * Получает список имен файлов в папке почтового ящика.
     */
    suspend fun listEnvelopes(token: String, remoteFolder: String): List<String> = withContext(Dispatchers.IO) {
        val cleanFolder = normalizePath(remoteFolder)

        // 1. Через REST API (поддерживает и app:, и disk:)
        try {
            val encoded = URLEncoder.encode(cleanFolder, "UTF-8")
            val request = Request.Builder()
                .url("$REST_BASE?path=$encoded&limit=100")
                .get()
                .header("Authorization", "OAuth $token")
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val embedded = json.optJSONObject("_embedded")
                    val items = embedded?.optJSONArray("items")
                    if (items != null) {
                        val names = mutableListOf<String>()
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            if (item.optString("type") == "file") {
                                names.add(item.getString("name"))
                            }
                        }
                        return@withContext names
                    }
                } else if (resp.code == 404) {
                    return@withContext emptyList()
                } else {
                    Log.w(TAG, "REST list failed for $cleanFolder: HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "REST list failed for $cleanFolder, trying WebDAV PROPFIND", e)
        }

        // 2. Fallback через WebDAV PROPFIND (только если не app:)
        if (!cleanFolder.startsWith("app:")) {
            try {
                val webdavPath = cleanFolder.removePrefix("disk:")
                val request = Request.Builder()
                    .url("$WEBDAV_BASE$webdavPath/")
                    .method("PROPFIND", null)
                    .header("Authorization", "OAuth $token")
                    .header("Depth", "1")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (resp.code in 200..207) {
                        val xml = resp.body?.string() ?: ""
                        return@withContext parseWebDavFileNames(xml, cleanFolder)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "WebDAV PROPFIND failed for $cleanFolder", e)
            }
        }

        emptyList()
    }

    /**
     * Скачивает байты зашифрованного конверта.
     */
    suspend fun downloadEnvelope(token: String, remoteFolder: String, fileName: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val cleanFolder = normalizePath(remoteFolder)
            val filePath = "$cleanFolder/$fileName"

            // 1. Если не app:, пробуем WebDAV GET напрямую
            if (!cleanFolder.startsWith("app:")) {
                try {
                    val webdavPath = cleanFolder.removePrefix("disk:") + "/$fileName"
                    val request = Request.Builder()
                        .url("$WEBDAV_BASE$webdavPath")
                        .get()
                        .header("Authorization", "OAuth $token")
                        .build()
                    client.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            return@withContext resp.body?.bytes()
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. REST API download link
            try {
                val encoded = URLEncoder.encode(filePath, "UTF-8")
                val getLinkReq = Request.Builder()
                    .url("$REST_BASE/download?path=$encoded")
                    .get()
                    .header("Authorization", "OAuth $token")
                    .build()

                val downloadHref = client.newCall(getLinkReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e(TAG, "Failed to get download URL for $filePath: HTTP ${resp.code}")
                        return@withContext null
                    }
                    val body = resp.body?.string() ?: return@withContext null
                    JSONObject(body).optString("href")
                }

                if (downloadHref.isNotBlank()) {
                    val downloadReq = Request.Builder().url(downloadHref).get().build()
                    client.newCall(downloadReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            return@withContext resp.body?.bytes()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download envelope $fileName", e)
            }

            null
        }

    /**
     * Удаляет файл конверта после успешного прочтения (подтверждение доставки).
     */
    suspend fun deleteEnvelope(token: String, remoteFolder: String, fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            val cleanFolder = normalizePath(remoteFolder)
            val filePath = "$cleanFolder/$fileName"

            if (!cleanFolder.startsWith("app:")) {
                try {
                    val webdavPath = cleanFolder.removePrefix("disk:") + "/$fileName"
                    val request = Request.Builder()
                        .url("$WEBDAV_BASE$webdavPath")
                        .delete()
                        .header("Authorization", "OAuth $token")
                        .build()
                    client.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful || resp.code == 404) return@withContext true
                    }
                } catch (_: Exception) {}
            }

            try {
                val encoded = URLEncoder.encode(filePath, "UTF-8")
                val request = Request.Builder()
                    .url("$REST_BASE?path=$encoded&permanently=true")
                    .delete()
                    .header("Authorization", "OAuth $token")
                    .build()
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || resp.code == 404
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete envelope $fileName", e)
                false
            }
        }

    fun normalizePath(path: String): String {
        val p = path.trim().trimEnd('/')
        return when {
            p.startsWith("app:/") || p.startsWith("disk:/") -> p
            p.startsWith("app:") -> "app:/" + p.removePrefix("app:").trimStart('/')
            p.startsWith("disk:") -> "disk:/" + p.removePrefix("disk:").trimStart('/')
            else -> "app:/" + p.trimStart('/')
        }
    }

    private fun parseWebDavFileNames(xml: String, currentFolder: String): List<String> {
        val list = mutableListOf<String>()
        val regex = Regex("<(?:[a-zA-Z0-9]+:)?href>([^<]+)</(?:[a-zA-Z0-9]+:)?href>")
        val matches = regex.findAll(xml)
        for (m in matches) {
            val rawHref = m.groupValues[1]
            val fileName = rawHref.substringAfterLast("/").trim()
            if (fileName.isNotEmpty() && !fileName.equals(currentFolder.substringAfterLast("/"), ignoreCase = true)) {
                list.add(fileName)
            }
        }
        return list
    }
}
