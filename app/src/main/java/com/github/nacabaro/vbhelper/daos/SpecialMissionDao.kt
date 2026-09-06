package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Query
import com.github.nacabaro.vbhelper.domain.device_data.SpecialMissions
import kotlinx.coroutines.flow.Flow

@Dao
interface SpecialMissionDao {
    @Query("""
        UPDATE SpecialMissions SET 
            goal = 0,
            progress = 0,
            missionType = "NONE",
            status = "UNAVAILABLE",
            timeElapsedInMinutes = 0,
            timeLimitInMinutes = 0
        WHERE id = :id
    """)
    suspend fun clearSpecialMission(id: Long)

    @Query("""
        SELECT *
        FROM SpecialMissions
        WHERE id = :id
    """)
    fun getSpecialMission(id: Long): Flow<SpecialMissions>
}
