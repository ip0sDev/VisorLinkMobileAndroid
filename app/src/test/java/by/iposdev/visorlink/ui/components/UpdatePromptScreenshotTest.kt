package org.visorlink.app.ui.components

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ThemeMode
import org.visorlink.app.data.model.flags.AppFlags
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.theme.VisorLinkTheme

/** Попап обновления из Google Play: доступно (с размером) и загружено. Оба закрываются «Позже». */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ru-w400dp-h800dp-xhdpi")
class UpdatePromptScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val flagsRepository = mock<FlagsRepository> { on { flags } doReturn MutableStateFlow(AppFlags()) }

    @Before
    fun startDi() {
        startKoin { modules(module { single { flagsRepository } }) }
    }

    @After
    fun stopDi() = stopKoin()

    private fun snap(name: String, dialog: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VisorLinkTheme(appTheme = AppTheme.BIOLUME, themeMode = ThemeMode.DARK, setStatusBarColor = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { dialog() }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("src/test/screenshots/update_play_$name.png")
    }

    @Test
    fun available() = snap("available") { UpdateAvailableDialog(sizeText = "12 МБ", onUpdate = {}, onLater = {}) }

    @Test
    fun ready() = snap("ready") { UpdateReadyDialog(onRestart = {}, onLater = {}) }
}
