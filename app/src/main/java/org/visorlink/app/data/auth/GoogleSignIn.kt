package org.visorlink.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import org.visorlink.app.R

/** Пользователь закрыл окно выбора Google-аккаунта — показывать нечего. */
class GoogleSignInCancelled : Exception("cancelled")

/**
 * Google ID-токен через Credential Manager для **кнопки** «Войти через Google».
 *
 * Используется [GetSignInWithGoogleOption] — поток Google для явной кнопки: свой экран выбора
 * аккаунта, он есть всегда. Раньше был [GetGoogleIdOption] — «шторка» One Tap, она для
 * автоподсказки, а не для кнопки: на части устройств (Samsung с Samsung Pass как менеджером
 * паролей, Android 14+) шторка зависала и закрывалась с «Запрос на вход был отменён
 * приложением», а после пары закрытий Google включает «остывание» и отменяет запросы сразу.
 * Шторка остаётся запасным путём, если экрана кнопки на устройстве нет.
 *
 * Окно открывается поверх Activity: из [context] достаётся сама Activity, а не обёртка
 * (локаль, тема), иначе системный выбор может открыться в отдельной задаче и потерять ответ.
 */
suspend fun requestGoogleIdToken(context: Context): String {
    val activity = context.findActivity() ?: context
    val manager = CredentialManager.create(activity)
    val clientId = activity.getString(R.string.default_web_client_id)
    val button = GetSignInWithGoogleOption.Builder(clientId).build()
    val response = try {
        manager.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(button).build())
    } catch (e: GetCredentialCancellationException) {
        throw GoogleSignInCancelled()
    } catch (e: GetCredentialException) {
        if (e is NoCredentialException) throw e
        // Экрана кнопки нет или он не поднялся — пробуем шторку с выбором аккаунта
        val sheet = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        try {
            manager.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(sheet).build())
        } catch (cancel: GetCredentialCancellationException) {
            throw GoogleSignInCancelled()
        }
    }
    val credential = response.credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    error("Unexpected credential type: ${credential.type}")
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Ошибка входа / привязки Google → текст (веб `googleAuthErrors.js`). `null` — пользователь сам
 * закрыл окно, показывать нечего.
 */
object GoogleAuthErrors {

    /** Код ошибки Firebase Auth без префикса, как в вебе (`account-exists-with-different-credential`). */
    internal fun codeOf(t: Throwable): String? = when (t) {
        is FirebaseAuthException -> t.errorCode.removePrefix("ERROR_").lowercase().replace('_', '-')
        else -> null
    }

    /** Ресурс текста; `null` — показывать нечего (отмена). [fallback] — когда причина неизвестна. */
    fun messageRes(t: Throwable): Int? {
        if (t is GoogleSignInCancelled || t is GetCredentialCancellationException) return null
        if (t is FirebaseNetworkException || t is java.io.IOException) return R.string.auth_google_network
        if (t is NoCredentialException) return R.string.auth_google_no_account
        return when (codeOf(t)) {
            "account-exists-with-different-credential", "email-already-in-use" -> R.string.auth_google_account_exists
            "credential-already-in-use", "provider-already-linked" -> R.string.auth_google_already_linked
            "operation-not-allowed" -> R.string.auth_google_unavailable
            "network-request-failed" -> R.string.auth_google_network
            else -> if (t is FirebaseAuthUserCollisionException) R.string.auth_google_account_exists else R.string.auth_google_failed
        }
    }

    fun isRecentLoginRequired(t: Throwable): Boolean = t is FirebaseAuthRecentLoginRequiredException
}
