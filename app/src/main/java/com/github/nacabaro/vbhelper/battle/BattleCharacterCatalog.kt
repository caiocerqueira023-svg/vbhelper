package com.github.nacabaro.vbhelper.battle

import android.content.Context
import com.google.gson.Gson

data class ExtractedBattleCharacter(
    val characterId: String,
    val phase: Int,
    val hp: Int,
    val ap: Int,
    val bp: Int,
    val smallAttackFile: String,
    val largeAttackFile: String,
    val attribute: com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute = com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute.NONE
) {
    val hasAnyUsableStats: Boolean
        get() = listOf(hp, ap, bp).any { it in 1 until 65_535 }
}

private data class CharacterDataResponse(
    val DataList: List<String>? = null,
    val all_attributes: CharacterDataAttributes? = null
)

private data class CharacterDataAttributes(val DataList: List<String>? = null)

/** Shared, process-wide index of the bundled character stats and attack VFX metadata. */
object BattleCharacterCatalog {
    private val gson = Gson()
    private val idPattern = Regex("charaId='([^']+)'")
    private val phasePattern = Regex("\\bphase=(-?\\d+)")
    private val hpPattern = Regex("\\bhp=(-?\\d+)")
    private val apPattern = Regex("\\bap=(-?\\d+)")
    private val bpPattern = Regex("\\bbp=(-?\\d+)")
    private val typePattern = Regex("\\btype=(-?\\d+)")
    private val smallAttackPattern = Regex("smalefilename='([^']*)'")
    private val largeAttackPattern = Regex("laugeFileName='([^']*)'")

    @Volatile
    private var cachedById: Map<String, ExtractedBattleCharacter>? = null

    fun all(context: Context): Map<String, ExtractedBattleCharacter> = cachedById ?: synchronized(this) {
        cachedById ?: readCatalog(context).also { cachedById = it }
    }

    fun find(context: Context, characterId: String): ExtractedBattleCharacter? =
        all(context)[characterId.lowercase()]

    private fun readCatalog(context: Context): Map<String, ExtractedBattleCharacter> = runCatching {
        val json = context.applicationContext.assets.open(BattleAssetPaths.CHARACTER_DATA)
            .bufferedReader()
            .use { it.readText() }
        val response = gson.fromJson(json, CharacterDataResponse::class.java)
        (response.DataList ?: response.all_attributes?.DataList.orEmpty())
            .mapNotNull(::parseRecord)
            .associateBy { it.characterId.lowercase() }
    }.getOrDefault(emptyMap())

    private fun parseRecord(serialized: String): ExtractedBattleCharacter? {
        val characterId = idPattern.find(serialized)?.groupValues?.getOrNull(1) ?: return null
        fun intValue(pattern: Regex): Int = pattern.find(serialized)
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        fun stringValue(pattern: Regex): String = pattern.find(serialized)
            ?.groupValues?.getOrNull(1) ?: "0"

        return ExtractedBattleCharacter(
            characterId = characterId,
            phase = intValue(phasePattern),
            hp = intValue(hpPattern),
            ap = intValue(apPattern),
            bp = intValue(bpPattern),
            smallAttackFile = stringValue(smallAttackPattern),
            largeAttackFile = stringValue(largeAttackPattern),
            attribute = when (intValue(typePattern)) {
                1 -> com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute.VACCINE
                2 -> com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute.VIRUS
                3 -> com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute.DATA
                4 -> com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute.FREE
                else -> com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute.NONE
            }
        )
    }
}
