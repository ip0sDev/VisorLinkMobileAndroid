package org.visorlink.app.ui.navigation

import kotlinx.serialization.Serializable
@Serializable
object AuthRoute
@Serializable
object ChatListRoute
@Serializable
object ProfileRoute
@Serializable
object SettingsRoute
@Serializable
data class ChatRoute(val chatId: String,val title: String,val avatarUrl: String)