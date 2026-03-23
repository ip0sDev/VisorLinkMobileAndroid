package by.iposdev.visorlink.ui

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object ChatList : Screen("chat_list")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Stickers : Screen("stickers")
    object CreateChat : Screen("create_chat")
    object Notifications : Screen("notifications")
    object CacheSettings : Screen("cache_settings")

    // ─── Обновлено для поддержки перехода по @тегам ──────────────────────────
    object Search : Screen("search?query={query}") {
        fun createRoute(query: String? = null): String {
            return if (query != null) "search?query=$query" else "search"
        }
    }

    // Обновим и FindChannel, если вы решите перенаправлять теги каналов туда
    object FindChannel : Screen("find_channel?query={query}") {
        fun createRoute(query: String? = null): String {
            return if (query != null) "find_channel?query=$query" else "find_channel"
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    object Chat : Screen("chat/{chatId}/{otherUid}") {
        fun createRoute(chatId: String, otherUid: String) = "chat/$chatId/$otherUid"
    }

    object OtherProfile : Screen("other_profile/{uid}") {
        fun createRoute(uid: String) = "other_profile/$uid"
    }

    object ChatSettings : Screen("chat_settings/{chatId}") {
        fun createRoute(chatId: String) = "chat_settings/$chatId"
    }

    // URL передаём как query-параметр — он надёжно переживает URLEncoding
    object ImageViewer : Screen("image_viewer?url={url}") {
        fun createRoute(url: String): String {
            val encoded = java.net.URLEncoder.encode(url, "UTF-8")
            return "image_viewer?url=$encoded"
        }
    }
}