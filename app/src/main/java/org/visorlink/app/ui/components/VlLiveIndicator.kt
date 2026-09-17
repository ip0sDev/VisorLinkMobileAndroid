package org.visorlink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.vlBiopulse

/**
 * Live-индикатор: «онлайн», запись голосового, идущая загрузка.
 *
 * Единственный элемент в приложении, которому разрешён **анимированный** glow
 * (гайдлайн §6). FAB, кнопки и карточки в состоянии покоя не пульсируют — если
 * захочется «оживить» что-то ещё, это нарушение §1.2 и §10.
 *
 * При системно отключённых анимациях пульс автоматически заменяется статичным
 * свечением той же интенсивности, что на пике — логика внутри [vlBiopulse].
 */
@Composable
fun VlLiveDot(
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    color: Color? = null,
    active: Boolean = true,
) {
    val tokens = VlTheme.tokens
    val dotColor = color ?: tokens.status.success

    Box(
        modifier = modifier
            .size(size)
            .vlBiopulse(tokens = tokens, color = dotColor, active = active, shape = tokens.shapes.indicator)
            .clip(tokens.shapes.indicator)
            .background(dotColor, tokens.shapes.indicator)
    )
}

/**
 * Статус-точка присутствия: зелёная «онлайн» с биопульсом, серая «офлайн» без него.
 * В MATERIAL3 пульса нет вообще — сигнальный слой там отключён.
 */
@Composable
fun VlPresenceDot(
    online: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val tokens = VlTheme.tokens
    VlLiveDot(
        modifier = modifier,
        size = size,
        color = if (online) tokens.status.success else MaterialTheme.colorScheme.outline,
        active = online,
    )
}
