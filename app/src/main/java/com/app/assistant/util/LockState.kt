package com.app.assistant.util

sealed class LockState {
    object None : LockState() // Default state when no task is locked

    data class LockAlarm(
        // Already processed day (e.g., "tomorrow")
        var day: String? = null,
        // Time to be provided
        var time: String? = null,
    ) : LockState()

    data class LockReminder(
        // Already processed day (e.g., "tomorrow")
        var day: String? = null,
        // Time to be provided
        var time: String? = null,
        // Message for reminder
        var context: String? = null,
    ) : LockState()

    object LockNavigation : LockState() // No additional data needed

    // Add more categories here as needed
}
