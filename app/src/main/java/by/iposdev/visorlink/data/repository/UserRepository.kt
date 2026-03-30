package by.iposdev.visorlink.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import by.iposdev.visorlink.data.model.Sticker
import by.iposdev.visorlink.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database

class UserRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val functions: FirebaseFunctions
) {
    private val currentUid get() = auth.currentUser!!.uid

    fun currentUserFlow(): Flow<UserProfile?> = callbackFlow {
        val uid = auth.currentUser?.uid ?: run { trySend(null); close(); return@callbackFlow }
        val reg = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ -> trySend(snap?.toObject(UserProfile::class.java)) }
        awaitClose { reg.remove() }
    }

    suspend fun getUserProfile(uid: String): UserProfile? =
        db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)

    suspend fun findUserByUsername(username: String): UserProfile? {
        val clean = username.lowercase().removePrefix("@").trim()
        val doc = db.collection("usernames").document(clean).get().await()
        if (!doc.exists()) return null
        val uid = doc.getString("uid") ?: return null
        return getUserProfile(uid)
    }

    suspend fun uploadAvatar(uri: Uri): String {
        val ref = storage.reference.child("avatars/$currentUid/avatar")
        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()
        db.collection("users").document(currentUid)
            .update("avatarUrl", url, "updatedAt", FieldValue.serverTimestamp()).await()
        return url
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

    suspend fun uploadSticker(uri: Uri, name: String) {
        val fileName = "${System.currentTimeMillis()}_${uri.lastPathSegment}"
        val storagePath = "stickers/$currentUid/$fileName"
        val ref = storage.reference.child(storagePath)
        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()
        db.collection("users").document(currentUid).collection("stickers").add(mapOf(
            "url" to url, "name" to name,
            "storagePath" to storagePath,
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun deleteSticker(sticker: Sticker) {
        storage.reference.child(sticker.storagePath).delete().await()
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
                // Если значения нет, по умолчанию считаем официальным (чтобы не пугать зря)
                val isOfficial = snap.getValue(Boolean::class.java) ?: true
                trySend(isOfficial)
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(true)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
}}