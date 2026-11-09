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
    private val context: Context,
    private val contentKeyManager: org.visorlink.app.utils.ContentKeyManager
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

    /**
     * Включение PIN-защиты (CK-схема, TODO §14.3): только флаг и таймаут блокировки.
     * Серверный pinHash больше НЕ пишется — проверка PIN выполняется локально
     * разворотом PIN-обёртки ключа контента (ContentKeyManager).
     */
    suspend fun enablePinProtection(uid: String) {
        db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid)
            .set(
                mapOf(
                    "pinEnabled" to true,
                    "pinHash" to FieldValue.delete(),
                    "lockTimeout" to 5,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
    }

    /**
     * Legacy-проверка PIN по серверному хэшу — только для немигрированных (v1) пользователей.
     * После миграции (cryptoV2) хэш удалён, проверка выполняется локально через PIN-обёртку.
     */
    suspend fun verifyPin(uid: String, enteredPin: String): Boolean = try {
        val snap = db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid).get().await()
        val storedHash = snap.getString("pinHash") ?: ""
        val isValid = verifyPinHash(enteredPin, uid, storedHash)
        if (isValid && !storedHash.startsWith("pbkdf2:")) {
            // Прозрачная миграция старого SHA-256 хэша на стойкий PBKDF2
            val hash = hashPin(enteredPin, uid)
            db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid)
                .set(mapOf("pinHash" to hash, "pinEnabled" to true, "lockTimeout" to 5, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
        }
        isValid
    } catch (e: Exception) { false }

    /**
     * Отметка миграции на CK-схему (TODO §14.4): cryptoV2=true, серверный pinHash удаляется.
     * Вызывается после успешной пере-шифровки контента.
     */
    suspend fun markCryptoV2(uid: String) {
        db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid)
            .set(
                mapOf(
                    "cryptoV2" to true,
                    "pinHash" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
    }

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

    /**
     * Полное стирание «Избранного» и дневника — escape-hatch при забытом recovery-коде
     * (TODO §14.4): медиа-блобы и все документы удаляются, настройки сбрасываются
     * (pinEnabled=false, cryptoV2=false), локальные обёртки ключа домена и recovery-обёртка
     * стираются. После этого пользователь может включить PIN заново — миграционный флоу
     * создаст новый CK и новый recovery-код.
     */
    suspend fun wipeAllSavedData(
        uid: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val storage = FirebaseStorage.getInstance()
        val col = db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES)

        // Предвыборка всех документов — честный total для прогресса
        val docs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
        var cursor: com.google.firebase.firestore.DocumentSnapshot? = null
        while (true) {
            var query = col.orderBy("createdAt", Query.Direction.ASCENDING).limit(400)
            cursor?.let { query = query.startAfter(it) }
            val snap = query.get().await()
            if (snap.isEmpty) break
            docs.addAll(snap.documents)
            cursor = snap.documents.last()
            if (snap.size() < 400) break
        }

        val total = docs.size
        var done = 0
        var batch = db.batch()
        var ops = 0
        for (doc in docs) {
            // Медиа-блоб в saved_messages/{uid}/ — владелец может удалять (правила storage)
            val url = doc.getString("url")
            if (!url.isNullOrBlank()) {
                try { storage.getReferenceFromUrl(url).delete().await() } catch (_: Exception) {}
            }
            batch.delete(doc.reference)
            ops++
            if (ops == 400) { // батчи по 400 — ниже лимита Firestore
                batch.commit().await()
                batch = db.batch()
                ops = 0
            }
            done++
            onProgress(done, total)
        }
        if (ops > 0) batch.commit().await()

        // Сброс настроек: правила пропускают update с этим набором полей
        db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid)
            .set(
                mapOf(
                    "pinEnabled" to false,
                    "pinHash" to FieldValue.delete(),
                    "cryptoV2" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

        // Локальные обёртки CK домена, lockout-ключи + recovery-обёртка на сервере
        contentKeyManager.deleteRecoveryWrap(uid, org.visorlink.app.utils.ContentKeyManager.DOMAIN_SAVED)
        contentKeyManager.wipeDomain(uid, org.visorlink.app.utils.ContentKeyManager.DOMAIN_SAVED)

        // Кэш расшифрованных файлов больше не нужен
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("decrypted_") || file.name.startsWith("voice_") || file.name.startsWith("upload_") || file.name.startsWith("orig_")) {
                file.delete()
            }
        }
    }

    /**
     * Снятие PIN-защиты — НОВАЯ семантика (TODO §14.4): данные НЕ удаляются.
     * Снимается только гейт: PIN- и bio-обёртки ключа контента удаляются, контент
     * остаётся зашифрованным и читаемым через свободную Keystore-обёртку.
     */
    suspend fun disablePin(uid: String, contentKey: SecretKey) {
        contentKeyManager.disablePin(uid, org.visorlink.app.utils.ContentKeyManager.DOMAIN_SAVED, contentKey)
        db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid)
            .update(mapOf("pinEnabled" to false, "pinHash" to FieldValue.delete(), "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun updateLockTimeout(uid: String, minutes: Int) {
        db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid).set(mapOf("lockTimeout" to minutes, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }

    fun messagesFlow(uid: String, key: SecretKey?, includeDiary: Boolean = false, legacyKey: SecretKey? = null): Flow<List<SavedMessage>> = callbackFlow {
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
                        if (msg.encrypted == true && key == null && legacyKey == null) return@mapNotNull null
                        decryptIfNeeded(msg, key, legacyKey)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { reg.remove() }
    }

    fun diaryFlow(uid: String, key: SecretKey?, legacyKey: SecretKey? = null): Flow<List<SavedMessage>> = callbackFlow {
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
                        doc.toObject(SavedMessage::class.java)?.copy(id = doc.id)?.let { msg -> decryptIfNeeded(msg, key, legacyKey) }
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
                put("keyVersion", 2L)
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
        // Сжимаем до шифрования: зашифрованные байты уже не сжать
        val compressed = org.visorlink.app.utils.ImageCompressor.compress(context, uri)
        val fileName = "${System.currentTimeMillis()}_image.${compressed.extension}"
        val tempOriginalFile = File(context.cacheDir, "orig_$fileName")
        val tempUploadFile = File(context.cacheDir, "upload_$fileName")

        tempOriginalFile.writeBytes(compressed.bytes)

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
                put("keyVersion", 2L)
                put("fileKeyVersion", 2L)
            }

            if (!caption.isNullOrBlank()) {
                if (key != null) {
                    // Подпись шифруется со СВОИМ IV (captionIv): историческое шифрование подписи
                    // с IV файла делало её нерасшифровываемой (TODO §14.4)
                    val (ct, captionIv) = encryptText(caption, key)
                    put("encryptedCaption", ct)
                    put("captionIv", captionIv)
                    if (fileIv == null) { put("encrypted", true); put("iv", captionIv); put("keyVersion", 2L) }
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

    suspend fun getDecryptedMediaFile(message: SavedMessage, key: SecretKey?, legacyKey: SecretKey? = null): File? = withContext(Dispatchers.IO) {
        if (message.encrypted != true || message.iv == null) return@withContext null
        // Медиа-блоб имеет свою версию ключа (текст и файл могут мигрировать в разные проходы)
        val effective = if (message.fileKeyVersion == 2L) key else (legacyKey ?: key)
        if (effective == null) return@withContext null
        val mediaUrl = message.url ?: return@withContext null

        val decryptedFile = File(context.cacheDir, "decrypted_${message.id}_${message.fileName ?: "media"}")

        if (decryptedFile.exists() && decryptedFile.length() > 0) {
            return@withContext decryptedFile
        }

        try {
            val connection = java.net.URL(mediaUrl).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            decryptStreamToFile(connection.inputStream, decryptedFile, message.iv, effective)

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
                updates["captionIv"] = ivNew
            } else {
                updates["encryptedText"] = encNewText
                updates["iv"] = ivNew
            }
            updates["keyVersion"] = 2L
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

    private fun decryptIfNeeded(msg: SavedMessage, key: SecretKey?, legacyKey: SecretKey?): SavedMessage {
        // v2-подпись может существовать и при encrypted != true (мигрированная подпись открытого медиа)
        if (msg.encrypted != true && msg.encryptedText == null && msg.encryptedCaption == null) return msg
        // v2 — ключ контента (CK); v1 — legacy-ключ от PIN (окно миграции)
        val effective = if (msg.keyVersion == 2L) key else (legacyKey ?: key)
        if (effective == null) return msg
        val plaintext = msg.encryptedText?.let { ct ->
            val v = msg.iv ?: return@let null
            decryptText(ct, v, effective)
        }
        val caption = msg.encryptedCaption?.let { ct ->
            // v2: у подписи свой IV; v1: исторически общий с текстом/файлом
            val v = msg.captionIv ?: msg.iv ?: return@let null
            decryptText(ct, v, effective)
        }
        return msg.copy(text = plaintext ?: msg.text, caption = caption ?: msg.caption)
    }

    // ─── Миграция на ключ контента v2 (TODO §14.4) ────────────────────────────

    /**
     * Пере-шифровка текстов/подписей/истории под ключ контента (CK). Ничего не удаляет;
     * документ, который не удалось расшифровать legacy-ключом, пропускается без изменений.
     * Возвращает число мигрированных документов.
     */
    suspend fun migrateContentToV2(
        uid: String, legacyKey: SecretKey?, ck: SecretKey,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        var migrated = 0
        var processed = 0
        val col = db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES)
        // Общий объём — для прогресса; если count() недоступен, останется 0 (UI покажет indeterminate)
        val total = try {
            col.count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
        } catch (_: Exception) { 0L }
        var cursor: com.google.firebase.firestore.DocumentSnapshot? = null
        while (true) {
            var query = col.orderBy("createdAt", Query.Direction.ASCENDING).limit(200)
            cursor?.let { query = query.startAfter(it) }
            val snap = query.get().await()
            if (snap.isEmpty) break
            val batch = db.batch()
            var dirty = false
            for (doc in snap.documents) {
                cursor = doc
                processed++
                val msg = try {
                    doc.toObject(SavedMessage::class.java)?.copy(id = doc.id)
                } catch (_: Exception) { null } ?: continue
                if (msg.keyVersion == 2L) continue
                val updates = buildV2Updates(msg, legacyKey, ck) ?: continue
                batch.update(doc.reference, updates)
                migrated++
                dirty = true
            }
            if (dirty) batch.commit().await()
            onProgress(processed, total.toInt())
            if (snap.size() < 200) break
        }
        migrated
    }

    /**
     * Пере-шифровка медиа-блобов (download → decrypt v1 → encrypt CK → upload →
     * обновление документа → удаление старого блоба). Долгая операция — вызывать в фоне.
     * Файл, который не удалось пере-шифровать, остаётся в v1 до следующего прохода.
     */
    suspend fun migrateMediaToV2(
        uid: String, legacyKey: SecretKey, ck: SecretKey,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        var migrated = 0
        val col = db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES)
        val storage = FirebaseStorage.getInstance()

        // Предвыборка кандидатов — честный total для прогресса
        val candidates = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
        var scanCursor: com.google.firebase.firestore.DocumentSnapshot? = null
        while (true) {
            var scan = col.orderBy("createdAt", Query.Direction.ASCENDING).limit(200)
            scanCursor?.let { scan = scan.startAfter(it) }
            val snap = scan.get().await()
            if (snap.isEmpty) break
            for (doc in snap.documents) {
                scanCursor = doc
                val msg = try { doc.toObject(SavedMessage::class.java)?.copy(id = doc.id) } catch (_: Exception) { null } ?: continue
                if (msg.url == null || msg.iv == null) continue
                if (msg.fileKeyVersion == 2L) continue
                if (msg.type != MessageType.IMAGE && msg.type != MessageType.VOICE) continue
                candidates.add(doc)
            }
            if (snap.size() < 200) break
        }

        val total = candidates.size
        var processed = 0
        for (doc in candidates) {
                val msg = try { doc.toObject(SavedMessage::class.java)?.copy(id = doc.id) } catch (_: Exception) { null } ?: run { processed++; onProgress(migrated, total); continue }
                val url = msg.url ?: run { processed++; onProgress(migrated, total); continue }
                val oldIv = msg.iv ?: run { processed++; onProgress(migrated, total); continue }
                try {
                    val tempPlain = File(context.cacheDir, "migr_${System.currentTimeMillis()}_plain")
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 60000
                    decryptStreamToFile(conn.inputStream, tempPlain, oldIv, legacyKey)

                    val newName = "v2_${msg.fileName ?: "media_${System.currentTimeMillis()}"}"
                    val tempEnc = File(context.cacheDir, "migr_${System.currentTimeMillis()}_enc")
                    val newIv = encryptFile(tempPlain, tempEnc, ck)

                    val ref = storage.reference.child("saved_messages/$uid/$newName")
                    ref.putFile(android.net.Uri.fromFile(tempEnc)).await()
                    val newUrl = ref.downloadUrl.await().toString()

                    doc.reference.update(
                        mapOf("url" to newUrl, "iv" to newIv, "fileKeyVersion" to 2L)
                    ).await()
                    migrated++

                    // Старый блоб (зашифрован слабым v1-ключом) удаляем — правила разрешают владельцу
                    try { storage.getReferenceFromUrl(url).delete().await() } catch (_: Exception) {}
                    tempPlain.delete()
                    tempEnc.delete()
                } catch (_: Exception) {
                    // Оставляем как есть — повторится при следующем проходе миграции
                }
            processed++
            onProgress(migrated, total)
        }
        migrated
    }

    /**
     * Обратная миграция v2 → v1 (kill-switch выключили — TODO §14.4).
     * pin != null: контент пере-шифруется прежним v1-ключом (PBKDF2 от PIN);
     * pin == null (PIN снят): контент переводится в открытый вид — как в прежней схеме.
     * Медиа-блобы перекачиваются (decrypt CK → encrypt v1/upload открытый), старые v2-блобы удаляются.
     * Возвращает число обработанных документов.
     */
    suspend fun rollbackToV1(
        uid: String, ck: SecretKey, pin: String?,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val storage = FirebaseStorage.getInstance()
        val col = db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES)
        val v1Key = pin?.let { deriveKey(it, uid) }

        // Предвыборка всех документов (личный объём — допустимо)
        val docs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
        var cursor: com.google.firebase.firestore.DocumentSnapshot? = null
        while (true) {
            var query = col.orderBy("createdAt", Query.Direction.ASCENDING).limit(200)
            cursor?.let { query = query.startAfter(it) }
            val snap = query.get().await()
            if (snap.isEmpty) break
            for (doc in snap.documents) {
                cursor = doc
                val msg = try { doc.toObject(SavedMessage::class.java)?.copy(id = doc.id) } catch (_: Exception) { null } ?: continue
                if (msg.keyVersion == 2L) docs.add(doc)
            }
            if (snap.size() < 200) break
        }

        val total = docs.size
        var processed = 0
        for (doc in docs) {
            val msg = try { doc.toObject(SavedMessage::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
            if (msg == null) { processed++; onProgress(processed, total); continue }

            val updates = mutableMapOf<String, Any?>()
            updates["keyVersion"] = FieldValue.delete()

            // ── Текст ──
            if (msg.encryptedText != null) {
                val iv = msg.iv
                val plain = if (iv != null) decryptText(msg.encryptedText, iv, ck) else null
                if (plain != null && v1Key != null) {
                    val (ct, newIv) = encryptText(plain, v1Key)
                    updates["encryptedText"] = ct
                    updates["iv"] = newIv
                } else if (plain != null) {
                    updates["text"] = plain
                    updates["encryptedText"] = FieldValue.delete()
                    updates["encrypted"] = FieldValue.delete()
                    updates["iv"] = FieldValue.delete()
                } else {
                    // Не расшифровали — оставляем v2 как есть, повторится при следующем откате
                    processed++; onProgress(processed, total); continue
                }
            } else if (!msg.text.isNullOrEmpty()) {
                // Открытый текст не трогаем
            }

            // ── Подпись медиа: в v1 открытая (зашифрованная подпись в v1 фактически не читалась) ──
            if (msg.encryptedCaption != null) {
                val iv = msg.captionIv ?: msg.iv
                val plain = if (iv != null) decryptText(msg.encryptedCaption, iv, ck) else null
                if (plain != null) {
                    updates["caption"] = plain
                    updates["encryptedCaption"] = FieldValue.delete()
                    updates["captionIv"] = FieldValue.delete()
                }
            } else if (!msg.caption.isNullOrEmpty()) {
                // Уже открытая — не трогаем
            }

            // ── Медиа-блоб ──
            if (msg.url != null && msg.fileKeyVersion == 2L && msg.iv != null &&
                (msg.type == MessageType.IMAGE || msg.type == MessageType.VOICE)
            ) {
                try {
                    val tempPlain = File(context.cacheDir, "rb_${System.currentTimeMillis()}_plain")
                    val conn = java.net.URL(msg.url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 60000
                    decryptStreamToFile(conn.inputStream, tempPlain, msg.iv, ck)

                    val newName = "v1_${msg.fileName ?: "media_${System.currentTimeMillis()}"}"
                    val uploadFile: File
                    var newIv: String? = null
                    if (v1Key != null) {
                        val tempEnc = File(context.cacheDir, "rb_${System.currentTimeMillis()}_enc")
                        newIv = encryptFile(tempPlain, tempEnc, v1Key)
                        uploadFile = tempEnc
                        tempPlain.delete()
                    } else {
                        uploadFile = tempPlain
                    }

                    val ref = storage.reference.child("saved_messages/$uid/$newName")
                    ref.putFile(android.net.Uri.fromFile(uploadFile)).await()
                    updates["url"] = ref.downloadUrl.await().toString()
                    if (newIv != null) updates["iv"] = newIv else updates["iv"] = FieldValue.delete()
                    updates["fileKeyVersion"] = FieldValue.delete()

                    try { storage.getReferenceFromUrl(msg.url).delete().await() } catch (_: Exception) {}
                    uploadFile.delete()
                } catch (_: Exception) {
                    // Блоб не перекачался — поля текста всё равно откатываем; блоб повторится
                }
            }

            try { doc.reference.update(updates).await() } catch (_: Exception) {}
            processed++
            onProgress(processed, total)
        }
        processed
    }

    /** Откат флага схемы (TODO §14.4): cryptoV2=false; при известном PIN восстанавливаем v1-проверку (pinHash). */
    suspend fun markCryptoV1(uid: String, pin: String?) {
        val data = buildMap<String, Any?> {
            put("cryptoV2", false)
            put("updatedAt", FieldValue.serverTimestamp())
            if (pin != null) put("pinHash", hashPin(pin, uid))
        }
        db.collection(FirestoreCollections.SAVED_MESSAGES_SETTINGS).document(uid)
            .set(data, SetOptions.merge()).await()
    }

    private fun buildV2Updates(msg: SavedMessage, legacyKey: SecretKey?, ck: SecretKey): Map<String, Any?>? {
        val updates = mutableMapOf<String, Any?>()
        updates["keyVersion"] = 2L

        if (msg.encryptedText != null) {
            val iv = msg.iv ?: return null
            val plain = decryptText(msg.encryptedText, iv, legacyKey ?: return null) ?: return null
            val (ct, newIv) = encryptText(plain, ck)
            updates["encryptedText"] = ct
            updates["iv"] = newIv
        } else if (!msg.text.isNullOrEmpty()) {
            // Старые открытые записи тоже шифруем (plaintext исчезает из базы)
            val (ct, newIv) = encryptText(msg.text, ck)
            updates["encryptedText"] = ct
            updates["iv"] = newIv
            updates["encrypted"] = true
            updates["text"] = FieldValue.delete()
        }

        if (msg.encryptedCaption != null) {
            // v1: подпись расшифровывалась общим iv (историческое поведение)
            val iv = msg.iv ?: return null
            val plain = decryptText(msg.encryptedCaption, iv, legacyKey ?: return null) ?: return null
            val (ct, civ) = encryptText(plain, ck)
            updates["encryptedCaption"] = ct
            updates["captionIv"] = civ
        } else if (!msg.caption.isNullOrEmpty()) {
            val (ct, civ) = encryptText(msg.caption, ck)
            updates["encryptedCaption"] = ct
            updates["captionIv"] = civ
            updates["caption"] = FieldValue.delete()
        }

        if (msg.editHistory.isNotEmpty()) {
            val lk = legacyKey ?: return null
            val newHistory = mutableListOf<Map<String, Any>>()
            for (h in msg.editHistory) {
                val hCt = h["encryptedText"] as? String
                val hIv = h["iv"] as? String
                if (hCt != null && hIv != null) {
                    val plain = decryptText(hCt, hIv, lk) ?: return null
                    val (ct, iv) = encryptText(plain, ck)
                    newHistory.add(h + mapOf("encryptedText" to ct, "iv" to iv))
                } else {
                    newHistory.add(h)
                }
            }
            updates["editHistory"] = newHistory
        }
        // Медиа-блоб не трогаем: iv документа может быть IV файла — его обновляет migrateMediaToV2
        return updates
    }

    // ─── Ротация ключа контента (сброс recovery-кода, TODO §14.4) ─────────────

    /**
     * Пере-шифровка ВСЕХ документов keyVersion==2 под новый CK: тексты (encryptedText/iv;
     * подпись через captionIv||iv; editHistory) и медиа-блобы fileKeyVersion==2
     * (download → decrypt oldCK → encrypt newCK → upload нового имени с префиксом v2_ →
     * update url/iv → удаление старого блоба). keyVersion остаётся 2 — правила
     * безопасности пропускают такой diff как isCryptoV2Migration.
     *
     * Идемпотентна: документ, уже пере-шифрованный под newCK при прерванной ротации,
     * определяется (не читается старым ключом, читается новым) и пропускается.
     * Бросает исключение, если что-то не удалось пере-шифровать, — ротация остаётся
     * незавершённой и возобновляется при следующем входе по PIN.
     */
    suspend fun rotateContentKey(
        uid: String, oldCK: SecretKey, newCK: SecretKey,
        onProgress: (stage: String, done: Int, total: Int) -> Unit = { _, _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val storage = FirebaseStorage.getInstance()
        val col = db.collection(FirestoreCollections.SAVED_MESSAGES).document(uid).collection(FirestoreCollections.MESSAGES)

        // Предвыборка кандидатов — честный total для прогресса
        val textDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
        val mediaDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
        var cursor: com.google.firebase.firestore.DocumentSnapshot? = null
        while (true) {
            var query = col.orderBy("createdAt", Query.Direction.ASCENDING).limit(200)
            cursor?.let { query = query.startAfter(it) }
            val snap = query.get().await()
            if (snap.isEmpty) break
            for (doc in snap.documents) {
                cursor = doc
                val msg = try { doc.toObject(SavedMessage::class.java)?.copy(id = doc.id) } catch (_: Exception) { null } ?: continue
                if (msg.keyVersion == 2L &&
                    (msg.encryptedText != null || msg.encryptedCaption != null || msg.editHistory.isNotEmpty())
                ) textDocs.add(doc)
                if (msg.url != null && msg.iv != null && msg.fileKeyVersion == 2L &&
                    (msg.type == MessageType.IMAGE || msg.type == MessageType.VOICE)
                ) mediaDocs.add(doc)
            }
            if (snap.size() < 200) break
        }

        var failed = 0

        // ── Тексты ──
        val stageTexts = "Пере-шифровка текстов"
        val tTotal = textDocs.size
        for ((idx, doc) in textDocs.withIndex()) {
            val msg = try { doc.toObject(SavedMessage::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
            if (msg != null) {
                val updates = rotateTextDoc(msg, oldCK, newCK)
                if (updates == null) {
                    failed++ // не читается ни старым, ни новым ключом
                } else if (updates.isNotEmpty()) {
                    try { doc.reference.update(updates).await() } catch (_: Exception) { failed++ }
                }
            }
            onProgress(stageTexts, idx + 1, tTotal)
        }

        // ── Медиа-блобы ──
        val stageMedia = "Пере-шифровка медиа"
        val mTotal = mediaDocs.size
        for ((idx, doc) in mediaDocs.withIndex()) {
            val msg = try { doc.toObject(SavedMessage::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
            val url = msg?.url
            val blobIv = msg?.iv
            if (msg != null && url != null && blobIv != null) {
                try {
                    val tempPlain = File(context.cacheDir, "rot_${System.currentTimeMillis()}_plain")
                    var underOld = true
                    try {
                        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 15000
                        conn.readTimeout = 60000
                        decryptStreamToFile(conn.inputStream, tempPlain, blobIv, oldCK)
                    } catch (_: Exception) {
                        underOld = false
                        tempPlain.delete()
                    }
                    if (!underOld) {
                        // Не читается старым ключом — возможно, уже под новым (прерванная ротация)
                        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 15000
                        conn.readTimeout = 60000
                        decryptStreamToFile(conn.inputStream, tempPlain, blobIv, newCK)
                    }

                    val newName = "v2_${msg.fileName ?: "media_${System.currentTimeMillis()}"}"
                    val tempEnc = File(context.cacheDir, "rot_${System.currentTimeMillis()}_enc")
                    val newIv = encryptFile(tempPlain, tempEnc, newCK)

                    val ref = storage.reference.child("saved_messages/$uid/$newName")
                    ref.putFile(android.net.Uri.fromFile(tempEnc)).await()
                    val newUrl = ref.downloadUrl.await().toString()

                    doc.reference.update(mapOf("url" to newUrl, "iv" to newIv, "fileKeyVersion" to 2L)).await()

                    // Старый блоб удаляем — владелец может (правила storage)
                    try { storage.getReferenceFromUrl(url).delete().await() } catch (_: Exception) {}
                    tempPlain.delete()
                    tempEnc.delete()
                } catch (_: Exception) {
                    failed++ // повторится при возобновлении ротации
                }
            }
            onProgress(stageMedia, idx + 1, mTotal)
        }

        if (failed > 0) {
            throw IllegalStateException("Не удалось пере-шифровать $failed элементов — попробуйте ещё раз")
        }
        textDocs.size + mediaDocs.size
    }

    /**
     * Пере-шифровка полей текста одного документа под новый CK. null — документ
     * не читается ни старым, ни новым ключом (уже потерян); пустая карта — пропустить.
     */
    private fun rotateTextDoc(msg: SavedMessage, oldCK: SecretKey, newCK: SecretKey): Map<String, Any?>? {
        val updates = mutableMapOf<String, Any?>()
        var undecryptable = false

        if (msg.encryptedText != null) {
            val iv = msg.iv
            val plain = iv?.let { decryptText(msg.encryptedText, it, oldCK) }
            if (plain != null) {
                val (ct, newIv) = encryptText(plain, newCK)
                updates["encryptedText"] = ct
                updates["iv"] = newIv
            } else if (iv == null || decryptText(msg.encryptedText, iv, newCK) == null) {
                undecryptable = true
            }
        } else if (!msg.text.isNullOrEmpty()) {
            // Открытый текст при keyVersion==2 не встречается, но на всякий случай не трогаем
        }

        if (msg.encryptedCaption != null) {
            // v2: у подписи свой IV (captionIv); fallback на iv документа — исторические документы
            val iv = msg.captionIv ?: msg.iv
            val plain = iv?.let { decryptText(msg.encryptedCaption, it, oldCK) }
            if (plain != null) {
                val (ct, civ) = encryptText(plain, newCK)
                updates["encryptedCaption"] = ct
                updates["captionIv"] = civ
            } else if (iv == null || decryptText(msg.encryptedCaption, iv, newCK) == null) {
                undecryptable = true
            }
        }

        if (msg.editHistory.isNotEmpty()) {
            val newHistory = mutableListOf<Map<String, Any>>()
            var changed = false
            for (h in msg.editHistory) {
                val hCt = h["encryptedText"] as? String
                val hIv = h["iv"] as? String
                val plain = if (hCt != null && hIv != null) decryptText(hCt, hIv, oldCK) else null
                if (plain != null) {
                    val (ct, iv) = encryptText(plain, newCK)
                    newHistory.add(h + mapOf("encryptedText" to ct, "iv" to iv))
                    changed = true
                } else {
                    if (hCt != null && hIv != null && decryptText(hCt, hIv, newCK) == null) {
                        undecryptable = true
                    }
                    newHistory.add(h)
                }
            }
            if (changed) updates["editHistory"] = newHistory
        }

        return if (undecryptable) null else updates
    }
}