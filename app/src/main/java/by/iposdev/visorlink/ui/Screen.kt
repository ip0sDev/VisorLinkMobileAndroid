package by.iposdev.visorlink.ui

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object VerifyEmail : Screen("verify_email")
    object Onboarding : Screen("onboarding")
    object ChatList : Screen("chat_list")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Stickers : Screen("stickers")
    object CreateChat : Screen("create_chat")
    object Notifications : Screen("notifications")
    object CacheSettings : Screen("cache_settings")
    object Customization : Screen("customization")

    object Search : Screen("search?query={query}") {
        fun createRoute(query: String? = null) =
            if (query != null) "search?query=$query" else "search"
    }

    object FindChannel : Screen("find_channel?query={query}") {
        fun createRoute(query: String? = null) =
            if (query != null) "find_channel?query=$query" else "find_channel"
    }

    object Chat : Screen("chat/{chatId}/{otherUid}") {
        fun createRoute(chatId: String, otherUid: String) = "chat/$chatId/$otherUid"
    }

    object OtherProfile : Screen("other_profile/{uid}") {
        fun createRoute(uid: String) = "other_profile/$uid"
    }

    object ChatSettings : Screen("chat_settings/{chatId}") {
        fun createRoute(chatId: String) = "chat_settings/$chatId"
    }

    object ImageViewer : Screen("media_viewer?url={url}&type={type}") {
        fun createRoute(url: String, type: String = "image"): String {
            val encoded = java.net.URLEncoder.encode(url, "UTF-8")
            return "media_viewer?url=$encoded&type=$type"
        }
    }

    object Comments : Screen("comments/{chatId}/{messageId}") {
        fun createRoute(chatId: String, messageId: String) = "comments/$chatId/$messageId"
    }

    object SavedMessages : Screen("saved_messages")
    object SavedMessagesSettings : Screen("saved_messages_settings")
    object Feed : Screen("feed")
    object StorageManager : Screen("storage_manager")
    object Diary : Screen("diary")
    object DiaryEntry : Screen("diary_entry?id={id}") {
        fun createRoute(id: String? = null) = if (id != null) "diary_entry?id=$id" else "diary_entry"
    }
}