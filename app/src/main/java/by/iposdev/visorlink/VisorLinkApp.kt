package by.iposdev.visorlink

import android.app.Application
import by.iposdev.visorlink.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VisorLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@VisorLinkApp)
            modules(appModule)
        }
    }
}