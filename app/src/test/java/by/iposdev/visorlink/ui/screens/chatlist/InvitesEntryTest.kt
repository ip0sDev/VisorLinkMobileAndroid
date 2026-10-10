package org.visorlink.app.ui.screens.chatlist

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.visorlink.app.data.model.AppNotification
import org.visorlink.app.data.model.Chat

class InvitesEntryTest {

    private fun chat(id: String, seconds: Long?) = Chat(id = id, lastMessageAt = seconds?.let { Timestamp(it, 0) })
    private fun invite(seconds: Long?) = AppNotification(id = "n$seconds", createdAt = seconds?.let { Timestamp(it, 0) })

    private val chats = listOf(chat("a", 300), chat("b", 200), chat("c", 100))

    @Test
    fun `entry takes the date of the newest invite`() {
        val entry = invitesEntry("me", listOf(invite(150), invite(250), invite(null)))
        assertEquals("invites_me", entry.id)
        assertEquals(250L, entry.lastMessageAt?.seconds)
    }

    @Test
    fun `without invites the entry has no date and goes last`() {
        val entry = invitesEntry("me", emptyList())
        assertNull(entry.lastMessageAt)
        assertEquals(listOf("a", "b", "c", "invites_me"), withEntryByDate(chats, entry).map { it.id })
    }

    @Test
    fun `entry is placed among chats by date`() {
        val entry = invitesEntry("me", listOf(invite(250)))
        assertEquals(listOf("a", "invites_me", "b", "c"), withEntryByDate(chats, entry).map { it.id })
    }

    @Test
    fun `newest invite puts the entry on top, oldest at the bottom`() {
        assertEquals("invites_me", withEntryByDate(chats, invitesEntry("me", listOf(invite(999)))).first().id)
        assertEquals("invites_me", withEntryByDate(chats, invitesEntry("me", listOf(invite(1)))).last().id)
    }

    @Test
    fun `chats without a date stay below a dated entry`() {
        val withEmpty = chats + chat("new", null)
        val entry = invitesEntry("me", listOf(invite(50)))
        assertEquals(listOf("a", "b", "c", "invites_me", "new"), withEntryByDate(withEmpty, entry).map { it.id })
    }

    @Test
    fun `entry goes into an empty list`() {
        assertEquals(listOf("invites_me"), withEntryByDate(emptyList(), invitesEntry("me", listOf(invite(5)))).map { it.id })
    }
}
