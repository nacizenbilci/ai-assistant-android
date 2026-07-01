package com.app.assistant.model

sealed class DeviceAction {
    data class MakeCall(val phoneNumber: String) : DeviceAction()
    data class NavigateTo(val location: String) : DeviceAction()
    data class PlaySong(val videoId: String) : DeviceAction()
    data class SearchSong(val query: String) : DeviceAction()
    data class SetAlarm(
        val message: String,
        val hour: Int,
        val minutes: Int,
        val repeatDays: List<Int>,
        val isReminder: Boolean
    ) : DeviceAction()
}
