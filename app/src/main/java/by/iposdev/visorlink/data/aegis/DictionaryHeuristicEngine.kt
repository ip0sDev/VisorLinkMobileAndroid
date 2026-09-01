// data/aegis/DictionaryHeuristicEngine.kt
package by.iposdev.visorlink.data.aegis

import android.util.Log
import by.iposdev.visorlink.data.model.aegis.*
import kotlin.random.Random

class SafetyInterceptor {
    private val blockedInCrisis = setOf(LinkEmotion.HAPPY, LinkEmotion.PARTY, LinkEmotion.OFFENDED)
    private val blockedInRecovery = setOf(LinkEmotion.PARTY)

    fun applySafetyOverride(rawResponse: LinkResponse, memory: LinkMemoryState): LinkResponse {
        return when {
            memory.isCrisisMode -> {
                if (rawResponse.emotion in blockedInCrisis) {
                    rawResponse.copy(
                        emotion = LinkEmotion.CONCERNED,
                        action = LinkAction.HUG_EDGE,
                        visorIcon = VisorIcon.HEART,
                        message = "Я рядом. Мы с этим справимся."
                    )
                } else rawResponse
            }
            memory.recoveryProgress < 100 -> {
                if (rawResponse.emotion in blockedInRecovery || rawResponse.action == LinkAction.WAVE) {
                    rawResponse.copy(
                        emotion = LinkEmotion.IDLE,
                        action = LinkAction.TOUCH_GLASS,
                        visorIcon = VisorIcon.SMILE,
                        message = "Рад видеть, что тебе понемногу становится лучше."
                    )
                } else rawResponse
            }
            else -> rawResponse
        }
    }
}

class DictionaryHeuristicEngine(
    private val repository: DictionaryRepository
) : AegisBrainEngine {

    override val engineName: String = "Declarative Dictionary Engine v2.2 (Safety+Proactive)"
    private val safetyInterceptor = SafetyInterceptor()

    private val crisisKeywords = listOf("плохо", "больно", "устал", "не могу", "депрессия", "одиноко", "грустно", "тяжело")
    private val reliefKeywords = listOf("отпустило", "лучше", "спасибо", "успокоился", "нормально", "хорошо", "радостно")

    override suspend fun evaluate(
        text: String?,
        context: SimulatedContext,
        memory: LinkMemoryState
    ): Pair<LinkResponse?, LinkMemoryState> {
        var newMemory = memory
        var rawResponse: LinkResponse? = null
        val startTime = System.currentTimeMillis()

        if (text != null) {
            val lowerText = text.lowercase()
            newMemory = updateMemoryBasedOnText(lowerText, newMemory)
            rawResponse = processInput(lowerText, context)
        } else {
            val timeSinceLastAction = System.currentTimeMillis() - memory.lastInteractionTime

            if (!newMemory.isCrisisMode && timeSinceLastAction > 30_000) {
                rawResponse = generateProactiveResponse(context)
                if (rawResponse != null) {
                    newMemory = newMemory.copy(lastInteractionTime = System.currentTimeMillis())
                }
            }

            if (!newMemory.isCrisisMode && newMemory.recoveryProgress < 100) {
                newMemory = newMemory.copy(recoveryProgress = minOf(100, newMemory.recoveryProgress + 5))
            }
        }

        val finalResponse = rawResponse?.let {
            safetyInterceptor.applySafetyOverride(it, newMemory).copy(
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        return Pair(finalResponse, newMemory)
    }

    private fun updateMemoryBasedOnText(text: String, memory: LinkMemoryState): LinkMemoryState {
        val hasCrisis = crisisKeywords.any { text.contains(it) }
        val hasRelief = reliefKeywords.any { text.contains(it) }

        return when {
            hasCrisis -> memory.copy(isCrisisMode = true, recoveryProgress = 0, lastInteractionTime = System.currentTimeMillis())
            hasRelief && memory.isCrisisMode -> memory.copy(isCrisisMode = false, recoveryProgress = 10, lastInteractionTime = System.currentTimeMillis())
            else -> memory.copy(lastInteractionTime = System.currentTimeMillis())
        }
    }

    private fun generateProactiveResponse(context: SimulatedContext): LinkResponse? {
        // 1. Критические системные уведомления (имеют приоритет)
        if (context.batteryLevel < 15 && !context.isCharging) {
            return LinkResponse(
                emotion = LinkEmotion.CONCERNED, action = LinkAction.POINT, visorIcon = VisorIcon.EXCLAMATION,
                requiresUserReply = false, message = "Кажется, нам нужно подкрепиться электричеством..."
            )
        }
        if (isLateNight(context.sysTime)) {
            return LinkResponse(
                emotion = LinkEmotion.SLEEPY, action = LinkAction.YAWN, visorIcon = VisorIcon.ZZZ,
                requiresUserReply = false, message = "Уже поздно. Может, пора спать?"
            )
        }

        // 2. Случайное перемещение по экрану (Шанс 30% при каждом тике)
        if (Random.nextFloat() < 0.3f) {
            val randomAction = listOf(LinkAction.IDLE, LinkAction.PEEK, LinkAction.HUG_EDGE, LinkAction.SIT, LinkAction.WAVE).random()
            return LinkResponse(
                emotion = LinkEmotion.IDLE,
                action = randomAction,
                visorIcon = VisorIcon.SMILE,
                requiresUserReply = false,
                message = "" // Без текста, просто перелетел
            )
        }

        return null
    }

    override suspend fun processInput(text: String, context: SimulatedContext): LinkResponse {
        val startTime = System.currentTimeMillis()
        val dictionary = try {
            repository.getActiveDictionary()
        } catch (e: Exception) {
            Log.e("AegisEngine", "Error loading dictionary", e)
            return LinkResponse(LinkEmotion.CONFUSED, LinkAction.HEAD_TILT, VisorIcon.QUESTION, false, "Error")
        }

        val input = text.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }
        val hasMeaningfulText = input.trim().length > 3

        val keywordCandidates = dictionary.categories
            .map { category ->
                val matchCount = category.keywords.count { keyword -> input.contains(keyword.lowercase()) }
                val score = if (matchCount > 0) matchCount * category.priority else 0
                category.responses.randomOrNull() to score
            }
            .filter { it.second > 0 }

        val bestKeyword = keywordCandidates.maxByOrNull { it.second }

        val contextCandidates = dictionary.globalRules
            .filter { checkCondition(it.condition, context) }
            .map { rule ->
                val effectivePriority = if (hasMeaningfulText && rule.priority < 90) {
                    rule.priority / 2
                } else {
                    rule.priority
                }
                rule.responses.randomOrNull() to effectivePriority
            }
            .filter { it.first != null }

        val bestContext = contextCandidates.maxByOrNull { it.second }

        val winners = listOfNotNull(bestKeyword, bestContext).sortedByDescending { it.second }
        val finalResponse = winners.firstOrNull()?.first

        return if (finalResponse != null) {
            buildResponse(finalResponse, context, startTime)
        } else {
            LinkResponse(
                emotion = LinkEmotion.IDLE,
                action = LinkAction.IDLE,
                visorIcon = VisorIcon.SMILE,
                requiresUserReply = false,
                message = "Запись сохранена в зашифрованный дневник!",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun checkCondition(condition: RuleCondition, context: SimulatedContext): Boolean {
        condition.timeRange?.let { if (!isTimeInRange(context.sysTime, it)) return false }
        condition.maxBattery?.let { if (context.batteryLevel > it) return false }
        condition.dayOfWeek?.let { days ->
            val activeDays = days.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (activeDays.isNotEmpty() && context.dayOfWeek !in activeDays) return false
        }
        condition.isCharging?.let { if (context.isCharging != it) return false }
        return true
    }

    private fun isTimeInRange(current: String, range: String): Boolean {
        return try {
            val parts = range.split("-")
            val start = parts[0].split(":")[0].toInt()
            val end = parts[1].split(":")[0].toInt()
            val now = current.split(":")[0].toInt()
            if (start > end) now >= start || now <= end else now in start..end
        } catch (e: Exception) { false }
    }

    private fun isLateNight(sysTime: String): Boolean {
        val hour = sysTime.split(":").firstOrNull()?.toIntOrNull() ?: return false
        return hour >= 23 || hour < 5
    }

    private fun buildResponse(resp: RuleResponse, context: SimulatedContext, startTime: Long): LinkResponse {
        val template = resp.templates.randomOrNull() ?: "..."
        val message = template.replace("{sysTime}", context.sysTime).replace("{batteryLevel}", context.batteryLevel.toString())
        return LinkResponse(
            emotion = try { LinkEmotion.valueOf(resp.emotion) } catch (e: Exception) { LinkEmotion.IDLE },
            action = try { LinkAction.valueOf(resp.action) } catch (e: Exception) { LinkAction.IDLE },
            visorIcon = try { VisorIcon.valueOf(resp.visorIcon) } catch (e: Exception) { VisorIcon.SMILE },
            requiresUserReply = resp.requiresUserReply,
            message = message,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }
}