package com.app.assistant.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.assistant.db.DynamicConversationRepository
import com.app.assistant.db.SyncStateList
import com.app.assistant.model.Conversation
import com.app.assistant.model.Group
import com.app.assistant.repository.SettingsRepository
import com.app.assistant.translation.TranslatorManager
import com.app.assistant.usecase.CallContactUseCase
import com.app.assistant.usecase.GetWeatherUseCase
import com.app.assistant.usecase.NavigateUseCase
import com.app.assistant.usecase.PlaySongUseCase
import com.app.assistant.usecase.ProcessChatCommandUseCase
import com.app.assistant.usecase.SetAlarmUseCase
import com.app.assistant.usecase.SetReminderUseCase
import com.app.assistant.util.Category
import com.app.assistant.util.Constants.MAIN_CONTEXT
import com.app.assistant.util.LockState
import com.app.assistant.classifier.TextClassifierHelper
import com.google.mediapipe.tasks.text.textclassifier.TextClassifierResult
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.lang.reflect.Modifier
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainViewModel(
    application: Application,
    private val speak: Boolean,
    private val settingsRepository: SettingsRepository,
    private val callContactUseCase: CallContactUseCase,
    private val playSongUseCase: PlaySongUseCase,
    private val navigateUseCase: NavigateUseCase,
    private val getWeatherUseCase: GetWeatherUseCase,
    private val setAlarmUseCase: SetAlarmUseCase,
    private val setReminderUseCase: SetReminderUseCase,
    private val processChatCommandUseCase: ProcessChatCommandUseCase,
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
    private val _isTranslationEnabled = MutableStateFlow(
        settingsRepository.getIsTranslationEnabled()
    )
    val isTranslationEnabled: StateFlow<Boolean> = _isTranslationEnabled

    // MutableStateFlow for activeLanguageCode
    private val _activeLanguageCode = MutableStateFlow(
        settingsRepository.getActiveLanguageCode()
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
    private val listener = object : TextClassifierHelper.TextResultsListener {
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
        classifierHelper = TextClassifierHelper(
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
            val newItem: Conversation = if (getIsTranslationEnabled()) {
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

            val translatedQuestionInEnglish = if (getIsTranslationEnabled()) {
                translatorManager.translateToEnglishSuspend(originalQuestion) ?: originalQuestion
            } else {
                originalQuestion
            }

            val processedQuestion = processChatCommandUseCase.cleanAndPunctuate(translatedQuestionInEnglish)
            chatList.indexOfFirst { it.id == itemId }.takeIf { it != -1 }?.let { index ->
                val updatedItem = chatList[index].copy(englishText = processedQuestion)
                chatList.set(index, updatedItem)
            }

            if (lockState != LockState.None && processChatCommandUseCase.isNegativeOrNotRequired(processedQuestion)) {
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
        val finalCategory = processChatCommandUseCase.resolveCategory(results, inputText)
        callCommand(finalCategory.name, itemId, loadingItemId, speak)

        chatList.find { it.id == itemId }?.let {
            it.category = finalCategory.name
        }
        Log.d("Classifier Inference Result", finalCategory.name)
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

    private fun callAI(
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        viewModelScope.launch {
            val response = processChatCommandUseCase.getAiChatResponse(MAIN_CONTEXT, chatList.toList())
            processResponse(response, loadingItemId, speak, category = category)
        }
    }

    private suspend fun TranslatorManager.translateToEnglishSuspend(text: String): String? =
        suspendCoroutine { continuation ->
            translateToEnglish(text) { translatedText ->
                continuation.resume(translatedText)
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
            val plaintext = com.app.assistant.util.MarkdownUtils.markdownToPlainText(it)

            if (getIsTranslationEnabled()) {
                translatorManager.translateFromEnglish(plaintext) { translatedText ->
                    val finalText = translatedText ?: plaintext
                    val index = chatList.indexOfFirst { item -> item.id == loadingItemId }
                    if (index != -1) {
                        chatList.removeAt(index)
                    }
                    addConversationItem(plaintext, finalText, false, category, contentURL, navigationURI)
                    if (speak) {
                        speakResponse(finalText)
                    }
                }
            } else {
                val index = chatList.indexOfFirst { item -> item.id == loadingItemId }
                if (index != -1) {
                    chatList.removeAt(index)
                }
                addConversationItem(response, "", false, category, contentURL, navigationURI)
                if (speak) {
                    speakResponse(plaintext)
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
        val conversation = Conversation(
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

    private fun clearLockStateData() {
        if (lockState is LockState.LockAlarm) {
            (lockState as LockState.LockAlarm).day = null
        }
    }

    // Call Contact UseCase Delegate
    private fun callContact(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        if (chatList.isNotEmpty()) {
            val prompt = chatList.find { it.id == itemId }?.englishText ?: return
            viewModelScope.launch {
                callContactUseCase.execute(
                    prompt = prompt,
                    onPermissionRequest = { permissions ->
                        _uiEvent.emit(UIEvent.RequestPermissions(permissions, 102))
                        processResponse(getRandomResponse(ResponseStrings.permissionContactsCall), loadingItemId, speak, Category.OTHER)
                    },
                    onIntentTriggered = { intent ->
                        _uiEvent.emit(UIEvent.StartIntent(intent))
                    },
                    onSuccess = { name, dialUri ->
                        processResponse(
                            name,
                            loadingItemId,
                            false,
                            category,
                            navigationURI = dialUri
                        )
                    },
                    onFailure = { errorMsg ->
                        processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
                    }
                )
            }
        }
    }

    // Play Song UseCase Delegate
    private fun playSong(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        val prompt = chatList.find { it.id == itemId }?.englishText ?: return
        viewModelScope.launch {
            playSongUseCase.execute(
                prompt = prompt,
                onIntentTriggered = { intent ->
                    _uiEvent.emit(UIEvent.StartIntent(intent))
                },
                onSuccess = { songName, videoId, thumbnailUrl, videoUri ->
                    processResponse(
                        "Playing $songName",
                        loadingItemId,
                        speak,
                        category,
                        thumbnailUrl,
                        videoUri
                    )
                },
                onMissingApiKey = { searchQuery ->
                    processResponse("Your Youtube API key is missing or invalid.", loadingItemId, speak, Category.OTHER)
                },
                onFailure = { errorMsg ->
                    processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
                }
            )
        }
    }

    // Navigate UseCase Delegate
    private fun navigate(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        val prompt = chatList.find { it.id == itemId }?.englishText ?: return
        viewModelScope.launch {
            navigateUseCase.execute(
                prompt = prompt,
                onIntentTriggered = { intent ->
                    _uiEvent.emit(UIEvent.StartIntent(intent))
                },
                onSuccess = { location, navigationUri ->
                    processResponse(
                        "Navigating to $location.",
                        loadingItemId,
                        speak,
                        category,
                        navigationURI = navigationUri
                    )
                },
                onFailure = { errorMsg ->
                    processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
                }
            )
        }
    }

    // Weather UseCase Delegate
    private fun fetchWeather(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        val prompt = chatList.find { it.id == itemId }?.englishText ?: return
        viewModelScope.launch {
            getWeatherUseCase.execute(
                prompt = prompt,
                onPermissionRequest = { permissions ->
                    _uiEvent.emit(UIEvent.RequestPermissions(permissions, 103))
                    processResponse(getRandomResponse(ResponseStrings.permissionLocation), loadingItemId, speak, Category.OTHER)
                },
                onLocationRequest = {
                    _uiEvent.emit(
                        UIEvent.GetLocationForWeather(
                            itemId,
                            loadingItemId,
                            speak,
                            category.name,
                            prompt
                        )
                    )
                },
                onSuccess = { response, location ->
                    processResponse(
                        response,
                        loadingItemId,
                        speak,
                        category,
                        navigationURI = URI("https://www.google.com/search?q=weather+$location")
                    )
                },
                onFailure = { errorMsg ->
                    processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
                }
            )
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
        viewModelScope.launch {
            getWeatherUseCase.processLocationWeather(
                lat = lat,
                long = long,
                prompt = prompt,
                onSuccess = { response, city ->
                    processResponse(
                        response,
                        loadingItemId,
                        speak,
                        Category.valueOf(categoryName),
                        navigationURI = URI("https://www.google.com/search?q=weather+${
                            java.net.URLEncoder.encode(city, java.nio.charset.StandardCharsets.UTF_8.toString())
                        }")
                    )
                },
                onFailure = { errorMsg ->
                    processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
                }
            )
        }
    }

    fun onLocationFailed(
        loadingItemId: Long,
        speak: Boolean,
        errorType: String,
    ) {
        viewModelScope.launch {
            val responseText = when (errorType) {
                "GPS_OFF" -> getRandomResponse(ResponseStrings.locationServiceOff)
                "UNAVAILABLE" -> getRandomResponse(ResponseStrings.weatherReportUnavailable)
                else -> getRandomResponse(ResponseStrings.locationUnknownSuggestCity)
            }
            processResponse(responseText, loadingItemId, speak, Category.OTHER)
        }
    }

    // Set Alarm UseCase Delegate
    private fun setAlarm(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        val prompt = chatList.find { it.id == itemId }?.englishText ?: return
        viewModelScope.launch {
            setAlarmUseCase.execute(
                prompt = prompt,
                onPromptForTime = { dayMatch ->
                    lockState = LockState.LockAlarm(day = dayMatch)
                    processResponse(
                        getRandomResponse(ResponseStrings.promptForTime),
                        loadingItemId,
                        speak,
                        Category.OTHER
                    )
                },
                onSuccess = { intent ->
                    _uiEvent.emit(UIEvent.StartIntent(intent))
                    processResponse(
                        getRandomResponse(ResponseStrings.alarmSetSuccess),
                        loadingItemId,
                        speak,
                        category
                    )
                },
                onFailure = { errorMsg ->
                    processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
                }
            )
        }
    }

    private fun handleAlarmLockState(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        state: LockState.LockAlarm,
    ) {
        val prompt = chatList.find { it.id == itemId }?.englishText ?: return
        viewModelScope.launch {
            setAlarmUseCase.execute(
                prompt = prompt,
                dayOverride = state.day,
                onPromptForTime = {
                    processResponse(getRandomResponse(ResponseStrings.invalidTime), loadingItemId, speak, Category.OTHER)
                },
                onSuccess = { intent ->
                    lockState = LockState.None
                    _uiEvent.emit(UIEvent.StartIntent(intent))
                    processResponse(getRandomResponse(ResponseStrings.alarmSetSuccess), loadingItemId, speak, Category.ALARM)
                },
                onFailure = { errorMsg ->
                    processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
                }
            )
        }
    }

    // Set Reminder UseCase Delegate
    private fun setReminder(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        val prompt = chatList.find { it.id == itemId }?.englishText ?: return
        viewModelScope.launch {
            setReminderUseCase.execute(
                prompt = prompt,
                onPromptForTime = { dayMatch, context ->
                    lockState = LockState.LockReminder(day = dayMatch, context = context)
                    processResponse(getRandomResponse(ResponseStrings.promptForTime), loadingItemId, speak, Category.OTHER)
                },
                onSuccess = { intent ->
                    _uiEvent.emit(UIEvent.StartIntent(intent))
                    processResponse(getRandomResponse(ResponseStrings.reminderSetSuccess), loadingItemId, speak, category)
                },
                onFailure = { errorMsg ->
                    processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
                }
            )
        }
    }

    private fun handleReminderLockState(
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
        state: LockState.LockReminder,
    ) {
        val prompt = chatList.find { it.id == itemId }?.englishText ?: return
        viewModelScope.launch {
            setReminderUseCase.execute(
                prompt = prompt,
                dayOverride = state.day,
                contextOverride = state.context,
                onPromptForTime = { _, _ ->
                    processResponse(getRandomResponse(ResponseStrings.invalidTime), loadingItemId, speak, Category.OTHER)
                },
                onSuccess = { intent ->
                    lockState = LockState.None
                    _uiEvent.emit(UIEvent.StartIntent(intent))
                    processResponse(getRandomResponse(ResponseStrings.reminderSetSuccess), loadingItemId, speak, Category.ALARM)
                },
                onFailure = { errorMsg ->
                    processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
                }
            )
        }
    }

    private fun getRandomResponse(responses: List<String>): String =
        responses.randomOrNull() ?: responses.firstOrNull() ?: "An unexpected error occurred."

    object ResponseStrings {
        val permissionContactsCall = listOf(
            "I need permission to access your contacts and make phone call. Please allow and try again.",
            "Hey, I’ll need access to your contacts and calling first. Can you allow that?",
            "Looks like I don’t have permission to call yet. Please enable it and retry.",
            "I can help with that, but I need contacts and call access first.",
            "Please grant me contacts and calling permission so I can make the call for you.",
            "I can’t make calls without your permission. Could you turn it on?"
        )

        val permissionLocation = listOf(
            "I need permission to access your location. Please try again.",
            "I can get the weather, but I’ll need your location first.",
            "Looks like location access isn’t granted. Please allow it and try again.",
            "Hey, could you enable location permission so I can show the weather?",
            "To fetch the forecast, I’ll need your location. Can you grant access?",
            "Without your location, I can’t check the weather. Please turn it on."
        )

        val locationServiceOff = listOf(
            "I need to know your location for that. Please turn on your location and try again.",
            "looks like location is off. Could you enable it?",
            "I can’t continue without your location. Please turn it on.",
            "Hey, I’ll need your location for this. Can you switch it on?",
            "Looks like your location services are disabled. Please activate them.",
            "Please turn on location, then I’ll be able to continue."
        )

        val callFailed = listOf(
            "Sorry, failed to make call. Please try again.",
            "Oops, the call didn’t go through. Want to retry?",
            "I couldn’t complete the call. Please try again.",
            "Looks like that call failed. Give it another shot?",
            "Something went wrong with the call. Please try again later.",
            "I wasn’t able to connect the call. Can we try once more?"
        )

        val contactNotFound = listOf(
            "I cannot find such contact, please try again.",
            "I didn’t find that contact in your list.",
            "No contact matched that name, could you check and retry?",
            "Sorry, I couldn’t locate that person in your contacts.",
            "Looks like that name isn’t saved in your contacts.",
            "I couldn’t find that contact. Maybe try with a different name?"
        )

        val songNotFound = listOf(
            "I can not find such song, please try again.",
            "Sorry, I couldn’t find that track.",
            "no song matched your request. Want to try another?",
            "I wasn’t able to locate that song. Please retry.",
            "Looks like that song isn’t available right now.",
            "I couldn’t find that one. Maybe try with a different title?"
        )

        val locationNotFound = listOf(
            "I can not find such location, please try again.",
            "Sorry, I couldn’t figure out where that is.",
            "I wasn’t able to locate that place.",
            "No results for that location. Can you check and try again?",
            "Looks like that place isn’t on my map data.",
            "I couldn’t find that spot. Maybe try with a different name?"
        )

        val weatherReportUnavailable = listOf(
            "Seems weather report is not available, please try again.",
            "Sorry, I couldn’t get the weather right now.",
            "The weather service isn’t responding. Please try later.",
            "Looks like weather data is down at the moment.",
            "I wasn’t able to fetch the forecast. Can you retry later?",
            "Weather info isn’t available right now. Please check back soon."
        )

        val locationUnknownSuggestCity = listOf(
            "Your location is not available to me. Please try again with your city name.",
            "I couldn’t detect your location. Could you tell me your city instead?",
            "Looks like location services aren’t working. Please provide your city name.",
            "I’m not getting your location right now. Can you enter your city?",
            "Sorry, I can’t access your current location. A city name would help.",
            "Your location seems unavailable. Please try with your city name."
        )

        val invalidTime = listOf(
            "That doesn't seems like an actual time. Please try again.",
            "I didn’t recognize that as a valid time.",
            "That time format looks off. Could you retry?",
            "Sorry, I couldn’t understand that time input.",
            "That doesn’t look like a proper time. Please try again.",
            "Can you give me a valid time so I can continue?"
        )

        val alarmSetSuccess = listOf(
            "Alarm set successfully...",
            "Done! Your alarm is ready.",
            "Great, I’ve set the alarm for you.",
            "All set, your alarm has been scheduled.",
            "Alarm saved successfully.",
            "Okay, I’ve configured the alarm as requested."
        )

        val reminderSetSuccess = listOf(
            "Reminder set successfully...",
            "Done! Your reminder is ready.",
            "Great, I’ve saved the reminder for you.",
            "All set, your reminder has been scheduled.",
            "Reminder saved successfully.",
            "Okay, I’ve created the reminder as requested."
        )

        val promptForTime = listOf(
            "Sure, at what time?",
            "Alright, when should I set it?",
            "Okay, what time would you like?",
            "Got it, please tell me the time.",
            "When do you want me to set it for?",
            "Sure thing, what time works for you?"
        )

        val genericError = listOf(
            "Something went wrong, please try again.",
            "Oops, that didn’t work. Please retry.",
            "I ran into an issue. Can you try again?",
            "Sorry, something broke there. Please try once more.",
            "That didn’t go through. Could you retry?",
            "An error popped up. Let’s try that again."
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
            youtubeKey = com.app.assistant.BuildConfig.YOUTUBE_API_KEY
        }
        return youtubeKey
    }

    fun loadChatKey(): String? {
        var chatKey = settingsRepository.getChatApiKey()
        if (chatKey.isNullOrBlank()) {
            chatKey = com.app.assistant.BuildConfig.GROQ_API_KEY
        }
        return chatKey
    }
}
