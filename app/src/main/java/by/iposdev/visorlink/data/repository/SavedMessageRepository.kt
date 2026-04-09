package by.iposdev.visorlink.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.utils.decryptText
import by.iposdev.visorlink.utils.encryptText
import by.iposdev.visorlink.utils.hashPin
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.crypto.SecretKey

class SavedMessagesRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) {

    // ─── Settings ─────────────────────────────────────────────────────────────

    suspend fun loadSettings(uid: String): SavedMessagesSettings? {
        val snap = db.collection("savedMessagesSettings").document(uid).get().await()
        return if (snap.exists()) snap.toObject(SavedMessagesSettings::class.java) else null
    }

    fun settingsFlow(uid: String): Flow<SavedMessagesSettings?> = callbackFlow {
        val reg = db.collection("savedMessagesSettings").document(uid)
            .addSnapshotListener { snap, _ ->
                val settings = if (snap?.exists() == true)
                    snap.toObject(SavedMessagesSettings::class.java) else null
                trySend(settings)
            }
        awaitClose { reg.remove() }
    }

    // ─── PIN ──────────────────────────────────────────────────────────────────

    suspend fun setPin(uid: String, pin: String) {
        val hash = hashPin(pin, uid)
        db.collection("savedMessagesSettings").document(uid)
            .set(
                mapOf(
                    "pinEnabled"        to true,
                    "pinHash"           to hash,
                    "lockTimeout"       to 5,
                    "updatedAt"         to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
    }

    suspend fun verifyPin(uid: String, enteredPin: String): Boolean {
        val snap = db.collection("savedMessagesSettings").document(uid).get().await()
        val storedHash = snap.getString("pinHash") ?: return false
        return hashPin(enteredPin, uid) == storedHash
    }

    suspend fun disablePin(uid: String) {
        db.collection("savedMessagesSettings").document(uid)
            .update(
                mapOf(
                    "pinEnabled"        to false,
                    "pinHash"           to FieldValue.delete(),
                    "updatedAt"         to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun updateLockTimeout(uid: String, minutes: Int) {
        db.collection("savedMessagesSettings").document(uid)
            .set(
                mapOf("lockTimeout" to minutes, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            ).await()
    }

    // ─── Messages Flow ────────────────────────────────────────────────────────

    fun messagesFlow(uid: String, key: SecretKey?): Flow<List<SavedMessage>> = callbackFlow {
        val reg = db.collection("savedMessages").document(uid)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val messages = snap?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(SavedMessage::class.java)
                            ?.copy(id = doc.id)
                            ?.let { msg -> decryptIfNeeded(msg, key) }
                    } catch (e: Exception) {
                        Log.e("SavedRepo", "Ошибка парсинга документа ${doc.id}: ${e.message}", e)
                        null
                    }
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { reg.remove() }
    }

    // ─── Save text ────────────────────────────────────────────────────────────

    suspend fun saveText(
        uid: String,
        text: String,
        key: SecretKey?,
        forwardFrom: ForwardFrom? = null
    ) {
        val msgRef = db.collection("savedMessages").document(uid).collection("messages").document()
        val data = buildMap<String, Any?> {
            put("senderId",  uid)
            put("type",      MessageType.TEXT)
            put("createdAt", FieldValue.serverTimestamp())
            put("deleted",   false)
            forwardFrom?.let { put("forwardFrom", it.toMap()) }

            if (key != null) {
                val (ct, iv) = encryptText(text, key)
                put("encrypted",     true)
                put("encryptedText", ct)
                put("iv",            iv)
                // "text" намеренно не добавляем
            } else {
                put("text", text)
            }
        }
        msgRef.set(data).await()
    }

    // ─── Save media (image/voice/sticker/album) ───────────────────────────────

    suspend fun saveMedia(
        uid: String,
        type: String,
        url: String,
        fileName: String? = null,
        duration: Int? = null,
        caption: String? = null,
        images: List<AlbumImage>? = null,
        stickerId: String? = null,
        packId: String? = null,
        packName: String? = null,
        packEmoji: String? = null,
        spoiler: Boolean = false,
        forwardFrom: ForwardFrom? = null,
        // ключ не используется для медиа, но может использоваться для caption
        key: SecretKey? = null
    ) {
        val msgRef = db.collection("savedMessages").document(uid).collection("messages").document()
        val data = buildMap<String, Any?> {
            put("senderId",  uid)
            put("type",      type)
            put("createdAt", FieldValue.serverTimestamp())
            put("deleted",   false)
            put("url",       url)
            if (fileName != null) put("fileName", fileName)
            if (duration != null) put("duration", duration)
            if (spoiler) put("spoiler", true)
            if (stickerId != null) put("stickerId", stickerId)
            if (packId != null) put("packId", packId)
            if (packName != null) put("packName", packName)
            if (packEmoji != null) put("packEmoji", packEmoji)
            if (images != null) put("images", images.map { it.toMap() })
            forwardFrom?.let { put("forwardFrom", it.toMap()) }

            // caption — шифруем если ключ есть
            if (!caption.isNullOrBlank()) {
                if (key != null) {
                    val (ct, iv) = encryptText(caption, key)
                    put("encryptedCaption", ct)
                    put("iv",               iv)
                    put("encrypted",        true)
                } else {
                    put("caption", caption)
                }
            }
        }
        msgRef.set(data).await()
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    suspend fun deleteMessage(uid: String, messageId: String) {
        db.collection("savedMessages").document(uid)
            .collection("messages").document(messageId)
            .update(
                mapOf(
                    "deleted"   to true,
                    "deletedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    // ─── Internal: decrypt in-memory if needed ────────────────────────────────

    private fun decryptIfNeeded(msg: SavedMessage, key: SecretKey?): SavedMessage {
        if (msg.encrypted != true) return msg
        if (key == null) return msg  // заблокировано — текст не показываем
        val ct = msg.encryptedText ?: return msg
        val iv = msg.iv ?: return msg
        val plaintext = decryptText(ct, iv, key) ?: return msg  // null → ошибка расшифровки
        val caption = msg.encryptedCaption?.let { decryptText(it, iv, key) }
        return msg.copy(text = plaintext, caption = caption ?: msg.caption)
    }
    // ─── Save Image ───────────────────────────────────────────────────────────

    suspend fun saveImage(
        uid: String,
        uri: Uri,
        caption: String? = null,
        key: SecretKey? = null,
        isSpoiler: Boolean = false,
        forwardFrom: ForwardFrom? = null
    ) {
        // 1. Загружаем в Storage (папка savedMessages/UID/media)
        val fileName = "img_${System.currentTimeMillis()}_${uri.lastPathSegment}"
        val ref = storage.reference.child("savedMessages/$uid/media/$fileName")

        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()

        // 2. Сохраняем метаданные в Firestore через твой метод saveMedia
        saveMedia(
            uid = uid,
            type = MessageType.IMAGE,
            url = url,
            fileName = uri.lastPathSegment ?: "image.jpg",
            caption = caption,
            spoiler = isSpoiler,
            forwardFrom = forwardFrom,
            key = key
        )
    }

    // ─── Save Voice ───────────────────────────────────────────────────────────

    suspend fun saveVoice(
        uid: String,
        uri: Uri, // Изменили с File на Uri
        durationSec: Int,
        key: SecretKey? = null,
        forwardFrom: ForwardFrom? = null
    ) {
        val fileName = "voice_${System.currentTimeMillis()}.webm"
        val ref = storage.reference.child("savedMessages/$uid/media/$fileName")

        // Теперь просто передаем uri напрямую
        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()

        saveMedia(
            uid = uid,
            type = MessageType.VOICE,
            url = url,
            duration = durationSec,
            forwardFrom = forwardFrom,
            key = key
        )
    }
    suspend fun getBiometricPin(uid: String): String? {
        // Биометрия без хранения plain-PIN невозможна без Keystore-ключа.
        // Возвращаем null — биометрия тогда только разблокирует UI,
        // но ключ шифрования не восстанавливается (текст недоступен до PIN-ввода).
        // Для полноценной биометрии нужно хранить PIN в Android Keystore.
        return null
    }
}