package by.iposdev.visorlink.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class ChatSyncSpecTest {

    // ── calculateNextSeq tests ──────────────────────────────────────────

    @Test
    fun `calculateNextSeq returns 1 when chat has no lastSeq and messages are empty`() {
        val nextSeq = calculateNextSeq(null, emptyList())
        assertEquals(1L, nextSeq)
    }

    @Test
    fun `calculateNextSeq returns chat lastSeq plus 1 when messages are empty`() {
        val chat = Chat(id = "c1", lastSeq = 5L)
        val nextSeq = calculateNextSeq(chat, emptyList())
        assertEquals(6L, nextSeq)
    }

    @Test
    fun `calculateNextSeq considers max seq from local messages`() {
        val chat = Chat(id = "c1", lastSeq = 3L)
        val messages = listOf(
            Message(id = "m1", seq = 2L),
            Message(id = "m2", seq = 7L),
            Message(id = "m3", seq = 4L)
        )
        val nextSeq = calculateNextSeq(chat, messages)
        assertEquals(8L, nextSeq)
    }

    @Test
    fun `calculateNextSeq falls back to chat lastSeq when messages have legacy null seq`() {
        val chat = Chat(id = "c1", lastSeq = 10L)
        val messages = listOf(
            Message(id = "m1", seq = null),
            Message(id = "m2", seq = null)
        )
        val nextSeq = calculateNextSeq(chat, messages)
        assertEquals(11L, nextSeq)
    }

    // ── sortMessages tests ──────────────────────────────────────────────

    @Test
    fun `sortMessages sorts by seq ascending when both have seq`() {
        val m1 = Message(id = "m1", seq = 10L, createdAt = Timestamp(100, 0))
        val m2 = Message(id = "m2", seq = 5L, createdAt = Timestamp(200, 0))
        val m3 = Message(id = "m3", seq = 20L, createdAt = Timestamp(50, 0))

        val sorted = sortMessages(listOf(m1, m2, m3))
        assertEquals(listOf("m2", "m1", "m3"), sorted.map { it.id })
    }

    @Test
    fun `sortMessages puts seq message after legacy message when dt is less than 2s`() {
        // Legacy message sent right before migration at t=100s
        val legacy = Message(id = "legacy", seq = null, createdAt = Timestamp(100, 0))
        // Seq message sent right after at t=101s (dt = 1s < 2s)
        val seqMsg = Message(id = "seq1", seq = 1L, createdAt = Timestamp(101, 0))

        val sorted = sortMessages(listOf(seqMsg, legacy))
        assertEquals(listOf("legacy", "seq1"), sorted.map { it.id })
    }

    @Test
    fun `sortMessages sorts by createdAt when one has seq and dt is greater than or equal to 2s`() {
        // Legacy message sent long after (e.g. from an un-updated client at t=200s)
        val legacyLate = Message(id = "legacyLate", seq = null, createdAt = Timestamp(200, 0))
        // Seq message sent at t=100s (dt = 100s >= 2s)
        val seqEarly = Message(id = "seqEarly", seq = 10L, createdAt = Timestamp(100, 0))

        val sorted = sortMessages(listOf(legacyLate, seqEarly))
        assertEquals(listOf("seqEarly", "legacyLate"), sorted.map { it.id })
    }

    @Test
    fun `sortMessages sorts legacy messages by createdAt ascending`() {
        val m1 = Message(id = "m1", seq = null, createdAt = Timestamp(200, 0))
        val m2 = Message(id = "m2", seq = null, createdAt = Timestamp(100, 0))
        val m3 = Message(id = "m3", seq = null, createdAt = Timestamp(150, 0))

        val sorted = sortMessages(listOf(m1, m2, m3))
        assertEquals(listOf("m2", "m3", "m1"), sorted.map { it.id })
    }

    @Test
    fun `sortMessages tie breaks by id when seq and createdAt are identical`() {
        val m1 = Message(id = "b", seq = 1L, createdAt = Timestamp(100, 0))
        val m2 = Message(id = "a", seq = 1L, createdAt = Timestamp(100, 0))

        val sorted = sortMessages(listOf(m1, m2))
        assertEquals(listOf("a", "b"), sorted.map { it.id })
    }

    // ── isLastMessageRead tests ─────────────────────────────────────────

    @Test
    fun `isLastMessageRead returns false when last message is from someone else and unread`() {
        val chat = Chat(
            id = "c1",
            type = "direct",
            lastMessageSenderId = "otherUser",
            unreadCount = mapOf("me" to 1)
        )
        assertFalse(isLastMessageRead(chat, "me"))
    }

    @Test
    fun `isLastMessageRead returns true when last message is from someone else and already read`() {
        val chat = Chat(
            id = "c1",
            type = "direct",
            lastMessageSenderId = "otherUser",
            unreadCount = mapOf("me" to 0)
        )
        assertTrue(isLastMessageRead(chat, "me"))
    }

    @Test
    fun `isLastMessageRead returns true for direct chat when other user is in readBy`() {
        val chat = Chat(
            id = "c1",
            type = "direct",
            participants = listOf("me", "bob"),
            lastMessageSenderId = "me",
            lastMessage = mapOf(
                "text" to "Hello Bob",
                "readBy" to listOf("me", "bob")
            )
        )
        assertTrue(isLastMessageRead(chat, "me"))
    }

    @Test
    fun `isLastMessageRead returns true for direct chat when other user unreadCount is 0`() {
        val chat = Chat(
            id = "c1",
            type = "direct",
            participants = listOf("me", "bob"),
            lastMessageSenderId = "me",
            unreadCount = mapOf("bob" to 0)
        )
        assertTrue(isLastMessageRead(chat, "me"))
    }

    @Test
    fun `isLastMessageRead returns false for direct chat when other user unreadCount is greater than 0 and not in readBy`() {
        val chat = Chat(
            id = "c1",
            type = "direct",
            participants = listOf("me", "bob"),
            lastMessageSenderId = "me",
            unreadCount = mapOf("bob" to 2),
            lastMessage = mapOf(
                "text" to "Hello Bob",
                "readBy" to listOf("me")
            )
        )
        assertFalse(isLastMessageRead(chat, "me"))
    }

    @Test
    fun `isLastMessageRead returns true for group chat when someone else is in readBy`() {
        val chat = Chat(
            id = "g1",
            type = "group",
            participants = listOf("me", "bob", "charlie"),
            lastMessageSenderId = "me",
            lastMessage = mapOf(
                "text" to "Team meeting",
                "readBy" to listOf("me", "charlie")
            )
        )
        assertTrue(isLastMessageRead(chat, "me"))
    }

    @Test
    fun `isLastMessageRead returns false for group chat when only sender is in readBy`() {
        val chat = Chat(
            id = "g1",
            type = "group",
            participants = listOf("me", "bob", "charlie"),
            lastMessageSenderId = "me",
            lastMessage = mapOf(
                "text" to "Team meeting",
                "readBy" to listOf("me")
            )
        )
        assertFalse(isLastMessageRead(chat, "me"))
    }

    // ── lastMessageInfo tests ───────────────────────────────────────────

    @Test
    fun `lastMessageInfo parses structured map`() {
        val chat = Chat(
            id = "c1",
            lastMessage = mapOf(
                "text" to "Hello world",
                "senderId" to "u1",
                "senderUsername" to "alice",
                "readBy" to listOf("u1", "u2")
            )
        )
        val info = chat.lastMessageInfo()
        assertNotNull(info)
        assertEquals("Hello world", info?.text)
        assertEquals("u1", info?.senderId)
        assertEquals("alice", info?.senderUsername)
        assertEquals(listOf("u1", "u2"), info?.readBy)
    }

    @Test
    fun `lastMessageInfo parses legacy string`() {
        val chat = Chat(
            id = "c1",
            lastMessage = "Legacy preview text",
            lastMessageSenderId = "u1"
        )
        val info = chat.lastMessageInfo()
        assertNotNull(info)
        assertEquals("Legacy preview text", info?.text)
        assertEquals("u1", info?.senderId)
        assertTrue(info?.readBy?.isEmpty() == true)
    }
}
