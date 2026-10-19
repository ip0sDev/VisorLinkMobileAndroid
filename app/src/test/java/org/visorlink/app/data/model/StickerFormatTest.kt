package org.visorlink.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.visorlink.app.ui.components.chat.driveImageCandidates

class StickerFormatTest {

    @Test
    fun `явный format важнее расширения`() {
        assertEquals(StickerFormat.WEBM, StickerFormat.resolve("webm", "https://x/a.png"))
        assertEquals(StickerFormat.LOTTIE, StickerFormat.resolve("LOTTIE", "https://x/a"))
    }

    @Test
    fun `неизвестный format - static`() {
        assertEquals(StickerFormat.STATIC, StickerFormat.resolve("tgs", "https://x/a.json"))
    }

    @Test
    fun `старые стикеры без format угадываются по расширению`() {
        assertEquals(StickerFormat.LOTTIE, StickerFormat.resolve(null, "https://x/src_AQAD.json?alt=media&token=1"))
        assertEquals(StickerFormat.GIF, StickerFormat.resolve(null, "https://x/a.GIF"))
        assertEquals(StickerFormat.STATIC, StickerFormat.resolve(null, "https://x/a.webp"))
        assertEquals(StickerFormat.STATIC, StickerFormat.resolve(null, null))
    }

    @Test
    fun `в сообщение статика пишется как null`() {
        assertNull(StickerFormat.forMessage(StickerFormat.STATIC))
        assertEquals("webp", StickerFormat.forMessage(StickerFormat.WEBP))
    }

    @Test
    fun `цепочка Drive - lh3, thumbnail, uc`() {
        assertEquals(
            listOf(
                "https://lh3.googleusercontent.com/d/abc_1",
                "https://drive.google.com/thumbnail?id=abc_1&sz=w2000",
                "https://drive.google.com/uc?export=view&id=abc_1",
            ),
            driveImageCandidates("https://lh3.googleusercontent.com/d/abc_1", null)
        )
    }

    @Test
    fun `не-Drive ссылка - единственный кандидат`() {
        assertEquals(listOf("https://cdn/x.jpg"), driveImageCandidates("https://cdn/x.jpg", null))
        assertEquals(emptyList<String>(), driveImageCandidates(null, null))
    }
}
