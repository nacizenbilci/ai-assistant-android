package com.app.assistant.speech

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class SpeechModelManager(private val context: Context) {

    private val modelDir = File(context.filesDir, "sherpa-onnx")

    data class ModelFileDesc(
        val url: String,
        val localName: String,
        val sizeBytes: Long
    )

    private val modelFiles = listOf(
        ModelFileDesc(
            url = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/encoder.int8.onnx",
            localName = "parakeet-encoder.onnx",
            sizeBytes = 652184296L
        ),
        ModelFileDesc(
            url = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/decoder.int8.onnx",
            localName = "parakeet-decoder.onnx",
            sizeBytes = 7257753L
        ),
        ModelFileDesc(
            url = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/joiner.int8.onnx",
            localName = "parakeet-joiner.onnx",
            sizeBytes = 1739080L
        ),
        ModelFileDesc(
            url = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8/resolve/main/tokens.txt",
            localName = "parakeet-tokens.txt",
            sizeBytes = 9384L
        ),
        ModelFileDesc(
            url = "https://huggingface.co/csukuangfj/vad/resolve/main/silero_vad.onnx",
            localName = "silero_vad.onnx",
            sizeBytes = 1807522L
        )
    )

    val totalSizeBytes: Long = modelFiles.sumOf { it.sizeBytes }

    private var currentCall: okhttp3.Call? = null
    private val isCancelled = AtomicBoolean(false)

    fun getModelDirectory(): File = modelDir

    fun isModelDownloaded(): Boolean {
        if (!modelDir.exists()) return false
        return modelFiles.all { fileDesc ->
            val file = File(modelDir, fileDesc.localName)
            file.exists() && file.length() == fileDesc.sizeBytes
        }
    }

    fun deleteLocalModel(): Boolean {
        if (!modelDir.exists()) return true
        var allDeleted = true
        modelFiles.forEach { fileDesc ->
            val file = File(modelDir, fileDesc.localName)
            if (file.exists()) {
                val deleted = file.delete()
                if (!deleted) allDeleted = false
            }
            // delete any tmp files too
            val tmpFile = File(modelDir, "${fileDesc.localName}.tmp")
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
        }
        return allDeleted
    }

    fun getEncoderPath(): String = File(modelDir, "parakeet-encoder.onnx").absolutePath
    fun getDecoderPath(): String = File(modelDir, "parakeet-decoder.onnx").absolutePath
    fun getJoinerPath(): String = File(modelDir, "parakeet-joiner.onnx").absolutePath
    fun getTokensPath(): String = File(modelDir, "parakeet-tokens.txt").absolutePath
    fun getVadPath(): String = File(modelDir, "silero_vad.onnx").absolutePath

    suspend fun downloadModel(
        client: OkHttpClient,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        onComplete: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        isCancelled.set(false)
        withContext(Dispatchers.IO) {
            try {
                if (!modelDir.exists()) {
                    modelDir.mkdirs()
                }

                var cumulativeDownloaded = 0L

                for (fileDesc in modelFiles) {
                    val targetFile = File(modelDir, fileDesc.localName)
                    
                    // If file is already fully and correctly downloaded, we skip it
                    if (targetFile.exists() && targetFile.length() == fileDesc.sizeBytes) {
                        cumulativeDownloaded += fileDesc.sizeBytes
                        onProgress(cumulativeDownloaded, totalSizeBytes)
                        continue
                    }

                    // Otherwise, download it
                    val tmpFile = File(modelDir, "${fileDesc.localName}.tmp")
                    if (tmpFile.exists()) {
                        tmpFile.delete()
                    }

                    val request = Request.Builder()
                        .url(fileDesc.url)
                        .build()

                    val call = client.newCall(request)
                    currentCall = call

                    if (isCancelled.get()) {
                        throw IOException("Download cancelled by user")
                    }

                    val response = call.execute()
                    if (!response.isSuccessful) {
                        throw IOException("Failed to download ${fileDesc.localName}: HTTP ${response.code}")
                    }

                    val responseBody = response.body ?: throw IOException("Empty response body for ${fileDesc.localName}")
                    
                    responseBody.byteStream().use { inputStream ->
                        FileOutputStream(tmpFile).use { outputStream ->
                            val buffer = ByteArray(1024 * 64) // 64kb buffer
                            var bytesRead: Int
                            var fileDownloaded = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                if (isCancelled.get()) {
                                    throw IOException("Download cancelled by user")
                                }
                                outputStream.write(buffer, 0, bytesRead)
                                fileDownloaded += bytesRead
                                onProgress(cumulativeDownloaded + fileDownloaded, totalSizeBytes)
                            }
                            outputStream.flush()
                        }
                    }
                    response.close()

                    if (isCancelled.get()) {
                        throw IOException("Download cancelled by user")
                    }

                    // Rename tmp to actual
                    if (tmpFile.renameTo(targetFile)) {
                        cumulativeDownloaded += fileDesc.sizeBytes
                        onProgress(cumulativeDownloaded, totalSizeBytes)
                    } else {
                        throw IOException("Failed to rename temporary file for ${fileDesc.localName}")
                    }
                }

                currentCall = null
                onComplete(true, null)

            } catch (e: java.lang.Exception) {
                currentCall = null
                Log.e("SpeechModelManager", "Error downloading speech models", e)
                
                // Clean up any remaining .tmp files
                modelFiles.forEach { fileDesc ->
                    val tmpFile = File(modelDir, "${fileDesc.localName}.tmp")
                    if (tmpFile.exists()) {
                        tmpFile.delete()
                    }
                }

                val msg = if (isCancelled.get()) "Download cancelled." else e.message ?: "Unknown error"
                onComplete(false, msg)
            }
        }
    }

    fun cancelDownload() {
        isCancelled.set(true)
        currentCall?.cancel()
        currentCall = null
    }
}
