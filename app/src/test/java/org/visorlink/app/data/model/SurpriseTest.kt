package org.visorlink.app.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.visorlink.app.ui.surprise.formatNumber
import java.util.Date

class SurpriseTest {

    private val from = 1_000_000L
    private val until = 2_000_000L

    @Test
    fun `without id there is no surprise`() {
        assertNull(Surprise.parse(mapOf("title" to "x")))
        assertNull(Surprise.parse(null))
    }

    @Test
    fun `parses timestamps, slides and wishes`() {
        val s = Surprise.parse(
            mapOf(
                "id" to "bday-2026",
                "showFrom" to Timestamp(Date(from)),
                "showUntil" to until,
                "fromName" to "@iposdev",
                "title" to "  С ДР!  ",
                "slides" to listOf(
                    mapOf("kicker" to "Сообщений", "value" to "1 284", "imageUrl" to "http://insecure"),
                    mapOf("kicker" to "пустой слайд"),
                    mapOf("value" to "17 марта", "countUp" to false),
                ),
                "wishes" to listOf(mapOf("name" to "Аня", "text" to "С праздником"), mapOf("name" to "без текста")),
            ),
        )!!
        assertEquals(from, s.showFrom)
        assertEquals(until, s.showUntil)
        assertEquals("iposdev", s.fromName)
        assertEquals("С ДР!", s.title)
        assertEquals(2, s.slides.size)
        assertNull(s.slides[0].imageUrl)
        assertTrue(s.slides[0].countUp)
        assertFalse(s.slides[1].countUp)
        assertEquals(listOf(SurpriseWish("Аня", "С праздником")), s.wishes)
        assertFalse(s.finished)
    }

    @Test
    fun `window is half-open and respects active`() {
        val s = Surprise(id = "a", showFrom = from, showUntil = until)
        assertFalse(s.isOpenAt(from - 1))
        assertTrue(s.isOpenAt(from))
        assertFalse(s.isOpenAt(until))
        assertFalse(s.copy(active = false).isOpenAt(from))
        assertEquals(10L, s.startsIn(from - 10))
        assertNull(s.startsIn(from))
        assertNull(s.copy(showUntil = from).startsIn(0))
    }

    @Test
    fun `finishedAt marks it as seen`() {
        assertTrue(Surprise.parse(mapOf("id" to "a", "finishedAt" to Timestamp(Date(from))))!!.finished)
    }

    @Test
    fun `numbers are grouped only when the source was`() {
        assertEquals("1284", formatNumber(1284, grouped = false))
        assertEquals("1 284", formatNumber(1284, grouped = true))
        assertEquals("12 345 678", formatNumber(12_345_678, grouped = true))
        assertEquals("999", formatNumber(999, grouped = true))
    }

    @Test
    fun `gift flag and claim result`() {
        assertFalse(Surprise.parse(mapOf("id" to "a"))!!.hasGift)
        assertTrue(Surprise.parse(mapOf("id" to "a", "gift" to mapOf("bits" to 5000)))!!.hasGift)
        val r = SurpriseGiftResult.parse(
            mapOf(
                "skin" to mapOf(
                    "id" to "s1", "seed" to 22102026L, "serial" to "VL-LUNA-2210", "edition" to "legendary", "mintedAt" to 42L,
                    "traits" to mapOf("finish" to "gold", "foil" to "galaxy", "laminated" to true),
                    "custom" to mapOf("base" to "#1A1030", "primary" to "#C9A0DC", "secondary" to "#FFF555", "pattern" to "neko"),
                ),
                "equipped" to true, "bits" to 5000L, "proDays" to 365L, "proUntil" to 1_800_000_000_000L,
            ),
        )!!
        assertEquals("VL-LUNA-2210", r.skin!!.serial)
        assertEquals(org.visorlink.app.data.idcard.IdEdition.LEGENDARY, r.skin!!.edition)
        assertEquals(org.visorlink.app.data.idcard.IdPattern.NEKO, r.skin!!.custom!!.pattern)
        assertEquals(22102026L, r.skin!!.traits.seed)
        assertEquals(42L, r.skinMintedAt)
        assertEquals(5000, r.bits)
        assertEquals(365, r.proDays)
        assertFalse(r.demo)
    }
}
