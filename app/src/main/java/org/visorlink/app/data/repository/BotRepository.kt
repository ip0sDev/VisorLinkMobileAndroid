package org.visorlink.app.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Боты в личных сообщениях (порт Flutter `data/chat/bots.dart`). Свои боты — вкладка «Боты» веба —
 * только через callables `getUserBots` / `createDmBot` / `editDmBot` / `deleteDmBot` /
 * `regenerateDmBotToken` (регион europe-west1). Прежние имена `listDmBots` / `regenerateBotToken`
 * на сервере не существуют.
 */

data class DmBot(
    val uid: String,
    val username: String,
    val name: String,
    val avatarUrl: String? = null,
    /** `unverified` | `verified` | `official`. */
    val badge: String = "unverified",
    val banned: Boolean = false,
    /** Бот получает входящие сообщения на вебхук; иначе только отправляет. */
    val receiveMessages: Boolean = false,
    val webhookUrl: String = "",
    /** Сам секрет клиенту не отдаётся — только признак, что он задан. */
    val hasWebhookSecret: Boolean = false,
    val lastDeliveryError: String? = null,
) {
    companion object {
        fun fromMap(m: Map<*, *>): DmBot = DmBot(
            uid = m["botUid"] as? String ?: "",
            username = m["username"] as? String ?: "",
            name = m["name"] as? String ?: "",
            avatarUrl = m["avatarUrl"] as? String,
            badge = m["badge"] as? String ?: "unverified",
            banned = m["banned"] == true,
            receiveMessages = m["receiveMessages"] == true,
            webhookUrl = m["webhookUrl"] as? String ?: "",
            hasWebhookSecret = m["hasWebhookSecret"] == true,
            lastDeliveryError = m["lastDeliveryError"] as? String,
        )
    }
}

class BotRepository(
    private val functions: FirebaseFunctions
) {
    private suspend fun call(name: String, data: Map<String, Any?>): Map<*, *> =
        (functions.getHttpsCallable(name).call(data).await().data as? Map<*, *>) ?: emptyMap<Any, Any>()

    suspend fun list(): List<DmBot> {
        val raw = call("getUserBots", emptyMap())["bots"] as? List<*> ?: return emptyList()
        return raw.filterIsInstance<Map<*, *>>().map { DmBot.fromMap(it) }
    }

    /** Создание: возвращает токен — он показывается один раз. */
    suspend fun create(
        name: String,
        username: String,
        receiveMessages: Boolean = false,
        webhookUrl: String = "",
        webhookSecret: String = "",
    ): String {
        val d = call(
            "createDmBot",
            mapOf(
                "name" to name.trim(),
                "username" to username.trim().removePrefix("@").lowercase(),
                "receiveMessages" to receiveMessages,
                "webhookUrl" to webhookUrl.trim(),
                "webhookSecret" to webhookSecret.trim(),
            ),
        )
        return d["botToken"] as String
    }

    /** Правка сбрасывает значок верификации (так решает сервер). Пустой [webhookSecret] — оставить прежний. */
    suspend fun edit(
        botUid: String,
        name: String,
        receiveMessages: Boolean,
        webhookUrl: String,
        webhookSecret: String = "",
        avatarUrl: String? = null,
    ) {
        call(
            "editDmBot",
            buildMap {
                put("botUid", botUid)
                put("name", name.trim())
                if (avatarUrl != null) put("avatarUrl", avatarUrl)
                put("receiveMessages", receiveMessages)
                put("webhookUrl", webhookUrl.trim())
                if (webhookSecret.trim().isNotEmpty()) put("webhookSecret", webhookSecret.trim())
            },
        )
    }

    suspend fun delete(botUid: String) {
        call("deleteDmBot", mapOf("botUid" to botUid))
    }

    suspend fun regenerateToken(botUid: String): String =
        call("regenerateDmBotToken", mapOf("botUid" to botUid))["botToken"] as String
}
