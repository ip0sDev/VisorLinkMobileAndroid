package org.visorlink.app.data.model

import java.util.Random

/**
 * Правила и ограничения для папок чатов VisorLink PRO (по спецификации веба и Flutter).
 */
object ChatFolderRules {
    const val MAX_FOLDERS = 10
    const val NAME_MAX = 24
    const val CHATS_MAX = 200
    val RULES = listOf("direct", "group", "channel", "unread")
    val RULE_TITLES = mapOf(
        "direct" to "Личные",
        "group" to "Группы",
        "channel" to "Каналы",
        "unread" to "Непрочитанные"
    )
}

/**
 * Модель папки чатов.
 * Хранится в `users/{uid}/chatFolders/config` в документе Firestore.
 */
data class ChatFolder(
    val id: String,
    val name: String,
    val emoji: String = "",
    val rules: Set<String> = emptySet(),
    val chatIds: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = chatIds.isEmpty() && rules.isEmpty()

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "emoji" to emoji,
        "rules" to rules.associateWith { true },
        "chatIds" to chatIds
    )

    companion object {
        fun newId(): String {
            val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
            val rnd = Random()
            val suffix = StringBuilder()
            for (i in 0 until 4) {
                suffix.append(chars[rnd.nextInt(chars.length)])
            }
            return "f_${System.currentTimeMillis().toString(36)}_$suffix"
        }

        fun normalize(m: Map<*, *>): ChatFolder {
            val id = m["id"] as? String ?: ""
            val rawName = (m["name"] as? String ?: "").replace(Regex("\\s+"), " ").trim()
            val name = if (rawName.length > ChatFolderRules.NAME_MAX) rawName.substring(0, ChatFolderRules.NAME_MAX) else rawName
            val rawEmoji = (m["emoji"] as? String ?: "").trim()
            val emoji = if (rawEmoji.length > 8) rawEmoji.substring(0, 8) else rawEmoji

            val rawRules = m["rules"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val activeRules = ChatFolderRules.RULES.filter { rawRules[it] == true }.toSet()

            val rawChatIds = m["chatIds"] as? List<*> ?: emptyList<Any>()
            val chatIds = rawChatIds
                .mapNotNull { it as? String }
                .filter { it.isNotEmpty() && it.length <= 128 }
                .distinct()
                .take(ChatFolderRules.CHATS_MAX)

            return ChatFolder(
                id = id,
                name = name,
                emoji = emoji,
                rules = activeRules,
                chatIds = chatIds
            )
        }
    }
}

private fun chatTypeString(chat: Chat): String {
    return when (chat.chatType()) {
        ChatType.GROUP -> "group"
        ChatType.CHANNEL -> "channel"
        ChatType.DIRECT -> "direct"
        else -> "other"
    }
}

/**
 * Проверяет, попадает ли чат в папку:
 * id в `chatIds` ИЛИ соответствует правилам папки.
 * Типовые правила (direct, group, channel) работают как «ИЛИ» между собой,
 * а unread комбинируется через «И» (например: группы И непрочитанные).
 */
fun chatInFolder(chat: Chat, folder: ChatFolder, unreadCount: Int): Boolean {
    if (folder.chatIds.contains(chat.id)) return true
    val rules = folder.rules
    val hasTypeRule = rules.contains("direct") || rules.contains("group") || rules.contains("channel")
    if (hasTypeRule && !rules.contains(chatTypeString(chat))) return false
    if (rules.contains("unread") && unreadCount <= 0) return false
    return hasTypeRule || rules.contains("unread")
}
