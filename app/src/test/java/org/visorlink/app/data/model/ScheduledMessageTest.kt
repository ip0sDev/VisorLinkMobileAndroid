package org.visorlink.app.data.model

import org.junit.Assert.*
import org.junit.Test

class ScheduledMessageTest {

    @Test
    fun testSchedulePresets() {
        val now = 1000000000L
        val presets = schedulePresets(now)
        assertTrue(presets.isNotEmpty())

        // All preset timestamps must be strictly in the future
        for (preset in presets) {
            assertTrue(preset.atMs > now)
        }
    }

    @Test
    fun testScheduleTimeError() {
        val now = 1000000000L

        // In the past or too soon (< 60 seconds)
        assertEquals("Не раньше чем через минуту.", scheduleTimeError(now - 1000L, now))
        assertEquals("Не раньше чем через минуту.", scheduleTimeError(now + 10000L, now))

        // Too far ahead (> 365 days)
        val wayAhead = now + ScheduleRules.MAX_AHEAD_MS + 1000000L
        assertEquals("Не позже чем через год.", scheduleTimeError(wayAhead, now))

        // Valid delay (e.g. 1 hour)
        assertNull(scheduleTimeError(now + 3600000L, now))
    }

    @Test
    fun testScheduledReasonText() {
        assertEquals("Не отправлено из-за ошибки сервера.", scheduledReasonText(null))
        assertEquals("Отложенные сообщения доступны с подпиской PRO.", scheduledReasonText("pro-required"))
        assertEquals("Запланировать можно не больше 20 сообщений.", scheduledReasonText("limit"))
        assertEquals("Текст сообщения: от 1 до 2000 символов.", scheduledReasonText("bad-text"))
        assertEquals("Время отправки: от минуты до года вперёд.", scheduledReasonText("bad-time"))
        assertEquals("Не отправлено: вы заблокированы в этом чате.", scheduledReasonText("banned"))
    }
}
