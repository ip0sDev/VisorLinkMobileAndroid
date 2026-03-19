package by.iposdev.visorlink.ui

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object ChatList : Screen("chat_list")
    object Search : Screen("search")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Stickers : Screen("stickers")
    object CreateChat : Screen("create_chat")
    object FindChannel : Screen("find_channel")
    object Notifications : Screen("notifications")

    object Chat : Screen("chat/{chatId}/{otherUid}") {
        fun createRoute(chatId: String, otherUid: String) = "chat/$chatId/$otherUid"
    }
    object OtherProfile : Screen("other_profile/{uid}") {
        fun createRoute(uid: String) = "other_profile/$uid"
    }
    object ChatSettings : Screen("chat_settings/{chatId}") {
        fun createRoute(chatId: String) = "chat_settings/$chatId"
    }


}