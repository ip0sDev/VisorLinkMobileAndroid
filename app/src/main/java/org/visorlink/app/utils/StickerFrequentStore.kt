package org.visorlink.app.utils

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.visorlink.app.data.model.StickerItem
import org.visorlink.app.data.model.StickerPack

/** Элемент истории регулярного использования стикера (порт Flutter `FrequentSticker`). */
data class FrequentSticker(
    val sticker: StickerItem,
    val packId: String,
    val packName: String,
    val packEmoji: String,
    val useCount: Int,
    val lastUsedTimestamp: Long,
) {
    /**
     * Показатель регулярности и актуальности (Frecency): чем регулярнее и ближе по времени
     * использован стикер, тем выше балл.
     */
    val score: Double
        get() {
            val now = System.currentTimeMillis()
            val diffHours = ((now - lastUsedTimestamp) / (1000.0 * 3600.0)).coerceIn(0.0, 720.0)
            val recencyFactor = 1.0 / (1.0 + diffHours / 24.0) // Затухание за дни
            return (useCount * 2.0) + (recencyFactor * 5.0)
        }

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", sticker.id)
        put("url", sticker.url)
        put("emoji", sticker.emoji)
        put("storagePath", sticker.storagePath)
        put("format", sticker.format ?: JSONObject.NULL)
        put("previewUrl", sticker.previewUrl ?: JSONObject.NULL)
        put("packId", packId)
        put("packName", packName)
        put("packEmoji", packEmoji)
        put("useCount", useCount)
        put("lastUsedTimestamp", lastUsedTimestamp)
    }

    companion object {
        fun fromJsonObject(map: JSONObject): FrequentSticker = FrequentSticker(
            sticker = StickerItem(
                id = map.optString("id"),
                url = map.optString("url"),
                emoji = map.optString("emoji", "🎭"),
                storagePath = map.optString("storagePath"),
                format = map.optString("format").takeIf { map.has("format") && !map.isNull("format") },
                previewUrl = map.optString("previewUrl").takeIf { map.has("previewUrl") && !map.isNull("previewUrl") },
            ),
            packId = map.optString("packId"),
            packName = map.optString("packName"),
            packEmoji = map.optString("packEmoji", "✨"),
            useCount = map.optInt("useCount", 1),
            lastUsedTimestamp = map.optLong("lastUsedTimestamp", System.currentTimeMillis()),
        )
    }
}

/**
 * Учёт регулярности использования стикеров (порт Flutter `StickerUsageRepository`):
 * история в SharedPreferences, ранжирование по частоте и давности (Frecency).
 */
class StickerFrequentStore(context: Context) {

    private val prefs = context.getSharedPreferences("vl_sticker_usage", Context.MODE_PRIVATE)

    private var itemsInternal by mutableStateOf<List<FrequentSticker>>(emptyList())

    /** Для Compose: отсортированный список, обновляется при каждой записи. */
    val items: List<FrequentSticker> get() = itemsInternal

    init {
        loadFromStorage()
    }

    /** Отсортированный топ часто используемых стикеров. */
    fun frequent(limit: Int = 28): List<FrequentSticker> = itemsInternal.take(limit)

    /** Записать отправку стикера для ранжирования регулярности. */
    fun record(sticker: StickerItem, pack: StickerPack) {
        if (sticker.url.isEmpty()) return
        val existingIndex = itemsInternal.indexOfFirst {
            it.sticker.id == sticker.id || (it.sticker.url == sticker.url && it.packId == pack.id)
        }
        val now = System.currentTimeMillis()
        val updated = itemsInternal.toMutableList()
        if (existingIndex >= 0) {
            val existing = updated[existingIndex]
            updated[existingIndex] = existing.copy(useCount = existing.useCount + 1, lastUsedTimestamp = now)
        } else {
            updated.add(
                FrequentSticker(
                    sticker = sticker,
                    packId = pack.id,
                    packName = pack.name,
                    packEmoji = pack.emoji,
                    useCount = 1,
                    lastUsedTimestamp = now,
                )
            )
        }
        itemsInternal = sortItems(updated)
        saveToStorage(itemsInternal)
    }

    private fun sortItems(items: List<FrequentSticker>): List<FrequentSticker> =
        items.sortedWith(compareByDescending<FrequentSticker> { it.score }.thenByDescending { it.lastUsedTimestamp })

    private fun loadFromStorage() {
        try {
            val raw = prefs.getString(STORAGE_KEY, null)
            if (raw != null && raw.isNotEmpty()) {
                val decoded = JSONArray(raw)
                itemsInternal = sortItems(
                    (0 until decoded.length())
                        .map { FrequentSticker.fromJsonObject(decoded.getJSONObject(it)) }
                        .filter { it.sticker.url.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to load frequent stickers: ${e.message}")
        }
    }

    private fun saveToStorage(items: List<FrequentSticker>) {
        try {
            val array = JSONArray()
            items.take(MAX_STORED_ITEMS).forEach { array.put(it.toJsonObject()) }
            prefs.edit().putString(STORAGE_KEY, array.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "failed to save frequent stickers: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "StickerFrequentStore"
        private const val STORAGE_KEY = "vl_frequent_stickers_v1"
        private const val MAX_STORED_ITEMS = 120
    }
}
