package com.github.nacabaro.vbhelper.species

import com.github.cfogrady.vbnfc.data.NfcCharacter
import java.util.Locale

/** Authored app rules, not measurements from a device or statistics claimed by research profiles. */
object CatalogGameplayProfile {
    fun tier(species: CanonicalSpeciesDto): Int = when (species.level?.lowercase(Locale.ROOT)?.trim()) {
        "baby i", "fresh" -> 0
        "baby ii", "in-training" -> 1
        "child", "rookie" -> 2
        "adult", "champion", "armor" -> 3
        "perfect" -> 4
        "ultimate", "mega", "super ultimate", "ultra" -> 5
        "hybrid" -> when (CanonicalSpeciesCatalog.key(species.name)) {
            "flamon", "strabimon" -> 2
            "aldamon", "beowulfmon", "jetsilphymon", "rhinokabuterimon", "daipenmon", "raihimon" -> 4
            "kaisergreymon", "magnagarurumon", "susanoomon" -> 5
            else -> 3
        }
        else -> 3
    }

    fun attribute(set: DotSpriteSetDto): NfcCharacter.Attribute = when (set.attribute) {
        "0" -> NfcCharacter.Attribute.Free
        "1" -> NfcCharacter.Attribute.Virus
        "2" -> NfcCharacter.Attribute.Data
        "3" -> NfcCharacter.Attribute.Vaccine
        else -> NfcCharacter.Attribute.None
    }

    /** HP/BP/AP in native DIM input ranges so the shared DIM-to-app projection stays valid. */
    fun stats(species: CanonicalSpeciesDto): Triple<Int, Int, Int> {
        // Mirrors the published VitalWear DiM averages per phase used by TrainingBattleStats.
        val table = mapOf(
            0 to Triple(1f, 1f, 1f),
            1 to Triple(1f, 1f, 1f),
            2 to Triple(3f, 10f, 2f),
            3 to Triple(4.5182f, 20.2727f, 2.5727f),
            4 to Triple(9.6494f, 32.9221f, 4.0519f),
            5 to Triple(13.5963f, 52.4771f, 7.1743f),
        )
        val (hp, bp, ap) = table.getValue(tier(species))
        fun variation(salt: String) = 90 + Math.floorMod((species.id + salt).hashCode(), 21)
        return Triple((hp * variation("hp") / 100).toInt().coerceAtLeast(1),
            (bp * variation("bp") / 100).toInt().coerceAtLeast(1),
            (ap * variation("ap") / 100).toInt().coerceAtLeast(1))
    }
}
