package org.visorlink.app.ui.components.led

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.floor
import org.visorlink.app.ui.theme.VlTheme

// ── Точечный алфавит 5×7 ───────────────────────────────────────────────────

val VlLedFont = mapOf(
    ' ' to listOf(".....", ".....", ".....", ".....", ".....", ".....", "....."),
    'A' to listOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
    'B' to listOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
    'C' to listOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
    'D' to listOf("###..", "#..#.", "#...#", "#...#", "#...#", "#..#.", "###.."),
    'E' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
    'F' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
    'G' to listOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"),
    'H' to listOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
    'I' to listOf(".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
    'J' to listOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
    'K' to listOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
    'L' to listOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
    'M' to listOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
    'N' to listOf("#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"),
    'O' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
    'P' to listOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
    'Q' to listOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
    'R' to listOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
    'S' to listOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
    'T' to listOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
    'U' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
    'V' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
    'W' to listOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."),
    'X' to listOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
    'Y' to listOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
    'Z' to listOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
    'Б' to listOf("#####", "#....", "#....", "####.", "#...#", "#...#", "####."),
    'Г' to listOf("#####", "#....", "#....", "#....", "#....", "#....", "#...."),
    'Д' to listOf("..##.", ".#.#.", ".#.#.", ".#.#.", ".#.#.", "#####", "#...#"),
    'Ж' to listOf("#.#.#", "#.#.#", "#.#.#", ".###.", "#.#.#", "#.#.#", "#.#.#"),
    'З' to listOf(".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."),
    'И' to listOf("#...#", "#...#", "#..##", "#.#.#", "##..#", "#...#", "#...#"),
    'Й' to listOf(".#.#.", "..#..", "#...#", "#..##", "#.#.#", "##..#", "#...#"),
    'Л' to listOf("..###", ".#..#", ".#..#", ".#..#", ".#..#", ".#..#", "#...#"),
    'П' to listOf("#####", "#...#", "#...#", "#...#", "#...#", "#...#", "#...#"),
    'У' to listOf("#...#", "#...#", "#...#", ".####", "....#", "....#", ".###."),
    'Ф' to listOf("..#..", ".###.", "#.#.#", "#.#.#", "#.#.#", ".###.", "..#.."),
    'Ц' to listOf("#..#.", "#..#.", "#..#.", "#..#.", "#..#.", "#####", "....#"),
    'Ч' to listOf("#...#", "#...#", "#...#", ".####", "....#", "....#", "....#"),
    'Ш' to listOf("#.#.#", "#.#.#", "#.#.#", "#.#.#", "#.#.#", "#.#.#", "#####"),
    'Щ' to listOf("#.#.#", "#.#.#", "#.#.#", "#.#.#", "#.#.#", "#####", "....#"),
    'Ъ' to listOf("##...", ".#...", ".#...", ".###.", ".#..#", ".#..#", ".###."),
    'Ы' to listOf("#...#", "#...#", "#...#", "##..#", "#.#.#", "#.#.#", "##..#"),
    'Ь' to listOf("#....", "#....", "#....", "####.", "#...#", "#...#", "####."),
    'Э' to listOf(".###.", "#...#", "....#", "..###", "....#", "#...#", ".###."),
    'Ю' to listOf("#..#.", "#.#.#", "#.#.#", "###.#", "#.#.#", "#.#.#", "#..#."),
    'Я' to listOf(".####", "#...#", "#...#", ".####", "..#.#", ".#..#", "#...#"),
    'Ё' to listOf(".#.#.", "#####", "#....", "####.", "#....", "#....", "#####"),
    '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
    '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
    '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
    '3' to listOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
    '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
    '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
    '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
    '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
    '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
    '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
    '.' to listOf(".....", ".....", ".....", ".....", ".....", ".##..", ".##.."),
    ',' to listOf(".....", ".....", ".....", ".....", ".##..", "..#..", ".#..."),
    ':' to listOf(".....", ".##..", ".##..", ".....", ".##..", ".##..", "....."),
    ';' to listOf(".....", ".##..", ".##..", ".....", ".##..", "..#..", ".#..."),
    '!' to listOf("..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#.."),
    '?' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."),
    '-' to listOf(".....", ".....", ".....", ".###.", ".....", ".....", "....."),
    '—' to listOf(".....", ".....", ".....", "#####", ".....", ".....", "....."),
    '+' to listOf(".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."),
    '%' to listOf("##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##"),
    '/' to listOf(".....", "....#", "...#.", "..#..", ".#...", "#....", "....."),
    '·' to listOf(".....", ".....", ".....", "..#..", ".....", ".....", "....."),
    '<' to listOf("...#.", "..#..", ".#...", "#....", ".#...", "..#..", "...#."),
    '>' to listOf(".#...", "..#..", "...#.", "....#", "...#.", "..#..", ".#..."),
    '(' to listOf("...#.", "..#..", ".#...", ".#...", ".#...", "..#..", "...#."),
    ')' to listOf(".#...", "..#..", "...#.", "...#.", "...#.", "..#..", ".#..."),
    '"' to listOf(".#.#.", ".#.#.", ".#.#.", ".....", ".....", ".....", "....."),
    '\'' to listOf("..#..", "..#..", "..#..", ".....", ".....", ".....", "....."),
    '#' to listOf(".#.#.", ".#.#.", "#####", ".#.#.", "#####", ".#.#.", ".#.#."),
    '@' to listOf(".###.", "#...#", "....#", ".##.#", "#.#.#", "#.#.#", ".###."),
    '_' to listOf(".....", ".....", ".....", ".....", ".....", ".....", "#####"),
    '←' to listOf(".....", "..#..", ".#...", "#####", ".#...", "..#..", "....."),
    '…' to listOf(".....", ".....", ".....", ".....", ".....", ".....", "#.#.#"),
)

val VlLedFontAliases = mapOf(
    'А' to 'A', 'В' to 'B', 'Е' to 'E', 'К' to 'K', 'М' to 'M',
    'Н' to 'H', 'О' to 'O', 'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'Х' to 'X'
)

fun vlLedGlyphKey(ch: Char): Char? {
    val upper = ch.uppercaseChar()
    val k = VlLedFontAliases[upper] ?: upper
    return if (VlLedFont.containsKey(k)) k else null
}

fun vlLedCanRender(text: String): Boolean {
    var known = 0
    var total = 0
    for (ch in text) {
        if (ch.isWhitespace()) continue
        total++
        if (vlLedGlyphKey(ch) != null) known++
    }
    return total == 0 || (known.toFloat() / total.toFloat()) >= 0.8f
}

fun vlLedTextColumns(text: String): Int {
    val len = text.length
    return if (len == 0) 0 else len * 6 - 1
}

fun vlLedTextRows(text: String): List<String> {
    val glyphs = text.map { ch ->
        val k = vlLedGlyphKey(ch) ?: ' '
        VlLedFont[k] ?: VlLedFont[' ']!!
    }
    if (glyphs.isEmpty()) return List(7) { "" }
    return List(7) { r -> glyphs.joinToString(".") { it[r] } }
}

fun vlLedTruncate(text: String, columns: Int): String {
    val fit = (columns + 1) / 6
    if (text.length <= fit) return text
    if (fit <= 1) return text.take(fit)
    return text.take(fit - 1).trimEnd() + "…"
}

/**
 * Статичная точечная надпись шрифтом 5×7 на LED-матрице.
 */
@Composable
fun VlLedText(
    text: String,
    modifier: Modifier = Modifier,
    dot: Dp = 2.dp,
    gap: Dp = 1.dp,
    color: Color? = null,
    semanticLabel: String? = null,
) {
    val rows = remember(text) { vlLedTextRows(text) }
    VlLedMatrix(
        rows = rows,
        dot = dot,
        gap = gap,
        color = color,
        contentDescription = semanticLabel ?: text,
        modifier = modifier,
    )
}

/**
 * Бегущая строка шрифтом 5×7:
 *  - Если помещается целиком в ширину — отображается статично;
 *  - Если не помещается — плавно бежит, зацикливаясь через разделитель «·» с непрерывной
 *    субпиксельной инерцией светодиодов;
 *  - При «убрать анимации» (reduceMotion) обрезается с многоточием;
 *  - При несовместимом тексте (иероглифы/эмодзи) показывает обычный Text.
 */
@Composable
fun VlLedMarquee(
    text: String,
    modifier: Modifier = Modifier,
    speedColsPerSec: Float = 12f,
    dot: Dp = 2.dp,
    gap: Dp = 1.dp,
    color: Color? = null,
    animate: Boolean = true,
    fallbackStyle: TextStyle? = null,
    semanticLabel: String? = null,
) {
    val tokens = VlTheme.tokens
    val still = tokens.reduceMotion || !animate

    if (fallbackStyle != null && !vlLedCanRender(text)) {
        Text(
            text = text,
            style = fallbackStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val maxW = maxWidth
        val dotPx = with(androidx.compose.ui.platform.LocalDensity.current) { dot.toPx() }
        val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { gap.toPx() }
        val stepPx = dotPx + gapPx

        val visibleCols = if (maxW != Dp.Unspecified && maxW != Dp.Infinity && maxW > 0.dp) {
            val totalW = with(androidx.compose.ui.platform.LocalDensity.current) { maxW.toPx() }
            ((totalW + gapPx) / stepPx).toInt().coerceAtLeast(1)
        } else {
            100
        }

        val textCols = remember(text) { vlLedTextColumns(text) }
        val label = semanticLabel ?: text

        if (textCols <= visibleCols) {
            VlLedText(
                text = text,
                dot = dot,
                gap = gap,
                color = color,
                semanticLabel = label,
            )
        } else if (still) {
            val truncated = remember(text, visibleCols) { vlLedTruncate(text, visibleCols) }
            VlLedText(
                text = truncated,
                dot = dot,
                gap = gap,
                color = color,
                semanticLabel = label,
            )
        } else {
            // Бегущая анимация с субпиксельным скроллингом и инерцией люминофора
            val loopText = remember(text) { "$text · " }
            val loopRows = remember(loopText) { vlLedTextRows(loopText) }
            val loopCols = remember(loopRows) { (loopRows.firstOrNull()?.length ?: 0) + 1 }

            var offsetFloat by remember(text) { mutableFloatStateOf(0f) }

            LaunchedEffect(text, loopCols, speedColsPerSec) {
                offsetFloat = 0f
                // 1 секунду стоим в начале, чтобы прочитать первое слово
                delay(1000)
                val startTime = System.currentTimeMillis()
                while (isActive) {
                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f
                    val moved = elapsedSec * speedColsPerSec
                    offsetFloat = if (loopCols > 0) moved % loopCols else 0f
                    delay(16) // ~60 FPS плавного движения
                }
            }

            val ink = rememberLedInk(color)

            Canvas(
                modifier = Modifier
                    .semantics { contentDescription = label }
                    .size(
                        width = (dot + gap) * visibleCols - gap,
                        height = (dot + gap) * 7 - gap,
                    )
            ) {
                vlPaintLedDots(
                    cols = visibleCols,
                    rows = 7,
                    dotPx = dotPx,
                    gapPx = gapPx,
                    ink = ink,
                    intensity = { x, y ->
                        if (loopCols <= 0) 0f else {
                            val u = (x + offsetFloat) % loopCols
                            val col0 = floor(u).toInt()
                            val frac = u - col0
                            val col1 = (col0 + 1) % loopCols
                            val rowStr = loopRows.getOrNull(y) ?: ""
                            val lit0 = col0 < rowStr.length && rowStr[col0] == '#'
                            val lit1 = col1 < rowStr.length && rowStr[col1] == '#'
                            val v = (if (lit0) 1f - frac else 0f) + (if (lit1) frac else 0f)
                            v.coerceIn(0f, 1f)
                        }
                    }
                )
            }
        }
    }
}
