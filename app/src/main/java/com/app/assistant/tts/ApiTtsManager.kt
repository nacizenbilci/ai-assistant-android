package com.app.assistant.tts

import android.content.Context
import com.app.assistant.config.SpeechConfig
import com.app.assistant.config.TtsApiProvider
import com.app.assistant.repository.SettingsRepository

class ApiTtsManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val onSpeakingStateChanged: (isSpeaking: Boolean) -> Unit
) : TtsManager {

    private val edgeTtsApiManager = EdgeTtsApiManager(context, settingsRepository, ::handleSpeakingStateChanged)
    private val googleTtsApiManager = GoogleTtsApiManager(context, settingsRepository, ::handleSpeakingStateChanged)

    private val activeProvider: TtsApiProvider
        get() = SpeechConfig.ACTIVE_TTS_PROVIDER

    private val currentManager: TtsManager
        get() = when (activeProvider) {
            TtsApiProvider.EDGE_TTS -> edgeTtsApiManager
            TtsApiProvider.GOOGLE_TTS -> googleTtsApiManager
        }

    private fun handleSpeakingStateChanged(isSpeaking: Boolean) {
        onSpeakingStateChanged(isSpeaking)
    }

    override fun speak(text: String, queueMode: Int) {
        // Stop the other manager to prevent overlapped audio if provider was changed dynamically
        when (activeProvider) {
            TtsApiProvider.EDGE_TTS -> googleTtsApiManager.stop()
            TtsApiProvider.GOOGLE_TTS -> edgeTtsApiManager.stop()
        }
        currentManager.speak(text, queueMode)
    }

    override fun stop() {
        edgeTtsApiManager.stop()
        googleTtsApiManager.stop()
    }

    override fun isSpeaking(): Boolean {
        return currentManager.isSpeaking()
    }

    override fun shutdown() {
        edgeTtsApiManager.shutdown()
        googleTtsApiManager.shutdown()
    }
}
