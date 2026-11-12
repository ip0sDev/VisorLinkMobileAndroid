package org.visorlink.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlDepth
import org.visorlink.app.ui.theme.VlDurations
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlKeycapBackground
import org.visorlink.app.ui.theme.vlSignalBorder
import org.visorlink.app.ui.theme.vlSignalGlow
import org.visorlink.app.ui.theme.vlStructure
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic

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
    glowActive: Boolean = true,
    animateEntrance: Boolean = true,
) {
    VlFab(
        onClick = onClick,
        modifier = modifier,
        size = size,
        hapticEnabled = hapticEnabled,
        containerColor = containerColor,
        glowActive = glowActive,
        animateEntrance = animateEntrance,
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
    animateEntrance: Boolean = true,
    content: @Composable () -> Unit
) {
    val haptic = rememberHaptic()
    val tokens = VlTheme.tokens
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val fabShape: Shape = shape ?: if (tokens.structure.enabled) tokens.shapes.fab else VlTheme.tokens.shapes.adapt(RoundedCornerShape(16.dp))

    // Постоянное слабое свечение в покое, усиленное на время нажатия и плавно затухающее при отмене glowActive.
    val targetGlowAlpha = when {
        !glowActive -> 0f
        isPressed -> tokens.signal.glowAlpha
        else -> tokens.signal.fabRestAlpha
    }
    val glowAlpha by animateFloatAsState(
        targetValue = targetGlowAlpha,
        animationSpec = if (tokens.reduceMotion) snap() else tween(durationMillis = VlDurations.D1),
        label = "vlfab_glow_alpha",
    )
    val keycap = tokens.structure.keycap
    val depth = if (isPressed && keycap) VlDepth.Inset else if (tokens.structure.enabled) VlDepth.Raised else VlDepth.Flat

    // FAB пружинисто сжимается при нажатии (F vl_chrome.dart:263-266: 0.9, 110/520 мс)
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !tokens.reduceMotion) 0.91f else 1f,
        animationSpec = when {
            tokens.reduceMotion -> snap()
            isPressed -> tween(durationMillis = 110, easing = FastOutSlowInEasing)
            else -> spring(dampingRatio = 0.58f, stiffness = 260f)
        },
        label = "vlfab_scale",
    )

    val fabBody: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .height(size)
                .widthIn(min = size)
                // Масштабируем до свечения и тени, чтобы при нажатии рельеф двигался вместе с кнопкой
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .vlSignalGlow(
                    tokens = tokens.signal,
                    color = containerColor,
                    shape = fabShape,
                    active = glowAlpha > 0.005f,
                    alphaOverride = glowAlpha,
                )
                // Фокус-контур цвета onPrimary (F vl_chrome.dart:277)
                .vlSignalBorder(
                    tokens = tokens.signal,
                    color = MaterialTheme.colorScheme.onPrimary,
                    shape = fabShape,
                    active = isFocused,
                )
                .then(
                    if (tokens.structure.enabled) Modifier.vlStructure(tokens.structure, depth, fabShape)
                    else Modifier
                )
                .clip(fabShape)
                .vlKeycapBackground(tokens.structure, containerColor, fabShape, depth)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { haptic.perform(HapticType.CLICK, hapticEnabled); onClick() },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }

    if (animateEntrance) {
        // FAB въезжает снизу с задержкой 150 мс (F vl_chrome.dart:286)
        VlEntrance(modifier = modifier, offsetDp = 40.dp, delayMs = 150) {
            fabBody()
        }
    } else {
        Box(modifier = modifier) {
            fabBody()
        }
    }
}
