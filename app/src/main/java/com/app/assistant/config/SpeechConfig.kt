package com.app.assistant.config

import com.app.assistant.BuildConfig
import com.app.assistant.repository.SettingsRepository

enum class TtsApiProvider {
    EDGE_TTS,
    GOOGLE_TTS
}

enum class SttApiProvider {
    GROQ_WHISPER,
    GOOGLE_STT
}

object SpeechConfig {
    // Current Active Providers - change these to switch implementations and rebuild
    var ACTIVE_TTS_PROVIDER = TtsApiProvider.EDGE_TTS
    val ACTIVE_STT_PROVIDER = SttApiProvider.GROQ_WHISPER

    // Edge TTS Configurations
    object EdgeTts {
        const val WSS_URL = "wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1"
        val SUBSCRIPTION_KEY = BuildConfig.EDGE_TTS_SUBSCRIPTION_KEY
        const val VOICE = "en-IN-NeerjaNeural"
        const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
    }

    // Google TTS Configurations
    object GoogleTts {
        const val BASE_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
        const val VOICE = "en-US-Wavenet-D"
        const val AUDIO_ENCODING = "MP3"
        
        fun getApiKey(settingsRepository: SettingsRepository): String {
            return settingsRepository.getChatApiKey() ?: ""
        }
    }

    // Groq Whisper Configurations
    object GroqWhisper {
        const val BASE_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
        const val MODEL = "whisper-large-v3"
        
        // Retrieve key from BuildConfig (local.properties)
        val API_KEY: String
            get() = BuildConfig.GROQ_API_KEY
    }

    // Google STT Configurations
    object GoogleStt {
        const val BASE_URL = "https://speech.googleapis.com/v1/speech:recognize"
        const val ENCODING = "LINEAR16"
        const val SAMPLE_RATE_HERTZ = 16000
    }
}
