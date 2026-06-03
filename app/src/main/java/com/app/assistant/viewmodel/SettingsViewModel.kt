package com.app.assistant.viewmodel

import androidx.lifecycle.ViewModel
import com.app.assistant.llm.LlmProvider
import com.app.assistant.repository.SettingsRepository

import com.app.assistant.BuildConfig

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

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
}
