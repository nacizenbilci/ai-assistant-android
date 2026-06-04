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

    private val _verificationState = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val verificationState: StateFlow<VerificationState> = _verificationState.asStateFlow()

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
        customAssistantRole: String
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
                provider = provider,
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
            val hasAudio = provider == LlmProvider.GEMINI && (model.contains("1.5") || model.contains("2.0"))
            val hasVideo = provider == LlmProvider.GEMINI && (model.contains("1.5") || model.contains("2.0"))

            val capabilities = ModelCapabilities(
                hasImageInput = isVisionOk,
                hasAudioInput = hasAudio,
                hasVideoInput = hasVideo
            )

            // Save detected capabilities in repository
            settingsRepository.setIsImageSupported(isVisionOk)
            settingsRepository.setIsAudioSupported(hasAudio)
            settingsRepository.setIsVideoSupported(hasVideo)
            settingsRepository.setIsModelVerified(true)

            _verificationState.value = VerificationState.Success(capabilities)
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
