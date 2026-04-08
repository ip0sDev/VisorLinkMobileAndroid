package by.iposdev.visorlink.utils

object ActiveChatTracker {
    @Volatile
    var activeChatId: String? = null
}