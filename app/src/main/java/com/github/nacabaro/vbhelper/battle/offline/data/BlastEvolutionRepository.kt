package com.github.nacabaro.vbhelper.battle.offline.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Universal Blast Evolution table shipped with the app. It is intentionally NOT
 * limited to the evolutions allowed by any single DIM: any individual whose
 * species appears here may pick the listed form (solo) or Jogress result (duo
 * lead), no matter which card raised it. Sources are Wikimon pages; specials
 * are filled only where certain and otherwise resolved from the species
 * database at battle time.
 */
data class BlastSoloForm(
    val source: String,
    val target: String,
    val kind: String,
    val special: String? = null,
    val sourceUri: String? = null
)

data class BlastJogressEntry(
    val a: String,
    val b: String,
    val result: String,
    val resultSpecial: String? = null,
    val sources: List<String> = emptyList()
) {
    /** The component that is not [species], or null when [species] is not involved. */
    fun partnerFor(species: String, normalize: (String) -> String): String? {
        val want = normalize(species)
        return when (normalize(a)) {
            want -> b
            else -> if (normalize(b) == want) a else null
        }
    }
}

data class BlastEvolutionData(
    val version: Int = 0,
    @SerializedName("soloForms") val soloForms: List<BlastSoloForm> = emptyList(),
    @SerializedName("jogress") val jogress: List<BlastJogressEntry> = emptyList(),
    @SerializedName("aliases") val aliases: Map<String, String> = emptyMap()
)

object BlastEvolutionRepository {
    const val ASSET_PATH = "blast_evolution.json"

    @Volatile
    private var cached: BlastEvolutionData? = null

    fun load(context: Context): BlastEvolutionData {
        cached?.let { return it }
        val parsed = runCatching {
            context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
                Gson().fromJson(reader, BlastEvolutionData::class.java)
            }
        }.getOrNull() ?: BlastEvolutionData()
        cached = parsed
        return parsed
    }

    /**
     * Canonical form for name matching: lowercased with spacing and
     * punctuation stripped, then alias-resolved. This unifies Wikimon
     * spellings ("War Greymon", "XV-mon", "Dukemon: Crimson Mode") with DIM
     * spellings ("WarGreymon", "ExVeemon", "Gallantmon Crimson Mode").
     * Parenthetical qualifiers are kept so distinct variants (e.g. regular
     * vs Virus MetalGreymon) never conflate.
     */
    fun normalize(data: BlastEvolutionData, name: String): String {
        fun strip(s: String) = s.trim().lowercase()
            .replace(" ", "").replace("-", "").replace(":", "").replace("'", "")
            .replace("(", "").replace(")", "")
        return strip(data.aliases[strip(name)] ?: name)
    }

    /** Normalized lookup set built from [SpeciesProfileDao.getPresentSpeciesNames]. */
    fun presentSet(data: BlastEvolutionData, presentNames: Collection<String?>): Set<String> =
        presentNames.mapNotNull { it?.takeIf { name -> name.isNotBlank() } }
            .map { normalize(data, it).lowercase() }
            .toSet()

    fun isPresent(data: BlastEvolutionData, present: Set<String>, name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return normalize(data, name).lowercase() in present
    }

    /** Solo FORM targets whose species is loaded in at least one app DIM. */
    fun availableForms(
        data: BlastEvolutionData,
        speciesName: String?,
        present: Set<String>
    ): List<BlastSoloForm> =
        soloFormsFor(data, speciesName).filter { isPresent(data, present, it.target) }

    /**
     * Jogress entries whose result species is loaded in at least one app DIM
     * AND whose required partner species is loaded too. A pair the player can
     * never field is not offered.
     */
    fun availableJogress(
        data: BlastEvolutionData,
        speciesName: String?,
        present: Set<String>
    ): List<BlastJogressEntry> {
        if (speciesName.isNullOrBlank()) return emptyList()
        val norm: (String) -> String = { normalize(data, it) }
        return data.jogress.filter { entry ->
            entry.partnerFor(speciesName, norm)?.let { partner ->
                isPresent(data, present, entry.result) && isPresent(data, present, partner)
            } == true
        }
    }

    /** Solo FORM targets for a species, matched case-insensitively with alias support. */
    fun soloFormsFor(data: BlastEvolutionData, speciesName: String?): List<BlastSoloForm> {
        if (speciesName.isNullOrBlank()) return emptyList()
        val want = normalize(data, speciesName).lowercase()
        return data.soloForms.filter { normalize(data, it.source).lowercase() == want }
    }

    /** Jogress entries involving a species (as either component). */
    fun jogressFor(data: BlastEvolutionData, speciesName: String?): List<BlastJogressEntry> {
        if (speciesName.isNullOrBlank()) return emptyList()
        val norm: (String) -> String = { normalize(data, it) }
        return data.jogress.filter { it.partnerFor(speciesName, norm) != null }
    }

    /**
     * Ordered Jogress resolution for a fusion lead: the lead's equipped result
     * must match an entry whose other component is the partner's species.
     */
    fun resolveLeadJogress(
        data: BlastEvolutionData,
        leadSpecies: String?,
        partnerSpecies: String?,
        leadChoiceResult: String?
    ): BlastJogressEntry? {
        if (leadSpecies.isNullOrBlank() || partnerSpecies.isNullOrBlank() || leadChoiceResult.isNullOrBlank()) return null
        val norm: (String) -> String = { normalize(data, it) }
        val wantResult = norm(leadChoiceResult)
        return data.jogress.firstOrNull { entry ->
            norm(entry.result) == wantResult &&
                entry.partnerFor(leadSpecies, norm)?.let { norm(it) == norm(partnerSpecies) } == true
        }
    }

    fun clearCache() {
        cached = null
    }
}
