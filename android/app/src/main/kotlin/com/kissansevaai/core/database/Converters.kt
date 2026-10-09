package com.kissansevaai.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
