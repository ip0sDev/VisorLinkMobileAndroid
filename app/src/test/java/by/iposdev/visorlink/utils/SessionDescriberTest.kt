package org.visorlink.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.visorlink.app.data.repository.SessionInfo
import org.visorlink.app.data.repository.SessionRepository

/** Подпись сессий — как describeSession в src/utils/sessions.js. */
class SessionDescriberTest {

    private fun session(ua: String = "", client: String? = null, model: String? = null, os: String? = null, app: String? = null) =
        SessionInfo(
            id = "1700000000", userAgent = ua, createdAt = 0, lastSeenAt = 0, tfaVerified = true, isCurrent = false,
            client = client, deviceModel = model, osVersion = os, appVersion = app,
        )

    @Test
    fun `Android-приложение — VisorLink, модель и версия ОС`() {
        val d = SessionDescriber.describe(session(client = "android", model = "Google Pixel 8", os = "14", app = "4.3.00"))
        assertEquals("VisorLink 4.3.00 · Google Pixel 8 · Android 14", d.label)
        assertTrue(d.isMobile)
    }

    @Test
    fun `отсутствующие части опускаются, минимум — VisorLink · Android`() {
        assertEquals("VisorLink · Android", SessionDescriber.describe(session(client = "android")).label)
        assertEquals("VisorLink · Android 14", SessionDescriber.describe(session(client = "android", os = "14")).label)
        assertEquals("VisorLink 4.3 · Pixel · Android", SessionDescriber.describe(session(client = "android", model = "Pixel", app = "4.3")).label)
    }

    @Test
    fun `старые сессии с okhttp и Dalvik — тоже Android, даже без client`() {
        assertEquals("VisorLink · Android", SessionDescriber.describe(session(ua = "okhttp/4.12.0")).label)
        assertEquals("VisorLink · Android", SessionDescriber.describe(session(ua = "Dalvik/2.1.0 (Linux; U; Android 14)", client = "web")).label)
    }

    @Test
    fun `браузеры — по User-Agent`() {
        val chrome = SessionDescriber.describe(
            session(ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36", client = "web"),
        )
        assertEquals("Chrome · Windows", chrome.label)
        assertFalse(chrome.isMobile)
        val iphone = SessionDescriber.describe(
            session(ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"),
        )
        assertEquals("Safari · iPhone", iphone.label)
        assertTrue(iphone.isMobile)
        assertEquals("Edge · macOS", SessionDescriber.describe(session(ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Chrome/130.0 Safari/537.36 Edg/130.0")).label)
    }

    @Test
    fun `нераспознанное устройство — без подписи`() {
        assertNull(SessionDescriber.describe(session()).label)
        assertNull(SessionDescriber.describe(session(ua = "curl/8.0")).label)
    }

    @Test
    fun `модель устройства для registerSession`() {
        assertEquals("Google Pixel 8", SessionRepository.deviceModel("Google", "Pixel 8"))
        assertEquals("Samsung SM-S918B", SessionRepository.deviceModel("samsung", "SM-S918B"))
        assertEquals("OnePlus 9 Pro", SessionRepository.deviceModel("OnePlus", "OnePlus 9 Pro"))
        assertEquals("Pixel", SessionRepository.deviceModel("", "Pixel"))
        assertEquals(60, SessionRepository.deviceModel("Maker", "M".repeat(100)).length)
    }
}
