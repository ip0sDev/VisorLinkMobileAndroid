package by.iposdev.visorlink.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "CacheManager"
private const val PREFS_NAME = "cache_settings"
private const val KEY_MAX_IMAGE_MB = "max_image_mb"
private const val KEY_MAX_VOICE_MB = "max_voice_mb"
private const val KEY_MAX_WAVEFORM_MB = "max_waveform_mb"
private const val KEY_CHAT_CACHE_DAYS = "chat_cache_days"

// Имена папок в cacheDir
const val CACHE_DIR_IMAGES   = "coil_cache"        // Coil пишет сюда сам
const val CACHE_DIR_VOICE    = "voice_cache"
const val CACHE_DIR_WAVEFORM = "waveform_cache"
const val CACHE_DIR_CHAT     = "chat_data_cache"

data class CacheConfig(
    val maxImageMb: Int    = 150,
    val maxVoiceMb: Int    = 100,
    val maxWaveformMb: Int = 10,
    val chatCacheDays: Int = 7
)

data class CacheSizeInfo(
    val imagesMb: Float,
    val voiceMb: Float,
    val waveformMb: Float,
    val chatMb: Float,
    val totalMb: Float
)

class CacheManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Конфигурация ──────────────────────────────────────────────────────────

    fun loadConfig(): CacheConfig = CacheConfig(
        maxImageMb    = prefs.getInt(KEY_MAX_IMAGE_MB, 150),
        maxVoiceMb    = prefs.getInt(KEY_MAX_VOICE_MB, 100),
        maxWaveformMb = prefs.getInt(KEY_MAX_WAVEFORM_MB, 10),
        chatCacheDays = prefs.getInt(KEY_CHAT_CACHE_DAYS, 7)
    )

    fun saveConfig(config: CacheConfig) {
        prefs.edit()
            .putInt(KEY_MAX_IMAGE_MB, config.maxImageMb)
            .putInt(KEY_MAX_VOICE_MB, config.maxVoiceMb)
            .putInt(KEY_MAX_WAVEFORM_MB, config.maxWaveformMb)
            .putInt(KEY_CHAT_CACHE_DAYS, config.chatCacheDays)
            .apply()
    }

    // ── Подсчёт размера ───────────────────────────────────────────────────────

    suspend fun getCacheSizes(): CacheSizeInfo = withContext(Dispatchers.IO) {
        val images   = dirSizeMb(File(context.cacheDir, CACHE_DIR_IMAGES))
        val voice    = dirSizeMb(File(context.cacheDir, CACHE_DIR_VOICE))
        val waveform = dirSizeMb(File(context.cacheDir, CACHE_DIR_WAVEFORM))
        val chat     = dirSizeMb(File(context.cacheDir, CACHE_DIR_CHAT)) + (context.getDatabasePath("visorlink_cache.db").length().toFloat() / (1024 * 1024))
        CacheSizeInfo(
            imagesMb   = images,
            voiceMb    = voice,
            waveformMb = waveform,
            chatMb     = chat,
            totalMb    = images + voice + waveform + chat
        )
    }

    // ── Очистка ───────────────────────────────────────────────────────────────

    suspend fun clearImages() = withContext(Dispatchers.IO) {
        clearDir(File(context.cacheDir, CACHE_DIR_IMAGES))
        AppImageLoader.clearMemoryCache(context)
        Log.d(TAG, "Image cache cleared")
    }

    suspend fun clearVoice() = withContext(Dispatchers.IO) {
        clearDir(File(context.cacheDir, CACHE_DIR_VOICE))
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("waveform_") && it.name.endsWith(".tmp") }
            ?.forEach { it.delete() }
        Log.d(TAG, "Voice cache cleared")
    }

    suspend fun clearWaveforms() = withContext(Dispatchers.IO) {
        clearDir(File(context.cacheDir, CACHE_DIR_WAVEFORM))
        Log.d(TAG, "Waveform cache cleared")
    }

    suspend fun clearChatData() = withContext(Dispatchers.IO) {
        ChatDataCache.clearAll(context) // Очищаем SQLite
        clearDir(File(context.cacheDir, CACHE_DIR_CHAT)) // Очищаем старую папку (если осталась)
        Log.d(TAG, "Chat data cache cleared")
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        clearImages()
        clearVoice()
        clearWaveforms()
        clearChatData()
        Log.d(TAG, "All caches cleared")
    }

    // ── Автоочистка по LRU при превышении лимита ──────────────────────────────

    suspend fun evictIfNeeded() = withContext(Dispatchers.IO) {
        val config = loadConfig()
        evictDir(File(context.cacheDir, CACHE_DIR_VOICE), config.maxVoiceMb.toLong())
        evictDir(File(context.cacheDir, CACHE_DIR_WAVEFORM), config.maxWaveformMb.toLong())
        evictDir(File(context.cacheDir, CACHE_DIR_CHAT), 50L)
    }

    private fun evictDir(dir: File, maxMb: Long) {
        if (!dir.exists()) return
        val maxBytes = maxMb * 1024 * 1024
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var totalBytes = files.sumOf { it.length() }
        for (file in files) {
            if (totalBytes <= maxBytes) break
            totalBytes -= file.length()
            file.delete()
        }
    }

    private fun dirSizeMb(dir: File): Float {
        if (!dir.exists()) return 0f
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
            .toFloat() / (1024 * 1024)
    }

    private fun clearDir(dir: File) {
        if (!dir.exists()) return
        dir.walkBottomUp().forEach { file ->
            if (file != dir) file.delete()
        }
    }
}