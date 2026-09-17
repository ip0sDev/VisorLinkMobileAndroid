package org.visorlink.app.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.visorlink.app.data.model.MusicPlaylist

private const val DB_NAME = "visorlink_music.db"
private const val DB_VERSION = 1

class MusicDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE music_tracks (
                id TEXT PRIMARY KEY,
                title TEXT,
                performer TEXT,
                duration INTEGER DEFAULT 0,
                file_size INTEGER DEFAULT 0,
                url TEXT,
                cdn_media_id TEXT,
                local_path TEXT,
                cover_url TEXT,
                cover_cdn_media_id TEXT,
                cover_local_path TEXT,
                is_favorite INTEGER DEFAULT 0,
                source_type TEXT,
                chat_id TEXT,
                message_id TEXT,
                added_at INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE music_playlists (
                id TEXT PRIMARY KEY,
                title TEXT,
                icon TEXT DEFAULT '🎵',
                is_default INTEGER DEFAULT 0,
                created_at INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE playlist_tracks (
                playlist_id TEXT,
                track_id TEXT,
                added_at INTEGER,
                PRIMARY KEY (playlist_id, track_id)
            )
            """.trimIndent()
        )

        // Добавляем системный плейлист по умолчанию — "Понравившиеся"
        db.execSQL(
            """
            INSERT OR IGNORE INTO music_playlists (id, title, icon, is_default, created_at)
            VALUES ('${MusicPlaylist.FAVORITES_ID}', 'Понравившиеся', '❤️', 1, ${System.currentTimeMillis()})
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Миграции для последующих версий при необходимости
    }
}
