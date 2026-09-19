package org.visorlink.app.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import org.visorlink.app.data.model.*
import org.visorlink.app.data.remote.FirestoreCollections
import org.visorlink.app.utils.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.SecretKey

class SavedMessagesRepository(
    private val db: FirebaseFirestore,
    private val context: Context
) {

    suspend fun loadSettings(uid: String): SavedMessagesSettings? = try {
        val snap = db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid).get().await()
        if (snap.exists()) snap.toObject(SavedMessagesSettings::class.java) else null
    } catch (e: Exception) { null }

    fun settingsFlow(uid: String): Flow<SavedMessagesSettings?> = callbackFlow {
        val reg = db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid)
            .addSnapshotListener { snap, _ ->
                val settings = if (snap?.exists() == true) snap.toObject(SavedMessagesSettings::class.java) else null
                trySend(settings)
            }
        awaitClose { reg.remove() }
    }

    suspend fun setPin(uid: String, pin: String) {
        val hash = hashPin(pin, uid)
        db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid)
            .set(mapOf("pinEnabled" to true, "pinHash" to hash, "lockTimeout" to 5, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }

    suspend fun verifyPin(uid: String, enteredPin: String): Boolean = try {
        val snap = db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid).get().await()
        val storedHash = snap.getString("pinHash") ?: ""
        val isValid = verifyPinHash(enteredPin, uid, storedHash)
        if (isValid && !storedHash.startsWith("pbkdf2:")) {
            // Прозрачная миграция старого SHA-256 хэша на стойкий PBKDF2
            setPin(uid, enteredPin)
        }
        isValid
    } catch (e: Exception) { false }

    suspend fun clearAllSavedMessages(uid: String) = withContext(Dispatchers.IO) {
        try {
            val messagesRef = db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES)
            val snapshot = messagesRef.get().await()
            val chunks = snapshot.documents.chunked(500)
            for (chunk in chunks) {
                val batch = db.batch()
                for (doc in chunk) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("decrypted_") || file.name.startsWith("voice_") || file.name.startsWith("upload_") || file.name.startsWith("orig_")) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun disablePin(uid: String) {
        clearAllSavedMessages(uid)
        db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid).update(mapOf("pinEnabled" to false, "pinHash" to FieldValue.delete(), "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun updateLockTimeout(uid: String, minutes: Int) {
        db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid).set(mapOf("lockTimeout" to minutes, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }

    fun messagesFlow(uid: String, key: SecretKey?, includeDiary: Boolean = false): Flow<List<SavedMessage>> = callbackFlow {
        val query = db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES)
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val reg = query.addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snap?.documents?.mapNotNull { doc ->
                    try {
                        if (doc.getBoolean("deleted") == true) return@mapNotNull null
                        
                        val isDiary = doc.getBoolean("isDiary") ?: false
                        if (!includeDiary && isDiary) return@mapNotNull null

                        val msg = doc.toObject(SavedMessage::class.java)?.copy(id = doc.id) ?: return@mapNotNull null
                        if (msg.encrypted == true && key == null) return@mapNotNull null
                        decryptIfNeeded(msg, key)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { reg.remove() }
    }

    fun diaryFlow(uid: String, key: SecretKey?): Flow<List<SavedMessage>> = callbackFlow {
        val reg = db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES)
            .whereEqualTo("isDiary", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snap?.documents?.mapNotNull { doc ->
                    try {
                        if (doc.getBoolean("deleted") == true) return@mapNotNull null
                        doc.toObject(SavedMessage::class.java)?.copy(id = doc.id)?.let { msg -> decryptIfNeeded(msg, key) }
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveText(uid: String, text: String, key: SecretKey?, forwardFrom: ForwardFrom? = null, isDiary: Boolean = false) {
        val msgRef = db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES).document()
        val userRef = db.collection(FirestoreCollections.USERS).document(uid) // Ссылка на кулдаун пользователя

        val data = buildMap<String, Any?> {
            put("senderId",  uid); put("type", MessageType.TEXT); put("createdAt", FieldValue.serverTimestamp()); put("deleted", false)
            put("isDiary", isDiary)
            forwardFrom?.let { put("forwardFrom", it.toMap()) }
            if (key != null) {
                val (ct, iv) = encryptText(text, key)
                put("encrypted", true); put("encryptedText", ct); put("iv", iv)
            } else {
                put("text", text)
            }
        }

        // Отправляем батчем (вместе с кулдауном), чтобы пройти правила безопасности Firestore
        val batch = db.batch()
        batch.set(msgRef, data)
        batch.update(userRef, "lastMessageAt", FieldValue.serverTimestamp())
        batch.commit().await()
    }

    suspend fun saveImage(uid: String, uri: Uri, caption: String? = null, key: SecretKey? = null, isSpoiler: Boolean = false, forwardFrom: ForwardFrom? = null, isDiary: Boolean = false): String = withContext(Dispatchers.IO) {
        val fileName = "${System.currentTimeMillis()}_${uri.lastPathSegment ?: "image.jpg"}"
        val tempOriginalFile = File(context.cacheDir, "orig_$fileName")
        val tempUploadFile = File(context.cacheDir, "upload_$fileName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            tempOriginalFile.outputStream().use { output -> input.copyTo(output) }
        }

        val fileIv: String?
        val fileToUpload: File

        if (key != null) {
            fileIv = encryptFile(tempOriginalFile, tempUploadFile, key)
            fileToUpload = tempUploadFile
        } else {
            fileIv = null
            fileToUpload = tempOriginalFile
        }

        val storageRef = FirebaseStorage.getInstance().reference.child("saved_messages/$uid/$fileName")
        storageRef.putFile(Uri.fromFile(fileToUpload)).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()

        tempOriginalFile.delete()
        tempUploadFile.delete()

        saveMediaMeta(uid, MessageType.IMAGE, downloadUrl, fileName, null, caption, emptyList(), null, null, null, null, isSpoiler, forwardFrom, key, fileIv, isDiary)
        return@withContext downloadUrl
    }

    suspend fun saveVoice(uid: String, uri: Uri, durationSec: Int, key: SecretKey? = null, forwardFrom: ForwardFrom? = null) = withContext(Dispatchers.IO) {
        val fileName = "voice_${System.currentTimeMillis()}.webm"
        val tempOriginalFile = File(context.cacheDir, "orig_$fileName")
        val tempUploadFile = File(context.cacheDir, "upload_$fileName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            tempOriginalFile.outputStream().use { output -> input.copyTo(output) }
        }

        val fileIv: String?
        val fileToUpload: File

        if (key != null) {
            fileIv = encryptFile(tempOriginalFile, tempUploadFile, key)
            fileToUpload = tempUploadFile
        } else {
            fileIv = null
            fileToUpload = tempOriginalFile
        }

        val storageRef = FirebaseStorage.getInstance().reference.child("saved_messages/$uid/$fileName")
        storageRef.putFile(Uri.fromFile(fileToUpload)).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()

        tempOriginalFile.delete()
        tempUploadFile.delete()

        saveMediaMeta(uid, MessageType.VOICE, downloadUrl, null, durationSec, null, emptyList(), null, null, null, null, false, forwardFrom, key, fileIv)
    }

    private suspend fun saveMediaMeta(
        uid: String, type: String, url: String?, fileName: String?, duration: Int?, caption: String?, images: List<AlbumImage>, stickerId: String?, packId: String?, packName: String?, packEmoji: String?, spoiler: Boolean, forwardFrom: ForwardFrom?, key: SecretKey?, fileIv: String?, isDiary: Boolean = false
    ) {
        val msgRef = db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES).document()
        val userRef = db.collection(FirestoreCollections.USERS).document(uid) // Ссылка на кулдаун пользователя

        val data = buildMap<String, Any?> {
            put("senderId", uid); put("type", type); put("createdAt", FieldValue.serverTimestamp()); put("deleted", false)
            if (url != null) put("url", url)
            put("isDiary", isDiary)
            if (fileName != null) put("fileName", fileName)
            if (duration != null) put("duration", duration)
            if (spoiler) put("spoiler", true)
            if (stickerId != null) put("stickerId", stickerId)
            if (packId != null) put("packId", packId)
            if (packName != null) put("packName", packName)
            if (packEmoji != null) put("packEmoji", packEmoji)
            if (images.isNotEmpty()) put("images", images.map { it.toMap() })
            forwardFrom?.let { put("forwardFrom", it.toMap()) }

            if (fileIv != null) {
                put("encrypted", true)
                put("iv", fileIv)
            }

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

        // Отправляем батчем (вместе с кулдауном), чтобы пройти правила безопасности Firestore
        val batch = db.batch()
        batch.set(msgRef, data)
        batch.update(userRef, "lastMessageAt", FieldValue.serverTimestamp())
        batch.commit().await()
    }

    suspend fun getDecryptedMediaFile(message: SavedMessage, key: SecretKey?): File? = withContext(Dispatchers.IO) {
        if (message.encrypted != true || key == null || message.iv == null) return@withContext null
        val mediaUrl = message.url ?: return@withContext null

        val decryptedFile = File(context.cacheDir, "decrypted_${message.id}_${message.fileName ?: "media"}")

        if (decryptedFile.exists() && decryptedFile.length() > 0) {
            return@withContext decryptedFile
        }

        try {
            val connection = java.net.URL(mediaUrl).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            decryptStreamToFile(connection.inputStream, decryptedFile, message.iv, key)

            decryptedFile
        } catch (e: Exception) {
            decryptedFile.delete()
            null
        }
    }

    suspend fun deleteMessage(uid: String, messageId: String) {
        db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES).document(messageId).delete().await()
    }

    suspend fun editMessage(uid: String, message: SavedMessage, newText: String, key: SecretKey?) {
        val isCaption = message.type != MessageType.TEXT && message.caption != null
        val oldText = if (isCaption) message.caption ?: "" else message.text ?: ""
        
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val currentTimeISO = isoFormat.format(Date())

        val updates = mutableMapOf<String, Any?>(
            "lastEdited" to FieldValue.serverTimestamp()
        )

        if (key != null) {
            val (encNewText, ivNew) = encryptText(newText, key)
            val (encOldText, ivOld) = encryptText(oldText, key)

            val historyItem = mapOf(
                "encryptedText" to encOldText,
                "iv" to ivOld,
                "editedAt" to currentTimeISO
            )

            if (isCaption) {
                updates["encryptedCaption"] = encNewText
            } else {
                updates["encryptedText"] = encNewText
            }
            updates["iv"] = ivNew
            updates["editHistory"] = FieldValue.arrayUnion(historyItem)
        } else {
            val historyItem = mapOf(
                "text" to oldText,
                "editedAt" to currentTimeISO
            )
            if (isCaption) {
                updates["caption"] = newText
            } else {
                updates["text"] = newText
            }
            updates["editHistory"] = FieldValue.arrayUnion(historyItem)
        }

        db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES).document(message.id)
            .update(updates).await()
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
}