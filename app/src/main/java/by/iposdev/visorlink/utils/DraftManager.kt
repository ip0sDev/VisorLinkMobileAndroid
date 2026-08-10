package by.iposdev.visorlink.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DraftManager(context: Context) {
    private val prefs = context.getSharedPreferences("visorlink_drafts", Context.MODE_PRIVATE)
    private val _draftsFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val draftsFlow = _draftsFlow.asStateFlow()

    init {
        // При старте выгружаем все сохраненные черновики в память для мгновенного доступа в UI
        val all = prefs.all.mapNotNull {
            val value = it.value as? String
            if (!value.isNullOrEmpty()) it.key to value else null
        }.toMap()
        _draftsFlow.value = all
    }

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