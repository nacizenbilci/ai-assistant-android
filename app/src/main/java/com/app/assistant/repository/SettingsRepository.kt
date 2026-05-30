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
}
