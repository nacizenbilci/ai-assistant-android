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
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class, DelicateCoroutinesApi::class)
class EdgeTtsApiManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val onSpeakingStateChanged: (isSpeaking: Boolean) -> Unit
) : TtsManager {

    private val ttsDispatcher = newSingleThreadContext("EdgeTtsApiThread")
    private val job = SupervisorJob()
    private val scope = CoroutineScope(ttsDispatcher + job)

    private val speechQueue = Channel<String>(Channel.UNLIMITED)
    private val streamChannel = Channel<SentenceStream>(Channel.UNLIMITED)
    
    private val activeStreams = ConcurrentHashMap<String, SentenceStream>()
    
    private var webSocket: WebSocket? = null
    private var activeWebSocketCall: Call? = null
    
    private var generatorJob: Job? = null
    private var playbackJob: Job? = null
    private var idleTimeoutJob: Job? = null
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
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
        val frameChannel: Channel<ByteArray> = Channel(Channel.UNLIMITED),
        val completionDeferred: CompletableDeferred<Unit> = CompletableDeferred()
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
                
                try {
                    ensureWebSocketConnected()
                } catch (e: Exception) {
                    Log.e("EdgeTtsApiManager", "Could not connect to WebSocket, skipping text: $text", e)
                    isGenerating = false
                    updateSpeakingState()
                    continue
                }
                
                idleTimeoutJob?.cancel()

                val sentences = splitIntoSentences(text)
                for (sentence in sentences) {
                    val cleaned = cleanTextForTts(sentence)
                    if (cleaned.isEmpty()) continue
                    
                    val requestId = UUID.randomUUID().toString().replace("-", "").lowercase()
                    val stream = SentenceStream(requestId)
                    activeStreams[requestId] = stream
                    
                    launchParser(stream)
                    
                    try {
                        sendSsmlRequest(requestId, cleaned)
                        streamChannel.send(stream)
                        stream.completionDeferred.await()
                    } catch (e: Exception) {
                        Log.e("EdgeTtsApiManager", "Error in sequential streaming for requestId: $requestId", e)
                        stream.rawDataChannel.close()
                        stream.completionDeferred.complete(Unit)
                    }
                    
                    activeStreams.remove(requestId)
                }
                
                if (speechQueue.isEmpty) {
                    isGenerating = false
                    updateSpeakingState()
                    resetIdleTimeout()
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
                Log.e("EdgeTtsApiManager", "Error decoding/playing stream", e)
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
                Log.e("EdgeTtsApiManager", "Error parsing MP3 frames", e)
            } finally {
                stream.frameChannel.close()
            }
        }
    }

    private suspend fun ensureWebSocketConnected() {
        if (webSocket != null) return

        val connectionId = UUID.randomUUID().toString().replace("-", "").uppercase()
        val secMsGec = generateSecMsGec()
        val url = "${SpeechConfig.EdgeTts.WSS_URL}" +
                "?Ocp-Apim-Subscription-Key=${SpeechConfig.EdgeTts.SUBSCRIPTION_KEY}" +
                "&ConnectionId=$connectionId" +
                "&Sec-MS-GEC=$secMsGec" +
                "&Sec-MS-GEC-Version=1-130.0.2849.68"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
            .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .addHeader("Pragma", "no-cache")
            .addHeader("Cache-Control", "no-cache")
            .build()

        val connectDeferred = CompletableDeferred<WebSocket>()
        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("EdgeTtsApiManager", "WebSocket connected successfully")
                connectDeferred.complete(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleTextMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                handleBinaryMessage(bytes.toByteArray())
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d("EdgeTtsApiManager", "WebSocket closed")
                if (webSocket == ws) {
                    webSocket = null
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("EdgeTtsApiManager", "WebSocket failure: ${t.message}", t)
                if (connectDeferred.isActive) {
                    connectDeferred.completeExceptionally(t)
                }
                if (webSocket == ws) {
                    webSocket = null
                }
                for (stream in activeStreams.values) {
                    stream.rawDataChannel.close()
                    stream.completionDeferred.complete(Unit)
                }
                activeStreams.clear()
            }
        }

        activeWebSocketCall?.cancel()
        
        try {
            webSocket = client.newWebSocket(request, listener)
            withTimeout(5000L) {
                connectDeferred.await()
            }
        } catch (e: Exception) {
            Log.e("EdgeTtsApiManager", "Failed to connect WebSocket", e)
            webSocket = null
            throw e
        }
    }

    private fun handleTextMessage(text: String) {
        val lines = text.lines()
        var reqId: String? = null
        var isTurnEnd = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("X-RequestId:", ignoreCase = true)) {
                reqId = trimmed.substringAfter(":").trim()
            }
            if (trimmed.startsWith("Path:", ignoreCase = true)) {
                val path = trimmed.substringAfter(":").trim()
                if (path.equals("turn.end", ignoreCase = true)) {
                    isTurnEnd = true
                }
            }
        }

        if (reqId != null && isTurnEnd) {
            val stream = activeStreams[reqId]
            if (stream != null) {
                stream.rawDataChannel.close()
                stream.completionDeferred.complete(Unit)
            }
        }
    }

    private fun handleBinaryMessage(bytesArray: ByteArray) {
        if (bytesArray.size < 2) return
        val headerLength = ((bytesArray[0].toInt() and 0xFF) shl 8) or (bytesArray[1].toInt() and 0xFF)
        if (headerLength + 2 <= bytesArray.size) {
            val headerStr = String(bytesArray, 2, headerLength, Charsets.UTF_8)
            val lines = headerStr.lines()
            var reqId: String? = null
            var isAudio = false
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("X-RequestId:", ignoreCase = true)) {
                    reqId = trimmed.substringAfter(":").trim()
                }
                if (trimmed.startsWith("Path:", ignoreCase = true)) {
                    val path = trimmed.substringAfter(":").trim()
                    if (path.equals("audio", ignoreCase = true)) {
                        isAudio = true
                    }
                }
            }

            if (reqId != null && isAudio) {
                val stream = activeStreams[reqId]
                if (stream != null) {
                    val audioOffset = 2 + headerLength
                    val audioData = bytesArray.copyOfRange(audioOffset, bytesArray.size)
                    if (audioData.isNotEmpty()) {
                        stream.rawDataChannel.trySend(audioData)
                    }
                }
            }
        }
    }

    private fun sendSsmlRequest(requestId: String, text: String) {
        val ws = webSocket ?: return
        val dateStr = getFormattedDate()
        
        val configMsg = "X-Timestamp:$dateStr\r\n" +
                "Content-Type:application/json; charset=utf-8\r\n" +
                "Path:speech.config\r\n\r\n" +
                "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"true\",\"wordBoundaryEnabled\":\"true\"},\"outputFormat\":\"${SpeechConfig.EdgeTts.OUTPUT_FORMAT}\"}}}}"
        ws.send(configMsg)

        val escapedText = escapeXml(text)
        val ssmlMsg = "X-RequestId:$requestId\r\n" +
                "Content-Type:application/ssml+xml\r\n" +
                "X-Timestamp:$dateStr\r\n" +
                "Path:ssml\r\n\r\n" +
                "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                "<voice name='${SpeechConfig.EdgeTts.VOICE}'>" +
                "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>" +
                escapedText +
                "</prosody></voice></speak>"
        
        ws.send(ssmlMsg)
    }

    private fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun generateSecMsGec(): String {
        val unixTime = System.currentTimeMillis() / 1000
        var ticks = unixTime
        ticks -= ticks % 300
        val winTicks = (ticks + 11644473600L) * 10000000L
        val strToHash = "${winTicks}${SpeechConfig.EdgeTts.SUBSCRIPTION_KEY}"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(strToHash.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
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

    private fun resetIdleTimeout() {
        idleTimeoutJob?.cancel()
        idleTimeoutJob = scope.launch {
            delay(10000L)
            closeWebSocketConnection()
        }
    }

    private fun closeWebSocketConnection() {
        webSocket?.close(1000, "Idle timeout")
        webSocket = null
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
            stream?.completionDeferred?.complete(Unit)
        }
        for (stream in activeStreams.values) {
            stream.rawDataChannel.close()
            stream.completionDeferred.complete(Unit)
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
        closeWebSocketConnection()
        ttsDispatcher.close()
    }
}
