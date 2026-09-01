package by.iposdev.visorlink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlRaised
import by.iposdev.visorlink.ui.theme.vlSignalGlow
import by.iposdev.visorlink.utils.HapticType
import by.iposdev.visorlink.utils.rememberHaptic

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
    text: String? = null,
    size: Dp = 56.dp,
    hapticEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    VlFab(
        onClick = onClick,
        modifier = modifier,
        size = size,
        hapticEnabled = hapticEnabled,
        containerColor = containerColor,
    ) {
        if (text == null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Базовый компонент FAB с поддержкой произвольного контента.
 * Используется, когда FAB должен менять состояние (например, в ChatListFab).
 */
@Composable
fun VlFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    hapticEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    glowActive: Boolean = true,
    shape: Shape? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val haptic = rememberHaptic()
    val tokens = VlTheme.tokens
    val isPressed by interactionSource.collectIsPressedAsState()

    val fabShape: Shape = shape ?: if (tokens.structure.enabled) tokens.shapes.fab else RoundedCornerShape(16.dp)

    // Постоянное слабое свечение в покое, усиленное на время нажатия.
    val glowAlpha = if (isPressed) tokens.signal.glowAlpha else tokens.signal.fabRestAlpha

    Box(
        modifier = modifier
            .height(size)
            .widthIn(min = size)
            .vlSignalGlow(
                tokens = tokens.signal,
                color = containerColor,
                shape = fabShape,
                active = glowActive,
                alphaOverride = glowAlpha,
            )
            .then(if (tokens.structure.enabled) Modifier.vlRaised(tokens.structure, fabShape) else Modifier)
            .clip(fabShape)
            .background(containerColor, fabShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
