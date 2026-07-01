package com.app.assistant.llm

import kotlinx.coroutines.flow.Flow

interface LlmAdapter {
    suspend fun generateResponse(
        systemContext: String,
        messages: List<LlmMessage>
    ): String?

    fun generateResponseStream(
        systemContext: String,
        messages: List<LlmMessage>
    ): Flow<String>
}
