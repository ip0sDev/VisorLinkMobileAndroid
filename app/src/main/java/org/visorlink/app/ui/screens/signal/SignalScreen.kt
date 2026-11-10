package org.visorlink.app.ui.screens.signal

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
import org.visorlink.app.ui.components.VlSegmentedControl
import org.visorlink.app.ui.components.VlSwitch
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.VlTopAppBarButton
import org.visorlink.app.ui.theme.VlDepth
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlDashedBorder
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlInset
import org.visorlink.app.ui.theme.vlRaised
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

private fun groupDigits(n: Int): String =
    String.format(Locale.US, "%,d", n).replace(',', ' ')

private val dateFormatRu by lazy {
    SimpleDateFormat("d MMMM y", Locale("ru"))
}

private val monthFormatRu by lazy {
    SimpleDateFormat("LLL", Locale("ru"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalScreen(
    onNavigateBack: () -> Unit,
    onOpenSteps: () -> Unit = {},
    asTab: Boolean = false,
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
                navigationIcon = if (asTab) {
                    // Вкладка навбара: без стрелки назад (прецедент ProfileScreen.asTab)
                    {}
                } else {
                    {
                        VlTopAppBarButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.signal_back)
                            )
                        }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Шаги (этап 1 активностей, флаг enable_step_activities): отдельная подстраница с Линком
                    val flagsRepository: org.visorlink.app.data.repository.FlagsRepository = org.koin.compose.koinInject()
                    val flags by flagsRepository.flags.collectAsStateWithLifecycle()
                    if (flags.stepActivities) {
                        val stepsVm: StepsViewModel = koinViewModel()
                        val stepsUi by stepsVm.ui.collectAsStateWithLifecycle()
                        VlCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenSteps
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DirectionsWalk,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.step_card_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.step_entry_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (stepsUi.enabled) {
                                    Text(
                                        text = stringResource(R.string.step_entry_trailing, groupDigits(stepsUi.steps)),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

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
                        blockedUids = uiState.blockedUids,
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

                    Spacer(modifier = Modifier.height(32.dp))
                }
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
                    Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
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
private fun SignalSmallButton(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val enabled = onClick != null
    val fg = if (primary) cs.onPrimary else cs.onSurface
    val bg = if (primary) cs.primary else cs.surfaceContainerHighest
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val shape = tokens.shapes.button
    val depthModifier = if (tokens.structure.enabled && enabled) {
        if (isPressed) Modifier.vlInset(tokens.structure, shape)
        else Modifier.vlRaised(tokens.structure, shape)
    } else Modifier

    Surface(
        onClick = { onClick?.invoke() },
        enabled = enabled,
        shape = shape,
        color = bg,
        interactionSource = interactionSource,
        modifier = modifier
            .height(40.dp)
            .alpha(if (enabled) (if (isPressed) 0.75f else 1f) else 0.45f)
            .then(depthModifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = fg
            )
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
        icon = Icons.Rounded.Sensors
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "$shown",
                    style = tokens.data.dataMedium.copy(
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (urgent) tokens.status.warning else cs.primary
                    )
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Веха: ${ms.day} дн.",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurface
                )
                Text(
                    text = "+${groupDigits(ms.bonus)} Bits",
                    style = tokens.data.dataSmall.copy(
                        color = tokens.status.warning,
                        fontWeight = FontWeight.Bold
                    )
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
                Text(
                    text = "$best",
                    style = tokens.data.dataSmall.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Активных дней", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(
                    text = groupDigits(profile.activeDays),
                    style = tokens.data.dataSmall.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Сообщений за полгода", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(
                    text = groupDigits(total),
                    style = tokens.data.dataSmall.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreakFreezeCard(
    profile: UserProfile,
    now: Long,
    isBuying: Boolean,
    error: String?,
    onBuy: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
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
        icon = Icons.Rounded.AcUnit,
        trailing = {
            Text(
                "${stock.freezes} / ${SignalRules.FREEZE_MAX}",
                style = tokens.data.dataSmall.copy(color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            )
        },
        desc = "Заморозка прикрывает одни пропущенные сутки: если вы не писали дольше 48 часов, серия не сгорит. Тратится сама, когда нужна; замороженный день серию не растит."
    ) {
        val slotShape = tokens.shapes.rounded(12.dp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until SignalRules.FREEZE_MAX) {
                    val active = i < stock.freezes
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .then(
                                if (!active) Modifier.vlDashedBorder(
                                    color = cs.outlineVariant.copy(alpha = 0.8f),
                                    shape = slotShape
                                ) else Modifier
                            )
                            .clip(slotShape)
                            .background(
                                if (active) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerLow
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.AcUnit,
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
                    Text(
                        "Спишется ${SignalRules.FREEZE_PRICE} Bits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface
                    )
                    SignalSmallButton(
                        text = "Отмена",
                        onClick = if (isBuying) null else { { confirm = false } },
                        primary = false
                    )
                    SignalSmallButton(
                        text = if (isBuying) "Загрузка…" else "Купить",
                        onClick = if (isBuying) null else { { confirm = false; onBuy() } },
                        primary = true
                    )
                }
            } else {
                SignalSmallButton(
                    text = if (full) "Запас полон" else "Купить заморозку · ${SignalRules.FREEZE_PRICE}",
                    onClick = if (full || poor || isBuying) null else { { confirm = true } },
                    primary = false,
                    icon = if (full) Icons.Rounded.Lock else Icons.Rounded.AcUnit
                )
            }
        }

        if (!full && poor && !confirm) {
            Text(
                "Нужно ${SignalRules.FREEZE_PRICE} Bits.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
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
    val cell = 14.dp
    val gap = 3.dp
    val width = (SignalRules.WEEKS * 17 - 3).dp

    SignalCardContainer(
        title = "Активность",
        desc = "Дней с сообщениями за полгода: ${cal.activeDays}."
    ) {
        val scrollState = rememberScrollState()
        LaunchedEffect(Unit) {
            scrollState.scrollTo(scrollState.maxValue)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier.width(width),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Метки месяцев
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
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

                // Матрица колонок
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.Top
                ) {
                    for (col in cal.columns) {
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            for (c in col) {
                                ActivityCellWidget(level = c.level, future = c.future, size = 14)
                            }
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
    level: Int,
    future: Boolean = false,
    size: Int = 14
) {
    val cs = MaterialTheme.colorScheme
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
    blockedUids: Set<String>,
    pro: Boolean,
    now: Long
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    var allTime by remember { mutableStateOf(false) }
    val effectiveAllTime = allTime && pro

    val top = remember(stats.peers, effectiveAllTime, blockedUids, now) {
        topPeers(stats.peers, allTime = effectiveAllTime, limit = 5, exclude = blockedUids, now = now)
    }

    val visibleRows = remember(top, peerProfiles) {
        top.mapNotNull { r ->
            val p = peerProfiles[r.uid]
            if (p != null && (p.isBot || (p.username.isEmpty() && p.displayName.isEmpty()))) {
                null
            } else {
                r to p
            }
        }
    }

    SignalCardContainer(
        title = "С кем вы общаетесь",
        desc = "Сколько сообщений вы написали в личных чатах. Видите только вы."
    ) {
        VlSegmentedControl(
            labels = listOf(
                "30 дней",
                if (pro) "Всё время" else "🔒 Всё время"
            ),
            selectedIndex = if (allTime) 1 else 0,
            onSelected = { index ->
                if (index == 1 && !pro) return@VlSegmentedControl
                allTime = (index == 1)
            }
        )

        if (visibleRows.isEmpty()) {
            Text(
                "Пока пусто — статистика появится после первых сообщений.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        } else {
            val maxCount = max(1, visibleRows.firstOrNull()?.first?.count ?: 1)
            visibleRows.forEachIndexed { index, (row, p) ->
                val name = p?.displayName?.ifEmpty { p.username } ?: p?.username?.ifEmpty { "..." } ?: "..."
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "${index + 1}",
                        style = tokens.data.dataSmall.copy(color = cs.onSurfaceVariant),
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
                        style = tokens.data.dataSmall.copy(color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        if (!pro) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(13.dp))
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
    val shape = tokens.shapes.rounded(12.dp)

    SignalCardContainer(
        title = "Достижения",
        icon = Icons.Rounded.EmojiEvents,
        trailing = {
            Text(
                "$count / ${ACHIEVEMENTS.size}",
                style = tokens.data.dataSmall.copy(color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            )
        },
        desc = "Разовые награды Bits за важные шаги. Начисляются сами."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (a in ACHIEVEMENTS) {
                val unlocked = achievementsMap.containsKey(a.id)
                val at = achievementsMap[a.id]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (unlocked) 1f else 0.55f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(shape)
                            .background(
                                if (unlocked) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerLow
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (unlocked) achievementIcon(a.id) else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (unlocked) cs.primary else cs.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
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
                        style = tokens.data.dataSmall.copy(color = tokens.status.warning, fontWeight = FontWeight.Bold)
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
                Icons.Rounded.LocalFireDepartment,
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
    "id_card" -> Icons.Rounded.Badge
    "sticker_pack" -> Icons.Rounded.EmojiEmotions
    "first_trade" -> Icons.Rounded.SwapHoriz
    "first_gift" -> Icons.Rounded.CardGiftcard
    "streak_30" -> Icons.Rounded.LocalFireDepartment
    "streak_100" -> Icons.Rounded.RocketLaunch
    "half_year" -> Icons.Rounded.EventAvailable
    "one_year" -> Icons.Rounded.Cake
    "bug_hunter" -> Icons.Rounded.BugReport
    "recruiter" -> Icons.Rounded.PersonAddAlt1
    else -> Icons.Rounded.EmojiEvents
}
