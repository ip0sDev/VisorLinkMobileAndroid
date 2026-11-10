package org.visorlink.app.data.model

import java.util.Calendar
import java.util.Locale

/**
 * Правила и лимиты для отложенных сообщений VisorLink PRO.
 */
object ScheduleRules {
    const val MIN_AHEAD_MS = 60 * 1000L // 1 минута
    const val MAX_AHEAD_MS = 365L * 24 * 3600 * 1000 // 1 год
    const val MAX_PENDING = 20
}

/**
 * Модель отложенного сообщения.
 * Коллекция: `scheduledMessages/{id}`
 */
data class ScheduledMessage(
    val id: String,
    val chatId: String,
    val topicId: String? = null,
    val text: String,
    val replyUsername: String? = null,
    val replyText: String? = null,
    val sendAt: Long? = null,
    val failed: Boolean = false,
    val failReason: String? = null,
    val createdAt: Long = 0L
) {
    companion object {
        fun parse(id: String, m: Map<*, *>): ScheduledMessage {
            val reply = m["replyTo"] as? Map<*, *>
            val status = m["status"] as? String
            val sendAtNum = (m["sendAt"] as? Number)?.toLong()
            val createdNum = (m["createdAt"] as? Number)?.toLong()
                ?: (m["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                ?: 0L

            return ScheduledMessage(
                id = id,
                chatId = m["chatId"] as? String ?: "",
                topicId = (m["topicId"] as? String)?.takeIf { it.isNotEmpty() },
                text = m["text"] as? String ?: "",
                replyUsername = (reply?.get("senderUsername") as? String)?.takeIf { it.isNotEmpty() },
                replyText = (reply?.get("text") as? String)?.takeIf { it.isNotEmpty() },
                sendAt = sendAtNum,
                failed = status == "failed",
                failReason = (m["failReason"] as? String)?.takeIf { it.isNotEmpty() },
                createdAt = createdNum
            )
        }
    }
}

data class SchedulePreset(
    val label: String,
    val atMs: Long
)

/**
 * Быстрые варианты времени: через час, сегодня вечером в 20:00 (если есть запас более часа), завтра утром в 9:00, через неделю.
 */
fun schedulePresets(now: Long = System.currentTimeMillis()): List<SchedulePreset> {
    val out = mutableListOf<SchedulePreset>()
    out.add(SchedulePreset("Через час", now + 3600 * 1000L))

    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
    if (currentHour < 19) {
        cal.set(Calendar.HOUR_OF_DAY, 20)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        out.add(SchedulePreset("Сегодня вечером (20:00)", cal.timeInMillis))
    }

    val tomorrow = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    out.add(SchedulePreset("Завтра утром (09:00)", tomorrow.timeInMillis))

    out.add(SchedulePreset("Через неделю", now + 7L * 24 * 3600 * 1000L))
    return out
}

/**
 * Валидация выбранного времени перед отправкой на сервер.
 */
fun scheduleTimeError(ms: Long, now: Long = System.currentTimeMillis()): String? {
    if (ms < now + ScheduleRules.MIN_AHEAD_MS) return "Не раньше чем через минуту."
    if (ms > now + ScheduleRules.MAX_AHEAD_MS) return "Не позже чем через год."
    return null
}

/**
 * Человекопонятный текст причины ошибки или отказа отправки.
 */
fun scheduledReasonText(reason: String?): String = when (reason) {
    "pro-required" -> "Отложенные сообщения доступны с подпиской PRO."
    "limit" -> "Запланировать можно не больше 20 сообщений."
    "bad-text" -> "Текст сообщения: от 1 до 2000 символов."
    "bad-time" -> "Время отправки: от минуты до года вперёд."
    "not-found" -> "Сообщение не найдено (возможно, оно уже отправлено)."
    "not-pending" -> "Сообщение уже не ожидает отправки."
    "not-member" -> "Не отправлено: вы больше не участник чата."
    "banned" -> "Не отправлено: вы заблокированы в этом чате."
    "muted" -> "Не отправлено: вам запрещено писать в этот чат."
    "channel-admin" -> "Не отправлено: в канал могут писать только администраторы."
    "no-topic" -> "Не отправлено: тема форума удалена."
    "blocked" -> "Не отправлено: переписка заблокирована."
    "error", null -> "Не отправлено из-за ошибки сервера."
    else -> "Произошла ошибка. Попробуйте ещё раз."
}
