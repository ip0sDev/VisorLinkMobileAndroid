package org.visorlink.app.data.activity

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.visorlink.app.data.repository.FlagsRepository
import java.time.LocalDate
import kotlin.coroutines.resume

/**
 * Сэмплирование шагомера: разово снимает показание кумулятивного TYPE_STEP_COUNTER
 * и отправляет дневной отчёт. Никаких фоновых служб — только точечные сэмплы
 * (экран открыт, воркер) и ноль wakelock'ов; счётчик считается сам, sensor hub'ом.
 */
class StepSampler(
    private val context: Context,
    private val prefs: StepPrefs,
    private val flagsRepository: FlagsRepository,
    private val repository: ActivityRepository
) {

    /**
     * Снять сэмпл и отправить отчёт. `null` — сэмплировать нечего: нет согласия,
     * флаг выключен, сенсора нет или устройство ещё не спарено с Hermes.
     * @throws org.visorlink.app.data.remote.EconomyException при отказе сервера (воркер ретраит)
     */
    suspend fun sampleAndReport(): StepReportResult? {
        if (!prefs.enabled) return null
        if (!flagsRepository.flags.value.stepActivities) return null

        val cumulative = readCumulativeOnce() ?: run {
            Log.i(TAG, "No TYPE_STEP_COUNTER sensor")
            return null
        }

        val now = System.currentTimeMillis()
        val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        val bootDay = LocalDate.ofEpochDay((now - SystemClock.elapsedRealtime()) / DAY_MS).toString()
        val day = LocalDate.now().toString()

        val sample = StepCounterModel.onSample(prefs.state(), bootCount, bootDay, cumulative, now, day)
        prefs.save(sample.state)
        return repository.report(sample.todaySteps, day)
    }

    /**
     * Одно показание счётчика: регистрируем слушателя, снимаем первое значение, отписываемся.
     * TYPE_STEP_COUNTER отдаёт накопленное значение сразу после регистрации.
     */
    private suspend fun readCumulativeOnce(): Int? {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(SAMPLE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values.firstOrNull()?.toInt())
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
                cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
            }
        }
    }

    private companion object {
        const val TAG = "StepSampler"
        const val SAMPLE_TIMEOUT_MS = 3_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
