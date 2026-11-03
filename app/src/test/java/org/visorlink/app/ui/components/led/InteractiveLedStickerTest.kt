package org.visorlink.app.ui.components.led

import org.junit.Assert.*
import org.junit.Test
import org.visorlink.app.data.model.StickerFormat
import org.visorlink.app.data.repository.StickerPackRepository

class InteractiveLedStickerTest {

    @Test
    fun testBuiltinForgeLedPack() {
        val pack = StickerPackRepository.builtinForgeLedPack
        assertEquals("forge_led_faces", pack.id)
        assertEquals("Forge LED", pack.name)
        assertEquals(11, pack.stickers.size)
        assertTrue(pack.isOfficial)
        for (sticker in pack.stickers) {
            assertEquals("led", sticker.format)
            assertTrue("Sticker url must start with led:", sticker.url.startsWith("led:"))
            assertTrue("effectiveFormat must be led", sticker.isLed)
        }
    }

    @Test
    fun testStickerFormatResolve() {
        assertEquals(StickerFormat.LED, StickerFormat.resolve(null, "led:happy"))
        assertEquals(StickerFormat.LED, StickerFormat.resolve(null, "led_cat"))
        assertEquals(StickerFormat.LED, StickerFormat.resolve("led", "https://example.com/sticker"))
        assertEquals(StickerFormat.LOTTIE, StickerFormat.resolve(null, "file.json"))
        assertEquals(StickerFormat.GIF, StickerFormat.resolve(null, "file.gif"))
        assertEquals(StickerFormat.STATIC, StickerFormat.resolve(null, "file.png"))
    }
}
