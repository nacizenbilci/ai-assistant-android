package com.app.assistant.viewmodel

import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.app.assistant.model.Conversation
import com.app.assistant.util.Category
import com.app.assistant.util.LockState
import com.app.assistant.util.Constants.MAIN_CONTEXT
import kotlinx.coroutines.launch
import java.net.URI

// Call Contact UseCase Delegate
fun MainViewModel.callContact(
    itemId: Long,
    loadingItemId: Long,
    speak: Boolean,
    category: Category,
) {
    if (chatList.isNotEmpty()) {
        val prompt = chatList.find { it.id == itemId }?.englishText ?: return
        viewModelScope.launch {
            callContactUseCase.execute(
                prompt = prompt,
                onPermissionRequest = { permissions ->
                    _uiEvent.emit(UIEvent.RequestPermissions(permissions, 102))
                    processResponse(getRandomResponse(ResponseStrings.permissionContactsCall), loadingItemId, speak, Category.OTHER)
                },
                onIntentTriggered = { intent ->
                    _uiEvent.emit(UIEvent.StartIntent(intent))
                },
                onSuccess = { name, dialUri ->
                    processResponse(
                        name,
                        loadingItemId,
                        false,
                        category,
                        navigationURI = dialUri
                    )
                },
                onFailure = { errorMsg ->
                    processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
                }
            )
        }
    }
}

// Play Song UseCase Delegate
fun MainViewModel.playSong(
    itemId: Long,
    loadingItemId: Long,
    speak: Boolean,
    category: Category,
) {
    val prompt = chatList.find { it.id == itemId }?.englishText ?: return
    viewModelScope.launch {
        playSongUseCase.execute(
            prompt = prompt,
            onIntentTriggered = { intent ->
                _uiEvent.emit(UIEvent.StartIntent(intent))
            },
            onSuccess = { songName, videoId, thumbnailUrl, videoUri ->
                processResponse(
                    "Playing $songName",
                    loadingItemId,
                    speak,
                    category,
                    thumbnailUrl,
                    videoUri
                )
            },
            onMissingApiKey = { searchQuery ->
                processResponse("Your Youtube API key is missing or invalid.", loadingItemId, speak, Category.OTHER)
            },
            onFailure = { errorMsg ->
                processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
            }
        )
    }
}

// Navigate UseCase Delegate
fun MainViewModel.navigate(
    itemId: Long,
    loadingItemId: Long,
    speak: Boolean,
    category: Category,
) {
    val prompt = chatList.find { it.id == itemId }?.englishText ?: return
    viewModelScope.launch {
        navigateUseCase.execute(
            prompt = prompt,
            onIntentTriggered = { intent ->
                _uiEvent.emit(UIEvent.StartIntent(intent))
            },
            onSuccess = { location, navigationUri ->
                processResponse(
                    "Navigating to $location.",
                    loadingItemId,
                    speak,
                    category,
                    navigationURI = navigationUri
                )
            },
            onFailure = { errorMsg ->
                processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
            }
        )
    }
}

// Weather UseCase Delegate
fun MainViewModel.fetchWeather(
    itemId: Long,
    loadingItemId: Long,
    speak: Boolean,
    category: Category,
) {
    val prompt = chatList.find { it.id == itemId }?.englishText ?: return
    viewModelScope.launch {
        getWeatherUseCase.execute(
            prompt = prompt,
            onPermissionRequest = { permissions ->
                _uiEvent.emit(UIEvent.RequestPermissions(permissions, 103))
                processResponse(getRandomResponse(ResponseStrings.permissionLocation), loadingItemId, speak, Category.OTHER)
            },
            onLocationRequest = {
                _uiEvent.emit(
                    UIEvent.GetLocationForWeather(
                        itemId,
                        loadingItemId,
                        speak,
                        category.name,
                        prompt
                    )
                )
            },
            onSuccess = { response, location ->
                processResponse(
                    response,
                    loadingItemId,
                    speak,
                    category,
                    navigationURI = URI("https://www.google.com/search?q=weather+$location")
                )
            },
            onFailure = { errorMsg ->
                processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
            }
        )
    }
}

fun MainViewModel.onLocationReceived(
    lat: Double,
    long: Double,
    itemId: Long,
    loadingItemId: Long,
    speak: Boolean,
    categoryName: String,
    prompt: String,
) {
    viewModelScope.launch {
        getWeatherUseCase.processLocationWeather(
            lat = lat,
            long = long,
            prompt = prompt,
            onSuccess = { response, city ->
                processResponse(
                    response,
                    loadingItemId,
                    speak,
                    Category.valueOf(categoryName),
                    navigationURI = URI("https://www.google.com/search?q=weather+${
                        java.net.URLEncoder.encode(city, java.nio.charset.StandardCharsets.UTF_8.toString())
                    }")
                )
            },
            onFailure = { errorMsg ->
                processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
            }
        )
    }
}

fun MainViewModel.onLocationFailed(
    loadingItemId: Long,
    speak: Boolean,
    errorType: String,
) {
    viewModelScope.launch {
        val responseText = when (errorType) {
            "GPS_OFF" -> getRandomResponse(ResponseStrings.locationServiceOff)
            "UNAVAILABLE" -> getRandomResponse(ResponseStrings.weatherReportUnavailable)
            else -> getRandomResponse(ResponseStrings.locationUnknownSuggestCity)
        }
        processResponse(responseText, loadingItemId, speak, Category.OTHER)
    }
}

// Set Alarm UseCase Delegate
fun MainViewModel.setAlarm(
    itemId: Long,
    loadingItemId: Long,
    speak: Boolean,
    category: Category,
) {
    val prompt = chatList.find { it.id == itemId }?.englishText ?: return
    viewModelScope.launch {
        setAlarmUseCase.execute(
            prompt = prompt,
            onPromptForTime = { dayMatch ->
                lockState = LockState.LockAlarm(day = dayMatch)
                processResponse(
                    getRandomResponse(ResponseStrings.promptForTime),
                    loadingItemId,
                    speak,
                    Category.OTHER
                )
            },
            onSuccess = { intent ->
                _uiEvent.emit(UIEvent.StartIntent(intent))
                processResponse(
                    getRandomResponse(ResponseStrings.alarmSetSuccess),
                    loadingItemId,
                    speak,
                    category
                )
            },
            onFailure = { errorMsg ->
                processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
            }
        )
    }
}

fun MainViewModel.handleAlarmLockState(
    itemId: Long,
    loadingItemId: Long,
    speak: Boolean,
    state: LockState.LockAlarm,
) {
    val prompt = chatList.find { it.id == itemId }?.englishText ?: return
    viewModelScope.launch {
        setAlarmUseCase.execute(
            prompt = prompt,
            dayOverride = state.day,
            onPromptForTime = {
                processResponse(getRandomResponse(ResponseStrings.invalidTime), loadingItemId, speak, Category.OTHER)
            },
            onSuccess = { intent ->
                lockState = LockState.None
                _uiEvent.emit(UIEvent.StartIntent(intent))
                processResponse(getRandomResponse(ResponseStrings.alarmSetSuccess), loadingItemId, speak, Category.ALARM)
            },
            onFailure = { errorMsg ->
                processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
            }
        )
    }
}

// Set Reminder UseCase Delegate
fun MainViewModel.setReminder(
    itemId: Long,
    loadingItemId: Long,
    speak: Boolean,
    category: Category,
) {
    val prompt = chatList.find { it.id == itemId }?.englishText ?: return
    viewModelScope.launch {
        setReminderUseCase.execute(
            prompt = prompt,
            onPromptForTime = { dayMatch, context ->
                lockState = LockState.LockReminder(day = dayMatch, context = context)
                processResponse(getRandomResponse(ResponseStrings.promptForTime), loadingItemId, speak, Category.OTHER)
            },
            onSuccess = { intent ->
                _uiEvent.emit(UIEvent.StartIntent(intent))
                processResponse(getRandomResponse(ResponseStrings.reminderSetSuccess), loadingItemId, speak, category)
            },
            onFailure = { errorMsg ->
                processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
            }
        )
    }
}

fun MainViewModel.handleReminderLockState(
    itemId: Long,
    loadingItemId: Long,
    speak: Boolean,
    state: LockState.LockReminder,
) {
    val prompt = chatList.find { it.id == itemId }?.englishText ?: return
    viewModelScope.launch {
        setReminderUseCase.execute(
            prompt = prompt,
            dayOverride = state.day,
            contextOverride = state.context,
            onPromptForTime = { _, _ ->
                processResponse(getRandomResponse(ResponseStrings.invalidTime), loadingItemId, speak, Category.OTHER)
            },
            onSuccess = { intent ->
                lockState = LockState.None
                _uiEvent.emit(UIEvent.StartIntent(intent))
                processResponse(getRandomResponse(ResponseStrings.reminderSetSuccess), loadingItemId, speak, Category.ALARM)
            },
            onFailure = { errorMsg ->
                processResponse(errorMsg, loadingItemId, speak, Category.OTHER)
            }
        )
    }
}

fun MainViewModel.callAI(
    loadingItemId: Long,
    speak: Boolean,
    category: Category,
) {
    viewModelScope.launch {
        val response = processChatCommandUseCase.getAiChatResponse(MAIN_CONTEXT, chatList.toList())
        processResponse(response, loadingItemId, speak, category = category)
    }
}
