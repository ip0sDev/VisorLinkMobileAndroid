package by.iposdev.visorlink.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.iposdev.visorlink.ui.theme.VlDepth
import by.iposdev.visorlink.ui.theme.VlTheme
import by.iposdev.visorlink.ui.theme.vlHairline
import by.iposdev.visorlink.ui.theme.vlStructure

/**
 * Базовый контейнер приложения.
 *
 * Компонент объявляет **роль** поверхности (кликабельная / принимающая ввод /
 * элемент группы), а как эту роль отрисовать — решает тема через
 * `VlTheme.tokens`:
 *  - MATERIAL3 — плоская заливка + лёгкий scale при нажатии (как было);
 *  - BIOLUME — неоморфный рельеф: raised в покое, inset при нажатии и у полей
 *    ввода (§4.1), плюс нейтральная грань `outlineVariant` у карточек (§7).
 *
 * Подпись сознательно не меняется: экраны вызывают `VlSurface` одинаково для
 * любой темы и ничего про тему не знают.
 */
@Composable
fun VlSurface(
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
    val tokens = VlTheme.tokens

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val baseRadius = customRadius ?: when {
        tokens.isBiolume && isButton -> tokens.shapes.buttonRadius
        tokens.isBiolume -> tokens.shapes.cardRadius
        isButton -> 20.dp
        else -> 24.dp
    }

    // Скругления элемента группы: у крайних — большой радиус снаружи, внутренние
    // углы схлопываются до 4dp. Работает одинаково в обеих темах.
    val shape: Shape = if (total <= 1) {
        RoundedCornerShape(baseRadius)
    } else {
        val smallR = 4.dp
        when (index) {
            0 -> RoundedCornerShape(topStart = baseRadius, topEnd = baseRadius, bottomStart = smallR, bottomEnd = smallR)
            total - 1 -> RoundedCornerShape(topStart = smallR, topEnd = smallR, bottomStart = baseRadius, bottomEnd = baseRadius)
            else -> RoundedCornerShape(smallR)
        }
    }

    // §4.1: то, что «принимает» (поле ввода) или уже нажато — врезано;
    // остальное — приподнято. В M3 оба варианта дают no-op.
    val depth = when {
        !tokens.isBiolume -> VlDepth.Flat
        isInput || isPressed -> VlDepth.Inset
        else -> VlDepth.Raised
    }

    // Scale оставляем только M3E: в Biolume смену рельефа raised → inset уже
    // достаточно читаемо, а масштабирование поверх неё выглядит «желейно».
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isInput && !tokens.isBiolume) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "vlsurface_scale",
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else {
        Modifier
    }

    val bg = overrideColor ?: when {
        // §3.1: и карточки, и поля в покое сидят на surfaceContainer — рельеф
        // различает их роли, а не заливка.
        tokens.isBiolume -> cs.surfaceContainer
        isInput -> cs.surfaceContainerHighest
        else -> cs.surfaceContainerLow
    }

    // Грань нужна только карточкам и только там, где рельеф может не прочитаться
    // (§3.1). На цветной заливке (overrideColor) её не рисуем — там уже есть свой контур.
    val hairlineModifier = if (tokens.isBiolume && !isInput && overrideColor == null) {
        Modifier.vlHairline(cs.outlineVariant, shape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .scale(scale)
            .vlStructure(tokens.structure, depth, shape)
            .clip(shape)
            .background(bg, shape)
            .then(hairlineModifier)
            .then(clickModifier)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) { content() }
}
