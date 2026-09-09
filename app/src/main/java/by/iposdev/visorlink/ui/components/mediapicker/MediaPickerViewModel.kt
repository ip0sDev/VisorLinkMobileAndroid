package by.iposdev.visorlink.ui.components.mediapicker

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Фильтр для отображения медиа в галерее.
 */
enum class MediaFilter {
    ALL,
    PHOTOS_ONLY,
    VIDEOS_ONLY
}

/**
 * Локальный элемент медиа для галереи.
 */
data class LocalMediaItem(
    val id: Long,
    val uri: Uri,
    val type: MediaType,
    val durationMs: Long? = null,
    val dateAdded: Long = 0,
    val displayName: String = ""
)

/**
 * ViewModel для управления загрузкой и выбором медиафайлов через ContentResolver.
 */
class MediaPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val _mediaItems = MutableStateFlow<List<LocalMediaItem>>(emptyList())
    val mediaItems: StateFlow<List<LocalMediaItem>> = _mediaItems.asStateFlow()

    private val _selectedItems = MutableStateFlow<List<LocalMediaItem>>(emptyList())
    val selectedItems: StateFlow<List<LocalMediaItem>> = _selectedItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentFilter: MediaFilter = MediaFilter.ALL

    /**
     * Загрузка медиа из MediaStore с учетом выбранного фильтра.
     */
    fun loadMedia(filter: MediaFilter = currentFilter) {
        currentFilter = filter
        viewModelScope.launch {
            _isLoading.value = true
            val items = withContext(Dispatchers.IO) {
                queryMediaStore(filter)
            }
            _mediaItems.value = items
            _isLoading.value = false
        }
    }

    /**
     * Переключение выбора элемента.
     * Возвращает true, если элемент выбран или снят, false если превышен лимит.
     */
    fun toggleSelection(item: LocalMediaItem, maxSelection: Int = 10): Boolean {
        val current = _selectedItems.value.toMutableList()
        val index = current.indexOfFirst { it.uri == item.uri }
        return if (index >= 0) {
            current.removeAt(index)
            _selectedItems.value = current
            true
        } else {
            if (current.size >= maxSelection) {
                false
            } else {
                current.add(item)
                _selectedItems.value = current
                true
            }
        }
    }

    fun isSelected(item: LocalMediaItem): Boolean {
        return _selectedItems.value.any { it.uri == item.uri }
    }

    fun getSelectionIndex(item: LocalMediaItem): Int? {
        val idx = _selectedItems.value.indexOfFirst { it.uri == item.uri }
        return if (idx >= 0) idx + 1 else null
    }

    fun clearSelection() {
        _selectedItems.value = emptyList()
    }

    private fun queryMediaStore(filter: MediaFilter): List<LocalMediaItem> {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        val result = ArrayList<LocalMediaItem>()

        // 1. Запрос изображений
        if (filter == MediaFilter.ALL || filter == MediaFilter.PHOTOS_ONLY) {
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            val imageSortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            try {
                resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageProjection,
                    null,
                    null,
                    imageSortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

                    var count = 0
                    while (cursor.moveToNext() && count < 500) {
                        val id = cursor.getLong(idCol)
                        val dateAdded = cursor.getLong(dateCol)
                        val displayName = cursor.getString(nameCol) ?: ""
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                        result.add(
                            LocalMediaItem(
                                id = id,
                                uri = uri,
                                type = MediaType.IMAGE,
                                durationMs = null,
                                dateAdded = dateAdded,
                                displayName = displayName
                            )
                        )
                        count++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Запрос видео
        if (filter == MediaFilter.ALL || filter == MediaFilter.VIDEOS_ONLY) {
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DISPLAY_NAME
            )
            val videoSortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            try {
                resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    videoProjection,
                    null,
                    null,
                    videoSortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

                    var count = 0
                    while (cursor.moveToNext() && count < 500) {
                        val id = cursor.getLong(idCol)
                        val duration = cursor.getLong(durationCol)
                        val dateAdded = cursor.getLong(dateCol)
                        val displayName = cursor.getString(nameCol) ?: ""
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                        result.add(
                            LocalMediaItem(
                                id = id,
                                uri = uri,
                                type = MediaType.VIDEO,
                                durationMs = if (duration > 0) duration else null,
                                dateAdded = dateAdded,
                                displayName = displayName
                            )
                        )
                        count++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Сортировка по дате добавления (новые первыми)
        result.sortByDescending { it.dateAdded }
        return result
    }
}
