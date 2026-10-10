package org.visorlink.app.ui.screens.chatlist

import org.visorlink.app.data.model.AppNotification
import org.visorlink.app.data.model.Chat

/** id синтетической строки «Приглашения» в списке чатов (как `saved_<uid>` у «Избранного»). */
internal fun invitesEntryId(uid: String) = "invites_$uid"

/**
 * Строка «Приглашения» в списке чатов. Её дата — дата самого свежего приглашения, поэтому
 * строка встаёт среди чатов по времени, как обычный чат; без приглашений даты нет, и она
 * уходит в конец списка. Писать туда нельзя — нажатие открывает экран приглашений.
 */
internal fun invitesEntry(uid: String, invites: List<AppNotification>): Chat = Chat(
    id = invitesEntryId(uid),
    type = "direct",
    lastMessageAt = invites.mapNotNull { it.createdAt }.maxOrNull(),
)

/**
 * Вставляет [entry] в список [chats], отсортированный по убыванию даты: перед первым чатом,
 * который старше (или без даты). Без даты у [entry] — в конец.
 */
internal fun withEntryByDate(chats: List<Chat>, entry: Chat): List<Chat> {
    val at = entry.lastMessageAt ?: return chats + entry
    val index = chats.indexOfFirst { chat -> chat.lastMessageAt?.let { it < at } ?: true }
    return if (index < 0) chats + entry else chats.toMutableList().apply { add(index, entry) }
}
