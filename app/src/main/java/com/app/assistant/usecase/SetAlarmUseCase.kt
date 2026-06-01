package com.app.assistant.usecase

import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import java.util.Calendar
import java.util.Locale

class SetAlarmUseCase {
    suspend fun execute(
        prompt: String,
        dayOverride: String? = null,
        onPromptForTime: suspend (dayMatch: String?) -> Unit,
        onSuccess: suspend (intent: Intent) -> Unit,
        onFailure: suspend (errorMsg: String) -> Unit
    ) {
        try {
            val sanitizedPrompt = prompt.replace(PunctuationRegex, "")

            val dayMatch = dayOverride ?: DayRegex.find(sanitizedPrompt)?.value?.lowercase(Locale.ROOT)
            val timeMatch = TimeRegex.find(sanitizedPrompt)
            val relativeTimeMatch = RelativeTimeRegex.find(sanitizedPrompt)

            if (timeMatch == null && relativeTimeMatch == null) {
                onPromptForTime(dayMatch)
            } else {
                val intent = setAlarmFromPrompt(dayMatch, timeMatch, relativeTimeMatch, "New Alarm")
                onSuccess(intent)
            }
        } catch (e: Exception) {
            Log.e("SetAlarmUseCase", "Error setting alarm", e)
            onFailure("Something went wrong, please try again.")
        }
    }

    private fun setAlarmFromPrompt(
        dayMatch: String?,
        timeMatch: MatchResult?,
        relativeTimeMatch: MatchResult?,
        message: String
    ): Intent {
        val calendar = Calendar.getInstance()

        if (relativeTimeMatch != null) {
            val firstValue = relativeTimeMatch.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val halfValue = if (relativeTimeMatch.groupValues[2] == "half") 0.5 else 0.0
            val firstUnit = relativeTimeMatch.groupValues[3].lowercase(Locale.ROOT)

            var secondValue = 0
            var secondUnit = ""

            if (relativeTimeMatch.groupValues[4].isNotEmpty()) {
                secondValue = relativeTimeMatch.groupValues[4].toInt()
                secondUnit = relativeTimeMatch.groupValues[5].lowercase(Locale.ROOT)
            }

            when {
                firstUnit.contains("hour") || firstUnit.contains("hrs") -> {
                    calendar.add(Calendar.HOUR_OF_DAY, firstValue)
                    if (halfValue > 0) {
                        calendar.add(Calendar.MINUTE, 30)
                    }
                }
                firstUnit.contains("min") -> {
                    calendar.add(Calendar.MINUTE, firstValue)
                }
            }

            when {
                secondUnit.contains("hour") || secondUnit.contains("hrs") -> calendar.add(Calendar.HOUR_OF_DAY, secondValue)
                secondUnit.contains("minute") || secondUnit.contains("minutes") || secondUnit.contains("min") -> calendar.add(Calendar.MINUTE, secondValue)
            }
        } else if (timeMatch != null) {
            val hour = timeMatch.groupValues[1].toInt()
            val minutes = timeMatch.groupValues[3].toIntOrNull() ?: 0
            val amPm = timeMatch.groupValues[4].uppercase(Locale.ROOT)

            val hour24 = when {
                amPm == "PM" && hour != 12 -> hour + 12
                amPm == "AM" && hour == 12 -> 0
                else -> hour
            }

            calendar.set(Calendar.HOUR_OF_DAY, hour24)
            calendar.set(Calendar.MINUTE, minutes)
        }

        val repeatDays = mutableListOf<Int>()

        when {
            dayMatch == "today" -> { /* No change needed */ }
            dayMatch == "tomorrow" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            dayMatch == "next week" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 7)
            }
            dayMatch == "next weekend" -> {
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysUntilSaturday = (Calendar.SATURDAY - currentDayOfWeek + 7) % 7
                calendar.add(Calendar.DAY_OF_MONTH, daysUntilSaturday)
            }
            dayMatch != null -> {
                val dayMap = mapOf(
                    "sunday" to Calendar.SUNDAY,
                    "monday" to Calendar.MONDAY,
                    "tuesday" to Calendar.TUESDAY,
                    "wednesday" to Calendar.WEDNESDAY,
                    "thursday" to Calendar.THURSDAY,
                    "friday" to Calendar.FRIDAY,
                    "saturday" to Calendar.SATURDAY
                )

                dayMap.entries.forEach { (dayName, dayConstant) ->
                    if (dayMatch.contains(dayName)) {
                        repeatDays.add(dayConstant)
                    }
                }

                if (repeatDays.isEmpty()) {
                    val targetDay = dayMap.entries.firstOrNull { dayMatch.contains(it.key) }?.value
                    if (targetDay != null) {
                        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                        val daysToAdd = (targetDay - currentDayOfWeek + 7) % 7
                        calendar.add(Calendar.DAY_OF_MONTH, if (daysToAdd == 0) 7 else daysToAdd)
                    }
                }
            }
        }

        return Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_HOUR, calendar.get(Calendar.HOUR_OF_DAY))
            putExtra(AlarmClock.EXTRA_MINUTES, calendar.get(Calendar.MINUTE))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)

            if (repeatDays.isNotEmpty()) {
                putExtra(AlarmClock.EXTRA_DAYS, ArrayList(repeatDays))
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        private val PunctuationRegex = "[.?!]+".toRegex()
        private val DayRegex = ("(?i)\\b(today|tomorrow|next week|next weekend|" +
            "((Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)" +
            "( morning| evening| night| afternoon)?))\\b").toRegex(RegexOption.IGNORE_CASE)
        private val TimeRegex = "(?i)\\b(\\d{1,2})(:(\\d{2}))?\\s*(AM|PM)?\\b".toRegex(RegexOption.IGNORE_CASE)
        private val RelativeTimeRegex = ("(?i)\\b(\\d+)?\\s*(?:and\\s*(half))?\\s*" +
            "(hours?|hrs?|mins?|minutes?|seconds?|secs?)\\s*(?:and\\s*(\\d+))?\\s*" +
            "(mins?|minutes?|secs?|seconds?)?\\s*(from now|later|next)?\\b").toRegex(RegexOption.IGNORE_CASE)
    }
}
