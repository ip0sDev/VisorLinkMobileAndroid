package org.visorlink.app.ui.components.idcard

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import coil.compose.AsyncImage
import org.visorlink.app.R
import org.visorlink.app.data.idcard.IdCard
import org.visorlink.app.data.idcard.IdCardGenerator
import org.visorlink.app.data.idcard.IdCardTraits
import org.visorlink.app.data.idcard.IdEdition
import org.visorlink.app.data.idcard.IdFinish
import org.visorlink.app.data.idcard.IdFoil
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.ui.theme.IdCardMaterials
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.ui.theme.BaseLayer
import org.visorlink.app.ui.theme.CardPalette
import org.visorlink.app.ui.theme.CardTextures
import org.visorlink.app.ui.theme.CardTone
import org.visorlink.app.ui.theme.cardPalette
import org.visorlink.app.ui.theme.customPalette
import org.visorlink.app.ui.theme.clamp
import org.visorlink.app.ui.theme.cssLinear
import org.visorlink.app.ui.theme.cssRepeating
import org.visorlink.app.ui.theme.drawBase
import org.visorlink.app.ui.theme.hsl
import org.visorlink.app.ui.theme.mix
import org.visorlink.app.ui.theme.ART_W
import org.visorlink.app.ui.theme.ART_H
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Кто изображён на карте. */
data class IdCardPerson(val displayName: String?, val username: String?, val avatarUrl: String?)

/** Карта — физический предмет 85.6 × 54; всё внутри в em, ширина карты = 36em (как в idcard.css). */
private const val EM_W = 36f
private const val EM_H = EM_W * 54f / 85.6f

private val CardInter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
private val CardMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/** Вордмарк VISORLINK — латиница, Space Grotesk допустим (см. CLAUDE.md про кириллицу). */
private val CardBrand = FontFamily(
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_semibold, FontWeight.Bold),
)

/**
 * ID-карта VisorLink: лицо и оборот, оформление по режиму, признаки выпуска и детали
 * из seed. Порт src/components/idcard/IdCard.jsx: цвета, расположение полей и тексты
 * совпадают с вебом, текстуры краски и зерна — те же алгоритмы.
 *
 * @param accent цвет профиля — тонкая линия под шапкой.
 * @param emojis эмодзи профиля — наклейка (до 2 символов).
 * @param issueTime секунды от начала церемонии выдачи (анимация печати) или `null`.
 */
@Composable
fun VlIdCard(
    card: IdCard,
    person: IdCardPerson,
    modifier: Modifier = Modifier,
    width: Dp = 320.dp,
    accent: Color? = null,
    emojis: String = "",
    interactive: Boolean = true,
    issueTime: Float? = null,
    /** 1 — показать оборот сразу (скриншот-тесты, превью). */
    initialFace: Int = 0,
    /**
     * Своя нарисованная подпись (SVG-путь `M x y L x y …` в 100×30, спека §2.2). Заменяет росчерк
     * из seed везде, где он рисуется; невалидная строка — росчерк. У Protogen не видна (ключ).
     */
    signature: String? = null,
) {
    val reduceMotion = VlTheme.tokens.reduceMotion
    val tilt = remember {
        CardTiltState().apply { if (initialFace == 1) { face = 1; ry = 180f; ty = 180f } }
    }
    val active = interactive && issueTime == null
    val name = person.displayName?.takeIf { it.isNotBlank() } ?: person.username?.takeIf { it.isNotBlank() } ?: "—"
    val modeLabel = modeLabel(card.mode)
    val registered = formatCardDate(card.registeredAt)
    val aria = stringResource(R.string.idcard_aria, name, person.username ?: "user", modeLabel, registered, card.serial)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IdCardScene(card, person, name, width, accent, emojis, issueTime, tilt, active, reduceMotion, signature, Modifier.semantics { contentDescription = aria })
        if (active) {
            val label = stringResource(if (tilt.face == 1) R.string.idcard_show_front else R.string.idcard_show_back)
            Row(
                Modifier
                    .clip(VlTheme.tokens.shapes.chip)
                    .clickable { tilt.flip(reduceMotion) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun modeLabel(mode: IdMode): String = stringResource(
    when (mode) {
        IdMode.STANDARD -> R.string.idcard_mode_standard
        IdMode.PROTOGEN -> R.string.idcard_mode_protogen
        IdMode.BEAST -> R.string.idcard_mode_beast
    },
)

/** «3 октября 2026» / «October 3, 2026» — как formatDate(…, 'day') в вебе. */
@Composable
internal fun formatCardDate(ms: Long): String {
    if (ms <= 0) return "—"
    val locale = LocalConfiguration.current.locales[0]
    val pattern = if (locale.language == "ru") "d MMMM yyyy" else "MMMM d, yyyy"
    return SimpleDateFormat(pattern, if (locale.language == "ru") locale else Locale.ENGLISH).format(Date(ms))
}

// ── Наклон, переворот, инерция (веб: useCardTilt.js) ──

private const val MAX_TILT_X = 14f
private const val MAX_TILT_Y = 18f

private fun faceAngle(ry: Float, face: Int) = Math.round((ry - face * 180f) / 360f) * 360f + face * 180f

@Stable
internal class CardTiltState {
    var rx by mutableFloatStateOf(0f)
    var ry by mutableFloatStateOf(0f)
    var face by mutableIntStateOf(0)
    var vy = 0f
    var vx = 0f
    var tx = 0f
    var ty = 0f
    var dragging = false
    /** Растёт при каждом «пинке» — будит цикл анимации. */
    var kicks by mutableIntStateOf(0)
    /** Пиковая скорость с последнего приземления — для силы удара. */
    var peakSpeed = 0f
    var haptics: CardHaptics? = null
    /** Карта реагирует на наклон; у неинтерактивной «скрытое изображение» — постоянные 0.2. */
    var interactive = true
    private var lastDetent = 0
    private var lastShowBack = false
    private var lastVySign = 0

    fun kick() { kicks++ }

    fun flip(reduceMotion: Boolean) {
        ty = faceAngle(ry, face) + 180f // всегда вперёд на пол-оборота
        face = 1 - face
        haptics?.flip()
        if (reduceMotion) { ry = ty; vy = 0f; rx = 0f; tx = 0f } else kick()
    }

    /**
     * Один шаг пружины на 1/60 с; true — карта успокоилась. Жёсткость и затухание подобраны
     * с перелётом: карта качается и дважды-трижды «отпружинивает» к стороне, у веба — без отскока.
     */
    fun step(): Boolean {
        if (!dragging) {
            vy += (ty - ry) * 0.11f
            vy *= 0.83f
            ry += vy
            vx += (tx - rx) * 0.16f
            vx *= 0.78f
            rx += vx
            peakSpeed = max(peakSpeed, abs(vy))
            // Отскок: скорость сменила знак рядом с целью — короткий щелчок, гаснет вместе с качанием
            val sign = if (vy > 0.3f) 1 else if (vy < -0.3f) -1 else 0
            if (sign != 0 && lastVySign != 0 && sign != lastVySign && abs(ty - ry) < 25f) {
                haptics?.bounce((abs(vy) / 6f).coerceAtMost(1f))
            }
            if (sign != 0) lastVySign = sign
        }
        afterMove()
        val settled = !dragging && abs(ty - ry) < 0.05f && abs(vy) < 0.05f && abs(tx - rx) < 0.05f && abs(vx) < 0.05f
        if (settled) {
            if (peakSpeed > 4f) haptics?.land((peakSpeed / 20f).coerceAtMost(1f))
            peakSpeed = 0f
            lastVySign = 0
        }
        return settled
    }

    /** Хаптика по положению: деление каждые 30° и переход через ребро. */
    fun afterMove() {
        val detent = Math.floorDiv(ry.toInt(), 30)
        if (detent != lastDetent) {
            haptics?.detent((abs(vy) / 12f).coerceIn(0f, 1f))
            lastDetent = detent
        }
        val back = showBack
        if (back != lastShowBack) {
            haptics?.edge()
            lastShowBack = back
        }
    }

    /** Наклон видимой стороны для параллакса слоёв (fx, fy) и его величина. */
    val faceY: Float get() {
        val local = (((ry % 360f) + 540f) % 360f) - 180f
        return if (abs(local) > 90f) (if (local > 0) local - 180f else local + 180f) else local
    }
    val fx: Float get() = clamp(faceY / 24f, -1.6f, 1.6f)
    val fy: Float get() = clamp(rx / MAX_TILT_X, -1.2f, 1.2f)
    val showBack: Boolean get() = abs((((ry % 360f) + 540f) % 360f) - 180f) > 90f
}

@Composable
private fun IdCardScene(
    card: IdCard,
    person: IdCardPerson,
    name: String,
    width: Dp,
    accent: Color?,
    emojis: String,
    issueTime: Float?,
    tilt: CardTiltState,
    active: Boolean,
    reduceMotion: Boolean,
    signature: String?,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val em = width / EM_W
    val height = width * (54f / 85.6f)
    val traits = card.traits
    val details = remember(traits.seed) { IdCardGenerator.deriveDetails(traits.seed) }
    // Кастомный скин заменяет палитру режима (раскладка и графика — режима), если все три цвета валидны
    val palette = remember(card.mode, traits.variant, traits.finish, card.custom) {
        card.custom?.colors?.let { (b, pr, se) -> customPalette(b, pr, se, traits.finish) } ?: cardPalette(card.mode, traits.variant, traits.finish)
    }
    // Своя подпись (§2.2) или росчерк из seed (§2.1): штрихи в координатах 100×30
    val strokes = remember(signature, traits.seed) {
        IdCardGenerator.parseSignature(signature) ?: listOf(IdCardGenerator.signaturePoints(traits.seed))
    }
    tilt.interactive = active
    val p = IssueProgress(issueTime)

    val haptics = rememberCardHaptics()
    tilt.haptics = if (active) haptics else null

    // Цикл анимации: один на всё время жизни карты. Спит, пока карта успокоена; «пинок»
    // (палец, гироскоп, переворот) будит его. Шаг фиксированный 1/60 с — на 120 Гц так же.
    LaunchedEffect(tilt) {
        var seen = -1
        while (true) {
            seen = snapshotFlow { tilt.kicks }.first { it != seen }
            var acc = 0L
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                acc += now - last
                last = now
                var settled = false
                while (acc >= 16_666_667L) { settled = tilt.step(); acc -= 16_666_667L }
                seen = tilt.kicks
                if (settled) break
            }
        }
    }

    // Гироскоп: наклон относительно того, как держат телефон; база медленно подстраивается
    if (active && !reduceMotion) GyroTilt(tilt)

    // Нажатие: карта чуть «утопает» под пальцем, при отпускании пружинит обратно с перелётом
    val press = remember { androidx.compose.animation.core.Animatable(1f) }
    val pressScope = androidx.compose.runtime.rememberCoroutineScope()
    val onPress: (Boolean) -> Unit = { down ->
        pressScope.launch {
            if (reduceMotion) return@launch
            if (down) {
                press.animateTo(0.955f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 900f))
            } else {
                press.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.32f, stiffness = 420f))
            }
        }
    }

    Box(
        modifier
            .size(width, height)
            .then(if (active) Modifier.pointerInput(Unit) { dragTilt(tilt, density.density, reduceMotion, onPress) } else Modifier),
    ) {
        // Тень на «столе»: сдвигается и сжимается при повороте
        val cosY = abs(cos(tilt.ry * PI.toFloat() / 180f))
        Box(
            Modifier
                .offset(x = width * 0.06f, y = height * 0.16f)
                .size(width * 0.88f, height * 0.89f)
                .graphicsLayer {
                    translationX = -tilt.faceY * 0.3f * density.density
                    translationY = tilt.rx * -0.5f * density.density
                    scaleX = 0.3f + 0.7f * cosY
                    alpha = 0.75f
                }
                .drawBehind {
                    drawIntoBlur(this, (em * 1.1f).toPx(), (em * 1.4f).toPx())
                },
        )
        val thunk = p.thunk
        androidx.compose.runtime.CompositionLocalProvider(LocalCardTilt provides tilt) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationX = tilt.rx + 2.5f * thunk
                    rotationY = tilt.ry
                    scaleX = (1f - 0.04f * thunk) * press.value
                    scaleY = (1f - 0.04f * thunk) * press.value
                    cameraDistance = 11f * density.density * (width.value / 36f)
                },
        ) {
            val faceShape = if (card.mode == IdMode.PROTOGEN) ProtogenFaceShape else IdCardMaterials.corner(em * 0.95f)
            if (!tilt.showBack) {
                Face(card, palette, details, faceShape, em, tilt, p) {
                    FrontContent(card, person, name, palette, details, em, accent, emojis, p, strokes)
                }
            } else {
                Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                    Face(card, palette, details, faceShape, em, tilt, p) {
                        BackContent(card, person, name, palette, details, em, p, strokes)
                    }
                }
            }
        }
        }
    }
}

private fun drawIntoBlur(scope: DrawScope, blurPx: Float, radiusPx: Float) = with(scope) {
    drawIntoCanvasCompat { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(140, 0, 0, 0)
            maskFilter = android.graphics.BlurMaskFilter(max(1f, blurPx), android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(0f, 0f, size.width, size.height, radiusPx, radiusPx, paint)
    }
}

private inline fun DrawScope.drawIntoCanvasCompat(block: (android.graphics.Canvas) -> Unit) =
    drawContext.canvas.nativeCanvas.let(block)

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dragTilt(
    tilt: CardTiltState,
    density: Float,
    reduceMotion: Boolean,
    onPress: (Boolean) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        tilt.dragging = true
        tilt.haptics?.press()
        onPress(true)
        val startRy = tilt.ry
        var moved = 0f
        var total = Offset.Zero
        var lastX = down.position.x
        var lastT = down.uptimeMillis
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            total += change.positionChange()
            moved = max(moved, hypot(total.x, total.y) / density)
            val dt = max(1L, change.uptimeMillis - lastT)
            tilt.vy = ((change.position.x - lastX) / density * 0.55f) / (dt / 16f)
            tilt.peakSpeed = max(tilt.peakSpeed, abs(tilt.vy))
            lastX = change.position.x
            lastT = change.uptimeMillis
            tilt.ry = startRy + total.x / density * 0.55f
            tilt.rx = clamp(-total.y / density * 0.12f, -MAX_TILT_X, MAX_TILT_X)
            tilt.afterMove()
            if (moved >= 6f) change.consume()
        }
        tilt.dragging = false
        onPress(false)
        if (moved < 6f) {
            tilt.flip(reduceMotion)
            return@awaitEachGesture
        }
        tilt.haptics?.release()
        // Инерция: куда «докатится» карта — та сторона и будет
        val projected = tilt.ry + tilt.vy * 14f
        val snapped = Math.round(projected / 180f) * 180f
        tilt.face = ((Math.round(snapped / 180f) % 2) + 2) % 2
        tilt.ty = snapped
        tilt.tx = 0f
        tilt.vx = 0f
        if (reduceMotion) { tilt.ry = snapped; tilt.rx = 0f; tilt.vy = 0f } else tilt.kick()
    }
}

@Composable
private fun GyroTilt(tilt: CardTiltState) {
    val context = LocalContext.current
    DisposableEffect(tilt) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        // Накопленный поворот телефона относительно «нейтрали», градусы
        var angX = 0f
        var angY = 0f
        var lastNs = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val dt = if (lastNs == 0L) 0f else (e.timestamp - lastNs) / 1e9f
                lastNs = e.timestamp
                if (dt <= 0f || dt > 0.1f) return
                // values[0] — вокруг оси X (наклон к себе), values[1] — вокруг Y (влево/вправо), рад/с
                angX += Math.toDegrees(e.values[0].toDouble()).toFloat() * dt
                angY += Math.toDegrees(e.values[1].toDouble()).toFloat() * dt
                // База медленно подстраивается: как держат телефон — то и нейтраль (~1.5 с)
                val leak = (1f - dt * 0.7f).coerceIn(0f, 1f)
                angX *= leak
                angY *= leak
                if (tilt.dragging) return
                val dx = clamp(angY / 22f, -1f, 1f)
                val dy = clamp(angX / 22f, -1f, 1f)
                tilt.tx = -dy * MAX_TILT_X
                tilt.ty = faceAngle(tilt.ry, tilt.face) + dx * MAX_TILT_Y * (if (tilt.face == 1) -1 else 1)
                tilt.kick()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
}

// ── Церемония выдачи: прогресс по времени (задержки — как в idcard.css) ──

internal class IssueProgress(private val t: Float?) {
    val issuing = t != null
    private fun seg(delay: Float, dur: Float) = if (t == null) 1f else ((t - delay) / dur).coerceIn(0f, 1f)
    private fun steps(v: Float, n: Int) = floor(v * n) / n
    private fun easeOut(v: Float) = 1f - (1f - v) * (1f - v)

    val print get() = seg(0.2f, 0.9f)
    val draw get() = easeOut(seg(0.2f, 1.8f))
    val head get() = seg(0.5f, 0.5f)
    val develop get() = seg(0.9f, 1.3f)
    val brackets get() = steps(seg(1.6f, 0.3f), 2)
    val led get() = seg(2.1f, 0.4f)

    /** Мигание визора при загрузке (idc-boot, steps(1)). */
    val ledBoot: Float get() {
        if (t == null) return 1f
        val v = seg(2.4f, 0.8f)
        return when {
            v <= 0f -> 0f
            v < 0.2f -> 0f
            v < 0.3f -> 1f
            v < 0.5f -> 0f
            v < 0.62f -> 1f
            v < 0.75f -> 0.3f
            else -> 1f
        }
    }

    /** Поле печатается слева направо (idc-type, steps(16)). */
    fun type(i: Int) = steps(seg(1.5f + i * 0.3f, 0.5f), 16)
    val serial get() = steps(seg(3.1f, 0.6f), 12)

    /** Удар штампа: масштаб 2.3 → 1, краска проявляется к 55 %. */
    val stampScale: Float get() {
        val v = seg(3.6f, 0.42f)
        val c = 1.70158f * 1.4f
        val back = 1f + (c + 1f) * (v - 1f) * (v - 1f) * (v - 1f) + c * (v - 1f) * (v - 1f)
        return 2.3f + (1f - 2.3f) * back
    }
    val stampInk get() = (seg(3.6f, 0.42f) / 0.55f).coerceAtMost(1f)

    val thunk: Float get() {
        if (t == null) return 0f
        val v = seg(3.85f, 0.3f)
        return if (v <= 0f || v >= 1f) 0f else if (v < 0.35f) v / 0.35f else (1f - v) / 0.65f
    }
    val sticker: Float get() {
        val v = seg(4.1f, 0.4f)
        val c = 1.70158f * 1.6f
        return if (v >= 1f) 1f else 1f + (c + 1f) * (v - 1f) * (v - 1f) * (v - 1f) + c * (v - 1f) * (v - 1f)
    }
    /** Ламинатор: блик проходит по карте; null — не идёт. */
    val laminate: Float? get() = if (t == null) null else seg(4.4f, 0.9f).takeIf { it > 0f && it < 1f }
    val holo get() = seg(5f, 0.8f)
    val holoSweep get() = easeOut(seg(5f, 1.2f))
    val revealFlash: Float? get() {
        if (t == null) return null
        val v = seg(5.1f, 1.3f)
        return if (v <= 0f) 0f else if (v < 0.45f) v / 0.45f else 1f - (v - 0.45f) / 0.55f * 0.8f
    }
}

// ── Сторона карты: фон, край, слои отделки и потёртости ──

private val ProtogenFaceShape: Shape = GenericShape { size, _ ->
    // polygon(3.04% 0, 100% 0, 100% 94.4%, 96.9% 100%, 0 100%, 0 5.2%)
    moveTo(size.width * 0.0304f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height * 0.944f)
    lineTo(size.width * 0.969f, size.height)
    lineTo(0f, size.height)
    lineTo(0f, size.height * 0.052f)
    close()
}

@Composable
private fun Face(
    card: IdCard,
    palette: CardPalette,
    details: IdCardGenerator.Details,
    shape: Shape,
    em: Dp,
    tilt: CardTiltState,
    p: IssueProgress,
    content: @Composable BoxScope.() -> Unit,
) {
    val traits = card.traits
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .drawBehind { drawBase(palette.base) }
            .drawWithContent {
                drawContent()
                // Край: inset 0 0 0 1px edge + блик сверху
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = Path().apply { addOutline(outline) }
                clipPath(path) {
                    when {
                        traits.finish == IdFinish.GOLD -> {
                            drawPath(path, IdCardMaterials.GoldEdge, style = Stroke(2f))
                            drawPath(path, IdCardMaterials.GoldInner, style = Stroke((em * 0.4f).toPx()))
                        }
                        else -> drawPath(path, palette.edge, style = Stroke(2f))
                    }
                    drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, 1f), Offset(size.width, 1f), 1f)
                    if (traits.wear == 3) {
                        // inset 0 0 1.8em — затемнение по краю: несколько полос, гаснущих внутрь
                        val c = if (palette.tone == CardTone.LIGHT) IdCardMaterials.WearShadowLight.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.22f)
                        val band = (em * 1.8f).toPx()
                        for (i in 1..6) drawPath(path, c.copy(alpha = c.alpha / 6f), style = Stroke(band * 2f * i / 6f))
                    }
                }
            },
    ) {
        content()
        Overlays(traits, details, palette, em, tilt, p, protogen = card.mode == IdMode.PROTOGEN)
    }
}

/** Слои поверх содержимого стороны: отделка, «скрытое изображение», потёртость, плёнка, блик. */
@Composable
private fun BoxScope.Overlays(traits: IdCardTraits, details: IdCardGenerator.Details, palette: CardPalette, em: Dp, tilt: CardTiltState, p: IssueProgress, protogen: Boolean) {
    val reveal = traits.foil == IdFoil.AURORA || traits.foil == IdFoil.GALAXY
    val revealArt = remember(traits.foil, traits.seed) { if (reveal) RevealArt(traits.foil == IdFoil.GALAXY, traits.seed) else null }
    val scratches = remember(traits.seed, traits.wear) { IdCardGenerator.scratches(traits.seed, traits.wear * 5) }
    val light = palette.tone == CardTone.LIGHT
    Canvas(Modifier.matchParentSize()) {
        val w = size.width
        val h = size.height
        val emPx = em.toPx()
        val fx = tilt.fx
        val fy = tilt.fy
        val mag = min(1f, hypot(fx, fy))

        if (traits.finish == IdFinish.PEARL) {
            val box = Size(w * 2f, h * 2f)
            val origin = Offset(-w * 0.5f + fx * 0.12f * box.width, -h * 0.5f + fy * 0.1f * box.height)
            drawRect(
                cssLinear(120f, IdCardMaterials.Pearl, box, origin),
                alpha = if (light) 0.55f else 0.22f,
            )
        }
        if (traits.finish == IdFinish.METALLIC) {
            var x = 0f
            while (x < w) {
                drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, h), 1f)
                drawLine(Color.Black.copy(alpha = 0.04f), Offset(x + 1f, 0f), Offset(x + 1f, h), 1f)
                x += 3f
            }
        }
        revealArt?.let { art ->
            // Проступает под углом (§11.3 reveal); у неинтерактивной карты — постоянные 0.2
            val opacity = p.revealFlash ?: if (tilt.interactive) clamp(mag * 1.5f - 0.15f, 0f, 1f) else 0.2f
            val galaxy = traits.foil == IdFoil.GALAXY
            val box = Size(w * 2.2f, h * 2.2f)
            val origin = Offset(-w * 0.6f - fx * 0.16f * box.width, -h * 0.6f - fy * 0.13f * box.height)
            val brush = if (galaxy) {
                cssRepeating(118f, IdCardMaterials.RevealGalaxy, box, origin)
            } else {
                cssRepeating(118f, IdCardMaterials.RainbowAurora, box, origin)
            }
            // Как каскад CSS: правило светлого тона идёт последним и перекрывает и галактику
            val fillAlpha = when {
                light -> 0.5f
                galaxy -> 0.95f
                else -> 0.32f
            }
            scale(w / ART_W, h / ART_H, pivot = Offset.Zero) {
                drawPath(art.fill, brush, alpha = opacity * fillAlpha)
                drawPath(art.strokes, brush, alpha = opacity * fillAlpha, style = Stroke(2.2f))
            }
        }
        if (traits.wear > 0) {
            // Зерно: статичная текстура плиткой 6em
            drawRect(textureBrush(CardTextures.grain, emPx * 6f), alpha = when (traits.wear) { 1 -> 0.45f; 2 -> 0.75f; else -> 1f })
            val scratch = if (light) IdCardMaterials.ScratchLight else Color.White.copy(alpha = 0.38f)
            for (l in scratches) {
                drawLine(scratch, Offset(l.x1.toFloat() / 100f * w, l.y1.toFloat() / 100f * h), Offset(l.x2.toFloat() / 100f * w, l.y2.toFloat() / 100f * h), (l.w * 2.4).toFloat() * emPx / 10f, StrokeCap.Round)
            }
            if (traits.wear >= 2) drawSmudge(details.smudge, emPx, light)
            if (traits.wear >= 3) drawChip(details.chipCorner, emPx, palette.core)
        }
        if (traits.laminated) {
            val inset = emPx * 0.28f
            // Плёнка Protogen — без скругления, как и сама карта
            val r = if (protogen) 0f else emPx * 0.72f
            val filmTopLeft = Offset(inset, inset)
            val filmSize = Size(w - 2 * inset, h - 2 * inset)
            val corner = androidx.compose.ui.geometry.CornerRadius(r)
            drawRoundRect(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 0.35f to Color.Transparent, startY = inset, endY = h - inset),
                filmTopLeft, filmSize, corner,
            )
            // inset 0 0 0.7em rgba(255,255,255,.06): мягкое свечение по краю плёнки внутрь
            val glow = emPx * 0.7f
            for (i in 1..5) {
                drawRoundRect(Color.White.copy(alpha = 0.06f / 5f), filmTopLeft, filmSize, corner, style = Stroke(glow * 2f * i / 5f))
            }
            drawRoundRect(Color.White.copy(alpha = 0.3f), filmTopLeft, filmSize, corner, style = Stroke(1f))
            details.bubble?.let { b ->
                val c = Offset(b.x / 100f * w, b.y / 100f * h)
                val bw = emPx * 1.1f * b.size.toFloat()
                val bh = emPx * 0.8f * b.size.toFloat()
                rotate(-20f, c) {
                    drawOval(Color.White.copy(alpha = 0.3f), c - Offset(bw / 2, bh / 2), Size(bw, bh), style = Stroke(bw * 0.12f))
                    drawOval(Color.White.copy(alpha = 0.6f), c - Offset(bw * 0.25f, bh * 0.3f), Size(bw * 0.15f, bh * 0.15f))
                }
            }
            p.laminate?.let { v ->
                val bx = (-0.9f + 1.8f * v) * w
                drawRect(
                    cssLinear(100f, listOf(0f to Color.Transparent, 0.4f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.65f), 0.6f to Color.Transparent, 1f to Color.Transparent), Size(w * 1.2f, h * 1.2f), Offset(bx - w * 0.1f, -h * 0.1f)),
                    alpha = 1f - v,
                )
            }
        }
        // Блик: на ламинате и металлических отделках ярче
        val strong = traits.laminated || traits.finish in setOf(IdFinish.METALLIC, IdFinish.OBSIDIAN, IdFinish.GOLD)
        val box = Size(w * 2.6f, h * 2.6f)
        val origin = Offset(-w * 0.8f - fx * 0.2f * box.width, -h * 0.8f + fy * 0.16f * box.height)
        val sheen = if (strong) {
            listOf(0f to Color.Transparent, 0.39f to Color.Transparent, 0.46f to Color.White.copy(alpha = 0.06f), 0.5f to Color.White.copy(alpha = 0.16f), 0.54f to Color.White.copy(alpha = 0.06f), 0.61f to Color.Transparent, 1f to Color.Transparent)
        } else {
            listOf(0f to Color.Transparent, 0.43f to Color.Transparent, 0.47f to Color.White.copy(alpha = 0.04f), 0.5f to Color.White.copy(alpha = 0.09f), 0.53f to Color.White.copy(alpha = 0.04f), 0.59f to Color.Transparent, 1f to Color.Transparent)
        }
        drawRect(cssLinear(105f, sheen, box, origin))
    }
}

private fun textureBrush(texture: androidx.compose.ui.graphics.ImageBitmap, tilePx: Float): Brush {
    val bitmap = texture.asAndroidBitmap()
    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(android.graphics.Matrix().apply { setScale(tilePx / bitmap.width, tilePx / bitmap.height) })
    }
    return ShaderBrush(shader)
}

private fun DrawScope.drawSmudge(s: IdCardGenerator.Smudge, emPx: Float, light: Boolean) {
    val c = Offset(s.x / 100f * size.width, s.y / 100f * size.height)
    val ring = if (light) IdCardMaterials.SmudgeLight else Color.White
    rotate(s.rot.toFloat(), c) {
        // Папиллярные линии: концентрические эллипсы, гаснущие к краю
        var r = emPx * 0.12f
        val maxR = emPx * 2.7f
        while (r < maxR) {
            val fade = 1f - (r / maxR)
            drawOval(ring.copy(alpha = 0.07f * fade * 1.6f), c - Offset(r, r * 1.2f), Size(r * 2, r * 2.4f), style = Stroke(emPx * 0.07f))
            r += emPx * 0.19f
        }
    }
}

private fun DrawScope.drawChip(corner: Int, emPx: Float, core: Color) {
    val cw = emPx * 1.7f
    val ch = emPx * 1.2f
    val w = size.width
    val h = size.height
    val pts: List<Offset> = when (corner) {
        0 -> listOf(Offset(0f, 0f), Offset(cw, 0f), Offset(cw * 0.62f, ch * 0.28f), Offset(cw * 0.22f, ch * 0.52f), Offset(0f, ch))
        1 -> listOf(Offset(w - cw, 0f), Offset(w, 0f), Offset(w, ch), Offset(w - cw + cw * 0.76f, ch * 0.5f), Offset(w - cw + cw * 0.34f, ch * 0.24f))
        2 -> listOf(Offset(w, h - ch), Offset(w, h), Offset(w - cw, h), Offset(w - cw + cw * 0.4f, h - ch + ch * 0.72f), Offset(w - cw + cw * 0.8f, h - ch + ch * 0.44f))
        else -> listOf(Offset(0f, h - ch), Offset(cw * 0.28f, h - ch + ch * 0.5f), Offset(cw * 0.66f, h - ch + ch * 0.76f), Offset(cw, h), Offset(0f, h))
    }
    drawPath(Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }; close() }, core, alpha = 0.7f)
}

// ── Позиционирование в em ──

private fun Modifier.at(x: Float, y: Float, em: Dp) = this.offset(x = em * x, y = em * y)

private fun emSp(em: Dp, k: Float, density: androidx.compose.ui.unit.Density) = with(density) { (em * k).toSp() }

// ── Лицевая сторона ──

@Composable
private fun BoxScope.FrontContent(
    card: IdCard,
    person: IdCardPerson,
    name: String,
    pal: CardPalette,
    d: IdCardGenerator.Details,
    em: Dp,
    accent: Color?,
    emojis: String,
    p: IssueProgress,
    strokes: List<List<Pair<Double, Double>>>,
) {
    val density = LocalDensity.current
    val mode = card.mode
    val traits = card.traits
    val registered = formatCardDate(card.registeredAt)
    // Форма голограммы: у кастомного скина — своя (§4.5)
    val holoShape = card.custom?.holo ?: d.holoShape

    // Защитная печать: гильош / дорожки / окрас (со сдвигом печати)
    PrintLayer(mode, d, d.artSeed, pal, em, faint = false, p = p)

    if (mode == IdMode.PROTOGEN) {
        // Рамка: неоновый контур со срезами и линия под шапкой
        Canvas(Modifier.matchParentSize().graphicsLayer { alpha = p.head }) {
            scale(size.width / ART_W, size.height / ART_H, pivot = Offset.Zero) {
                val k = ART_W / size.width
                drawPath(svgPath("M26 1.5H855.5V510L829 538.5H1.5V28z"), pal.neon.copy(alpha = 0.75f), style = Stroke(1.5f * density.density * k))
                drawPath(svgPath("M14 62H300l14-12H842"), pal.neon.copy(alpha = 0.75f * 0.5f), style = Stroke(1f * density.density * k))
            }
        }
    }

    Header(mode, pal, em, p)

    if (mode == IdMode.STANDARD) {
        Text(
            "VISORLINK · ID · ".repeat(14),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = TextStyle(fontFamily = CardMono, fontWeight = FontWeight.Medium, fontSize = emSp(em, 0.3f, density), letterSpacing = emSp(em, 0.09f, density), color = pal.muted),
            modifier = Modifier.fillMaxWidth().at(0f, 2.86f, em).graphicsLayer { alpha = 0.6f * p.head },
        )
    }

    Photo(mode, person, name, pal, em, traits.tilt.toFloat(), p)

    if (mode == IdMode.PROTOGEN) LedVisor(d.led, pal, em, p)

    Fields(card, name, person.username ?: "user", registered, formatCardDate(card.issuedAt), pal, em, p)

    when (mode) {
        IdMode.STANDARD -> {
            // Подпись
            Column(Modifier.at(12.4f, 13.4f, em).width(em * 10.5f).typeReveal(p.type(5))) {
                Text(
                    stringResource(R.string.idcard_field_signature).uppercase(),
                    style = TextStyle(fontFamily = CardInter, fontWeight = FontWeight.SemiBold, fontSize = emSp(em, 0.5f, density), letterSpacing = emSp(em, 0.06f, density), color = pal.muted),
                )
                SignatureCanvas(strokes, if (pal.tone == CardTone.DARK) hsl(210f, 80f, 80f) else hsl(226f, 62f, 30f), alignStart = false, Modifier.fillMaxWidth().height(em * 3f))
            }
            // «Призрачное» фото
            person.avatarUrl?.let { url ->
                Box(
                    Modifier
                        .at(EM_W - 6.4f - 3.4f, EM_H - 1.3f - 3.4f * 4f / 3f, em)
                        .size(em * 3.4f, em * 3.4f * 4f / 3f)
                        .graphicsLayer {
                            alpha = 0.22f * p.develop
                            blendMode = if (pal.tone == CardTone.DARK) BlendMode.Screen else BlendMode.Multiply
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.radialGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent, radius = min(size.width, size.height) / 2f), blendMode = BlendMode.DstIn)
                        },
                ) {
                    AsyncImage(
                        model = url, contentDescription = null, contentScale = ContentScale.Crop,
                        // grayscale(1) contrast(1.3)
                        colorFilter = ColorFilter.colorMatrix(saturationContrast(0f, 1.3f)),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            SerialText(card.serial, pal, em, p)
        }

        IdMode.BEAST -> SerialText(card.serial, pal, em, p)

        IdMode.PROTOGEN -> Text(
            "SYS.OK ▮▮▮▮▯ // VLK.REG // ${card.serial.drop(3)}",
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontFamily = CardMono, fontWeight = FontWeight.Medium, fontSize = emSp(em, 0.5f, density), letterSpacing = emSp(em, 0.1f, density), color = pal.muted),
            modifier = Modifier.at(12.2f, EM_H - 1f - 0.7f, em).graphicsLayer { alpha = 0.85f }.typeReveal(p.serial),
        )
    }

    // Штамп с датой регистрации
    val stampLabel = stringResource(if (mode == IdMode.PROTOGEN) R.string.idcard_stamp_qc else R.string.idcard_stamp).uppercase()
    val stampWord = stringResource(R.string.idcard_stamp).uppercase()
    val ring = if (mode == IdMode.BEAST) "VISORLINK ✦ $stampWord ✦ " else "VISORLINK ★ $stampWord ★ "
    val shape = stampShape(mode, d.stampStyle, small = false)
    val (sx, sy) = when (shape) {
        StampShape.ROUND -> 7.5f to 12.9f
        StampShape.OVAL, StampShape.RECT -> 6.6f to 14.4f
        StampShape.HEX -> 13.4f to 15.5f
    }
    Stamp(shape, mode, StampText(ring, stampLabel, registered), pal, d, em, sx, sy, d.stampRot.toFloat(), p, glow = mode == IdMode.PROTOGEN)

    // Голограмма — всегда в правом нижнем углу, никогда поверх фото
    if (traits.foil != IdFoil.NONE) {
        val tiltSpot = d.holoSpot == IdCardGenerator.HoloSpot.CORNER_TILT
        val (right, bottom) = when {
            mode == IdMode.PROTOGEN && tiltSpot -> 1.8f to 2.1f
            mode == IdMode.PROTOGEN -> 1.5f to 1.9f
            tiltSpot -> 1.6f to 1.6f
            else -> 1.3f to 1.3f
        }
        Holo(
            mode, traits, holoShape, 4.4f,
            Modifier
                .at(EM_W - right - 4.4f, EM_H - bottom - 4.4f, em)
                .graphicsLayer {
                    rotationZ = if (tiltSpot) -9f else 0f
                    alpha = p.holo
                    val s = 1.35f + (1f - 1.35f) * p.holo
                    scaleX = s; scaleY = s
                },
            em, p,
        )
    }

    // Наклейка из эмодзи профиля (до 2 символов)
    val sticker = remember(emojis) { emojis.codePointsTake(2) }
    if (sticker.isNotEmpty()) {
        Text(
            sticker,
            fontSize = emSp(em, 1.15f, density),
            lineHeight = emSp(em, 1.38f, density),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = -em * 1.2f, y = em * 3.3f)
                .graphicsLayer { rotationZ = 9f; scaleX = p.sticker; scaleY = p.sticker }
                .background(Color.White, IdCardMaterials.corner(em * 0.5f))
                .padding(horizontal = em * 0.3f, vertical = em * 0.15f),
        )
    }

    // Линия цвета профиля
    if (accent != null) {
        if (mode == IdMode.PROTOGEN) {
            Box(Modifier.at(1.6f, 2.2f, em).size(em * 5.5f, em * 0.12f).background(accent))
        } else {
            Box(Modifier.fillMaxWidth().at(0f, 2.7f, em).height(em * 0.16f).background(accent))
        }
    }
}

private fun String.codePointsTake(n: Int): String {
    // Array.from(str) в JS делит строку по кодовым точкам
    val sb = StringBuilder()
    var i = 0
    var count = 0
    while (i < length && count < n) {
        val cp = codePointAt(i)
        sb.appendCodePoint(cp)
        i += Character.charCount(cp)
        count++
    }
    return sb.toString()
}

/** «Печать» слева направо: клип по доле ширины. */
private fun Modifier.typeReveal(fraction: Float): Modifier =
    if (fraction >= 1f) this else this.drawWithContent {
        clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() }
    }

@Composable
private fun BoxScope.PrintLayer(mode: IdMode, d: IdCardGenerator.Details, seed: Long, pal: CardPalette, em: Dp, faint: Boolean, p: IssueProgress) {
    val guilloche = remember(d) { if (mode == IdMode.STANDARD) GuillocheArt(d.guilloche, d.waves) else null }
    val circuits = remember(seed, mode) { if (mode == IdMode.PROTOGEN) CircuitsArt(seed) else null }
    val coat = remember(seed, mode, d.coat) { if (mode == IdMode.BEAST) CoatArt(seed, d.coat) else null }
    Canvas(Modifier.matchParentSize().graphicsLayer { alpha = (if (faint) 0.45f else 1f) * p.print }) {
        val emPx = em.toPx()
        // .idc-art: left −2%, top −2%, 104 × 104 %, плюс сдвиг печати (misprint, em)
        translate(-size.width * 0.02f + d.misprint.x.toFloat() * emPx, -size.height * 0.02f + d.misprint.y.toFloat() * emPx) {
            scale(size.width * 1.04f / ART_W, size.height * 1.04f / ART_H, pivot = Offset.Zero) {
                guilloche?.draw(this, pal.line, pal.line2, p.draw)
                circuits?.draw(this, pal.line, p.draw)
                coat?.draw(this, pal.line2)
            }
        }
    }
}

@Composable
private fun Header(mode: IdMode, pal: CardPalette, em: Dp, p: IssueProgress) {
    val density = LocalDensity.current
    val height = if (mode == IdMode.PROTOGEN) 2.5f else 2.7f
    val color = when (mode) {
        IdMode.STANDARD -> pal.bandInk
        IdMode.BEAST -> pal.band
        IdMode.PROTOGEN -> pal.neon
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(em * height)
            .graphicsLayer { alpha = p.head }
            .then(
                when (mode) {
                    IdMode.STANDARD -> Modifier.background(Brush.horizontalGradient(listOf(pal.band, mix(pal.band, 80f, null))))
                    IdMode.BEAST -> Modifier
                        .background(Brush.horizontalGradient(0f to mix(pal.band, 22f, null), 0.85f to Color.Transparent))
                        .drawBehind { drawLine(mix(pal.band, 40f, null), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }
                    IdMode.PROTOGEN -> Modifier
                },
            )
            .padding(start = em * (if (mode == IdMode.PROTOGEN) 1.6f else 1.2f), end = em * 1.2f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(em * 0.6f),
    ) {
        Text(
            "VISORLINK",
            maxLines = 1,
            style = if (mode == IdMode.PROTOGEN) {
                TextStyle(fontFamily = CardMono, fontWeight = FontWeight.Medium, fontSize = emSp(em, 1.15f, density), letterSpacing = emSp(em, 0.276f, density), color = color, shadow = Shadow(color.copy(alpha = 0.7f), blurRadius = with(density) { (em * 0.6f).toPx() }))
            } else {
                TextStyle(fontFamily = CardBrand, fontWeight = FontWeight.Bold, fontSize = emSp(em, 1.15f, density), letterSpacing = emSp(em, 0.207f, density), color = color)
            },
        )
        Text(
            if (mode == IdMode.PROTOGEN) "// " + stringResource(R.string.idcard_protogen_doc) else stringResource(R.string.idcard_title).uppercase(),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style = TextStyle(
                fontFamily = if (mode == IdMode.PROTOGEN) CardMono else CardInter,
                fontWeight = FontWeight.SemiBold,
                fontSize = emSp(em, 0.62f, density),
                letterSpacing = emSp(em, if (mode == IdMode.PROTOGEN) 0.062f else 0.087f, density),
                color = if (mode == IdMode.PROTOGEN) pal.muted else color.copy(alpha = color.alpha * 0.85f),
            ),
            modifier = Modifier.weight(1f, fill = mode == IdMode.STANDARD),
        )
        if (mode != IdMode.STANDARD) {
            val badgeShape = IdCardMaterials.corner(if (mode == IdMode.PROTOGEN) em * 0.2f else em * 99f)
            Row(
                Modifier
                    .then(if (mode == IdMode.PROTOGEN) Modifier.background(mix(pal.neon, 12f, null), badgeShape) else Modifier)
                    .border(1.dp, color.copy(alpha = 0.6f), badgeShape)
                    .padding(horizontal = em * 0.6f, vertical = em * 0.25f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(em * 0.35f),
            ) {
                Canvas(Modifier.size(em * 1.3f)) { drawModeGlyph(mode, color) }
                Text(
                    modeLabel(mode).uppercase(),
                    style = TextStyle(fontFamily = CardMono, fontWeight = FontWeight.Medium, fontSize = emSp(em, 0.6f, density), letterSpacing = emSp(em, 0.072f, density), color = color),
                )
            }
        }
    }
}

@Composable
private fun Photo(mode: IdMode, person: IdCardPerson, name: String, pal: CardPalette, em: Dp, tiltDeg: Float, p: IssueProgress) {
    val density = LocalDensity.current
    val (left, top, w) = when (mode) {
        IdMode.PROTOGEN -> Triple(1.6f, 3.5f, 9.2f)
        else -> Triple(1.2f, 3.8f, 9.8f)
    }
    val h = w * 4f / 3f
    val shape: Shape = when (mode) {
        IdMode.STANDARD -> IdCardMaterials.corner(em * 0.35f)
        IdMode.BEAST -> IdCardMaterials.corners(topStart = em * 4.9f, topEnd = em * 4.9f, bottomEnd = em * 0.45f, bottomStart = em * 0.45f)
        IdMode.PROTOGEN -> GenericShape { s, _ ->
            moveTo(0f, 0f); lineTo(s.width * 0.84f, 0f); lineTo(s.width, s.height * 0.09f)
            lineTo(s.width, s.height); lineTo(s.width * 0.16f, s.height); lineTo(0f, s.height * 0.91f); close()
        }
    }
    Box(Modifier.at(left, top, em).size(em * w, em * h).graphicsLayer { rotationZ = tiltDeg }) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (mode == IdMode.BEAST) Modifier.drawBehind {
                        // Арочная рамка: два кольца цвета полосы
                        val o = shape.createOutline(size, layoutDirection, this)
                        val path = Path().apply { addOutline(o) }
                        drawPath(path, mix(pal.band, 16f, null), style = Stroke((em * 0.68f).toPx()))
                        drawPath(path, mix(pal.band, 60f, null), style = Stroke((em * 0.28f).toPx()))
                    } else Modifier,
                )
                .clip(shape)
                .background(pal.photoBg)
                .then(if (mode == IdMode.STANDARD) Modifier.border(1.dp, pal.edge, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val develop = p.develop
            if (person.avatarUrl != null) {
                // Standard: saturate(0.85) contrast(1.03); при проявлении — из ч/б
                val saturation = (if (mode == IdMode.STANDARD) 0.85f else 1f) * develop
                val contrast = if (mode == IdMode.STANDARD) 1.03f else 1f
                AsyncImage(
                    model = person.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (saturation < 1f || contrast != 1f) ColorFilter.colorMatrix(saturationContrast(saturation, contrast)) else null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.2f + 0.8f * develop },
                )
            } else {
                Text(
                    name.take(1).uppercase(),
                    style = TextStyle(fontFamily = CardInter, fontWeight = FontWeight.Bold, fontSize = emSp(em, 3f, density), color = pal.muted),
                    modifier = Modifier.graphicsLayer { alpha = 0.2f + 0.8f * develop },
                )
            }
            if (mode == IdMode.PROTOGEN) {
                // Сканлайны и неоновый отсвет поверх фото
                Canvas(Modifier.matchParentSize()) {
                    val step = (em * 0.24f).toPx()
                    val line = (em * 0.05f).toPx()
                    var y = 0f
                    while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.1f), Offset(0f, y), Size(size.width, line)); y += step }
                    drawRect(cssLinear(160f, listOf(0f to mix(pal.neon, 20f, null), 0.55f to Color.Transparent), size))
                }
            }
        }
        if (mode == IdMode.PROTOGEN) {
            Canvas(
                Modifier
                    .offset(-em * 0.45f, -em * 0.45f)
                    .size(em * (w + 0.9f), em * (h + 0.9f))
                    .graphicsLayer { alpha = p.brackets },
            ) {
                val sx = size.width / 100f
                val sy = size.height / 100f
                val path = Path().apply {
                    moveTo(0f, 14 * sy); lineTo(0f, 0f); lineTo(14 * sx, 0f)
                    moveTo(86 * sx, 0f); lineTo(100 * sx, 0f); lineTo(100 * sx, 14 * sy)
                    moveTo(100 * sx, 86 * sy); lineTo(100 * sx, 100 * sy); lineTo(86 * sx, 100 * sy)
                    moveTo(14 * sx, 100 * sy); lineTo(0f, 100 * sy); lineTo(0f, 86 * sy)
                }
                drawPath(path, pal.neon, style = Stroke(2f * density.density))
            }
        }
    }
}

@Composable
private fun LedVisor(face: Int, pal: CardPalette, em: Dp, p: IssueProgress) {
    val dots = remember(face) { ledDots(face) }
    val shape = IdCardMaterials.corners(topStart = em * 1.15f, topEnd = em * 1.15f, bottomEnd = em * 1.8f, bottomStart = em * 1.8f)
    Box(
        Modifier
            .at(1.6f, 16.5f, em)
            .size(em * 9.2f, em * 3.4f)
            .graphicsLayer { alpha = p.led }
            .drawBehind {
                drawIntoCanvasCompat { c ->
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.TRANSPARENT
                        setShadowLayer((em * 1.2f).toPx(), 0f, 0f, mix(pal.neon, 16f, null).toArgb())
                    }
                    val o = shape.createOutline(size, layoutDirection, this)
                    c.drawPath(Path().apply { addOutline(o) }.asAndroidPath(), paint)
                }
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(IdCardMaterials.LedTop, IdCardMaterials.Void)))
            .border(1.dp, mix(pal.neon, 40f, null), shape)
            .padding(horizontal = em * 0.5f, vertical = em * 0.4f),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cw = size.width / 30f
            val ch = size.height / 10f
            // Фон-матрица: тусклые точки в каждой ячейке
            for (y in 0 until 10) for (x in 0 until 30) {
                drawCircle(Color.White.copy(alpha = 0.08f), min(cw, ch) * 0.29f, Offset((x + 0.5f) * cw, (y + 0.5f) * ch))
            }
            val boot = p.ledBoot
            if (boot <= 0f) return@Canvas
            for (dot in dots) {
                val topLeft = Offset((dot.x + 0.12f) * cw, (dot.y + 0.12f) * ch)
                val s = Size(0.76f * cw, 0.76f * ch)
                drawRect(pal.neon.copy(alpha = 0.35f * boot), topLeft - Offset(cw * 0.15f, ch * 0.15f), Size(s.width + cw * 0.3f, s.height + ch * 0.3f))
                drawRect(pal.neon.copy(alpha = boot), topLeft, s)
            }
        }
    }
}

@Composable
private fun Fields(card: IdCard, name: String, username: String, registered: String, issued: String, pal: CardPalette, em: Dp, p: IssueProgress) {
    val density = LocalDensity.current
    val mode = card.mode
    val protogen = mode == IdMode.PROTOGEN
    val dtStyle = TextStyle(
        fontFamily = if (protogen) CardMono else CardInter,
        fontWeight = if (protogen) FontWeight.Medium else FontWeight.SemiBold,
        fontSize = emSp(em, 0.56f, density),
        lineHeight = emSp(em, 0.672f, density),
        letterSpacing = emSp(em, if (protogen) 0.045f else 0.067f, density),
        color = if (protogen) pal.neon.copy(alpha = pal.neon.alpha * 0.8f) else pal.muted,
    )

    @Composable
    fun Field(label: String, value: String, i: Int, kind: String, modifier: Modifier = Modifier) {
        Column(modifier.typeReveal(p.type(i))) {
            Text((if (protogen) "▸ " else "") + label.uppercase(), maxLines = 1, style = dtStyle)
            Spacer(Modifier.height(em * 0.14f))
            val base = when (kind) {
                "name" -> TextStyle(fontFamily = CardInter, fontWeight = FontWeight.Bold, fontSize = emSp(em, 1.32f, density), lineHeight = emSp(em, 1.58f, density))
                "mono" -> TextStyle(fontFamily = CardMono, fontWeight = FontWeight.Medium, fontSize = emSp(em, 1.05f, density), lineHeight = emSp(em, 1.31f, density))
                "half" -> TextStyle(fontFamily = CardMono, fontWeight = FontWeight.Medium, fontSize = emSp(em, 0.966f, density), lineHeight = emSp(em, 1.2f, density))
                "sn" -> TextStyle(fontFamily = CardMono, fontWeight = FontWeight.Medium, fontSize = emSp(em, 1.05f, density), lineHeight = emSp(em, 1.31f, density), letterSpacing = emSp(em, 0.147f, density), color = pal.neon, shadow = Shadow(pal.neon.copy(alpha = 0.55f), blurRadius = with(density) { (em * 0.5f).toPx() }))
                else -> TextStyle(fontFamily = CardInter, fontWeight = FontWeight.SemiBold, fontSize = emSp(em, 1.05f, density), lineHeight = emSp(em, 1.31f, density))
            }
            val color = when {
                kind == "sn" -> pal.neon
                kind == "species" && mode == IdMode.BEAST -> pal.band
                else -> pal.ink
            }
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, style = base.copy(color = color))
        }
    }

    Column(
        Modifier
            .at(if (protogen) 12.2f else 12.4f, if (protogen) 3.5f else 3.7f, em)
            .width(em * (EM_W - (if (protogen) 12.2f else 12.4f) - 1.2f)),
        verticalArrangement = Arrangement.spacedBy(em * (if (protogen) 0.5f else 0.6f)),
    ) {
        Field(stringResource(R.string.idcard_field_name), name, 0, "name")
        Field(stringResource(R.string.idcard_field_username), "@$username", 1, "mono")
        if (mode != IdMode.STANDARD && !card.species.isNullOrBlank()) {
            Field(stringResource(if (protogen) R.string.idcard_field_model else R.string.idcard_field_species), card.species, 2, "species")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(em * 1f)) {
            Field(stringResource(R.string.idcard_field_registered), registered, 3, "half", Modifier.weight(1f))
            Field(stringResource(R.string.idcard_field_issued), issued, 4, "half", Modifier.weight(1f))
        }
        if (protogen) Field(stringResource(R.string.idcard_field_serial), card.serial, 5, "sn")
    }
}

/**
 * Подпись в viewBox 100×30, вписанная с сохранением пропорций: на лице — по центру
 * (`xMidYMid meet`), на обороте — прижата влево (`xMinYMid meet`). Каждый штрих — отдельный
 * `M`; линия 1.2, скруглённые концы и стыки.
 */
@Composable
private fun SignatureCanvas(strokes: List<List<Pair<Double, Double>>>, color: Color, alignStart: Boolean, modifier: Modifier) {
    val path = remember(strokes) {
        Path().apply {
            strokes.forEach { stroke ->
                stroke.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat()) }
            }
        }
    }
    Canvas(modifier) {
        val s = min(size.width / 100f, size.height / 30f)
        val dx = if (alignStart) 0f else (size.width - 100f * s) / 2f
        translate(dx, (size.height - 30f * s) / 2f) {
            scale(s, s, pivot = Offset.Zero) {
                drawPath(path, color, style = Stroke(1.2f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

/** CSS `saturate(s) contrast(c)`: сначала насыщенность, затем контраст вокруг середины. */
private fun saturationContrast(saturation: Float, contrast: Float): ColorMatrix {
    val m = ColorMatrix().apply { setToSaturation(saturation) }
    val t = 127.5f * (1f - contrast)
    for (row in 0 until 3) {
        for (col in 0 until 4) m[row, col] = m[row, col] * contrast
        m[row, 4] = m[row, 4] * contrast + t
    }
    return m
}

@Composable
private fun SerialText(serial: String, pal: CardPalette, em: Dp, p: IssueProgress) {
    val density = LocalDensity.current
    Text(
        serial,
        maxLines = 1,
        style = TextStyle(fontFamily = CardMono, fontWeight = FontWeight.Medium, fontSize = emSp(em, 0.95f, density), letterSpacing = emSp(em, 0.114f, density), color = pal.muted),
        modifier = Modifier.at(1.2f, EM_H - 0.9f - 1.3f, em).typeReveal(p.serial),
    )
}

/**
 * Резиновая печать: неровная краска (текстура плиткой 7em), нажим с одной стороны
 * (градиент под углом stampPress), наложение multiply / screen.
 */
@Composable
private fun Stamp(
    shape: StampShape,
    mode: IdMode,
    text: StampText,
    pal: CardPalette,
    d: IdCardGenerator.Details,
    em: Dp,
    x: Float,
    y: Float,
    rot: Float,
    p: IssueProgress,
    glow: Boolean = false,
    animate: Boolean = true,
) {
    val context = LocalContext.current
    val fonts = remember(context) {
        StampFonts(
            text = ResourcesCompat.getFont(context, R.font.inter_bold) ?: android.graphics.Typeface.DEFAULT_BOLD,
            mono = ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium) ?: android.graphics.Typeface.MONOSPACE,
        )
    }
    val dark = pal.tone == CardTone.DARK
    val ink = (d.stampInk.toFloat() + if (dark) 0.12f else 0f).coerceAtMost(1f)
    val scale = if (animate) p.stampScale else 1f
    val inkProgress = if (animate) p.stampInk else 1f
    val place = Modifier
        .at(x, y, em)
        .size(em * shape.w, em * shape.h)
    if (glow) {
        // drop-shadow(0 0 .25em stamp 55%): неоновая печать Protogen светится (размытие — API 31+)
        Canvas(
            place
                .graphicsLayer {
                    translationX = (em * d.stampDx.toFloat()).toPx()
                    translationY = (em * d.stampDy.toFloat()).toPx()
                    rotationZ = rot
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.55f * ink * inkProgress
                }
                .blur(em * 0.25f, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
        ) {
            scale(size.width / shape.vbW, size.height / shape.vbH, pivot = Offset.Zero) {
                drawStamp(shape, mode, text, fonts, pal.stamp)
            }
        }
    }
    Canvas(
        place
            .graphicsLayer {
                translationX = (em * d.stampDx.toFloat()).toPx()
                translationY = (em * d.stampDy.toFloat()).toPx()
                rotationZ = rot
                scaleX = scale
                scaleY = scale
                alpha = ink * inkProgress
                blendMode = if (dark) BlendMode.Screen else BlendMode.Multiply
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        scale(size.width / shape.vbW, size.height / shape.vbH, pivot = Offset.Zero) {
            drawStamp(shape, mode, text, fonts, pal.stamp)
        }
        // Маска краски ∩ нажим
        drawRect(textureBrush(CardTextures.ink, (em * 7f).toPx()), blendMode = BlendMode.DstIn)
        drawRect(
            cssLinear(d.stampPress.toFloat(), listOf(0f to Color.Black, 0.3f to Color.Black, 1f to Color.Black.copy(alpha = 0.45f)), size),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** Голограмма: форма-маска, радужная фольга (сдвигается при наклоне), блик и тиснёная эмблема. */
@Composable
private fun Holo(mode: IdMode, traits: IdCardTraits, shape: IdCardGenerator.HoloShape, sizeEm: Float, modifier: Modifier, em: Dp, p: IssueProgress, tilt: CardTiltState? = null) {
    val context = LocalContext.current
    val mask = remember(shape) { holoShapePath(shape) }
    val brand = remember(context) { ResourcesCompat.getFont(context, R.font.space_grotesk_semibold) }
    val tiltState = tilt ?: LocalCardTilt.current
    Canvas(modifier.size(em * sizeEm)) {
        val s = size.width / 100f
        val clip = Path().apply { addPath(mask) }.also { it.transform(androidx.compose.ui.graphics.Matrix().apply { scale(s, s) }) }
        clipPath(clip) {
            val box = Size(size.width * 3f, size.height * 3f)
            // Золото — всегда золотой фон, даже у галактики (правило золота в CSS идёт позже)
            when {
                traits.finish == IdFinish.GOLD -> drawRect(cssLinear(135f, IdCardMaterials.HoloGold, size))
                traits.foil == IdFoil.GALAXY -> drawRect(Brush.radialGradient(colorStops = IdCardMaterials.HoloGalaxy.toTypedArray(), center = Offset(size.width * 0.32f, size.height * 0.3f), radius = size.width * 0.75f))
                else -> drawRect(cssLinear(135f, IdCardMaterials.HoloSilver, size))
            }
            val fx = tiltState?.fx ?: 0f
            val fy = tiltState?.fy ?: 0f
            val sweep = 1f - p.holoSweep
            val rainbowOrigin = Offset(-size.width + (-fx * 0.16f + 0.3f * sweep) * box.width, -size.height + (-fy * 0.13f + 0.22f * sweep) * box.height)
            val (stops, alpha) = when (traits.foil) {
                IdFoil.AURORA -> IdCardMaterials.RainbowAurora to 0.85f
                IdFoil.GALAXY -> IdCardMaterials.RainbowGalaxy to 0.5f
                else -> IdCardMaterials.RainbowClassic to (if (traits.foil == IdFoil.PRISM) 0.85f else 0.32f)
            }
            drawRect(cssRepeating(118f, stops, box, rainbowOrigin), alpha = alpha)
            if (traits.foil == IdFoil.GALAXY) {
                // Звёзды — две сетки, 1.1em и 1.4em
                for ((stepEm, r, a) in listOf(Triple(1.1f, 0.05f, 1f), Triple(1.4f, 0.04f, 0.7f))) {
                    val step = (em * stepEm).toPx()
                    var yy = 0f
                    while (yy < size.height) {
                        var xx = 0f
                        while (xx < size.width) { drawCircle(Color.White.copy(alpha = a), (em * r).toPx(), Offset(xx + step / 2, yy + step / 2)); xx += step }
                        yy += step
                    }
                }
            }
            val glintOrigin = Offset(-size.width - fx * 0.34f * box.width, -size.height + fy * 0.26f * box.height)
            drawRect(cssLinear(115f, listOf(0f to Color.Transparent, 0.44f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.8f), 0.56f to Color.Transparent, 1f to Color.Transparent), box, glintOrigin))
            scale(s, s, pivot = Offset.Zero) { drawHoloEmblem(mode, brand) }
        }
    }
}

internal val LocalCardTilt = androidx.compose.runtime.staticCompositionLocalOf<CardTiltState?> { null }

// ── Оборот ──

@Composable
private fun BoxScope.BackContent(card: IdCard, person: IdCardPerson, name: String, pal: CardPalette, d: IdCardGenerator.Details, em: Dp, p: IssueProgress, strokes: List<List<Pair<Double, Double>>>) {
    val density = LocalDensity.current
    val mode = card.mode
    val traits = card.traits
    val protogen = mode == IdMode.PROTOGEN
    val issued = formatCardDate(card.issuedAt)

    if (mode != IdMode.STANDARD) PrintLayer(mode, d, d.artSeed xor 0xBAC, pal, em, faint = true, p = p)

    // Магнитная полоса
    Box(
        Modifier
            .fillMaxWidth()
            .at(0f, 2f, em)
            .height(em * 3.6f)
            .background(
                when (mode) {
                    IdMode.PROTOGEN -> Brush.horizontalGradient(0f to IdCardMaterials.Void, 0.7f to mix(pal.neon, 24f, IdCardMaterials.Void), 1f to IdCardMaterials.Void)
                    IdMode.BEAST -> Brush.verticalGradient(IdCardMaterials.StripeBeast)
                    IdMode.STANDARD -> Brush.verticalGradient(colorStops = IdCardMaterials.StripeStandard.toTypedArray())
                },
            )
            .drawBehind {
                if (protogen) {
                    drawLine(mix(pal.neon, 45f, null), Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f)
                    drawLine(mix(pal.neon, 45f, null), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
                }
                if (mode == IdMode.BEAST) drawLine(mix(pal.band, 40f, null), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
            },
    )

    // Штрихкод и примечание
    Column(Modifier.at(1.2f, 6.6f, em).width(em * 19.5f), verticalArrangement = Arrangement.spacedBy(em * 0.6f)) {
        val bars = remember(card.serial) { IdCardGenerator.barcodeBars(card.serial) }
        val barShape = if (protogen) RectangleShape else IdCardMaterials.corner(em * 0.25f)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(em * 2.6f)
                .clip(barShape)
                .background(if (protogen) Color.Transparent else Color.White)
                .then(if (protogen) Modifier.border(1.dp, pal.line, barShape) else Modifier)
                .padding(horizontal = em * 0.5f, vertical = em * 0.3f),
        ) {
            val total = bars.sumOf { it.first }.toFloat()
            val k = size.width / total
            var x = 0f
            val fill = if (protogen) pal.neon else IdCardMaterials.BarcodeInk
            for ((w, on) in bars) {
                if (on) drawRect(fill, Offset(x * k, 0f), Size(w * k, size.height))
                x += w
            }
        }
        Text(
            stringResource(R.string.idcard_back_note),
            style = TextStyle(fontFamily = CardInter, fontSize = emSp(em, 0.62f, density), lineHeight = emSp(em, 0.9f, density), color = pal.muted),
        )
    }

    // Тираж: мин. высота 4.4em, мини-голограмма прижата к правому нижнему углу блока
    val edition = card.edition
    Box(
        Modifier
            .at(EM_W - 1.2f - 13.4f, 6.6f, em)
            .width(em * 13.4f)
            .heightIn(min = em * 4.4f)
            .drawBehind {
                val r = if (protogen) 0f else (em * 0.5f).toPx()
                drawRoundRect(pal.line, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(2f))
            },
    ) {
        Column(
            Modifier.padding(horizontal = em * 0.8f, vertical = em * 0.7f),
            verticalArrangement = Arrangement.spacedBy(em * 0.3f),
        ) {
            Text(
                stringResource(R.string.idcard_edition_label).uppercase(),
                style = TextStyle(fontFamily = CardInter, fontWeight = FontWeight.SemiBold, fontSize = emSp(em, 0.5f, density), letterSpacing = emSp(em, 0.08f, density), color = pal.muted),
            )
            val editionColor = editionColor(edition, pal.tone)
            Text(
                editionName(edition).uppercase(),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    fontFamily = CardInter, fontWeight = FontWeight.Bold, fontSize = emSp(em, 0.92f, density), letterSpacing = emSp(em, 0.055f, density),
                    color = editionColor ?: pal.ink,
                ).let { if (edition == IdEdition.LEGENDARY) it.copy(brush = Brush.linearGradient(IdCardMaterials.Legendary)) else it },
            )
            // Надпись кастомного скина (§2.3): бренд 600 0.62em, 0.04em, ink, свечение neon 45 %, перенос по любым символам
            card.custom?.labelText?.let { label ->
                Text(
                    label.toCharArray().joinToString("\u200B"),
                    style = TextStyle(
                        fontFamily = CardBrand, fontWeight = FontWeight.SemiBold, fontSize = emSp(em, 0.62f, density),
                        letterSpacing = emSp(em, 0.025f, density), color = pal.ink,
                        shadow = Shadow(pal.neon.copy(alpha = pal.neon.alpha * 0.45f), blurRadius = with(density) { (em * 0.6f).toPx() }),
                    ),
                    modifier = Modifier.fillMaxWidth(0.68f),
                )
            }
            Text(
                finishName(traits.finish) + " · " + foilName(traits.foil) + if (protogen) " · FW ${card.version}.${traits.seed % 10}" else "",
                style = TextStyle(fontFamily = CardMono, fontWeight = FontWeight.Medium, fontSize = emSp(em, 0.56f, density), lineHeight = emSp(em, 0.73f, density), color = pal.muted),
                modifier = Modifier.fillMaxWidth(0.68f),
            )
        }
        if (traits.foil != IdFoil.NONE) {
            Holo(mode, traits, card.custom?.holo ?: d.holoShape, 2.4f, Modifier.align(Alignment.BottomEnd).offset(x = -em * 0.6f, y = -em * 0.6f), em, p)
        }
    }

    // Полоса подписи (у Protogen — ключ подлинности)
    Column(Modifier.at(1.2f, 12.4f, em).width(em * 19.5f)) {
        Text(
            stringResource(if (protogen) R.string.idcard_field_auth_key else R.string.idcard_field_signature).uppercase(),
            style = TextStyle(fontFamily = CardInter, fontWeight = FontWeight.SemiBold, fontSize = emSp(em, 0.5f, density), letterSpacing = emSp(em, 0.07f, density), color = pal.muted),
        )
        Spacer(Modifier.height(em * 0.25f))
        val stripShape = if (protogen) RectangleShape else IdCardMaterials.corner(em * 0.25f)
        val stripInk = when (mode) {
            IdMode.BEAST -> IdCardMaterials.SignatureInkBeast
            IdMode.PROTOGEN -> pal.neon
            IdMode.STANDARD -> hsl(226f, 62f, 30f)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(em * 3f)
                .clip(stripShape)
                .drawBehind {
                    if (protogen) {
                        drawRect(mix(pal.neon, 7f, IdCardMaterials.ProtogenStrip))
                        drawRect(pal.line, style = Stroke(2f))
                    } else {
                        drawRect(stripes(-35f, IdCardMaterials.SignaturePaper, IdCardMaterials.SignaturePaper2, (em * 0.7f).toPx()))
                    }
                }
                .padding(horizontal = em * 0.7f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(em * 0.6f),
        ) {
            if (protogen) {
                Text(
                    IdCardGenerator.authKey(traits.seed),
                    maxLines = 1,
                    style = TextStyle(fontFamily = CardMono, fontWeight = FontWeight.Medium, fontSize = emSp(em, 0.82f, density), letterSpacing = emSp(em, 0.066f, density), color = stripInk, shadow = Shadow(stripInk.copy(alpha = 0.5f), blurRadius = with(density) { (em * 0.4f).toPx() })),
                )
            } else {
                SignatureCanvas(strokes, stripInk, alignStart = true, Modifier.weight(1f).height(em * 2.6f))
                if (mode == IdMode.BEAST) {
                    Canvas(Modifier.size(em * 1.8f).graphicsLayer { rotationZ = -14f; alpha = 0.85f }) { drawModeGlyph(IdMode.BEAST, stripInk) }
                }
            }
        }
    }

    // Второй штамп «Проверено»
    if (d.secondStamp) {
        Stamp(
            StampShape.RECT, mode, StampText("", stringResource(R.string.idcard_stamp_verified).uppercase(), issued), pal, d, em,
            EM_W - 9.5f - StampShape.RECT.w, 12.2f, d.secondRot.toFloat(), p, glow = protogen, animate = false,
        )
    }

    // MRZ
    val lines = remember(card, person, name) {
        IdCardGenerator.mrzLines(card.serial, mode, person.username ?: "user", name, card.registeredAt, card.issuedAt)
    }
    Column(Modifier.align(Alignment.BottomStart).padding(start = em * 1.2f, end = em * 1.2f, bottom = em * 0.8f)) {
        lines.forEach {
            Text(
                it,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = TextStyle(fontFamily = CardMono, fontWeight = FontWeight.Medium, fontSize = emSp(em, 0.78f, density), lineHeight = emSp(em, 1.05f, density), letterSpacing = emSp(em, 0.125f, density), color = if (protogen) pal.neon.copy(alpha = 0.75f) else pal.ink.copy(alpha = 0.85f)),
            )
        }
    }
}

@Composable
internal fun editionName(e: IdEdition) = stringResource(
    when (e) {
        IdEdition.COMMON -> R.string.idcard_edition_common
        IdEdition.UNCOMMON -> R.string.idcard_edition_uncommon
        IdEdition.RARE -> R.string.idcard_edition_rare
        IdEdition.EPIC -> R.string.idcard_edition_epic
        IdEdition.LEGENDARY -> R.string.idcard_edition_legendary
    },
)

@Composable
internal fun finishName(f: IdFinish) = stringResource(
    when (f) {
        IdFinish.BASE -> R.string.idcard_finish_base
        IdFinish.PEARL -> R.string.idcard_finish_pearl
        IdFinish.METALLIC -> R.string.idcard_finish_metallic
        IdFinish.OBSIDIAN -> R.string.idcard_finish_obsidian
        IdFinish.GOLD -> R.string.idcard_finish_gold
    },
)

@Composable
internal fun foilName(f: IdFoil) = stringResource(
    when (f) {
        IdFoil.NONE -> R.string.idcard_foil_none
        IdFoil.CLASSIC -> R.string.idcard_foil_classic
        IdFoil.PRISM -> R.string.idcard_foil_prism
        IdFoil.AURORA -> R.string.idcard_foil_aurora
        IdFoil.GALAXY -> R.string.idcard_foil_galaxy
    },
)

private fun editionColor(e: IdEdition, tone: CardTone): Color? = when (e) {
    IdEdition.UNCOMMON -> if (tone == CardTone.LIGHT) hsl(150f, 60f, 28f) else hsl(150f, 70f, 66f)
    IdEdition.RARE -> if (tone == CardTone.LIGHT) hsl(212f, 70f, 38f) else hsl(212f, 90f, 72f)
    IdEdition.EPIC -> if (tone == CardTone.LIGHT) hsl(276f, 55f, 42f) else hsl(276f, 85f, 78f)
    else -> null
}

/** Значок режима (визор / лапа) — у имени в шапке ЛС и в профиле. У Standard значка нет. */
@Composable
fun IdModeGlyph(mode: IdMode, modifier: Modifier = Modifier, size: Dp = 14.dp, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    if (mode == IdMode.STANDARD) return
    val label = modeLabel(mode)
    Canvas(modifier.size(size).semantics { contentDescription = label }) { drawModeGlyph(mode, color.copy(alpha = color.alpha * 0.85f)) }
}

/** Полосы под углом (repeating-linear-gradient из двух цветов поровну, период [periodPx]). */
private fun stripes(angleDeg: Float, a: Color, b: Color, periodPx: Float): Brush {
    val rad = angleDeg * PI.toFloat() / 180f
    val dir = Offset(kotlin.math.sin(rad), -cos(rad))
    return Brush.linearGradient(
        0f to a, 0.5f to a, 0.5f to b, 1f to b,
        start = Offset.Zero,
        end = dir * periodPx,
        tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
    )
}
