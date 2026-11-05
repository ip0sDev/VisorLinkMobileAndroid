package org.visorlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlDepth
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlHairline
import org.visorlink.app.ui.theme.vlKeycapBackground
import org.visorlink.app.ui.theme.vlRaised

/**
 * Карточка контента (порт Flutter `VlCard`).
 *
 * Гайдлайн §7 + §2: в Biolume и Forge — raised + нейтральная грань outlineVariant,
 * никакого свечения (§10: карточка ничего не сообщает).
 * В MATERIAL3 — плоская поверхность surfaceContainerHighest.
 */
@Composable
fun VlCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    containerColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
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
        val fill = containerColor ?: cs.surfaceContainer
        Box(
            modifier = modifier
                .vlRaised(tokens.structure, resolvedShape)
                .clip(resolvedShape)
                .vlKeycapBackground(tokens.structure, fill, resolvedShape, VlDepth.Raised)
                .vlHairline(cs.outlineVariant, resolvedShape)
                .then(clickModifier)
                .padding(contentPadding),
            content = content,
        )
        return
    }

    Card(
        modifier = modifier.then(clickModifier),
        shape = resolvedShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: cs.surfaceContainerHighest,
        ),
    ) {
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}
