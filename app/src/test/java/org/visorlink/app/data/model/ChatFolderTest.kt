package org.visorlink.app.data.model

import org.junit.Assert.*
import org.junit.Test

class ChatFolderTest {

    @Test
    fun testNormalizeChatFolder() {
        val map = mapOf(
            "id" to "1",
            "name" to " Work ",
            "emoji" to "💼",
            "rules" to mapOf("direct" to true),
            "chatIds" to listOf("c1", "c2")
        )
        val normalized = ChatFolder.normalize(map)
        assertEquals("1", normalized.id)
        assertEquals("Work", normalized.name)
        assertEquals("💼", normalized.emoji)
        assertTrue(normalized.rules.contains("direct"))
        assertEquals(listOf("c1", "c2"), normalized.chatIds)
    }

    @Test
    fun testChatInFolderByRules() {
        val folder = ChatFolder(
            id = "f1",
            name = "Direct Only",
            rules = setOf("direct")
        )

        val directChat = Chat(id = "c1", type = "direct")
        val groupChat = Chat(id = "c2", type = "group")
        val channelChat = Chat(id = "c3", type = "channel")

        assertTrue(chatInFolder(directChat, folder, 0))
        assertFalse(chatInFolder(groupChat, folder, 0))
        assertFalse(chatInFolder(channelChat, folder, 0))
    }

    @Test
    fun testChatInFolderUnreadRule() {
        val folder = ChatFolder(
            id = "f2",
            name = "Unread",
            rules = setOf("unread")
        )

        val chat = Chat(id = "c1", type = "direct")
        assertFalse(chatInFolder(chat, folder, unreadCount = 0))
        assertTrue(chatInFolder(chat, folder, unreadCount = 3))
    }

    @Test
    fun testChatInFolderExplicitInclusion() {
        val folder = ChatFolder(
            id = "f3",
            name = "Work",
            chatIds = listOf("c1", "c2")
        )

        val chat1 = Chat(id = "c1", type = "direct")
        val chat3 = Chat(id = "c3", type = "direct")

        assertTrue(chatInFolder(chat1, folder, 0))
        assertFalse(chatInFolder(chat3, folder, 0))
    }
}
