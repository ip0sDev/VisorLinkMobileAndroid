package by.iposdev.visorlink.di

import by.iposdev.visorlink.data.repository.AuthRepository
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.ForwardRepository
import by.iposdev.visorlink.data.repository.SavedMessagesRepository
import by.iposdev.visorlink.data.repository.StickerPackRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.ui.appcheck.AppCheckViewModel
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.screens.chat.ChatViewModel
import by.iposdev.visorlink.ui.screens.chatlist.ChatListViewModel
import by.iposdev.visorlink.ui.screens.comments.CommentsViewModel
import by.iposdev.visorlink.ui.screens.group.ChatSettingsViewModel
import by.iposdev.visorlink.ui.screens.profile.OtherProfileViewModel
import by.iposdev.visorlink.ui.screens.profile.ProfileViewModel
import by.iposdev.visorlink.ui.screens.saved.SavedMessagesViewModel
import by.iposdev.visorlink.ui.screens.search.SearchViewModel
import by.iposdev.visorlink.ui.screens.settings.CacheViewModel
import by.iposdev.visorlink.ui.screens.stickers.StickerPackViewModel
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.update.AppUpdateViewModel
import by.iposdev.visorlink.utils.CacheManager
import by.iposdev.visorlink.utils.DraftManager
import by.iposdev.visorlink.utils.VoicePlayerManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.firebase.storage.storage
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // ─── Firebase ─────────────────────────────────────────────────────────────

    single {
        Firebase.firestore.also { db ->
            db.firestoreSettings = firestoreSettings {
                isPersistenceEnabled = true
                cacheSizeBytes = FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED
            }
        }
    }
    single { Firebase.auth }
    single { Firebase.storage }
    single { Firebase.functions("europe-west1") }

    // ─── Repositories ─────────────────────────────────────────────────────────

    single { AuthRepository(get(), get()) }
    single { ChatRepository(get(), get(), get(), get(), androidContext()) }
    single { UserRepository(get(), get(), get(), get(), androidContext()) }
    single { StickerPackRepository(get(), androidContext()) }

    // ─── Utils ────────────────────────────────────────────────────────────────

    single { CacheManager(androidContext()) }
    single { VoicePlayerManager(androidContext()) }
    single { DraftManager(androidContext()) }

    // ─── ViewModels ───────────────────────────────────────────────────────────

    viewModel { AppCheckViewModel() }

    viewModel { AuthViewModel(get()) }
    viewModel { ThemeViewModel(androidContext()) }
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
    single { SavedMessagesRepository(get(), get()) }
    single { ForwardRepository(get()) }

    viewModel { SavedMessagesViewModel(get(), get(), get(), androidContext(), get()) }
}