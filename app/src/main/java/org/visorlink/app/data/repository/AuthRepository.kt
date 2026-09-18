package org.visorlink.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

import org.visorlink.app.data.remote.chat.SyncUserRequest
import org.visorlink.app.data.remote.chat.VisorLinkApi
import org.visorlink.app.data.repository.FlagsRepository

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

    fun getCurrentAuthState(): AuthState {
        val user = auth.currentUser
        return when {
            user == null         -> AuthState.NoSession
            user.isEmailVerified -> AuthState.Verified(user)
            else                 -> AuthState.Unverified(user)
        }
    }

    // Emits on every auth state change AND on every ID token refresh.
    // AuthStateListener fires on login/logout but NOT when emailVerified flips.
    // IdTokenListener fires after user.reload() + getIdToken(true), catching
    // the verification case that AuthStateListener misses.
    val authState: Flow<AuthState> = callbackFlow {
        // ИСПРАВЛЕНИЕ: Используем классические анонимные классы (object : Interface)
        // вместо лямбд. Это обходит баг компилятора Kotlin с UnknownInitialization.
        val authListener = object : FirebaseAuth.AuthStateListener {
            override fun onAuthStateChanged(firebaseAuth: FirebaseAuth) {
                trySend(getCurrentAuthState())
            }
        }

        val tokenListener = object : FirebaseAuth.IdTokenListener {
            override fun onIdTokenChanged(firebaseAuth: FirebaseAuth) {
                trySend(getCurrentAuthState())
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
        suspend fun cleanupOrphanAccount() {
            var deleted = false
            for (attempt in 1..3) {
                try {
                    user.delete().await()
                    deleted = true
                    break
                } catch (_: Exception) {
                    kotlinx.coroutines.delay(500L * attempt)
                }
            }
            if (!deleted) {
                // If account deletion fails, sign out so the app doesn't stay in an orphaned uninitialized state
                try { auth.signOut() } catch (_: Exception) {}
            }
        }

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
            cleanupOrphanAccount()
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

    suspend fun adminListAccessRequests(status: String = "pending"): List<org.visorlink.app.data.model.AccessRequest> {
        val res = functions
            .getHttpsCallable("adminListAccessRequests")
            .call(mapOf("status" to status))
            .await()
        val data = res.data as? Map<*, *> ?: return emptyList()
        val list = data["requests"] as? List<Map<String, Any?>> ?: return emptyList()
        return list.map { item ->
            org.visorlink.app.data.model.AccessRequest(
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

    // ── Account Deletion (Google Play Compliance) ────────────────────────────
    suspend fun deleteAccount(password: String) {
        val user = auth.currentUser ?: throw IllegalStateException("Пользователь не авторизован")
        val email = user.email ?: throw IllegalStateException("У пользователя отсутствует email")

        // 1. Повторная аутентификация пользователя для подтверждения намерения
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).await()

        // 2. Серверный вызов каскадного удаления аккаунта
        try {
            functions.getHttpsCallable("deleteAccount").call().await()
        } catch (e: Exception) {
            android.util.Log.w("AuthRepository", "Cloud function deleteAccount failed or not present: ${e.message}")
        }

        // 3. Удаление пользователя из Firebase Auth
        user.delete().await()

        // 4. Завершение сессии
        auth.signOut()
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