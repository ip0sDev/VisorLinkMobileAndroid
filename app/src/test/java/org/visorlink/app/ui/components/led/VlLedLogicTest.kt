package org.visorlink.app.ui.components.led

import org.junit.Assert.*
import org.junit.Test

class VlLedLogicTest {

    @Test
    fun testFaceRowsDimensions() {
        for (expr in VlLedExpression.entries) {
            val rows = vlLedFaceRows(expr)
            assertEquals("Expression $expr must have 12 rows", 12, rows.size)
            for ((idx, row) in rows.withIndex()) {
                assertEquals("Expression $expr row $idx must have 32 columns", 32, row.length)
                assertTrue("Row must only contain '#' or '.'", row.all { it == '#' || it == '.' })
            }
        }
    }

    @Test
    fun testParseLedExpression() {
        assertEquals(VlLedExpression.HAPPY, VlLedExpression.fromUrl("led:happy"))
        assertEquals(VlLedExpression.CAT, VlLedExpression.fromUrl("led:cat"))
        assertEquals(VlLedExpression.COOL, VlLedExpression.fromUrl("led_cool"))
        assertEquals(VlLedExpression.ANGRY, VlLedExpression.fromUrl("angry"))
        assertEquals(VlLedExpression.GLITCH, VlLedExpression.fromUrl("glitch"))
        assertEquals(VlLedExpression.CRY, VlLedExpression.fromUrl("cry"))
        assertEquals(VlLedExpression.HAPPY, VlLedExpression.fromUrl("unknown_expr"))
        assertEquals(VlLedExpression.HAPPY, VlLedExpression.fromUrl(null))
        assertEquals(VlLedExpression.HAPPY, VlLedExpression.fromUrl(""))
    }

    @Test
    fun testDissolveTransition() {
        val happy = vlLedFaceRows(VlLedExpression.HAPPY)
        val angry = vlLedFaceRows(VlLedExpression.ANGRY)

        val at0 = vlLedDissolve(happy, angry, 0f)
        assertEquals(happy, at0)

        val at1 = vlLedDissolve(happy, angry, 1f)
        assertEquals(angry, at1)

        val atMid = vlLedDissolve(happy, angry, 0.5f)
        assertEquals(12, atMid.size)
        assertEquals(32, atMid[0].length)

        // Детерминированность: повторный запуск дает идентичный результат
        val atMid2 = vlLedDissolve(happy, angry, 0.5f)
        assertEquals(atMid, atMid2)
    }

    @Test
    fun testLedDigits() {
        val rows = vlLedDigitRows("+123")
        assertEquals(5, rows.size)
        // 4 глифа по 3 точки + 3 разделителя по 1 точке = 15 колонок
        assertEquals(15, rows[0].length)
        for (row in rows) {
            assertTrue(row.contains('#'))
        }
    }

    @Test
    fun testBarHeight() {
        assertEquals(0, vlLedBarHeight(-1f, 8))
        assertEquals(0, vlLedBarHeight(0f, 8))
        assertEquals(4, vlLedBarHeight(0.5f, 8))
        assertEquals(8, vlLedBarHeight(1f, 8))
        assertEquals(8, vlLedBarHeight(2f, 8))
    }

    @Test
    fun testIconRows() {
        for (kind in VlLedIconKind.entries) {
            val rows = vlLedIconRows(kind)
            assertEquals(7, rows.size)
            for (row in rows) {
                assertEquals(7, row.length)
            }
        }
    }
}
