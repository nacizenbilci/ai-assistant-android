package com.app.assistant.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.assistant.BuildConfig
import com.app.assistant.classifier.TextClassifierHelper
import com.app.assistant.db.DynamicConversationRepository
import com.app.assistant.db.SyncStateList
import com.app.assistant.model.Conversation
import com.app.assistant.model.Group
import com.app.assistant.repository.ContactsRepository
import com.app.assistant.repository.SettingsRepository
import com.app.assistant.repository.WeatherRepository
import com.app.assistant.translation.TranslatorManager
import com.app.assistant.util.Category
import com.app.assistant.util.Constants.MAIN_CONTEXT
import com.app.assistant.util.LockState
import com.google.mediapipe.tasks.text.textclassifier.TextClassifierResult
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.lang.reflect.Modifier
import java.net.URI
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainViewModel(
    application: Application,
    private val speak: Boolean,
    private val settingsRepository: SettingsRepository,
    private val contactsRepository: ContactsRepository,
    private val weatherRepository: WeatherRepository,
) : AndroidViewModel(application) {
    var question = mutableStateOf("")

    // DB reference
    private val repository = DynamicConversationRepository(application)
    var chatList = SyncStateList(repository)
        private set
    var currentGroupId: Long = -1L
        private set
    var languages: List<Pair<String, String>>
    var showBottomSheet = mutableStateOf(false)
        private set
    private val _isLanguageLoading = MutableStateFlow(false)
    val isLanguageLoading: StateFlow<Boolean> = _isLanguageLoading
    private val _showToastEvent = MutableSharedFlow<String>()
    val showToastEvent = _showToastEvent.asSharedFlow()
    private val translatorManager = TranslatorManager()

    // For custom ui
    private val _isCustomUI = MutableStateFlow(false)
    val isCustomUI: StateFlow<Boolean> = _isCustomUI

    // For custom ui half page
    private val _isCustomUIHalfPage = MutableStateFlow(false)
    val isCustomUIHalfPage: StateFlow<Boolean> = _isCustomUIHalfPage

    // MutableStateFlow for isTranslationEnabled
    private val _isTranslationEnabled =
        MutableStateFlow(
            settingsRepository.getIsTranslationEnabled(),
        )
    val isTranslationEnabled: StateFlow<Boolean> = _isTranslationEnabled

    // MutableStateFlow for activeLanguageCode
    private val _activeLanguageCode =
        MutableStateFlow(
            settingsRepository.getActiveLanguageCode(),
        )
    private var _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _groupList = MutableStateFlow<List<Group>>(emptyList())
    val groupList: StateFlow<List<Group>> = _groupList.asStateFlow()

    // UI Event flow
    private val _uiEvent = MutableSharedFlow<UIEvent>()
    val uiEvent: SharedFlow<UIEvent> = _uiEvent.asSharedFlow()

    // Callback properties for SpeechToText
    var speechResultCallback: ((String) -> Unit)? = null
    var speechPartialResultCallback: ((String) -> Unit)? = null

    // Classifier
    private lateinit var classifierHelper: TextClassifierHelper

    // LockState
    var lockState: LockState = LockState.None
        set(value) {
            // Automatically clear any stored data when lockState is set to None
            if (value is LockState.None) {
                clearLockStateData()
            }
            field = value
        }

    // Classifier listener
    private val listener =
        object :
            TextClassifierHelper.TextResultsListener {
            override fun onResult(
                results: TextClassifierResult,
                inferenceTime: Long,
                inputText: String,
                itemId: Long,
                loadingItemId: Long,
                speak: Boolean,
            ) {
                processClassifierResponse(inferenceTime, results, inputText, itemId, loadingItemId, speak)
            }

            override fun onError(error: String) {
                Log.d("Classifier error", "Unable to classify$error")
            }
        }

    init {
        if (speak) {
            _isCustomUI.value = true
            _isCustomUIHalfPage.value = true
            _isListening.value = true
        }
        languages = getPublicStaticFinalStringsWithNames(TranslateLanguage::class.java)
        if (speak) {
            startSpeechRecognition()
        }
        initializeTextClassifier()
        initializeTranslator()
        loadGroup()
    }

    fun expandToFullScreen() {
        if (_isCustomUI.value) {
            _isCustomUI.value = false
        }
        if (_isCustomUIHalfPage.value) {
            _isCustomUIHalfPage.value = false
        }
    }

    private fun loadGroup() {
        viewModelScope.launch {
            _groupList.value = repository.loadAllGroups().toList()
        }
    }

    fun loadMessagesFromGroup(groupId: Long) {
        viewModelScope.launch {
            val newMessages = repository.loadMessagesForGroup(groupId)
            chatList.clear()
            chatList.addAll(newMessages)
            repository.currentGroupId = groupId
        }
    }

    private fun initializeTranslator() {
        if (getIsTranslationEnabled() && getActiveLanguageCode() != "") {
            setupTranslator(getActiveLanguageCode())
        }
    }

    private fun initializeTextClassifier() {
        // Create the classification helper that will do the heavy lifting
        classifierHelper =
            TextClassifierHelper(
                context = getApplication<Application>().applicationContext,
                listener = listener,
            )
        classifierHelper.initClassifier()
    }

    // Function to update isTranslationEnabled and persist the value
    fun updateTranslationEnabled(enabled: Boolean) {
        _isTranslationEnabled.value = enabled
        settingsRepository.setTranslationEnabled(enabled)
    }

    // Function to get the current value of isTranslationEnabled
    fun getIsTranslationEnabled(): Boolean = _isTranslationEnabled.value

    // Function to update activeLanguageCode and persist the value
    fun updateActiveLanguageCode(languageCode: String) {
        _activeLanguageCode.value = languageCode
        settingsRepository.setActiveLanguageCode(languageCode)
    }

    // Function to get the current value of ActiveLanguageCode
    fun getActiveLanguageCode(): String = _activeLanguageCode.value

    fun setSpeaking(speaking: Boolean) {
        _isSpeaking.value = speaking
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun stopTextToSpeech() {
        viewModelScope.launch {
            _uiEvent.emit(UIEvent.StopSpeaking)
        }
        _isSpeaking.value = false
    }

    private fun getPublicStaticFinalStringsWithNames(clazz: Class<*>): List<Pair<String, String>> {
        val publicStaticFinalStringsWithNames = mutableListOf<Pair<String, String>>()

        val fields = clazz.declaredFields
        for (field in fields) {
            val modifiers = field.modifiers
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                if (field.type == String::class.java) {
                    try {
                        val name = field.name
                        val value = field.get(null) as String
                        publicStaticFinalStringsWithNames.add(Pair(name, value))
                    } catch (e: IllegalAccessException) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return publicStaticFinalStringsWithNames
    }

    fun shutdownResources() {
        translatorManager.closeTranslator()
    }

    fun processQuestion(
        focusManager: FocusManager? = null,
        keyboardController: SoftwareKeyboardController? = null,
        speak: Boolean = false,
    ) {
        val originalQuestion = question.value
        focusManager?.clearFocus()
        keyboardController?.hide()
        question.value = ""

        viewModelScope.launch {
            val itemId: Long
            val newItem: Conversation =
                if (getIsTranslationEnabled()) {
                    Conversation(englishText = "", translatedText = originalQuestion, isMe = true)
                } else {
                    Conversation(englishText = originalQuestion, translatedText = "", isMe = true)
                }

            chatList.add(newItem)
            if (_isCustomUIHalfPage.value) {
                _isCustomUIHalfPage.value = false
            }

            itemId = newItem.id

            val loadingItem = Conversation(englishText = "", translatedText = "", isMe = false, isLoading = true)
            chatList.add(loadingItem)
            val loadingItemId = loadingItem.id

            val translatedQuestionInEnglish =
                if (getIsTranslationEnabled()) {
                    translatorManager.translateToEnglishSuspend(originalQuestion) ?: originalQuestion
                } else {
                    originalQuestion
                }

            val processedQuestion = cleanAndPunctuate(translatedQuestionInEnglish)
            chatList.indexOfFirst { it.id == itemId }.takeIf { it != -1 }?.let { index ->
                val updatedItem = chatList[index].copy(englishText = processedQuestion)
                chatList.set(index, updatedItem)
            }

            if (lockState != LockState.None && isNegativeOrNotRequired(processedQuestion)) {
                lockState = LockState.None
                classifierHelper.classify(processedQuestion, itemId, loadingItemId, speak)
            } else {
                when (val state = lockState) {
                    is LockState.LockAlarm -> handleAlarmLockState(itemId, loadingItemId, speak, state)
                    is LockState.LockReminder -> handleReminderLockState(itemId, loadingItemId, speak, state)
                    is LockState.LockNavigation -> TODO()
                    is LockState.None -> classifierHelper.classify(processedQuestion, itemId, loadingItemId, speak)
                }
            }
        }
    }

    fun processQuestion(
        focusManager: FocusManager? = null,
        keyboardController: SoftwareKeyboardController? = null,
        context: android.content.Context,
        speak: Boolean = false,
    ) {
        processQuestion(focusManager, keyboardController, speak)
    }

    private fun processClassifierResponse(
        inferenceTime: Long,
        results: TextClassifierResult,
        inputText: String,
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
    ) {
        val categories =
            results
                .classificationResult()
                .classifications()
                .first()
                .categories()

        // Define words for each category using Category enum
        val categoryWords =
            mapOf(
                Category.CALL to listOf("call", "phone", "ring", "connect", "need", "get", "dial"),
                Category.SONGS to listOf("music", "song", "play", "tune", "listen"),
                Category.ALARM to listOf("alarm", "wake", "set", "remind", "morning"),
                Category.REMINDER to listOf("remind", "notify", "alert", "remember"),
                Category.NAVIGATION to
                    listOf(
                        "navigate",
                        "find",
                        "show",
                        "directions",
                        "take me to",
                        "where is",
                        "get to",
                        "go to",
                        "navigation to",
                        "way",
                    ),
                Category.WEATHER to
                    listOf(
                        "sunny",
                        "cloudy",
                        "rain",
                        "temperature",
                        "umbrella",
                        "weather",
                        "outside",
                        "report",
                        "forecast",
                        "carry",
                        "going to",
                        "how is",
                        "will it be",
                    ),
                Category.SETTINGS to emptyList(),
                Category.OTHER to emptyList(), // "OTHER" doesn't need specific words
            )

        // Find the category with the highest score
        val highestCategory = categories.maxByOrNull { it.score() }
        val highestCategoryEnum = highestCategory?.categoryName()?.uppercase()?.let { Category.valueOf(it) }

        // Check if the highest category has specific words to verify
        val wordsToMatch = categoryWords[highestCategoryEnum] ?: emptyList()
        val containsWord = wordsToMatch.any { word -> word in inputText.lowercase() }

        // Set final category based on word verification
        val finalCategory =
            if (wordsToMatch.isNotEmpty() && !containsWord) {
                // Force the category to "OTHER" if no matching words found for the category
                categories.firstOrNull { it.categoryName().equals(Category.OTHER.name, ignoreCase = true) }
            } else {
                // Keep the highest category if matching words found or no words defined
                highestCategory
            }

        val categoryFromString = getCategoryFromString(finalCategory?.categoryName()?.uppercase() ?: "").toString()

        callCommand(categoryFromString, itemId, loadingItemId, speak)

        if (finalCategory != null) {
            chatList.find { it.id == itemId }?.category = getCategoryFromString(finalCategory.categoryName()).name
            Log.d("Classifier Inference Result", finalCategory.categoryName())
        }
    }

    private fun callCommand(
        categoryFromString: String,
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
    ) {
        when (categoryFromString) {
            Category.CALL.name -> {
                callContact(itemId, loadingItemId, speak, Category.CALL)
            }

            Category.OTHER.name -> {
                callAI(loadingItemId, speak, Category.OTHER)
            }

            Category.SETTINGS.name -> {
                callAI(loadingItemId, speak, Category.OTHER)
            }

            Category.SONGS.name -> {
                playSong(itemId, loadingItemId, speak, Category.SONGS)
            }

            Category.NAVIGATION.name -> {
                navigate(itemId, loadingItemId, speak, Category.NAVIGATION)
            }

            Category.WEATHER.name -> {
                fetchWeather(itemId, loadingItemId, speak, Category.WEATHER)
            }

            Category.REMINDER.name -> {
                setReminder(itemId, loadingItemId, speak, Category.REMINDER)
            }

            Category.ALARM.name -> {
                setAlarm(itemId, loadingItemId, speak, Category.ALARM)
            }
        }
    }

    // Function to map a category name to a Category enum
    private fun getCategoryFromString(categoryName: String): Category =
        Category.entries.find { it.name == categoryName.uppercase() } ?: Category.OTHER

    private fun callAI(
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        viewModelScope.launch {
            val response =
                withContext(Dispatchers.IO) {
                    extractFromAI(sendQuestionToApiLm(MAIN_CONTEXT))
                }

            processResponse(response, loadingItemId, speak, category = category)
        }
    }

    // Suspending function for translation
    private suspend fun TranslatorManager.translateToEnglishSuspend(text: String): String? =
        suspendCoroutine { continuation ->
            translateToEnglish(text) { translatedText ->
                continuation.resume(translatedText)
            }
        }

    private fun cleanAndPunctuate(input: String): String {
        val trimmedInput = input.trim()

        // Return immediately if the input is empty
        if (trimmedInput.isEmpty()) return ""

        // Check if the last character is already a punctuation (either "." or "?")
        val lastChar = trimmedInput.last()
        return if (lastChar == '.' || lastChar == '?') {
            trimmedInput
        } else {
            // List of common question words
            val questionWords = listOf("wh", "how", "can", "do", "is", "are", "does", "did", "will", "could", "should", "would")
            if (questionWords.any { trimmedInput.lowercase().startsWith(it) }) {
                "$trimmedInput?"
            } else {
                trimmedInput
            }
        }
    }

    private suspend fun processResponse(
        response: String?,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
        contentURL: String = "",
        navigationURI: URI = URI(""),
    ) {
        response?.let {
            withContext(Dispatchers.Main) {
                val plaintext = markdownToPlainText(it)

                if (getIsTranslationEnabled()) {
                    translatorManager.translateFromEnglish(plaintext) { translatedText ->
                        val finalText = translatedText ?: plaintext
                        chatList.removeAt(chatList.indexOfFirst { it.id == loadingItemId })
                        addConversationItem(plaintext, finalText, false, category, contentURL, navigationURI)
                        if (speak) {
                            speakResponse(finalText)
                        }
                    }
                } else {
                    chatList.removeAt(chatList.indexOfFirst { it.id == loadingItemId })
                    addConversationItem(response, "", false, category, contentURL, navigationURI)
                    if (speak) {
                        speakResponse(plaintext)
                    }
                }
            }
        }
    }

    private fun addConversationItem(
        englishText: String,
        translatedText: String,
        isUser: Boolean,
        category: Category,
        contentURL: String = "",
        navigationURI: URI = URI(""),
    ) {
        val conversation =
            Conversation(
                englishText = englishText,
                translatedText = translatedText,
                isMe = isUser,
                category = category.name,
                contentURL = contentURL,
                navigationURI = navigationURI,
            )
        chatList.add(conversation)
        if (chatList.size <= 2) {
            loadGroup()
        }
    }

    private fun speakResponse(plaintext: String) {
        viewModelScope.launch {
            _uiEvent.emit(UIEvent.SpeakText(plaintext))
        }
    }

    // Function to clear the conversation list
    fun clearBoxes() {
        viewModelScope.launch {
            chatList.clearAll().join()
            loadGroup()
        }
    }

    // Function to start new chat
    fun newChat() {
        viewModelScope.launch {
            chatList.clear()
            repository.currentGroupId = -1L
        }
    }

    fun deleteMessage(index: Int) {
        if (index != null) {
            chatList.removeAt(index)
        }
    }

    private suspend fun youtubeApiCall(query: String): Pair<String, String> {
        val client = OkHttpClient()
        val deferredResult = CompletableDeferred<Pair<String, String>>()

        val urlBuilder =
            "https://www.googleapis.com/youtube/v3/search"
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("key", loadYoutubeKey())
                ?.addQueryParameter("q", query)
                ?.addQueryParameter("type", "video")
                ?.addQueryParameter("part", "snippet")
                ?.addQueryParameter("maxResults", "1")
                ?.build()

        val request =
            Request
                .Builder()
                .url(urlBuilder.toString())
                .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    println("Request failed: $e")
                    deferredResult.completeExceptionally(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            deferredResult.complete(extractVideoIdAndThumbnail(body))
                        } else {
                            deferredResult.complete(Pair("", ""))
                        }
                    } else {
                        println("Request unsuccessful: ${response.code}")
                        if (response.code == 403) {
                            deferredResult.complete(Pair("Missing API Key", ""))
                        } else {
                            deferredResult.complete(Pair("", ""))
                        }
                    }
                    response.close()
                }
            },
        )

        return deferredResult.await()
    }

    fun extractVideoIdAndThumbnail(jsonString: String): Pair<String, String> {
        val jsonObject = JSONObject(jsonString)
        val itemsArray = jsonObject.optJSONArray("items") ?: return Pair("", "")

        if (itemsArray.length() > 0) {
            val firstItem = itemsArray.optJSONObject(0) ?: return Pair("", "")
            val idObject = firstItem.optJSONObject("id") ?: return Pair("", "")

            // Make sure it's a video, not a channel
            if (idObject.optString("kind") == "youtube#video") {
                val videoId = idObject.optString("videoId", "")
                val thumbnailUrl =
                    firstItem
                        .optJSONObject("snippet")
                        ?.optJSONObject("thumbnails")
                        ?.optJSONObject("high")
                        ?.optString("url", "") ?: ""

                return Pair(videoId, thumbnailUrl)
            }
        }

        return Pair("", "") // Return empty if no valid video is found
    }

    private suspend fun sendToAI(messagesArray: JSONArray): String {
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(
                    60,
                    TimeUnit.SECONDS,
                ).readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

        val requestBodyJson =
            JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", messagesArray)
                put("temperature", 1)
                put("top_p", 1)
                put("stop", null as Any?)
            }

        val requestBody =
            requestBodyJson
                .toString()
                .toRequestBody("application/json".toMediaTypeOrNull())

        val request =
            Request
                .Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${loadChatKey()}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 401) {
                            // API key is likely missing or invalid
                            return@withContext """{"choices":[{"message":{"content":"Your chat API key is missing or invalid."}}]}"""
                        }
                        throw IOException("Unexpected code $response")
                    }
                    response.body!!.string()
                }
            } catch (e: UnknownHostException) {
                """{"choices":[{"message":{"content":"Seems this device is offline. Maybe try checking data connection."}}]}"""
            } catch (e: Exception) {
                e.printStackTrace()
                """{"choices":[{"message":{"content":"Seems some issue in my server. Please try again."}}]}"""
            }
        }
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
                    content = content.replace(thinkTagRegex, "") // Remove <think>...</think>

                    // Remove any standalone opening or closing <think> tags
                    content = content.replace("<think>", "").replace("</think>", "")

                    return content.trim() // Clean and return the result
                } else {
                    return ""
                }
            } else {
                return "Seems some issue in my server. Please try again."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private suspend fun sendQuestionToApiLm(system_context: String): String {
        val messagesArray = JSONArray()

        messagesArray.put(JSONObject().put("role", "system").put("content", system_context))

        if (chatList.isNotEmpty()) {
            for (item in chatList) {
                val role = if (item.isMe) "user" else "assistant"
                val messageObject = JSONObject().put("role", role).put("content", item.englishText)
                messagesArray.put(messageObject)
            }
        }
        val response = sendToAI(messagesArray)
        return response
    }

    private fun startSpeechRecognition() {
        viewModelScope.launch {
            _uiEvent.emit(UIEvent.StartSpeechRecognition)
        }
    }

    fun startSpeechToText(
        context: android.content.Context,
        onResult: (String) -> Unit,
        onPartialResult: (String) -> Unit,
    ) {
        speechResultCallback = onResult
        speechPartialResultCallback = onPartialResult
        viewModelScope.launch {
            _uiEvent.emit(UIEvent.StartSpeechRecognition)
        }
    }

    fun onSpeechRecognized(recognizedText: String) {
        speechResultCallback?.invoke(recognizedText)
    }

    fun onSpeechPartialResult(recognizedText: String) {
        speechPartialResultCallback?.invoke(recognizedText)
    }

    fun markdownToPlainText(markdown: String): String {
        var plainText = markdown

        // Basic Markdown stripping (simplified)
        plainText = plainText.replace(Regex("#+ "), "") // Remove headers
        plainText = plainText.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // Bold
        plainText = plainText.replace(Regex("\\*(.*?)\\*"), "$1") // Italic
        plainText = plainText.replace(Regex("!\\[.*?\\]\\(.*?\\)"), "") // Images
        plainText = plainText.replace(Regex("\\[.*?\\]\\(.*?\\)"), "") // Links
        plainText = plainText.replace(Regex("`{1,3}.*?`{1,3}"), "") // Inline code
        plainText = plainText.replace(Regex("\\n\\n"), "\n") // Simplify newlines

        return plainText.trim()
    }

    // Update the selected item
    fun onItemSelected(selectedLanguageCode: String) {
        _isLanguageLoading.value = true
        setupTranslator(selectedLanguageCode, true)
        triggerToast("Downloading translation model.")
    }

    private fun setupTranslator(
        selectedLanguageCode: String,
        showCompletionToast: Boolean = false,
    ) {
        translatorManager.setupTranslators(selectedLanguageCode) { success ->
            if (success) {
                updateActiveLanguageCode(selectedLanguageCode)
                _isLanguageLoading.value = false
                showBottomSheet.value = false
                if (showCompletionToast) {
                    triggerToast("Download completed, its recommended to use selected language keyboard.")
                }
            } else {
                _isLanguageLoading.value = false
                triggerToast("Something went wrong, model download failed.")
            }
        }
    }

    private fun triggerToast(message: String) {
        viewModelScope.launch {
            _showToastEvent.emit(message)
        }
    }

    private fun isNegativeOrNotRequired(phrase: String): Boolean {
        val negativePatterns =
            listOf(
                Regex("\\bno\\b", RegexOption.IGNORE_CASE),
                Regex("\\bnah\\b", RegexOption.IGNORE_CASE),
                Regex("\\bnot\\b.*", RegexOption.IGNORE_CASE), // Matches "Not now", "Not anymore", etc.
                Regex("\\bnever\\b.*", RegexOption.IGNORE_CASE), // Matches "Never mind", "Never again", etc.
                Regex("\\bforget\\b.*", RegexOption.IGNORE_CASE), // Matches "Forget it", "Forget about it"
                Regex("\\bleave\\b.*", RegexOption.IGNORE_CASE), // Matches "Leave it"
                Regex("\\bdrop\\b.*", RegexOption.IGNORE_CASE), // Matches "Drop it"
                Regex("\\bcancel\\b", RegexOption.IGNORE_CASE),
                Regex("\\babort\\b", RegexOption.IGNORE_CASE),
                Regex("\\bstop\\b", RegexOption.IGNORE_CASE),
                Regex("\\bquit\\b", RegexOption.IGNORE_CASE),
                Regex("\\bdisregard\\b", RegexOption.IGNORE_CASE),
                Regex("changed my mind", RegexOption.IGNORE_CASE),
                Regex("don't bother", RegexOption.IGNORE_CASE),
                Regex("let it go", RegexOption.IGNORE_CASE),
                Regex("scratch that", RegexOption.IGNORE_CASE),
                Regex("just kidding", RegexOption.IGNORE_CASE),
            )

        return negativePatterns.any { it.containsMatchIn(phrase) }
    }

    // Function to clear data in lock states
    private fun clearLockStateData() {
        // Reset specific fields for all lock states
        if (lockState is LockState.LockAlarm) {
            (lockState as LockState.LockAlarm).day = null
        }
    }

    // For call
    private fun callContact(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        if (chatList.isNotEmpty()) {
            val prompt = chatList.find { it.id == itemId }?.englishText?.replace("\\p{Punct}+".toRegex(), "") ?: return

            viewModelScope.launch {
                val requiredPermissions =
                    arrayOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE,
                    ).filter {
                        ContextCompat.checkSelfPermission(getApplication(), it) != PackageManager.PERMISSION_GRANTED
                    }

                if (requiredPermissions.isNotEmpty()) {
                    _uiEvent.emit(UIEvent.RequestPermissions(requiredPermissions.toTypedArray(), 102))
                    processResponse(getRandomResponse(ResponseStrings.permissionContactsCall), loadingItemId, speak, Category.OTHER)
                    return@launch
                }

                val bestMatch = contactsRepository.searchContact(prompt)

                if (bestMatch != null) {
                    try {
                        val callIntent =
                            Intent(Intent.ACTION_CALL).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                data = Uri.parse("tel:${bestMatch.phoneNumber}")
                            }
                        _uiEvent.emit(UIEvent.StartIntent(callIntent))
                        processResponse(
                            bestMatch.name,
                            loadingItemId,
                            false,
                            category,
                            navigationURI = URI("tel:${bestMatch.phoneNumber.replace(" ", "")}"),
                        )
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error making call:", e)
                        processResponse("Sorry, failed to make call. Please try again.", loadingItemId, speak, Category.OTHER)
                    }
                } else {
                    processResponse("I cannot find such contact, please try again.", loadingItemId, speak, Category.OTHER)
                }
            }
        }
    }

    // For Song
    private fun playSong(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        try {
            val prompt = chatList.find { it.id == itemId }?.englishText?.replace("\\p{Punct}+".toRegex(), "") ?: return
            if (!prompt.lowercase().contains("play ")) {
                viewModelScope.launch {
                    processResponse(getRandomResponse(ResponseStrings.songNotFound), loadingItemId, speak, Category.OTHER)
                }
                return
            }
            val searchQuery = prompt.lowercase().substringAfter("play").trim()
            viewModelScope.launch {
                val (videoId, thumbnailUrl) = youtubeApiCall(searchQuery)
                if (videoId.isEmpty()) {
                    processResponse(getRandomResponse(ResponseStrings.songNotFound), loadingItemId, speak, Category.OTHER)
                } else if (videoId == "Missing API Key") {
                    val intent =
                        Intent(Intent.ACTION_SEARCH).apply {
                            setPackage("com.google.android.youtube")
                            putExtra("query", searchQuery)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    _uiEvent.emit(UIEvent.StartIntent(intent))
                    processResponse("Your Youtube API key is missing or invalid.", loadingItemId, speak, Category.OTHER)
                } else {
                    val intent =
                        Intent(Intent.ACTION_VIEW, Uri.parse("http://www.youtube.com/watch?v=$videoId")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    _uiEvent.emit(UIEvent.StartIntent(intent))
                    processResponse(
                        "Playing $searchQuery",
                        loadingItemId,
                        speak,
                        category,
                        thumbnailUrl,
                        URI("http://www.youtube.com/watch?v=$videoId"),
                    )
                }
            }
        } catch (e: Exception) {
            Log.d("Exception", e.message.toString())
            viewModelScope.launch {
                processResponse(getRandomResponse(ResponseStrings.genericError), loadingItemId, speak, Category.OTHER)
            }
        }
    }

    // For Navigation
    private fun navigate(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        try {
            if (chatList.isNotEmpty()) {
                val prompt = chatList.find { it.id == itemId }?.englishText?.replace("\\p{Punct}+".toRegex(), "") ?: return
                val location = extractLocation(prompt)
                viewModelScope.launch {
                    if (location != null) {
                        val gmmIntentUri = Uri.parse("google.navigation:q=$location")
                        val mapIntent =
                            Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                setPackage("com.google.android.apps.maps")
                            }
                        _uiEvent.emit(UIEvent.StartIntent(mapIntent))
                        val encodedURI =
                            java.net.URLEncoder.encode(
                                location,
                                java.nio.charset.StandardCharsets.UTF_8
                                    .toString(),
                            )
                        processResponse(
                            "Navigating to $location.",
                            loadingItemId,
                            speak,
                            category,
                            navigationURI = URI("google.navigation:q=$encodedURI"),
                        )
                    } else {
                        processResponse(getRandomResponse(ResponseStrings.locationNotFound), loadingItemId, speak, Category.OTHER)
                    }
                }
            } else {
                viewModelScope.launch {
                    processResponse(getRandomResponse(ResponseStrings.locationNotFound), loadingItemId, speak, Category.OTHER)
                }
            }
        } catch (e: Exception) {
            Log.d("Exception", e.message.toString())
            viewModelScope.launch {
                processResponse(getRandomResponse(ResponseStrings.genericError), loadingItemId, speak, Category.OTHER)
            }
        }
    }

    private fun extractLocation(command: String): String? {
        val regex =
            Regex(
                "(?<=to |find |show me |give me directions to |navigate me to |" +
                    "how do I get to |I'm on my way to |start a navigation to |" +
                    "where is |help me find |traffic like on the way to |way to )" +
                    "([A-Za-z0-9\\s&]+)",
            )

        val matchResult = regex.find(command)
        return matchResult?.value?.trim()
    }

    // For Weather
    private fun fetchWeather(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        try {
            if (chatList.isNotEmpty()) {
                val prompt = chatList.find { it.id == itemId }?.englishText?.replace("\\p{Punct}+".toRegex(), "") ?: return
                val location = weatherRepository.extractPlaceName(prompt)
                if (location.isNotEmpty()) {
                    viewModelScope.launch {
                        val coordinates = weatherRepository.getCoordinatesFromCity(location)
                        if (coordinates != null) {
                            val (lat, long) = coordinates
                            val weatherData = weatherRepository.getWeatherData(lat, long)
                            if (weatherData != null) {
                                weatherData.remove("latitude")
                                weatherData.remove("longitude")
                                val response =
                                    withContext(Dispatchers.IO) {
                                        extractFromAI(sendQuestionToAI(prompt, weatherData.toString()))
                                    }
                                if (!response.isNullOrEmpty()) {
                                    processResponse(
                                        response,
                                        loadingItemId,
                                        speak,
                                        category,
                                        navigationURI = URI("https://www.google.com/search?q=weather+$location"),
                                    )
                                }
                            } else {
                                processResponse(
                                    getRandomResponse(ResponseStrings.weatherReportUnavailable),
                                    loadingItemId,
                                    speak,
                                    Category.OTHER,
                                )
                            }
                        } else {
                            processResponse(
                                getRandomResponse(ResponseStrings.locationNotFound),
                                loadingItemId,
                                speak,
                                Category.OTHER,
                            )
                        }
                    }
                } else {
                    val isPermissionGranted =
                        ContextCompat.checkSelfPermission(
                            getApplication(),
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED

                    if (!isPermissionGranted) {
                        viewModelScope.launch {
                            _uiEvent.emit(
                                UIEvent.RequestPermissions(
                                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                                    103,
                                ),
                            )
                            processResponse(
                                getRandomResponse(ResponseStrings.permissionLocation),
                                loadingItemId,
                                speak,
                                Category.OTHER,
                            )
                        }
                    } else {
                        viewModelScope.launch {
                            _uiEvent.emit(
                                UIEvent.GetLocationForWeather(
                                    itemId,
                                    loadingItemId,
                                    speak,
                                    category.name,
                                    prompt,
                                ),
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("Exception", e.message.toString())
            viewModelScope.launch {
                processResponse(getRandomResponse(ResponseStrings.genericError), loadingItemId, speak, Category.OTHER)
            }
        }
    }

    fun onLocationReceived(
        lat: Double,
        long: Double,
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        categoryName: String,
        prompt: String,
    ) {
        val category = Category.valueOf(categoryName)
        val city = weatherRepository.getCityNameFromLocation(lat, long) ?: "your location"
        viewModelScope.launch {
            val weatherData = weatherRepository.getWeatherData(lat, long)
            if (weatherData != null) {
                weatherData.remove("latitude")
                weatherData.remove("longitude")
                val response =
                    withContext(Dispatchers.IO) {
                        extractFromAI(sendQuestionToAI(prompt, weatherData.toString()))
                    }
                if (!response.isNullOrEmpty()) {
                    processResponse(
                        response,
                        loadingItemId,
                        speak,
                        category,
                        navigationURI =
                            URI(
                                "https://www.google.com/search?q=weather+${
                                    java.net.URLEncoder.encode(
                                        city,
                                        java.nio.charset.StandardCharsets.UTF_8
                                            .toString(),
                                    )
                                }",
                            ),
                    )
                }
            } else {
                processResponse(getRandomResponse(ResponseStrings.weatherReportUnavailable), loadingItemId, speak, Category.OTHER)
            }
        }
    }

    fun onLocationFailed(
        loadingItemId: Long,
        speak: Boolean,
        errorType: String,
    ) {
        viewModelScope.launch {
            val responseText =
                when (errorType) {
                    "GPS_OFF" -> getRandomResponse(ResponseStrings.locationServiceOff)
                    "UNAVAILABLE" -> getRandomResponse(ResponseStrings.weatherReportUnavailable)
                    else -> getRandomResponse(ResponseStrings.locationUnknownSuggestCity)
                }
            processResponse(responseText, loadingItemId, speak, Category.OTHER)
        }
    }

    private suspend fun sendQuestionToAI(
        question: String,
        weatherData: String,
    ): String {
        val messagesArray = JSONArray()

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault(Locale.Category.FORMAT)).format(Calendar.getInstance().time)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault(Locale.Category.FORMAT)).format(Calendar.getInstance().time)
        val systemContext =
            "You are a smart weather assistant, up-to-date with the current date and time, " +
                "which is $date at $time. Please respond with an answer to the user's question based on " +
                "the latest weather data provided. No need to mention data or time; just answer naturally in 2 to 3 lines."
        messagesArray.put(JSONObject().put("role", "system").put("content", systemContext))
        messagesArray.put(JSONObject().put("role", "user").put("content", question + System.lineSeparator() + weatherData))

        val response = sendToAI(messagesArray)
        return response
    }

    // For Alarm
    private fun setAlarm(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        try {
            if (chatList.isNotEmpty()) {
                val prompt = chatList.find { it.id == itemId }?.englishText?.replace("[.?!]+".toRegex(), "") ?: return

                val dayRegex =
                    (
                        "(?i)\\b(today|tomorrow|next week|next weekend|" +
                            "((Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)" +
                            "( morning| evening| night| afternoon)?))\\b"
                    ).toRegex(RegexOption.IGNORE_CASE)
                val timeRegex = "(?i)\\b(\\d{1,2})(:(\\d{2}))?\\s*(AM|PM)?\\b".toRegex(RegexOption.IGNORE_CASE)
                val relativeTimeRegex =
                    (
                        "(?i)\\b(\\d+)?\\s*(?:and\\s*(half))?\\s*" +
                            "(hours?|hrs?|mins?|minutes?|seconds?|secs?)\\s*(?:and\\s*(\\d+))?\\s*" +
                            "(mins?|minutes?|secs?|seconds?)?\\s*(from now|later|next)?\\b"
                    ).toRegex(RegexOption.IGNORE_CASE)

                // Extract day, time, and relative time from the prompt
                val dayMatch = dayRegex.find(prompt)?.value?.lowercase(Locale.ROOT)
                val timeMatch = timeRegex.find(prompt)
                val relativeTimeMatch = relativeTimeRegex.find(prompt)

                if (timeMatch == null && relativeTimeMatch == null) {
                    viewModelScope.launch {
                        lockState = LockState.LockAlarm(day = dayMatch)
                        processResponse(
                            getRandomResponse(ResponseStrings.promptForTime),
                            loadingItemId,
                            speak,
                            Category.OTHER,
                        )
                    }
                } else {
                    viewModelScope.launch {
                        setAlarmFromPrompt(dayMatch, timeMatch, relativeTimeMatch)
                        processResponse(
                            getRandomResponse(ResponseStrings.alarmSetSuccess),
                            loadingItemId,
                            speak,
                            category,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("Exception", e.message.toString())
            viewModelScope.launch {
                processResponse(getRandomResponse(ResponseStrings.genericError), loadingItemId, speak, Category.OTHER)
            }
        }
    }

    private fun handleAlarmLockState(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        state: LockState.LockAlarm,
    ) {
        try {
            val prompt = chatList.find { it.id == itemId }?.englishText?.replace("[.?!]+".toRegex(), "") ?: return
            val timeRegex = "(\\d{1,2})(:(\\d{2}))?\\s*(AM|PM)?".toRegex(RegexOption.IGNORE_CASE)
            val relativeTimeRegex = "(\\d+)\\s*(hours?|mins?|minutes?)\\s*(from now|later|next)?".toRegex(RegexOption.IGNORE_CASE)

            val dayMatch = state.day
            val timeMatch = timeRegex.find(prompt)
            val relativeTimeMatch = relativeTimeRegex.find(prompt)

            if (timeMatch == null && relativeTimeMatch == null) {
                viewModelScope.launch {
                    processResponse(getRandomResponse(ResponseStrings.invalidTime), loadingItemId, speak, Category.OTHER)
                }
                return
            } else {
                lockState = LockState.None
                viewModelScope.launch {
                    setAlarmFromPrompt(dayMatch, timeMatch, relativeTimeMatch)
                    processResponse(getRandomResponse(ResponseStrings.alarmSetSuccess), loadingItemId, speak, Category.ALARM)
                }
            }
        } catch (e: Exception) {
            Log.d("Exception", e.message.toString())
            viewModelScope.launch {
                processResponse(getRandomResponse(ResponseStrings.genericError), loadingItemId, speak, Category.OTHER)
            }
        }
    }

    private suspend fun setAlarmFromPrompt(
        dayMatch: String?,
        timeMatch: MatchResult?,
        relativeTimeMatch: MatchResult?,
    ) {
        val calendar = Calendar.getInstance()

        if (relativeTimeMatch != null) {
            // Parse the first time unit
            val firstValue = relativeTimeMatch.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val halfValue = if (relativeTimeMatch.groupValues[2] == "half") 0.5 else 0.0 // Handle "half" scenario
            val firstUnit = relativeTimeMatch.groupValues[3].lowercase(Locale.ROOT)

            var secondValue = 0
            var secondUnit = ""

            // Check if a second time component exists (e.g., "30 minutes" in "3 hours and 30 minutes")
            if (relativeTimeMatch.groupValues[4].isNotEmpty()) {
                secondValue = relativeTimeMatch.groupValues[4].toInt()
                secondUnit = relativeTimeMatch.groupValues[5].lowercase(Locale.ROOT)
            }

            // Add first time unit
            when {
                firstUnit.contains("hour") || firstUnit.contains("hrs") -> {
                    calendar.add(Calendar.HOUR_OF_DAY, firstValue) // Add full hours
                    if (halfValue > 0) {
                        calendar.add(Calendar.MINUTE, 30) // Correctly add 30 minutes for "half"
                    }
                }

                firstUnit.contains("min") -> {
                    calendar.add(Calendar.MINUTE, firstValue)
                }
            }

            // Add second time unit if available
            when {
                secondUnit.contains("hour") || secondUnit.contains("hrs") -> calendar.add(Calendar.HOUR_OF_DAY, secondValue)

                secondUnit.contains("minute") ||
                    secondUnit.contains("minutes") ||
                    secondUnit.contains("min") -> calendar.add(Calendar.MINUTE, secondValue)
            }
        } else if (timeMatch != null) {
            // Handle absolute time
            val hour = timeMatch.groupValues[1].toInt()
            val minutes = timeMatch.groupValues[3].toIntOrNull() ?: 0 // Default minutes to 0 if missing
            val amPm = timeMatch.groupValues[4].uppercase(Locale.ROOT)

            // Convert time to 24-hour format
            val hour24 =
                when {
                    amPm == "PM" && hour != 12 -> hour + 12

                    amPm == "AM" && hour == 12 -> 0

                    // If no AM/PM, assume 24-hour format
                    else -> hour
                }

            calendar.set(Calendar.HOUR_OF_DAY, hour24)
            calendar.set(Calendar.MINUTE, minutes)
        }

        val repeatDays = mutableListOf<Int>()

        // Adjust day and determine repeat days (existing logic)
        when {
            dayMatch == "today" -> { /* No change needed */ }

            dayMatch == "tomorrow" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            dayMatch == "next week" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 7)
            }

            dayMatch == "next weekend" -> {
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysUntilSaturday = (Calendar.SATURDAY - currentDayOfWeek + 7) % 7
                calendar.add(Calendar.DAY_OF_MONTH, daysUntilSaturday)
            }

            dayMatch != null -> {
                val dayMap =
                    mapOf(
                        "sunday" to Calendar.SUNDAY,
                        "monday" to Calendar.MONDAY,
                        "tuesday" to Calendar.TUESDAY,
                        "wednesday" to Calendar.WEDNESDAY,
                        "thursday" to Calendar.THURSDAY,
                        "friday" to Calendar.FRIDAY,
                        "saturday" to Calendar.SATURDAY,
                    )

                dayMap.entries.forEach { (dayName, dayConstant) ->
                    if (dayMatch.contains(dayName)) {
                        repeatDays.add(dayConstant)
                    }
                }

                if (repeatDays.isEmpty()) {
                    val targetDay = dayMap.entries.firstOrNull { dayMatch.contains(it.key) }?.value
                    if (targetDay != null) {
                        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                        val daysToAdd = (targetDay - currentDayOfWeek + 7) % 7
                        calendar.add(Calendar.DAY_OF_MONTH, if (daysToAdd == 0) 7 else daysToAdd)
                    }
                }
            }
        }

        // Create the intent to set the alarm
        val intent =
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, "New Alarm")
                putExtra(AlarmClock.EXTRA_HOUR, calendar.get(Calendar.HOUR_OF_DAY))
                putExtra(AlarmClock.EXTRA_MINUTES, calendar.get(Calendar.MINUTE))
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)

                if (repeatDays.isNotEmpty()) {
                    putExtra(AlarmClock.EXTRA_DAYS, ArrayList(repeatDays))
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        // Start the intent via UI event
        _uiEvent.emit(UIEvent.StartIntent(intent))
    }

    // For Reminder
    private fun setReminder(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        try {
            if (chatList.isNotEmpty()) {
                val prompt = chatList.find { it.id == itemId }?.englishText?.replace("[.?!]+".toRegex(), "") ?: return

                val dayRegex =
                    (
                        "(?i)\\b(today|tomorrow|next week|next weekend|" +
                            "((Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)" +
                            "( morning| evening| night| afternoon)?))\\b"
                    ).toRegex(RegexOption.IGNORE_CASE)
                val timeRegex = "(?i)\\b(\\d{1,2})(:(\\d{2}))?\\s*(AM|PM)?\\b".toRegex(RegexOption.IGNORE_CASE)
                val relativeTimeRegex =
                    (
                        "(?i)\\b(\\d+)?\\s*(?:and\\s*(half))?\\s*" +
                            "(hours?|hrs?|mins?|minutes?|seconds?|secs?)\\s*(?:and\\s*(\\d+))?\\s*" +
                            "(mins?|minutes?|secs?|seconds?)?\\s*(from now|later|next)?\\b"
                    ).toRegex(RegexOption.IGNORE_CASE)
                val contextRegex =
                    "remind me(?: to| for| of| about| to create)?\\s+(.*?)(?:\\s+(at|on|in)\\s+.*)?$".toRegex(
                        RegexOption.IGNORE_CASE,
                    )

                val contextMatch = contextRegex.find(prompt)
                val context = contextMatch?.groupValues?.get(1)?.trim() ?: "Reminder"

                val timeMatch = timeRegex.find(prompt)
                val relativeTimeMatch = relativeTimeRegex.find(prompt)
                val dayMatch = dayRegex.find(prompt)?.value?.lowercase()

                if (timeMatch == null && relativeTimeMatch == null) {
                    viewModelScope.launch {
                        lockState = LockState.LockReminder(day = dayMatch, context = context)
                        processResponse(getRandomResponse(ResponseStrings.promptForTime), loadingItemId, speak, Category.OTHER)
                    }
                } else {
                    viewModelScope.launch {
                        setReminderFromPrompt(dayMatch, timeMatch, relativeTimeMatch, context)
                        processResponse(getRandomResponse(ResponseStrings.reminderSetSuccess), loadingItemId, speak, category)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("Exception", e.message.toString())
            viewModelScope.launch {
                processResponse(getRandomResponse(ResponseStrings.genericError), loadingItemId, speak, Category.OTHER)
            }
        }
    }

    private fun handleReminderLockState(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        state: LockState.LockReminder,
    ) {
        try {
            val prompt = chatList.find { it.id == itemId }?.englishText?.replace("[.?!]+".toRegex(), "") ?: return

            val timeRegex = "(\\d{1,2})(:(\\d{2}))?\\s*(AM|PM)?".toRegex(RegexOption.IGNORE_CASE)
            val relativeTimeRegex = "(\\d+)\\s*(hours?|mins?|minutes?)\\s*(from now|later|next)?".toRegex(RegexOption.IGNORE_CASE)

            val context = state.context ?: "Reminder"
            val dayMatch = state.day
            val timeMatch = timeRegex.find(prompt)
            val relativeTimeMatch = relativeTimeRegex.find(prompt)

            if (timeMatch == null && relativeTimeMatch == null) {
                viewModelScope.launch {
                    processResponse(getRandomResponse(ResponseStrings.invalidTime), loadingItemId, speak, Category.OTHER)
                }
                return
            } else {
                lockState = LockState.None
                viewModelScope.launch {
                    setReminderFromPrompt(dayMatch, timeMatch, relativeTimeMatch, context)
                    processResponse(getRandomResponse(ResponseStrings.reminderSetSuccess), loadingItemId, speak, Category.ALARM)
                }
            }
        } catch (e: Exception) {
            Log.d("Exception", e.message.toString())
            viewModelScope.launch {
                processResponse(getRandomResponse(ResponseStrings.genericError), loadingItemId, speak, Category.OTHER)
            }
        }
    }

    private suspend fun setReminderFromPrompt(
        dayMatch: String?,
        timeMatch: MatchResult?,
        relativeTimeMatch: MatchResult?,
        context: String,
    ) {
        val calendar = Calendar.getInstance()

        if (relativeTimeMatch != null) {
            // Parse the first time unit
            val firstValue = relativeTimeMatch.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val halfValue = if (relativeTimeMatch.groupValues[2] == "half") 0.5 else 0.0 // Handle "half" scenario
            val firstUnit = relativeTimeMatch.groupValues[3].lowercase(Locale.ROOT)

            var secondValue = 0
            var secondUnit = ""

            // Check if a second time component exists (e.g., "30 minutes" in "3 hours and 30 minutes")
            if (relativeTimeMatch.groupValues[4].isNotEmpty()) {
                secondValue = relativeTimeMatch.groupValues[4].toInt()
                secondUnit = relativeTimeMatch.groupValues[5].lowercase(Locale.ROOT)
            }

            // Add first time unit
            when {
                firstUnit.contains("hour") || firstUnit.contains("hrs") -> {
                    calendar.add(Calendar.HOUR_OF_DAY, firstValue) // Add full hours
                    if (halfValue > 0) {
                        calendar.add(Calendar.MINUTE, 30) // Correctly add 30 minutes for "half"
                    }
                }

                firstUnit.contains("min") -> {
                    calendar.add(Calendar.MINUTE, firstValue)
                }
            }

            // Add second time unit if available
            when {
                secondUnit.contains("hour") || secondUnit.contains("hrs") -> calendar.add(Calendar.HOUR_OF_DAY, secondValue)

                secondUnit.contains("minute") ||
                    secondUnit.contains("minutes") ||
                    secondUnit.contains("min") -> calendar.add(Calendar.MINUTE, secondValue)
            }
        } else if (timeMatch != null) {
            // Handle absolute time
            val hour = timeMatch.groupValues[1].toInt()
            val minutes = timeMatch.groupValues[3].toIntOrNull() ?: 0 // Default minutes to 0 if missing
            val amPm = timeMatch.groupValues[4].uppercase(Locale.ROOT)

            // Convert time to 24-hour format
            val hour24 =
                when {
                    amPm == "PM" && hour != 12 -> hour + 12

                    amPm == "AM" && hour == 12 -> 0

                    // If no AM/PM, assume 24-hour format
                    else -> hour
                }

            calendar.set(Calendar.HOUR_OF_DAY, hour24)
            calendar.set(Calendar.MINUTE, minutes)
        }

        val repeatDays = mutableListOf<Int>()

        // Adjust day and determine repeat days (existing logic)
        when {
            dayMatch == "today" -> { /* No change needed */ }

            dayMatch == "tomorrow" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            dayMatch == "next week" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 7)
            }

            dayMatch == "next weekend" -> {
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysUntilSaturday = (Calendar.SATURDAY - currentDayOfWeek + 7) % 7
                calendar.add(Calendar.DAY_OF_MONTH, daysUntilSaturday)
            }

            dayMatch != null -> {
                val dayMap =
                    mapOf(
                        "sunday" to Calendar.SUNDAY,
                        "monday" to Calendar.MONDAY,
                        "tuesday" to Calendar.TUESDAY,
                        "wednesday" to Calendar.WEDNESDAY,
                        "thursday" to Calendar.THURSDAY,
                        "friday" to Calendar.FRIDAY,
                        "saturday" to Calendar.SATURDAY,
                    )

                dayMap.entries.forEach { (dayName, dayConstant) ->
                    if (dayMatch.contains(dayName)) {
                        repeatDays.add(dayConstant)
                    }
                }

                if (repeatDays.isEmpty()) {
                    val targetDay = dayMap.entries.firstOrNull { dayMatch.contains(it.key) }?.value
                    if (targetDay != null) {
                        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                        val daysToAdd = (targetDay - currentDayOfWeek + 7) % 7
                        calendar.add(Calendar.DAY_OF_MONTH, if (daysToAdd == 0) 7 else daysToAdd)
                    }
                }
            }
        }

        // Create the intent to set the alarm
        val intent =
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, context)
                putExtra(AlarmClock.EXTRA_HOUR, calendar.get(Calendar.HOUR_OF_DAY))
                putExtra(AlarmClock.EXTRA_MINUTES, calendar.get(Calendar.MINUTE))
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                putExtra(AlarmClock.EXTRA_RINGTONE, AlarmClock.VALUE_RINGTONE_SILENT)

                if (repeatDays.isNotEmpty()) {
                    putExtra(AlarmClock.EXTRA_DAYS, ArrayList(repeatDays))
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        // Start the intent via UI event
        _uiEvent.emit(UIEvent.StartIntent(intent))
    }

    private fun getRandomResponse(responses: List<String>): String =
        responses.randomOrNull() ?: responses.firstOrNull() ?: "An unexpected error occurred."

    object ResponseStrings {
        val permissionContactsCall =
            listOf(
                "I need permission to access your contacts and make phone call. Please allow and try again.",
                "Hey, I’ll need access to your contacts and calling first. Can you allow that?",
                "Looks like I don’t have permission to call yet. Please enable it and retry.",
                "I can help with that, but I need contacts and call access first.",
                "Please grant me contacts and calling permission so I can make the call for you.",
                "I can’t make calls without your permission. Could you turn it on?",
            )

        val permissionLocation =
            listOf(
                "I need permission to access your location. Please try again.",
                "I can get the weather, but I’ll need your location first.",
                "Looks like location access isn’t granted. Please allow it and try again.",
                "Hey, could you enable location permission so I can show the weather?",
                "To fetch the forecast, I’ll need your location. Can you grant access?",
                "Without your location, I can’t check the weather. Please turn it on.",
            )

        val locationServiceOff =
            listOf(
                "I need to know your location for that. Please turn on your location and try again.",
                "looks like location is off. Could you enable it?",
                "I can’t continue without your location. Please turn it on.",
                "Hey, I’ll need your location for this. Can you switch it on?",
                "Looks like your location services are disabled. Please activate them.",
                "Please turn on location, then I’ll be able to continue.",
            )

        val callFailed =
            listOf(
                "Sorry, failed to make call. Please try again.",
                "Oops, the call didn’t go through. Want to retry?",
                "I couldn’t complete the call. Please try again.",
                "Looks like that call failed. Give it another shot?",
                "Something went wrong with the call. Please try again later.",
                "I wasn’t able to connect the call. Can we try once more?",
            )

        val contactNotFound =
            listOf(
                "I cannot find such contact, please try again.",
                "I didn’t find that contact in your list.",
                "No contact matched that name, could you check and retry?",
                "Sorry, I couldn’t locate that person in your contacts.",
                "Looks like that name isn’t saved in your contacts.",
                "I couldn’t find that contact. Maybe try with a different name?",
            )

        val songNotFound =
            listOf(
                "I can not find such song, please try again.",
                "Sorry, I couldn’t find that track.",
                "no song matched your request. Want to try another?",
                "I wasn’t able to locate that song. Please retry.",
                "Looks like that song isn’t available right now.",
                "I couldn’t find that one. Maybe try with a different title?",
            )

        val locationNotFound =
            listOf(
                "I can not find such location, please try again.",
                "Sorry, I couldn’t figure out where that is.",
                "I wasn’t able to locate that place.",
                "No results for that location. Can you check and try again?",
                "Looks like that place isn’t on my map data.",
                "I couldn’t find that spot. Maybe try with a different name?",
            )

        val weatherReportUnavailable =
            listOf(
                "Seems weather report is not available, please try again.",
                "Sorry, I couldn’t get the weather right now.",
                "The weather service isn’t responding. Please try later.",
                "Looks like weather data is down at the moment.",
                "I wasn’t able to fetch the forecast. Can you retry later?",
                "Weather info isn’t available right now. Please check back soon.",
            )

        val locationUnknownSuggestCity =
            listOf(
                "Your location is not available to me. Please try again with your city name.",
                "I couldn’t detect your location. Could you tell me your city instead?",
                "Looks like location services aren’t working. Please provide your city name.",
                "I’m not getting your location right now. Can you enter your city?",
                "Sorry, I can’t access your current location. A city name would help.",
                "Your location seems unavailable. Please try with your city name.",
            )

        val invalidTime =
            listOf(
                "That doesn't seems like an actual time. Please try again.",
                "I didn’t recognize that as a valid time.",
                "That time format looks off. Could you retry?",
                "Sorry, I couldn’t understand that time input.",
                "That doesn’t look like a proper time. Please try again.",
                "Can you give me a valid time so I can continue?",
            )

        val alarmSetSuccess =
            listOf(
                "Alarm set successfully...",
                "Done! Your alarm is ready.",
                "Great, I’ve set the alarm for you.",
                "All set, your alarm has been scheduled.",
                "Alarm saved successfully.",
                "Okay, I’ve configured the alarm as requested.",
            )

        val reminderSetSuccess =
            listOf(
                "Reminder set successfully...",
                "Done! Your reminder is ready.",
                "Great, I’ve saved the reminder for you.",
                "All set, your reminder has been scheduled.",
                "Reminder saved successfully.",
                "Okay, I’ve created the reminder as requested.",
            )

        val promptForTime =
            listOf(
                "Sure, at what time?",
                "Alright, when should I set it?",
                "Okay, what time would you like?",
                "Got it, please tell me the time.",
                "When do you want me to set it for?",
                "Sure thing, what time works for you?",
            )

        val genericError =
            listOf(
                "Something went wrong, please try again.",
                "Oops, that didn’t work. Please retry.",
                "I ran into an issue. Can you try again?",
                "Sorry, something broke there. Please try once more.",
                "That didn’t go through. Could you retry?",
                "An error popped up. Let’s try that again.",
            )
    }

    fun saveKeys(
        youtubeApiKey: String,
        chatApiKey: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.saveKeys(youtubeApiKey, chatApiKey)
            Log.d("MainViewModel", "API Keys saved securely.")
        }
    }

    fun loadYoutubeKey(): String? {
        var youtubeKey = settingsRepository.getYoutubeApiKey()
        if (youtubeKey.isNullOrBlank()) {
            Log.d("MainViewModel", "YouTube key not found in SharedPreferences, checking BuildConfig.")
            youtubeKey = BuildConfig.YOUTUBE_API_KEY
        }
        return youtubeKey
    }

    fun loadChatKey(): String? {
        var chatKey = settingsRepository.getChatApiKey()
        if (chatKey.isNullOrBlank()) {
            Log.d("MainViewModel", "Chat key not found in SharedPreferences, checking BuildConfig.")
            chatKey = BuildConfig.GROQ_API_KEY
        }
        return chatKey
    }
}
