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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.theme.accentGlowShadow
import by.iposdev.visorlink.ui.theme.forgeNeuBrutalism
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

            val shadowMod = Modifier.forgeNeuBrutalism(
                isPressed = isPressed,
                isDark = isDark,
                offsetDp = if (isButton) 3.dp else 4.dp
            )

            Box(
                modifier = modifier
                    // Не рисуем тень для полей ввода (чтобы они не выпирали, а были плоскими)
                    .then(if (!isInput) shadowMod else Modifier.border(2.dp, if(isDark) Color(0xFF333333) else Color.Black, RectangleShape))
                    .background(bg, shape)
                    .then(clickModifier)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { content() }
        }

        else -> {
            val style = rememberExthruStyle(appTheme)
            // Фон делаем немного прозрачным, чтобы Haze и Glow красиво просвечивали
            val bg = overrideColor ?: if (isInput) style.inputBg else style.cardBg.copy(alpha = if (isDark) 0.8f else 0.9f)

            val shadowModifier = if (overrideColor == Color.Transparent) Modifier else if (!showInset) {
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

            val glowModifier = if (overrideColor == Color.Transparent) Modifier else if (onClick != null && !isInput) {
                Modifier.accentGlowShadow(accent = style.accent, isPressed = isPressed, cornerRadius = baseRadius)
            } else Modifier

            Box(
                modifier = modifier
                    .scale(scale)
                    .then(shadowModifier)
                    .then(glowModifier)
                    .background(bg, shape)
                    .clip(shape)
                    .then(if (overrideColor == Color.Transparent) Modifier else Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = if (isDark) 0.05f else 0.4f),
                        shape = shape
                    ))
                    .then(clickModifier),
                contentAlignment = Alignment.Center,
            ) {
                // Внутренний блик
                if (!showInset && overrideColor != Color.Transparent) {
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
                
                Box(Modifier.padding(contentPadding), contentAlignment = Alignment.Center) {
                    content()
                }
            }
        }
    }
}