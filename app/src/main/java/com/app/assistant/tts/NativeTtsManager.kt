package com.app.assistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class NativeTtsManager(
    private val context: Context,
    private val onSpeakingStateChanged: (isSpeaking: Boolean) -> Unit
) : TtsManager {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    // Track active/queued utterances to prevent premature transition to LISTENING state
    private val activeUtterances = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Buffer incoming speak requests while the platform TTS engine is initializing
    private val pendingUtterances = Collections.synchronizedList(mutableListOf<Pair<String, Int>>())

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                Log.d("NativeTtsManager", "Initialization Success")
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onSpeakingStateChanged(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { activeUtterances.remove(it) }
                        if (activeUtterances.isEmpty()) {
                            onSpeakingStateChanged(false)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { activeUtterances.remove(it) }
                        if (activeUtterances.isEmpty()) {
                            onSpeakingStateChanged(false)
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        utteranceId?.let { activeUtterances.remove(it) }
                        if (activeUtterances.isEmpty()) {
                            onSpeakingStateChanged(false)
                        }
                    }
                })

                // Flush any buffered utterances now that the TTS service is bound
                synchronized(pendingUtterances) {
                    for (pair in pendingUtterances) {
                        speakInternal(pair.first, pair.second)
                    }
                    pendingUtterances.clear()
                }
            } else {
                Log.e("NativeTtsManager", "Initialization Failed")
                synchronized(pendingUtterances) {
                    pendingUtterances.clear()
                }
                onSpeakingStateChanged(false)
            }
        }
    }

    override fun speak(text: String, queueMode: Int) {
        val tts = textToSpeech
        if (tts == null) {
            Log.w("NativeTtsManager", "TTS is null")
            return
        }

        if (!isInitialized) {
            Log.i("NativeTtsManager", "TTS not initialized yet. Queueing utterance: $text")
            synchronized(pendingUtterances) {
                if (queueMode == TtsManager.QUEUE_FLUSH) {
                    pendingUtterances.clear()
                }
                pendingUtterances.add(Pair(text, queueMode))
            }
            onSpeakingStateChanged(true)
            return
        }

        speakInternal(text, queueMode)
    }

    private fun speakInternal(text: String, queueMode: Int) {
        val tts = textToSpeech ?: return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val useVoiceCall = audioManager.isBluetoothScoOn ||
                audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_IN_COMMUNICATION

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(if (useVoiceCall) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        tts.setAudioAttributes(audioAttributes)

        val mode = if (queueMode == TtsManager.QUEUE_ADD) {
            TextToSpeech.QUEUE_ADD
        } else {
            activeUtterances.clear()
            TextToSpeech.QUEUE_FLUSH
        }

        val utteranceId = "tts_utterance_id_${System.currentTimeMillis()}"
        activeUtterances.add(utteranceId)

        val result = tts.speak(text, mode, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            activeUtterances.remove(utteranceId)
            if (activeUtterances.isEmpty()) {
                onSpeakingStateChanged(false)
            }
        }
    }

    override fun stop() {
        synchronized(pendingUtterances) {
            pendingUtterances.clear()
        }
        if (isInitialized) {
            activeUtterances.clear()
            val wasSpeaking = isSpeaking()
            textToSpeech?.stop()
            if (wasSpeaking) {
                onSpeakingStateChanged(false)
            }
        } else {
            onSpeakingStateChanged(false)
        }
    }

    override fun isSpeaking(): Boolean {
        return (isInitialized && (textToSpeech?.isSpeaking == true || activeUtterances.isNotEmpty())) ||
                pendingUtterances.isNotEmpty()
    }

    override fun shutdown() {
        synchronized(pendingUtterances) {
            pendingUtterances.clear()
        }
        activeUtterances.clear()
        textToSpeech?.let {
            it.stop()
            it.shutdown()
        }
        textToSpeech = null
        isInitialized = false
    }
}