package org.visorlink.app.ui.components

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import org.visorlink.app.ui.components.led.vlLedTextRows
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Идентификатор авторской мини-иллюстрации Forge (vl_glyph_kit.dart).
 */
enum class VlKit {
    ALARM, CLOCK, PHONE_VIBE, SWIPE, COMPASS, NOTE, TRAY_DOWN, BOOK, ADMIN_SHIELD, CLOUD, PLANE, SMS, DOOR,
    TRASH, TRASH_X, BROOM, UNLINK, EYE_OFF, FINGERPRINT, PINPAD, MAIL, PADLOCK, LOCK_RESET, GOOGLE, AVATAR,
    PERSON_ADD, GROUP, MEGAPHONE, MAGNIFIER, PENCIL, TOGGLE_ON, TOGGLE_OFF, IMAGE, DOCUMENT, DATABASE, DRIVE,
    GAUGE, LAYERS, SPARKLES, CHIP, SUN, MOON, SUN_MOON, BUBBLE_EN, BUBBLE_RU, FLASK, SYNC, SEAL, PULSE,
    GAVEL, BROWSER, INFO, BUG, CODE_OFF, ROBOT, WARN, INBOX, TEXT_AA, PHOTOS, STAR, MASKS, BELL_OFF, SHIELD,
    BLOCK, KEY, GEAR, SPEAKER_ON, SPEAKER_OFF, FLAG, GIF, NEW_BURST, PLAYLIST, ARROW_UP, ARROW_DOWN, COPY,
    CHECK_BADGE, PLUS_BADGE, INVITE, TELEGRAM, CLOUD_STORAGE
}

/**
 * Подбор авторской мини-иллюстрации по иконке.
 */
fun vlKitOf(icon: ImageVector): VlKit? {
    val name = icon.name.lowercase()
    return when {
        "alarm" in name -> VlKit.ALARM
        "schedule" in name -> VlKit.CLOCK
        "vibrat" in name -> VlKit.PHONE_VIBE
        "swipe" in name -> VlKit.SWIPE
        "explore" in name -> VlKit.COMPASS
        "audio" in name || "music" in name -> VlKit.NOTE
        "verticalalignbottom" in name || "agenda" in name -> VlKit.TRAY_DOWN
        "book" in name -> VlKit.BOOK
        "admin" in name -> VlKit.ADMIN_SHIELD
        "clouddone" in name -> VlKit.CLOUD
        "cloudqueue" in name -> VlKit.CLOUD_STORAGE
        "telegram" in name || name == "ic_telegram" -> VlKit.TELEGRAM
        "send" in name -> VlKit.PLANE
        "sms" in name -> VlKit.SMS
        "logout" in name || "exittoapp" in name || "door" in name -> VlKit.DOOR
        "deleteforever" in name -> VlKit.TRASH_X
        "delete" in name -> VlKit.TRASH
        "sweep" in name || "clean" in name || "broom" in name -> VlKit.BROOM
        "unlink" in name || "linkoff" in name -> VlKit.UNLINK
        "visibilityoff" in name -> VlKit.EYE_OFF
        "fingerprint" in name -> VlKit.FINGERPRINT
        "password" in name || "pin" in name -> VlKit.PINPAD
        "mail" in name -> VlKit.MAIL
        "lockreset" in name -> VlKit.LOCK_RESET
        "lock" in name -> VlKit.PADLOCK
        "google" in name || "accountcircle" in name -> VlKit.GOOGLE
        "personadd" in name -> VlKit.PERSON_ADD
        "group" in name -> VlKit.GROUP
        "person" in name -> VlKit.AVATAR
        "campaign" in name || "megaphone" in name -> VlKit.MEGAPHONE
        "search" in name || "magnifier" in name -> VlKit.MAGNIFIER
        "edit" in name || "pencil" in name -> VlKit.PENCIL
        "toggleon" in name -> VlKit.TOGGLE_ON
        "toggleoff" in name -> VlKit.TOGGLE_OFF
        "image" in name -> VlKit.IMAGE
        "textsnippet" in name || "document" in name -> VlKit.DOCUMENT
        "storage" in name -> VlKit.DATABASE
        "drive" in name -> VlKit.DRIVE
        "speed" in name || "gauge" in name -> VlKit.GAUGE
        "layers" in name -> VlKit.LAYERS
        "autoawesome" in name || "sparkle" in name -> VlKit.SPARKLES
        "memory" in name || "chip" in name -> VlKit.CHIP
        "brightnessauto" in name -> VlKit.SUN_MOON
        "lightmode" in name -> VlKit.SUN
        "darkmode" in name -> VlKit.MOON
        "gtranslate" in name -> VlKit.BUBBLE_RU
        "translate" in name -> VlKit.BUBBLE_EN
        "science" in name || "flask" in name || "tune" in name -> VlKit.FLASK
        "sync" in name || "refresh" in name -> VlKit.SYNC
        "verifieduser" in name || "seal" in name -> VlKit.SEAL
        "healthandsafety" in name || "pulse" in name -> VlKit.PULSE
        "gavel" in name -> VlKit.GAVEL
        "openinbrowser" in name || "browser" in name -> VlKit.BROWSER
        "info" in name -> VlKit.INFO
        "bug" in name -> VlKit.BUG
        "codeoff" in name || "code" in name -> VlKit.CODE_OFF
        "smarttoy" in name || "robot" in name || "terminal" in name -> VlKit.ROBOT
        "warning" in name || "warn" in name -> VlKit.WARN
        "movetoinbox" in name || "inbox" in name -> VlKit.INBOX
        "textfields" in name || "textaa" in name -> VlKit.TEXT_AA
        "photo" in name || "media" in name -> VlKit.PHOTOS
        "star" in name -> VlKit.STAR
        "theater" in name || "mask" in name -> VlKit.MASKS
        "notificationsoff" in name -> VlKit.BELL_OFF
        "shield" in name || "secur" in name -> VlKit.SHIELD
        "block" in name -> VlKit.BLOCK
        "key" in name -> VlKit.KEY
        "settings" in name || "gear" in name -> VlKit.GEAR
        "volumeoff" in name -> VlKit.SPEAKER_OFF
        "volume" in name || "speaker" in name -> VlKit.SPEAKER_ON
        "flag" in name -> VlKit.FLAG
        "gif" in name -> VlKit.GIF
        "newrelease" in name || "store" in name -> VlKit.NEW_BURST
        "playlist" in name -> VlKit.PLAYLIST
        "arrowup" in name -> VlKit.ARROW_UP
        "arrowdown" in name -> VlKit.ARROW_DOWN
        "copy" in name -> VlKit.COPY
        "check" in name -> VlKit.CHECK_BADGE
        "add" in name -> VlKit.PLUS_BADGE
        "contact" in name || "invite" in name || "gift" in name -> VlKit.INVITE
        else -> null
    }
}

/**
 * Отрисовка векторного 3D-предмета VlKit.
 */
fun DrawScope.drawVlKit(kind: VlKit, s: Float, dark: Boolean, led: Color) {
    VlKitScope(this, s, dark, led).render(kind)
}

val PI_F = PI.toFloat()

fun Path.addRRect(r: RoundRect) = addRoundRect(r)
fun Float.clamp(min: Float, max: Float) = coerceIn(min, max)

class PaintDef(val color: Color = Color.Unspecified, val brush: Brush? = null)

class LineStroke(val color: Color = Color.Unspecified, val width: Float, val cap: StrokeCap = StrokeCap.Round, val brush: Brush? = null) {
    constructor(brush: Brush, width: Float, cap: StrokeCap = StrokeCap.Round) : this(Color.Unspecified, width, cap, brush)
}

class VlKitScope(
    val drawScope: DrawScope,
    val s: Float,
    val dark: Boolean,
    val led: Color
) {
    val _white = Color(0xFFFFFFFF)
    val _black = Color(0xFF000000)
    val _blue = Color(0xFF3D7BFF)
    val _cyan = Color(0xFF19B8E6)
    val _green = Color(0xFF2BB673)
    val _teal = Color(0xFF14B8A6)
    val _violet = Color(0xFF8B5CFF)
    val _pink = Color(0xFFFF4F9A)
    val _orange = Color(0xFFFF6A2B)
    val _amber = Color(0xFFFFB020)
    val _red = Color(0xFFF0384F)
    val _gold = Color(0xFFE4A92B)

    val Offset.dx: Float get() = x
    val Offset.dy: Float get() = y
    val Rect.center: Offset get() = Offset((left + right) / 2f, (top + bottom) / 2f)
    operator fun Offset.times(scale: Float) = Offset(x * scale, y * scale)
    operator fun Float.times(offset: Offset) = Offset(offset.x * this, offset.y * this)
    val <T> List<T>.length: Int get() = size
    val <T> List<T>.first: T get() = first()

    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    fun r(l: Float, t: Float, w: Float, h: Float) = Rect(l * s, t * s, (l + w) * s, (t + h) * s)
    fun rectFromCircle(center: Offset, radius: Float) = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    fun rectFromCenter(center: Offset, width: Float, height: Float) = Rect(center.x - width / 2f, center.y - height / 2f, center.x + width / 2f, center.y + height / 2f)
    fun rr(rect: Rect, radius: Float) = RoundRect(rect, CornerRadius(radius * s, radius * s))
    fun rrPath(rect: Rect, radius: Float) = Path().apply {
        addRoundRect(rr(rect, radius))
    }
    fun ovalPath(rect: Rect) = Path().apply {
        addOval(rect)
    }

    fun Rect.inflate(delta: Float) = Rect(left - delta, top - delta, right + delta, bottom + delta)
    fun Path.shift(offset: Offset) = Path().apply { addPath(this@shift, offset) }

    fun grad(rect: Rect, colors: List<Color>, start: Offset? = null, end: Offset? = null, stops: List<Float>? = null, b: Alignment? = null, e: Alignment? = null): PaintDef {
        val sOffset = start ?: (if (b == Alignment.TopCenter) Offset(rect.center.x, rect.top) else if (b == Alignment.TopStart) rect.topLeft else Offset(rect.center.x, rect.top))
        val eOffset = end ?: (if (e == Alignment.BottomCenter) Offset(rect.center.x, rect.bottom) else if (e == Alignment.BottomEnd) rect.bottomRight else Offset(rect.center.x, rect.bottom))
        val brush = if (stops != null && stops.size == colors.size) {
            val pairs = stops.mapIndexed { idx, stop -> stop to colors[idx] }.toTypedArray()
            Brush.linearGradient(*pairs, start = sOffset, end = eOffset)
        } else {
            Brush.linearGradient(colors, start = sOffset, end = eOffset)
        }
        return PaintDef(brush = brush)
    }

    fun line(color: Color, w: Float) = LineStroke(color, w * s)
    fun line(color: Color, w: Int) = LineStroke(color, w.toFloat() * s)
    fun paint(color: Color) = PaintDef(color = color)
    fun paint(brush: Brush) = PaintDef(brush = brush)

    fun shadow(path: Path, color: Color? = null) {
        val c = color ?: _black.copy(alpha = if (dark) 0.55f else 0.20f)
        drawScope.drawPath(path, c.copy(alpha = c.alpha * 0.45f), style = Stroke(width = s * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun obj(path: Path, colors: List<Color>, b: Alignment? = null, e: Alignment? = null, glow: Color? = null) {
        val bounds = path.getBounds()
        shadow(path, color = if (glow == null) null else glow.copy(alpha = if (dark) 0.42f else 0.30f))
        val bStart = if (b == Alignment.TopStart ) bounds.topLeft else Offset(bounds.center.x, bounds.top)
        val bEnd = if (e == Alignment.BottomEnd ) bounds.bottomRight else Offset(bounds.center.x, bounds.bottom)
        drawScope.drawPath(path, Brush.linearGradient(colors, start = bStart, end = bEnd), style = Fill)
        drawScope.drawPath(path, _white.copy(alpha = if (dark) 0.16f else 0.32f), style = Stroke(width = 0.012f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    val glassColors get() = if (dark) listOf(Color(0xFF3A3C43), Color(0xFF18191C)) else listOf(_white, Color(0xFFE2E4E8))

    fun glass(path: Path, b: Alignment? = null, e: Alignment? = null) {
        val bounds = path.getBounds()
        shadow(path)
        val bStart = if (b == Alignment.TopCenter) Offset(bounds.center.x, bounds.top) else bounds.topLeft
        val bEnd = if (e == Alignment.BottomCenter) Offset(bounds.center.x, bounds.bottom) else bounds.bottomRight
        drawScope.drawPath(path, Brush.linearGradient(glassColors, start = bStart, end = bEnd), style = Fill)
        drawScope.drawPath(path, if (dark) _white.copy(alpha = 0.12f) else _black.copy(alpha = 0.07f), style = Stroke(width = 0.012f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun screenGlass(rect: Rect, radius: Float) {
        drawScope.drawRoundRect(
            color = Color(0xFF0A0B0E),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(radius * s, radius * s)
        )
    }

    fun ledDot(o: Offset, radius: Float, color: Color? = null, on: Boolean = true) {
        val col = color ?: led
        if (on) {
            drawScope.drawCircle(col.copy(alpha = if (dark) 0.50f else 0.25f), radius = radius * 2.1f, center = o)
        }
        drawScope.drawCircle(if (on) col else col.copy(alpha = 0.25f), radius = radius, center = o)
    }

    fun lens(center: Offset, radius: Float, hue: Color) {
        drawScope.drawCircle(
            brush = Brush.radialGradient(
                listOf(lerp(hue, _white, 0.34f), hue, lerp(hue, _black, 0.40f)),
                center = center + Offset(-0.3f * radius, -0.4f * radius),
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }

    fun tint(a: Color, t: Float, to: Color = _black) = lerp(a, to, t)
    val ink get() = if (dark) _white.copy(alpha = 0.85f) else _black.copy(alpha = 0.70f)
    val inkSoft get() = if (dark) _white.copy(alpha = 0.40f) else _black.copy(alpha = 0.22f)

    fun polygon(pts: List<Offset>) = Path().apply {
        if (pts.isNotEmpty()) {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                lineTo(pts[i].x, pts[i].y)
            }
            close()
        }
    }

    fun star(center: Offset, radius: Float, inner: Float, points: Int = 5, rotateAngle: Float = -PI_F / 2f) = Path().apply {
        for (i in 0 until points * 2) {
            val a = rotateAngle + i * PI_F / points
            val rad = if (i % 2 == 0) radius else inner
            val o = Offset(center.x + cos(a) * rad, center.y + sin(a) * rad)
            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        }
        close()
    }

    fun rotated(pivot: Offset, degrees: Number, block: () -> Unit) {
        drawScope.rotate(degrees.toFloat(), pivot) {
            block()
        }
    }

    fun bellPath() = Path().apply {
        moveTo(s * 0.5f, s * 0.14f)
        cubicTo(s * 0.72f, s * 0.14f, s * 0.78f, s * 0.32f, s * 0.78f, s * 0.5f)
        lineTo(s * 0.78f, s * 0.62f)
        lineTo(s * 0.88f, s * 0.74f)
        lineTo(s * 0.12f, s * 0.74f)
        lineTo(s * 0.22f, s * 0.62f)
        lineTo(s * 0.22f, s * 0.5f)
        cubicTo(s * 0.22f, s * 0.32f, s * 0.28f, s * 0.14f, s * 0.5f, s * 0.14f)
        close()
    }

    fun shieldPath() = Path().apply {
        moveTo(s * 0.5f, s * 0.1f)
        lineTo(s * 0.84f, s * 0.22f)
        cubicTo(s * 0.84f, s * 0.56f, s * 0.72f, s * 0.78f, s * 0.5f, s * 0.9f)
        cubicTo(s * 0.28f, s * 0.78f, s * 0.16f, s * 0.56f, s * 0.16f, s * 0.22f)
        close()
    }

    fun check(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) = Path().apply {
        moveTo(s * x0, s * y0)
        lineTo(s * x1, s * y1)
        lineTo(s * x2, s * y2)
    }

    inner class CanvasBridge {
        fun drawLine(p1: Offset, p2: Offset, stroke: LineStroke) {
            drawScope.drawLine(stroke.color, p1, p2, strokeWidth = stroke.width, cap = stroke.cap)
        }
        fun drawCircle(center: Offset, radius: Float, stroke: LineStroke) {
            drawScope.drawCircle(stroke.color, radius = radius, center = center, style = Stroke(width = stroke.width, cap = stroke.cap))
        }
        fun drawCircle(center: Offset, radius: Float, paint: PaintDef) {
            if (paint.brush != null) {
                drawScope.drawCircle(paint.brush, radius = radius, center = center)
            } else {
                drawScope.drawCircle(paint.color, radius = radius, center = center)
            }
        }
        fun drawPath(path: Path, stroke: LineStroke) {
            if (stroke.brush != null) {
                drawScope.drawPath(path, stroke.brush, style = Stroke(width = stroke.width, cap = stroke.cap, join = StrokeJoin.Round))
            } else {
                drawScope.drawPath(path, stroke.color, style = Stroke(width = stroke.width, cap = stroke.cap, join = StrokeJoin.Round))
            }
        }
        fun drawPath(path: Path, paint: PaintDef) {
            if (paint.brush != null) {
                drawScope.drawPath(path, paint.brush, style = Fill)
            } else {
                drawScope.drawPath(path, paint.color, style = Fill)
            }
        }
        fun drawRect(rect: Rect, stroke: LineStroke) {
            drawScope.drawRect(stroke.color, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = stroke.width, cap = stroke.cap, join = StrokeJoin.Round))
        }
        fun drawRect(rect: Rect, paint: PaintDef) {
            if (paint.brush != null) {
                drawScope.drawRect(paint.brush, topLeft = rect.topLeft, size = rect.size, style = Fill)
            } else {
                drawScope.drawRect(paint.color, topLeft = rect.topLeft, size = rect.size, style = Fill)
            }
        }
        fun drawOval(rect: Rect, stroke: LineStroke) {
            drawScope.drawOval(stroke.color, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = stroke.width, cap = stroke.cap, join = StrokeJoin.Round))
        }
        fun drawOval(rect: Rect, paint: PaintDef) {
            if (paint.brush != null) {
                drawScope.drawOval(paint.brush, topLeft = rect.topLeft, size = rect.size, style = Fill)
            } else {
                drawScope.drawOval(paint.color, topLeft = rect.topLeft, size = rect.size, style = Fill)
            }
        }
        fun drawRRect(rrect: RoundRect, stroke: LineStroke) {
            drawScope.drawRoundRect(stroke.color, topLeft = Offset(rrect.left, rrect.top), size = Size(rrect.width, rrect.height), cornerRadius = rrect.topLeftCornerRadius, style = Stroke(width = stroke.width, cap = stroke.cap, join = StrokeJoin.Round))
        }
        fun drawRRect(rrect: RoundRect, paint: PaintDef) {
            if (paint.brush != null) {
                drawScope.drawRoundRect(paint.brush, topLeft = Offset(rrect.left, rrect.top), size = Size(rrect.width, rrect.height), cornerRadius = rrect.topLeftCornerRadius, style = Fill)
            } else {
                drawScope.drawRoundRect(paint.color, topLeft = Offset(rrect.left, rrect.top), size = Size(rrect.width, rrect.height), cornerRadius = rrect.topLeftCornerRadius, style = Fill)
            }
        }
        fun drawArc(rect: Rect, startAngle: Float, sweepAngle: Float, useCenter: Boolean, stroke: LineStroke) {
            drawScope.drawArc(stroke.color, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = useCenter, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = stroke.width, cap = stroke.cap))
        }
        fun drawArc(rect: Rect, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: PaintDef) {
            if (paint.brush != null) {
                drawScope.drawArc(paint.brush, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = useCenter, topLeft = rect.topLeft, size = rect.size, style = Fill)
            } else {
                drawScope.drawArc(paint.color, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = useCenter, topLeft = rect.topLeft, size = rect.size, style = Fill)
            }
        }
                fun drawText(text: String, x: Float, y: Float, size: Float, color: Color, bold: Boolean = false) {
            drawScope.drawIntoCanvas {
                val paint = android.graphics.Paint().apply {
                    this.color = color.toArgb()
                    this.textSize = size
                    this.isFakeBoldText = bold
                    this.isAntiAlias = true
                }
                it.nativeCanvas.drawText(text, x, y, paint)
            }
        }
        fun withClip(path: Path, block: () -> Unit) {
            drawScope.clipPath(path) {
                block()
            }
        }
    }

    val c = CanvasBridge()

    fun render(kind: VlKit) {
        when (kind) {
            VlKit.ALARM -> _alarm()
            VlKit.CLOCK -> _clock()
            VlKit.PHONE_VIBE -> _phoneVibe()
            VlKit.SWIPE -> _swipe()
            VlKit.COMPASS -> _compass()
            VlKit.NOTE -> _note()
            VlKit.TRAY_DOWN -> _trayDown()
            VlKit.BOOK -> _book()
            VlKit.ADMIN_SHIELD -> _adminShield()
            VlKit.CLOUD -> _cloud(storage = false)
            VlKit.PLANE -> _plane()
            VlKit.SMS -> _sms()
            VlKit.DOOR -> _door()
            VlKit.TRASH -> _trash(x = false)
            VlKit.TRASH_X -> _trash(x = true)
            VlKit.BROOM -> _broom()
            VlKit.UNLINK -> _unlink()
            VlKit.EYE_OFF -> _eyeOff()
            VlKit.FINGERPRINT -> _fingerprint()
            VlKit.PINPAD -> _pinpad()
            VlKit.MAIL -> _mail()
            VlKit.PADLOCK -> _padlock(reset = false)
            VlKit.LOCK_RESET -> _padlock(reset = true)
            VlKit.GOOGLE -> _google()
            VlKit.AVATAR -> _avatars(count = 1, plus = false)
            VlKit.PERSON_ADD -> _avatars(count = 1, plus = true)
            VlKit.GROUP -> _avatars(count = 2, plus = false)
            VlKit.MEGAPHONE -> _megaphone()
            VlKit.MAGNIFIER -> _magnifier()
            VlKit.PENCIL -> _pencil()
            VlKit.TOGGLE_ON -> _toggle(on = true)
            VlKit.TOGGLE_OFF -> _toggle(on = false)
            VlKit.IMAGE -> _picture(slash = false)
            VlKit.DOCUMENT -> _document()
            VlKit.DATABASE -> _database()
            VlKit.DRIVE -> _drive()
            VlKit.GAUGE -> _gauge()
            VlKit.LAYERS -> _layers()
            VlKit.SPARKLES -> _sparkles()
            VlKit.CHIP -> _chip()
            VlKit.SUN -> _sun()
            VlKit.MOON -> _moon()
            VlKit.SUN_MOON -> _sunMoon()
            VlKit.BUBBLE_EN -> _bubble("EN", _blue)
            VlKit.BUBBLE_RU -> _bubble("RU", _teal)
            VlKit.FLASK -> _flask()
            VlKit.SYNC -> _sync()
            VlKit.SEAL -> _seal()
            VlKit.PULSE -> _pulse()
            VlKit.GAVEL -> _gavel()
            VlKit.BROWSER -> _browser()
            VlKit.INFO -> _info()
            VlKit.BUG -> _bug()
            VlKit.CODE_OFF -> _codeOff()
            VlKit.ROBOT -> _robot()
            VlKit.WARN -> _warn()
            VlKit.INBOX -> _inbox()
            VlKit.TEXT_AA -> _textAa()
            VlKit.PHOTOS -> _photos()
            VlKit.STAR -> _star()
            VlKit.MASKS -> _masks()
            VlKit.BELL_OFF -> _bellOff()
            VlKit.SHIELD -> _shield()
            VlKit.BLOCK -> _block()
            VlKit.KEY -> _key()
            VlKit.GEAR -> _gear()
            VlKit.SPEAKER_ON -> _speaker(on = true)
            VlKit.SPEAKER_OFF -> _speaker(on = false)
            VlKit.FLAG -> _flag()
            VlKit.GIF -> _ledLabel("GIF", _pink)
            VlKit.NEW_BURST -> _newBurst()
            VlKit.PLAYLIST -> _playlist()
            VlKit.ARROW_UP -> _arrowBadge(up = true)
            VlKit.ARROW_DOWN -> _arrowBadge(up = false)
            VlKit.COPY -> _copy()
            VlKit.CHECK_BADGE -> _badge(_green, check = true)
            VlKit.PLUS_BADGE -> _badge(_cyan, check = false)
            VlKit.INVITE -> _invite()
            VlKit.TELEGRAM -> _telegram()
            VlKit.CLOUD_STORAGE -> _cloud(storage = true)
        }
    }

// ── Время и сигналы ──

  fun _alarm() {
    for (left in listOf(true, false)) {
      val o = p(if (left) 0.2f else 0.8f, 0.2f);
      val bell = Path().apply { addOval(rectFromCircle(o, s * 0.13f)) };
      obj(bell, listOf(Color(0xFFFFC46B), _orange), glow = _orange);
    }
    val body = Path().apply { addOval(rectFromCircle(p(0.5f, 0.56f), s * 0.34f)) };
    glass(body);
    c.drawCircle(p(0.5f, 0.56f), s * 0.34f, line(_orange, 0.05f));
    c.drawLine(p(0.5f, 0.56f), p(0.5f, 0.38f), line(ink, 0.04f));
    c.drawLine(p(0.5f, 0.56f), p(0.64f, 0.62f), line(ink, 0.04f));
    ledDot(p(0.5f, 0.56f), s * 0.025f);
    c.drawLine(p(0.3f, 0.86f), p(0.24f, 0.94f), line(_orange, 0.05f));
    c.drawLine(p(0.7f, 0.86f), p(0.76f, 0.94f), line(_orange, 0.05f));
  }

  fun _clock() {
    val body = Path().apply { addOval(rectFromCircle(p(0.5f, 0.5f), s * 0.38f)) };
    glass(body);
    c.drawCircle(p(0.5f, 0.5f), s * 0.38f, line(_teal, 0.05f));
    for (i in 0 until 12) {
      val a = i * PI_F / 6;
      val r0 = s * (if (i % 3 == 0) 0.26f else 0.29f); val r1 = s * 0.32f;
      c.drawLine(p(0.5f, 0.5f) + Offset(cos(a), sin(a)) * r0, p(0.5f, 0.5f) + Offset(cos(a), sin(a)) * r1, line(inkSoft, 0.02f));
    }
    c.drawLine(p(0.5f, 0.5f), p(0.5f, 0.3f), line(ink, 0.045f));
    c.drawLine(p(0.5f, 0.5f), p(0.64f, 0.58f), line(_teal, 0.045f));
    ledDot(p(0.5f, 0.5f), s * 0.03f);
  }

  fun _bellOff() {
    val body = bellPath();
    shadow(body);
    c.drawPath(body, grad(r(0f, s * 0.14f, s, s * 0.6f), if (dark) listOf(Color(0xFF6B6E78), Color(0xFF3A3C43)) else listOf(Color(0xFFC9CCD3), Color(0xFF9094A0))));
    lens(p(0.5f, 0.82f), s * 0.08f, if (dark) Color(0xFF6B6E78) else Color(0xFF9094A0));
    val cut = Path().apply { moveTo(s * 0.18f, s * 0.86f); lineTo(s * 0.84f, s * 0.16f) };
    c.drawPath(cut, line(if (dark) Color(0xFF1A1B1F) else _white, 0.15f));
    c.drawPath(cut, LineStroke(brush = Brush.linearGradient(listOf(Color(0xFFFF6B8A), _red), start = Offset(0f, s), end = Offset(s, 0f)), width = s * 0.075f));
  }

  // ── Телефон и жесты ──

  fun _phoneVibe() {
    val body = rrPath(r(0.32f, 0.12f, 0.36f, 0.76f), 0.09f);
    glass(body, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    val screen = r(0.37f, 0.18f, 0.26f, 0.64f);
    screenGlass(screen, 0.05f);
    ledDot(screen.center + Offset(0f, -s * 0.06f), s * 0.03f);
    ledDot(screen.center + Offset(0f, s * 0.06f), s * 0.02f, on = false);
    for (left in listOf(true, false)) {
      for (k in listOf(0, 1)) {
        val x = if (left) 0.22f - k * 0.12f else 0.78f + k * 0.12f;
        val path = Path().apply { moveTo(s * x, s * 0.32f); quadraticBezierTo(s * (x + (if (left) -0.04f else 0.04f)), s * 0.5f, s * x, s * 0.68f) };
        c.drawPath(path, line((_violet).copy(alpha = if (k == 0) 1f else 0.55f), 0.05f));
      }
    }
  }

  fun _swipe() {
    // Палец ведёт вверх: пунктир-след, стрелка и круги от касания
    for (i in 0 until 4) {
      c.drawCircle(p(0.5f, 0.36f + i * 0.1f), s * 0.018f, paint((_orange).copy(alpha = 0.3f + i * 0.2f)));
    }
    c.drawPath(Path().apply { moveTo(s * 0.36f, s * 0.26f); lineTo(s * 0.5f, s * 0.12f); lineTo(s * 0.64f, s * 0.26f) }, line(_orange, 0.07f));
    val dot = p(0.5f, 0.76f);
    c.drawCircle(dot, s * 0.22f, line((_orange).copy(alpha = 0.3f), 0.025f));
    c.drawCircle(dot, s * 0.17f, paint((_orange).copy(alpha = 0.2f)));
    lens(dot, s * 0.11f, _orange);
  }

  // ── Навигация и мир ──

  fun _compass() {
    val body = Path().apply { addOval(rectFromCircle(p(0.5f, 0.5f), s * 0.38f)) };
    glass(body);
    c.drawCircle(p(0.5f, 0.5f), s * 0.38f, line(_blue, 0.05f));
    rotated(p(0.5f, 0.5f), 40) {
      c.drawPath(polygon(listOf(p(0.5f, 0.22f), p(0.58f, 0.5f), p(0.42f, 0.5f))), paint(_red));
      c.drawPath(polygon(listOf(p(0.5f, 0.78f), p(0.58f, 0.5f), p(0.42f, 0.5f))), paint(if (dark) (_white).copy(alpha = 0.8f) else Color(0xFF9094A0)));
    }
    c.drawCircle(p(0.5f, 0.5f), s * 0.035f, paint(if (dark) _white else Color(0xFF3A3C43)));
  }

  fun _browser() {
    val body = rrPath(r(0.1f, 0.18f, 0.8f, 0.64f), 0.09f);
    glass(body, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    c.drawRRect(rr(r(0.1f, 0.18f, 0.8f, 0.15f), 0.07f), paint((_blue).copy(alpha = 0.9f)));
    for (i in 0 until 3) {
      c.drawCircle(p(0.2f + i * 0.07f, 0.255f), s * 0.017f, paint((_white).copy(alpha = 0.85f)));
    }
    c.drawLine(p(0.5f, 0.74f), p(0.5f, 0.48f), line(_blue, 0.06f));
    c.drawPath(Path().apply { moveTo(s * 0.4f, s * 0.57f); lineTo(s * 0.5f, s * 0.47f); lineTo(s * 0.6f, s * 0.57f) }, line(_blue, 0.06f));
  }

  fun _trayDown() {
    val mon = rrPath(r(0.1f, 0.14f, 0.8f, 0.5f), 0.07f);
    glass(mon, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    val screen = r(0.15f, 0.19f, 0.7f, 0.4f);
    screenGlass(screen, 0.04f);
    for (i in 0 until 5) {
      ledDot(Offset(screen.left + s * (0.1f + i * 0.12f), screen.center.dy), s * 0.016f, on = i < 3);
    }
    c.drawLine(p(0.5f, 0.68f), p(0.5f, 0.86f), line(_teal, 0.06f));
    c.drawPath(Path().apply { moveTo(s * 0.4f, s * 0.77f); lineTo(s * 0.5f, s * 0.87f); lineTo(s * 0.6f, s * 0.77f) }, line(_teal, 0.06f));
  }

  // ── Тексты и данные ──

  fun _book() {
    val cover = rrPath(r(0.08f, 0.2f, 0.84f, 0.64f), 0.07f);
    obj(cover, listOf(tint(_blue, 0.1f, _white), tint(_blue, 0.45f)), glow = _blue);
    for (left in listOf(true, false)) {
      val page = polygon(if (left)
          listOf(p(0.14f, 0.26f), p(0.48f, 0.32f), p(0.48f, 0.8f), p(0.14f, 0.74f))
          else listOf(p(0.86f, 0.26f), p(0.52f, 0.32f), p(0.52f, 0.8f), p(0.86f, 0.74f)));
      c.drawPath(page, grad(page.getBounds(), if (dark) listOf(Color(0xFFE9EBF0), Color(0xFFC4C8D2)) else listOf(_white, Color(0xFFE6E8EC))));
      for (i in 0 until 3) {
        val y = 0.42f + i * 0.1f;
        val x0 = if (left) 0.2f else 0.58f; val x1 = if (left) 0.42f else 0.8f;
        c.drawLine(p(x0, y), p(x1, y + (if (left) 0.01f else -0.01f)), line((_blue).copy(alpha = 0.5f), 0.025f));
      }
    }
    c.drawLine(p(0.5f, 0.3f), p(0.5f, 0.8f), line((_black).copy(alpha = 0.25f), 0.015f));
  }

  fun _document() {
    val sheet = Path().apply { moveTo(s * 0.2f, s * 0.1f); lineTo(s * 0.62f, s * 0.1f); lineTo(s * 0.82f, s * 0.3f); lineTo(s * 0.82f, s * 0.9f); lineTo(s * 0.2f, s * 0.9f); close() };
    glass(sheet, b = Alignment.TopStart, e = Alignment.BottomEnd);
    c.drawPath(polygon(listOf(p(0.62f, 0.1f), p(0.62f, 0.3f), p(0.82f, 0.3f))), paint((_blue).copy(alpha = 0.85f)));
    for (i in 0 until 4) {
      c.drawLine(p(0.3f, 0.46f + i * 0.12f), p(if (i == 3) 0.55f else 0.72f, 0.46f + i * 0.12f), line(if (i == 0) _blue else inkSoft, 0.04f));
    }
  }

  fun _database() {
    for (i in 0 until 3) {
      val slab = rrPath(r(0.12f, 0.14f + i * 0.26f, 0.76f, 0.2f), 0.07f);
      obj(slab, if (i == 0) listOf(tint(_cyan, 0.15f, _white), tint(_cyan, 0.4f)) else (if (dark) listOf(Color(0xFF3A3C43), Color(0xFF1E1F23)) else listOf(_white, Color(0xFFE2E4E8))), glow = if (i == 0) _cyan else null);
      ledDot(p(0.24f, 0.24f + i * 0.26f), s * 0.025f, color = if (i == 0) _white else led, on = i < 2);
      c.drawLine(p(0.4f, 0.24f + i * 0.26f), p(0.76f, 0.24f + i * 0.26f), line(if (i == 0) (_white).copy(alpha = 0.6f) else inkSoft, 0.03f));
    }
  }

  fun _picture(slash: Boolean = false) {
    val frame = r(0.08f, 0.18f, 0.84f, 0.64f);
    val body = rrPath(frame, 0.1f);
    glass(body);
    c.withClip(body) {
    c.drawPath(polygon(listOf(p(0.08f, 0.82f), p(0.34f, 0.5f), p(0.52f, 0.7f), p(0.64f, 0.56f), p(0.92f, 0.82f))), paint(if (dark) Color(0xFF4A4D58) else Color(0xFFBCC0C9)));
    }
    lens(p(0.7f, 0.36f), s * 0.07f, _pink);
  }

  fun _photos() {
    rotated(p(0.5f, 0.5f), -10) {
      val back = rrPath(r(0.16f, 0.12f, 0.62f, 0.54f), 0.08f);
      obj(back, listOf(tint(_pink, 0.1f, _white), tint(_pink, 0.35f)), glow = _pink);
    }
    val front = r(0.22f, 0.3f, 0.64f, 0.54f);
    val body = rrPath(front, 0.08f);
    glass(body);
    c.withClip(body) {
    c.drawPath(polygon(listOf(p(0.22f, 0.84f), p(0.42f, 0.58f), p(0.56f, 0.74f), p(0.66f, 0.62f), p(0.86f, 0.84f))), paint(if (dark) Color(0xFF4A4D58) else Color(0xFFBCC0C9)));
    }
    lens(p(0.7f, 0.44f), s * 0.055f, _amber);
  }

  fun _textAa() {
    val body = rrPath(r(0.1f, 0.16f, 0.8f, 0.68f), 0.12f)
    glass(body, b = Alignment.TopCenter, e = Alignment.BottomCenter)
    c.drawText("A", s * 0.22f, s * 0.58f, s * 0.42f, _violet, bold = true)
    c.drawText("a", s * 0.54f, s * 0.62f, s * 0.30f, inkSoft, bold = true)
  }

  fun _bubble(text: String, hue: Color) {
    val body = Path().apply { addRRect(rr(r(0.1f, 0.16f, 0.8f, 0.56f), 0.16f)); moveTo(s * 0.3f, s * 0.7f); lineTo(s * 0.26f, s * 0.88f); lineTo(s * 0.46f, s * 0.72f); close() };
    obj(body, listOf(tint(hue, 0.15f, _white), tint(hue, 0.4f)), glow = hue);
    _dotText(text, p(0.5f, 0.44f), s * 0.047f);
  }

  fun _sms() {
    val body = Path().apply { addRRect(rr(r(0.1f, 0.16f, 0.8f, 0.56f), 0.16f)); moveTo(s * 0.3f, s * 0.7f); lineTo(s * 0.26f, s * 0.88f); lineTo(s * 0.46f, s * 0.72f); close() };
    obj(body, listOf(tint(_cyan, 0.15f, _white), tint(_cyan, 0.4f)), glow = _cyan);
    for (i in 0 until 3) {
      c.drawCircle(p(0.3f + i * 0.2f, 0.44f), s * 0.045f, paint(_white));
    }
  }

  fun _plane() {
    val wing = polygon(listOf(p(0.1f, 0.46f), p(0.9f, 0.14f), p(0.64f, 0.86f), p(0.46f, 0.6f)));
    obj(wing, listOf(tint(_cyan, 0.1f, _white), tint(_blue, 0.2f)), b = Alignment.TopStart, e = Alignment.BottomEnd, glow = _blue);
    c.drawPath(polygon(listOf(p(0.46f, 0.6f), p(0.9f, 0.14f), p(0.5f, 0.78f))), paint((_black).copy(alpha = if (dark) 0.28f else 0.16f)));
    c.drawLine(p(0.46f, 0.6f), p(0.9f, 0.14f), line((_white).copy(alpha = 0.5f), 0.012f));
  }

  fun _inbox() {
    val tray = Path().apply { moveTo(s * 0.1f, s * 0.56f); lineTo(s * 0.26f, s * 0.3f); lineTo(s * 0.74f, s * 0.3f); lineTo(s * 0.9f, s * 0.56f); lineTo(s * 0.9f, s * 0.8f); lineTo(s * 0.1f, s * 0.8f); close() };
    glass(tray, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    c.drawPath(Path().apply { moveTo(s * 0.1f, s * 0.56f); lineTo(s * 0.36f, s * 0.56f); lineTo(s * 0.42f, s * 0.66f); lineTo(s * 0.58f, s * 0.66f); lineTo(s * 0.64f, s * 0.56f); lineTo(s * 0.9f, s * 0.56f) }, line(inkSoft, 0.025f));
    c.drawLine(p(0.5f, 0.1f), p(0.5f, 0.42f), line(_cyan, 0.07f));
    c.drawPath(Path().apply { moveTo(s * 0.4f, s * 0.32f); lineTo(s * 0.5f, s * 0.44f); lineTo(s * 0.6f, s * 0.32f) }, line(_cyan, 0.07f));
  }

  /// «Почта и пароль»: конверт с оранжевым клапаном и табло пароля из трёх точек поверх угла.
  fun _mail() {
    val frame = r(0.06f, 0.2f, 0.88f, 0.58f);
    val body = rrPath(frame, 0.1f);
    glass(body, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    c.withClip(body) {
    val flap = polygon(listOf(p(0.0f, 0.12f), p(1.0f, 0.12f), p(0.5f, 0.56f)));
    c.drawPath(flap, grad(flap.getBounds(), listOf(Color(0xFFFFC46B), _orange)));
    }
    c.drawPath(body, line(if (dark) (_white).copy(alpha = 0.12f) else (_black).copy(alpha = 0.07f), 0.012f));
    val pass = r(0.46f, 0.56f, 0.5f, 0.28f);
    c.drawRRect(rr(pass.inflate(s * 0.03f), 0.16f), paint(if (dark) Color(0xFF1A1B1F) else _white));
    c.drawRRect(rr(pass, 0.13f), paint(Color(0xFF0A0B0E)));
    for (i in 0 until 3) {
      ledDot(Offset(pass.left + s * (0.1f + i * 0.15f), pass.center.dy), s * 0.034f, color = _green);
    }
  }

  fun _megaphone() {
    val horn = polygon(listOf(p(0.16f, 0.4f), p(0.58f, 0.18f), p(0.58f, 0.78f), p(0.16f, 0.6f)));
    obj(horn, listOf(tint(_orange, 0.1f, _white), _orange), b = Alignment.TopStart, e = Alignment.BottomEnd, glow = _orange);
    val base = rrPath(r(0.08f, 0.38f, 0.16f, 0.24f), 0.05f);
    glass(base);
    val grip = rrPath(r(0.22f, 0.6f, 0.12f, 0.26f), 0.04f);
    glass(grip);
    for (i in 0 until 2) {
      val x = 0.68f + i * 0.1f;
      c.drawPath(Path().apply { moveTo(s * x, s * (0.38f - i * 0.02f)); quadraticBezierTo(s * (x + 0.07f), s * 0.48f, s * x, s * (0.58f + i * 0.02f)) }, line((_orange).copy(alpha = 1 - i * 0.4f), 0.045f));
    }
  }

  fun _magnifier() {
    val handle = Path().apply { moveTo(s * 0.64f, s * 0.64f); lineTo(s * 0.86f, s * 0.86f) };
    c.drawPath(handle.shift(Offset(0f, s * 0.04f)), line((_black).copy(alpha = if (dark) 0.5f else 0.2f), 0.12f));
    c.drawPath(handle, line(if (dark) Color(0xFF50535E) else Color(0xFF8A8D99), 0.12f));
    val ring = rectFromCircle(p(0.44f, 0.44f), s * 0.28f);
    c.drawCircle(p(0.44f, 0.44f), s * 0.28f, paint((_cyan).copy(alpha = if (dark) 0.18f else 0.14f)));
    c.drawOval(ring, line(Color(0xFFB8BCC6), 0.075f));
    c.drawOval(ring.deflate(s * 0.04f), line((_cyan).copy(alpha = 0.7f), 0.025f));
  }

  // ── Люди ──

  fun _avatars(count: Int = 1, plus: Boolean = false) {
    fun one(o: Offset, k: Float, body: List<Color>) {
      val head = Path().apply { addOval(rectFromCircle(o + Offset(0f, -s * 0.12f * k), s * 0.15f * k)) }
      val bust = Path().apply { addArc(rectFromCenter(o + Offset(0f, s * 0.3f * k), s * 0.56f * k, s * 0.6f * k), 180f, 180f); close() }
      obj(bust, body, glow = body.last())
      obj(head, listOf(tint(body.first(), 0.1f, _white), body.first()))
    }

    if (count == 2) {
      one(p(0.64f, 0.4f), 0.9f, listOf(tint(_blue, 0.1f, _white), tint(_blue, 0.3f)))
      one(p(0.36f, 0.46f), 1f, listOf(tint(_violet, 0.1f, _white), tint(_violet, 0.3f)))
    } else {
      one(p(if (plus) 0.42f else 0.5f, 0.42f), 1.05f, listOf(tint(_violet, 0.1f, _white), tint(_violet, 0.3f)))
    }
    if (plus) {
      val badge = p(0.76f, 0.72f)
      c.drawCircle(badge, s * 0.17f, paint(if (dark) Color(0xFF1A1B1F) else _white))
      lens(badge, s * 0.13f, _green)
      c.drawLine(badge + Offset(-s * 0.06f, 0f), badge + Offset(s * 0.06f, 0f), line(_white, 0.04f))
      c.drawLine(badge + Offset(0f, -s * 0.06f), badge + Offset(0f, s * 0.06f), line(_white, 0.04f))
    }
  }

  fun _google() {
    val center = p(0.5f, 0.5f);
    val ringR = s * 0.275f; val w = s * 0.15f;
    val box = rectFromCircle(center, ringR);
    shadow(Path().apply { addOval(rectFromCircle(center, s * 0.4f)) });
    c.drawCircle(center, s * 0.4f, paint(if (dark) Color(0xFFF1F2F4) else _white));
    c.drawCircle(center, s * 0.4f, line((_black).copy(alpha = if (dark) 0.0f else 0.07f), 0.012f));
    fun arc(col: Color) = LineStroke(col, w, StrokeCap.Butt);
    val barTopRad = asin(((w / 2f) / ringR).clamp(0.0f, 1.0f));
    val barTopDeg = barTopRad * 180f / PI_F;
    c.drawArc(box, 222f, 93f, false, arc(Color(0xFFEA4335)));
    c.drawArc(box, 132f, 90f, false, arc(Color(0xFFFBBC05)));
    c.drawArc(box, 42f, 90f, false, arc(Color(0xFF34A853)));
    c.drawArc(box, -barTopDeg, barTopDeg + 42f, false, arc(Color(0xFF4285F4)));
    // Перекладина подрезана по внешнему радиусу кольца, иначе её угол торчит за дугу
    c.withClip(Path().apply { addOval(rectFromCircle(center, ringR + w / 2)) }) {
    c.drawRect(Rect(center.dx - s * 0.005f, center.dy - w / 2, center.dx + ringR + w, center.dy + w / 2), paint(Color(0xFF4285F4)));
    }
  }

  // ── Замки и защита ──

  fun _shield() {
    val body = shieldPath();
    obj(body, listOf(Color(0xFF6FE3A8), _teal), b = Alignment.TopStart, e = Alignment.BottomEnd, glow = _green);
    c.drawPath(check(0.36f, 0.5f, 0.47f, 0.61f, 0.66f, 0.4f), line(_white, 0.075f));
  }

  fun _seal() {
    val center = p(0.5f, 0.5f);
    val path = Path();
    val n = 10;
    for (i in 0 until n * 2) {
      val a = -PI_F / 2 + i * PI_F / n;
      val rad = s * (if (i % 2 == 0) 0.4f else 0.34f);
      val o = center + Offset(cos(a), sin(a)) * rad;
      if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y);
    }
    path.close();
    obj(path, listOf(tint(_blue, 0.15f, _white), tint(_blue, 0.3f)), glow = _blue);
    c.drawPath(check(0.35f, 0.51f, 0.46f, 0.62f, 0.66f, 0.4f), line(_white, 0.08f));
  }

  fun _adminShield() {
    val body = shieldPath();
    obj(body, listOf(Color(0xFFFFB27A), _red), b = Alignment.TopStart, e = Alignment.BottomEnd, glow = _red);
    val gear = Path();
    for (i in 0 until 16) {
      val a = i * PI_F / 8;
      val rad = s * (if (i % 2 == 0) 0.17f else 0.13f);
      val o = p(0.5f, 0.48f) + Offset(cos(a), sin(a)) * rad;
      if (i == 0) gear.moveTo(o.x, o.y) else gear.lineTo(o.x, o.y);
    }
    gear.close();
    c.drawPath(gear, paint(_white));
    c.drawCircle(p(0.5f, 0.48f), s * 0.06f, paint(tint(_red, 0.2f)));
  }

  fun _padlock(reset: Boolean = false) {
    val shackle = Path().apply { moveTo(s * 0.34f, s * 0.46f); lineTo(s * 0.34f, s * 0.34f); cubicTo(s * 0.34f, s * 0.18f, s * 0.66f, s * 0.18f, s * 0.66f, s * 0.34f); lineTo(s * 0.66f, s * 0.46f) };
    c.drawPath(shackle, line(if (dark) Color(0xFFB8BCC6) else Color(0xFF8A8D99), 0.09f));
    val body = rrPath(r(0.22f, 0.44f, 0.56f, 0.44f), 0.1f);
    obj(body, listOf(tint(_amber, 0.15f, _white), tint(_amber, 0.15f)), glow = _amber);
    c.drawCircle(p(0.5f, 0.62f), s * 0.05f, paint(tint(_amber, 0.6f)));
    c.drawLine(p(0.5f, 0.64f), p(0.5f, 0.74f), line(tint(_amber, 0.6f), 0.05f));
    if (reset) {
      val badge = p(0.76f, 0.76f);
      c.drawCircle(badge, s * 0.18f, paint(if (dark) Color(0xFF1A1B1F) else _white));
      lens(badge, s * 0.14f, _teal);
      c.drawArc(rectFromCircle(badge, s * 0.07f), -PI_F * 0.2f, PI_F * 1.5f, false, line(_white, 0.035f));
      c.drawPath(polygon(listOf(badge + Offset(s * 0.075f, -s * 0.07f), badge + Offset(s * 0.11f, -s * 0.02f), badge + Offset(s * 0.04f, -s * 0.01f))), paint(_white));
    }
  }

  fun _fingerprint() {
    val body = Path().apply { addOval(rectFromCircle(p(0.5f, 0.5f), s * 0.4f)) };
    glass(body);
    val o = p(0.5f, 0.5f);
    for (i in 0 until 4) {
      val rad = s * (0.1f + i * 0.07f);
      c.drawArc(rectFromCircle(o, rad), PI_F * 1.1f + i * 0.08f, PI_F * (1.45f - i * 0.05f), false, line(if (i % 2 == 0) _green else (_green).copy(alpha = 0.7f), 0.04f));
    }
    ledDot(o, s * 0.025f, color = _green);
  }

  fun _pinpad() {
    val pill = rrPath(r(0.08f, 0.34f, 0.84f, 0.32f), 0.14f);
    glass(pill, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    screenGlass(r(0.13f, 0.39f, 0.74f, 0.22f), 0.09f);
    for (i in 0 until 4) {
      ledDot(p(0.26f + i * 0.16f, 0.5f), s * 0.03f, color = _green, on = i < 3);
    }
  }

  // ── Корзина, выход, мётлы ──

  fun _door() {
    val frame = rrPath(r(0.14f, 0.12f, 0.46f, 0.76f), 0.06f);
    glass(frame, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    val inner = polygon(listOf(p(0.2f, 0.18f), p(0.4f, 0.24f), p(0.4f, 0.78f), p(0.2f, 0.82f)));
    c.drawPath(inner, paint((_red).copy(alpha = if (dark) 0.85f else 0.75f)));
    c.drawCircle(p(0.35f, 0.52f), s * 0.02f, paint(_white));
    c.drawLine(p(0.48f, 0.5f), p(0.86f, 0.5f), line(_red, 0.07f));
    c.drawPath(Path().apply { moveTo(s * 0.74f, s * 0.38f); lineTo(s * 0.88f, s * 0.5f); lineTo(s * 0.74f, s * 0.62f) }, line(_red, 0.07f));
  }

  fun _trash(x: Boolean = false) {
    val body = Path().apply { moveTo(s * 0.24f, s * 0.3f); lineTo(s * 0.76f, s * 0.3f); lineTo(s * 0.7f, s * 0.88f); lineTo(s * 0.3f, s * 0.88f); close() };
    obj(body, listOf(Color(0xFFFF6B8A), Color(0xFFC72A4B)), glow = _red);
    val lid = rrPath(r(0.18f, 0.2f, 0.64f, 0.1f), 0.04f);
    obj(lid, listOf(Color(0xFFFF8CA3), Color(0xFFE0405A)));
    c.drawPath(Path().apply { moveTo(s * 0.4f, s * 0.2f); lineTo(s * 0.4f, s * 0.14f); lineTo(s * 0.6f, s * 0.14f); lineTo(s * 0.6f, s * 0.2f) }, line((_red).copy(alpha = 0.9f), 0.045f));
    if (x) {
      c.drawLine(p(0.41f, 0.46f), p(0.59f, 0.72f), line(_white, 0.055f));
      c.drawLine(p(0.59f, 0.46f), p(0.41f, 0.72f), line(_white, 0.055f));
    } else {
      for (i in 0 until 3) {
        c.drawLine(p(0.38f + i * 0.12f, 0.44f), p(0.39f + i * 0.11f, 0.78f), line((_white).copy(alpha = 0.6f), 0.035f));
      }
    }
  }

  fun _broom() {
    rotated(p(0.5f, 0.5f), 38) {
      c.drawLine(p(0.5f, 0.04f), p(0.5f, 0.56f), line(if (dark) Color(0xFF8A8D99) else Color(0xFF6B6E78), 0.07f));
      val head = Path().apply { moveTo(s * 0.34f, s * 0.54f); lineTo(s * 0.66f, s * 0.54f); lineTo(s * 0.74f, s * 0.92f); lineTo(s * 0.26f, s * 0.92f); close() };
      obj(head, listOf(Color(0xFFFFC46B), _orange), glow = _orange);
      for (i in 0 until 4) {
        c.drawLine(p(0.36f + i * 0.09f, 0.66f), p(0.34f + i * 0.1f, 0.9f), line((_white).copy(alpha = 0.45f), 0.02f));
      }
    }
    for (o in listOf(p(0.2f, 0.3f), p(0.14f, 0.54f))) {
      c.drawPath(star(o, s * 0.07f, s * 0.02f, points = 4), paint(_amber));
    }
  }

  fun _unlink() {
    for (k in listOf(0, 1)) {
      rotated(p(0.5f, 0.5f), -45) {
        val link = rrPath(r(if (k == 0) 0.08f else 0.5f, 0.36f, 0.42f, 0.28f), 0.14f);
        c.drawPath(link.shift(Offset(0f, s * 0.03f)), line((_black).copy(alpha = if (dark) 0.5f else 0.2f), 0.09f));
        c.drawPath(link, line(if (dark) Color(0xFFB8BCC6) else Color(0xFF8A8D99), 0.09f));
      }
    }
    val cut = Path().apply { moveTo(s * 0.4f, s * 0.62f); lineTo(s * 0.6f, s * 0.38f) };
    c.drawPath(cut, line(if (dark) Color(0xFF1A1B1F) else _white, 0.17f));
    c.drawPath(cut, line(_red, 0.075f));
  }

  fun _eyeOff() {
    val eye = Path().apply { moveTo(s * 0.08f, s * 0.5f); quadraticBezierTo(s * 0.5f, s * 0.12f, s * 0.92f, s * 0.5f); quadraticBezierTo(s * 0.5f, s * 0.88f, s * 0.08f, s * 0.5f); close() };
    glass(eye, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    lens(p(0.5f, 0.5f), s * 0.17f, _violet);
    c.drawCircle(p(0.5f, 0.5f), s * 0.06f, paint(Color(0xFF0A0B0E)));
    val cut = Path().apply { moveTo(s * 0.18f, s * 0.82f); lineTo(s * 0.82f, s * 0.18f) };
    c.drawPath(cut, line(if (dark) Color(0xFF1A1B1F) else _white, 0.15f));
    c.drawPath(cut, line(_red, 0.075f));
  }

  // ── Переключатели, режимы, цвет ──

  fun _toggle(on: Boolean = true) {
    val track = rrPath(r(0.08f, 0.3f, 0.84f, 0.4f), 0.2f);
    if (on) {
      obj(track, listOf(tint(_teal, 0.15f, _white), tint(_teal, 0.25f)), glow = _teal);
    } else {
      glass(track, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    }
    val x = if (on) 0.7f else 0.3f;
    lens(p(x, 0.5f), s * 0.15f, if (on) _white else (if (dark) Color(0xFF8A8D99) else Color(0xFFB8BCC6)));
    for (i in 0 until 3) {
      val cx = if (on) 0.22f + i * 0.1f else 0.54f + i * 0.1f;
      ledDot(p(cx, 0.5f), s * 0.018f, color = if (on) _white else led, on = on);
    }
  }

  fun _sun() {
    for (i in 0 until 8) {
      val a = i * PI_F / 4;
      c.drawLine(p(0.5f, 0.5f) + Offset(cos(a), sin(a)) * s * 0.3f, p(0.5f, 0.5f) + Offset(cos(a), sin(a)) * s * 0.4f, line(_amber, 0.06f));
    }
    c.drawCircle(p(0.5f, 0.5f), s * 0.26f, paint((_amber).copy(alpha = if (dark) 0.3f else 0.25f)));
    lens(p(0.5f, 0.5f), s * 0.2f, _amber);
  }

  fun moonPath(center: Offset, radius: Float): Path = Path().apply {
    op(
      Path().apply { addOval(rectFromCircle(center, radius)) },
      Path().apply { addOval(rectFromCircle(center + Offset(radius * 0.55f, -radius * 0.35f), radius * 0.85f)) },
      PathOperation.Difference
    )
  }

  fun _moon() {
    val moon = moonPath(p(0.46f, 0.54f), s * 0.34f);
    obj(moon, listOf(tint(_violet, 0.2f, _white), tint(_violet, 0.3f)), b = Alignment.TopStart, e = Alignment.BottomEnd, glow = _violet);
    for (o in listOf(p(0.72f, 0.28f), p(0.84f, 0.46f))) {
      c.drawPath(star(o, s * 0.06f, s * 0.02f, points = 4), paint(if (dark) (_white).copy(alpha = 0.9f) else _violet));
    }
  }

  fun _sunMoon() {
    val disc = Path().apply { addOval(rectFromCircle(p(0.5f, 0.5f), s * 0.36f)) };
    shadow(disc);
    c.withClip(disc) {
    c.drawRect(Rect(0f, 0f, s * 0.5f, s), grad(Rect(0f, 0f, s * 0.5f, s), listOf(Color(0xFFFFD36B), _amber)));
    c.drawRect(Rect(s * 0.5f, 0f, s, s), grad(Rect(s * 0.5f, 0f, s, s), listOf(tint(_violet, 0.1f, _white), tint(_violet, 0.35f))));
    }
    c.drawLine(p(0.5f, 0.16f), p(0.5f, 0.84f), line((_white).copy(alpha = 0.5f), 0.02f));
    c.drawCircle(p(0.5f, 0.5f), s * 0.36f, line((_white).copy(alpha = if (dark) 0.2f else 0.4f), 0.015f));
  }

  fun _layers() {
    for (i in 2 downTo 0) {
      val y = 0.2f + i * 0.17f;
      val plate = polygon(listOf(p(0.5f, y), p(0.9f, y + 0.17f), p(0.5f, y + 0.34f), p(0.1f, y + 0.17f)));
      val cols = if (i == 0) listOf(tint(_teal, 0.2f, _white), tint(_teal, 0.1f)) else (if (dark) listOf(Color(0xFF4A4D58), Color(0xFF26272C)) else listOf(_white, Color(0xFFD5D8DE)));
      obj(plate, cols, glow = if (i == 0) _teal else null);
    }
  }

  fun _sparkles() {
    val big = star(p(0.46f, 0.54f), s * 0.34f, s * 0.09f, points = 4);
    obj(big, listOf(tint(_amber, 0.15f, _white), _pink), b = Alignment.TopStart, e = Alignment.BottomEnd, glow = _pink);
    c.drawPath(star(p(0.8f, 0.22f), s * 0.12f, s * 0.035f, points = 4), paint(_amber));
    c.drawPath(star(p(0.82f, 0.78f), s * 0.08f, s * 0.025f, points = 4), paint(_pink));
  }

  fun _chip() {
    for (i in 0 until 4) {
      val v = 0.28f + i * 0.15f;
      c.drawLine(p(v, 0.12f), p(v, 0.88f), line(if (dark) Color(0xFF6B6E78) else Color(0xFFB8BCC6), 0.045f));
      c.drawLine(p(0.12f, v), p(0.88f, v), line(if (dark) Color(0xFF6B6E78) else Color(0xFFB8BCC6), 0.045f));
    }
    val die = rrPath(r(0.22f, 0.22f, 0.56f, 0.56f), 0.09f);
    glass(die, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    screenGlass(r(0.3f, 0.3f, 0.4f, 0.4f), 0.05f);
    ledDot(p(0.5f, 0.5f), s * 0.045f);
    for (o in listOf(p(0.38f, 0.38f), p(0.62f, 0.38f), p(0.38f, 0.62f), p(0.62f, 0.62f))) {
      ledDot(o, s * 0.014f, on = false);
    }
  }

  fun _gauge() {
    val dial = Path().apply { addOval(rectFromCircle(p(0.5f, 0.54f), s * 0.38f)) }
    glass(dial)
    val box = rectFromCircle(p(0.5f, 0.54f), s * 0.28f)
    c.drawArc(box, 144f, 252f, false, LineStroke(brush = Brush.sweepGradient(listOf(_green, _amber, _red), center = box.center), width = s * 0.06f))
    rotated(p(0.5f, 0.54f), 32) { c.drawLine(p(0.5f, 0.54f), p(0.5f, 0.3f), line(ink, 0.04f)) }
    ledDot(p(0.5f, 0.54f), s * 0.035f)
  }

  fun _cloud(storage: Boolean = false) {
    // Круги склеены в один контур: иначе кромка обводит каждый круг и внутри облака видны швы
    var cloud = Path().apply { addOval(rectFromCircle(p(0.34f, 0.58f), s * 0.2f)) };
    for (part in listOf(
      Path().apply { addOval(rectFromCircle(p(0.52f, 0.42f), s * 0.25f)) },
      Path().apply { addOval(rectFromCircle(p(0.7f, 0.58f), s * 0.2f)) },
      Path().apply { addRRect(rr(r(0.3f, 0.52f, 0.42f, 0.26f), 0.1f)) },
    )) {
      cloud = Path().apply { op(cloud, part, PathOperation.Union) };
    }
    obj(cloud, listOf(tint(_cyan, 0.15f, _white), tint(_cyan, 0.3f)), glow = _cyan);
    if (storage) {
      c.drawLine(p(0.5f, 0.7f), p(0.5f, 0.46f), line(_white, 0.06f));
      c.drawPath(Path().apply { moveTo(s * 0.4f, s * 0.55f); lineTo(s * 0.5f, s * 0.45f); lineTo(s * 0.6f, s * 0.55f) }, line(_white, 0.06f));
    } else {
      c.drawPath(check(0.4f, 0.58f, 0.48f, 0.66f, 0.62f, 0.5f), line(_white, 0.06f));
    }
  }

  fun _drive() {
    val w = 0.17f;
    fun seg(col: Color) = line(col, w);
    c.drawPath(Path().apply { moveTo(s * 0.5f, s * 0.2f); lineTo(s * 0.18f, s * 0.74f) }, seg(Color(0xFF0F9D58)));
    c.drawPath(Path().apply { moveTo(s * 0.5f, s * 0.2f); lineTo(s * 0.82f, s * 0.74f) }, seg(Color(0xFFF4B400)));
    c.drawPath(Path().apply { moveTo(s * 0.18f, s * 0.76f); lineTo(s * 0.82f, s * 0.76f) }, seg(Color(0xFF4285F4)));
  }

  fun _flask() {
    val body = Path().apply { moveTo(s * 0.4f, s * 0.12f); lineTo(s * 0.6f, s * 0.12f); lineTo(s * 0.6f, s * 0.38f); lineTo(s * 0.84f, s * 0.8f); quadraticBezierTo(s * 0.88f, s * 0.9f, s * 0.76f, s * 0.9f); lineTo(s * 0.24f, s * 0.9f); quadraticBezierTo(s * 0.12f, s * 0.9f, s * 0.16f, s * 0.8f); lineTo(s * 0.4f, s * 0.38f); close() };
    glass(body, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    c.withClip(body) {
    c.drawRect(r(0f, 0.6f, 1f, 0.4f), grad(r(0f, 0.6f, 1f, 0.4f), listOf(tint(_green, 0.1f, _white), _teal), b = Alignment.TopCenter, e = Alignment.BottomCenter));
    }
    c.drawCircle(p(0.44f, 0.7f), s * 0.03f, paint((_white).copy(alpha = 0.8f)));
    c.drawCircle(p(0.58f, 0.78f), s * 0.02f, paint((_white).copy(alpha = 0.8f)));
    c.drawLine(p(0.36f, 0.12f), p(0.64f, 0.12f), line(if (dark) Color(0xFFB8BCC6) else Color(0xFF8A8D99), 0.05f));
  }

  fun _sync() {
    val disc = Path().apply { addOval(rectFromCircle(p(0.5f, 0.5f), s * 0.4f)) };
    obj(disc, listOf(tint(_cyan, 0.15f, _white), tint(_blue, 0.2f)), glow = _blue);
    val box = rectFromCircle(p(0.5f, 0.5f), s * 0.2f);
    c.drawArc(box, -PI_F * 0.1f, PI_F * 0.85f, false, line(_white, 0.06f));
    c.drawArc(box, PI_F * 0.9f, PI_F * 0.85f, false, line(_white, 0.06f));
    c.drawPath(polygon(listOf(p(0.7f, 0.34f), p(0.78f, 0.5f), p(0.62f, 0.48f))), paint(_white));
    c.drawPath(polygon(listOf(p(0.3f, 0.66f), p(0.22f, 0.5f), p(0.38f, 0.52f))), paint(_white));
  }

  fun _pulse() {
    val body = rrPath(r(0.08f, 0.2f, 0.84f, 0.6f), 0.12f)
    glass(body, b = Alignment.TopCenter, e = Alignment.BottomCenter)
    screenGlass(r(0.14f, 0.26f, 0.72f, 0.48f), 0.07f)
    val ecg = Path().apply { moveTo(s * 0.18f, s * 0.52f); lineTo(s * 0.34f, s * 0.52f); lineTo(s * 0.4f, s * 0.38f); lineTo(s * 0.48f, s * 0.64f); lineTo(s * 0.55f, s * 0.44f); lineTo(s * 0.6f, s * 0.52f); lineTo(s * 0.82f, s * 0.52f) }
    c.drawPath(ecg, line(_green.copy(alpha = 0.4f), 0.07f))
    c.drawPath(ecg, line(_green, 0.035f))
  }

  fun _gavel() {
    c.drawPath(rrPath(r(0.12f, 0.8f, 0.5f, 0.1f), 0.04f).shift(Offset(0f, s * 0.03f)), paint((_black).copy(alpha = if (dark) 0.5f else 0.2f)));
    c.drawPath(rrPath(r(0.12f, 0.8f, 0.5f, 0.1f), 0.04f), grad(r(0.12f, 0.8f, 0.5f, 0.1f), if (dark) listOf(Color(0xFF6B6E78), Color(0xFF3A3C43)) else listOf(Color(0xFFC9CCD3), Color(0xFF9094A0))));
    rotated(p(0.5f, 0.5f), -40) {
      c.drawLine(p(0.5f, 0.42f), p(0.5f, 0.92f), line(Color(0xFFB0753A), 0.08f));
      val head = rrPath(r(0.22f, 0.12f, 0.56f, 0.3f), 0.07f);
      obj(head, listOf(Color(0xFFFFC46B), Color(0xFFC77A24)), glow = _amber);
      c.drawLine(p(0.32f, 0.12f), p(0.32f, 0.42f), line((_black).copy(alpha = 0.2f), 0.02f));
      c.drawLine(p(0.68f, 0.12f), p(0.68f, 0.42f), line((_black).copy(alpha = 0.2f), 0.02f));
    }
  }

  /// Версия приложения: синяя бирка с отверстием, галочкой-«v» и тремя точками номера.
  fun _info() {
    rotated(p(0.5f, 0.5f), 14) {
      val tag = Path().apply { addRoundRect(RoundRect(r(0.2f, 0.08f, 0.6f, 0.84f), topLeft = CornerRadius(s * 0.26f, s * 0.26f), topRight = CornerRadius(s * 0.26f, s * 0.26f), bottomLeft = CornerRadius(s * 0.1f, s * 0.1f), bottomRight = CornerRadius(s * 0.1f, s * 0.1f))) }
      obj(tag, listOf(tint(_blue, 0.12f, _white), tint(_blue, 0.4f)), glow = _blue)
      c.drawCircle(p(0.5f, 0.25f), s * 0.06f, paint(Color(0xFF0A0B0E)))
      c.drawCircle(p(0.5f, 0.25f), s * 0.06f, line((_white).copy(alpha = 0.45f), 0.012f))
      c.drawPath(Path().apply { moveTo(s * 0.35f, s * 0.46f); lineTo(s * 0.5f, s * 0.66f); lineTo(s * 0.65f, s * 0.46f) }, line(_white, 0.09f))
      for (i in 0 until 3) {
        c.drawCircle(p(0.38f + i * 0.12f, 0.8f), s * 0.026f, paint((_white).copy(alpha = 0.6f)))
      }
    }
  }

  fun _warn() {
    val tri = Path().apply { moveTo(s * 0.5f, s * 0.12f); quadraticBezierTo(s * 0.56f, s * 0.12f, s * 0.6f, s * 0.2f); lineTo(s * 0.9f, s * 0.74f); quadraticBezierTo(s * 0.96f, s * 0.86f, s * 0.84f, s * 0.86f); lineTo(s * 0.16f, s * 0.86f); quadraticBezierTo(s * 0.04f, s * 0.86f, s * 0.1f, s * 0.74f); lineTo(s * 0.4f, s * 0.2f); quadraticBezierTo(s * 0.44f, s * 0.12f, s * 0.5f, s * 0.12f); close() };
    obj(tri, listOf(Color(0xFFFFD36B), Color(0xFFFF8A1F)), glow = _amber);
    c.drawLine(p(0.5f, 0.34f), p(0.5f, 0.58f), line(Color(0xFF5A3400), 0.08f));
    c.drawCircle(p(0.5f, 0.71f), s * 0.045f, paint(Color(0xFF5A3400)));
  }

  fun _bug() {
    val legs = line(tint(_red, 0.45f), 0.045f);
    for (sx in listOf(-1.0f, 1.0f)) {
      for (y in listOf(0.42f, 0.57f, 0.72f)) {
        val k = (y - 0.57f) * 1.4f;
        c.drawPath(Path().apply { moveTo(s * (0.5f + sx * 0.2f), s * y); lineTo(s * (0.5f + sx * 0.3f), s * (y + k * 0.35f)); lineTo(s * (0.5f + sx * 0.38f), s * (y + k)) }, legs);
      }
      c.drawPath(Path().apply { moveTo(s * (0.5f + sx * 0.06f), s * 0.2f); quadraticBezierTo(s * (0.5f + sx * 0.1f), s * 0.08f, s * (0.5f + sx * 0.2f), s * 0.07f) }, line(tint(_red, 0.45f), 0.035f));
    }
    val body = Path().apply { addOval(r(0.27f, 0.3f, 0.46f, 0.58f)) };
    obj(body, listOf(Color(0xFFFF8CA3), Color(0xFFC72A4B)), glow = _red);
    val head = Path().apply { addOval(rectFromCircle(p(0.5f, 0.29f), s * 0.13f)) };
    obj(head, listOf(Color(0xFF5A1230), Color(0xFF2E0A1A)));
    c.drawLine(p(0.5f, 0.42f), p(0.5f, 0.86f), line((_black).copy(alpha = 0.28f), 0.022f));
    for (o in listOf(p(0.4f, 0.55f), p(0.6f, 0.55f), p(0.41f, 0.72f), p(0.59f, 0.72f))) {
      c.drawCircle(o, s * 0.045f, paint(Color(0xFF5A1230)));
    }
  }

  fun _codeOff() {
    val body = rrPath(r(0.08f, 0.18f, 0.84f, 0.64f), 0.12f);
    glass(body, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    c.drawPath(Path().apply { moveTo(s * 0.36f, s * 0.36f); lineTo(s * 0.22f, s * 0.5f); lineTo(s * 0.36f, s * 0.64f) }, line(_violet, 0.06f));
    c.drawPath(Path().apply { moveTo(s * 0.64f, s * 0.36f); lineTo(s * 0.78f, s * 0.5f); lineTo(s * 0.64f, s * 0.64f) }, line(_violet, 0.06f));
    val cut = Path().apply { moveTo(s * 0.42f, s * 0.76f); lineTo(s * 0.58f, s * 0.24f) };
    c.drawPath(cut, line(if (dark) Color(0xFF1A1B1F) else _white, 0.14f));
    c.drawPath(cut, line(_red, 0.065f));
  }

  fun _robot() {
    c.drawLine(p(0.5f, 0.2f), p(0.5f, 0.1f), line(if (dark) Color(0xFFB8BCC6) else Color(0xFF8A8D99), 0.04f));
    ledDot(p(0.5f, 0.09f), s * 0.03f, color = _pink);
    for (x in listOf(0.08f, 0.84f)) {
      c.drawRRect(rr(r(x, 0.42f, 0.08f, 0.2f), 0.03f), paint((_violet).copy(alpha = 0.9f)));
    }
    val head = rrPath(r(0.16f, 0.2f, 0.68f, 0.6f), 0.16f);
    obj(head, listOf(tint(_violet, 0.15f, _white), tint(_violet, 0.3f)), glow = _violet);
    screenGlass(r(0.24f, 0.32f, 0.52f, 0.28f), 0.09f);
    ledDot(p(0.4f, 0.46f), s * 0.04f, color = _cyan);
    ledDot(p(0.6f, 0.46f), s * 0.04f, color = _cyan);
    for (i in 0 until 4) {
      c.drawCircle(p(0.38f + i * 0.08f, 0.7f), s * 0.015f, paint((_white).copy(alpha = 0.7f)));
    }
  }

  fun _masks() {
    rotated(p(0.5f, 0.5f), -12) {
      val back = Path().apply { addOval(r(0.1f, 0.16f, 0.5f, 0.6f)) };
      obj(back, listOf(tint(_pink, 0.1f, _white), tint(_pink, 0.3f)), glow = _pink);
      c.drawArc(r(0.2f, 0.5f, 0.3f, 0.16f), 0.3f, PI_F - 0.6f, false, line(_white, 0.035f));
      c.drawCircle(p(0.25f, 0.38f), s * 0.03f, paint(_white));
      c.drawCircle(p(0.45f, 0.38f), s * 0.03f, paint(_white));
    }
    rotated(p(0.5f, 0.5f), 12) {
      val front = Path().apply { addOval(r(0.4f, 0.28f, 0.5f, 0.6f)) };
      obj(front, listOf(tint(_violet, 0.1f, _white), tint(_violet, 0.3f)), glow = _violet);
      c.drawArc(r(0.5f, 0.7f, 0.3f, 0.14f), PI_F + 0.3f, PI_F - 0.6f, false, line(_white, 0.035f));
      c.drawCircle(p(0.55f, 0.5f), s * 0.03f, paint(_white));
      c.drawCircle(p(0.75f, 0.5f), s * 0.03f, paint(_white));
    }
  }

  fun _star() {
    val shape = star(p(0.5f, 0.54f), s * 0.38f, s * 0.17f);
    obj(shape, listOf(Color(0xFFFFE08A), _gold), glow = _gold);
  }

  fun _block() {
    val disc = Path().apply { addOval(rectFromCircle(p(0.5f, 0.5f), s * 0.38f)) };
    glass(disc);
    c.drawCircle(p(0.5f, 0.5f), s * 0.3f, line(_red, 0.1f));
    c.drawLine(p(0.29f, 0.29f), p(0.71f, 0.71f), line(_red, 0.1f));
  }

  fun _key() {
    rotated(p(0.5f, 0.5f), -40) {
      val shaft = rrPath(r(0.44f, 0.34f, 0.12f, 0.56f), 0.03f);
      obj(shaft, listOf(Color(0xFFFFE08A), _gold));
      c.drawRect(r(0.56f, 0.74f, 0.1f, 0.06f), paint(_gold));
      c.drawRect(r(0.56f, 0.62f, 0.14f, 0.06f), paint(_gold));
      val head = Path().apply { addOval(rectFromCircle(p(0.5f, 0.24f), s * 0.19f)) };
      obj(head, listOf(Color(0xFFFFE08A), _gold));
      c.drawCircle(p(0.5f, 0.24f), s * 0.07f, paint(if (dark) Color(0xFF1A1B1F) else _white));
    }
  }

  fun _gear() {
    val path = Path();
    val n = 8;
    for (i in 0 until n * 4) {
      val a = i * PI_F / (n * 2);
      val k = i % 4;
      val rad = s * (if (k == 1 || k == 2) 0.42f else 0.33f);
      val o = p(0.5f, 0.5f) + Offset(cos(a), sin(a)) * rad;
      if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y);
    }
    path.close();
    obj(path, if (dark) listOf(Color(0xFF8A8D99), Color(0xFF4A4D58)) else listOf(Color(0xFFD5D8DE), Color(0xFF9094A0)));
    c.drawCircle(p(0.5f, 0.5f), s * 0.17f, paint(if (dark) Color(0xFF1A1B1F) else Color(0xFF3A3C43)));
    ledDot(p(0.5f, 0.5f), s * 0.045f);
  }

  fun _speaker(on: Boolean = true) {
    val body = polygon(listOf(p(0.12f, 0.4f), p(0.3f, 0.4f), p(0.5f, 0.22f), p(0.5f, 0.78f), p(0.3f, 0.6f), p(0.12f, 0.6f)));
    glass(body, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    if (on) {
      for (i in 0 until 2) {
        val x = 0.62f + i * 0.12f;
        c.drawPath(Path().apply { moveTo(s * x, s * (0.4f - i * 0.07f)); quadraticBezierTo(s * (x + 0.07f), s * 0.5f, s * x, s * (0.6f + i * 0.07f)) }, line((_cyan).copy(alpha = 1 - i * 0.35f), 0.05f));
      }
    } else {
      val bg = if (dark) Color(0xFF1A1B1F) else _white;
      c.drawLine(p(0.6f, 0.36f), p(0.88f, 0.64f), line(bg, 0.14f));
      c.drawLine(p(0.6f, 0.36f), p(0.88f, 0.64f), line(_red, 0.07f));
      c.drawLine(p(0.88f, 0.36f), p(0.6f, 0.64f), line(bg, 0.14f));
      c.drawLine(p(0.88f, 0.36f), p(0.6f, 0.64f), line(_red, 0.07f));
    }
  }

  fun _flag() {
    c.drawLine(p(0.26f, 0.12f), p(0.26f, 0.9f), line(if (dark) Color(0xFFB8BCC6) else Color(0xFF8A8D99), 0.07f));
    val cloth = Path().apply { moveTo(s * 0.26f, s * 0.14f); cubicTo(s * 0.46f, s * 0.04f, s * 0.6f, s * 0.28f, s * 0.84f, s * 0.18f); lineTo(s * 0.84f, s * 0.56f); cubicTo(s * 0.6f, s * 0.66f, s * 0.46f, s * 0.42f, s * 0.26f, s * 0.52f); close() };
    obj(cloth, listOf(Color(0xFFFF8A5B), _red), glow = _red);
  }

  fun _ledLabel(text: String, hue: Color) {
    val body = rrPath(r(0.08f, 0.26f, 0.84f, 0.48f), 0.14f);
    obj(body, listOf(tint(hue, 0.12f, _white), tint(hue, 0.4f)), glow = hue);
    _dotText(text, p(0.5f, 0.5f), s * 0.045f);
  }

  fun _dotText(text: String, center: Offset, pitch: Float) {
    val rows = vlLedTextRows(text)
    val dot = pitch * 0.72f
    val x0 = center.x - (rows.first().length * pitch - (pitch - dot)) / 2f
    val y0 = center.y - 3.5f * pitch
    for (y in 0 until rows.size) {
      val row = rows[y]
      for (x in 0 until row.length) {
        if (row[x] == '#') {
          c.drawCircle(Offset(x0 + x * pitch + dot / 2f, y0 + y * pitch + dot / 2f), dot / 2f, paint(_white))
        }
      }
    }
  }

  fun _invite() {
    val env = rrPath(r(0.08f, 0.36f, 0.84f, 0.52f), 0.1f);
    glass(env, b = Alignment.TopCenter, e = Alignment.BottomCenter);
    rotated(p(0.5f, 0.4f), -7) {
      val card = rrPath(r(0.22f, 0.1f, 0.56f, 0.5f), 0.08f);
      obj(card, listOf(tint(_violet, 0.12f, _white), tint(_violet, 0.35f)), glow = _violet);
      c.drawCircle(p(0.38f, 0.27f), s * 0.065f, paint((_white).copy(alpha = 0.85f)));
      c.drawLine(p(0.5f, 0.24f), p(0.68f, 0.24f), line((_white).copy(alpha = 0.8f), 0.035f));
      c.drawLine(p(0.32f, 0.42f), p(0.68f, 0.42f), line((_white).copy(alpha = 0.5f), 0.03f));
    }
    val pocket = Path().apply { op(env, polygon(listOf(p(0.0f, 0.4f), p(0.5f, 0.7f), p(1.0f, 0.4f), p(1.0f, 1.0f), p(0.0f, 1.0f))), PathOperation.Intersect) };
    c.drawPath(pocket, grad(pocket.getBounds(), glassColors, b = Alignment.TopCenter, e = Alignment.BottomCenter));
    c.drawPath(Path().apply { moveTo(s * 0.1f, s * 0.42f); lineTo(s * 0.5f, s * 0.7f); lineTo(s * 0.9f, s * 0.42f) }, line(inkSoft, 0.025f));
    c.drawPath(env, line(if (dark) (_white).copy(alpha = 0.12f) else (_black).copy(alpha = 0.07f), 0.012f));
    val badge = p(0.8f, 0.76f);
    c.drawCircle(badge, s * 0.17f, paint(if (dark) Color(0xFF1A1B1F) else _white));
    lens(badge, s * 0.13f, _green);
    c.drawLine(badge + Offset(-s * 0.06f, 0f), badge + Offset(s * 0.06f, 0f), line(_white, 0.04f));
    c.drawLine(badge + Offset(0f, -s * 0.06f), badge + Offset(0f, s * 0.06f), line(_white, 0.04f));
  }

  /// Telegram: голубой диск с белым бумажным самолётом.
  fun _telegram() {
    val disc = Path().apply { addOval(rectFromCircle(p(0.5f, 0.5f), s * 0.4f)) };
    obj(disc, listOf(Color(0xFF4FC3F7), Color(0xFF1B8FD0)), glow = Color(0xFF2AABEE));
    val plane = polygon(listOf(p(0.2f, 0.49f), p(0.76f, 0.27f), p(0.66f, 0.74f), p(0.5f, 0.62f), p(0.42f, 0.71f), p(0.4f, 0.58f)));
    c.drawPath(plane.shift(Offset(0f, s * 0.012f)), paint((Color(0xFF0B4F7C)).copy(alpha = 0.35f)));
    c.drawPath(plane, paint(_white));
    c.drawPath(polygon(listOf(p(0.4f, 0.58f), p(0.7f, 0.35f), p(0.5f, 0.62f), p(0.42f, 0.71f))), paint(Color(0xFFD3E6F2)));
  }

  fun _newBurst() {
    val center = p(0.5f, 0.5f);
    val path = Path();
    val n = 12;
    for (i in 0 until n * 2) {
      val a = -PI_F / 2 + i * PI_F / n;
      val o = center + Offset(cos(a), sin(a)) * s * (if (i % 2 == 0) 0.42f else 0.35f);
      if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y);
    }
    path.close();
    obj(path, listOf(Color(0xFFFFC46B), _orange), glow = _orange);
    _dotText("NEW", center, s * 0.04f);
  }

  fun _playlist() {
    for (i in 0 until 3) {
      val bar = rrPath(r(0.1f, 0.2f + i * 0.2f, 0.5f - i * 0.04f, 0.12f), 0.05f);
      obj(bar, if (i == 0) listOf(tint(_pink, 0.12f, _white), tint(_pink, 0.3f)) else (if (dark) listOf(Color(0xFF4A4D58), Color(0xFF2A2B30)) else listOf(_white, Color(0xFFD5D8DE))));
    }
    c.drawLine(p(0.82f, 0.74f), p(0.82f, 0.32f), line(_pink, 0.045f));
    c.drawPath(polygon(listOf(p(0.82f, 0.32f), p(0.94f, 0.4f), p(0.82f, 0.44f))), paint(_pink));
    lens(p(0.74f, 0.76f), s * 0.085f, _pink);
  }

  fun _arrowBadge(up: Boolean = true) {
    val hue = if (up) _green else _amber;
    val disc = Path().apply { addOval(rectFromCircle(p(0.5f, 0.5f), s * 0.38f)) };
    shadow(disc, color = (hue).copy(alpha = if (dark) 0.4f else 0.3f));
    lens(p(0.5f, 0.5f), s * 0.38f, hue);
    val d = if (up) -1.0f else 1.0f;
    c.drawLine(p(0.5f, 0.5f - d * 0.18f), p(0.5f, 0.5f + d * 0.18f), line(_white, 0.08f));
    c.drawPath(Path().apply { moveTo(s * 0.36f, s * (0.5f + d * 0.04f)); lineTo(s * 0.5f, s * (0.5f + d * 0.18f)); lineTo(s * 0.64f, s * (0.5f + d * 0.04f)) }, line(_white, 0.08f));
  }

  fun _badge(hue: Color, check: Boolean = true) {
    val disc = Path().apply { addOval(rectFromCircle(p(0.5f, 0.5f), s * 0.38f)) };
    shadow(disc, color = (hue).copy(alpha = if (dark) 0.4f else 0.3f));
    lens(p(0.5f, 0.5f), s * 0.38f, hue);
    if (check) {
      c.drawPath(this.check(0.34f, 0.52f, 0.45f, 0.63f, 0.67f, 0.4f), line(_white, 0.09f));
    } else {
      c.drawLine(p(0.32f, 0.5f), p(0.68f, 0.5f), line(_white, 0.09f));
      c.drawLine(p(0.5f, 0.32f), p(0.5f, 0.68f), line(_white, 0.09f));
    }
  }

  fun _copy() {
    val back = rrPath(r(0.36f, 0.1f, 0.5f, 0.62f), 0.07f);
    glass(back);
    val front = rrPath(r(0.14f, 0.28f, 0.5f, 0.62f), 0.07f);
    obj(front, listOf(tint(_blue, 0.12f, _white), tint(_blue, 0.35f)), glow = _blue);
    for (i in 0 until 3) {
      c.drawLine(p(0.23f, 0.48f + i * 0.12f), p(0.55f - (if (i == 2) 0.1f else 0f), 0.48f + i * 0.12f), line((_white).copy(alpha = 0.8f), 0.035f));
    }
  }

  fun _pencil() {
    rotated(p(0.5f, 0.5f), 40) {
      val body = rrPath(r(0.4f, 0.14f, 0.2f, 0.58f), 0.03f);
      obj(body, listOf(Color(0xFFFFE08A), _gold));
      c.drawLine(p(0.5f, 0.16f), p(0.5f, 0.72f), line((_white).copy(alpha = 0.35f), 0.02f));
      c.drawRRect(rr(r(0.4f, 0.04f, 0.2f, 0.12f), 0.04f), paint(Color(0xFFFF8CBF)));
      c.drawRect(r(0.4f, 0.14f, 0.2f, 0.07f), grad(r(0.4f, 0.14f, 0.2f, 0.07f), listOf(Color(0xFFEEF0F3), Color(0xFFA2A7B2), Color(0xFFDADDE3))));
      c.drawPath(polygon(listOf(p(0.4f, 0.72f), p(0.6f, 0.72f), p(0.5f, 0.94f))), paint(Color(0xFFE9C79B)));
      c.drawPath(polygon(listOf(p(0.46f, 0.86f), p(0.54f, 0.86f), p(0.5f, 0.94f))), paint(if (dark) Color(0xFFB8BCC6) else Color(0xFF3A3C43)));
    }
  }

  fun _note() {
    val disc = Path().apply { addOval(rectFromCircle(p(0.5f, 0.5f), s * 0.4f)) };
    glass(disc);
    c.drawCircle(p(0.5f, 0.5f), s * 0.28f, line((_white).copy(alpha = if (dark) 0.06f else 0.0f), 0.01f));
    val beam = polygon(listOf(p(0.4f, 0.28f), p(0.72f, 0.2f), p(0.72f, 0.32f), p(0.4f, 0.4f)));
    val note = Path().apply { addOval(rectFromCenter(p(0.34f, 0.66f), s * 0.2f, s * 0.15f)); addOval(rectFromCenter(p(0.66f, 0.58f), s * 0.2f, s * 0.15f)) };
    c.drawPath(Path().apply { moveTo(s * 0.4f, s * 0.66f); lineTo(s * 0.4f, s * 0.3f) }, line(_pink, 0.045f));
    c.drawPath(Path().apply { moveTo(s * 0.72f, s * 0.58f); lineTo(s * 0.72f, s * 0.22f) }, line(_pink, 0.045f));
    c.drawPath(beam, paint(_pink));
    c.drawPath(note, grad(r(0.2f, 0.5f, 0.6f, 0.3f), listOf(Color(0xFFFF8CC0), Color(0xFFC81E7A))));
  }

  
}
