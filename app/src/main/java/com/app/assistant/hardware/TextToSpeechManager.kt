package com.app.assistant.hardware

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log

class TextToSpeechManager(
    private val context: Context,
    private val onSpeakingStateChanged: (isSpeaking: Boolean) -> Unit
) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                Log.d("TextToSpeechManager", "Initialization Success")
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onSpeakingStateChanged(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        onSpeakingStateChanged(false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onSpeakingStateChanged(false)
                    }
                })
            } else {
                Log.e("TextToSpeechManager", "Initialization Failed")
            }
        }
    }

    fun speak(text: String) {
        val tts = textToSpeech
        if (tts == null || !isInitialized) {
            Log.w("TextToSpeechManager", "TTS not initialized or null")
            return
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance_id")
    }

    fun stop() {
        if (isInitialized) {
            textToSpeech?.stop()
        }
    }

    fun isSpeaking(): Boolean {
        return isInitialized && textToSpeech?.isSpeaking == true
    }

    fun shutdown() {
        textToSpeech?.let {
            it.stop()
            it.shutdown()
        }
        textToSpeech = null
        isInitialized = false
    }
}
