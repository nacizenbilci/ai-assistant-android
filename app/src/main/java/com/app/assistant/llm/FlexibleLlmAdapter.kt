package com.app.assistant.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.UnknownHostException

class FlexibleLlmAdapter(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val config: LlmConfig
) : LlmAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generateResponse(
        systemContext: String,
        messages: List<LlmMessage>
    ): String? {
        // 1. Resolve URL
        val url = config.url
            .replace("{{API_KEY}}", apiKey)
            .replace("{{MODEL}}", model)

        // 2. Resolve Headers
        val requestBuilder = Request.Builder().url(url)
        for ((key, value) in config.headers) {
            val resolvedValue = value
                .replace("{{API_KEY}}", apiKey)
                .replace("{{MODEL}}", model)
            requestBuilder.addHeader(key, resolvedValue)
        }

        // 3. Serialize Messages
        // Filter out system message if we use SYSTEM_CONTEXT placeholder in the template
        val hasSystemContextPlaceholder = config.requestTemplate.contains("{{SYSTEM_CONTEXT}}")
        val messagesToSerialize = if (hasSystemContextPlaceholder) {
            messages.filter { it.role != "system" }
        } else {
            messages
        }

        val serializedMessagesBuilder = StringBuilder()
        serializedMessagesBuilder.append("[")
        messagesToSerialize.forEachIndexed { index, msg ->
            val roleValue = when (msg.role) {
                "system" -> config.systemRole ?: "system"
                "user" -> config.userRole
                "assistant" -> config.assistantRole
                else -> msg.role
            }
            val escapedContent = escapeJsonString(msg.content)
            val msgJson = config.messageFormat
                .replace("{{ROLE}}", roleValue)
                .replace("{{CONTENT}}", escapedContent)
            
            serializedMessagesBuilder.append(msgJson)
            if (index < messagesToSerialize.size - 1) {
                serializedMessagesBuilder.append(",")
            }
        }
        serializedMessagesBuilder.append("]")
        val messagesJsonArray = serializedMessagesBuilder.toString()

        // 4. Resolve Request Body
        val requestBodyString = config.requestTemplate
            .replace("{{MODEL}}", model)
            .replace("{{MESSAGES}}", messagesJsonArray)
            .replace("{{SYSTEM_CONTEXT}}", escapeJsonString(systemContext))

        val mediaType = config.headers["Content-Type"]?.toMediaTypeOrNull() ?: "application/json".toMediaTypeOrNull()
        val requestBody = requestBodyString.toRequestBody(mediaType)

        requestBuilder.post(requestBody)
        val request = requestBuilder.build()

        val rawResponse = withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 401) {
                            return@withContext """{"error_code": 401, "msg": "API key is missing or invalid."}"""
                        }
                        throw IOException("Unexpected code $response")
                    }
                    response.body!!.string()
                }
            } catch (e: UnknownHostException) {
                """{"error_code": 503, "msg": "Seems this device is offline. Maybe try checking data connection."}"""
            } catch (e: Exception) {
                Log.e("FlexibleLlmAdapter", "Error during LLM API call", e)
                """{"error_code": 500, "msg": "Seems some issue in LLM server. Please try again."}"""
            }
        }

        return extractResponseContent(rawResponse, config.responsePath)
    }

    private fun extractResponseContent(rawResponse: String, path: String): String? {
        try {
            if (rawResponse.contains("error_code")) {
                val errExtract = extractValueFromJson(rawResponse, "msg")
                if (errExtract != null) return errExtract
            }

            val extracted = extractValueFromJson(rawResponse, path) ?: return null

            var content = extracted
            val thinkTagRegex = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
            content = content.replace(thinkTagRegex, "")
            content = content.replace("<think>", "").replace("</think>", "")
            return content.trim()
        } catch (e: Exception) {
            Log.e("FlexibleLlmAdapter", "Error extracting LLM response", e)
            return null
        }
    }

    private fun extractValueFromJson(jsonStr: String, path: String): String? {
        return try {
            val jsonElement = Json.parseToJsonElement(jsonStr)
            val tokens = path.split(Regex("[\\.\\[\\]]+")).filter { it.isNotEmpty() }
            var currentElement = jsonElement
            for (token in tokens) {
                currentElement = when (currentElement) {
                    is kotlinx.serialization.json.JsonObject -> {
                        currentElement[token] ?: return null
                    }
                    is kotlinx.serialization.json.JsonArray -> {
                        val index = token.toIntOrNull() ?: return null
                        if (index in 0 until currentElement.size) {
                            currentElement[index]
                        } else {
                            return null
                        }
                    }
                    else -> return null
                }
            }
            if (currentElement is kotlinx.serialization.json.JsonPrimitive) {
                currentElement.content
            } else {
                currentElement.toString()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun escapeJsonString(input: String): String {
        val builder = StringBuilder()
        for (ch in input) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        builder.append(String.format("\\u%04x", ch.code))
                    } else {
                        builder.append(ch)
                    }
                }
            }
        }
        return builder.toString()
    }
}
