package by.iposdev.visorlink.utils

import android.content.Context
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "AppImageLoader"

/**
 * Singleton-обёртка над Coil ImageLoader.
 * Инициализируй один раз в Application.onCreate() через init().
 * При изменении настроек кэша вызывай reinit().
 */
object AppImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    /**
     * Инициализация — вызывать в Application.onCreate()
     */
    fun init(context: Context, config: CacheConfig = CacheConfig()) {
        val loader = buildLoader(context, config)
        instance = loader
        Coil.setImageLoader(loader)
        Log.d(TAG, "ImageLoader initialized: images=${config.maxImageMb}MB")
    }

    /**
     * Пересоздать загрузчик с новым конфигом (напр. после изменения лимита в настройках)
     */
    fun reinit(context: Context, config: CacheConfig) {
        instance?.shutdown()
        init(context, config)
    }

    /**
     * Сбросить memory cache (вызывается из CacheManager.clearImages())
     */
    fun clearMemoryCache(context: Context) {
        instance?.memoryCache?.clear()
        Log.d(TAG, "Memory cache cleared")
    }

    fun get(context: Context, config: CacheConfig = CacheConfig()): ImageLoader =
        instance ?: buildLoader(context, config).also {
            instance = it
            Coil.setImageLoader(it)
        }

    // ── Построение ImageLoader ────────────────────────────────────────────────

    private fun buildLoader(context: Context, config: CacheConfig = CacheConfig()): ImageLoader {
        val diskCacheDir = File(context.cacheDir, CACHE_DIR_IMAGES)

        val okhttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Принудительно кешируем даже ответы без Cache-Control (Firebase Storage)
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=2592000") // 30 дней
                    .build()
            }
            .build()

        return ImageLoader.Builder(context)
            .components {
                // Добавляем декодер для поддержки превью видео и GIF
                add(VideoFrameDecoder.Factory())
                add(ImageDecoderDecoder.Factory())
            }
            .okHttpClient(okhttp)
            // Disk cache — для картинок (аватарки, фото из чатов)
            .diskCache {
                val size = if (config.isUnlimited) Long.MAX_VALUE else config.maxImageMb.toLong() * 1024 * 1024
                DiskCache.Builder()
                    .directory(diskCacheDir)
                    .maxSizeBytes(size)
                    .build()
            }
            // Memory cache — быстрый показ уже открытых картинок
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20) // 20% от RAM приложения
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            // Crossfade по умолчанию для всех изображений
            .crossfade(true)
            .crossfade(300)
            .build()
    }
}