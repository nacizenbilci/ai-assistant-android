package com.app.assistant.db

import androidx.room.TypeConverter
import java.net.URI

class Converters {
    @TypeConverter
    fun fromString(value: String?): URI? {
        return value?.let { URI(it) }
    }

    @TypeConverter
    fun uriToString(uri: URI?): String? {
        return uri?.toString()
    }
}
