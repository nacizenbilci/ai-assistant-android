package com.app.assistant.usecase

import com.app.assistant.model.DeviceAction
import java.net.URI

class NavigateUseCase(private val resourceProvider: ResourceProvider) {
    suspend fun execute(
        prompt: String,
        onIntentTriggered: suspend (DeviceAction) -> Unit,
        onSuccess: suspend (location: String, navigationUri: URI) -> Unit,
        onFailure: suspend (errorMsg: String) -> Unit
    ) {
        try {
            val sanitizedPrompt = prompt.replace("\\p{Punct}+".toRegex(), "")
            val location = extractLocation(sanitizedPrompt)
            if (location != null) {
                val encodedURI = java.net.URLEncoder.encode(
                    location,
                    java.nio.charset.StandardCharsets.UTF_8.toString()
                )
                onIntentTriggered(DeviceAction.NavigateTo(location))
                onSuccess(location, URI("google.navigation:q=$encodedURI"))
            } else {
                onFailure(resourceProvider.getString("location_not_found"))
            }
        } catch (e: Exception) {
            System.err.println("Error navigating: ${e.message}")
            onFailure(resourceProvider.getString("generic_error"))
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
