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
import com.app.assistant.viewmodel.SettingsViewModel
import com.app.assistant.viewmodel.SettingsViewModelFactory
import com.app.assistant.viewmodel.UIEvent
import com.app.assistant.viewmodel.onLocationFailed
import com.app.assistant.viewmodel.onLocationReceived
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

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(com.app.assistant.repository.SettingsRepository(application))
    }

    private lateinit var textToSpeechManager: com.app.assistant.hardware.TextToSpeechManager
    private lateinit var speechRecognizerManager: com.app.assistant.hardware.SpeechRecognizerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allowOnLockScreen()
        enableEdgeToEdge()
        
        textToSpeechManager = com.app.assistant.hardware.TextToSpeechManager(this) { isSpeaking ->
            viewModel.setSpeaking(isSpeaking)
        }
        speechRecognizerManager = com.app.assistant.hardware.SpeechRecognizerManager(this, lifecycleScope)

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
                            textToSpeechManager.speak(event.text)
                        }

                        is UIEvent.StopSpeaking -> {
                            textToSpeechManager.stop()
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
            SetupUI(viewModel, settingsViewModel)
        }
    }

    private fun startSpeechRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        if (textToSpeechManager.isSpeaking()) {
            textToSpeechManager.stop()
        }

        speechRecognizerManager.startListening(
            languageCode = viewModel.getActiveLanguageCode(),
            isTranslationEnabled = viewModel.getIsTranslationEnabled(),
            listener = object : com.app.assistant.hardware.SpeechRecognizerManager.SpeechListener {
                override fun onReadyForSpeech() {}

                override fun onBeginningOfSpeech() {
                    viewModel.setListening(true)
                }

                override fun onEndOfSpeech() {
                    viewModel.setListening(false)
                }

                override fun onError(errorCode: Int) {
                    viewModel.setListening(false)
                }

                override fun onResults(recognizedText: String) {
                    viewModel.setListening(false)
                    viewModel.onSpeechRecognized(recognizedText)
                }

                override fun onPartialResults(recognizedText: String) {
                    viewModel.onSpeechPartialResult(recognizedText)
                }
            }
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
        textToSpeechManager.shutdown()
        speechRecognizerManager.destroy()
        viewModel.shutdownResources()
    }
}
