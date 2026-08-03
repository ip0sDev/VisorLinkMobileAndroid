package by.iposdev.visorlink.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// ─── Auth state ───────────────────────────────────────────────────────────────

sealed class AuthState {
    object NoSession : AuthState()
    data class Unverified(val user: FirebaseUser) : AuthState()
    data class Verified(val user: FirebaseUser) : AuthState()
}

class AuthRepository(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions          // inject via Koin
) {
    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUid: String? get() = auth.currentUser?.uid

    // Emits on every auth state change AND on every ID token refresh.
    // AuthStateListener fires on login/logout but NOT when emailVerified flips.
    // IdTokenListener fires after user.reload() + getIdToken(true), catching
    // the verification case that AuthStateListener misses.
    val authState: Flow<AuthState> = callbackFlow {
        fun currentState(): AuthState {
            val user = auth.currentUser
            return when {
                user == null           -> AuthState.NoSession
                user.isEmailVerified   -> AuthState.Verified(user)
                else                   -> AuthState.Unverified(user)
            }
        }

        // ИСПРАВЛЕНИЕ: Используем классические анонимные классы (object : Interface)
        // вместо лямбд. Это обходит баг компилятора Kotlin с UnknownInitialization.
        val authListener = object : FirebaseAuth.AuthStateListener {
            override fun onAuthStateChanged(firebaseAuth: FirebaseAuth) {
                trySend(currentState())
            }
        }

        val tokenListener = object : FirebaseAuth.IdTokenListener {
            override fun onIdTokenChanged(firebaseAuth: FirebaseAuth) {
                trySend(currentState())
            }
        }

        auth.addAuthStateListener(authListener)
        auth.addIdTokenListener(tokenListener)

        awaitClose {
            auth.removeAuthStateListener(authListener)
            auth.removeIdTokenListener(tokenListener)
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────
    /**
     * Per guideline §2.1:
     *  1. createUserWithEmailAndPassword
     *  2. Call createUserProfile Cloud Function (writes /users + /usernames atomically)
     *  3. sendEmailVerification
     *
     * Client no longer writes to Firestore directly — security rules require
     * email_verified == true, so we delegate to the CF (Admin SDK bypasses rules).
     */
    suspend fun register(email: String, password: String, username: String) {
        val clean = username.lowercase().trim()
        require(clean.length in 3..32) { "Username must be 3–32 characters" }
        require(Regex("^[a-zA-Z0-9_]+\$").matches(clean)) {
            "Username can only contain letters, numbers and underscores"
        }

        // Step 1 — create Auth account
        val cred = auth.createUserWithEmailAndPassword(email, password).await()
        val user = cred.user ?: error("Auth account creation returned null user")

        // Step 2 — create Firestore profile via Cloud Function
        // CF validates username, writes /users/{uid} and /usernames/{username} atomically.
        // If username is taken the CF deletes the Auth account and throws already-exists.
        try {
            functions
                .getHttpsCallable("createUserProfile")
                .call(mapOf("username" to username.trim()))
                .await()
        } catch (e: Exception) {
            // CF already deleted the Auth account if username is taken.
            // Attempt local cleanup as a safety net for other errors.
            runCatching { user.delete().await() }
            throw mapFunctionsError(e)
        }

        // Step 3 — send verification email
        user.sendEmailVerification().await()
        // Caller (ViewModel) should now route to VerifyEmailScreen
    }

    // ── Login ─────────────────────────────────────────────────────────────────
    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    // ── Email verification ────────────────────────────────────────────────────
    /**
     * Reload the current user and force-refresh the ID token so Firestore
     * picks up the new email_verified claim. Returns true when verified.
     */
    suspend fun reloadAndCheckVerified(): Boolean {
        val user = auth.currentUser ?: return false
        user.reload().await()
        if (user.isEmailVerified) {
            user.getIdToken(true).await()   // force-refresh JWT
            return true
        }
        return false
    }

    suspend fun resendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()?.await()
            ?: error("No authenticated user")
    }

    // ── Logout ────────────────────────────────────────────────────────────────
    fun logout() = auth.signOut()

    // ── Error mapping ─────────────────────────────────────────────────────────
    // Maps Firebase / CF error codes to user-friendly messages per guideline §8.
    private fun mapFunctionsError(e: Exception): Exception {
        val msg = e.message ?: return e
        return when {
            "already-exists"              in msg -> Exception("Username already taken. Please choose a different one.")
            "invalid-argument"            in msg -> Exception("Invalid username. Use 3–32 letters, numbers or underscores.")
            "unauthenticated"             in msg -> Exception("Session error. Please try again.")
            "email-already-in-use"        in msg -> Exception("This email is already registered. Try logging in.")
            "weak-password"               in msg -> Exception("Password is too short (minimum 6 characters).")
            "too-many-requests"           in msg -> Exception("Too many attempts. Please wait a moment and try again.")
            "network-request-failed"      in msg -> Exception("Network error. Check your connection and try again.")
            else                                 -> e
        }
    }
}