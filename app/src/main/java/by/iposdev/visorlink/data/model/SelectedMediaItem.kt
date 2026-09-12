package by.iposdev.visorlink.data.model

import android.net.Uri

/**
 * Тип медиафайла для медиа-пикера.
 */
enum class MediaType {
    IMAGE,
    VIDEO
}

/**
 * Выбранный элемент в медиа-пикере.
 */
data class SelectedMediaItem(
    val uri: Uri,
    val type: MediaType,
    val durationMs: Long? = null
)
