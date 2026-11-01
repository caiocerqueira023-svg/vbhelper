package com.tamerslab.gamesave

/**
 * Declared, versioned stat/progression projection for one NDS Story layout. The projection is an
 * authored Tamers Lab policy, never inferred from native fields: a layout without a verified policy
 * keeps native values exact and applies no conversion.
 */
data class NdsStoryConversionPolicy(
    val layoutId: String,
    val version: Int,
    val policyId: String,
)

/** Policy registry. World DS keeps its existing `world-ds-stat-v1`; a later layout declares its own. */
object NdsStoryConversionPolicies {
    val WORLD_DS = NdsStoryConversionPolicy(
        layoutId = NdsStoryProfiles.WORLD_DS.layoutId,
        version = WorldDsStatConversion.VERSION,
        policyId = WorldDsStatConversion.POLICY,
    )

    val all: List<NdsStoryConversionPolicy> = listOf(WORLD_DS)

    fun forProfile(profile: NdsStoryProfile): NdsStoryConversionPolicy? =
        all.firstOrNull { it.layoutId == profile.layoutId }
}
