package org.visorlink.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.visorlink.app.data.model.UserProfile
import java.util.Date

/**
 * Акцент PRO — порт `src/utils/profileAccent.js`. Эталоны посчитаны оригинальным JS в node
 * (`readableAccent(hex, 'dark' | 'light')`, `uiAccentVars(...)['--on-primary']`): если тест
 * падает, приложение красит интерфейс не тем цветом, что веб.
 */
class ProAccentTest {

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
    private fun color(h: String) = Color(h.substring(1).toLong(16) or 0xFF000000)

    // hex → (dark, light)
    private val reference = mapOf(
        "#35C7E8" to ("#35C7E8" to "#1A6374"),
        "#8C6BFF" to ("#9577FF" to "#5F49AD"),
        "#FF4D6A" to ("#FF4D6A" to "#A33144"),
        "#2DD4BF" to ("#2DD4BF" to "#16665C"),
        "#000000" to ("#8F8F8F" to "#000000"),
        "#FFFFFF" to ("#FFFFFF" to "#575757"),
        "#1A1A1A" to ("#919191" to "#1A1A1A"),
        "#FFE45C" to ("#FFE45C" to "#665B25"),
        "#6366F1" to ("#7F82F4" to "#4B4EB7"),
        "#94A3B8" to ("#94A3B8" to "#535B67"),
    )

    @Test
    fun `readable accent matches the web byte for byte`() {
        reference.forEach { (src, expected) ->
            assertEquals("$src dark", expected.first, hex(ProAccent.readable(color(src), light = false)))
            assertEquals("$src light", expected.second, hex(ProAccent.readable(color(src), light = true)))
        }
    }

    @Test
    fun `text on accent is near-black on dark theme and white on light theme`() {
        reference.forEach { (src, expected) ->
            assertEquals("$src dark", "#0B0F12", hex(ProAccent.onColor(color(expected.first))))
            assertEquals("$src light", "#FFFFFF", hex(ProAccent.onColor(color(expected.second))))
        }
    }

    @Test
    fun `profile color is accentHex first then the legacy preset`() {
        assertEquals("#ABCDEF", ProAccent.hexOf(mapOf("accentHex" to "#abcdef", "accent" to "blue")))
        assertEquals("#FF4D6A", ProAccent.hexOf(mapOf("accent" to "crimson")))
        assertNull(ProAccent.hexOf(mapOf("accentHex" to "bad", "accent" to "default")))
        assertNull(ProAccent.hexOf(null))
    }

    @Test
    fun `accent applies only with active PRO`() {
        val custom = mapOf<String, Any?>("accentHex" to "#FFE45C")
        val future = Timestamp(Date(System.currentTimeMillis() + 86_400_000))
        val past = Timestamp(Date(System.currentTimeMillis() - 86_400_000))
        assertEquals("#FFE45C", ProAccent.of(UserProfile(proUntil = future, customization = custom))?.let(::hex))
        assertNull(ProAccent.of(UserProfile(proUntil = past, customization = custom)))
        assertNull(ProAccent.of(UserProfile(proUntil = future)))
    }
}
