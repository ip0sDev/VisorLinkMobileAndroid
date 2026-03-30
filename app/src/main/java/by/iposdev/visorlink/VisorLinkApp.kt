package by.iposdev.visorlink

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import by.iposdev.visorlink.di.appModule
import by.iposdev.visorlink.utils.NotificationHelper
import by.iposdev.visorlink.utils.PresenceManager
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VisorLinkApp : Application() {

    private var presenceManager: PresenceManager? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)

        // ─── Firebase App Check ───────────────────────────────────────────────
        // Debug builds → DebugAppCheckProviderFactory.
        //   Класс подключается через рефлексию, чтобы release-сборка не требовала
        //   firebase-appcheck-debug артефакт (он добавлен как debugImplementation).
        //   При первом запуске печатает в logcat:
        //   "D/ProviderInstaller: Debug secret: XXXXXXXX-XXXX-..."
        //   Этот UUID нужно добавить в Firebase Console →
        //   App Check → ваше приложение → Manage debug tokens.
        //
        // Release builds → PlayIntegrityAppCheckProviderFactory.
        if (BuildConfig.DEBUG) {
            try {
                val factoryClass = Class.forName(
                    "com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory"
                )
                val getInstance = factoryClass.getMethod("getInstance")
                val factory = getInstance.invoke(null)
                Firebase.appCheck.installAppCheckProviderFactory(
                    factory as com.google.firebase.appcheck.AppCheckProviderFactory
                )
            } catch (e: Exception) {
                // firebase-appcheck-debug не подключён — fallback на Play Integrity
                Firebase.appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        } else {
            Firebase.appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

        startKoin {
            androidContext(this@VisorLinkApp)
            modules(appModule)
        }

        // Автоматически управляем presence при смене auth state
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid != null) {
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