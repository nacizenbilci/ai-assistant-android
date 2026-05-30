package com.app.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.app.assistant.repository.ContactsRepository
import com.app.assistant.repository.SettingsRepository
import com.app.assistant.repository.WeatherRepository

class MainViewModelFactory(
    private val application: Application,
    private val speak: Boolean,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val settingsRepo = SettingsRepository(application)
            val contactsRepo = ContactsRepository(application)
            val weatherRepo = WeatherRepository(application)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, speak, settingsRepo, contactsRepo, weatherRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
