package com.app.assistant.usecase

import android.util.Log
import com.app.assistant.BuildConfig
import com.app.assistant.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GetAiResponseUseCase(
    private val settingsRepository: SettingsRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun execute(messagesArray: JSONArray): String? {
        val requestBodyJson = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", messagesArray)
            put("temperature", 1)
            put("top_p", 1)
            put("stop", null as Any?)
        }

        val requestBody = requestBodyJson.toString()
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
                val jsonObject = JSONObject(response)
                val choicesArray = jsonObject.getJSONArray("choices")
                if (choicesArray.length() > 0) {
                    val choiceObject = choicesArray.getJSONObject(0)
                    val messageObject = choiceObject.getJSONObject("message")
                    var content = messageObject.getString("content")

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
