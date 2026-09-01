package by.iposdev.visorlink.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import by.iposdev.visorlink.data.model.StickerItem
import by.iposdev.visorlink.data.model.StickerPack
import by.iposdev.visorlink.utils.ChatDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class StickerPackRepository(
    private val auth: FirebaseAuth,
    private val context: Context
) {
    private val currentUid get() = auth.currentUser!!.uid
    private val baseUrl = "https://api.visorlink.org"

    private val _packsFlow = MutableStateFlow<List<StickerPack>>(emptyList())

    fun observeUserPacks(): Flow<List<StickerPack>> = _packsFlow.asStateFlow()

    suspend fun refreshPacks() = withContext(Dispatchers.IO) {
        try {
            val cached = ChatDataCache.loadStickerPacks(context, currentUid)
            if (cached.isNotEmpty() && _packsFlow.value.isEmpty()) {
                _packsFlow.value = cached
            }

            val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: return@withContext
            val url = URL("$baseUrl/stickerpacks/my")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val packsArray = json.optJSONArray("packs") ?: JSONArray()
                val packs = (0 until packsArray.length()).map {
                    parseStickerPack(packsArray.getJSONObject(it))
                }
                _packsFlow.value = packs
                ChatDataCache.saveStickerPacks(context, currentUid, packs)
            }
        } catch (e: Exception) {
            Log.e("StickerRepo", "Failed to fetch packs", e)
        }
    }

    private fun parseStickerPack(json: JSONObject): StickerPack {
        val stickersArray = json.optJSONArray("stickers") ?: JSONArray()
        val stickers = (0 until stickersArray.length()).map { i ->
            val sJson = stickersArray.getJSONObject(i)
            StickerItem(
                id = sJson.getString("id"),
                url = sJson.getString("url"),
                emoji = sJson.getString("emoji"),
                sortOrder = sJson.optInt("sort_order", 0)
            )
        }.sortedBy { it.sortOrder }

        return StickerPack(
            id = json.getString("id"),
            name = json.getString("name"),
            emoji = json.getString("emoji"),
            authorId = json.getString("author_id"),
            authorName = json.getString("author_name"),
            stickerCount = json.getInt("sticker_count"),
            stickers = stickers
        )
    }

    suspend fun getPackById(packId: String): StickerPack? {
        return _packsFlow.value.find { it.id == packId }
    }

    suspend fun hasPack(packId: String): Boolean {
        return _packsFlow.value.any { it.id == packId }
    }

    suspend fun fetchPackDetails(packId: String): StickerPack? = withContext(Dispatchers.IO) {
        val local = _packsFlow.value.find { it.id == packId }
        if (local != null) return@withContext local

        try {
            val token = auth.currentUser?.getIdToken(false)?.await()?.token
            val url = URL("$baseUrl/stickerpacks/$packId")
            val conn = url.openConnection() as HttpURLConnection
            if (!token.isNullOrEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val packJson = json.optJSONObject("pack") ?: json
                parseStickerPack(packJson)
            } else null
        } catch (e: Exception) {
            Log.e("StickerRepo", "Failed to fetch pack details for $packId", e)
            null
        }
    }

    suspend fun createPack(name: String, emoji: String): String = withContext(Dispatchers.IO) {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: throw Exception("No auth token")
        val url = URL("$baseUrl/stickerpacks/create")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")

        val authorName = ChatDataCache.loadProfile(context, currentUid)?.displayName ?: "User"

        val json = JSONObject().apply {
            put("name", name)
            put("emoji", emoji)
            put("author_name", authorName)
        }

        conn.outputStream.write(json.toString().toByteArray(Charsets.UTF_8))

        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().readText()
            val resJson = JSONObject(response)
            refreshPacks()
            return@withContext resJson.getString("pack_id")
        } else {
            throw Exception("Create pack failed")
        }
    }

    suspend fun deletePack(packId: String, isOwner: Boolean) = withContext(Dispatchers.IO) {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: throw Exception("No auth token")
        val endpoint = if (isOwner) "/stickerpacks/$packId" else "/stickerpacks/$packId/library"
        val url = URL("$baseUrl$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Authorization", "Bearer $token")

        if (conn.responseCode == 200) {
            refreshPacks()
        } else {
            throw Exception("Delete pack failed")
        }
    }

    suspend fun addPackToUser(packId: String): String = withContext(Dispatchers.IO) {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: throw Exception("No auth token")
        val url = URL("$baseUrl/stickerpacks/$packId/library")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")

        if (conn.responseCode == 200) {
            refreshPacks()
            return@withContext "Pack Added"
        } else {
            throw Exception("Add pack failed")
        }
    }

    suspend fun uploadSticker(packId: String, uri: Uri, emoji: String): StickerItem = withContext(Dispatchers.IO) {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: throw Exception("No auth token")
        val boundary = "----VisorLinkStickerBoundary${System.currentTimeMillis()}"
        val url = URL("$baseUrl/stickerpacks/$packId/add_sticker")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        DataOutputStream(conn.outputStream).use { out ->
            val crlf = "\r\n"
            val twoHyphens = "--"

            fun writeField(name: String, value: String) {
                out.writeBytes("$twoHyphens$boundary$crlf")
                out.writeBytes("Content-Disposition: form-data; name=\"$name\"$crlf$crlf")
                out.write(value.toByteArray(Charsets.UTF_8))
                out.writeBytes(crlf)
            }

            writeField("emoji", emoji)
            val fileName = "sticker_${System.currentTimeMillis()}.webp"
            writeField("name", fileName)

            out.writeBytes("$twoHyphens$boundary$crlf")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$crlf")
            out.writeBytes("Content-Type: image/webp$crlf$crlf")

            context.contentResolver.openInputStream(uri)?.use { input ->
                input.copyTo(out)
            }
            out.writeBytes(crlf)
            out.writeBytes("$twoHyphens$boundary--$crlf")
            out.flush()
        }

        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            refreshPacks()
            return@withContext StickerItem(
                id = json.getString("id"),
                url = json.getString("url"),
                emoji = json.getString("emoji"),
                sortOrder = json.optInt("sort_order", 0)
            )
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText()
            throw Exception("Upload failed: $err")
        }
    }

    suspend fun deleteStickerFromPack(packId: String, sticker: StickerItem) = withContext(Dispatchers.IO) {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: throw Exception("No auth token")
        val url = URL("$baseUrl/stickerpacks/$packId/stickers/${sticker.id}")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Authorization", "Bearer $token")

        if (conn.responseCode == 200) {
            refreshPacks()
        } else {
            throw Exception("Delete sticker failed")
        }
    }
}