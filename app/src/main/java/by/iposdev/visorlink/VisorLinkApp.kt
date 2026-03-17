package by.iposdev.visorlink

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import by.iposdev.visorlink.di.appModule
import by.iposdev.visorlink.utils.NotificationHelper
import by.iposdev.visorlink.utils.PresenceManager
import com.google.firebase.auth.FirebaseAuth
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VisorLinkApp : Application() {

    private var presenceManager: PresenceManager? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)

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