package com.github.nacabaro.vbhelper.database

import androidx.room.TypeConverter
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType

class PersonalityConverters {
    @TypeConverter
    fun personalityTypeToString(value: DigimonPersonalityType): String = value.name

    @TypeConverter
    fun stringToPersonalityType(value: String): DigimonPersonalityType =
        runCatching { DigimonPersonalityType.valueOf(value) }
            .getOrDefault(DigimonPersonalityType.FRIENDLY)
}
