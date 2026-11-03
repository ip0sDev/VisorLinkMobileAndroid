package org.visorlink.app.data.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class SignalModelsTest {

    private val h = 3600L * 1000
    private val day = 24L * h

    @Test
    fun testMilestones() {
        assertEquals(Milestone(day = 7, bonus = 150, left = 7), nextMilestone(0))
        assertEquals(Milestone(day = 30, bonus = 500, left = 23), nextMilestone(7))
        assertEquals(Milestone(day = 100, bonus = 2000, left = 70), nextMilestone(30))
        assertEquals(Milestone(day = 300, bonus = 2000, left = 50), nextMilestone(250))

        assertEquals(0, prevMilestoneDay(3))
        assertEquals(7, prevMilestoneDay(12))
        assertEquals(30, prevMilestoneDay(99))
        assertEquals(200, prevMilestoneDay(250))
    }

    @Test
    fun testStreakTiming() {
        val last = 1000L * day

        assertEquals(StreakState.DONE, streakTiming(5, last, last + 10 * h).state)
        assertEquals(StreakState.OPEN, streakTiming(5, last, last + 30 * h).state)
        assertEquals(StreakState.LOST, streakTiming(5, last, last + 49 * h).state)
        assertEquals(StreakState.NONE, streakTiming(0, last, last + h).state)

        // Freezes cover extra days past 48h
        val t = streakTiming(5, last, last + 50 * h, freezes = 1)
        assertEquals(StreakState.FROZEN, t.state)
        assertEquals(1, t.freezesNeeded)
        assertEquals(last + 72 * h, t.burnAt)
        assertEquals(StreakState.LOST, streakTiming(5, last, last + 73 * h, freezes = 1).state)
        assertEquals(2, streakTiming(5, last, last + 73 * h, freezes = 2).freezesNeeded)
    }

    @Test
    fun testFreezeStock() {
        val now = 1000L * day

        assertEquals(FreezeStock(freezes = 1, nextFreeAt = 0L), freezeStock(freezes = 0, grantAt = now - 31 * day, pro = true, now = now))
        assertEquals(FreezeStock(freezes = 2, nextFreeAt = 0L), freezeStock(freezes = 2, grantAt = now - 31 * day, pro = true, now = now))
        assertEquals(FreezeStock(freezes = 1, nextFreeAt = now + 20 * day), freezeStock(freezes = 1, grantAt = now - 10 * day, pro = true, now = now))
        assertEquals(FreezeStock(freezes = 1, nextFreeAt = 0L), freezeStock(freezes = 1, grantAt = 0L, pro = false, now = now))
    }

    @Test
    fun testActivityLevels() {
        val input = listOf(0, 1, 3, 4, 10, 11, 30, 31)
        val expected = listOf(0, 1, 1, 2, 2, 3, 3, 4)
        assertEquals(expected, input.map(::activityLevel))
    }

    @Test
    fun testCalendarBuilding() {
        val todayCal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 29, 12, 0, 0)
        }
        val cal = buildCalendar(
            days = mapOf("2026-10-29" to 5, "2026-10-01" to 40, "2026-10-30" to 9),
            weeks = 26,
            todayMillis = todayCal.timeInMillis
        )
        assertEquals(26, cal.columns.size)
        assertEquals(45, cal.total)
        assertEquals(2, cal.activeDays)

        val lastCol = cal.columns.last()
        assertEquals("2026-10-29", lastCol[3].key)
        assertEquals(2, lastCol[3].level)
        assertTrue(lastCol[4].future)
        assertEquals(0, lastCol[4].count)
    }

    @Test
    fun testTopPeers() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 31, 12, 0, 0)
        }
        val now = nowCal.timeInMillis

        val peers = mapOf(
            "a" to SignalPeer(total = 900, last = 1L, recent = mapOf("2026-10-30" to 3)),
            "b" to SignalPeer(total = 50, last = 2L, recent = mapOf("2026-10-29" to 10, "2026-08-01" to 99)),
            "c" to SignalPeer(total = 5000, last = 3L, recent = emptyMap())
        )

        val recentTop = topPeers(peers, allTime = false, now = now).map { it.uid }
        assertEquals(listOf("b", "a"), recentTop)

        val allTimeTop = topPeers(peers, allTime = true, now = now).map { it.uid }
        assertEquals(listOf("c", "a", "b"), allTimeTop)

        val excludeTop = topPeers(peers, allTime = true, exclude = setOf("c"), now = now).map { it.uid }
        assertEquals(listOf("a", "b"), excludeTop)
    }

    @Test
    fun testUnseenAchievements() {
        val now = 1000L * day
        val unlocked = mapOf(
            "id_card" to now - 40 * day,
            "first_gift" to now - h,
            "first_trade" to now - 2 * h
        )

        val freshNoSeen = unseenAchievements(unlocked, seen = null, now = now).map { it.a.id }
        assertEquals(listOf("first_trade", "first_gift"), freshNoSeen)

        val freshWithSeen = unseenAchievements(unlocked, seen = setOf("id_card", "first_trade"), now = now).map { it.a.id }
        assertEquals(listOf("first_gift"), freshWithSeen)
    }

    @Test
    fun testFormatLeft() {
        val now = 1000L * day
        assertEquals("12 мин", formatLeft(now + 12 * 60 * 1000, now))
        assertEquals("5 ч", formatLeft(now + 5 * 3600 * 1000, now))
        assertEquals("5 ч 12 мин", formatLeft(now + 5 * 3600 * 1000 + 12 * 60 * 1000, now))
    }
}
