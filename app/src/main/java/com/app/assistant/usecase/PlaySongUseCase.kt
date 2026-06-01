package com.app.assistant.usecase

import com.app.assistant.BuildConfig
import com.app.assistant.model.DeviceAction
import com.app.assistant.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URI

@Serializable
data class YouTubeItemId(
    val kind: String? = null,
    val videoId: String? = null
)

@Serializable
data class YouTubeThumbnailDetail(
    val url: String? = null
)

@Serializable
data class YouTubeThumbnails(
    val high: YouTubeThumbnailDetail? = null
)

@Serializable
data class YouTubeSnippet(
    val thumbnails: YouTubeThumbnails? = null
)

@Serializable
data class YouTubeSearchItem(
    val id: YouTubeItemId? = null,
    val snippet: YouTubeSnippet? = null
)

@Serializable
data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem>? = null
)

class PlaySongUseCase(
    private val resourceProvider: ResourceProvider,
    private val settingsRepository: SettingsRepository,
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(
        prompt: String,
        onIntentTriggered: suspend (DeviceAction) -> Unit,
        onSuccess: suspend (songName: String, videoId: String, thumbnailUrl: String, videoUri: URI) -> Unit,
        onMissingApiKey: suspend (searchQuery: String) -> Unit,
        onFailure: suspend (errorMsg: String) -> Unit
    ) {
        try {
            val sanitizedPrompt = prompt.replace("\\p{Punct}+".toRegex(), "")
            if (!sanitizedPrompt.lowercase().contains("play ")) {
                onFailure(resourceProvider.getString("song_not_found"))
                return
            }
            val searchQuery = sanitizedPrompt.lowercase().substringAfter("play").trim()

            val (videoId, thumbnailUrl) = youtubeApiCall(searchQuery)

            if (videoId.isEmpty()) {
                onFailure(resourceProvider.getString("song_not_found"))
            } else if (videoId == "Missing API Key") {
                onIntentTriggered(DeviceAction.SearchSong(searchQuery))
                onMissingApiKey(searchQuery)
            } else {
                onIntentTriggered(DeviceAction.PlaySong(videoId))
                onSuccess(
                    searchQuery,
                    videoId,
                    thumbnailUrl,
                    URI("https://www.youtube.com/watch?v=$videoId")
                )
            }
        } catch (e: Exception) {
            System.err.println("Error playing song: ${e.message}")
            onFailure(resourceProvider.getString("generic_error"))
        }
    }

    private suspend fun youtubeApiCall(query: String): Pair<String, String> {
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
        try {
            val response = json.decodeFromString<YouTubeSearchResponse>(jsonString)
            val items = response.items ?: return Pair("", "")
            if (items.isNotEmpty()) {
                val firstItem = items[0]
                val idObject = firstItem.id ?: return Pair("", "")
                if (idObject.kind == "youtube#video") {
                    val videoId = idObject.videoId ?: ""
                    val thumbnailUrl = firstItem.snippet?.thumbnails?.high?.url ?: ""
                    return Pair(videoId, thumbnailUrl)
                }
            }
        } catch (e: Exception) {
            System.err.println("Error parsing YouTube JSON: ${e.message}")
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
