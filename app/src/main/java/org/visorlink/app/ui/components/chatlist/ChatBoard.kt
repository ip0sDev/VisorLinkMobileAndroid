package org.visorlink.app.ui.components.chatlist

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.visorlink.app.data.model.SyncState
import org.visorlink.app.ui.components.led.*
import org.visorlink.app.ui.theme.VlTheme

/**
 * Выражение мордочки по состоянию связи: нет сети — спит, синхронизация — глаза бегают.
 */
fun chatBoardExpression(online: Boolean, sync: SyncState): VlLedExpression {
    if (!online) return VlLedExpression.SLEEPY
    if (sync != SyncState.SYNCED) return VlLedExpression.SCAN
    return VlLedExpression.HAPPY
}

/**
 * Сегменты бегущей строки табло.
 */
fun chatBoardSegments(unread: Int, online: Boolean, sync: SyncState): List<String> = buildList {
    if (!online) add("ОФЛАЙН")
    add(if (unread > 0) "$unread НОВЫХ" else "НЕТ НОВЫХ")
    add(
        when (sync) {
            SyncState.SYNCED -> "СИНХР OK"
            SyncState.WAITING_FOR_NETWORK -> "ЖДУ СЕТЬ"
            SyncState.CONNECTING -> "СВЯЗЬ…"
            SyncState.UPDATING -> "СИНХР…"
        }
    )
}

/**
 * Число на счётчике: больше трёх знаков табло не вмещает.
 */
fun chatBoardCount(unread: Int): String = if (unread > 999) "999+" else unread.toString()

private val GiftClosedRows = listOf(
    "....###....",
    "...#...#...",
    "..#.....#..",
    ".#########.",
    ".####.####.",
    ".####.####.",
    ".####.####.",
    ".####.####.",
    ".####.####.",
    ".####.####.",
    ".#########.",
)

private val GiftOpenRows = listOf(
    "#.##...##.#",
    ".#..#.#..#.",
    "..###.###..",
    "###########",
    "#####.#####",
    "...........",
    ".####.####.",
    ".####.####.",
    ".####.####.",
    ".####.####.",
    ".#########.",
)

/**
 * Пиксельный подарок на LED-матрице: кадры меняются раз в 650 мс.
 */
@Composable
fun IonLedGift(
    modifier: Modifier = Modifier,
    dot: Dp = 2.5.dp,
    gap: Dp = 1.dp,
) {
    val tokens = VlTheme.tokens
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(tokens.reduceMotion) {
        if (!tokens.reduceMotion) {
            while (isActive) {
                delay(650)
                open = !open
            }
        }
    }

    VlLedMatrix(
        rows = if (open) GiftOpenRows else GiftClosedRows,
        dot = dot,
        gap = gap,
        modifier = modifier.semantics {
            contentDescription = "Подарок VL Ion"
        }
    )
}

/**
 * LED-табло в шапке списка чатов (только Forge): талисман, счётчик новых сообщений
 * и бегущая строка состояния.
 */
@Composable
fun ChatBoardView(
    unread: Int,
    online: Boolean,
    sync: SyncState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    gift: Boolean = false,
    onGift: (() -> Unit)? = null,
    face: VlLedFaceController? = null,
) {
    val expression = chatBoardExpression(online = online, sync = sync)
    val status = chatBoardSegments(unread = unread, online = online, sync = sync).joinToString(" · ")
    val giftStatus = listOf("VL ION", "НАЖМИ, ЧТОБЫ ОТКРЫТЬ").joinToString(" · ")

    val localFace = rememberLedFaceController()
    val effectiveFace = face ?: localFace

    val mascotClickModifier = Modifier
        .semantics {
            role = Role.Button
            contentDescription = "Талисман"
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                effectiveFace.flash(VlLedExpression.BOOP, durationMs = 1200L)
            }
        )

    val boardModifier = if (gift && onGift != null) {
        modifier
            .semantics {
                role = Role.Button
                contentDescription = "Подарок VL Ion"
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onGift
            )
    } else {
        modifier
    }

    if (compact) {
        VlLedScreen(
            modifier = boardModifier,
            radius = 14.dp,
            padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VlLedFace(
                    expression = expression,
                    controller = effectiveFace,
                    dot = 1.5.dp,
                    gap = 0.5.dp,
                    interactive = false,
                    modifier = mascotClickModifier,
                )
                if (gift) {
                    IonLedGift(dot = 1.5.dp, gap = 0.5.dp)
                }
                Box(modifier = Modifier.weight(1f)) {
                    VlLedMarquee(
                        text = if (gift) "ПОДАРОК · $giftStatus" else status,
                        dot = 1.5.dp,
                        gap = 0.5.dp,
                    )
                }
            }
        }
        return
    }

    VlLedScreen(
        modifier = boardModifier,
        radius = 18.dp,
        padding = PaddingValues(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VlLedFace(
                expression = expression,
                controller = effectiveFace,
                dot = 3.dp,
                gap = 1.dp,
                interactive = false,
                modifier = mascotClickModifier,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (gift) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val narrow = maxWidth < 190.dp
                        val giftDot = if (narrow) 2.dp else 2.5.dp
                        val giftGap = if (narrow) 0.75.dp else 1.dp
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(if (narrow) 8.dp else 10.dp)
                        ) {
                            IonLedGift(dot = giftDot, gap = giftGap)
                            VlLedText("ПОДАРОК", dot = giftDot, gap = giftGap)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VlLedText(chatBoardCount(unread), dot = 3.dp, gap = 1.dp)
                        VlLedText("НОВЫХ", dot = 1.5.dp, gap = 0.75.dp)
                    }
                }
                VlLedMarquee(
                    text = if (gift) giftStatus else status,
                    dot = 2.dp,
                    gap = 1.dp,
                )
            }
        }
    }
}

/**
 * Табло с подключением контроллера и реакцией на непрочитанные / подарки.
 */
@Composable
fun ChatBoard(
    unread: Int,
    online: Boolean,
    sync: SyncState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    gift: Boolean = false,
    onGift: (() -> Unit)? = null,
) {
    val face = rememberLedFaceController()

    // При увеличении числа непрочитанных мигаем сердечком
    var prevUnread by remember { mutableIntStateOf(unread) }
    LaunchedEffect(unread) {
        if (unread > prevUnread) {
            face.flash(VlLedExpression.HEART, 1200L)
        }
        prevUnread = unread
    }

    // Пока доступен подарок — периодически подмигиваем сердечком
    LaunchedEffect(gift) {
        if (gift) {
            while (isActive) {
                delay(6000)
                face.flash(VlLedExpression.HEART, 1400L)
            }
        }
    }

    ChatBoardView(
        unread = unread,
        online = online,
        sync = sync,
        modifier = modifier,
        compact = compact,
        gift = gift,
        onGift = onGift,
        face = face,
    )
}
