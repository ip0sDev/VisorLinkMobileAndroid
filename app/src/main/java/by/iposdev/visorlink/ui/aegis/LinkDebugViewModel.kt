package by.iposdev.visorlink.ui.aegis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.iposdev.visorlink.data.aegis.*
import by.iposdev.visorlink.data.model.aegis.*
import by.iposdev.visorlink.data.repository.FlagsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LinkDebugViewModel(
    private val dictionaryEngine: DictionaryHeuristicEngine,
    private val llmEngine: MediaPipeLlmEngine,
    private val flagsRepository: FlagsRepository
) : ViewModel() {

    private val _currentEngine = MutableStateFlow<AegisBrainEngine>(dictionaryEngine)
    val currentEngineName = _currentEngine.map { it.engineName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), dictionaryEngine.engineName)

    private val _uiState = MutableStateFlow(LinkUiState())
    val uiState: StateFlow<LinkUiState> = _uiState.asStateFlow()

    fun setEngine(useLlm: Boolean) {
        _currentEngine.value = if (useLlm) llmEngine else dictionaryEngine
    }

    val isAegisDebugMode: StateFlow<Boolean> = flagsRepository.flags
        .map { it.isAegisDebugMode && it.testFlag }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun updateContext(newContext: SimulatedContext) {
        _uiState.update { it.copy(simulatedContext = newContext) }
    }

    fun analyzeText(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            // Simulate processing delay
            delay(300)
            val response = _currentEngine.value.processInput(text, _uiState.value.simulatedContext)
            _uiState.update { 
                it.copy(
                    lastResponse = response,
                    isProcessing = false
                )
            }
        }
    }

    fun simulateIdle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            delay(1000)
            val idleResponse = LinkResponse(
                emotion = LinkEmotion.SLEEPY,
                action = LinkAction.YAWN,
                visorIcon = VisorIcon.ZZZ,
                requiresUserReply = false,
                message = "Вип... Кажется, мы долго ничего не делали. Я вздремну?"
            )
            _uiState.update { 
                it.copy(
                    lastResponse = idleResponse,
                    isProcessing = false
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = LinkUiState()
    }

    fun toggleSafeMode(enabled: Boolean) {
        _uiState.update { it.copy(isSafeMode = enabled) }
    }
}
