package com.app.assistant.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AudioHygieneProcessor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val isHandsFree: Boolean,
    private val onSamplesReady: (FloatArray) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    @Volatile
    private var isRecording = false

    fun start(): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("AudioHygieneProcessor", "RECORD_AUDIO permission not granted")
            return false
        }

        try {
            setupAudioRouting()

            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioHygieneProcessor", "Failed to initialize AudioRecord")
                stopRouting()
                return false
            }

            audioRecord = record

            // Explicitly enable system audio effects on the audio session
            val sessionId = record.audioSessionId
            enableSystemAudioEffects(sessionId)

            record.startRecording()
            isRecording = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(512)
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // Convert Short samples to Float samples in range [-1.0, 1.0]
                        val floatSamples = FloatArray(read) { buffer[it] / 32768.0f }
                        onSamplesReady(floatSamples)
                    }
                    //delay(10)
                }
            }

            Log.i("AudioHygieneProcessor", "Audio recording started successfully.")
            return true

        } catch (e: Exception) {
            Log.e("AudioHygieneProcessor", "Error starting audio recording", e)
            cleanResources()
            return false
        }
    }

    fun stop() {
        Log.i("AudioHygieneProcessor", "Stopping audio recording.")
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        cleanResources()
    }

    private fun setupAudioRouting() {
        try {
            if (isHandsFree) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
                Log.d("AudioHygieneProcessor", "Audio routing set to Speakerphone (Hands-Free)")
            } else {
                audioManager.mode = AudioManager.MODE_IN_CALL
                audioManager.isBluetoothScoOn = true
                audioManager.startBluetoothSco()
                Log.d("AudioHygieneProcessor", "Audio routing set to Bluetooth SCO")
            }
        } catch (e: Exception) {
            Log.e("AudioHygieneProcessor", "Error setting up audio routing", e)
        }
    }

    private fun stopRouting() {
        try {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = false
            if (audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                audioManager.mode = AudioManager.MODE_NORMAL
            }
            Log.d("AudioHygieneProcessor", "Audio routing reset to normal")
        } catch (e: Exception) {
            Log.e("AudioHygieneProcessor", "Error stopping audio routing", e)
        }
    }

    private fun enableSystemAudioEffects(audioSessionId: Int) {
        // Acoustic Echo Cancellation
        if (AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d("AudioHygieneProcessor", "System AcousticEchoCanceler enabled on session $audioSessionId")
                }
            } catch (e: Exception) {
                Log.e("AudioHygieneProcessor", "Failed to create AcousticEchoCanceler", e)
            }
        } else {
            Log.w("AudioHygieneProcessor", "System AcousticEchoCanceler is not available on this device")
        }

        // Noise Suppression
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d("AudioHygieneProcessor", "System NoiseSuppressor enabled on session $audioSessionId")
                }
            } catch (e: Exception) {
                Log.e("AudioHygieneProcessor", "Failed to create NoiseSuppressor", e)
            }
        } else {
            Log.w("AudioHygieneProcessor", "System NoiseSuppressor is not available on this device")
        }

        // Automatic Gain Control
        if (AutomaticGainControl.isAvailable()) {
            try {
                gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                    Log.d("AudioHygieneProcessor", "System AutomaticGainControl enabled on session $audioSessionId")
                }
            } catch (e: Exception) {
                Log.e("AudioHygieneProcessor", "Failed to create AutomaticGainControl", e)
            }
        } else {
            Log.w("AudioHygieneProcessor", "System AutomaticGainControl is not available on this device")
        }
    }

    private fun cleanResources() {
        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    try {
                        stop()
                    } catch (e: Exception) {
                        Log.e("AudioHygieneProcessor", "Error stopping AudioRecord", e)
                    }
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioHygieneProcessor", "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }

        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        gainControl?.release()
        gainControl = null

        stopRouting()
    }
}
