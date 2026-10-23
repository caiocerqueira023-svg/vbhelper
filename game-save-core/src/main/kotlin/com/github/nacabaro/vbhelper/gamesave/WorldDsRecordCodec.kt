package com.github.nacabaro.vbhelper.gamesave

import java.util.Base64

/** Structural codec for the 384-byte Digimon World DS individual record. */
object WorldDsRecordCodec {
    const val RECORD_BYTES = WorldDsResearchInspector.RECORD_BYTES
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

    private const val FORM_SLOTS = 5
    private const val SKILL_SLOTS = 7
    private const val ABILITY_SLOTS = 4
    private const val RESISTANCE_SLOTS = 7
    private const val UNK_70_NATIVE = 33
    private const val UNK_108_NATIVE = 0x40

    data class NameEncoding(val codes: List<Int>, val fallback: Boolean, val truncated: Boolean)

    data class GeneratedRecord(
        val bytes: ByteArray,
        val name: String,
        val nameFallback: Boolean,
        val nameTruncated: Boolean,
        val maxLevel: Int,
    )

    fun encodeName(text: String): NameEncoding {
        val codes = mutableListOf<Int>()
        var fallback = false
        for (char in text) {
            if (codes.size == MAX_NAME_CHARS) break
            val code = glyphCode(char)
            if (code == null) fallback = true else codes += code
        }
        return NameEncoding(codes, fallback, text.length > MAX_NAME_CHARS)
    }

    fun decodeName(codes: List<Int>): String = WorldDsText.decode(codes)

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
        require(localId in 1 until 0xFFFFFF00L)
        require(speciesId in 0 until WorldDsSaveReader.SPECIES_COUNT.toLong())
        val bytes = seed?.copyOf() ?: ByteArray(RECORD_BYTES)
        require(bytes.size == RECORD_BYTES)
        var encoding = encodeName(name)
        val usedFallback = encoding.fallback
        if (usedFallback) {
            val fallback = encodeName(fallbackName)
            if (fallback.fallback || fallback.codes.isEmpty()) throw IllegalArgumentException("unencodable name")
            encoding = fallback
        }
        writeU32(bytes, OFFSET_LOCAL_ID, localId)
        writeU32(bytes, OFFSET_SPECIES_ID, speciesId)
        writeName(bytes, encoding.codes)
        writeU32(bytes, OFFSET_HP, stats.hp.toLong())
        writeU32(bytes, OFFSET_MAX_HP, stats.maxHp.toLong())
        writeU16(bytes, OFFSET_MP, stats.mp)
        writeU16(bytes, OFFSET_MAX_MP, stats.maxMp)
        writeU16(bytes, OFFSET_ATTACK, stats.attack)
        writeU16(bytes, OFFSET_DEFENSE, stats.defense)
        writeU16(bytes, OFFSET_SPIRIT, stats.spirit)
        writeU16(bytes, OFFSET_SPEED, stats.speed)
        val seededMaxLevel = seed?.let { u16(it, OFFSET_MAX_LEVEL) }?.takeIf { it in 1..999 }
        val maxLevel = seededMaxLevel ?: stats.aptitude.coerceIn(0, 999)
        writeU16(bytes, OFFSET_MAX_LEVEL, maxLevel)
        writeU16(bytes, OFFSET_LEVEL, stats.level)
        stats.exp.forEachIndexed { index, value -> writeU32(bytes, OFFSET_EXP + index * 4, value) }
        writeSpeciesFields(bytes, speciesId, seed, defaults, lineage)
        val curve = u8(bytes, OFFSET_EXP_CURVE)
        writeU32(bytes, OFFSET_EXP_TO_NEXT, WorldDsExpCurves.expToNext(curve, stats.level, maxLevel).toLong())
        return GeneratedRecord(bytes, if (usedFallback) fallbackName else name, usedFallback, encoding.truncated, maxLevel)
    }

    private fun writeSpeciesFields(
        bytes: ByteArray,
        speciesId: Long,
        seed: ByteArray?,
        defaults: WorldDsRecordDefaults?,
        lineage: List<Long>,
    ) {
        val stage = WorldDsSpeciesStages.recordStage(speciesId)
        val seedSpecies = seed?.let { u32(it, OFFSET_SPECIES_ID) }
        val speciesChanged = seed == null || seedSpecies != speciesId
        val seedHasPayload = seed != null && hasSpeciesPayload(seed)
        val useDefaults = defaults != null && (speciesChanged || !seedHasPayload)

        writeU8(bytes, OFFSET_STAGE, stage)
        if (useDefaults) {
            writeU8(bytes, OFFSET_UNK_1A, defaults.unk1A)
            writeU16(bytes, OFFSET_UNK_50, defaults.unk50)
            for (slot in 0 until ABILITY_SLOTS) writeU16(bytes, OFFSET_ABILITIES + slot * 2, 0xFFFF)
            writeU16(bytes, OFFSET_ABILITIES, defaults.ability)
            for (slot in 0 until ABILITY_SLOTS) writeU32(bytes, OFFSET_ABILITY_SOURCES + slot * 4, -1L)
            writeU32(bytes, OFFSET_ABILITY_SOURCES, speciesId)
            for (slot in 0 until SKILL_SLOTS) writeU16(bytes, OFFSET_SKILLS + slot * 2, 0xFFFF)
            defaults.skills.take(4).forEachIndexed { index, skill ->
                if (skill in 0..0xFFFE) writeU16(bytes, OFFSET_SKILLS + index * 2, skill)
            }
        }

        val seededCurve = seed?.let { u8(it, OFFSET_EXP_CURVE) }
        val curve = if (!speciesChanged && seededCurve != null && seededCurve in 0..2) {
            seededCurve
        } else {
            WorldDsSpeciesStages.expCurve(speciesId)
        }
        writeU8(bytes, OFFSET_EXP_CURVE, curve)

        val seededGrowth = seed?.let { u8(it, OFFSET_GROWTH) }
        val growth = if (!speciesChanged && seededGrowth != null && seededGrowth <= 5) {
            seededGrowth
        } else {
            WorldDsSpeciesStages.growth(speciesId)
        }
        writeU8(bytes, OFFSET_GROWTH, growth)
        if (seed == null) writeU8(bytes, OFFSET_UNK_1D, 0)

        val forms = historyForms(seed, speciesId, stage, speciesChanged, lineage)
        forms.forEachIndexed { index, value ->
            writeU32(bytes, OFFSET_HISTORY + HISTORY_FORMS + index * 4, value.toLong())
        }
        writeU32(bytes, OFFSET_HISTORY + HISTORY_UNK0, stageFlags(stage).toLong())
        writeU8(bytes, OFFSET_HISTORY + HISTORY_STAGE, stage)
        writeU8(bytes, OFFSET_HISTORY + HISTORY_UNK_4F, 0)
        writeU16(bytes, OFFSET_HISTORY + HISTORY_UNK_50,
            if (useDefaults) defaults.historyUnk50 else u16(bytes, OFFSET_HISTORY + HISTORY_UNK_50))

        if (useDefaults || seed == null || !hasResistances(seed)) {
            if (defaults != null) {
                for (slot in 0 until RESISTANCE_SLOTS) {
                    writeU16(bytes, OFFSET_HISTORY + HISTORY_RESISTANCES + slot * 2,
                        defaults.resistances.getOrElse(slot) { 500 })
                }
            }
        }

        if (u16(bytes, OFFSET_UNK_70) == 0) writeU16(bytes, OFFSET_UNK_70, UNK_70_NATIVE)
        writeU16(bytes, OFFSET_UNK_108, u16(bytes, OFFSET_UNK_108) or UNK_108_NATIVE)
    }

    /**
     * Records produced before species-field generation carry no abilities, skills, resistances or
     * history. They are valid structurally but the game treats them as an empty/corrupt template.
     * Detecting that shape lets an existing snapshot be regenerated and replaced in place.
     */
    fun needsSpeciesRepair(bytes: ByteArray): Boolean {
        require(bytes.size == RECORD_BYTES)
        val stage = u8(bytes, OFFSET_STAGE)
        val historyStage = u8(bytes, OFFSET_HISTORY + HISTORY_STAGE)
        val species = readS32(bytes, OFFSET_SPECIES_ID)
        val unk0 = readS32(bytes, OFFSET_HISTORY + HISTORY_UNK0)
        val forms = List(FORM_SLOTS) { readS32(bytes, OFFSET_HISTORY + HISTORY_FORMS + it * 4) }
        return stage !in 0 until FORM_SLOTS || !hasSpeciesPayload(bytes) || !hasResistances(bytes) ||
            (unk0 and 0x1C) != stageFlags(stage) || forms.all { it == 0 } ||
            forms.getOrNull(stage) != species || forms.none { it == species } || historyStage != stage
    }

    /** Forms this record has worn before the current one, earliest first. */
    fun previousSpeciesIds(bytes: ByteArray): List<Long> {
        require(bytes.size == RECORD_BYTES)
        val species = speciesId(bytes)
        return List(FORM_SLOTS) { readS32(bytes, OFFSET_HISTORY + HISTORY_FORMS + it * 4).toLong() }
            .filter { it in 0 until WorldDsSaveReader.SPECIES_COUNT.toLong() && it != species }
    }

    private fun historyForms(
        seed: ByteArray?,
        speciesId: Long,
        stage: Int,
        speciesChanged: Boolean,
        lineage: List<Long>,
    ): IntArray {
        val seeded = seed?.takeIf {
            !speciesChanged && u8(it, OFFSET_STAGE) == stage &&
                (readS32(it, OFFSET_HISTORY + HISTORY_UNK0) and 0x1C) == stageFlags(stage)
        }?.let { source ->
            List(FORM_SLOTS) { readS32(source, OFFSET_HISTORY + HISTORY_FORMS + it * 4) }
        }?.takeIf { values ->
            values.any { it != 0 } && values.all { it == -1 || it in 0 until WorldDsSaveReader.SPECIES_COUNT } &&
                values[stage] == speciesId.toInt()
        }
        if (seeded != null) {
            return IntArray(FORM_SLOTS) { index -> if (index == stage) speciesId.toInt() else seeded[index] }
        }
        val forms = IntArray(FORM_SLOTS) { -1 }
        forms[stage] = speciesId.toInt()
        var index = stage
        var cursor = 0
        while (index > 0 && cursor < lineage.size) {
            val previous = lineage[cursor++]
            if (previous !in 0 until WorldDsSaveReader.SPECIES_COUNT.toLong()) break
            index -= 1
            forms[index] = previous.toInt()
        }
        return forms
    }

    private fun stageFlags(stage: Int): Int = when (stage) {
        0 -> 0x1C
        1 -> 0x18
        2 -> 0x10
        else -> 0
    }

    private fun hasSpeciesPayload(bytes: ByteArray): Boolean =
        u8(bytes, OFFSET_UNK_1A) != 0 ||
            u16(bytes, OFFSET_UNK_50) != 0 ||
            (0 until ABILITY_SLOTS).any { u16(bytes, OFFSET_ABILITIES + it * 2) in 1..0xFFFE } ||
            (0 until SKILL_SLOTS).any { u16(bytes, OFFSET_SKILLS + it * 2) in 1..0xFFFE }

    private fun hasResistances(bytes: ByteArray): Boolean =
        (0 until RESISTANCE_SLOTS).any { readS16(bytes, OFFSET_HISTORY + HISTORY_RESISTANCES + it * 2) != 0 }

    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeBase64(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)

    fun localId(bytes: ByteArray): Long {
        require(bytes.size == RECORD_BYTES)
        return u32(bytes, OFFSET_LOCAL_ID)
    }

    fun speciesId(bytes: ByteArray): Long {
        require(bytes.size == RECORD_BYTES)
        return u32(bytes, OFFSET_SPECIES_ID)
    }

    private fun writeName(bytes: ByteArray, codes: List<Int>) {
        for (slot in 0 until NAME_SLOTS) writeU16(bytes, OFFSET_NAME + slot * 2, NAME_TERMINATOR)
        codes.forEachIndexed { index, code -> writeU16(bytes, OFFSET_NAME + index * 2, code) }
    }

    private fun glyphCode(char: Char): Int? = when (char) {
        in 'A'..'Z' -> 0x50 + (char - 'A')
        in 'a'..'z' -> 0x6A + (char - 'a')
        in '0'..'9' -> 1 + (char - '0')
        ' ' -> 0x00; '/' -> 0x0B; '%' -> 0x0C; ':' -> 0x0D; '\'' -> 0x0E; '"' -> 0x0F
        ',' -> 0x11; '.' -> 0x12; '!' -> 0x13; '?' -> 0x14; '(' -> 0x15; ')' -> 0x16
        '+' -> 0x17; '-' -> 0x18; '=' -> 0x19; '~' -> 0x1B; '&' -> 0x23
        else -> null
    }

    private fun writeU8(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 4) bytes[offset + index] = ((value shr (8 * index)) and 0xFF).toByte()
    }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (0 until 4).fold(0L) { value, index -> value or ((bytes[offset + index].toLong() and 0xFF) shl (8 * index)) }

    private fun readS16(bytes: ByteArray, offset: Int): Int = u16(bytes, offset).toShort().toInt()

    private fun readS32(bytes: ByteArray, offset: Int): Int = u32(bytes, offset).toInt()
}
