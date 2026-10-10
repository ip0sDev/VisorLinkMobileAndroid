package org.visorlink.app.ui.components.idcard

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.visorlink.app.data.idcard.IdCard
import org.visorlink.app.data.idcard.IdCardGenerator
import org.visorlink.app.data.idcard.IdMode
import org.visorlink.app.data.idcard.IdOfferStatus
import org.visorlink.app.data.idcard.IdSkin
import org.visorlink.app.data.idcard.IdSkinOrigin
import org.visorlink.app.data.idcard.IdSkinWearer
import org.visorlink.app.data.idcard.IdTrade
import org.visorlink.app.data.idcard.IdTradeDeal
import org.visorlink.app.data.idcard.IdTradeOffer
import org.visorlink.app.data.idcard.IdTradeStatus
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.ThemeMode
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.model.flags.AppFlags
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.data.repository.IdCardRepository
import org.visorlink.app.data.repository.IdCardState
import org.visorlink.app.data.repository.IdSkinsState
import org.visorlink.app.data.repository.IdTradeState
import org.visorlink.app.data.repository.UserRepository
import org.visorlink.app.ui.components.VlSettingsSection
import org.visorlink.app.ui.idcard.IdModeUiState
import org.visorlink.app.ui.idcard.LocalIdCardClock
import org.visorlink.app.ui.idcard.IdSkinInventory
import org.visorlink.app.ui.idcard.IdSkinPicker
import org.visorlink.app.ui.idcard.IdSkinRoll
import org.visorlink.app.ui.idcard.IdSkinSheet
import org.visorlink.app.ui.idcard.IdSwapFx
import org.visorlink.app.ui.idcard.IdTradeMessage
import org.visorlink.app.ui.idcard.IdTradeOffers
import org.visorlink.app.ui.idcard.SkinPickPurpose
import org.visorlink.app.ui.idcard.LocalIdModeState
import org.visorlink.app.ui.idcard.ModeOption
import org.visorlink.app.ui.idcard.SpeciesField
import org.visorlink.app.ui.components.VlDialogButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import org.visorlink.app.ui.theme.VisorLinkTheme

/**
 * Инвентарь скинов, лист скина, прокрутка и карточки обмена в чате (спека
 * ANDROID_ID_SKINS_AND_SESSIONS_SPEC.md). Репозитории — заглушки с фиксированными данными.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ru-w400dp-h1000dp-xhdpi")
class IdSkinScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = 1_791_200_000_000L
    private val day = 24L * 3600 * 1000

    private fun skin(id: String, seed: Long, origin: IdSkinOrigin = IdSkinOrigin.ROLL, listedIn: String? = null): IdSkin {
        val t = IdCardGenerator.generateTraits(seed)
        return IdSkin(id, IdCardGenerator.generateSerial(seed), t, t.edition, now - 30 * day, now - 10 * day, origin, trades = if (origin == IdSkinOrigin.TRADE) 2 else 0, listedIn = listedIn)
    }

    private val skins = listOf(
        skin("s1", 123456789L, IdSkinOrigin.ISSUE),
        skin("s2", 3735928559L, listedIn = "T"),
        skin("s3", 42L, IdSkinOrigin.TRADE),
    )

    private fun card(mode: IdMode = IdMode.STANDARD, rolledAt: Long = now - 8 * day) = IdCard(
        mode = mode,
        species = if (mode == IdMode.STANDARD) null else "Снежный барс",
        serial = skins[0].serial,
        traits = skins[0].traits,
        issuedAt = now - 30 * day,
        registeredAt = 1_741_910_400_000L,
        skinId = "s1",
        slots = 6,
        rolledAt = rolledAt,
    )

    private val me = UserProfile(uid = "me", username = "ivan_petrov", displayName = "Иван Петров", bits = 350, createdAt = Timestamp(1_741_910_400L, 0))

    private fun trade(id: String, status: IdTradeStatus, owner: String = "u2", closedReason: String? = null) = IdTrade(
        id = id, chatId = "C", messageId = "m-$id", ownerUid = owner, ownerName = if (owner == "me") "ivan_petrov" else "foxy",
        skinId = "x", skin = skins[1].look, note = "Меняю на золото", status = status, closedReason = closedReason, offerCount = 2,
        deal = if (status == IdTradeStatus.DONE) IdTradeDeal("me", "me", "ivan_petrov", "s3", skins[2].look) else null,
    )

    private val trades = listOf(
        trade("open", IdTradeStatus.OPEN),
        trade("mine", IdTradeStatus.OPEN, owner = "me"),
        trade("pending", IdTradeStatus.OPEN),
        trade("done", IdTradeStatus.DONE),
        trade("closed", IdTradeStatus.CLOSED, closedReason = "owner"),
    )

    private val flagsRepository = mock<FlagsRepository> { on { flags } doReturn MutableStateFlow(AppFlags()) }
    private val users = mock<UserRepository> { on { userProfileFlow(any()) } doReturn flowOf(me) }
    private var cardForRepo = card()
    private val repo = mock<IdCardRepository> {
        on { cardFlow(any()) } doReturn flowOf(IdCardState.Ready(cardForRepo))
        on { skinsFlow(any()) } doReturn flowOf(IdSkinsState.Ready(skins))
        trades.forEach { t -> on { tradeFlow(t.id) } doReturn flowOf(IdTradeState.Ready(t)) }
        on { myOfferFlow(any(), any()) } doReturn flowOf(null)
        on { myOfferFlow("pending", "me") } doReturn flowOf(IdTradeOffer("me", "me", "ivan_petrov", "s3", skins[2].look, IdOfferStatus.PENDING))
        onBlocking { rollSkin() } doReturn skin("new", 777L)
        on { pendingOffersFlow(any()) } doReturn flowOf(
            listOf(
                IdTradeOffer("u3", "u3", "foxy", "x1", skin("x1", 99L).look, IdOfferStatus.PENDING),
                IdTradeOffer("u4", "u4", "protoboi", "x2", skin("x2", 2024L).look, IdOfferStatus.PENDING),
            ),
        )
    }

    private val wearer = IdSkinWearer(IdMode.STANDARD, null, 1_741_910_400_000L)
    private val person = IdCardPerson("Иван Петров", "ivan_petrov", null)

    @Before
    fun startDi() {
        startKoin { modules(module { single { flagsRepository }; single { users }; single { repo } }) }
    }

    @After
    fun stopDi() = stopKoin()

    private fun scene(theme: AppTheme = AppTheme.BIOLUME, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VisorLinkTheme(appTheme = theme, themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, setStatusBarColor = false) {
                // Снимки в обмене без даты выпуска показывают «Выдана сегодня» — день фиксирован
                CompositionLocalProvider(
                    LocalIdModeState provides IdModeUiState(enabled = true, myUid = "me"),
                    LocalIdCardClock provides { now },
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    private fun inventory(theme: AppTheme, dark: Boolean, name: String) {
        scene(theme, dark) {
            Column(Modifier.width(400.dp).padding(vertical = 8.dp)) {
                VlSettingsSection(title = "Инвентарь скинов") { IdSkinInventory(card = card(), profile = me) }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("src/test/screenshots/idskin_inventory_$name.png")
    }

    @Test fun inventoryBiolumeDark() = inventory(AppTheme.BIOLUME, true, "biolume_dark")

    @Test fun inventoryBiolumeLight() = inventory(AppTheme.BIOLUME, false, "biolume_light")

    @Test fun inventoryForgeProtogen() = inventory(AppTheme.FORGE_PROTOGEN, true, "forge_protogen")

    @Test
    fun inventoryCooldownOldCard() {
        // Карта до инвентаря: подписка пуста — бланк карты единственный надетый скин; кулдаун прокрутки
        val old = card(rolledAt = System.currentTimeMillis() - 2 * day).copy(skinId = null, slots = null)
        val emptyRepo = mock<IdCardRepository> { on { skinsFlow(any()) } doReturn flowOf(IdSkinsState.Ready(emptyList())) }
        stopKoin()
        startKoin { modules(module { single { flagsRepository }; single { users }; single { emptyRepo } }) }
        scene {
            Column(Modifier.width(400.dp).padding(vertical = 8.dp)) {
                VlSettingsSection(title = "Инвентарь скинов") { IdSkinInventory(card = old, profile = me.copy(bits = 40)) }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("src/test/screenshots/idskin_inventory_cooldown.png")
    }

    @Test
    @Config(qualifiers = "ru-w400dp-h2900dp-xhdpi")
    fun tradeCards() {
        scene {
            Column(Modifier.width(400.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                trades.forEach { t ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.width(320.dp)) {
                        IdTradeMessage(tradeId = t.id, messageId = t.messageId, chatId = "C", modifier = Modifier.padding(10.dp))
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.width(320.dp)) {
                    // Подделка: обмен из другого сообщения
                    IdTradeMessage(tradeId = "open", messageId = "forged", chatId = "C", modifier = Modifier.padding(10.dp))
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("src/test/screenshots/idskin_trade_cards.png")
    }

    @Test
    fun skinSheet() {
        scene {
            IdSkinSheet(skins[1], isEquipped = false, wearer = IdSkinWearer(IdMode.STANDARD, null, 1_741_910_400_000L), person = IdCardPerson("Иван Петров", "ivan_petrov", null), onClose = {})
        }
        compose.mainClock.advanceTimeBy(1_500)
        captureScreenRoboImage("src/test/screenshots/idskin_sheet.png")
    }

    @Test
    fun rollReelAndReveal() {
        scene {
            IdSkinRoll(wearer = IdSkinWearer(IdMode.BEAST, "Снежный барс", 1_741_910_400_000L), person = IdCardPerson("Иван Петров", "ivan_petrov", null), onClose = {}, random = kotlin.random.Random(7))
        }
        compose.mainClock.advanceTimeBy(2_600)
        captureScreenRoboImage("src/test/screenshots/idskin_roll_reel.png")
        compose.mainClock.advanceTimeBy(8_000)
        captureScreenRoboImage("src/test/screenshots/idskin_roll_reveal.png")
    }

    @Test
    fun pickerOffer() {
        scene { IdSkinPicker(SkinPickPurpose.Offer(trades[0], wearer), onClose = {}) }
        compose.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("src/test/screenshots/idskin_picker_offer.png")
    }

    @Test
    fun offers() {
        scene { IdTradeOffers(trades[1], wearer, person, onClose = {}) }
        compose.mainClock.advanceTimeBy(1_500)
        captureScreenRoboImage("src/test/screenshots/idskin_offers.png")
    }

    @Test
    fun swap() {
        scene {
            Column(Modifier.width(400.dp).padding(16.dp)) { IdSwapFx(skins[1].look, skins[2].look, wearer, person, onDone = {}) }
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().captureRoboImage("src/test/screenshots/idskin_swap_mid.png")
        compose.mainClock.advanceTimeBy(1_800)
        compose.onRoot().captureRoboImage("src/test/screenshots/idskin_swap_end.png")
    }

    /** Выбор режима и поле модели с кнопкой «Сохранить» (как в настройках «ID-карта»). */
    @Test
    fun modeOptionsForge() {
        scene(AppTheme.FORGE_PROTOGEN) {
            Column(Modifier.width(400.dp).padding(vertical = 8.dp)) {
                VlSettingsSection(title = "Режим карты") {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IdMode.entries.forEach { m ->
                            ModeOption(m, desc = "Описание режима ${m.id}", selected = m == IdMode.PROTOGEN, onClick = {}, modifier = Modifier.fillMaxWidth())
                        }
                        SpeciesField(IdMode.PROTOGEN, "MK-I", {}, Modifier.fillMaxWidth(), trailing = {
                            VlDialogButton(onClick = {}, isPrimary = true) { Text("Сохранить") }
                        })
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("src/test/screenshots/idcard_mode_options_forge_protogen.png")
    }

    /** Подарок сервиса и обмен в ленте: без url это не «архивное вложение». */
    @Test
    @Config(qualifiers = "ru-w400dp-h1300dp-xhdpi")
    fun giftAndTradeInChat() {
        val chat = org.visorlink.app.data.model.Chat(id = "C", type = "direct")
        @Composable fun bubble(m: org.visorlink.app.data.model.Message, mine: Boolean) = org.visorlink.app.ui.components.chat.MessageBubble(
            message = m, isMine = mine, otherUid = "u2", currentUid = "me",
            chatType = org.visorlink.app.data.model.ChatType.DIRECT, hapticEnabled = false, showSenderName = false,
            voicePlayback = org.visorlink.app.utils.VoicePlaybackState(),
            onPlayVoice = { _, _ -> }, onSeekVoice = {}, onLongPressStart = {}, onLongPressDrag = {}, onLongPressEnd = {},
            onMediaTap = { _, _ -> }, onAlbumTap = { _, _ -> }, onReact = {}, onReplyClick = {}, onMentionClick = {},
            chat = chat,
        )
        scene {
            Column(Modifier.width(400.dp).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bubble(org.visorlink.app.data.model.Message(id = "g", senderId = "system", type = "gift", createdAt = Timestamp(1_791_200_000L, 0)), mine = false)
                bubble(org.visorlink.app.data.model.Message(id = "m-open", senderId = "u2", type = "id_trade", tradeId = "open", text = "Меняю на золото", createdAt = Timestamp(1_791_200_000L, 0)), mine = false)
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("src/test/screenshots/idskin_gift_and_trade_in_chat.png")
    }
}
