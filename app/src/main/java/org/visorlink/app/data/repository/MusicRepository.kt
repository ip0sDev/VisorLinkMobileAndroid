package org.visorlink.app.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import org.visorlink.app.data.model.MusicPlaylist
import org.visorlink.app.data.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val TAG = "MusicRepository"

class MusicRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbHelper = MusicDatabase(context)

    private val _dbChangeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dbChangeSignal = _dbChangeSignal.asSharedFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private fun notifyChanged() {
        _dbChangeSignal.tryEmit(Unit)
    }

    private fun triggerAutoDownload(track: MusicTrack) {
        if (track.sourceType == MusicTrack.SOURCE_DEVICE) return
        val path = track.localPath
        if (path != null && File(path).exists() && File(path).length() > 0L) return
        scope.launch {
            try {
                downloadTrackFile(track)
            } catch (e: Exception) {
                Log.e(TAG, "Auto-download failed for ${track.id}", e)
            }
        }
    }

    // ── Flow-потоки для UI ───────────────────────────────────────────────────

    fun getAllTracksFlow(): Flow<List<MusicTrack>> = callbackFlow {
        fun reload() {
            launch(Dispatchers.IO) {
                trySend(loadAllTracks())
            }
        }
        reload()
        val collector = launch {
            dbChangeSignal.collect { reload() }
        }
        awaitClose { collector.cancel() }
    }

    fun getFavoritesFlow(): Flow<List<MusicTrack>> = callbackFlow {
        fun reload() {
            launch(Dispatchers.IO) {
                trySend(loadFavorites())
            }
        }
        reload()
        val collector = launch {
            dbChangeSignal.collect { reload() }
        }
        awaitClose { collector.cancel() }
    }

    fun getPlaylistTracksFlow(playlistId: String): Flow<List<MusicTrack>> = callbackFlow {
        fun reload() {
            launch(Dispatchers.IO) {
                trySend(loadPlaylistTracks(playlistId))
            }
        }
        reload()
        val collector = launch {
            dbChangeSignal.collect { reload() }
        }
        awaitClose { collector.cancel() }
    }

    fun getPlaylistsFlow(): Flow<List<MusicPlaylist>> = callbackFlow {
        fun reload() {
            launch(Dispatchers.IO) {
                trySend(loadPlaylists())
            }
        }
        reload()
        val collector = launch {
            dbChangeSignal.collect { reload() }
        }
        awaitClose { collector.cancel() }
    }

    // ── Запросы к БД (IO) ────────────────────────────────────────────────────

    suspend fun loadAllTracks(): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM music_tracks ORDER BY added_at DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                tracks.add(cursorToTrack(it))
            }
        }
        tracks
    }

    suspend fun loadFavorites(): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT t.* FROM music_tracks t
            INNER JOIN playlist_tracks pt ON t.id = pt.track_id
            WHERE pt.playlist_id = '${MusicPlaylist.FAVORITES_ID}'
            ORDER BY pt.added_at DESC
        """.trimIndent()
        val cursor = db.rawQuery(query, null)
        cursor.use {
            while (it.moveToNext()) {
                tracks.add(cursorToTrack(it))
            }
        }
        tracks
    }

    suspend fun loadPlaylistTracks(playlistId: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT t.* FROM music_tracks t
            INNER JOIN playlist_tracks pt ON t.id = pt.track_id
            WHERE pt.playlist_id = ?
            ORDER BY pt.added_at DESC
        """.trimIndent()
        val cursor = db.rawQuery(query, arrayOf(playlistId))
        cursor.use {
            while (it.moveToNext()) {
                tracks.add(cursorToTrack(it))
            }
        }
        tracks
    }

    suspend fun loadPlaylists(): List<MusicPlaylist> = withContext(Dispatchers.IO) {
        val playlists = mutableListOf<MusicPlaylist>()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT p.*, COUNT(pt.track_id) AS track_count
            FROM music_playlists p
            LEFT JOIN playlist_tracks pt ON p.id = pt.playlist_id
            GROUP BY p.id
            ORDER BY p.is_default DESC, p.created_at ASC
        """.trimIndent()
        val cursor = db.rawQuery(query, null)
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow("id"))
                val title = it.getString(it.getColumnIndexOrThrow("title"))
                val icon = it.getString(it.getColumnIndexOrThrow("icon")) ?: "🎵"
                val isDefault = it.getInt(it.getColumnIndexOrThrow("is_default")) == 1
                val createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                val trackCount = it.getInt(it.getColumnIndexOrThrow("track_count"))
                playlists.add(
                    MusicPlaylist(
                        id = id,
                        title = title,
                        icon = icon,
                        trackCount = trackCount,
                        isDefault = isDefault,
                        createdAt = createdAt
                    )
                )
            }
        }
        playlists
    }

    suspend fun saveTrack(track: MusicTrack): Boolean = withContext(Dispatchers.IO) {
        val resolvedUrl = org.visorlink.app.data.model.resolveStreamableAudioUrl(track.url, track.cdnMediaId) ?: track.url
        val normalizedTrack = if (resolvedUrl != track.url) track.copy(url = resolvedUrl) else track
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", normalizedTrack.id)
            put("title", normalizedTrack.title)
            put("performer", normalizedTrack.performer)
            put("duration", normalizedTrack.duration)
            put("file_size", normalizedTrack.fileSize)
            put("url", normalizedTrack.url)
            put("cdn_media_id", normalizedTrack.cdnMediaId)
            put("local_path", normalizedTrack.localPath)
            put("cover_url", normalizedTrack.coverUrl)
            put("cover_cdn_media_id", normalizedTrack.coverCdnMediaId)
            put("cover_local_path", normalizedTrack.coverLocalPath)
            put("is_favorite", if (normalizedTrack.isFavorite) 1 else 0)
            put("source_type", normalizedTrack.sourceType)
            put("chat_id", normalizedTrack.chatId)
            put("message_id", normalizedTrack.messageId)
            put("added_at", normalizedTrack.addedAt)
        }
        val result = db.insertWithOnConflict("music_tracks", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        if (result != -1L) {
            notifyChanged()
            triggerAutoDownload(normalizedTrack)
            true
        } else {
            false
        }
    }

    suspend fun deleteTrack(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("playlist_tracks", "track_id = ?", arrayOf(trackId))
        val count = db.delete("music_tracks", "id = ?", arrayOf(trackId))
        if (count > 0) {
            notifyChanged()
            true
        } else {
            false
        }
    }

    suspend fun isTrackFavorite(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM playlist_tracks WHERE playlist_id = ? AND track_id = ? LIMIT 1",
            arrayOf(MusicPlaylist.FAVORITES_ID, trackId)
        )
        cursor.use { it.moveToFirst() }
    }

    suspend fun toggleFavorite(track: MusicTrack): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val isFav = isTrackFavorite(track.id)
        if (isFav) {
            // Удаляем из Избранного
            db.delete("playlist_tracks", "playlist_id = ? AND track_id = ?", arrayOf(MusicPlaylist.FAVORITES_ID, track.id))
            db.execSQL("UPDATE music_tracks SET is_favorite = 0 WHERE id = ?", arrayOf(track.id))
            notifyChanged()
            false
        } else {
            // Убеждаемся, что трек сохранен в БД
            saveTrack(track.copy(isFavorite = true))
            val values = ContentValues().apply {
                put("playlist_id", MusicPlaylist.FAVORITES_ID)
                put("track_id", track.id)
                put("added_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict("playlist_tracks", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            db.execSQL("UPDATE music_tracks SET is_favorite = 1 WHERE id = ?", arrayOf(track.id))
            notifyChanged()
            true
        }
    }

    suspend fun createPlaylist(title: String, icon: String = "🎵"): MusicPlaylist = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val playlist = MusicPlaylist(
            id = id,
            title = title,
            icon = icon,
            trackCount = 0,
            isDefault = false,
            createdAt = System.currentTimeMillis()
        )
        val values = ContentValues().apply {
            put("id", playlist.id)
            put("title", playlist.title)
            put("icon", playlist.icon)
            put("is_default", 0)
            put("created_at", playlist.createdAt)
        }
        dbHelper.writableDatabase.insert("music_playlists", null, values)
        notifyChanged()
        playlist
    }

    suspend fun deletePlaylist(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        if (playlistId == MusicPlaylist.FAVORITES_ID) return@withContext false // Запрет удаления системного плейлиста
        val db = dbHelper.writableDatabase
        db.delete("playlist_tracks", "playlist_id = ?", arrayOf(playlistId))
        val count = db.delete("music_playlists", "id = ?", arrayOf(playlistId))
        if (count > 0) {
            notifyChanged()
            true
        } else {
            false
        }
    }

    suspend fun addTrackToPlaylist(playlistId: String, track: MusicTrack): Boolean = withContext(Dispatchers.IO) {
        saveTrack(track)
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("playlist_id", playlistId)
            put("track_id", track.id)
            put("added_at", System.currentTimeMillis())
        }
        val result = db.insertWithOnConflict("playlist_tracks", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        if (result != -1L) {
            if (playlistId == MusicPlaylist.FAVORITES_ID) {
                db.execSQL("UPDATE music_tracks SET is_favorite = 1 WHERE id = ?", arrayOf(track.id))
            }
            notifyChanged()
            true
        } else {
            false
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val count = db.delete("playlist_tracks", "playlist_id = ? AND track_id = ?", arrayOf(playlistId, trackId))
        if (count > 0) {
            if (playlistId == MusicPlaylist.FAVORITES_ID) {
                db.execSQL("UPDATE music_tracks SET is_favorite = 0 WHERE id = ?", arrayOf(trackId))
            }
            notifyChanged()
            true
        } else {
            false
        }
    }

    // ── Сканирование устройства (MediaStore) ──────────────────────────────────

    suspend fun getDeviceTracks(): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor = context.contentResolver.query(collection, projection, selection, null, sortOrder)
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (it.moveToNext()) {
                    val mediaId = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Без названия"
                    val artist = it.getString(artistCol) ?: "Неизвестный исполнитель"
                    val durationMs = it.getInt(durCol)
                    val size = it.getLong(sizeCol)
                    val path = it.getString(dataCol)
                    val contentUri = ContentUris.withAppendedId(collection, mediaId)

                    tracks.add(
                        MusicTrack(
                            id = "device_$mediaId",
                            title = title,
                            performer = artist,
                            duration = durationMs / 1000,
                            fileSize = size,
                            url = contentUri.toString(),
                            localPath = path,
                            sourceType = MusicTrack.SOURCE_DEVICE,
                            addedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan device audio", e)
        }
        tracks
    }

    // ── Скачивание аудиофайла в постоянный кэш приложения ────────────────────

    suspend fun downloadTrackFile(track: MusicTrack): MusicTrack = withContext(Dispatchers.IO) {
        if (track.sourceType == MusicTrack.SOURCE_DEVICE) return@withContext track
        if (track.localPath != null && File(track.localPath).exists() && File(track.localPath).length() > 0L) {
            return@withContext track
        }

        val dir = File(context.filesDir, "music")
        if (!dir.exists()) dir.mkdirs()

        val safeId = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        // Проверяем, может файл уже есть на диске в каталоге музыки
        val existingCachedFile = dir.listFiles()?.firstOrNull { it.name.startsWith("track_${safeId}.") && it.length() > 0L }
        if (existingCachedFile != null) {
            val updatedTrack = track.copy(
                localPath = existingCachedFile.absolutePath,
                fileSize = existingCachedFile.length()
            )
            saveTrack(updatedTrack)
            return@withContext updatedTrack
        }

        val rawUrl = track.url
        val downloadUrl = org.visorlink.app.data.model.resolveStreamableAudioUrl(rawUrl, track.cdnMediaId)
        if (downloadUrl.isNullOrBlank()) {
            Log.w(TAG, "No valid download URL for track ${track.id}")
            return@withContext track
        }

        try {
            _downloadProgress.update { it + (track.id to 0.01f) }

            val ext = downloadUrl.substringBefore('?').substringAfterLast('.', "mp3")
                .take(5).filter { it.isLetterOrDigit() }.ifEmpty { "mp3" }
            val fileName = "track_${safeId}.$ext"
            val targetFile = File(dir, fileName)

            if (!targetFile.exists() || targetFile.length() == 0L) {
                val tmpFile = File(dir, "$fileName.tmp")
                var currentUrl = downloadUrl
                var activeConn: HttpURLConnection? = null
                var redirectCount = 0
                val maxRedirects = 6

                while (redirectCount < maxRedirects) {
                    val urlObj = URL(currentUrl)
                    val c = (urlObj.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000
                        readTimeout = 60_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                    }
                    c.connect()
                    val code = c.responseCode
                    if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_SEE_OTHER, 307, 308)) {
                        val redirectLocation = c.getHeaderField("Location")
                        c.disconnect()
                        if (!redirectLocation.isNullOrBlank()) {
                            currentUrl = if (redirectLocation.startsWith("http")) redirectLocation else URL(urlObj, redirectLocation).toString()
                            redirectCount++
                            continue
                        }
                    }
                    activeConn = c
                    break
                }

                val conn = activeConn ?: throw IOException("Failed to connect to $downloadUrl")
                try {
                    val responseCode = conn.responseCode
                    if (responseCode !in 200..299) {
                        throw IOException("HTTP error $responseCode: ${conn.responseMessage}")
                    }

                    val totalBytes = conn.contentLengthLong
                    var downloadedBytes = 0L
                    var lastReported = 0f

                    FileOutputStream(tmpFile).use { out ->
                        conn.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                                downloadedBytes += read
                                if (totalBytes > 0L) {
                                    val prog = (downloadedBytes.toFloat() / totalBytes).coerceIn(0.01f, 0.99f)
                                    if (prog - lastReported >= 0.02f) {
                                        lastReported = prog
                                        _downloadProgress.update { it + (track.id to prog) }
                                    }
                                }
                            }
                        }
                    }
                    if (tmpFile.exists() && tmpFile.length() > 0L) {
                        if (targetFile.exists()) targetFile.delete()
                        tmpFile.renameTo(targetFile)
                    }
                } finally {
                    conn.disconnect()
                    if (tmpFile.exists()) tmpFile.delete()
                }
            }

            if (targetFile.exists() && targetFile.length() > 0L) {
                _downloadProgress.update { it + (track.id to 1.0f) }
                val updatedTrack = track.copy(
                    localPath = targetFile.absolutePath,
                    fileSize = targetFile.length(),
                    url = downloadUrl
                )
                saveTrack(updatedTrack)
                _downloadProgress.update { it - track.id }
                return@withContext updatedTrack
            } else {
                Log.e(TAG, "Target file does not exist after download for ${track.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download track ${track.id} from $downloadUrl", e)
        } finally {
            _downloadProgress.update { it - track.id }
        }
        track
    }

    private fun cursorToTrack(cursor: Cursor): MusicTrack {
        return MusicTrack(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            performer = cursor.getString(cursor.getColumnIndexOrThrow("performer")),
            duration = cursor.getInt(cursor.getColumnIndexOrThrow("duration")),
            fileSize = cursor.getLong(cursor.getColumnIndexOrThrow("file_size")),
            url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
            cdnMediaId = cursor.getString(cursor.getColumnIndexOrThrow("cdn_media_id")),
            localPath = cursor.getString(cursor.getColumnIndexOrThrow("local_path")),
            coverUrl = cursor.getString(cursor.getColumnIndexOrThrow("cover_url")),
            coverCdnMediaId = cursor.getString(cursor.getColumnIndexOrThrow("cover_cdn_media_id")),
            coverLocalPath = cursor.getString(cursor.getColumnIndexOrThrow("cover_local_path")),
            isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")) == 1,
            sourceType = cursor.getString(cursor.getColumnIndexOrThrow("source_type")) ?: MusicTrack.SOURCE_CHAT,
            chatId = cursor.getString(cursor.getColumnIndexOrThrow("chat_id")),
            messageId = cursor.getString(cursor.getColumnIndexOrThrow("message_id")),
            addedAt = cursor.getLong(cursor.getColumnIndexOrThrow("added_at"))
        )
    }
}
