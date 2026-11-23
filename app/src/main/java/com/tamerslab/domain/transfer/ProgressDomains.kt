package com.tamerslab.domain.transfer

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.tamerslab.domain.device_data.DigimonIndividual

/** Which side of a transfer first created a progress block. Never re-seeded afterwards. */
enum class ProgressSeedSource { VB, STORY, IMPORT }

/**
 * The single story-series progression shared by every supported story game. One block per individual;
 * story games read it on send and write it back on import. VB transfers never touch this row.
 *
 * Only values that exist in every story title live here. Per-title extras (Lost Evolution aptitude,
 * DS per-channel experience) stay out of this block and are carried per-title.
 */
@Entity(
    tableName = "story_progress",
    foreignKeys = [
        ForeignKey(
            entity = DigimonIndividual::class,
            parentColumns = ["individualId"],
            childColumns = ["individualId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StoryProgressDomain(
    @PrimaryKey val individualId: String,
    val level: Int,
    val expTotal: Long,
    val hp: Int,
    val maxHp: Int,
    val mp: Int,
    val maxMp: Int,
    val attack: Int,
    val defense: Int,
    val spirit: Int,
    val speed: Int,
    /** Lost Evolution-only value; null until that title reports it. */
    val aptitude: Int? = null,
    val seededAt: Long,
    val seedSource: ProgressSeedSource,
    val revision: Long,
    val updatedAt: Long,
)

/**
 * Pure domain rules for the two progress domains. Kept free of Android/Room so the transfer writers
 * and the UI can share the same decisions and so they are unit-testable.
 */
object ProgressDomainRules {
    const val VB_VITAL_MIN = 0
    const val VB_VITAL_MAX = 9999
    const val STORY_LEVEL_MIN = 1
    const val STORY_LEVEL_MAX = 99

    /**
     * The established vital-points → story-level curve. The owner's reference point
     * (Ultimate with 7500 vital points → level 75) is the midpoint of this linear curve.
     */
    fun levelForVitalPoints(vitalPoints: Int): Int =
        (STORY_LEVEL_MIN + ((vitalPoints.coerceIn(VB_VITAL_MIN, VB_VITAL_MAX).toLong() *
            (STORY_LEVEL_MAX - STORY_LEVEL_MIN) + (VB_VITAL_MAX - VB_VITAL_MIN) / 2) /
            (VB_VITAL_MAX - VB_VITAL_MIN))).toInt()

    fun vitalPointsForLevel(level: Int): Int =
        (((level.coerceIn(STORY_LEVEL_MIN, STORY_LEVEL_MAX) - STORY_LEVEL_MIN).toLong() *
            (VB_VITAL_MAX - VB_VITAL_MIN) + (STORY_LEVEL_MAX - STORY_LEVEL_MIN) / 2) /
            (STORY_LEVEL_MAX - STORY_LEVEL_MIN)).toInt()

    /** Story progress is seeded exactly once: only when no block exists yet. */
    fun needsStorySeed(existing: StoryProgressDomain?): Boolean = existing == null

    /**
     * A story send may only rewrite progression the destination can represent; the shared block is
     * clamped to the range every story title accepts.
     */
    fun clampStory(source: StoryProgressDomain): StoryProgressDomain = source.copy(
        level = source.level.coerceIn(STORY_LEVEL_MIN, STORY_LEVEL_MAX),
        expTotal = source.expTotal.coerceAtLeast(0L),
        hp = source.hp.coerceIn(0, source.maxHp.coerceAtLeast(0)),
        maxHp = source.maxHp.coerceAtLeast(1),
        mp = source.mp.coerceIn(0, source.maxMp.coerceAtLeast(0)),
        maxMp = source.maxMp.coerceAtLeast(0),
        attack = source.attack.coerceAtLeast(0),
        defense = source.defense.coerceAtLeast(0),
        spirit = source.spirit.coerceAtLeast(0),
        speed = source.speed.coerceAtLeast(0),
        aptitude = source.aptitude?.coerceIn(0, 99),
    )
}
