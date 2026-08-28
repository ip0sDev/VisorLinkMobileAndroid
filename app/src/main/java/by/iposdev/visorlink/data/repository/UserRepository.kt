// data/repository/UserRepository.kt
package by.iposdev.visorlink.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import by.iposdev.visorlink.data.model.Sticker
import by.iposdev.visorlink.data.model.UserProfile
import by.iposdev.visorlink.data.remote.chat.UpdateProfileRequest
import by.iposdev.visorlink.data.remote.chat.UserDto
import by.iposdev.visorlink.data.remote.chat.VisorLinkApi
import by.iposdev.visorlink.utils.ChatDataCache
import by.iposdev.visorlink.utils.CdnService
import by.iposdev.visorlink.data.repository.FlagsRepository
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class UserRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val context: Context,
    private val api: VisorLinkApi,
    private val flagsRepository: FlagsRepository
) {
    private val currentUid get() = auth.currentUser!!.uid
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backendPrefs = context.getSharedPreferences("visorlink_backend_settings", Context.MODE_PRIVATE)

    private fun isProfileBackendEnabled(): Boolean {
        return flagsRepository.flags.value.isEnabled("test_backend_enabled") && 
                backendPrefs.getBoolean("use_backend_profile", false)
    }

    private fun isFirestoreDisabled(): Boolean {
        return flagsRepository.flags.value.isEnabled("test_backend_enabled") && 
                backendPrefs.getBoolean("disable_firestore_completely", false)
    }

    private fun UserDto.toDomain() = UserProfile(
        uid = id,
        username = username,
        displayName = displayName ?: username,
        bio = bio ?: "",
        avatarUrl = avatarUrl,
        online = isOnline,
        isAdmin = isAdmin,
        diaryEnabled = diaryEnabled,
        customization = customization ?: emptyMap()
    )

    fun currentUserFlow(): Flow<UserProfile?> = userProfileFlow(auth.currentUser?.uid ?: "")

    fun userProfileFlow(uid: String): Flow<UserProfile?> = channelFlow {
        if (uid.isEmpty()) { send(null); close(); return@channelFlow }

        val cacheUid = if (isProfileBackendEnabled()) uid + "_backend" else uid

        launch(Dispatchers.IO) {
            val cached = ChatDataCache.loadProfile(context, cacheUid)
            if (cached != null) send(cached)
        }

        if (isProfileBackendEnabled()) {
            try {
                val net = api.getUserProfile(uid).toDomain()
                send(net)
                launch(Dispatchers.IO) { ChatDataCache.saveProfile(context, net.copy(uid = cacheUid)) }
            } catch (e: Exception) {
                // If failed, channelFlow will still emit cached if any
            }
            // Backend doesn't support snapshot listener yet, so we just finish or keep open for cache
            // For now, let's just keep it open.
            awaitClose { }
            return@channelFlow
        }

        if (isFirestoreDisabled()) {
            awaitClose { }
            return@channelFlow
        }

        val reg = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                val net = snap?.toObject(UserProfile::class.java)
                trySend(net)
                if (net != null) launch(Dispatchers.IO) { ChatDataCache.saveProfile(context, net) }
            }
        awaitClose { reg.remove() }
    }

    suspend fun getUserProfile(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        val cacheUid = if (isProfileBackendEnabled()) uid + "_backend" else uid
        val cached = ChatDataCache.loadProfile(context, cacheUid)
        
        if (isProfileBackendEnabled()) {
            try {
                val net = api.getUserProfile(uid).toDomain()
                ChatDataCache.saveProfile(context, net.copy(uid = cacheUid))
                return@withContext net
            } catch (e: Exception) {
                return@withContext cached
            }
        }

        if (isFirestoreDisabled()) return@withContext cached

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

    suspend fun findUserByUsername(username: String): UserProfile? = if (isProfileBackendEnabled()) {
        try {
            val results = api.searchUsers(username)
            results.firstOrNull()?.toDomain()
        } catch (e: Exception) { null }
    } else try {
        val clean = username.lowercase().removePrefix("@").trim()
        val doc = db.collection("usernames").document(clean).get().await()
        if (!doc.exists()) null
        else {
            val uid = doc.getString("uid") ?: return null
            getUserProfile(uid)
        }
    } catch (e: Exception) { null }

    suspend fun uploadAvatar(uri: Uri): String = withContext(Dispatchers.IO) {
        val url = uploadFile(uri)
        if (!isFirestoreDisabled()) {
            db.collection("users").document(currentUid)
                .update("avatarUrl", url, "updatedAt", FieldValue.serverTimestamp()).await()
        }
        return@withContext url
    }

    suspend fun uploadFile(uri: Uri): String = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }

        // Determine mime type
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

        val mediaId = CdnService.uploadFile(tempFile, mimeType, isVault = false)
        tempFile.delete()

        return@withContext "${CdnService.BASE_URL}/p/$mediaId"
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
        if (isProfileBackendEnabled()) {
            try {
                api.updateProfile(UpdateProfileRequest(displayName = displayName, bio = bio))
            } catch (e: Exception) {}
        } else {
            functions.getHttpsCallable("updateProfile")
                .call(mapOf("displayName" to displayName, "bio" to bio)).await()
        }
        
        scope.launch {
            val cacheUid = if (isProfileBackendEnabled()) currentUid + "_backend" else currentUid
            val profile = ChatDataCache.loadProfile(context, cacheUid)
            if (profile != null) {
                ChatDataCache.saveProfile(context, profile.copy(displayName = displayName, bio = bio))
            }
        }
    }

    fun stickersFlow(uid: String): Flow<List<Sticker>> = callbackFlow {
        if (isFirestoreDisabled()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = db.collection("users").document(uid).collection("stickers")
            .addSnapshotListener { snap, _ ->
                val stickers = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Sticker::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(stickers)
            }
        awaitClose { reg.remove() }
    }

    suspend fun uploadSticker(uri: Uri, name: String) = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "sticker_${System.currentTimeMillis()}.webp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }

        val mediaId = CdnService.uploadFile(tempFile, "image/webp", isVault = false)
        tempFile.delete()

        val url = "${CdnService.BASE_URL}/p/$mediaId"
        db.collection("users").document(currentUid).collection("stickers").add(mapOf(
            "url" to url,
            "name" to name,
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun deleteSticker(sticker: Sticker) {
        db.collection("users").document(currentUid)
            .collection("stickers").document(sticker.id).delete().await()
    }

    suspend fun saveFcmToken(token: String) {
        functions.getHttpsCallable("saveFcmToken")
            .call(mapOf("token" to token)).await()
    }

    suspend fun removeFcmToken(token: String) {
        val uid = currentUid ?: return
        try {
            db.collection("users").document(uid)
                .update("fcmTokens", FieldValue.arrayRemove(token)).await()
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
        if (isFirestoreDisabled()) return
        db.collection("users").document(currentUid)
            .update("showStreak", show).await()
    }

    suspend fun updateCustomization(customization: Map<String, Any?>) {
        if (isProfileBackendEnabled()) {
            try {
                api.updateProfile(UpdateProfileRequest(customization = customization))
            } catch (e: Exception) {}
        } else {
            db.collection("users").document(currentUid)
                .update("customization", customization).await()
        }

        scope.launch {
            val cacheUid = if (isProfileBackendEnabled()) currentUid + "_backend" else currentUid
            val profile = ChatDataCache.loadProfile(context, cacheUid)
            if (profile != null) {
                ChatDataCache.saveProfile(context, profile.copy(customization = customization))
            }
        }
    }

    suspend fun updateIgnoreCustomizations(ignore: Boolean) {
        db.collection("users").document(currentUid)
            .update("ignoreCustomizations", ignore).await()
    }

    suspend fun generateTgCode(): String {
        val result = functions.getHttpsCallable("generateTgCode").call().await()
        return (result.data as Map<*, *>)["code"] as String
    }

    suspend fun unbindTelegram() {
        functions.getHttpsCallable("unbindTelegram").call().await()
    }
}