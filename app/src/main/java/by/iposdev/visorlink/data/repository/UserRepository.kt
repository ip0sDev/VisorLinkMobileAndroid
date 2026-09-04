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
import android.util.Log
import com.google.firebase.Timestamp
import java.util.Date
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
    private val currentUid: String get() = auth.currentUser?.uid ?: ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backendPrefs = context.getSharedPreferences("visorlink_backend_settings", Context.MODE_PRIVATE)

    private fun isProfileBackendEnabled(): Boolean {
        val serverV2 = flagsRepository.isBackendV2Enabled()
        val serverFlag = flagsRepository.flags.value.isEnabled("test_backend_enabled")
        val userSetting = backendPrefs.getBoolean("use_backend_profile", false)
        return serverV2 || (serverFlag && userSetting)
    }

    private fun isFirestoreDisabled(): Boolean {
        return flagsRepository.isBackendV2Enabled() || (flagsRepository.flags.value.isEnabled("test_backend_enabled") && 
                backendPrefs.getBoolean("disable_firestore_completely", false))
    }


    private val memoryProfiles = java.util.concurrent.ConcurrentHashMap<String, MutableStateFlow<UserProfile?>>()
    private val lastFetchTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun updateCachedProfile(uid: String, update: (UserProfile) -> UserProfile) {
        val cacheUid = if (isProfileBackendEnabled()) uid + "_backend" else uid
        val current = memoryProfiles[uid]?.value
        if (current != null) {
            val updated = update(current)
            memoryProfiles[uid]?.value = updated
            scope.launch(Dispatchers.IO) {
                ChatDataCache.saveProfile(context, updated.copy(uid = cacheUid))
            }
        } else {
            scope.launch(Dispatchers.IO) {
                val loaded = ChatDataCache.loadProfile(context, cacheUid)
                if (loaded != null) {
                    val updated = update(loaded)
                    memoryProfiles.getOrPut(uid) { MutableStateFlow(null) }.value = updated
                    ChatDataCache.saveProfile(context, updated.copy(uid = cacheUid))
                }
            }
        }
    }

    private fun UserDto.toDomain(): UserProfile {
        val custMap = customization
        val diary = diaryEnabled
            ?: (custMap["diaryEnabled"] as? Boolean)
            ?: context.getSharedPreferences("visorlink_settings", Context.MODE_PRIVATE).getBoolean("diary_enabled_$id", true)

        return UserProfile(
            uid = id,
            username = username,
            displayName = displayName?.ifEmpty { username } ?: username,
            bio = bio ?: "",
            avatarUrl = avatarUrl,
            online = isOnline,
            isAdmin = isAdmin,
            isBot = isBot,
            botBadge = botBadge ?: "unverified",
            bits = bits,
            streak = if (showStreak) streak else 0,
            showStreak = showStreak,
            proUntil = proUntil?.let { Timestamp(Date(it)) },
            trialUsed = trialUsed,
            tfaEnabled = tfaEnabled,
            registeredViaOfficialClient = registeredViaOfficialClient,
            diaryEnabled = diary,
            customization = custMap,
            acceptedAt = acceptedAt?.let { Timestamp(Date(it)) },
            acceptedVersion = acceptedVersion ?: (custMap["acceptedVersion"] as? String)
        )
    }

    fun updateDiaryEnabled(uid: String, enabled: Boolean) {
        context.getSharedPreferences("visorlink_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("diary_enabled_$uid", enabled)
            .apply()

        updateCachedProfile(uid) { it.copy(diaryEnabled = enabled) }

        if (isProfileBackendEnabled()) {
            scope.launch {
                try {
                    api.updateProfile(UpdateProfileRequest(
                        customization = mapOf("diaryEnabled" to enabled)
                    ))
                } catch (e: Exception) {
                    Log.w("UserRepository", "Failed to sync diaryEnabled to backend: ${e.message}")
                }
            }
        } else if (!isFirestoreDisabled()) {
            db.collection("users").document(uid).update("diaryEnabled", enabled)
        }
    }

    fun currentUserFlow(): Flow<UserProfile?> = userProfileFlow(auth.currentUser?.uid ?: "")

    fun userProfileFlow(uid: String): Flow<UserProfile?> = channelFlow {
        if (uid.isEmpty()) { send(null); close(); return@channelFlow }

        val cacheUid = if (isProfileBackendEnabled()) uid + "_backend" else uid
        val stateFlow = memoryProfiles.getOrPut(uid) {
            MutableStateFlow(null)
        }

        launch(Dispatchers.IO) {
            val cached = stateFlow.value ?: ChatDataCache.loadProfile(context, cacheUid)
            if (cached != null) {
                stateFlow.value = cached
            }
        }

        if (isProfileBackendEnabled()) {
            val now = System.currentTimeMillis()
            val lastFetch = lastFetchTimes[uid] ?: 0L
            if (now - lastFetch > 10_000L || stateFlow.value == null) {
                lastFetchTimes[uid] = now
                launch(Dispatchers.IO) {
                    try {
                        val net = api.getUserProfile(uid).toDomain()
                        stateFlow.value = net
                        ChatDataCache.saveProfile(context, net.copy(uid = cacheUid))
                    } catch (e: Exception) {
                        Log.w("UserRepository", "Failed to fetch profile for $uid: ${e.message}")
                    }
                }
            }

            stateFlow.collect { profile ->
                send(profile)
            }
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
                if (net != null) {
                    stateFlow.value = net
                    launch(Dispatchers.IO) { ChatDataCache.saveProfile(context, net) }
                }
            }
        awaitClose { reg.remove() }
    }

    suspend fun getUserProfile(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        val cacheUid = if (isProfileBackendEnabled()) uid + "_backend" else uid
        val cached = memoryProfiles[uid]?.value ?: ChatDataCache.loadProfile(context, cacheUid)
        
        if (isProfileBackendEnabled()) {
            val now = System.currentTimeMillis()
            val lastFetch = lastFetchTimes[uid] ?: 0L
            if (cached != null && (now - lastFetch <= 10_000L)) {
                return@withContext cached
            }
            return@withContext try {
                val net = api.getUserProfile(uid).toDomain()
                lastFetchTimes[uid] = now
                memoryProfiles.getOrPut(uid) { MutableStateFlow(null) }.value = net
                ChatDataCache.saveProfile(context, net.copy(uid = cacheUid))
                net
            } catch (e: Exception) {
                cached
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
        if (isProfileBackendEnabled()) {
            return try {
                val res = api.checkUsername(username)
                Pair(res.available, null)
            } catch (e: Exception) {
                Pair(false, e.message)
            }
        }
        val result = functions.getHttpsCallable("checkUsername")
            .call(mapOf("username" to username)).await()
        val data = result.data as Map<*, *>
        return Pair(data["available"] as Boolean, data["reason"] as? String)
    }

    suspend fun changeUsername(newUsername: String, displayName: String) {
        if (isProfileBackendEnabled()) {
            api.updateUsername(by.iposdev.visorlink.data.remote.chat.UpdateUsernameRequest(username = newUsername, displayName = displayName))
            return
        }
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
        if (isProfileBackendEnabled()) {
            try {
                val packs = api.getMyStickers()
                val stickers = packs.flatMap { pack ->
                    pack.stickers?.map { s ->
                        Sticker(id = s.id ?: s.url, url = s.url, name = s.name ?: "")
                    } ?: emptyList()
                }
                trySend(stickers)
            } catch (_: Exception) {
                trySend(emptyList())
            }
            awaitClose { }
            return@callbackFlow
        }

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
        if (!isFirestoreDisabled()) {
            val uid = currentUid.ifEmpty { return@withContext }
            db.collection("users").document(uid).collection("stickers").add(mapOf(
                "url" to url,
                "name" to name,
                "createdAt" to FieldValue.serverTimestamp()
            )).await()
        }
    }

    suspend fun deleteSticker(sticker: Sticker) {
        if (!isFirestoreDisabled()) {
            val uid = currentUid.ifEmpty { return }
            db.collection("users").document(uid)
                .collection("stickers").document(sticker.id).delete().await()
        }
    }

    suspend fun saveFcmToken(token: String) {
        val uid = currentUid
        if (uid.isNotEmpty() && !isFirestoreDisabled()) {
            try {
                db.collection("users").document(uid)
                    .update("fcmTokens", FieldValue.arrayUnion(token)).await()
            } catch (e: Exception) {
                android.util.Log.w("UserRepository", "Direct Firestore FCM token sync skipped or failed: ${e.message}")
            }
        }

        if (isProfileBackendEnabled()) {
            try {
                api.saveFcmToken(by.iposdev.visorlink.data.remote.chat.FcmTokenRequest(token = token))
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "Failed to save FCM token to backend", e)
            }
            return
        }

        try {
            functions.getHttpsCallable("saveFcmToken")
                .call(mapOf("token" to token)).await()
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to save FCM token via Cloud Functions", e)
        }
    }

    suspend fun removeFcmToken(token: String) {
        val uid = currentUid.ifEmpty { return }
        if (isProfileBackendEnabled() || isFirestoreDisabled()) {
            return
        }
        try {
            db.collection("users").document(uid)
                .update("fcmTokens", FieldValue.arrayRemove(token)).await()
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to remove FCM token from Firestore", e)
        }
    }

    fun clientStatusFlow(uid: String): Flow<Boolean> = callbackFlow {
        if (isProfileBackendEnabled()) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
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
        if (isProfileBackendEnabled()) {
            try {
                val res = api.buyPro(by.iposdev.visorlink.data.remote.chat.BuyProRequest(useTrial = useTrial))
                val newProUntil = res.proUntil?.let { Timestamp(Date(it)) }
                val newBits = res.bits
                updateCachedProfile(currentUid) { current ->
                    current.copy(
                        proUntil = newProUntil ?: current.proUntil,
                        bits = newBits ?: current.bits,
                        trialUsed = if (useTrial) true else current.trialUsed
                    )
                }
                try {
                    val net = api.getUserProfile(currentUid).toDomain()
                    val cacheUid = currentUid + "_backend"
                    memoryProfiles.getOrPut(currentUid) { MutableStateFlow(null) }.value = net
                    ChatDataCache.saveProfile(context, net.copy(uid = cacheUid))
                } catch (_: Exception) {}
                return
            } catch (e: retrofit2.HttpException) {
                val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                val errorMsg = errorBody?.let {
                    try { org.json.JSONObject(it).optString("error", it) } catch (_: Exception) { it }
                } ?: e.message()
                throw Exception(errorMsg)
            }
        }
        functions.getHttpsCallable("buyProSubscription")
            .call(mapOf("useTrial" to useTrial))
            .await()
    }

    suspend fun updateShowStreak(show: Boolean) {
        if (isProfileBackendEnabled()) {
            try {
                api.updateProfile(UpdateProfileRequest(showStreak = show))
            } catch (_: Exception) {}
            return
        }
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
        if (!isFirestoreDisabled()) {
            db.collection("users").document(currentUid)
                .update("ignoreCustomizations", ignore).await()
        }
    }

    suspend fun generateTgCode(): String {
        if (isProfileBackendEnabled()) {
            val res = api.generateTelegramCode()
            return res.code
        }
        val result = functions.getHttpsCallable("generateTgCode").call().await()
        return (result.data as Map<*, *>)["code"] as String
    }

    suspend fun updateTfaEnabled(enabled: Boolean) {
        if (isProfileBackendEnabled()) {
            try {
                api.updateProfile(UpdateProfileRequest(tfaEnabled = enabled))
            } catch (_: Exception) {}
        } else if (!isFirestoreDisabled()) {
            val uid = currentUid.ifEmpty { return }
            db.collection("users").document(uid).update("tfaEnabled", enabled).await()
        }
        updateCachedProfile(currentUid) { it.copy(tfaEnabled = enabled) }
    }

    suspend fun updateDiaryReminders(enabled: Boolean, time: String) {
        if (isProfileBackendEnabled()) {
            try {
                api.updateProfile(UpdateProfileRequest(
                    customization = mapOf(
                        "diaryRemindersEnabled" to enabled,
                        "diaryReminderTime" to time
                    )
                ))
            } catch (_: Exception) {}
        } else if (!isFirestoreDisabled()) {
            val uid = currentUid.ifEmpty { return }
            db.collection("users").document(uid).update(
                "diaryRemindersEnabled", enabled,
                "diaryReminderTime", time
            ).await()
        }
        updateCachedProfile(currentUid) {
            it.copy(diaryRemindersEnabled = enabled, diaryReminderTime = time)
        }
    }

    suspend fun unbindTelegram() {
        if (isProfileBackendEnabled()) {
            api.unbindTelegram()
            return
        }
        functions.getHttpsCallable("unbindTelegram").call().await()
    }
}