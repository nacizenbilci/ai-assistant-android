package com.app.assistant.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object ModelCapabilityProber {

    fun resolveCapabilitiesOffline(provider: LlmProvider, modelName: String): ModelCapabilities {
        val model = modelName.lowercase()
        return when (provider) {
            LlmProvider.GEMINI -> {
                val isMultimodal = model.contains("gemini-1.5") || model.contains("gemini-2.0") || model.contains("gemini-pro")
                ModelCapabilities(
                    hasImageInput = isMultimodal || model.contains("vision"),
                    hasAudioInput = isMultimodal,
                    hasVideoInput = isMultimodal
                )
            }
            LlmProvider.OPENAI -> {
                val hasVision = model.contains("gpt-4o") || model.contains("vision") || model.contains("gpt-4-turbo")
                val hasAudio = model.contains("gpt-4o-audio")
                ModelCapabilities(
                    hasImageInput = hasVision,
                    hasAudioInput = hasAudio,
                    hasVideoInput = false
                )
            }
            LlmProvider.ANTHROPIC -> {
                val supportsVision = model.contains("claude-3")
                ModelCapabilities(
                    hasImageInput = supportsVision,
                    hasAudioInput = false,
                    hasVideoInput = false
                )
            }
            LlmProvider.GROQ -> {
                ModelCapabilities(
                    hasImageInput = model.contains("vision") || model.contains("scout"),
                    hasAudioInput = false,
                    hasVideoInput = false
                )
            }
            LlmProvider.OLLAMA -> {
                val isMultimodal = model.contains("llava") || model.contains("bakllava") || model.contains("minicpm") || model.contains("vision")
                ModelCapabilities(
                    hasImageInput = isMultimodal,
                    hasAudioInput = false,
                    hasVideoInput = false
                )
            }
            LlmProvider.DEEPSEEK -> {
                ModelCapabilities(
                    hasImageInput = model.contains("vl"),
                    hasAudioInput = false,
                    hasVideoInput = false
                )
            }
            LlmProvider.OPEN_ROUTER -> {
                val hasVision = model.contains("vision") || model.contains("llava") || model.contains("gpt-4o") || model.contains("claude-3") || model.contains("gemini")
                val hasAudio = model.contains("gemini") || model.contains("audio")
                val hasVideo = model.contains("gemini")
                ModelCapabilities(
                    hasImageInput = hasVision,
                    hasAudioInput = hasAudio,
                    hasVideoInput = hasVideo
                )
            }
            LlmProvider.CUSTOM -> {
                ModelCapabilities(
                    hasImageInput = false,
                    hasAudioInput = false,
                    hasVideoInput = false
                )
            }
        }
    }

    suspend fun probeTextConnection(
        client: OkHttpClient,
        apiKey: String,
        model: String,
        config: LlmConfig
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = config.url
                .replace("{{API_KEY}}", apiKey)
                .replace("{{MODEL}}", model)

            val requestBuilder = Request.Builder().url(url)
            for ((key, value) in config.headers) {
                val resolvedValue = value
                    .replace("{{API_KEY}}", apiKey)
                    .replace("{{MODEL}}", model)
                requestBuilder.addHeader(key, resolvedValue)
            }

            val hasSystemContextPlaceholder = config.requestTemplate.contains("{{SYSTEM_CONTEXT}}")
            
            val escapedContent = "say ok"
            val msgJson = config.messageFormat
                .replace("{{ROLE}}", config.userRole)
                .replace("{{CONTENT}}", escapedContent)
            
            val messagesJsonArray = "[$msgJson]"
            var requestBodyString = config.requestTemplate
                .replace("{{MODEL}}", model)
                .replace("{{MESSAGES}}", messagesJsonArray)

            if (hasSystemContextPlaceholder) {
                requestBodyString = requestBodyString.replace("{{SYSTEM_CONTEXT}}", "test connection")
            }

            val mediaType = config.headers["Content-Type"]?.toMediaTypeOrNull() ?: "application/json".toMediaTypeOrNull()
            val requestBody = requestBodyString.toRequestBody(mediaType)
            requestBuilder.post(requestBody)

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                Log.d("ModelCapabilityProber", "Text test response code: ${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("ModelCapabilityProber", "Text test failed", e)
            false
        }
    }

    suspend fun probeVisionConnection(
        client: OkHttpClient,
        provider: LlmProvider,
        apiKey: String,
        model: String,
        config: LlmConfig
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = config.url
                .replace("{{API_KEY}}", apiKey)
                .replace("{{MODEL}}", model)

            val requestBuilder = Request.Builder().url(url)
            for ((key, value) in config.headers) {
                val resolvedValue = value
                    .replace("{{API_KEY}}", apiKey)
                    .replace("{{MODEL}}", model)
                requestBuilder.addHeader(key, resolvedValue)
            }

            // 2x2 transparent PNG base64
            val base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAC0lEQVR4nGNgQAcAABIAAXfx+gAAAAAASUVORK5CYII="
            
            val requestBodyString = when (provider) {
                LlmProvider.GEMINI -> {
                    """
                    {
                      "contents": [{
                        "role": "user",
                        "parts": [
                          { "text": "is this image ok?" },
                          { "inlineData": { "mimeType": "image/png", "data": "$base64Image" } }
                        ]
                      }],
                      "generationConfig": { "maxOutputTokens": 1 }
                    }
                    """.trimIndent()
                }
                LlmProvider.ANTHROPIC -> {
                    """
                    {
                      "model": "$model",
                      "max_tokens": 10,
                      "messages": [{
                        "role": "user",
                        "content": [
                          { "type": "text", "text": "is this image ok?" },
                          { "type": "image", "source": { "type": "base64", "media_type": "image/png", "data": "$base64Image" } }
                        ]
                      }]
                    }
                    """.trimIndent()
                }
                else -> { // OpenAI-compatible schema (OpenAI, Groq, OpenRouter, Custom, Ollama, DeepSeek)
                    """
                    {
                      "model": "$model",
                      "max_tokens": 10,
                      "messages": [{
                        "role": "user",
                        "content": [
                          { "type": "text", "text": "is this image ok?" },
                          { "type": "image_url", "image_url": { "url": "data:image/png;base64,$base64Image" } }
                        ]
                      }]
                    }
                    """.trimIndent()
                }
            }

            val mediaType = "application/json".toMediaTypeOrNull()
            val requestBody = requestBodyString.toRequestBody(mediaType)
            requestBuilder.post(requestBody)

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                Log.d("ModelCapabilityProber", "Vision test response code: ${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("ModelCapabilityProber", "Vision test failed", e)
            false
        }
    }
}
