package com.tamerslab.transfer.data.gameSave

import com.tamerslab.gamesave.NativeStatParameters
import com.tamerslab.gamesave.NdsStoryProfiles
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.WorldDsStatConversion

/**
 * Authored, versioned VBHelper <-> Dawn/Dusk stat policy.
 *
 * The bands mirror the World DS policy because both titles share the same engine ranges (level
 * 1..99, HP up to ~999999, MP up to 9999, combat stats to 999, aptitude/max-level 0..99), but the
 * policy id is separate and the EXP channel count differs (Dawn/Dusk store nine 32-bit channels).
 * Only fields with an established meaning are projected; the extra native channels stay in the
 * archived record and are never interpreted as app progress.
 */
object Ds2StatConversion {
    const val VERSION = 1
    const val POLICY = "ds2-stat-v1"
    const val EXP_CHANNELS = 9

    data class NativeStats(
        val level: Int,
        val maxLevel: Int,
        val hp: Int,
        val maxHp: Int,
        val mp: Int,
        val maxMp: Int,
        val attack: Int,
        val defense: Int,
        val spirit: Int,
        val speed: Int,
        val exp: List<Long>,
    ) {
        init {
            require(level in 1..99)
            require(maxLevel in 0..99)
            require(maxHp >= 1 && hp in 0..maxHp)
            require(maxMp >= 0 && mp in 0..maxMp)
            require(exp.size == EXP_CHANNELS)
        }
    }

    fun nativeStats(record: ObservedDigimon): NativeStats? {
        val parameters: NativeStatParameters = record.nativeParameters ?: return null
        if (parameters.expCounters.size != EXP_CHANNELS) return null
        return runCatching {
            NativeStats(record.level, parameters.aptitude, parameters.hp, parameters.maxHp,
                parameters.mp, parameters.maxMp, parameters.attack, parameters.defense,
                parameters.spirit, parameters.speed, parameters.expCounters)
        }.getOrNull()
    }

    /** Level and aptitude are direct native fields; HP/ATK/DEF map to the app training bands. */
    fun toApp(native: NativeStats, app: WorldDsStatConversion.AppStats): WorldDsStatConversion.ConvertedAppStats {
        val hp = unscale(native.maxHp, DS_HP_MIN, DS_HP_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
        val bp = unscale(native.defense, DS_COMBAT_MIN, DS_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
        val ap = unscale(native.attack, DS_COMBAT_MIN, DS_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
        val spiritBase = ((hp * 20 + bp * 30 + ap * 50) / 100).coerceIn(1, DS_SPIRIT_MAX)
        val spirit = unscale(native.spirit, DS_COMBAT_MIN, DS_COMBAT_MAX, 1, DS_SPIRIT_MAX)
        val mood = if (spiritBase <= 0) 50 else
            (((spirit.toLong() * 100 / spiritBase) - 75) * 2).toInt().coerceIn(VB_MOOD_MIN, VB_MOOD_MAX)
        return WorldDsStatConversion.ConvertedAppStats(
            trainingHp = (hp - app.baseHp).coerceIn(0, VB_TRAINING_MAX),
            trainingBp = (bp - app.baseBp).coerceIn(0, VB_TRAINING_MAX),
            trainingAp = (ap - app.baseAp).coerceIn(0, VB_TRAINING_MAX),
            vitalPoints = vitalPointsForLevel(native.level),
            mood = mood,
            // Dawn/Dusk EXP channel meanings beyond the training counters are not established, so
            // no app counter is seeded from them.
            trophies = 0,
            wins = 0,
            losses = 0,
            activityLevel = 0,
            derivedMp = unscale(native.maxMp, DS_MP_MIN, DS_MP_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX),
            derivedSpeed = unscale(native.speed, DS_COMBAT_MIN, DS_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX),
            aptitude = native.maxLevel,
        )
    }

    /** Only direct app fields receive native deltas; composite DS-only values stay archived. */
    fun applyDelta(previous: NativeStats, current: NativeStats, app: WorldDsStatConversion.AppStats):
        WorldDsStatConversion.AppStats = app.copy(
        vitalPoints = (app.vitalPoints + vitalPointsForLevel(current.level) - vitalPointsForLevel(previous.level))
            .coerceIn(VB_VITAL_MIN, VB_VITAL_MAX),
        trainingHp = (app.trainingHp + hpValue(current) - hpValue(previous)).coerceIn(0, VB_TRAINING_MAX),
        trainingBp = (app.trainingBp + bpValue(current) - bpValue(previous)).coerceIn(0, VB_TRAINING_MAX),
        trainingAp = (app.trainingAp + apValue(current) - apValue(previous)).coerceIn(0, VB_TRAINING_MAX),
    )

    /** Values the record synthesis writes into the native windows. */
    data class NativeProjection(
        val level: Int,
        val hp: Int,
        val maxHp: Int,
        val mp: Int,
        val maxMp: Int,
        val attack: Int,
        val defense: Int,
        val spirit: Int,
        val speed: Int,
        val maxLevel: Int,
    )

    /**
     * Forward projection used only when a partner has no exact retained record: the app's balanced
     * training state is scaled into the same bands the game itself creates records with, and the
     * level is capped by the species' max level. Nine native counters stay zero, exactly as the
     * engine's creation path leaves them.
     */
    fun toNative(app: WorldDsStatConversion.AppStats, maxLevel: Int): NativeProjection {
        val hp = effective(app.baseHp, app.trainingHp)
        val bp = effective(app.baseBp, app.trainingBp)
        val ap = effective(app.baseAp, app.trainingAp)
        val mood = app.mood.coerceIn(VB_MOOD_MIN, VB_MOOD_MAX)
        val energy = weighted(hp, bp, ap, 30, 45, 25)
        val spiritBase = weighted(hp, bp, ap, 20, 30, 50)
        val spirit = (spiritBase * (75 + mood * 50 / 100) + 50) / 100
        val speed = weighted(hp, bp, ap, 20, 10, 60)
        val maxHp = scale(hp, VB_HEALTH_MIN, VB_HEALTH_MAX, DS_HP_MIN, DS_HP_MAX)
        val maxMp = scale(energy, VB_HEALTH_MIN, VB_HEALTH_MAX, DS_MP_MIN, DS_MP_MAX)
        val cappedMaxLevel = maxLevel.coerceIn(1, 99)
        return NativeProjection(
            level = levelForVitalPoints(app.vitalPoints).coerceIn(1, cappedMaxLevel),
            hp = maxHp,
            maxHp = maxHp,
            mp = maxMp,
            maxMp = maxMp,
            attack = scale(ap, VB_HEALTH_MIN, VB_HEALTH_MAX, DS_COMBAT_MIN, DS_COMBAT_MAX),
            defense = scale(bp, VB_HEALTH_MIN, VB_HEALTH_MAX, DS_COMBAT_MIN, DS_COMBAT_MAX),
            spirit = scale(spirit, 1, DS_SPIRIT_MAX, DS_COMBAT_MIN, DS_COMBAT_MAX),
            speed = scale(speed, VB_HEALTH_MIN, VB_HEALTH_MAX, DS_COMBAT_MIN, DS_COMBAT_MAX),
            maxLevel = cappedMaxLevel,
        )
    }

    private fun effective(base: Int, training: Int): Int =
        (base.coerceAtLeast(0) + training.coerceIn(0, VB_TRAINING_MAX)).coerceIn(VB_HEALTH_MIN, VB_HEALTH_MAX)

    private fun weighted(hp: Int, bp: Int, ap: Int, hpWeight: Int, bpWeight: Int, apWeight: Int): Int {
        val total = hpWeight + bpWeight + apWeight
        return ((hp * hpWeight + bp * bpWeight + ap * apWeight + total / 2) / total).coerceIn(1, DS_SPIRIT_MAX)
    }
    fun hpValue(native: NativeStats): Int = unscale(native.maxHp, DS_HP_MIN, DS_HP_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
    fun bpValue(native: NativeStats): Int = unscale(native.defense, DS_COMBAT_MIN, DS_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
    fun apValue(native: NativeStats): Int = unscale(native.attack, DS_COMBAT_MIN, DS_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)

    fun vitalPointsForLevel(level: Int): Int =
        (((level.coerceIn(1, 99) - 1).toLong() * (VB_VITAL_MAX - VB_VITAL_MIN) + 98 / 2) / 98).toInt()

    fun levelForVitalPoints(vitalPoints: Int): Int =
        (1 + ((vitalPoints.coerceIn(VB_VITAL_MIN, VB_VITAL_MAX).toLong() * 98 + (VB_VITAL_MAX - VB_VITAL_MIN) / 2) /
            (VB_VITAL_MAX - VB_VITAL_MIN))).toInt()

    /** The registered Dawn/Dusk profile this policy belongs to. */
    val profile get() = NdsStoryProfiles.DAWN_DUSK

    private const val VB_HEALTH_MIN = 1
    private const val VB_HEALTH_MAX = 1000
    private const val VB_VITAL_MIN = 0
    private const val VB_VITAL_MAX = 9999
    private const val VB_MOOD_MIN = 0
    private const val VB_MOOD_MAX = 100
    private const val VB_TRAINING_MAX = 999
    private const val DS_HP_MIN = 10
    private const val DS_HP_MAX = 999999
    private const val DS_MP_MIN = 5
    private const val DS_MP_MAX = 9999
    private const val DS_COMBAT_MIN = 5
    private const val DS_COMBAT_MAX = 999
    private const val DS_SPIRIT_MAX = 1250

    private fun scale(value: Int, sourceMin: Int, sourceMax: Int, targetMin: Int, targetMax: Int): Int {
        val source = value.coerceIn(sourceMin, sourceMax).toLong()
        val sourceRange = (sourceMax - sourceMin).toLong()
        val numerator = (source - sourceMin) * (targetMax - targetMin)
        return (targetMin + (numerator + sourceRange / 2) / sourceRange).toInt()
    }

    private fun unscale(value: Int, sourceMin: Int, sourceMax: Int, targetMin: Int, targetMax: Int): Int =
        scale(value, sourceMin, sourceMax, targetMin, targetMax)
}
