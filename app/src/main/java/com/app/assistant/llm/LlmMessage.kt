package com.app.assistant.llm

import kotlinx.serialization.Serializable

@Serializable
data class LlmAttachment(
    val base64Data: String,
    val mimeType: String,
    val fileName: String
)

@Serializable
data class LlmMessage(
    val role: String,
    val content: String,
    val attachments: List<LlmAttachment>? = null
)
