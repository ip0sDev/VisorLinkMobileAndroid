package org.visorlink.app.data.model

data class MusicTrack(
    val id: String,
    val title: String,
    val performer: String,
    val duration: Int = 0,             // в секундах
    val fileSize: Long = 0L,           // в байтах
    val url: String? = null,           // удаленный URL
    val cdnMediaId: String? = null,    // CDN media ID
    val localPath: String? = null,     // локальный путь к файлу
    val coverUrl: String? = null,
    val coverCdnMediaId: String? = null,
    val coverLocalPath: String? = null,
    val isFavorite: Boolean = false,
    val sourceType: String = SOURCE_CHAT, // "chat", "device", "import"
    val chatId: String? = null,
    val messageId: String? = null,
    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_CHAT = "chat"
        const val SOURCE_DEVICE = "device"
        const val SOURCE_IMPORT = "import"
    }

    val displayTitle: String get() = title.ifBlank { "Без названия" }
    val displayPerformer: String get() = performer.ifBlank { "Неизвестный исполнитель" }

    fun getEffectiveStreamUrl(): String? = resolveStreamableAudioUrl(url, cdnMediaId)
}

/**
 * Извлекает Google Drive file ID из произвольного URL (lh3, drive.google.com, id=...).
 */
fun extractGoogleDriveFileId(url: String): String? {
    if (url.isBlank()) return null
    return when {
        url.contains("googleusercontent.com/d/") -> {
            url.substringAfter("googleusercontent.com/d/").substringBefore("=").substringBefore("/").substringBefore("?")
        }
        url.contains("drive.google.com/file/d/") -> {
            url.substringAfter("drive.google.com/file/d/").substringBefore("/").substringBefore("?")
        }
        url.contains("id=") -> {
            url.substringAfter("id=").substringBefore("&")
        }
        else -> null
    }?.trim()?.takeIf { it.isNotBlank() }
}

/**
 * Преобразует любую ссылку (включая непрямые ссылки Google Drive и превью lh3)
 * в прямую ссылку на потоковое воспроизведение и скачивание.
 */
fun resolveStreamableAudioUrl(url: String?, driveFileId: String? = null): String? {
    val cleanUrl = url?.trim()?.ifBlank { null }
    val effectiveDriveId = driveFileId?.trim()?.ifBlank { null }

    val fileId = effectiveDriveId ?: cleanUrl?.let { extractGoogleDriveFileId(it) }
    if (fileId != null) {
        return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
    }
    return cleanUrl
}

data class MusicPlaylist(
    val id: String,
    val title: String,
    val icon: String = "🎵",
    val trackCount: Int = 0,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val FAVORITES_ID = "favorites"
    }
}

enum class MusicRepeatMode {
    OFF, ALL, ONE
}

