package com.github.nacabaro.vbhelper.gamesave

/**
 * Structural codec facade for the 384-byte Digimon World DS individual record. The layout-driven
 * implementation is [NdsStoryRecordCodec] over [NdsStoryProfiles.WORLD_DS]; the constants and
 * function signatures here are the stable World DS contract used by the app, desktop tools and the
 * party-edit writer.
 */
object WorldDsRecordCodec {
    private val codec = NdsStoryRecordCodec(NdsStoryProfiles.WORLD_DS)

    const val RECORD_BYTES = 0x180
    const val NAME_SLOTS = 9
    const val NAME_TERMINATOR = 0xFFFF
    const val MAX_NAME_CHARS = NAME_SLOTS - 1

    const val OFFSET_LOCAL_ID = 0x00
    const val OFFSET_SPECIES_ID = 0x04
    const val OFFSET_NAME = 0x08
    const val OFFSET_UNK_1A = 0x1A
    const val OFFSET_EXP_CURVE = 0x1B
    const val OFFSET_GROWTH = 0x1C
    const val OFFSET_UNK_1D = 0x1D
    const val OFFSET_STAGE = 0x1E
    const val OFFSET_ABILITIES = 0x22
    const val OFFSET_ABILITY_SOURCES = 0x2C
    const val OFFSET_HP = 0x3C
    const val OFFSET_MAX_HP = 0x40
    const val OFFSET_MP = 0x44
    const val OFFSET_MAX_MP = 0x46
    const val OFFSET_ATTACK = 0x48
    const val OFFSET_DEFENSE = 0x4A
    const val OFFSET_SPIRIT = 0x4C
    const val OFFSET_SPEED = 0x4E
    const val OFFSET_UNK_50 = 0x50
    const val OFFSET_MAX_LEVEL = 0x52
    const val OFFSET_SKILLS = 0x54
    const val OFFSET_UNK_70 = 0x70
    const val OFFSET_LEVEL = 0x74
    const val OFFSET_EXP = 0x78
    const val OFFSET_EXP_TO_NEXT = 0x9C
    const val OFFSET_HISTORY = 0xA0
    const val HISTORY_UNK0 = 0x00
    const val HISTORY_FORMS = 0x04
    const val HISTORY_STAGE = 0x4E
    const val HISTORY_UNK_4F = 0x4F
    const val HISTORY_UNK_50 = 0x50
    const val HISTORY_RESISTANCES = 0x52
    const val OFFSET_UNK_100 = 0x100
    const val OFFSET_FLAGS = 0x104
    const val OFFSET_UNK_108 = 0x108

    init {
        require(NdsStoryProfiles.WORLD_DS.record.bytes == RECORD_BYTES)
        require(NdsStoryProfiles.WORLD_DS.record.nameSlots == NAME_SLOTS)
    }

    data class NameEncoding(val codes: List<Int>, val fallback: Boolean, val truncated: Boolean)

    data class GeneratedRecord(
        val bytes: ByteArray,
        val name: String,
        val nameFallback: Boolean,
        val nameTruncated: Boolean,
        val maxLevel: Int,
    )

    fun encodeName(text: String): NameEncoding {
        val encoded = codec.encodeName(text)
        return NameEncoding(encoded.codes, encoded.fallback, encoded.truncated)
    }

    fun decodeName(codes: List<Int>): String = codec.decodeName(codes)

    fun generate(
        localId: Long,
        speciesId: Long,
        name: String,
        fallbackName: String,
        stats: WorldDsStatConversion.NativeStats,
        seed: ByteArray? = null,
        defaults: WorldDsRecordDefaults? = null,
        lineage: List<Long> = emptyList(),
    ): GeneratedRecord {
        val generated = codec.generate(localId, speciesId, name, fallbackName, stats, seed, defaults, lineage)
        return GeneratedRecord(generated.bytes, generated.name, generated.nameFallback,
            generated.nameTruncated, generated.maxLevel)
    }

    fun needsSpeciesRepair(bytes: ByteArray): Boolean = codec.needsSpeciesRepair(bytes)

    fun previousSpeciesIds(bytes: ByteArray): List<Long> = codec.previousSpeciesIds(bytes)

    fun base64(bytes: ByteArray): String = codec.base64(bytes)

    fun decodeBase64(encoded: String): ByteArray = codec.decodeBase64(encoded)

    fun localId(bytes: ByteArray): Long = codec.localId(bytes)

    fun speciesId(bytes: ByteArray): Long = codec.speciesId(bytes)
}
