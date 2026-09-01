// data/model/aegis/AegisModels.kt
package by.iposdev.visorlink.data.model.aegis

enum class LinkEmotion { HAPPY, SAD, CONCERNED, SLEEPY, CONFUSED, CURIOUS, PARTY, IDLE, OFFENDED }

enum class LinkAction { IDLE, WAVE, HUG_EDGE, HEAD_TILT, EAT_CACHE, YAWN, TOUCH_GLASS, PEEK, SIT, POINT }

enum class VisorIcon { HEART, EXCLAMATION, DOTS, CROSS, CHECKMARK, ZZZ, QUESTION, SMILE, ANGRY }

data class LinkResponse(
    val emotion: LinkEmotion,
    val action: LinkAction,
    val visorIcon: VisorIcon,
    val requiresUserReply: Boolean,
    val message: String,
    val executionTimeMs: Long = 0
)

data class SimulatedContext(
    val sysTime: String = "14:00", // "HH:mm"
    val batteryLevel: Int = 80,
    val isDarkMode: Boolean = true,
    val dayOfWeek: Int = 1, // 1=Mon, 7=Sun
    val isCharging: Boolean = false
)

data class LinkUiState(
    val lastResponse: LinkResponse? = null,
    val isProcessing: Boolean = false,
    val isSafeMode: Boolean = false,
    val simulatedContext: SimulatedContext = SimulatedContext()
)

// ─── НОВЫЕ КЛАССЫ (MVI & Memory) ───

data class LinkMemoryState(
    val isCrisisMode: Boolean = false,
    val recoveryProgress: Int = 100, // 0..100. 100 = полностью восстановлен
    val lastInteractionTime: Long = System.currentTimeMillis(),
    val isSleeping: Boolean = false
)

sealed class LinkIntent {
    data class ProcessText(val text: String) : LinkIntent()
    object Tick : LinkIntent()
    object Boop : LinkIntent()
    object Pet : LinkIntent()
}