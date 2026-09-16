// di/AppModule.kt
package by.iposdev.visorlink.di

import by.iposdev.visorlink.data.aegis.*
import by.iposdev.visorlink.data.remote.flags.AegisKeyManager
import by.iposdev.visorlink.data.remote.flags.FlagsApi
import by.iposdev.visorlink.data.remote.chat.BackendConnectivityInterceptor
import by.iposdev.visorlink.data.remote.chat.ChatWebSocketClient
import by.iposdev.visorlink.data.remote.chat.DynamicBaseUrlInterceptor
import by.iposdev.visorlink.data.remote.chat.FirebaseAuthInterceptor
import by.iposdev.visorlink.data.remote.chat.VisorLinkApi
import by.iposdev.visorlink.data.repository.*
import by.iposdev.visorlink.ui.aegis.AegisLifeViewModel
import by.iposdev.visorlink.ui.aegis.LinkDebugViewModel
import by.iposdev.visorlink.ui.appcheck.AppCheckViewModel
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.screens.chat.ChatViewModel
import by.iposdev.visorlink.ui.screens.music.MusicViewModel
import by.iposdev.visorlink.ui.screens.diary.DiaryViewModel
import by.iposdev.visorlink.ui.screens.chatlist.ChatListViewModel
import by.iposdev.visorlink.ui.screens.main.MainViewModel
import by.iposdev.visorlink.ui.screens.feed.FeedViewModel
import by.iposdev.visorlink.ui.screens.comments.CommentsViewModel
import by.iposdev.visorlink.ui.screens.group.ChatSettingsViewModel
import by.iposdev.visorlink.ui.screens.profile.OtherProfileViewModel
import by.iposdev.visorlink.ui.screens.profile.ProfileViewModel
import by.iposdev.visorlink.ui.screens.saved.SavedMessagesViewModel
import by.iposdev.visorlink.ui.screens.search.SearchViewModel
import by.iposdev.visorlink.ui.screens.settings.CacheViewModel
import by.iposdev.visorlink.ui.screens.settings.StorageViewModel
import by.iposdev.visorlink.ui.screens.status.StatusViewModel
import by.iposdev.visorlink.ui.screens.settings.ProViewModel
import by.iposdev.visorlink.ui.screens.settings.CustomizationViewModel
import by.iposdev.visorlink.ui.screens.settings.FlagFlipperViewModel
import by.iposdev.visorlink.ui.screens.stickers.StickerPackViewModel
import by.iposdev.visorlink.ui.screens.topics.TopicListViewModel
import by.iposdev.visorlink.ui.screens.topics.TaskTrackerViewModel
import by.iposdev.visorlink.ui.components.mediapicker.MediaPickerViewModel
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.utils.CacheManager
import by.iposdev.visorlink.utils.TfaManager
import by.iposdev.visorlink.utils.DiaryReminderManager
import by.iposdev.visorlink.utils.DraftManager
import by.iposdev.visorlink.utils.NetworkMonitor
import by.iposdev.visorlink.utils.OutboxManager
import by.iposdev.visorlink.utils.VoicePlayerManager
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
    single { by.iposdev.visorlink.data.repository.TypingRepository(get()) }
    single { by.iposdev.visorlink.utils.SidebarTypingManager(get()) }

    single {
        Retrofit.Builder()
            .baseUrl("https://flags.visorlink.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FlagsApi::class.java)
    }
    single { AegisKeyManager() }
    single { FlagsRepository(androidContext(), get(), get()) }
    single { BackendFallbackManager(androidContext(), get()) { get<VisorLinkApi>() } }

    // --- Chat Backend ---
    single(named("chatOkHttp")) {
        OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(androidContext()))
            .addInterceptor(BackendConnectivityInterceptor(get()))
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
    single { ChatWebSocketClient(get(named("chatOkHttp")), get()) }

    single { AuthRepository(get(), get(), get(), get()) }
    single { ChatRepository(get(), get(), get(), androidContext(), get(), get(), get(), get()) }
    single { UserRepository(get(), get(), get(), androidContext(), get(), get()) }
    single { TopicsRepository(get(), get()) }
    single { StickerPackRepository(get(), androidContext()) }
    single { BotRepository(get(), get(), get()) }
    single { LegalRepository(get(), androidContext(), get(), get(), get()) }

    // ── Google Drive & Storage ──
    single { by.iposdev.visorlink.data.repository.GoogleDriveConfigRepository(get()) }
    single { by.iposdev.visorlink.utils.GoogleDriveAuthManager(androidContext()) }
    single { by.iposdev.visorlink.data.remote.GoogleDriveService(get()) }

    single { CacheManager(androidContext()) }
    single { TfaManager(androidContext()) }
    single { by.iposdev.visorlink.utils.StealthManager(androidContext()) }
    single { VoicePlayerManager(androidContext()) }
    single { by.iposdev.visorlink.utils.MusicPlayerManager(androidContext()) }
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
            fallbackManager = get(),
            driveService = get(),
            driveAuthManager = get()
        )
    }
    single { DraftManager(androidContext()) }
    single { DiaryReminderManager(androidContext()) }
    single { SettingsRepository(androidContext()) }
    single { by.iposdev.visorlink.utils.FcmManager(androidContext(), get(), get()) }
    single { by.iposdev.visorlink.utils.UsageRankManager(androidContext()) }

    viewModel { AppCheckViewModel() }

    viewModel { AuthViewModel(get(), get(), get(), get()) }
    viewModel { ThemeViewModel(get()) }
    viewModel { MainViewModel(get(), get(), get()) }
    viewModel { ChatListViewModel(get(), get(), get(), get(), get(), androidApplication(), get(), get()) }
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
    viewModel { StorageViewModel(get(), get()) }
    viewModel { StatusViewModel(get(), get(), get(named("chatOkHttp"))) }

    single { SavedMessagesRepository(get(), androidContext()) }
    single { FeedRepository(get(), get(), androidContext(), get(), get(), get()) }
    single { ForwardRepository(get(), get(), get()) }
    single { by.iposdev.visorlink.utils.BiometricPinManager(androidContext()) }

    viewModel { SavedMessagesViewModel(get(), get(), get(), get(), androidApplication(), get(), get(), get()) }
    viewModel { DiaryViewModel(get(), get(), get(), androidApplication(), get(), get()) }
    viewModel { FeedViewModel(get(), get(), get()) }

    viewModel { ProViewModel(get()) }
    viewModel { CustomizationViewModel(get()) }
    viewModel { FlagFlipperViewModel(get()) }
    viewModel { MediaPickerViewModel(androidApplication()) }
    
    // ── Bug Reports & FaultyWire ──
    single { by.iposdev.visorlink.data.remote.CdnUploadService(OkHttpClient()) }
    single { by.iposdev.visorlink.data.repository.BugReportRepository(get()) }
    viewModel { by.iposdev.visorlink.ui.screens.settings.BugReportViewModel(get(), get()) }
    
    // ── Aegis Project ──
    single { DictionaryRepository(androidContext()) }
    single { DictionaryHeuristicEngine(get()) }
    single { MediaPipeLlmEngine() }
    viewModel { LinkDebugViewModel(get(), get(), get()) }
    viewModel { AegisLifeViewModel(get(), get()) }
}
