package by.iposdev.visorlink.utils

import android.content.Context
import android.util.Log
import by.iposdev.visorlink.data.model.Message
import by.iposdev.visorlink.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "ChatDataCache"

/**
 * Простой файловый кэш для сообщений и профилей.
 * Формат: JSON-файлы в cacheDir/chat_data_cache/
 *
 * Firestore уже имеет встроенный offline кэш, но он не даёт
 * контроль над размером и сроком жизни — этот кэш дополняет его,
 * обеспечивая быструю отрисовку до прихода данных из сети.
 */
object ChatDataCache {

    // ── Сообщения ─────────────────────────────────────────────────────────────

    suspend fun saveMessages(context: Context, chatId: String, messages: List<Message>) =
        withContext(Dispatchers.IO) {
            try {
                val arr = JSONArray()
                messages.takeLast(50).forEach { msg -> // храним последние 50
                    arr.put(msg.toJson())
                }
                cacheFile(context, "msgs_$chatId").writeText(arr.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save messages for $chatId", e)
            }
        }

    suspend fun loadMessages(context: Context, chatId: String): List<Message> =
        withContext(Dispatchers.IO) {
            try {
                val file = cacheFile(context, "msgs_$chatId")
                if (!file.exists()) return@withContext emptyList()
                val arr = JSONArray(file.readText())
                (0 until arr.length()).map { arr.getJSONObject(it).toMessage() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load messages for $chatId", e)
                emptyList()
            }
        }

    // ── Профили пользователей ─────────────────────────────────────────────────

    suspend fun saveProfile(context: Context, profile: UserProfile) =
        withContext(Dispatchers.IO) {
            try {
                cacheFile(context, "profile_${profile.uid}").writeText(profile.toJson().toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save profile ${profile.uid}", e)
            }
        }

    suspend fun loadProfile(context: Context, uid: String): UserProfile? =
        withContext(Dispatchers.IO) {
            try {
                val file = cacheFile(context, "profile_$uid")
                if (!file.exists()) return@withContext null
                file.readText().toUserProfile()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profile $uid", e)
                null
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun cacheFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, CACHE_DIR_CHAT)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$name.json")
    }

    // ── Message serialization (только нужные поля) ────────────────────────────

    private fun Message.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("senderId", senderId)
        put("senderUsername", senderUsername)
        put("type", type)
        put("text", text ?: JSONObject.NULL)
        put("url", url ?: JSONObject.NULL)
        put("duration", duration ?: JSONObject.NULL)
        put("deleted", deleted)
        put("createdAt", createdAt?.seconds ?: JSONObject.NULL)
    }

    private fun JSONObject.toMessage(): Message = Message(
        id             = getString("id"),
        senderId       = getString("senderId"),
        senderUsername = optString("senderUsername"),
        type           = optString("type", "text"),
        text           = if (isNull("text")) null else optString("text"),
        url            = if (isNull("url")) null else optString("url"),
        duration       = if (isNull("duration")) null else optInt("duration"),
        deleted        = optBoolean("deleted", false),
        createdAt      = if (isNull("createdAt")) null else
            com.google.firebase.Timestamp(getLong("createdAt"), 0)
    )

    // ── UserProfile serialization ─────────────────────────────────────────────

    private fun UserProfile.toJson(): JSONObject = JSONObject().apply {
        put("uid", uid)
        put("username", username)
        put("displayName", displayName)
        put("bio", bio)
        put("avatarUrl", avatarUrl ?: JSONObject.NULL)
        put("online", online)
    }

    private fun String.toUserProfile(): UserProfile? = try {
        val j = JSONObject(this)
        UserProfile(
            uid         = j.getString("uid"),
            username    = j.optString("username"),
            displayName = j.optString("displayName"),
            bio         = j.optString("bio"),
            avatarUrl   = if (j.isNull("avatarUrl")) null else j.optString("avatarUrl"),
            online      = j.optBoolean("online", false)
        )
    } catch (e: Exception) { null }
}