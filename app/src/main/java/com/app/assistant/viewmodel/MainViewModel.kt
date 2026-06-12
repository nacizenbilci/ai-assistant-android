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
import com.app.assistant.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
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
    private val _showBottomSheet = MutableStateFlow(false)
    val showBottomSheet: StateFlow<Boolean> = _showBottomSheet.asStateFlow()

    fun setShowBottomSheet(show: Boolean) {
        _showBottomSheet.value = show
    }
    private val _isLanguageLoading = MutableStateFlow(false)
    val isLanguageLoading: StateFlow<Boolean> = _isLanguageLoading
    private val _showToastEvent = MutableSharedFlow<String>()
    val showToastEvent = _showToastEvent.asSharedFlow()

    // For custom ui
    private val _isCustomUI = MutableStateFlow(false)
    val isCustomUI: StateFlow<Boolean> = _isCustomUI

    // For custom ui half page
    private val _isCustomUIHalfPage = MutableStateFlow(false)
    val isCustomUIHalfPage: StateFlow<Boolean> = _isCustomUIHalfPage

    private var _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isVoiceProcessing = MutableStateFlow(false)
    val isVoiceProcessing: StateFlow<Boolean> = _isVoiceProcessing.asStateFlow()

    private val _isHandsFreeModeActive = MutableStateFlow(false)
    val isHandsFreeModeActive: StateFlow<Boolean> = _isHandsFreeModeActive.asStateFlow()

    fun toggleHandsFreeMode() {
        val modelManager = com.app.assistant.speech.SpeechModelManager(getApplication())
        val isDownloaded = modelManager.isModelDownloaded()
        if (!isDownloaded && !_isHandsFreeModeActive.value) {
            triggerToast("Offline speech models are required for Hands-Free mode. Please download them in settings.")
            return
        }
        setHandsFreeModeActive(!_isHandsFreeModeActive.value)
    }

    fun setHandsFreeModeActive(active: Boolean) {
        if (active) {
            val modelManager = com.app.assistant.speech.SpeechModelManager(getApplication())
            if (!modelManager.isModelDownloaded()) {
                triggerToast("Offline speech models are required for Hands-Free mode. Please download them in settings.")
                return
            }
        }
        _isHandsFreeModeActive.value = active
        if (active) {
            _isCustomUI.value = false // Keep standard layout size so our overlay takes full screen
            _isCustomUIHalfPage.value = false // Clear half-page voice panel flags
            _isListening.value = false
            _isVoiceProcessing.value = false
        } else {
            stopTextToSpeech()
            _isListening.value = false
            _isVoiceProcessing.value = false
        }
    }

    private val _groupList = MutableStateFlow<List<Group>>(emptyList())
    val groupList: StateFlow<List<Group>> = _groupList.asStateFlow()

    // UI Event flow
    internal val _uiEvent = Channel<UIEvent>(Channel.BUFFERED)
    val uiEvent: Flow<UIEvent> = _uiEvent.receiveAsFlow()

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
            Log.e("Classifier error", "Unable to classify: $error")
        }

        override fun onError(
            error: String,
            itemId: Long,
            loadingItemId: Long,
            speak: Boolean
        ) {
            Log.e("Classifier error", "Unable to classify: $error")
            viewModelScope.launch(Dispatchers.Main) {
                val index = chatList.indexOfFirst { it.id == loadingItemId }
                if (index != -1) {
                    val item = chatList[index].copy(
                        text = "Classification failed to load.",
                        isMe = false,
                        isLoading = false,
                        isStreaming = false,
                        category = Category.OTHER.name
                    )
                    chatList.set(index, item)
                }
            }
        }
    }

    init {
        if (speak) {
            _isCustomUI.value = true
            _isCustomUIHalfPage.value = true
            _isListening.value = true
        }
        if (speak) {
            startSpeechRecognition()
        }
        initializeTextClassifier()
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

    private fun initializeTextClassifier() {
        classifierHelper = TextClassifierHelper(
            context = getApplication<Application>().applicationContext,
            listener = listener,
        )
    }

    fun setSpeaking(speaking: Boolean) {
        _isSpeaking.value = speaking
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun setVoiceProcessing(processing: Boolean) {
        _isVoiceProcessing.value = processing
    }

    fun stopTextToSpeech() {
        viewModelScope.launch {
            _uiEvent.send(UIEvent.StopSpeaking)
        }
        _isSpeaking.value = false
    }

    fun shutdownResources() {
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
            
            val speakBuffer = StringBuilder()
            var processedAnswerLength = 0
            var isFirstSentence = true
            
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
                        
                        if (speak) {
                            val actualAnswerText = Conversation(text = fullResponse, isMe = false).getActualAnswer()
                            if (actualAnswerText.length > processedAnswerLength) {
                                val newTokens = actualAnswerText.substring(processedAnswerLength)
                                processedAnswerLength = actualAnswerText.length
                                speakBuffer.append(newTokens)
                                
                                var searchIndex = 0
                                while (searchIndex < speakBuffer.length) {
                                    val boundaryIndex = findSentenceBoundary(speakBuffer, searchIndex)
                                    if (boundaryIndex != -1) {
                                        val sentence = speakBuffer.substring(0, boundaryIndex + 1)
                                        speakBuffer.delete(0, boundaryIndex + 1)
                                        searchIndex = 0
                                        
                                        val cleanText = com.app.assistant.util.MarkdownUtils.markdownToPlainText(sentence).trim()
                                        if (cleanText.isNotEmpty()) {
                                            val queueMode = if (isFirstSentence) 0 else 1
                                            isFirstSentence = false
                                            speakResponse(cleanText, queueMode)
                                        }
                                    } else {
                                        break
                                    }
                                }
                            }
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
                val remainingText = speakBuffer.toString().trim()
                if (remainingText.isNotEmpty()) {
                    val cleanText = com.app.assistant.util.MarkdownUtils.markdownToPlainText(remainingText).trim()
                    if (cleanText.isNotEmpty()) {
                        val queueMode = if (isFirstSentence) 0 else 1
                        speakResponse(cleanText, queueMode)
                    }
                }
            }
        }
    }

    private fun findSentenceBoundary(buffer: StringBuilder, startIndex: Int): Int {
        val len = buffer.length
        for (i in startIndex until len) {
            val c = buffer[i]
            if (c == '\n' || c == '\r') {
                return i
            }
            if (c == '.' || c == '?' || c == '!') {
                if (i + 1 < len) {
                    val nextChar = buffer[i + 1]
                    if (nextChar.isWhitespace()) {
                        if (c == '.') {
                            if (isAbbreviationOrDecimal(buffer, i)) {
                                continue
                            }
                        }
                        return i
                    }
                }
            }
        }
        return -1
    }

    private fun isAbbreviationOrDecimal(buffer: StringBuilder, dotIndex: Int): Boolean {
        var start = dotIndex - 1
        while (start >= 0 && buffer[start].isLetter()) {
            start--
        }
        val word = buffer.substring(start + 1, dotIndex).lowercase()
        val abbreviations = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "eg", "ie", "vs", "etc", "st", "co",
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
            "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
        )
        return word in abbreviations
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

    internal fun speakResponse(plaintext: String, queueMode: Int = 0) {
        viewModelScope.launch {
            _uiEvent.send(UIEvent.SpeakText(plaintext, queueMode))
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

    fun startSpeechRecognition() {
        viewModelScope.launch {
            _uiEvent.send(UIEvent.StartSpeechRecognition)
        }
    }

    fun onSpeechRecognized(recognizedText: String) {
        _question.value = recognizedText
        if (recognizedText.isNotBlank()) {
            processQuestion(speak = true)
        }
    }

    fun onSpeechPartialResult(recognizedText: String) {
        _question.value = recognizedText
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

    override fun onCleared() {
        super.onCleared()
        shutdownResources()
    }
}
