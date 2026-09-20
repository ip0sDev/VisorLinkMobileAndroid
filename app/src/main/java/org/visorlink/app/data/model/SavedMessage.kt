package org.visorlink.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

// ─── Saved Messages ───────────────────────────────────────────────────────────

@IgnoreExtraProperties
data class SavedMessage(
    val id: String = "",
    val senderId: String = "",
    val type: String = MessageType.TEXT,
    val text: String? = null,
    val encrypted: Boolean? = null,
    val encryptedText: String? = null,
    val encryptedCaption: String? = null,
    val iv: String? = null,
    val url: String? = null,
    val cdnMediaId: String? = null,
    val fileName: String? = null,
    val duration: Int? = null,
    val caption: String? = null,
    val images: List<AlbumImage> = emptyList(),
    val spoiler: Boolean = false,
    val forwardFrom: Map<String, Any?>? = null,
    val tg_forwarded: Boolean? = null,
    val tg_forwarded_from: String? = null,
    val tg_forwarded_from_fallback: String? = null,
    val isUnofficialClient: Boolean? = null,
    val stickerId: String? = null,
    val packId: String? = null,
    val packName: String? = null,
    val packEmoji: String? = null,
    val deleted: Boolean = false,
    val deletedAt: Timestamp? = null,
    val lastEdited: Timestamp? = null,
    val editHistory: List<Map<String, Any>> = emptyList(),
    val createdAt: Timestamp? = null,
    val isDiary: Boolean = false,
    // Временные/Локальные данные для расшифровки на лету в UI
    val localBytes: ByteArray? = null
) {

    val parsedForwardFrom: ForwardFrom?
        get() = forwardFrom?.let {
            try {
                ForwardFrom(
                    senderId       = it["senderId"] as? String ?: "",
                    senderUsername = it["senderUsername"] as? String ?: "",
                    chatId         = it["chatId"] as? String,
                    chatName       = it["chatName"] as? String,
                    messageId      = it["messageId"] as? String ?: ""
                )
            } catch (_: Exception) { null }
        }
}

data class SavedMessagesSettings(
    val pinEnabled: Boolean = false,
    val pinHash: String? = null,
    val lockTimeout: Int = 5,         // минуты; 0 = немедленно
    val updatedAt: Timestamp? = null
)

// ─── Forward ──────────────────────────────────────────────────────────────────

data class ForwardFrom(
    val senderId: String = "",
    val senderUsername: String = "",
    val chatId: String? = null,
    val chatName: String? = null,
    val messageId: String = ""
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("senderId", senderId)
        put("senderUsername", senderUsername)
        chatId?.takeIf { it.isNotBlank() }?.let { put("chatId", it) }
        chatName?.takeIf { it.isNotBlank() }?.let { put("chatName", it) }
        messageId.takeIf { it.isNotBlank() }?.let { put("messageId", it) }
    }
}

// Правило одноуровневой пересылки (как в Telegram):
// При пересылке уже пересланного сообщения сохраняется оригинальный forwardFrom.
fun buildForwardFrom(
    message: Message,
    sourceChatId: String?,
    sourceChatName: String?
): ForwardFrom {
    message.parsedForwardFrom?.let { return it }
    return ForwardFrom(
        senderId = message.senderId,
        senderUsername = message.senderUsername.ifEmpty { "user" },
        chatId = sourceChatId?.takeIf { it.isNotBlank() },
        chatName = sourceChatName?.takeIf { it.isNotBlank() },
        messageId = message.id.takeIf { it.isNotBlank() } ?: ""
    )
}

// Вспомогательная модель для пересылаемого сообщения (объединяет Message и SavedMessage)
data class ForwardableMessage(
    val id: String,
    val senderId: String,
    val senderUsername: String,
    val type: String,
    val text: String? = null,
    val encryptedText: String? = null,
    val iv: String? = null,
    val encrypted: Boolean? = null,
    val imageUrl: String? = null,
    val voiceUrl: String? = null,
    val caption: String? = null,
    val stickerId: String? = null,
    val packEmoji: String? = null,
    val albumItems: List<AlbumImage>? = null,
    val chatId: String? = null,
    val chatName: String? = null,

    // Пересылка (VisorLink Native)
    val forwardFrom: ForwardFrom? = null,

    // Пересылка (Telegram Bot Forwarding)
    val tg_forwarded: Boolean? = null,
    val tg_forwarded_from: String? = null,
    val tg_forwarded_from_fallback: String? = null,
    val isUnofficialClient: Boolean? = null
) {
    companion object {
        fun fromMessage(msg: Message, chatId: String, chatName: String?): ForwardableMessage =
            ForwardableMessage(
                id             = msg.id,
                senderId       = msg.senderId,
                senderUsername = msg.senderUsername,
                type           = msg.type,
                text           = msg.text,
                imageUrl       = if (msg.type == MessageType.IMAGE) msg.url else null,
                voiceUrl       = if (msg.type == MessageType.VOICE) msg.url else null,
                caption        = msg.caption,
                stickerId      = msg.stickerId,
                packEmoji      = msg.packEmoji,
                albumItems     = msg.images.takeIf { it.isNotEmpty() },
                chatId         = chatId,
                chatName       = chatName,

                forwardFrom                = msg.parsedForwardFrom,
                tg_forwarded               = msg.tg_forwarded,
                tg_forwarded_from          = msg.tg_forwarded_from,
                tg_forwarded_from_fallback = msg.tg_forwarded_from_fallback,
                isUnofficialClient         = msg.isUnofficialClient
            )

        fun fromSaved(msg: SavedMessage, uid: String): ForwardableMessage =
            ForwardableMessage(
                id             = msg.id,
                senderId       = msg.senderId,
                senderUsername = "",    // из Избранного username не нужен
                type           = msg.type,
                text           = msg.text,
                encryptedText  = msg.encryptedText,
                iv             = msg.iv,
                encrypted      = msg.encrypted,
                imageUrl       = if (msg.type == MessageType.IMAGE) msg.url else null,
                voiceUrl       = if (msg.type == MessageType.VOICE) msg.url else null,
                caption        = msg.caption,
                stickerId      = msg.stickerId,
                packEmoji      = msg.packEmoji,
                albumItems     = msg.images.takeIf { it.isNotEmpty() },
                chatId         = null,
                chatName       = "Избранное",

                forwardFrom                = msg.parsedForwardFrom,
                tg_forwarded               = msg.tg_forwarded,
                tg_forwarded_from          = msg.tg_forwarded_from,
                tg_forwarded_from_fallback = msg.tg_forwarded_from_fallback,
                isUnofficialClient         = msg.isUnofficialClient
            )
    }
}