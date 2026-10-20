package org.visorlink.app.baselineprofile

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** applicationId приложения (суффикс .canary в build.gradle.kts приложения сейчас отключён). */
internal const val TARGET_PACKAGE = "org.visorlink.app"

/**
 * Листает первый прокручиваемый список вниз и обратно [rounds] раз. Список ищется заново перед
 * каждым жестом: Compose пересоздаёт узлы при рекомпозиции, и сохранённый UiObject2 устаревал
 * (StaleObjectException на fling). Нет списка (не вошли в аккаунт) — тихо ничего не делает.
 */
internal fun UiDevice.flingFirstList(rounds: Int) {
    if (!wait(Until.hasObject(By.scrollable(true)), 8_000)) return
    repeat(rounds) {
        for (direction in listOf(Direction.DOWN, Direction.UP)) {
            try {
                val list = findObject(By.scrollable(true)) ?: return
                list.setGestureMargin(displayWidth / 5)
                list.fling(direction)
            } catch (_: StaleObjectException) {
                // Узел пересоздан между поиском и жестом — следующий жест найдёт его заново
            }
            waitForIdle()
        }
    }
}
