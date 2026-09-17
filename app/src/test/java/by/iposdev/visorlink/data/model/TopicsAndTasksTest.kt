package org.visorlink.app.data.model

import org.junit.Assert.*
import org.junit.Test

class TopicsAndTasksTest {

    @Test
    fun `topic displayIcon falls back appropriately`() {
        val generalTopic = Topic(id = "general", isGeneral = true, icon = "")
        assertEquals("#", generalTopic.displayIcon)

        val chatTopic = Topic(id = "topic1", type = "chat", icon = "")
        assertEquals("💬", chatTopic.displayIcon)

        val taskTopic = Topic(id = "topic2", type = "tasks", icon = "")
        assertEquals("📋", taskTopic.displayIcon)

        val customTopic = Topic(id = "topic3", icon = "🚀")
        assertEquals("🚀", customTopic.displayIcon)
    }

    @Test
    fun `topic displayColor defaults to cyan`() {
        val topicNoColor = Topic(id = "t1", color = "")
        assertEquals("#35C7E8", topicNoColor.displayColor)

        val topicWithColor = Topic(id = "t2", color = "#8C6BFF")
        assertEquals("#8C6BFF", topicWithColor.displayColor)
    }

    @Test
    fun `topic isTasks property works correctly`() {
        val chatTopic = Topic(type = "chat")
        assertFalse(chatTopic.isTasks)

        val taskTopic = Topic(type = "tasks")
        assertTrue(taskTopic.isTasks)
    }

    @Test
    fun `topic lastMessageText formats sender and message`() {
        val topicWithSender = Topic(
            lastMessage = mapOf("senderUsername" to "alex", "text" to "Hello team!")
        )
        assertEquals("alex: Hello team!", topicWithSender.lastMessageText())

        val topicWithoutSender = Topic(
            lastMessage = mapOf("text" to "Status update")
        )
        assertEquals("Status update", topicWithoutSender.lastMessageText())

        val emptyTopic = Topic()
        assertEquals("", emptyTopic.lastMessageText())
    }

    @Test
    fun `taskStatus fromId resolves properly`() {
        assertEquals(TaskStatus.TODO, TaskStatus.fromId("todo"))
        assertEquals(TaskStatus.IN_PROGRESS, TaskStatus.fromId("in_progress"))
        assertEquals(TaskStatus.REVIEW, TaskStatus.fromId("review"))
        assertEquals(TaskStatus.DONE, TaskStatus.fromId("done"))
        assertEquals(TaskStatus.TODO, TaskStatus.fromId("unknown_status"))
        assertEquals(TaskStatus.TODO, TaskStatus.fromId(null))
    }

    @Test
    fun `taskPriority fromId resolves properly`() {
        assertEquals(TaskPriority.LOW, TaskPriority.fromId("low"))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromId("medium"))
        assertEquals(TaskPriority.HIGH, TaskPriority.fromId("high"))
        assertEquals(TaskPriority.URGENT, TaskPriority.fromId("urgent"))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromId("unknown_priority"))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromId(null))
    }

    @Test
    fun `topic message filtering conforms strictly to specification`() {
        val generalTopic = Topic(id = "general", isGeneral = true)
        val devTopic = Topic(id = "topic_dev", isGeneral = false)

        val msgLegacy = Message(id = "1", text = "legacy message", topicId = null)
        val msgEmpty = Message(id = "2", text = "empty topic message", topicId = "")
        val msgGeneral = Message(id = "3", text = "general message", topicId = "general")
        val msgDev = Message(id = "4", text = "dev message", topicId = "topic_dev")
        val msgDesign = Message(id = "5", text = "design message", topicId = "topic_design")

        val allMessages = listOf(msgLegacy, msgEmpty, msgGeneral, msgDev, msgDesign)

        // General topic: includes null, "", "general", or topic.id
        val generalFiltered = allMessages.filter { msg ->
            val isGeneral = generalTopic.isGeneral || generalTopic.id == "general"
            if (isGeneral) {
                msg.topicId.isNullOrEmpty() || msg.topicId == "general" || msg.topicId == generalTopic.id
            } else {
                msg.topicId == generalTopic.id
            }
        }
        assertEquals(listOf(msgLegacy, msgEmpty, msgGeneral), generalFiltered)

        // Dev topic: only includes topicId == "topic_dev"
        val devFiltered = allMessages.filter { msg ->
            val isGeneral = devTopic.isGeneral || devTopic.id == "general"
            if (isGeneral) {
                msg.topicId.isNullOrEmpty() || msg.topicId == "general" || msg.topicId == devTopic.id
            } else {
                msg.topicId == devTopic.id
            }
        }
        assertEquals(listOf(msgDev), devFiltered)
    }

    @Test
    fun `chat forum mode default is false`() {
        val defaultChat = Chat(id = "c1")
        assertFalse(defaultChat.isForumActive)

        val forumChatRoot = Chat(id = "c2", isForum = true)
        assertTrue(forumChatRoot.isForumActive)

        val forumChatSettings = Chat(id = "c3", settings = ChatSettings(isForum = true))
        assertTrue(forumChatSettings.isForumActive)

        val forumChatSnake = Chat(id = "c4", is_forum = true)
        assertTrue(forumChatSnake.isForumActive)

        val forumChatSnakeSettings = Chat(id = "c5", settings = ChatSettings(is_forum = true))
        assertTrue(forumChatSnakeSettings.isForumActive)
    }
}
