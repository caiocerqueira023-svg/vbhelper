package com.github.nacabaro.vbhelper.database

import androidx.room.TypeConverter
import com.github.nacabaro.vbhelper.domain.personality.SocialStyle
import com.github.nacabaro.vbhelper.domain.personality.SpeechQuirk
import com.github.nacabaro.vbhelper.domain.personality.Temperament

class PersonalityConverters {
    @TypeConverter
    fun temperamentToString(value: Temperament): String = value.name

    @TypeConverter
    fun stringToTemperament(value: String): Temperament =
        runCatching { Temperament.valueOf(value) }.getOrDefault(Temperament.CALM)

    @TypeConverter
    fun socialStyleToString(value: SocialStyle): String = value.name

    @TypeConverter
    fun stringToSocialStyle(value: String): SocialStyle =
        runCatching { SocialStyle.valueOf(value) }.getOrDefault(SocialStyle.LOYAL_WARM)

    @TypeConverter
    fun speechQuirkToString(value: SpeechQuirk): String = value.name

    @TypeConverter
    fun stringToSpeechQuirk(value: String): SpeechQuirk =
        runCatching { SpeechQuirk.valueOf(value) }.getOrDefault(SpeechQuirk.SHORT_DIRECT)
}
