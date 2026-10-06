package org.visorlink.app.ui.components.idcard

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.getKoin
import org.visorlink.app.data.repository.SettingsRepository

/**
 * Тактильная отдача ID-карты: композиции примитивов (VibrationEffect.Composition) — короткие
 * «пластиковые» щелчки на поворотах, мягкий удар при приземлении на сторону, «вжух» при
 * перевороте, удар штампа при выдаче. На устройствах без поддержки примитивов — предустановленные
 * эффекты. Уважает настройку «Вибрация» (SettingsRepository.hapticEnabled).
 */
internal class CardHaptics(context: Context, private val enabled: () -> Boolean) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private var lastAt = 0L

    private fun supported(vararg ids: Int) =
        vibrator?.areAllPrimitivesSupported(*ids) == true

    /** Примитивы API 31+ (LOW_TICK, THUD, SPIN) — с проверкой. */
    private val lowTick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else VibrationEffect.Composition.PRIMITIVE_TICK
    private val thud = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) VibrationEffect.Composition.PRIMITIVE_THUD else VibrationEffect.Composition.PRIMITIVE_CLICK
    private val spin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) VibrationEffect.Composition.PRIMITIVE_SPIN else VibrationEffect.Composition.PRIMITIVE_QUICK_RISE

    private fun play(minGapMs: Long, fallback: Int, vararg steps: Triple<Int, Float, Int>) {
        val v = vibrator ?: return
        if (!enabled() || !v.hasVibrator()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < minGapMs) return
        lastAt = now
        val ids = steps.map { it.first }.toIntArray()
        val effect = if (supported(*ids)) {
            VibrationEffect.startComposition().apply { steps.forEach { (id, scale, delay) -> addPrimitive(id, scale.coerceIn(0f, 1f), delay) } }.compose()
        } else {
            VibrationEffect.createPredefined(fallback)
        }
        v.vibrate(effect)
    }

    /** Деление «трещотки» при вращении (каждые 30°). Сила — от скорости. */
    fun detent(speed: Float) = play(28, VibrationEffect.EFFECT_TICK, Triple(lowTick, 0.25f + 0.5f * speed, 0))

    /** Касание карты: лёгкий щелчок пластика. */
    fun press() = play(20, VibrationEffect.EFFECT_TICK, Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.45f, 0))

    /** Отпустили и карта «подпрыгнула». */
    fun release() = play(20, VibrationEffect.EFFECT_TICK, Triple(lowTick, 0.6f, 0))

    /** Переворот: раскрутка с нарастанием. */
    fun flip() = play(
        40, VibrationEffect.EFFECT_HEAVY_CLICK,
        Triple(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f, 0),
        Triple(spin, 0.55f, 30),
    )

    /** Карта прошла ребро — видна другая сторона. */
    fun edge() = play(25, VibrationEffect.EFFECT_CLICK, Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 0))

    /** Пружина перелетела цель и вернулась — затухающий отскок. */
    fun bounce(strength: Float) = play(45, VibrationEffect.EFFECT_TICK, Triple(lowTick, 0.15f + 0.55f * strength, 0))

    /** Приземление на сторону после сильного броска. */
    fun land(strength: Float) = play(
        60, VibrationEffect.EFFECT_HEAVY_CLICK,
        Triple(thud, 0.4f + 0.6f * strength, 0),
        Triple(lowTick, 0.3f * strength, 40),
    )

    // ── Церемония выдачи ──

    /** Протяжка карты принтером. */
    fun printerStep() = play(60, VibrationEffect.EFFECT_TICK, Triple(lowTick, 0.35f, 0))

    /** Удар штампа: тяжёлый удар и отдача. */
    fun stamp() = play(
        0, VibrationEffect.EFFECT_HEAVY_CLICK,
        Triple(thud, 1f, 0),
        Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 35),
        Triple(lowTick, 0.3f, 50),
    )

    /** Ламинатор: медленное нарастание. */
    fun laminate() = play(0, VibrationEffect.EFFECT_TICK, Triple(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.45f, 0))

    /** Вспышка голограммы. */
    fun holo() = play(
        0, VibrationEffect.EFFECT_CLICK,
        Triple(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.6f, 0),
        Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 20),
    )

    // ── Скины: прокрутка, раскрытие, обмен ──

    /** Лёгкое касание: выбор скина, отправка, отклонение. */
    fun tap() = play(20, VibrationEffect.EFFECT_TICK, Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 0))

    /** Барабан стартует. */
    fun rollStart() = play(0, VibrationEffect.EFFECT_CLICK, Triple(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.55f, 0))

    /** Стрелка барабана перескочила плашку — не чаще раза в 70 мс. */
    fun reelTick() = play(70, VibrationEffect.EFFECT_TICK, Triple(lowTick, 0.4f, 0))

    /** Раскрытие скина: чем реже тираж, тем сильнее удар (0…4). */
    fun reveal(rarity: Int) = play(
        0, VibrationEffect.EFFECT_HEAVY_CLICK,
        Triple(thud, 0.5f + 0.125f * rarity, 0),
        Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.4f + 0.15f * rarity, 60),
        Triple(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.2f * rarity, 40),
    )

    /** Карты поменялись местами. */
    fun swap() = play(
        0, VibrationEffect.EFFECT_DOUBLE_CLICK,
        Triple(spin, 0.5f, 0),
        Triple(thud, 0.8f, 120),
        Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 60),
    )

    /** «Карта выдана», скин надет или продан, обмен принят. */
    fun success() = play(
        0, VibrationEffect.EFFECT_DOUBLE_CLICK,
        Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 0),
        Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 90),
        Triple(lowTick, 0.4f, 60),
    )
}

/** Настройка «Вибрация» берётся из SettingsRepository, если он есть (в скриншот-тестах Koin — заглушка). */
@Composable
internal fun rememberCardHaptics(): CardHaptics {
    val context = LocalContext.current
    val koin = getKoin()
    return remember(context) {
        val settings = koin.getOrNull<SettingsRepository>()
        CardHaptics(context) { settings?.hapticEnabled?.value ?: true }
    }
}
