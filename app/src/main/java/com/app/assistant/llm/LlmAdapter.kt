package com.app.assistant.llm

interface LlmAdapter {
    suspend fun generateResponse(
        systemContext: String,
        messages: List<LlmMessage>
    ): String?
}
