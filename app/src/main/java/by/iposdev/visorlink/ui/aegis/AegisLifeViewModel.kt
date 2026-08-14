package by.iposdev.visorlink.ui.aegis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.aegis.DictionaryHeuristicEngine
import by.iposdev.visorlink.data.model.Message
import by.iposdev.visorlink.data.model.aegis.*
import by.iposdev.visorlink.data.repository.FlagsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class AegisLifeViewModel(
    private val aegisBrain: DictionaryHeuristicEngine,
    private val flagsRepository: FlagsRepository
) : ViewModel() {

    val isAegisEnabled: StateFlow<Boolean> = flagsRepository.flags
        .map { it.isEnabled("is_aegis_debug_mode") && it.isEnabled("test_flag") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uiState = MutableStateFlow(AegisLifeState())
    val uiState = _uiState.asStateFlow()

    private var isManuallyDismissed = false
    private var lastAnalyzedMessageId: String? = null

    data class AegisLifeState(
        val action: LinkAction = LinkAction.IDLE,
        val emotion: LinkEmotion = LinkEmotion.IDLE,
        val visorIcon: VisorIcon = VisorIcon.DOTS,
        val isVisible: Boolean = false,
        val message: String = ""
    )

    fun onInteract() {
        isManuallyDismissed = false
        showAegis(LinkAction.WAVE, LinkEmotion.HAPPY, VisorIcon.SMILE)
    }

    fun onDismiss(wasOffended: Boolean) {
        isManuallyDismissed = true
        _uiState.update { it.copy(
            isVisible = false,
            emotion = if (wasOffended) LinkEmotion.OFFENDED else LinkEmotion.IDLE,
            visorIcon = if (wasOffended) VisorIcon.ANGRY else VisorIcon.DOTS
        ) }
    }

    fun onBoop() {
        showAegis(_uiState.value.action, LinkEmotion.HAPPY, VisorIcon.HEART, "Бип! Это было приятно!")
    }

    fun onPet() {
        showAegis(_uiState.value.action, LinkEmotion.HAPPY, VisorIcon.SMILE, "Ммм... Бззззтт...")
    }

    fun analyzeMessages(messages: List<Message>) {
        if (isManuallyDismissed || messages.isEmpty()) return
        
        val latestMessage = messages.lastOrNull() ?: return
        if (latestMessage.id == lastAnalyzedMessageId) return
        
        lastAnalyzedMessageId = latestMessage.id

        viewModelScope.launch {
            val text = latestMessage.text ?: ""
            // Context simulation for the engine
            val context = SimulatedContext()
            val response = aegisBrain.processInput(text, context)
            
            // Увеличим шанс появления до 50% для лучшей видимости проактивности
            val finalResponse = if (response.emotion == LinkEmotion.IDLE && Random.nextFloat() < 0.5f) {
                LinkResponse(
                    emotion = LinkEmotion.HAPPY,
                    action = LinkAction.WAVE,
                    visorIcon = VisorIcon.SMILE,
                    requiresUserReply = false,
                    message = "Бип! Я просто заглянул проверить, как ты."
                )
            } else {
                response
            }

            if (finalResponse.emotion != LinkEmotion.IDLE) {
                val proactiveAction = when {
                    text.length > 50 -> LinkAction.SIT
                    text.contains("?") -> LinkAction.HEAD_TILT
                    text.length % 2 == 0 -> LinkAction.HUG_EDGE
                    else -> LinkAction.PEEK
                }
                
                showAegis(proactiveAction, finalResponse.emotion, finalResponse.visorIcon, finalResponse.message)
            }
        }
    }

    private fun showAegis(action: LinkAction, emotion: LinkEmotion, visor: VisorIcon, msg: String = "") {
        _uiState.update { it.copy(
            action = action,
            emotion = emotion,
            visorIcon = visor,
            message = msg,
            isVisible = true
        ) }
    }
}
