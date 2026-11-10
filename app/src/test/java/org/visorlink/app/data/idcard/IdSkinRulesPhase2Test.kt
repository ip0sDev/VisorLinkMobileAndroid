package org.visorlink.app.data.idcard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdSkinRulesPhase2Test {

    private val day = 24L * 3600 * 1000
    private val traits = IdCardTraits(seed = 42, finish = IdFinish.GOLD, foil = IdFoil.GALAXY, variant = 3)
    private val card = IdCard(serial = "VL-TEST-1234", traits = traits, issuedAt = 1_000L, slots = 5)

    private fun testSkin(id: String, obtainedAt: Long = 0) = IdSkin(
        id = id, serial = "VL-$id", traits = IdCardTraits(seed = 7), edition = IdEdition.COMMON,
        mintedAt = 0, obtainedAt = obtainedAt, origin = IdSkinOrigin.ROLL,
    )

    @Test
    fun `PRO дает плюс 5 слотов инвентаря`() {
        assertEquals(5, IdSkinRules.capacityOf(card, pro = false))
        assertEquals(10, IdSkinRules.capacityOf(card, pro = true))

        val customCard = card.copy(slots = 8)
        assertEquals(8, IdSkinRules.capacityOf(customCard, pro = false))
        assertEquals(13, IdSkinRules.capacityOf(customCard, pro = true))
    }

    @Test
    fun `подсчет занятых слотов — виртуальные не считаются`() {
        val virtualSkin = testSkin("v").copy(virtual = true)
        val regularSkin1 = testSkin("1")
        val regularSkin2 = testSkin("2")

        assertEquals(0, IdSkinRules.occupied(listOf(virtualSkin)))
        assertEquals(2, IdSkinRules.occupied(listOf(virtualSkin, regularSkin1, regularSkin2)))
    }

    @Test
    fun `кулдаун без PRO — ровно 7 дней`() {
        val rolledAt = 100 * day
        val c = card.copy(rolledAt = rolledAt)
        assertEquals(rolledAt + 7 * day, IdSkinRules.rollAvailableAt(c, pro = false))
    }

    @Test
    fun `кулдаун с PRO — 3_5 дня от rolledAt либо rolledPrevAt плюс 7 дней`() {
        val now = 100 * day
        // Первый бросок 1 день назад, предыдущего нет
        val c1 = card.copy(rolledAt = now - 1 * day, rolledPrevAt = null)
        assertEquals((now - 1 * day) + (3.5 * day).toLong(), IdSkinRules.rollAvailableAt(c1, pro = true))

        // Два броска: rolledPrevAt 2 дня назад, rolledAt 1 день назад
        val c2 = card.copy(rolledAt = now - 1 * day, rolledPrevAt = now - 2 * day)
        // 2 броска за 7 дней: доступно через 7 дней после первого из двух
        val expected = (now - 2 * day) + 7 * day
        assertEquals(expected, IdSkinRules.rollAvailableAt(c2, pro = true))
    }

    @Test
    fun `скин на 14-дневном холде нельзя подарить`() {
        val now = 50 * day
        val freshSkin = testSkin("fresh", obtainedAt = now - 5 * day)
        val oldSkin = testSkin("old", obtainedAt = now - 15 * day)

        assertTrue(freshSkin.onHold(now))
        assertFalse(oldSkin.onHold(now))
    }

    @Test
    fun `паттерны VL Series и ROOT флаги`() {
        assertTrue(IdPattern.VL_ION.isVl)
        assertTrue(IdPattern.VL_PRO_AURUM.isVl)
        assertTrue(IdPattern.VL_PRO_AURUM.isPro)
        assertTrue(IdPattern.VL_PRO_NOIR.isPro)
        assertFalse(IdPattern.VL_ION.isPro)

        assertTrue(IdPattern.ROOT.isSingle)
        assertFalse(IdPattern.VL_ION.isSingle)

        assertEquals(2000, IdSkinRules.PREMIUM_ROLL_COST_BITS)
    }
}
