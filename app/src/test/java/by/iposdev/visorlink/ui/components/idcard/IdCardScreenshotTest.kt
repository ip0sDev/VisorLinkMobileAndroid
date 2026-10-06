package org.visorlink.app.ui.components.idcard

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.visorlink.app.data.idcard.IdCard
import org.visorlink.app.data.idcard.IdCardGenerator
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ThemeMode
import org.visorlink.app.data.model.flags.AppFlags
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.theme.VisorLinkTheme

/**
 * ID-карта: лицо и оборот каждого режима на нескольких seed. Карта — физический предмет и
 * от темы не зависит, поэтому снимается в одной теме. Для сверки с вебом: та же карта
 * (seed, серийник, даты) в вебе выглядит так же (спека §11).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ru-w400dp-h900dp-xhdpi")
class IdCardScreenshotTest(private val mode: IdMode, private val seed: Long) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> =
            listOf(IdMode.STANDARD, IdMode.PROTOGEN, IdMode.BEAST).flatMap { m ->
                listOf(123456789L, 42L, 3735928559L).map { arrayOf<Any>(m, it) }
            }
    }

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
    fun card() {
        val card = IdCard(
            mode = mode,
            species = if (mode == IdMode.STANDARD) null else if (mode == IdMode.PROTOGEN) "Protogen MK-II" else "Снежный барс",
            serial = IdCardGenerator.generateSerial(seed),
            traits = IdCardGenerator.generateTraits(seed),
            issuedAt = 1_791_200_000_000L,
            registeredAt = 1_741_910_400_000L,
            version = 1,
        )
        val person = IdCardPerson("Иван Петров", "ivan_petrov", null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VisorLinkTheme(appTheme = AppTheme.BIOLUME, themeMode = ThemeMode.DARK, setStatusBarColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.width(400.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        VlIdCard(card, person, width = 360.dp, accent = Color(0xFF0EA5E9), emojis = "🦊✨", interactive = false)
                        VlIdCard(card, person, width = 360.dp, interactive = false, initialFace = 1)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("src/test/screenshots/idcard_${mode.id}_$seed.png")
    }
}
