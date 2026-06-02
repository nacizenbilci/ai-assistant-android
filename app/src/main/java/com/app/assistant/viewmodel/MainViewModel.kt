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
import com.app.assistant.R
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
    internal val settingsRepository: SettingsRepository,
    private val repository: DynamicConversationRepository,
    internal val callContactUseCase: CallContactUseCase,
    internal val playSongUseCase: PlaySongUseCase,
    internal val navigateUseCase: NavigateUseCase,
    internal val getWeatherUseCase: GetWeatherUseCase,
    internal val setAlarmUseCase: SetAlarmUseCase,
    internal val setReminderUseCase: SetReminderUseCase,
    internal val processChatCommandUseCase: ProcessChatCommandUseCase,
) : AndroidViewModel(application) {
    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    fun setQuestion(text: String) {
        _question.value = text
    }

    val chatList: SyncStateList by lazy {
        SyncStateList(repository, viewModelScope)
    }
    var currentGroupId: Long = -1L
        private set
    var languages: List<Pair<String, String>> = emptyList()

    private val _showBottomSheet = MutableStateFlow(false)
    val showBottomSheet: StateFlow<Boolean> = _showBottomSheet.asStateFlow()

    fun setShowBottomSheet(show: Boolean) {
        _showBottomSheet.value = show
    }
    private val _isLanguageLoading = MutableStateFlow(false)
    val isLanguageLoading: StateFlow<Boolean> = _isLanguageLoading
    private val _showToastEvent = MutableSharedFlow<String>()
    val showToastEvent = _showToastEvent.asSharedFlow()
    internal val translatorManager = TranslatorManager()

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
    internal val _uiEvent = MutableSharedFlow<UIEvent>()
    val uiEvent: SharedFlow<UIEvent> = _uiEvent.asSharedFlow()

    // Callback properties for SpeechToText
    var speechResultCallback: ((String) -> Unit)? = null
    var speechPartialResultCallback: ((String) -> Unit)? = null

    // Classifier
    private lateinit var classifierHelper: TextClassifierHelper

    // LockState
    var lockState: LockState = LockState.None
        internal set(value) {
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
        viewModelScope.launch(Dispatchers.Default) {
            languages = cachedLanguages ?: synchronized(this) {
                cachedLanguages ?: getPublicStaticFinalStringsWithNames(TranslateLanguage::class.java).also {
                    cachedLanguages = it
                }
            }
        }
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
        classifierHelper.shutDown()
    }

    fun processQuestion(
        focusManager: FocusManager? = null,
        keyboardController: SoftwareKeyboardController? = null,
        speak: Boolean = false,
    ) {
        val originalQuestion = question.value
        focusManager?.clearFocus()
        keyboardController?.hide()
        setQuestion("")

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



    private fun processClassifierResponse(
        inferenceTime: Long,
        results: TextClassifierResult,
        inputText: String,
        itemId: Long,
        loadingItemId: Long,
        speak: Boolean,
    ) {
        viewModelScope.launch {
            val finalCategory = processChatCommandUseCase.resolveCategory(results, inputText)
            callCommand(finalCategory.name, itemId, loadingItemId, speak)

            val index = chatList.indexOfFirst { it.id == itemId }
            if (index != -1) {
                val updatedItem = chatList[index].copy(category = finalCategory.name)
                chatList.set(index, updatedItem)
            }
            Log.d("Classifier Inference Result", finalCategory.name)
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
    internal fun processResponse(
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

    internal fun addConversationItem(
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

    internal fun speakResponse(plaintext: String) {
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
        triggerToast(getApplication<Application>().getString(R.string.downloading_model_toast))
    }

    private fun setupTranslator(
        selectedLanguageCode: String,
        showCompletionToast: Boolean = false,
    ) {
        translatorManager.setupTranslators(selectedLanguageCode) { success ->
            if (success) {
                updateActiveLanguageCode(selectedLanguageCode)
                _isLanguageLoading.value = false
                setShowBottomSheet(false)
                if (showCompletionToast) {
                    triggerToast(getApplication<Application>().getString(R.string.download_completed_toast))
                }
            } else {
                _isLanguageLoading.value = false
                triggerToast(getApplication<Application>().getString(R.string.download_failed_toast))
            }
        }
    }

    private fun triggerToast(message: String) {
        viewModelScope.launch {
            _showToastEvent.emit(message)
        }
    }

    internal fun clearLockStateData() {
        if (lockState is LockState.LockAlarm) {
            (lockState as LockState.LockAlarm).day = null
        }
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

    companion object {
        private var cachedLanguages: List<Pair<String, String>>? = null
    }
}
