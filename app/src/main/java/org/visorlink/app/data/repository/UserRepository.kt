// data/repository/UserRepository.kt
package org.visorlink.app.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import org.visorlink.app.data.model.Sticker
import org.visorlink.app.data.model.UserProfile
import org.visorlink.app.utils.ChatDataCache
import org.visorlink.app.data.repository.FlagsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import java.io.File
import java.io.FileOutputStream
import android.util.Log
import com.google.firebase.Timestamp
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class UserRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val context: Context
) {
    private val currentUid: String get() = auth.currentUser?.uid ?: ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val memoryProfiles = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<UserProfile?>>()
    private val lastFetchTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun updateCachedProfile(uid: String, update: (UserProfile) -> UserProfile) {
        val current = memoryProfiles[uid]?.value
        if (current != null) {
            val updated = update(current)
            memoryProfiles[uid]?.value = updated
            scope.launch(Dispatchers.IO) {
                ChatDataCache.saveProfile(context, updated.copy(uid = uid))
            }
        } else {
            scope.launch(Dispatchers.IO) {
                val loaded = ChatDataCache.loadProfile(context, uid)
                if (loaded != null) {
                    val updated = update(loaded)
                    memoryProfiles.getOrPut(uid) { MutableStateFlow(null) }.value = updated
                    ChatDataCache.saveProfile(context, updated.copy(uid = uid))
                }
            }
        }
    }

    fun updateDiaryEnabled(uid: String, enabled: Boolean) {
        context.getSharedPreferences("visorlink_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("diary_enabled_$uid", enabled)
            .apply()

        updateCachedProfile(uid) { it.copy(diaryEnabled = enabled) }

        db.collection("users").document(uid).update("diaryEnabled", enabled)
    }

    fun currentUserFlow(): Flow<UserProfile?> = userProfileFlow(auth.currentUser?.uid ?: "")

    fun userProfileFlow(uid: String): Flow<UserProfile?> = channelFlow {
        if (uid.isEmpty()) { send(null); close(); return@channelFlow }

        val stateFlow = memoryProfiles.getOrPut(uid) {
            MutableStateFlow(null)
        }

        launch(Dispatchers.IO) {
            val cached = stateFlow.value ?: ChatDataCache.loadProfile(context, uid)
            if (cached != null) {
                stateFlow.value = cached
            }
        }

        val reg = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                val net = snap?.toObject(UserProfile::class.java)
                trySend(net)
                if (net != null) {
                    stateFlow.value = net
                    launch(Dispatchers.IO) { ChatDataCache.saveProfile(context, net) }
                }
            }
        awaitClose { reg.remove() }
    }

    /**
     * Есть ли документ `users/{uid}`. «Нет» — только по снимку **с сервера**: офлайн-кэш без
     * документа ещё не ответ (иначе офлайн-вход вёл бы на завершение регистрации). Ошибки
     * чтения ничего не решают и не отдаются.
     */
    fun profileExistsFlow(uid: String): Flow<Boolean> = callbackFlow {
        val reg = db.collection("users").document(uid)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snap, _ ->
                if (snap == null) return@addSnapshotListener
                if (snap.exists() || !snap.metadata.isFromCache) trySend(snap.exists())
            }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    suspend fun getUserProfile(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        val cached = memoryProfiles[uid]?.value ?: ChatDataCache.loadProfile(context, uid)
        
        if (cached != null) {
            launch {
                try {
                    val net = db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)
                    if (net != null) ChatDataCache.saveProfile(context, net)
                } catch (_: Exception) {}
            }
            return@withContext cached
        }
        try {
            val net = db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)
            if (net != null) ChatDataCache.saveProfile(context, net)
            return@withContext net
        } catch (e: Exception) {
            null
        }
    }

    suspend fun findUserByUsername(username: String): UserProfile? = try {
        val clean = username.lowercase().removePrefix("@").trim()
        val doc = db.collection("usernames").document(clean).get().await()
        if (!doc.exists()) null
        else {
            val uid = doc.getString("uid") ?: return null
            getUserProfile(uid)
        }
    } catch (e: Exception) { null }

    suspend fun ensureStaticImage(context: Context, imageUri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(imageUri)
        val isGif = mimeType == "image/gif" || imageUri.path?.endsWith(".gif", ignoreCase = true) == true
        
        val inputStream = context.contentResolver.openInputStream(imageUri)
            ?: throw IllegalArgumentException("Не удалось открыть файл изображения")
        val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (originalBitmap == null) {
            throw IllegalArgumentException("Не удалось декодировать изображение")
        }

        val outputStream = java.io.ByteArrayOutputStream()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            originalBitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 90, outputStream)
        } else {
            @Suppress("DEPRECATION")
            originalBitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, outputStream)
        }
        outputStream.toByteArray()
    }

    suspend fun uploadAvatarWithModeration(uid: String, imageBytes: ByteArray): Result<String> = runCatching {
        val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
        val storagePath = "users/$uid/avatar_${System.currentTimeMillis()}.webp"
        val fileRef = storage.reference.child(storagePath)

        // 1. Загрузка в Firebase Storage
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/webp")
            .build()
            
        fileRef.putBytes(imageBytes, metadata).await()
        val downloadUrl = fileRef.downloadUrl.await().toString()

        // 2. Вызов Cloud Function SafeSearch модерации
        val funcs = try {
            com.google.firebase.functions.FirebaseFunctions.getInstance("europe-west1")
        } catch (_: Exception) {
            functions
        }
        val data = mapOf(
            "storagePath" to storagePath,
            "downloadUrl" to downloadUrl
        )

        val callableResult = funcs
            .getHttpsCallable("setProfileAvatarWithSafeSearch")
            .call(data)
            .await()

        val responseMap = callableResult.data as? Map<*, *>
        val finalAvatarUrl = responseMap?.get("avatarUrl") as? String ?: downloadUrl
        finalAvatarUrl
    }

    suspend fun uploadAvatar(uri: Uri): String = withContext(Dispatchers.IO) {
        val uid = currentUid.ifEmpty { throw IllegalStateException("Пользователь не авторизован") }
        val imageBytes = ensureStaticImage(context, uri)
        val result = uploadAvatarWithModeration(uid, imageBytes)
        val avatarUrl = result.getOrThrow()

        db.collection("users").document(uid)
            .update("avatarUrl", avatarUrl, "updatedAt", FieldValue.serverTimestamp()).await()

        updateCachedProfile(uid) { it.copy(avatarUrl = avatarUrl) }
        return@withContext avatarUrl
    }

    suspend fun uploadFile(uri: Uri): String = withContext(Dispatchers.IO) {
        val uid = currentUid.ifEmpty { throw IllegalStateException("Пользователь не авторизован") }
        val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = if (mimeType.contains("png")) "png" else if (mimeType.contains("webp")) "webp" else "jpg"
        val storagePath = "users/$uid/customization_${System.currentTimeMillis()}.$ext"
        val fileRef = storage.reference.child(storagePath)

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Не удалось прочитать файл")

        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType(mimeType)
            .build()

        fileRef.putBytes(bytes, metadata).await()
        return@withContext fileRef.downloadUrl.await().toString()
    }

    suspend fun checkUsername(username: String): Pair<Boolean, String?> {
        val result = functions.getHttpsCallable("checkUsername")
            .call(mapOf("username" to username)).await()
        val data = result.data as Map<*, *>
        return Pair(data["available"] as Boolean, data["reason"] as? String)
    }

    suspend fun changeUsername(newUsername: String, displayName: String) {
        functions.getHttpsCallable("changeUsername")
            .call(mapOf("newUsername" to newUsername, "displayName" to displayName)).await()
    }

    suspend fun updateProfile(displayName: String, bio: String) {
        functions.getHttpsCallable("updateProfile")
            .call(mapOf("displayName" to displayName, "bio" to bio)).await()

        
        scope.launch {
            val profile = ChatDataCache.loadProfile(context, currentUid)
            if (profile != null) {
                ChatDataCache.saveProfile(context, profile.copy(displayName = displayName, bio = bio))
            }
        }
    }

    fun stickersFlow(uid: String): Flow<List<Sticker>> = callbackFlow {
        val reg = db.collection("users").document(uid).collection("stickers")
            .addSnapshotListener { snap, _ ->
                val stickers = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Sticker::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(stickers)
            }
        awaitClose { reg.remove() }
    }

    suspend fun deleteSticker(sticker: Sticker) {
        val uid = currentUid.ifEmpty { return }
        db.collection("users").document(uid)
            .collection("stickers").document(sticker.id).delete().await()
    }

    suspend fun saveFcmToken(token: String) {
        val uid = currentUid
        if (uid.isNotEmpty()) {
            try {
                db.collection("users").document(uid)
                    .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge()).await()
                android.util.Log.d("UserRepository", "FCM token synced to Firestore for $uid")
            } catch (e: Exception) {
                android.util.Log.w("UserRepository", "Direct Firestore FCM token sync failed: ${e.message}")
            }
        }
    }

    suspend fun removeFcmToken(token: String) {
        val uid = currentUid.ifEmpty { return }
        try {
            db.collection("users").document(uid)
                .update("fcmTokens", FieldValue.arrayRemove(token)).await()
            android.util.Log.d("UserRepository", "FCM token removed from Firestore")
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to remove FCM token from Firestore", e)
        }
    }

    fun clientStatusFlow(uid: String): Flow<Boolean> = callbackFlow {
        val ref = Firebase.database.getReference("users/$uid/clientStatus/isOfficial")
        val listener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val isOfficial = snap.getValue(Boolean::class.java) ?: true
                trySend(isOfficial)
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(true)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun buyPro(useTrial: Boolean) {
        functions.getHttpsCallable("buyProSubscription")
            .call(mapOf("useTrial" to useTrial))
            .await()
    }

    suspend fun updateShowStreak(show: Boolean) {
        db.collection("users").document(currentUid)
            .update("showStreak", show).await()
    }

    suspend fun updateCustomization(customization: Map<String, Any?>) {
        // Оптимистично: экран профиля и превью редактора обновляются сразу, не
        // дожидаясь снапшота Firestore (а в режиме бэкенда снапшота нет вовсе)
        updateCachedProfile(currentUid) { it.copy(customization = customization) }
        db.collection("users").document(currentUid)
            .update("customization", customization).await()
    }

    suspend fun updateIgnoreCustomizations(ignore: Boolean) {
        // У бэкенда пока нет поля ignoreCustomizations — там настройка живёт только локально
        updateCachedProfile(currentUid) { it.copy(ignoreCustomizations = ignore) }
        db.collection("users").document(currentUid)
            .update("ignoreCustomizations", ignore).await()
    }

    suspend fun generateTgCode(): String {
        val result = functions.getHttpsCallable("generateTgCode").call().await()
        return (result.data as Map<*, *>)["code"] as String
    }

    suspend fun updateDiaryReminders(enabled: Boolean, time: String) {
        val uid = currentUid.ifEmpty { return }
        db.collection("users").document(uid).update(
            "diaryRemindersEnabled", enabled,
            "diaryReminderTime", time
        ).await()

        updateCachedProfile(currentUid) {
            it.copy(diaryRemindersEnabled = enabled, diaryReminderTime = time)
        }
    }

    suspend fun unbindTelegram() {
        functions.getHttpsCallable("unbindTelegram").call().await()
    }

    // ── UGC Compliance: Block & Unblock Users ────────────────────────────────
    suspend fun blockUser(targetUid: String) {
        val uid = currentUid.ifEmpty { return }
        try {
            db.collection("users").document(uid)
                .update("blockedUserIds", FieldValue.arrayUnion(targetUid))
                .await()
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to block user in Firestore", e)
        }

        updateCachedProfile(uid) { current ->
            if (!current.blockedUserIds.contains(targetUid)) {
                current.copy(blockedUserIds = current.blockedUserIds + targetUid)
            } else {
                current
            }
        }
    }

    suspend fun unblockUser(targetUid: String) {
        val uid = currentUid.ifEmpty { return }
        try {
            db.collection("users").document(uid)
                .update("blockedUserIds", FieldValue.arrayRemove(targetUid))
                .await()
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to unblock user in Firestore", e)
        }

        updateCachedProfile(uid) { current ->
            current.copy(blockedUserIds = current.blockedUserIds - targetUid)
        }
    }
}
