package org.visorlink.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.visorlink.app.R
import org.visorlink.app.ui.components.TerminalMotion
import org.visorlink.app.ui.components.VlLiveDot
import org.visorlink.app.ui.theme.VlDurations
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlBiopulse
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlInset
import org.visorlink.app.ui.theme.vlRaised
import org.visorlink.app.ui.theme.vlSignalGlow
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import kotlin.math.abs

/**
 * Смещение страницы [page] относительно центрального положения [pagerState]
 * для построения глубины, параллакса и непрерывного прогресс-трека.
 */
fun PagerState.pageOffsetFor(page: Int): Float =
    (currentPage - page) + currentPageOffsetFraction

/**
 * Непрерывный «жидкий» прогресс-бар в стиле Biolume:
 * акцентная капсула внутри `vlInset`-желоба плавно течёт вслед за свайпом пальца.
 */
@Composable
fun OnboardingLiquidProgressTrack(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val shape = tokens.shapes.pill
    val fraction = ((pagerState.currentPage + pagerState.currentPageOffsetFraction + 1f) / pageCount.toFloat())
        .coerceIn(1f / pageCount, 1f)

    BoxWithConstraints(
        modifier = modifier
            .width(128.dp)
            .height(10.dp)
            .vlInset(tokens.structure, shape)
            .clip(shape)
            .background(cs.surfaceContainerLow)
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val fillWidth = (maxWidth * fraction).coerceAtLeast(16.dp)
        Box(
            modifier = Modifier
                .width(fillWidth)
                .fillMaxHeight()
                .vlSignalGlow(
                    tokens = tokens.signal,
                    color = cs.primary,
                    shape = shape,
                    active = true,
                )
                .clip(shape)
                .background(cs.primary),
        )
    }
}

/**
 * Контейнер элемента с каскадным появлением (staggered entrance),
 * уважающий `reduceMotion` и характер движения текущей темы (`glitchMotion` / spring).
 */
@Composable
fun OnboardingStaggeredItem(
    visible: Boolean,
    delayMs: Int,
    modifier: Modifier = Modifier,
    parallaxOffsetX: Float = 0f,
    content: @Composable () -> Unit,
) {
    val tokens = VlTheme.tokens
    val reduceMotion = tokens.reduceMotion
    val glitch = tokens.glitchMotion

    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(visible, reduceMotion) {
        if (reduceMotion) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (visible) {
            if (delayMs > 0) delay(delayMs.toLong())
            if (glitch) {
                progress.animateTo(1f, TerminalMotion.tween(VlDurations.D3))
            } else {
                progress.animateTo(
                    1f,
                    spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
        }
    }

    val p = progress.value
    Box(
        modifier = modifier.graphicsLayer {
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 24.dp.toPx()
            translationX = parallaxOffsetX
            val scale = 0.96f + 0.04f * p
            scaleX = scale
            scaleY = scale
        },
    ) {
        content()
    }
}

/**
 * Фирменная иконка VisorLink на стартовом экране в неоморфной капсуле Biolume.
 * При нажатии реагирует упругим отскоком, усилением свечения и тактильным импульсом.
 */
@Composable
fun OnboardingBrandEmblem(
    pageOffset: Float,
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val logoShape = tokens.shapes.adapt(CircleShape)

    val tapScale = remember { Animatable(1f) }
    var glowBoost by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .padding(12.dp)
            .size(100.dp)
            .graphicsLayer {
                val absOffset = abs(pageOffset).coerceIn(0f, 1f)
                translationX = -pageOffset * 48.dp.toPx()
                val s = (1f - absOffset * 0.12f) * tapScale.value
                scaleX = s
                scaleY = s
                alpha = 1f - absOffset * 0.5f
            }
            .vlBiopulse(
                tokens = tokens,
                color = cs.primary,
                active = glowBoost || !tokens.reduceMotion,
                shape = logoShape,
            )
            .vlRaised(tokens = tokens.structure, shape = logoShape)
            .vlSignalGlow(
                tokens = tokens.signal,
                color = cs.primary,
                shape = logoShape,
                active = true,
            )
            .clip(logoShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.perform(HapticType.BOOP, enabled = hapticEnabled)
                scope.launch {
                    glowBoost = true
                    tapScale.snapTo(0.88f)
                    tapScale.animateTo(
                        1f,
                        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
                    )
                    delay(300)
                    glowBoost = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Интерактивные ценностные пилюли на экране приветствия:
 * при тапе на любую пилюлю она утапливается (`vlInset`) и внизу плавно раскрывается её описание.
 */
@Composable
fun OnboardingPillarShowcase(
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val haptic = rememberHaptic()
    var selectedPillar by remember { mutableIntStateOf(0) }

    val pillars = listOf(
        Triple(Icons.Default.ChatBubble, stringResource(R.string.intro_pillar_chats), stringResource(R.string.intro_pillar_chats_desc)),
        Triple(Icons.Default.Shield, stringResource(R.string.intro_pillar_privacy), stringResource(R.string.intro_pillar_privacy_desc)),
        Triple(Icons.Default.AutoAwesome, stringResource(R.string.intro_pillar_modules), stringResource(R.string.intro_pillar_modules_desc)),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            pillars.forEachIndexed { idx, (icon, title, _) ->
                val isSelected = selectedPillar == idx
                val shape = tokens.shapes.pill
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 480f),
                    label = "pillar_scale_$idx",
                )
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) tokens.selectionFill else cs.surfaceContainerLow,
                    animationSpec = tween(200),
                    label = "pillar_bg_$idx",
                )
                val tintColor by animateColorAsState(
                    targetValue = if (isSelected) cs.primary else cs.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "pillar_tint_$idx",
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .then(
                            if (isSelected) {
                                Modifier.vlInset(tokens.structure, shape)
                            } else {
                                Modifier.vlRaised(tokens.structure, shape)
                            },
                        )
                        .clip(shape)
                        .background(bgColor)
                        .then(
                            if (isSelected) {
                                Modifier.vlHairline(cs.primary.copy(alpha = 0.35f), shape)
                            } else {
                                Modifier.vlHairline(cs.outlineVariant.copy(alpha = 0.2f), shape)
                            },
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) {
                            if (selectedPillar != idx) {
                                haptic.perform(HapticType.SELECTION, enabled = hapticEnabled)
                                selectedPillar = idx
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tintColor,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = tintColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Карточка раскрытого описания выбранной ценности
        val cardShape = tokens.shapes.adapt(RoundedCornerShape(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .vlRaised(tokens.structure, cardShape)
                .clip(cardShape)
                .background(cs.surfaceContainerLow)
                .vlHairline(cs.outlineVariant.copy(alpha = 0.25f), cardShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = selectedPillar,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 4 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(150)) { -it / 4 })
                },
                label = "pillar_desc",
            ) { idx ->
                Text(
                    text = pillars[idx].third,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Живой интерактивный макет мессенджера VisorLink для экрана выбора стиля:
 * перестраивает цвета, фон и рельеф в реальном времени и позволяет тапнуть по реакции.
 */
@Composable
fun OnboardingLiveMessengerMock(
    hapticEnabled: Boolean,
    pulseKey: Any?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val haptic = rememberHaptic()
    val cardShape = tokens.shapes.adapt(RoundedCornerShape(24.dp))
    val incomingShape = tokens.shapes.adapt(
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
    )
    val outgoingShape = tokens.shapes.adapt(
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
    )
    val inputShape = tokens.shapes.pill

    var reactionLiked by remember { mutableStateOf(false) }
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (!tokens.reduceMotion && pulseKey != null) {
            pulseScale.snapTo(0.985f)
            pulseScale.animateTo(
                1f,
                spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium),
            )
        }
    }

    val primaryColor by animateColorAsState(cs.primary, tween(260), label = "mock_primary")
    val onPrimaryColor by animateColorAsState(cs.onPrimary, tween(260), label = "mock_on_primary")
    val cardBg by animateColorAsState(cs.surfaceContainerLow, tween(260), label = "mock_bg")
    val incomingBg by animateColorAsState(cs.surfaceContainerHigh, tween(260), label = "mock_incoming")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
            }
            .vlRaised(tokens.structure, cardShape)
            .clip(cardShape)
            .background(cardBg)
            .vlHairline(cs.outlineVariant.copy(alpha = 0.25f), cardShape)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Шапка мини-чата
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_background),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.intro_preview_sender),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        VlLiveDot(color = primaryColor, size = 6.dp)
                        Text(
                            text = stringResource(R.string.intro_preview_online),
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }

            // Входящий баббл
            Box(
                modifier = Modifier
                    .align(Alignment.Start)
                    .widthIn(max = 240.dp)
                    .vlRaised(tokens.structure, incomingShape)
                    .clip(incomingShape)
                    .background(incomingBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.intro_preview_incoming),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurface,
                )
            }

            // Исходящий баббл + интерактивная реакция
            Column(
                modifier = Modifier.align(Alignment.End),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .vlSignalGlow(tokens.signal, primaryColor, outgoingShape, active = true)
                        .clip(outgoingShape)
                        .background(primaryColor)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.intro_preview_outgoing),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = onPrimaryColor,
                    )
                }

                // Интерактивная пилюля реакции
                val reactionShape = tokens.shapes.pill
                val reactionScale by animateFloatAsState(
                    targetValue = if (reactionLiked) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                    label = "reaction_scale",
                )
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = reactionScale
                            scaleY = reactionScale
                        }
                        .clip(reactionShape)
                        .background(if (reactionLiked) primaryColor.copy(alpha = 0.16f) else cs.surfaceContainerHighest)
                        .vlHairline(
                            color = if (reactionLiked) primaryColor.copy(alpha = 0.5f) else cs.outlineVariant.copy(alpha = 0.3f),
                            shape = reactionShape,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptic.perform(HapticType.REACTION, enabled = hapticEnabled)
                            reactionLiked = !reactionLiked
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (reactionLiked) primaryColor else cs.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                    AnimatedContent(
                        targetState = if (reactionLiked) 2 else 1,
                        transitionSpec = {
                            (fadeIn(tween(150)) + slideInVertically { -it }) togetherWith
                                (fadeOut(tween(120)) + slideOutVertically { it })
                        },
                        label = "reaction_count",
                    ) { count ->
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (reactionLiked) primaryColor else cs.onSurfaceVariant,
                        )
                    }
                }
            }

            // Мини-поле ввода
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .vlInset(tokens.structure, inputShape)
                    .clip(inputShape)
                    .background(cs.surface)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.intro_preview_input_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(primaryColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = onPrimaryColor,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

private const val DOCK_TAB_CHATS = 0
private const val DOCK_TAB_FEED = 1
private const val DOCK_TAB_DIARY = 2
private const val DOCK_TAB_MUSIC = 3

/**
 * Живая сцена главного экрана VisorLink для страницы настройки модулей:
 * сверху показывает интерактивное превью активной вкладки, а внизу — плавающий док навигации,
 * куда включённые модули (Каналы, Дневник, Музыка) влетают и исчезают в реальном времени.
 */
@Composable
fun OnboardingDockStage(
    feedEnabled: Boolean,
    diaryEnabled: Boolean,
    musicEnabled: Boolean,
    hapticEnabled: Boolean,
    pageOffset: Float,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val haptic = rememberHaptic()
    val stageShape = tokens.shapes.adapt(RoundedCornerShape(24.dp))
    val dockShape = tokens.shapes.bar

    var activeDockTab by remember { mutableIntStateOf(DOCK_TAB_CHATS) }

    // Если активный в превью модуль отключили — мягко возвращаемся в Чаты
    LaunchedEffect(feedEnabled, diaryEnabled, musicEnabled) {
        val valid = when (activeDockTab) {
            DOCK_TAB_FEED -> feedEnabled
            DOCK_TAB_DIARY -> diaryEnabled
            DOCK_TAB_MUSIC -> musicEnabled
            else -> true
        }
        if (!valid) activeDockTab = DOCK_TAB_CHATS
    }

    // При включении нового модуля — автоматически показываем его в превью, чтобы эффект был мгновенным
    var prevFeed by remember { mutableStateOf(feedEnabled) }
    var prevDiary by remember { mutableStateOf(diaryEnabled) }
    var prevMusic by remember { mutableStateOf(musicEnabled) }
    LaunchedEffect(feedEnabled) {
        if (feedEnabled && !prevFeed) activeDockTab = DOCK_TAB_FEED
        prevFeed = feedEnabled
    }
    LaunchedEffect(diaryEnabled) {
        if (diaryEnabled && !prevDiary) activeDockTab = DOCK_TAB_DIARY
        prevDiary = diaryEnabled
    }
    LaunchedEffect(musicEnabled) {
        if (musicEnabled && !prevMusic) activeDockTab = DOCK_TAB_MUSIC
        prevMusic = musicEnabled
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .graphicsLayer {
                val absOffset = abs(pageOffset).coerceIn(0f, 1f)
                translationX = -pageOffset * 28.dp.toPx()
                alpha = 1f - absOffset * 0.45f
            }
            .vlInset(tokens.structure, stageShape)
            .clip(stageShape)
            .background(cs.surfaceContainerLow)
            .vlHairline(cs.outlineVariant.copy(alpha = 0.25f), stageShape)
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Содержимое выбранной вкладки на мини-экране
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 122.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = activeDockTab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(200)) + slideInVertically(tween(220)) { dir * it / 5 }) togetherWith
                            (fadeOut(tween(140)) + slideOutVertically(tween(180)) { -dir * it / 5 })
                    },
                    label = "dock_stage_content",
                ) { tab ->
                    when (tab) {
                        DOCK_TAB_FEED -> MiniFeedPreviewCard()
                        DOCK_TAB_DIARY -> MiniDiaryPreviewCard()
                        DOCK_TAB_MUSIC -> MiniMusicPreviewCard(reduceMotion = tokens.reduceMotion)
                        else -> MiniChatsPreviewCard()
                    }
                }
            }

            // Живой плавающий док навигации VisorLink
            Row(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .vlRaised(tokens.structure, dockShape)
                    .clip(dockShape)
                    .background(cs.surface)
                    .vlHairline(cs.outlineVariant.copy(alpha = 0.35f), dockShape)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockPreviewItem(
                    icon = Icons.Default.ChatBubble,
                    label = stringResource(R.string.intro_dock_chats),
                    selected = activeDockTab == DOCK_TAB_CHATS,
                    accent = cs.primary,
                    onClick = {
                        if (activeDockTab != DOCK_TAB_CHATS) {
                            haptic.perform(HapticType.TAB_SWITCH, enabled = hapticEnabled)
                            activeDockTab = DOCK_TAB_CHATS
                        }
                    },
                )

                DockModuleTabSlot(
                    visible = feedEnabled,
                    icon = Icons.Default.Explore,
                    label = stringResource(R.string.feed_title),
                    selected = activeDockTab == DOCK_TAB_FEED,
                    accent = cs.primary,
                    onClick = {
                        if (activeDockTab != DOCK_TAB_FEED) {
                            haptic.perform(HapticType.TAB_SWITCH, enabled = hapticEnabled)
                            activeDockTab = DOCK_TAB_FEED
                        }
                    },
                )

                DockModuleTabSlot(
                    visible = diaryEnabled,
                    icon = Icons.Default.Book,
                    label = stringResource(R.string.diary_title),
                    selected = activeDockTab == DOCK_TAB_DIARY,
                    accent = Color(0xFF10B981),
                    onClick = {
                        if (activeDockTab != DOCK_TAB_DIARY) {
                            haptic.perform(HapticType.TAB_SWITCH, enabled = hapticEnabled)
                            activeDockTab = DOCK_TAB_DIARY
                        }
                    },
                )

                DockModuleTabSlot(
                    visible = musicEnabled,
                    icon = Icons.Default.MusicNote,
                    label = stringResource(R.string.nav_tab_music),
                    selected = activeDockTab == DOCK_TAB_MUSIC,
                    accent = Color(0xFFFFB020),
                    onClick = {
                        if (activeDockTab != DOCK_TAB_MUSIC) {
                            haptic.perform(HapticType.TAB_SWITCH, enabled = hapticEnabled)
                            activeDockTab = DOCK_TAB_MUSIC
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DockModuleTabSlot(
    visible: Boolean,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) +
            expandHorizontally(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.7f),
        exit = fadeOut(tween(130)) +
            shrinkHorizontally(tween(170)) +
            scaleOut(tween(130), targetScale = 0.7f),
    ) {
        DockPreviewItem(
            icon = icon,
            label = label,
            selected = selected,
            accent = accent,
            onClick = onClick,
        )
    }
}

@Composable
private fun DockPreviewItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val pillShape = tokens.shapes.pill
    val bg by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(180),
        label = "dock_item_bg",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) accent else cs.onSurfaceVariant,
        animationSpec = tween(180),
        label = "dock_item_tint",
    )

    Row(
        modifier = Modifier
            .clip(pillShape)
            .background(bg)
            .then(if (selected) Modifier.vlInset(tokens.structure, pillShape) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(150)) + expandHorizontally(spring(dampingRatio = 0.78f)),
            exit = fadeOut(tween(110)) + shrinkHorizontally(tween(130)),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MiniChatsPreviewCard() {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val rowShape = tokens.shapes.adapt(RoundedCornerShape(16.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiniChatRow(
            initial = "A",
            name = stringResource(R.string.intro_dock_chat_1_name),
            preview = stringResource(R.string.intro_dock_chat_1_msg),
            avatarColor = cs.primary,
            unread = true,
            shape = rowShape,
        )
        MiniChatRow(
            initial = "D",
            name = stringResource(R.string.intro_dock_chat_2_name),
            preview = stringResource(R.string.intro_dock_chat_2_msg),
            avatarColor = cs.secondary,
            unread = false,
            shape = rowShape,
        )
    }
}

@Composable
private fun MiniChatRow(
    initial: String,
    name: String,
    preview: String,
    avatarColor: Color,
    unread: Boolean,
    shape: Shape,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .vlRaised(tokens.structure, shape)
            .clip(shape)
            .background(cs.surface)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = avatarColor,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (unread) {
            VlLiveDot(color = cs.primary, size = 7.dp)
        }
    }
}

@Composable
private fun MiniFeedPreviewCard() {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val cardShape = tokens.shapes.adapt(RoundedCornerShape(18.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .vlRaised(tokens.structure, cardShape)
            .clip(cardShape)
            .background(cs.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.intro_dock_feed_tag),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = cs.primary,
            )
        }
        Text(
            text = stringResource(R.string.intro_dock_feed_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
        )
        Text(
            text = stringResource(R.string.intro_dock_feed_body),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MiniDiaryPreviewCard() {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val cardShape = tokens.shapes.adapt(RoundedCornerShape(18.dp))
    val emerald = Color(0xFF10B981)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .vlRaised(tokens.structure, cardShape)
            .clip(cardShape)
            .background(cs.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Book,
                contentDescription = null,
                tint = emerald,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.intro_dock_diary_date),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = emerald,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = cs.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = stringResource(R.string.intro_dock_diary_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
        )
        Text(
            text = stringResource(R.string.intro_dock_diary_body),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MiniMusicPreviewCard(reduceMotion: Boolean) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val cardShape = tokens.shapes.adapt(RoundedCornerShape(18.dp))
    val amber = Color(0xFFFFB020)

    val infinite = rememberInfiniteTransition(label = "mini_eq")
    val barMultipliers = listOf(0.45f, 0.9f, 0.6f, 1.0f, 0.5f)
    val phases = barMultipliers.mapIndexed { i, target ->
        if (reduceMotion) {
            remember { androidx.compose.runtime.mutableFloatStateOf(target) }
        } else {
            infinite.animateFloat(
                initialValue = 0.25f,
                targetValue = target,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 480 + i * 110, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "eq_bar_$i",
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .vlRaised(tokens.structure, cardShape)
            .clip(cardShape)
            .background(cs.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(tokens.shapes.adapt(RoundedCornerShape(12.dp)))
                .background(
                    Brush.linearGradient(
                        listOf(amber.copy(alpha = 0.85f), cs.primary.copy(alpha = 0.75f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.intro_dock_music_track),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.intro_dock_music_artist),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }

        // Живой эквалайзер
        Row(
            modifier = Modifier.height(26.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            phases.forEach { anim ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight(anim.value)
                        .clip(CircleShape)
                        .background(amber),
                )
            }
        }
    }
}

/**
 * Тактильная модульная карточка в стиле Biolume:
 * - включена → физически приподнята (`vlRaised`) с живой точкой и галочкой;
 * - выключена → утоплена (`vlInset`) с приглушённой иконкой и кнопкой «Добавить».
 */
@Composable
fun OnboardingModuleTile(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    hapticEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val haptic = rememberHaptic()
    val tileShape = tokens.shapes.adapt(RoundedCornerShape(20.dp))

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 460f),
        label = "module_tile_scale",
    )
    val bgColor by animateColorAsState(
        targetValue = if (enabled) cs.surface else cs.surfaceContainerLow,
        animationSpec = tween(200),
        label = "module_tile_bg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (enabled) accentColor else cs.onSurfaceVariant.copy(alpha = 0.55f),
        animationSpec = tween(200),
        label = "module_tile_icon_tint",
    )
    val iconBoxBg by animateColorAsState(
        targetValue = if (enabled) accentColor.copy(alpha = 0.16f) else cs.surfaceContainerHighest.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "module_tile_icon_bg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (enabled) {
                    Modifier.vlRaised(tokens.structure, tileShape)
                } else {
                    Modifier.vlInset(tokens.structure, tileShape)
                },
            )
            .clip(tileShape)
            .background(bgColor)
            .vlHairline(
                color = if (enabled) accentColor.copy(alpha = 0.35f) else cs.outlineVariant.copy(alpha = 0.2f),
                shape = tileShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptic.perform(HapticType.TAB_SWITCH, enabled = hapticEnabled)
                onToggle()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(tokens.shapes.adapt(RoundedCornerShape(14.dp)))
                .background(iconBoxBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) cs.onSurface else cs.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant.copy(alpha = if (enabled) 0.9f else 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Статус-капсула: Вкл / Добавить
        val badgeShape = tokens.shapes.pill
        Row(
            modifier = Modifier
                .clip(badgeShape)
                .background(
                    if (enabled) accentColor.copy(alpha = 0.16f) else cs.surfaceContainerHighest.copy(alpha = 0.65f),
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (enabled) Icons.Default.Check else Icons.Default.Add,
                contentDescription = null,
                tint = if (enabled) accentColor else cs.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(if (enabled) R.string.intro_module_on else R.string.intro_module_off),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) accentColor else cs.onSurfaceVariant,
            )
        }
    }
}
