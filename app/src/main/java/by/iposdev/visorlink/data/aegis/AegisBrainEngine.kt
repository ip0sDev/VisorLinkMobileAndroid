// data/aegis/AegisBrainEngine.kt
package by.iposdev.visorlink.data.aegis

import by.iposdev.visorlink.data.model.aegis.LinkMemoryState
import by.iposdev.visorlink.data.model.aegis.LinkResponse
import by.iposdev.visorlink.data.model.aegis.SimulatedContext

interface AegisBrainEngine {
    val engineName: String

    // Новый метод с учетом памяти и проактивности
    suspend fun evaluate(
        text: String?,
        context: SimulatedContext,
        memory: LinkMemoryState
    ): Pair<LinkResponse?, LinkMemoryState>

    // Старый метод оставлен для обратной совместимости
    suspend fun processInput(text: String, context: SimulatedContext): LinkResponse
}