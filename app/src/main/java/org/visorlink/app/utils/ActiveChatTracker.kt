package org.visorlink.app.utils

object ActiveChatTracker {
    @Volatile
    var activeChatId: String? = null

    @Volatile
    var isAppInForeground: Boolean = false

    fun isChatActive(chatId: String): Boolean =
        isAppInForeground && activeChatId == chatId
}