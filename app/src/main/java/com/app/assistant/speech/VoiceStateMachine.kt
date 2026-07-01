package com.app.assistant.speech

class VoiceStateMachine(
    private val isHandsFree: Boolean,
    private val callback: StateCallback
) {
    interface StateCallback {
        fun onTransitionToListening()
        fun onTransitionToProcessing(speechSamples: FloatArray)
        fun onTransitionToBotSpeaking()
        fun onTransitionToIdle()
        fun stopTtsPlayback()
        fun onMicReady(ready: Boolean)
    }

    private var state = VoiceState.IDLE
    private var lastTtsStartTime: Long = 0L

    @Synchronized
    fun getCurrentState(): VoiceState = state

    @Synchronized
    fun onUserStartedListening() {
        if (state == VoiceState.IDLE) {
            state = VoiceState.LISTENING
            logD("State transition: IDLE -> LISTENING")
            callback.onMicReady(true)
            callback.onTransitionToListening()
        }
    }

    @Synchronized
    fun onUserStoppedListening() {
        if (state != VoiceState.IDLE) {
            logD("State transition: $state -> IDLE (stopped manually)")
            state = VoiceState.IDLE
            callback.onMicReady(false)
            callback.onTransitionToIdle()
        }
    }

    @Synchronized
    fun onSpeechStartDetected() {
        when (state) {
            VoiceState.BOT_SPEAKING -> {
                val elapsedSinceTtsStart = System.currentTimeMillis() - lastTtsStartTime
                if (elapsedSinceTtsStart < 300) {
                    logD("Ignoring speech start detected during echo bleed window ($elapsedSinceTtsStart ms < 300 ms).")
                    return
                }
                logI("[Interruption] Speech detected during TTS. Stopping TTS and listening.")
                callback.stopTtsPlayback()
                state = VoiceState.LISTENING
                callback.onMicReady(true)
                callback.onTransitionToListening()
            }
            VoiceState.LISTENING -> {
                logD("VAD Speech start detected while listening.")
                callback.onTransitionToListening()
            }
            else -> {
                logD("VAD Speech start detected during state: $state. Ignoring.")
            }
        }
    }

    @Synchronized
    fun onSpeechEndDetected(samples: FloatArray) {
        if (state == VoiceState.LISTENING || state == VoiceState.BOT_SPEAKING) {
            logD("State transition: $state -> PROCESSING")
            state = VoiceState.PROCESSING
            callback.onMicReady(false)
            callback.onTransitionToProcessing(samples)
        } else {
            logD("VAD Speech end detected during state: $state. Ignoring.")
        }
    }

    @Synchronized
    fun onTtsStateChanged(isSpeaking: Boolean) {
        logD("TTS state changed: isSpeaking = $isSpeaking, current state = $state")
        when {
            isSpeaking -> {
                lastTtsStartTime = System.currentTimeMillis()
                if (state == VoiceState.PROCESSING || state == VoiceState.LISTENING) {
                    logD("State transition: $state -> BOT_SPEAKING")
                    state = VoiceState.BOT_SPEAKING
                    callback.onTransitionToBotSpeaking()
                }
            }
            else -> {
                // TTS stopped
                if (state == VoiceState.BOT_SPEAKING) {
                    if (isHandsFree) {
                        logD("State transition: BOT_SPEAKING -> LISTENING (Hands-Free auto-loop)")
                        state = VoiceState.LISTENING
                        callback.onMicReady(true)
                        callback.onTransitionToListening()
                    } else {
                        logD("State transition: BOT_SPEAKING -> IDLE")
                        state = VoiceState.IDLE
                        callback.onMicReady(false)
                        callback.onTransitionToIdle()
                    }
                }
            }
        }
    }

    private fun logD(msg: String) {
        try {
            android.util.Log.d("VoiceStateMachine", msg)
        } catch (e: Throwable) {
            println("[VoiceStateMachine] D: $msg")
        }
    }

    private fun logI(msg: String) {
        try {
            android.util.Log.i("VoiceStateMachine", msg)
        } catch (e: Throwable) {
            println("[VoiceStateMachine] I: $msg")
        }
    }
}
