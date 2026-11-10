package org.visorlink.app.ui.screens.signal

import androidx.annotation.StringRes
import org.visorlink.app.R
import org.visorlink.app.data.activity.StepCounterModel
import org.visorlink.app.data.model.aegis.LinkEmotion
import org.visorlink.app.data.model.aegis.VisorIcon

/** Настроение Линка на странице шагов: эмоция, иконка визора и реплика. */
data class LinkMood(
    val emotion: LinkEmotion,
    val visor: VisorIcon?,
    @StringRes val messageRes: Int,
    val args: List<Any> = emptyList()
)

/**
 * Чистый маппинг состояния шагов → как себя ведёт Линк.
 * Реплики — строковые ресурсы (RU/EN), аргументы подставляются при показе.
 */
object LinkStepsMood {

    fun mood(state: StepsViewModel.UiState): LinkMood = when {
        !state.enabled -> LinkMood(LinkEmotion.IDLE, VisorIcon.SMILE, R.string.link_step_invite)

        state.errorReason == "rebind-wait" ->
            LinkMood(LinkEmotion.SAD, VisorIcon.CROSS, R.string.link_step_rebind)
        state.errorReason == "device-unknown" || state.errorReason == "bad-signature" ->
            LinkMood(LinkEmotion.CONCERNED, VisorIcon.CROSS, R.string.link_step_device)
        state.errorReason != null ->
            LinkMood(LinkEmotion.CONCERNED, VisorIcon.EXCLAMATION, R.string.link_step_error)

        state.sensorMissing ->
            LinkMood(LinkEmotion.SLEEPY, VisorIcon.ZZZ, R.string.link_step_sensor)

        state.reporting ->
            LinkMood(LinkEmotion.CURIOUS, VisorIcon.DOTS, R.string.link_step_counting)

        state.trustReason != null ->
            LinkMood(LinkEmotion.CONFUSED, VisorIcon.QUESTION, R.string.link_step_trust)

        state.steps >= (state.thresholds.lastOrNull() ?: Int.MAX_VALUE) ->
            LinkMood(LinkEmotion.PARTY, VisorIcon.CHECKMARK, R.string.link_step_done)

        // Стрик продлён и первый порог уже взят — празднуем двойную цену
        state.boost && state.steps >= (state.thresholds.firstOrNull() ?: Int.MAX_VALUE) ->
            LinkMood(LinkEmotion.PARTY, VisorIcon.SMILE, R.string.link_step_boost)

        else -> {
            val next = StepCounterModel.nextThreshold(state.steps, state.thresholds)
            if (next == null) {
                LinkMood(LinkEmotion.PARTY, VisorIcon.CHECKMARK, R.string.link_step_done)
            } else {
                val per = if (state.boost) StepCounterModel.AWARD_BOOST else StepCounterModel.AWARD_BASE
                LinkMood(
                    LinkEmotion.HAPPY,
                    VisorIcon.SMILE,
                    R.string.link_step_go,
                    listOf(next - state.steps, per)
                )
            }
        }
    }
}
