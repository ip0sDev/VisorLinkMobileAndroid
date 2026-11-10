package org.visorlink.app.ui.theme

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.visorlink.app.data.model.flags.AppFlags
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ThemeMode
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.components.*

/**
 * 1-в-1 копия витрины Flutter `vl_showcase_golden_test.dart`.
 * Снимает все 8 сцен в точности с теми же размерами (400x1000) и стилизацией.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ru-w400dp-h1000dp-mdpi")
class VlShowcaseScreenshotTest(
    private val fileName: String,
    private val appTheme: AppTheme,
    private val dark: Boolean,
) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = listOf(
            arrayOf("showcase_biolume_dark", AppTheme.BIOLUME, true),
            arrayOf("showcase_biolume_light", AppTheme.BIOLUME, false),
            arrayOf("showcase_forge_protogen", AppTheme.FORGE_PROTOGEN, true),
            arrayOf("showcase_forge_protogen_light", AppTheme.FORGE_PROTOGEN, false),
            arrayOf("showcase_forge_beast", AppTheme.FORGE_BEAST, true),
            arrayOf("showcase_forge_beast_light", AppTheme.FORGE_BEAST, false),
            arrayOf("showcase_m3e_dark", AppTheme.MATERIAL3_EXPRESSIVE, true),
            arrayOf("showcase_m3e_light", AppTheme.MATERIAL3_EXPRESSIVE, false),
        )
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
    fun showcase() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VisorLinkTheme(
                appTheme = appTheme,
                themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                setStatusBarColor = false,
                dynamicColor = false,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.width(400.dp).height(1000.dp)
                ) {
                    Scaffold(
                        topBar = {
                            VlTopAppBar(
                                title = { Text("Настройки") },
                                navigationIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                        },
                        floatingActionButton = {
                            VlFab(onClick = {}, icon = Icons.Default.Edit, hapticEnabled = false)
                        },
                        containerColor = MaterialTheme.colorScheme.background,
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(bottom = 24.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Box(Modifier.padding(start = 28.dp, top = 12.dp, end = 28.dp)) {
                                VlBrandText("VisorLink")
                            }
                            VlSettingsSection(title = "Аккаунт") {
                                VlSettingsItem(
                                    icon = Icons.Default.Person,
                                    title = "Профиль",
                                    subtitle = "@ivan_petrov",
                                    onClick = {}
                                )
                                VlSettingsItem(
                                    icon = Icons.Default.Notifications,
                                    title = "Уведомления",
                                    trailing = { VlSwitch(checked = true, onCheckedChange = {}, hapticEnabled = false) }
                                )
                                VlSettingsItem(
                                    icon = Icons.Default.Vibration,
                                    title = "Вибрация",
                                    trailing = { VlSwitch(checked = false, onCheckedChange = {}, hapticEnabled = false) }
                                )
                                VlSettingsItem(
                                    icon = Icons.AutoMirrored.Filled.Logout,
                                    title = "Выйти",
                                    isDestructive = true,
                                    onClick = {}
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                VlSegmentedControl(
                                    labels = listOf("Бот", "Почта", "Telegram"),
                                    selectedIndex = 1,
                                    onSelected = {},
                                    hapticEnabled = false
                                )
                                VlTextField(value = "", onValueChange = {}, label = "Код из 6 цифр", placeholder = "Код из 6 цифр")
                                VlTextField(value = "", onValueChange = {}, label = "Почта", placeholder = "name@example.com", isError = true, supportingText = "Неверный формат")
                                VlButton(onClick = {}, hapticEnabled = false) { Text("Подтвердить") }
                                VlButton(onClick = {}, isDestructive = true, hapticEnabled = false) { Text("Удалить аккаунт") }
                                VlButton(onClick = {}, enabled = false, hapticEnabled = false) { Text("Недоступно") }
                                VlCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        VlLiveDot()
                                        Text(
                                            "Карточка: рельеф и грань, без свечения",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                ) {
                                    VlDialogButton(label = "Отмена", onClick = {})
                                    VlDialogButton(label = "Выйти", isDestructive = true, onClick = {})
                                    VlDialogButton(label = "Сохранить", isPrimary = true, onClick = {})
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        val path = "src/test/screenshots/$fileName.png"
        compose.onRoot().captureRoboImage(path)
    }
}
