package org.visorlink.app.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DraftManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debounceJobs = ConcurrentHashMap<String, Job>()
    private val memoryCache = ConcurrentHashMap<String, String>()

    private val prefs by lazy {
        context.applicationContext.getSharedPreferences("visorlink_drafts_v2", Context.MODE_PRIVATE)
    }

    private val _draftsFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val draftsFlow = _draftsFlow.asStateFlow()

    init {
        scope.launch {
            try {
                val all = prefs.all.mapNotNull {
                    val value = it.value as? String
                    if (!value.isNullOrEmpty()) {
                        memoryCache[it.key] = value
                        it.key to value
                    } else null
                }.toMap()
                _draftsFlow.value = all
            } catch (_: Exception) {}
        }
    }

    fun saveDraft(chatId: String, text: String) {
        if (text.isEmpty()) {
            clearDraft(chatId)
            return
        }

        val previous = memoryCache[chatId]
        if (previous == text) return

        memoryCache[chatId] = text
        _draftsFlow.value = _draftsFlow.value + (chatId to text)

        debounceJobs[chatId]?.cancel()
        debounceJobs[chatId] = scope.launch {
            delay(400)
            try {
                prefs.edit().putString(chatId, text).apply()
            } catch (_: Exception) {}
            debounceJobs.remove(chatId)
        }
    }

    fun getDraft(chatId: String): String {
        return memoryCache[chatId] ?: run {
            val loaded = prefs.getString(chatId, "") ?: ""
            if (loaded.isNotEmpty()) {
                memoryCache[chatId] = loaded
            }
            loaded
        }
    }

    fun clearDraft(chatId: String) {
        debounceJobs.remove(chatId)?.cancel()
        val existed = memoryCache.remove(chatId) != null
        val hadInFlow = _draftsFlow.value.containsKey(chatId)
        if (existed || hadInFlow) {
            val mut = _draftsFlow.value.toMutableMap()
            mut.remove(chatId)
            _draftsFlow.value = mut
        }
        scope.launch {
            try {
                if (prefs.contains(chatId)) {
                    prefs.edit().remove(chatId).apply()
                }
            } catch (_: Exception) {}
        }
    }
}