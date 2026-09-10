package com.github.nacabaro.vbhelper.database

import androidx.room.TypeConverter
import com.github.nacabaro.vbhelper.domain.lorebook.LorebookEntrySource
import com.github.nacabaro.vbhelper.domain.world.RecruitmentState
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

    @TypeConverter
    fun fromLorebookSource(value: LorebookEntrySource): String = value.name

    @TypeConverter
    fun toLorebookSource(value: String?): LorebookEntrySource =
        runCatching { LorebookEntrySource.valueOf(value.orEmpty()) }
            .getOrDefault(LorebookEntrySource.CUSTOM)

    @TypeConverter
    fun fromRecruitmentState(value: RecruitmentState): String = value.name

    @TypeConverter
    fun toRecruitmentState(value: String?): RecruitmentState =
        runCatching { RecruitmentState.valueOf(value.orEmpty()) }
            .getOrDefault(RecruitmentState.WILD)
}
