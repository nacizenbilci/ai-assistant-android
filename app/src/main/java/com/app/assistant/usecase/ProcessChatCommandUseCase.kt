package com.app.assistant.usecase

import com.app.assistant.llm.LlmMessage
import com.app.assistant.model.Conversation
import com.app.assistant.repository.SettingsRepository
import com.app.assistant.util.Category
import com.google.mediapipe.tasks.text.textclassifier.TextClassifierResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.collect
import java.util.Locale

class ProcessChatCommandUseCase(
    private val getAiResponseUseCase: GetAiResponseUseCase,
    private val settingsRepository: SettingsRepository
) {
    fun cleanAndPunctuate(input: String): String {
        val trimmedInput = input.trim()
        if (trimmedInput.isEmpty()) return ""

        val lastChar = trimmedInput.last()
        return if (lastChar == '.' || lastChar == '?') {
            trimmedInput
        } else {
            val questionWords = listOf("wh", "how", "can", "do", "is", "are", "does", "did", "will", "could", "should", "would")
            if (questionWords.any { trimmedInput.lowercase().startsWith(it) }) {
                "$trimmedInput?"
            } else {
                trimmedInput
            }
        }
    }

    fun isNegativeOrNotRequired(phrase: String): Boolean {
        return negativePatterns.any { it.containsMatchIn(phrase) }
    }

    fun resolveCategory(results: TextClassifierResult, inputText: String): Category {
        val categories = results
            .classificationResult()
            .classifications()
            .first()
            .categories()



        val highestCategory = categories.maxByOrNull { it.score() }
        val highestCategoryEnum = highestCategory?.categoryName()?.uppercase()?.let { Category.valueOf(it) }

        val wordsToMatch = categoryWords[highestCategoryEnum] ?: emptyList()
        val containsWord = wordsToMatch.any { word -> word in inputText.lowercase() }

        val finalCategory = if (wordsToMatch.isNotEmpty() && !containsWord) {
            categories.firstOrNull { it.categoryName().equals(Category.OTHER.name, ignoreCase = true) }
        } else {
            highestCategory
        }

        return getCategoryFromString(finalCategory?.categoryName()?.uppercase() ?: "")
    }

    fun getCategoryFromString(categoryName: String): Category =
        Category.entries.find { it.name == categoryName.uppercase() } ?: Category.OTHER

    suspend fun getAiChatResponse(
        systemContext: String,
        chatHistory: List<Conversation>,
        isHandsFreeActive: Boolean = false,
        isVisionActive: Boolean = false,
        isScreenActive: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val messages = mapConversationsToLlmMessages(systemContext, chatHistory, isHandsFreeActive, isVisionActive, isScreenActive)
        getAiResponseUseCase.execute(messages)
    }

    fun getAiChatResponseStream(
        systemContext: String,
        chatHistory: List<Conversation>,
        isHandsFreeActive: Boolean = false,
        isVisionActive: Boolean = false,
        isScreenActive: Boolean = false
    ): kotlinx.coroutines.flow.Flow<String> = flow {
        val messages = withContext(Dispatchers.IO) {
            mapConversationsToLlmMessages(systemContext, chatHistory, isHandsFreeActive, isVisionActive, isScreenActive)
        }
        getAiResponseUseCase.executeStream(messages).collect {
            emit(it)
        }
    }.flowOn(Dispatchers.IO)

    private fun mapConversationsToLlmMessages(
        systemContext: String,
        chatHistory: List<Conversation>,
        isHandsFreeActive: Boolean = false,
        isVisionActive: Boolean = false,
        isScreenActive: Boolean = false
    ): List<LlmMessage> {
        val messages = mutableListOf<LlmMessage>()
        messages.add(LlmMessage(role = "system", content = systemContext))

        val latestImageAttachmentId = if (isHandsFreeActive && (isVisionActive || isScreenActive)) {
            chatHistory.flatMap { it.attachments }
                .lastOrNull { it.mimeType.startsWith("image/") }
                ?.id
        } else {
            null
        }

        for (item in chatHistory) {
            if (item.isLoading) continue
            val role = if (item.isMe) "user" else "assistant"
            val llmAttachments = if (item.attachments.isNotEmpty()) {
                val filtered = item.attachments.filter { att ->
                    if (att.mimeType.startsWith("text/") || att.mimeType == "application/json") {
                        false
                    } else {
                        when {
                            att.mimeType.startsWith("image/") -> {
                                val isSupported = settingsRepository.getIsImageSupported() || att.fileName.startsWith("vision_frame")
                                val isLatest = latestImageAttachmentId == null || att.id == latestImageAttachmentId
                                isSupported && isLatest
                            }
                            att.mimeType.startsWith("audio/") -> settingsRepository.getIsAudioSupported()
                            att.mimeType.startsWith("video/") -> settingsRepository.getIsVideoSupported()
                            att.mimeType.startsWith("application/pdf") -> settingsRepository.getIsDocumentSupported()
                            else -> false
                        }
                    }
                }
                if (filtered.isNotEmpty()) {
                    filtered.map { att ->
                        val file = java.io.File(att.filePath)
                        val base64 = if (file.exists()) {
                            val encryptedBytes = file.readBytes()
                            val decryptedBytes = com.app.assistant.db.EncryptionUtil.decryptFile(encryptedBytes, att.iv)
                            android.util.Base64.encodeToString(decryptedBytes, android.util.Base64.NO_WRAP)
                        } else {
                            ""
                        }
                        com.app.assistant.llm.LlmAttachment(
                            base64Data = base64,
                            mimeType = att.mimeType,
                            fileName = att.fileName
                        )
                    }
                } else null
            } else null
            messages.add(LlmMessage(role = role, content = item.text, attachments = llmAttachments))
        }
        return messages
    }

    companion object {
        private val negativePatterns = listOf(
            Regex("\\bno\\b", RegexOption.IGNORE_CASE),
            Regex("\\bnah\\b", RegexOption.IGNORE_CASE),
            Regex("\\bnot\\b.*", RegexOption.IGNORE_CASE),
            Regex("\\bnever\\b.*", RegexOption.IGNORE_CASE),
            Regex("\\bforget\\b.*", RegexOption.IGNORE_CASE),
            Regex("\\bleave\\b.*", RegexOption.IGNORE_CASE),
            Regex("\\bdrop\\b.*", RegexOption.IGNORE_CASE),
            Regex("\\bcancel\\b", RegexOption.IGNORE_CASE),
            Regex("\\babort\\b", RegexOption.IGNORE_CASE),
            Regex("\\bstop\\b", RegexOption.IGNORE_CASE),
            Regex("\\bquit\\b", RegexOption.IGNORE_CASE),
            Regex("\\bdisregard\\b", RegexOption.IGNORE_CASE),
            Regex("changed my mind", RegexOption.IGNORE_CASE),
            Regex("don't bother", RegexOption.IGNORE_CASE),
            Regex("let it go", RegexOption.IGNORE_CASE),
            Regex("scratch that", RegexOption.IGNORE_CASE),
            Regex("just kidding", RegexOption.IGNORE_CASE)
        )

        private val categoryWords = mapOf(
            Category.CALL to listOf("call", "phone", "ring", "connect", "need", "get", "dial"),
            Category.SONGS to listOf("music", "song", "play", "tune", "listen"),
            Category.ALARM to listOf("alarm", "wake", "set", "remind", "morning"),
            Category.REMINDER to listOf("remind", "notify", "alert", "remember"),
            Category.NAVIGATION to listOf(
                "navigate", "find", "show", "directions", "take me to",
                "where is", "get to", "go to", "navigation to", "way"
            ),
            Category.WEATHER to listOf(
                "sunny", "cloudy", "rain", "temperature", "umbrella", "weather",
                "outside", "report", "forecast", "carry", "going to", "how is", "will it be"
            ),
            Category.SETTINGS to emptyList(),
            Category.OTHER to emptyList()
        )
    }
}
