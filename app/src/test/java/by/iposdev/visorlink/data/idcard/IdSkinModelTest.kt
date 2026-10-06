package org.visorlink.app.data.idcard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.visorlink.app.data.model.isIdTradePreview

/** Инвентарь скинов и обмен (спека ANDROID_ID_SKINS_AND_SESSIONS_SPEC.md, веб: useIdSkins.js, idCardService.ts). */
class IdSkinModelTest {

    private val day = 24L * 3600 * 1000
    private val traits = IdCardTraits(seed = 42, finish = IdFinish.GOLD, foil = IdFoil.GALAXY, variant = 3)
    private val oldCard = IdCard(serial = "VL-AAAA-BBBB", traits = traits, issuedAt = 1_000L, reissuedAt = 5_000L)

    private fun skin(id: String, obtainedAt: Long = 0) = IdSkin(
        id = id, serial = "VL-$id", traits = IdCardTraits(seed = 7), edition = IdEdition.COMMON,
        mintedAt = 0, obtainedAt = obtainedAt, origin = IdSkinOrigin.ROLL,
    )

    @Test
    fun `карта до инвентаря — единственный надетый виртуальный скин`() {
        val items = IdSkinRules.inventoryOf(oldCard, emptyList())
        assertEquals(1, items.size)
        val v = items.single()
        assertTrue(v.virtual)
        assertEquals(IdSkinRules.VIRTUAL_SKIN_ID, v.id)
        assertEquals("VL-AAAA-BBBB", v.serial)
        assertEquals(IdEdition.LEGENDARY, v.edition)
        assertEquals(5_000L, v.mintedAt) // reissuedAt || issuedAt, как в вебе
        assertEquals(IdSkinRules.VIRTUAL_SKIN_ID, IdSkinRules.equippedId(oldCard, items))
    }

    @Test
    fun `перенесённая карта — инвентарь из подписки, надет skinId`() {
        val card = oldCard.copy(skinId = "b")
        val skins = listOf(skin("a"), skin("b"))
        assertEquals(skins, IdSkinRules.inventoryOf(card, skins))
        assertEquals("b", IdSkinRules.equippedId(card, skins))
        assertTrue(IdSkinRules.inventoryOf(null, skins).isEmpty())
    }

    @Test
    fun `слоты — от 5 до 20, по умолчанию 5`() {
        assertEquals(5, IdSkinRules.slotsOf(oldCard))
        assertEquals(5, IdSkinRules.slotsOf(oldCard.copy(slots = 2)))
        assertEquals(12, IdSkinRules.slotsOf(oldCard.copy(slots = 12)))
        assertEquals(20, IdSkinRules.slotsOf(oldCard.copy(slots = 99)))
    }

    @Test
    fun `кулдаун прокрутки — rolledAt, иначе reissuedAt, иначе issuedAt`() {
        assertEquals(1_000L, IdSkinRules.lastRollAt(IdCard(issuedAt = 1_000L)))
        assertEquals(5_000L, IdSkinRules.lastRollAt(oldCard))
        assertEquals(9_000L, IdSkinRules.lastRollAt(oldCard.copy(rolledAt = 9_000L)))
        assertEquals(9_000L + 7 * day, IdSkinRules.rollAvailableAt(oldCard.copy(rolledAt = 9_000L)))
    }

    @Test
    fun `панель прокрутки — кулдаун важнее слота, слот важнее Bits`() {
        val now = 100 * day
        val ready = oldCard.copy(rolledAt = now - 8 * day, slots = 5)
        assertEquals(RollState.READY, IdSkinRules.rollState(ready, bits = 200, used = 4, now = now))
        assertEquals(RollState.BITS, IdSkinRules.rollState(ready, bits = 199, used = 4, now = now))
        assertEquals(RollState.FULL, IdSkinRules.rollState(ready, bits = 0, used = 5, now = now))
        assertEquals(RollState.COOLDOWN, IdSkinRules.rollState(ready.copy(rolledAt = now - day), bits = 0, used = 5, now = now))
    }

    @Test
    fun `скин из Firestore — тираж хранится, у старых считается по признакам`() {
        val raw = mapOf(
            "seed" to 123456789L,
            "serial" to "VL-7KQM-3XPA",
            "traits" to mapOf("finish" to "gold", "foil" to "galaxy", "laminated" to true, "wear" to 2, "variant" to 4, "tilt" to 0.5),
            "mintedAt" to 10L, "obtainedAt" to 20L, "origin" to "trade", "trades" to 3, "listedIn" to "T1",
        )
        val s = IdSkin.parse("S1", raw)!!
        assertEquals(123456789L, s.traits.seed) // seed рядом с traits
        assertEquals(IdEdition.LEGENDARY, s.edition)
        assertEquals(IdSkinOrigin.TRADE, s.origin)
        assertEquals(3, s.trades)
        assertEquals("T1", s.listedIn)
        assertEquals(IdEdition.RARE, IdSkin.parse("S2", raw + ("edition" to "rare"))!!.edition)
        assertNull(IdSkin.parse("S3", mapOf("seed" to 1)))
    }

    @Test
    fun `карта — новые поля инвентаря`() {
        val c = IdCard.parse(mapOf("serial" to "VL-1", "skinId" to "s1", "slots" to 7L, "rolledAt" to 3L, "equippedAt" to 4L))!!
        assertEquals("s1", c.skinId)
        assertEquals(7, c.slots)
        assertEquals(3L, c.rolledAt)
        assertEquals(4L, c.equippedAt)
        assertNull(IdCard.parse(mapOf("serial" to "VL-1"))!!.skinId)
    }

    @Test
    fun `обмен показывается только для своего чата и своего сообщения`() {
        val raw = mapOf(
            "chatId" to "C", "messageId" to "M", "ownerUid" to "u1", "ownerName" to "iposdev", "skinId" to "s",
            "skin" to mapOf("seed" to 1L, "serial" to "VL-1", "traits" to mapOf("finish" to "base"), "edition" to "epic"),
            "note" to "Меняю на золото", "status" to "done", "offerCount" to 2,
            "deal" to mapOf("offerId" to "u2", "fromUid" to "u2", "fromName" to "foxy", "skinId" to "x", "skin" to mapOf("serial" to "VL-2", "traits" to emptyMap<String, Any>())),
        )
        val t = IdTrade.parse("T", raw)!!
        assertTrue(t.belongsTo("C", "M"))
        assertFalse(t.belongsTo("C", "other"))
        assertFalse(t.belongsTo("other", "M"))
        assertEquals(IdEdition.EPIC, t.skin.edition)
        assertEquals(IdTradeStatus.DONE, t.status)
        assertEquals("foxy", t.deal?.fromName)
        assertEquals("VL-2", t.deal?.skin?.serial)
        assertNull(IdTrade.parse("T", raw - "skin"))
        assertEquals(IdTradeStatus.CLOSED, IdTrade.parse("T", raw + ("status" to "weird"))!!.status)
    }

    @Test
    fun `скин на карте смотрящего — режим и дата регистрации его, бланк от скина`() {
        val look = IdSkinLook("VL-9", traits, IdEdition.LEGENDARY)
        val card = look.asCard(IdSkinWearer(IdMode.PROTOGEN, "MK-II", 77L), issuedAt = 55L)
        assertEquals(IdMode.PROTOGEN, card.mode)
        assertEquals("MK-II", card.species)
        assertEquals("VL-9", card.serial)
        assertEquals(traits, card.traits)
        assertEquals(55L, card.issuedAt)
        assertEquals(77L, card.registeredAt)
    }

    @Test
    fun `превью id_trade в списке чатов`() {
        assertTrue(isIdTradePreview(mapOf("type" to "id_trade", "text" to "🪪 ID-карта на обмен")))
        assertTrue(isIdTradePreview(mapOf("text" to "🪪 ID-карта на обмен ")))
        assertTrue(isIdTradePreview("🪪 ID-карта на обмен"))
        assertFalse(isIdTradePreview(mapOf("type" to "text", "text" to "привет")))
        assertFalse(isIdTradePreview(null))
    }
}
