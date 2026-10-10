package org.visorlink.app.data.repository

import android.util.Log
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

import org.visorlink.app.BuildConfig
import org.visorlink.app.data.auth.TfaCodeInfo
import org.visorlink.app.data.auth.TfaMethod

// ─── Auth state ───────────────────────────────────────────────────────────────

/** Отвязка Google без пароля оставила бы аккаунт без способа входа. */
class NoPasswordException : Exception("no-password")

sealed class AuthState {
    object NoSession : AuthState()
    data class Unverified(val user: FirebaseUser) : AuthState()
    data class Verified(val user: FirebaseUser) : AuthState()
}

class AuthRepository(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,          // inject via Koin
    private val flagsRepository: FlagsRepository? = null,
    private val sessions: SessionRepository? = null
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

    // ── Способы входа ─────────────────────────────────────────────────────────

    /** Провайдеры текущего аккаунта: `password`, `google.com`. */
    val providerIds: List<String>
        get() = auth.currentUser?.providerData?.map { it.providerId }.orEmpty()

    val hasPassword: Boolean get() = PROVIDER_PASSWORD in providerIds
    val hasGoogle: Boolean get() = PROVIDER_GOOGLE in providerIds

    /** Почта привязанного Google-аккаунта (или имя), `null` — Google не привязан. */
    val googleAccountLabel: String?
        get() = auth.currentUser?.providerData?.firstOrNull { it.providerId == PROVIDER_GOOGLE }
            ?.let { it.email ?: it.displayName ?: "" }

    /**
     * Вход (и начало регистрации) через Google. Возвращает `isNewUser` из `additionalUserInfo`:
     * у нового Google-аккаунта ещё нет профиля — регистрацию завершает [completeGoogleProfile].
     */
    suspend fun signInWithGoogle(idToken: String): Boolean {
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        return result.additionalUserInfo?.isNewUser == true
    }

    /**
     * Профиль Google-аккаунта: тот же `createUserProfile` с инвайтом. Почту подтверждать не нужно
     * (у Google `email_verified = true`); имя и фото сервер берёт из Google. При неверном инвайте
     * или занятом нике сервер Google-аккаунт **не удаляет** — пользователь правит данные и пробует снова.
     */
    suspend fun completeGoogleProfile(username: String, inviteCode: String) {
        functions.getHttpsCallable("createUserProfile")
            .call(mapOf("username" to username.trim(), "inviteCode" to inviteCode.trim()))
            .await()
    }

    /** «Отменить регистрацию»: удалить незавершённый Google-аккаунт, при ошибке — просто выйти. */
    suspend fun cancelGoogleSignup() {
        val user = auth.currentUser ?: return
        try { user.delete().await() } catch (_: Exception) { auth.signOut() }
        if (auth.currentUser != null) auth.signOut()
    }

    /** Привязка Google к текущему аккаунту; новый `auth_time` → перенос 2FA ([keepSession]). */
    suspend fun linkGoogle(idToken: String) {
        keepSession {
            val user = auth.currentUser ?: error("Not authenticated")
            user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        }
    }

    /** Отвязка Google. Без пароля не разрешается — аккаунт остался бы без способа входа. */
    suspend fun unlinkGoogle() {
        val user = auth.currentUser ?: error("Not authenticated")
        if (!hasPassword) throw NoPasswordException()
        user.unlink(PROVIDER_GOOGLE).await()
    }

    /**
     * «Задать пароль» аккаунту, созданному через Google: вход по почте станет вторым способом.
     * Если Firebase просит свежий вход — повторно выбираем Google-аккаунт ([googleIdToken]),
     * делаем `reauthenticate` и повторяем. Обе операции меняют `auth_time` → [keepSession].
     */
    suspend fun setPassword(newPassword: String, googleIdToken: suspend () -> String) {
        val email = auth.currentUser?.email ?: error("Not authenticated")
        val link: suspend () -> Unit = {
            keepSession {
                val user = auth.currentUser ?: error("Not authenticated")
                user.linkWithCredential(EmailAuthProvider.getCredential(email, newPassword)).await()
            }
        }
        try {
            link()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            if (!hasGoogle) throw e
            val token = googleIdToken()
            keepSession {
                val user = auth.currentUser ?: error("Not authenticated")
                user.reauthenticate(GoogleAuthProvider.getCredential(token, null)).await()
            }
            link()
        }
    }

    /** Смена пароля: повторный вход + `updatePassword` выдают новый токен — тоже через [keepSession]. */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser ?: error("Not authenticated")
        val email = user.email ?: error("No email")
        keepSession {
            user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
            (auth.currentUser ?: user).updatePassword(newPassword).await()
        }
    }

    /**
     * Привязка способа входа и повторный вход выдают токен с новым `auth_time` — для правил Firestore
     * это новая сессия без 2FA, и всё, что требует пройденной 2FA, начинает отвечать PERMISSION_DENIED.
     * Переносим подтверждение на новую сессию (`transferTfaSession` проверяет старый токен) и
     * регистрируем её в менеджере сессий. Веб — `keepSession` в AuthContext.jsx.
     */
    suspend fun <T> keepSession(action: suspend () -> T): T {
        // Старый токен — строго ДО операции: после неё он уже не тот
        val previous = auth.currentUser?.let { runCatching { it.getIdToken(false).await() }.getOrNull() }
        val result = action()
        val after = auth.currentUser ?: return result
        val previousToken = previous?.token ?: return result
        val newAuthTime = runCatching { after.getIdToken(true).await().authTimestamp }.getOrNull() ?: return result
        if (newAuthTime == previous.authTimestamp) return result
        try {
            functions.getHttpsCallable("transferTfaSession")
                .call(mapOf("previousIdToken" to previousToken))
                .await()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "2FA carry-over failed: ${(e as? FirebaseFunctionsException)?.details ?: e.message}")
        }
        // id сессии = auth_time: новая сессия появится в «Активных сессиях», отзыв проверяется по новому id
        runCatching { sessions?.register() }
        return result
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

    /**
     * Запрос кода. Идемпотентен: повтор тем же способом раньше чем через минуту не шлёт новый код,
     * а возвращает срок прежнего (`alreadySent`). Ошибки разбирает [org.visorlink.app.data.auth.TfaError].
     */
    suspend fun request2FA(method: TfaMethod): TfaCodeInfo {
        val res = functions
            .getHttpsCallable("request2FA")
            .call(mapOf("method" to method.id))
            .await()
        return TfaCodeInfo.parse(res.data, method, System.currentTimeMillis())
    }

    suspend fun verify2FA(code: String) {
        functions
            .getHttpsCallable("verify2FA")
            .call(mapOf("code" to code))
            .await()
    }

    /** Включение/выключение 2FA только с одноразовым кодом; при включении сервер сам авторизует текущую сессию. */
    suspend fun set2FAEnabled(enabled: Boolean, code: String) {
        functions
            .getHttpsCallable("set2FAEnabled")
            .call(mapOf("enabled" to enabled, "code" to code))
            .await()
    }

    /**
     * Коды 2FA в Telegram. При включении сервер сразу пишет в Telegram: если бот заблокирован или
     * не запущен — `unavailable` / `send-failed`; без привязки — `no-telegram`.
     */
    suspend fun setTfaTelegram(enabled: Boolean) {
        functions
            .getHttpsCallable("setTfaTelegram")
            .call(mapOf("enabled" to enabled))
            .await()
    }

    /** Отвязка Telegram; сервер сам выключает и `tfaTelegram`. */
    suspend fun unbindTelegram() {
        functions.getHttpsCallable("unbindTelegram").call().await()
    }

    /**
     * Белый список 2FA: `users/{uid}/authorized_sessions/{auth_time}`. Отдаёт только ответы
     * **сервера**: «документа нет» из офлайн-кэша — ещё не ответ, его пропускаем. Документ может
     * появиться позже (код ввели, сессию перенесли) — подписка остаётся живой и сообщит об этом.
     * Ошибка чтения закрывает поток с исключением.
     */
    fun sessionAuthorizedFlow(uid: String, authTime: String): Flow<Boolean> = callbackFlow {
        val reg = FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("authorized_sessions").document(authTime)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap == null || (!snap.exists() && snap.metadata.isFromCache)) return@addSnapshotListener
                trySend(snap.exists())
            }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

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
    /** Своя сессия завершается на сервере (таймаут ~2 с), затем signOut() — сеть не должна задерживать выход. */
    fun logout() {
        val s = sessions
        if (s == null || auth.currentUser == null) { auth.signOut(); return }
        kotlinx.coroutines.MainScope().launch {
            try { kotlinx.coroutines.withTimeoutOrNull(2_000) { s.terminateCurrent() } } catch (_: Exception) {}
            s.stopWatching()
            auth.signOut()
        }
    }

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

    companion object {
        private const val TAG = "AuthRepository"
        const val PROVIDER_PASSWORD = "password"
        const val PROVIDER_GOOGLE = "google.com"
    }
}
