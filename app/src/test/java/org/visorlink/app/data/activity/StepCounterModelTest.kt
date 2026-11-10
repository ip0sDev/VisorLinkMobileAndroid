package org.visorlink.app.data.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepCounterModelTest {

    private val day = "2026-11-07"
    private val now = 1_000_000L * 1000

    @Test
    fun `first run starts from zero and takes the counter as baseline`() {
        val sample = StepCounterModel.onSample(
            saved = StepSampleState(),
            bootCount = 12, bootDayKey = day, cumulative = 4321, nowMs = now, dayKey = day
        )
        assertEquals(0, sample.todaySteps)
        assertEquals(4321, sample.state.baseline)
        assertEquals(12, sample.state.bootCount)
    }

    @Test
    fun `same day accumulates delta from baseline`() {
        val saved = StepSampleState(baseline = 1000, bootCount = 12, lastDay = day, lastSteps = 500, lastSampleAt = now)
        val sample = StepCounterModel.onSample(saved, 12, day, 1750, now + 60_000, day)
        assertEquals(1250, sample.todaySteps)
        assertEquals(1750, sample.state.baseline)
    }

    @Test
    fun `counter going down never reduces the day total`() {
        val saved = StepSampleState(baseline = 1000, bootCount = 12, lastDay = day, lastSteps = 500, lastSampleAt = now)
        val sample = StepCounterModel.onSample(saved, 12, day, 900, now + 60_000, day)
        assertEquals(500, sample.todaySteps)
    }

    @Test
    fun `new day takes steps since last sample`() {
        val saved = StepSampleState(baseline = 5000, bootCount = 12, lastDay = "2026-11-06", lastSteps = 8000, lastSampleAt = now)
        val sample = StepCounterModel.onSample(saved, 12, day, 6200, now, day)
        assertEquals(1200, sample.todaySteps)
    }

    @Test
    fun `reboot today counts steps since boot`() {
        val saved = StepSampleState(baseline = 4000, bootCount = 11, lastDay = day, lastSteps = 3000, lastSampleAt = now)
        val sample = StepCounterModel.onSample(saved, 12, day, 900, now + 60_000, day)
        assertEquals(900, sample.todaySteps)
        assertEquals(12, sample.state.bootCount)
        assertEquals(900, sample.state.lastSteps)
    }

    @Test
    fun `reboot before today starts the day from zero`() {
        val yesterday = "2026-11-06"
        val saved = StepSampleState(baseline = 4000, bootCount = 11, lastDay = yesterday, lastSteps = 3000, lastSampleAt = now)
        val sample = StepCounterModel.onSample(saved, 12, yesterday, 900, now, day)
        assertEquals(0, sample.todaySteps)
    }

    @Test
    fun `next threshold walks the ladder`() {
        assertEquals(1000, StepCounterModel.nextThreshold(0))
        assertEquals(3000, StepCounterModel.nextThreshold(1000))
        assertEquals(10000, StepCounterModel.nextThreshold(6001))
        assertNull(StepCounterModel.nextThreshold(10000))
    }

    @Test
    fun `crossed count matches thresholds`() {
        assertEquals(0, StepCounterModel.crossedCount(999))
        assertEquals(2, StepCounterModel.crossedCount(3000))
        assertEquals(4, StepCounterModel.crossedCount(15000))
    }
}
