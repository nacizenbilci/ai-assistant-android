package com.app.assistant.viewmodel

import android.content.Intent
import com.google.android.gms.common.api.ResolvableApiException

sealed class UIEvent {
    data class RequestPermissions(
        val permissions: Array<String>,
        val requestCode: Int,
    ) : UIEvent()

    data class StartIntent(
        val intent: Intent,
    ) : UIEvent()

    data class ShowToast(
        val message: String,
    ) : UIEvent()

    data class SpeakText(
        val text: String,
        val queueMode: Int = 0,
    ) : UIEvent()

    object StopSpeaking : UIEvent()

    object StartSpeechRecognition : UIEvent()

    object StopSpeechRecognition : UIEvent()

    data class GetLocationForWeather(
        val itemId: Long,
        val loadingItemId: Long,
        val speak: Boolean,
        val categoryName: String,
        val prompt: String,
    ) : UIEvent()

    data class ResolveLocationSettings(
        val exception: ResolvableApiException,
    ) : UIEvent()
}
