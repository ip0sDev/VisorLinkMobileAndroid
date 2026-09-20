package org.visorlink.app.ui.screens.chat

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "WaveformExtractor"
private const val BAR_COUNT = 40  // кол-во баров в спектрограмме

// Глобальный кеш: url → нормализованные амплитуды [0f..1f]
private val waveformCache = java.util.concurrent.ConcurrentHashMap<String, List<Float>>()

/**
 * Скачивает аудио, декодирует PCM через MediaCodec,
 * сэмплирует в BAR_COUNT амплитуд и кеширует результат.
 */
suspend fun extractWaveform(context: Context, url: String): List<Float> {
    waveformCache[url]?.let { return it }

    return withContext(Dispatchers.IO) {
        try {
            val file = downloadToCache(context, url)
            val amplitudes = decodeToAmplitudes(file.absolutePath)
            waveformCache[url] = amplitudes
            amplitudes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract waveform for $url", e)
            // Фоллбек — синусоидная "заглушка" вместо фейкового плоского ряда
            generateFallbackWaveform()
        }
    }
}

// ── Скачивание в кеш-файл ────────────────────────────────────────────────────

private fun downloadToCache(context: Context, url: String): File {
    val fileName = "waveform_${url.hashCode()}.tmp"
    val file = File(context.cacheDir, fileName)
    if (file.exists() && file.length() > 0) return file

    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 15_000
    connection.connect()

    FileOutputStream(file).use { out ->
        connection.inputStream.use { input -> input.copyTo(out) }
    }
    connection.disconnect()
    return file
}

// ── Декодирование PCM через MediaCodec ───────────────────────────────────────

private fun decodeToAmplitudes(filePath: String): List<Float> {
    val extractor = MediaExtractor()
    extractor.setDataSource(filePath)

    // Ищем аудиодорожку
    var audioTrackIndex = -1
    var format: MediaFormat? = null
    for (i in 0 until extractor.trackCount) {
        val fmt = extractor.getTrackFormat(i)
        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith("audio/")) {
            audioTrackIndex = i
            format = fmt
            break
        }
    }

    if (audioTrackIndex < 0 || format == null) {
        extractor.release()
        return generateFallbackWaveform()
    }

    extractor.selectTrack(audioTrackIndex)

    val mime = format.getString(MediaFormat.KEY_MIME)!!
    val codec = MediaCodec.createDecoderByType(mime)
    codec.configure(format, null, null, 0)
    codec.start()

    val rawSamples = mutableListOf<Short>()
    val bufferInfo = MediaCodec.BufferInfo()
    var inputDone = false
    var outputDone = false

    try {
        while (!outputDone) {
            // Подаём входные буферы
            if (!inputDone) {
                val inputIdx = codec.dequeueInputBuffer(10_000)
                if (inputIdx >= 0) {
                    val buf = codec.getInputBuffer(inputIdx)!!
                    val sampleSize = extractor.readSampleData(buf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIdx, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIdx, 0, sampleSize,
                            extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // Читаем декодированный PCM
            val outputIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIdx >= 0) {
                val buf = codec.getOutputBuffer(outputIdx)!!
                buf.order(ByteOrder.LITTLE_ENDIAN)
                while (buf.remaining() >= 2) {
                    rawSamples.add(buf.short)
                }
                codec.releaseOutputBuffer(outputIdx, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }

            // Защита от бесконечного цикла на битых файлах
            if (rawSamples.size > 10_000_000) break
        }
    } finally {
        codec.stop()
        codec.release()
        extractor.release()
    }

    return samplesToAmplitudes(rawSamples)
}

// ── Сэмплирование в BAR_COUNT амплитуд ───────────────────────────────────────

private fun samplesToAmplitudes(samples: List<Short>): List<Float> {
    if (samples.isEmpty()) return generateFallbackWaveform()

    val chunkSize = (samples.size / BAR_COUNT).coerceAtLeast(1)
    val amplitudes = mutableListOf<Float>()

    for (i in 0 until BAR_COUNT) {
        val start = i * chunkSize
        val end = minOf(start + chunkSize, samples.size)
        if (start >= samples.size) {
            amplitudes.add(0f)
            continue
        }
        // RMS (root mean square) — ближе к тому как слышит ухо
        var sum = 0.0
        for (j in start until end) {
            val s = samples[j].toDouble() / Short.MAX_VALUE
            sum += s * s
        }
        amplitudes.add(sqrt(sum / (end - start)).toFloat())
    }

    // Нормализуем к [0..1]
    val maxAmp = amplitudes.maxOrNull()?.takeIf { it > 0f } ?: 1f
    return amplitudes.map { (it / maxAmp).coerceIn(0.05f, 1f) }
}

// ── Фоллбек — красивая синусоидная заглушка ───────────────────────────────────

fun generateFallbackWaveform(): List<Float> {
    return (0 until BAR_COUNT).map { i ->
        val base = 0.3f + 0.5f * kotlin.math.sin(i * 0.45f).toFloat().let { abs(it) }
        val noise = (kotlin.math.sin(i * 2.3f + 1.1f) * 0.15f).toFloat()
        (base + noise).coerceIn(0.1f, 1f)
    }
}