package org.visorlink.app.utils

import android.content.Context
import android.content.SharedPreferences
import org.visorlink.app.data.model.StickerPack
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class UsageRankManagerTest {

    private val context: Context = mock()
    private val appContext: Context = mock()
    private val sharedPreferences: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()
    private lateinit var usageRankManager: UsageRankManager

    @Before
    fun setUp() {
        whenever(context.applicationContext).thenReturn(appContext)
        whenever(appContext.getSharedPreferences(any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.all).thenReturn(emptyMap())
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putInt(any(), any())).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        usageRankManager = UsageRankManager(context)
    }

    @Test
    fun `getRankedReactions puts most frequently used reaction first`() {
        usageRankManager.recordReactionUsage("🔥")
        usageRankManager.recordReactionUsage("🔥")
        usageRankManager.recordReactionUsage("❤️")

        val ranked = usageRankManager.getRankedReactions()
        assertEquals("🔥", ranked[0])
        assertEquals("❤️", ranked[1])
        // Base list reactions that were not used are still present
        assertTrue(ranked.contains("👍"))
        assertTrue(ranked.contains("😂"))
    }

    @Test
    fun `rankPacks sorts packs by usage count descending`() {
        val pack1 = StickerPack(id = "pack_1", name = "Pack 1", emoji = "🐱", authorId = "a1", authorName = "Author", stickerCount = 5)
        val pack2 = StickerPack(id = "pack_2", name = "Pack 2", emoji = "🐶", authorId = "a2", authorName = "Author", stickerCount = 5)
        val pack3 = StickerPack(id = "pack_3", name = "Pack 3", emoji = "🦊", authorId = "a3", authorName = "Author", stickerCount = 5)

        usageRankManager.recordPackUsage("pack_2")
        usageRankManager.recordPackUsage("pack_2")
        usageRankManager.recordPackUsage("pack_3")

        val sorted = usageRankManager.rankPacks(listOf(pack1, pack2, pack3))
        assertEquals("pack_2", sorted[0].id)
        assertEquals("pack_3", sorted[1].id)
        assertEquals("pack_1", sorted[2].id)
    }

    @Test
    fun `rankPacks preserves list when single or empty`() {
        val pack1 = StickerPack(id = "pack_1", name = "Pack 1", emoji = "🐱", authorId = "a1", authorName = "Author", stickerCount = 5)
        val singleList = listOf(pack1)
        val emptyList = emptyList<StickerPack>()

        assertEquals(singleList, usageRankManager.rankPacks(singleList))
        assertEquals(emptyList, usageRankManager.rankPacks(emptyList))
    }
}
