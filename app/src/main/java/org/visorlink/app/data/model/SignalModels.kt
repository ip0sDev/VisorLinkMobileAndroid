package org.visorlink.app.data.model

import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * «Сигнал» — экран стрика (веб `src/utils/signalModel.js`): серия и её срок, вехи, заморозки, календарь,
 * собеседники. Bits и продление считает сервер (`functions/economyLogic.js`), статистику —
 * `functions/signalLogic.js`.
 */
object SignalRules {
    const val MIN_GAP_MS: Long = 20L * 3600 * 1000
    const val RESET_MS: Long = 48L * 3600 * 1000
    const val DAY_MS: Long = 24L * 3600 * 1000
    val MILESTONES: List<Milestone> = listOf(
        Milestone(day = 7, bonus = 150),
        Milestone(day = 30, bonus = 500)
    )
    const val HUNDRED_BONUS: Int = 2000

    const val FREEZE_PRICE: Int = 150
    const val FREEZE_MAX: Int = 2
    const val PRO_FREEZE_EVERY_MS: Long = 30L * DAY_MS

    /** Недель в календаре активности. */
    const val WEEKS: Int = 26
}

data class Milestone(
    val day: Int,
    val bonus: Int,
    val left: Int = 0
)

/**
 * Ближайшая веха после текущей серии: 7, 30, затем каждая сотня.
 */
fun nextMilestone(streak: Int): Milestone {
    val s = max(0, streak)
    for (m in SignalRules.MILESTONES) {
        if (s < m.day) return Milestone(day = m.day, bonus = m.bonus, left = m.day - s)
    }
    val day = (s / 100 + 1) * 100
    return Milestone(day = day, bonus = SignalRules.HUNDRED_BONUS, left = day - s)
}

/**
 * Прошлая веха (откуда считать прогресс): 0, 7, 30, 100, 200…
 */
fun prevMilestoneDay(streak: Int): Int {
    val s = max(0, streak)
    if (s >= 100) return (s / 100) * 100
    if (s >= 30) return 30
    if (s >= 7) return 7
    return 0
}

enum class StreakState {
    /** Серии нет. */
    NONE,

    /** Сегодня уже продлена. */
    DONE,

    /** Можно продлить. */
    OPEN,

    /** Окно 48 ч прошло, серию держат заморозки. */
    FROZEN,

    /** Сгорела. */
    LOST
}

data class StreakTiming(
    val state: StreakState,
    val renewAt: Long = 0L,
    val openUntil: Long = 0L,
    val burnAt: Long = 0L,
    val freezesNeeded: Int = 0
)

/**
 * Состояние серии по времени последнего продления; [freezes] — запас: каждая заморозка отодвигает
 * сгорание на сутки (`nextStreak` на сервере).
 */
fun streakTiming(streak: Int, lastMs: Long, now: Long, freezes: Int = 0): StreakTiming {
    if (lastMs <= 0L || streak <= 0) return StreakTiming(StreakState.NONE)
    val renewAt = lastMs + SignalRules.MIN_GAP_MS
    val openUntil = lastMs + SignalRules.RESET_MS
    val burnAt = openUntil + max(0, freezes) * SignalRules.DAY_MS
    if (now > burnAt) return StreakTiming(StreakState.LOST)
    if (now < renewAt) return StreakTiming(StreakState.DONE, renewAt = renewAt, openUntil = openUntil, burnAt = burnAt)
    if (now > openUntil) {
        val needed = ceil((now - openUntil).toDouble() / SignalRules.DAY_MS).toInt()
        return StreakTiming(
            state = StreakState.FROZEN,
            openUntil = openUntil,
            burnAt = burnAt,
            freezesNeeded = needed
        )
    }
    return StreakTiming(StreakState.OPEN, openUntil = openUntil, burnAt = burnAt)
}

data class FreezeStock(
    val freezes: Int,
    val nextFreeAt: Long
)

/**
 * Запас заморозок, как его увидит сервер: сохранённые + положенная бесплатная PRO (раз в 30 дней, не сверх двух).
 */
fun freezeStock(freezes: Int, grantAt: Long, pro: Boolean, now: Long): FreezeStock {
    val have = freezes.coerceIn(0, SignalRules.FREEZE_MAX)
    val due = pro && (now - grantAt >= SignalRules.PRO_FREEZE_EVERY_MS)
    if (due && have < SignalRules.FREEZE_MAX) return FreezeStock(freezes = have + 1, nextFreeAt = 0L)
    val nextFree = if (pro && !due) grantAt + SignalRules.PRO_FREEZE_EVERY_MS else 0L
    return FreezeStock(freezes = have, nextFreeAt = nextFree)
}

/**
 * `YYYY-MM-DD` в местном времени устройства.
 */
fun localDayKey(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

/**
 * Уровень клетки календаря 0…4 по числу сообщений: 0 / 1–3 / 4–10 / 11–30 / 31+.
 */
fun activityLevel(n: Int): Int {
    if (n <= 0) return 0
    if (n <= 3) return 1
    if (n <= 10) return 2
    if (n <= 30) return 3
    return 4
}

data class CalendarCell(
    val key: String,
    val date: Date,
    val count: Int,
    val level: Int,
    /** Будущий день текущей недели: пустая клетка с рамкой. */
    val future: Boolean
)

data class MonthLabel(
    val col: Int,
    val date: Date
)

data class ActivityCalendar(
    /** Колонки — недели (пн…вс), последняя — текущая. */
    val columns: List<List<CalendarCell>>,
    /** Подписи месяцев: колонка и первый день. */
    val months: List<MonthLabel>,
    val activeDays: Int,
    val total: Int
)

/**
 * Календарь за [weeks] недель (веб `buildCalendar`).
 */
fun buildCalendar(days: Map<String, Int>, weeks: Int = SignalRules.WEEKS, todayMillis: Long = System.currentTimeMillis()): ActivityCalendar {
    val todayCal = Calendar.getInstance().apply {
        timeInMillis = todayMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val endMillis = todayCal.timeInMillis
    // Понедельник = 0, Воскресенье = 6
    val dayOfWeek = (todayCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val startCal = Calendar.getInstance().apply {
        timeInMillis = endMillis
        add(Calendar.DAY_OF_MONTH, -dayOfWeek - (weeks - 1) * 7)
    }

    val columns = mutableListOf<List<CalendarCell>>()
    val months = mutableListOf<MonthLabel>()
    var activeDays = 0
    var total = 0

    val runner = Calendar.getInstance().apply { timeInMillis = startCal.timeInMillis }

    for (w in 0 until weeks) {
        val col = mutableListOf<CalendarCell>()
        for (d in 0 until 7) {
            val date = runner.time
            val millis = runner.timeInMillis
            val key = localDayKey(millis)
            val future = millis > endMillis
            val count = if (future) 0 else (days[key] ?: 0)
            if (count > 0) {
                activeDays++
                total += count
            }
            if (runner.get(Calendar.DAY_OF_MONTH) == 1 || (w == 0 && d == 0)) {
                months.add(MonthLabel(col = w, date = date))
            }
            col.add(CalendarCell(key = key, date = date, count = count, level = activityLevel(count), future = future))
            runner.add(Calendar.DAY_OF_MONTH, 1)
        }
        columns.add(col)
    }

    // Подпись месяца в первой колонке не нужна, если через неделю начинается следующий
    val cleanedMonths = months.filterIndexed { i, m ->
        !(i == 0 && months.size > 1 && (months[1].col - months[0].col < 2))
    }

    return ActivityCalendar(columns = columns, months = cleanedMonths, activeDays = activeDays, total = total)
}

/**
 * Собеседник в `signalStats.peers`: всего сообщений, последнее (мс), по дням за 30 дней.
 */
data class SignalPeer(
    val total: Int = 0,
    val last: Long = 0L,
    val recent: Map<String, Int> = emptyMap()
)

/**
 * `signalStats/{uid}` — пишет сервер, читает только владелец.
 */
data class SignalStats(
    val days: Map<String, Int> = emptyMap(),
    val peers: Map<String, SignalPeer> = emptyMap(),
    val since: Long? = null
) {
    companion object {
        val EMPTY = SignalStats()
    }
}

data class TopPeerRow(
    val uid: String,
    val count: Int,
    val last: Long
)

/**
 * Топ собеседников: [allTime] — за всё время (`total`), иначе сумма `recent` за 30 дней.
 */
fun topPeers(
    peers: Map<String, SignalPeer>,
    allTime: Boolean,
    limit: Int = 5,
    exclude: Set<String> = emptySet(),
    now: Long
): List<TopPeerRow> {
    val cutoff = localDayKey(now - 30 * SignalRules.DAY_MS)
    val rows = mutableListOf<TopPeerRow>()

    for ((uid, p) in peers) {
        if (uid in exclude) continue
        var count = 0
        if (allTime) {
            count = p.total
        } else {
            for ((k, v) in p.recent) {
                if (k >= cutoff) count += v
            }
        }
        if (count > 0) {
            rows.add(TopPeerRow(uid = uid, count = count, last = p.last))
        }
    }

    rows.sortWith { a, b ->
        if (b.count != a.count) b.count - a.count else b.last.compareTo(a.last)
    }
    return rows.take(limit)
}

/**
 * Достижение для показа (веб `src/utils/achievements.js`; награды и условия — `functions/achievementLogic.js`).
 */
data class Achievement(
    val id: String,
    val bits: Int,
    val title: String,
    val desc: String
)

/**
 * Порядок — порядок показа.
 */
val ACHIEVEMENTS: List<Achievement> = listOf(
    Achievement("id_card", 50, "Своя ID-карта", "Получите ID-карту"),
    Achievement("sticker_pack", 50, "Коллекционер", "Добавьте стикерпак"),
    Achievement("first_trade", 100, "Первая сделка", "Обменяйтесь скинами"),
    Achievement("first_gift", 100, "Щедрость", "Подарите что-нибудь другу"),
    Achievement("streak_30", 300, "Месяц на связи", "Серия 30 дней"),
    Achievement("streak_100", 1000, "Сотня", "Серия 100 дней"),
    Achievement("half_year", 300, "Полгода с VisorLink", "Аккаунту полгода"),
    Achievement("one_year", 500, "Год с VisorLink", "Аккаунту год"),
    Achievement("bug_hunter", 100, "Охотник за багами", "Баг-репорт принят командой"),
    Achievement("recruiter", 100, "Наставник", "Приглашённый друг освоился")
)

data class UnseenAchievement(
    val a: Achievement,
    val at: Long
)

/**
 * Новые для уведомления, по порядку получения. [seen] == null — первый запуск на устройстве: тогда
 * только полученные за последние сутки.
 */
fun unseenAchievements(
    unlocked: Map<String, Long>,
    seen: Set<String>?,
    now: Long
): List<UnseenAchievement> {
    val out = mutableListOf<UnseenAchievement>()
    for (a in ACHIEVEMENTS) {
        val at = unlocked[a.id]
        if (at != null) {
            val isFresh = if (seen != null) !seen.contains(a.id) else (now - at < SignalRules.DAY_MS)
            if (isFresh) {
                out.add(UnseenAchievement(a = a, at = at))
            }
        }
    }
    out.sortBy { it.at }
    return out
}

/**
 * «5 ч 12 мин» / «12 мин» до момента [at].
 */
fun formatLeft(at: Long, now: Long): String {
    val min = max(1, ceil((at - now).toDouble() / 60000.0).toInt())
    val h = min / 60
    val m = min % 60
    if (h == 0) return "$m мин"
    return if (m == 0) "$h ч" else "$h ч $m мин"
}
