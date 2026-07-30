package by.iposdev.visorlink.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import by.iposdev.visorlink.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val DB_NAME = "visorlink_cache.db"
private const val DB_VERSION = 1
private const val TAG = "ChatDataCache"

class LocalCacheDB(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        // Создаем таблицы. Используем составные первичные ключи для защиты от дублей.
        db.execSQL("CREATE TABLE chats (uid TEXT, chat_id TEXT, data TEXT, PRIMARY KEY(uid, chat_id))")
        db.execSQL("CREATE TABLE messages (chat_id TEXT, msg_id TEXT, ts INTEGER, data TEXT, PRIMARY KEY(chat_id, msg_id))")
        db.execSQL("CREATE TABLE profiles (uid TEXT PRIMARY KEY, data TEXT)")
        db.execSQL("CREATE TABLE stickers (uid TEXT, pack_id TEXT, data TEXT, PRIMARY KEY(uid, pack_id))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS chats")
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS profiles")
        db.execSQL("DROP TABLE IF EXISTS stickers")
        onCreate(db)
    }
}

object ChatDataCache {
    private var dbHelper: LocalCacheDB? = null

    private fun getDb(context: Context): LocalCacheDB {
        if (dbHelper == null) {
            dbHelper = LocalCacheDB(context.applicationContext)
        }
        return dbHelper!!
    }

    fun clearAll(context: Context) {
        getDb(context).writableDatabase.apply {
            execSQL("DELETE FROM chats")
            execSQL("DELETE FROM messages")
            execSQL("DELETE FROM profiles")
            execSQL("DELETE FROM stickers")
        }
    }

    // ── Списки чатов ─────────────────────────────────────────────────────────

    suspend fun saveChatList(context: Context, uid: String, chats: List<Chat>) =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).writableDatabase
                db.beginTransaction()
                try {
                    db.delete("chats", "uid=?", arrayOf(uid))
                    val stmt = db.compileStatement("INSERT INTO chats (uid, chat_id, data) VALUES (?, ?, ?)")
                    chats.forEach { chat ->
                        stmt.bindString(1, uid)
                        stmt.bindString(2, chat.id)
                        stmt.bindString(3, chat.toJson().toString())
                        stmt.executeInsert()
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to save chat list", e) }
        }

    suspend fun loadChatList(context: Context, uid: String): List<Chat> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<Chat>()
            try {
                val db = getDb(context).readableDatabase
                db.rawQuery("SELECT data FROM chats WHERE uid=?", arrayOf(uid)).use { cursor ->
                    while (cursor.moveToNext()) {
                        try { list.add(JSONObject(cursor.getString(0)).toChat()) } catch(_: Exception) {}
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to load chat list", e) }
            list
        }

    // ── Сообщения ─────────────────────────────────────────────────────────────

    suspend fun saveMessages(context: Context, chatId: String, messages: List<Message>) =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).writableDatabase
                db.beginTransaction()
                try {
                    val stmt = db.compileStatement("INSERT OR REPLACE INTO messages (chat_id, msg_id, ts, data) VALUES (?, ?, ?, ?)")
                    messages.takeLast(60).forEach { msg ->
                        stmt.bindString(1, chatId)
                        stmt.bindString(2, msg.id)
                        stmt.bindLong(3, msg.createdAt?.seconds ?: 0L)
                        stmt.bindString(4, msg.toJson().toString())
                        stmt.executeInsert()
                    }
                    // Очистка старых сообщений (лимит 60 на чат)
                    db.execSQL("DELETE FROM messages WHERE chat_id = ? AND msg_id NOT IN (SELECT msg_id FROM messages WHERE chat_id = ? ORDER BY ts DESC LIMIT 60)", arrayOf(chatId, chatId))
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to save messages for $chatId", e) }
        }

    suspend fun loadMessages(context: Context, chatId: String): List<Message> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<Message>()
            try {
                val db = getDb(context).readableDatabase
                // Выбираем 60 последних сообщений
                db.rawQuery("SELECT data FROM messages WHERE chat_id=? ORDER BY ts DESC LIMIT 60", arrayOf(chatId)).use { cursor ->
                    while (cursor.moveToNext()) {
                        try { list.add(JSONObject(cursor.getString(0)).toMessage()) } catch(_: Exception) {}
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to load messages", e) }
            // Возвращаем в хронологическом порядке (т.к. брали DESC)
            list.reversed()
        }

    // ── Стикерпаки ────────────────────────────────────────────────────────────

    suspend fun saveStickerPacks(context: Context, uid: String, packs: List<StickerPack>) =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).writableDatabase
                db.beginTransaction()
                try {
                    db.delete("stickers", "uid=?", arrayOf(uid))
                    val stmt = db.compileStatement("INSERT INTO stickers (uid, pack_id, data) VALUES (?, ?, ?)")
                    packs.forEach { pack ->
                        stmt.bindString(1, uid)
                        stmt.bindString(2, pack.id)
                        stmt.bindString(3, pack.toJson().toString())
                        stmt.executeInsert()
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to save sticker packs", e) }
        }

    suspend fun loadStickerPacks(context: Context, uid: String): List<StickerPack> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<StickerPack>()
            try {
                val db = getDb(context).readableDatabase
                db.rawQuery("SELECT data FROM stickers WHERE uid=?", arrayOf(uid)).use { cursor ->
                    while (cursor.moveToNext()) {
                        try { list.add(JSONObject(cursor.getString(0)).toStickerPack()) } catch(_: Exception) {}
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to load sticker packs", e) }
            list
        }

    // ── Профили пользователей ─────────────────────────────────────────────────

    suspend fun saveProfile(context: Context, profile: UserProfile) =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).writableDatabase
                val stmt = db.compileStatement("INSERT OR REPLACE INTO profiles (uid, data) VALUES (?, ?)")
                stmt.bindString(1, profile.uid)
                stmt.bindString(2, profile.toJson().toString())
                stmt.executeInsert()
            } catch (e: Exception) { Log.e(TAG, "Failed to save profile ${profile.uid}", e) }
        }

    suspend fun loadProfile(context: Context, uid: String): UserProfile? =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).readableDatabase
                db.rawQuery("SELECT data FROM profiles WHERE uid=?", arrayOf(uid)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        return@withContext JSONObject(cursor.getString(0)).toUserProfile()
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to load profile", e) }
            null
        }

    // ════════════════════════════════════════════════════════════════════════
    // ── Вспомогательные мапперы JSON ──
    // ════════════════════════════════════════════════════════════════════════

    private fun Chat.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("participants", JSONArray(participants))
        val pData = JSONObject()
        participantData.forEach { (k, v) ->
            val vObj = JSONObject()
            v.forEach { (vk, vv) -> vObj.put(vk, vv) }
            pData.put(k, vObj)
        }
        put("participantData", pData)
        put("name", name)
        put("tag", tag)
        put("description", description)
        put("avatarUrl", avatarUrl ?: JSONObject.NULL)
        put("createdBy", createdBy)
        put("memberCount", memberCount)
        put("memberIds", JSONArray(memberIds))
        val sData = JSONObject().apply {
            put("joinByLink", settings.joinByLink)
            put("joinByTag", settings.joinByTag)
            put("allowReactions", settings.allowReactions)
            put("allowComments", settings.allowComments)
            put("inviteLink", settings.inviteLink)
        }
        put("settings", sData)
        put("lastMessage", lastMessage?.toString() ?: JSONObject.NULL)
        put("lastMessageAt", lastMessageAt?.seconds ?: JSONObject.NULL)
        put("createdAt", createdAt?.seconds ?: JSONObject.NULL)
    }

    private fun JSONObject.toChat(): Chat {
        val pArray = optJSONArray("participants") ?: JSONArray()
        val pList = (0 until pArray.length()).map { pArray.getString(it) }

        val pDataObj = optJSONObject("participantData") ?: JSONObject()
        val pData = mutableMapOf<String, Map<String, String>>()
        pDataObj.keys().forEach { k ->
            val vObj = pDataObj.getJSONObject(k)
            val vMap = mutableMapOf<String, String>()
            vObj.keys().forEach { vk -> vMap[vk] = vObj.getString(vk) }
            pData[k] = vMap
        }

        val mArray = optJSONArray("memberIds") ?: JSONArray()
        val mList = (0 until mArray.length()).map { mArray.getString(it) }

        val sObj = optJSONObject("settings") ?: JSONObject()
        val settings = ChatSettings(
            joinByLink = sObj.optBoolean("joinByLink", true),
            joinByTag = sObj.optBoolean("joinByTag", false),
            allowReactions = sObj.optBoolean("allowReactions", true),
            allowComments = sObj.optBoolean("allowComments", true),
            inviteLink = sObj.optString("inviteLink", "")
        )

        return Chat(
            id = getString("id"),
            type = getString("type"),
            participants = pList,
            participantData = pData,
            name = optString("name", ""),
            tag = optString("tag", ""),
            description = optString("description", ""),
            avatarUrl = if (isNull("avatarUrl")) null else getString("avatarUrl"),
            createdBy = optString("createdBy", ""),
            memberCount = optInt("memberCount", 0),
            memberIds = mList,
            settings = settings,
            lastMessage = if (isNull("lastMessage")) null else getString("lastMessage"),
            lastMessageAt = if (isNull("lastMessageAt")) null else com.google.firebase.Timestamp(getLong("lastMessageAt"), 0),
            createdAt = if (isNull("createdAt")) null else com.google.firebase.Timestamp(getLong("createdAt"), 0)
        )
    }

    private fun Message.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("senderId", senderId)
        put("senderUsername", senderUsername)
        put("type", type)
        put("text", text ?: JSONObject.NULL)
        put("url", url ?: JSONObject.NULL)
        put("fileName", fileName ?: JSONObject.NULL)
        put("duration", duration ?: JSONObject.NULL)
        put("stickerId", stickerId ?: JSONObject.NULL)
        put("packId", packId ?: JSONObject.NULL)
        put("packName", packName ?: JSONObject.NULL)
        put("packEmoji", packEmoji ?: JSONObject.NULL)
        put("deleted", deleted)
        put("createdAt", createdAt?.seconds ?: JSONObject.NULL)
        put("spoiler", spoiler)
        put("commentsEnabled", commentsEnabled ?: JSONObject.NULL)
        put("commentsCount", commentsCount)
        put("caption", caption ?: JSONObject.NULL)
        put("readBy", JSONArray(readBy))

        val imgArr = JSONArray()
        images.forEach {
            imgArr.put(JSONObject().apply {
                put("url", it.url)
                put("fileName", it.fileName)
                put("spoiler", it.spoiler)
            })
        }
        put("images", imgArr)

        replyTo?.let { rep -> put("replyTo", JSONObject(rep)) } ?: put("replyTo", JSONObject.NULL)
        forwardFrom?.let { fwd -> put("forwardFrom", JSONObject(fwd)) } ?: put("forwardFrom", JSONObject.NULL)

        val rArr = JSONArray()
        reactions.forEach { rMap -> rArr.put(JSONObject(rMap)) }
        put("reactions", rArr)
    }

    private fun JSONObject.toMessage(): Message {
        val readByArr = optJSONArray("readBy") ?: JSONArray()
        val readByList = (0 until readByArr.length()).map { readByArr.getString(it) }

        val imagesArr = optJSONArray("images") ?: JSONArray()
        val imagesList = (0 until imagesArr.length()).map {
            val iObj = imagesArr.getJSONObject(it)
            AlbumImage(
                url = iObj.getString("url"),
                fileName = iObj.getString("fileName"),
                spoiler = iObj.getBoolean("spoiler")
            )
        }

        val reactionsArr = optJSONArray("reactions") ?: JSONArray()
        val reactionsList = (0 until reactionsArr.length()).map {
            val rObj = reactionsArr.getJSONObject(it)
            val uidsArr = rObj.getJSONArray("uids")
            val uidsList = (0 until uidsArr.length()).map { i -> uidsArr.getString(i) }
            mapOf("emoji" to rObj.getString("emoji"), "count" to rObj.getLong("count"), "uids" to uidsList)
        }

        val replyMap = if (isNull("replyTo")) null else {
            val rObj = getJSONObject("replyTo")
            mapOf<String, Any?>(
                "id" to rObj.optString("id"),
                "type" to rObj.optString("type"),
                "text" to if (rObj.isNull("text")) null else rObj.optString("text"),
                "url" to if (rObj.isNull("url")) null else rObj.optString("url"),
                "senderUsername" to rObj.optString("senderUsername")
            )
        }

        val forwardMap = if (isNull("forwardFrom")) null else {
            val fObj = getJSONObject("forwardFrom")
            mapOf<String, Any?>(
                "senderId" to fObj.optString("senderId"),
                "senderUsername" to fObj.optString("senderUsername"),
                "chatId" to if (fObj.isNull("chatId")) null else fObj.optString("chatId"),
                "chatName" to if (fObj.isNull("chatName")) null else fObj.optString("chatName"),
                "messageId" to fObj.optString("messageId")
            )
        }

        return Message(
            id = getString("id"),
            senderId = getString("senderId"),
            senderUsername = optString("senderUsername"),
            type = optString("type", "text"),
            text = if (isNull("text")) null else optString("text"),
            url = if (isNull("url")) null else optString("url"),
            fileName = if (isNull("fileName")) null else optString("fileName"),
            duration = if (isNull("duration")) null else optInt("duration"),
            stickerId = if (isNull("stickerId")) null else optString("stickerId"),
            packId = if (isNull("packId")) null else optString("packId"),
            packName = if (isNull("packName")) null else optString("packName"),
            packEmoji = if (isNull("packEmoji")) null else optString("packEmoji"),
            deleted = optBoolean("deleted", false),
            createdAt = if (isNull("createdAt")) null else com.google.firebase.Timestamp(getLong("createdAt"), 0),
            spoiler = optBoolean("spoiler", false),
            commentsEnabled = if (isNull("commentsEnabled")) null else optBoolean("commentsEnabled"),
            commentsCount = optInt("commentsCount", 0),
            caption = if (isNull("caption")) null else optString("caption"),
            readBy = readByList,
            images = imagesList,
            replyTo = replyMap,
            forwardFrom = forwardMap,
            reactions = reactionsList
        )
    }

    private fun StickerPack.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("emoji", emoji)
        put("authorId", authorId)
        put("authorName", authorName)
        put("stickerCount", stickerCount)
        val sArr = JSONArray()
        stickers.forEach { s ->
            sArr.put(JSONObject().apply {
                put("id", s.id)
                put("url", s.url)
                put("emoji", s.emoji)
                put("storagePath", s.storagePath)
                put("sortOrder", s.sortOrder)
            })
        }
        put("stickers", sArr)
    }

    private fun JSONObject.toStickerPack(): StickerPack {
        val sArr = optJSONArray("stickers") ?: JSONArray()
        val sList = (0 until sArr.length()).map {
            val sObj = sArr.getJSONObject(it)
            StickerItem(
                id = sObj.getString("id"),
                url = sObj.getString("url"),
                emoji = sObj.getString("emoji"),
                storagePath = sObj.optString("storagePath", ""),
                sortOrder = sObj.optInt("sortOrder", 0)
            )
        }
        return StickerPack(
            id = getString("id"),
            name = getString("name"),
            emoji = getString("emoji"),
            authorId = getString("authorId"),
            authorName = getString("authorName"),
            stickerCount = getInt("stickerCount"),
            stickers = sList
        )
    }

    private fun UserProfile.toJson(): JSONObject = JSONObject().apply {
        put("uid", uid)
        put("username", username)
        put("displayName", displayName)
        put("bio", bio)
        put("avatarUrl", avatarUrl ?: JSONObject.NULL)
        put("online", online)
        put("isAdmin", isAdmin)
    }

    private fun JSONObject.toUserProfile(): UserProfile? = try {
        UserProfile(
            uid         = getString("uid"),
            username    = optString("username"),
            displayName = optString("displayName"),
            bio         = optString("bio"),
            avatarUrl   = if (isNull("avatarUrl")) null else optString("avatarUrl"),
            online      = optBoolean("online", false),
            isAdmin     = optBoolean("isAdmin", false)
        )
    } catch (e: Exception) { null }
}