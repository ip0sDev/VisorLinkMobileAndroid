// data/aegis/AegisBrainEngine.kt
package org.visorlink.app.data.aegis

import org.visorlink.app.data.model.aegis.LinkMemoryState
import org.visorlink.app.data.model.aegis.LinkResponse
import org.visorlink.app.data.model.aegis.SimulatedContext

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