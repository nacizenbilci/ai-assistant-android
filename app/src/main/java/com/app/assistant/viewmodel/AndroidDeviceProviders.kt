package com.app.assistant.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import androidx.core.content.ContextCompat
import com.app.assistant.R
import com.app.assistant.model.DeviceAction
import com.app.assistant.usecase.PermissionChecker
import com.app.assistant.usecase.ResourceProvider

class AndroidPermissionChecker(private val context: Context) : PermissionChecker {
    override fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

class AndroidResourceProvider(private val context: Context) : ResourceProvider {
    override fun getString(key: String): String {
        return when (key) {
            "song_not_found" -> context.getString(R.string.song_not_found_fallback)
            "generic_error" -> context.getString(R.string.generic_error_fallback)
            "location_not_found" -> context.getString(R.string.location_not_found_fallback)
            "new_alarm_message" -> context.getString(R.string.new_alarm_message)
            "reminder_default_context" -> context.getString(R.string.reminder_default_context)
            else -> ""
        }
    }
}

fun DeviceAction.toIntent(): Intent {
    return when (this) {
        is DeviceAction.MakeCall -> Intent(Intent.ACTION_CALL).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = Uri.parse("tel:$phoneNumber")
        }
        is DeviceAction.NavigateTo -> {
            val gmmIntentUri = Uri.parse("google.navigation:q=$location")
            Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.apps.maps")
            }
        }
        is DeviceAction.PlaySong -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        is DeviceAction.SearchSong -> Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        is DeviceAction.SetAlarm -> Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (isReminder) {
                putExtra(AlarmClock.EXTRA_RINGTONE, AlarmClock.VALUE_RINGTONE_SILENT)
            }
            if (repeatDays.isNotEmpty()) {
                putExtra(AlarmClock.EXTRA_DAYS, ArrayList(repeatDays))
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
