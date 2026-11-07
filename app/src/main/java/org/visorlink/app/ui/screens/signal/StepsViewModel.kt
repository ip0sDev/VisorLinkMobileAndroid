package org.visorlink.app.ui.screens.signal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import org.visorlink.app.data.activity.ActivityDay
import org.visorlink.app.data.activity.StepCounterModel
import org.visorlink.app.data.activity.StepPrefs
import org.visorlink.app.data.activity.StepReportWorker
import org.visorlink.app.data.activity.StepSampler
import org.visorlink.app.data.activity.ActivityRepository
import org.visorlink.app.data.remote.EconomyException
import java.time.LocalDate

/**
 * Шаги во «Сигнале»: локальный сэмпл счётчика + серверный документ дня
 * (steps/awarded приходит только с сервера — он и есть источник правды по Bits).
 */
class StepsViewModel(
    private val appContext: Context,
    private val prefs: StepPrefs,
    private val sampler: StepSampler,
    private val repository: ActivityRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    data class UiState(
        val enabled: Boolean = false,
        /** Шаги сегодня: свежий локальный сэмпл либо серверный док. */
        val steps: Int = 0,
        val serverAwarded: Int = 0,
        val boost: Boolean = false,
        val thresholds: List<Int> = StepCounterModel.THRESHOLDS,
        val reporting: Boolean = false,
        /** Причина отказа сервера: 'rebind-wait', 'bad-signature', 'device-unknown'… */
        val errorReason: String? = null,
        val errorWaitMs: Long = 0L,
        val trustReason: String? = null,
        val trustDaysLeft: Long = 0L,
        val sensorMissing: Boolean = false
    )

    private val _ui = MutableStateFlow(UiState(enabled = prefs.enabled))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        if (prefs.enabled) {
            observeServerDay()
            // Шаги с прошлого сэмпла подтягиваются при первом открытии вкладки
            sampleNow()
        }
    }

    private fun observeServerDay() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.todayActivity(uid, LocalDate.now().toString()).collect { day ->
                _ui.update {
                    it.copy(
                        steps = it.steps.coerceAtLeast(day?.steps ?: 0),
                        serverAwarded = day?.awarded ?: 0,
                        boost = day?.boost ?: false,
                        trustReason = if (day?.trustPending == true) it.trustReason ?: "trust-pending" else null
                    )
                }
            }
        }
    }

    fun sampleNow() {
        if (!prefs.enabled || _ui.value.reporting) return
        _ui.update { it.copy(reporting = true, errorReason = null) }
        viewModelScope.launch {
            try {
                val result = sampler.sampleAndReport()
                _ui.update { current ->
                    if (result == null) current.copy(reporting = false)
                    else current.copy(
                        reporting = false,
                        steps = result.steps,
                        serverAwarded = result.todayBits,
                        thresholds = result.thresholds,
                        trustReason = if (result.trustOk) null else (result.trustReason ?: "trust-pending"),
                        trustDaysLeft = result.trustDaysLeft
                    )
                }
            } catch (e: EconomyException) {
                _ui.update { it.copy(reporting = false, errorReason = e.reason ?: "error", errorWaitMs = e.waitMs) }
            } catch (e: Exception) {
                _ui.update { it.copy(reporting = false, errorReason = "error") }
            }
        }
    }

    fun markSensorMissing() = _ui.update { it.copy(sensorMissing = true) }

    /** Согласие дано и разрешение получено: включаем сэмплирование и воркер. */
    fun enable() {
        prefs.enabled = true
        StepReportWorker.ensureScheduled(appContext)
        _ui.update { it.copy(enabled = true, errorReason = null, trustReason = null) }
        observeServerDay()
        sampleNow()
    }

    /** Выключить; с удалением серверной истории — по обещанию приватности. */
    fun disable(deleteServerData: Boolean) {
        prefs.enabled = false
        StepReportWorker.cancel(appContext)
        _ui.update { UiState(enabled = false) }
        if (deleteServerData) {
            viewModelScope.launch { repository.deleteServerActivity() }
        }
    }
}
