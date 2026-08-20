package by.iposdev.visorlink.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.theme.VlPressStyle
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.motionSpec
import by.iposdev.visorlink.ui.theme.motionSpec
import by.iposdev.visorlink.ui.theme.vlHairline
import by.iposdev.visorlink.ui.theme.vlRaised

/**
 * Нижняя навигация — плавающая «таблетка».
 *
 * Разделение слоёв здесь строго по гайдлайну:
 *  - **панель** — структура: raised + нейтральная грань, она физически лежит над
 *    контентом (§4.1);
 *  - **активный пункт** — ПЛОСКАЯ заливка `selectionFill` + pill-форма, без
 *    рельефа и без свечения. Это прямое требование §4.2 («Selected: чип, вкладка,
 *    nav-item → плоская заливка `*Container`, БЕЗ glow») — вдавливать pill, как
 *    чип из §7, здесь нельзя: на элементе высотой 30dp inset-тень читается размытым
 *    пятном, а не рельефом.
 *
 * Панель сама отбивается от системной навигации: у Scaffold в `MainScreen`
 * `contentWindowInsets` обнулены, поэтому рассчитывать на inset родителя нельзя.
 */
@Composable
fun VlNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    diaryEnabled: Boolean,
    discoverEnabled: Boolean,
    onOpenDiary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val barShape: Shape = tokens.shapes.bar

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // Рельеф рисуется ЗА границами панели. В Forge панель прижата к краям,
            // поэтому воздух снаружи ей не нужен.
            .padding(
                start = if (tokens.isForge) 0.dp else 20.dp,
                end = if (tokens.isForge) 0.dp else 20.dp,
                top = 8.dp,
                bottom = if (tokens.isForge) 0.dp else 12.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, barShape)
                    else Modifier
                )
                .clip(barShape)
                .background(
                    if (tokens.structure.enabled) cs.surfaceContainer else cs.surfaceContainerLow,
                    barShape,
                )
                .then(
                    if (tokens.structure.enabled) Modifier.vlHairline(cs.outlineVariant, barShape)
                    else Modifier
                )
                .padding(horizontal = 6.dp, vertical = if (tokens.isForge) 8.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VlTabItem(
                modifier = Modifier.weight(1f),
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                icon = Icons.Outlined.ChatBubbleOutline,
                selectedIcon = Icons.Filled.ChatBubble,
                // Ярлык вкладки, а НЕ chatlist_title: тот равен «VisorLink» —
                // это заголовок экрана, и в роли подписи вкладки он выглядел
                // как случайное слово рядом с «Discover» и «Дневник».
                label = stringResource(R.string.nav_tab_chats),
            )
            if (discoverEnabled) {
                VlTabItem(
                    modifier = Modifier.weight(1f),
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    icon = Icons.Outlined.Explore,
                    selectedIcon = Icons.Filled.Explore,
                    label = stringResource(R.string.feed_title),
                )
            }
            if (diaryEnabled) {
                VlTabItem(
                    modifier = Modifier.weight(1f),
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

/**
 * Пункт навигации: pill-индикатор с иконкой и подпись под ним.
 *
 * Индикатор — **отдельный слой позади иконки**, а не её родитель. Иначе анимация
 * появления масштабировала бы и саму иконку: невыбранные пункты рисовались бы
 * ощутимо мельче выбранного (ровно этот баг здесь и был).
 *
 * Ширину задаёт родитель через `weight(1f)`: панель тянется на всю доступную
 * ширину и делит её равными долями. При размере по контенту панель с двумя
 * вкладками сжималась до ~164dp и висела узкой пилюлей по центру экрана, а
 * подписи разной длины («Чаты» / «Discover») делали пункты неровными.
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

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && tokens.motion.pressStyle != VlPressStyle.STAMP) 0.93f else 1f,
        animationSpec = tokens.motion.motionSpec(),
        label = "tab_press",
    )
    val stampOffset by animateDpAsState(
        targetValue = if (isPressed && tokens.motion.pressStyle == VlPressStyle.STAMP) tokens.motion.pressOffset else 0.dp,
        animationSpec = tokens.motion.motionSpec<Dp>(),
        label = "tab_stamp",
    )
    val indicatorScale by animateFloatAsState(
        // Forge не «вырастает», а щёлкает: масштаба нет, только заливка.
        targetValue = if (selected || tokens.isForge) 1f else 0.7f,
        animationSpec = tokens.motion.motionSpec(),
        label = "tab_indicator",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) tokens.selectionFill else Color.Transparent,
        animationSpec = tokens.motion.motionSpec(),
        label = "tab_indicator_color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.onSurfaceVariant,
        animationSpec = tokens.motion.motionSpec(),
        label = "tab_content",
    )

    val pillShape: Shape = tokens.shapes.pill

    Column(
        modifier = modifier
            .clip(tokens.shapes.pill)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .offset { IntOffset(stampOffset.roundToPx(), stampOffset.roundToPx()) }
            .scale(pressScale)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(width = 56.dp, height = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Индикатор: сосед иконки, не родитель — масштабируется только он.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(indicatorScale)
                    .clip(pillShape)
                    .background(indicatorColor, pillShape)
            )
            Icon(
                imageVector = if (selected) selectedIcon else icon,
                contentDescription = label,
                tint = contentColor,
                // Размер постоянный: скачок 24↔26dp при переключении читался
                // как дребезг.
                modifier = Modifier.size(23.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
