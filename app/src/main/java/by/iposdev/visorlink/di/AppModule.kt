package by.iposdev.visorlink.di

import by.iposdev.visorlink.data.model.StickerPack
import by.iposdev.visorlink.data.repository.AuthRepository
import by.iposdev.visorlink.data.repository.ChatRepository
import by.iposdev.visorlink.data.repository.StickerPackRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.screens.chat.ChatViewModel
import by.iposdev.visorlink.ui.screens.chatlist.ChatListViewModel
import by.iposdev.visorlink.ui.screens.comments.CommentsViewModel
import by.iposdev.visorlink.ui.screens.group.ChatSettingsViewModel
import by.iposdev.visorlink.ui.screens.profile.OtherProfileViewModel
import by.iposdev.visorlink.ui.screens.profile.ProfileViewModel
import by.iposdev.visorlink.ui.screens.search.SearchViewModel
import by.iposdev.visorlink.ui.screens.settings.CacheViewModel
import by.iposdev.visorlink.ui.screens.stickers.StickerPackViewModel
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.update.AppUpdateViewModel
import by.iposdev.visorlink.utils.CacheManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.firebase.storage.storage
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
    single { Firebase.functions("us-central1") }

    // ─── Repositories ─────────────────────────────────────────────────────────

    single { AuthRepository(get(), get()) }
    single { ChatRepository(get(), get(), get(), get()) }
    single { UserRepository(get(), get(), get(), get()) }

    // ── Sticker Packs ─────────────────────────────────────────────────────────
    single { StickerPackRepository(get(), get(), get(), get()) }

    // ─── Utils ────────────────────────────────────────────────────────────────

    single { CacheManager(androidContext()) }

    // ─── ViewModels ───────────────────────────────────────────────────────────

    viewModel { AuthViewModel(get()) }
    viewModel { ThemeViewModel(androidContext()) }
    viewModel { ChatListViewModel(get(), get(), get()) }

    viewModel { parameters ->
        ChatViewModel(
            chatRepository = get(),
            userRepository = get(),
            auth           = get(),
            db             = get(),          // ← добавить
            context        = androidContext(),
            chatId         = parameters.get(),
            otherUid       = parameters.get()
        )
    }

    // ─── v4: Comments ─────────────────────────────────────────────────────────

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

    // ─── Search / Profile / Settings ──────────────────────────────────────────

    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get()) }
    viewModel { parameters -> OtherProfileViewModel(get(), get(), get(), parameters.get()) }

    // ─── Legacy personal stickers screen ──────────────────────────────────────
    viewModel { StickerPackViewModel(get(), get()) }

    // ── NEW: Sticker Pack picker ───────────────────────────────────────────────
    //  Объявляем как single чтобы состояние паков не пересоздавалось при каждом
    //  открытии StickerPickerBottomSheet (Koin сам вернёт тот же экземпляр).
    viewModel { StickerPackViewModel(get(), get()) }

    // ─── Chat Settings ────────────────────────────────────────────────────────

    viewModel { params ->
        ChatSettingsViewModel(
            chatRepository = get(),
            userRepository = get(),
            auth           = get(),
            db             = get(),
            chatId         = params.get()
        )
    }

    // ─── App Update / Cache ───────────────────────────────────────────────────

    viewModel { AppUpdateViewModel() }
    viewModel { CacheViewModel(get(), androidContext()) }
}