package com.app.assistant.viewmodel

import android.content.Context
import com.app.assistant.R

fun getRandomResponse(responses: List<String>): String =
    responses.randomOrNull() ?: responses.firstOrNull() ?: "An unexpected error occurred."

object ResponseStrings {
    fun permissionContactsCall(context: Context): List<String> =
        context.resources.getStringArray(R.array.permission_contacts_call).toList()

    fun permissionLocation(context: Context): List<String> =
        context.resources.getStringArray(R.array.permission_location).toList()

    fun locationServiceOff(context: Context): List<String> =
        context.resources.getStringArray(R.array.location_service_off).toList()

    fun callFailed(context: Context): List<String> =
        context.resources.getStringArray(R.array.call_failed).toList()

    fun contactNotFound(context: Context): List<String> =
        context.resources.getStringArray(R.array.contact_not_found).toList()

    fun songNotFound(context: Context): List<String> =
        context.resources.getStringArray(R.array.song_not_found).toList()

    fun locationNotFound(context: Context): List<String> =
        context.resources.getStringArray(R.array.location_not_found).toList()

    fun weatherReportUnavailable(context: Context): List<String> =
        context.resources.getStringArray(R.array.weather_report_unavailable).toList()

    fun locationUnknownSuggestCity(context: Context): List<String> =
        context.resources.getStringArray(R.array.location_unknown_suggest_city).toList()

    fun invalidTime(context: Context): List<String> =
        context.resources.getStringArray(R.array.invalid_time).toList()

    fun alarmSetSuccess(context: Context): List<String> =
        context.resources.getStringArray(R.array.alarm_set_success).toList()

    fun reminderSetSuccess(context: Context): List<String> =
        context.resources.getStringArray(R.array.reminder_set_success).toList()

    fun promptForTime(context: Context): List<String> =
        context.resources.getStringArray(R.array.prompt_for_time).toList()

    fun genericError(context: Context): List<String> =
        context.resources.getStringArray(R.array.generic_error).toList()
}
