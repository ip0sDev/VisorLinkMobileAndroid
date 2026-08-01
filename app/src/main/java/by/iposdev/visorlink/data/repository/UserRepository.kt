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
import by.iposdev.visorlink.utils.ChatDataCache
import by.iposdev.visorlink.utils.CdnService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

class UserRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val context: Context
) {
    private val currentUid get() = auth.currentUser!!.uid

    fun currentUserFlow(): Flow<UserProfile?> = callbackFlow {
        val uid = auth.currentUser?.uid ?: run { trySend(null); close(); return@callbackFlow }

        launch(Dispatchers.IO) {
            val cached = ChatDataCache.loadProfile(context, uid)
            if (cached != null) trySend(cached)
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
        val cached = ChatDataCache.loadProfile(context, uid)
        if (cached != null) {
            launch {
                try {
                    val net = db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)
                    if (net != null) ChatDataCache.saveProfile(context, net)
                } catch (_: Exception) {}
            }
            return@withContext cached
        }
        val net = db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)
        if (net != null) ChatDataCache.saveProfile(context, net)
        return@withContext net
    }

    suspend fun findUserByUsername(username: String): UserProfile? {
        val clean = username.lowercase().removePrefix("@").trim()
        val doc = db.collection("usernames").document(clean).get().await()
        if (!doc.exists()) return null
        val uid = doc.getString("uid") ?: return null
        return getUserProfile(uid)
    }

    suspend fun uploadAvatar(uri: Uri): String = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }

        val mediaId = CdnService.uploadFile(tempFile, "image/jpeg", isVault = false)
        tempFile.delete()

        val url = "${CdnService.BASE_URL}/p/$mediaId"

        db.collection("users").document(currentUid)
            .update("avatarUrl", url, "updatedAt", FieldValue.serverTimestamp()).await()
        return@withContext url
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
}