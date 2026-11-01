package com.tamerslab.domain.card

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.tamerslab.hardware.nfc.data.NfcCharacter
import com.tamerslab.domain.characters.Sprite

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Sprite::class,
            parentColumns = ["id"],
            childColumns = ["spriteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cardId"]),
        Index(value = ["spriteId"]),
        Index(value = ["speciesId"]),
        Index(value = ["catalogSpeciesId"], unique = true)
    ]
)

/*
 * Character represents a character on a card. There should only be one of these per dimId
 * and monIndex.
 * TODO: Customs will mean this should be unique per cardName and monIndex
 */
data class CardCharacter (
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Optional physical-card binding. Catalog definitions never require a Card row. */
    val cardId: Long?,
    val spriteId: Long,
    val charaIndex: Int,
    val stage: Int, // These should be replaced with enums
    val attribute: NfcCharacter.Attribute, // This one too
    val baseHp: Int,
    val baseBp: Int,
    val baseAp: Int,
    val nameSprite: ByteArray,
    val nameWidth: Int,
    val nameHeight: Int,
    @ColumnInfo(defaultValue = "NULL") val speciesId: String? = null,
    /** Set only on the single app-managed definition of a canonical species. */
    @ColumnInfo(defaultValue = "NULL") val catalogSpeciesId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CardCharacter

        if (id != other.id) return false
        if (cardId != other.cardId) return false
        if (spriteId != other.spriteId) return false
        if (charaIndex != other.charaIndex) return false
        if (stage != other.stage) return false
        if (attribute != other.attribute) return false
        if (baseHp != other.baseHp) return false
        if (baseBp != other.baseBp) return false
        if (baseAp != other.baseAp) return false
        if (!nameSprite.contentEquals(other.nameSprite)) return false
        if (nameWidth != other.nameWidth) return false
        if (nameHeight != other.nameHeight) return false
        if (speciesId != other.speciesId || catalogSpeciesId != other.catalogSpeciesId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (cardId?.hashCode() ?: 0)
        result = 31 * result + spriteId.hashCode()
        result = 31 * result + charaIndex
        result = 31 * result + stage
        result = 31 * result + attribute.hashCode()
        result = 31 * result + baseHp
        result = 31 * result + baseBp
        result = 31 * result + baseAp
        result = 31 * result + nameSprite.contentHashCode()
        result = 31 * result + nameWidth
        result = 31 * result + nameHeight
        return result
    }
}
