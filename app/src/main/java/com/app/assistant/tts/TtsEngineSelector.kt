package com.app.assistant.tts

import android.content.Context
import com.app.assistant.repository.SettingsRepository
import java.io.File

class TtsEngineSelector(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val onSpeakingStateChanged: (isSpeaking: Boolean) -> Unit
) : TtsManager {

    private val nativeTtsManager = NativeTtsManager(context, onSpeakingStateChanged)
    private val offlineTtsManager = OfflineTtsManager(context, settingsRepository, onSpeakingStateChanged)
    private val apiTtsManager = ApiTtsManager(context, settingsRepository, onSpeakingStateChanged)

    override fun speak(text: String, queueMode: Int) {
        val mode = settingsRepository.getTtsMode()
        
        if (queueMode == TtsManager.QUEUE_FLUSH) {
            nativeTtsManager.stop()
            offlineTtsManager.stop()
            apiTtsManager.stop()
        }

        when (mode) {
            TtsMode.OFFLINE -> {
                if (isOfflineModelInstalled()) {
                    offlineTtsManager.speak(text, queueMode)
                } else {
                    nativeTtsManager.speak(text, queueMode)
                }
            }
            TtsMode.API -> {
                apiTtsManager.speak(text, queueMode)
            }
            else -> {
                nativeTtsManager.speak(text, queueMode)
            }
        }
    }

    override fun stop() {
        nativeTtsManager.stop()
        offlineTtsManager.stop()
        apiTtsManager.stop()
    }

    override fun isSpeaking(): Boolean {
        val mode = settingsRepository.getTtsMode()
        return when (mode) {
            TtsMode.OFFLINE -> {
                if (isOfflineModelInstalled()) {
                    offlineTtsManager.isSpeaking()
                } else {
                    nativeTtsManager.isSpeaking()
                }
            }
            TtsMode.API -> {
                apiTtsManager.isSpeaking()
            }
            else -> {
                nativeTtsManager.isSpeaking()
            }
        }
    }

    override fun shutdown() {
        nativeTtsManager.shutdown()
        offlineTtsManager.shutdown()
        apiTtsManager.shutdown()
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
