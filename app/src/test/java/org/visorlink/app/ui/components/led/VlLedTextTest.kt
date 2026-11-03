package org.visorlink.app.ui.components.led

import org.junit.Assert.*
import org.junit.Test

class VlLedTextTest {

    @Test
    fun testFontCharactersDimensions() {
        val required = "ABCDEFGHIJKLMNOPQRSTUVWXYZАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ0123456789 .,:;!?-—+%/·<>()'\"#@_←…"
        for (ch in required) {
            val key = vlLedGlyphKey(ch)
            assertNotNull("Character '$ch' must be recognized by vlLedGlyphKey", key)
            val rows = vlLedTextRows(ch.toString())
            assertEquals("Glyph '$ch' must have 7 rows", 7, rows.size)
            for (r in rows) {
                assertEquals("Glyph '$ch' row must have 5 columns", 5, r.length)
            }
            if (ch != ' ') {
                assertTrue("Non-space glyph '$ch' must have at least one '#' dot", rows.joinToString("").contains('#'))
            }
        }
    }

    @Test
    fun testCharacterSpacing() {
        val rows = vlLedTextRows("II")
        assertEquals(7, rows.size)
        // 2 знака по 5 + 1 разделитель = 11 колонок
        assertEquals(11, rows[0].length)
        for (r in rows) {
            assertEquals('.', r[5])
        }
        assertEquals(11, vlLedTextColumns("II"))
        assertEquals(0, vlLedTextColumns(""))
    }

    @Test
    fun testCyrillicAliasesAndCase() {
        assertEquals(vlLedTextRows("сеть ok"), vlLedTextRows("СЕТЬ OK"))
        assertEquals(vlLedTextRows("ё"), vlLedTextRows("Ё"))
        assertEquals(vlLedTextRows("АВЕКМНОРСТХ"), vlLedTextRows("ABEKMHOPCTX"))
    }

    @Test
    fun testCanRender() {
        assertTrue(vlLedCanRender("Исполнитель — Трек"))
        assertTrue(vlLedCanRender("Hello World 123!"))
        assertTrue(vlLedCanRender(""))
        assertFalse(vlLedCanRender("夜に駆ける"))
    }

    @Test
    fun testTruncate() {
        assertEquals("ПРИВЕТ", vlLedTruncate("ПРИВЕТ", 35))
        assertEquals("ПРИВЕ…", vlLedTruncate("ПРИВЕТ МИР", 35))
        assertTrue(vlLedTextColumns(vlLedTruncate("ПРИВЕТ МИР", 35)) <= 35)
    }
}
