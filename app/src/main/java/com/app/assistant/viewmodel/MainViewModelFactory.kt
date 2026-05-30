package com.app.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.app.assistant.repository.ContactsRepository
import com.app.assistant.repository.SettingsRepository
import com.app.assistant.repository.WeatherRepository
import com.app.assistant.usecase.CallContactUseCase
import com.app.assistant.usecase.GetAiResponseUseCase
import com.app.assistant.usecase.GetWeatherUseCase
import com.app.assistant.usecase.NavigateUseCase
import com.app.assistant.usecase.PlaySongUseCase
import com.app.assistant.usecase.ProcessChatCommandUseCase
import com.app.assistant.usecase.SetAlarmUseCase
import com.app.assistant.usecase.SetReminderUseCase

class MainViewModelFactory(
    private val application: Application,
    private val speak: Boolean,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val settingsRepo = SettingsRepository(application)
            val contactsRepo = ContactsRepository(application)
            val weatherRepo = WeatherRepository(application)

            val getAiResponseUseCase = GetAiResponseUseCase(settingsRepo)
            val callContactUseCase = CallContactUseCase(application, contactsRepo)
            val playSongUseCase = PlaySongUseCase(settingsRepo)
            val navigateUseCase = NavigateUseCase()
            val getWeatherUseCase = GetWeatherUseCase(application, weatherRepo, getAiResponseUseCase)
            val setAlarmUseCase = SetAlarmUseCase()
            val setReminderUseCase = SetReminderUseCase()
            val processChatCommandUseCase = ProcessChatCommandUseCase(getAiResponseUseCase)

            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                application,
                speak,
                settingsRepo,
                callContactUseCase,
                playSongUseCase,
                navigateUseCase,
                getWeatherUseCase,
                setAlarmUseCase,
                setReminderUseCase,
                processChatCommandUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
