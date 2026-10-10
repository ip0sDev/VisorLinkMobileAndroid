package org.visorlink.app.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

class FeedModelsTest {

    private val channel = Chat(id = "c1", type = "channel", name = "Новости")

    private fun like(vararg uids: String) = mapOf<String, Any>("emoji" to FEED_LIKE_EMOJI, "uids" to uids.toList(), "count" to uids.size.toLong())

    private fun msg(id: String, seconds: Long? = 100, vararg reactions: Map<String, Any>) =
        Message(id = id, senderId = "author", text = "пост $id", createdAt = seconds?.let { Timestamp(it, 0) }, reactions = reactions.toList())

    // ── Лайки ─────────────────────────────────────────────────────────────────

    @Test
    fun `like count is the number of distinct people, not the stored count`() {
        // Старые клиенты могли записать uid дважды и count, ушедший в минус
        val broken = mapOf<String, Any>("emoji" to FEED_LIKE_EMOJI, "uids" to listOf("a", "a", "b"), "count" to -2L)
        val post = FeedPost(channel, msg("m1", 100, broken, mapOf("emoji" to "🔥", "uids" to listOf("c"), "count" to 1L)))

        assertEquals(2, post.likeCount)
        assertTrue(post.isLikedBy("a"))
        assertFalse(post.isLikedBy("c"))
    }

    @Test
    fun `withLike is idempotent`() {
        val post = FeedPost(channel, msg("m1", 100, like("a")))

        assertSame(post, post.withLike("a", true))
        assertEquals(2, post.withLike("me", true).likeCount)
        assertEquals(2, post.withLike("me", true).withLike("me", true).likeCount)
        assertEquals(0, post.withLike("a", false).likeCount)
        assertEquals(0, post.withLike("a", false).withLike("a", false).likeCount)
    }

    @Test
    fun `channel settings switch likes, comments and sharing`() {
        val locked = channel.copy(settings = ChatSettings(allowReactions = false, allowComments = false, noForwards = true))
        val post = FeedPost(locked, msg("m1"))
        assertFalse(post.likesEnabled)
        assertFalse(post.commentsEnabled)
        assertFalse(post.shareable)

        val open = FeedPost(channel, msg("m1"))
        assertTrue(open.likesEnabled && open.commentsEnabled && open.shareable)
        // Комментарии выключены у самого поста
        assertFalse(FeedPost(channel, msg("m1").copy(commentsEnabled = false)).commentsEnabled)
    }

    @Test
    fun `body falls back to caption and ignores blanks`() {
        assertEquals("подпись", FeedPost(channel, Message(id = "m", text = "  ", caption = "подпись")).body)
        assertNull(FeedPost(channel, Message(id = "m", text = "", caption = " ")).body)
    }

    // ── Видимость и порядок ──────────────────────────────────────────────────

    @Test
    fun `deleted, hidden and unpublished posts are not shown`() {
        assertTrue(FeedPosts.isVisible(msg("m"), "me"))
        assertFalse(FeedPosts.isVisible(msg("m").copy(deleted = true), "me"))
        assertFalse(FeedPosts.isVisible(msg("m").copy(isHidden = true), "me"))
        assertFalse(FeedPosts.isVisible(msg("m").copy(isPublished = false), "me"))
        // Свой неопубликованный пост автор видит
        assertTrue(FeedPosts.isVisible(msg("m").copy(isPublished = false), "author"))
    }

    @Test
    fun `assemble merges channels newest first with pending posts on top`() {
        val other = Chat(id = "c2", type = "channel", name = "Спорт")
        val posts = FeedPosts.assemble(
            channels = listOf(channel, other),
            messagesByChannel = mapOf(
                "c1" to listOf(msg("a", 300), msg("b", 100)),
                "c2" to listOf(msg("a", 200), msg("pending", null)),
                "gone" to listOf(msg("x", 999)),
            ),
        )
        assertEquals(listOf("c2/pending", "c1/a", "c2/a", "c1/b"), posts.map { it.key })
        assertEquals("Спорт", posts.first { it.key == "c2/a" }.channel.name)
    }

    @Test
    fun `only channels are followed, freshest first, capped`() {
        val chats = (1..(FeedPosts.MAX_CHANNELS + 5)).map { Chat(id = "c$it", type = "channel", lastMessageAt = Timestamp(it.toLong(), 0)) } +
            Chat(id = "group", type = "group", lastMessageAt = Timestamp(10_000, 0))
        val followed = FeedPosts.channelsToFollow(chats)

        assertEquals(FeedPosts.MAX_CHANNELS, followed.size)
        assertEquals("c${FeedPosts.MAX_CHANNELS + 5}", followed.first().id)
        assertTrue(followed.none { it.id == "group" })
    }

    // ── Медиа ────────────────────────────────────────────────────────────────

    @Test
    fun `media classification`() {
        val drive = "https://lh3.googleusercontent.com/d/abc"
        assertNull(FeedMedia.of(Message(type = MessageType.TEXT, text = "x")))
        assertEquals(FeedMedia.Images(listOf(drive)), FeedMedia.of(Message(type = MessageType.IMAGE, url = drive, driveFileId = "abc")))
        assertEquals(FeedMedia.Archived, FeedMedia.of(Message(type = MessageType.IMAGE, url = "https://api.visorlink.org/f/1", cdnMediaId = "1")))
        assertEquals(FeedMedia.Attachment(FeedMedia.SPOILER), FeedMedia.of(Message(type = MessageType.IMAGE, url = drive, driveFileId = "abc", spoiler = true)))
        assertEquals(FeedMedia.Attachment(MessageType.VIDEO), FeedMedia.of(Message(type = MessageType.VIDEO, url = drive, driveFileId = "v")))

        val album = Message(
            type = MessageType.ALBUM,
            images = listOf(AlbumImage(url = drive), AlbumImage(url = "https://api.visorlink.org/f/2"), AlbumImage(previewUrl = "$drive/2")),
        )
        assertEquals(FeedMedia.Images(listOf(drive, "$drive/2")), FeedMedia.of(album))
        assertEquals(FeedMedia.Archived, FeedMedia.of(Message(type = MessageType.ALBUM, images = listOf(AlbumImage(cdnMediaId = "x")))))
    }

    @Test
    fun `curated channel parsing`() {
        val ch = CuratedChannel.fromMap(mapOf("id" to "c1", "name" to "Новости", "tag" to "@news", "memberCount" to 1200L, "avatarUrl" to ""))!!
        assertEquals("news", ch.tag)
        assertEquals(1200, ch.memberCount)
        assertNull(ch.avatarUrl)
        assertNull(CuratedChannel.fromMap(mapOf("name" to "без id")))
    }
}
