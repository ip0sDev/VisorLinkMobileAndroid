package by.iposdev.visorlink.data.model

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
