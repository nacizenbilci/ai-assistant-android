package com.app.assistant.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive


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

    suspend fun fetchAvailableModels(
        client: OkHttpClient,
        provider: LlmProvider,
        apiKey: String,
        customUrl: String = "",
        customHeaders: String = ""
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = when (provider) {
                LlmProvider.GROQ -> "https://api.groq.com/openai/v1/models"
                LlmProvider.OPENAI -> "https://api.openai.com/v1/models"
                LlmProvider.OPEN_ROUTER -> "https://openrouter.ai/api/v1/models"
                LlmProvider.DEEPSEEK -> "https://api.deepseek.com/v1/models"
                LlmProvider.OLLAMA -> {
                    val configUrl = provider.config.url
                    if (configUrl.endsWith("/api/chat")) {
                        configUrl.substringBeforeLast("/api/chat") + "/api/tags"
                    } else {
                        "http://10.0.2.2:11434/api/tags"
                    }
                }
                LlmProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                LlmProvider.ANTHROPIC -> "https://api.anthropic.com/v1/models"
                LlmProvider.CUSTOM -> {
                    if (customUrl.isBlank()) return@withContext emptyList<String>()
                    if (customUrl.endsWith("/chat/completions")) {
                        customUrl.substringBeforeLast("/chat/completions") + "/models"
                    } else if (customUrl.endsWith("/v1/chat/completions")) {
                        customUrl.substringBeforeLast("/v1/chat/completions") + "/v1/models"
                    } else {
                        null
                    }
                }
            } ?: return@withContext emptyList<String>()

            val requestBuilder = Request.Builder().url(url).get()
            
            when (provider) {
                LlmProvider.GROQ, LlmProvider.OPENAI, LlmProvider.OPEN_ROUTER, LlmProvider.DEEPSEEK -> {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                    requestBuilder.addHeader("Content-Type", "application/json")
                }
                LlmProvider.ANTHROPIC -> {
                    requestBuilder.addHeader("x-api-key", apiKey)
                    requestBuilder.addHeader("anthropic-version", "2023-06-01")
                    requestBuilder.addHeader("content-type", "application/json")
                }
                LlmProvider.CUSTOM -> {
                    if (customHeaders.isNotBlank()) {
                        try {
                            val parsed = parseHeadersJson(customHeaders)
                            for ((k, v) in parsed) {
                                requestBuilder.addHeader(k, v.replace("{{API_KEY}}", apiKey))
                            }
                        } catch (e: Exception) {
                            Log.e("ModelCapabilityProber", "Failed to parse custom headers", e)
                        }
                    }
                }
                else -> {}
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ModelCapabilityProber", "Model fetch response not successful: ${response.code}")
                    return@withContext emptyList<String>()
                }
                val bodyString = response.body?.string() ?: return@withContext emptyList<String>()
                
                val element = Json.parseToJsonElement(bodyString)
                val modelsList = mutableListOf<String>()

                when (provider) {
                    LlmProvider.OLLAMA -> {
                        val modelsArray = element.jsonObject["models"]?.jsonArray
                        modelsArray?.forEach { modelObj ->
                            modelObj.jsonObject["name"]?.jsonPrimitive?.content?.let { name ->
                                modelsList.add(name)
                            }
                        }
                    }
                    LlmProvider.GEMINI -> {
                        val modelsArray = element.jsonObject["models"]?.jsonArray
                        modelsArray?.forEach { modelObj ->
                            modelObj.jsonObject["name"]?.jsonPrimitive?.content?.let { name ->
                                val cleanedName = if (name.startsWith("models/")) name.substringAfter("models/") else name
                                modelsList.add(cleanedName)
                            }
                        }
                    }
                    else -> {
                        val dataArray = element.jsonObject["data"]?.jsonArray
                        dataArray?.forEach { modelObj ->
                            modelObj.jsonObject["id"]?.jsonPrimitive?.content?.let { id ->
                                modelsList.add(id)
                            }
                        }
                    }
                }
                
                modelsList.distinct().sorted()
            }
        } catch (e: Exception) {
            Log.e("ModelCapabilityProber", "Failed to fetch models", e)
            emptyList()
        }
    }

    private fun parseHeadersJson(jsonStr: String): Map<String, String> {
        return try {
            val element = Json.parseToJsonElement(jsonStr)
            if (element is kotlinx.serialization.json.JsonObject) {
                element.mapValues { (_, value) ->
                    if (value is kotlinx.serialization.json.JsonPrimitive) value.content else value.toString()
                }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
