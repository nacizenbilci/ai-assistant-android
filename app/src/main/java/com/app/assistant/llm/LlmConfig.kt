package com.app.assistant.llm

data class LlmConfig(
    val url: String,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap(),
    val responsePath: String,
    val messageFormat: String,
    val systemRole: String? = null,
    val userRole: String = "user",
    val assistantRole: String = "assistant",
    val requestTemplate: String
)
