package org.visorlink.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.visorlink.app.data.model.ScheduledMessage
import org.visorlink.app.data.remote.EconomyCaller

/**
 * Репозиторий отложенных сообщений VisorLink PRO.
 * Слушает коллекцию `scheduledMessages` и вызывает Cloud Function `scheduledMessage`.
 */
class ScheduledRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val tag = "ScheduledRepository"

    /**
     * Поток своих запланированных сообщений в конкретном чате (ожидающие и не ушедшие с ошибкой).
     */
    fun mine(uid: String, chatId: String): Flow<List<ScheduledMessage>> = callbackFlow {
        if (uid.isEmpty() || chatId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("scheduledMessages")
            .whereEqualTo("uid", uid)
            .whereEqualTo("chatId", chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Ошибка чтения scheduledMessages: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val list = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    ScheduledMessage.parse(doc.id, data)
                }.sortedWith { a, b ->
                    val x = a.sendAt ?: Long.MAX_VALUE
                    val y = b.sendAt ?: Long.MAX_VALUE
                    if (x != y) x.compareTo(y) else a.createdAt.compareTo(b.createdAt)
                }

                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Создать запланированное сообщение.
     */
    suspend fun create(
        chatId: String,
        topicId: String? = null,
        text: String,
        sendAt: Long,
        replyTo: Map<String, Any?>? = null
    ): String {
        val payload = mutableMapOf<String, Any?>(
            "action" to "create",
            "chatId" to chatId,
            "text" to text,
            "sendAt" to sendAt
        )
        if (!topicId.isNullOrEmpty()) payload["topicId"] = topicId
        if (replyTo != null) payload["replyTo"] = replyTo

        val res = EconomyCaller.callEconomy("scheduledMessage", payload)
        return res["id"] as? String ?: ""
    }

    /**
     * Обновить текст или время запланированного сообщения.
     */
    suspend fun update(id: String, text: String? = null, sendAt: Long? = null) {
        val payload = mutableMapOf<String, Any?>(
            "action" to "update",
            "id" to id
        )
        if (text != null) payload["text"] = text
        if (sendAt != null) payload["sendAt"] = sendAt
        EconomyCaller.callEconomy("scheduledMessage", payload)
    }

    /**
     * Отменить запланированное сообщение (или удалить неудавшееся).
     */
    suspend fun cancel(id: String) {
        EconomyCaller.callEconomy("scheduledMessage", mapOf("action" to "cancel", "id" to id))
    }

    /**
     * Отправить сообщение немедленно.
     */
    suspend fun sendNow(id: String) {
        EconomyCaller.callEconomy("scheduledMessage", mapOf("action" to "sendNow", "id" to id))
    }
}
