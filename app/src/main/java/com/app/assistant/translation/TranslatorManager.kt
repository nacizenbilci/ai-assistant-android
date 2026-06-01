package com.app.assistant.translation

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslatorManager {
    private var toEnglishTranslator: Translator? = null
    private var fromEnglishTranslator: Translator? = null
    private val modelManager = RemoteModelManager.getInstance()

    fun setupTranslators(
        selectedLanguageCode: String,
        onResult: (Boolean) -> Unit,
    ) {
        modelManager
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { downloadedModels ->
                val downloadedLanguages = downloadedModels.map { it.language }.toSet()

                val isSelectedModelDownloaded = selectedLanguageCode in downloadedLanguages
                val isEnglishModelDownloaded = TranslateLanguage.ENGLISH in downloadedLanguages

                if (!isSelectedModelDownloaded) {
                    downloadLanguageModel(selectedLanguageCode) { success ->
                        if (!success) {
                            onResult(false)
                        } else {
                            if (!isEnglishModelDownloaded) {
                                downloadLanguageModel(TranslateLanguage.ENGLISH) { successEnglish ->
                                    if (!successEnglish) {
                                        onResult(false)
                                    } else {
                                        initializeTranslators(selectedLanguageCode)
                                        onResult(true)
                                    }
                                }
                            } else {
                                Log.d("Translator", "English language model already exists. Not downloading.")
                                initializeTranslators(selectedLanguageCode)
                                onResult(true)
                            }
                        }
                    }
                } else {
                    if (!isEnglishModelDownloaded) {
                        Log.d("Translator", "Selected model exists, downloading English model.")
                        downloadLanguageModel(TranslateLanguage.ENGLISH) { successEnglish ->
                            if (!successEnglish) {
                                onResult(false)
                            } else {
                                initializeTranslators(selectedLanguageCode)
                                onResult(true)
                            }
                        }
                    } else {
                        Log.d("Translator", "Both models already exist. Not downloading.")
                        initializeTranslators(selectedLanguageCode)
                        onResult(true)
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("Translator", "Failed to get downloaded models: ${e.message}")
                onResult(false)
            }
    }

    // Helper function to initialize translators for the selected language
    private fun initializeTranslators(selectedLanguageCode: String) {
        // Set up translator for selected language -> English
        val toEnglishOptions =
            TranslatorOptions
                .Builder()
                .setSourceLanguage(selectedLanguageCode)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()

        toEnglishTranslator = Translation.getClient(toEnglishOptions)

        // Set up translator for English -> selected language
        val fromEnglishOptions =
            TranslatorOptions
                .Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(selectedLanguageCode)
                .build()

        fromEnglishTranslator = Translation.getClient(fromEnglishOptions)
    }

    // Use this method to translate from selected language to English
    fun translateToEnglish(
        text: String,
        callback: (translatedText: String?) -> Unit,
    ) {
        toEnglishTranslator
            ?.translate(text)
            ?.addOnSuccessListener { translatedText ->
                callback(translatedText)
            }?.addOnFailureListener { e ->
                Log.e("Translator", "Translation to English failed: ${e.message}")
                callback(null)
            }
    }

    // Use this method to translate from English to the selected language
    fun translateFromEnglish(
        text: String,
        callback: (translatedText: String?) -> Unit,
    ) {
        fromEnglishTranslator
            ?.translate(text)
            ?.addOnSuccessListener { translatedText ->
                callback(translatedText)
            }?.addOnFailureListener { e ->
                Log.e("Translator", "Translation from English failed: ${e.message}")
                callback(null)
            }
    }

    private fun downloadLanguageModel(
        languageCode: String,
        onDownloadComplete: (Boolean) -> Unit,
    ) {
        val languageModel = TranslateRemoteModel.Builder(languageCode).build()
        val conditions = DownloadConditions.Builder().build()

        modelManager
            .download(languageModel, conditions)
            .addOnSuccessListener {
                Log.d("Translator", "Language model for $languageCode downloaded successfully.")
                onDownloadComplete(true)
            }.addOnFailureListener { e ->
                Log.e("Translator", "Failed to download language model for $languageCode: ${e.message}")
                onDownloadComplete(false)
            }
    }

    // Delete the previously downloaded language model
    private fun deletePreviousLanguageModel(languageCode: String) {
        val languageModel = TranslateRemoteModel.Builder(languageCode).build()
        modelManager
            .deleteDownloadedModel(languageModel)
            .addOnSuccessListener {
                Log.d("Translator", "Language model for $languageCode deleted successfully.")
            }.addOnFailureListener { e ->
                Log.e("Translator", "Failed to delete language model for $languageCode: ${e.message}")
            }
    }

    fun closeTranslator() {
        toEnglishTranslator?.close()
        fromEnglishTranslator?.close()
    }
}
