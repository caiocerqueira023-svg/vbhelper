package com.tamerslab.gamesave

/**
 * Authored, versioned Tamers Lab <-> Digimon World DS stat policy. The exact values on both sides are
 * persisted separately; this policy only supplies the projection between them.
 */
object WorldDsStatConversion {
    const val VERSION = 1
    const val POLICY = "world-ds-stat-v1"
    const val EXP_CHANNELS = 8

    const val VB_HEALTH_MIN = 1
    const val VB_HEALTH_MAX = 1000
    const val VB_VITAL_MIN = 0
    const val VB_VITAL_MAX = 9999
    const val VB_MOOD_MIN = 0
    const val VB_MOOD_MAX = 100
    const val VB_TRAINING_MAX = 999

    const val DS_LEVEL_MIN = 1
    const val DS_LEVEL_MAX = 99
    const val DS_APTITUDE_MIN = 0
    const val DS_APTITUDE_MAX = 99
    const val DS_HP_MIN = 10
    const val DS_HP_MAX = 999999
    const val DS_MP_MIN = 5
    const val DS_MP_MAX = 9999
    const val DS_COMBAT_MIN = 5
    const val DS_COMBAT_MAX = 999
    const val DS_SPIRIT_MAX = 1250
    const val DS_EXP_MIN = 0L
    const val DS_EXP_MAX = 99999L

    data class AppStats(
        val stage: Int,
        val baseHp: Int,
        val baseBp: Int,
        val baseAp: Int,
        val trainingHp: Int = 0,
        val trainingBp: Int = 0,
        val trainingAp: Int = 0,
        val vitalPoints: Int = 0,
        val mood: Int = 50,
        val trophies: Int = 0,
        val wins: Int = 0,
        val losses: Int = 0,
        val ageInDays: Int = 0,
        val activityLevel: Int = 0,
        val generation: Int = 1,
        val rank: Int = 0,
    ) {
        init {
            require(stage in 0..5)
            require(baseHp >= 0 && baseBp >= 0 && baseAp >= 0)
        }
    }

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
            require(level in DS_LEVEL_MIN..DS_LEVEL_MAX)
            require(aptitude in DS_APTITUDE_MIN..DS_APTITUDE_MAX)
            require(maxHp in DS_HP_MIN..DS_HP_MAX && hp in 0..maxHp)
            require(maxMp in 0..DS_MP_MAX && mp in 0..maxMp)
            require(listOf(attack, defense, spirit, speed).all { it >= 0 })
            require(exp.size == EXP_CHANNELS && exp.all { it in DS_EXP_MIN..DS_EXP_MAX })
        }
    }

    data class ConvertedAppStats(
        val trainingHp: Int,
        val trainingBp: Int,
        val trainingAp: Int,
        val vitalPoints: Int,
        val mood: Int,
        val trophies: Int,
        val wins: Int,
        val losses: Int,
        val activityLevel: Int,
        val derivedMp: Int,
        val derivedSpeed: Int,
        val aptitude: Int,
    )

    fun toNative(app: AppStats): NativeStats {
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
        return NativeStats(
            level = levelForVitalPoints(app.vitalPoints),
            aptitude = aptitude(app),
            hp = maxHp,
            maxHp = maxHp,
            mp = maxMp,
            maxMp = maxMp,
            attack = scale(ap, VB_HEALTH_MIN, VB_HEALTH_MAX, DS_COMBAT_MIN, DS_COMBAT_MAX),
            defense = scale(bp, VB_HEALTH_MIN, VB_HEALTH_MAX, DS_COMBAT_MIN, DS_COMBAT_MAX),
            spirit = scale(spirit, 1, DS_SPIRIT_MAX, DS_COMBAT_MIN, DS_COMBAT_MAX),
            speed = scale(speed, VB_HEALTH_MIN, VB_HEALTH_MAX, DS_COMBAT_MIN, DS_COMBAT_MAX),
            exp = listOf(
                bounded(app.trainingHp.toLong()),
                bounded(app.trainingBp.toLong()),
                bounded(app.trainingAp.toLong()),
                bounded(app.vitalPoints.toLong()),
                bounded(app.trophies.toLong()),
                bounded(app.wins.toLong()),
                bounded(app.losses.toLong()),
                bounded(app.activityLevel.toLong()),
            ),
        )
    }

    fun toApp(native: NativeStats, app: AppStats): ConvertedAppStats {
        val hp = unscale(native.maxHp, DS_HP_MIN, DS_HP_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
        val bp = unscale(native.defense, DS_COMBAT_MIN, DS_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
        val ap = unscale(native.attack, DS_COMBAT_MIN, DS_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
        val spiritBase = weighted(hp, bp, ap, 20, 30, 50)
        val spirit = unscale(native.spirit, DS_COMBAT_MIN, DS_COMBAT_MAX, 1, DS_SPIRIT_MAX)
        val mood = if (spiritBase <= 0) 50 else
            (((spirit.toLong() * 100 / spiritBase) - 75) * 2).toInt().coerceIn(VB_MOOD_MIN, VB_MOOD_MAX)
        return ConvertedAppStats(
            trainingHp = (hp - app.baseHp).coerceIn(0, VB_TRAINING_MAX),
            trainingBp = (bp - app.baseBp).coerceIn(0, VB_TRAINING_MAX),
            trainingAp = (ap - app.baseAp).coerceIn(0, VB_TRAINING_MAX),
            vitalPoints = vitalPointsForLevel(native.level),
            mood = mood,
            trophies = native.exp[4].coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            wins = native.exp[5].coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            losses = native.exp[6].coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            activityLevel = native.exp[7].coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            derivedMp = unscale(native.maxMp, DS_MP_MIN, DS_MP_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX),
            derivedSpeed = unscale(native.speed, DS_COMBAT_MIN, DS_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX),
            aptitude = native.aptitude,
        )
    }

    /**
     * Only direct app fields receive native deltas. DS-only composites (MP, spirit, speed, aptitude)
     * stay in the archived native snapshot instead of being written into unrelated app fields.
     */
    fun applyDelta(previous: NativeStats, current: NativeStats, app: AppStats): AppStats = app.copy(
        vitalPoints = (app.vitalPoints + vitalPointsForLevel(current.level) - vitalPointsForLevel(previous.level))
            .coerceIn(VB_VITAL_MIN, VB_VITAL_MAX),
        trainingHp = (app.trainingHp + hpValue(current) - hpValue(previous)).coerceIn(0, VB_TRAINING_MAX),
        trainingBp = (app.trainingBp + bpValue(current) - bpValue(previous)).coerceIn(0, VB_TRAINING_MAX),
        trainingAp = (app.trainingAp + apValue(current) - apValue(previous)).coerceIn(0, VB_TRAINING_MAX),
        trophies = (app.trophies.toLong() + current.exp[4] - previous.exp[4]).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        wins = (app.wins.toLong() + current.exp[5] - previous.exp[5]).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        losses = (app.losses.toLong() + current.exp[6] - previous.exp[6]).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        activityLevel = (app.activityLevel.toLong() + current.exp[7] - previous.exp[7])
            .coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
    )

    fun levelForVitalPoints(vitalPoints: Int): Int =
        (DS_LEVEL_MIN + ((vitalPoints.coerceIn(VB_VITAL_MIN, VB_VITAL_MAX).toLong() * (DS_LEVEL_MAX - DS_LEVEL_MIN) +
            (VB_VITAL_MAX - VB_VITAL_MIN) / 2) / (VB_VITAL_MAX - VB_VITAL_MIN))).toInt()

    fun vitalPointsForLevel(level: Int): Int =
        (((level.coerceIn(DS_LEVEL_MIN, DS_LEVEL_MAX) - DS_LEVEL_MIN).toLong() * (VB_VITAL_MAX - VB_VITAL_MIN) +
            (DS_LEVEL_MAX - DS_LEVEL_MIN) / 2) / (DS_LEVEL_MAX - DS_LEVEL_MIN)).toInt()

    fun hpValue(native: NativeStats): Int = unscale(native.maxHp, DS_HP_MIN, DS_HP_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
    fun bpValue(native: NativeStats): Int = unscale(native.defense, DS_COMBAT_MIN, DS_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)
    fun apValue(native: NativeStats): Int = unscale(native.attack, DS_COMBAT_MIN, DS_COMBAT_MAX, VB_HEALTH_MIN, VB_HEALTH_MAX)

    private fun effective(base: Int, training: Int): Int =
        (base.coerceAtLeast(0) + training.coerceIn(0, VB_TRAINING_MAX)).coerceIn(VB_HEALTH_MIN, VB_HEALTH_MAX)

    private fun weighted(hp: Int, bp: Int, ap: Int, hpWeight: Int, bpWeight: Int, apWeight: Int): Int {
        val total = hpWeight + bpWeight + apWeight
        return ((hp * hpWeight + bp * bpWeight + ap * apWeight + total / 2) / total).coerceIn(1, DS_SPIRIT_MAX)
    }

    private fun aptitude(app: AppStats): Int {
        val experience = app.trophies.toLong() + app.wins + app.losses + app.ageInDays + app.rank * 10L
        val rank = (experience / 25L).coerceAtMost(55L)
        val generation = app.generation.coerceIn(1, 10)
        return (10 + app.stage.coerceIn(0, 5) * 10 + rank + generation).toInt().coerceIn(DS_APTITUDE_MIN, DS_APTITUDE_MAX)
    }

    private fun bounded(value: Long): Long = value.coerceIn(DS_EXP_MIN, DS_EXP_MAX)

    private fun scale(value: Int, sourceMin: Int, sourceMax: Int, targetMin: Int, targetMax: Int): Int {
        val source = value.coerceIn(sourceMin, sourceMax).toLong()
        val sourceRange = (sourceMax - sourceMin).toLong()
        val numerator = (source - sourceMin) * (targetMax - targetMin)
        return (targetMin + (numerator + sourceRange / 2) / sourceRange).toInt()
    }

    private fun unscale(value: Int, sourceMin: Int, sourceMax: Int, targetMin: Int, targetMax: Int): Int =
        scale(value, sourceMin, sourceMax, targetMin, targetMax)
}
