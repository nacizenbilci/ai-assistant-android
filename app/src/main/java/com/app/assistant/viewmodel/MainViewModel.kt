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
import com.app.assistant.model.Attachment
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private val _selectedAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val selectedAttachments: StateFlow<List<Attachment>> = _selectedAttachments.asStateFlow()

    private fun compressImageIfNeeded(context: android.content.Context, uri: android.net.Uri): ByteArray? {
        val mimeType = context.contentResolver.getType(uri) ?: return null
        if (!mimeType.startsWith("image/")) {
            return null
        }
        try {
            // First read EXIF rotation
            var rotation = 0
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = android.media.ExifInterface(stream)
                    val orientation = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    rotation = when (orientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to parse EXIF orientation", e)
            }

            // Decode dimensions only
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }

            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return null

            // Calculate sample size (approximate scaling down during decode)
            val maxDim = 1200
            var inSampleSize = 1
            if (width > maxDim || height > maxDim) {
                val halfWidth = width / 2
                val halfHeight = height / 2
                while ((halfWidth / inSampleSize) >= maxDim || (halfHeight / inSampleSize) >= maxDim) {
                    inSampleSize *= 2
                }
            }

            // Decode full image with sample size
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // Precise scaling to max 1200px
            val currentWidth = bitmap.width
            val currentHeight = bitmap.height
            val scaledBitmap = if (currentWidth > maxDim || currentHeight > maxDim) {
                val ratio = currentWidth.toFloat() / currentHeight.toFloat()
                val (newWidth, newHeight) = if (currentWidth > currentHeight) {
                    maxDim to (maxDim / ratio).toInt()
                } else {
                    (maxDim * ratio).toInt() to maxDim
                }
                android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                    if (it != bitmap) {
                        bitmap.recycle()
                    }
                }
            } else {
                bitmap
            }

            // Correct EXIF rotation
            val rotatedBitmap = if (rotation != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                android.graphics.Bitmap.createBitmap(
                    scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height, matrix, true
                ).also {
                    if (it != scaledBitmap) {
                        scaledBitmap.recycle()
                    }
                }
            } else {
                scaledBitmap
            }

            // Compress to JPEG 80%
            val outputStream = java.io.ByteArrayOutputStream()
            rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
            val compressedBytes = outputStream.toByteArray()
            rotatedBitmap.recycle()
            return compressedBytes
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error compressing image", e)
            return null
        }
    }

    fun addSelectedAttachment(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val contentResolver = context.contentResolver
            var fileName = "file_${System.currentTimeMillis()}"
            var mimeType = contentResolver.getType(uri) ?: "*/*"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            try {
                val isImage = mimeType.startsWith("image/") || 
                              fileName.endsWith(".jpg", ignoreCase = true) || 
                              fileName.endsWith(".jpeg", ignoreCase = true) || 
                              fileName.endsWith(".png", ignoreCase = true) || 
                              fileName.endsWith(".webp", ignoreCase = true)

                var fileBytes: ByteArray? = null
                if (isImage) {
                    fileBytes = compressImageIfNeeded(context, uri)
                    if (fileBytes != null) {
                        mimeType = "image/jpeg"
                        if (!fileName.lowercase().endsWith(".jpg") && !fileName.lowercase().endsWith(".jpeg")) {
                            val dotIndex = fileName.lastIndexOf('.')
                            fileName = if (dotIndex != -1) {
                                "${fileName.substring(0, dotIndex)}.jpg"
                            } else {
                                "$fileName.jpg"
                            }
                        }
                    }
                }

                if (fileBytes == null) {
                    val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                    fileBytes = inputStream.readBytes()
                    inputStream.close()
                }

                val iv = com.app.assistant.db.EncryptionUtil.generateIV()
                val encryptedBytes = com.app.assistant.db.EncryptionUtil.encryptFile(fileBytes, iv)

                val attachmentsDir = java.io.File(context.filesDir, "attachments")
                if (!attachmentsDir.exists()) {
                    attachmentsDir.mkdirs()
                }

                val targetFile = java.io.File(attachmentsDir, "file_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.enc")
                targetFile.writeBytes(encryptedBytes)

                val attachment = Attachment(
                    filePath = targetFile.absolutePath,
                    mimeType = mimeType,
                    fileName = fileName,
                    iv = iv
                )
                _selectedAttachments.value = _selectedAttachments.value + attachment
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to add attachment", e)
            }
        }
    }

    fun removeSelectedAttachment(attachment: Attachment) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(attachment.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _selectedAttachments.value = _selectedAttachments.value - attachment
        }
    }

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
        val currentAttachments = _selectedAttachments.value
        _selectedAttachments.value = emptyList()

        focusManager?.clearFocus()
        keyboardController?.hide()
        setQuestion("")

        viewModelScope.launch {
            val itemId: Long
            val processedQuestion = processChatCommandUseCase.cleanAndPunctuate(originalQuestion)
            val finalQuestionText = withContext(Dispatchers.IO) {
                val finalQuestionBuilder = java.lang.StringBuilder(processedQuestion)
                currentAttachments.forEach { att ->
                    if (att.mimeType.startsWith("text/") || att.mimeType == "application/json") {
                        try {
                            val file = java.io.File(att.filePath)
                            if (file.exists()) {
                                val encryptedBytes = file.readBytes()
                                val decryptedBytes = com.app.assistant.db.EncryptionUtil.decryptFile(encryptedBytes, att.iv)
                                val textContent = String(decryptedBytes, Charsets.UTF_8)
                                finalQuestionBuilder.append("\n\n[Attached File: ${att.fileName}]\n---\n$textContent\n---\n")
                            }
                        } catch (e: java.lang.Exception) {
                            Log.e("MainViewModel", "Failed to extract text file content", e)
                        }
                    }
                }
                finalQuestionBuilder.toString()
            }

            val newItem = Conversation(text = finalQuestionText, isMe = true, attachments = currentAttachments)

            chatList.add(newItem)
            if (_isCustomUIHalfPage.value) {
                _isCustomUIHalfPage.value = false
            }

            itemId = newItem.id

            val loadingItem = Conversation(text = "", isMe = false, isLoading = true)
            chatList.add(loadingItem)
            val loadingItemId = loadingItem.id

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

    internal fun callAI(
        loadingItemId: Long,
        speak: Boolean,
        category: Category,
    ) {
        viewModelScope.launch {
            var fullResponse = ""
            var hasStarted = false
            
            try {
                processChatCommandUseCase.getAiChatResponseStream(MAIN_CONTEXT, chatList.toList())
                    .collect { chunk ->
                        if (!hasStarted) {
                            hasStarted = true
                            val index = chatList.indexOfFirst { it.id == loadingItemId }
                            if (index != -1) {
                                val item = chatList[index].copy(
                                    text = "",
                                    isMe = false,
                                    isLoading = false,
                                    isStreaming = true,
                                    category = category.name
                                )
                                chatList.set(index, item)
                            }
                        }
                        fullResponse += chunk
                        val index = chatList.indexOfFirst { it.id == loadingItemId }
                        if (index != -1) {
                            val item = chatList[index].copy(
                                text = fullResponse,
                                isMe = false,
                                isLoading = false,
                                isStreaming = true,
                                category = category.name
                            )
                            chatList.set(index, item)
                        }
                    }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Streaming error", e)
                fullResponse = "Error occurred while streaming response."
            }

            val index = chatList.indexOfFirst { it.id == loadingItemId }
            if (index != -1) {
                val item = chatList[index].copy(
                    text = fullResponse,
                    isMe = false,
                    isLoading = false,
                    isStreaming = false,
                    category = category.name
                )
                chatList.set(index, item)
            } else {
                addConversationItem(fullResponse, false, category)
            }

            if (chatList.size <= 2) {
                loadGroup()
            }

            if (speak) {
                val conversationTemp = Conversation(text = fullResponse, isMe = false)
                val answerToSpeak = com.app.assistant.util.MarkdownUtils.markdownToPlainText(conversationTemp.getActualAnswer())
                if (answerToSpeak.isNotBlank()) {
                    speakResponse(answerToSpeak)
                }
            }
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
            val index = chatList.indexOfFirst { item -> item.id == loadingItemId }
            if (index != -1) {
                chatList.removeAt(index)
            }
            addConversationItem(response, false, category, contentURL, navigationURI)
            if (speak) {
                val conversationTemp = Conversation(text = response, isMe = false)
                val answerToSpeak = com.app.assistant.util.MarkdownUtils.markdownToPlainText(conversationTemp.getActualAnswer())
                if (answerToSpeak.isNotBlank()) {
                    speakResponse(answerToSpeak)
                }
            }
        }
    }

    internal fun addConversationItem(
        text: String,
        isUser: Boolean,
        category: Category,
        contentURL: String = "",
        navigationURI: URI = URI(""),
    ) {
        val conversation = Conversation(
            text = text,
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



    companion object {
        private var cachedLanguages: List<Pair<String, String>>? = null
    }
}
