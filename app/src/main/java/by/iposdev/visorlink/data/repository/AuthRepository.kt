package by.iposdev.visorlink.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUid: String? get() = auth.currentUser?.uid

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun register(email: String, password: String, username: String) {
        val clean = username.lowercase().trim()
        require(clean.length >= 3) { "Username must be at least 3 characters" }
        require(Regex("^[a-zA-Z0-9_]+$").matches(clean)) { "Username can only contain letters, numbers and underscores" }

        val cred = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = cred.user!!.uid

        delay(500)

        val usernameDoc = db.collection("usernames").document(clean).get().await()
        if (usernameDoc.exists()) {
            cred.user!!.delete().await()
            throw Exception("Username already taken")
        }

        db.collection("users").document(uid).set(mapOf(
            "uid" to uid,
            "email" to email,
            "username" to clean,
            "displayName" to username,
            "bio" to "",
            "avatarUrl" to null,
            "online" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastSeen" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()

        db.collection("usernames").document(clean)
            .set(mapOf("uid" to uid)).await()
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    fun logout() = auth.signOut()
}