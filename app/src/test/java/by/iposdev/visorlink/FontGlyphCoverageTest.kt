package by.iposdev.visorlink

import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Юнит-тест для автоматической проверки покрытия кириллицы в шрифтах проекта.
 *
 * Проверяет все .ttf файлы в res/font.
 * Space Grotesk добавлен в исключения, так как используется только для латинского вордмарка.
 * Остальные шрифты (Inter, JetBrains Mono, Moniqa) должны поддерживать кириллицу.
 */
class FontGlyphCoverageTest {

    // Основной диапазон кириллицы (А-я) + буквы Ё/ё
    private val cyrillicRange = (0x0410..0x044F).toList() + 0x0401 + 0x0451

    private val whitelist = setOf(
        "space_grotesk_medium.ttf",
        "space_grotesk_semibold.ttf"
    )

    @Test
    fun testCyrillicCoverage() {
        var fontDir = File("app/src/main/res/font")
        if (!fontDir.exists()) {
            // Поддержка запуска как из корня проекта, так и из модуля :app
            fontDir = File("src/main/res/font")
        }

        if (!fontDir.exists()) {
            fail("Директория шрифтов не найдена: ${fontDir.absolutePath}")
        }

        val ttfFiles = fontDir.listFiles { _, name -> name.endsWith(".ttf") }
        if (ttfFiles == null || ttfFiles.isEmpty()) {
            fail("Шрифты .ttf не найдены в ${fontDir.absolutePath}")
            return
        }

        val failures = mutableListOf<String>()

        for (file in ttfFiles) {
            if (whitelist.contains(file.name)) continue

            val coveredChars = getCoveredChars(file)
            val missing = cyrillicRange.filter { it !in coveredChars }

            if (missing.isNotEmpty()) {
                val example = missing.take(3).joinToString(", ") { "U+%04X".format(it) }
                failures.add("${file.name}: не хватает ${missing.size} символов кириллицы (напр. $example)")
            }
        }

        if (failures.isNotEmpty()) {
            fail("Ошибка покрытия шрифтов:\n${failures.joinToString("\n")}")
        }
    }

    private fun getCoveredChars(file: File): Set<Int> {
        val raf = RandomAccessFile(file, "r")
        try {
            val covered = mutableSetOf<Int>()

            // 1. Offset Table
            raf.seek(4)
            val numTables = raf.readUnsignedShort()
            raf.seek(12)

            // 2. Table Directory - ищем 'cmap'
            var cmapOffset = -1L
            for (i in 0 until numTables) {
                val tag = readTag(raf)
                raf.readInt() // checksum
                val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                raf.readInt() // length
                if (tag == "cmap") {
                    cmapOffset = offset
                    break
                }
            }

            if (cmapOffset == -1L) return emptySet()

            // 3. cmap Index
            raf.seek(cmapOffset)
            raf.readUnsignedShort() // version
            val numSubtables = raf.readUnsignedShort()

            val subtableOffsets = mutableListOf<Long>()
            for (i in 0 until numSubtables) {
                val platformId = raf.readUnsignedShort()
                val encodingId = raf.readUnsignedShort()
                val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                // Ищем Unicode BMP (3, 1) или Unicode Full (3, 10)
                if (platformId == 3 && (encodingId == 1 || encodingId == 10)) {
                    subtableOffsets.add(cmapOffset + offset)
                }
            }

            // 4. Парсинг таблиц форматов 4 и 12
            for (offset in subtableOffsets) {
                raf.seek(offset)
                val format = raf.readUnsignedShort()
                if (format == 4) {
                    parseFormat4(raf, covered)
                } else if (format == 12) {
                    parseFormat12(raf, covered)
                }
            }

            return covered
        } finally {
            raf.close()
        }
    }

    private fun readTag(raf: RandomAccessFile): String {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return String(bytes)
    }

    private fun parseFormat4(raf: RandomAccessFile, covered: MutableSet<Int>) {
        raf.readUnsignedShort() // length
        raf.readUnsignedShort() // language
        val segCount = raf.readUnsignedShort() / 2
        raf.seek(raf.filePointer + 6) // skip searchRange, entrySelector, rangeShift

        val endCodes = IntArray(segCount) { raf.readUnsignedShort() }
        raf.readUnsignedShort() // reservedPad
        val startCodes = IntArray(segCount) { raf.readUnsignedShort() }
        val idDeltas = ShortArray(segCount) { raf.readShort() }
        val idRangeOffsetsPos = raf.filePointer
        val idRangeOffsets = IntArray(segCount) { raf.readUnsignedShort() }

        for (i in 0 until segCount) {
            val start = startCodes[i]
            val end = endCodes[i]
            if (start == 0xFFFF) continue

            for (code in start..end) {
                if (idRangeOffsets[i] == 0) {
                    val glyphId = (code + idDeltas[i]) and 0xFFFF
                    if (glyphId != 0) covered.add(code)
                } else {
                    val glyphOffset = idRangeOffsetsPos + i * 2 + idRangeOffsets[i] + (code - start) * 2
                    raf.seek(glyphOffset)
                    val glyphId = (raf.readUnsignedShort() + idDeltas[i]) and 0xFFFF
                    if (glyphId != 0) covered.add(code)
                }
            }
        }
    }

    private fun parseFormat12(raf: RandomAccessFile, covered: MutableSet<Int>) {
        raf.readUnsignedShort() // reserved
        raf.readInt() // length
        raf.readInt() // language
        val numGroups = raf.readInt()
        for (i in 0 until numGroups) {
            val start = raf.readInt()
            val end = raf.readInt()
            raf.readInt() // startGlyphID
            for (code in start..end) {
                covered.add(code)
            }
        }
    }
}
