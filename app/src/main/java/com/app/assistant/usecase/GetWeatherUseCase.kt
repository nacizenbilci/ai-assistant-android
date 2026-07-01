package com.app.assistant.usecase

import com.app.assistant.llm.LlmMessage
import com.app.assistant.repository.WeatherRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GetWeatherUseCase(
    private val permissionChecker: PermissionChecker,
    private val weatherRepository: WeatherRepository,
    private val getAiResponseUseCase: GetAiResponseUseCase
) {
    suspend fun execute(
        prompt: String,
        onPermissionRequest: suspend (Array<String>) -> Unit,
        onLocationRequest: suspend () -> Unit,
        onSuccess: suspend (response: String, city: String) -> Unit,
        onFailure: suspend (errorMsg: String) -> Unit
    ) {
        try {
            val location = weatherRepository.extractPlaceName(prompt)
            if (location.isNotEmpty()) {
                val coordinates = weatherRepository.getCoordinatesFromCity(location)
                if (coordinates != null) {
                    val (lat, long) = coordinates
                    val weatherData = weatherRepository.getWeatherData(lat, long)
                    if (weatherData != null) {
                        val weatherJson = Json.parseToJsonElement(weatherData).jsonObject
                        val filteredMap = weatherJson.filterKeys { it != "latitude" && it != "longitude" }
                        val filteredJson = JsonObject(filteredMap)

                        val aiResponse = queryWeatherAI(prompt, filteredJson.toString())
                        if (!aiResponse.isNullOrEmpty()) {
                            onSuccess(aiResponse, location)
                        } else {
                            onFailure("Seems weather report is not available, please try again.")
                        }
                    } else {
                        onFailure("Seems weather report is not available, please try again.")
                    }
                } else {
                    onFailure("I can not find such location, please try again.")
                }
            } else {
                val isPermissionGranted = permissionChecker.hasPermission("android.permission.ACCESS_COARSE_LOCATION")

                if (!isPermissionGranted) {
                    onPermissionRequest(arrayOf("android.permission.ACCESS_COARSE_LOCATION"))
                } else {
                    onLocationRequest()
                }
            }
        } catch (e: Exception) {
            System.err.println("Error fetching weather: ${e.message}")
            onFailure("Something went wrong, please try again.")
        }
    }

    suspend fun processLocationWeather(
        lat: Double,
        long: Double,
        prompt: String,
        onSuccess: suspend (response: String, city: String) -> Unit,
        onFailure: suspend (errorMsg: String) -> Unit
    ) {
        val city = weatherRepository.getCityNameFromLocation(lat, long) ?: "your location"
        val weatherData = weatherRepository.getWeatherData(lat, long)
        if (weatherData != null) {
            val weatherJson = Json.parseToJsonElement(weatherData).jsonObject
            val filteredMap = weatherJson.filterKeys { it != "latitude" && it != "longitude" }
            val filteredJson = JsonObject(filteredMap)

            val aiResponse = queryWeatherAI(prompt, filteredJson.toString())
            if (!aiResponse.isNullOrEmpty()) {
                onSuccess(aiResponse, city)
            } else {
                onFailure("Seems weather report is not available, please try again.")
            }
        } else {
            onFailure("Seems weather report is not available, please try again.")
        }
    }

    private suspend fun queryWeatherAI(question: String, weatherData: String): String? {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault(Locale.Category.FORMAT)).format(Calendar.getInstance().time)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault(Locale.Category.FORMAT)).format(Calendar.getInstance().time)
        val systemContext =
            "You are a smart weather assistant, up-to-date with the current date and time, " +
                "which is $date at $time. Please respond with an answer to the user's question based on " +
                "the latest weather data provided. No need to mention data or time; just answer naturally in 2 to 3 lines."

        val messages = listOf(
            LlmMessage(role = "system", content = systemContext),
            LlmMessage(role = "user", content = question + System.lineSeparator() + weatherData)
        )

        return getAiResponseUseCase.execute(messages)
    }
}
