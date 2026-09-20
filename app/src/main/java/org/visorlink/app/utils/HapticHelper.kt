package org.visorlink.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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

class HapticHelper(private val context: Context) {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun perform(type: HapticType, enabled: Boolean) {
        if (!enabled) return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (type) {
                HapticType.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticType.LONG_PRESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HapticType.SUCCESS -> VibrationEffect.createWaveform(
                    longArrayOf(0, 40, 60, 40), intArrayOf(0, 180, 0, 120), -1
                )
                HapticType.ERROR -> VibrationEffect.createWaveform(
                    longArrayOf(0, 80, 40, 80, 40, 80), intArrayOf(0, 200, 0, 200, 0, 200), -1
                )
                HapticType.MESSAGE_SENT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticType.MESSAGE_RECEIVED -> VibrationEffect.createWaveform(
                    longArrayOf(0, 30, 50, 30), intArrayOf(0, 120, 0, 80), -1
                )
                HapticType.REACTION -> VibrationEffect.createWaveform(
                    longArrayOf(0, 20, 30, 20), intArrayOf(0, 100, 0, 60), -1
                )
                HapticType.SELECTION -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val duration = when (type) {
                HapticType.CLICK, HapticType.SELECTION, HapticType.MESSAGE_SENT -> 20L
                HapticType.LONG_PRESS -> 50L
                HapticType.SUCCESS, HapticType.MESSAGE_RECEIVED, HapticType.REACTION -> 35L
                HapticType.ERROR -> 100L
            }
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}

@Composable
fun rememberHaptic(): HapticHelper {
    val context = LocalContext.current
    return remember { HapticHelper(context) }
}