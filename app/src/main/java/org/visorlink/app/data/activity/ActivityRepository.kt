package org.visorlink.app.data.activity

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.visorlink.app.data.remote.EconomyCaller
import org.visorlink.app.data.remote.EconomyException
import org.visorlink.app.data.remote.flags.HermesKeyManager
import org.visorlink.app.data.repository.FlagsRepository
import java.util.UUID

/** Ответ activityReport: пороги сервер — единый источник для прогресса в UI. */
data class StepReportResult(
    val steps: Int,
    val awarded: Int,
    val todayBits: Int,
    val thresholds: List<Int>,
    val trustOk: Boolean,
    val trustReason: String?,
    val trustWaitMs: Long,
    val trustDaysLeft: Long
)

/** Документ дня users/{uid}/activity/{date} — серверное «шаги и начислено сегодня». */
data class ActivityDay(
    val steps: Int = 0,
    val awarded: Int = 0,
    val boost: Boolean = false,
    val trustPending: Boolean = false
)

/**
 * Отчёты об активности: callable `activityReport` с ECDSA-подписью устройства Hermes
 * (каноническая строка `deviceId+timestamp+nonce+date+steps` — тот же стиль, что в /api/v1/config)
 * и чтение дневного документа из Firestore.
 */
class ActivityRepository(
    private val flagsRepository: FlagsRepository,
    private val keyManager: HermesKeyManager,
    private val db: FirebaseFirestore
) {

    /**
     * Отправить дневной тотал шагов. Бросает [EconomyException] при отказе сервера —
     * воркер решает, ретраить или нет.
     */
    suspend fun report(todaySteps: Int, date: String): StepReportResult {
        val deviceId = flagsRepository.getDeviceId()
            ?: throw EconomyException(reason = "not-paired", message = "Hermes device not paired")
        val timestamp = System.currentTimeMillis() / 1000
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val signature = keyManager.signString("$deviceId$timestamp$nonce$date$todaySteps")

        val res = EconomyCaller.callEconomy(
            "activityReport",
            mapOf(
                "device_id" to deviceId,
                "timestamp" to timestamp,
                "nonce" to nonce,
                "signature" to signature,
                "date" to date,
                "steps" to todaySteps
            )
        )
        val trust = res["trust"] as? Map<*, *>
        return StepReportResult(
            steps = (res["steps"] as? Number)?.toInt() ?: todaySteps,
            awarded = (res["awarded"] as? Number)?.toInt() ?: 0,
            todayBits = (res["todayBits"] as? Number)?.toInt() ?: 0,
            thresholds = (res["thresholds"] as? List<*>)
                ?.filterIsInstance<Number>()?.map { it.toInt() }
                ?.takeIf { it.isNotEmpty() } ?: StepCounterModel.THRESHOLDS,
            trustOk = trust?.get("ok") as? Boolean ?: true,
            trustReason = trust?.get("reason") as? String,
            trustWaitMs = (trust?.get("waitMs") as? Number)?.toLong() ?: 0L,
            trustDaysLeft = (trust?.get("daysLeft") as? Number)?.toLong() ?: 0L
        )
    }

    /** Серверное состояние дня: steps/awarded приходят после клампа и идемпотентного начисления. */
    fun todayActivity(uid: String, date: String): Flow<ActivityDay?> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        var reg: ListenerRegistration? = null
        reg = db.collection("users").document(uid).collection("activity").document(date)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w("ActivityRepository", "activity/$date error: ${err.message}")
                    trySend(null)
                    return@addSnapshotListener
                }
                val d = snap?.data
                trySend(
                    if (d == null) null
                    else ActivityDay(
                        steps = (d["steps"] as? Number)?.toInt() ?: 0,
                        awarded = (d["awarded"] as? Number)?.toInt() ?: 0,
                        boost = d["boost"] == true,
                        trustPending = d["trustPending"] == true
                    )
                )
            }
        awaitClose { reg?.remove() }
    }

    /** «Выключить и удалить мои данные о шагах» — callable activityDelete чистит серверную историю. */
    suspend fun deleteServerActivity() {
        try {
            EconomyCaller.callEconomy("activityDelete")
        } catch (e: EconomyException) {
            Log.w("ActivityRepository", "activityDelete failed: ${e.reason}")
        }
    }
}
