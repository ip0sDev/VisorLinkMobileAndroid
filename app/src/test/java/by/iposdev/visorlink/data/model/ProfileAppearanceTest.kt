package org.visorlink.app.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ProfileAppearanceTest {

    private val proUntilFuture = Timestamp(Date(System.currentTimeMillis() + 86_400_000))
    private val proUntilPast = Timestamp(Date(System.currentTimeMillis() - 86_400_000))

    private val fullCustomization = mapOf<String, Any?>(
        "theme" to "biolume",
        "accent" to "crimson",
        "font" to "rounded",
        "layout" to "compact",
        "bgUrl" to "https://cdn/bg.jpg",
        "gifUrl" to "https://cdn/banner.gif",
        "emojis" to "🔥",
    )

    private fun user(uid: String, pro: Timestamp? = null, ignore: Boolean = false, cust: Map<String, Any?> = fullCustomization) =
        UserProfile(uid = uid, proUntil = pro, ignoreCustomizations = ignore, customization = cust)

    @Test
    fun `parse reads every known key`() {
        val a = ProfileAppearance.parse(fullCustomization)
        assertEquals(AppTheme.BIOLUME, a.theme)
        assertEquals(ColorPreset.CRIMSON, a.accent)
        assertEquals(ProfileFont.ROUNDED, a.font)
        assertEquals(ProfileLayout.COMPACT, a.layout)
        assertEquals("https://cdn/bg.jpg", a.backgroundUrl)
        assertEquals("https://cdn/banner.gif", a.bannerUrl)
        assertEquals("🔥", a.emojis)
    }

    @Test
    fun `unknown theme falls back to viewer instead of M3E`() {
        // Старый CustomizationHelper.parseStyle превращал мусор в MATERIAL3_EXPRESSIVE
        assertNull(ProfileAppearance.parse(mapOf("theme" to "neon")).theme)
    }

    @Test
    fun `removed Forge theme falls back to viewer`() {
        // Старый Forge удалён: у профилей, где он остался, тема берётся у смотрящего
        assertNull(ProfileAppearance.parse(mapOf("theme" to "forge")).theme)
    }

    @Test
    fun `default and garbage values mean not set`() {
        val a = ProfileAppearance.parse(
            mapOf("accent" to "default", "font" to "default", "layout" to 42, "bgUrl" to "  ", "gifUrl" to null)
        )
        assertEquals(ProfileAppearance.None, a)
        assertTrue(a.isEmpty)
    }

    @Test
    fun `writeTo round-trips and keeps foreign keys`() {
        val base = mapOf<String, Any?>("diaryEnabled" to true, "acceptedVersion" to "3")
        val a = ProfileAppearance.parse(fullCustomization)
        val written = a.writeTo(base)
        assertEquals(true, written["diaryEnabled"])
        assertEquals("3", written["acceptedVersion"])
        assertEquals(a, ProfileAppearance.parse(written))
    }

    @Test
    fun `writeTo removes cleared fields instead of storing nulls`() {
        val cleared = ProfileAppearance.parse(fullCustomization).copy(theme = null, backgroundUrl = null, layout = ProfileLayout.DEFAULT)
        val written = cleared.writeTo(fullCustomization)
        assertFalse(written.containsKey("theme"))
        assertFalse(written.containsKey("bgUrl"))
        assertFalse(written.containsKey("layout"))
        assertEquals("crimson", written["accent"])
    }

    @Test
    fun `own profile is always shown even without PRO`() {
        val me = user("me", pro = proUntilPast)
        assertEquals(AppTheme.BIOLUME, ProfileAppearance.resolve(owner = me, viewer = me).theme)
    }

    @Test
    fun `other profile requires active PRO`() {
        val viewer = user("v", cust = emptyMap())
        assertTrue(ProfileAppearance.resolve(user("o", pro = proUntilPast), viewer).isEmpty)
        assertTrue(ProfileAppearance.resolve(user("o", pro = null), viewer).isEmpty)
        assertEquals(AppTheme.BIOLUME, ProfileAppearance.resolve(user("o", pro = proUntilFuture), viewer).theme)
    }

    @Test
    fun `viewer can hide others customization, background included`() {
        val viewer = user("v", ignore = true, cust = emptyMap())
        val owner = user("o", pro = proUntilFuture)
        val a = ProfileAppearance.resolve(owner, viewer)
        assertTrue(a.isEmpty)
        // Обои чата «как у собеседника» раньше обходили эту настройку
        assertNull(a.backgroundUrl)
    }

    @Test
    fun `missing owner or viewer gives nothing`() {
        assertTrue(ProfileAppearance.resolve(null, user("v")).isEmpty)
        assertTrue(ProfileAppearance.resolve(user("o", pro = proUntilFuture), null).isEmpty)
    }
}
