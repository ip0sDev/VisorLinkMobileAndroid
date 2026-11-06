package org.visorlink.app.ui.theme

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.visorlink.app.BuildConfig
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Два независимых слоя глубины из гайдлайна §4 (Biolume и Forge).
 *
 * Все модификаторы здесь — **не-composable** и принимают токены явным параметром.
 * Это осознанно: так их можно ставить в условные ветки модификаторной цепочки
 * (`.then(if (x) Modifier.vlRaised(t, s) else Modifier)`) без риска сломать
 * композицию условным вызовом @Composable.
 *
 * Если [VlStructureTokens.enabled] == false (тема MATERIAL3), структурные
 * модификаторы возвращают `this` без изменений — компонент писать дважды не нужно.
 */

// ── Внутреннее: мягкая тень произвольной формы ───────────────────────────────

private fun Shape.toPath(size: Size, layoutDirection: LayoutDirection, density: DrawScope): Path {
    val outline: Outline = createOutline(size, layoutDirection, density)
    return Path().apply { addOutline(outline) }
}

/**
 * Перевод радиуса размытия дизайна в сигму Гаусса (паритет с Flutter `shadowSigma` в `vl_depth.dart`).
 */
private fun shadowSigma(blurPx: Float): Float = blurPx * 0.57735f + 0.5f

/**
 * Рисует мягкую смещённую тень произвольной формы через `BlurMaskFilter`
 * (полный паритет с Flutter `drawSoftShadow` и `_drawInsetShadow` в `vl_depth.dart`).
 * Использование `Paint.setShadowLayer` с `Color.TRANSPARENT` на Android Skia обнуляет альфу тени.
 */
internal fun DrawScope.drawSoftShadow(
    path: Path,
    color: Color,
    blurPx: Float,
    dx: Float,
    dy: Float,
    strokeWidthPx: Float? = null,
) {
    if (color.alpha <= 0.001f || blurPx <= 0f) return
    val sigma = shadowSigma(blurPx)

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.save()
        native.translate(dx, dy)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            if (strokeWidthPx != null) {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = strokeWidthPx
            } else {
                style = android.graphics.Paint.Style.FILL
            }
            maskFilter = android.graphics.BlurMaskFilter(
                sigma,
                android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
        }
        native.drawPath(path.asAndroidPath(), paint)
        native.restore()
    }
}

// ── Заливка клавиши (Forge keycap) ───────────────────────────────────────────

/**
 * Заливка поверхности с учётом светового градиента приборных клавиш ([VlStructureTokens.keycap]):
 * в состоянии [VlDepth.Raised] верхняя кромка клавиши светлее к белому (на фарфоре — в полную силу
 * `keycapSheen`, на цветных и графитовых клавишах — до 16 %, чтобы не выбеливать тон).
 * В Biolume и M3E (или в [VlDepth.Inset] / [VlDepth.Flat]) — обычная сплошная заливка.
 */
fun Modifier.vlKeycapBackground(
    tokens: VlStructureTokens,
    color: Color,
    shape: Shape,
    depth: VlDepth = VlDepth.Raised,
): Modifier {
    if (color.alpha <= 0.001f) return this
    if (tokens.keycap && depth == VlDepth.Raised) {
        val sheen = if (color.luminance() > 0.6f) tokens.keycapSheen else minOf(tokens.keycapSheen, 0.16f)
        val topColor = lerp(color, Color.White, sheen)
        return this.background(Brush.verticalGradient(listOf(topColor, color)), shape)
    }
    return this.background(color, shape)
}

// ── §4.1 Слой структуры: Raised ──────────────────────────────────────────────

/**
 * Приподнятая поверхность:
 * - В Biolume: двойной свет/тень от источника сверху-слева.
 * - В Forge (`tokens.keycap == true`): направленный свет сверху — тень строго вниз + плотная
 *   контактная тень под нижним ребром и внутренний блик верхней кромки.
 */
fun Modifier.vlRaised(
    tokens: VlStructureTokens,
    shape: Shape,
): Modifier {
    if (!tokens.enabled) return this

    if (tokens.keycap) {
        return this
            .drawBehind {
                val path = shape.toPath(size, layoutDirection, this)
                drawSoftShadow(
                    path = path,
                    color = tokens.shadowDark,
                    blurPx = tokens.raisedBlur.toPx(),
                    dx = 0f,
                    dy = tokens.raisedOffset.toPx(),
                )
                // Плотная контактная тень прямо под нижним ребром: клавиша «сидит» в панели
                drawSoftShadow(
                    path = path,
                    color = tokens.shadowDark,
                    blurPx = 2.dp.toPx(),
                    dx = 0f,
                    dy = 1.dp.toPx(),
                )
            }
            .drawWithContent {
                drawContent()
                val path = shape.toPath(size, layoutDirection, this)
                val sheenBrush = Brush.verticalGradient(
                    0f to tokens.shadowLight,
                    0.35f to Color.Transparent,
                )
                clipPath(path) {
                    drawPath(
                        path = path,
                        brush = sheenBrush,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
    }

    if (tokens.outline != null && tokens.hardShadow != null) {
        return this.vlHairline(tokens.outline, shape, width = 1.5.dp)
    }

    tokens.outline?.let { outline ->
        return this
            .drawBehind {
                drawSoftShadow(
                    path = shape.toPath(size, layoutDirection, this),
                    color = tokens.shadowDark,
                    blurPx = tokens.raisedBlur.toPx(),
                    dx = 0f,
                    dy = tokens.raisedOffset.toPx(),
                )
            }
            .vlHairline(outline, shape)
    }

    return this.drawBehind {
        val path = shape.toPath(size, layoutDirection, this)
        // Светлая контр-подсветка идёт первой, чтобы тёмная тень легла поверх неё.
        drawSoftShadow(
            path = path,
            color = tokens.shadowLight,
            blurPx = tokens.raisedLightBlur.toPx(),
            dx = -tokens.raisedLightOffset.toPx(),
            dy = -tokens.raisedLightOffset.toPx(),
        )
        drawSoftShadow(
            path = path,
            color = tokens.shadowDark,
            blurPx = tokens.raisedBlur.toPx(),
            dx = tokens.raisedOffset.toPx(),
            dy = tokens.raisedOffset.toPx(),
        )
    }
}

// ── §4.1 Слой структуры: Inset ───────────────────────────────────────────────

/**
 * Врезанная поверхность: тени рисуются ПОВЕРХ контента и обрезаются формой, из-за
 * чего видна только их внутренняя часть. Для элементов, которые «во что-то
 * принимают» — поля ввода, трек тумблера, нажатая кнопка, выбранный чип.
 */
fun Modifier.vlInset(
    tokens: VlStructureTokens,
    shape: Shape,
): Modifier {
    if (!tokens.enabled) return this

    if (tokens.keycap) {
        return this.drawWithContent {
            drawContent()
            val path = shape.toPath(size, layoutDirection, this)
            // Свет сверху: тень падает от верхней кромки вниз
            clipPath(path) {
                drawSoftShadow(
                    path = path,
                    color = tokens.shadowDark,
                    blurPx = tokens.insetBlur.toPx(),
                    dx = 0f,
                    dy = tokens.insetOffset.toPx(),
                    strokeWidthPx = tokens.insetOffset.toPx() * 2f,
                )
            }
        }
    }

    if (tokens.outline != null && tokens.hardShadow != null) {
        val outline = tokens.outline
        return this.drawWithContent {
            drawContent()
            val path = shape.toPath(size, layoutDirection, this)
            clipPath(path) { drawPath(path = path, color = outline, style = Stroke(width = 1.5.dp.toPx() * 2f)) }
        }
    }

    tokens.outline?.let { outline ->
        return this.drawWithContent {
            drawContent()
            val path = shape.toPath(size, layoutDirection, this)
            clipPath(path) {
                drawSoftShadow(
                    path = path,
                    color = tokens.shadowDark,
                    blurPx = tokens.insetBlur.toPx(),
                    dx = 0f,
                    dy = tokens.insetOffset.toPx(),
                    strokeWidthPx = tokens.insetOffset.toPx() * 2f,
                )
                drawPath(path = path, color = outline, style = Stroke(width = 1.dp.toPx() * 2f))
            }
        }
    }

    return this.drawWithContent {
        drawContent()
        val path = shape.toPath(size, layoutDirection, this)
        // Обводка вдвое толще смещения: clipPath срезает наружную половину.
        val darkStroke = tokens.insetOffset.toPx() * 2f
        val lightStroke = tokens.insetLightOffset.toPx() * 2f

        clipPath(path) {
            drawSoftShadow(
                path = path,
                color = tokens.shadowDark,
                blurPx = tokens.insetBlur.toPx(),
                dx = tokens.insetOffset.toPx(),
                dy = tokens.insetOffset.toPx(),
                strokeWidthPx = darkStroke,
            )
            drawSoftShadow(
                path = path,
                color = tokens.shadowLight,
                blurPx = tokens.insetLightBlur.toPx(),
                dx = -tokens.insetLightOffset.toPx(),
                dy = -tokens.insetLightOffset.toPx(),
                strokeWidthPx = lightStroke,
            )
        }
    }
}

/** Диспетчер: компонент объявляет роль, тема решает, как её отрисовать. */
fun Modifier.vlStructure(
    tokens: VlStructureTokens,
    depth: VlDepth,
    shape: Shape,
): Modifier = when (depth) {
    VlDepth.Raised -> vlRaised(tokens, shape)
    VlDepth.Inset -> vlInset(tokens, shape)
    VlDepth.Flat -> this
}

// ── §4.2 Слой сигнала ────────────────────────────────────────────────────────

/**
 * Слой сигнала (§4.2)
 *
 * Цветное свечение поверх структуры. Включается ТОЛЬКО когда элемент что-то
 * сообщает: focus, press, live, selected-CTA. Гайдлайн §10: в покое не светится
 * ничего, кроме FAB (и клавиши в Forge), и на экране одновременно активен максимум один glow.
 */
fun Modifier.vlSignalGlow(
    tokens: VlSignalTokens,
    color: Color,
    shape: Shape,
    active: Boolean = true,
    alphaOverride: Float? = null,
    /** false — постоянное свечение темы (кнопка Forge), а не сигнал: в счёт §10 не идёт. */
    counted: Boolean = true,
): Modifier {
    if (!tokens.enabled || !active) return this
    val alpha = alphaOverride ?: tokens.glowAlpha
    if (alpha <= 0.001f) return this

    return this.composed {
        if (BuildConfig.DEBUG && counted) {
            val counter = LocalSignalCounter.current
            DisposableEffect(Unit) {
                val current = counter?.incrementAndGet() ?: 0
                if (current > 1) {
                    Log.w("VlTheme", "Violation of §10: Multiple signal glows detected on screen ($current active)")
                }
                onDispose { counter?.decrementAndGet() }
            }
        }

        drawBehind {
            drawSoftShadow(
                path = shape.toPath(size, layoutDirection, this),
                color = color.copy(alpha = alpha),
                blurPx = tokens.glowBlur.toPx(),
                dx = 0f,
                dy = 0f,
            )
        }
    }
}

/**
 * Отладочный счетчик для правила «Один glow на экран» (§10).
 * Используется только в [BuildConfig.DEBUG].
 */
val LocalSignalCounter = compositionLocalOf<AtomicInteger?> { null }

/**
 * Сигнальный контур — по границе формы. Используется вместе с [vlSignalGlow]
 * у сфокусированного поля (§4.2). НЕ путать с нейтральным `outlineVariant`: это
 * разные по смыслу токены (§10).
 */
fun Modifier.vlSignalBorder(
    tokens: VlSignalTokens,
    color: Color,
    shape: Shape,
    active: Boolean = true,
): Modifier {
    if (!tokens.enabled || !active) return this
    return this.drawWithContent {
        drawContent()
        val path = shape.toPath(size, layoutDirection, this)
        clipPath(path) {
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = tokens.focusBorder.toPx() * 2f),
            )
        }
    }
}

/**
 * Нейтральная грань для случаев, где неоморфная тень плохо читается — например
 * на цветной заливке (§3.1). Карточка в Biolume/Forge = raised + эта грань (§7).
 */
fun Modifier.vlHairline(
    color: Color,
    shape: Shape,
    width: Dp = 1.dp,
): Modifier = this.drawWithContent {
    drawContent()
    val path = shape.toPath(size, layoutDirection, this)
    clipPath(path) {
        drawPath(path = path, color = color, style = Stroke(width = width.toPx() * 2f))
    }
}

/**
 * Строка таблицы (оставлено для совместимости).
 */
fun Modifier.vlTableRow(
    divider: Color,
    accent: Color? = null,
    pressed: Boolean = false,
    pressFill: Color = Color.Transparent,
    dividerStart: Dp = 0.dp,
): Modifier = this.drawWithContent {
    if (pressed) drawRect(pressFill)
    drawContent()
    val h = 1.dp.toPx()
    drawRect(divider, Offset(dividerStart.toPx(), size.height - h), Size(size.width - dividerStart.toPx(), h))
    if (accent != null) {
        val inset = 10.dp.toPx()
        drawRect(accent, Offset(0f, inset), Size(3.dp.toPx(), size.height - inset * 2))
    }
}

/**
 * Линейка вдоль верхнего края (оставлено для совместимости).
 */
fun Modifier.vlTopTicks(color: Color, active: Boolean = true, step: Dp = 8.dp): Modifier {
    if (!active) return this
    return this.drawWithContent {
        drawContent()
        val s = step.toPx()
        var i = 0
        while (i * s < size.width) {
            val x = i * s
            val tick = if (i % 5 == 0) 6.dp.toPx() else 3.dp.toPx()
            drawRect(color.copy(alpha = 0.34f * (1f - x / size.width * 0.8f)), Offset(x, 0f), Size(1.dp.toPx(), tick))
            i++
        }
    }
}

/**
 * Угловые скобки (оставлено для совместимости).
 */
fun Modifier.vlCornerBrackets(
    color: Color,
    active: Boolean = true,
    length: Dp = 16.dp,
    thickness: Dp = 4.dp,
    progress: () -> Float = { 1f },
): Modifier {
    if (!active) return this
    return this.drawWithContent {
        drawContent()
        val p = progress().coerceIn(0f, 1f)
        if (p <= 0f) return@drawWithContent
        val len = length.toPx() * p
        val t = thickness.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w - len, t / 2), Offset(w, t / 2), strokeWidth = t)
        drawLine(color, Offset(w - t / 2, 0f), Offset(w - t / 2, len), strokeWidth = t)
        drawLine(color, Offset(0f, h - t / 2), Offset(len, h - t / 2), strokeWidth = t)
        drawLine(color, Offset(t / 2, h - len), Offset(t / 2, h), strokeWidth = t)
    }
}

/**
 * «Трассировка» контура (оставлено для совместимости).
 */
fun Modifier.vlTraceBorder(
    color: Color,
    shape: Shape,
    progress: () -> Float,
    width: Dp = 2.dp,
    tail: Float = 0.3f,
): Modifier = this.drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
    val total = measure.length
    val segment = Path()
    onDrawWithContent {
        drawContent()
        val p = progress()
        if (p > 0.001f && p < 0.999f && total > 0f) {
            segment.reset()
            val end = total * p
            val start = (end - total * tail).coerceAtLeast(0f)
            measure.getSegment(start, end, segment, true)
            drawPath(segment, color.copy(alpha = color.alpha * (1f - p * 0.7f)), style = Stroke(width = width.toPx()))
        }
    }
}

/**
 * Жёсткая тень (оставлено для совместимости).
 */
fun Modifier.vlHardShadow(
    color: Color,
    shape: Shape,
    offset: Dp = 4.dp,
    active: Boolean = true,
): Modifier {
    if (!active) return this
    return this.drawBehind {
        val d = offset.toPx()
        translate(d, d) {
            drawPath(shape.toPath(size, layoutDirection, this), color)
        }
    }
}

/**
 * Косая «предупреждающая» штриховка (оставлено для совместимости).
 */
fun Modifier.vlHazardStripes(
    color: Color,
    shape: Shape,
    active: Boolean = true,
    stripe: Dp = 5.dp,
    period: Dp = 14.dp,
): Modifier {
    if (!active) return this
    return this.drawWithContent {
        drawContent()
        clipPath(shape.toPath(size, layoutDirection, this)) {
            val step = period.toPx()
            val w = stripe.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = w)
                x += step
            }
        }
    }
}

/**
 * Пунктирный контур по форме (оставлено для совместимости).
 */
fun Modifier.vlDashedBorder(
    color: Color,
    shape: Shape,
    active: Boolean = true,
    width: Dp = 1.dp,
    dash: Dp = 4.dp,
    gap: Dp = 3.dp,
): Modifier {
    if (!active) return this
    return this.drawWithContent {
        drawContent()
        drawPath(
            path = shape.toPath(size, layoutDirection, this),
            color = color,
            style = Stroke(
                width = width.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
            ),
        )
    }
}

// ── §6 Движение: «Биопульс» ──────────────────────────────────────────────────

/**
 * Единственный источник анимированного свечения в состоянии покоя интерфейса.
 * Допустим только там, где элемент действительно сообщает live-состояние:
 * статус-точка «онлайн», индикатор записи, загрузка. FAB, кнопки и карточки
 * НЕ пульсируют (§6).
 */
fun Modifier.vlBiopulse(
    tokens: VlTokens,
    color: Color,
    active: Boolean = true,
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
): Modifier {
    if (!tokens.signal.enabled || !active) return this

    // Пик анимации — им же подменяем пульс при отключённых анимациях.
    if (tokens.reduceMotion) {
        return this.drawBehind {
            drawSoftShadow(
                path = shape.toPath(size, layoutDirection, this),
                color = color.copy(alpha = 0.45f),
                blurPx = 20.dp.toPx(),
                dx = 0f,
                dy = 0f,
            )
        }
    }

    return this.composed {
        val transition = rememberInfiniteTransition(label = "biopulse")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(tokens.signal.pulsePeriodMs / 2, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "biopulse_progress",
        )

        drawBehind {
            val path = shape.toPath(size, layoutDirection, this)
            // Кольцо: 0dp/.45 → 5dp/0
            val ringWidth = 5.dp.toPx() * progress
            if (ringWidth > 0.5f) {
                clipPathInverseSafe {
                    drawPath(
                        path = path,
                        color = color.copy(alpha = 0.45f * (1f - progress)),
                        style = Stroke(width = ringWidth * 2f),
                    )
                }
            }
            // Glow: 10dp/.22 → 20dp/.45
            drawSoftShadow(
                path = path,
                color = color.copy(alpha = 0.22f + (0.45f - 0.22f) * progress),
                blurPx = (10.dp.toPx() + 10.dp.toPx() * progress),
                dx = 0f,
                dy = 0f,
            )
        }
    }
}

private inline fun DrawScope.clipPathInverseSafe(block: DrawScope.() -> Unit) = block()

// ── Фоновые и линейные модификаторы ──────────────────────────────────────────

/**
 * Фон поверх интерфейса ([VlTokens.terminal]): если не заданы дымка или сканлайны — no-op.
 */
fun Modifier.vlTerminalBackdrop(tokens: VlTokens): Modifier {
    val t = tokens.terminal ?: return this
    if (t.haze == null && !t.scanlines) return this
    return this.drawWithCache {
        val w = size.width
        val h = size.height
        val big = maxOf(w, h)
        val haze = t.haze?.let {
            Brush.radialGradient(listOf(it, Color.Transparent), center = Offset(w * 0.5f, -h * 0.06f), radius = big * 0.7f)
        }
        val scan = if (t.scanlines) {
            val line = 1.dp.toPx().coerceAtLeast(1f)
            val gap = 3.dp.toPx().coerceAtLeast(line + 1f)
            val tile = ImageBitmap(1, gap.roundToInt().coerceAtLeast(2))
            Canvas(tile).drawRect(
                Rect(0f, 0f, 1f, line.toInt().coerceAtLeast(1).toFloat()),
                Paint().apply { color = Color.White.copy(alpha = t.scanlineAlpha) },
            )
            ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
        } else null

        onDrawWithContent {
            drawContent()
            haze?.let { drawRect(it) }
            scan?.let { drawRect(it) }
        }
    }
}

/**
 * Тонкая LED-линия под шапкой экрана (Forge): мягкий градиент с центральным LED-отсветом.
 * В остальных темах — no-op.
 */
fun Modifier.vlNeonRule(tokens: VlTokens, width: Dp = 1.5.dp): Modifier {
    val ledColor = tokens.led?.color ?: tokens.terminal?.neon ?: return this
    return this.drawWithContent {
        drawContent()
        val h = width.toPx()
        val brush = Brush.horizontalGradient(
            listOf(
                ledColor.copy(alpha = 0.08f),
                ledColor.copy(alpha = 0.75f),
                ledColor.copy(alpha = 0.08f),
            )
        )
        drawRect(brush, Offset(0f, size.height - h), Size(size.width, h))
    }
}
