package by.iposdev.visorlink.utils

import android.content.Context
import by.iposdev.visorlink.data.model.StickerPack
import by.iposdev.visorlink.ui.components.chat.QUICK_REACTIONS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class UsageStat(
    val count: Int = 0,
    val lastUsedTimestamp: Long = 0L
)

/**
 * Менеджер ранжирования стикерпаков и реакций на основе истории использования пользователем.
 * Данные сохраняются локально в SharedPreferences и кэшируются в памяти.
 */
class UsageRankManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy {
        context.applicationContext.getSharedPreferences("visorlink_usage_rankings", Context.MODE_PRIVATE)
    }

    private val reactionStats = ConcurrentHashMap<String, UsageStat>()
    private val packStats = ConcurrentHashMap<String, UsageStat>()

    private val _rankedReactionsFlow = MutableStateFlow<List<String>>(QUICK_REACTIONS)
    val rankedReactionsFlow: StateFlow<List<String>> = _rankedReactionsFlow.asStateFlow()

    init {
        scope.launch {
            loadStats()
            updateRankedReactions()
        }
    }

    private fun loadStats() {
        try {
            val all = prefs.all
            for ((key, value) in all) {
                if (key.startsWith(PREFIX_REACTION_COUNT)) {
                    val emoji = key.removePrefix(PREFIX_REACTION_COUNT)
                    val count = (value as? Number)?.toInt() ?: continue
                    val time = prefs.getLong(PREFIX_REACTION_TIME + emoji, 0L)
                    reactionStats[emoji] = UsageStat(count, time)
                } else if (key.startsWith(PREFIX_PACK_COUNT)) {
                    val packId = key.removePrefix(PREFIX_PACK_COUNT)
                    val count = (value as? Number)?.toInt() ?: continue
                    val time = prefs.getLong(PREFIX_PACK_TIME + packId, 0L)
                    packStats[packId] = UsageStat(count, time)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Зафиксировать использование реакции пользователем.
     */
    fun recordReactionUsage(emoji: String) {
        if (emoji.isBlank()) return
        val now = System.currentTimeMillis()
        val current = reactionStats[emoji] ?: UsageStat()
        val updated = UsageStat(current.count + 1, now)
        reactionStats[emoji] = updated
        updateRankedReactions()

        scope.launch {
            try {
                prefs.edit()
                    .putInt(PREFIX_REACTION_COUNT + emoji, updated.count)
                    .putLong(PREFIX_REACTION_TIME + emoji, updated.lastUsedTimestamp)
                    .apply()
            } catch (_: Exception) {}
        }
    }

    /**
     * Зафиксировать использование стикерпака пользователем.
     */
    fun recordPackUsage(packId: String) {
        if (packId.isBlank()) return
        val now = System.currentTimeMillis()
        val current = packStats[packId] ?: UsageStat()
        val updated = UsageStat(current.count + 1, now)
        packStats[packId] = updated

        scope.launch {
            try {
                prefs.edit()
                    .putInt(PREFIX_PACK_COUNT + packId, updated.count)
                    .putLong(PREFIX_PACK_TIME + packId, updated.lastUsedTimestamp)
                    .apply()
            } catch (_: Exception) {}
        }
    }

    /**
     * Получить список реакций, отсортированный по частоте и времени использования.
     */
    fun getRankedReactions(baseList: List<String> = QUICK_REACTIONS): List<String> {
        val usedReactions = reactionStats.filter { it.value.count > 0 }
            .toList()
            .sortedWith(
                compareByDescending<Pair<String, UsageStat>> { it.second.count }
                    .thenByDescending { it.second.lastUsedTimestamp }
            )
            .map { it.first }

        val remaining = baseList.filter { it !in usedReactions }
        return usedReactions + remaining
    }

    /**
     * Отранжировать список стикерпаков: наиболее часто и недавно используемые первыми.
     */
    fun rankPacks(packs: List<StickerPack>): List<StickerPack> {
        if (packs.size <= 1) return packs
        return packs.sortedWith(
            compareByDescending<StickerPack> { packStats[it.id]?.count ?: 0 }
                .thenByDescending { packStats[it.id]?.lastUsedTimestamp ?: 0L }
        )
    }

    private fun updateRankedReactions() {
        _rankedReactionsFlow.value = getRankedReactions()
    }

    companion object {
        private const val PREFIX_REACTION_COUNT = "reaction_cnt_"
        private const val PREFIX_REACTION_TIME = "reaction_time_"
        private const val PREFIX_PACK_COUNT = "pack_cnt_"
        private const val PREFIX_PACK_TIME = "pack_time_"
    }
}
