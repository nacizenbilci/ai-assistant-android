package com.app.assistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.app.assistant.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import com.k2fsa.sherpa.onnx.*
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class, DelicateCoroutinesApi::class)
class OfflineTtsManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val onSpeakingStateChanged: (isSpeaking: Boolean) -> Unit
) : TtsManager {

    private val ttsDispatcher = newSingleThreadContext("OfflineTtsThread")
    private val job = SupervisorJob()
    private val scope = CoroutineScope(ttsDispatcher + job)
    
    private val speechQueue = Channel<String>(Channel.UNLIMITED)
    private val audioQueue = Channel<GeneratedAudio>(Channel.UNLIMITED)
    
    private var offlineTts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    
    private var generatorJob: Job? = null
    private var playbackJob: Job? = null
    
    @Volatile
    private var isSpeaking = false
    @Volatile
    private var isGenerating = false
    @Volatile
    private var isPlaying = false
    private var isInitialized = false

    private class GeneratedAudio(val samples: ShortArray, val sampleRate: Int)

    init {
        startQueueProcessing()
    }

    @Synchronized
    private fun initTtsEngine(): Boolean {
        if (isInitialized && offlineTts != null) return true
        
        try {
            val modelConfig: OfflineTtsModelConfig
            
            val supertonicDir = File(context.filesDir, "sherpa-onnx-tts/sherpa-onnx-supertonic-3-tts-int8-2026-05-11")
            val durationPredictor = File(supertonicDir, "duration_predictor.int8.onnx")
            val textEncoder = File(supertonicDir, "text_encoder.int8.onnx")
            val vectorEstimator = File(supertonicDir, "vector_estimator.int8.onnx")
            val vocoder = File(supertonicDir, "vocoder.int8.onnx")
            val ttsJson = File(supertonicDir, "tts.json")
            val unicodeIndexer = File(supertonicDir, "unicode_indexer.bin")
            val voiceStyle = File(supertonicDir, "voice.bin")
            
            if (!durationPredictor.exists() || !textEncoder.exists() || !vectorEstimator.exists() || 
                !vocoder.exists() || !ttsJson.exists() || !unicodeIndexer.exists() || !voiceStyle.exists()) {
                Log.w("OfflineTtsManager", "Cannot init OfflineTts (SUPERTONIC): Missing files. durationPredictor: ${durationPredictor.exists()}, textEncoder: ${textEncoder.exists()}, vectorEstimator: ${vectorEstimator.exists()}, vocoder: ${vocoder.exists()}, ttsJson: ${ttsJson.exists()}, unicodeIndexer: ${unicodeIndexer.exists()}, voiceStyle: ${voiceStyle.exists()}")
                return false
            }
            
            val supertonicConfig = OfflineTtsSupertonicModelConfig(
                durationPredictor = durationPredictor.absolutePath,
                textEncoder = textEncoder.absolutePath,
                vectorEstimator = vectorEstimator.absolutePath,
                vocoder = vocoder.absolutePath,
                ttsJson = ttsJson.absolutePath,
                unicodeIndexer = unicodeIndexer.absolutePath,
                voiceStyle = voiceStyle.absolutePath
            )
            modelConfig = OfflineTtsModelConfig(
                supertonic = supertonicConfig,
                numThreads = 2,
                debug = true
            )
            
            val config = OfflineTtsConfig(
                model = modelConfig,
                maxNumSentences = 1
            )
            
            // JNI initialization requires AssetManager to be null when loading absolute paths
            offlineTts = OfflineTts(null, config)
            isInitialized = true
            Log.i("OfflineTtsManager", "Engine initialized successfully (type: SUPERTONIC)")
            return true
        } catch (e: Exception) {
            Log.e("OfflineTtsManager", "Failed to initialize OfflineTts engine", e)
            return false
        }
    }

    private fun cleanTextForTts(text: String): String {
        return text
            .replace("—", " - ")
            .replace("–", " - ")
            .replace("“", "")
            .replace("”", "")
            .replace("\"", "")
            .replace("‘", "'")
            .replace("’", "'")
            .trim()
    }

    private fun startQueueProcessing() {
        generatorJob?.cancel()
        playbackJob?.cancel()
        
        while (!audioQueue.isEmpty) {
            audioQueue.tryReceive()
        }

        generatorJob = scope.launch {
            for (text in speechQueue) {
                if (!initTtsEngine()) {
                    Log.w("OfflineTtsManager", "Engine not initialized. Skipping text: $text")
                    continue
                }

                val tts = offlineTts ?: continue
                val cleanedText = cleanTextForTts(text)
                if (cleanedText.isEmpty()) continue

                isGenerating = true
                updateSpeakingState()

                try {
                    val audio = withContext(Dispatchers.Default) {
                        tts.generate(cleanedText, 0, 1.0f)
                    }
                    val floatSamples = audio.samples
                    val sampleRate = audio.sampleRate

                    if (floatSamples.isNotEmpty()) {
                        val shortSamples = ShortArray(floatSamples.size)
                        for (i in floatSamples.indices) {
                            val sample = floatSamples[i]
                            val clamped = Math.max(-1.0f, Math.min(1.0f, sample))
                            shortSamples[i] = (clamped * 32767.0f).toInt().toShort()
                        }
                        audioQueue.send(GeneratedAudio(shortSamples, sampleRate))
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e("OfflineTtsManager", "Error generating speech", e)
                }

                if (speechQueue.isEmpty) {
                    isGenerating = false
                    updateSpeakingState()
                }
            }
        }

        playbackJob = scope.launch {
            var activeTrack: AudioTrack? = null
            var currentSampleRate = 0

            try {
                while (coroutineContext.isActive) {
                    val audio = audioQueue.receive()
                    isPlaying = true
                    updateSpeakingState()

                    if (activeTrack == null || currentSampleRate != audio.sampleRate) {
                        activeTrack?.let {
                            try { it.stop(); it.release() } catch (e: Exception) {}
                        }
                        currentSampleRate = audio.sampleRate
                        val minBufferSize = AudioTrack.getMinBufferSize(
                            currentSampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val useVoiceCall = audioManager.isBluetoothScoOn || 
                                           audioManager.mode == AudioManager.MODE_IN_CALL || 
                                           audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
                        
                        activeTrack = AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(if (useVoiceCall) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(currentSampleRate)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build()
                            )
                            .setBufferSizeInBytes(Math.max(minBufferSize, audio.samples.size * 2))
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()
                        activeTrack.play()
                        audioTrack = activeTrack
                    }

                    // Write short samples to stream track
                    activeTrack.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)

                    // If there is no more audio in the queue, and we are not generating more,
                    // we wait for the current audio block to finish playing before releasing the track.
                    if (audioQueue.isEmpty && !isGenerating && speechQueue.isEmpty) {
                        val durationMs = (audio.samples.size.toDouble() / currentSampleRate * 1000).toLong()
                        delay(durationMs)

                        // Check again to ensure no new audio arrived during the delay
                        if (audioQueue.isEmpty && !isGenerating && speechQueue.isEmpty) {
                            try {
                                activeTrack.stop()
                                activeTrack.release()
                            } catch (e: Exception) {}
                            activeTrack = null
                            audioTrack = null
                            isPlaying = false
                            updateSpeakingState()
                        }
                    }
                }
            } catch (e: CancellationException) {
                // normal cancellation
            } catch (e: Exception) {
                Log.e("OfflineTtsManager", "Error playing audio stream", e)
            } finally {
                activeTrack?.let {
                    try { it.stop(); it.release() } catch (e: Exception) {}
                }
                audioTrack = null
                isPlaying = false
                updateSpeakingState()
            }
        }
    }

    private fun updateSpeakingState() {
        val shouldBeSpeaking = isGenerating || isPlaying || !speechQueue.isEmpty || !audioQueue.isEmpty
        if (shouldBeSpeaking != isSpeaking) {
            isSpeaking = shouldBeSpeaking
            scope.launch(Dispatchers.Main) {
                onSpeakingStateChanged(shouldBeSpeaking)
            }
        }
    }

    override fun speak(text: String, queueMode: Int) {
        scope.launch {
            if (queueMode == TtsManager.QUEUE_FLUSH) {
                stopPlaybackOnly()
            }
            val sentences = splitIntoSentences(text)
            for (sentence in sentences) {
                val cleanedText = cleanTextForTts(sentence)
                if (cleanedText.isNotEmpty()) {
                    speechQueue.send(cleanedText)
                }
            }
            updateSpeakingState()
        }
    }

    private fun splitIntoSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val currentSentence = StringBuilder()
        val len = text.length
        var i = 0
        while (i < len) {
            val c = text[i]
            currentSentence.append(c)

            // Split on newline or carriage return
            if (c == '\n' || c == '\r') {
                val sentenceStr = currentSentence.toString().trim()
                if (sentenceStr.isNotEmpty()) {
                    sentences.add(sentenceStr)
                }
                currentSentence.clear()
                i++
                continue
            }

            // Split on sentence-ending punctuation followed by whitespace or end of text
            if (c == '.' || c == '?' || c == '!' || c == ':' || c == ';') {
                var isBoundary = false
                if (i + 1 == len) {
                    isBoundary = true
                } else {
                    val nextChar = text[i + 1]
                    if (nextChar.isWhitespace()) {
                        isBoundary = true
                    }
                }

                if (isBoundary) {
                    // Check if it's an abbreviation or decimal number (only for period)
                    if (c == '.' && isAbbreviationOrDecimal(text, i)) {
                        // Not a boundary
                    } else {
                        val sentenceStr = currentSentence.toString().trim()
                        if (sentenceStr.isNotEmpty()) {
                            sentences.add(sentenceStr)
                        }
                        currentSentence.clear()
                    }
                }
            }
            i++
        }

        // Add any remaining text
        val remaining = currentSentence.toString().trim()
        if (remaining.isNotEmpty()) {
            sentences.add(remaining)
        }

        return sentences
    }

    private fun isAbbreviationOrDecimal(text: String, dotIndex: Int): Boolean {
        if (dotIndex > 0 && dotIndex + 1 < text.length) {
            if (text[dotIndex - 1].isDigit() && text[dotIndex + 1].isDigit()) {
                return true
            }
        }
        var start = dotIndex - 1
        while (start >= 0 && text[start].isLetter()) {
            start--
        }
        val word = text.substring(start + 1, dotIndex).lowercase()
        val abbreviations = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "eg", "ie", "vs", "etc", "st", "co",
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
            "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
        )
        return word in abbreviations
    }

    private fun stopPlaybackOnly() {
        while (!speechQueue.isEmpty) {
            speechQueue.tryReceive()
        }
        
        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("OfflineTtsManager", "Error stopping AudioTrack", e)
        }
        audioTrack = null
        
        startQueueProcessing()

        isGenerating = false
        isPlaying = false
        updateSpeakingState()
    }

    override fun stop() {
        stopPlaybackOnly()
    }

    override fun isSpeaking(): Boolean {
        return isSpeaking
    }

    override fun shutdown() {
        job.cancel()
        stopPlaybackOnly()
        try {
            runBlocking(ttsDispatcher) {
                offlineTts?.release()
                offlineTts = null
            }
        } catch (e: Exception) {
            Log.e("OfflineTtsManager", "Error releasing offline TTS", e)
        }
        isInitialized = false
        ttsDispatcher.close()
    }
}
