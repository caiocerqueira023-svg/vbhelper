package com.github.nacabaro.vbhelper.battle.offline.data

import com.github.nacabaro.vbhelper.battle.offline.core.BattleStatusEffect
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueDefinition
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueImpactShape
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueKind
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueRangeProfile
import kotlin.math.roundToInt

/** Neutral tactical families. They deliberately do not represent elemental natures. */
enum class GenericTechniqueFamily {
    PRESSURE,
    CONTROL,
    TEMPO,
    ENDURANCE,
    ASSAULT,
    TACTICS,
    PRECISION,
    CHAOS
}

data class GenericTechniqueEntry(
    val family: GenericTechniqueFamily,
    val rank: Int,
    /** Reference values retained for one-to-one catalogue auditing; runtime costs are normalized. */
    val sourcePower: Int,
    val sourceEnergyCost: Int,
    val definition: TechniqueDefinition
)

/**
 * Element-free adaptation of Re:Digitize Decode's 8 x 7 replaceable-skill matrix.
 *
 * Source powers and costs preserve the relative progression documented by the community skill lists.
 * Runtime energy, timings, range units and names are VBHelper sandbox values. Status categories
 * follow their equivalent Decode skill slots, while durations and magnitudes stay battle-tuned.
 */
object GenericTechniqueCatalog {
    const val SOURCE_REFERENCE = "https://gamefaqs.gamespot.com/3ds/705638-digimon-world-redigitize-decode/faqs/71924"
    const val RANGE_REFERENCE = "https://w.atwiki.jp/redigitize_3ds/pages/28.html"

    private val stun = listOf(BattleStatusEffect("decode_stun", 650L, preventsActions = true))
    private val paralysis = listOf(BattleStatusEffect("decode_paralysis", 1_100L, preventsActions = true))
    private val liquidCrystalization = listOf(BattleStatusEffect("decode_liquid_crystalization", 1_100L, preventsActions = true))
    private val slow = listOf(BattleStatusEffect("decode_slow", 4_000L, magnitude = 0.35f, slowsMovement = true))
    private val confusion = listOf(BattleStatusEffect("decode_confusion", 3_500L, attackMultiplier = 0.82f))
    private val noise = listOf(BattleStatusEffect("decode_noise", 3_500L, defenseMultiplier = 0.85f))
    private val poison = listOf(BattleStatusEffect("decode_poison", 4_500L, damagePerSecond = 22f))
    private val attackBoost = listOf(BattleStatusEffect("decode_attack_up", 8_000L, attackMultiplier = 1.18f))
    private val defenseBoost = listOf(BattleStatusEffect("decode_defense_up", 8_000L, defenseMultiplier = 1.18f))
    private val tempoBoost = listOf(BattleStatusEffect(
        "decode_speed_up",
        8_000L,
        movementMultiplier = 1.20f,
        cooldownMultiplier = 0.90f
    ))
    private val fullBoost = listOf(BattleStatusEffect(
        "decode_all_stats_up",
        8_000L,
        attackMultiplier = 1.08f,
        defenseMultiplier = 1.08f,
        movementMultiplier = 1.08f,
        cooldownMultiplier = 0.92f
    ))

    private val PRESSURE = GenericTechniqueFamily.PRESSURE
    private val CONTROL = GenericTechniqueFamily.CONTROL
    private val TEMPO = GenericTechniqueFamily.TEMPO
    private val ENDURANCE = GenericTechniqueFamily.ENDURANCE
    private val ASSAULT = GenericTechniqueFamily.ASSAULT
    private val TACTICS = GenericTechniqueFamily.TACTICS
    private val PRECISION = GenericTechniqueFamily.PRECISION
    private val CHAOS = GenericTechniqueFamily.CHAOS

    private val SELF = TechniqueRangeProfile.SELF
    private val CLOSE = TechniqueRangeProfile.CLOSE
    private val CLOSE_MEDIUM = TechniqueRangeProfile.CLOSE_MEDIUM
    private val MEDIUM_LONG = TechniqueRangeProfile.MEDIUM_LONG
    private val ALL_FIELD = TechniqueRangeProfile.ALL_FIELD

    private val AROUND_USER = TechniqueImpactShape.AROUND_USER
    private val AROUND_TARGET = TechniqueImpactShape.AROUND_TARGET
    private val ALL_OPPONENTS = TechniqueImpactShape.ALL_OPPONENTS

    val entries: List<GenericTechniqueEntry> = listOf(
        entry(PRESSURE, 1, "short_burst", "Rajada curta", 120, 15, CLOSE),
        entry(PRESSURE, 2, "delayed_marker", "Marcador tardio", 165, 54, MEDIUM_LONG, AROUND_TARGET, radius = 1.15f),
        entry(PRESSURE, 3, "circular_pulse", "Pulso circular", 230, 112, CLOSE, AROUND_USER, radius = 2.15f),
        entry(PRESSURE, 4, "remote_trap", "Armadilha remota", 190, 152, MEDIUM_LONG, AROUND_TARGET, paralysis, radius = 1.35f),
        entry(PRESSURE, 5, "expanding_wave", "Onda expansiva", 280, 315, MEDIUM_LONG, AROUND_USER, confusion, radius = 4.5f),
        entry(PRESSURE, 6, "guided_drop", "Queda guiada", 240, 388, MEDIUM_LONG),
        entry(PRESSURE, 7, "field_break", "Ruptura de campo", 350, 512, ALL_FIELD, ALL_OPPONENTS, slow),

        entry(CONTROL, 1, "inhibiting_breath", "Sopro inibidor", 105, 18, CLOSE, effects = slow),
        entry(CONTROL, 2, "impact_shot", "Projétil de impacto", 150, 72, MEDIUM_LONG, effects = paralysis),
        entry(CONTROL, 3, "disorienting_blast", "Explosão desorientadora", 215, 115, CLOSE, AROUND_TARGET, confusion, radius = 1.25f),
        entry(CONTROL, 4, "disruptive_drop", "Queda disruptiva", 170, 158, MEDIUM_LONG, effects = paralysis),
        entry(CONTROL, 5, "attrition_barrage", "Barragem de desgaste", 250, 330, ALL_FIELD, ALL_OPPONENTS, poison, hits = 3),
        entry(CONTROL, 6, "impact_zone", "Zona de impacto", 225, 390, MEDIUM_LONG, AROUND_TARGET, paralysis, radius = 2.2f),
        entry(CONTROL, 7, "containment_field", "Campo de contenção", 330, 534, ALL_FIELD, ALL_OPPONENTS, liquidCrystalization),

        entry(TEMPO, 1, "interrupting_touch", "Toque interruptor", 110, 23, CLOSE, effects = stun),
        entry(TEMPO, 2, "acceleration", "Aceleração", 0, 60, SELF, effects = tempoBoost),
        entry(TEMPO, 3, "linear_cutter", "Corte linear", 230, 135, MEDIUM_LONG),
        entry(TEMPO, 4, "interference_cloud", "Nuvem de interferência", 250, 192, MEDIUM_LONG, AROUND_TARGET, paralysis, radius = 1.8f),
        entry(TEMPO, 5, "blocking_pulse", "Pulso de bloqueio", 270, 368, ALL_FIELD, ALL_OPPONENTS, liquidCrystalization),
        entry(TEMPO, 6, "disorienting_field", "Campo desorientador", 210, 475, ALL_FIELD, ALL_OPPONENTS, confusion),
        entry(TEMPO, 7, "total_overload", "Sobrecarga total", 320, 584, ALL_FIELD, ALL_OPPONENTS, noise),

        entry(ENDURANCE, 1, "attrition_dust", "Pó de desgaste", 95, 12, CLOSE, effects = poison),
        entry(ENDURANCE, 2, "resistant_stance", "Postura resistente", 0, 50, SELF, effects = defenseBoost),
        entry(ENDURANCE, 3, "attrition_ring", "Círculo de desgaste", 170, 98, CLOSE_MEDIUM, AROUND_USER, poison, radius = 2.8f),
        entry(ENDURANCE, 4, "restricting_bind", "Vínculo restritivo", 150, 172, MEDIUM_LONG, effects = slow),
        entry(ENDURANCE, 5, "concussive_drop", "Queda contundente", 230, 234, MEDIUM_LONG, effects = paralysis),
        entry(ENDURANCE, 6, "rupture_wave", "Onda de ruptura", 270, 286, ALL_FIELD, ALL_OPPONENTS, paralysis),
        entry(ENDURANCE, 7, "persistent_storm", "Tempestade persistente", 315, 408, ALL_FIELD, ALL_OPPONENTS, poison, hits = 4),

        entry(ASSAULT, 1, "quick_combo", "Combo rápido", 85, 8, CLOSE, hits = 2),
        entry(ASSAULT, 2, "offensive_charge", "Carga ofensiva", 0, 60, SELF, effects = attackBoost),
        entry(ASSAULT, 3, "pressure_spin", "Giro de pressão", 160, 85, CLOSE, AROUND_USER, radius = 2.1f),
        entry(ASSAULT, 4, "stunning_impact", "Impacto atordoante", 155, 168, CLOSE, effects = paralysis),
        entry(ASSAULT, 5, "combat_focus", "Foco de combate", 0, 240, SELF, effects = fullBoost),
        entry(ASSAULT, 6, "rupture_charge", "Investida de ruptura", 250, 264, CLOSE, effects = paralysis, knockback = 0.8f),
        entry(ASSAULT, 7, "pressure_aura", "Aura de pressão", 290, 412, CLOSE_MEDIUM, AROUND_USER, slow, radius = 3.0f),

        entry(TACTICS, 1, "disruptive_contact", "Contato disruptivo", 135, 42, CLOSE, effects = paralysis),
        entry(TACTICS, 2, "focused_shot", "Disparo focado", 150, 30, MEDIUM_LONG),
        entry(TACTICS, 3, "disorienting_mist", "Névoa desorientadora", 240, 248, CLOSE_MEDIUM, AROUND_TARGET, confusion, radius = 1.7f),
        entry(TACTICS, 4, "impact_flash", "Clarão de impacto", 230, 345, CLOSE_MEDIUM, AROUND_USER, paralysis, radius = 2.6f),
        entry(TACTICS, 5, "interference_field", "Campo de interferência", 335, 612, ALL_FIELD, ALL_OPPONENTS, noise),
        entry(TACTICS, 6, "concentrated_pulse", "Pulso concentrado", 350, 572, ALL_FIELD, ALL_OPPONENTS),
        entry(TACTICS, 7, "system_lock", "Bloqueio sistêmico", 400, 724, ALL_FIELD, ALL_OPPONENTS, liquidCrystalization),

        entry(PRECISION, 1, "calibrated_strike", "Golpe calibrado", 150, 36, CLOSE),
        entry(PRECISION, 2, "optimization", "Otimização", 0, 30, SELF, effects = fullBoost),
        entry(PRECISION, 3, "contact_orb", "Orbe de contato", 220, 176, CLOSE, AROUND_TARGET, paralysis, radius = 1.35f),
        entry(PRECISION, 4, "counter_field", "Campo de contra-ataque", 260, 285, CLOSE, AROUND_USER, confusion, radius = 1.8f),
        entry(PRECISION, 5, "heavy_beam", "Feixe pesado", 320, 415, CLOSE_MEDIUM),
        entry(PRECISION, 6, "precision_drop", "Queda precisa", 270, 524, MEDIUM_LONG),
        entry(PRECISION, 7, "integral_lock", "Bloqueio integral", 380, 682, ALL_FIELD, ALL_OPPONENTS, liquidCrystalization),

        entry(CHAOS, 1, "erratic_strike", "Golpe errático", 100, 25, CLOSE, effects = confusion),
        entry(CHAOS, 2, "shock_burst", "Rajada de choque", 160, 64, CLOSE, effects = paralysis),
        entry(CHAOS, 3, "attrition_trap", "Armadilha de desgaste", 170, 138, MEDIUM_LONG, AROUND_TARGET, poison, radius = 1.3f),
        entry(CHAOS, 4, "fragmented_barrier", "Barreira fragmentada", 240, 186, CLOSE_MEDIUM, AROUND_USER, poison, radius = 2.5f),
        entry(CHAOS, 5, "chaotic_barrage", "Barragem caótica", 280, 342, ALL_FIELD, ALL_OPPONENTS, confusion),
        entry(CHAOS, 6, "slowing_drop", "Queda desaceleradora", 250, 495, MEDIUM_LONG, effects = slow),
        entry(CHAOS, 7, "interference_meteors", "Meteoros de interferência", 325, 608, ALL_FIELD, ALL_OPPONENTS, noise)
    )

    val definitions: List<TechniqueDefinition> = entries.map { it.definition }

    val defaultTechniqueIds: List<String> = listOf(
        "generic_assault_quick_combo",
        "generic_pressure_guided_drop",
        "generic_endurance_attrition_ring"
    )

    val specialTechniqueId: String = "generic_tactics_system_lock"

    val battleDefinitions: List<TechniqueDefinition> = definitions.map { definition ->
        if (definition.techniqueId == specialTechniqueId) definition.copy(
            kind = TechniqueKind.SPECIAL,
            displayName = "Ruptura inata",
            power = 82,
            energyCost = 0,
            commandPointCost = 0,
            minRange = 3f,
            maxRange = 10.5f,
            rangeProfile = TechniqueRangeProfile.MEDIUM_LONG,
            impactShape = TechniqueImpactShape.AROUND_TARGET,
            areaRadius = 2.2f,
            startupMillis = 1_200L,
            activeMillis = 310L,
            recoveryMillis = 1_050L,
            cooldownMillis = 0L,
            interruptibleDuringStartup = false,
            hitCount = 3,
            attackVisual = "large"
        ) else definition
    }

    val selectableEntries: List<GenericTechniqueEntry> = entries
        .filterNot { it.definition.techniqueId == specialTechniqueId }

    val trainingTechniques: List<TechniqueDefinition> = defaultTechniqueIds.map(::definition) +
        battleDefinitions.single { it.techniqueId == specialTechniqueId }
    val trainingTechniqueIds: List<String> = defaultTechniqueIds
    val trainingSpecialTechniqueId: String = specialTechniqueId

    fun definition(techniqueId: String): TechniqueDefinition = entries
        .firstOrNull { it.definition.techniqueId == techniqueId }
        ?.definition
        ?: error("Unknown generic technique: $techniqueId")

    private fun entry(
        family: GenericTechniqueFamily,
        rank: Int,
        id: String,
        name: String,
        power: Int,
        sourceCost: Int,
        range: TechniqueRangeProfile,
        shape: TechniqueImpactShape = TechniqueImpactShape.SINGLE_TARGET,
        effects: List<BattleStatusEffect> = emptyList(),
        radius: Float = 0f,
        hits: Int = 1,
        knockback: Float = 0f
    ): GenericTechniqueEntry {
        require(rank in 1..7)
        val (minRange, profileMaxRange) = when (range) {
            TechniqueRangeProfile.SELF -> 0f to 0f
            TechniqueRangeProfile.CLOSE -> 0.8f to 2.2f
            TechniqueRangeProfile.CLOSE_MEDIUM -> 0.8f to 4.4f
            TechniqueRangeProfile.MEDIUM_LONG -> 3.0f to 10.5f
            TechniqueRangeProfile.ALL_FIELD -> 0f to 16f
            TechniqueRangeProfile.CUSTOM -> error("Generic catalogue entries require an explicit range profile.")
        }
        // A self-centred impact cannot connect beyond its own radius. Keep its activation
        // range honest so movement, AI scoring and the loadout UI all describe the same reach.
        val maxRange = if (shape == TechniqueImpactShape.AROUND_USER) {
            minOf(profileMaxRange, radius)
        } else profileMaxRange
        val kind = when {
            range == TechniqueRangeProfile.SELF -> TechniqueKind.SUPPORT
            shape != TechniqueImpactShape.SINGLE_TARGET -> TechniqueKind.AREA
            range == TechniqueRangeProfile.MEDIUM_LONG -> TechniqueKind.PROJECTILE
            else -> TechniqueKind.MELEE
        }
        val startup = when (range) {
            TechniqueRangeProfile.SELF -> 240L + rank * 25L
            TechniqueRangeProfile.CLOSE -> 220L + rank * 30L
            TechniqueRangeProfile.CLOSE_MEDIUM -> 300L + rank * 35L
            TechniqueRangeProfile.MEDIUM_LONG -> 380L + rank * 45L
            TechniqueRangeProfile.ALL_FIELD -> 680L + rank * 75L
            TechniqueRangeProfile.CUSTOM -> 300L
        }
        val recovery = when (range) {
            TechniqueRangeProfile.SELF -> 420L + rank * 45L
            TechniqueRangeProfile.CLOSE -> 420L + rank * 55L
            TechniqueRangeProfile.CLOSE_MEDIUM -> 520L + rank * 65L
            TechniqueRangeProfile.MEDIUM_LONG -> 620L + rank * 75L
            TechniqueRangeProfile.ALL_FIELD -> 900L + rank * 95L
            TechniqueRangeProfile.CUSTOM -> 500L
        }
        val runtimeCost = if (sourceCost == 0) 0 else (sourceCost * 0.20f).roundToInt().coerceAtLeast(3)
        // Decode's listed power is kept above for comparison, but VBHelper's HP/AP curve is smaller.
        // Multi-hit moves divide that budget so hit count changes rhythm rather than multiplying DPS.
        val runtimePower = if (power == 0) 0 else (power * 0.60f / hits).roundToInt().coerceAtLeast(1)
        return GenericTechniqueEntry(
            family = family,
            rank = rank,
            sourcePower = power,
            sourceEnergyCost = sourceCost,
            definition = TechniqueDefinition(
                techniqueId = "generic_${family.name.lowercase()}_$id",
                displayName = name,
                kind = kind,
                power = runtimePower,
                energyCost = runtimeCost,
                minRange = minRange,
                maxRange = maxRange,
                rangeProfile = range,
                impactShape = shape,
                areaRadius = radius,
                startupMillis = startup,
                activeMillis = 100L + (hits - 1) * 70L,
                recoveryMillis = recovery,
                cooldownMillis = 750L + rank * 420L + if (range == TechniqueRangeProfile.ALL_FIELD) 2_200L else 0L,
                interruptibleDuringStartup = range != TechniqueRangeProfile.ALL_FIELD || rank < 7,
                staggerPower = if (effects.any { it.preventsActions }) 24f + rank * 4f else 0f,
                statusEffects = effects,
                attackVisual = if (rank >= 5) "large" else "small",
                hitCount = hits,
                knockbackDistance = knockback,
                element = null
            )
        )
    }

}
