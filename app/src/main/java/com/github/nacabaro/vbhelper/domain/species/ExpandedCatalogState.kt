package com.github.nacabaro.vbhelper.domain.species

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.characters.Sprite
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

@Entity
data class AppSourceState(@PrimaryKey val id: Int = 1, val mode: String)

@Entity(foreignKeys = [
    ForeignKey(entity = CardCharacter::class, parentColumns = ["id"], childColumns = ["cardCharacterId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Sprite::class, parentColumns = ["id"], childColumns = ["spriteId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("spriteId")])
data class SpeciesDefaultSprite(@PrimaryKey val cardCharacterId: Long, val spriteId: Int)

/** App training is permanent-individual state and survives export/re-import of a watch representation. */
@Entity(foreignKeys = [ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"],
    childColumns = ["individualId"], onDelete = ForeignKey.CASCADE)])
data class AppPartnerProgress(@PrimaryKey val individualId: String, val trainingHp: Int = 0,
    val trainingAp: Int = 0, val trainingBp: Int = 0,
    @ColumnInfo(defaultValue = "1") val generation: Int = 1,
    @ColumnInfo(defaultValue = "0") val totalTrophies: Int = 0,
    @ColumnInfo(defaultValue = "0") val rank: Int = 0,
    @ColumnInfo(defaultValue = "6000") val remainingTrainingTimeInMinutes: Int = 6000,
    @ColumnInfo(defaultValue = "0") val itemType: Int = 0,
    @ColumnInfo(defaultValue = "0") val itemMultiplier: Int = 0,
    @ColumnInfo(defaultValue = "0") val itemRemainingTime: Int = 0,
    @ColumnInfo(defaultValue = "0") val mentalEffectValue: Int = 0,
    @ColumnInfo(defaultValue = "0") val mentalEffectMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val activityEffectValue: Int = 0,
    @ColumnInfo(defaultValue = "0") val activityEffectMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val vitalEffectValue: Int = 0,
    @ColumnInfo(defaultValue = "0") val vitalEffectMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val abilityType: Int = 0,
    @ColumnInfo(defaultValue = "0") val abilityRarity: Int = 0,
    @ColumnInfo(defaultValue = "0") val abilityBranch: Int = 0,
    @ColumnInfo(defaultValue = "0") val abilityReset: Int = 0,
    @ColumnInfo(defaultValue = "10000") val originBasisPoints: Int = 10000)

@Entity
data class FirstPartnerEncounter(@PrimaryKey val id: Int = 1, val individualId: String,
    val speciesId: String, val step: Int = 0, val completedAt: Long? = null)
