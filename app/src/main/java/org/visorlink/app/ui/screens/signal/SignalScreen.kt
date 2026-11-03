package org.visorlink.app.ui.screens.signal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.visorlink.app.R
import org.visorlink.app.data.model.*
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.components.VlSwitch
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

private fun groupDigits(n: Int): String =
    String.format(Locale.US, "%,d", n).replace(',', ' ')

private val dateFormatRu by lazy {
    SimpleDateFormat("d MMMM y", Locale("ru"))
}

private val monthFormatRu by lazy {
    SimpleDateFormat("LLL", Locale("ru"))
}

private val dayFormatRu by lazy {
    SimpleDateFormat("d MMMM", Locale("ru"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalScreen(
    onNavigateBack: () -> Unit,
    viewModel: SignalViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = uiState.profile

    // Таймер обновления времени раз в 30 секунд для точности отсчёта
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            viewModel.updateNow()
        }
    }

    Scaffold(
        topBar = {
            VlTopAppBar(
                title = { Text(stringResource(R.string.signal_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.signal_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StreakCard(
                    profile = profile,
                    stats = uiState.stats,
                    now = uiState.now
                )

                StreakFreezeCard(
                    profile = profile,
                    now = uiState.now,
                    isBuying = uiState.isBuyingFreeze,
                    error = uiState.freezeError,
                    onBuy = { viewModel.buyStreakFreeze() }
                )

                CalendarCard(
                    stats = uiState.stats,
                    now = uiState.now
                )

                PeersCard(
                    stats = uiState.stats,
                    peerProfiles = uiState.peerProfiles,
                    pro = profile.isProActive(),
                    now = uiState.now
                )

                AchievementsCard(
                    achievementsMap = uiState.achievements
                )

                FooterCard(
                    profile = profile,
                    stats = uiState.stats,
                    onShowStreakChange = { viewModel.setShowStreak(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SignalCardContainer(
    title: String,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    desc: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    VlCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
            }
            if (desc != null) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun StreakCard(
    profile: UserProfile,
    stats: SignalStats,
    now: Long
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val stock = freezeStock(
        freezes = profile.streakFreezes,
        grantAt = profile.streakFreezeGrantAtMs,
        pro = profile.isProActive(),
        now = now
    )
    val timing = streakTiming(
        streak = profile.streak,
        lastMs = profile.lastStreakUpdate?.toDate()?.time ?: 0L,
        now = now,
        freezes = stock.freezes
    )
    val shown = if (timing.state == StreakState.LOST) 0 else profile.streak
    val ms = nextMilestone(shown)
    val from = prevMilestoneDay(shown)
    val progress = ((shown - from).toFloat() / max(1, ms.day - from)).coerceIn(0f, 1f)
    val best = max(profile.streakBest, profile.streak)
    val total = buildCalendar(stats.days, SignalRules.WEEKS, now).total
    val urgent = timing.state == StreakState.OPEN || timing.state == StreakState.FROZEN

    val status = when (timing.state) {
        StreakState.DONE -> "Сегодня засчитано. Следующее продление — через ${formatLeft(timing.renewAt, now)}."
        StreakState.OPEN -> if (stock.freezes > 0) {
            "Напишите сегодня, чтобы не тратить заморозку: окно закроется через ${formatLeft(timing.openUntil, now)}."
        } else {
            "Напишите сегодня — иначе серия сгорит через ${formatLeft(timing.burnAt, now)}."
        }
        StreakState.FROZEN -> "Окно 48 часов прошло — серию держит заморозка. Напишите в течение ${formatLeft(timing.burnAt, now)}, спишется заморозок: ${timing.freezesNeeded}."
        else -> "Напишите кому-нибудь, чтобы начать серию."
    }

    SignalCardContainer(
        title = "Сигнал",
        icon = Icons.Default.Sensors
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "$shown",
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (urgent) tokens.status.warning else cs.primary
                )
                Text(
                    text = "дн. подряд",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (urgent) tokens.status.warning else cs.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Веха: ${ms.day} дн.",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurface
                )
                Text(
                    text = "+${groupDigits(ms.bonus)} Bits",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = tokens.status.warning,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = cs.primary,
                trackColor = cs.surfaceContainerHighest
            )
            Text(
                text = "Осталось ${ms.left} дн.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Лучшая серия", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("$best", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Активных дней", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(groupDigits(profile.activeDays), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Сообщений", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(groupDigits(total), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StreakFreezeCard(
    profile: UserProfile,
    now: Long,
    isBuying: Boolean,
    error: String?,
    onBuy: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val pro = profile.isProActive()
    val stock = freezeStock(
        freezes = profile.streakFreezes,
        grantAt = profile.streakFreezeGrantAtMs,
        pro = pro,
        now = now
    )
    val full = stock.freezes >= SignalRules.FREEZE_MAX
    val poor = profile.bits < SignalRules.FREEZE_PRICE
    val use = profile.parsedLastFreezeUse
    val recentUse = if (use != null && now - use.at < 7 * SignalRules.DAY_MS) use else null

    var confirm by remember { mutableStateOf(false) }

    val proNote = when {
        !pro -> "С PRO — одна бесплатная заморозка в месяц."
        stock.nextFreeAt > 0 -> "Следующая бесплатная заморозка PRO — ${dateFormatRu.format(Date(stock.nextFreeAt))}."
        full -> "Бесплатная заморозка PRO придёт, как только в запасе освободится место."
        else -> "Бесплатная заморозка PRO уже ждёт и добавится к запасу при следующем продлении."
    }

    SignalCardContainer(
        title = "Заморозки",
        icon = Icons.Default.AcUnit,
        trailing = {
            Text(
                "${stock.freezes} / ${SignalRules.FREEZE_MAX}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurfaceVariant
            )
        },
        desc = "Заморозка прикрывает одни пропущенные сутки: если вы не писали дольше 48 часов, серия не сгорит. Тратится сама, когда нужна; замороженный день серию не растит."
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until SignalRules.FREEZE_MAX) {
                    val active = i < stock.freezes
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerLow
                            )
                            .then(
                                if (!active) Modifier.border(1.dp, cs.outlineVariant.copy(alpha = 0.8f), RoundedCornerShape(12.dp)) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AcUnit,
                            contentDescription = null,
                            tint = if (active) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (confirm) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { confirm = false }, enabled = !isBuying) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = {
                            confirm = false
                            onBuy()
                        },
                        enabled = !isBuying
                    ) {
                        Text(if (isBuying) "Загрузка…" else "Купить за ${SignalRules.FREEZE_PRICE}")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { confirm = true },
                    enabled = !full && !poor && !isBuying
                ) {
                    Icon(
                        if (full) Icons.Default.Lock else Icons.Default.AcUnit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (full) "Запас полон" else "Купить · ${SignalRules.FREEZE_PRICE}")
                }
            }
        }

        if (!full && poor && !confirm) {
            Text("Нужно ${SignalRules.FREEZE_PRICE} Bits.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (recentUse != null) {
            Text(
                "Заморозка спасла серию ${dateFormatRu.format(Date(recentUse.at))} (потрачено: ${recentUse.n}).",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.primary
            )
        }
        Text(proNote, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CalendarCard(
    stats: SignalStats,
    now: Long
) {
    val cs = MaterialTheme.colorScheme
    val cal = remember(stats.days, now) {
        buildCalendar(stats.days, SignalRules.WEEKS, now)
    }

    SignalCardContainer(
        title = "Активность",
        desc = "Дней с сообщениями за полгода: ${cal.activeDays}."
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), reverseScrolling = true)
        ) {
            // Метки месяцев над колонками
            Box(
                modifier = Modifier
                    .width((SignalRules.WEEKS * 17).dp)
                    .height(18.dp)
            ) {
                for (m in cal.months) {
                    Text(
                        text = monthFormatRu.format(m.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.offset(x = (m.col * 17).dp)
                    )
                }
            }

            // Матрица 7 строк x 26 колонок
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (col in cal.columns) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (cell in col) {
                            ActivityCellWidget(cell = cell)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Меньше", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(modifier = Modifier.width(4.dp))
            for (level in 0..4) {
                ActivityCellWidget(level = level, size = 12)
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text("Больше", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActivityCellWidget(
    cell: CalendarCell? = null,
    level: Int = cell?.level ?: 0,
    size: Int = 14
) {
    val cs = MaterialTheme.colorScheme
    val future = cell?.future == true
    val mix = when (level.coerceIn(0, 4)) {
        1 -> 0.28f
        2 -> 0.52f
        3 -> 0.76f
        4 -> 1.0f
        else -> 0.0f
    }
    val cellColor = if (future) Color.Transparent else {
        androidx.compose.ui.graphics.lerp(cs.surfaceContainerHigh, cs.primary, mix)
    }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(cellColor)
            .then(
                if (future) Modifier.border(1.dp, cs.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(3.dp)) else Modifier
            )
    )
}

@Composable
private fun PeersCard(
    stats: SignalStats,
    peerProfiles: Map<String, UserProfile>,
    pro: Boolean,
    now: Long
) {
    val cs = MaterialTheme.colorScheme
    var allTime by remember { mutableStateOf(false) }
    val effectiveAllTime = allTime && pro

    val top = remember(stats.peers, effectiveAllTime, now) {
        topPeers(stats.peers, allTime = effectiveAllTime, limit = 5, now = now)
    }

    SignalCardContainer(
        title = "С кем вы общаетесь",
        desc = "Сколько сообщений вы написали в личных чатах. Видите только вы."
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cs.surfaceContainerHighest)
                .padding(2.dp)
        ) {
            FilterChip(
                selected = !allTime,
                onClick = { allTime = false },
                label = { Text("30 дней") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            FilterChip(
                selected = allTime,
                onClick = { if (pro) allTime = true },
                label = { Text(if (pro) "Всё время" else "🔒 Всё время") },
                modifier = Modifier.weight(1f)
            )
        }

        if (top.isEmpty()) {
            Text(
                "Пока пусто — статистика появится после первых сообщений.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        } else {
            val maxCount = max(1, top.firstOrNull()?.count ?: 1)
            top.forEachIndexed { index, row ->
                val p = peerProfiles[row.uid]
                val name = p?.displayName?.ifEmpty { p.username } ?: p?.username?.ifEmpty { "..." } ?: "..."
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.width(18.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        if (p?.avatarUrl != null) {
                            AsyncImage(
                                model = p.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                name.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = cs.primary
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val fraction = (row.count.toFloat() / maxCount).coerceIn(0.06f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(cs.primary)
                        )
                    }

                    Text(
                        groupDigits(row.count),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (!pro) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text("Статистика за всё время — в PRO", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AchievementsCard(
    achievementsMap: Map<String, Long>
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val count = ACHIEVEMENTS.count { achievementsMap.containsKey(it.id) }

    SignalCardContainer(
        title = "Достижения",
        icon = Icons.Default.EmojiEvents,
        trailing = {
            Text(
                "$count / ${ACHIEVEMENTS.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurfaceVariant
            )
        },
        desc = "Разовые награды Bits за важные шаги. Начисляются сами."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (a in ACHIEVEMENTS) {
                val unlocked = achievementsMap.containsKey(a.id)
                val at = achievementsMap[a.id]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (unlocked) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerLow
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (unlocked) achievementIcon(a.id) else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (unlocked) cs.primary else cs.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (unlocked && at != null) "Получено ${dateFormatRu.format(Date(at))}" else a.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }

                    Text(
                        "+${groupDigits(a.bits)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = tokens.status.warning
                    )
                }
            }
        }
    }
}

@Composable
private fun FooterCard(
    profile: UserProfile,
    stats: SignalStats,
    onShowStreakChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    VlCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = Color(0xFFE11D48),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Показывать серию в профиле",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Значок с числом дней рядом с вашим именем.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            VlSwitch(
                checked = profile.showStreak,
                onCheckedChange = onShowStreakChange
            )
        }
    }

    Text(
        "Серия продлевается, если вы пишете в чат раз в 20–48 часов; пропущенный день прикроет заморозка. Награда — до 50 Bits в день и бонусы за 7, 30 и каждые 100 дней.",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )

    if (stats.since != null) {
        Text(
            "Статистика ведётся с ${dateFormatRu.format(Date(stats.since))}.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

fun achievementIcon(id: String): ImageVector = when (id) {
    "id_card" -> Icons.Default.Badge
    "sticker_pack" -> Icons.Default.EmojiEmotions
    "first_trade" -> Icons.Default.SwapHoriz
    "first_gift" -> Icons.Default.CardGiftcard
    "streak_30" -> Icons.Default.LocalFireDepartment
    "streak_100" -> Icons.Default.RocketLaunch
    "half_year" -> Icons.Default.EventAvailable
    "one_year" -> Icons.Default.Cake
    "bug_hunter" -> Icons.Default.BugReport
    "recruiter" -> Icons.Default.PersonAddAlt1
    else -> Icons.Default.EmojiEvents
}
