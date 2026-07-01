package com.app.assistant.repository

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale

@Serializable
data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

class WeatherRepository(
    private val context: Context,
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun extractPlaceName(inputText: String): String {
        // Pattern to match place names after "in" or "for", excluding trailing words like "today", "tomorrow", etc.
        val pattern =
            Regex(
                "\\b(?:in|for)\\s+([A-Za-z]+(?:\\s+[A-Za-z]+)*)" +
                    "(?=\\b(?:\\s+(today|now|tomorrow|report|forecast|currently|evening|morning|afternoon)\\b|\\s*$))",
            )

        // Search for a match in the input text
        val match = pattern.find(inputText)

        // Check if the captured group does not match excluded words (e.g., "tomorrow") and return it
        val placeName =
            match
                ?.groups
                ?.get(1)
                ?.value
                ?.trim()
        return if (placeName != null &&
            !placeName.lowercase().matches(Regex("today|now|tomorrow|report|forecast|currently|evening|morning|afternoon"))
        ) {
            placeName
        } else {
            ""
        }
    }

    suspend fun getCoordinatesFromCity(cityName: String): Pair<Double, Double>? {
        val deferredResult = CompletableDeferred<Pair<Double, Double>?>()

        val urlBuilder =
            "https://geocoding-api.open-meteo.com/v1/search"
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("name", cityName)
                ?.build()

        val request =
            Request
                .Builder()
                .url(urlBuilder.toString())
                .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    Log.e("WeatherRepository", "Request failed: $e")
                    deferredResult.completeExceptionally(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        deferredResult.complete(extractCoordinates(body, cityName))
                    } else {
                        Log.e("WeatherRepository", "Request unsuccessful: ${response.code}")
                        deferredResult.cancel()
                    }
                }
            },
        )

        return deferredResult.await()
    }

    private fun extractCoordinates(
        body: String,
        cityName: String,
    ): Pair<Double, Double>? {
        if (body.isEmpty()) return null
        try {
            val response = json.decodeFromString<GeocodingResponse>(body)
            val results = response.results ?: return null
            if (results.isEmpty()) return null

            val matchedResult = results.find { it.name.equals(cityName, ignoreCase = true) } ?: results[0]
            return Pair(matchedResult.latitude, matchedResult.longitude)
        } catch (e: Exception) {
            Log.d("extractCoordinates Exception", e.message ?: "")
        }
        return null
    }

    suspend fun getCityNameFromLocation(
        latitude: Double,
        longitude: Double,
    ): String? = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.getDefault())
        return@withContext try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                addresses[0].locality // This gives the city name
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getWeatherData(
        latitude: Double,
        longitude: Double,
    ): String? {
        val deferredResult = CompletableDeferred<String?>()

        val urlBuilder =
            "https://api.open-meteo.com/v1/forecast"
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("latitude", latitude.toString())
                ?.addQueryParameter("longitude", longitude.toString())
                ?.addQueryParameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,windspeed_10m_max")
                ?.addQueryParameter("hourly", "temperature_2m,precipitation,windspeed_10m")
                ?.addQueryParameter("timezone", "auto")
                ?.addQueryParameter("forecast_days", "10")
                ?.build()

        val request =
            Request
                .Builder()
                .url(urlBuilder.toString())
                .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    Log.e("WeatherRepository", "Weather data request failed: $e")
                    deferredResult.complete(null)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        deferredResult.complete(body)
                    } else {
                        Log.e("WeatherRepository", "Weather data request unsuccessful: ${response.code}")
                        deferredResult.complete(null)
                    }
                }
            },
        )

        return deferredResult.await()
    }
}
