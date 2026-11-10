package org.visorlink.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.visorlink.app.data.remote.EconomyException

class UserGiftTest {

    @Test
    fun `цены подарков соответствуют спецификации`() {
        assertEquals(300, UserGifts.proPrices["pro_week"])
        assertEquals(1000, UserGifts.proPrices["pro_month"])

        assertEquals(20, UserGifts.postcardPrices["spark"])
        assertEquals(35, UserGifts.postcardPrices["aurora"])
        assertEquals(50, UserGifts.postcardPrices["supernova"])
    }

    @Test
    fun `максимальная длина записки 140 символов`() {
        assertEquals(140, UserGifts.NOTE_MAX)
    }

    @Test
    fun `обработка ошибок callable userGift`() {
        assertEquals("Подарок уже открыт.", giftErrorText(EconomyException("redeemed")))
        assertEquals("Подарок отозван.", giftErrorText(EconomyException("cancelled")))
        assertTrue(giftErrorText(EconomyException("skin-hold", waitMs = 14L * 24 * 3600 * 1000)).contains("подарить через"))
        assertEquals("Скин выставлен на обмен — сначала снимите его.", giftErrorText(EconomyException("already-listed")))
        assertEquals("У собеседника бесконечный PRO.", giftErrorText(EconomyException("recipient-eternal")))
        assertTrue(giftErrorText(EconomyException("gift-limit")).contains("Не больше 3 подарков"))

        // Анти-фарм с днями ожидания
        val waitEx = EconomyException("trust-activity", daysLeft = 5)
        assertTrue(giftErrorText(waitEx).contains("5 дн."))
    }
}
