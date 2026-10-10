package org.visorlink.app.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Состояние упругой желейной деформации (Squash & Stretch).
 * Поддерживает мягкие сжатия при нажатии, упругие баунсы при отпускании и импульсы событий.
 */
@Stable
class LiquidJellyState(
    private val scope: CoroutineScope,
    val softness: Float = 0.10f,
    val damping: Float = 0.65f,
    val stiffness: Float = 320f,
    /** Forge v2: терминал не «желирует» — импульсы не деформируют и не сдвигают элемент. */
    val glitch: Boolean = false,
) {
    val animatable = Animatable(0f)

    val scaleX: Float
        get() = if (glitch) 1f else calculateJellyScale(animatable.value).first

    val scaleY: Float
        get() = if (glitch) 1f else calculateJellyScale(animatable.value).second

    fun press(customSoftness: Float = softness) {
        scope.launch {
            if (glitch) return@launch
            animatable.animateTo(-customSoftness, tween(80))
        }
    }

    fun release(bounceStretch: Float = softness * 0.7f) {
        scope.launch {
            if (glitch) return@launch
            animatable.animateTo(bounceStretch, tween(70))
            animatable.animateTo(0f, spring(dampingRatio = damping, stiffness = stiffness))
        }
    }

    fun pulse(intensity: Float = softness) {
        scope.launch {
            if (glitch) return@launch
            animatable.animateTo(intensity, tween(80))
            animatable.animateTo(0f, spring(dampingRatio = damping, stiffness = stiffness))
        }
    }
}

/**
 * [LaunchedEffect], пропускающий первый запуск при входе в композицию.
 *
 * Жидкие импульсы реагируют на *изменение* значения. Обычный LaunchedEffect срабатывает
 * ещё и на старте, из-за чего при открытии экрана «вздрагивали» все переключатели, FAB,
 * шапка и панель ввода одновременно.
 */
@Composable
fun LaunchedEffectAfterFirst(key1: Any?, block: suspend CoroutineScope.() -> Unit) {
    val isFirst = remember { booleanArrayOf(true) }
    LaunchedEffect(key1) {
        if (isFirst[0]) {
            isFirst[0] = false
            return@LaunchedEffect
        }
        block()
    }
}

/**
 * Создание и запоминание состояния желейной деформации.
 */
@Composable
fun rememberLiquidJellyState(
    softness: Float = 0.10f,
    damping: Float = 0.65f,
    stiffness: Float = 320f
): LiquidJellyState {
    val scope = rememberCoroutineScope()
    val glitch = org.visorlink.app.ui.theme.VlTheme.tokens.glitchMotion
    return remember(softness, damping, stiffness, glitch) {
        LiquidJellyState(scope, softness, damping, stiffness, glitch)
    }
}

/**
 * Модификатор применения желейной упругой деформации (Squash & Stretch).
 */
fun Modifier.liquidJelly(
    state: LiquidJellyState,
    /** Для панелей, прижатых к краю экрана: центр масштаба на краю, иначе между панелью и краем открывается щель. */
    transformOrigin: TransformOrigin = TransformOrigin.Center
): Modifier = this.graphicsLayer {
    this.transformOrigin = transformOrigin
    this.scaleX = state.scaleX
    this.scaleY = state.scaleY
}

/**
 * Состояние непрерывного жидкостного бегунка для таббаров и навбаров.
 * Мгновенный отклик на смену индекса (0ms latency), упругое вытягивание вдоль вектора перемещения.
 */
@Stable
class LiquidRunnerState(
    private val scope: CoroutineScope,
    initialIndex: Int = 0
) {
    val animatedIndex = Animatable(initialIndex.toFloat())
    val stretchAnim = Animatable(0f)

    val currentIndex: Float
        get() = animatedIndex.value

    val stretch: Float
        get() = stretchAnim.value

    val scaleX: Float
        get() = 1f + abs(stretch) * 0.28f

    val scaleY: Float
        get() = 1f - abs(stretch) * 0.18f

    fun moveTo(targetIndex: Int) {
        val prevIndex = animatedIndex.value
        val diff = targetIndex - prevIndex
        if (abs(diff) < 0.001f) return
        val stretchDir = if (diff > 0) 1f else -1f

        scope.launch {
            stretchAnim.animateTo(stretchDir * 0.20f, tween(90, easing = FastOutSlowInEasing))
            stretchAnim.animateTo(0f, spring(dampingRatio = 0.60f, stiffness = 340f))
        }
        scope.launch {
            animatedIndex.animateTo(
                targetValue = targetIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.70f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
}

/**
 * Создание и запоминание состояния жидкостного бегунка.
 */
@Composable
fun rememberLiquidRunnerState(
    initialIndex: Int = 0
): LiquidRunnerState {
    val scope = rememberCoroutineScope()
    return remember {
        LiquidRunnerState(scope, initialIndex)
    }
}

/**
 * Резиновое сопротивление у предела перетаскивания.
 * До [softLimit] смещение идёт один к одному, дальше асимптотически замедляется,
 * никогда не превышая `softLimit + maxOverflow`. Палец «тянет резину» вместо упора в стену.
 */
fun rubberBand(
    offset: Float,
    softLimit: Float,
    maxOverflow: Float,
    tension: Float = 0.55f
): Float {
    if (softLimit <= 0f) return offset
    val magnitude = abs(offset)
    if (magnitude <= softLimit) return offset
    if (maxOverflow <= 0f) return sign(offset) * softLimit

    val overflow = magnitude - softLimit
    // Классическая кривая резинки: линейна в нуле, асимптота ровно в maxOverflow
    val damped = (overflow * maxOverflow * tension) / (maxOverflow + tension * overflow)
    return sign(offset) * (softLimit + damped)
}

/**
 * Направленное растяжение при перетаскивании (Squash & Stretch вдоль оси движения).
 * Объём сохраняется: насколько элемент вытянулся по оси драга, настолько же он сжался поперёк.
 * Точка трансформации ставится на отстающий край, поэтому элемент «тянется» за пальцем,
 * а не раздувается симметрично.
 *
 * @param dragPx текущее смещение пальца в пикселях (знак задаёт направление)
 * @param referencePx смещение, на котором растяжение достигает [maxStretch]
 * @param vertical растягивать по вертикали вместо горизонтали
 */
fun Modifier.liquidDragStretch(
    dragPx: Float,
    referencePx: Float,
    enabled: Boolean = true,
    maxStretch: Float = 0.06f,
    vertical: Boolean = false
): Modifier = if (!enabled || referencePx <= 0f) this else this.graphicsLayer {
    val ratio = (dragPx / referencePx).coerceIn(-1f, 1f)
    val stretch = abs(ratio) * maxStretch
    // Сжатие поперёк оси движения компенсирует растяжение вдоль неё
    val along = 1f + stretch
    val across = 1f / along

    if (vertical) {
        scaleY = along
        scaleX = across
        transformOrigin = TransformOrigin(0.5f, if (dragPx > 0f) 0f else 1f)
    } else {
        scaleX = along
        scaleY = across
        transformOrigin = TransformOrigin(if (dragPx > 0f) 0f else 1f, 0.5f)
    }
}

/**
 * Появление панели, «выливающейся» из края контейнера: пружинный рост по высоте
 * с лёгким перелётом плюс подтягивание масштаба.
 */
fun liquidRevealEnter(fromBottom: Boolean = true, glitch: Boolean = false): EnterTransition =
    if (glitch) terminalRevealEnter(fromBottom) else expandVertically(
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f),
        expandFrom = if (fromBottom) Alignment.Bottom else Alignment.Top
    ) + scaleIn(
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 480f),
        initialScale = 0.92f,
        transformOrigin = TransformOrigin(0.5f, if (fromBottom) 1f else 0f)
    ) + fadeIn(tween(130, delayMillis = 30))

/**
 * Обратное втягивание панели в край контейнера. Заметно жёстче входа —
 * жидкость возвращается в резервуар быстрее, чем вытекает.
 */
fun liquidRevealExit(toBottom: Boolean = true, glitch: Boolean = false): ExitTransition =
    if (glitch) terminalRevealExit(toBottom) else shrinkVertically(
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 620f),
        shrinkTowards = if (toBottom) Alignment.Bottom else Alignment.Top
    ) + scaleOut(
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 620f),
        targetScale = 0.94f,
        transformOrigin = TransformOrigin(0.5f, if (toBottom) 1f else 0f)
    ) + fadeOut(tween(100))

/** Forge v2: панель раскрывается ровно, как строки терминала, без перелёта и масштаба. */
private fun terminalRevealEnter(fromBottom: Boolean): EnterTransition =
    expandVertically(
        animationSpec = TerminalMotion.tween(240),
        expandFrom = if (fromBottom) Alignment.Bottom else Alignment.Top
    ) + fadeIn(TerminalMotion.tween(240))

private fun terminalRevealExit(toBottom: Boolean): ExitTransition =
    shrinkVertically(
        animationSpec = TerminalMotion.tween(180),
        shrinkTowards = if (toBottom) Alignment.Bottom else Alignment.Top
    ) + fadeOut(TerminalMotion.tween(160))

/**
 * Прогресс пружинного всплывания попапа: 0 → 1 с перелётом за единицу.
 * Запускается один раз при входе в композицию.
 */
@Composable
fun rememberLiquidPopProgress(
    damping: Float = 0.58f,
    stiffness: Float = 420f
): Float {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    // Forge v2: без пружинного перелёта (liquidPopIn тогда не «желирует»)
    val glitch = org.visorlink.app.ui.theme.VlTheme.tokens.glitchMotion

    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = if (glitch) TerminalMotion.tween(220)
        else spring(dampingRatio = damping, stiffness = stiffness),
        label = "liquid_pop"
    )
    return progress
}

/**
 * Желейное всплывание попапа. На перелёте пружины ([progress] > 1) разводит оси
 * в разные стороны — именно это отличает «каплю» от обычного scale-in.
 */
fun Modifier.liquidPopIn(
    progress: Float,
    origin: TransformOrigin = TransformOrigin.Center
): Modifier = this.graphicsLayer {
    transformOrigin = origin
    val base = 0.84f + 0.16f * progress
    // Перелёт пружины превращается в лёгкое желе: шире по X, ниже по Y
    val overshoot = (progress - 1f).coerceIn(-0.5f, 0.5f)
    scaleX = base + overshoot * 0.07f
    scaleY = base - overshoot * 0.07f
    alpha = (progress * 1.4f).coerceIn(0f, 1f)
}

/**
 * Жидкое вытекание / выезд карточки из верхней пилюли (Top Pill).
 * Карточка начинает движение сверху из-под пилюли с упругим вытягиванием (Squash & Stretch),
 * каскадной задержкой по индексу и пружинным приземлением с перелётом.
 *
 * @param index позиция элемента в списке для каскадной задержки (0, 1, 2...)
 * @param triggerKey ключ перезапуска анимации (например, смена активной вкладки или загрузка чатов)
 */
@Composable
fun Modifier.liquidPillCardSlideOut(
    index: Int,
    triggerKey: Any? = Unit
): Modifier {
    val animProgress = remember(triggerKey) { Animatable(0f) }
    val glitch = org.visorlink.app.ui.theme.VlTheme.tokens.glitchMotion
    if (glitch) return terminalCardBoot(index, triggerKey)

    LaunchedEffect(triggerKey) {
        val delayMs = (index.coerceAtMost(8)) * 36L
        if (delayMs > 0) {
            delay(delayMs)
        }
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.66f,
                stiffness = 260f
            )
        )
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val travelPx = remember(density) { with(density) { 130.dp.toPx() } }

    return this.graphicsLayer {
        val p = animProgress.value
        // Условие по модулю: пружина перелетает за 1, и ветка приземления с перелётом
        // раньше никогда не выполнялась (p < 0.999 исключает p > 1)
        if (kotlin.math.abs(p - 1f) > 0.001f) {
            // Выезд сверху из-под пилюли вниз в свою позицию
            translationY = (1f - p) * -travelPx

            // Жидкий эффект деформации капли:
            // В полёте карточка вытягивается вдоль вектора движения (scaleY > 1),
            // а при пружинном перелёте (p > 1) упруго сплющивается и стабилизируется
            val overshoot = p - 1f
            if (p < 1f) {
                val stretch = (1f - p) * 0.08f
                scaleY = 1f + stretch
                scaleX = 1f - (stretch * 0.45f)
            } else {
                scaleY = 1f - (overshoot * 0.12f)
                scaleX = 1f + (overshoot * 0.08f)
            }
            alpha = (p * 2.2f).coerceIn(0f, 1f)
        } else {
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }
    }
}

/**
 * Forge v2: карточка проявляется каскадом по индексу и ровно доводится на место коротким
 * сдвигом сверху — без растяжения, перелёта, рывков и мерцания.
 */
@Composable
private fun Modifier.terminalCardBoot(index: Int, triggerKey: Any?): Modifier {
    val p = remember(triggerKey) { Animatable(0f) }
    LaunchedEffect(triggerKey) {
        p.animateTo(1f, TerminalMotion.tween(340, delayMs = index.coerceAtMost(8) * 40))
    }
    return this.graphicsLayer {
        val v = p.value
        if (v >= 1f) return@graphicsLayer
        alpha = v
        translationY = (1f - v) * -12f * density
    }
}

// ── Перелив (вливание / выливание) ───────────────────────────────────────────

/**
 * Прогресс «перелива» панели: 0 — резервуар пуст, 1 — жидкость на месте.
 *
 * Вливание пружинит с лёгким перелётом (жидкость плещет о дальнюю стенку),
 * выливание уходит короткой линейной струёй без отскока: вытекает содержимое
 * охотнее, чем заполняется.
 *
 * Значение читается как состояние, поэтому вызывающий может держать контент
 * смонтированным, пока прогресс не дойдёт до нуля, и увидеть анимацию ухода.
 */
@Composable
fun rememberLiquidPourProgress(
    visible: Boolean
): Float {
    val anim = remember { Animatable(if (visible) 1f else 0f) }
    val glitch = org.visorlink.app.ui.theme.VlTheme.tokens.glitchMotion
    LaunchedEffect(visible) {
        when {
            // Forge v2: без плеска — ровная развёртка туда и обратно
            glitch -> anim.animateTo(if (visible) 1f else 0f, TerminalMotion.tween(if (visible) 260 else 180))
            visible -> anim.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 320f))
            else -> anim.animateTo(0f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
        }
    }
    return anim.value
}

/**
 * Панель наливается в свои границы и выливается обратно за край, к которому
 * пришвартована ([fromTop] — из-под шапки вниз, иначе — снизу вверх).
 *
 * Механика:
 *  - высота резерва в лэйауте равна [progress] от измеренной, контент прибит к
 *    краю-источнику, поэтому соседи плавно расступаются и сходятся;
 *  - содержимое обрезается по синусоидальному фронту жидкости, амплитуда которого
 *    максимальна на середине перелива и равна нулю в покое (в покое не дрожит
 *    ничего — это же правило, что и «в покое не светится ничего»);
 *  - по фронту идёт мениск, а перед ним отрываются капли.
 *
 * Модификаторы рисования стоят ДО [layout] в цепочке сознательно: draw-нода
 * должна получить уже урезанный размер, иначе волна считалась бы от полной
 * высоты контента.
 */
fun Modifier.liquidPour(
    progress: Float,
    fromTop: Boolean,
    accent: Color,
    waves: Boolean = true,
    /** Forge v2: вместо волны и капель — ровный срез с неоновой линией развёртки. */
    glitch: Boolean = false,
): Modifier {
    val clamped = progress.coerceIn(0f, 1f)
    if (glitch) return this
        .drawWithContent {
            drawContent()
            if (clamped in 0.001f..0.999f) {
                val y = if (fromTop) size.height - 1.dp.toPx() else 0f
                drawRect(accent.copy(alpha = 0.8f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx()))
            }
        }
        .clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * clamped).roundToInt().coerceAtLeast(0)
            layout(placeable.width, height) {
                placeable.place(0, if (fromTop) 0 else height - placeable.height)
            }
        }
    return this
        .graphicsLayer {
            val turbulence = pourTurbulence(progress)
            // Струя уже панели: на пике перелива горлышко поджимается
            scaleX = 1f - turbulence * 0.045f
            transformOrigin = TransformOrigin(0.5f, if (fromTop) 0f else 1f)
            alpha = (clamped * 2.4f).coerceIn(0f, 1f)
        }
        .then(
            if (!waves) {
                // Без волны обрезаем строго по резерву: layout сам контент не режет,
                // и панель наезжала бы на соседей всё время перелива
                Modifier.clipToBounds()
            } else Modifier.drawWithContent {
                val turbulence = pourTurbulence(progress)
                if (turbulence <= 0.001f || size.width <= 0f || size.height <= 0f) {
                    drawContent()
                    return@drawWithContent
                }
                val amplitude = turbulence * 8.dp.toPx()
                val front = if (fromTop) size.height else 0f
                val body = liquidFrontPath(size.width, size.height, amplitude, front, fromTop)
                clipPath(body) { this@drawWithContent.drawContent() }

                // Мениск — тонкая светлая линия по поверхности жидкости
                val edge = liquidFrontPath(size.width, size.height, amplitude, front, fromTop, edgeOnly = true)
                drawPath(
                    path = edge,
                    color = accent.copy(alpha = 0.55f * turbulence.coerceAtMost(1f)),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Оторвавшиеся капли летят за фронтом, в сторону источника перелива
                val dir = if (fromTop) 1f else -1f
                val dropAlpha = (turbulence * 0.85f).coerceIn(0f, 1f)
                DropSpots.forEach { (fx, lead) ->
                    val radius = (2f + 1.6f * lead) * turbulence * density
                    if (radius < 0.5f) return@forEach
                    drawCircle(
                        color = accent.copy(alpha = dropAlpha * (1f - lead * 0.45f)),
                        radius = radius,
                        center = Offset(
                            x = size.width * fx,
                            y = front + dir * (amplitude + lead * turbulence * 22.dp.toPx())
                        )
                    )
                }
            }
        )
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * clamped).roundToInt().coerceAtLeast(0)
            layout(placeable.width, height) {
                placeable.place(0, if (fromTop) 0 else height - placeable.height)
            }
        }
}

/** Доля ширины и «опережение» каждой капли. */
private val DropSpots = listOf(0.26f to 0f, 0.52f to 0.55f, 0.78f to 0.25f)

/**
 * Возмущение поверхности: ноль в обоих покоях (пусто / налито), максимум на
 * середине перелива, плюс всплеск на пружинном перелёте.
 */
private fun pourTurbulence(progress: Float): Float {
    val clamped = progress.coerceIn(0f, 1f)
    val splash = (progress - 1f).coerceAtLeast(0f) * 1.5f
    return kotlin.math.sin(PI.toFloat() * clamped) + splash
}

/**
 * Силуэт жидкости (или только её поверхность при [edgeOnly]) с синусоидальным
 * фронтом около [front].
 */
private fun liquidFrontPath(
    width: Float,
    height: Float,
    amplitude: Float,
    front: Float,
    fromTop: Boolean,
    edgeOnly: Boolean = false
): Path {
    val path = Path()
    val periods = 1.75f
    val step = (width / 32f).coerceAtLeast(1f)
    fun waveAt(x: Float) = front + amplitude * kotlin.math.sin((x / width) * periods * 2f * PI.toFloat())

    if (!edgeOnly) {
        val back = if (fromTop) 0f else height
        path.moveTo(0f, back)
    } else {
        path.moveTo(0f, waveAt(0f))
    }
    path.lineTo(0f, waveAt(0f))
    var x = step
    while (x < width) {
        path.lineTo(x, waveAt(x))
        x += step
    }
    path.lineTo(width, waveAt(width))
    if (!edgeOnly) {
        path.lineTo(width, if (fromTop) 0f else height)
        path.close()
    }
    return path
}



