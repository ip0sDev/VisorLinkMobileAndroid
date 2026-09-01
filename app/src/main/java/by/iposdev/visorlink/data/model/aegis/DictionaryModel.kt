package by.iposdev.visorlink.data.model.aegis

import kotlinx.serialization.Serializable

@Serializable
data class HeuristicDictionary(
    val version: String = "1.0.0",
    val globalRules: List<ContextRule> = emptyList(),
    val categories: List<KeywordCategory> = emptyList()
)

@Serializable
data class ContextRule(
    val id: String,
    val priority: Int = 0,
    val condition: RuleCondition,
    val responses: List<RuleResponse> = emptyList()
)

@Serializable
data class RuleCondition(
    val timeRange: String? = null, // e.g. "23:00-05:59"
    val maxBattery: Int? = null,
    val dayOfWeek: String? = null, // e.g. "6,7"
    val isCharging: Boolean? = null
)

@Serializable
data class KeywordCategory(
    val id: String,
    val priority: Int = 0,
    val keywords: List<String> = emptyList(),
    val responses: List<RuleResponse> = emptyList()
)

@Serializable
data class RuleResponse(
    val emotion: String = "IDLE",
    val action: String = "IDLE",
    val visorIcon: String = "SMILE",
    val requiresUserReply: Boolean = false,
    val templates: List<String> = emptyList()
)
