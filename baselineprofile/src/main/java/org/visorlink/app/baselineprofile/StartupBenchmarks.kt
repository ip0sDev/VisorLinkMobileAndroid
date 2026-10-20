package org.visorlink.app.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Замеры «до/после» baseline-профиля: холодный старт и кадры при прокрутке списка.
 *
 * Запуск: `./gradlew :baselineprofile:connectedPlayBenchmarkReleaseAndroidTest` на реальном устройстве
 * (на эмуляторе цифры не годятся). Сравнивайте `...NoCompilation` и `...BaselineProfile`.
 * Для осмысленных цифр прокрутки войдите в приложение заранее.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun scrollNoCompilation() = scroll(CompilationMode.None())

    @Test
    fun scrollBaselineProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 8,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        device.flingFirstList(rounds = 3)
    }
}
