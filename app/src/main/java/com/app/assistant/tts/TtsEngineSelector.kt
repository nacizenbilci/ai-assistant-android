package com.app.assistant.tts

import android.content.Context
import com.app.assistant.repository.SettingsRepository
import java.io.File

class TtsEngineSelector(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: okhttp3.OkHttpClient = com.app.assistant.viewmodel.MainViewModelFactory.okHttpClient,
    private val onSpeakingStateChanged: (isSpeaking: Boolean) -> Unit
) : TtsManager {

    private var nativeTtsManager: NativeTtsManager? = null
    private var offlineTtsManager: OfflineTtsManager? = null
    private var apiTtsManager: ApiTtsManager? = null

    private fun getNativeTtsManager(): NativeTtsManager {
        return nativeTtsManager ?: NativeTtsManager(context, onSpeakingStateChanged).also { nativeTtsManager = it }
    }

    private fun getOfflineTtsManager(): OfflineTtsManager {
        return offlineTtsManager ?: OfflineTtsManager(context, settingsRepository, onSpeakingStateChanged).also { offlineTtsManager = it }
    }

    private fun getApiTtsManager(): ApiTtsManager {
        return apiTtsManager ?: ApiTtsManager(context, settingsRepository, okHttpClient, onSpeakingStateChanged).also { apiTtsManager = it }
    }

    override fun speak(text: String, queueMode: Int) {
        val mode = settingsRepository.getTtsMode()
        
        if (queueMode == TtsManager.QUEUE_FLUSH) {
            nativeTtsManager?.stop()
            offlineTtsManager?.stop()
            apiTtsManager?.stop()
        }

        when (mode) {
            TtsMode.OFFLINE -> {
                if (isOfflineModelInstalled()) {
                    getOfflineTtsManager().speak(text, queueMode)
                } else {
                    getNativeTtsManager().speak(text, queueMode)
                }
            }
            TtsMode.API -> {
                getApiTtsManager().speak(text, queueMode)
            }
            else -> {
                getNativeTtsManager().speak(text, queueMode)
            }
        }
    }

    override fun stop() {
        nativeTtsManager?.stop()
        offlineTtsManager?.stop()
        apiTtsManager?.stop()
    }

    override fun isSpeaking(): Boolean {
        val mode = settingsRepository.getTtsMode()
        return when (mode) {
            TtsMode.OFFLINE -> {
                if (isOfflineModelInstalled()) {
                    offlineTtsManager?.isSpeaking() ?: false
                } else {
                    nativeTtsManager?.isSpeaking() ?: false
                }
            }
            TtsMode.API -> {
                apiTtsManager?.isSpeaking() ?: false
            }
            else -> {
                nativeTtsManager?.isSpeaking() ?: false
            }
        }
    }

    override fun shutdown() {
        nativeTtsManager?.shutdown()
        offlineTtsManager?.shutdown()
        apiTtsManager?.shutdown()
    }

    private fun isOfflineModelInstalled(): Boolean {
        val supertonicDir = File(context.filesDir, "sherpa-onnx-tts/sherpa-onnx-supertonic-3-tts-int8-2026-05-11")
        val durationPredictor = File(supertonicDir, "duration_predictor.int8.onnx")
        val textEncoder = File(supertonicDir, "text_encoder.int8.onnx")
        val vectorEstimator = File(supertonicDir, "vector_estimator.int8.onnx")
        val vocoder = File(supertonicDir, "vocoder.int8.onnx")
        val ttsJson = File(supertonicDir, "tts.json")
        val unicodeIndexer = File(supertonicDir, "unicode_indexer.bin")
        val voiceStyle = File(supertonicDir, "voice.bin")
        return durationPredictor.exists() && textEncoder.exists() && vectorEstimator.exists() &&
               vocoder.exists() && ttsJson.exists() && unicodeIndexer.exists() && voiceStyle.exists()
    }
}
