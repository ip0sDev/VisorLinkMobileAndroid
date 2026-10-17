package org.visorlink.app.utils

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import org.koin.compose.getKoin
import org.visorlink.app.data.repository.SettingsRepository

enum class HapticType {
    CLICK,          // обычный тап
    LONG_PRESS,     // длинный тап
    SUCCESS,        // успешное действие
    ERROR,          // ошибка
    MESSAGE_SENT,   // отправка сообщения
    MESSAGE_RECEIVED, // получение сообщения
    REACTION,       // реакция на сообщение
    SELECTION,      // выбор в списке
}

/** Общий вход к вибромотору: один способ получить его и один способ запустить эффект. */
internal object HapticEngine {
    fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /**
     * Эффект с usage TOUCH: система применяет «Вибрацию при касании» и режим «Не беспокоить».
     * Без атрибутов вибрация шла бы как usage UNKNOWN и обходила эти настройки.
     */
    fun vibrate(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }

    /** Составной эффект из примитивов или null, если мотор не умеет хотя бы один из них. */
    fun composition(vibrator: Vibrator, vararg steps: Triple<Int, Float, Int>): VibrationEffect? {
        val ids = steps.map { it.first }.toIntArray()
        if (!vibrator.areAllPrimitivesSupported(*ids)) return null
        return VibrationEffect.startComposition().apply {
            steps.forEach { (id, scale, delay) -> addPrimitive(id, scale.coerceIn(0f, 1f), delay) }
        }.compose()
    }
}

/**
 * Единый слой тактильной отдачи. Простые касания идут через [View.performHapticFeedback] —
 * тот же канал, что у системных элементов (уважает системные настройки и тему устройства).
 * Богатые события (успех, ошибка, реакция, новое сообщение) — композиции примитивов, а на
 * моторах без них — ближайшая системная константа. Настройка «Вибрация» читается здесь же,
 * поэтому забытый флаг у вызывающего её не обходит.
 */
class HapticHelper internal constructor(
    context: Context,
    private val view: View,
    private val settings: SettingsRepository?,
) {
    private val vibrator: Vibrator? by lazy { HapticEngine.vibrator(context) }
    private var lastAt = 0L

    fun perform(type: HapticType, enabled: Boolean = true) {
        if (!enabled || settings?.hapticEnabled?.value == false) return
        // Антидребезг: несколько событий в одном кадре (тап + выбор) слились бы в кашу
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < MIN_GAP_MS) return
        lastAt = now

        when (type) {
            HapticType.CLICK -> view.tap(HapticFeedbackConstants.VIRTUAL_KEY)
            HapticType.LONG_PRESS -> view.tap(HapticFeedbackConstants.LONG_PRESS)
            HapticType.SELECTION, HapticType.MESSAGE_SENT -> view.tap(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackConstants.SEGMENT_TICK
                else HapticFeedbackConstants.CLOCK_TICK
            )
            HapticType.SUCCESS -> rich(
                HapticFeedbackConstants.CONFIRM,
                Triple(PRIMITIVE_CLICK, 0.6f, 0),
                Triple(PRIMITIVE_CLICK, 1f, 90),
            )
            HapticType.ERROR -> rich(
                HapticFeedbackConstants.REJECT,
                Triple(PRIMITIVE_CLICK, 1f, 0),
                Triple(PRIMITIVE_CLICK, 0.8f, 70),
                Triple(PRIMITIVE_CLICK, 0.6f, 70),
            )
            HapticType.MESSAGE_RECEIVED -> rich(
                HapticFeedbackConstants.CLOCK_TICK,
                Triple(PRIMITIVE_TICK, 0.5f, 0),
                Triple(PRIMITIVE_TICK, 0.35f, 60),
            )
            HapticType.REACTION -> rich(
                HapticFeedbackConstants.CONTEXT_CLICK,
                Triple(PRIMITIVE_CLICK, 0.4f, 0),
                Triple(PRIMITIVE_TICK, 0.5f, 40),
            )
        }
    }

    private fun View.tap(constant: Int) {
        performHapticFeedback(constant)
    }

    private fun rich(fallbackConstant: Int, vararg steps: Triple<Int, Float, Int>) {
        val v = vibrator
        val effect = if (v != null && v.hasVibrator()) HapticEngine.composition(v, *steps) else null
        if (v != null && effect != null) HapticEngine.vibrate(v, effect) else view.tap(fallbackConstant)
    }

    private companion object {
        const val MIN_GAP_MS = 25L
        const val PRIMITIVE_CLICK = VibrationEffect.Composition.PRIMITIVE_CLICK
        const val PRIMITIVE_TICK = VibrationEffect.Composition.PRIMITIVE_TICK
    }
}

/** Настройка «Вибрация» берётся из SettingsRepository, если он есть (в скриншот-тестах Koin — заглушка). */
@Composable
fun rememberHaptic(): HapticHelper {
    val context = LocalContext.current
    val view = LocalView.current
    val koin = getKoin()
    return remember(context, view) { HapticHelper(context, view, koin.getOrNull<SettingsRepository>()) }
}
