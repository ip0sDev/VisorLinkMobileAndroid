package org.visorlink.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Прогрессивный блюр на краях контента.
 * Отключён для паритета с Flutterlink: верхняя и нижняя панели имеют собственные чёткие границы,
 * а размытие краёв контейнера с `contentPadding` замыливало верхнюю карточку / LED-табло под шапкой.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.progressiveEdgeBlur(
    topBlur: Dp = 10.dp,
    bottomBlur: Dp = 10.dp,
    topThreshold: Float = 0.05f,
    bottomThreshold: Float = 0.05f,
    enabled: Boolean = true
): Modifier = this
