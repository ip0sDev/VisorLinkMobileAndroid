package by.iposdev.visorlink.data.repository

import by.iposdev.visorlink.data.model.flags.AppFlags
import by.iposdev.visorlink.data.remote.chat.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class BackendV2SwitchTest {

    private val gson = Gson()

    @Test
    fun `AppFlags backend_v2_enabled defaults to false when not present`() {
        val flags = AppFlags()
        assertFalse(flags.isEnabled("backend_v2_enabled"))
        assertFalse(flags.isBackendV2Enabled)
    }

    @Test
    fun `AppFlags backend_v2_enabled reads from server claims`() {
        val flags = AppFlags(
            serverClaims = mapOf("backend_v2_enabled" to true)
        )
        assertTrue(flags.isEnabled("backend_v2_enabled"))
        assertTrue(flags.isBackendV2Enabled)
    }

    @Test
    fun `AppFlags local overrides take precedence over server claims`() {
        // Server says true, local override says false
        val flagsOverriddenFalse = AppFlags(
            serverClaims = mapOf("backend_v2_enabled" to true),
            localOverrides = mapOf("backend_v2_enabled" to false)
        )
        assertFalse(flagsOverriddenFalse.isEnabled("backend_v2_enabled"))
        assertFalse(flagsOverriddenFalse.isBackendV2Enabled)

        // Server says false, local override says true
        val flagsOverriddenTrue = AppFlags(
            serverClaims = mapOf("backend_v2_enabled" to false),
            localOverrides = mapOf("backend_v2_enabled" to true)
        )
        assertTrue(flagsOverriddenTrue.isEnabled("backend_v2_enabled"))
        assertTrue(flagsOverriddenTrue.isBackendV2Enabled)
    }

    @Test
    fun `AppFlags animation_test reads from claims and overrides`() {
        val flagsTrue = AppFlags(
            localOverrides = mapOf("animation_test" to true)
        )
        assertTrue(flagsTrue.isEnabled("animation_test"))

        val flagsFalse = AppFlags(
            localOverrides = mapOf("animation_test" to false)
        )
        assertFalse(flagsFalse.isEnabled("animation_test"))

        val flagsServer = AppFlags(
            serverClaims = mapOf("animation_test" to true)
        )
        assertTrue(flagsServer.isEnabled("animation_test"))
    }

    @Test
    fun `MessageDto parses contract json correctly`() {
        val json = """
            {
                "id": "msg_123",
                "chatId": "chat_abc",
                "senderId": "uid_1",
                "senderUsername": "alex",
                "type": "text",
                "text": "Hello world",
                "reactions": [
                    { "emoji": "👍", "uids": ["uid_2"], "count": 1 }
                ],
                "readBy": ["uid_1", "uid_2"],
                "spoiler": false,
                "commentsCount": 3,
                "createdAt": 1700000000000
            }
        """.trimIndent()

        val dto = gson.fromJson(json, MessageDto::class.java)
        assertEquals("msg_123", dto.id)
        assertEquals("chat_abc", dto.chatId)
        assertEquals("uid_1", dto.senderId)
        assertEquals("alex", dto.senderUsername)
        assertEquals("Hello world", dto.text)
        assertEquals(1, dto.reactions?.size)
        assertEquals("👍", dto.reactions?.first()?.emoji)
        assertEquals(2, dto.readBy?.size)
        assertEquals(3, dto.commentsCount)
        assertEquals(1700000000000L, dto.createdAt)
    }

    @Test
    fun `ChatDto parses contract json and handles lastMessageText correctly`() {
        // Case 1: lastMessage as object
        val jsonWithObj = """
            {
                "id": "chat_456",
                "type": "group",
                "name": "General Chat",
                "tag": "general",
                "lastMessage": {
                    "id": "msg_999",
                    "text": "Latest message here",
                    "timestamp": 1700000005000
                },
                "lastMessageAt": 1700000005000,
                "memberCount": 15,
                "createdAt": 1690000000000
            }
        """.trimIndent()

        val dto1 = gson.fromJson(jsonWithObj, ChatDto::class.java)
        assertEquals("chat_456", dto1.id)
        assertEquals("group", dto1.type)
        assertEquals("General Chat", dto1.name)
        assertEquals("general", dto1.tag)
        assertEquals(15, dto1.memberCount)
        assertEquals("Latest message here", dto1.lastMessageText)

        // Case 2: lastMessage as string
        val jsonWithStr = """
            {
                "id": "chat_789",
                "type": "direct",
                "lastMessage": "Direct message string",
                "createdAt": 1690000000000
            }
        """.trimIndent()

        val dto2 = gson.fromJson(jsonWithStr, ChatDto::class.java)
        assertEquals("Direct message string", dto2.lastMessageText)
    }

    @Test
    fun `UserDto parses contract json with uid fallback`() {
        val json = """
            {
                "id": "user_doc_id",
                "uid": "user_firebase_uid",
                "username": "coder_pro",
                "displayName": "Coder Pro",
                "bits": 250,
                "streak": 5,
                "showStreak": true,
                "isAdmin": false
            }
        """.trimIndent()

        val dto = gson.fromJson(json, UserDto::class.java)
        assertEquals("user_firebase_uid", dto.actualId)
        assertEquals("coder_pro", dto.username)
        assertEquals(250, dto.bits)
        assertEquals(5, dto.streak)
        assertTrue(dto.showStreak)
    }

    @Test
    fun `WsEvent envelope parses message_new event`() {
        val wsJson = """
            {
                "type": "message_new",
                "channel": "chat:chat_123",
                "data": {
                    "id": "m_1",
                    "chatId": "chat_123",
                    "senderId": "u_1",
                    "senderUsername": "bob",
                    "type": "text",
                    "text": "Incoming WS message",
                    "createdAt": 1700000010000
                }
            }
        """.trimIndent()

        val event = gson.fromJson(wsJson, WsEvent::class.java)
        assertEquals("message_new", event.type)
        assertEquals("chat:chat_123", event.channel)
        assertNotNull(event.data)

        val message = gson.fromJson(event.data.toString(), MessageDto::class.java)
        assertEquals("m_1", message.id)
        assertEquals("Incoming WS message", message.text)
    }

    @Test
    fun `UserDto parses various customization formats safely without exception`() {
        // Case 1: object customization
        val objJson = """
            {
                "id": "u_1",
                "username": "alice",
                "customization": { "theme": "cyberpunk", "fontSize": 14, "diaryEnabled": true }
            }
        """.trimIndent()
        val dto1 = gson.fromJson(objJson, UserDto::class.java)
        assertEquals("cyberpunk", dto1.customization["theme"])
        assertEquals(true, dto1.customization["diaryEnabled"])

        // Case 2: raw string from Firestore migration
        val strJson = """
            {
                "id": "u_2",
                "username": "bob",
                "customization": "{theme=dark, color=#123456}"
            }
        """.trimIndent()
        val dto2 = gson.fromJson(strJson, UserDto::class.java)
        assertNotNull(dto2.customization)

        // Case 3: JSON-encoded string
        val jsonStrJson = """
            {
                "id": "u_3",
                "username": "charlie",
                "customization": "{\"theme\":\"sunset\"}"
            }
        """.trimIndent()
        val dto3 = gson.fromJson(jsonStrJson, UserDto::class.java)
        assertEquals("sunset", dto3.customization["theme"])

        // Case 4: null customization
        val nullJson = """
            {
                "id": "u_4",
                "username": "dave",
                "customization": null
            }
        """.trimIndent()
        val dto4 = gson.fromJson(nullJson, UserDto::class.java)
        assertTrue(dto4.customization.isEmpty())
    }

    @Test
    fun `FeedItemDto parses backend DiscoverFeedDto correctly`() {
        val json = """
            {
                "id": "feed_001",
                "messageId": "msg_001",
                "chatId": "channel_001",
                "channelName": "VisorLink Official News",
                "channelAvatar": "https://visorlink.org/avatar.png",
                "channelTag": "news",
                "senderId": "admin_uid",
                "senderUsername": "admin",
                "type": "post",
                "text": "Major update released!",
                "likeCount": 42,
                "likedByMe": true,
                "createdAt": 1700000000000
            }
        """.trimIndent()

        val dto = gson.fromJson(json, FeedItemDto::class.java)
        assertEquals("feed_001", dto.id)
        assertEquals("VisorLink Official News", dto.channelName)
        assertEquals("https://visorlink.org/avatar.png", dto.channelAvatar)
        assertEquals("news", dto.channelTag)
        assertEquals(42, dto.likeCount)
        assertTrue(dto.likedByMe)
    }

    @Test
    fun `UserDto parses proUntil and trialUsed correctly`() {
        val futureTime = System.currentTimeMillis() + 86400000L * 30
        val json = """
            {
                "id": "pro_user_1",
                "username": "vip",
                "proUntil": $futureTime,
                "trialUsed": true,
                "bits": 250
            }
        """.trimIndent()
        val dto = gson.fromJson(json, UserDto::class.java)
        assertEquals("pro_user_1", dto.id)
        assertEquals(futureTime, dto.proUntil)
        assertTrue(dto.trialUsed)
        assertEquals(250, dto.bits)

        val profile = by.iposdev.visorlink.data.model.UserProfile(
            uid = dto.id,
            username = dto.username,
            proUntil = dto.proUntil?.let { com.google.firebase.Timestamp(java.util.Date(it)) },
            trialUsed = dto.trialUsed,
            bits = dto.bits
        )
        assertTrue(profile.isProActive())
    }

    @Test
    fun `BuyProResponse parses contract json correctly`() {
        val json = """
            {
                "success": true,
                "proUntil": 1750000000000,
                "proUntilIso": "2025-06-15T12:00:00Z",
                "bits": 150
            }
        """.trimIndent()
        val res = gson.fromJson(json, BuyProResponse::class.java)
        assertTrue(res.success)
        assertEquals(1750000000000L, res.proUntil)
        assertEquals(150, res.bits)
    }
}
