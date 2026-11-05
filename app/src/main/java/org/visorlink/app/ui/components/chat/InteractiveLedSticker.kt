package org.visorlink.app.ui.components.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.visorlink.app.ui.components.led.*
import org.visorlink.app.ui.theme.VlTheme

private val Reactions = mapOf(
    VlLedExpression.HAPPY to (VlLedExpression.HEART to VlLedExpression.BOOP),
    VlLedExpression.BOOP to (VlLedExpression.WIDE to VlLedExpression.CAT),
    VlLedExpression.HEART to (VlLedExpression.BOOP to VlLedExpression.HAPPY),
    VlLedExpression.CAT to (VlLedExpression.HEART to VlLedExpression.HAPPY),
    VlLedExpression.COOL to (VlLedExpression.WIDE to VlLedExpression.HAPPY),
    VlLedExpression.WIDE to (VlLedExpression.HAPPY to VlLedExpression.BOOP),
    VlLedExpression.SLEEPY to (VlLedExpression.WIDE to VlLedExpression.ANGRY),
    VlLedExpression.GLITCH to (VlLedExpression.WIDE to VlLedExpression.SCAN),
    VlLedExpression.CRY to (VlLedExpression.HEART to VlLedExpression.HAPPY),
    VlLedExpression.ANGRY to (VlLedExpression.WIDE to VlLedExpression.GLITCH),
    VlLedExpression.SCAN to (VlLedExpression.WIDE to VlLedExpression.HAPPY),
)

/**
 * Интерактивный LED-стикер Forge:
 * В покое мордочка моргает (у плачущей капают слёзы, помеха рябит).
 * По тапу — «буп»: корпус пружинит со сжатием, мордочка отвечает эмоцией со своим
 * тактильным почерком, над визором всплывает счёт комбо серии `+N`.
 * Серия из восьми бупов перегревает матрицу (реакция GLITCH).
 */
@Composable
fun InteractiveLedSticker(
    expression: VlLedExpression,
    modifier: Modifier = Modifier,
    color: Color? = null,
    size: Dp = 140.dp,
    interactive: Boolean = true,
) {
    val tokens = VlTheme.tokens
    val still = tokens.reduceMotion
    val controller = rememberLedFaceController()
    val scope = rememberCoroutineScope()

    var combo by remember { mutableIntStateOf(0) }
    var shownCombo by remember { mutableIntStateOf(0) }

    LaunchedEffect(combo) {
        if (combo > 0) {
            delay(700)
            combo = 0
        }
    }

    val squashFactor = remember { Animatable(1f) }
    val popProgress = remember { Animatable(1f) }
    var squashJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var popJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val pad = size * 0.075f
    val pitch = (size - pad * 2) / 32f
    val dot = pitch * 0.74f
    val gap = pitch - dot
    val height = pitch * 12 - gap + pad * 2

    val onBoop: () -> Unit = {
        combo++
        shownCombo = combo
        val overload = combo % 8 == 0
        val pair = Reactions[expression] ?: (VlLedExpression.HEART to VlLedExpression.BOOP)
        val reaction = if (overload) VlLedExpression.GLITCH else (if (combo % 2 != 0) pair.first else pair.second)
        controller.flash(reaction, durationMs = if (overload) 1400L else 900L, haptic = true)

        if (!still) {
            squashJob?.cancel()
            squashJob = scope.launch {
                // Плавное сжатие без мгновенного сброса snapTo
                squashFactor.animateTo(0.88f, tween(90, easing = FastOutSlowInEasing))
                squashFactor.animateTo(1.04f, tween(140, easing = FastOutSlowInEasing))
                squashFactor.animateTo(1.0f, tween(160, easing = FastOutSlowInEasing))
            }
            popJob?.cancel()
            popJob = scope.launch {
                popProgress.snapTo(0f)
                popProgress.animateTo(1f, tween(900, easing = LinearEasing))
            }
        }
    }

    val clickModifier = if (interactive) {
        Modifier
            .semantics {
                this.contentDescription = "LED-стикер, буп"
                this.role = Role.Button
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBoop,
            )
    } else Modifier

    val activeColor = color ?: tokens.led?.color ?: MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(width = size, height = height)
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        // Визор мордочки
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val v = squashFactor.value
                    scaleX = 2f - v
                    scaleY = v
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
        ) {
            VlLedScreen(
                radius = size * 0.12f,
                padding = PaddingValues(pad),
                modifier = Modifier.fillMaxSize(),
            ) {
                VlLedFace(
                    expression = expression,
                    controller = controller,
                    dot = dot,
                    gap = gap,
                    color = color,
                    autoBlink = true,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // Всплывающее табло серии +N
        if (interactive && shownCombo > 0 && popProgress.value < 1f) {
            val t = popProgress.value
            val alpha = if (t < 0.66f) 1f else (1f - (t - 0.66f) / 0.34f)
            val offsetY = -pitch * 4 * (1f - (1f - t) * (1f - t)) // ease-out

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -pad, y = -pitch * 6 + offsetY)
                    .graphicsLayer { this.alpha = alpha }
            ) {
                VlLedDigits(
                    text = "+$shownCombo",
                    dot = dot,
                    gap = gap,
                    color = activeColor,
                )
            }
        }
    }
}
