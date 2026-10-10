package org.visorlink.app.data.idcard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.visorlink.app.ui.theme.CardTone
import org.visorlink.app.ui.theme.customPalette

/**
 * Кастомный скин (спека ANDROID_ID_CARD_RENDER_SPEC.md §5.4, §2.3) и своя подпись (§2.2) —
 * как `cardStyle.customColors / customPalette` и `normalizeSignature` в вебе.
 */
class IdCardCustomTest {

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    // ── custom ────────────────────────────────────────────────────────────────

    @Test
    fun `custom palette applies only when all three colors are valid`() {
        val ok = IdCustomLook.parse(mapOf("base" to "#112233", "primary" to "#AABBCC", "secondary" to "#FF0000", "label" to "Серия 1"))!!
        assertEquals(Triple("#112233", "#AABBCC", "#FF0000"), ok.colors)
        assertEquals("Серия 1", ok.labelText)
        val bad = IdCustomLook.parse(mapOf("base" to "#112233", "primary" to "red", "secondary" to "#FF0000", "label" to "x"))!!
        assertNull(bad.colors)
        assertNull("надпись — только при валидной палитре", bad.labelText)
    }

    @Test
    fun `custom label is cut to 32 characters`() {
        val c = IdCustomLook.parse(mapOf("base" to "#000000", "primary" to "#FFFFFF", "secondary" to "#FFFFFF", "label" to "x".repeat(50)))!!
        assertEquals(32, c.labelText!!.length)
    }

    @Test
    fun `custom holo shape is read by name`() {
        assertEquals(IdCardGenerator.HoloShape.ROSETTE, IdCustomLook.parse(mapOf("holo" to "rosette"))!!.holo)
        assertNull(IdCustomLook.parse(mapOf("holo" to "triangle"))!!.holo)
    }

    @Test
    fun `custom skin is always epic`() {
        val card = IdCard.parse(mapOf("serial" to "VL-AAAA-BBBB", "traits" to mapOf("seed" to 1, "finish" to "base", "foil" to "none"), "custom" to mapOf("base" to "#000000")))!!
        assertEquals(IdEdition.EPIC, card.edition)
        val skin = IdSkinLook.parse(mapOf("serial" to "VL-AAAA-BBBB", "traits" to mapOf("seed" to 1), "custom" to mapOf("base" to "#000000")))!!
        assertEquals(IdEdition.EPIC, skin.edition)
        assertEquals(skin.custom, skin.asCard(IdSkinWearer(), 0).custom)
    }

    @Test
    fun `custom palette tone and ink follow luminance of base and primary`() {
        // Тёмный пластик и тёмный основной: тон dark, на шапке белый текст
        val dark = customPalette("#101820", "#203040", "#FF8800", IdFinish.BASE)
        assertEquals(CardTone.DARK, dark.tone)
        assertEquals("#FFFFFF", hex(dark.bandInk))
        // color-mix(in srgb, p 10%, #F4F7FA) — покомпонентно p·0.1 + B·0.9
        assertEquals(hex(Color(0xFF203040).let { p -> Color(p.red * 0.1f + 0xF4 / 255f * 0.9f, p.green * 0.1f + 0xF7 / 255f * 0.9f, p.blue * 0.1f + 0xFA / 255f * 0.9f) }), hex(dark.ink))
        // Светлый пластик, светлый основной: тон light, на шапке #101418, край — белый 70 %
        val light = customPalette("#F0F0F0", "#FFE45C", "#3366FF", IdFinish.BASE)
        assertEquals(CardTone.LIGHT, light.tone)
        assertEquals("#101418", hex(light.bandInk))
        assertEquals(0.7f, light.edge.alpha, 0.01f)
        // Обсидиан всегда тёмный
        assertEquals(CardTone.DARK, customPalette("#F0F0F0", "#FFE45C", "#3366FF", IdFinish.OBSIDIAN).tone)
    }

    // ── Своя подпись ──────────────────────────────────────────────────────────

    @Test
    fun `drawn signature is split into strokes`() {
        val s = IdCardGenerator.parseSignature("M12.4 20.1L14 19.3L16.2 17.9M30 22L41.5 18")!!
        assertEquals(2, s.size)
        assertEquals(listOf(12.4 to 20.1, 14.0 to 19.3, 16.2 to 17.9), s[0])
        assertEquals(listOf(30.0 to 22.0, 41.5 to 18.0), s[1])
    }

    @Test
    fun `invalid drawn signature falls back to the seed flourish`() {
        assertNull(IdCardGenerator.parseSignature(null))
        assertNull(IdCardGenerator.parseSignature(""))
        assertNull("начинается не с M", IdCardGenerator.parseSignature("L1 2"))
        assertNull("два знака после точки", IdCardGenerator.parseSignature("M1.25 2"))
        assertNull("кривые нельзя", IdCardGenerator.parseSignature("M1 2C3 4 5 6 7 8"))
        assertNull("y вне 0–30", IdCardGenerator.parseSignature("M10 31"))
        assertNull("x вне 0–100", IdCardGenerator.parseSignature("M101 3"))
        assertNull("длиннее 6000", IdCardGenerator.parseSignature("M1 1" + "L2 2".repeat(1600)))
    }
}
