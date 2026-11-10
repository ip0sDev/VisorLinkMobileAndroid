// ui/aegis/AegisBody.kt
package org.visorlink.app.ui.aegis

import android.graphics.BlurMaskFilter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.visorlink.app.data.model.aegis.LinkAction
import org.visorlink.app.data.model.aegis.LinkEmotion
import org.visorlink.app.data.model.aegis.VisorIcon
import org.visorlink.app.ui.theme.VlTheme
import org.visorlink.app.utils.HapticType
import org.visorlink.app.utils.rememberHaptic
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Цвет свечения LED-визора и ядер брони в зависимости от эмоции и иконки.
 */
internal fun linkVisorColor(emotion: LinkEmotion, visorIcon: VisorIcon?): Color = when {
    visorIcon == VisorIcon.HEART -> Color(0xFFFF4D8D)
    emotion == LinkEmotion.OFFENDED || visorIcon == VisorIcon.ANGRY -> Color(0xFFFF453A)
    emotion == LinkEmotion.PARTY -> Color(0xFF2EE59D)
    emotion == LinkEmotion.HAPPY -> Color(0xFF35C7E8)
    emotion == LinkEmotion.CURIOUS -> Color(0xFF3DF2FF)
    emotion == LinkEmotion.CONFUSED || visorIcon == VisorIcon.QUESTION -> Color(0xFFFFB340)
    emotion == LinkEmotion.CONCERNED || visorIcon == VisorIcon.CROSS -> Color(0xFFFF6B4A)
    emotion == LinkEmotion.SAD -> Color(0xFF4A90FF)
    emotion == LinkEmotion.SLEEPY || visorIcon == VisorIcon.ZZZ -> Color(0xFFA76BFF)
    else -> Color(0xFF35C7E8)
}

/**
 * Анимированный маскот-протоген Линк.
 * Рисуется векторно на Canvas в единой координатной сетке 200×200 (масштабируется под любой размер
 * без разъезжания деталей): титановый шлем с изогнутым OLED-визором, боковые шарниры-коммуникаторы
 * с неоновыми кольцами (в стиле иконки VisorLink), пушистые кибер-уши с неоновыми прожилками,
 * меховой воротник, нагрудная броня с реактором, лапки, пушистый хвост и живая LED-мимика.
 */
@Composable
fun AnimatedProtogen(
    emotion: LinkEmotion,
    visorIcon: VisorIcon?,
    isPressed: Boolean,
    modifier: Modifier = Modifier,
    isPetting: Boolean = false
) {
    val reduceMotion = VlTheme.tokens.reduceMotion
    val targetColor = remember(emotion, visorIcon) { linkVisorColor(emotion, visorIcon) }
    val visorColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(420),
        label = "visor_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "protogen_life")
    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_phase"
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2f).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    var isBlinking by remember { mutableStateOf(false) }
    var earTwitch by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduceMotion, emotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2400, 5200))
            if (emotion != LinkEmotion.SLEEPY && visorIcon != VisorIcon.ZZZ) {
                isBlinking = true
                delay(130)
                isBlinking = false
                // Иногда двойное моргание или лёгкое подёргивание ухом
                if (Random.nextFloat() < 0.28f) {
                    delay(90)
                    isBlinking = true
                    delay(110)
                    isBlinking = false
                }
            }
            if (Random.nextFloat() < 0.45f) {
                earTwitch = if (Random.nextBoolean()) -6f else 6f
                delay(180)
                earTwitch = 0f
            }
        }
    }

    val targetLeftEar = when (emotion) {
        LinkEmotion.SAD, LinkEmotion.SLEEPY -> 28f
        LinkEmotion.CONCERNED -> 18f
        LinkEmotion.OFFENDED -> 24f
        LinkEmotion.CONFUSED -> -6f
        LinkEmotion.CURIOUS -> -12f
        LinkEmotion.HAPPY, LinkEmotion.PARTY -> -8f
        else -> 3f
    } + earTwitch

    val targetRightEar = when (emotion) {
        LinkEmotion.SAD, LinkEmotion.SLEEPY -> 28f
        LinkEmotion.CONCERNED -> 18f
        LinkEmotion.OFFENDED -> 24f
        LinkEmotion.CONFUSED -> 22f
        LinkEmotion.CURIOUS -> 8f
        LinkEmotion.HAPPY, LinkEmotion.PARTY -> -8f
        else -> 3f
    } - earTwitch * 0.5f

    val leftEarAngle by animateFloatAsState(
        targetValue = if (isPressed || isPetting) -14f else targetLeftEar,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 260f),
        label = "left_ear"
    )
    val rightEarAngle by animateFloatAsState(
        targetValue = if (isPressed || isPetting) -14f else targetRightEar,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 260f),
        label = "right_ear"
    )

    val targetHeadTilt = when {
        isPetting -> 5f
        emotion == LinkEmotion.CONFUSED -> -8f
        emotion == LinkEmotion.CURIOUS -> 5f
        emotion == LinkEmotion.SAD -> -3f
        else -> 0f
    }
    val headTilt by animateFloatAsState(
        targetValue = targetHeadTilt,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "head_tilt"
    )

    val squishX by animateFloatAsState(
        targetValue = if (isPressed) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 500f),
        label = "squish_x"
    )
    val squishY by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 500f),
        label = "squish_y"
    )

    val breath = if (reduceMotion) 0.5f else breathPhase
    val wave = if (reduceMotion) 0f else wavePhase

    Canvas(
        modifier = modifier
            .defaultMinSize(minWidth = 120.dp, minHeight = 120.dp)
            .graphicsLayer {
                scaleX = squishX
                scaleY = squishY
                transformOrigin = TransformOrigin(0.5f, 0.9f)
            }
    ) {
        val s = min(size.width, size.height) / 200f
        val ox = (size.width - 200f * s) / 2f
        val oy = (size.height - 200f * s) / 2f

        translate(ox, oy) {
            scale(s, s, pivot = Offset.Zero) {
                drawProtogenCharacter(
                    emotion = emotion,
                    visorIcon = visorIcon,
                    visorColor = visorColor,
                    isPressed = isPressed,
                    isPetting = isPetting,
                    isBlinking = isBlinking,
                    breath = breath,
                    wave = wave,
                    leftEarAngle = leftEarAngle,
                    rightEarAngle = rightEarAngle,
                    headTilt = headTilt
                )
            }
        }
    }
}

private fun DrawScope.drawProtogenCharacter(
    emotion: LinkEmotion,
    visorIcon: VisorIcon?,
    visorColor: Color,
    isPressed: Boolean,
    isPetting: Boolean,
    isBlinking: Boolean,
    breath: Float,
    wave: Float,
    leftEarAngle: Float,
    rightEarAngle: Float,
    headTilt: Float
) {
    val armorDark = Color(0xFF0E1520)
    val armorMid = Color(0xFF182334)
    val armorLight = Color(0xFF283850)
    val armorRim = Color(0xFF3E5474)
    val furDark = Color(0xFF101824)
    val furMid = Color(0xFF1B2738)
    val furLight = Color(0xFF2C3E57)
    val visorGlassTop = Color(0xFF08101A)
    val visorGlassBottom = Color(0xFF03060B)

    val bodyBob = (breath - 0.5f) * 2.4f
    val headBob = (breath - 0.5f) * 3.6f + if (emotion == LinkEmotion.PARTY) sin(wave * 2f) * 2.2f else 0f
    val wagSpeed = when {
        isPetting || emotion == LinkEmotion.PARTY || visorIcon == VisorIcon.HEART -> 3f
        emotion == LinkEmotion.HAPPY -> 2f
        emotion == LinkEmotion.SLEEPY || emotion == LinkEmotion.SAD -> 0.5f
        else -> 1f
    }
    val tailAngle = sin(wave * wagSpeed) * (if (isPetting || emotion == LinkEmotion.PARTY) 14f else 7f) +
        if (emotion == LinkEmotion.SAD || emotion == LinkEmotion.SLEEPY) 16f else 0f

    // ── 0. Мягкое фоновое свечение и тень под лапками ──
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(visorColor.copy(alpha = 0.20f + 0.06f * breath), Color.Transparent),
            center = Offset(100f, 96f),
            radius = 88f
        ),
        radius = 88f,
        center = Offset(100f, 96f)
    )
    drawOval(
        color = Color.Black.copy(alpha = 0.28f),
        topLeft = Offset(48f, 178f),
        size = Size(104f, 14f)
    )

    // ── 1. Пушистый хвост протогена с кибер-кольцом ──
    rotate(degrees = tailAngle, pivot = Offset(132f, 162f)) {
        val tailPath = Path().apply {
            moveTo(128f, 166f)
            cubicTo(154f, 166f, 182f, 148f, 184f, 122f)
            cubicTo(185f, 110f, 176f, 104f, 168f, 112f)
            lineTo(171f, 103f)
            cubicTo(160f, 106f, 150f, 118f, 146f, 130f)
            lineTo(143f, 122f)
            cubicTo(136f, 134f, 130f, 148f, 124f, 158f)
            close()
        }
        drawPath(
            path = tailPath,
            brush = Brush.linearGradient(
                colors = listOf(furMid, furDark),
                start = Offset(130f, 160f),
                end = Offset(184f, 110f)
            )
        )
        drawPath(
            path = tailPath,
            color = visorColor.copy(alpha = 0.45f),
            style = Stroke(width = 1.8f, join = StrokeJoin.Round)
        )
        // Кибер-кольцо у основания хвоста
        drawLine(
            color = visorColor.copy(alpha = 0.85f),
            start = Offset(142f, 141f),
            end = Offset(155f, 151f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }

    // ── 2. Торс и титановая нагрудная броня с реактором ──
    translate(left = 0f, top = bodyBob) {
        // Базовый контур корпуса
        val torsoPath = Path().apply {
            moveTo(62f, 126f)
            lineTo(138f, 126f)
            cubicTo(154f, 132f, 160f, 152f, 154f, 178f)
            lineTo(46f, 178f)
            cubicTo(40f, 152f, 46f, 132f, 62f, 126f)
            close()
        }
        drawPath(
            path = torsoPath,
            brush = Brush.verticalGradient(
                colors = listOf(armorMid, armorDark),
                startY = 126f,
                endY = 180f
            )
        )
        drawPath(
            path = torsoPath,
            color = armorRim,
            style = Stroke(width = 2f, join = StrokeJoin.Round)
        )

        // Плечевые щитки (наплечники) с неоновыми полосами
        val leftPauldron = Path().apply {
            moveTo(46f, 142f)
            cubicTo(48f, 130f, 58f, 126f, 68f, 128f)
            lineTo(62f, 158f)
            lineTo(45f, 154f)
            close()
        }
        val rightPauldron = Path().apply {
            moveTo(154f, 142f)
            cubicTo(152f, 130f, 142f, 126f, 132f, 128f)
            lineTo(138f, 158f)
            lineTo(155f, 154f)
            close()
        }
        drawPath(leftPauldron, armorLight)
        drawPath(rightPauldron, armorLight)
        drawLine(
            color = visorColor.copy(alpha = 0.85f),
            start = Offset(51f, 137f),
            end = Offset(59f, 151f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = visorColor.copy(alpha = 0.85f),
            start = Offset(149f, 137f),
            end = Offset(141f, 151f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round
        )

        // Центральная нагрудная пластина
        val chestPlate = Path().apply {
            moveTo(72f, 132f)
            lineTo(128f, 132f)
            lineTo(120f, 174f)
            lineTo(80f, 174f)
            close()
        }
        drawPath(chestPlate, Color(0xFF131D2B))
        drawPath(chestPlate, armorRim.copy(alpha = 0.8f), style = Stroke(width = 1.6f, join = StrokeJoin.Round))

        // Светящееся ядро-реактор на груди (шестигранник / ромб)
        val coreCenter = Offset(100f, 155f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(visorColor.copy(alpha = 0.45f + 0.2f * breath), Color.Transparent),
                center = coreCenter,
                radius = 18f
            ),
            radius = 18f,
            center = coreCenter
        )
        val coreHex = Path().apply {
            moveTo(100f, 144f)
            lineTo(110f, 150f)
            lineTo(110f, 160f)
            lineTo(100f, 166f)
            lineTo(90f, 160f)
            lineTo(90f, 150f)
            close()
        }
        drawPath(coreHex, Color(0xFF070C14))
        drawPath(coreHex, visorColor, style = Stroke(width = 2.2f, join = StrokeJoin.Round))
        drawCircle(
            color = visorColor,
            radius = 4.2f + 0.8f * breath,
            center = coreCenter
        )

        // ── 3. Пушистый меховой воротник (Шея/Грудь) ──
        val neckRuff = Path().apply {
            moveTo(54f, 122f)
            lineTo(44f, 130f)
            lineTo(56f, 135f)
            lineTo(50f, 144f)
            lineTo(68f, 141f)
            lineTo(76f, 149f)
            lineTo(90f, 142f)
            lineTo(100f, 151f)
            lineTo(110f, 142f)
            lineTo(124f, 149f)
            lineTo(132f, 141f)
            lineTo(150f, 144f)
            lineTo(144f, 135f)
            lineTo(156f, 130f)
            lineTo(146f, 122f)
            close()
        }
        drawPath(
            path = neckRuff,
            brush = Brush.verticalGradient(
                colors = listOf(furLight, furMid, furDark),
                startY = 120f,
                endY = 151f
            )
        )
        drawPath(
            path = neckRuff,
            color = armorRim.copy(alpha = 0.65f),
            style = Stroke(width = 1.6f, join = StrokeJoin.Round)
        )
    }

    // ── 4 & 5 & 6. Голова протогена: уши, боковые кисточки, титановый шлем и изогнутый LED-визор ──
    val headRotation = headTilt + if (isPetting) sin(wave * 3f) * 3f else 0f
    translate(left = 0f, top = headBob) {
        rotate(degrees = headRotation, pivot = Offset(100f, 94f)) {
            // Боковые меховые кисточки / малые плавники за щеками
            val leftTuft = Path().apply {
                moveTo(42f, 76f)
                lineTo(18f, 72f)
                lineTo(26f, 84f)
                lineTo(14f, 92f)
                lineTo(30f, 98f)
                lineTo(24f, 108f)
                lineTo(44f, 106f)
                close()
            }
            val rightTuft = Path().apply {
                moveTo(158f, 76f)
                lineTo(182f, 72f)
                lineTo(174f, 84f)
                lineTo(186f, 92f)
                lineTo(170f, 98f)
                lineTo(176f, 108f)
                lineTo(156f, 106f)
                close()
            }
            drawPath(leftTuft, furMid)
            drawPath(leftTuft, furLight, style = Stroke(width = 1.5f, join = StrokeJoin.Round))
            drawPath(rightTuft, furMid)
            drawPath(rightTuft, furLight, style = Stroke(width = 1.5f, join = StrokeJoin.Round))

            // ── Левое ухо протогена (с неоновым мехом внутри, как в BrandMark) ──
            rotate(degrees = -leftEarAngle, pivot = Offset(64f, 56f)) {
                val outerEarL = Path().apply {
                    moveTo(44f, 66f)
                    cubicTo(30f, 48f, 22f, 24f, 26f, 10f)
                    cubicTo(42f, 14f, 64f, 28f, 76f, 50f)
                    close()
                }
                drawPath(
                    path = outerEarL,
                    brush = Brush.linearGradient(
                        colors = listOf(furLight, armorDark),
                        start = Offset(26f, 10f),
                        end = Offset(68f, 62f)
                    )
                )
                drawPath(
                    path = outerEarL,
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(width = 2.6f, join = StrokeJoin.Round)
                )

                // Зубчатый неоновый мех внутри уха (отсылка к ic_launcher_foreground.xml)
                val innerEarL = Path().apply {
                    moveTo(33f, 19f)
                    lineTo(32f, 30f)
                    lineTo(40f, 32f)
                    lineTo(36f, 43f)
                    lineTo(45f, 45f)
                    lineTo(42f, 56f)
                    cubicTo(50f, 60f, 59f, 59f, 66f, 54f)
                    lineTo(60f, 42f)
                    lineTo(50f, 30f)
                    close()
                }
                drawPath(innerEarL, Color(0xFF0B131E))
                drawPath(
                    path = innerEarL,
                    color = visorColor,
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // ── Правое ухо протогена ──
            rotate(degrees = rightEarAngle, pivot = Offset(136f, 56f)) {
                val outerEarR = Path().apply {
                    moveTo(156f, 66f)
                    cubicTo(170f, 48f, 178f, 24f, 174f, 10f)
                    cubicTo(158f, 14f, 136f, 28f, 124f, 50f)
                    close()
                }
                drawPath(
                    path = outerEarR,
                    brush = Brush.linearGradient(
                        colors = listOf(furLight, armorDark),
                        start = Offset(174f, 10f),
                        end = Offset(132f, 62f)
                    )
                )
                drawPath(
                    path = outerEarR,
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(width = 2.6f, join = StrokeJoin.Round)
                )

                val innerEarR = Path().apply {
                    moveTo(167f, 19f)
                    lineTo(168f, 30f)
                    lineTo(160f, 32f)
                    lineTo(164f, 43f)
                    lineTo(155f, 45f)
                    lineTo(158f, 56f)
                    cubicTo(150f, 60f, 141f, 59f, 134f, 54f)
                    lineTo(140f, 42f)
                    lineTo(150f, 30f)
                    close()
                }
                drawPath(innerEarR, Color(0xFF0B131E))
                drawPath(
                    path = innerEarR,
                    color = visorColor,
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // ── Хохолок меха на макушке шлема ──
            val crownTuft = Path().apply {
                moveTo(84f, 42f)
                lineTo(92f, 27f)
                lineTo(99f, 36f)
                lineTo(105f, 23f)
                lineTo(111f, 37f)
                lineTo(118f, 30f)
                lineTo(115f, 43f)
                close()
            }
            drawPath(crownTuft, furMid)
            drawPath(crownTuft, visorColor.copy(alpha = 0.65f), style = Stroke(width = 1.6f, join = StrokeJoin.Round))

            // ── Корпус шлема протогена (титановая броня + внешний контур) ──
            val helmetPath = Path().apply {
                moveTo(100f, 36f)
                cubicTo(132f, 36f, 158f, 50f, 164f, 78f)
                cubicTo(168f, 98f, 158f, 118f, 134f, 127f)
                cubicTo(118f, 133f, 108f, 135f, 100f, 135f)
                cubicTo(92f, 135f, 82f, 133f, 66f, 127f)
                cubicTo(42f, 118f, 32f, 98f, 36f, 78f)
                cubicTo(42f, 50f, 68f, 36f, 100f, 36f)
                close()
            }
            // Тень шлема
            translate(0f, 3.5f) {
                drawPath(helmetPath, Color.Black.copy(alpha = 0.42f))
            }
            // Титановая основа шлема
            drawPath(
                path = helmetPath,
                brush = Brush.verticalGradient(
                    colors = listOf(armorLight, armorMid, armorDark),
                    startY = 36f,
                    endY = 135f
                )
            )

            // ── Изогнутое стекло OLED-визора (выступающая мордочка протогена) ──
            val visorPath = Path().apply {
                moveTo(100f, 55f)
                cubicTo(114f, 55f, 132f, 47f, 148f, 56f)
                cubicTo(160f, 64f, 163f, 84f, 157f, 104f)
                cubicTo(151f, 120f, 126f, 132f, 100f, 132f)
                cubicTo(74f, 132f, 49f, 120f, 43f, 104f)
                cubicTo(37f, 84f, 40f, 64f, 52f, 56f)
                cubicTo(68f, 47f, 86f, 55f, 100f, 55f)
                close()
            }
            drawPath(
                path = visorPath,
                brush = Brush.verticalGradient(
                    colors = listOf(visorGlassTop, visorGlassBottom, Color(0xFF06101C)),
                    startY = 48f,
                    endY = 132f
                )
            )

            // Внутреннее свечение и сетка матрицы внутри визора
            clipPath(visorPath) {
                // Тонкая точечная матрица неактивных светодиодов
                val step = 6.5f
                var gy = 54f
                while (gy <= 130f) {
                    var gx = 42f
                    while (gx <= 158f) {
                        drawCircle(
                            color = visorColor.copy(alpha = 0.055f),
                            radius = 1.1f,
                            center = Offset(gx, gy)
                        )
                        gx += step
                    }
                    gy += step
                }

                // Мягкий неоновый отсвет снизу визора
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(visorColor.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(100f, 124f),
                        radius = 52f
                    ),
                    radius = 52f,
                    center = Offset(100f, 124f)
                )

                // Глянцевый блик изогнутого стекла сверху
                val glossPath = Path().apply {
                    moveTo(52f, 62f)
                    cubicTo(74f, 52f, 126f, 52f, 148f, 62f)
                    cubicTo(132f, 58f, 68f, 58f, 52f, 62f)
                    close()
                }
                drawPath(glossPath, Color.White.copy(alpha = 0.16f))
            }

            // ── Налобная бронепластина и белые стыки визора (как в BrandMark) ──
            val browCrest = Path().apply {
                moveTo(74f, 40f)
                lineTo(100f, 56f)
                lineTo(126f, 40f)
            }
            drawPath(
                path = browCrest,
                color = visorColor.copy(alpha = 0.7f),
                style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Фирменные белые стыки брони со стеклом визора
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(49f, 57f),
                end = Offset(69f, 51f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(151f, 57f),
                end = Offset(131f, 51f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            // Внешний контур шлема и рамки визора
            drawPath(
                path = visorPath,
                color = visorColor.copy(alpha = 0.42f),
                style = Stroke(width = 1.8f, join = StrokeJoin.Round)
            )
            drawPath(
                path = helmetPath,
                color = Color.White.copy(alpha = 0.92f),
                style = Stroke(width = 3.2f, join = StrokeJoin.Round)
            )

            // ── 6. Мимика LED-дисплея (с неоновым ореолом BlurMaskFilter) ──
            drawVisorFacialExpression(
                emotion = emotion,
                visorIcon = visorIcon,
                visorColor = visorColor,
                isPressed = isPressed,
                isPetting = isPetting,
                isBlinking = isBlinking,
                wave = wave
            )

            // ── Боковые шарниры щеки / коммуникаторы связи (фирменная деталь VisorLink) ──
            drawCheekCommunicator(center = Offset(38f, 96f), visorColor = visorColor)
            drawCheekCommunicator(center = Offset(162f, 96f), visorColor = visorColor)
        }
    }

    // ── 7. Передние кибер-лапки (пружинят при нажатии, глажке и празднике) ──
    val pawLift = when {
        isPressed -> -6f
        isPetting || emotion == LinkEmotion.PARTY -> -4f + abs(sin(wave * 3f)) * -5f
        else -> 0f
    }
    drawProtogenPaw(center = Offset(66f, 175f + pawLift), visorColor = visorColor)
    drawProtogenPaw(center = Offset(134f, 175f - pawLift * 0.4f), visorColor = visorColor)

    // ── 8. Эмоциональные LED-частицы и волны связи вокруг головы ──
    drawFloatingEffects(
        emotion = emotion,
        visorIcon = visorIcon,
        visorColor = visorColor,
        isPressed = isPressed,
        isPetting = isPetting,
        wave = wave
    )
}

/**
 * Боковой шарнир щеки (коммуникатор) — фирменный круглый узел на стыке челюсти и визора.
 */
private fun DrawScope.drawCheekCommunicator(center: Offset, visorColor: Color) {
    drawCircle(color = Color(0xFF0D131C), radius = 12.5f, center = center)
    drawCircle(
        color = Color.White.copy(alpha = 0.92f),
        radius = 12.5f,
        center = center,
        style = Stroke(width = 2.8f)
    )
    drawCircle(
        color = visorColor.copy(alpha = 0.55f),
        radius = 8.8f,
        center = center,
        style = Stroke(width = 1.4f)
    )
    // Внутренний неоновый значок связи (три светодиода)
    drawCircle(color = visorColor, radius = 1.5f, center = center + Offset(-3.4f, 0f))
    drawCircle(color = visorColor, radius = 1.5f, center = center)
    drawCircle(color = visorColor, radius = 1.5f, center = center + Offset(3.4f, 0f))
}

/**
 * Лапка протогена с подушечками-светодиодами.
 */
private fun DrawScope.drawProtogenPaw(center: Offset, visorColor: Color) {
    val pawRect = RoundRect(
        rect = Rect(center.x - 16f, center.y - 10f, center.x + 16f, center.y + 10f),
        topLeft = CornerRadius(11f, 11f),
        topRight = CornerRadius(11f, 11f),
        bottomLeft = CornerRadius(9f, 9f),
        bottomRight = CornerRadius(9f, 9f)
    )
    val path = Path().apply { addRoundRect(pawRect) }
    drawPath(path, Color(0xFF152030))
    drawPath(path, Color.White.copy(alpha = 0.82f), style = Stroke(width = 2.2f))
    // Три светящихся коготка / подушечки
    drawLine(
        color = visorColor.copy(alpha = 0.85f),
        start = Offset(center.x - 6.5f, center.y - 3f),
        end = Offset(center.x - 6.5f, center.y + 4f),
        strokeWidth = 2.2f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = visorColor.copy(alpha = 0.85f),
        start = Offset(center.x, center.y - 4f),
        end = Offset(center.x, center.y + 4f),
        strokeWidth = 2.2f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = visorColor.copy(alpha = 0.85f),
        start = Offset(center.x + 6.5f, center.y - 3f),
        end = Offset(center.x + 6.5f, center.y + 4f),
        strokeWidth = 2.2f,
        cap = StrokeCap.Round
    )
}

/**
 * Отрисовка светящегося неонового пути с мягким ореолом (BlurMaskFilter) и ярким сердечником.
 */
private fun DrawScope.drawNeonPath(
    path: Path,
    color: Color,
    strokeWidth: Float = 3.8f,
    fill: Boolean = false
) {
    drawIntoCanvas { canvas ->
        val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.copy(alpha = 0.55f).toArgb()
            style = if (fill) android.graphics.Paint.Style.FILL_AND_STROKE else android.graphics.Paint.Style.STROKE
            this.strokeWidth = strokeWidth * 2.2f
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            maskFilter = BlurMaskFilter(5.5f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), glowPaint)
    }
    if (fill) {
        drawPath(path = path, color = color)
    } else {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Отрисовка глаз, румянца, ноздрей и фирменной зигзагообразной LED-пасти протогена.
 */
private fun DrawScope.drawVisorFacialExpression(
    emotion: LinkEmotion,
    visorIcon: VisorIcon?,
    visorColor: Color,
    isPressed: Boolean,
    isPetting: Boolean,
    isBlinking: Boolean,
    wave: Float
) {
    val leftEyeCenter = Offset(72f, 80f)
    val rightEyeCenter = Offset(128f, 80f)

    // ── 1. Глаза на визоре ──
    when {
        isPressed -> {
            // При «бупе» по носу — зажмуренные от удовольствия глаза > <
            val squintL = Path().apply {
                moveTo(60f, 73f)
                lineTo(80f, 80f)
                lineTo(60f, 87f)
            }
            val squintR = Path().apply {
                moveTo(140f, 73f)
                lineTo(120f, 80f)
                lineTo(140f, 87f)
            }
            drawNeonPath(squintL, visorColor, strokeWidth = 4.2f)
            drawNeonPath(squintR, visorColor, strokeWidth = 4.2f)
        }

        isBlinking -> {
            val blinkL = Path().apply {
                moveTo(58f, 81f)
                quadraticTo(72f, 84f, 84f, 81f)
            }
            val blinkR = Path().apply {
                moveTo(116f, 81f)
                quadraticTo(128f, 84f, 142f, 81f)
            }
            drawNeonPath(blinkL, visorColor, strokeWidth = 3.6f)
            drawNeonPath(blinkR, visorColor, strokeWidth = 3.6f)
        }

        visorIcon == VisorIcon.HEART || isPetting -> {
            val pulse = 1f + sin(wave * 3f) * 0.08f
            drawHeartEye(leftEyeCenter, 11.5f * pulse, visorColor)
            drawHeartEye(rightEyeCenter, 11.5f * pulse, visorColor)
        }

        visorIcon == VisorIcon.CROSS -> {
            val crossL = Path().apply {
                moveTo(62f, 71f); lineTo(82f, 89f)
                moveTo(82f, 71f); lineTo(62f, 89f)
            }
            val crossR = Path().apply {
                moveTo(118f, 71f); lineTo(138f, 89f)
                moveTo(138f, 71f); lineTo(118f, 89f)
            }
            drawNeonPath(crossL, visorColor, strokeWidth = 4f)
            drawNeonPath(crossR, visorColor, strokeWidth = 4f)
        }

        visorIcon == VisorIcon.ZZZ || emotion == LinkEmotion.SLEEPY -> {
            val sleepL = Path().apply {
                moveTo(59f, 79f)
                quadraticTo(72f, 89f, 85f, 79f)
            }
            val sleepR = Path().apply {
                moveTo(115f, 79f)
                quadraticTo(128f, 89f, 141f, 79f)
            }
            drawNeonPath(sleepL, visorColor, strokeWidth = 3.8f)
            drawNeonPath(sleepR, visorColor, strokeWidth = 3.8f)
        }

        visorIcon == VisorIcon.EXCLAMATION -> {
            // Широко раскрытые удивлённые кибер-глаза
            val ringL = Path().apply { addOval(Rect(leftEyeCenter, 10f)) }
            val ringR = Path().apply { addOval(Rect(rightEyeCenter, 10f)) }
            drawNeonPath(ringL, visorColor, strokeWidth = 3.4f)
            drawNeonPath(ringR, visorColor, strokeWidth = 3.4f)
            drawCircle(visorColor, radius = 3.2f, center = leftEyeCenter)
            drawCircle(visorColor, radius = 3.2f, center = rightEyeCenter)
        }

        visorIcon == VisorIcon.QUESTION || emotion == LinkEmotion.CONFUSED -> {
            // Озадаченный взгляд: левый глаз прищурен, правый приподнят с бровью
            val leftSquint = Path().apply {
                moveTo(59f, 81f)
                lineTo(84f, 79f)
            }
            val rightWide = Path().apply {
                addOval(Rect(rightEyeCenter + Offset(0f, -2f), 9.5f))
            }
            val rightBrow = Path().apply {
                moveTo(116f, 64f)
                quadraticTo(128f, 59f, 140f, 65f)
            }
            drawNeonPath(leftSquint, visorColor, strokeWidth = 4f)
            drawNeonPath(rightWide, visorColor, strokeWidth = 3.4f)
            drawNeonPath(rightBrow, visorColor, strokeWidth = 2.8f)
        }

        visorIcon == VisorIcon.ANGRY || emotion == LinkEmotion.OFFENDED -> {
            val angryL = Path().apply {
                moveTo(58f, 72f)
                lineTo(85f, 82f)
                lineTo(62f, 86f)
                close()
            }
            val angryR = Path().apply {
                moveTo(142f, 72f)
                lineTo(115f, 82f)
                lineTo(138f, 86f)
                close()
            }
            drawNeonPath(angryL, visorColor, fill = true)
            drawNeonPath(angryR, visorColor, fill = true)
        }

        emotion == LinkEmotion.SAD || emotion == LinkEmotion.CONCERNED -> {
            val sadL = Path().apply {
                moveTo(58f, 84f)
                quadraticTo(70f, 74f, 84f, 78f)
            }
            val sadR = Path().apply {
                moveTo(142f, 84f)
                quadraticTo(130f, 74f, 116f, 78f)
            }
            val browL = Path().apply {
                moveTo(58f, 71f); lineTo(78f, 66f)
            }
            val browR = Path().apply {
                moveTo(142f, 71f); lineTo(122f, 66f)
            }
            drawNeonPath(sadL, visorColor, strokeWidth = 4f)
            drawNeonPath(sadR, visorColor, strokeWidth = 4f)
            drawNeonPath(browL, visorColor.copy(alpha = 0.8f), strokeWidth = 2.4f)
            drawNeonPath(browR, visorColor.copy(alpha = 0.8f), strokeWidth = 2.4f)
        }

        emotion == LinkEmotion.HAPPY || emotion == LinkEmotion.PARTY ||
            visorIcon == VisorIcon.SMILE || visorIcon == VisorIcon.CHECKMARK -> {
            // Радостные дуги-шевроны протогена ^ ^
            val happyL = Path().apply {
                moveTo(58f, 84f)
                cubicTo(64f, 70f, 78f, 70f, 85f, 83f)
            }
            val happyR = Path().apply {
                moveTo(115f, 83f)
                cubicTo(122f, 70f, 136f, 70f, 142f, 84f)
            }
            drawNeonPath(happyL, visorColor, strokeWidth = 4.4f)
            drawNeonPath(happyR, visorColor, strokeWidth = 4.4f)
        }

        else -> {
            // Фирменный выразительный кибер-глаз протогена (как в BrandMark / ic_launcher_foreground.xml)
            val scanShift = if (visorIcon == VisorIcon.DOTS || emotion == LinkEmotion.CURIOUS) {
                sin(wave * 2f) * 3.5f
            } else 0f
            val cyberEyeL = Path().apply {
                moveTo(57f + scanShift, 79f)
                cubicTo(63f + scanShift, 69f, 78f + scanShift, 69f, 85f + scanShift, 77f)
                lineTo(75f + scanShift, 86f)
                cubicTo(66f + scanShift, 85f, 60f + scanShift, 83f, 57f + scanShift, 79f)
                close()
            }
            val cyberEyeR = Path().apply {
                moveTo(143f + scanShift, 79f)
                cubicTo(137f + scanShift, 69f, 122f + scanShift, 69f, 115f + scanShift, 77f)
                lineTo(125f + scanShift, 86f)
                cubicTo(134f + scanShift, 85f, 140f + scanShift, 83f, 143f + scanShift, 79f)
                close()
            }
            drawNeonPath(cyberEyeL, visorColor, fill = true)
            drawNeonPath(cyberEyeR, visorColor, fill = true)
        }
    }

    // ── 2. LED-румянец на щеках ──
    val showBlush = isPressed || isPetting ||
        emotion == LinkEmotion.HAPPY || emotion == LinkEmotion.PARTY ||
        visorIcon == VisorIcon.HEART
    if (showBlush) {
        val blushColor = Color(0xFFFF5C8A).copy(alpha = 0.85f)
        for (i in 0..2) {
            val dx = i * 5f
            drawLine(
                color = blushColor,
                start = Offset(52f + dx, 96f),
                end = Offset(55f + dx, 90f),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = blushColor,
                start = Offset(134f + dx, 90f),
                end = Offset(137f + dx, 96f),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round
            )
        }
    }

    // ── 3. Точки-ноздри на кончике визора (как в ic_launcher_foreground.xml) ──
    drawCircle(color = visorColor.copy(alpha = 0.9f), radius = 1.7f, center = Offset(95f, 96f))
    drawCircle(color = visorColor.copy(alpha = 0.9f), radius = 1.7f, center = Offset(105f, 96f))

    // ── 4. Фирменная зигзагообразная LED-пасть протогена (W-волна) ──
    val mouthPath = Path().apply {
        when {
            emotion == LinkEmotion.SAD || emotion == LinkEmotion.CONCERNED || visorIcon == VisorIcon.CROSS -> {
                // Грустный зигзаг уголками вниз
                moveTo(72f, 115f)
                lineTo(83f, 108f)
                lineTo(92f, 113f)
                lineTo(100f, 107f)
                lineTo(108f, 113f)
                lineTo(117f, 108f)
                lineTo(128f, 115f)
            }
            emotion == LinkEmotion.SLEEPY || visorIcon == VisorIcon.ZZZ -> {
                // Спокойная сонная волна
                moveTo(82f, 111f)
                quadraticTo(91f, 114f, 100f, 111f)
                quadraticTo(109f, 114f, 118f, 111f)
            }
            visorIcon == VisorIcon.EXCLAMATION || visorIcon == VisorIcon.QUESTION || emotion == LinkEmotion.CONFUSED -> {
                // Удивлённый/вопросительный зигзаг
                moveTo(78f, 112f)
                lineTo(88f, 107f)
                lineTo(100f, 114f)
                lineTo(112f, 107f)
                lineTo(122f, 110f)
            }
            else -> {
                // Классическая улыбка протогена с голосовой волной (зубчатый W-контур)
                val lift = if (isPressed || isPetting || emotion == LinkEmotion.PARTY) -2.5f else 0f
                moveTo(66f, 106f + lift)
                lineTo(77f, 114f)
                lineTo(88f, 107f)
                lineTo(100f, 115f)
                lineTo(112f, 107f)
                lineTo(123f, 114f)
                lineTo(134f, 106f + lift)
            }
        }
    }
    drawNeonPath(mouthPath, visorColor, strokeWidth = 3.4f)
}

/**
 * Отрисовка сердечка в глазу на визоре.
 */
private fun DrawScope.drawHeartEye(center: Offset, size: Float, color: Color) {
    val x = center.x
    val y = center.y
    val s = size
    val heart = Path().apply {
        moveTo(x, y + s * 0.75f)
        cubicTo(x - s * 1.2f, y + s * 0.1f, x - s * 0.95f, y - s * 0.85f, x, y - s * 0.25f)
        cubicTo(x + s * 0.95f, y - s * 0.85f, x + s * 1.2f, y + s * 0.1f, x, y + s * 0.75f)
        close()
    }
    drawNeonPath(heart, color, fill = true)
}

/**
 * Четырёхлучевая неоновая искра.
 */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val x = center.x
    val y = center.y
    val s = radius
    val p = Path().apply {
        moveTo(x, y - s)
        quadraticTo(x + s * 0.18f, y - s * 0.18f, x + s, y)
        quadraticTo(x + s * 0.18f, y + s * 0.18f, x, y + s)
        quadraticTo(x - s * 0.18f, y + s * 0.18f, x - s, y)
        quadraticTo(x - s * 0.18f, y - s * 0.18f, x, y - s)
        close()
    }
    drawPath(p, color)
}

/**
 * Всплывающие индикаторы вокруг головы Линка: сердечки, искры, Zzz, знак вопроса, волны связи.
 */
private fun DrawScope.drawFloatingEffects(
    emotion: LinkEmotion,
    visorIcon: VisorIcon?,
    visorColor: Color,
    isPressed: Boolean,
    isPetting: Boolean,
    wave: Float
) {
    val floatY = sin(wave) * 3.5f

    when {
        isPressed || isPetting || visorIcon == VisorIcon.HEART -> {
            drawHeartEye(Offset(26f, 44f + floatY), 6.5f, Color(0xFFFF4D8D))
            drawHeartEye(Offset(174f, 38f - floatY), 5.5f, Color(0xFFFF4D8D))
            drawSparkle(Offset(156f, 20f + floatY * 0.5f), 5.5f, visorColor)
        }

        emotion == LinkEmotion.PARTY || visorIcon == VisorIcon.CHECKMARK -> {
            drawSparkle(Offset(22f, 44f + floatY), 6.5f, visorColor)
            drawSparkle(Offset(178f, 38f - floatY), 6.5f, Color(0xFFFFD166))
            drawSparkle(Offset(152f, 16f + floatY), 4.5f, visorColor)
        }

        emotion == LinkEmotion.SLEEPY || visorIcon == VisorIcon.ZZZ -> {
            drawZGlyph(Offset(158f, 34f + floatY), 6f, visorColor.copy(alpha = 0.85f))
            drawZGlyph(Offset(172f, 20f - floatY * 0.6f), 8f, visorColor)
        }

        emotion == LinkEmotion.CONFUSED || visorIcon == VisorIcon.QUESTION -> {
            val qPath = Path().apply {
                moveTo(164f, 24f + floatY)
                cubicTo(164f, 16f + floatY, 178f, 16f + floatY, 178f, 24f + floatY)
                cubicTo(178f, 30f + floatY, 171f, 30f + floatY, 171f, 35f + floatY)
            }
            drawNeonPath(qPath, visorColor, strokeWidth = 3f)
            drawCircle(visorColor, radius = 2f, center = Offset(171f, 41f + floatY))
        }

        visorIcon == VisorIcon.DOTS || emotion == LinkEmotion.CURIOUS -> {
            // Волны радиосвязи у визора (как в BrandMark / ic_launcher_foreground.xml)
            val wave1 = Path().apply {
                moveTo(178f, 84f)
                cubicTo(183f, 89f, 183f, 97f, 178f, 102f)
            }
            val wave2 = Path().apply {
                moveTo(185f, 79f)
                cubicTo(192f, 86f, 192f, 100f, 185f, 107f)
            }
            drawNeonPath(wave1, visorColor, strokeWidth = 2.6f)
            drawPath(
                path = wave2,
                color = visorColor.copy(alpha = 0.5f),
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )
        }

        else -> {}
    }
}

private fun DrawScope.drawZGlyph(center: Offset, halfSize: Float, color: Color) {
    val z = Path().apply {
        moveTo(center.x - halfSize, center.y - halfSize)
        lineTo(center.x + halfSize, center.y - halfSize)
        lineTo(center.x - halfSize, center.y + halfSize)
        lineTo(center.x + halfSize, center.y + halfSize)
    }
    drawNeonPath(z, color, strokeWidth = 2.4f)
}

@Composable
fun AegisAura(
    action: LinkAction,
    emotion: LinkEmotion,
    visorIcon: VisorIcon?,
    message: String = "",
    onDismiss: (wasOffended: Boolean) -> Unit,
    onBoop: () -> Unit = {},
    onPet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var petAccumulator by remember { mutableFloatStateOf(0f) }

    // Кастомная позиция, куда юзер прикрепил Линка
    var customPosition by remember { mutableStateOf<IntOffset?>(null) }

    val haptic = rememberHaptic()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "hover")
    val hoverY by infiniteTransition.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hover_y"
    )

    // Если движок прислал новую команду движения (не IDLE), сбрасываем кастомную позицию
    LaunchedEffect(action) {
        if (action != LinkAction.IDLE) {
            customPosition = null
        }
    }

    // Базовые позиции от движка
    val engineBaseOffset = remember(action, screenWidthPx, screenHeightPx) {
        when (action) {
            LinkAction.HUG_EDGE -> IntOffset(
                (-25 * density.density).toInt(),
                (screenHeightPx * 0.4f).toInt()
            )
            LinkAction.PEEK -> IntOffset(
                (screenWidthPx - 80 * density.density).toInt(),
                (screenHeightPx * 0.15f).toInt()
            )
            LinkAction.SIT -> IntOffset(
                (screenWidthPx * 0.6f).toInt(),
                (screenHeightPx - 160 * density.density).toInt()
            )
            LinkAction.WAVE -> IntOffset(
                (screenWidthPx * 0.3f).toInt(),
                (screenHeightPx * 0.4f).toInt()
            )
            else -> IntOffset(
                (screenWidthPx * 0.1f).toInt(),
                (screenHeightPx * 0.6f).toInt()
            )
        }
    }

    // Активная позиция: либо куда юзер прилепил, либо куда сказал движок
    val activeBaseOffset = customPosition ?: engineBaseOffset

    val animatedX by animateFloatAsState(
        targetValue = if (isDragging) activeBaseOffset.x + offsetX else activeBaseOffset.x.toFloat(),
        animationSpec = if (isDragging) snap() else spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "x"
    )
    val animatedY by animateFloatAsState(
        targetValue = if (isDragging) activeBaseOffset.y + offsetY else activeBaseOffset.y.toFloat() + hoverY,
        animationSpec = if (isDragging) snap() else spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "y"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isDragging) (offsetX * 0.05f).coerceIn(-15f, 15f) else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "aegis_rotation"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
            .graphicsLayer { rotationZ = rotation }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = message.isNotEmpty(),
                enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = VlTheme.tokens.shapes.button,
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(bottom = 8.dp).widthIn(max = 220.dp)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Box(modifier = Modifier.size(120.dp)) {
                AnimatedProtogen(
                    emotion = emotion,
                    visorIcon = visorIcon,
                    isPressed = isPressed || isDragging,
                    modifier = Modifier.fillMaxSize()
                )

                // ── ЗОНА ГОЛОВЫ (Тапы и Глажка) ──
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(70.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    tryAwaitRelease()
                                    isPressed = false
                                },
                                onTap = {
                                    haptic.perform(HapticType.REACTION, true)
                                    onBoop()
                                },
                                onLongPress = {
                                    haptic.perform(HapticType.LONG_PRESS, true)
                                    onDismiss(false) // Убрать долгим нажатием
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { petAccumulator = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    petAccumulator += abs(dragAmount)
                                    // Порог глажки снижен со 150 до 60
                                    if (petAccumulator > 60f) {
                                        onPet()
                                        haptic.perform(HapticType.SELECTION, true)
                                        petAccumulator = 0f
                                    }
                                }
                            )
                        }
                )

                // ── ЗОНА ТЕЛА (Перетаскивание и Снаппинг) ──
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(60.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    tryAwaitRelease()
                                    isPressed = false
                                },
                                onTap = {
                                    haptic.perform(HapticType.REACTION, true)
                                    onBoop()
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    haptic.perform(HapticType.SELECTION, true)
                                },
                                onDragEnd = {
                                    isDragging = false
                                    isPressed = false

                                    // ── ЛОГИКА МАГНИТНОГО СНАППИНГА ──
                                    val dropX = activeBaseOffset.x + offsetX
                                    val dropY = activeBaseOffset.y + offsetY

                                    val aegisSize = 120 * density.density
                                    val bottomBarOffset = 160 * density.density // Высота бара ввода + отступ

                                    // Вычисляем расстояния до 3-х магнитных зон
                                    val distLeft = dropX
                                    val distRight = screenWidthPx - dropX - aegisSize
                                    val distBottom = screenHeightPx - dropY - bottomBarOffset

                                    val minDist = minOf(distLeft, distRight, distBottom)

                                    val snapX: Float
                                    val snapY: Float

                                    when (minDist) {
                                        distBottom -> {
                                            // Прилипает к боттом бару (можно двигать по X)
                                            snapX = dropX.coerceIn(0f, screenWidthPx - aegisSize)
                                            snapY = screenHeightPx - bottomBarOffset
                                        }
                                        distLeft -> {
                                            // Прилипает к сообщениям слева (можно двигать по Y)
                                            snapX = -25f * density.density
                                            snapY = dropY.coerceIn(0f, screenHeightPx - bottomBarOffset)
                                        }
                                        else -> {
                                            // Прилипает к своим сообщениям справа
                                            snapX = screenWidthPx - 80f * density.density
                                            snapY = dropY.coerceIn(0f, screenHeightPx - bottomBarOffset)
                                        }
                                    }

                                    customPosition = IntOffset(snapX.roundToInt(), snapY.roundToInt())
                                    offsetX = 0f
                                    offsetY = 0f

                                    haptic.perform(HapticType.SUCCESS, true)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            )
                        }
                )
            }
        }
    }
}