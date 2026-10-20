package org.visorlink.app.ui.surprise

import org.visorlink.app.data.idcard.IdCardGenerator
import org.visorlink.app.data.idcard.IdCustomLook
import org.visorlink.app.data.idcard.IdEdition
import org.visorlink.app.data.idcard.IdFinish
import org.visorlink.app.data.idcard.IdFoil
import org.visorlink.app.data.idcard.IdPattern
import org.visorlink.app.data.idcard.IdSkinLook
import org.visorlink.app.data.model.Surprise
import org.visorlink.app.data.model.SurpriseGiftResult
import org.visorlink.app.data.model.SurpriseSlide
import org.visorlink.app.data.model.SurpriseWish

/**
 * Демо сюрприза (настройки разработчика): весь показ на встроенных данных. Числа выдуманы,
 * подарок — карта «Лунный кот» на своей карте смотрящего, сервер не вызывается.
 */
internal object SurpriseDemo {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    val surprise = Surprise(
        id = "demo",
        fromName = "iposdev",
        message = "Это демо: так сюрприз увидит получатель. Поздравление печатается по букве — тапни, чтобы допечатать сразу.",
        slides = listOf(
            SurpriseSlide(kicker = "Всё началось", value = "17 марта", countUp = false, text = "«Привет!»", caption = "17 марта 2026, 22:36 — первое сообщение"),
            SurpriseSlide(kicker = "С тех пор прошло", value = "219 дней", caption = "и в 157 из них вы переписывались"),
            SurpriseSlide(kicker = "Сообщений", value = "12 345", text = "вы написали друг другу", caption = "ты — 5 678, @iposdev — 6 667"),
            SurpriseSlide(kicker = "Серия", value = "39 дней", text = "подряд без единого дня молчания"),
            SurpriseSlide(kicker = "Главная реакция", value = "❤️", countUp = false, text = "560 раз", caption = "всего реакций: 1 580"),
            SurpriseSlide(kicker = "Полуночники", value = "03:52", countUp = false, text = "самое позднее сообщение", caption = "11 июня 2026"),
        ),
        wishes = listOf(
            SurpriseWish("Аня", "С днём рождения! Пусть всё получается 🎉"),
            SurpriseWish("Макс", "Расти большой, не будь лапшой"),
        ),
        giftTitle = "С днём рождения!",
        giftText = "Пусть этот год будет таким же громким, как наша переписка.",
        hasGift = true,
    )

    /** Тот же дизайн, что в scripts/birthday-surprise.cjs (GIFT). */
    fun gift(now: Long) = SurpriseGiftResult(
        skin = IdSkinLook(
            serial = "VL-LUNA-2210",
            traits = IdCardGenerator.generateTraits(22102026L).copy(finish = IdFinish.GOLD, foil = IdFoil.GALAXY, laminated = true, wear = 0),
            edition = IdEdition.LEGENDARY,
            custom = IdCustomLook(
                base = "#1A1030", primary = "#C9A0DC", secondary = "#FFF555",
                holo = null, label = "КИРЯ · ЛУННЫЙ КОТ · 1/1", pattern = IdPattern.NEKO,
            ),
        ),
        skinMintedAt = now,
        equipped = true,
        bits = 5000,
        proDays = 365,
        proUntil = now + 365 * DAY_MS,
        demo = true,
    )
}
