package org.visorlink.app.data.model.flags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFlagsTest {

    private fun flags(
        claims: Map<String, Any?> = emptyMap(),
        overrides: Map<String, Boolean> = emptyMap(),
        isDebug: Boolean = false
    ) = AppFlags(serverClaims = claims, localOverrides = overrides, isDebug = isDebug)

    @Test
    fun `flag is on only when the server sent true`() {
        val f = flags(mapOf("enable_alternative_outbox" to true, "other" to false, "count" to 3))
        assertTrue(f.isEnabled("enable_alternative_outbox"))
        assertFalse(f.isEnabled("other"))
        assertFalse(f.isEnabled("count"))
        assertFalse(f.isEnabled("missing"))
    }

    @Test
    fun `local override can turn a server flag off`() {
        val f = flags(mapOf("enable_alternative_outbox" to true), mapOf("enable_alternative_outbox" to false))
        assertFalse(f.isEnabled("enable_alternative_outbox"))
        assertEquals(false, f.overrideOf("enable_alternative_outbox"))
    }

    @Test
    fun `local override cannot turn on what the server did not enable`() {
        // Сохранённое когда-то «true» не держит фичу включённой после снятия на сервере
        val f = flags(mapOf("enable_alternative_outbox" to false), mapOf("enable_alternative_outbox" to true))
        assertFalse(f.isEnabled("enable_alternative_outbox"))
        assertFalse(flags(overrides = mapOf("anything" to true)).isEnabled("anything"))
    }

    @Test
    fun `flipper access follows the server test_flag, not the override`() {
        val f = flags(mapOf("test_flag" to true), mapOf("test_flag" to false))
        assertTrue("выключив test_flag во Flipper, нельзя потерять к нему доступ", f.testFlag)
        assertFalse("а оверлей и отладка Aegis выключаются", f.isEnabled("test_flag"))
        assertFalse(flags().testFlag)
    }

    @Test
    fun `service mode ignores overrides and is never flippable`() {
        val f = flags(mapOf("service_mode_enabled" to true), mapOf("service_mode_enabled" to false))
        assertTrue(f.serviceMode)
        assertTrue(f.isEnabled("service_mode_enabled"))
        assertFalse("service_mode_enabled" in f.flippableKeys)
        assertFalse(flags(mapOf("service_mode_enabled" to "true")).serviceMode)
    }

    @Test
    fun `flippable keys are the server-enabled ones with one name for aegis debug`() {
        val f = flags(
            mapOf(
                "test_flag" to true,
                "is_aegis_debug_mode" to true,
                "aegis_debug_mode_enabled" to true,
                "enable_alternative_outbox" to false,
                "heuristic_dict_url" to "https://example.org/dict.json",
            )
        )
        assertEquals(listOf("aegis_debug_mode_enabled", "test_flag"), f.flippableKeys)
    }

    @Test
    fun `aegis debug aliases share the server value and the override`() {
        val f = flags(mapOf("is_aegis_debug_mode" to true), mapOf("aegis_debug_mode_enabled" to false))
        assertTrue(f.serverValue("aegis_debug_mode_enabled"))
        assertFalse(f.isEnabled("is_aegis_debug_mode"))
        assertFalse(f.isEnabled("aegis_debug_mode_enabled"))
        assertEquals("aegis_debug_mode_enabled", AppFlags.canonical("is_aegis_debug_mode"))
    }

    @Test
    fun `profile navbar follows the server flag and can be switched off locally`() {
        assertFalse(flags().profileNavbar)
        assertTrue(flags(mapOf(AppFlags.PROFILE_NAVBAR to true)).profileNavbar)
        assertFalse(flags(mapOf(AppFlags.PROFILE_NAVBAR to true), mapOf(AppFlags.PROFILE_NAVBAR to false)).profileNavbar)
        assertFalse(flags(overrides = mapOf(AppFlags.PROFILE_NAVBAR to true)).profileNavbar)
    }

    @Test
    fun `string claims are read as config, not flags`() {
        val f = flags(mapOf("heuristic_dict_url" to "https://example.org/dict.json"))
        assertEquals("https://example.org/dict.json", f.heuristicDictUrl)
        assertNull(flags().heuristicDictUrl)
    }

    @Test
    fun `in debug mode all known app flags are flippable even if server sends nothing`() {
        val f = flags(claims = emptyMap(), isDebug = true)
        val expected = AppFlags.KNOWN_FLAGS.map(AppFlags::canonical).distinct().sorted()
        assertEquals(expected, f.flippableKeys)
    }

    @Test
    fun `in debug mode additional boolean claims from server are also flippable`() {
        val f = flags(
            claims = mapOf(
                "custom_server_flag" to false,
                "heuristic_dict_url" to "https://example.org/dict.json"
            ),
            isDebug = true
        )
        val expected = (AppFlags.KNOWN_FLAGS + "custom_server_flag").map(AppFlags::canonical).distinct().sorted()
        assertEquals(expected, f.flippableKeys)
        assertFalse("строковые клеймы не должны попадать во flippableKeys", "heuristic_dict_url" in f.flippableKeys)
    }

    @Test
    fun `in debug mode local override can enable flags when server sent false or nothing`() {
        val fWithoutServer = flags(overrides = mapOf("enable_profile_navbar" to true), isDebug = true)
        assertTrue(fWithoutServer.isEnabled("enable_profile_navbar"))
        assertTrue(fWithoutServer.profileNavbar)

        val fWithServerFalse = flags(
            claims = mapOf("enable_alternative_outbox" to false),
            overrides = mapOf("enable_alternative_outbox" to true),
            isDebug = true
        )
        assertTrue(fWithServerFalse.isEnabled("enable_alternative_outbox"))
    }

    @Test
    fun `in debug mode service mode can be overridden locally for testing`() {
        val f = flags(
            claims = mapOf("service_mode_enabled" to false),
            overrides = mapOf("service_mode_enabled" to true),
            isDebug = true
        )
        assertTrue(f.serviceMode)
        assertTrue(f.isEnabled("service_mode_enabled"))
    }

    // ── force_update_min_version ──────────────────────────────────────────────

    @Test
    fun `force update is required only when the minimum is above this build`() {
        assertTrue(flags(mapOf("force_update_min_version" to 181)).forceUpdateRequired(versionCode = 180))
        assertFalse("та же сборка — можно работать", flags(mapOf("force_update_min_version" to 180)).forceUpdateRequired(versionCode = 180))
        assertFalse(flags(mapOf("force_update_min_version" to 150)).forceUpdateRequired(versionCode = 180))
        assertFalse("нет параметра — не требуется", flags().forceUpdateRequired(versionCode = 180))
    }

    @Test
    fun `force update minimum accepts numbers and numeric strings`() {
        assertEquals(181, flags(mapOf("force_update_min_version" to 181.0)).forceUpdateMinVersion)
        assertEquals(181, flags(mapOf("force_update_min_version" to " 181 ")).forceUpdateMinVersion)
        assertNull(flags(mapOf("force_update_min_version" to "soon")).forceUpdateMinVersion)
        assertNull(flags(mapOf("force_update_min_version" to true)).forceUpdateMinVersion)
    }

    @Test
    fun `force update cannot be switched off locally`() {
        val f = flags(mapOf("force_update_min_version" to 999), overrides = mapOf("force_update_min_version" to false))
        assertTrue(f.forceUpdateRequired(versionCode = 180))
    }
}
