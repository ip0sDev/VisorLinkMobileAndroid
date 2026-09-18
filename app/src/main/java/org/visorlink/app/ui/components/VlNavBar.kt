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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.R
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlInset
import org.visorlink.app.ui.theme.vlRaised
import kotlin.math.abs

/**
 * Нижняя навигация — плавающая панель в стиле Biolume.
 * При включенном флаге animation_test активируется неоморфный желейный бегунок (Liquid Runner).
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
    modifier: Modifier = Modifier,
    flagsRepository: FlagsRepository = koinInject()
) {
    val flags by flagsRepository.flags.collectAsState()
    val isLiquidEnabled = flags.isEnabled("animation_test")

    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val isDark = cs.surface.luminance() < 0.5f
    val barShape: Shape = if (tokens.isForge) tokens.shapes.bar else RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = if (tokens.isForge) 0.dp else 24.dp,
                end = if (tokens.isForge) 0.dp else 24.dp,
                top = 8.dp,
                bottom = if (tokens.isForge) 0.dp else 16.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLiquidEnabled) {
            NeumorphicLiquidNavBarContent(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                diaryEnabled = diaryEnabled,
                discoverEnabled = discoverEnabled,
                musicEnabled = musicEnabled,
                onOpenDiary = onOpenDiary,
                onOpenMusic = onOpenMusic,
                barShape = barShape
            )
        } else {
            val barBrush = remember(tokens.isBiolume, tokens.structure.enabled, cs) {
                if (tokens.isBiolume) {
                    val topColor = cs.surfaceContainerHigh.copy(alpha = 0.96f)
                    val bottomColor = cs.surfaceContainer.copy(alpha = 0.94f)
                    Brush.verticalGradient(listOf(topColor, bottomColor))
                } else {
                    val color = if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerLow
                    Brush.verticalGradient(listOf(color, color))
                }
            }

            val barBorder = remember(tokens.isBiolume, cs, isDark) {
                if (tokens.isBiolume) {
                    val topHighlight = if (isDark) cs.outlineVariant.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.50f)
                    val bottomShadow = if (isDark) cs.outlineVariant.copy(alpha = 0.04f) else cs.outlineVariant.copy(alpha = 0.12f)
                    BorderStroke(1.dp, Brush.verticalGradient(listOf(topHighlight, bottomShadow)))
                } else null
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .then(
                        if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, barShape)
                        else Modifier
                    )
                    .clip(barShape)
                    .background(barBrush, barShape)
                    .then(
                        if (barBorder != null) Modifier.border(barBorder, barShape)
                        else if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, barShape)
                        else Modifier
                    )
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VlTabItem(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    icon = Icons.Outlined.ChatBubbleOutline,
                    selectedIcon = Icons.Filled.ChatBubble,
                    label = stringResource(R.string.nav_tab_chats),
                )
                if (discoverEnabled) {
                    VlTabItem(
                        selected = selectedTab == 1,
                        onClick = { onTabSelected(1) },
                        icon = Icons.Outlined.Explore,
                        selectedIcon = Icons.Filled.Explore,
                        label = stringResource(R.string.feed_title),
                    )
                }
                if (musicEnabled) {
                    VlTabItem(
                        selected = selectedTab == 3,
                        onClick = onOpenMusic,
                        icon = Icons.Default.Audiotrack,
                        selectedIcon = Icons.Default.Audiotrack,
                        label = stringResource(R.string.nav_tab_music),
                    )
                }
                if (diaryEnabled) {
                    VlTabItem(
                        selected = selectedTab == 2,
                        onClick = onOpenDiary,
                        icon = Icons.Default.Edit,
                        selectedIcon = Icons.Default.Edit,
                        label = stringResource(R.string.diary_title),
                    )
                }
            }
        }
    }
}

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

    val widths = tabs.mapIndexed { idx, tab ->
        if (idx == activeIdx) getActiveTabWidthDp(tab.label) else 48f
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
    barShape: Shape
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val density = LocalDensity.current

    val chatsLabel = stringResource(R.string.nav_tab_chats)
    val feedLabel = stringResource(R.string.feed_title)
    val musicLabel = stringResource(R.string.nav_tab_music)
    val diaryLabel = stringResource(R.string.diary_title)

    val navTabs = remember(chatsLabel, feedLabel, musicLabel, diaryLabel, diaryEnabled, discoverEnabled, musicEnabled, onOpenDiary, onOpenMusic) {
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
        }
    }

    val activeIndex = navTabs.indexOfFirst { it.tabId == selectedTab }.coerceAtLeast(0)
    val pillShape: Shape = if (tokens.isForge) tokens.shapes.pill else CircleShape

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val initialEstimatedWidthDp = remember(screenWidthDp, tokens.isForge) {
        val horizontalMargin = if (tokens.isForge) 0f else 48f
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
                    spring(dampingRatio = 0.72f, stiffness = 320f)
                )
            }
            launch {
                runnerWidth.animateTo(
                    targetWidth,
                    spring(dampingRatio = 0.72f, stiffness = 320f)
                )
            }
        }
    }

    LaunchedEffect(activeIndex) {
        if (prevActiveIndex != activeIndex) {
            val diff = activeIndex - prevActiveIndex
            prevActiveIndex = activeIndex
            val dir = if (diff > 0) 1f else -1f
            launch {
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
                totalWidthPx = coords.size.width.toFloat() - with(density) { 16.dp.toPx() }
            }
    ) {
        // Динамический жидкостный неоморфный бегунок с адаптивным размером
        if (totalWidthPx > 10f) {
            val runnerLeftDp = with(density) { runnerLeft.value.toDp() }
            val runnerWidthDp = with(density) { runnerWidth.value.toDp().coerceAtLeast(40.dp) }
            val scaleX = 1f + abs(stretchAnim.value) * 0.18f
            val scaleY = 1f - abs(stretchAnim.value) * 0.12f

            Box(
                modifier = Modifier
                    .offset(x = runnerLeftDp)
                    .width(runnerWidthDp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        this.scaleX = scaleX
                        this.scaleY = scaleY
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
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 320f
                ),
                label = "liquid_tab_left_$index"
            )
            val animWidthDp by animateFloatAsState(
                targetValue = slot.widthDp,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 320f
                ),
                label = "liquid_tab_width_$index"
            )

            val contentColor by animateColorAsState(
                targetValue = if (isSelected) cs.primary else cs.onSurfaceVariant,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "liquid_tab_content_color_$index"
            )

            Box(
                modifier = Modifier
                    .offset(x = animLeftDp.dp)
                    .width(animWidthDp.dp)
                    .fillMaxHeight()
                    .clip(pillShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
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
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
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
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
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

    val pillShape: Shape = if (tokens.isForge) tokens.shapes.pill else CircleShape

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
