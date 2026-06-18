package com.app.assistant.tts

import android.content.Context
import com.app.assistant.config.SpeechConfig
import com.app.assistant.config.TtsApiProvider
import com.app.assistant.repository.SettingsRepository
import okhttp3.OkHttpClient

class ApiTtsManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient,
    private val onSpeakingStateChanged: (isSpeaking: Boolean) -> Unit
) : TtsManager {

    private var edgeTtsApiManager: EdgeTtsApiManager? = null
    private var googleTtsApiManager: GoogleTtsApiManager? = null

    private fun getEdgeTtsApiManager(): EdgeTtsApiManager {
        return edgeTtsApiManager ?: EdgeTtsApiManager(
            context,
            settingsRepository,
            okHttpClient,
            ::handleSpeakingStateChanged
        ).also { edgeTtsApiManager = it }
    }

    private fun getGoogleTtsApiManager(): GoogleTtsApiManager {
        return googleTtsApiManager ?: GoogleTtsApiManager(
            context,
            settingsRepository,
            okHttpClient,
            ::handleSpeakingStateChanged
        ).also { googleTtsApiManager = it }
    }

    private val activeProvider: TtsApiProvider
        get() = SpeechConfig.ACTIVE_TTS_PROVIDER

    private val currentManager: TtsManager
        get() = when (activeProvider) {
            TtsApiProvider.EDGE_TTS -> getEdgeTtsApiManager()
            TtsApiProvider.GOOGLE_TTS -> getGoogleTtsApiManager()
        }

    private fun handleSpeakingStateChanged(isSpeaking: Boolean) {
        onSpeakingStateChanged(isSpeaking)
    }

    override fun speak(text: String, queueMode: Int) {
        // Stop the other manager to prevent overlapped audio if provider was changed dynamically
        when (activeProvider) {
            TtsApiProvider.EDGE_TTS -> googleTtsApiManager?.stop()
            TtsApiProvider.GOOGLE_TTS -> edgeTtsApiManager?.stop()
        }
        currentManager.speak(text, queueMode)
    }

    override fun stop() {
        edgeTtsApiManager?.stop()
        googleTtsApiManager?.stop()
    }

    override fun isSpeaking(): Boolean {
        val manager = when (activeProvider) {
            TtsApiProvider.EDGE_TTS -> edgeTtsApiManager
            TtsApiProvider.GOOGLE_TTS -> googleTtsApiManager
        }
        return manager?.isSpeaking() ?: false
    }

    override fun shutdown() {
        edgeTtsApiManager?.shutdown()
        googleTtsApiManager?.shutdown()
    }
}
