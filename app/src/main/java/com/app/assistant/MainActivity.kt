package com.app.assistant

import android.Manifest
import android.app.KeyguardManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.app.assistant.ui.screen.SetupUI
import com.app.assistant.viewmodel.MainViewModel
import com.app.assistant.viewmodel.MainViewModelFactory
import com.app.assistant.viewmodel.UIEvent
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application, intent.getBooleanExtra("speak", false))
    }

    private lateinit var textToSpeech: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allowOnLockScreen()
        enableEdgeToEdge()
        initializeTextToSpeech()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect { event ->
                    when (event) {
                        is UIEvent.RequestPermissions -> {
                            ActivityCompat.requestPermissions(this@MainActivity, event.permissions, event.requestCode)
                        }

                        is UIEvent.StartIntent -> {
                            try {
                                startActivity(event.intent)
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error starting intent", e)
                            }
                        }

                        is UIEvent.ShowToast -> {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                        }

                        is UIEvent.SpeakText -> {
                            speakResponse(event.text)
                        }

                        is UIEvent.StopSpeaking -> {
                            if (::textToSpeech.isInitialized) {
                                textToSpeech.stop()
                            }
                        }

                        is UIEvent.StartSpeechRecognition -> {
                            startSpeechRecognition()
                        }

                        is UIEvent.StopSpeechRecognition -> {
                            // no-op
                        }

                        is UIEvent.GetLocationForWeather -> {
                            getCurrentLocationForWeather(event)
                        }

                        is UIEvent.ResolveLocationSettings -> {
                            try {
                                event.exception.startResolutionForResult(this@MainActivity, 104)
                            } catch (sendEx: IntentSender.SendIntentException) {
                                Log.e("MainActivity", "Error showing location settings dialog: ${sendEx.message}")
                            }
                        }
                    }
                }
            }
        }

        setContent {
            SetupUI(viewModel)
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech =
            TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    Log.d("TextToSpeech", "Initialization Success")
                } else {
                    Log.d("TextToSpeech", "Initialization Failed")
                }
            }
    }

    private fun speakResponse(plaintext: String) {
        if (!::textToSpeech.isInitialized) return
        textToSpeech.speak(plaintext, TextToSpeech.QUEUE_FLUSH, null, "left_for_now_id")
        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onDone(utteranceId: String?) {
                    viewModel.setSpeaking(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    viewModel.setSpeaking(false)
                }

                override fun onStart(utteranceId: String?) {
                    viewModel.setSpeaking(true)
                }
            },
        )
    }

    private fun startSpeechRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_CALL
        audioManager.isBluetoothScoOn = true
        audioManager.startBluetoothSco()

        val originalRingerMode = audioManager.ringerMode

        if (::textToSpeech.isInitialized && textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val speechRecognizerIntent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (viewModel.getIsTranslationEnabled()) {
                    val localeCode = getLocaleCode(viewModel.getActiveLanguageCode())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeCode)
                } else {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                }
            }

        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(bundle: Bundle?) {
                    if (originalRingerMode == AudioManager.RINGER_MODE_NORMAL) {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    }
                }

                override fun onBeginningOfSpeech() {
                    viewModel.setListening(true)
                }

                override fun onRmsChanged(v: Float) {}

                override fun onBufferReceived(bytes: ByteArray?) {}

                override fun onEndOfSpeech() {
                    viewModel.setListening(false)
                    if (originalRingerMode == AudioManager.RINGER_MODE_NORMAL) {
                        lifecycleScope.launch {
                            delay(800)
                            audioManager.ringerMode = originalRingerMode
                        }
                    }
                }

                override fun onError(errorCode: Int) {
                    viewModel.setListening(false)
                    if (originalRingerMode == AudioManager.RINGER_MODE_NORMAL) {
                        lifecycleScope.launch {
                            delay(800)
                            audioManager.ringerMode = originalRingerMode
                        }
                    }
                }

                override fun onResults(bundle: Bundle?) {
                    viewModel.setListening(false)
                    val recognizedText = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                    viewModel.onSpeechRecognized(recognizedText)
                }

                override fun onPartialResults(bundle: Bundle) {
                    val recognizedText = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                    viewModel.onSpeechPartialResult(recognizedText)
                }

                override fun onEvent(
                    i: Int,
                    bundle: Bundle?,
                ) {}
            },
        )

        speechRecognizer.startListening(speechRecognizerIntent)

        registerComponentCallbacks(
            object : ComponentCallbacks2 {
                override fun onTrimMemory(level: Int) {
                    if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                        speechRecognizer.destroy()
                    }
                }

                override fun onConfigurationChanged(newConfig: Configuration) {}

                override fun onLowMemory() {}
            },
        )
    }

    private fun getCurrentLocationForWeather(event: UIEvent.GetLocationForWeather) {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!gpsEnabled) {
            promptEnableLocation()
            viewModel.onLocationFailed(event.loadingItemId, event.speak, "GPS_OFF")
        } else {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            val locationRequest =
                LocationRequest
                    .Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                    .setWaitForAccurateLocation(true)
                    .setMaxUpdates(1)
                    .build()

            val locationCallback =
                object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        val location = locationResult.lastLocation
                        if (location != null) {
                            viewModel.onLocationReceived(
                                location.latitude,
                                location.longitude,
                                event.itemId,
                                event.loadingItemId,
                                event.speak,
                                event.categoryName,
                                event.prompt,
                            )
                        } else {
                            viewModel.onLocationFailed(event.loadingItemId, event.speak, "UNAVAILABLE")
                        }
                        fusedLocationClient.removeLocationUpdates(this)
                    }

                    override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                        if (!locationAvailability.isLocationAvailable) {
                            viewModel.onLocationFailed(event.loadingItemId, event.speak, "SUGGEST_CITY")
                        }
                    }
                }

            val permissionGranted =
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            if (permissionGranted) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            } else {
                viewModel.onLocationFailed(event.loadingItemId, event.speak, "PERMISSION")
            }
        }
    }

    private fun promptEnableLocation() {
        val locationRequest =
            LocationRequest
                .Builder(100, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build()

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val settingsClient: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = settingsClient.checkLocationSettings(builder.build())

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(this@MainActivity, 104)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.d("Location", "Error showing location settings dialog: ${sendEx.message}")
                }
            }
        }
    }

    private fun getLocaleCode(languageCode: String): String {
        val languageCountryMapping =
            mapOf(
                "af" to "ZA",
                "sq" to "AL",
                "ar" to "SA",
                "be" to "BY",
                "bg" to "BG",
                "bn" to "BD",
                "ca" to "ES",
                "zh" to "CN",
                "hr" to "HR",
                "cs" to "CZ",
                "da" to "DK",
                "nl" to "NL",
                "en" to "US",
                "eo" to "EO",
                "et" to "EE",
                "fi" to "FI",
                "fr" to "FR",
                "gl" to "ES",
                "ka" to "GE",
                "de" to "DE",
                "el" to "GR",
                "gu" to "IN",
                "ht" to "HT",
                "he" to "IL",
                "hi" to "IN",
                "hu" to "HU",
                "is" to "IS",
                "id" to "ID",
                "ga" to "IE",
                "it" to "IT",
                "ja" to "JP",
                "kn" to "IN",
                "ko" to "KR",
                "lt" to "LT",
                "lv" to "LV",
                "mk" to "MK",
                "mr" to "IN",
                "ms" to "MY",
                "mt" to "MT",
                "no" to "NO",
                "fa" to "IR",
                "pl" to "PL",
                "pt" to "BR",
                "ro" to "RO",
                "ru" to "RU",
                "sk" to "SK",
                "sl" to "SI",
                "es" to "ES",
                "sv" to "SE",
                "sw" to "KE",
                "tl" to "PH",
                "ta" to "IN",
                "te" to "IN",
                "th" to "TH",
                "tr" to "TR",
                "uk" to "UA",
                "ur" to "PK",
                "vi" to "VN",
                "cy" to "GB",
            )
        val countryCode = languageCountryMapping[languageCode] ?: Locale.getDefault().country
        return "$languageCode-$countryCode"
    }

    private fun allowOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { // Android 8.1+
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        try {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                if (keyguardManager.isKeyguardLocked) {
                    keyguardManager.requestDismissKeyguard(this, null)
                }
            }
        } catch (ex: Exception) {
            ex.message?.let { Log.d("Exception Occurred", it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::textToSpeech.isInitialized) {
            textToSpeech.shutdown()
        }
        viewModel.shutdownResources()
    }
}
