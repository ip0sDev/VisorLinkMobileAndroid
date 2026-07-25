package by.iposdev.visorlink

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import by.iposdev.visorlink.di.appModule
import by.iposdev.visorlink.utils.ActiveChatTracker
import by.iposdev.visorlink.utils.NotificationHelper
import by.iposdev.visorlink.utils.PresenceManager
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.functions
import com.google.firebase.messaging.FirebaseMessaging
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VisorLinkApp : Application() {

    private var presenceManager: PresenceManager? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)

        // ─── Firebase App Check ───────────────────────────────────────────────
        if (BuildConfig.DEBUG) {
            try {
                val factoryClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                val getInstance = factoryClass.getMethod("getInstance")
                val factory = getInstance.invoke(null)
                Firebase.appCheck.installAppCheckProviderFactory(
                    factory as com.google.firebase.appcheck.AppCheckProviderFactory
                )
            } catch (e: Exception) {
                Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            }
        } else {
            Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }

        startKoin {
            androidContext(this@VisorLinkApp)
            modules(appModule)
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

        // Автоматически управляем presence и FCM токеном при смене auth state
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid != null) {
                // ── ИСПРАВЛЕНИЕ FCM: Отправляем токен сразу после авторизации ──
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    try {
                        Firebase.functions.getHttpsCallable("saveFcmToken").call(mapOf("token" to token))
                        Log.d("FCM", "Token synced on auth state change")
                    } catch (e: Exception) {
                        Log.e("FCM", "Failed to sync token on auth state change", e)
                    }
                }

                presenceManager?.detach()
                presenceManager = PresenceManager(uid).also {
                    it.attach(ProcessLifecycleOwner.get().lifecycle)
                }
            } else {
                presenceManager?.detach()
                presenceManager = null
            }
        }
    }
}