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
import org.visorlink.app.ui.navigation.*
import org.visorlink.app.ui.screens.auth.AuthScreen
import org.visorlink.app.ui.screens.chatlist.ChatListScreen
import org.visorlink.app.ui.theme.VisorLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisorLinkTheme {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = AuthRoute,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        composable<AuthRoute> {
                            AuthScreen(
                                onLoginSuccess = {
                                    navController.navigate(ChatListRoute)
                                }
                            )
                        }
                        composable<ChatListRoute> {
                            ChatListScreen()
                        }
                    }
                }
            }
        }
    }
}