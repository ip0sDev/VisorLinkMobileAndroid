package org.visorlink.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Апелляция считается в символах (code points), как на сервере: эмодзи — один символ. */
class BanRepositoryTest {

    private val emoji = "😀" // 😀: две UTF-16 единицы, один символ

    @Test
    fun `emoji counts as one character, not two`() {
        assertEquals(1, BanRepository.appealLength(emoji))
        assertEquals(2, emoji.length)
    }

    @Test
    fun `300 emoji pass and 301 do not`() {
        assertEquals(300, BanRepository.appealLength(emoji.repeat(300)))
        assertTrue(BanRepository.appealLength(emoji.repeat(300)) <= BanRepository.APPEAL_MAX)
        assertTrue(BanRepository.appealLength(emoji.repeat(301)) > BanRepository.APPEAL_MAX)
    }

    @Test
    fun `clamp never splits a surrogate pair`() {
        val clamped = BanRepository.clampAppeal(emoji.repeat(305))
        assertEquals(BanRepository.APPEAL_MAX, BanRepository.appealLength(clamped))
        assertFalse(Character.isHighSurrogate(clamped.last()))
    }

    @Test
    fun `clamp leaves short text untouched`() {
        assertEquals("привет", BanRepository.clampAppeal("привет"))
    }

    @Test
    fun `non-functions failure maps to OTHER`() {
        assertEquals(AppealError.OTHER, BanRepository.mapAppealError(RuntimeException("net")))
        assertFalse(BanRepository.isBannedError(RuntimeException("net")))
    }
}
