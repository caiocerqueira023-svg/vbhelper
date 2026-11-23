package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.ProgressSeedSource
import com.tamerslab.domain.transfer.StoryProgressDomain
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.WorldDsStatConversion

/**
 * Maps a story-title native record into the single shared story progress block.
 *
 * The story block keeps only values that every supported story title understands: level, total EXP,
 * HP/MP/ATK/DEF/SPI/SPD and (Lost Evolution only) aptitude. Per-title extras are not collapsed into
 * it; they stay in the archived native record. This is a read-only observation for now — the send path
 * still uses the old conversion until story-authoritative generation is wired.
 */
internal object StoryProgressTracking {
    fun fromWorldDs(native: WorldDsStatConversion.NativeStats): StorySnapshot = StorySnapshot(
        level = native.level,
        expTotal = native.exp.sum(),
        hp = native.hp,
        maxHp = native.maxHp,
        mp = native.mp,
        maxMp = native.maxMp,
        attack = native.attack,
        defense = native.defense,
        spirit = native.spirit,
        speed = native.speed,
        aptitude = null,
    )

    fun fromDs2(native: Ds2StatConversion.NativeStats): StorySnapshot = StorySnapshot(
        level = native.level,
        expTotal = native.exp.sum(),
        hp = native.hp,
        maxHp = native.maxHp,
        mp = native.mp,
        maxMp = native.maxMp,
        attack = native.attack,
        defense = native.defense,
        spirit = native.spirit,
        speed = native.speed,
        aptitude = null,
    )

    fun fromLe(native: LeStatConversion.NativeStats): StorySnapshot = StorySnapshot(
        level = native.level,
        expTotal = native.exp.sum(),
        hp = native.hp,
        maxHp = native.maxHp,
        mp = native.mp,
        maxMp = native.maxMp,
        attack = native.attack,
        defense = native.defense,
        spirit = native.spirit,
        speed = native.speed,
        aptitude = native.aptitude,
    )

    /**
     * The authoritative World DS write: the shared story values are written exactly, with the
     * normalized total EXP spread over the record's channels so the sum is preserved.
     */
    fun toWorldDsNative(story: StoryProgressDomain): WorldDsStatConversion.NativeStats {
        // The shared block may come from a title with different bands; clamp into this record's.
        val maxHp = story.maxHp.coerceIn(10, 999999)
        val maxMp = story.maxMp.coerceIn(0, 9999)
        return WorldDsStatConversion.NativeStats(
            level = story.level.coerceIn(1, 99),
            aptitude = (story.aptitude ?: 99).coerceIn(0, 99),
            hp = story.hp.coerceIn(0, maxHp),
            maxHp = maxHp,
            mp = story.mp.coerceIn(0, maxMp),
            maxMp = maxMp,
            attack = story.attack.coerceAtLeast(0),
            defense = story.defense.coerceAtLeast(0),
            spirit = story.spirit.coerceAtLeast(0),
            speed = story.speed.coerceAtLeast(0),
            exp = spreadExp(story.expTotal, WorldDsStatConversion.EXP_CHANNELS),
        )
    }

    fun toDs2Native(story: StoryProgressDomain, maxLevel: Int = 99): Ds2StatConversion.NativeProjection {
        val maxHp = story.maxHp.coerceIn(1, 999999)
        val maxMp = story.maxMp.coerceIn(0, 9999)
        return Ds2StatConversion.NativeProjection(
            level = story.level.coerceIn(1, 99),
            hp = story.hp.coerceIn(0, maxHp),
            maxHp = maxHp,
            mp = story.mp.coerceIn(0, maxMp),
            maxMp = maxMp,
            attack = story.attack.coerceIn(0, 1250),
            defense = story.defense.coerceIn(0, 1250),
            spirit = story.spirit.coerceIn(0, 1250),
            speed = story.speed.coerceIn(0, 1250),
            maxLevel = maxLevel.coerceIn(0, 99),
        )
    }

    fun toLeNative(story: StoryProgressDomain): LeStatConversion.NativeProjection {
        // Lost Evolution uses tighter bands than the shared block (speed 1..99, combat 1..999).
        val maxHp = story.maxHp.coerceIn(1, 9999)
        val maxMp = story.maxMp.coerceIn(0, 9999)
        return LeStatConversion.NativeProjection(
            level = story.level.coerceIn(1, 99),
            aptitude = (story.aptitude ?: 100).coerceIn(1, 255),
            hp = story.hp.coerceIn(0, maxHp),
            maxHp = maxHp,
            mp = story.mp.coerceIn(0, maxMp),
            maxMp = maxMp,
            attack = story.attack.coerceIn(1, 999),
            defense = story.defense.coerceIn(1, 999),
            spirit = story.spirit.coerceIn(1, 999),
            speed = story.speed.coerceIn(1, 99),
        )
    }

    /** Spreads the normalized total EXP over a title's native channels (sum preserved). */
    fun expChannels(total: Long, channels: Int): List<Long> = spreadExp(total, channels)

    private fun spreadExp(total: Long, channels: Int): List<Long> {
        if (channels <= 0) return emptyList()
        val bounded = total.coerceIn(0L, 99_999L * channels)
        val base = bounded / channels
        val remainder = bounded % channels
        return List(channels) { index -> (base + if (index == 0) remainder else 0L).coerceIn(0L, 99_999L) }
    }

    /** Decodes a retained record from any story layout into the shared snapshot. */
    fun fromRecord(record: ObservedDigimon, gameId: String): StorySnapshot? = when {
        gameId == com.tamerslab.gamesave.NdsStoryProfiles.LOST_EVOLUTION.gameId ->
            LeStatConversion.nativeStats(record)?.let(::fromLe)
        gameId == com.tamerslab.gamesave.NdsStoryProfiles.DAWN_DUSK.gameId ->
            Ds2StatConversion.nativeStats(record)?.let(::fromDs2)
        gameId == com.tamerslab.gamesave.NdsStoryProfiles.WORLD_DS.gameId ->
            WorldDsReceiveConversion.nativeStats(record)?.let(::fromWorldDs)
        else -> null
    }

    /** The story block for one import. Revisions and seed provenance are assigned by the repository. */
    fun toDomain(
        snapshot: StorySnapshot,
        individualId: String,
        recordedAt: Long,
        seedSource: ProgressSeedSource,
    ): StoryProgressDomain = StoryProgressDomain(
        individualId = individualId,
        level = snapshot.level,
        expTotal = snapshot.expTotal,
        hp = snapshot.hp,
        maxHp = snapshot.maxHp,
        mp = snapshot.mp,
        maxMp = snapshot.maxMp,
        attack = snapshot.attack,
        defense = snapshot.defense,
        spirit = snapshot.spirit,
        speed = snapshot.speed,
        aptitude = snapshot.aptitude,
        seededAt = recordedAt,
        seedSource = seedSource,
        revision = 0L,
        updatedAt = recordedAt,
    )
}

/** Shared story-series values, still in native units because every story title uses the same bands. */
internal data class StorySnapshot(
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
    val aptitude: Int?,
)
