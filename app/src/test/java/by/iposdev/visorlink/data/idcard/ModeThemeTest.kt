package org.visorlink.app.data.idcard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Порт functions/test/modeTheme.test.js — те же случаи, что в вебе. */
class ModeThemeTest {

    private val biolume = ModeTheme(ModeStyle.BIOLUME, null, false)

    @Test
    fun `Standard и выключенный флаг — всегда Biolume без фиксации тёмной`() {
        assertEquals(biolume, ModeThemeRules.resolve(idCardsEnabled = true, idMode = "standard"))
        assertEquals(biolume, ModeThemeRules.resolve(idCardsEnabled = false, idMode = "protogen"))
        assertEquals(biolume, ModeThemeRules.resolve(idCardsEnabled = true, idMode = null))
        assertEquals(ModeStyle.BIOLUME, ModeThemeRules.resolve(idCardsEnabled = true, idMode = "admin").style) // мусор в поле
    }

    @Test
    fun `Особый режим вне чата — Forge своего оттенка и только тёмная`() {
        assertEquals(ModeTheme(ModeStyle.FORGE, IdMode.PROTOGEN, true), ModeThemeRules.resolve(true, "protogen"))
        assertEquals(ModeTheme(ModeStyle.FORGE, IdMode.BEAST, true), ModeThemeRules.resolve(true, "beast", chatTheme = ChatThemeContext.MODE))
    }

    @Test
    fun `Нейтральный чат — Biolume, но тёмная остаётся`() {
        assertEquals(ModeTheme(ModeStyle.BIOLUME, null, true), ModeThemeRules.resolve(true, "beast", chatTheme = ChatThemeContext.NEUTRAL))
    }

    @Test
    fun `Маска — Biolume и свободный выбор темы, в любом контексте`() {
        for (chatTheme in listOf(null, ChatThemeContext.MODE, ChatThemeContext.NEUTRAL)) {
            assertEquals(biolume, ModeThemeRules.resolve(true, "protogen", masked = true, chatTheme = chatTheme))
        }
    }

    @Test
    fun `Контекст чата — ЛС по режиму собеседника, Standard нейтрально, бот тему не трогает, загрузка без изменений`() {
        assertEquals(ChatThemeContext.MODE, ModeThemeRules.chatThemeFor(true, true, false, "beast", null))
        assertEquals(ChatThemeContext.NEUTRAL, ModeThemeRules.chatThemeFor(true, true, false, "standard", null))
        assertEquals(ChatThemeContext.MODE, ModeThemeRules.chatThemeFor(true, true, true, null, null))
        assertEquals(ChatThemeContext.MODE, ModeThemeRules.chatThemeFor(true, true, true, "standard", null))
        assertNull(ModeThemeRules.chatThemeFor(true, false, false, null, null))
    }

    @Test
    fun `Контекст чата — группа и канал только с включённым ID группы`() {
        assertEquals(ChatThemeContext.MODE, ModeThemeRules.chatThemeFor(false, false, false, null, true))
        assertEquals(ChatThemeContext.NEUTRAL, ModeThemeRules.chatThemeFor(false, false, false, null, false))
        assertEquals(ChatThemeContext.NEUTRAL, ModeThemeRules.chatThemeFor(false, false, false, null, null))
    }

    @Test
    fun `Чужой профиль — гамма владельца только для смотрящего с особым режимом`() {
        assertNull(ModeThemeRules.ownerProfileTheme(viewerSpecial = false, isMe = false, ownerMode = "beast"))
        assertNull(ModeThemeRules.ownerProfileTheme(viewerSpecial = true, isMe = true, ownerMode = "beast"))
        assertEquals(ModeTheme(ModeStyle.FORGE, IdMode.BEAST, true), ModeThemeRules.ownerProfileTheme(true, false, "beast"))
        assertEquals(ModeTheme(ModeStyle.BIOLUME, null, true), ModeThemeRules.ownerProfileTheme(true, false, "standard"))
    }

    @Test
    fun `тираж по очкам отделки и фольги`() {
        assertEquals(IdEdition.LEGENDARY, editionOf(IdFinish.GOLD, IdFoil.AURORA))
        assertEquals(IdEdition.LEGENDARY, editionOf(IdFinish.OBSIDIAN, IdFoil.GALAXY))
        assertEquals(IdEdition.EPIC, editionOf(IdFinish.GOLD, IdFoil.CLASSIC))
        assertEquals(IdEdition.RARE, editionOf(IdFinish.METALLIC, IdFoil.PRISM))
        assertEquals(IdEdition.UNCOMMON, editionOf(IdFinish.BASE, IdFoil.AURORA))
        assertEquals(IdEdition.COMMON, editionOf(IdFinish.PEARL, IdFoil.CLASSIC))
    }

    @Test
    fun `кулдауны считаются от последней смены режима и прокрутки (у старых карт — замены)`() {
        val card = IdCard(issuedAt = 1_000, modeChangedAt = 5_000, reissuedAt = null)
        assertEquals(20_000L, IdCardRules.modeAvailableAt(card))
        assertEquals(1_000L + IdSkinRules.ROLL_COOLDOWN_MS, IdSkinRules.rollAvailableAt(card))
        assertEquals(9_000L + IdSkinRules.ROLL_COOLDOWN_MS, IdSkinRules.rollAvailableAt(card.copy(reissuedAt = 9_000)))
    }

    @Test
    fun `разбор документа карты и группы`() {
        val card = IdCard.parse(mapOf(
            "mode" to "beast", "species" to "Snow leopard", "serial" to "VL-7KQM-3XPA",
            "traits" to mapOf("seed" to 123456789L, "finish" to "gold", "laminated" to true, "wear" to 2, "foil" to "prism", "variant" to 7, "tilt" to -1.2),
            "issuedAt" to 1791200000000L, "registeredAt" to 1741910400000L, "modeChangedAt" to 1791200000000L, "reissuedAt" to null, "version" to 2,
        ))!!
        assertEquals(IdMode.BEAST, card.mode)
        assertEquals("Snow leopard", card.species)
        assertEquals(IdCardTraits(123456789L, IdFinish.GOLD, true, 2, IdFoil.PRISM, 7, -1.2), card.traits)
        assertNull(card.reissuedAt)
        assertEquals(2, card.version)
        // Мусор в полях — значения по умолчанию, а не исключение
        assertEquals(IdMode.STANDARD, IdCard.parse(mapOf("mode" to "admin", "traits" to "x"))!!.mode)
        assertEquals(1L, IdCard.parse(mapOf("traits" to mapOf("seed" to 0)))!!.traits.seed)

        val group = GroupIdCard.parse(mapOf("enabled" to true, "serial" to "VL-AAAA-BBBB", "traits" to mapOf("seed" to 5), "issuedAt" to 10L))!!
        assertEquals(true, group.enabled)
        assertEquals("VL-AAAA-BBBB", group.asCard(IdMode.PROTOGEN)!!.serial)
        assertEquals(10L, group.asCard(IdMode.PROTOGEN)!!.registeredAt)
        assertNull(GroupIdCard.parse(null))
    }

    @Test
    fun `место карты в профиле`() {
        assertEquals(IdCardPosition.AFTER_HEADER, IdCardPosition.of(null))
        assertEquals(IdCardPosition.AFTER_HEADER, IdCardPosition.of(mapOf("idCardPosition" to "sideways")))
        assertEquals(IdCardPosition.TOP, IdCardPosition.of(mapOf("idCardPosition" to "top")))
        assertEquals(IdCardPosition.BOTTOM, IdCardPosition.of(mapOf("idCardPosition" to "bottom")))
    }
}
