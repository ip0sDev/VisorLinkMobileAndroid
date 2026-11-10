package org.visorlink.app.ui.components.led

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.visorlink.app.ui.theme.VlTheme

// Заставка запуска на табло 48×24: развёртка → «VISORLINK» бегущей строкой → мордочка
// просыпается (порт Flutter `vl_led_boot.dart`). Цикл, пока идёт проверка; навигацию
// заставка не держит.

const val VL_LED_BOOT_COLS = 48
const val VL_LED_BOOT_ROWS = 24

/** Длина одного цикла заставки, мс. */
const val VL_LED_BOOT_CYCLE_MS = 3600

private const val SWEEP_END = 500f
private const val TEXT_END = 2300f
private const val SLEEPY_END = 2800f
private const val BLINK_END = 2950f

/** Кадр заставки в момент [ms] от начала цикла: 24 строки по 48 точек. */
fun vlLedBootFrame(ms: Double): List<String> {
    val t = (ms % VL_LED_BOOT_CYCLE_MS).toFloat()
    val grid = Array(VL_LED_BOOT_ROWS) { BooleanArray(VL_LED_BOOT_COLS) }

    fun stamp(rows: List<String>, x0: Int, y0: Int) {
        for (y in rows.indices) {
            val line = rows[y]
            for (x in line.indices) {
                val gx = x0 + x
                val gy = y0 + y
                if (line[x] == '#' && gx >= 0 && gx < VL_LED_BOOT_COLS && gy >= 0 && gy < VL_LED_BOOT_ROWS) {
                    grid[gy][gx] = true
                }
            }
        }
    }

    when {
        t < SWEEP_END -> {
            // Развёртка: яркая строка с коротким следом бежит сверху вниз
            val y = (t / SWEEP_END * VL_LED_BOOT_ROWS).toInt()
            for (row in listOf(y, y - 1)) {
                if (row >= 0 && row < VL_LED_BOOT_ROWS) grid[row].fill(true)
            }
        }
        t < TEXT_END -> {
            val text = vlLedTextRows("VISORLINK")
            val travel = VL_LED_BOOT_COLS + (text.firstOrNull()?.length ?: 0)
            val x = VL_LED_BOOT_COLS - ((t - SWEEP_END) / (TEXT_END - SWEEP_END) * travel).toInt()
            stamp(text, x, (VL_LED_BOOT_ROWS - 7) / 2)
        }
        else -> {
            // Мордочка просыпается: сонная → моргнувшая счастливая
            val face = if (t < SLEEPY_END) {
                vlLedFaceRows(VlLedExpression.SLEEPY)
            } else {
                vlLedFaceRows(VlLedExpression.HAPPY, blink = t < BLINK_END)
            }
            stamp(face, (VL_LED_BOOT_COLS - 32) / 2, (VL_LED_BOOT_ROWS - 12) / 2)
        }
    }
    return grid.map { row -> String(row.map { if (it) '#' else '.' }.toCharArray()) }
}

/**
 * Заставка запуска: цикл [VL_LED_BOOT_CYCLE_MS] мс, пока идёт проверка. При «убрать
 * анимации» — сразу проснувшаяся мордочка.
 */
@Composable
fun VlLedBoot(dot: Dp = 4.dp, gap: Dp = 1.5.dp, modifier: Modifier = Modifier) {
    val tokens = VlTheme.tokens
    val still = tokens.reduceMotion
    val time = remember { Animatable(0f) }

    LaunchedEffect(still) {
        if (still) {
            time.snapTo(1f)
        } else {
            while (true) {
                time.snapTo(0f)
                time.animateTo(1f, tween(VL_LED_BOOT_CYCLE_MS, easing = LinearEasing))
            }
        }
    }

    VlLedMatrix(
        rows = vlLedBootFrame(
            if (still) (VL_LED_BOOT_CYCLE_MS - 1).toDouble() else (time.value * VL_LED_BOOT_CYCLE_MS).toDouble()
        ),
        dot = dot,
        gap = gap,
        modifier = modifier,
    )
}
