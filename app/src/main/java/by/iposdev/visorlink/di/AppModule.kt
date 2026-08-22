// di/AppModule.kt
package by.iposdev.visorlink.di

import by.iposdev.visorlink.data.aegis.*
import by.iposdev.visorlink.data.remote.flags.AegisKeyManager
import by.iposdev.visorlink.data.remote.flags.FlagsApi
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
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.update.AppUpdateViewModel
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
            .baseUrl("http://10.0.2.2:8080") // Default for emulator, should be configurable
            .client(get(named("chatOkHttp")))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VisorLinkApi::class.java)
    }
    single { ChatWebSocketClient(get(named("chatOkHttp"))) }

    single { AuthRepository(get(), get()) }
    single { ChatRepository(get(), get(), get(), androidContext(), get(), get(), get(), get()) }
    single { UserRepository(get(), get(), get(), androidContext(), get(), get()) }
    single { StickerPackRepository(get(), androidContext()) }
    single { BotRepository(get()) }

    single { CacheManager(androidContext()) }
    single { TfaManager(androidContext()) }
    single { VoicePlayerManager(androidContext()) }
    single { NetworkMonitor(androidContext()) }
    single { OutboxManager(androidContext(), get(), get(), get(), get(), get()) }
    single { DraftManager(androidContext()) }
    single { DiaryReminderManager(androidContext()) }

    viewModel { AppCheckViewModel() }

    viewModel { AuthViewModel(get(), get(), get()) }
    viewModel { ThemeViewModel(androidContext()) }
    viewModel { MainViewModel(get(), get()) }
    viewModel { ChatListViewModel(get(), get(), get(), get()) }

    viewModel { parameters ->
        ChatViewModel(
            chatRepository = get(),
            userRepository = get(),
            auth           = get(),
            db             = get(),
            context        = androidContext(),
            draftManager   = get(),
            chatId         = parameters.get(),
            otherUid       = parameters.get()
        )
    }

    viewModel { parameters ->
        CommentsViewModel(
            chatId         = parameters.get(),
            messageId      = parameters.get(),
            chatRepository = get(),
            userRepository = get(),
            auth           = get(),
            context        = androidContext()
        )
    }

    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get()) }
    viewModel { parameters -> OtherProfileViewModel(get(), get(), get(), parameters.get()) }

    viewModel { StickerPackViewModel(get(), get()) }

    viewModel { params ->
        ChatSettingsViewModel(
            chatRepository = get(),
            userRepository = get(),
            auth           = get(),
            db             = get(),
            chatId         = params.get()
        )
    }

    viewModel { AppUpdateViewModel(androidApplication()) }
    viewModel { CacheViewModel(get(), androidContext()) }
    viewModel { StorageViewModel() }
    viewModel { StatusViewModel(get(), get()) }

    // Передаем Context для работы с файлами
    single { SavedMessagesRepository(get(), androidContext()) }
    single { FeedRepository(get(), get(), androidContext(), get(), get(), get()) }
    single { ForwardRepository(get()) }

    viewModel { SavedMessagesViewModel(get(), get(), get(), androidContext(), get()) }
    viewModel { DiaryViewModel(get(), get(), get(), androidContext(), get()) }
    viewModel { FeedViewModel(get(), get(), get()) }

    viewModel { ProViewModel(get()) }
    viewModel { CustomizationViewModel(get()) }
    viewModel { FlagFlipperViewModel(get()) }
    
    // ── Aegis Project ──
    single { DictionaryRepository(androidContext()) }
    single { DictionaryHeuristicEngine(get()) }
    single { MediaPipeLlmEngine() }
    viewModel { LinkDebugViewModel(get(), get(), get()) }
    viewModel { AegisLifeViewModel(get(), get()) }
}
