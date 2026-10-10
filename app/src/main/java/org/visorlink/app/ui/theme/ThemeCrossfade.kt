package org.visorlink.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/**
 * Плавная смена темы целиком (Biolume ↔ Forge v2, светлая ↔ тёмная). Палитру
 * [VisorLinkTheme] умеет анимировать сама, но формы, шрифты, рельеф и свечение
 * переключаются мгновенно — экран «дёргался». Здесь перед сменой снимается кадр в
 * старой теме, новая тема применяется сразу, а снимок поверх неё растворяется.
 *
 * Перерисовка не дублируется (в отличие от Crossfade): дерево одно, поэтому навигация,
 * поля ввода и прокрутка сохраняются. Снимок касания не перехватывает.
 *
 * @param key что считается «сменой темы»; остальные изменения [value] (акцент и т. п.)
 *   применяются сразу — их цвета и так анимирует палитра.
 */
@Composable
fun <T> ThemeCrossfade(
    value: T,
    key: (T) -> Any?,
    /** Как переход навигации (300 мс): смена темы при входе в чат — одно движение с ним. */
    durationMs: Int = 320,
    content: @Composable (T) -> Unit,
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var shownKey by remember { mutableStateOf(key(value)) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(0f) }
    val layer = rememberGraphicsLayer()
    val targetKey = key(value)

    LaunchedEffect(targetKey) {
        if (targetKey == shownKey) return@LaunchedEffect
        // Смена посреди растворения (вошли в чат и сразу вышли): прежний снимок не обрываем —
        // он дорастворяется над новой темой, без резкого скачка прозрачности
        if (snapshot != null) {
            shownKey = targetKey
            fade.animateTo(0f, tween((durationMs * fade.value).toInt().coerceAtLeast(120), easing = FastOutSlowInEasing))
            snapshot = null
            return@LaunchedEffect
        }
        // Кадр ещё в старой теме: тема меняется только после снимка
        val frame = if (reduceMotion) null else runCatching { layer.toImageBitmap() }.getOrNull()
        // Снимок полностью закрывает экран раньше, чем применится новая тема: ни одного кадра
        // «новая тема без покрытия» (раньше прозрачность выставлялась уже после смены)
        fade.snapTo(1f)
        snapshot = frame
        shownKey = targetKey
        if (frame != null) {
            fade.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing))
        }
        snapshot = null
    }

    // Пока снимок не сделан, держим прежнюю тему (один-два кадра)
    val last = remember { arrayOf<Any?>(value) }
    if (targetKey == shownKey) last[0] = value
    @Suppress("UNCHECKED_CAST")
    val shown = last[0] as T
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) { content(shown) }
        snapshot?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
            )
        }
    }
}
