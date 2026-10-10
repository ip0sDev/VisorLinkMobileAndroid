package org.visorlink.app.ui.theme

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.firebase.Timestamp
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
import org.visorlink.app.data.model.AppTheme
import org.visorlink.app.data.model.Chat
import org.visorlink.app.data.model.ChatType
import org.visorlink.app.data.model.ColorPreset
import org.visorlink.app.data.model.Message
import org.visorlink.app.data.model.ProfileAppearance
import org.visorlink.app.data.model.ProfileFont
import org.visorlink.app.data.model.ProfileLayout
import org.visorlink.app.data.model.ThemeMode
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.data.model.flags.AppFlags
import org.visorlink.app.data.repository.FlagsRepository
import org.visorlink.app.ui.components.VlAlertDialog
import org.visorlink.app.ui.components.VlButton
import org.visorlink.app.ui.components.VlCard
import org.visorlink.app.ui.components.VlDialogButton
import org.visorlink.app.ui.components.VlFab
import org.visorlink.app.ui.components.VlIconTray
import org.visorlink.app.ui.components.VlLiveDot
import org.visorlink.app.ui.components.VlNavigationBar
import org.visorlink.app.ui.components.VlOptionRow
import org.visorlink.app.ui.components.VlPresenceDot
import org.visorlink.app.ui.components.VlSegmentedControl
import org.visorlink.app.ui.components.VlSettingsItem
import org.visorlink.app.ui.components.VlSettingsSection
import org.visorlink.app.ui.components.VlSurface
import org.visorlink.app.ui.components.VlSwitch
import org.visorlink.app.ui.components.VlTextField
import org.visorlink.app.ui.components.VlTopAppBar
import org.visorlink.app.ui.components.chat.MessageBubble
import org.visorlink.app.ui.components.chatlist.ChatListItem
import org.visorlink.app.ui.screens.settings.ProfilePreview
import org.visorlink.app.utils.VoicePlaybackState

/**
 * Скриншот-тесты тем: каждая сцена снимается во всех [AppTheme] × светлая/тёмная.
 *
 * Эталоны лежат в `app/src/test/screenshots`. Команды:
 * - `./gradlew recordRoborazziPlayDebug` — перезаписать эталоны;
 * - `./gradlew verifyRoborazziPlayDebug` — сравнить с эталонами (CI);
 * - `./gradlew compareRoborazziPlayDebug` — сохранить диффы, не падая.
 *
 * Сцены собраны из настоящих компонентов `Vl*` с фиктивными данными, без
 * Firebase: приложение подменено пустым [Application], Koin — заглушкой флагов.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ru-w400dp-h1400dp-xhdpi")
class ThemeScreenshotTest(
    private val appTheme: AppTheme,
    private val dark: Boolean,
) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> =
            AppTheme.entries.flatMap { t ->
                // Forge v2 — только тёмная: светлый вариант совпал бы с тёмным
                if (t.selectable) listOf(arrayOf<Any>(t, false), arrayOf<Any>(t, true)) else listOf(arrayOf<Any>(t, true))
            }
    }

    private val modeName get() = if (dark) "dark" else "light"

    @get:Rule
    val compose = createComposeRule()

    // Компоненты, которые берут FlagsRepository через koinInject(), получают
    // заглушку без серверных флагов.
    private val flagsRepository = mock<FlagsRepository> { on { flags } doReturn MutableStateFlow(AppFlags()) }

    @Before
    fun startDi() {
        startKoin { modules(module { single { flagsRepository } }) }
    }

    @After
    fun stopDi() = stopKoin()

    /**
     * Часы Compose остановлены: в сценах есть бесконечные анимации (`VlLiveDot`,
     * свечение FAB), и с автопрокруткой тест вечно ждал бы idle. Кадр снимается
     * через фиксированное время — так скриншот детерминирован.
     */
    private fun snap(scene: String, wholeScreen: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VisorLinkTheme(
                appTheme = appTheme,
                themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                setStatusBarColor = false,
            ) {
                // Surface, а не Box.background: как и Scaffold в реальных экранах,
                // он выставляет LocalContentColor = onBackground. Фон-терминал Forge v2 —
                // как у корня приложения в MainActivity
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.vlTerminalBackdrop(VlTheme.tokens)) {
                    Box(Modifier.width(400.dp).padding(16.dp)) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        val path = "src/test/screenshots/${scene}_${appTheme.id}_$modeName.png"
        // Диалог живёт в отдельном окне, onRoot() его не видит — снимаем весь экран
        if (wholeScreen) captureScreenRoboImage(path) else compose.onRoot().captureRoboImage(path)
    }

    @Test
    fun controls() = snap("controls") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Заголовок экрана", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Основной текст. The quick brown fox — съешь же ещё этих мягких булок.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VlButton(onClick = {}, hapticEnabled = false) { Text("Отправить") }
                VlButton(onClick = {}, enabled = false, hapticEnabled = false) { Text("Выкл") }
                VlButton(onClick = {}, isDestructive = true, hapticEnabled = false) { Text("Удалить") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VlSwitch(checked = true, onCheckedChange = {}, hapticEnabled = false)
                VlSwitch(checked = false, onCheckedChange = {}, hapticEnabled = false)
                VlIconTray(icon = Icons.Default.Lock)
                VlIconTray(icon = Icons.Default.Palette, selected = true)
                VlIconTray(icon = Icons.Default.Notifications, isError = true)
            }
            VlSegmentedControl(
                labels = listOf("Система", "Светлая", "Тёмная"),
                selectedIndex = 1,
                onSelected = {},
                hapticEnabled = false,
            )
            VlTextField(value = "", onValueChange = {}, placeholder = "Поиск чатов")
            VlTextField(value = "ivan@visorlink.org", onValueChange = {}, label = "Почта")
            VlTextField(value = "123", onValueChange = {}, label = "Пароль", isError = true)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                VlFab(onClick = {}, icon = Icons.Default.Edit, hapticEnabled = false)
                VlFab(onClick = {}, icon = Icons.Default.Add, text = "Новый чат", hapticEnabled = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VlLiveDot()
                VlPresenceDot(online = true)
                VlPresenceDot(online = false)
            }
        }
    }

    @Test
    fun surfaces() = snap("surfaces") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VlTopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = {}) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null) }
                },
            )
            VlCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Карточка", style = MaterialTheme.typography.titleMedium)
                    Text("Вторичный текст карточки", style = MaterialTheme.typography.bodySmall)
                }
            }
            VlSurface(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Text("VlSurface")
            }
            VlSettingsSection(title = "Аккаунт") {
                VlSettingsItem(icon = Icons.Default.Person, title = "Профиль", subtitle = "Имя, аватар, био", index = 0)
                VlSettingsItem(icon = Icons.Default.Lock, title = "Безопасность", index = 1)
                VlSettingsItem(icon = Icons.Default.Notifications, title = "Выйти", isDestructive = true, index = 2)
            }
            Column {
                VlOptionRow(icon = Icons.Default.Palette, label = "Biolume", selected = true, onClick = {}, desc = "Неоморфизм", index = 0, total = 2)
                VlOptionRow(icon = Icons.Default.Palette, label = "Forge", selected = false, onClick = {}, index = 1, total = 2)
            }
        }
    }

    @Test
    fun chatList() = snap("chatlist") {
        val me = "me"
        val now = Timestamp(1_790_000_000L, 0)
        fun chat(id: String, name: String, text: String, sender: String, unread: Int) = Chat(
            id = id,
            type = "direct",
            participants = listOf(me, id),
            name = name,
            // ChatListItem берёт имя собеседника из participantData, а не из профиля
            participantData = mapOf(id to mapOf("displayName" to name, "username" to id)),
            lastMessage = mapOf("text" to text, "senderId" to sender, "readBy" to listOf<String>()),
            lastMessageSenderId = sender,
            lastMessageAt = now,
            unreadCount = mapOf(me to unread),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatListItem(
                chat = chat("u1", "Иван Петров", "Привет! Скинешь файлы?", "u1", 3),
                chatType = ChatType.DIRECT,
                currentUid = me,
                otherProfile = UserProfile(uid = "u1", displayName = "Иван Петров", username = "ivan", online = true),
                draftText = null,
                unreadCount = 3,
                onClick = {},
            )
            ChatListItem(
                chat = chat("u2", "Anna", "Ок, договорились", me, 0),
                chatType = ChatType.DIRECT,
                currentUid = me,
                otherProfile = UserProfile(uid = "u2", displayName = "Anna", username = "anna"),
                draftText = "черновик ответа",
                onClick = {},
            )
            ChatListItem(
                chat = chat("u3", "Мария", "печатает…", "u3", 0),
                chatType = ChatType.DIRECT,
                currentUid = me,
                otherProfile = UserProfile(uid = "u3", displayName = "Мария", username = "maria"),
                draftText = null,
                isTyping = true,
                onClick = {},
            )
            // Строка «Приглашения»: синтетический чат, встаёт в список по дате приглашения
            ChatListItem(
                chat = Chat(id = "invites_me", lastMessageAt = now),
                chatType = ChatType.DIRECT,
                currentUid = me,
                otherProfile = null,
                draftText = null,
                unreadCount = 2,
                isInvites = true,
                previewOverride = "2 новых приглашения",
                onClick = {},
            )
            ChatListItem(
                chat = chat("u4", "Компактный режим", "Длинное сообщение, которое должно обрезаться многоточием в конце строки", "u4", 12),
                chatType = ChatType.DIRECT,
                currentUid = me,
                otherProfile = UserProfile(uid = "u4", displayName = "Компактный режим", username = "compact"),
                draftText = null,
                unreadCount = 12,
                isCompactList = true,
                onClick = {},
            )
        }
    }

    @Test
    fun bubbles() = snap("bubbles") {
        val now = Timestamp(1_790_000_000L, 0)
        @Composable
        fun bubble(id: String, text: String, mine: Boolean) = MessageBubble(
            message = Message(id = id, senderId = if (mine) "me" else "u1", senderUsername = "ivan", text = text, createdAt = now),
            isMine = mine,
            otherUid = "u1",
            currentUid = "me",
            chatType = ChatType.DIRECT,
            hapticEnabled = false,
            showSenderName = false,
            voicePlayback = VoicePlaybackState(),
            onPlayVoice = { _, _ -> },
            onSeekVoice = {},
            onLongPressStart = {},
            onLongPressDrag = {},
            onLongPressEnd = {},
            onMediaTap = { _, _ -> },
            onAlbumTap = { _, _ -> },
            onReact = {},
            onReplyClick = {},
            onMentionClick = {},
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            bubble("m1", "Привет! Как дела с релизом?", mine = false)
            bubble("m2", "Почти готово, осталось починить темы", mine = true)
            bubble("m3", "Длинное сообщение, чтобы проверить перенос строк и ширину пузыря на всю доступную ширину экрана телефона.", mine = false)
            bubble("m4", "👍", mine = true)
        }
    }

    @Test
    fun navigationBar() = snap("navbar") {
        Column {
            Spacer(Modifier.height(24.dp))
            VlNavigationBar(
                selectedTab = 0,
                onTabSelected = {},
                diaryEnabled = true,
                discoverEnabled = true,
                onOpenDiary = {},
            )
        }
    }

    /**
     * Оформление профиля поверх темы зрителя (параметр теста). Первая карточка —
     * без оформления: должна совпадать с темой зрителя. Остальные переопределяют
     * акцент, шрифт, тему и раскладку — превью редактора рисуется так же.
     */
    /**
     * Страница профиля поверх фото: вуаль ([ProfileBackdropScrim]), шапка и детали. Владелец —
     * PRO со светлым HEX-акцентом: раньше в светлой теме @ник и ссылки на нём пропадали, а
     * тёмный текст на тёмном фото под белой вуалью 20 % не читался.
     */
    @Test
    fun profilePage() = snap("profilepage") {
        val user = UserProfile(
            uid = "u1", displayName = "Иван Петров", username = "ivan", online = true, isAdmin = true,
            bio = "Пишу код и катаюсь на велике. Сайт: https://visorlink.org",
            proUntil = Timestamp(java.util.Date(System.currentTimeMillis() + 86_400_000L)),
        )
        val appearance = ProfileAppearance(accentHex = "#FFE45C", emojis = "🚲")
        ProfileAppearanceTheme(
            appearance = appearance,
            viewerTheme = appTheme,
            viewerMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
            viewerPreset = ColorPreset.DEFAULT,
            // Цвет профиля PRO заменяет оттенок Forge v2 (в Biolume его перекрывает accentHex профиля)
            proAccent = androidx.compose.ui.graphics.Color(0xFFFFE45C),
        ) {
            Box(Modifier.fillMaxWidth()) {
                // Пёстрое «фото» фона: тёмные и светлые пятна, как у настоящей фотографии
                Box(
                    Modifier.matchParentSize().background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(
                                androidx.compose.ui.graphics.Color(0xFF1B263B),
                                androidx.compose.ui.graphics.Color(0xFFE76F51),
                                androidx.compose.ui.graphics.Color(0xFFF4F1DE),
                                androidx.compose.ui.graphics.Color(0xFF264653),
                            )
                        )
                    )
                )
                org.visorlink.app.ui.screens.profile.ProfileBackdropScrim(Modifier.matchParentSize())
                Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    org.visorlink.app.ui.screens.profile.ProfileHeader(user = user, appearance = appearance, isMe = false)
                    org.visorlink.app.ui.screens.profile.ProfileDetails(user, isMe = false)
                }
            }
        }
    }

    /**
     * Лента «Каналы»: пост с моим 👍, комментариями и просмотрами, пост с вложением, которое
     * лента не проигрывает, и каталог — «Подписаться» / «Перейти».
     */
    @Test
    fun feed() = snap("feed") {
        val channel = Chat(id = "c1", type = "channel", name = "VisorLink News")
        val liked = org.visorlink.app.data.model.FeedPost(
            channel,
            Message(
                id = "m1", senderId = "author", commentsCount = 12, viewsCount = 1834,
                text = "Вышло обновление 4.3: инвентарь скинов ID-карт и обмен в чатах. Подробности — https://visorlink.org",
                reactions = listOf(mapOf("emoji" to org.visorlink.app.data.model.FEED_LIKE_EMOJI, "uids" to listOf("me", "a", "b"), "count" to 3L)),
            ),
        )
        val video = org.visorlink.app.data.model.FeedPost(
            channel.copy(settings = org.visorlink.app.data.model.ChatSettings(allowComments = false)),
            Message(id = "m2", senderId = "author", type = "video", url = "https://lh3.googleusercontent.com/d/v", driveFileId = "v", caption = "Как работает обмен"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(liked, video).forEach { post ->
                org.visorlink.app.ui.components.feed.FeedPostCard(
                    post = post, currentUid = "me",
                    onLike = {}, onComments = {}, onOpenChannel = {}, onOpenImage = {}, onAction = {},
                )
            }
            org.visorlink.app.ui.components.feed.CuratedChannelCard(
                channel = org.visorlink.app.data.model.CuratedChannel("c2", "Спорт", "Главные матчи дня и разборы", tag = "sport", memberCount = 15400),
                subscribed = false, joining = false, onSubscribe = {}, onOpen = {},
            )
            org.visorlink.app.ui.components.feed.CuratedChannelCard(
                channel = org.visorlink.app.data.model.CuratedChannel("c1", "VisorLink News", tag = "news", memberCount = 3),
                subscribed = true, joining = false, onSubscribe = {}, onOpen = {},
            )
        }
    }

    /**
     * Вкладка «Профиль» (флаг enable_profile_navbar): шапка — ID-карта вместо аватара, под ней
     * детали профиля без второй карты, внизу навбар с выбранной вкладкой «Профиль».
     */
    @Test
    fun profileTab() = snap("profiletab") {
        val user = UserProfile(
            uid = "u1", displayName = "Иван Петров", username = "ivan_petrov", online = true,
            bio = "Пишу код и катаюсь на велике.",
        )
        val card = org.visorlink.app.data.idcard.IdCard(
            serial = org.visorlink.app.data.idcard.IdCardGenerator.generateSerial(42L),
            traits = org.visorlink.app.data.idcard.IdCardGenerator.generateTraits(42L),
            issuedAt = 1_791_200_000_000L,
            registeredAt = 1_741_910_400_000L,
            version = 1,
        )
        Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            org.visorlink.app.ui.idcard.IdCardHeader(card, user)
            org.visorlink.app.ui.screens.profile.ProfileDetails(user, isMe = true, idCardSlot = false)
            VlNavigationBar(
                selectedTab = org.visorlink.app.ui.components.NAV_TAB_PROFILE,
                onTabSelected = {},
                diaryEnabled = true,
                discoverEnabled = true,
                onOpenDiary = {},
                profileEnabled = true,
            )
        }
    }

    @Test
    fun profileAppearance() = snap("profile") {
        val profile = UserProfile(uid = "u1", displayName = "Иван Петров", username = "ivan")
        val cases = listOf(
            "без оформления" to ProfileAppearance.None,
            "акцент + округлый" to ProfileAppearance(accent = ColorPreset.CRIMSON, font = ProfileFont.ROUNDED, emojis = "🔥"),
            "M3E + засечки, компактный" to ProfileAppearance(theme = AppTheme.MATERIAL3_EXPRESSIVE, font = ProfileFont.SERIF, layout = ProfileLayout.COMPACT),
            "Biolume + моно + синий" to ProfileAppearance(theme = AppTheme.BIOLUME, font = ProfileFont.MONO, accent = ColorPreset.BLUE),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cases.forEach { (label, appearance) ->
                Text(label, style = MaterialTheme.typography.labelMedium)
                ProfileAppearanceTheme(
                    appearance = appearance,
                    viewerTheme = appTheme,
                    viewerMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                    viewerPreset = ColorPreset.DEFAULT,
                ) {
                    ProfilePreview(profile = profile, appearance = appearance)
                }
            }
        }
    }

    @Test
    fun dialog() = snap("dialog", wholeScreen = true) {
        Box(Modifier.height(320.dp).fillMaxWidth()) {
            VlAlertDialog(
                onDismissRequest = {},
                title = { Text("Удалить чат?") },
                text = { Text("История сообщений будет удалена у вас. Это действие нельзя отменить.") },
                actions = {
                    VlDialogButton(onClick = {}) { Text("Отмена") }
                    VlDialogButton(onClick = {}, isPrimary = true, isDestructive = true) { Text("Удалить") }
                },
            )
        }
    }
}
