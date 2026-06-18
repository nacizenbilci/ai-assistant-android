package com.app.assistant.speech

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import com.app.assistant.config.SpeechConfig

class SpeechRecognizerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val settingsRepository = com.app.assistant.repository.SettingsRepository(context)
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private var vad: com.k2fsa.sherpa.onnx.Vad? = null
    private var offlineRecognizer: com.k2fsa.sherpa.onnx.OfflineRecognizer? = null

    private val modelManager = SpeechModelManager(context)
    private var isInHybridTransition = false
    private var hybridPrefixText = ""

    private var activeListener: SpeechListener? = null
    private var isHandsFreeMode = false
    @Volatile
    private var isMicMuted = false

    private var audioHygieneProcessor: AudioHygieneProcessor? = null
    private var vadIntelligenceProcessor: VadIntelligenceProcessor? = null
    private var voiceStateMachine: VoiceStateMachine? = null

    @Volatile
    var isTtsSpeaking = false
        set(value) {
            field = value
            voiceStateMachine?.onTtsStateChanged(value)
        }

    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                stop()
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

    @Synchronized
    private fun initVad() {
        if (vad != null) return
        try {
            if (!modelManager.isModelDownloaded()) {
                Log.w("SpeechRecognizerManager", "Cannot init VAD: Model files not downloaded.")
                return
            }

            val sileroConfig = com.k2fsa.sherpa.onnx.SileroVadModelConfig(
                model = modelManager.getVadPath(),
                threshold = 0.5f,
                minSilenceDuration = 0.5f,
                minSpeechDuration = 0.1f,
                windowSize = 512,
                maxSpeechDuration = 10.0f
            )
            val vadConfig = com.k2fsa.sherpa.onnx.VadModelConfig(
                sileroVadModelConfig = sileroConfig,
                sampleRate = 16000,
                numThreads = 4,
                provider = "cpu"
            )
            vad = com.k2fsa.sherpa.onnx.Vad(
                assetManager = null,
                config = vadConfig
            )
            Log.i("SpeechRecognizerManager", "[Silero VAD] Initialized successfully")
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Failed to initialize VAD from disk", e)
        }
    }

    @Synchronized
    private fun initOfflineRecognizer() {
        if (offlineRecognizer != null) return
        try {
            if (!modelManager.isModelDownloaded()) {
                Log.w("SpeechRecognizerManager", "Cannot init Parakeet: Model files not downloaded.")
                return
            }

            val transducerConfig = com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig(
                encoder = modelManager.getEncoderPath(),
                decoder = modelManager.getDecoderPath(),
                joiner = modelManager.getJoinerPath()
            )
            val modelConfig = com.k2fsa.sherpa.onnx.OfflineModelConfig(
                transducer = transducerConfig,
                tokens = modelManager.getTokensPath(),
                modelType = "nemo_transducer",
                numThreads = 2,
                provider = "cpu"
            )
            val recognizerConfig = com.k2fsa.sherpa.onnx.OfflineRecognizerConfig(
                modelConfig = modelConfig,
                featConfig = com.k2fsa.sherpa.onnx.FeatureConfig(
                    sampleRate = 16000,
                    featureDim = 128
                ),
                decodingMethod = "greedy_search"
            )
            offlineRecognizer = com.k2fsa.sherpa.onnx.OfflineRecognizer(
                assetManager = null,
                config = recognizerConfig
            )
            Log.i("SpeechRecognizerManager", "[Parakeet Recognizer] Initialized successfully")
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Failed to initialize Parakeet from disk", e)
        }
    }

    fun preLoadModelAsync() {
        val mode = settingsRepository.getSttMode()
        if (mode == SttMode.PARAKEET && modelManager.isModelDownloaded()) {
            scope.launch(Dispatchers.IO) {
                initVad()
                initOfflineRecognizer()
            }
        }
    }

    fun startListening(isHandsFree: Boolean = false, listener: SpeechListener) {
        val mode = settingsRepository.getSttMode()
        Log.i("SpeechRecognizerManager", "[Listening] Start Listening requested. Hands-free: $isHandsFree, STT Mode: $mode")
        
        isInHybridTransition = false
        hybridPrefixText = ""
        activeListener = listener
        isHandsFreeMode = isHandsFree

        if (isHandsFree) {
            if (modelManager.isModelDownloaded()) {
                startThreeLayerPipeline(isHandsFree = true, listener)
            } else {
                Log.w("SpeechRecognizerManager", "Hands-free requested but model not downloaded. Falling back to Native STT.")
                startNativeListening(listener)
            }
            return
        }

        when (mode) {
            SttMode.NATIVE -> {
                startNativeListening(listener)
            }
            SttMode.PARAKEET -> {
                if (modelManager.isModelDownloaded()) {
                    startThreeLayerPipeline(isHandsFree = false, listener)
                } else {
                    Log.w("SpeechRecognizerManager", "Parakeet selected but not downloaded. Falling back to Native STT.")
                    startNativeListening(listener)
                }
            }
            SttMode.HYBRID -> {
                if (modelManager.isModelDownloaded()) {
                    if (offlineRecognizer != null && vad != null) {
                        startThreeLayerPipeline(isHandsFree = false, listener)
                    } else {
                        startHybridTransitionListening(listener)
                    }
                } else {
                    Log.w("SpeechRecognizerManager", "Hybrid selected but model not downloaded. Using Native STT.")
                    startNativeListening(listener)
                }
            }
            SttMode.API -> {
                if (modelManager.isModelDownloaded()) {
                    startThreeLayerPipeline(isHandsFree = isHandsFree, listener)
                } else {
                    Log.w("SpeechRecognizerManager", "STT API selected but VAD model not downloaded. Falling back to Native STT.")
                    startNativeListening(listener)
                }
            }
        }
    }

    private val stateMachineCallback = object : VoiceStateMachine.StateCallback {
        override fun onTransitionToListening() {
            scope.launch(Dispatchers.Main) {
                isListening = true
                activeListener?.onBeginningOfSpeech()
            }
        }

        override fun onTransitionToProcessing(speechSamples: FloatArray) {
            scope.launch(Dispatchers.Main) {
                isListening = false
                activeListener?.onEndOfSpeech()
            }
            scope.launch(Dispatchers.IO) {
                transcribeAndDeliver(speechSamples)
            }
        }

        override fun onTransitionToBotSpeaking() {
            isListening = false
            vadIntelligenceProcessor?.clear()
        }

        override fun onTransitionToIdle() {
            isListening = false
            stopPipeline()
        }

        override fun stopTtsPlayback() {
            // This propagates TTS state change
            isTtsSpeaking = false
        }

        override fun onMicReady(ready: Boolean) {
            scope.launch(Dispatchers.Main) {
                if (ready) {
                    activeListener?.onReadyForSpeech()
                }
            }
        }
    }

    private fun startThreeLayerPipeline(isHandsFree: Boolean, listener: SpeechListener) {
        scope.launch {
            try {
                cleanupSpeechRecognizer()
                withContext(Dispatchers.IO) {
                    initVad()
                    val mode = settingsRepository.getSttMode()
                    if (mode == SttMode.PARAKEET || mode == SttMode.HYBRID || mode == SttMode.NATIVE) {
                        initOfflineRecognizer()
                    }

                    val activeVad = vad
                    if (activeVad == null) {
                        Log.e("SpeechRecognizerManager", "VAD is null. Cannot listen.")
                        withContext(Dispatchers.Main) {
                            listener.onError(-1)
                        }
                        return@withContext
                    }

                    // Stop any existing pipeline before starting a new one
                    stopPipeline()

                    // Initialize the Orchestrator (Layer 3)
                    val stateMachine = VoiceStateMachine(isHandsFree, stateMachineCallback)
                    voiceStateMachine = stateMachine

                    // Initialize the Intelligence Layer (Layer 2)
                    val vadProcessor = VadIntelligenceProcessor(activeVad, object : VadIntelligenceProcessor.VadListener {
                        override fun onSpeechStart() {
                            stateMachine.onSpeechStartDetected()
                        }

                        override fun onSpeechEnd(samples: FloatArray) {
                            stateMachine.onSpeechEndDetected(samples)
                        }
                    })
                    vadIntelligenceProcessor = vadProcessor

                    // Initialize the Audio Hygiene Layer (Layer 1)
                    val audioProcessor = AudioHygieneProcessor(context, scope, isHandsFree) { floatSamples ->
                        // Pass samples directly up to Layer 2
                        vadProcessor.acceptSamples(floatSamples)
                    }
                    audioHygieneProcessor = audioProcessor

                    vadProcessor.clear()
                    if (isMicMuted) {
                        Log.i("SpeechRecognizerManager", "Pipeline initialized but mic is muted. Not starting AudioRecord.")
                        stateMachine.onUserStartedListening()
                    } else {
                        if (audioProcessor.start()) {
                            stateMachine.onUserStartedListening()
                        } else {
                            withContext(Dispatchers.Main) {
                                listener.onError(-1)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SpeechRecognizerManager", "Error starting 3-layer pipeline", e)
                withContext(Dispatchers.Main) {
                    listener.onError(-1)
                }
            }
        }
    }

    private suspend fun transcribeAndDeliver(samples: FloatArray) {
        val currentListener = activeListener ?: return
        val mode = settingsRepository.getSttMode()
        var text = ""

        try {
            if (mode == SttMode.PARAKEET || mode == SttMode.HYBRID || mode == SttMode.NATIVE) {
                val recognizer = offlineRecognizer
                if (recognizer != null) {
                    val stream = recognizer.createStream()
                    stream.acceptWaveform(samples, 16000)
                    recognizer.decode(stream)
                    text = recognizer.getResult(stream).text.trim()
                    stream.release()
                } else {
                    Log.e("SpeechRecognizerManager", "Offline recognizer not initialized")
                }
            } else if (mode == SttMode.API) {
                // Convert float samples back to short samples for WAV encoding
                val shortSamples = ShortArray(samples.size) {
                    (samples[it] * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                }
                val wavBytes = getWavBytes(shortSamples, 16000)
                text = transcribeWithGroq(wavBytes)
            }

            withContext(Dispatchers.Main) {
                if (text.isNotEmpty()) {
                    currentListener.onResults(text)
                } else {
                    currentListener.onError(SpeechRecognizer.ERROR_NO_MATCH)
                }
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Transcription failed", e)
            withContext(Dispatchers.Main) {
                currentListener.onError(-1)
            }
        } finally {
            if (!isHandsFreeMode) {
                stop()
            }
        }
    }

    private fun startHybridTransitionListening(listener: SpeechListener) {
        isInHybridTransition = false
        hybridPrefixText = ""
        startNativeListening(listener)

        scope.launch(Dispatchers.IO) {
            initVad()
            initOfflineRecognizer()
        }
    }

    private fun startNativeListening(listener: SpeechListener) {
        try {
            cleanupSpeechRecognizer()
            if (isMicMuted) {
                Log.i("SpeechRecognizerManager", "startNativeListening: mic is muted, not starting SpeechRecognizer")
                return
            }

            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isBluetoothScoOn = true
            audioManager.startBluetoothSco()

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(bundle: Bundle?) {
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
                }

                override fun onError(errorCode: Int) {
                    isListening = false
                    listener.onError(errorCode)
                    stopBluetoothSco()
                }

                override fun onResults(bundle: Bundle?) {
                    isListening = false
                    val recognizedText = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                    listener.onResults(recognizedText)
                    stopBluetoothSco()
                }

                override fun onPartialResults(bundle: Bundle) {
                    val recognizedText = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                    listener.onPartialResults(recognizedText)
                }

                override fun onEvent(i: Int, bundle: Bundle?) {}
            })

            recognizer.startListening(speechRecognizerIntent)
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error starting native speech recognition", e)
            stopBluetoothSco()
            listener.onError(-1)
        }
    }

    private fun stopBluetoothSco() {
        try {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = false
            if (audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                audioManager.mode = AudioManager.MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error stopping Bluetooth SCO", e)
        }
    }

    private fun stopPipeline() {
        audioHygieneProcessor?.stop()
        audioHygieneProcessor = null
        
        vadIntelligenceProcessor?.clear()
        vadIntelligenceProcessor = null
        
        voiceStateMachine = null
    }

    @Synchronized
    fun setMicMuted(muted: Boolean) {
        if (isMicMuted == muted) return
        isMicMuted = muted
        Log.i("SpeechRecognizerManager", "setMicMuted changed to $muted")
        
        if (muted) {
            stopMicAccess()
        } else {
            resumeMicAccess()
        }
    }

    private fun stopMicAccess() {
        Log.i("SpeechRecognizerManager", "stopMicAccess: making microphone unavailable")
        audioHygieneProcessor?.stop()
        
        speechRecognizer?.let {
            it.stopListening()
            cleanupSpeechRecognizer()
            stopBluetoothSco()
        }
    }

    private fun resumeMicAccess() {
        Log.i("SpeechRecognizerManager", "resumeMicAccess: resuming microphone access")
        val listener = activeListener ?: return
        
        if (isHandsFreeMode) {
            val audioProcessor = audioHygieneProcessor
            val stateMachine = voiceStateMachine
            if (audioProcessor != null && stateMachine != null) {
                scope.launch(Dispatchers.IO) {
                    if (audioProcessor.start()) {
                        stateMachine.onUserStartedListening()
                    } else {
                        scope.launch(Dispatchers.Main) {
                            listener.onError(-1)
                        }
                    }
                }
            } else {
                if (modelManager.isModelDownloaded()) {
                    startThreeLayerPipeline(isHandsFree = true, listener)
                } else {
                    startNativeListening(listener)
                }
            }
        }
    }

    fun stop() {
        Log.i("SpeechRecognizerManager", "[Listening] Stop Listening requested manually")
        voiceStateMachine?.onUserStoppedListening()
        stopPipeline()
        cleanupSpeechRecognizer()
        stopBluetoothSco()
    }

    private fun cleanupSpeechRecognizer() {
        speechRecognizer?.let {
            it.destroy()
            speechRecognizer = null
        }
    }

    fun destroy() {
        Log.i("SpeechRecognizerManager", "[Listening] destroy() requested")
        context.unregisterComponentCallbacks(componentCallbacks)
        stop()
        
        vad?.let {
            it.release()
            vad = null
            Log.i("SpeechRecognizerManager", "[Silero VAD] Released/Stopped")
        }
        
        offlineRecognizer?.let {
            it.release()
            offlineRecognizer = null
            Log.i("SpeechRecognizerManager", "[Parakeet Recognizer] Released/Stopped")
        }
    }

    private fun getWavBytes(pcmData: ShortArray, sampleRate: Int): ByteArray {
        val totalAudioLen = pcmData.size * 2
        val totalDataLen = totalAudioLen + 36
        val longSampleRate = sampleRate.toLong()
        val channels = 1
        val byteRate = sampleRate * 2

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val wavBytes = ByteArray(44 + totalAudioLen)
        System.arraycopy(header, 0, wavBytes, 0, 44)

        var offset = 44
        for (sample in pcmData) {
            wavBytes[offset++] = (sample.toInt() and 0xff).toByte()
            wavBytes[offset++] = ((sample.toInt() shr 8) and 0xff).toByte()
        }
        return wavBytes
    }

    private suspend fun transcribeWithGroq(wavBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val client = okhttp3.OkHttpClient()
        val requestBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("model", com.app.assistant.config.SpeechConfig.GroqWhisper.MODEL)
            .addFormDataPart(
                "file", 
                "speech.wav", 
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        val request = okhttp3.Request.Builder()
            .url(com.app.assistant.config.SpeechConfig.GroqWhisper.BASE_URL)
            .addHeader("Authorization", "Bearer ${com.app.assistant.config.SpeechConfig.GroqWhisper.API_KEY}")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Unsuccessful response from Groq: ${response.code} - ${response.message}")
            }
            val bodyStr = response.body?.string() ?: ""
            val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(bodyStr)
            jsonElement.jsonObject["text"]?.jsonPrimitive?.content ?: ""
        }
    }
}
