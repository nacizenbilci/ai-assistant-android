package com.app.assistant.usecase

import com.app.assistant.model.Conversation
import com.app.assistant.util.Category
import com.google.mediapipe.tasks.text.textclassifier.TextClassifierResult
import java.util.Locale

class ProcessChatCommandUseCase(
    private val getAiResponseUseCase: GetAiResponseUseCase
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
        val negativePatterns = listOf(
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
        return negativePatterns.any { it.containsMatchIn(phrase) }
    }

    fun resolveCategory(results: TextClassifierResult, inputText: String): Category {
        val categories = results
            .classificationResult()
            .classifications()
            .first()
            .categories()

        val categoryWords = mapOf(
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
        chatHistory: List<Conversation>
    ): String? {
        val messages = mutableListOf<GroqMessage>()
        messages.add(GroqMessage(role = "system", content = systemContext))

        for (item in chatHistory) {
            val role = if (item.isMe) "user" else "assistant"
            messages.add(GroqMessage(role = role, content = item.englishText))
        }

        return getAiResponseUseCase.execute(messages)
    }
}
