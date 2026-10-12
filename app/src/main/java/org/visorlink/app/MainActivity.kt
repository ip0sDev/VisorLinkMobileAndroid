package org.visorlink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.visorlink.app.ui.navigation.*
import org.visorlink.app.ui.screens.auth.AuthScreen
import org.visorlink.app.ui.screens.chatlist.ChatListScreen
import org.visorlink.app.ui.screens.profile.ProfileScreen
import org.visorlink.app.ui.screens.settings.SettingsScreen
import org.visorlink.app.ui.theme.VisorLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisorLinkTheme {
                val navController = rememberNavController()

                val currentUser = Firebase.auth.currentUser

                val startDestination: Any = if (currentUser != null) {
                    ChatListRoute
                } else {
                    AuthRoute
                }
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        composable<AuthRoute> {
                            AuthScreen(
                                onLoginSuccess = {
                                    navController.navigate(ChatListRoute)
                                }
                            )
                        }
                        composable<ChatListRoute> {
                            ChatListScreen(
                                onNavigateToProfile ={
                                    navController.navigate(ProfileRoute)
                                },
                                onNavigateToSettings = {
                                    navController.navigate(SettingsRoute)
                                }
                            )
                        }
                        composable<ProfileRoute> {
                            ProfileScreen()
                        }
                        composable<SettingsRoute> {
                            SettingsScreen()
                        }
                    }
            }
        }
    }
}