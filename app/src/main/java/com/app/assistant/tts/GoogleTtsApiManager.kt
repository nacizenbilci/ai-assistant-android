package com.app.assistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.app.assistant.config.SpeechConfig
import com.app.assistant.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class, DelicateCoroutinesApi::class)
class GoogleTtsApiManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val onSpeakingStateChanged: (isSpeaking: Boolean) -> Unit
) : TtsManager {

    private val ttsDispatcher = newSingleThreadContext("GoogleTtsApiThread")
    private val job = SupervisorJob()
    private val scope = CoroutineScope(ttsDispatcher + job)

    private val speechQueue = Channel<String>(Channel.UNLIMITED)
    private val streamChannel = Channel<SentenceStream>(Channel.UNLIMITED)
    
    private val activeStreams = ConcurrentHashMap<String, SentenceStream>()
    
    private var generatorJob: Job? = null
    private var playbackJob: Job? = null
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isSpeaking = false
    @Volatile
    private var isGenerating = false
    @Volatile
    private var isPlaying = false

    private class SentenceStream(
        val requestId: String,
        val rawDataChannel: Channel<ByteArray> = Channel(Channel.UNLIMITED),
        val frameChannel: Channel<ByteArray> = Channel(Channel.UNLIMITED)
    )

    init {
        startProcessing()
    }

    private fun startProcessing() {
        generatorJob?.cancel()
        playbackJob?.cancel()

        generatorJob = scope.launch {
            for (text in speechQueue) {
                isGenerating = true
                updateSpeakingState()
                
                val sentences = splitIntoSentences(text)
                for (sentence in sentences) {
                    val cleaned = cleanTextForTts(sentence)
                    if (cleaned.isEmpty()) continue
                    
                    val requestId = UUID.randomUUID().toString().replace("-", "").lowercase()
                    val stream = SentenceStream(requestId)
                    activeStreams[requestId] = stream
                    
                    launchParser(stream)
                    
                    try {
                        val audioBytes = fetchGoogleTts(cleaned)
                        stream.rawDataChannel.send(audioBytes)
                        stream.rawDataChannel.close()
                        streamChannel.send(stream)
                    } catch (e: Exception) {
                        Log.e("GoogleTtsApiManager", "Error fetching Google TTS for sentence: $cleaned", e)
                        stream.rawDataChannel.close()
                    }
                    
                    activeStreams.remove(requestId)
                }
                
                if (speechQueue.isEmpty) {
                    isGenerating = false
                    updateSpeakingState()
                }
            }
        }

        playbackJob = scope.launch {
            for (stream in streamChannel) {
                isPlaying = true
                updateSpeakingState()
                
                playStream(stream)
                
                if (streamChannel.isEmpty && !isGenerating && speechQueue.isEmpty) {
                    isPlaying = false
                    updateSpeakingState()
                }
            }
        }
    }

    private suspend fun fetchGoogleTts(text: String): ByteArray = withContext(Dispatchers.IO) {
        val apiKey = SpeechConfig.GoogleTts.getApiKey(settingsRepository)
        if (apiKey.isEmpty()) {
            throw Exception("Google Cloud TTS API key is empty.")
        }
        val url = "${SpeechConfig.GoogleTts.BASE_URL}?key=$apiKey"
        
        val voiceName = SpeechConfig.GoogleTts.VOICE
        val parts = voiceName.split("-")
        val languageCode = if (parts.size >= 2) "${parts[0]}-${parts[1]}" else "en-US"

        val jsonBodyElement = buildJsonObject {
            putJsonObject("input") {
                put("text", text)
            }
            putJsonObject("voice") {
                put("languageCode", languageCode)
                put("name", voiceName)
            }
            putJsonObject("audioConfig") {
                put("audioEncoding", SpeechConfig.GoogleTts.AUDIO_ENCODING)
                put("sampleRateHertz", 24000)
            }
        }
        val jsonBody = jsonBodyElement.toString()

        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw Exception("Google Cloud TTS API call failed: ${response.code} - ${response.message}. Body: $errBody")
            }
            val bodyStr = response.body?.string() ?: throw Exception("Empty response body from Google TTS")
            val jsonElement = Json.parseToJsonElement(bodyStr)
            val audioContentBase64 = jsonElement.jsonObject["audioContent"]?.jsonPrimitive?.content
                ?: throw Exception("No audioContent field in response JSON")
            android.util.Base64.decode(audioContentBase64, android.util.Base64.DEFAULT)
        }
    }

    private suspend fun playStream(stream: SentenceStream) {
        withContext(Dispatchers.Default) {
            val sampleRate = 24000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val useVoiceCall = audioManager.isBluetoothScoOn || 
                               audioManager.mode == AudioManager.MODE_IN_CALL || 
                               audioManager.mode == AudioManager.MODE_IN_COMMUNICATION

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (useVoiceCall) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(Math.max(minBufferSize, 1024 * 16))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            var codec: MediaCodec? = null
            try {
                audioTrack.play()
                
                val mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_MPEG, sampleRate, 1)
                codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG).apply {
                    configure(mediaFormat, null, null, 0)
                    start()
                }

                var decodingActive = true
                var hasMoreInput = true
                var presentationTimeUs = 0L
                val frameIterator = stream.frameChannel.iterator()

                while (decodingActive && coroutineContext.isActive) {
                    var didWork = false

                    if (hasMoreInput) {
                        val inputIndex = codec.dequeueInputBuffer(0)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                if (frameIterator.hasNext()) {
                                    val frame = frameIterator.next()
                                    inputBuffer.put(frame)
                                    codec.queueInputBuffer(inputIndex, 0, frame.size, presentationTimeUs, 0)
                                    presentationTimeUs += (frame.size * 8000000L) / 48000L
                                    didWork = true
                                } else {
                                    if (stream.frameChannel.isClosedForReceive) {
                                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                        hasMoreInput = false
                                        didWork = true
                                    }
                                }
                            }
                        }
                    }

                    val bufferInfo = MediaCodec.BufferInfo()
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val pcmData = ByteArray(bufferInfo.size)
                            outputBuffer.get(pcmData)
                            outputBuffer.clear()
                            audioTrack.write(pcmData, 0, pcmData.size)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decodingActive = false
                        }
                        didWork = true
                    }

                    if (!didWork) {
                        delay(5)
                    }
                }

                if (coroutineContext.isActive) {
                    delay(150)
                }

            } catch (e: Exception) {
                Log.e("GoogleTtsApiManager", "Error decoding/playing stream", e)
            } finally {
                try {
                    audioTrack.stop()
                    audioTrack.flush()
                    audioTrack.release()
                } catch (e: Exception) {}
                try {
                    codec?.stop()
                    codec?.release()
                } catch (e: Exception) {}
            }
            Unit
        }
    }

    private fun launchParser(stream: SentenceStream): Job {
        return scope.launch(Dispatchers.Default) {
            val parserBuffer = ByteArrayOutputStream()
            try {
                for (bytes in stream.rawDataChannel) {
                    parserBuffer.write(bytes)
                    var bufferBytes = parserBuffer.toByteArray()
                    var offset = 0
                    while (offset + 4 <= bufferBytes.size) {
                        val b0 = bufferBytes[offset].toInt() and 0xFF
                        val b1 = bufferBytes[offset + 1].toInt() and 0xFF
                        if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                            val version = (b1 shr 3) and 0x03
                            val layer = (b1 shr 1) and 0x03
                            val bitrateIndex = (bufferBytes[offset + 2].toInt() and 0xFF) shr 4
                            val srIndex = (bufferBytes[offset + 2].toInt() and 0xFF shr 2) and 0x03
                            val padding = (bufferBytes[offset + 2].toInt() and 0xFF shr 1) and 0x01

                            val mpeg1Bitrates = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
                            val mpeg2Bitrates = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
                            val sampleRates = arrayOf(
                                intArrayOf(11025, 12000, 8000),
                                intArrayOf(0, 0, 0),
                                intArrayOf(22050, 24000, 16000),
                                intArrayOf(44100, 48000, 32000)
                            )

                            if (version < sampleRates.size && srIndex < sampleRates[version].size) {
                                val bitrate = (if (version == 3) mpeg1Bitrates[bitrateIndex] else mpeg2Bitrates[bitrateIndex]) * 1000
                                val sampleRate = sampleRates[version][srIndex]

                                if (bitrate > 0 && sampleRate > 0 && layer == 1) {
                                    val coeff = if (version == 3) 144 else 72
                                    val frameSize = (coeff * bitrate) / sampleRate + padding

                                    if (offset + frameSize <= bufferBytes.size) {
                                        val frame = bufferBytes.copyOfRange(offset, offset + frameSize)
                                        stream.frameChannel.send(frame)
                                        offset += frameSize
                                        continue
                                    } else {
                                        break
                                    }
                                }
                            }
                        }
                        offset++
                    }

                    val temp = parserBuffer.toByteArray()
                    parserBuffer.reset()
                    if (offset < temp.size) {
                        parserBuffer.write(temp, offset, temp.size - offset)
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleTtsApiManager", "Error parsing MP3 frames", e)
            } finally {
                stream.frameChannel.close()
            }
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

    private fun splitIntoSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val currentSentence = StringBuilder()
        val len = text.length
        var i = 0
        while (i < len) {
            val c = text[i]
            currentSentence.append(c)

            if (c == '\n' || c == '\r') {
                val sentenceStr = currentSentence.toString().trim()
                if (sentenceStr.isNotEmpty()) {
                    sentences.add(sentenceStr)
                }
                currentSentence.clear()
                i++
                continue
            }

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
                    if (c == '.' && isAbbreviationOrDecimal(text, i)) {
                        // abbreviation
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

    private fun updateSpeakingState() {
        val shouldBeSpeaking = isGenerating || isPlaying || !speechQueue.isEmpty || !streamChannel.isEmpty
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
            speechQueue.send(text)
            updateSpeakingState()
        }
    }

    private fun stopPlaybackOnly() {
        while (!speechQueue.isEmpty) {
            speechQueue.tryReceive()
        }
        while (!streamChannel.isEmpty) {
            val stream = streamChannel.tryReceive().getOrNull()
            stream?.rawDataChannel?.close()
        }
        for (stream in activeStreams.values) {
            stream.rawDataChannel.close()
        }
        activeStreams.clear()

        startProcessing()

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
        ttsDispatcher.close()
    }
}
