package by.iposdev.visorlink.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.data.remote.FirestoreCollections
import by.iposdev.visorlink.utils.encryptText
import kotlinx.coroutines.tasks.await
import javax.crypto.SecretKey

import by.iposdev.visorlink.data.remote.chat.AlbumImageDto
import by.iposdev.visorlink.data.remote.chat.ForwardDto
import by.iposdev.visorlink.data.remote.chat.SendMessageRequest
import by.iposdev.visorlink.data.remote.chat.VisorLinkApi

class ForwardRepository(
    private val db: FirebaseFirestore,
    private val flagsRepository: FlagsRepository? = null,
    private val api: VisorLinkApi? = null
) {

    /**
     * Пересылает сообщение в обычный чат.
     * Батч атомарно записывает сообщение + обновляет preview + кулдаун пользователя.
     */
    suspend fun forwardMessage(
        originalMsg: ForwardableMessage,
        targetChat: Chat,
        currentUser: UserProfile
    ) {
        // Глубина пересылки = 1: берём самый исходный forwardFrom
        val forwardFrom = originalMsg.forwardFrom ?: ForwardFrom(
            senderId       = originalMsg.senderId,
            senderUsername = originalMsg.senderUsername,
            chatId         = originalMsg.chatId,
            chatName       = originalMsg.chatName,
            messageId      = originalMsg.id
        )

        if (flagsRepository?.isBackendV2Enabled() == true && api != null) {
            val req = SendMessageRequest(
                chatId = targetChat.id,
                type = originalMsg.type,
                text = originalMsg.text,
                caption = originalMsg.caption,
                url = originalMsg.imageUrl ?: originalMsg.voiceUrl,
                stickerId = originalMsg.stickerId,
                packEmoji = originalMsg.packEmoji,
                images = originalMsg.albumItems?.map {
                    AlbumImageDto(
                        url = it.url,
                        cdnMediaId = it.cdnMediaId,
                        fileName = it.fileName,
                        spoiler = it.spoiler
                    )
                },
                forwardFrom = ForwardDto(
                    senderId = forwardFrom.senderId,
                    senderUsername = forwardFrom.senderUsername,
                    chatId = forwardFrom.chatId
                )
            )
            api.sendMessage(req)
            return
        }

        val msgRef = db.collection(FirestoreCollections.CHATS).document(targetChat.id)
            .collection(FirestoreCollections.MESSAGES).document()

        val msgData = buildMap<String, Any?> {
            put("senderId",       currentUser.uid)
            put("senderUsername", currentUser.username)
            put("createdAt",      FieldValue.serverTimestamp())
            put("type",           originalMsg.type)
            put("forwardFrom",    forwardFrom.toMap())
            put("deleted",        false)
            put("reactions",      emptyList<Any>())
            put("readBy",         listOf(currentUser.uid))

            originalMsg.text?.let           { put("text",          it) }
            originalMsg.encryptedText?.let  { put("encryptedText", it) }
            originalMsg.iv?.let             { put("iv",            it) }
            originalMsg.encrypted?.let      { put("encrypted",     it) }
            originalMsg.imageUrl?.let       { put("url",           it) }
            originalMsg.voiceUrl?.let       { put("url",           it) }
            originalMsg.caption?.let        { put("caption",       it) }
            originalMsg.stickerId?.let      { put("stickerId",     it) }
            originalMsg.albumItems?.let     { put("images",        it.map { img -> img.toMap() }) }
        }

        val preview = "↩ " + when (originalMsg.type) {
            MessageType.TEXT    -> (originalMsg.text ?: "").take(60)
            MessageType.IMAGE   -> "🖼 Фото"
            MessageType.VOICE   -> "🎙 Голосовое сообщение"
            MessageType.STICKER -> "${originalMsg.packEmoji ?: "😊"} Стикер"
            MessageType.ALBUM   -> "📷 ${originalMsg.albumItems?.size ?: ""} фото"
            else                -> "Пересланное сообщение"
        }

        val batch = db.batch()
        batch.set(msgRef, msgData)
        batch.update(
            db.collection(FirestoreCollections.CHATS).document(targetChat.id),
            mapOf(
                "lastMessage" to mapOf(
                    "text" to preview,
                    "senderId" to currentUser.uid,
                    "senderUsername" to currentUser.username
                ),
                "lastMessageAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection(FirestoreCollections.USERS).document(currentUser.uid),
            "lastMessageAt", FieldValue.serverTimestamp()
        )
        batch.commit().await()
    }

    /**
     * Пересылает/сохраняет сообщение в Избранное.
     * Если key != null и тип text → шифруем текст перед сохранением.
     */
    suspend fun forwardToSavedMessages(
        originalMsg: ForwardableMessage,
        uid: String,
        key: SecretKey?
    ) {
        val forwardFrom = originalMsg.forwardFrom ?: ForwardFrom(
            senderId       = originalMsg.senderId,
            senderUsername = originalMsg.senderUsername,
            chatId         = originalMsg.chatId,
            chatName       = originalMsg.chatName,
            messageId      = originalMsg.id
        )

        val msgRef = db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid)
            .collection(FirestoreCollections.MESSAGES).document()

        val msgData = buildMap<String, Any?> {
            put("senderId",    uid)
            put("createdAt",   FieldValue.serverTimestamp())
            put("type",        originalMsg.type)
            put("forwardFrom", forwardFrom.toMap())
            put("deleted",     false)

            if (key != null && originalMsg.type == MessageType.TEXT && originalMsg.text != null) {
                val (ct, iv) = encryptText(originalMsg.text, key)
                put("encrypted",     true)
                put("encryptedText", ct)
                put("iv",            iv)
                // "text" не сохраняем — сервер не должен видеть plaintext
            } else {
                originalMsg.text?.let          { put("text",          it) }
                originalMsg.encryptedText?.let { put("encryptedText", it) }
                originalMsg.iv?.let            { put("iv",            it) }
                originalMsg.encrypted?.let     { put("encrypted",     it) }
            }

            originalMsg.imageUrl?.let   { put("url",       it) }
            originalMsg.voiceUrl?.let   { put("url",       it) }
            originalMsg.caption?.let    { put("caption",   it) }
            originalMsg.stickerId?.let  { put("stickerId", it) }
            originalMsg.albumItems?.let { put("images",    it.map { img -> img.toMap() }) }
        }

        val batch = db.batch()
        batch.set(msgRef, msgData)
        // Синхронизируем кулдаун
        batch.update(
            db.collection(FirestoreCollections.USERS).document(uid),
            "lastMessageAt", FieldValue.serverTimestamp()
        )
        batch.commit().await()
    }
}