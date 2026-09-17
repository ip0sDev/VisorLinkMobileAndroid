// data/aegis/MediaPipeLlmEngine.kt
package org.visorlink.app.data.aegis

import org.visorlink.app.data.model.aegis.*

class MediaPipeLlmEngine : AegisBrainEngine {
    override val engineName: String = "MediaPipe Gemma 4 E4B"

    override suspend fun evaluate(
        text: String?,
        context: SimulatedContext,
        memory: LinkMemoryState
    ): Pair<LinkResponse?, LinkMemoryState> {
        if (text == null) return Pair(null, memory) // LLM пока не умеет в проактивность

        val response = processInput(text, context)
        return Pair(response, memory.copy(lastInteractionTime = System.currentTimeMillis()))
    }

    override suspend fun processInput(text: String, context: SimulatedContext): LinkResponse {
        val startTime = System.currentTimeMillis()
        return LinkResponse(
            emotion = LinkEmotion.CONFUSED,
            action = LinkAction.HEAD_TILT,
            visorIcon = VisorIcon.QUESTION,
            requiresUserReply = false,
            message = "Нейроядро еще не загружено в ОЗУ.",
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }
}