package by.iposdev.visorlink.data.aegis

import by.iposdev.visorlink.data.model.aegis.LinkResponse
import by.iposdev.visorlink.data.model.aegis.SimulatedContext

interface AegisBrainEngine {
    val engineName: String
    suspend fun processInput(text: String, context: SimulatedContext): LinkResponse
}
