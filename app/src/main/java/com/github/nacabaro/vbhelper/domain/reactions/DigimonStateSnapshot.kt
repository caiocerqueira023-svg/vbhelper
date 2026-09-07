package com.github.nacabaro.vbhelper.domain.reactions

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = DigimonIndividual::class,
            parentColumns = ["individualId"],
            childColumns = ["individualId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DigimonStateSnapshot(
    @PrimaryKey val individualId: String,
    val stage: Int,
    val mood: Int,
    val vitalPoints: Int,
    val trophies: Int,
    val totalBattlesWon: Int,
    val totalBattlesLost: Int,
    val injuryStatus: String,
    val specialMissionsJson: String,
    val capturedAt: Long
)

data class SnapshotMission(
    val watchId: Int,
    val missionType: String,
    val progress: Int,
    val goal: Int
)
