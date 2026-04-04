package by.iposdev.visorlink.data.model

import org.junit.Assert.*
import org.junit.Test

class ChatModelTest {

    private fun directChat(uid1: String, uid2: String) = Chat(
        id = "chat1",
        type = "direct",
        participants = listOf(uid1, uid2),
        participantData = mapOf(
            uid1 to mapOf("displayName" to "Alice", "username" to "alice"),
            uid2 to mapOf("displayName" to "Bob",   "username" to "bob")
        )
    )

    @Test
    fun `chatType returns DIRECT for direct type`() {
        assertEquals(ChatType.DIRECT, directChat("u1", "u2").chatType())
    }

    @Test
    fun `chatType returns GROUP for group type`() {
        assertEquals(ChatType.GROUP, Chat(type = "group").chatType())
    }

    @Test
    fun `chatType returns CHANNEL for channel type`() {
        assertEquals(ChatType.CHANNEL, Chat(type = "channel").chatType())
    }

    @Test
    fun `chatType returns DIRECT for unknown type`() {
        assertEquals(ChatType.DIRECT, Chat(type = "unknown").chatType())
    }

    @Test
    fun `otherParticipantId returns the other user`() {
        val chat = directChat("me", "other")
        assertEquals("other", chat.otherParticipantId("me"))
    }

    @Test
    fun `otherParticipantId returns empty string when only one participant`() {
        val chat = Chat(participants = listOf("me"))
        assertEquals("", chat.otherParticipantId("me"))
    }

    @Test
    fun `displayName returns other user displayName for DIRECT chat`() {
        val chat = directChat("me", "other")
        assertEquals("Bob", chat.displayName("me"))
    }

    @Test
    fun `displayName returns chat name for GROUP`() {
        val chat = Chat(type = "group", name = "Dev Team")
        assertEquals("Dev Team", chat.displayName("me"))
    }

    @Test
    fun `displayName returns chat name for CHANNEL`() {
        val chat = Chat(type = "channel", name = "Announcements")
        assertEquals("Announcements", chat.displayName("me"))
    }

    @Test
    fun `otherDisplayName returns empty string when participant not found`() {
        val chat = Chat(type = "direct", participants = listOf("me"))
        assertEquals("", chat.otherDisplayName("me"))
    }
}

class MemberTest {

    @Test
    fun `isAdmin returns true for admin role`() {
        assertTrue(Member(role = "admin").isAdmin())
    }

    @Test
    fun `isAdmin returns true for owner role`() {
        assertTrue(Member(role = "owner").isAdmin())
    }

    @Test
    fun `isAdmin returns false for member role`() {
        assertFalse(Member(role = "member").isAdmin())
    }

    @Test
    fun `isOwner returns true only for owner`() {
        assertTrue(Member(role = "owner").isOwner())
        assertFalse(Member(role = "admin").isOwner())
    }

    @Test
    fun `canSend returns false when banned`() {
        assertFalse(Member(banned = true).canSend())
    }

    @Test
    fun `canSend returns false when muted without expiry`() {
        assertFalse(Member(muted = true, mutedUntil = null).canSend())
    }

    @Test
    fun `canSend returns true when not banned or muted`() {
        assertTrue(Member(banned = false, muted = false).canSend())
    }

    @Test
    fun `canSendMedia returns false when banned`() {
        assertFalse(Member(banned = true).canSendMedia())
    }

    @Test
    fun `canSendMedia returns false when mediaRestricted`() {
        assertFalse(Member(muted = false, banned = false, mediaRestricted = true).canSendMedia())
    }

    @Test
    fun `canSendMedia returns true when active and not restricted`() {
        assertTrue(Member(banned = false, muted = false, mediaRestricted = false).canSendMedia())
    }
}

class PermissionHelpersTest {

    @Test
    fun `canSendMessage always true for DIRECT`() {
        assertTrue(canSendMessage(null, ChatType.DIRECT))
        assertTrue(canSendMessage(Member(banned = true), ChatType.DIRECT))
    }

    @Test
    fun `canSendMessage in GROUP depends on member canSend`() {
        assertTrue(canSendMessage(Member(banned = false, muted = false), ChatType.GROUP))
        assertFalse(canSendMessage(Member(banned = true), ChatType.GROUP))
        assertFalse(canSendMessage(null, ChatType.GROUP))
    }

    @Test
    fun `canSendMessage in CHANNEL requires admin`() {
        assertTrue(canSendMessage(Member(role = "admin"), ChatType.CHANNEL))
        assertFalse(canSendMessage(Member(role = "member"), ChatType.CHANNEL))
        assertFalse(canSendMessage(null, ChatType.CHANNEL))
    }

    @Test
    fun `canSendMedia always true for DIRECT`() {
        assertTrue(canSendMedia(null, ChatType.DIRECT))
    }

    @Test
    fun `canSendMedia in CHANNEL requires admin`() {
        assertTrue(canSendMedia(Member(role = "admin"), ChatType.CHANNEL))
        assertFalse(canSendMedia(Member(role = "member"), ChatType.CHANNEL))
    }

    @Test
    fun `canReact always true for non-channel`() {
        val chat = Chat(settings = ChatSettings(allowReactions = false))
        assertTrue(canReact(chat, ChatType.DIRECT))
        assertTrue(canReact(chat, ChatType.GROUP))
    }

    @Test
    fun `canReact in CHANNEL follows settings`() {
        assertTrue(canReact(Chat(settings = ChatSettings(allowReactions = true)), ChatType.CHANNEL))
        assertFalse(canReact(Chat(settings = ChatSettings(allowReactions = false)), ChatType.CHANNEL))
    }

    @Test
    fun `commentsAllowed false when channel disables comments`() {
        val channel = Chat(settings = ChatSettings(allowComments = false))
        val post = Message(commentsEnabled = true)
        assertFalse(commentsAllowed(channel, post))
    }

    @Test
    fun `commentsAllowed false when post disables comments`() {
        val channel = Chat(settings = ChatSettings(allowComments = true))
        val post = Message(commentsEnabled = false)
        assertFalse(commentsAllowed(channel, post))
    }

    @Test
    fun `commentsAllowed true when both enabled`() {
        val channel = Chat(settings = ChatSettings(allowComments = true))
        val post = Message(commentsEnabled = true)
        assertTrue(commentsAllowed(channel, post))
    }

    @Test
    fun `commentsAllowed true when post commentsEnabled is null (default)`() {
        val channel = Chat(settings = ChatSettings(allowComments = true))
        val post = Message(commentsEnabled = null)
        assertTrue(commentsAllowed(channel, post))
    }
}

class MessageTest {

    @Test
    fun `replyData returns null when replyTo is null`() {
        assertNull(Message().replyData)
    }

    @Test
    fun `replyData parses map correctly`() {
        val msg = Message(
            replyTo = mapOf(
                "id" to "r1",
                "type" to "text",
                "text" to "hello",
                "url" to null,
                "senderUsername" to "alice"
            )
        )
        val reply = msg.replyData!!
        assertEquals("r1", reply.id)
        assertEquals("text", reply.type)
        assertEquals("hello", reply.text)
        assertNull(reply.url)
        assertEquals("alice", reply.senderUsername)
    }

    @Test
    fun `parsedReactions filters out malformed entries`() {
        val msg = Message(
            reactions = listOf(
                mapOf("emoji" to "👍", "uids" to listOf("u1"), "count" to 1L),
                mapOf("uids" to listOf("u2"), "count" to 1L) // no emoji — should be skipped
            )
        )
        assertEquals(1, msg.parsedReactions.size)
        assertEquals("👍", msg.parsedReactions[0].emoji)
    }

    @Test
    fun `parsedReactions returns empty list when reactions is empty`() {
        assertTrue(Message().parsedReactions.isEmpty())
    }
}

class ReplyDataTest {

    @Test
    fun `toMap contains all fields`() {
        val reply = ReplyData("id1", "text", "hi", null, "bob")
        val map = reply.toMap()
        assertEquals("id1", map["id"])
        assertEquals("text", map["type"])
        assertEquals("hi", map["text"])
        assertNull(map["url"])
        assertEquals("bob", map["senderUsername"])
    }
}

class AlbumImageTest {

    @Test
    fun `toMap returns correct structure`() {
        val img = AlbumImage(url = "https://example.com/img.jpg", fileName = "img.jpg", spoiler = true)
        val map = img.toMap()
        assertEquals("https://example.com/img.jpg", map["url"])
        assertEquals("img.jpg", map["fileName"])
        assertEquals(true, map["spoiler"])
    }
}