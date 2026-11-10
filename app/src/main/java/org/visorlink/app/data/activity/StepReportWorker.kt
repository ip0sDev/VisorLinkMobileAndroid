package org.visorlink.app.data.activity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.visorlink.app.data.remote.EconomyException
import java.util.concurrent.TimeUnit

/**
 * Периодический сэмпл шагов (30 мин). WorkManager переживёт перезагрузку сам,
 * doze-задержки не страшны: счётчик кумулятивный, один сэмпл возвращает всё.
 */
class StepReportWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params), KoinComponent {

    override suspend fun doWork(): Result {
        if (runAttemptCount > MAX_ATTEMPTS) return Result.failure()
        val sampler: StepSampler = get()
        return try {
            sampler.sampleAndReport()
            Result.success()
        } catch (e: EconomyException) {
            // Финальные отказы сервера не ретраим: подпись/устройство/ребайнд сами не починятся
            if (e.reason in NO_RETRY_REASONS) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "step_report_periodic"
        private const val MAX_ATTEMPTS = 5
        private val NO_RETRY_REASONS = setOf("bad-signature", "device-unknown", "rebind-wait", "not-paired")

        /** Идемпотентно включить воркер (KEEP). Вызывать при согласии пользователя и на старте приложения. */
        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<StepReportWorker>(30, TimeUnit.MINUTES).build()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
