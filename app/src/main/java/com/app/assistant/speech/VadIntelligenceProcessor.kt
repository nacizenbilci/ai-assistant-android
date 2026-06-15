package com.app.assistant.speech

import android.util.Log
import com.k2fsa.sherpa.onnx.Vad

class VadIntelligenceProcessor(
    private val vad: Vad,
    private val listener: VadListener
) {
    interface VadListener {
        fun onSpeechStart()
        fun onSpeechEnd(samples: FloatArray)
    }

    private var isSpeechActive = false

    fun acceptSamples(samples: FloatArray) {
        try {
            vad.acceptWaveform(samples)

            val currentSpeechDetected = vad.isSpeechDetected()
            if (currentSpeechDetected && !isSpeechActive) {
                isSpeechActive = true
                Log.d("VadIntelligenceProcessor", "VAD Speech start transition detected!")
                listener.onSpeechStart()
            } else if (!currentSpeechDetected && isSpeechActive) {
                isSpeechActive = false
                Log.d("VadIntelligenceProcessor", "VAD Speech end transition detected!")
            }

            // Check if there are completed segments of speech to process
            while (!vad.empty()) {
                val segment = vad.front()
                vad.pop()
                Log.i("VadIntelligenceProcessor", "Completed speech segment popped from VAD. Sample count: ${segment.samples.size}")
                listener.onSpeechEnd(segment.samples)
            }
        } catch (e: Exception) {
            Log.e("VadIntelligenceProcessor", "Error processing VAD acceptWaveform", e)
        }
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
