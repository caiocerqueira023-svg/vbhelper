package com.github.nacabaro.vbhelper.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SpeciesProfileConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromSpecialMoves(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toSpecialMoves(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return gson.fromJson(value, object : TypeToken<List<String>>() {}.type) ?: emptyList()
    }
}
