package by.iposdev.visorlink.ui.screens.settings

import androidx.lifecycle.ViewModel
import by.iposdev.visorlink.data.repository.FlagsRepository
import kotlinx.coroutines.flow.map

class FlagFlipperViewModel(
    private val flagsRepository: FlagsRepository
) : ViewModel() {

    val flippableFlags = flagsRepository.flags.map { flags ->
        val keys = flags.serverClaims.filter { 
            it.value is Boolean && it.value == true 
        }.keys.toMutableSet()
        
        // Ensure core flags are present
        keys.add("backend_v2_enabled")
        if (flags.testFlag) keys.add("test_flag")
        if (flags.isAegisDebugMode) {
            keys.add("aegis_debug_mode_enabled")
        }
        
        keys.toList().sorted()
    }

    val flags = flagsRepository.flags

    fun toggleFlag(key: String, enabled: Boolean) {
        flagsRepository.toggleFlag(key, enabled)
    }
}
