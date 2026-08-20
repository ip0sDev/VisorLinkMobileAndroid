package by.iposdev.visorlink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlRaised
import by.iposdev.visorlink.ui.theme.vlSignalGlow
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic
import androidx.compose.foundation.clickable

/**
 * Главный CTA экрана.
 *
 * Гайдлайн §7 + §10: FAB — **единственный** элемент с постоянным свечением, и оно
 * статичное. Пульса нет (это отличает FAB от live-индикаторов, см. [VlLiveDot]).
 * Форма — асимметричный скруглённый прямоугольник из shape-набора M3 Expressive,
 * а не идеальный круг.
 *
 * В MATERIAL3 — обычный круглый FAB без свечения.
 */
@Composable
fun VlFab(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 56.dp,
    hapticEnabled: Boolean = true,
) {
    val haptic = rememberHaptic()
    val cs = MaterialTheme.colorScheme
    val tokens = VlTheme.tokens

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val shape: Shape = if (tokens.isBiolume) tokens.shapes.fab else RoundedCornerShape(16.dp)

    // Постоянное слабое свечение в покое, усиленное на время нажатия.
    val glowAlpha = if (isPressed) tokens.signal.glowAlpha else tokens.signal.fabRestAlpha

    Box(
        modifier = modifier
            .size(size)
            .vlSignalGlow(
                tokens = tokens.signal,
                color = cs.primary,
                shape = shape,
                alphaOverride = glowAlpha,
            )
            .then(if (tokens.isBiolume) Modifier.vlRaised(tokens.structure, shape) else Modifier)
            .clip(shape)
            .background(cs.primary, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = cs.onPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}
