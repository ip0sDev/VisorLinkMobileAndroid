package org.visorlink.app.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Прямоугольник со срезанными под 45° углами — силуэт Forge v2. По умолчанию срезаны
 * верхний-начальный и нижний-конечный углы (диагональная пара): блок читается как «корпоративный
 * терминал», а жёсткая плита ([vlHardShadow]) повторяет срез. Срез не больше трети меньшей стороны,
 * поэтому мелкие элементы (точки статуса, бейджи) остаются аккуратными.
 */
class ChamferShape(
    private val cut: Dp,
    private val topStart: Boolean = true,
    private val topEnd: Boolean = false,
    private val bottomEnd: Boolean = true,
    private val bottomStart: Boolean = false,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rtl = layoutDirection == LayoutDirection.Rtl
        val tl = if (rtl) topEnd else topStart
        val tr = if (rtl) topStart else topEnd
        val br = if (rtl) bottomStart else bottomEnd
        val bl = if (rtl) bottomEnd else bottomStart
        val w = size.width
        val h = size.height
        val c = min(with(density) { cut.toPx() }, min(w, h) * 0.34f)
        val path = Path().apply {
            moveTo(if (tl) c else 0f, 0f)
            lineTo(if (tr) w - c else w, 0f)
            lineTo(w, if (tr) c else 0f)
            lineTo(w, if (br) h - c else h)
            lineTo(if (br) w - c else w, h)
            lineTo(if (bl) c else 0f, h)
            lineTo(0f, if (bl) h - c else h)
            lineTo(0f, if (tl) c else 0f)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is ChamferShape &&
        other.cut == cut && other.topStart == topStart && other.topEnd == topEnd &&
        other.bottomEnd == bottomEnd && other.bottomStart == bottomStart

    override fun hashCode(): Int {
        var r = cut.hashCode()
        r = 31 * r + topStart.hashCode()
        r = 31 * r + topEnd.hashCode()
        r = 31 * r + bottomEnd.hashCode()
        r = 31 * r + bottomStart.hashCode()
        return r
    }
}
