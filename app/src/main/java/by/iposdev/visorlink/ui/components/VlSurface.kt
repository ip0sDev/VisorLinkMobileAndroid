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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.data.model.AppTheme
import by.iposdev.visorlink.ui.theme.*

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

        AppTheme.FORGE, AppTheme.FORGE_TERMINAL -> {
            val style = rememberExthruStyle(appTheme)
            val bg = overrideColor ?: if (isInput) style.inputBg else style.cardBg

            val shadowMod = Modifier.forgeNeuBrutalism(
                isPressed = isPressed,
                isDark = isDark,
                offsetDp = if (isButton) 3.dp else 8.dp // Increased volume for cards
            )
            
            val industrialMod = if (!isInput) {
                Modifier.industrialPanel(
                    cornerRadius = 2.dp,
                    isDark = isDark,
                    accentGlow = isPressed,
                    accentColor = style.accent,
                    thickness = if (isButton) 1.5.dp else 4.dp // Even more volume for cards
                )
            } else {
                Modifier
                    .background(bg, RectangleShape)
                    .border(1.dp, if (isDark) Color(0xFF333333) else Color.Black, RectangleShape)
            }

            Box(
                modifier = modifier
                    .then(if (!isInput) shadowMod else Modifier)
                    .then(industrialMod)
                    .then(if (appTheme == AppTheme.FORGE_TERMINAL) Modifier.terminalScanlines() else Modifier)
                    .then(clickModifier)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { content() }
        }

        else -> {
            val style = rememberExthruStyle(appTheme)
            val isBiolume = appTheme == AppTheme.BIOLUME || appTheme == AppTheme.EXTHRU
            
            // "Liquid Glass" background with vertical depth - SUBTLE REFINEMENT
            val biolumeBg = if (isBiolume && overrideColor == null && !isInput) {
                Brush.verticalGradient(
                    colors = if (isDark)
                        listOf(style.cardBg.copy(alpha = 0.45f), style.cardBg.copy(alpha = 0.65f))
                    else
                        listOf(Color.White.copy(alpha = 0.90f), Color.White.copy(alpha = 0.70f))
                )
            } else null

            val bgModifier = when {
                biolumeBg != null -> Modifier.background(biolumeBg, shape)
                else -> Modifier.background(overrideColor ?: if (isInput) style.inputBg else style.cardBg.copy(alpha = if (isDark) 0.8f else 0.9f), shape)
            }

            val shadowModifier = if (overrideColor == Color.Transparent && !isBiolume) Modifier else if (!showInset) {
                Modifier.nmRaisedShadow(
                    isDark = isDark,
                    shadowRadius = if (isBiolume) 20.dp else if (isButton) 8.dp else 16.dp, 
                    offsetDp = if (isBiolume) 8.dp else if (isButton) 4.dp else 6.dp,
                    cornerRadius = baseRadius,
                    darkAlpha = if (isDark) 0.6f else 0.25f,
                    lightAlpha = if (isDark) 0.05f else 0.75f
                )
            } else {
                Modifier.nmInsetShadow(
                    isDark = isDark,
                    cornerRadius = baseRadius,
                    lineWidthDp = if (isButton) 2.dp else 1.5.dp,
                )
            }

            val glowModifier = if (overrideColor == Color.Transparent && !isBiolume) Modifier else if (onClick != null && !isInput) {
                Modifier.accentGlowShadow(accent = style.accent, isPressed = isPressed, cornerRadius = baseRadius)
            } else Modifier

            Box(
                modifier = modifier
                    .scale(scale)
                    .then(shadowModifier)
                    .then(glowModifier)
                    .then(bgModifier)
                    .clip(shape)
                    .then(
                        if (isBiolume && !showInset) 
                            Modifier.biolumeGlassBorder(shape, isDark, isPressed)
                        else if (overrideColor != Color.Transparent)
                            Modifier.border(1.dp, if (isDark) Color.White.copy(0.05f) else style.accent.copy(0.12f), shape)
                        else Modifier
                    )
                    .then(clickModifier),
                contentAlignment = Alignment.Center,
            ) {
                // Internal Specular (Reflection inside the glass)
                if (isBiolume && !showInset && overrideColor == null) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = if (isDark) 0.04f else 0.15f), Color.Transparent),
                                    center = Offset(0f, 0f),
                                    radius = 120.dp.value // Subtle large glow from top-left
                                ),
                                shape = shape
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