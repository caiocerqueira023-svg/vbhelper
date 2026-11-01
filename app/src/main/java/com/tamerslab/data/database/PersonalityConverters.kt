package com.tamerslab.data.database

import androidx.room.TypeConverter
import com.tamerslab.domain.personality.DigimonPersonalityType

class PersonalityConverters {
    @TypeConverter
    fun personalityTypeToString(value: DigimonPersonalityType): String = value.name

    @TypeConverter
    fun stringToPersonalityType(value: String): DigimonPersonalityType =
        runCatching { DigimonPersonalityType.valueOf(value) }
            .getOrDefault(DigimonPersonalityType.FRIENDLY)
}
