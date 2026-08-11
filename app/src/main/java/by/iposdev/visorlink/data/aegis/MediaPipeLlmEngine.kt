package by.iposdev.visorlink.data.aegis

import by.iposdev.visorlink.data.model.aegis.*

class MediaPipeLlmEngine : AegisBrainEngine {
    override val engineName: String = "MediaPipe Gemma 4 E4B"

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
