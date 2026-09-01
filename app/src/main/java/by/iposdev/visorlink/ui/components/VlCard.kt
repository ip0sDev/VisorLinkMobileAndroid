package by.iposdev.visorlink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlHairline
import by.iposdev.visorlink.ui.theme.vlRaised

/**
 * Карточка.
 *
 * Гайдлайн §7 + §2: в Biolume это гибрид M3 Elevated Card (глубина через тень) и
 * Outlined Card (геометрия) — neumorphic-raised плюс нейтральная грань
 * `outlineVariant`. Никакого свечения: карточка ничего не сообщает (§10).
 *
 * В MATERIAL3 — обычная M3 `Card`, чтобы «чистая» тема не поехала.
 *
 * Отличие от [VlSurface]: `VlSurface` — универсальный контейнер, который умеет
 * группироваться (`index`/`total`) и переключаться в inset. `VlCard` — именно
 * карточка контента, всегда raised, с гранью.
 */
@Composable
fun VlCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens
    val resolvedShape = shape ?: tokens.shapes.card

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    if (tokens.structure.enabled) {
        Box(
            modifier = modifier
                .vlRaised(tokens.structure, resolvedShape)
                .clip(resolvedShape)
                .background(containerColor ?: cs.surfaceContainer, resolvedShape)
                .vlHairline(cs.outlineVariant, resolvedShape)
                .then(clickModifier),
            content = content,
        )
        return
    }

    Card(
        modifier = modifier.then(clickModifier),
        shape = resolvedShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: cs.surfaceVariant,
        ),
    ) {
        Box(content = content)
    }
}
