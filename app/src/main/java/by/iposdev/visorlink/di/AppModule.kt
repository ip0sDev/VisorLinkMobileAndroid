// di/AppModule.kt
package by.iposdev.visorlink.di

import by.iposdev.visorlink.data.repository.AuthRepository
import by.iposdev.visorlink.data.repository.BotRepository
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.FeedRepository
import by.iposdev.visorlink.data.repository.ForwardRepository
import by.iposdev.visorlink.data.repository.SavedMessagesRepository
import by.iposdev.visorlink.data.repository.StickerPackRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.data.repository.FlagsRepository
import by.iposdev.visorlink.data.remote.flags.AegisKeyManager
import by.iposdev.visorlink.data.remote.flags.FlagsApi
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
import by.iposdev.visorlink.ui.screens.settings.ProViewModel
import by.iposdev.visorlink.ui.screens.settings.CustomizationViewModel
import by.iposdev.visorlink.ui.screens.stickers.StickerPackViewModel
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.update.AppUpdateViewModel
import by.iposdev.visorlink.utils.CacheManager
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
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
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

    single { AuthRepository(get(), get()) }
    single { ChatRepository(get(), get(), get(), androidContext(), get()) }
    single { UserRepository(get(), get(), get(), androidContext()) }
    single { StickerPackRepository(get(), androidContext()) }
    single { BotRepository(get()) }

    single { CacheManager(androidContext()) }
    single { VoicePlayerManager(androidContext()) }
    single { NetworkMonitor(androidContext()) }
    single { OutboxManager(androidContext(), get(), get(), get(), get(), get()) }
    single { DraftManager(androidContext()) }
    single { DiaryReminderManager(androidContext()) }

    viewModel { AppCheckViewModel() }

    viewModel { AuthViewModel(get()) }
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

    // Передаем Context для работы с файлами
    single { SavedMessagesRepository(get(), androidContext()) }
    single { FeedRepository(get(), get(), androidContext(), get()) }
    single { ForwardRepository(get()) }

    viewModel { SavedMessagesViewModel(get(), get(), get(), androidContext(), get()) }
    viewModel { DiaryViewModel(get(), get(), get(), androidContext(), get()) }
    viewModel { FeedViewModel(get(), get(), get()) }

    viewModel { ProViewModel(get()) }
    viewModel { CustomizationViewModel(get()) }
}