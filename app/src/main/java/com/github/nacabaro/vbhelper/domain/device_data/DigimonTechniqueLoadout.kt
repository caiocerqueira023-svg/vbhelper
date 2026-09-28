package com.github.nacabaro.vbhelper.domain.device_data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Three regular battle techniques selected for one permanent Digimon identity. */
@Entity(
    tableName = "DigimonTechniqueLoadout",
    primaryKeys = ["individualId", "slot"],
    foreignKeys = [
        ForeignKey(
            entity = DigimonIndividual::class,
            parentColumns = ["individualId"],
            childColumns = ["individualId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("individualId")]
)
data class DigimonTechniqueLoadout(
    val individualId: String,
    val slot: Int,
    val techniqueId: String
)
