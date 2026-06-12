package com.app.assistant.speech

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
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
import java.util.Locale
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import com.app.assistant.config.SpeechConfig
import kotlinx.coroutines.withContext

class SpeechRecognizerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private var vad: com.k2fsa.sherpa.onnx.Vad? = null
    private var offlineRecognizer: com.k2fsa.sherpa.onnx.OfflineRecognizer? = null

    private var isRecording = false
    private var recordingJob: Job? = null

    private val modelManager = SpeechModelManager(context)
    private var isInHybridTransition = false
    private var hybridPrefixText = ""

    @Volatile
    var isTtsSpeaking = false

    private val doubleTalkMinRmsFloor = 500.0
    private val doubleTalkMultiplier = 1.6
    private val doubleTalkStartDelayFrames = 8
    private val doubleTalkCalibrationFrames = 12
    private val doubleTalkConsecutiveFramesRequired = 3

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

    @Synchronized
    private fun initParakeetAndVad() {
        if (vad != null && offlineRecognizer != null) return
        try {
            if (!modelManager.isModelDownloaded()) {
                Log.w("SpeechRecognizerManager", "Cannot init Parakeet: Model files not downloaded.")
                return
            }

            // 1. Initialize Silero VAD using absolute disk paths
            val sileroConfig = com.k2fsa.sherpa.onnx.SileroVadModelConfig(
                model = modelManager.getVadPath(),
                threshold = 0.5f,
                minSilenceDuration = 0.5f,
                minSpeechDuration = 0.25f,
                windowSize = 512,
                maxSpeechDuration = 10.0f
            )
            val vadConfig = com.k2fsa.sherpa.onnx.VadModelConfig(
                sileroVadModelConfig = sileroConfig,
                sampleRate = 16000,
                numThreads = 2,
                provider = "cpu"
            )
            vad = com.k2fsa.sherpa.onnx.Vad(
                assetManager = null, // null means load from absolute path
                config = vadConfig
            )
            Log.d("SpeechRecognizerManager", "Silero VAD initialized successfully from disk")

            // 2. Initialize Offline Parakeet (NeMo Transducer) Recognizer using absolute disk paths
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
                assetManager = null, // null means load from absolute path
                config = recognizerConfig
            )
            Log.d("SpeechRecognizerManager", "Offline Parakeet Recognizer initialized successfully from disk")
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Failed to initialize Parakeet + VAD from disk", e)
        }
    }

    fun preLoadModelAsync() {
        val settingsRepository = com.app.assistant.repository.SettingsRepository(context)
        val mode = settingsRepository.getSttMode()
        if (mode == SttMode.PARAKEET || mode == SttMode.HYBRID) {
            if (modelManager.isModelDownloaded()) {
                scope.launch(Dispatchers.IO) {
                    initParakeetAndVad()
                }
            }
        }
    }

    fun startListening(isHandsFree: Boolean = false, listener: SpeechListener) {
        val settingsRepository = com.app.assistant.repository.SettingsRepository(context)
        val mode = settingsRepository.getSttMode()
        
        isInHybridTransition = false
        hybridPrefixText = ""

        if (isHandsFree) {
            if (modelManager.isModelDownloaded()) {
                startLocalParakeetListening(isHandsFree = true, listener)
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
                    startLocalParakeetListening(isHandsFree = false, listener)
                } else {
                    Log.w("SpeechRecognizerManager", "Parakeet selected but not downloaded. Falling back to Native STT.")
                    startNativeListening(listener)
                }
            }
            SttMode.HYBRID -> {
                if (modelManager.isModelDownloaded()) {
                    if (offlineRecognizer != null && vad != null) {
                        // Already loaded, start Parakeet directly
                        startLocalParakeetListening(isHandsFree = false, listener)
                    } else {
                        // Start native immediately and load Parakeet in background
                        startHybridTransitionListening(listener)
                    }
                } else {
                    Log.w("SpeechRecognizerManager", "Hybrid selected but model not downloaded. Using Native STT.")
                    startNativeListening(listener)
                }
            }
            SttMode.API -> {
                if (modelManager.isModelDownloaded()) {
                    startApiWhisperListening(isHandsFree = isHandsFree, listener)
                } else {
                    Log.w("SpeechRecognizerManager", "STT API selected but VAD model not downloaded. Falling back to Native STT.")
                    startNativeListening(listener)
                }
            }
        }
    }

    private fun startHybridTransitionListening(listener: SpeechListener) {
        isInHybridTransition = false
        hybridPrefixText = ""

        // Start native listening first
        startNativeListening(listener)

        // Load Parakeet in background so it is ready for the next call
        scope.launch(Dispatchers.IO) {
            initParakeetAndVad()
        }
    }

    private fun startLocalParakeetListening(isHandsFree: Boolean = false, listener: SpeechListener) {
        try {
            cleanupSpeechRecognizer()
            initParakeetAndVad()
            startLocalParakeetListeningInternal(isHandsFree, listener)
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error starting Parakeet STT", e)
            listener.onError(-1)
        }
    }

    private fun startLocalParakeetListeningInternal(isHandsFree: Boolean, listener: SpeechListener) {
        val activeVad = vad
        val recognizer = offlineRecognizer
        if (activeVad == null || recognizer == null) {
            Log.e("SpeechRecognizerManager", "Parakeet or VAD is null. Cannot listen.")
            listener.onError(-1)
            return
        }

        var echoCanceler: android.media.audiofx.AcousticEchoCanceler? = null
        try {
            if (isHandsFree) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
            } else {
                audioManager.mode = AudioManager.MODE_IN_CALL
                audioManager.isBluetoothScoOn = true
                audioManager.startBluetoothSco()
            }

            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                listener.onError(-1)
                stopBluetoothSco()
                return
            }
            
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("SpeechRecognizerManager", "AudioRecord could not be initialized")
                listener.onError(-1)
                stopBluetoothSco()
                return
            }

            if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                echoCanceler = android.media.audiofx.AcousticEchoCanceler.create(audioRecord.audioSessionId)
                if (echoCanceler != null) {
                    echoCanceler.enabled = true
                    Log.d("SpeechRecognizerManager", "AcousticEchoCanceler enabled successfully")
                } else {
                    Log.w("SpeechRecognizerManager", "AcousticEchoCanceler creation failed")
                }
            } else {
                Log.d("SpeechRecognizerManager", "AcousticEchoCanceler is not available on this device")
            }

            activeVad.clear()
            isListening = true
            isRecording = true
            listener.onReadyForSpeech()
            if (!isHandsFree) {
                listener.onBeginningOfSpeech()
            }

            audioRecord.startRecording()

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(512)
                var lastSpeechDetectedTime = System.currentTimeMillis()
                val silenceTimeoutMs = if (isHandsFree) 20000L else 5000L
                var isSpeechActive = false

                var wasTtsSpeaking = false
                var calibrationFramesCount = 0
                var calibrationRmsSum = 0.0
                var finalRmsFloor = doubleTalkMinRmsFloor
                var consecutiveInterruptionFrames = 0
                var hasError = false

                try {
                    while (isRecording && coroutineContext[Job]?.isActive == true) {
                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            val currentTtsSpeaking = isTtsSpeaking
                            if (isHandsFree && currentTtsSpeaking) {
                                lastSpeechDetectedTime = System.currentTimeMillis()
                                isSpeechActive = false
                            }

                            var sum = 0.0
                            for (i in 0 until read) {
                                sum += buffer[i] * buffer[i]
                            }
                            val rms = Math.sqrt(sum / read)

                            if (currentTtsSpeaking && !wasTtsSpeaking) {
                                calibrationFramesCount = 0
                                calibrationRmsSum = 0.0
                                consecutiveInterruptionFrames = 0
                                Log.d("SpeechRecognizerManager", "TTS speech started. Initiating double-talk calibration.")
                            }
                            wasTtsSpeaking = currentTtsSpeaking

                            val proceedWithSpeech: Boolean
                            if (isHandsFree && currentTtsSpeaking) {
                                if (calibrationFramesCount < doubleTalkStartDelayFrames + doubleTalkCalibrationFrames) {
                                    if (calibrationFramesCount >= doubleTalkStartDelayFrames) {
                                        calibrationRmsSum += rms
                                    }
                                    calibrationFramesCount++
                                    if (calibrationFramesCount == doubleTalkStartDelayFrames + doubleTalkCalibrationFrames) {
                                        val avgRms = calibrationRmsSum / doubleTalkCalibrationFrames
                                        finalRmsFloor = maxOf(avgRms, doubleTalkMinRmsFloor)
                                        Log.d("SpeechRecognizerManager", "Double-talk calibration complete. Avg RMS: $avgRms, Final RMS Floor: $finalRmsFloor")
                                    }
                                    consecutiveInterruptionFrames = 0
                                    activeVad.clear()
                                    proceedWithSpeech = false
                                } else {
                                    val adaptiveThreshold = finalRmsFloor * doubleTalkMultiplier
                                    if (rms > adaptiveThreshold) {
                                        consecutiveInterruptionFrames++
                                        if (consecutiveInterruptionFrames >= doubleTalkConsecutiveFramesRequired) {
                                            Log.d("SpeechRecognizerManager", "Double-talk detected! RMS: $rms, Adaptive Threshold: $adaptiveThreshold (Floor: $finalRmsFloor), interrupting TTS.")
                                            isTtsSpeaking = false
                                            withContext(Dispatchers.Main) {
                                                listener.onBeginningOfSpeech()
                                            }
                                            proceedWithSpeech = true
                                        } else {
                                            activeVad.clear()
                                            proceedWithSpeech = false
                                        }
                                    } else {
                                        consecutiveInterruptionFrames = 0
                                        activeVad.clear()
                                        proceedWithSpeech = false
                                    }
                                }
                            } else {
                                proceedWithSpeech = true
                            }

                            if (proceedWithSpeech) {
                                val floatSamples = FloatArray(read) { buffer[it] / 32768.0f }
                                activeVad.acceptWaveform(floatSamples)

                                if (activeVad.isSpeechDetected()) {
                                    lastSpeechDetectedTime = System.currentTimeMillis()
                                    if (!isSpeechActive) {
                                        isSpeechActive = true
                                        withContext(Dispatchers.Main) {
                                            listener.onBeginningOfSpeech()
                                        }
                                    }
                                } else {
                                    if (isSpeechActive) {
                                        isSpeechActive = false
                                        withContext(Dispatchers.Main) {
                                            listener.onEndOfSpeech()
                                        }
                                    }
                                }

                                var gotResult = false
                                while (!activeVad.empty()) {
                                    val segment = activeVad.front()
                                    
                                    val stream = recognizer.createStream()
                                    stream.acceptWaveform(segment.samples, sampleRate)
                                    recognizer.decode(stream)
                                    
                                    val result = recognizer.getResult(stream)
                                    val text = result.text.trim()
                                    stream.release()
                                    
                                    activeVad.pop()

                                    if (text.isNotEmpty()) {
                                        withContext(Dispatchers.Main) {
                                            listener.onResults(text)
                                        }
                                        gotResult = true
                                        break
                                    }
                                }

                                if (gotResult) {
                                    isSpeechActive = false
                                    if (!isHandsFree) {
                                        isRecording = false
                                        break
                                    }
                                }
                            }

                            if (System.currentTimeMillis() - lastSpeechDetectedTime > silenceTimeoutMs) {
                                Log.d("SpeechRecognizerManager", "Silence timeout reached, stopping Parakeet STT")
                                hasError = true
                                withContext(Dispatchers.Main) {
                                    listener.onError(6)
                                }
                                isRecording = false
                                break
                            }
                        }
                        delay(10)
                    }
                } catch (e: Exception) {
                    Log.e("SpeechRecognizerManager", "Error in VAD + Parakeet loop", e)
                    hasError = true
                    withContext(Dispatchers.Main) {
                        listener.onError(-1)
                    }
                } finally {
                    try {
                        audioRecord.stop()
                        echoCanceler?.let {
                            it.enabled = false
                            it.release()
                        }
                        audioRecord.release()
                    } catch (e: Exception) {
                        Log.e("SpeechRecognizerManager", "Error releasing AudioRecord", e)
                    }
                    isListening = false
                    withContext(Dispatchers.Main) {
                        if (!hasError) {
                            listener.onEndOfSpeech()
                        }
                        stopBluetoothSco()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error starting Parakeet STT", e)
            stopBluetoothSco()
            listener.onError(-1)
        }
    }

    private fun startNativeListening(listener: SpeechListener) {
        try {
            cleanupSpeechRecognizer()

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

    fun stop() {
        cleanupSpeechRecognizer()
        stopBluetoothSco()
    }

    private fun cleanupSpeechRecognizer() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        speechRecognizer?.let {
            it.destroy()
            speechRecognizer = null
        }
    }

    fun destroy() {
        context.unregisterComponentCallbacks(componentCallbacks)
        stopBluetoothSco()
        cleanupSpeechRecognizer()
        
        vad?.release()
        vad = null
        
        offlineRecognizer?.release()
        offlineRecognizer = null
    }

    private fun startApiWhisperListening(isHandsFree: Boolean = false, listener: SpeechListener) {
        try {
            cleanupSpeechRecognizer()
            initParakeetAndVad()
            startApiWhisperListeningInternal(isHandsFree, listener)
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error starting API Whisper STT", e)
            listener.onError(-1)
        }
    }

    private fun startApiWhisperListeningInternal(isHandsFree: Boolean, listener: SpeechListener) {
        val activeVad = vad
        if (activeVad == null) {
            Log.e("SpeechRecognizerManager", "VAD is null. Cannot listen.")
            listener.onError(-1)
            return
        }

        var echoCanceler: android.media.audiofx.AcousticEchoCanceler? = null
        try {
            if (isHandsFree) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
            } else {
                audioManager.mode = AudioManager.MODE_IN_CALL
                audioManager.isBluetoothScoOn = true
                audioManager.startBluetoothSco()
            }

            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                listener.onError(-1)
                stopBluetoothSco()
                return
            }
            
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("SpeechRecognizerManager", "AudioRecord could not be initialized")
                listener.onError(-1)
                stopBluetoothSco()
                return
            }

            if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                echoCanceler = android.media.audiofx.AcousticEchoCanceler.create(audioRecord.audioSessionId)
                if (echoCanceler != null) {
                    echoCanceler.enabled = true
                    Log.d("SpeechRecognizerManager", "AcousticEchoCanceler enabled successfully")
                } else {
                    Log.w("SpeechRecognizerManager", "AcousticEchoCanceler creation failed")
                }
            } else {
                Log.d("SpeechRecognizerManager", "AcousticEchoCanceler is not available on this device")
            }

            activeVad.clear()
            isListening = true
            isRecording = true
            listener.onReadyForSpeech()
            if (!isHandsFree) {
                listener.onBeginningOfSpeech()
            }

            audioRecord.startRecording()

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(512)
                var lastSpeechDetectedTime = System.currentTimeMillis()
                val silenceTimeoutMs = if (isHandsFree) 20000L else 5000L
                var isSpeechActive = false

                var wasTtsSpeaking = false
                var calibrationFramesCount = 0
                var calibrationRmsSum = 0.0
                var finalRmsFloor = doubleTalkMinRmsFloor
                var consecutiveInterruptionFrames = 0
                var hasError = false

                try {
                    while (isRecording && coroutineContext[Job]?.isActive == true) {
                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            val currentTtsSpeaking = isTtsSpeaking
                            if (isHandsFree && currentTtsSpeaking) {
                                lastSpeechDetectedTime = System.currentTimeMillis()
                                isSpeechActive = false
                            }

                            var sum = 0.0
                            for (i in 0 until read) {
                                sum += buffer[i] * buffer[i]
                            }
                            val rms = Math.sqrt(sum / read)

                            if (currentTtsSpeaking && !wasTtsSpeaking) {
                                calibrationFramesCount = 0
                                calibrationRmsSum = 0.0
                                consecutiveInterruptionFrames = 0
                                Log.d("SpeechRecognizerManager", "TTS speech started. Initiating double-talk calibration.")
                            }
                            wasTtsSpeaking = currentTtsSpeaking

                            val proceedWithSpeech: Boolean
                            if (isHandsFree && currentTtsSpeaking) {
                                if (calibrationFramesCount < doubleTalkStartDelayFrames + doubleTalkCalibrationFrames) {
                                    if (calibrationFramesCount >= doubleTalkStartDelayFrames) {
                                        calibrationRmsSum += rms
                                    }
                                    calibrationFramesCount++
                                    if (calibrationFramesCount == doubleTalkStartDelayFrames + doubleTalkCalibrationFrames) {
                                        val avgRms = calibrationRmsSum / doubleTalkCalibrationFrames
                                        finalRmsFloor = maxOf(avgRms, doubleTalkMinRmsFloor)
                                        Log.d("SpeechRecognizerManager", "Double-talk calibration complete. Avg RMS: $avgRms, Final RMS Floor: $finalRmsFloor")
                                    }
                                    consecutiveInterruptionFrames = 0
                                    activeVad.clear()
                                    proceedWithSpeech = false
                                } else {
                                    val adaptiveThreshold = finalRmsFloor * doubleTalkMultiplier
                                    if (rms > adaptiveThreshold) {
                                        consecutiveInterruptionFrames++
                                        if (consecutiveInterruptionFrames >= doubleTalkConsecutiveFramesRequired) {
                                            Log.d("SpeechRecognizerManager", "Double-talk detected! RMS: $rms, Adaptive Threshold: $adaptiveThreshold (Floor: $finalRmsFloor), interrupting TTS.")
                                            isTtsSpeaking = false
                                            withContext(Dispatchers.Main) {
                                                listener.onBeginningOfSpeech()
                                            }
                                            proceedWithSpeech = true
                                        } else {
                                            activeVad.clear()
                                            proceedWithSpeech = false
                                        }
                                    } else {
                                        consecutiveInterruptionFrames = 0
                                        activeVad.clear()
                                        proceedWithSpeech = false
                                    }
                                }
                            } else {
                                proceedWithSpeech = true
                            }

                            if (proceedWithSpeech) {
                                val floatSamples = FloatArray(read) { buffer[it] / 32768.0f }
                                activeVad.acceptWaveform(floatSamples)

                                if (activeVad.isSpeechDetected()) {
                                    lastSpeechDetectedTime = System.currentTimeMillis()
                                    if (!isSpeechActive) {
                                        isSpeechActive = true
                                        withContext(Dispatchers.Main) {
                                            listener.onBeginningOfSpeech()
                                        }
                                    }
                                } else {
                                    if (isSpeechActive) {
                                        isSpeechActive = false
                                        withContext(Dispatchers.Main) {
                                            listener.onEndOfSpeech()
                                        }
                                    }
                                }

                                var gotSegment = false
                                while (!activeVad.empty()) {
                                    val segment = activeVad.front()
                                    activeVad.pop()
                                    gotSegment = true

                                    scope.launch(Dispatchers.IO) {
                                        val segFloatSamples = segment.samples
                                        val shortSamples = ShortArray(segFloatSamples.size) {
                                            (segFloatSamples[it] * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                                        }
                                        val wavBytes = getWavBytes(shortSamples, sampleRate)
                                        try {
                                            val transcribedText = transcribeWithGroq(wavBytes)
                                            if (transcribedText.isNotEmpty()) {
                                                withContext(Dispatchers.Main) {
                                                    listener.onResults(transcribedText)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("SpeechRecognizerManager", "Error transcribing with Groq", e)
                                            withContext(Dispatchers.Main) {
                                                listener.onError(-1)
                                            }
                                        }
                                    }
                                }

                                if (gotSegment) {
                                    isSpeechActive = false
                                    if (!isHandsFree) {
                                        isRecording = false
                                        break
                                    }
                                }
                            }

                            if (System.currentTimeMillis() - lastSpeechDetectedTime > silenceTimeoutMs) {
                                Log.d("SpeechRecognizerManager", "Silence timeout reached, stopping API Whisper STT")
                                hasError = true
                                withContext(Dispatchers.Main) {
                                    listener.onError(6)
                                }
                                isRecording = false
                                break
                            }
                        }
                        delay(10)
                    }
                } catch (e: Exception) {
                    Log.e("SpeechRecognizerManager", "Error in VAD + API Whisper loop", e)
                    hasError = true
                    withContext(Dispatchers.Main) {
                        listener.onError(-1)
                    }
                } finally {
                    try {
                        audioRecord.stop()
                        echoCanceler?.let {
                            it.enabled = false
                            it.release()
                        }
                        audioRecord.release()
                    } catch (e: Exception) {
                        Log.e("SpeechRecognizerManager", "Error releasing AudioRecord", e)
                    }
                    isListening = false
                    withContext(Dispatchers.Main) {
                        if (!hasError) {
                            listener.onEndOfSpeech()
                        }
                        stopBluetoothSco()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognizerManager", "Error starting API Whisper STT", e)
            stopBluetoothSco()
            listener.onError(-1)
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
