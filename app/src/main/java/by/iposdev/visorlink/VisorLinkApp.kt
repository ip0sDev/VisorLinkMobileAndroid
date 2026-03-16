package by.iposdev.visorlink

import android.app.Application
import by.iposdev.visorlink.di.appModule
import by.iposdev.visorlink.utils.NotificationHelper
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VisorLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        startKoin {
            androidContext(this@VisorLinkApp)
            modules(appModule)
        }
    }
}