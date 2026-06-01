package com.app.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.app.assistant.db.DynamicConversationRepository
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
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainViewModelFactory(
    private val application: Application,
    private val speak: Boolean,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val settingsRepo = SettingsRepository(application)
            val contactsRepo = ContactsRepository(application)
            val dbRepo = DynamicConversationRepository(application)
            
            val okHttpClient = okHttpClient

            val weatherRepo = WeatherRepository(application, okHttpClient)

            val getAiResponseUseCase = GetAiResponseUseCase(settingsRepo, okHttpClient)
            val callContactUseCase = CallContactUseCase(application, contactsRepo)
            val playSongUseCase = PlaySongUseCase(application, settingsRepo, okHttpClient)
            val navigateUseCase = NavigateUseCase(application)
            val getWeatherUseCase = GetWeatherUseCase(application, weatherRepo, getAiResponseUseCase)
            val setAlarmUseCase = SetAlarmUseCase(application)
            val setReminderUseCase = SetReminderUseCase(application)
            val processChatCommandUseCase = ProcessChatCommandUseCase(getAiResponseUseCase)

            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                application,
                speak,
                settingsRepo,
                dbRepo,
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

    companion object {
        val okHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
