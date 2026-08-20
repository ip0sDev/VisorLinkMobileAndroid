package by.iposdev.visorlink.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.R
import by.iposdev.visorlink.ui.theme.VlTheme
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
    val barShape: Shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // Рельеф рисуется ЗА границами панели — без воздуха снизу тень
            // срезается краем экрана.
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (tokens.isBiolume) Modifier.vlRaised(tokens.structure, barShape)
                    else Modifier
                )
                .clip(barShape)
                .background(
                    if (tokens.isBiolume) cs.surfaceContainer else cs.surfaceContainerLow,
                    barShape,
                )
                .then(
                    if (tokens.isBiolume) Modifier.vlHairline(cs.outlineVariant, barShape)
                    else Modifier
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
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
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "tab_press",
    )
    val indicatorScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.7f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "tab_indicator",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) tokens.selectionFill else Color.Transparent,
        animationSpec = tween(200),
        label = "tab_indicator_color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.onSurfaceVariant,
        animationSpec = tween(200),
        label = "tab_content",
    )

    val pillShape: Shape = RoundedCornerShape(percent = 50)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
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
