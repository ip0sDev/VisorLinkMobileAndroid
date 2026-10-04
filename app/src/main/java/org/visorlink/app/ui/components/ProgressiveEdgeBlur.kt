package org.visorlink.app.ui.components

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Прогрессивный блюр на краях контента (Android 13+).
 * Создает плавное градиентное размытие элементов у краев экрана при скролле (iOS-like fading edge).
 * На Android < 13 (API < 33) эффект безопасно игнорируется платформой.
 */
fun Modifier.progressiveEdgeBlur(
    topBlur: Dp = 10.dp,
    bottomBlur: Dp = 10.dp,
    topThreshold: Float = 0.05f,
    bottomThreshold: Float = 0.05f,
    enabled: Boolean = true
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this
    }
    return this.blur(
        BlurRadiusSpec.verticalGradient(
            listOf(
                BlurStop(fraction = 0f, radius = topBlur),
                BlurStop(fraction = topThreshold, radius = 0.dp),
                BlurStop(fraction = (1f - bottomThreshold).coerceAtLeast(topThreshold), radius = 0.dp),
                BlurStop(fraction = 1f, radius = bottomBlur)
            )
        )
    )
}
