package com.app.assistant.usecase

import android.util.Log
import com.app.assistant.BuildConfig
import com.app.assistant.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

@Serializable
data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 1.0,
    val top_p: Double = 1.0,
    val stop: String? = null
)

@Serializable
data class GroqResponse(
    val choices: List<GroqChoice>
)

@Serializable
data class GroqChoice(
    val message: GroqMessage
)

class GetAiResponseUseCase(
    private val settingsRepository: SettingsRepository,
    private val client: OkHttpClient
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(messages: List<GroqMessage>): String? {
        val requestObj = GroqRequest(
            model = "llama-3.3-70b-versatile",
            messages = messages,
            temperature = 1.0,
            top_p = 1.0,
            stop = null
        )

        val requestBody = json.encodeToString(requestObj)
            .toRequestBody("application/json".toMediaTypeOrNull())

        val chatKey = loadChatKey()
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $chatKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val rawResponse = withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 401) {
                            return@withContext """{"choices":[{"message":{"content":"Your chat API key is missing or invalid."}}]}"""
                        }
                        throw IOException("Unexpected code $response")
                    }
                    response.body!!.string()
                }
            } catch (e: UnknownHostException) {
                """{"choices":[{"message":{"content":"Seems this device is offline. Maybe try checking data connection."}}]}"""
            } catch (e: Exception) {
                Log.e("GetAiResponseUseCase", "Error during AI call", e)
                """{"choices":[{"message":{"content":"Seems some issue in my server. Please try again."}}]}"""
            }
        }

        return extractFromAI(rawResponse)
    }

    fun extractFromAI(response: String): String? {
        try {
            if (response.isNotEmpty()) {
                val responseObj = json.decodeFromString<GroqResponse>(response)
                if (responseObj.choices.isNotEmpty()) {
                    var content = responseObj.choices[0].message.content

                    // Regex to remove <think> tags and the content inside
                    val thinkTagRegex = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
                    content = content.replace(thinkTagRegex, "")

                    // Remove any standalone opening or closing <think> tags
                    content = content.replace("<think>", "").replace("</think>", "")

                    return content.trim()
                } else {
                    return ""
                }
            } else {
                return "Seems some issue in my server. Please try again."
            }
        } catch (e: Exception) {
            Log.e("GetAiResponseUseCase", "Error extracting AI response", e)
            return null
        }
    }

    private fun loadChatKey(): String? {
        var chatKey = settingsRepository.getChatApiKey()
        if (chatKey.isNullOrBlank()) {
            chatKey = BuildConfig.GROQ_API_KEY
        }
        return chatKey
    }
}
