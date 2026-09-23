package org.visorlink.app.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.visorlink.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val DB_NAME = "visorlink_cache.db"
private const val DB_VERSION = 5
private const val TAG = "ChatDataCache"

class LocalCacheDB(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Создаем таблицы. Используем составные первичные ключи для защиты от дублей.
        db.execSQL("CREATE TABLE chats (uid TEXT, chat_id TEXT, data TEXT, PRIMARY KEY(uid, chat_id))")
        db.execSQL("CREATE TABLE messages (chat_id TEXT, msg_id TEXT, ts INTEGER, data TEXT, PRIMARY KEY(chat_id, msg_id))")
        db.execSQL("CREATE TABLE profiles (uid TEXT PRIMARY KEY, data TEXT)")
        db.execSQL("CREATE TABLE stickers (uid TEXT, pack_id TEXT, data TEXT, PRIMARY KEY(uid, pack_id))")
        db.execSQL("CREATE TABLE outbox (id TEXT PRIMARY KEY, chat_id TEXT, type TEXT, data TEXT, ts INTEGER, status INTEGER DEFAULT 0, retry_count INTEGER DEFAULT 0, last_attempt INTEGER DEFAULT 0, last_error TEXT, progress REAL DEFAULT 0.0)")
        db.execSQL("CREATE TABLE likes (uid TEXT, item_id TEXT, PRIMARY KEY(uid, item_id))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS outbox (id TEXT PRIMARY KEY, chat_id TEXT, type TEXT, data TEXT, ts INTEGER, status INTEGER DEFAULT 0)")
                db.execSQL("CREATE TABLE IF NOT EXISTS likes (uid TEXT, item_id TEXT, PRIMARY KEY(uid, item_id))")
            } catch (e: Exception) {
                Log.e(TAG, "Upgrade to v2 failed", e)
            }
        }
        if (oldVersion == 2) {
            ensureColumnExists(db, "outbox", "status", "INTEGER DEFAULT 0")
        }
        if (oldVersion < 4) {
            ensureColumnExists(db, "outbox", "retry_count", "INTEGER DEFAULT 0")
            ensureColumnExists(db, "outbox", "last_attempt", "INTEGER DEFAULT 0")
            ensureColumnExists(db, "outbox", "last_error", "TEXT")
        }
        if (oldVersion < 5) {
            ensureColumnExists(db, "outbox", "progress", "REAL DEFAULT 0.0")
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Downgrading database from $oldVersion to $newVersion. Recreating tables.")
        db.execSQL("DROP TABLE IF EXISTS chats")
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS profiles")
        db.execSQL("DROP TABLE IF EXISTS stickers")
        db.execSQL("DROP TABLE IF EXISTS outbox")
        db.execSQL("DROP TABLE IF EXISTS likes")
        onCreate(db)
    }

    private fun ensureColumnExists(db: SQLiteDatabase, table: String, column: String, def: String) {
        var cursor: android.database.Cursor? = null
        try {
            cursor = db.rawQuery("PRAGMA table_info($table)", null)
            var exists = false
            while (cursor.moveToNext()) {
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1 && cursor.getString(nameIndex) == column) {
                    exists = true
                    break
                }
            }
            if (!exists) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $column $def")
                Log.d(TAG, "Added column $column to $table")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure column $column in $table", e)
        } finally {
            cursor?.close()
        }
    }
}

object ChatDataCache {
    private var dbHelper: LocalCacheDB? = null

    private val _outboxSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val outboxSignal: SharedFlow<Unit> = _outboxSignal.asSharedFlow()

    fun outboxFlow(context: Context, chatId: String): Flow<List<QueuedAction>> = callbackFlow {
        fun reload() {
            launch(Dispatchers.IO) {
                try {
                    val outbox = loadOutbox(context, chatId)
                    trySend(outbox)
                } catch (_: Exception) {}
            }
        }
        reload()
        val collector = launch {
            outboxSignal.collect { reload() }
        }
        awaitClose { collector.cancel() }
    }

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
            execSQL("DELETE FROM outbox")
            execSQL("DELETE FROM likes")
        }
    }

    suspend fun pruneOldData(context: Context, days: Int) = withContext(Dispatchers.IO) {
        if (days <= 0) return@withContext
        try {
            val db = getDb(context).writableDatabase
            val threshold = System.currentTimeMillis() / 1000L - (days * 24 * 60 * 60)
            db.execSQL("DELETE FROM messages WHERE ts < ?", arrayOf(threshold.toString()))
            Log.d(TAG, "Pruned messages older than $days days (threshold: $threshold)")
        } catch (e: Exception) { Log.e(TAG, "Failed to prune old data", e) }
    }

    // ── Outbox ───────────────────────────────────────────────────────────────

    suspend fun addToOutbox(context: Context, chatId: String, type: String, data: JSONObject): String =
        withContext(Dispatchers.IO) {
            val uuid = UUID.randomUUID().toString().take(8)
            val id = "queued_${System.currentTimeMillis()}_$uuid"
            try {
                val db = getDb(context).writableDatabase
                val stmt = db.compileStatement("INSERT INTO outbox (id, chat_id, type, data, ts, status) VALUES (?, ?, ?, ?, ?, 0)")
                stmt.bindString(1, id)
                stmt.bindString(2, chatId)
                stmt.bindString(3, type)
                stmt.bindString(4, data.toString())
                stmt.bindLong(5, System.currentTimeMillis())
                stmt.executeInsert()
                _outboxSignal.emit(Unit)
            } catch (e: Exception) { Log.e(TAG, "Failed to add to outbox (id=$id)", e) }
            id
        }

    suspend fun loadOutbox(context: Context, filterChatId: String? = null): List<QueuedAction> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<QueuedAction>()
            try {
                val db = getDb(context).readableDatabase
                val sql = if (filterChatId != null) {
                    "SELECT id, chat_id, type, data, ts, status, retry_count, last_attempt, last_error, progress FROM outbox WHERE chat_id = ? ORDER BY ts ASC"
                } else {
                    "SELECT id, chat_id, type, data, ts, status, retry_count, last_attempt, last_error, progress FROM outbox ORDER BY ts ASC"
                }
                val args = if (filterChatId != null) arrayOf(filterChatId) else null
                db.rawQuery(sql, args).use { cursor ->
                    while (cursor.moveToNext()) {
                        list.add(QueuedAction(
                            id = cursor.getString(0),
                            chatId = cursor.getString(1),
                            type = cursor.getString(2),
                            data = JSONObject(cursor.getString(3)),
                            ts = cursor.getLong(4),
                            status = cursor.getInt(5),
                            retryCount = cursor.getInt(6),
                            lastAttempt = cursor.getLong(7),
                            lastError = cursor.getString(8),
                            progress = cursor.getFloat(9)
                        ))
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to load outbox", e) }
            list
        }

    suspend fun updateOutboxProgress(context: Context, id: String, progress: Float) =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).writableDatabase
                val stmt = db.compileStatement("UPDATE outbox SET progress=? WHERE id=?")
                stmt.bindDouble(1, progress.toDouble())
                stmt.bindString(2, id)
                stmt.executeUpdateDelete()
                _outboxSignal.emit(Unit)
            } catch (e: Exception) { Log.e(TAG, "Failed to update outbox progress", e) }
        }

    suspend fun updateOutboxRetry(context: Context, id: String, retryCount: Int, error: String?) =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).writableDatabase
                val stmt = db.compileStatement("UPDATE outbox SET retry_count=?, last_attempt=?, last_error=? WHERE id=?")
                stmt.bindLong(1, retryCount.toLong())
                stmt.bindLong(2, System.currentTimeMillis())
                if (error == null) stmt.bindNull(3) else stmt.bindString(3, error)
                stmt.bindString(4, id)
                stmt.executeUpdateDelete()
                _outboxSignal.emit(Unit)
            } catch (e: Exception) { Log.e(TAG, "Failed to update outbox retry", e) }
        }

    suspend fun updateOutboxStatus(context: Context, id: String, status: Int) =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).writableDatabase
                val stmt = db.compileStatement("UPDATE outbox SET status=? WHERE id=?")
                stmt.bindLong(1, status.toLong())
                stmt.bindString(2, id)
                stmt.executeUpdateDelete()
                _outboxSignal.emit(Unit)
            } catch (e: Exception) { Log.e(TAG, "Failed to update outbox status", e) }
        }

    suspend fun retryOutbox(context: Context, id: String) =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).writableDatabase
                val stmt = db.compileStatement("UPDATE outbox SET status=0, retry_count=0, last_attempt=0, last_error=NULL WHERE id=?")
                stmt.bindString(1, id)
                stmt.executeUpdateDelete()
                _outboxSignal.emit(Unit)
            } catch (e: Exception) { Log.e(TAG, "Failed to retry outbox action", e) }
        }

    suspend fun cleanupOutbox(context: Context, confirmedIds: List<String>) =
        withContext(Dispatchers.IO) {
            if (confirmedIds.isEmpty()) return@withContext
            try {
                val db = getDb(context).writableDatabase
                db.beginTransaction()
                try {
                    val stmt = db.compileStatement("DELETE FROM outbox WHERE id=?")
                    confirmedIds.forEach { id ->
                        stmt.bindString(1, id)
                        stmt.executeUpdateDelete()
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to cleanup outbox", e) }
        }

    // Action types: "text", "image", "sticker", "like"
    data class QueuedAction(
        val id: String,
        val chatId: String,
        val type: String,
        val data: JSONObject,
        val ts: Long,
        val status: Int = 0,
        val retryCount: Int = 0,
        val lastAttempt: Long = 0,
        val lastError: String? = null,
        val progress: Float = 0f
    )

    // ── Likes ────────────────────────────────────────────────────────────────

    suspend fun saveLike(context: Context, uid: String, itemId: String) =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).writableDatabase
                val stmt = db.compileStatement("INSERT OR IGNORE INTO likes (uid, item_id) VALUES (?, ?)")
                stmt.bindString(1, uid)
                stmt.bindString(2, itemId)
                stmt.executeInsert()
            } catch (e: Exception) { Log.e(TAG, "Failed to save like", e) }
        }

    suspend fun removeLike(context: Context, uid: String, itemId: String) =
        withContext(Dispatchers.IO) {
            try {
                getDb(context).writableDatabase.delete("likes", "uid=? AND item_id=?", arrayOf(uid, itemId))
            } catch (e: Exception) { Log.e(TAG, "Failed to remove like", e) }
        }

    suspend fun isLiked(context: Context, uid: String, itemId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).readableDatabase
                db.rawQuery("SELECT 1 FROM likes WHERE uid=? AND item_id=?", arrayOf(uid, itemId)).use { it.moveToFirst() }
            } catch (e: Exception) { false }
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

    suspend fun loadChat(context: Context, chatId: String): Chat? =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).readableDatabase
                db.rawQuery("SELECT data FROM chats WHERE chat_id=? LIMIT 1", arrayOf(chatId)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        try { return@withContext JSONObject(cursor.getString(0)).toChat() } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to load chat from cache", e) }
            null
        }

    // ── Сообщения ─────────────────────────────────────────────────────────────

    suspend fun saveMessages(context: Context, chatId: String, messages: List<Message>) =
        withContext(Dispatchers.IO) {
            try {
                val db = getDb(context).writableDatabase
                db.beginTransaction()
                try {
                    val stmt = db.compileStatement("INSERT OR REPLACE INTO messages (chat_id, msg_id, ts, data) VALUES (?, ?, ?, ?)")
                    messages.forEach { msg ->
                        stmt.bindString(1, chatId)
                        stmt.bindString(2, msg.id)
                        stmt.bindLong(3, msg.createdAt?.seconds ?: 0L)
                        stmt.bindString(4, msg.toJson().toString())
                        stmt.executeInsert()
                    }
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
                // Выбираем все сообщения для оффлайн-режима
                db.rawQuery("SELECT data FROM messages WHERE chat_id=? ORDER BY ts DESC", arrayOf(chatId)).use { cursor ->
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
                val effectiveUid = uid.ifBlank { "global" }
                db.beginTransaction()
                try {
                    // Полностью вычищаем старые стикеры, чтобы удаленные на сервере не оставались в SQLite
                    db.delete("stickers", null, null)
                    val stmt = db.compileStatement("INSERT INTO stickers (uid, pack_id, data) VALUES (?, ?, ?)")
                    packs.forEach { pack ->
                        stmt.bindString(1, effectiveUid)
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

    suspend fun deleteStickerPack(context: Context, packId: String) = withContext(Dispatchers.IO) {
        try {
            val db = getDb(context).writableDatabase
            db.delete("stickers", "pack_id=?", arrayOf(packId))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete sticker pack $packId from cache", e)
        }
    }

    suspend fun loadStickerPacks(context: Context, uid: String): List<StickerPack> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<StickerPack>()
            try {
                val db = getDb(context).readableDatabase
                val effectiveUid = uid.ifBlank { "global" }
                db.rawQuery("SELECT data FROM stickers WHERE uid=? OR uid='global'", arrayOf(effectiveUid)).use { cursor ->
                    while (cursor.moveToNext()) {
                        try { list.add(JSONObject(cursor.getString(0)).toStickerPack()) } catch(_: Exception) {}
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to load sticker packs", e) }
            list
        }

    suspend fun clearAllStickerPacks(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = getDb(context).writableDatabase
            db.delete("stickers", null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear sticker packs", e)
        }
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
            put("isForum", settings.isForum || isForum)
        }
        put("isForum", isForumActive)
        put("settings", sData)
        when (val lm = lastMessage) {
            is Map<*, *> -> put("lastMessage", JSONObject(lm))
            is String -> put("lastMessage", lm)
            else -> put("lastMessage", JSONObject.NULL)
        }
        put("lastMessageSenderId", lastMessageSenderId ?: JSONObject.NULL)
        put("lastSeq", lastSeq)
        put("lastMessageAt", lastMessageAt?.seconds ?: JSONObject.NULL)
        put("createdAt", createdAt?.seconds ?: JSONObject.NULL)
        val uData = JSONObject()
        unreadCount.forEach { (k, v) -> uData.put(k, v) }
        put("unreadCount", uData)
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
        val isForumSetting = sObj.optBoolean("isForum", false) || sObj.optBoolean("is_forum", false)
        val isForumRoot = optBoolean("isForum", false) || optBoolean("is_forum", false)
        val isForumFinal = isForumSetting || isForumRoot

        val settings = ChatSettings(
            joinByLink = sObj.optBoolean("joinByLink", true),
            joinByTag = sObj.optBoolean("joinByTag", false),
            allowReactions = sObj.optBoolean("allowReactions", true),
            allowComments = sObj.optBoolean("allowComments", true),
            inviteLink = sObj.optString("inviteLink", ""),
            isForum = isForumFinal
        )

        val uObj = optJSONObject("unreadCount")
        val uMap = mutableMapOf<String, Int>()
        uObj?.keys()?.forEach { k -> uMap[k] = uObj.optInt(k, 0) }

        val lastMessageParsed: Any? = when {
            isNull("lastMessage") -> null
            optJSONObject("lastMessage") != null -> {
                val o = getJSONObject("lastMessage")
                val map = mutableMapOf<String, Any>()
                o.keys().forEach { k ->
                    if (k == "readBy") {
                        val arr = o.optJSONArray(k)
                        map[k] = (0 until (arr?.length() ?: 0)).map { arr!!.getString(it) }
                    } else {
                        map[k] = o.get(k)
                    }
                }
                map
            }
            else -> optString("lastMessage")
        }

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
            isForum = isForumFinal,
            settings = settings,
            lastMessage = lastMessageParsed,
            lastMessageSenderId = if (isNull("lastMessageSenderId")) null else optString("lastMessageSenderId"),
            lastSeq = optLong("lastSeq", 0L),
            lastMessageAt = if (isNull("lastMessageAt")) null else com.google.firebase.Timestamp(getLong("lastMessageAt"), 0),
            createdAt = if (isNull("createdAt")) null else com.google.firebase.Timestamp(getLong("createdAt"), 0),
            unreadCount = uMap
        )
    }

    private fun Message.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("seq", seq ?: JSONObject.NULL)
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

        put("thumbUrl", thumbUrl ?: JSONObject.NULL)
        put("width", width ?: JSONObject.NULL)
        put("height", height ?: JSONObject.NULL)
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
            seq = if (has("seq") && !isNull("seq")) optLong("seq") else null,
            senderId = getString("senderId"),
            senderUsername = optString("senderUsername"),
            type = optString("type", "text"),
            text = if (isNull("text")) null else optString("text"),
            url = if (isNull("url")) null else optString("url"),
            fileName = if (isNull("fileName")) null else optString("fileName"),
            duration = if (isNull("duration")) null else optInt("duration"),
            thumbUrl = if (has("thumbUrl") && !isNull("thumbUrl")) optString("thumbUrl") else null,
            width = if (has("width") && !isNull("width")) optInt("width") else null,
            height = if (has("height") && !isNull("height")) optInt("height") else null,
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
        put("isOfficial", isOfficial)
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
            isOfficial = optBoolean("isOfficial", false),
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
        put("diaryEnabled", diaryEnabled)
        if (acceptedVersion != null) put("acceptedVersion", acceptedVersion)
        val custom = JSONObject()
        customization.forEach { (k, v) -> custom.put(k, v) }
        put("customization", custom)
    }

    private fun JSONObject.toUserProfile(): UserProfile? = try {
        val customObj = optJSONObject("customization") ?: JSONObject()
        val customMap = mutableMapOf<String, Any?>()
        customObj.keys().forEach { k -> customMap[k] = customObj.get(k) }

        UserProfile(
            uid         = getString("uid"),
            username    = optString("username"),
            displayName = optString("displayName"),
            bio         = optString("bio"),
            avatarUrl   = if (isNull("avatarUrl")) null else optString("avatarUrl"),
            online      = optBoolean("online", false),
            isAdmin     = optBoolean("isAdmin", false),
            diaryEnabled = optBoolean("diaryEnabled", false),
            acceptedVersion = if (isNull("acceptedVersion") || !has("acceptedVersion")) null else optString("acceptedVersion"),
            customization = customMap
        )
    } catch (e: Exception) { null }
}