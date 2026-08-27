package com.app.assistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class NativeTtsManager(
    private val context: Context,
    private val onSpeakingStateChanged: (isSpeaking: Boolean) -> Unit
) : TtsManager {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    private val activeUtterances =
        Collections.newSetFromMap(
            ConcurrentHashMap<String, Boolean>()
        )

    private val pendingUtterances =
        Collections.synchronizedList(
            mutableListOf<Pair<String, Int>>()
        )

    init {
        // Samsung sistem ayarını tamamen bypass et.
        // Uygulama doğrudan Google TTS motorunu kullanacak.
        textToSpeech = TextToSpeech(
            context.applicationContext,
            { status ->

                if (status == TextToSpeech.SUCCESS) {

                    isInitialized = true

                    configureSabanVoice()

                    Log.d(
                        TAG,
                        "Google TTS initialization success"
                    )

                    textToSpeech?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {

                            override fun onStart(
                                utteranceId: String?
                            ) {
                                onSpeakingStateChanged(true)
                            }

                            override fun onDone(
                                utteranceId: String?
                            ) {
                                utteranceId?.let {
                                    activeUtterances.remove(it)
                                }

                                if (activeUtterances.isEmpty()) {
                                    onSpeakingStateChanged(false)
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(
                                utteranceId: String?
                            ) {
                                utteranceId?.let {
                                    activeUtterances.remove(it)
                                }

                                if (activeUtterances.isEmpty()) {
                                    onSpeakingStateChanged(false)
                                }
                            }

                            override fun onStop(
                                utteranceId: String?,
                                interrupted: Boolean
                            ) {
                                utteranceId?.let {
                                    activeUtterances.remove(it)
                                }

                                if (activeUtterances.isEmpty()) {
                                    onSpeakingStateChanged(false)
                                }
                            }
                        }
                    )

                    synchronized(pendingUtterances) {

                        for (pair in pendingUtterances) {
                            speakInternal(
                                pair.first,
                                pair.second
                            )
                        }

                        pendingUtterances.clear()
                    }

                } else {

                    Log.e(
                        TAG,
                        "Google TTS initialization failed: $status"
                    )

                    synchronized(pendingUtterances) {
                        pendingUtterances.clear()
                    }

                    onSpeakingStateChanged(false)
                }
            },
            GOOGLE_TTS_ENGINE
        )
    }

    private fun configureSabanVoice() {

        val tts = textToSpeech ?: return

        val turkishLocale =
            Locale("tr", "TR")

        val languageResult =
            tts.setLanguage(turkishLocale)

        if (
            languageResult ==
            TextToSpeech.LANG_MISSING_DATA ||
            languageResult ==
            TextToSpeech.LANG_NOT_SUPPORTED
        ) {

            Log.e(
                TAG,
                "Google TTS Turkish language is unavailable"
            )

            return
        }

        val voices =
            tts.voices
                ?.filter {
                    it.locale.language.equals(
                        "tr",
                        ignoreCase = true
                    )
                }
                ?: emptyList()

        Log.d(
            TAG,
            "Available Turkish voices: " +
                voices.joinToString {
                    it.name
                }
        )

        // Google Android TTS'de bilinen Türkçe erkek
        // seslerini öncelikli kullan.
        val preferredPrefixes = listOf(
            "tr-tr-x-ama",
            "tr-tr-x-tmc"
        )

        var selectedVoice =
            preferredPrefixes.firstNotNullOfOrNull {
                prefix ->

                voices.firstOrNull {
                    it.name.startsWith(
                        prefix,
                        ignoreCase = true
                    )
                }
            }

        // Erkek ses adı cihazdaki sürümde farklıysa
        // önce Türkiye Türkçesi olan sesi dene.
        if (selectedVoice == null) {

            selectedVoice =
                voices.firstOrNull {
                    it.locale.country.equals(
                        "TR",
                        ignoreCase = true
                    )
                }
        }

        if (selectedVoice != null) {

            tts.voice = selectedVoice

            Log.d(
                TAG,
                "SABAN voice selected: ${selectedVoice.name}"
            )

        } else {

            Log.w(
                TAG,
                "Specific Turkish voice not found; " +
                    "using Google Turkish default."
            )
        }

        // Şaban için biraz daha sıcak ve erkek karakter.
        tts.setPitch(0.88f)
        tts.setSpeechRate(0.96f)
    }

    override fun speak(
        text: String,
        queueMode: Int
    ) {

        val tts = textToSpeech

        if (tts == null) {

            Log.w(
                TAG,
                "TTS is null"
            )

            return
        }

        if (!isInitialized) {

            synchronized(pendingUtterances) {

                if (
                    queueMode ==
                    TtsManager.QUEUE_FLUSH
                ) {
                    pendingUtterances.clear()
                }

                pendingUtterances.add(
                    Pair(
                        text,
                        queueMode
                    )
                )
            }

            onSpeakingStateChanged(true)

            return
        }

        speakInternal(
            text,
            queueMode
        )
    }

    private fun speakInternal(
        text: String,
        queueMode: Int
    ) {

        val tts =
            textToSpeech
                ?: return

        // Her konuşmada Türkçe ayarının korunmasını sağla.
        tts.language =
            Locale("tr", "TR")

        val audioManager =
            context.getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager

        val useVoiceCall =
            audioManager.isBluetoothScoOn ||
                audioManager.mode ==
                AudioManager.MODE_IN_CALL ||
                audioManager.mode ==
                AudioManager.MODE_IN_COMMUNICATION

        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(
                    if (useVoiceCall) {
                        AudioAttributes
                            .USAGE_VOICE_COMMUNICATION
                    } else {
                        AudioAttributes.USAGE_MEDIA
                    }
                )
                .setContentType(
                    AudioAttributes.CONTENT_TYPE_SPEECH
                )
                .build()

        tts.setAudioAttributes(
            audioAttributes
        )

        val mode =
            if (
                queueMode ==
                TtsManager.QUEUE_ADD
            ) {

                TextToSpeech.QUEUE_ADD

            } else {

                activeUtterances.clear()

                TextToSpeech.QUEUE_FLUSH
            }

        val utteranceId =
            "saban_${System.currentTimeMillis()}"

        activeUtterances.add(
            utteranceId
        )

        val result =
            tts.speak(
                text,
                mode,
                null,
                utteranceId
            )

        if (
            result !=
            TextToSpeech.SUCCESS
        ) {

            activeUtterances.remove(
                utteranceId
            )

            if (
                activeUtterances.isEmpty()
            ) {
                onSpeakingStateChanged(false)
            }
        }
    }

    override fun stop() {

        synchronized(pendingUtterances) {
            pendingUtterances.clear()
        }

        activeUtterances.clear()

        textToSpeech?.stop()

        onSpeakingStateChanged(false)
    }

    override fun isSpeaking(): Boolean {

        return (
            isInitialized &&
                (
                    textToSpeech?.isSpeaking == true ||
                        activeUtterances.isNotEmpty()
                )
            ) ||
            pendingUtterances.isNotEmpty()
    }

    override fun shutdown() {

        synchronized(pendingUtterances) {
            pendingUtterances.clear()
        }

        activeUtterances.clear()

        textToSpeech?.stop()
        textToSpeech?.shutdown()

        textToSpeech = null
        isInitialized = false
    }

    companion object {

        private const val TAG =
            "NativeTtsManager"

        private const val GOOGLE_TTS_ENGINE =
            "com.google.android.tts"
    }
}
