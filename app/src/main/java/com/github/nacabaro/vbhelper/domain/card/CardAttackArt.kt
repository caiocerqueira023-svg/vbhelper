package com.github.nacabaro.vbhelper.domain.card

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Attack assignments from the imported file, scoped to the local card species. */
@Entity(foreignKeys = [ForeignKey(
    entity = CardCharacter::class,
    parentColumns = ["id"],
    childColumns = ["cardCharacterId"],
    onDelete = ForeignKey.CASCADE
)])
data class CardAttackArt(
    @PrimaryKey val cardCharacterId: Long,
    val smallAttackId: Int,
    val largeAttackId: Int,
    val smallPixels: ByteArray? = null,
    val smallWidth: Int? = null,
    val smallHeight: Int? = null,
    val largePixels: ByteArray? = null,
    val largeWidth: Int? = null,
    val largeHeight: Int? = null
) {
    override fun equals(other: Any?): Boolean = other is CardAttackArt &&
        cardCharacterId == other.cardCharacterId && smallAttackId == other.smallAttackId && largeAttackId == other.largeAttackId &&
        smallPixels.contentEquals(other.smallPixels) && smallWidth == other.smallWidth && smallHeight == other.smallHeight &&
        largePixels.contentEquals(other.largePixels) && largeWidth == other.largeWidth && largeHeight == other.largeHeight

    override fun hashCode(): Int {
        var result = cardCharacterId.hashCode()
        result = 31 * result + smallAttackId
        result = 31 * result + largeAttackId
        result = 31 * result + smallPixels.contentHashCode()
        result = 31 * result + (smallWidth ?: 0)
        result = 31 * result + (smallHeight ?: 0)
        result = 31 * result + largePixels.contentHashCode()
        result = 31 * result + (largeWidth ?: 0)
        result = 31 * result + (largeHeight ?: 0)
        return result
    }
}
