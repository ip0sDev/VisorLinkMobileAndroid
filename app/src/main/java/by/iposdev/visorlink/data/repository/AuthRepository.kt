package by.iposdev.visorlink.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

import by.iposdev.visorlink.data.remote.chat.SyncUserRequest
import by.iposdev.visorlink.data.remote.chat.VisorLinkApi
import by.iposdev.visorlink.data.repository.FlagsRepository

// ─── Auth state ───────────────────────────────────────────────────────────────

sealed class AuthState {
    object NoSession : AuthState()
    data class Unverified(val user: FirebaseUser) : AuthState()
    data class Verified(val user: FirebaseUser) : AuthState()
}

class AuthRepository(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,          // inject via Koin
    private val api: VisorLinkApi? = null,
    private val flagsRepository: FlagsRepository? = null
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
    suspend fun register(email: String, password: String, username: String, inviteCode: String? = null) {
        val clean = username.lowercase().trim()
        require(clean.length in 3..32) { "Username must be 3–32 characters" }
        require(Regex("^[a-zA-Z0-9_]+\$").matches(clean)) {
            "Username can only contain letters, numbers and underscores"
        }

        // Step 1 — create Auth account
        val cred = auth.createUserWithEmailAndPassword(email, password).await()
        val user = cred.user ?: error("Auth account creation returned null user")

        // Step 2 — create profile
        if (flagsRepository?.isBackendV2Enabled() == true && api != null) {
            try {
                api.syncUser(SyncUserRequest(username = username.trim()))
            } catch (e: Exception) {
                runCatching { user.delete().await() }
                throw e
            }
        } else {
            try {
                val profileParams = mutableMapOf<String, Any>("username" to username.trim())
                if (!inviteCode.isNullOrBlank()) {
                    profileParams["inviteCode"] = inviteCode.trim()
                }
                functions
                    .getHttpsCallable("createUserProfile")
                    .call(profileParams)
                    .await()
            } catch (e: Exception) {
                // CF already deleted the Auth account if username is taken.
                // Attempt local cleanup as a safety net for other errors.
                runCatching { user.delete().await() }
                throw mapFunctionsError(e)
            }
        }

        // Step 3 — send verification email
        user.sendEmailVerification().await()
        // Caller (ViewModel) should now route to VerifyEmailScreen
    }

    // ── Login ─────────────────────────────────────────────────────────────────
    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
        if (flagsRepository?.isBackendV2Enabled() == true && api != null) {
            try {
                api.syncUser(SyncUserRequest())
            } catch (_: Exception) {}
        }
    }


    suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
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

    // ── Two-Factor Authentication ─────────────────────────────────────────────

    suspend fun request2FA(method: String) {
        functions
            .getHttpsCallable("request2FA")
            .call(mapOf("method" to method))
            .await()
    }

    suspend fun verify2FA(code: String) {
        functions
            .getHttpsCallable("verify2FA")
            .call(mapOf("code" to code))
            .await()
    }

    suspend fun getAuthTime(): String? {
        val user = auth.currentUser ?: return null
        val result: GetTokenResult = user.getIdToken(false).await()
        return result.claims["auth_time"]?.toString()
    }

    // ── Invite-Only Registration ─────────────────────────────────────────────

    suspend fun checkRegistrationCode(code: String): Boolean {
        val res = functions
            .getHttpsCallable("checkRegistrationCode")
            .call(mapOf("code" to code.trim()))
            .await()
        val data = res.data as? Map<*, *> ?: return false
        return data["valid"] == true
    }

    suspend fun requestAccess(email: String, username: String, note: String): String {
        val res = functions
            .getHttpsCallable("requestAccess")
            .call(mapOf(
                "email" to email.trim(),
                "username" to username.trim(),
                "note" to note.trim()
            ))
            .await()
        val data = res.data as? Map<*, *> ?: return ""
        return (data["requestId"] as? String) ?: ""
    }

    suspend fun adminListAccessRequests(status: String = "pending"): List<by.iposdev.visorlink.data.model.AccessRequest> {
        val res = functions
            .getHttpsCallable("adminListAccessRequests")
            .call(mapOf("status" to status))
            .await()
        val data = res.data as? Map<*, *> ?: return emptyList()
        val list = data["requests"] as? List<Map<String, Any?>> ?: return emptyList()
        return list.map { item ->
            by.iposdev.visorlink.data.model.AccessRequest(
                requestId = item["requestId"] as? String ?: "",
                email = item["email"] as? String ?: "",
                username = item["username"] as? String ?: "",
                note = item["note"] as? String ?: "",
                status = item["status"] as? String ?: "pending"
            )
        }
    }

    suspend fun adminApproveAccessRequest(requestId: String) {
        functions
            .getHttpsCallable("adminApproveAccessRequest")
            .call(mapOf("requestId" to requestId))
            .await()
    }

    suspend fun adminRejectAccessRequest(requestId: String) {
        functions
            .getHttpsCallable("adminRejectAccessRequest")
            .call(mapOf("requestId" to requestId))
            .await()
    }

    suspend fun adminCreateRegistrationCode(note: String = ""): String {
        val res = functions
            .getHttpsCallable("adminCreateRegistrationCode")
            .call(mapOf("note" to note))
            .await()
        val data = res.data as? Map<*, *> ?: return ""
        return (data["code"] as? String) ?: ""
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
            "invalid-invite"              in msg -> Exception("Invalid or already used invite code.")
            "invite-required"             in msg -> Exception("A valid invite code is required to register.")
            else                                 -> e
        }
    }
}