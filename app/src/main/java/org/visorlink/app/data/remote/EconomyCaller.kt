package org.visorlink.app.data.remote

import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await
import kotlin.math.ceil

/**
 * Ошибка callable экономики с причиной из `details` (веб `idCardErrorInfo`): `reason`, сколько ждать
 * (`waitMs`), сколько ещё активных дней нужно (`daysLeft`), текст сервера.
 */
class EconomyException(
    val reason: String?,
    val waitMs: Long = 0L,
    val daysLeft: Long = 0L,
    override val message: String? = null
) : Exception(message ?: reason) {
    override fun toString(): String = "EconomyException($reason, waitMs=$waitMs, daysLeft=$daysLeft, message=$message)"
}

/**
 * Единый транспорт вызовов callable функций экономики в регионе `europe-west1`.
 */
object EconomyCaller {
    private val functions: FirebaseFunctions
        get() = Firebase.functions("europe-west1")

    /**
     * Вызов callable в `europe-west1`: ответ — Map<String, Any?> (или пустая), ошибка — [EconomyException].
     */
    suspend fun callEconomy(name: String, data: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        return try {
            val result = functions.getHttpsCallable(name).call(data).await()
            @Suppress("UNCHECKED_CAST")
            (result.data as? Map<String, Any?>) ?: emptyMap()
        } catch (e: FirebaseFunctionsException) {
            val details = e.details as? Map<*, *> ?: emptyMap<Any, Any>()
            val wait = (details["waitMs"] as? Number)?.toLong() ?: 0L
            val days = (details["daysLeft"] as? Number)?.toLong() ?: 0L
            val reason = details["reason"] as? String
            throw EconomyException(
                reason = reason,
                waitMs = wait,
                daysLeft = days,
                message = e.message
            )
        }
    }

    /**
     * «35 с» / «12 ч» / «3 дн» — как `formatWait` в вебе (кулдаун прокрутки).
     */
    fun formatWait(ms: Long): String {
        val s = ceil(ms / 1000.0).toLong()
        if (s < 90) return "$s с"
        val h = ceil(s / 3600.0).toLong()
        if (h < 48) return "${if (h < 1) 1 else h} ч"
        return "${ceil(h / 24.0).toLong()} дн"
    }
}
