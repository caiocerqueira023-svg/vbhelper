package com.github.nacabaro.vbhelper.battle.offline.core

enum class BattleDamageFormula { ADAPTED, DW1_REFERENCE }

data class BattleDamageInput(
    val power: Int,
    val attack: Int,
    val defense: Int,
    val variancePercent: Int,
    val attackerAttribute: BattleAttribute = BattleAttribute.NONE,
    val defenderAttribute: BattleAttribute = BattleAttribute.NONE,
    val attackMultiplier: Float = 1f,
    val defenseMultiplier: Float = 1f,
    val criticalMultiplier: Float = 1f,
    val guarding: Boolean = false,
    val burned: Boolean = false,
    val frozen: Boolean = false,
    val counter: Boolean = false,
    val signaturePowerBonus: Int = 0,
    val finisher: Boolean = false,
    val finisherCharge: Int = 40,
    val formula: BattleDamageFormula = BattleDamageFormula.ADAPTED
)

data class BattleDamageResult(val damage: Int, val unguardedDamage: Int)

/** One attribute-only pipeline. DW1_REFERENCE expects stats already expressed in PS1 units. */
object BattleDamageResolver {
    fun resolve(input: BattleDamageInput): BattleDamageResult {
        require(input.power >= 0 && input.attack >= 0 && input.defense >= 0)
        require(input.variancePercent in 90..110 && input.signaturePowerBonus in 0..12)
        require(input.attackMultiplier.isFinite() && input.attackMultiplier > 0f)
        require(input.defenseMultiplier.isFinite() && input.defenseMultiplier > 0f)
        require(input.criticalMultiplier.isFinite() && input.criticalMultiplier >= 1f)
        if (input.power == 0) return BattleDamageResult(0, 0)
        val power = (input.power.toLong() + input.signaturePowerBonus).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        val attribute = input.attackerAttribute.damageMultiplierAgainst(input.defenderAttribute)
        var damage = when (input.formula) {
            BattleDamageFormula.ADAPTED -> {
                val defense = input.defense * input.defenseMultiplier * if (input.counter) 0.3 else 1.0
                (power.toDouble() * input.attack * input.attackMultiplier * attribute / 100.0 *
                    input.variancePercent / 100.0 * input.criticalMultiplier * 100.0 / (100.0 + defense))
                    .coerceIn(1.0, Int.MAX_VALUE.toDouble()).toLong()
            }
            BattleDamageFormula.DW1_REFERENCE -> {
                val attack = (input.attack * input.attackMultiplier).toInt()
                val defense = (input.defense * input.defenseMultiplier).toInt()
                val base = if (input.finisher) Dw1DamageFormula.finisher(power, attack, input.finisherCharge,
                    input.variancePercent) else Dw1DamageFormula.normal(power, attack, defense,
                    input.variancePercent, input.counter)
                (base * attribute).toLong()
            }
        }
        if (input.burned) damage -= damage / 4
        damage = damage.coerceAtLeast(1L)
        if (input.frozen) damage += damage / 4
        val unguarded = damage.coerceIn(1, 9999).toInt()
        val guarded = if (input.guarding) (unguarded * 0.3).toInt().coerceAtLeast(1) else unguarded
        return BattleDamageResult(guarded, unguarded)
    }
}

/** Assembly-derived ordinary/partner-finisher arithmetic with neutral specialty factor (30/30). */
object Dw1DamageFormula {
    fun normal(power: Int, attack: Int, defense: Int, variancePercent: Int, counter: Boolean = false): Int {
        require(power >= 0 && attack >= 0 && defense >= 0 && variancePercent in 90..110)
        val adjustedDefense = if (counter) defense.toLong() * 3 / 10 else defense.toLong()
        val difference = (attack.toLong() - adjustedDefense).coerceIn(-500, 500)
        val base = power.toLong() + difference * power / 500
        return (base * variancePercent / 100).coerceIn(1, 9999).toInt()
    }

    fun finisher(power: Int, attack: Int, charge: Int, variancePercent: Int): Int {
        require(power >= 0 && attack >= 0 && charge in 0..80 && variancePercent in 90..110)
        var base = attack.toLong() + power
        if (charge > 40) base = base * charge / 40
        return (base * variancePercent / 100).coerceIn(1, 9999).toInt()
    }
}
