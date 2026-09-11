package by.iposdev.visorlink.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DraftManager(private val context: Context) {
    private val prefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "visorlink_drafts",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences("visorlink_drafts_fallback", Context.MODE_PRIVATE)
        }
    }

    private val _draftsFlow by lazy {
        val all = try {
            prefs.all.mapNotNull {
                val value = it.value as? String
                if (!value.isNullOrEmpty()) it.key to value else null
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
        MutableStateFlow(all)
    }
    val draftsFlow get() = _draftsFlow.asStateFlow()

    fun saveDraft(chatId: String, text: String) {
        if (text.isEmpty()) {
            clearDraft(chatId)
        } else {
            prefs.edit().putString(chatId, text).apply()
            _draftsFlow.value = _draftsFlow.value + (chatId to text)
        }
    }

    fun getDraft(chatId: String): String = prefs.getString(chatId, "") ?: ""

    fun clearDraft(chatId: String) {
        if (prefs.contains(chatId)) {
            prefs.edit().remove(chatId).apply()
            val mut = _draftsFlow.value.toMutableMap()
            mut.remove(chatId)
            _draftsFlow.value = mut
        }
    }
}