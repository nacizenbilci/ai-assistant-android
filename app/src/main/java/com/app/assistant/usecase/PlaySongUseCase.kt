package com.app.assistant.usecase

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.app.assistant.BuildConfig
import com.app.assistant.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URI

class PlaySongUseCase(
    private val settingsRepository: SettingsRepository
) {
    suspend fun execute(
        prompt: String,
        onIntentTriggered: suspend (Intent) -> Unit,
        onSuccess: suspend (songName: String, videoId: String, thumbnailUrl: String, videoUri: URI) -> Unit,
        onMissingApiKey: suspend (searchQuery: String) -> Unit,
        onFailure: suspend (errorMsg: String) -> Unit
    ) {
        try {
            val sanitizedPrompt = prompt.replace("\\p{Punct}+".toRegex(), "")
            if (!sanitizedPrompt.lowercase().contains("play ")) {
                onFailure("I can not find such song, please try again.")
                return
            }
            val searchQuery = sanitizedPrompt.lowercase().substringAfter("play").trim()

            val (videoId, thumbnailUrl) = youtubeApiCall(searchQuery)

            if (videoId.isEmpty()) {
                onFailure("I can not find such song, please try again.")
            } else if (videoId == "Missing API Key") {
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", searchQuery)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                onIntentTriggered(intent)
                onMissingApiKey(searchQuery)
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.youtube.com/watch?v=$videoId")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                onIntentTriggered(intent)
                onSuccess(
                    searchQuery,
                    videoId,
                    thumbnailUrl,
                    URI("http://www.youtube.com/watch?v=$videoId")
                )
            }
        } catch (e: Exception) {
            Log.d("PlaySongUseCase", e.message.toString())
            onFailure("Something went wrong, please try again.")
        }
    }

    private suspend fun youtubeApiCall(query: String): Pair<String, String> {
        val client = OkHttpClient()
        val deferredResult = CompletableDeferred<Pair<String, String>>()

        val urlBuilder = "https://www.googleapis.com/youtube/v3/search"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("key", loadYoutubeKey())
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("type", "video")
            ?.addQueryParameter("part", "snippet")
            ?.addQueryParameter("maxResults", "1")
            ?.build()

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                deferredResult.completeExceptionally(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        deferredResult.complete(extractVideoIdAndThumbnail(body))
                    } else {
                        deferredResult.complete(Pair("", ""))
                    }
                } else {
                    if (response.code == 403) {
                        deferredResult.complete(Pair("Missing API Key", ""))
                    } else {
                        deferredResult.complete(Pair("", ""))
                    }
                }
                response.close()
            }
        })

        return deferredResult.await()
    }

    private fun extractVideoIdAndThumbnail(jsonString: String): Pair<String, String> {
        val jsonObject = JSONObject(jsonString)
        val itemsArray = jsonObject.optJSONArray("items") ?: return Pair("", "")

        if (itemsArray.length() > 0) {
            val firstItem = itemsArray.optJSONObject(0) ?: return Pair("", "")
            val idObject = firstItem.optJSONObject("id") ?: return Pair("", "")

            if (idObject.optString("kind") == "youtube#video") {
                val videoId = idObject.optString("videoId", "")
                val thumbnailUrl = firstItem
                    .optJSONObject("snippet")
                    ?.optJSONObject("thumbnails")
                    ?.optJSONObject("high")
                    ?.optString("url", "") ?: ""

                return Pair(videoId, thumbnailUrl)
            }
        }
        return Pair("", "")
    }

    private fun loadYoutubeKey(): String? {
        var youtubeKey = settingsRepository.getYoutubeApiKey()
        if (youtubeKey.isNullOrBlank()) {
            youtubeKey = BuildConfig.YOUTUBE_API_KEY
        }
        return youtubeKey
    }
}
