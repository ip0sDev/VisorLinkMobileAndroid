package org.visorlink.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import org.visorlink.app.data.idcard.IdEdition

/**
 * Материалы ID-карты: пластик, краска, фольга, магнитная полоса (веб: idcard.css). Это не
 * тема приложения — карта выглядит одинаково в любой теме, — но цвета по правилу проекта
 * живут в ui/theme. Палитры режимов и вариантов — в IdCardPalette.kt.
 */
object IdCardMaterials {
    // ── Голограмма: подложка фольги ──
    val HoloSilver = listOf(0f to Color(0xFFE9EEF4), 0.45f to Color(0xFFA9B5C3), 0.6f to Color(0xFFF4F7FA), 1f to Color(0xFF9DABBB))
    val HoloGold = listOf(0f to Color(0xFFFBE6A6), 0.45f to Color(0xFFC9962F), 0.6f to Color(0xFFFFF1C4), 1f to Color(0xFFB8862A))
    val HoloGalaxy = listOf(0f to Color(0xFF6A4CC0), 0.58f to Color(0xFF1E1650), 1f to Color(0xFF0A0820))

    // ── Радуга фольги (repeating-linear-gradient 118deg; последний стоп — период) ──
    val RainbowClassic = listOf(
        0f to Color(0xFFFF6B9A), 0.035f to Color(0xFFFFD36B), 0.07f to Color(0xFF7BFFB2),
        0.105f to Color(0xFF6BD7FF), 0.14f to Color(0xFFB58CFF), 0.175f to Color(0xFFFF6B9A),
    )
    val RainbowAurora = listOf(
        0f to Color(0xFF3DFFB8), 0.05f to Color(0xFF3DD8FF), 0.1f to Color(0xFF9B6BFF),
        0.14f to Color(0xFFFF6BD1), 0.19f to Color(0xFF3DFFB8),
    )
    val RainbowGalaxy = listOf(
        0f to Color(0xFFFF4FD8), 0.06f to Color(0xFF6B8BFF), 0.11f to Color(0xFF3DF2FF),
        0.16f to Color(0xFFB58CFF), 0.22f to Color(0xFFFF4FD8),
    )

    /** «Скрытое изображение» галактики. */
    val RevealGalaxy = listOf(
        0f to Color.White, 0.04f to Color(0xFFFF9BE6), 0.08f to Color(0xFF9BB4FF),
        0.11f to Color(0xFF7BFFF0), 0.14f to Color.White,
    )

    // ── Отделка ──
    val Pearl = listOf(
        0f to Color.Transparent, 0.22f to Color.Transparent, 0.33f to Color(0x66FFBEE6), 0.44f to Color(0x66BEE4FF),
        0.55f to Color(0x59C8FFE1), 0.65f to Color(0x59FFF0C8), 0.78f to Color.Transparent, 1f to Color.Transparent,
    )
    val GoldEdge = Color(0xD9FFECB4)
    val GoldInner = Color(0x59C9962F)
    val Legendary = listOf(Color(0xFFB8862A), Color(0xFFFFE9A8), Color(0xFFC9962F), Color(0xFFFFF1C4))

    // ── Потёртость на светлом пластике ──
    val WearShadowLight = Color(0xFF6E5A3C)
    val SmudgeLight = Color(0xFF463728)
    val ScratchLight = Color(0x42283240)

    // ── Оборот ──
    val Void = Color(0xFF020304)
    val StripeStandard = listOf(0f to Color(0xFF1C1E22), 0.55f to Color(0xFF0B0C0E), 1f to Color(0xFF18191C))
    val StripeBeast = listOf(Color(0xFF0B0908), Color(0xFF1A1612), Color(0xFF0B0908))
    val BarcodeInk = Color(0xFF111214)
    val SignaturePaper = Color(0xFFF4F1EA)
    val SignaturePaper2 = Color(0xFFE9E4D8)
    val SignatureInkBeast = Color(0xFF4A3018)
    val ProtogenStrip = Color(0xFF040506)

    // ── Protogen: визор и переходные отверстия дорожек ──
    val LedTop = Color(0xFF0C0F14)
    val Via = Color(0xFF06080B)

    // ── Скины (idskins.css): цвет тиража — «предметный», как название тиража на обороте ──

    fun edition(e: IdEdition): Color = when (e) {
        IdEdition.COMMON -> hsl(215f, 12f, 66f)
        IdEdition.UNCOMMON -> hsl(150f, 62f, 54f)
        IdEdition.RARE -> hsl(212f, 88f, 64f)
        IdEdition.EPIC -> hsl(276f, 80f, 70f)
        IdEdition.LEGENDARY -> hsl(42f, 95f, 58f)
    }

    /** `--ed-soft`: подложка значка тиража и лучей раскрытия. */
    fun editionSoft(e: IdEdition): Color = edition(e).copy(
        alpha = when (e) {
            IdEdition.COMMON, IdEdition.UNCOMMON -> 0.16f
            IdEdition.RARE -> 0.18f
            IdEdition.EPIC -> 0.2f
            IdEdition.LEGENDARY -> 0.22f
        },
    )

    /** Переливающееся золото: название legendary-тиража при раскрытии и значок тиража. */
    val LegendaryShimmer = listOf(hsl(42f, 95f, 52f), hsl(52f, 100f, 75f), hsl(30f, 95f, 55f), hsl(42f, 95f, 52f))

    /** Вращающийся ореол вокруг legendary-карты. */
    val LegendaryHalo = listOf(hsl(42f, 95f, 58f), hsl(20f, 95f, 60f), hsl(52f, 100f, 70f), hsl(42f, 95f, 58f))

    /** Бегущая кайма кнопки прокрутки — цвета тиражей. */
    val RollRim = listOf(hsl(276f, 80f, 70f), hsl(42f, 95f, 58f), hsl(150f, 62f, 54f))

    /** Монеты при продаже скина. */
    val Coin = hsl(42f, 95f, 58f)
    val CoinEdge = hsl(38f, 80f, 40f)

    /** Кружок голограммы на плашке барабана (conic-gradient). */
    val ReelHolo = listOf(Color(0xFFFF8BD1), Color(0xFF8BD8FF), Color(0xFFA5FFB0), Color(0xFFFFE28B), Color(0xFFFF8BD1))
    val ReelHoloGalaxy = listOf(Color(0xFF2B1A5E), Color(0xFF7A5CFF), Color(0xFFFF79D9), Color(0xFF39E1FF), Color(0xFF2B1A5E))
    val ReelLegendaryGlint = hsl(42f, 100f, 80f, 0.45f)

    // ── Формы ──

    /**
     * Скругления самой карты и её элементов (фото, визор, полоса подписи). Это форма
     * предмета, а не темы: в Forge карта не должна становиться квадратной, поэтому не
     * shapes.adapt().
     */
    fun corner(radius: Dp): Shape = RoundedCornerShape(radius)

    fun corners(topStart: Dp, topEnd: Dp, bottomEnd: Dp, bottomStart: Dp): Shape =
        RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomEnd = bottomEnd, bottomStart = bottomStart)
}
