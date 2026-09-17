// ui/aegis/AegisLifeViewModel.kt
package org.visorlink.app.ui.aegis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.visorlink.app.data.aegis.DictionaryHeuristicEngine
import org.visorlink.app.data.model.Message
import org.visorlink.app.data.model.aegis.*
import org.visorlink.app.data.repository.FlagsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AegisLifeViewModel(
    private val aegisBrain: DictionaryHeuristicEngine,
    private val flagsRepository: FlagsRepository
) : ViewModel() {

    val isAegisEnabled: StateFlow<Boolean> = flagsRepository.flags
        .map { it.isEnabled("is_aegis_debug_mode") && it.isEnabled("test_flag") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    data class AegisLifeState(
        val action: LinkAction = LinkAction.IDLE,
        val emotion: LinkEmotion = LinkEmotion.IDLE,
        val visorIcon: VisorIcon = VisorIcon.DOTS,
        val isVisible: Boolean = false,
        val message: String = ""
    )

    private val _uiState = MutableStateFlow(AegisLifeState())
    val uiState = _uiState.asStateFlow()

    private var linkMemory = LinkMemoryState()
    private var isManuallyDismissed = false
    private var lastAnalyzedMessageId: String? = null
    private val simulatedContext = SimulatedContext()

    // Блокировка движка на время реакции на тач
    private var touchLockJob: Job? = null
    private val engineMutex = kotlinx.coroutines.sync.Mutex()

    init {
        startProactiveLoop()
    }

    fun processIntent(intent: LinkIntent) {
        if (isManuallyDismissed) return

        viewModelScope.launch {
            when (intent) {
                is LinkIntent.ProcessText -> evaluateEngine(intent.text)
                is LinkIntent.Tick -> {
                    if (touchLockJob?.isActive != true && !engineMutex.isLocked) {
                        evaluateEngine(null)
                    }
                }
                is LinkIntent.Boop -> handleTouch(LinkEmotion.HAPPY, VisorIcon.HEART, "Бип!")
                is LinkIntent.Pet -> handleTouch(LinkEmotion.SLEEPY, VisorIcon.SMILE, "Ммм... Бззззтт...")
            }
        }
    }

    private fun startProactiveLoop() {
        viewModelScope.launch {
            while (true) {
                delay(5000)
                if (isAegisEnabled.value && !isManuallyDismissed) {
                    processIntent(LinkIntent.Tick)
                }
            }
        }
    }

    private suspend fun evaluateEngine(text: String?) {
        if (!engineMutex.tryLock()) return
        try {
            val (response, newMemory) = aegisBrain.evaluate(text, simulatedContext, linkMemory)
            linkMemory = newMemory

            if (response != null && response.emotion != LinkEmotion.IDLE) {
                showAegis(response.action, response.emotion, response.visorIcon, response.message)
            } else if (response != null) {
                // Если движок решил сменить позицию (Action), но эмоция IDLE
                _uiState.update { it.copy(action = response.action, emotion = response.emotion, visorIcon = response.visorIcon, message = "") }
            }
        } finally {
            engineMutex.unlock()
        }
    }

    private fun handleTouch(emotion: LinkEmotion, visor: VisorIcon, msg: String) {
        touchLockJob?.cancel() // Отменяем предыдущий лок

        // Обновляем таймер, чтобы движок не спамил сразу после тача
        linkMemory = linkMemory.copy(lastInteractionTime = System.currentTimeMillis())

        val currentAction = _uiState.value.action
        showAegis(currentAction, emotion, visor, msg)

        // Блокируем проактивность на 3 секунды, чтобы юзер насладился реакцией
        touchLockJob = viewModelScope.launch {
            delay(3000)
            // Возвращаемся в спокойное состояние
            _uiState.update { it.copy(emotion = LinkEmotion.IDLE, visorIcon = VisorIcon.SMILE, message = "") }
        }
    }

    fun onInteract() {
        isManuallyDismissed = false
        showAegis(LinkAction.WAVE, LinkEmotion.HAPPY, VisorIcon.SMILE, "Я тут!")
    }

    fun onDismiss(wasOffended: Boolean) {
        isManuallyDismissed = true
        _uiState.update { it.copy(
            isVisible = false,
            emotion = if (wasOffended) LinkEmotion.OFFENDED else LinkEmotion.IDLE,
            visorIcon = if (wasOffended) VisorIcon.ANGRY else VisorIcon.DOTS
        ) }
    }

    fun analyzeMessages(messages: List<Message>) {
        if (isManuallyDismissed || messages.isEmpty()) return
        val latestMessage = messages.lastOrNull() ?: return
        if (latestMessage.id == lastAnalyzedMessageId) return

        lastAnalyzedMessageId = latestMessage.id
        processIntent(LinkIntent.ProcessText(latestMessage.text ?: ""))
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