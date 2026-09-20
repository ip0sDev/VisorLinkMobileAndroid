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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.visorlink.app.data.repository.FlagsRepository
import kotlin.math.abs
import kotlin.math.sign

/**
 * Единая точка чтения флага жидких анимаций.
 * Все Liquid Glass эффекты обязаны проходить через неё, чтобы ключ флага не расползался по UI.
 */
@Composable
fun rememberLiquidEnabled(flagsRepository: FlagsRepository = koinInject()): Boolean {
    val flags by flagsRepository.flags.collectAsState()
    return flags.isEnabled("animation_test")
}

/**
 * Состояние упругой желейной деформации (Squash & Stretch).
 * Поддерживает мягкие сжатия при нажатии, упругие баунсы при отпускании и импульсы событий.
 */
@Stable
class LiquidJellyState(
    private val scope: CoroutineScope,
    val softness: Float = 0.10f,
    val damping: Float = 0.65f,
    val stiffness: Float = 320f
) {
    val animatable = Animatable(0f)

    val scaleX: Float
        get() = calculateJellyScale(animatable.value).first

    val scaleY: Float
        get() = calculateJellyScale(animatable.value).second

    fun press(customSoftness: Float = softness) {
        scope.launch {
            animatable.animateTo(-customSoftness, tween(80))
        }
    }

    fun release(bounceStretch: Float = softness * 0.7f) {
        scope.launch {
            animatable.animateTo(bounceStretch, tween(70))
            animatable.animateTo(0f, spring(dampingRatio = damping, stiffness = stiffness))
        }
    }

    fun pulse(intensity: Float = softness) {
        scope.launch {
            animatable.animateTo(intensity, tween(80))
            animatable.animateTo(0f, spring(dampingRatio = damping, stiffness = stiffness))
        }
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
    return remember(softness, damping, stiffness) {
        LiquidJellyState(scope, softness, damping, stiffness)
    }
}

/**
 * Модификатор применения желейной упругой деформации (Squash & Stretch).
 */
fun Modifier.liquidJelly(
    state: LiquidJellyState,
    enabled: Boolean = true
): Modifier = if (enabled) {
    this.graphicsLayer {
        this.scaleX = state.scaleX
        this.scaleY = state.scaleY
    }
} else this

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
fun liquidRevealEnter(fromBottom: Boolean = true): EnterTransition =
    expandVertically(
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
fun liquidRevealExit(toBottom: Boolean = true): ExitTransition =
    shrinkVertically(
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 620f),
        shrinkTowards = if (toBottom) Alignment.Bottom else Alignment.Top
    ) + scaleOut(
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 620f),
        targetScale = 0.94f,
        transformOrigin = TransformOrigin(0.5f, if (toBottom) 1f else 0f)
    ) + fadeOut(tween(100))

/**
 * Прогресс пружинного всплывания попапа: 0 → 1 с перелётом за единицу.
 * Запускается один раз при входе в композицию; при выключенных анимациях отдаёт 1f.
 * Состав вызовов не зависит от [enabled] — флаг переключается в рантайме
 * через FlagFlipper, и ранний выход ломал бы слоты композиции.
 */
@Composable
fun rememberLiquidPopProgress(
    enabled: Boolean,
    damping: Float = 0.58f,
    stiffness: Float = 420f
): Float {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = spring(dampingRatio = damping, stiffness = stiffness),
        label = "liquid_pop"
    )
    return if (enabled) progress else 1f
}

/**
 * Желейное всплывание попапа. На перелёте пружины ([progress] > 1) разводит оси
 * в разные стороны — именно это отличает «каплю» от обычного scale-in.
 */
fun Modifier.liquidPopIn(
    progress: Float,
    enabled: Boolean = true,
    origin: TransformOrigin = TransformOrigin.Center
): Modifier = if (!enabled) this else this.graphicsLayer {
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
 * @param enabled флаг активности анимации (animation_test)
 * @param triggerKey ключ перезапуска анимации (например, смена активной вкладки или загрузка чатов)
 */
@Composable
fun Modifier.liquidPillCardSlideOut(
    index: Int,
    enabled: Boolean = true,
    triggerKey: Any? = Unit
): Modifier {
    if (!enabled) return this

    val animProgress = remember(triggerKey) { Animatable(0f) }

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
        if (p < 0.999f) {
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


