// data/repository/SavedMessageRepository.kt
package by.iposdev.visorlink.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import by.iposdev.visorlink.data.model.*
import by.iposdev.visorlink.utils.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.crypto.SecretKey

class SavedMessagesRepository(
    private val db: FirebaseFirestore,
    private val context: Context
) {

    suspend fun loadSettings(uid: String): SavedMessagesSettings? {
        val snap = db.collection("savedMessagesSettings").document(uid).get().await()
        return if (snap.exists()) snap.toObject(SavedMessagesSettings::class.java) else null
    }

    fun settingsFlow(uid: String): Flow<SavedMessagesSettings?> = callbackFlow {
        val reg = db.collection("savedMessagesSettings").document(uid)
            .addSnapshotListener { snap, _ ->
                val settings = if (snap?.exists() == true) snap.toObject(SavedMessagesSettings::class.java) else null
                trySend(settings)
            }
        awaitClose { reg.remove() }
    }

    suspend fun setPin(uid: String, pin: String) {
        val hash = hashPin(pin, uid)
        db.collection("savedMessagesSettings").document(uid)
            .set(mapOf("pinEnabled" to true, "pinHash" to hash, "lockTimeout" to 5, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }

    suspend fun verifyPin(uid: String, enteredPin: String): Boolean {
        val snap = db.collection("savedMessagesSettings").document(uid).get().await()
        val storedHash = snap.getString("pinHash") ?: return false
        return hashPin(enteredPin, uid) == storedHash
    }

    suspend fun disablePin(uid: String) {
        db.collection("savedMessagesSettings").document(uid).update(mapOf("pinEnabled" to false, "pinHash" to FieldValue.delete(), "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun updateLockTimeout(uid: String, minutes: Int) {
        db.collection("savedMessagesSettings").document(uid).set(mapOf("lockTimeout" to minutes, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }

    fun messagesFlow(uid: String, key: SecretKey?): Flow<List<SavedMessage>> = callbackFlow {
        val reg = db.collection("savedMessages").document(uid).collection("messages").orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val messages = snap?.documents?.mapNotNull { doc ->
                    try { doc.toObject(SavedMessage::class.java)?.copy(id = doc.id)?.let { msg -> decryptIfNeeded(msg, key) } } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveText(uid: String, text: String, key: SecretKey?, forwardFrom: ForwardFrom? = null) {
        val msgRef = db.collection("savedMessages").document(uid).collection("messages").document()
        val data = buildMap<String, Any?> {
            put("senderId",  uid); put("type", MessageType.TEXT); put("createdAt", FieldValue.serverTimestamp()); put("deleted", false)
            forwardFrom?.let { put("forwardFrom", it.toMap()) }
            if (key != null) {
                val (ct, iv) = encryptText(text, key)
                put("encrypted", true); put("encryptedText", ct); put("iv", iv)
            } else {
                put("text", text)
            }
        }
        msgRef.set(data).await()
    }

    suspend fun saveImage(uid: String, uri: Uri, caption: String? = null, key: SecretKey? = null, isSpoiler: Boolean = false, forwardFrom: ForwardFrom? = null) = withContext(Dispatchers.IO) {
        val fileName = "${System.currentTimeMillis()}_${uri.lastPathSegment ?: "image.jpg"}"
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext
        val (uploadBytes, ivStr) = if (key != null) {
            val (encBytes, iv) = encryptBytes(bytes, key)
            encBytes to iv
        } else {
            bytes to null
        }
        val tempFile = File(context.cacheDir, fileName).apply { writeBytes(uploadBytes) }
        val mediaId = CdnService.uploadFile(tempFile, "image/jpeg", isVault = true)
        tempFile.delete()

        saveMediaMeta(uid, MessageType.IMAGE, mediaId, fileName, null, caption, emptyList(), null, null, null, null, isSpoiler, forwardFrom, key, ivStr)
    }

    suspend fun saveVoice(uid: String, uri: Uri, durationSec: Int, key: SecretKey? = null, forwardFrom: ForwardFrom? = null) = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext
        val (uploadBytes, ivStr) = if (key != null) {
            val (encBytes, iv) = encryptBytes(bytes, key)
            encBytes to iv
        } else {
            bytes to null
        }
        val tempFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.webm").apply { writeBytes(uploadBytes) }
        val mediaId = CdnService.uploadFile(tempFile, "audio/webm", isVault = true)
        tempFile.delete()

        saveMediaMeta(uid, MessageType.VOICE, mediaId, null, durationSec, null, emptyList(), null, null, null, null, false, forwardFrom, key, ivStr)
    }

    private suspend fun saveMediaMeta(
        uid: String, type: String, cdnMediaId: String, fileName: String?, duration: Int?, caption: String?, images: List<AlbumImage>, stickerId: String?, packId: String?, packName: String?, packEmoji: String?, spoiler: Boolean, forwardFrom: ForwardFrom?, key: SecretKey?, fileIv: String?
    ) {
        val msgRef = db.collection("savedMessages").document(uid).collection("messages").document()
        val data = buildMap<String, Any?> {
            put("senderId", uid); put("type", type); put("createdAt", FieldValue.serverTimestamp()); put("deleted", false)
            put("cdnMediaId", cdnMediaId)
            if (fileName != null) put("fileName", fileName)
            if (duration != null) put("duration", duration)
            if (spoiler) put("spoiler", true)
            if (stickerId != null) put("stickerId", stickerId)
            if (packId != null) put("packId", packId)
            if (packName != null) put("packName", packName)
            if (packEmoji != null) put("packEmoji", packEmoji)
            if (images.isNotEmpty()) put("images", images.map { it.toMap() })
            forwardFrom?.let { put("forwardFrom", it.toMap()) }

            // Если шифровали сам файл
            if (fileIv != null) {
                put("encrypted", true)
                put("iv", fileIv)
            }
            // Шифруем caption
            if (!caption.isNullOrBlank()) {
                if (key != null) {
                    val (ct, iv) = encryptText(caption, key)
                    put("encryptedCaption", ct)
                    if (fileIv == null) { put("encrypted", true); put("iv", iv) }
                } else {
                    put("caption", caption)
                }
            }
        }
        msgRef.set(data).await()
    }

    suspend fun deleteMessage(uid: String, messageId: String) {
        db.collection("savedMessages").document(uid).collection("messages").document(messageId).update(mapOf("deleted" to true, "deletedAt" to FieldValue.serverTimestamp())).await()
    }

    private fun decryptIfNeeded(msg: SavedMessage, key: SecretKey?): SavedMessage {
        if (msg.encrypted != true) return msg
        if (key == null) return msg
        val ct = msg.encryptedText
        val iv = msg.iv ?: return msg
        val plaintext = ct?.let { decryptText(it, iv, key) }
        val caption = msg.encryptedCaption?.let { decryptText(it, iv, key) }
        return msg.copy(text = plaintext ?: msg.text, caption = caption ?: msg.caption)
    }

    suspend fun getBiometricPin(uid: String): String? = null
}