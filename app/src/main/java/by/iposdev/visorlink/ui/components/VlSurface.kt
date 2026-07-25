package by.iposdev.visorlink.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.theme.accentGlowShadow
import by.iposdev.visorlink.ui.theme.forgeHardShadow
import by.iposdev.visorlink.ui.theme.nmInsetShadow
import by.iposdev.visorlink.ui.theme.nmRaisedShadow
import by.iposdev.visorlink.ui.theme.rememberExthruStyle

@Composable
fun VlSurface(
    appTheme: AppTheme,
    modifier: Modifier = Modifier,
    isButton: Boolean = false,
    isInput: Boolean = false,
    customRadius: Dp? = null,
    overrideColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    index: Int = 0,
    total: Int = 1,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.surface.luminance() < 0.5f

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val showInset = isInput || isPressed

    val baseRadius = customRadius ?: if (isButton) 20.dp else 24.dp

    val shape: Shape = when {
        appTheme == AppTheme.FORGE -> RectangleShape
        total <= 1 -> RoundedCornerShape(baseRadius)
        else -> {
            val smallR = 4.dp
            when {
                index == 0 -> RoundedCornerShape(topStart = baseRadius, topEnd = baseRadius, bottomStart = smallR, bottomEnd = smallR)
                index == total - 1 -> RoundedCornerShape(topStart = smallR, topEnd = smallR, bottomStart = baseRadius, bottomEnd = baseRadius)
                else -> RoundedCornerShape(smallR)
            }
        }
    }

    // 🔥 Плавная "прыгучая" анимация а-ля Flutter easeOutBack / Spring
    val pressedScale = if (appTheme == AppTheme.MATERIAL3_EXPRESSIVE || appTheme == AppTheme.ONE_UI) 0.96f else 0.93f
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isInput) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = if (isPressed) Spring.DampingRatioNoBouncy else Spring.DampingRatioMediumBouncy,
            stiffness = if (isPressed) Spring.StiffnessHigh else Spring.StiffnessMedium
        ),
        label = "vlsurface_scale",
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else Modifier

    when (appTheme) {
        AppTheme.MATERIAL3_EXPRESSIVE, AppTheme.ONE_UI -> {
            val bg = overrideColor ?: if (isInput) cs.surfaceContainerHighest else cs.surfaceContainerLow
            Box(
                modifier = modifier
                    .scale(scale)
                    .clip(shape)
                    .background(bg, shape)
                    .then(clickModifier)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { content() }
        }

        AppTheme.FORGE -> {
            val style = rememberExthruStyle(appTheme)
            val bg = overrideColor ?: if (isInput) style.inputBg else style.cardBg

            Box(
                modifier = modifier
                    .then(
                        if (!showInset) Modifier.forgeHardShadow(
                            color = style.darkShadow,
                            offset = if (isButton) 3.dp else 4.dp,
                        ) else Modifier
                    )
                    .scale(scale)
                    .background(bg, shape)
                    .forgeBevelBorder(isDark = isDark, inset = showInset)
                    .then(clickModifier)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { content() }
        }

        else -> {
            val style = rememberExthruStyle(appTheme)
            // Фон делаем немного прозрачным, чтобы Haze и Glow красиво просвечивали
            val bg = overrideColor ?: if (isInput) style.inputBg else style.cardBg.copy(alpha = if (isDark) 0.8f else 0.9f)

            val shadowModifier = if (!showInset) {
                Modifier.nmRaisedShadow(
                    isDark = isDark,
                    shadowRadius = if (isButton) 8.dp else 16.dp, // Увеличил размытие тени для глубины
                    offsetDp = if (isButton) 4.dp else 6.dp,
                    cornerRadius = baseRadius,
                )
            } else {
                Modifier.nmInsetShadow(
                    isDark = isDark,
                    cornerRadius = baseRadius,
                    lineWidthDp = if (isButton) 2.dp else 1.5.dp,
                )
            }

            val glowModifier = if (onClick != null && !isInput) {
                Modifier.accentGlowShadow(accent = style.accent, isPressed = isPressed, cornerRadius = baseRadius)
            } else Modifier

            Box(
                modifier = modifier
                    .scale(scale) // Масштаб применяется ко всему: и тени, и фону
                    .then(shadowModifier)
                    .then(glowModifier)
                    .background(bg, shape)
                    .clip(shape)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = if (isDark) 0.05f else 0.4f),
                        shape = shape
                    )
                    .then(clickModifier)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                // Внутренний блик
                if (!showInset) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    colors = if (isDark)
                                        listOf(Color.White.copy(0.04f), Color.Transparent)
                                    else
                                        listOf(Color.White.copy(0.2f), Color.Transparent)
                                )
                            )
                    )
                }
                content()
            }
        }
    }
}

private fun Modifier.forgeBevelBorder(
    isDark: Boolean,
    inset: Boolean,
    width: Dp = 2.dp,
): Modifier = this.drawBehind {
    val w = width.toPx()
    val light = if (isDark) Color.White.copy(alpha = 0.24f) else Color.White
    val dark = if (isDark) Color.Black.copy(alpha = 0.87f) else Color.Black.copy(alpha = 0.38f)

    val topLeft = if (inset) dark else light
    val bottomRight = if (inset) light else dark

    drawRect(color = topLeft, topLeft = Offset(0f, 0f), size = Size(size.width, w))
    drawRect(color = topLeft, topLeft = Offset(0f, 0f), size = Size(w, size.height))
    drawRect(color = bottomRight, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
    drawRect(color = bottomRight, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
}