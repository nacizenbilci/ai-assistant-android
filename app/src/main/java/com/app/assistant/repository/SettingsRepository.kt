package com.app.assistant.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SettingsRepository(
    context: Context,
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val securedPreferences: SharedPreferences =
        EncryptedSharedPreferences.create(
            "secure_prefs",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun getIsTranslationEnabled(): Boolean = sharedPreferences.getBoolean("is_translation_enabled", false)

    fun setTranslationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("is_translation_enabled", enabled).apply()
    }

    fun getActiveLanguageCode(): String = sharedPreferences.getString("active_language_code", "en") ?: "en"

    fun setActiveLanguageCode(languageCode: String) {
        sharedPreferences.edit().putString("active_language_code", languageCode).apply()
    }

    fun saveKeys(
        youtubeApiKey: String,
        chatApiKey: String,
    ) {
        with(securedPreferences.edit()) {
            if (youtubeApiKey.isNotBlank()) {
                putString("youtube_api_key", youtubeApiKey)
            }
            if (chatApiKey.isNotBlank()) {
                putString("chat_api_key", chatApiKey)
            }
            apply()
        }
    }

    fun getYoutubeApiKey(): String? = securedPreferences.getString("youtube_api_key", null)

    fun getChatApiKey(): String? = securedPreferences.getString("chat_api_key", null)

    fun getLlmProvider(): String = sharedPreferences.getString("llm_provider", "GROQ") ?: "GROQ"

    fun setLlmProvider(provider: String) {
        sharedPreferences.edit().putString("llm_provider", provider).apply()
    }

    fun getLlmModel(): String = sharedPreferences.getString("llm_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"

    fun setLlmModel(model: String) {
        sharedPreferences.edit().putString("llm_model", model).apply()
    }

    fun getLlmCustomUrl(): String = sharedPreferences.getString("llm_custom_url", "https://api.example.com/v1/chat/completions") ?: "https://api.example.com/v1/chat/completions"

    fun setLlmCustomUrl(url: String) {
        sharedPreferences.edit().putString("llm_custom_url", url).apply()
    }

    fun getLlmCustomHeaders(): String = sharedPreferences.getString("llm_custom_headers", "{\"Authorization\": \"Bearer {{API_KEY}}\", \"Content-Type\": \"application/json\"}") ?: "{\"Authorization\": \"Bearer {{API_KEY}}\", \"Content-Type\": \"application/json\"}"

    fun setLlmCustomHeaders(headers: String) {
        sharedPreferences.edit().putString("llm_custom_headers", headers).apply()
    }

    fun getLlmCustomResponsePath(): String = sharedPreferences.getString("llm_custom_response_path", "choices[0].message.content") ?: "choices[0].message.content"

    fun setLlmCustomResponsePath(path: String) {
        sharedPreferences.edit().putString("llm_custom_response_path", path).apply()
    }

    fun getLlmCustomRequestTemplate(): String = sharedPreferences.getString("llm_custom_request_template", "{\n  \"model\": \"{{MODEL}}\",\n  \"messages\": {{MESSAGES}}\n}") ?: "{\n  \"model\": \"{{MODEL}}\",\n  \"messages\": {{MESSAGES}}\n}"

    fun setLlmCustomRequestTemplate(template: String) {
        sharedPreferences.edit().putString("llm_custom_request_template", template).apply()
    }

    fun getLlmCustomMessageFormat(): String = sharedPreferences.getString("llm_custom_message_format", "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}") ?: "{\"role\": \"{{ROLE}}\", \"content\": \"{{CONTENT}}\"}"

    fun setLlmCustomMessageFormat(format: String) {
        sharedPreferences.edit().putString("llm_custom_message_format", format).apply()
    }

    fun getLlmCustomSystemRole(): String = sharedPreferences.getString("llm_custom_system_role", "system") ?: "system"

    fun setLlmCustomSystemRole(role: String) {
        sharedPreferences.edit().putString("llm_custom_system_role", role).apply()
    }

    fun getLlmCustomUserRole(): String = sharedPreferences.getString("llm_custom_user_role", "user") ?: "user"

    fun setLlmCustomUserRole(role: String) {
        sharedPreferences.edit().putString("llm_custom_user_role", role).apply()
    }

    fun getLlmCustomAssistantRole(): String = sharedPreferences.getString("llm_custom_assistant_role", "assistant") ?: "assistant"

    fun setLlmCustomAssistantRole(role: String) {
        sharedPreferences.edit().putString("llm_custom_assistant_role", role).apply()
    }
}
