package com.app.assistant.llm

data class ModelCapabilities(
    val hasImageInput: Boolean,
    val hasAudioInput: Boolean,
    val hasVideoInput: Boolean
)
