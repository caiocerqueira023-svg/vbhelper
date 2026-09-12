package com.github.nacabaro.vbhelper.world

import com.github.cfogrady.vbnfc.data.NfcCharacter

/**
 * The small first set of real-world contexts used by World spawns.
 *
 * Null is the safe fallback when public map data is unavailable, incomplete,
 * or cannot be reached. It deliberately has no spawn bias.
 */
enum class WorldBiome(val favoredAttribute: NfcCharacter.Attribute?) {
    URBAN(NfcCharacter.Attribute.Virus),
    PARK(NfcCharacter.Attribute.Vaccine),
    WATER(NfcCharacter.Attribute.Data),
    RURAL(NfcCharacter.Attribute.Data),
    INDUSTRIAL(NfcCharacter.Attribute.Virus),
    ENTERTAINMENT(NfcCharacter.Attribute.Data),
    GRASSLAND(NfcCharacter.Attribute.Vaccine),
    NULL(null)
}
