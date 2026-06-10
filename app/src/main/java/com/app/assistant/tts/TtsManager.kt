package com.app.assistant.tts

interface TtsManager {
    fun speak(text: String, queueMode: Int = QUEUE_FLUSH)
    fun stop()
    fun isSpeaking(): Boolean
    fun shutdown()

    companion object {
        const val QUEUE_FLUSH = 0
        const val QUEUE_ADD = 1
    }
}

enum class TtsMode {
    NATIVE,
    OFFLINE
}
