// di/AppModule.kt
package org.visorlink.app.di

import org.visorlink.app.data.aegis.*
import org.visorlink.app.data.remote.flags.AegisKeyManager
import org.visorlink.app.data.remote.flags.FlagsApi
import org.visorlink.app.data.remote.chat.ChatWebSocketClient
import org.visorlink.app.data.remote.chat.DynamicBaseUrlInterceptor
import org.visorlink.app.data.remote.chat.FirebaseAuthInterceptor
import org.visorlink.app.data.remote.chat.VisorLinkApi
import org.visorlink.app.data.repository.*
import org.visorlink.app.ui.aegis.AegisLifeViewModel
import org.visorlink.app.ui.aegis.LinkDebugViewModel
import org.visorlink.app.ui.appcheck.AppCheckViewModel
import org.visorlink.app.ui.screens.auth.AuthViewModel
import org.visorlink.app.ui.screens.chat.ChatViewModel
import org.visorlink.app.ui.screens.music.MusicViewModel
import org.visorlink.app.ui.screens.diary.DiaryViewModel
import org.visorlink.app.ui.screens.chatlist.ChatListViewModel
import org.visorlink.app.ui.screens.main.MainViewModel
import org.visorlink.app.ui.screens.feed.FeedViewModel
import org.visorlink.app.ui.screens.comments.CommentsViewModel
import org.visorlink.app.ui.screens.group.ChatSettingsViewModel
import org.visorlink.app.ui.screens.profile.OtherProfileViewModel
import org.visorlink.app.ui.screens.profile.ProfileViewModel
import org.visorlink.app.ui.screens.saved.SavedMessagesViewModel
import org.visorlink.app.ui.screens.search.SearchViewModel
import org.visorlink.app.ui.screens.settings.CacheViewModel
import org.visorlink.app.ui.screens.settings.StorageViewModel
import org.visorlink.app.ui.screens.status.StatusViewModel
import org.visorlink.app.ui.screens.settings.ProViewModel
import org.visorlink.app.ui.screens.settings.CustomizationViewModel
import org.visorlink.app.ui.screens.settings.FlagFlipperViewModel
import org.visorlink.app.ui.screens.stickers.StickerPackViewModel
import org.visorlink.app.ui.screens.topics.TopicListViewModel
import org.visorlink.app.ui.screens.topics.TaskTrackerViewModel
import org.visorlink.app.ui.components.mediapicker.MediaPickerViewModel
import org.visorlink.app.ui.theme.ThemeViewModel
import org.visorlink.app.utils.CacheManager
import org.visorlink.app.utils.TfaManager
import org.visorlink.app.utils.DiaryReminderManager
import org.visorlink.app.utils.DraftManager
import org.visorlink.app.utils.NetworkMonitor
import org.visorlink.app.utils.OutboxManager
import org.visorlink.app.utils.VoicePlayerManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val appModule = module {

    single {
        Firebase.firestore.also { db ->
            db.firestoreSettings = firestoreSettings {
                isPersistenceEnabled = true
                cacheSizeBytes = FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED
            }
        }
    }
    single { Firebase.auth }
    single { Firebase.functions("europe-west1") }
    single {
        com.google.firebase.database.FirebaseDatabase.getInstance("https://visorlink-f9484-default-rtdb.europe-west1.firebasedatabase.app")
    }
    single { org.visorlink.app.data.repository.TypingRepository(get()) }
    single { org.visorlink.app.utils.SidebarTypingManager(get()) }

    single {
        Retrofit.Builder()
            .baseUrl("https://flags.visorlink.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FlagsApi::class.java)
    }
    single { AegisKeyManager() }
    single { FlagsRepository(androidContext(), get(), get()) }

    // --- Chat Backend ---
    single(named("chatOkHttp")) {
        OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(androidContext()))
            .addInterceptor(FirebaseAuthInterceptor())
            .build()
    }
    single {
        Retrofit.Builder()
            .baseUrl("https://backend.visorlink.org/")
            .client(get(named("chatOkHttp")))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VisorLinkApi::class.java)
    }
    single { ChatWebSocketClient(get(named("chatOkHttp"))) }

    // --- Google Drive Storage ---
    single { org.visorlink.app.data.repository.GoogleDriveConfigRepository(get()) }
    single { org.visorlink.app.utils.GoogleDriveAuthManager(androidContext()) }
    single { org.visorlink.app.data.remote.GoogleDriveMediaService(get()) }

    // --- Yandex Disk Emergency Relay (enable_alternative_outbox) ---
    single { org.visorlink.app.data.remote.yandex.YandexRelayConfigManager(androidContext(), get()) }
    single { org.visorlink.app.data.remote.yandex.YandexDiskTransportService() }
    single { org.visorlink.app.data.remote.yandex.YandexDeadDropManager(androidContext(), get(), get()) }

    single { AuthRepository(get(), get(), get(), get()) }
    single {
        ChatRepository(
            auth = get(),
            db = get(),
            functions = get(),
            context = androidContext(),
            networkMonitor = get(),
            api = get(),
            wsClient = get(),
            flagsRepository = get(),
            googleDriveAuthManager = get(),
            googleDriveMediaService = get(),
            yandexDeadDropManager = get()
        )
    }
    single { UserRepository(get(), get(), get(), androidContext(), get(), get()) }
    single { TopicsRepository(get(), get()) }
    single { StickerPackRepository(get(), androidContext()) }
    single { BotRepository(get()) }
    single { LegalRepository(get(), androidContext(), get(), get()) }

    single { CacheManager(androidContext()) }
    single { TfaManager(androidContext()) }
    single { org.visorlink.app.utils.StealthManager(androidContext()) }
    single { VoicePlayerManager(androidContext()) }
    single { org.visorlink.app.utils.MusicPlayerManager(androidContext()) }
    single { MusicDatabase(androidContext()) }
    single { MusicRepository(androidContext()) }
    single { NetworkMonitor(androidContext()) }
    single {
        OutboxManager(
            context = androidContext(),
            chatRepository = get(),
            userRepository = get(),
            feedRepository = get(),
            functions = get(),
            networkMonitor = get(),
            googleDriveAuthManager = get(),
            googleDriveMediaService = get(),
            yandexDeadDropManager = get()
        )
    }
    single { DraftManager(androidContext()) }
    single { DiaryReminderManager(androidContext()) }
    single { SettingsRepository(androidContext()) }
    single { org.visorlink.app.utils.FcmManager(androidContext(), get(), get()) }
    single { org.visorlink.app.utils.UsageRankManager(androidContext()) }

    viewModel { AppCheckViewModel() }

    viewModel { AuthViewModel(get(), get(), get(), get()) }
    viewModel { ThemeViewModel(get()) }
    viewModel { MainViewModel(get(), get()) }
    viewModel {
        ChatListViewModel(
            chatRepository = get(),
            userRepository = get(),
            auth = get(),
            draftManager = get(),
            context = androidApplication(),
            sidebarTypingManager = get(),
            networkMonitor = get()
        )
    }
    viewModel { MusicViewModel(get(), get()) }

    viewModel { parameters ->
        ChatViewModel(
            chatRepository = get(),
            userRepository = get(),
            auth           = get(),
            db             = get(),
            context        = androidApplication(),
            draftManager   = get(),
            chatId         = parameters[0],
            otherUid       = parameters[1],
            initialTopicId = if (parameters.size() > 2) parameters[2] else null,
            typingRepository = get(),
            musicPlayerManager = get(),
            musicRepository = get(),
            networkMonitor = get(),
            usageRankManager = get()
        )
    }

    viewModel { parameters ->
        TopicListViewModel(
            chatId           = parameters.get(),
            topicsRepository = get(),
            chatRepository   = get(),
            userRepository   = get(),
            auth             = get(),
            db               = get()
        )
    }

    viewModel { parameters ->
        TaskTrackerViewModel(
            chatId           = parameters[0],
            topicId          = parameters[1],
            topicsRepository = get(),
            chatRepository   = get(),
            userRepository   = get(),
            auth             = get(),
            db               = get()
        )
    }

    viewModel { parameters ->
        CommentsViewModel(
            chatId         = parameters.get(),
            messageId      = parameters.get(),
            chatRepository = get(),
            userRepository = get(),
            auth           = get(),
            application    = androidApplication(),
            usageRankManager = get()
        )
    }

    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { parameters -> OtherProfileViewModel(get(), get(), get(), parameters.get()) }

    viewModel { StickerPackViewModel(get(), get(), get()) }

    viewModel { params ->
        ChatSettingsViewModel(
            chatRepository   = get(),
            userRepository   = get(),
            topicsRepository = get(),
            auth             = get(),
            db               = get(),
            chatId           = params.get()
        )
    }

    viewModel { CacheViewModel(get(), androidApplication()) }
    viewModel { StorageViewModel(get(), get(), get()) }
    viewModel { StatusViewModel(get(), get(), get(named("chatOkHttp"))) }

    single { SavedMessagesRepository(get(), androidContext()) }
    single { FeedRepository(get(), get(), androidContext(), get(), get(), get()) }
    single { ForwardRepository(get(), get(), get()) }
    single { org.visorlink.app.utils.BiometricPinManager(androidContext()) }

    viewModel { SavedMessagesViewModel(get(), get(), get(), get(), androidApplication(), get(), get(), get()) }
    viewModel { DiaryViewModel(get(), get(), get(), androidApplication(), get(), get()) }
    viewModel { FeedViewModel(get(), get(), get()) }

    viewModel { ProViewModel(get()) }
    viewModel { CustomizationViewModel(get()) }
    viewModel { FlagFlipperViewModel(get()) }
    viewModel { MediaPickerViewModel(androidApplication()) }
    
    // ── Bug Reports & FaultyWire ──
    single { org.visorlink.app.data.repository.BugReportRepository(get()) }
    single { org.visorlink.app.data.repository.ReportRepository(get(), get(), get()) }
    viewModel { org.visorlink.app.ui.screens.settings.BugReportViewModel(get()) }
    
    // ── Aegis Project ──
    single { DictionaryRepository(androidContext()) }
    single { DictionaryHeuristicEngine(get()) }
    single { MediaPipeLlmEngine() }
    viewModel { LinkDebugViewModel(get(), get(), get()) }
    viewModel { AegisLifeViewModel(get(), get()) }
}
