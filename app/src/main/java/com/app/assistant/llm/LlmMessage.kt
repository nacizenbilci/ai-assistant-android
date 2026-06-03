package com.app.assistant.llm

import kotlinx.serialization.Serializable

@Serializable
data class LlmMessage(
    val role: String,
    val content: String
)
