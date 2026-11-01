package com.tamerslab.domain.species

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tamerslab.data.database.SpritePreferenceSchema
import com.tamerslab.domain.characters.Sprite

/** User-owned reference records; bundled/downloaded catalog files remain source material. */
@Entity(indices = [Index(value = ["name"], unique = true), Index(value = ["originalKey"], unique = true)])
data class SpeciesCatalogEntry(
    @PrimaryKey val speciesKey: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val originalKey: String? = null,
    val level: String? = null,
    val type: String? = null,
    val profile: String? = null,
    val specialMoves: List<String> = emptyList(),
)

@Entity(foreignKeys = [ForeignKey(entity = Sprite::class, parentColumns = ["id"], childColumns = ["spriteId"],
    onDelete = ForeignKey.SET_NULL)], indices = [Index(value = ["spriteId"])])
data class SpeciesSpriteChoice(@PrimaryKey val speciesKey: String, val spriteId: Int?)

/** Presentation-only projection: no original card/sprite assignment is overwritten. */
@DatabaseView(viewName = "DisplaySprite", value = SpritePreferenceSchema.DISPLAY_SPRITE_QUERY)
data class DisplaySprite(
    val cardCharacterId: Long,
    val id: Int,
    val spriteIdle1: ByteArray,
    val spriteIdle2: ByteArray,
    val spriteWalk1: ByteArray,
    val spriteWalk2: ByteArray,
    val spriteRun1: ByteArray,
    val spriteRun2: ByteArray,
    val spriteTrain1: ByteArray,
    val spriteTrain2: ByteArray,
    val spriteHappy: ByteArray,
    val spriteSleep: ByteArray,
    val spriteAttack: ByteArray,
    val spriteDodge: ByteArray,
    val width: Int,
    val height: Int,
)

data class LoadedSpriteChoice(
    val spriteId: Int,
    val cardCharacterId: Long,
    val cardName: String,
    val speciesName: String?,
    val charaIndex: Int,
)
