package org.visorlink.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.visorlink.app.R
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlInset
import org.visorlink.app.ui.theme.vlRaised
import kotlin.math.abs

/**
 * Нижняя навигация — плавающая панель в стиле Biolume с неоморфным желейным бегунком
 * (Liquid Runner).
 */
@Composable
fun VlNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    diaryEnabled: Boolean,
    discoverEnabled: Boolean,
    musicEnabled: Boolean = false,
    onOpenDiary: () -> Unit,
    onOpenMusic: () -> Unit = {},
    /** Вкладка «Профиль» (флаг `enable_profile_navbar`) — последняя, значок ID-карты. */
    profileEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val isDark = cs.surface.luminance() < 0.5f
    // У M3E bar = 32dp, у Biolume — 50% от 64dp, то есть те же 32dp; у Forge — без скруглений
    val barShape: Shape = tokens.shapes.bar

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 16.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        NeumorphicLiquidNavBarContent(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            diaryEnabled = diaryEnabled,
            discoverEnabled = discoverEnabled,
            musicEnabled = musicEnabled,
            onOpenDiary = onOpenDiary,
            onOpenMusic = onOpenMusic,
            profileEnabled = profileEnabled,
            barShape = barShape
        )
    }
}

/** Номер вкладки «Профиль» в MainScreen (0 — чаты, 1 — каналы, 2 — дневник, 3 — музыка). */
const val NAV_TAB_PROFILE = 4

/**
 * Описание вкладки для жидкостного бара
 */
private data class VlNavTabDef(
    val tabId: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

private data class TabSlotMetrics(
    val leftDp: Float,
    val widthDp: Float
)

private fun getActiveTabWidthDp(label: String): Float {
    val textWidthDp = label.length * 8.0f
    return (24f + 6f + textWidthDp + 28f).coerceAtLeast(84f)
}

private fun calculateTabSlots(
    tabs: List<VlNavTabDef>,
    activeIdx: Int,
    totalWidthDp: Float
): List<TabSlotMetrics> {
    val count = tabs.size
    if (count == 0) return emptyList()

    var widths = tabs.mapIndexed { idx, tab ->
        if (idx == activeIdx) getActiveTabWidthDp(tab.label) else 48f
    }
    // Пять вкладок на узком экране (360dp) не помещаются: сначала ужимаем неактивные до 40dp,
    // потом подпись активной (она обрезается многоточием)
    if (count > 1 && widths.sum() > totalWidthDp) {
        val activeWanted = widths[activeIdx]
        val inactive = ((totalWidthDp - activeWanted) / (count - 1)).coerceIn(40f, 48f)
        val active = activeWanted.coerceAtMost((totalWidthDp - inactive * (count - 1)).coerceAtLeast(48f))
        widths = List(count) { if (it == activeIdx) active else inactive }
    }
    val contentSum = widths.sum()
    val availableSpace = (totalWidthDp - contentSum).coerceAtLeast(0f)

    // При 4 вкладках распределяем свободное пространство между всеми вкладками (N - 1)
    // При 2-3 вкладках центрируем блок вкладок с одинаковыми боковыми полями (N + 1)
    val useEdgeMargins = count < 4
    val gapCount = if (useEdgeMargins) count + 1 else (count - 1).coerceAtLeast(1)
    val gapSize = availableSpace / gapCount

    val slots = ArrayList<TabSlotMetrics>(count)
    var currentLeft = if (useEdgeMargins) gapSize else 0f

    for (w in widths) {
        slots.add(TabSlotMetrics(leftDp = currentLeft, widthDp = w))
        currentLeft += w + gapSize
    }
    return slots
}

/**
 * Неоморфный жидкостный навбар (Liquid Navigation Bar) с физикой бегунка Squash & Stretch.
 * Выполнен в строгом стиле Biolume: 100% сплошной непрозрачный материал, мягкие неоморфные тени.
 */
@Composable
private fun NeumorphicLiquidNavBarContent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    diaryEnabled: Boolean,
    discoverEnabled: Boolean,
    musicEnabled: Boolean,
    onOpenDiary: () -> Unit,
    onOpenMusic: () -> Unit,
    profileEnabled: Boolean,
    barShape: Shape
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val density = LocalDensity.current

    val chatsLabel = stringResource(R.string.nav_tab_chats)
    val feedLabel = stringResource(R.string.feed_title)
    val musicLabel = stringResource(R.string.nav_tab_music)
    val diaryLabel = stringResource(R.string.diary_title)
    val profileLabel = stringResource(R.string.nav_tab_profile)

    val navTabs = remember(chatsLabel, feedLabel, musicLabel, diaryLabel, profileLabel, diaryEnabled, discoverEnabled, musicEnabled, profileEnabled, onOpenDiary, onOpenMusic) {
        buildList {
            add(
                VlNavTabDef(
                    tabId = 0,
                    icon = Icons.Outlined.ChatBubbleOutline,
                    selectedIcon = Icons.Filled.ChatBubble,
                    label = chatsLabel,
                    onClick = { onTabSelected(0) }
                )
            )
            if (discoverEnabled) {
                add(
                    VlNavTabDef(
                        tabId = 1,
                        icon = Icons.Outlined.Explore,
                        selectedIcon = Icons.Filled.Explore,
                        label = feedLabel,
                        onClick = { onTabSelected(1) }
                    )
                )
            }
            if (musicEnabled) {
                add(
                    VlNavTabDef(
                        tabId = 3,
                        icon = Icons.Default.Audiotrack,
                        selectedIcon = Icons.Default.Audiotrack,
                        label = musicLabel,
                        onClick = onOpenMusic
                    )
                )
            }
            if (diaryEnabled) {
                add(
                    VlNavTabDef(
                        tabId = 2,
                        icon = Icons.Default.Edit,
                        selectedIcon = Icons.Default.Edit,
                        label = diaryLabel,
                        onClick = onOpenDiary
                    )
                )
            }
            if (profileEnabled) {
                add(
                    VlNavTabDef(
                        tabId = NAV_TAB_PROFILE,
                        icon = Icons.Outlined.Badge,
                        selectedIcon = Icons.Filled.Badge,
                        label = profileLabel,
                        onClick = { onTabSelected(NAV_TAB_PROFILE) }
                    )
                )
            }
        }
    }

    val activeIndex = navTabs.indexOfFirst { it.tabId == selectedTab }.coerceAtLeast(0)
    val pillShape: Shape = tokens.shapes.pill

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val initialEstimatedWidthDp = remember(screenWidthDp) {
        val horizontalMargin = 48f
        val innerPadding = 16f
        (screenWidthDp - horizontalMargin - innerPadding).coerceAtLeast(100f)
    }
    val initialEstimatedWidthPx = with(density) { initialEstimatedWidthDp.dp.toPx() }

    var totalWidthPx by remember { mutableFloatStateOf(initialEstimatedWidthPx) }

    val initialSlot = remember(navTabs, activeIndex, initialEstimatedWidthDp) {
        calculateTabSlots(navTabs, activeIndex, initialEstimatedWidthDp).getOrNull(activeIndex)
    }
    val initialLeftPx = with(density) { (initialSlot?.leftDp ?: 0f).dp.toPx() }
    val initialWidthPx = with(density) { (initialSlot?.widthDp ?: 48f).dp.toPx() }

    // Forge v2: бегунок едет ровно, без пружины и растяжения
    val glitch = VlTheme.tokens.glitchMotion
    val runnerLeft = remember { Animatable(initialLeftPx) }
    val runnerWidth = remember { Animatable(initialWidthPx) }
    val stretchAnim = remember { Animatable(0f) }
    var prevActiveIndex by remember { mutableIntStateOf(activeIndex) }

    val totalWidthDp = with(density) { totalWidthPx.toDp().value }
    val tabSlots = remember(navTabs, activeIndex, totalWidthDp) {
        calculateTabSlots(navTabs, activeIndex, totalWidthDp)
    }

    val activeSlot = tabSlots.getOrNull(activeIndex)

    LaunchedEffect(activeSlot) {
        if (activeSlot != null) {
            val targetLeft = with(density) { activeSlot.leftDp.dp.toPx() }
            val targetWidth = with(density) { activeSlot.widthDp.dp.toPx() }
            launch {
                runnerLeft.animateTo(
                    targetLeft,
                    navSpring(glitch)
                )
            }
            launch {
                runnerWidth.animateTo(
                    targetWidth,
                    navSpring(glitch)
                )
            }
        }
    }

    LaunchedEffect(activeIndex) {
        if (prevActiveIndex != activeIndex) {
            val diff = activeIndex - prevActiveIndex
            prevActiveIndex = activeIndex
            val dir = if (diff > 0) 1f else -1f
            if (!glitch) launch {
                stretchAnim.animateTo(dir * 0.16f, tween(80, easing = FastOutSlowInEasing))
                stretchAnim.animateTo(0f, spring(dampingRatio = 0.60f, stiffness = 320f))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .vlInset(tokens.structure, barShape)
            .clip(barShape)
            .background(cs.surfaceContainerLow)
            .vlHairline(cs.outlineVariant.copy(alpha = 0.5f), barShape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .onGloballyPositioned { coords ->
                // Модификатор стоит после padding — размер уже внутренний. Раньше
                // отсюда ещё раз вычитались 16dp, ширина занижалась, и вкладки
                // вместе с бегунком съезжали влево (справа пустело ~16dp)
                totalWidthPx = coords.size.width.toFloat()
            }
    ) {
        // Динамический жидкостный неоморфный бегунок с адаптивным размером
        if (totalWidthPx > 10f) {
            val runnerLeftDp = with(density) { runnerLeft.value.toDp() }
            val runnerWidthDp = with(density) { runnerWidth.value.toDp().coerceAtLeast(40.dp) }

            Box(
                modifier = Modifier
                    .offset(x = runnerLeftDp)
                    .width(runnerWidthDp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        // Читаем в фазе рисования: растяжение не вызывает рекомпозицию каждый кадр
                        val stretch = abs(stretchAnim.value)
                        this.scaleX = 1f + stretch * 0.18f
                        this.scaleY = 1f - stretch * 0.12f
                        this.clip = false
                    }
                    .vlRaised(tokens.structure, pillShape)
                    .clip(pillShape)
                    .background(cs.surfaceContainerHighest)
                    .vlHairline(cs.outlineVariant, pillShape)
            )
        }

        // Вкладки с контентно-адаптивными слотами
        navTabs.forEachIndexed { index, tab ->
            val slot = tabSlots.getOrNull(index) ?: TabSlotMetrics(0f, 48f)
            val isSelected = index == activeIndex

            val animLeftDp by animateFloatAsState(
                targetValue = slot.leftDp,
                animationSpec = navSpring(glitch),
                label = "liquid_tab_left_$index"
            )
            val animWidthDp by animateFloatAsState(
                targetValue = slot.widthDp,
                animationSpec = navSpring(glitch),
                label = "liquid_tab_width_$index"
            )

            val contentColor by animateColorAsState(
                targetValue = if (isSelected) cs.primary else cs.onSurfaceVariant,
                animationSpec = navBouncy(glitch),
                label = "liquid_tab_content_color_$index"
            )

            Box(
                modifier = Modifier
                    .offset(x = animLeftDp.dp)
                    .width(animWidthDp.dp)
                    .fillMaxHeight()
                    .clip(pillShape)
                    .semantics { selected = isSelected }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = tab.onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.12f else 1.0f,
                        animationSpec = navBouncy(glitch),
                        label = "liquid_icon_scale_$index"
                    )

                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                        contentDescription = tab.label,
                        tint = contentColor,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scale)
                    )

                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn(tween(140, delayMillis = 30)) + expandHorizontally(
                            navBouncy(glitch, Spring.StiffnessMediumLow)
                        ),
                        exit = fadeOut(tween(80)) + shrinkHorizontally(tween(90))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = contentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Пункт навигации: горизонтальная расширяющаяся pill-капсула со скейлом иконки и анимацией текста.
 */
@Composable
fun VlTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val isDark = cs.surface.luminance() < 0.5f

    val interactionSource = remember { MutableInteractionSource() }

    // Selection pill color
    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            if (isDark) cs.primary.copy(alpha = 0.20f)
            else cs.primary.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tab_pill_color"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.onSurfaceVariant,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tab_content_color"
    )

    val pillShape: Shape = tokens.shapes.pill

    val selectionProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tab_selection_progress"
    )

    val pillModifier = if (tokens.isBiolume) {
        val maxAlpha1 = if (isDark) 0.16f else 0.12f
        val maxAlpha2 = if (isDark) 0.08f else 0.05f
        val borderAlpha = if (isDark) 0.18f else 0.15f

        val pillGradient = Brush.horizontalGradient(
            listOf(
                cs.primary.copy(alpha = maxAlpha1 * selectionProgress),
                cs.primary.copy(alpha = maxAlpha2 * selectionProgress)
            )
        )
        val pillBorder = if (selectionProgress > 0.02f) {
            BorderStroke(
                1.dp,
                cs.primary.copy(alpha = borderAlpha * selectionProgress)
            )
        } else null

        Modifier
            .clip(pillShape)
            .background(pillGradient)
            .then(
                if (pillBorder != null) Modifier.border(pillBorder, pillShape)
                else Modifier
            )
    } else {
        Modifier
            .clip(pillShape)
            .background(pillColor)
    }

    Box(
        modifier = modifier
            .then(pillModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.15f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "icon_scale"
            )

            Icon(
                imageVector = if (selected) selectedIcon else icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .scale(scale)
            )

            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(150)) + expandHorizontally(
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = fadeOut(tween(100)) + shrinkHorizontally(tween(120))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = contentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Пружина бегунка; в Forge v2 — ровный переход терминала. */
private fun <T> navSpring(glitch: Boolean): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    if (glitch) TerminalMotion.tween(260) else spring(dampingRatio = 0.72f, stiffness = 320f)

/** Упругий акцент выбранной вкладки; в Forge v2 — без отскока. */
private fun <T> navBouncy(glitch: Boolean, stiffness: Float = Spring.StiffnessLow): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    if (glitch) TerminalMotion.tween(200)
    else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = stiffness)
