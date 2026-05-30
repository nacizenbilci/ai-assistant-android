package com.app.assistant.hardware

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class SpeechRecognizerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var originalRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var isListening = false

    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                stopBluetoothSco()
                cleanupSpeechRecognizer()
            }
        }
        override fun onConfigurationChanged(newConfig: Configuration) {}
        override fun onLowMemory() {}
    }

    init {
        context.registerComponentCallbacks(componentCallbacks)
    }

    interface SpeechListener {
        fun onReadyForSpeech()
        fun onBeginningOfSpeech()
        fun onEndOfSpeech()
        fun onError(errorCode: Int)
        fun onResults(recognizedText: String)
        fun onPartialResults(recognizedText: String)
    }

    fun startListening(
        languageCode: String?,
        isTranslationEnabled: Boolean,
        listener: SpeechListener
    ) {
        try {
            cleanupSpeechRecognizer()

            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isBluetoothScoOn = true
            audioManager.startBluetoothSco()

            originalRingerMode = audioManager.ringerMode

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (isTranslationEnabled && !languageCode.isNullOrEmpty()) {
                    val localeCode = getLocaleCode(languageCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeCode)
                } else {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                }
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(bundle: Bundle?) {
                    if (originalRingerMode == AudioManager.RINGER_MODE_NORMAL) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    }
                    listener.onReadyForSpeech()
                }

                override fun onBeginningOfSpeech() {
                    isListening = true
                    listener.onBeginningOfSpeech()
                }

                override fun onRmsChanged(v: Float) {}

                override fun onBufferReceived(bytes: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    listener.onEndOfSpeech()
                    restoreRingerMode()
                }

                override fun onError(errorCode: Int) {
                    isListening = false
                    listener.onError(errorCode)
                    restoreRingerMode()
                }

                override fun onResults(bundle: Bundle?) {
                    isListening = false
                    val recognizedText = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                    listener.onResults(recognizedText)
                }

                override fun onPartialResults(bundle: Bundle) {
                    val recognizedText = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                    listener.onPartialResults(recognizedText)
                }

                override fun onEvent(i: Int, bundle: Bundle?) {}
            })

            recognizer.startListening(speechRecognizerIntent)
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error starting speech recognition", e)
            listener.onError(-1)
        }
    }

    private fun restoreRingerMode() {
        if (originalRingerMode == AudioManager.RINGER_MODE_NORMAL) {
            scope.launch {
                delay(800)
                audioManager.ringerMode = originalRingerMode
            }
        }
    }

    private fun stopBluetoothSco() {
        try {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error stopping Bluetooth SCO", e)
        }
    }

    private fun cleanupSpeechRecognizer() {
        speechRecognizer?.let {
            it.destroy()
            speechRecognizer = null
        }
    }

    fun destroy() {
        context.unregisterComponentCallbacks(componentCallbacks)
        stopBluetoothSco()
        cleanupSpeechRecognizer()
    }

    private fun getLocaleCode(languageCode: String): String {
        val languageCountryMapping = mapOf(
            "af" to "ZA", "sq" to "AL", "ar" to "SA", "be" to "BY", "bg" to "BG",
            "bn" to "BD", "ca" to "ES", "zh" to "CN", "hr" to "HR", "cs" to "CZ",
            "da" to "DK", "nl" to "NL", "en" to "US", "eo" to "EO", "et" to "EE",
            "fi" to "FI", "fr" to "FR", "gl" to "ES", "ka" to "GE", "de" to "DE",
            "el" to "GR", "gu" to "IN", "ht" to "HT", "he" to "IL", "hi" to "IN",
            "hu" to "HU", "is" to "IS", "id" to "ID", "ga" to "IE", "it" to "IT",
            "ja" to "JP", "kn" to "IN", "ko" to "KR", "lt" to "LT", "lv" to "LV",
            "mk" to "MK", "mr" to "IN", "ms" to "MY", "mt" to "MT", "no" to "NO",
            "fa" to "IR", "pl" to "PL", "pt" to "BR", "ro" to "RO", "ru" to "RU",
            "sk" to "SK", "sl" to "SI", "es" to "ES", "sv" to "SE", "sw" to "KE",
            "tl" to "PH", "ta" to "IN", "te" to "IN", "th" to "TH", "tr" to "TR",
            "uk" to "UA", "ur" to "PK", "vi" to "VN", "cy" to "GB"
        )
        val countryCode = languageCountryMapping[languageCode] ?: Locale.getDefault().country
        return "$languageCode-$countryCode"
    }
}
