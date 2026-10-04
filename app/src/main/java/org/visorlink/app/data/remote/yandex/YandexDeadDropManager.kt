package org.visorlink.app.data.remote.yandex

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.visorlink.app.utils.ChatDataCache
import org.visorlink.app.utils.decryptText
import org.visorlink.app.utils.deriveKey
import org.visorlink.app.utils.encryptText
import java.security.MessageDigest
import java.util.UUID

/**
 * Бессерверный менеджер аварийной доставки (Dead-Drop Relay) через Яндекс.Диск.
 * Работает автономно между двумя абонентами без серверов-посредников.
 * Доступен только при активном фича-флаге `enable_alternative_outbox`.
 */
class YandexDeadDropManager(
    private val context: Context,
    private val configManager: YandexRelayConfigManager,
    private val transportService: YandexDiskTransportService
) {
    companion object {
        private const val TAG = "YandexDeadDrop"
        private const val ENVELOPE_VERSION = 1
    }

    /**
     * Проверяет, готов ли аварийный транспорт к отправке/получению.
     */
    fun isAvailable(): Boolean {
        return configManager.isRelayEnabled() && !configManager.getActiveToken().isNullOrBlank()
    }

    /**
     * Отправляет зашифрованное сообщение в почтовый ящик получателя на Яндекс.Диске.
     */
    suspend fun sendViaDeadDrop(
        chatId: String,
        recipientUid: String,
        messageJson: JSONObject
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false
        val token = configManager.getActiveToken() ?: return@withContext false

        val chatFolder = getChatMailboxPath(chatId)
        val recipientInbox = "$chatFolder/in_${hashId(recipientUid)}"
        Log.d(TAG, "sendViaDeadDrop: placing message in $recipientInbox for recipient $recipientUid")

        try {
            // Убеждаемся, что папка входящих получателя создана
            transportService.ensureFolder(token, recipientInbox)

            // Шифруем тело сообщения AES-256-GCM ключом чата
            val secretKey = deriveKey("vl_deaddrop_salt", chatId)
            val (ciphertext, iv) = encryptText(messageJson.toString(), secretKey)

            val envelope = JSONObject().apply {
                put("v", ENVELOPE_VERSION)
                put("chatId", chatId)
                put("iv", iv)
                put("payload", ciphertext)
                put("ts", System.currentTimeMillis())
            }

            val fileName = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.vldrop"
            val envelopeBytes = envelope.toString().toByteArray(Charsets.UTF_8)

            val success = transportService.uploadEnvelope(token, recipientInbox, fileName, envelopeBytes)
            if (success) {
                Log.d(TAG, "Message ${messageJson.optString("id")} successfully placed in dead-drop at $recipientInbox/$fileName")
            } else {
                Log.e(TAG, "Failed to upload envelope to $recipientInbox/$fileName")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message via dead-drop", e)
            false
        }
    }

    /**
     * Опрашивает почтовый ящик текущего пользователя на Яндекс.Диске,
     * расшифровывает входящие сообщения, сохраняет их в LocalCacheDB и удаляет файлы с Диска.
     */
    suspend fun pollIncomingMessages(
        chatId: String,
        currentUid: String
    ): List<JSONObject> = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext emptyList()
        val token = configManager.getActiveToken() ?: return@withContext emptyList()

        val chatFolder = getChatMailboxPath(chatId)
        val myInbox = "$chatFolder/in_${hashId(currentUid)}"

        val receivedList = mutableListOf<JSONObject>()

        try {
            val fileNames = transportService.listEnvelopes(token, myInbox)
                .filter { it.endsWith(".vldrop") }
                .sorted()

            if (fileNames.isNotEmpty()) {
                Log.d(TAG, "Found ${fileNames.size} envelopes in $myInbox: $fileNames")
            }

            if (fileNames.isEmpty()) return@withContext emptyList()

            val secretKey = deriveKey("vl_deaddrop_salt", chatId)

            for (fileName in fileNames) {
                val envelopeBytes = transportService.downloadEnvelope(token, myInbox, fileName)
                if (envelopeBytes == null) {
                    Log.w(TAG, "Failed to download envelope $fileName from $myInbox")
                    continue
                }

                try {
                    val envelopeJson = JSONObject(String(envelopeBytes, Charsets.UTF_8))
                    val ciphertext = envelopeJson.getString("payload")
                    val iv = envelopeJson.getString("iv")

                    val decryptedJsonStr = decryptText(ciphertext, iv, secretKey)
                    if (decryptedJsonStr != null) {
                        val msgJson = JSONObject(decryptedJsonStr)
                        receivedList.add(msgJson)

                        // Сохраняем в локальный SQLite кэш сразу
                        val localMsg = ChatDataCache.jsonToMessage(msgJson)
                        ChatDataCache.saveMessages(context, chatId, listOf(localMsg))
                        val text = localMsg.text ?: localMsg.caption ?: "Сообщение"
                        ChatDataCache.updateChatLastMessage(
                            context = context,
                            uid = currentUid,
                            chatId = chatId,
                            text = text,
                            senderId = localMsg.senderId,
                            tsSeconds = localMsg.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L)
                        )

                        // Подтверждение получения: удаляем файл из ящика на Диске
                        transportService.deleteEnvelope(token, myInbox, fileName)
                        Log.d(TAG, "Successfully processed and deleted envelope $fileName from $myInbox")
                    } else {
                        Log.e(TAG, "Failed to decrypt ciphertext in envelope $fileName")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse envelope $fileName", e)
                }
            }

            if (receivedList.isNotEmpty()) {
                Log.d(TAG, "Dead-drop polled for chat $chatId: received ${receivedList.size} new messages")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error polling dead-drop mailbox for chat $chatId at $myInbox", e)
        }

        receivedList
    }

    /**
     * Вычисляет детерминированный путь к каталогу чата на Яндекс.Диске.
     */
    fun getChatMailboxPath(chatId: String): String {
        val basePath = configManager.getBasePath()
        val hash = hashId("vl_chat_$chatId")
        return "$basePath/c_$hash"
    }

    private fun hashId(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}
