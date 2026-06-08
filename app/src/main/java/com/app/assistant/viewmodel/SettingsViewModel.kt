package com.app.assistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.assistant.BuildConfig
import com.app.assistant.llm.LlmConfig
import com.app.assistant.llm.LlmProvider
import com.app.assistant.llm.ModelCapabilities
import com.app.assistant.llm.ModelCapabilityProber
import com.app.assistant.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val currentBytes: Long, val totalBytes: Long) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

sealed class VerificationState {
    object Idle : VerificationState()
    object Verifying : VerificationState()
    data class Success(val capabilities: ModelCapabilities) : VerificationState()
    data class Error(val message: String) : VerificationState()
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val modelManager = com.app.assistant.speech.SpeechModelManager(settingsRepository.context)

    private val _modelDownloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val modelDownloadState: StateFlow<DownloadState> = _modelDownloadState.asStateFlow()

    private val _isModelInstalled = MutableStateFlow(modelManager.isModelDownloaded())
    val isModelInstalled: StateFlow<Boolean> = _isModelInstalled.asStateFlow()

    private val _verificationState = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val verificationState: StateFlow<VerificationState> = _verificationState.asStateFlow()

    private val _fetchedModels = MutableStateFlow<Map<LlmProvider, List<String>>>(emptyMap())
    val fetchedModels: StateFlow<Map<LlmProvider, List<String>>> = _fetchedModels.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _modelFetchError = MutableStateFlow<String?>(null)
    val modelFetchError: StateFlow<String?> = _modelFetchError.asStateFlow()

    fun resetVerificationState() {
        _verificationState.value = VerificationState.Idle
    }

    fun loadYoutubeKey(): String {
        val key = settingsRepository.getYoutubeApiKey()
        return if (key.isNullOrBlank()) BuildConfig.YOUTUBE_API_KEY else key
    }

    fun loadChatKey(): String {
        val key = settingsRepository.getChatApiKey()
        return if (key.isNullOrBlank()) BuildConfig.GROQ_API_KEY else key
    }
    
    fun loadLlmProvider(): LlmProvider {
        val name = settingsRepository.getLlmProvider()
        return try {
            LlmProvider.valueOf(name)
        } catch (e: Exception) {
            LlmProvider.GROQ
        }
    }

    fun loadLlmModel(): String = settingsRepository.getLlmModel()

    fun loadLlmCustomUrl(): String = settingsRepository.getLlmCustomUrl()
    fun loadLlmCustomHeaders(): String = settingsRepository.getLlmCustomHeaders()
    fun loadLlmCustomResponsePath(): String = settingsRepository.getLlmCustomResponsePath()
    fun loadLlmCustomRequestTemplate(): String = settingsRepository.getLlmCustomRequestTemplate()
    fun loadLlmCustomMessageFormat(): String = settingsRepository.getLlmCustomMessageFormat()
    fun loadLlmCustomSystemRole(): String = settingsRepository.getLlmCustomSystemRole()
    fun loadLlmCustomUserRole(): String = settingsRepository.getLlmCustomUserRole()
    fun loadLlmCustomAssistantRole(): String = settingsRepository.getLlmCustomAssistantRole()
    fun loadUseLocalWhisper(): Boolean = settingsRepository.getUseLocalWhisper()
    fun loadSttMode(): com.app.assistant.speech.SttMode = settingsRepository.getSttMode()

    fun deleteModel() {
        viewModelScope.launch(Dispatchers.IO) {
            modelManager.deleteLocalModel()
            _isModelInstalled.value = false
            val currentMode = settingsRepository.getSttMode()
            if (currentMode != com.app.assistant.speech.SttMode.NATIVE) {
                settingsRepository.setSttMode(com.app.assistant.speech.SttMode.NATIVE)
            }
            _modelDownloadState.value = DownloadState.Idle
        }
    }

    fun cancelModelDownload() {
        modelManager.cancelDownload()
        _modelDownloadState.value = DownloadState.Idle
    }

    fun startModelDownload() {
        viewModelScope.launch {
            _modelDownloadState.value = DownloadState.Downloading(0f, 0L, modelManager.totalSizeBytes)
            modelManager.downloadModel(
                client = okHttpClient,
                onProgress = { downloadedBytes, totalBytes ->
                    val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                    _modelDownloadState.value = DownloadState.Downloading(progress, downloadedBytes, totalBytes)
                },
                onComplete = { success, errorMsg ->
                    viewModelScope.launch {
                        if (success) {
                            _modelDownloadState.value = DownloadState.Completed
                            _isModelInstalled.value = true
                        } else {
                            _modelDownloadState.value = DownloadState.Error(errorMsg ?: "Unknown download error")
                            _isModelInstalled.value = modelManager.isModelDownloaded()
                        }
                    }
                }
            )
        }
    }

    fun saveSettings(
        youtubeApiKey: String,
        chatApiKey: String,
        provider: LlmProvider,
        model: String,
        customUrl: String,
        customHeaders: String,
        customResponsePath: String,
        customRequestTemplate: String,
        customMessageFormat: String,
        customSystemRole: String,
        customUserRole: String,
        customAssistantRole: String,
        sttMode: com.app.assistant.speech.SttMode
    ) {
        settingsRepository.saveKeys(youtubeApiKey, chatApiKey)
        settingsRepository.setLlmProvider(provider.name)
        settingsRepository.setLlmModel(model)
        settingsRepository.setLlmCustomUrl(customUrl)
        settingsRepository.setLlmCustomHeaders(customHeaders)
        settingsRepository.setLlmCustomResponsePath(customResponsePath)
        settingsRepository.setLlmCustomRequestTemplate(customRequestTemplate)
        settingsRepository.setLlmCustomMessageFormat(customMessageFormat)
        settingsRepository.setLlmCustomSystemRole(customSystemRole)
        settingsRepository.setLlmCustomUserRole(customUserRole)
        settingsRepository.setLlmCustomAssistantRole(customAssistantRole)
        settingsRepository.setSttMode(sttMode)
    }

    fun verifyModelAndSaveCapabilities(
        provider: LlmProvider,
        model: String,
        apiKey: String,
        customUrl: String,
        customHeaders: String,
        customResponsePath: String,
        customRequestTemplate: String,
        customMessageFormat: String,
        customSystemRole: String,
        customUserRole: String,
        customAssistantRole: String
    ) {
        viewModelScope.launch {
            _verificationState.value = VerificationState.Verifying
            
            val config = if (provider == LlmProvider.CUSTOM) {
                LlmConfig(
                    url = customUrl,
                    headers = parseHeadersJson(customHeaders),
                    responsePath = customResponsePath,
                    requestTemplate = customRequestTemplate,
                    messageFormat = customMessageFormat,
                    systemRole = customSystemRole.takeIf { it.isNotBlank() },
                    userRole = customUserRole,
                    assistantRole = customAssistantRole
                )
            } else {
                provider.config
            }

            // 1. Test Text connection
            val isTextOk = ModelCapabilityProber.probeTextConnection(
                client = okHttpClient,
                apiKey = apiKey,
                model = model,
                config = config
            )

            if (!isTextOk) {
                _verificationState.value = VerificationState.Error("Connection check failed. Please verify API key, URL, or internet connection.")
                settingsRepository.setIsModelVerified(false)
                return@launch
            }

            // 2. Test Vision (Image) connection
            val isVisionOk = ModelCapabilityProber.probeVisionConnection(
                client = okHttpClient,
                provider = provider,
                apiKey = apiKey,
                model = model,
                config = config
            )

            // 3. Audio/Video checks (currently Gemini 1.5/2.0 is the main provider that supports audio/video input natively in the API)
            val hasAudio = provider == LlmProvider.GEMINI && (model.contains("1.5") || model.contains("2.0") || model.contains("2.5"))
            val hasVideo = provider == LlmProvider.GEMINI && (model.contains("1.5") || model.contains("2.0") || model.contains("2.5"))
            val hasDoc = (provider == LlmProvider.GEMINI && (model.contains("1.5") || model.contains("2.0") || model.contains("2.5"))) || (provider == LlmProvider.ANTHROPIC && model.contains("claude-3"))

            val capabilities = ModelCapabilities(
                hasImageInput = isVisionOk,
                hasAudioInput = hasAudio,
                hasVideoInput = hasVideo,
                hasDocumentInput = hasDoc
            )

            // Save detected capabilities in repository
            settingsRepository.setIsImageSupported(isVisionOk)
            settingsRepository.setIsAudioSupported(hasAudio)
            settingsRepository.setIsVideoSupported(hasVideo)
            settingsRepository.setIsDocumentSupported(hasDoc)
            settingsRepository.setIsModelVerified(true)

            _verificationState.value = VerificationState.Success(capabilities)

            // Auto-fetch models on successful connection verification
            fetchModelsForProvider(
                provider = provider,
                apiKey = apiKey,
                customUrl = customUrl,
                customHeaders = customHeaders
            )
        }
    }

    fun fetchModelsForProvider(
        provider: LlmProvider,
        apiKey: String,
        customUrl: String = "",
        customHeaders: String = ""
    ) {
        viewModelScope.launch {
            if (apiKey.isBlank() && provider != LlmProvider.OLLAMA && provider != LlmProvider.OPEN_ROUTER) {
                _modelFetchError.value = "API Key is required to fetch models."
                return@launch
            }
            _isFetchingModels.value = true
            _modelFetchError.value = null
            try {
                val models = ModelCapabilityProber.fetchAvailableModels(
                    client = okHttpClient,
                    provider = provider,
                    apiKey = apiKey,
                    customUrl = customUrl,
                    customHeaders = customHeaders
                )
                if (models.isNotEmpty()) {
                    _fetchedModels.value = _fetchedModels.value + (provider to models)
                } else {
                    _modelFetchError.value = "No models returned from API."
                }
            } catch (e: Exception) {
                _modelFetchError.value = "Failed to fetch models: ${e.message}"
            } finally {
                _isFetchingModels.value = false
            }
        }
    }


    private fun parseHeadersJson(jsonStr: String): Map<String, String> {
        return try {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr)
            if (element is kotlinx.serialization.json.JsonObject) {
                element.mapValues { (_, value) ->
                    if (value is kotlinx.serialization.json.JsonPrimitive) value.content else value.toString()
                }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
