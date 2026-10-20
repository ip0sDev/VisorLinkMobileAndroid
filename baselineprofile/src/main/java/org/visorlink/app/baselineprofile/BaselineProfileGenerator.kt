package org.visorlink.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        device.flingFirstList(rounds = 2)
    }
}
