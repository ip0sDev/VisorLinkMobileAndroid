package org.visorlink.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Снимает baseline-профиль: холодный старт и прокрутка первого списка.
 *
 * Профиль полезен ровно настолько, насколько путь пользователя совпадает с реальным, поэтому
 * перед запуском ВОЙДИТЕ в приложение на устройстве (та же подпись — данные сохранятся):
 * без входа снимется только экран авторизации.
 *
 * Запуск: `./gradlew :app:generatePlayReleaseBaselineProfile` (подключённое устройство или эмулятор).
 * Результат ложится в `app/src/<вариант>/generated/baselineProfiles/` — закоммитьте его.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Список чатов, если пользователь вошёл: листаем вниз и обратно
        if (device.wait(Until.hasObject(By.scrollable(true)), 8_000)) {
            val list = device.findObject(By.scrollable(true))
            list.setGestureMargin(device.displayWidth / 5)
            repeat(2) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
                list.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }
}
