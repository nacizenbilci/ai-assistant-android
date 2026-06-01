package com.app.assistant.viewmodel

fun getRandomResponse(responses: List<String>): String =
    responses.randomOrNull() ?: responses.firstOrNull() ?: "An unexpected error occurred."

object ResponseStrings {
    val permissionContactsCall = listOf(
        "I need permission to access your contacts and make phone call. Please allow and try again.",
        "Hey, I’ll need access to your contacts and calling first. Can you allow that?",
        "Looks like I don’t have permission to call yet. Please enable it and retry.",
        "I can help with that, but I need contacts and call access first.",
        "Please grant me contacts and calling permission so I can make the call for you.",
        "I can’t make calls without your permission. Could you turn it on?"
    )

    val permissionLocation = listOf(
        "I need permission to access your location. Please try again.",
        "I can get the weather, but I’ll need your location first.",
        "Looks like location access isn’t granted. Please allow it and try again.",
        "Hey, could you enable location permission so I can show the weather?",
        "To fetch the forecast, I’ll need your location. Can you grant access?",
        "Without your location, I can’t check the weather. Please turn it on."
    )

    val locationServiceOff = listOf(
        "I need to know your location for that. Please turn on your location and try again.",
        "looks like location is off. Could you enable it?",
        "I can’t continue without your location. Please turn it on.",
        "Hey, I’ll need your location for this. Can you switch it on?",
        "Looks like your location services are disabled. Please activate them.",
        "Please turn on location, then I’ll be able to continue."
    )

    val callFailed = listOf(
        "Sorry, failed to make call. Please try again.",
        "Oops, the call didn’t go through. Want to retry?",
        "I couldn’t complete the call. Please try again.",
        "Looks like that call failed. Give it another shot?",
        "Something went wrong with the call. Please try again later.",
        "I wasn’t able to connect the call. Can we try once more?"
    )

    val contactNotFound = listOf(
        "I cannot find such contact, please try again.",
        "I didn’t find that contact in your list.",
        "No contact matched that name, could you check and retry?",
        "Sorry, I couldn’t locate that person in your contacts.",
        "Looks like that name isn’t saved in your contacts.",
        "I couldn’t find that contact. Maybe try with a different name?"
    )

    val songNotFound = listOf(
        "I can not find such song, please try again.",
        "Sorry, I couldn’t find that track.",
        "no song matched your request. Want to try another?",
        "I wasn’t able to locate that song. Please retry.",
        "Looks like that song isn’t available right now.",
        "I couldn’t find that one. Maybe try with a different title?"
    )

    val locationNotFound = listOf(
        "I can not find such location, please try again.",
        "Sorry, I couldn’t figure out where that is.",
        "I wasn’t able to locate that place.",
        "No results for that location. Can you check and try again?",
        "Looks like that place isn’t on my map data.",
        "I couldn’t find that spot. Maybe try with a different name?"
    )

    val weatherReportUnavailable = listOf(
        "Seems weather report is not available, please try again.",
        "Sorry, I couldn’t get the weather right now.",
        "The weather service isn’t responding. Please try later.",
        "Looks like weather data is down at the moment.",
        "I wasn’t able to fetch the forecast. Can you retry later?",
        "Weather info isn’t available right now. Please check back soon."
    )

    val locationUnknownSuggestCity = listOf(
        "Your location is not available to me. Please try again with your city name.",
        "I couldn’t detect your location. Could you tell me your city instead?",
        "Looks like location services aren’t working. Please provide your city name.",
        "I’m not getting your location right now. Can you enter your city?",
        "Sorry, I can’t access your current location. A city name would help.",
        "Your location seems unavailable. Please try with your city name."
    )

    val invalidTime = listOf(
        "That doesn't seems like an actual time. Please try again.",
        "I didn’t recognize that as a valid time.",
        "That time format looks off. Could you retry?",
        "Sorry, I couldn’t understand that time input.",
        "That doesn’t look like a proper time. Please try again.",
        "Can you give me a valid time so I can continue?"
    )

    val alarmSetSuccess = listOf(
        "Alarm set successfully...",
        "Done! Your alarm is ready.",
        "Great, I’ve set the alarm for you.",
        "All set, your alarm has been scheduled.",
        "Alarm saved successfully.",
        "Okay, I’ve configured the alarm as requested."
    )

    val reminderSetSuccess = listOf(
        "Reminder set successfully...",
        "Done! Your reminder is ready.",
        "Great, I’ve saved the reminder for you.",
        "All set, your reminder has been scheduled.",
        "Reminder saved successfully.",
        "Okay, I’ve created the reminder as requested."
    )

    val promptForTime = listOf(
        "Sure, at what time?",
        "Alright, when should I set it?",
        "Okay, what time would you like?",
        "Got it, please tell me the time.",
        "When do you want me to set it for?",
        "Sure thing, what time works for you?"
    )

    val genericError = listOf(
        "Something went wrong, please try again.",
        "Oops, that didn’t work. Please retry.",
        "I ran into an issue. Can you try again?",
        "Sorry, something broke there. Please try once more.",
        "That didn’t go through. Could you retry?",
        "An error popped up. Let’s try that again."
    )
}
