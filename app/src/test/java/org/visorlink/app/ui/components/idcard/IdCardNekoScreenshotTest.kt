package org.visorlink.app.ui.components.idcard

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import org.visorlink.app.data.idcard.IdFinish
import org.visorlink.app.data.idcard.IdFoil
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.idcard.IdPattern
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ThemeMode
import org.visorlink.app.data.model.flags.AppFlags
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.theme.VisorLinkTheme

/**
 * Авторский узор NEKO: голограмма-кошка, сетка из лапок и мордочек, кот под луной. Лицо ровно,
 * лицо и оборот под наклоном — «скрытое изображение» видно только под углом.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ru-w420dp-h1400dp-xxhdpi")
class IdCardNekoScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val flagsRepository = mock<FlagsRepository> { on { flags } doReturn MutableStateFlow(AppFlags()) }

    @Before
    fun startDi() {
        startKoin { modules(module { single { flagsRepository } }) }
    }

    @After
    fun stopDi() = stopKoin()

    @Test
    fun neko() {
        val seed = 22102026L
        val card = IdCard(
            mode = IdMode.STANDARD,
            serial = "VL-LUNA-2210",
            traits = IdCardGenerator.generateTraits(seed).copy(finish = IdFinish.GOLD, foil = IdFoil.GALAXY, laminated = true, wear = 0),
            issuedAt = 1_792_616_400_000L,
            registeredAt = 1_773_775_219_040L,
            version = 1,
            custom = IdCustomLook(
                base = "#1A1030", primary = "#C9A0DC", secondary = "#FFF555",
                holo = null, label = "КИРЯ · ЛУННЫЙ КОТ · 1/1", pattern = IdPattern.NEKO,
            ),
        )
        val person = IdCardPerson("Малис", "malisprod", null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VisorLinkTheme(appTheme = AppTheme.BIOLUME, themeMode = ThemeMode.DARK, setStatusBarColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.width(420.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(36.dp)) {
                        VlIdCard(card, person, width = 380.dp, emojis = "💀", interactive = false)
                        VlIdCard(card, person, width = 380.dp, emojis = "💀", interactive = false, previewTilt = Offset(-16f, 7f))
                        VlIdCard(card, person, width = 380.dp, emojis = "💀", interactive = false, previewTilt = Offset(20f, -9f))
                        VlIdCard(card, person, width = 380.dp, interactive = false, initialFace = 1, previewTilt = Offset(-14f, 6f))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("src/test/screenshots/idcard_neko.png")
    }
}
