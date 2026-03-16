package by.iposdev.visorlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import by.iposdev.visorlink.ui.VisorLinkNavGraph
import by.iposdev.visorlink.ui.screens.auth.AuthViewModel
import by.iposdev.visorlink.ui.theme.ThemeViewModel
import by.iposdev.visorlink.ui.theme.VisorLinkTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = koinViewModel()
            val authViewModel: AuthViewModel = koinViewModel()
            val appTheme by themeViewModel.appTheme.collectAsState()

            authViewModel.initPresenceIfLoggedIn()

            VisorLinkTheme(appTheme = appTheme, darkTheme = isSystemInDarkTheme()) {
                VisorLinkNavGraph(
                    authViewModel = authViewModel,
                    themeViewModel = themeViewModel
                )
            }
        }
    }
}