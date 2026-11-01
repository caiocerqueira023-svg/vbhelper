package com.tamerslab.domain.identity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tamerslab.domain.device_data.DigimonIndividual

/** Permanent source baseline; consuming a watch-transfer token must not delete it. */
@Entity(indices = [Index(value = ["individualId", "capturedAt"])], foreignKeys = [
    ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"], childColumns = ["individualId"],
        onDelete = ForeignKey.CASCADE)
])
data class WatchRepresentationSnapshot(
    @PrimaryKey val transferToken: String,
    val individualId: String,
    val sourceCharacterId: Long,
    val deviceType: Int,
    val dimId: Int,
    val capturedAt: Long,
    @ColumnInfo(defaultValue = "1") val version: Int = 1,
    val stateJson: String,
    @ColumnInfo(defaultValue = "NULL") val nativeJson: String? = null,
)
