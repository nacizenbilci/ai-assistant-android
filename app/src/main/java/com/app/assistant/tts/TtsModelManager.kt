package com.app.assistant.tts

import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TtsModelManager(private val context: Context) {

    private val baseDir = File(context.filesDir, "sherpa-onnx-tts")
    private val modelDir = File(baseDir, "sherpa-onnx-supertonic-3-tts-int8-2026-05-11")
    
    private val downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"
    
    private var currentCall: okhttp3.Call? = null
    private val isCancelled = AtomicBoolean(false)

    fun isModelDownloaded(): Boolean {
        val durationPredictor = File(modelDir, "duration_predictor.int8.onnx")
        val textEncoder = File(modelDir, "text_encoder.int8.onnx")
        val vectorEstimator = File(modelDir, "vector_estimator.int8.onnx")
        val vocoder = File(modelDir, "vocoder.int8.onnx")
        val ttsJson = File(modelDir, "tts.json")
        val unicodeIndexer = File(modelDir, "unicode_indexer.bin")
        val voiceStyle = File(modelDir, "voice.bin")
        return durationPredictor.exists() && durationPredictor.length() > 0 &&
               textEncoder.exists() && textEncoder.length() > 0 &&
               vectorEstimator.exists() && vectorEstimator.length() > 0 &&
               vocoder.exists() && vocoder.length() > 0 &&
               ttsJson.exists() && ttsJson.length() > 0 &&
               unicodeIndexer.exists() && unicodeIndexer.length() > 0 &&
               voiceStyle.exists() && voiceStyle.length() > 0
    }

    fun deleteLocalModel(): Boolean {
        return deleteDirectory(modelDir)
    }

    private fun deleteDirectory(dir: File): Boolean {
        if (dir.isDirectory) {
            val children = dir.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteDirectory(child)
                }
            }
        }
        return dir.delete()
    }

    suspend fun downloadModel(
        client: OkHttpClient,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        isCancelled.set(false)
        withContext(Dispatchers.IO) {
            val tempArchive = File(context.cacheDir, "sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2")
            try {
                if (tempArchive.exists()) {
                    tempArchive.delete()
                }
                
                if (!baseDir.exists()) {
                    baseDir.mkdirs()
                }

                val request = Request.Builder()
                    .url(downloadUrl)
                    .build()

                val call = client.newCall(request)
                currentCall = call

                if (isCancelled.get()) {
                    throw IOException("Download cancelled by user")
                }

                val response = call.execute()
                if (!response.isSuccessful) {
                    throw IOException("HTTP error code: ${response.code}")
                }

                val responseBody = response.body ?: throw IOException("Empty response body")
                val totalBytes = responseBody.contentLength()
                
                responseBody.byteStream().use { inputStream ->
                    FileOutputStream(tempArchive).use { outputStream ->
                        val buffer = ByteArray(1024 * 64)
                        var bytesRead: Int
                        var downloadedBytes = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (isCancelled.get()) {
                                throw IOException("Download cancelled by user")
                            }
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            onProgress(downloadedBytes, totalBytes)
                        }
                        outputStream.flush()
                    }
                }
                response.close()

                if (isCancelled.get()) {
                    throw IOException("Download cancelled by user")
                }

                // Extract archive
                onProgress(totalBytes, totalBytes) // Mark download as 100% before extraction
                val extractionSuccess = extractTarBz2(tempArchive, baseDir)
                
                if (extractionSuccess && isModelDownloaded()) {
                    onComplete(true, null)
                } else {
                    onComplete(false, "Extraction failed. Ensure your device has enough free storage.")
                }
            } catch (e: Exception) {
                Log.e("TtsModelManager", "Error downloading/extracting TTS model", e)
                val msg = if (isCancelled.get()) "Download cancelled." else e.message ?: "Unknown error"
                onComplete(false, msg)
            } finally {
                currentCall = null
                if (tempArchive.exists()) {
                    tempArchive.delete()
                }
            }
        }
    }

    fun cancelDownload() {
        isCancelled.set(true)
        currentCall?.cancel()
        currentCall = null
    }

    private fun extractTarBz2(archiveFile: File, targetDir: File): Boolean {
        targetDir.mkdirs()
        try {
            Log.i("TtsModelManager", "Starting native tar extraction of ${archiveFile.name} to ${targetDir.name}...")
            val process = ProcessBuilder()
                .command("tar", "-xjf", archiveFile.absolutePath, "-C", targetDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            
            val exitCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) { // 90 secs timeout for slower devices
                    process.exitValue()
                } else {
                    process.destroyForcibly()
                    -1
                }
            } else {
                process.waitFor()
            }
            
            if (exitCode == 0) {
                Log.i("TtsModelManager", "Native tar extraction completed successfully.")
                return true
            }
            
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            Log.e("TtsModelManager", "Native tar failed with exit code $exitCode. Output: $output")
        } catch (e: Exception) {
            Log.e("TtsModelManager", "Failed to run native tar command", e)
        }
        return false
    }
}
