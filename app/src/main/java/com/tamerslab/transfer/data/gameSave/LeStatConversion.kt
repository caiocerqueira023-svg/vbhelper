package com.tamerslab.transfer.data.gameSave

import com.tamerslab.gamesave.LostEvolution
import com.tamerslab.gamesave.NativeStatParameters
import com.tamerslab.gamesave.NdsStoryProfiles
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.WorldDsStatConversion

/**
 * Authored, versioned VBHelper <-> Lost Evolution stat policy.
 *
 * Bands come from the engine's own record creation code (ARM9 `FUN_020b0100`): level 1..99, HP
 * capped at 9999, MP 0..9999, attack/defense/spirit capped at 999, speed 1..99. Loyalty/aptitude
 * bytes are carried through untouched; the single 32-bit EXP word is archived and never collapsed
 * into a composite app value.
 */
object LeStatConversion {
    const val VERSION = 1
    const val POLICY = "le-stat-v1"
    const val EXP_CHANNELS = 1

    data class NativeStats(
        val level: Int,
        val aptitude: Int,
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

    /** Level is a direct native field; HP/ATK/DEF map to the app training bands. */
    fun toApp(native: NativeStats, app: WorldDsStatConversion.AppStats): WorldDsStatConversion.ConvertedAppStats {
        val hp = unscale(native.maxHp, LE_HP_MIN, LE_HP_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
        val bp = unscale(native.defense, LE_COMBAT_MIN, LE_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
        val ap = unscale(native.attack, LE_COMBAT_MIN, LE_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
        val spiritBase = ((hp * 20 + bp * 30 + ap * 50) / 100).coerceIn(1, LE_SPIRIT_MAX)
        val spirit = unscale(native.spirit, LE_COMBAT_MIN, LE_COMBAT_MAX, 1, LE_SPIRIT_MAX)
        val mood = if (spiritBase <= 0) 50 else
            (((spirit.toLong() * 100 / spiritBase) - 75) * 2).toInt().coerceIn(VB_MOOD_MIN, VB_MOOD_MAX)
        return WorldDsStatConversion.ConvertedAppStats(
            trainingHp = (hp - app.baseHp).coerceIn(0, VB_TRAINING_MAX),
            trainingBp = (bp - app.baseBp).coerceIn(0, VB_TRAINING_MAX),
            trainingAp = (ap - app.baseAp).coerceIn(0, VB_TRAINING_MAX),
            vitalPoints = vitalPointsForLevel(native.level),
            mood = mood,
            trophies = 0,
            wins = 0,
            losses = 0,
            activityLevel = 0,
            derivedMp = unscale(native.maxMp, LE_MP_MIN, LE_MP_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX),
            derivedSpeed = unscale(native.speed, LE_SPEED_MIN, LE_SPEED_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX),
            aptitude = native.aptitude,
        )
    }

    /** Only direct app fields receive native deltas; composite/native-only values stay archived. */
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
        val aptitude: Int,
    )

    /** Forward projection used when a partner has no exact retained Lost Evolution record. */
    fun toNative(app: WorldDsStatConversion.AppStats, aptitude: Int): NativeProjection {
        val hp = effective(app.baseHp, app.trainingHp)
        val bp = effective(app.baseBp, app.trainingBp)
        val ap = effective(app.baseAp, app.trainingAp)
        val mood = app.mood.coerceIn(VB_MOOD_MIN, VB_MOOD_MAX)
        val energy = weighted(hp, bp, ap, 30, 45, 25)
        val spiritBase = weighted(hp, bp, ap, 20, 30, 50)
        val spirit = (spiritBase * (75 + mood * 50 / 100) + 50) / 100
        val speed = weighted(hp, bp, ap, 20, 10, 60)
        val maxHp = scale(hp, VB_HEALTH_MIN, VB_HEALTH_MAX, LE_HP_MIN, LE_HP_MAX)
        val maxMp = scale(energy, VB_HEALTH_MIN, VB_HEALTH_MAX, LE_MP_MIN, LE_MP_MAX)
        return NativeProjection(
            level = levelForVitalPoints(app.vitalPoints).coerceIn(1, LostEvolution.MAX_LEVEL),
            hp = maxHp,
            maxHp = maxHp,
            mp = maxMp,
            maxMp = maxMp,
            attack = scale(ap, VB_HEALTH_MIN, VB_HEALTH_MAX, LE_COMBAT_MIN, LE_COMBAT_MAX),
            defense = scale(bp, VB_HEALTH_MIN, VB_HEALTH_MAX, LE_COMBAT_MIN, LE_COMBAT_MAX),
            spirit = scale(spirit, 1, LE_SPIRIT_MAX, LE_COMBAT_MIN, LE_COMBAT_MAX),
            speed = scale(speed, VB_HEALTH_MIN, VB_HEALTH_MAX, LE_SPEED_MIN, LE_SPEED_MAX),
            aptitude = aptitude.coerceIn(1, 100),
        )
    }

    private fun effective(base: Int, training: Int): Int =
        (base.coerceAtLeast(0) + training.coerceIn(0, VB_TRAINING_MAX)).coerceIn(VB_HEALTH_MIN, VB_HEALTH_MAX)

    private fun weighted(hp: Int, bp: Int, ap: Int, hpWeight: Int, bpWeight: Int, apWeight: Int): Int {
        val total = hpWeight + bpWeight + apWeight
        return ((hp * hpWeight + bp * bpWeight + ap * apWeight + total / 2) / total).coerceIn(1, LE_SPIRIT_MAX)
    }

    fun hpValue(native: NativeStats): Int = unscale(native.maxHp, LE_HP_MIN, LE_HP_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
    fun bpValue(native: NativeStats): Int = unscale(native.defense, LE_COMBAT_MIN, LE_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
    fun apValue(native: NativeStats): Int = unscale(native.attack, LE_COMBAT_MIN, LE_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)

    fun vitalPointsForLevel(level: Int): Int =
        (((level.coerceIn(1, 99) - 1).toLong() * (VB_VITAL_MAX - VB_VITAL_MIN) + 98 / 2) / 98).toInt()

    fun levelForVitalPoints(vitalPoints: Int): Int =
        (1 + ((vitalPoints.coerceIn(VB_VITAL_MIN, VB_VITAL_MAX).toLong() * 98 + (VB_VITAL_MAX - VB_VITAL_MIN) / 2) /
            (VB_VITAL_MAX - VB_VITAL_MIN))).toInt()

    /** The registered Lost Evolution profile this policy belongs to. */
    val profile get() = NdsStoryProfiles.LOST_EVOLUTION

    private const val VB_HEALTH_MIN = 1
    private const val VB_HEALTH_MAX = 1000
    private const val VB_VITAL_MIN = 0
    private const val VB_VITAL_MAX = 9999
    private const val VB_MOOD_MIN = 0
    private const val VB_MOOD_MAX = 100
    private const val VB_TRAINING_MAX = 999
    private const val LE_HP_MIN = 10
    private const val LE_HP_MAX = 9999
    private const val LE_MP_MIN = 0
    private const val LE_MP_MAX = 9999
    private const val LE_COMBAT_MIN = 1
    private const val LE_COMBAT_MAX = 999
    private const val LE_SPEED_MIN = 1
    private const val LE_SPEED_MAX = 99
    private const val LE_SPIRIT_MAX = 999

    private fun scale(value: Int, sourceMin: Int, sourceMax: Int, targetMin: Int, targetMax: Int): Int {
        val source = value.coerceIn(sourceMin, sourceMax).toLong()
        val sourceRange = (sourceMax - sourceMin).toLong()
        val numerator = (source - sourceMin) * (targetMax - targetMin)
        return (targetMin + (numerator + sourceRange / 2) / sourceRange).toInt()
    }

    private fun unscale(value: Int, sourceMin: Int, sourceMax: Int, targetMin: Int, targetMax: Int): Int =
        scale(value, sourceMin, sourceMax, targetMin, targetMax)
}
