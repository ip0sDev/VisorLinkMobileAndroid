package by.iposdev.visorlink

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import by.iposdev.visorlink.di.appModule
import by.iposdev.visorlink.utils.ActiveChatTracker
import by.iposdev.visorlink.utils.NotificationHelper
import by.iposdev.visorlink.utils.OutboxManager
import by.iposdev.visorlink.utils.PresenceManager
import by.iposdev.visorlink.data.repository.FlagsRepository
import by.iposdev.visorlink.data.repository.UserRepository
import by.iposdev.visorlink.utils.DiaryReminderManager
import by.iposdev.visorlink.utils.CacheManager
import by.iposdev.visorlink.utils.AppImageLoader
import io.sentry.android.core.SentryAndroid
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.functions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class VisorLinkApp : Application(), ImageLoaderFactory {

    private var presenceManager: PresenceManager? = null

    override fun newImageLoader(): ImageLoader {
        return AppImageLoader.get(this)
    }

    override fun onCreate() {
        super.onCreate()
        
        SentryAndroid.init(this) { options ->
            // Performance monitoring (10% samples in production to prevent overhead)
            options.tracesSampleRate = 0.1
            // User feedback
            options.isEnableUserInteractionTracing = false
            // Profile sessions - disabled to eliminate thread profiling lag in release builds
            options.profilesSampleRate = 0.0
        }

        NotificationHelper.createChannels(this)
        by.iposdev.visorlink.utils.DiagnosticLogBuffer.install()

        startKoin {
            androidContext(this@VisorLinkApp)
            modules(appModule)
        }

        // ─── Firebase App Check — ОБЯЗАТЕЛЬНО до любых Firestore-запросов ───
        try {
            if (BuildConfig.DEBUG) {
                try {
                    val factoryClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    val getInstance = factoryClass.getMethod("getInstance")
                    val factory = getInstance.invoke(null)
                    Firebase.appCheck.installAppCheckProviderFactory(
                        factory as com.google.firebase.appcheck.AppCheckProviderFactory
                    )
                } catch (_: Exception) {
                    Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
                }
            } else {
                Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            }
        } catch (e: Exception) {
            Log.e("VisorLinkApp", "Failed to init AppCheck", e)
        }

        // Initialize ImageLoader immediately on Main thread with default/fast config
        val cacheManager: CacheManager = GlobalContext.get().get()
        AppImageLoader.init(this, cacheManager.loadConfig())

        // OutboxManager — инициализируем сразу, чтобы был готов к отправке сообщений
        try {
            GlobalContext.get().get<OutboxManager>()
        } catch (_: Exception) {}

        // ─── Некритичные инициализации в фоне ────────────────
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        appScope.launch {
            // 1. IposStoreUpdates
            try {
                val updatePrefs = getSharedPreferences("visorlink_settings", MODE_PRIVATE)
                val savedChannelStr = updatePrefs.getString("update_channel", "release")
                val initialChannel = try {
                    com.ipos.store.sdk.UpdateChannel.fromString(savedChannelStr)
                } catch (_: IllegalArgumentException) {
                    com.ipos.store.sdk.UpdateChannel.RELEASE
                }
                com.ipos.store.sdk.IposStoreUpdates.init(this@VisorLinkApp, channel = initialChannel)
            } catch (e: Exception) {
                Log.e("VisorLinkApp", "Failed to init IposStoreUpdates", e)
            }

            // 2. Очистка кэша
            try {
                cacheManager.evictIfNeeded()
            } catch (_: Exception) {}

            // 3. Feature Flags
            try {
                GlobalContext.get().get<FlagsRepository>().fetchFlags()
            } catch (e: Exception) {
                Log.e("VisorLinkApp", "Failed to fetch flags", e)
            }
        }

        // ─── Diary Reminders Observer ───
        MainScope().launch {
            try {
                val userRepository: UserRepository = GlobalContext.get().get()
                val reminderManager: DiaryReminderManager = GlobalContext.get().get()
                userRepository.currentUserFlow().collect { profile ->
                    if (profile?.diaryEnabled == true && profile.diaryRemindersEnabled) {
                        reminderManager.scheduleReminder(profile.diaryReminderTime)
                    } else {
                        reminderManager.cancelReminder()
                    }
                }
            } catch (e: Exception) {
                Log.e("VisorLinkApp", "Diary reminder observer failed", e)
            }
        }

        // Регистрируем наблюдатель за жизненным циклом (свернуто/развернуто)
        // через анонимный объект, чтобы не было конфликтов с методами Application
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                ActiveChatTracker.isAppInForeground = true
            }

            override fun onStop(owner: LifecycleOwner) {
                ActiveChatTracker.isAppInForeground = false
            }
        })

        // Автоматически управляем presence и FCM-токеном при смене auth state
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid != null) {
                if (presenceManager?.uid != uid) {
                    presenceManager?.detach()
                    presenceManager = PresenceManager(uid).also {
                        it.attach(ProcessLifecycleOwner.get().lifecycle)
                    }
                }
                MainScope().launch {
                    try {
                        GlobalContext.get().get<by.iposdev.visorlink.utils.FcmManager>().syncTokenAfter2FA()
                    } catch (e: Exception) {
                        Log.e("VisorLinkApp", "FCM token sync failed on auth state change", e)
                    }
                }
            } else {
                presenceManager?.detach()
                presenceManager = null
            }
        }
    }
}