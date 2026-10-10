package org.visorlink.app.data.idcard

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Порт `src/utils/idCardModel.js` один в один: карта из одного seed должна выглядеть
 * одинаково у всех и на всех платформах. Порядок вызовов генератора менять нельзя —
 * иначе у уже выданных карт поменяются детали. Сверка с вебом — IdCardGeneratorTest
 * (эталонные значения посчитаны исходным JS).
 *
 * JS-арифметика: битовые операции — над int32 (`Int` в Kotlin, переполнение то же),
 * `>>> 0` — беззнаковое представление, `Math.round` — округление половины вверх.
 */
object IdCardGenerator {

    const val LED_FACES = 8
    private const val SERIAL_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** mulberry32. `seed` — любое 32-битное значение (берутся младшие 32 бита). */
    fun rng(seed: Long): () -> Double {
        var a = seed.toInt()
        return {
            a += 0x6D2B79F5
            var t = a
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + (t xor (t ushr 7)) * (t or 61))
            ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
        }
    }

    /** `Math.round` из JS: половина — вверх (к +∞), в отличие от `roundToInt`. */
    fun jsRound(x: Double): Long = floor(x + 0.5).toLong()

    private fun <T> pickWeighted(random: () -> Double, table: List<Pair<T, Int>>): T {
        var x = random() * table.sumOf { it.second }
        for ((value, w) in table) {
            x -= w
            if (x < 0) return value
        }
        return table.last().first
    }

    /** Признаки по seed — как на сервере (нужны демо-превью и карте группы без сервера). */
    fun generateTraits(seed: Long): IdCardTraits {
        val random = rng(seed)
        return IdCardTraits(
            seed = seed and 0xFFFFFFFFL,
            finish = pickWeighted(random, listOf(IdFinish.BASE to 55, IdFinish.PEARL to 20, IdFinish.METALLIC to 14, IdFinish.OBSIDIAN to 8, IdFinish.GOLD to 3)),
            laminated = random() < 0.6,
            wear = pickWeighted(random, listOf(0 to 40, 1 to 30, 2 to 20, 3 to 10)),
            foil = pickWeighted(random, listOf(IdFoil.NONE to 28, IdFoil.CLASSIC to 32, IdFoil.PRISM to 23, IdFoil.AURORA to 12, IdFoil.GALAXY to 5)),
            variant = floor(random() * 12).toInt(),
            tilt = jsRound((random() * 2 - 1) * 15) / 10.0,
        )
    }

    fun generateSerial(seed: Long): String {
        val random = rng(seed xor 0x5EED)
        fun part() = (0 until 4).map { SERIAL_ALPHABET[floor(random() * SERIAL_ALPHABET.length).toInt()] }.joinToString("")
        return "VL-${part()}-${part()}"
    }

    // ── Детали оформления из seed ──

    data class Guilloche(val cx: Int, val cy: Int, val n: Int, val m: Int, val rings: Int, val phase: Double, val r0: Int)
    data class Waves(val freq: Double, val amp: Double, val phase: Double)
    data class Smudge(val x: Int, val y: Int, val rot: Int)
    data class Bubble(val x: Int, val y: Int, val size: Double)
    data class Misprint(val x: Double, val y: Double)

    enum class HoloShape { HEX, SHIELD, STAR, ROSETTE }

    /** Голограмма всегда в правом нижнем углу; `CORNER_TILT` — тот же угол, повёрнуто на −9°. */
    enum class HoloSpot { CORNER, CORNER_TILT }

    data class Details(
        val guilloche: Guilloche,
        val waves: Waves,
        val holoShape: HoloShape,
        val holoSpot: HoloSpot,
        /** 0 круглый, 1 прямоугольный, 2 овальный (Standard). */
        val stampStyle: Int,
        val stampRot: Int,
        val stampDx: Double,
        val stampDy: Double,
        val stampInk: Double,
        val stampPress: Int,
        val secondStamp: Boolean,
        val secondRot: Int,
        /** Protogen: выражение на визоре 0–7. */
        val led: Int,
        /** Beast: гладкий, пятна, полосы, крапины, следы. */
        val coat: Int,
        val chipCorner: Int,
        val smudge: Smudge,
        val bubble: Bubble?,
        val misprint: Misprint,
        val artSeed: Long,
    )

    /** `deriveDetails(seed)`: порядок вызовов `r()` — как в вебе. */
    fun deriveDetails(seed: Long): Details {
        val r = rng(seed xor 0xA11CE5)
        fun <T> pick(arr: List<T>): T = arr[floor(r() * arr.size).toInt()]
        fun between(a: Double, b: Double) = a + r() * (b - a)
        fun roundBetween(a: Double, b: Double) = jsRound(between(a, b)).toInt()

        val guilloche = Guilloche(
            cx = roundBetween(560.0, 760.0),
            cy = roundBetween(260.0, 420.0),
            n = pick(listOf(12, 14, 16, 18, 20, 24)),
            m = pick(listOf(3, 5, 7)),
            rings = 11 + floor(r() * 6).toInt(),
            phase = between(0.12, 0.32),
            r0 = roundBetween(70.0, 110.0),
        )
        val waves = Waves(freq = between(1.6, 3.4), amp = between(8.0, 20.0), phase = between(0.0, PI * 2))
        val holoShape = pick(HoloShape.entries.toList())
        // Раньше первый вариант был 'photo' — вызов тот же, поэтому выданные карты не сдвинулись
        val holoSpot = pick(listOf(HoloSpot.CORNER_TILT, HoloSpot.CORNER, HoloSpot.CORNER))
        val stampStyle = floor(r() * 3).toInt()
        val stampRot = roundBetween(-24.0, 10.0)
        val stampDx = between(-0.7, 0.7)
        val stampDy = between(-0.6, 0.6)
        val stampInk = between(0.66, 0.92)
        val stampPress = roundBetween(0.0, 360.0)
        val secondStamp = r() < 0.45
        val secondRot = roundBetween(-12.0, 14.0)
        val led = floor(r() * LED_FACES).toInt()
        val coat = floor(r() * 5).toInt()
        val chipCorner = floor(r() * 4).toInt()
        val smudge = Smudge(x = roundBetween(15.0, 80.0), y = roundBetween(20.0, 70.0), rot = roundBetween(0.0, 180.0))
        val bubble = if (r() < 0.5) {
            Bubble(x = roundBetween(10.0, 88.0), y = roundBetween(12.0, 82.0), size = between(0.6, 1.2))
        } else null
        val misprint = Misprint(x = between(-0.25, 0.25), y = between(-0.2, 0.2))
        val artSeed = floor(r() * 4294967296.0).toLong() and 0xFFFFFFFFL
        return Details(
            guilloche, waves, holoShape, holoSpot, stampStyle, stampRot, stampDx, stampDy, stampInk,
            stampPress, secondStamp, secondRot, led, coat, chipCorner, smudge, bubble, misprint, artSeed,
        )
    }

    // ── Подпись ──

    /**
     * Точки росчерка в координатах 0–100 × 0–30, уже нормированные (как `signaturePath`).
     * Для отрисовки — эти точки; строка SVG ([signaturePath]) нужна только для сверки с вебом.
     */
    fun signaturePoints(seed: Long): List<Pair<Double, Double>> {
        val r = rng(seed xor 0x51611)
        val base = 23.0
        val pts = ArrayList<Pair<Double, Double>>()
        var x0 = 0.0
        val letters = 5 + floor(r() * 4).toInt()
        for (i in 0 until letters) {
            val capital = i == 0
            val h = if (capital) 17 + r() * 4 else if (r() < 0.35) 11 + r() * 5 else 5 + r() * 3
            val adv = if (capital) 9 + r() * 3 else 5.5 + r() * 3
            val d = if (capital) 4.5 + r() * 2 else 1.6 + r() * 1.8
            for (k in 0..22) {
                val t = (k / 22.0) * PI * 2
                val y = base - (h * (1 - cos(t))) / 2
                pts += (x0 + (adv * t) / (PI * 2) - d * sin(t) + (base - y) * 0.38) to y
            }
            x0 += adv
        }
        // Хвост: уходит вправо и возвращается подчёркиванием под имя
        val ex = pts.last().first
        val tail = 0.35 + r() * 0.4
        for (k in 1..16) {
            val t = k / 16.0
            pts += (ex + sin(t * PI) * 6 - t * x0 * tail) to (base + 1 + sin(t * PI) * 4.5 - t * 1.5)
        }
        val minX = pts.minOf { it.first }
        val scale = min(1.0, 94 / (pts.maxOf { it.first } - minX))
        return pts.map { (x, y) -> (3 + (x - minX) * scale) to (base - (base - y) * (0.6 + scale * 0.4)) }
    }

    /** Формат своей подписи (functions/publicCardLogic.js `normalizeSignature`). */
    private val SIGNATURE_RE = Regex("""^(?:[ML]\d{1,3}(?:\.\d)? \d{1,2}(?:\.\d)?)+$""")
    private val SIGNATURE_CMD = Regex("""([ML])(\d{1,3}(?:\.\d)?) (\d{1,2}(?:\.\d)?)""")
    const val SIGNATURE_MAX = 6000

    /**
     * Своя нарисованная подпись: только `M x y` / `L x y`, один знак после точки, x 0–100, y 0–30,
     * не длиннее [SIGNATURE_MAX], начинается с `M`. Каждое `M` — новый штрих. Невалидная — `null`
     * (тогда рисуется росчерк из seed).
     */
    fun parseSignature(d: String?): List<List<Pair<Double, Double>>>? {
        if (d.isNullOrEmpty() || d.length > SIGNATURE_MAX || d[0] != 'M' || !SIGNATURE_RE.matches(d)) return null
        val strokes = ArrayList<MutableList<Pair<Double, Double>>>()
        for (m in SIGNATURE_CMD.findAll(d)) {
            val x = m.groupValues[2].toDouble()
            val y = m.groupValues[3].toDouble()
            if (x > 100 || y > 30) return null
            if (m.groupValues[1] == "M" || strokes.isEmpty()) strokes += mutableListOf(x to y) else strokes.last() += x to y
        }
        return strokes.takeIf { it.isNotEmpty() }
    }

    /** SVG-путь подписи, байт в байт как в вебе (toFixed(1)). */
    fun signaturePath(seed: Long): String =
        "M" + signaturePoints(seed).joinToString("L") { (x, y) -> "${toFixed1(x)} ${toFixed1(y)}" }

    /** `Number.prototype.toFixed(1)`: половина — от нуля, по точному двоичному значению. */
    internal fun toFixed1(x: Double): String {
        if (x < 0) return "-" + toFixed1(-x)
        return BigDecimal(x).setScale(1, RoundingMode.HALF_UP).toPlainString()
    }

    // ── Потёртость ──

    data class Scratch(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val w: Double)

    /** Царапины: короткие линии в координатах 0–100. */
    fun scratches(seed: Long, count: Int): List<Scratch> {
        val random = rng(seed xor 0xC0FFEE)
        return List(count) {
            val x = random() * 100
            val y = random() * 100
            val len = 3 + random() * 11
            val angle = random() * PI
            Scratch(x, y, x + cos(angle) * len, y + sin(angle) * len * 0.6, 0.15 + random() * 0.35)
        }
    }

    // ── Оборот: штрихкод, MRZ, ключ подлинности ──

    /** Полосы штрихкода: (ширина, есть-полоса). */
    fun barcodeBars(serial: String): List<Pair<Int, Boolean>> {
        val bars = ArrayList<Pair<Int, Boolean>>()
        for (ch in serial) {
            val code = ch.code
            for (i in 0 until 5) bars += (((code shr i) and 1) + 1) to (i % 2 == 0)
            bars += 1 to false
        }
        return bars
    }

    // Кириллица → латиница (как в загранпаспортах), остальное — через NFKD без диакритики
    private val TRANSLIT = mapOf(
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Е' to "E", 'Ё' to "E", 'Ж' to "ZH", 'З' to "Z",
        'И' to "I", 'Й' to "I", 'К' to "K", 'Л' to "L", 'М' to "M", 'Н' to "N", 'О' to "O", 'П' to "P", 'Р' to "R",
        'С' to "S", 'Т' to "T", 'У' to "U", 'Ф' to "F", 'Х' to "KH", 'Ц' to "TS", 'Ч' to "CH", 'Ш' to "SH",
        'Щ' to "SHCH", 'Ъ' to "IE", 'Ы' to "Y", 'Ь' to "", 'Э' to "E", 'Ю' to "IU", 'Я' to "IA", 'І' to "I",
        'Ў' to "U", 'Є' to "IE", 'Ї' to "I", 'Ґ' to "G",
    )
    private val CYRILLIC_RE = Regex("[А-ЯЁІЎЄЇҐ]")
    private val DIACRITICS_RE = Regex("[\\u0300-\\u036f]")
    private val NON_MRZ_RE = Regex("[^A-Z0-9]+")

    private fun mrzText(s: String?): String {
        val upper = (s ?: "").uppercase()
        val translit = CYRILLIC_RE.replace(upper) { TRANSLIT[it.value[0]] ?: "" }
        val plain = DIACRITICS_RE.replace(Normalizer.normalize(translit, Normalizer.Form.NFKD), "")
        return NON_MRZ_RE.replace(plain, "<")
    }

    private fun yymmdd(ms: Long): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
        return "%02d%02d%02d".format(c.get(Calendar.YEAR) % 100, c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Три строки по 30 символов в духе MRZ (ID-1). */
    fun mrzLines(serial: String, mode: IdMode, username: String?, displayName: String?, registeredAt: Long, issuedAt: Long): List<String> {
        fun pad(s: String) = (s + "<".repeat(30)).take(30)
        val letter = when (mode) {
            IdMode.STANDARD -> "S"
            IdMode.PROTOGEN -> "P"
            IdMode.BEAST -> "B"
        }
        return listOf(
            pad("ID${letter}VLK${mrzText(serial)}"),
            pad("${yymmdd(registeredAt)}<${yymmdd(issuedAt)}<VLK<${mrzText(username)}"),
            pad(mrzText(displayName?.takeIf { it.isNotEmpty() } ?: username)),
        )
    }

    /** Протоген «подписывается» ключом: 4 группы hex из seed (декоративно, IdCard.jsx). */
    fun authKey(seed: Long): String {
        var x = seed.toInt() xor 0x9E3779B9.toInt()
        return (0 until 4).joinToString(":") {
            x = (x xor (x ushr 15)) * 0x2C1B3C6D
            "%08X".format(x.toLong() and 0xFFFFFFFFL).take(6)
        }
    }
}
