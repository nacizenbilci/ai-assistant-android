package com.app.assistant.usecase

import android.content.Intent
import android.net.Uri
import android.util.Log
import java.net.URI

class NavigateUseCase {
    suspend fun execute(
        prompt: String,
        onIntentTriggered: suspend (Intent) -> Unit,
        onSuccess: suspend (location: String, navigationUri: URI) -> Unit,
        onFailure: suspend (errorMsg: String) -> Unit
    ) {
        try {
            val sanitizedPrompt = prompt.replace("\\p{Punct}+".toRegex(), "")
            val location = extractLocation(sanitizedPrompt)
            if (location != null) {
                val gmmIntentUri = Uri.parse("google.navigation:q=$location")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("com.google.android.apps.maps")
                }
                onIntentTriggered(mapIntent)

                val encodedURI = java.net.URLEncoder.encode(
                    location,
                    java.nio.charset.StandardCharsets.UTF_8.toString()
                )
                onSuccess(location, URI("google.navigation:q=$encodedURI"))
            } else {
                onFailure("I can not find such location, please try again.")
            }
        } catch (e: Exception) {
            Log.d("NavigateUseCase", e.message.toString())
            onFailure("Something went wrong, please try again.")
        }
    }

    private fun extractLocation(command: String): String? {
        val regex = Regex(
            "(?<=to |find |show me |give me directions to |navigate me to |" +
                "how do I get to |I'm on my way to |start a navigation to |" +
                "where is |help me find |traffic like on the way to |way to )" +
                "([A-Za-z0-9\\s&]+)",
            RegexOption.IGNORE_CASE
        )

        val matchResult = regex.find(command)
        return matchResult?.value?.trim()
    }
}
