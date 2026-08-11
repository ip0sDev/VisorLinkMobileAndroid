package by.iposdev.visorlink.data.aegis

import android.util.Log
import by.iposdev.visorlink.data.model.aegis.*
import kotlin.random.Random

class DictionaryHeuristicEngine(
    private val repository: DictionaryRepository
) : AegisBrainEngine {

    override val engineName: String = "Declarative Dictionary Engine v2.1"

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

        // 1. Keyword Candidates
        val keywordCandidates = dictionary.categories
            .map { category ->
                val matchCount = category.keywords.count { keyword -> input.contains(keyword.lowercase()) }
                val score = if (matchCount > 0) matchCount * category.priority else 0
                category.responses.randomOrNull() to score
            }
            .filter { it.second > 0 }

        val bestKeyword = keywordCandidates.maxByOrNull { it.second }

        // 2. Context Candidates
        val contextCandidates = dictionary.globalRules
            .filter { checkCondition(it.condition, context) }
            .map { rule ->
                // PENALTY: Lower ambient context priority if user is actually talking to us
                val effectivePriority = if (hasMeaningfulText && rule.priority < 90) {
                    rule.priority / 2 
                } else {
                    rule.priority
                }
                rule.responses.randomOrNull() to effectivePriority
            }
            .filter { it.first != null }

        val bestContext = contextCandidates.maxByOrNull { it.second }

        // 3. Final Selection
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
