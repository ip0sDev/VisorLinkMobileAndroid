package org.visorlink.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import org.visorlink.app.data.repository.FlagsRepository

class FlagFlipperViewModel(
    private val flagsRepository: FlagsRepository
) : ViewModel() {

    val flags = flagsRepository.flags

    fun toggleFlag(key: String, enabled: Boolean) {
        flagsRepository.toggleFlag(key, enabled)
    }
}
