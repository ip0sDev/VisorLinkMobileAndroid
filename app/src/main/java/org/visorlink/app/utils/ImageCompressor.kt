package org.visorlink.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Сжатие картинок перед загрузкой в Storage.
 *
 * Статика: уменьшаем до [Profile.maxSide] (с учётом EXIF) и перекодируем в JPEG / WebP.
 * GIF: уменьшаем кадры, режем частоту кадров и пишем заново с дельта-кадрами.
 * Если результат не меньше оригинала (или GIF не разобрался), отдаём оригинал как есть.
 */
object ImageCompressor {

    class Profile(
        val maxSide: Int = 1920,
        val quality: Int = 82,
        val gifMaxSide: Int = 640,
        val gifMaxFps: Int = 15,
        val gifMaxFrames: Int = 120,
        /** true: GIF превращается в статичный первый кадр (аватар, обои чата) */
        val staticOnly: Boolean = false,
        /** true: статика всегда JPEG (без альфы), путь в Storage остаётся «.jpg» */
        val forceJpeg: Boolean = false,
    )

    class Result(val bytes: ByteArray, val mimeType: String) {
        val extension: String
            get() = when (mimeType) {
                "image/gif" -> "gif"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
    }

    suspend fun compress(context: Context, uri: Uri, profile: Profile = Profile()): Result =
        withContext(Dispatchers.IO) {
            val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Не удалось прочитать файл")
            val mime = context.contentResolver.getType(uri)
            compressBytes(context, uri, original, mime, profile)
        }

    private fun compressBytes(context: Context, uri: Uri, original: ByteArray, mime: String?, profile: Profile): Result {
        val isGif = isGif(original)
        val originalMime = if (isGif) "image/gif" else mime ?: "image/jpeg"
        val fallback = Result(original, originalMime)

        if (isGif && !profile.staticOnly) {
            val gif = runCatching { recompressGif(original, profile) }.getOrNull()
            return if (gif != null && gif.size < original.size) Result(gif, "image/gif") else fallback
        }

        val bitmap = runCatching { decodeScaled(context, uri, profile.maxSide) }.getOrNull() ?: return fallback
        try {
            val hasAlpha = bitmap.hasAlpha() && !profile.forceJpeg
            val out = ByteArrayOutputStream()
            val resultMime = if (hasAlpha) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, profile.quality, out)
                "image/webp"
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, profile.quality, out)
                "image/jpeg"
            }
            val bytes = out.toByteArray()
            // GIF в staticOnly всегда превращаем в кадр, остальным оставляем оригинал, если не выиграли
            return if (isGif || bytes.size < original.size) Result(bytes, resultMime) else fallback
        } finally {
            bitmap.recycle()
        }
    }

    private fun isGif(b: ByteArray) =
        b.size > 6 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()

    private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = max(info.size.width, info.size.height)
            if (side > maxSide) {
                val k = maxSide.toFloat() / side
                decoder.setTargetSize(
                    max(1, (info.size.width * k).roundToInt()),
                    max(1, (info.size.height * k).roundToInt()),
                )
            }
        }
    }

    // ── GIF ──────────────────────────────────────────────────────────────

    // Фиксированная палитра 6×7×6 = 252 цвета + индекс прозрачности
    private const val TRANSPARENT = 252
    private const val RL = 6
    private const val GL = 7
    private const val BL = 6

    private val bayer = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5,
    )

    @Suppress("DEPRECATION")
    private fun recompressGif(src: ByteArray, p: Profile): ByteArray? {
        val movie = Movie.decodeByteArray(src, 0, src.size) ?: return null
        val srcW = movie.width()
        val srcH = movie.height()
        if (srcW <= 0 || srcH <= 0) return null
        val duration = movie.duration()
        if (duration <= 0) return null // статичный GIF: не стоит того

        val scale = min(1f, p.gifMaxSide.toFloat() / max(srcW, srcH))
        val w = max(1, (srcW * scale).roundToInt())
        val h = max(1, (srcH * scale).roundToInt())

        val stepMs = max(1000 / p.gifMaxFps, (duration + p.gifMaxFrames - 1) / p.gifMaxFrames)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pixels = IntArray(w * h)

        val out = ByteArrayOutputStream()
        out.write("GIF89a".toByteArray())
        writeShort(out, w); writeShort(out, h)
        out.write(0xF7) // глобальная палитра, 256 цветов
        out.write(0); out.write(0)
        for (i in 0 until 256) {
            if (i < RL * GL * BL) {
                val r = i / (GL * BL)
                val g = (i / BL) % GL
                val b = i % BL
                out.write(level(r, RL)); out.write(level(g, GL)); out.write(level(b, BL))
            } else {
                out.write(0); out.write(0); out.write(0)
            }
        }
        // Бесконечный цикл
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B) + "NETSCAPE2.0".toByteArray() + byteArrayOf(3, 1, 0, 0, 0))

        var prev: ByteArray? = null
        var prevCleared = true
        var t = 0
        try {
            while (t < duration) {
                val delayMs = min(stepMs, duration - t).coerceAtLeast(20)
                bitmap.eraseColor(0)
                canvas.save()
                canvas.scale(scale, scale)
                movie.setTime(t)
                movie.draw(canvas, 0f, 0f)
                canvas.restore()
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

                val idx = ByteArray(w * h)
                var hasAlpha = false
                for (y in 0 until h) for (x in 0 until w) {
                    val c = pixels[y * w + x]
                    if ((c ushr 24) < 128) {
                        idx[y * w + x] = TRANSPARENT.toByte()
                        hasAlpha = true
                    } else {
                        idx[y * w + x] = quantize(c, x, y).toByte()
                    }
                }

                // Прозрачный кадр: стираем фон (disposal 2) и всегда пишем целиком
                var x0 = 0; var y0 = 0; var x1 = w; var y1 = h
                val delta = prev != null && !prevCleared && !hasAlpha
                val frame: ByteArray
                if (delta) {
                    val pv = prev!!
                    var minX = w; var minY = h; var maxX = -1; var maxY = -1
                    for (y in 0 until h) for (x in 0 until w) {
                        if (idx[y * w + x] != pv[y * w + x]) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                    if (maxX < 0) { // кадр не изменился: один пиксель, чтобы сохранить задержку
                        minX = 0; minY = 0; maxX = 0; maxY = 0
                    }
                    x0 = minX; y0 = minY; x1 = maxX + 1; y1 = maxY + 1
                    val fw = x1 - x0
                    frame = ByteArray(fw * (y1 - y0))
                    for (y in y0 until y1) for (x in x0 until x1) {
                        val v = idx[y * w + x]
                        frame[(y - y0) * fw + (x - x0)] =
                            if (v == pv[y * w + x]) TRANSPARENT.toByte() else v
                    }
                } else {
                    frame = idx
                }

                // Graphic Control Extension
                out.write(0x21); out.write(0xF9); out.write(4)
                val disposal = if (hasAlpha) 2 else 1
                val usesTransparency = delta || hasAlpha
                out.write((disposal shl 2) or (if (usesTransparency) 1 else 0))
                writeShort(out, (delayMs / 10).coerceAtLeast(2))
                out.write(TRANSPARENT)
                out.write(0)
                // Image Descriptor
                out.write(0x2C)
                writeShort(out, x0); writeShort(out, y0)
                writeShort(out, x1 - x0); writeShort(out, y1 - y0)
                out.write(0)
                lzw(frame, out)

                prev = idx
                prevCleared = hasAlpha
                t += stepMs
            }
        } finally {
            bitmap.recycle()
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun level(i: Int, levels: Int) = (i * 255f / (levels - 1)).roundToInt()

    private fun quantize(c: Int, x: Int, y: Int): Int {
        val d = bayer[(y and 3) * 4 + (x and 3)] / 16f - 0.5f
        val r = q((c shr 16) and 0xFF, RL, d)
        val g = q((c shr 8) and 0xFF, GL, d)
        val b = q(c and 0xFF, BL, d)
        return r * (GL * BL) + g * BL + b
    }

    private fun q(v: Int, levels: Int, dither: Float): Int {
        val step = 255f / (levels - 1)
        return ((v + dither * step) / step).roundToInt().coerceIn(0, levels - 1)
    }

    private fun writeShort(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
    }

    /** LZW для GIF, минимальный размер кода 8; пишется блоками по ≤255 байт. */
    private fun lzw(data: ByteArray, out: ByteArrayOutputStream) {
        out.write(8)
        val block = ByteArrayOutputStream()
        var bitBuf = 0
        var bitCnt = 0
        fun flushBlock(force: Boolean) {
            val b = block.toByteArray()
            var off = 0
            while (b.size - off >= 255 || (force && off < b.size)) {
                val n = min(255, b.size - off)
                out.write(n); out.write(b, off, n)
                off += n
            }
            block.reset()
            if (off < b.size) block.write(b, off, b.size - off)
        }

        val clear = 256
        val eoi = 257
        var codeSize = 9
        var next = 258
        val dict = HashMap<Int, Int>(8192)

        fun emit(code: Int) {
            bitBuf = bitBuf or (code shl bitCnt)
            bitCnt += codeSize
            while (bitCnt >= 8) {
                block.write(bitBuf and 0xFF)
                bitBuf = bitBuf ushr 8
                bitCnt -= 8
            }
            if (block.size() >= 255) flushBlock(false)
        }

        emit(clear)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val c = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or c
            val found = dict[key]
            if (found != null) {
                prefix = found
            } else {
                emit(prefix)
                if (next == (1 shl codeSize) && codeSize < 12) codeSize++
                if (next < 4096) {
                    dict[key] = next++
                } else {
                    emit(clear)
                    dict.clear()
                    next = 258
                    codeSize = 9
                }
                prefix = c
            }
        }
        emit(prefix)
        if (next == (1 shl codeSize) && codeSize < 12) codeSize++
        emit(eoi)
        if (bitCnt > 0) block.write(bitBuf and 0xFF)
        flushBlock(true)
        out.write(0)
    }
}
