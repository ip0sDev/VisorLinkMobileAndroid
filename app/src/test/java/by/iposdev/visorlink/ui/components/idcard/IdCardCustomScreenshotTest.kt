package org.visorlink.app.ui.components.idcard

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
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
import org.visorlink.app.data.idcard.IdCard
import org.visorlink.app.data.idcard.IdCardGenerator
import org.visorlink.app.data.idcard.IdCustomLook
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ThemeMode
import org.visorlink.app.data.model.flags.AppFlags
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.theme.VisorLinkTheme

/**
 * Кастомный скин (спека §5.4): своя палитра из трёх HEX, своя голограмма, надпись выпуска на
 * обороте и «Эпический» тираж; плюс своя нарисованная подпись (§2.2) — на лице по центру,
 * на обороте прижата влево.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ru-w400dp-h900dp-xhdpi")
class IdCardCustomScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val flagsRepository = mock<FlagsRepository> { on { flags } doReturn MutableStateFlow(AppFlags()) }

    @Before
    fun startDi() {
        startKoin { modules(module { single { flagsRepository } }) }
    }

    @After
    fun stopDi() = stopKoin()

    private fun snap(name: String, mode: IdMode, custom: IdCustomLook, signature: String?) {
        val seed = 1L
        val card = IdCard(
            mode = mode,
            species = if (mode == IdMode.BEAST) "Снежный барс" else null,
            serial = IdCardGenerator.generateSerial(seed),
            traits = IdCardGenerator.generateTraits(seed),
            issuedAt = 1_791_200_000_000L,
            registeredAt = 1_741_910_400_000L,
            version = 1,
            custom = custom,
        )
        val person = IdCardPerson("Иван Петров", "ivan_petrov", null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VisorLinkTheme(appTheme = AppTheme.BIOLUME, themeMode = ThemeMode.DARK, setStatusBarColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.width(400.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        VlIdCard(card, person, width = 360.dp, interactive = false, signature = signature)
                        VlIdCard(card, person, width = 360.dp, interactive = false, initialFace = 1, signature = signature)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("src/test/screenshots/idcard_custom_$name.png")
    }

    @Test
    fun lightStandardWithDrawnSignature() = snap(
        "standard_light", IdMode.STANDARD,
        IdCustomLook(base = "#F3EEE4", primary = "#7A2E8E", secondary = "#E0A100", holo = IdCardGenerator.HoloShape.SHIELD, label = "Осенняя серия 2026"),
        signature = "M8 22L12 12L16 22M14 18L19 18M24 22L26 10L30 20L34 10L36 22M42 20L50 14L58 21L66 12L74 20",
    )

    @Test
    fun darkBeast() = snap(
        "beast_dark", IdMode.BEAST,
        IdCustomLook(base = "#0E1A2B", primary = "#5FD8FF", secondary = "#FF5FA2", holo = IdCardGenerator.HoloShape.ROSETTE, label = "Для @visortestz"),
        signature = null,
    )
}
