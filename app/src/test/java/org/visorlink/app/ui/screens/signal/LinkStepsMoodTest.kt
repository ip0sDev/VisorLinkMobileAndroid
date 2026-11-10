package org.visorlink.app.ui.screens.signal

import org.junit.Assert.assertEquals
import org.junit.Test
import org.visorlink.app.data.activity.StepCounterModel
import org.visorlink.app.data.model.aegis.LinkEmotion
import org.visorlink.app.data.model.aegis.VisorIcon

class LinkStepsMoodTest {

    private fun state(
        enabled: Boolean = true,
        steps: Int = 0,
        boost: Boolean = false,
        thresholds: List<Int> = StepCounterModel.THRESHOLDS,
        reporting: Boolean = false,
        errorReason: String? = null,
        trustReason: String? = null,
        sensorMissing: Boolean = false
    ) = StepsViewModel.UiState(
        enabled = enabled,
        steps = steps,
        boost = boost,
        thresholds = thresholds,
        reporting = reporting,
        errorReason = errorReason,
        trustReason = trustReason,
        sensorMissing = sensorMissing
    )

    @Test
    fun `disabled link invites`() {
        val mood = LinkStepsMood.mood(state(enabled = false))
        assertEquals(LinkEmotion.IDLE, mood.emotion)
        assertEquals(VisorIcon.SMILE, mood.visor)
    }

    @Test
    fun `fresh day is a cheerful go`() {
        val mood = LinkStepsMood.mood(state(steps = 0))
        assertEquals(LinkEmotion.HAPPY, mood.emotion)
        assertEquals(1000, mood.args.first())
    }

    @Test
    fun `mid progress points to the next threshold`() {
        val mood = LinkStepsMood.mood(state(steps = 1200))
        assertEquals(LinkEmotion.HAPPY, mood.emotion)
        assertEquals(1800, mood.args.first()) // до 3000
        assertEquals(StepCounterModel.AWARD_BASE, mood.args.last())
    }

    @Test
    fun `all thresholds reached is a party`() {
        val mood = LinkStepsMood.mood(state(steps = 12000))
        assertEquals(LinkEmotion.PARTY, mood.emotion)
        assertEquals(VisorIcon.CHECKMARK, mood.visor)
    }

    @Test
    fun `boost day with first threshold taken is celebrated`() {
        val mood = LinkStepsMood.mood(state(steps = 1500, boost = true))
        assertEquals(LinkEmotion.PARTY, mood.emotion)
    }

    @Test
    fun `boost without any threshold yet still counts down with double price`() {
        val mood = LinkStepsMood.mood(state(steps = 500, boost = true))
        assertEquals(LinkEmotion.HAPPY, mood.emotion)
        assertEquals(StepCounterModel.AWARD_BOOST, mood.args.last())
    }

    @Test
    fun `reporting shows counting dots`() {
        val mood = LinkStepsMood.mood(state(steps = 1500, reporting = true))
        assertEquals(VisorIcon.DOTS, mood.visor)
    }

    @Test
    fun `trust pending is confused but explains`() {
        val mood = LinkStepsMood.mood(state(steps = 1500, trustReason = "trust-activity"))
        assertEquals(LinkEmotion.CONFUSED, mood.emotion)
        assertEquals(VisorIcon.QUESTION, mood.visor)
    }

    @Test
    fun `server errors are sad faces`() {
        assertEquals(LinkEmotion.SAD, LinkStepsMood.mood(state(errorReason = "rebind-wait")).emotion)
        assertEquals(LinkEmotion.CONCERNED, LinkStepsMood.mood(state(errorReason = "bad-signature")).emotion)
        assertEquals(LinkEmotion.CONCERNED, LinkStepsMood.mood(state(errorReason = "device-unknown")).emotion)
        assertEquals(LinkEmotion.CONCERNED, LinkStepsMood.mood(state(errorReason = "other")).emotion)
    }

    @Test
    fun `missing sensor makes link sleepy`() {
        val mood = LinkStepsMood.mood(state(sensorMissing = true))
        assertEquals(LinkEmotion.SLEEPY, mood.emotion)
        assertEquals(VisorIcon.ZZZ, mood.visor)
    }
}
