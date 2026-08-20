package by.iposdev.visorlink.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Два независимых слоя глубины из гайдлайна §4.
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
 * Рисует ТОЛЬКО тень (заливка прозрачная) — стандартный приём через
 * `Paint.setShadowLayer`. Другого способа получить мягкую смещённую тень
 * произвольной формы в Compose нет: `Modifier.shadow` не даёт управлять смещением
 * и не умеет рисовать вторую тень от противоположного источника света.
 */
private fun DrawScope.drawSoftShadow(
    path: Path,
    color: Color,
    blurPx: Float,
    dx: Float,
    dy: Float,
    strokeWidthPx: Float? = null,
) {
    if (color.alpha <= 0.001f || blurPx <= 0f) return

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.TRANSPARENT
            if (strokeWidthPx != null) {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = strokeWidthPx
            } else {
                style = android.graphics.Paint.Style.FILL
            }
            setShadowLayer(blurPx, dx, dy, color.toArgb())
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

// ── §4.1 Слой структуры: Raised ──────────────────────────────────────────────

/**
 * Приподнятая поверхность: тень от невидимого источника сверху-слева плюс
 * контр-подсветка снизу-справа. Для элементов, которые «на что-то нажимают» —
 * карточки, tonal/outlined-кнопки, чипы в покое, thumb тумблера.
 */
fun Modifier.vlRaised(
    tokens: VlStructureTokens,
    shape: Shape,
): Modifier {
    if (!tokens.enabled) return this
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
 * Цветное свечение поверх структуры. Включается ТОЛЬКО когда элемент что-то
 * сообщает: focus, press, live, selected-CTA. Гайдлайн §10: в покое не светится
 * ничего, кроме FAB, и на экране одновременно активен максимум один glow.
 *
 * @param active выключает эффект целиком, чтобы вызывающему коду не приходилось
 *   плодить условные ветки в модификаторной цепочке.
 */
fun Modifier.vlSignalGlow(
    tokens: VlSignalTokens,
    color: Color,
    shape: Shape,
    active: Boolean = true,
    alphaOverride: Float? = null,
): Modifier {
    if (!tokens.enabled || !active) return this
    val alpha = alphaOverride ?: tokens.glowAlpha
    if (alpha <= 0.001f) return this
    return this.drawBehind {
        drawSoftShadow(
            path = shape.toPath(size, layoutDirection, this),
            color = color.copy(alpha = alpha),
            blurPx = tokens.glowBlur.toPx(),
            dx = 0f,
            dy = 0f,
        )
    }
}

/**
 * Сигнальный контур — 1px по границе формы. Используется вместе с [vlSignalGlow]
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
 * на цветной заливке (§3.1). Карточка в Biolume = raised + эта грань (§7).
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

// ── §6 Движение: «Биопульс» ──────────────────────────────────────────────────

/**
 * Единственный источник анимированного свечения в состоянии покоя интерфейса.
 * Допустим только там, где элемент действительно сообщает live-состояние:
 * статус-точка «онлайн», индикатор записи, загрузка. FAB, кнопки и карточки
 * НЕ пульсируют (§6).
 *
 * Расширяющееся кольцо + растущий glow, период 2.4s, ease-in-out. При
 * [VlTokens.reduceMotion] заменяется статичным glow той же интенсивности, что на
 * пике — ровно как требует §6/§8.
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

/**
 * Кольцо пульса должно расти НАРУЖУ, поэтому обрезать его формой нельзя —
 * просто рисуем без клипа. Обёртка существует, чтобы намерение читалось в коде.
 */
private inline fun DrawScope.clipPathInverseSafe(block: DrawScope.() -> Unit) = block()
