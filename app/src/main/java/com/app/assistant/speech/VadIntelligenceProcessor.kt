package com.app.assistant.speech

import android.util.Log
import com.k2fsa.sherpa.onnx.Vad
import kotlin.math.max
import kotlin.math.sqrt

class VadIntelligenceProcessor(
    private val vad: Vad,
    private val listener: VadListener
) {
    interface VadListener {
        fun onSpeechStart()
        fun onSpeechEnd(samples: FloatArray)
    }

    private var isSpeechActive = false

    private var ambientNoiseFloor = 0.002f

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val MIN_SPEECH_DURATION_SEC = 0.4f
        private const val MIN_SAMPLES_REQUIRED = (SAMPLE_RATE * MIN_SPEECH_DURATION_SEC).toInt()

        private const val ABSOLUTE_MIN_THRESHOLD = 0.0015f

        private const val SNR_MULTIPLIER = 1.8f

        private const val EMA_ALPHA = 0.05f
    }

    fun acceptSamples(samples: FloatArray) {
        try {
            vad.acceptWaveform(samples)

            val currentSpeechDetected = vad.isSpeechDetected()

            if (!currentSpeechDetected && samples.isNotEmpty()) {
                val chunkRms = calculateRMS(samples)
                ambientNoiseFloor = (ambientNoiseFloor * (1f - EMA_ALPHA)) + (chunkRms * EMA_ALPHA)
            }

            if (currentSpeechDetected && !isSpeechActive) {
                isSpeechActive = true
                Log.d("VadIntelligenceProcessor", "VAD Speech start transition detected!")
                listener.onSpeechStart()
            } else if (!currentSpeechDetected && isSpeechActive) {
                isSpeechActive = false
                Log.d("VadIntelligenceProcessor", "VAD Speech end transition detected!")
            }

            while (!vad.empty()) {
                val segment = vad.front()
                vad.pop()

                val audioSamples = segment.samples

                if (audioSamples.size < MIN_SAMPLES_REQUIRED) {
                    Log.d("VadIntelligenceProcessor", "Rejected segment: Too short (${audioSamples.size} samples)")
                    continue
                }

                val rms = calculateRMS(audioSamples)
                val dynamicThreshold = max(ABSOLUTE_MIN_THRESHOLD, ambientNoiseFloor * SNR_MULTIPLIER)

                if (rms < dynamicThreshold) {
                    Log.d("VadIntelligenceProcessor", "Rejected segment: Low energy (RMS: $rms, Dynamic Threshold: $dynamicThreshold)")
                    continue
                }

                Log.i("VadIntelligenceProcessor", "Valid speech verified. Samples: ${audioSamples.size}, RMS: $rms, Threshold: $dynamicThreshold")
                listener.onSpeechEnd(audioSamples)
            }
        } catch (e: Exception) {
            Log.e("VadIntelligenceProcessor", "Error processing VAD acceptWaveform", e)
        }
    }

    private fun calculateRMS(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0f
        for (sample in samples) {
            sum += sample * sample
        }
        return sqrt(sum / samples.size)
    }

    fun clear() {
        try {
            vad.clear()
            isSpeechActive = false
            Log.d("VadIntelligenceProcessor", "VAD state cleared.")
        } catch (e: Exception) {
            Log.e("VadIntelligenceProcessor", "Error clearing VAD", e)
        }
    }
}