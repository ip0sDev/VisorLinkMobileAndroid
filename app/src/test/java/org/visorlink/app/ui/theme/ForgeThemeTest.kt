package org.visorlink.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class ForgeThemeTest {

    @Test
    fun testForgeCleanRoomPalette() {
        val lightScheme = forgeV2ColorScheme(ForgeV2.Protogen, dark = false)
        assertEquals(ForgeV2.CleanKey, lightScheme.primary)
        assertEquals(ForgeV2.CleanBackground, lightScheme.background)
        assertEquals(ForgeV2.CleanSurface, lightScheme.surface)
        assertEquals(ForgeV2.CleanOnSurface, lightScheme.onSurface)

        val lightLed = forgeV2Led(ForgeV2.Protogen, dark = false)
        assertEquals(ForgeV2.CleanKey, lightLed.color)
        assertEquals(ForgeV2.CleanScreen, lightLed.screen)
        assertEquals(0.dp, lightLed.glowBlur)
    }

    @Test
    fun testForgeBlackoutLedPalette() {
        val darkScheme = forgeV2ColorScheme(ForgeV2.Protogen, dark = true)
        assertEquals(ForgeV2.Protogen.primary, darkScheme.primary)
        assertEquals(ForgeV2.Protogen.background, darkScheme.background)
        assertEquals(ForgeV2.Protogen.surface, darkScheme.surface)
        assertEquals(ForgeV2.Protogen.onSurface, darkScheme.onSurface)

        val darkLed = forgeV2Led(ForgeV2.Protogen, dark = true)
        assertEquals(Color(0xFF3DF2FF), darkLed.color)
        assertEquals(Color(0xFF050507), darkLed.screen)
        assertEquals(6.dp, darkLed.glowBlur)
    }

    @Test
    fun testForgeBeastPalette() {
        val darkScheme = forgeV2ColorScheme(ForgeV2.Beast, dark = true)
        assertEquals(ForgeV2.Beast.primary, darkScheme.primary)
        assertEquals(ForgeV2.Beast.background, darkScheme.background)

        val darkLed = forgeV2Led(ForgeV2.Beast, dark = true)
        assertEquals(Color(0xFFFFB04D), darkLed.color)
        assertEquals(Color(0xFF080605), darkLed.screen)
    }

    @Test
    fun testForgeSwitchTokens() {
        val sw = forgeV2Switch(ForgeV2.Protogen, dark = true)
        assertTrue("Forge switch must have ledDots enabled", sw.ledDots)
        assertTrue("Forge switch must have litTrack enabled", sw.litTrack)
    }
}
