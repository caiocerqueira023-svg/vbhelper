package com.tamerslab.gamesave

/**
 * Structural codec for the 0x164-byte Dawn/Dusk individual record. The layout and the write
 * behavior mirror the shared engine's own creation path (`FUN_02030d44` + `FUN_02030f78`,
 * decompiled in analysis/ds2):

 *  - fields up to the skill list keep the World DS positions; the level is at 0x76
 *  - nine 32-bit counters at 0x78, next-level threshold at 0x9C
 *  - history at 0xB0: stage flags, five stage-indexed forms, a species bitmap at 0xC8
 *    (word = species/16, bit = species%16), the stage byte at 0x107 and eight resistances at 0x108
 *  - the state word at 0x120 is zero for ordinary records
 *
 * A seeded record keeps every byte outside the documented windows, exactly like the engine keeps
 * an existing record's unknown state when it rebuilds the fields it owns.
 */
object Ds2RecordCodec {
    const val RECORD_BYTES = 0x164
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
    const val OFFSET_LEVEL = 0x76
    const val OFFSET_EXP = 0x78
    const val OFFSET_EXP_TO_NEXT = 0x9C
    const val OFFSET_HISTORY = 0xB0
    const val OFFSET_HISTORY_BITMAP = 0xC8
    const val OFFSET_HISTORY_STAGE = 0x107
    const val OFFSET_RESISTANCES = 0x108
    const val OFFSET_FLAGS = 0x120

    const val ABILITY_SLOTS = 4
    const val SKILL_SLOTS = 14
    const val RESISTANCE_SLOTS = 8
    const val EXP_CHANNELS = 9
    const val FORM_SLOTS = 5
    /** The engine's history bitmap covers species<496; higher ids overlap the stage byte. */
    const val BITMAP_SPECIES_LIMIT = 496
    const val UNK_70_NATIVE = 0x21

    /** Species-derived values the engine copies when it creates a record (`/dat/dm`). */
    data class Ds2RecordDefaults(
        val unk1A: Int,
        val unk50: Int,
        val abilities: List<Int>,
        val growth: Int,
        val skills: List<Int>,
        val resistances: List<Int>,
        val expCurve: Int,
        val baseLevel: Int,
        val maxLevel: Int,
        val baseHp: Int = 1,
        val baseMp: Int = 0,
        val baseAttack: Int = 1,
        val baseDefense: Int = 1,
        val baseSpirit: Int = 1,
        val baseSpeed: Int = 1,
        /** Species `unk27`: the engine picks one of its set bits for `unk1D`. */
        val unk27: Int = 0,
    )

    /** Values the projection supplies for the record's own windows. */
    data class Ds2GeneratedStats(
        val level: Int,
        val hp: Int,
        val maxHp: Int,
        val mp: Int,
        val maxMp: Int,
        val attack: Int,
        val defense: Int,
        val spirit: Int,
        val speed: Int,
        val maxLevel: Int,
    )

    data class GeneratedRecord(
        val bytes: ByteArray,
        val name: String,
        val nameFallback: Boolean,
        val nameTruncated: Boolean,
        val maxLevel: Int,
        val localId: Long,
    )

    fun encodeName(text: String): NdsStoryNameEncoding = Ds2TextCodec.encode(text, MAX_NAME_CHARS)

    fun decodeName(codes: List<Int>): String = Ds2TextCodec.decode(codes)

    /**
     * Builds one record. With a [seed] the record keeps its identity and every unknown window; a
     * null seed produces a fresh record (identity 0 unless [localId] is given), exactly the shape
     * the engine creates before the save assigns the next id.
     */
    fun generate(
        localId: Long,
        speciesId: Long,
        name: String,
        fallbackName: String,
        stats: Ds2GeneratedStats,
        seed: ByteArray? = null,
        defaults: Ds2RecordDefaults? = null,
        lineage: List<Long> = emptyList(),
        exp: List<Long> = emptyList(),
    ): GeneratedRecord {
        require(speciesId in 0x41L..0x1F4L) { "Species outside the Dawn/Dusk range" }
        require(stats.level in 1..99) { "Level outside 1..99" }
        require(stats.maxHp in 1..999999 && stats.hp in 0..stats.maxHp) { "HP outside the validated range" }
        require(stats.maxMp in 0..9999 && stats.mp in 0..stats.maxMp) { "MP outside the validated range" }
        require(listOf(stats.attack, stats.defense, stats.spirit, stats.speed).all { it in 0..1250 }) {
            "Combat stats outside the validated range"
        }
        val bytes = seed?.copyOf() ?: ByteArray(RECORD_BYTES)
        require(bytes.size == RECORD_BYTES) { "A seeded record must be exactly 0x164 bytes" }
        val seedSpecies = seed?.let { u32(it, OFFSET_SPECIES_ID) }
        val speciesChanged = seed == null || seedSpecies != speciesId
        var encoding = encodeName(name)
        val usedFallback = encoding.fallback
        if (usedFallback) {
            val fallback = encodeName(fallbackName)
            if (fallback.fallback || fallback.codes.isEmpty()) throw IllegalArgumentException("unencodable name")
            encoding = fallback
        }
        writeU32(bytes, OFFSET_LOCAL_ID, if (seed == null) localId else u32(bytes, OFFSET_LOCAL_ID))
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
        writeU16(bytes, OFFSET_MAX_LEVEL, stats.maxLevel)
        writeU16(bytes, OFFSET_LEVEL, stats.level)
        for (slot in 0 until EXP_CHANNELS) {
            writeU32(bytes, OFFSET_EXP + slot * 4, exp.getOrElse(slot) { 0L }.coerceIn(0L, 0xFFFFFFFFL))
        }
        if (seed == null) {
            writeU16(bytes, OFFSET_UNK_70, UNK_70_NATIVE)
            writeU16(bytes, 0x74, 0)
            writeU16(bytes, 0x120, 0)
            writeU32(bytes, 0x124, 0)
        } else if (u16(bytes, OFFSET_UNK_70) == 0) {
            writeU16(bytes, OFFSET_UNK_70, UNK_70_NATIVE)
        }
        val buildSpeciesFields = defaults != null && (seed == null || speciesChanged || !hasSpeciesPayload(bytes))
        if (buildSpeciesFields) writeSpeciesFields(bytes, speciesId, defaults, lineage, speciesChanged)
        val recordedMaxLevel = u16(bytes, OFFSET_MAX_LEVEL)
        writeU32(bytes, OFFSET_EXP_TO_NEXT,
            Ds2SpeciesData.expToNext(u8(bytes, OFFSET_EXP_CURVE), stats.level, recordedMaxLevel).toLong())
        return GeneratedRecord(bytes, if (usedFallback) fallbackName else name, usedFallback,
            encoding.truncated, recordedMaxLevel, u32(bytes, OFFSET_LOCAL_ID))
    }

    /** True when a stored record lacks the fields the current creation path always writes. */
    fun needsSpeciesRepair(bytes: ByteArray): Boolean {
        if (bytes.size != RECORD_BYTES) return true
        return !hasSpeciesPayload(bytes)
    }

    private fun hasSpeciesPayload(bytes: ByteArray): Boolean =
        u8(bytes, OFFSET_UNK_1A) != 0 || u16(bytes, OFFSET_UNK_50) != 0 ||
            (0 until ABILITY_SLOTS).any { u16(bytes, OFFSET_ABILITIES + it * 2) in 1..0xFFFE } ||
            (0 until SKILL_SLOTS).any { u16(bytes, OFFSET_SKILLS + it * 2) in 1..0xFFFE }

    private fun writeSpeciesFields(
        bytes: ByteArray,
        speciesId: Long,
        defaults: Ds2RecordDefaults,
        lineage: List<Long>,
        speciesChanged: Boolean,
    ) {
        val stage = Ds2SpeciesData.recordStage(speciesId)
        writeU8(bytes, OFFSET_UNK_1A, defaults.unk1A)
        writeU8(bytes, OFFSET_EXP_CURVE, defaults.expCurve.coerceIn(0, 2))
        writeU8(bytes, OFFSET_GROWTH, defaults.growth.coerceIn(0, 5))
        writeU8(bytes, OFFSET_UNK_1D, firstSetBit(defaults.unk27))
        writeU8(bytes, OFFSET_STAGE, stage)
        writeU16(bytes, OFFSET_UNK_50, defaults.unk50)
        writeU16(bytes, OFFSET_MAX_LEVEL, defaults.maxLevel.coerceIn(0, 99))
        // Abilities: four u16, 0xFF in the base data means "none".
        for (slot in 0 until ABILITY_SLOTS) {
            val ability = defaults.abilities.getOrElse(slot) { 0xFF }
            writeU16(bytes, OFFSET_ABILITIES + slot * 2, if (ability == 0xFF) 0xFFFF else ability)
        }
        writeU32(bytes, OFFSET_ABILITY_SOURCES, speciesId)
        for (slot in 1 until ABILITY_SLOTS) writeU32(bytes, OFFSET_ABILITY_SOURCES + slot * 4, -1L)
        // Skills: the species' four, then the terminator slots.
        for (slot in 0 until SKILL_SLOTS) writeU16(bytes, OFFSET_SKILLS + slot * 2, 0xFFFF)
        defaults.skills.take(4).forEachIndexed { index, skill ->
            if (skill in 0..0xFFFE) writeU16(bytes, OFFSET_SKILLS + index * 2, skill)
        }
        writeHistory(bytes, speciesId, stage, defaults, lineage)
        for (slot in 0 until RESISTANCE_SLOTS) {
            writeU16(bytes, OFFSET_RESISTANCES + slot * 2, defaults.resistances.getOrElse(slot) { 500 })
        }
    }

    /**
     * Mirrors `FUN_02030f78`: stage flags and the stage byte first, then the five forms (current
     * species at its stage index, earlier entries filled from the predecessor chain) and the
     * species bitmap marking every form written.
     */
    private fun writeHistory(
        bytes: ByteArray,
        speciesId: Long,
        stage: Int,
        defaults: Ds2RecordDefaults,
        lineage: List<Long>,
    ) {
        writeStageByteAndFlags(bytes, stage)
        val forms = IntArray(FORM_SLOTS) { -1 }
        val formIndex = stage.coerceIn(0, FORM_SLOTS - 1)
        forms[formIndex] = speciesId.toInt()
        var index = formIndex - 1
        for (predecessor in lineage) {
            if (index < 0) break
            if (predecessor !in 0x41L..0x1F4L) break
            forms[index--] = predecessor.toInt()
        }
        forms.forEachIndexed { slot, value ->
            writeU32(bytes, OFFSET_HISTORY + 4 + slot * 4, value.toLong())
        }
        // Bitmap: the current species and the chain that was actually written.
        val marked = buildList {
            add(speciesId)
            lineage.take(formIndex.coerceAtLeast(0)).forEach { add(it) }
        }
        for (slot in 0 until 8) writeU16(bytes, OFFSET_HISTORY_BITMAP + slot * 2, 0)
        for (slot in 8 until 31) writeU16(bytes, OFFSET_HISTORY_BITMAP + slot * 2, 0)
        for (species in marked) markSpecies(bytes, species)
    }

    private fun writeStageByteAndFlags(bytes: ByteArray, stage: Int) {
        val flags = when (stage) {
            0 -> 0x1C
            1 -> 0x18
            2 -> 0x10
            else -> 0x00
        }
        writeU32(bytes, OFFSET_HISTORY, flags.toLong())
        writeU8(bytes, OFFSET_HISTORY_STAGE, stage.coerceIn(0, 4))
    }

    private fun markSpecies(bytes: ByteArray, speciesId: Long) {
        if (speciesId < 0 || speciesId >= BITMAP_SPECIES_LIMIT) return
        val index = (speciesId / 16).toInt()
        val offset = OFFSET_HISTORY_BITMAP + index * 2
        val bit = 1 shl (speciesId % 16).toInt()
        writeU16(bytes, offset, u16(bytes, offset) or bit)
    }

    private fun firstSetBit(mask: Int): Int {
        for (bit in 0 until 8) if (mask and (1 shl bit) != 0) return bit
        return 0
    }

    fun base64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    fun decodeBase64(encoded: String): ByteArray = java.util.Base64.getDecoder().decode(encoded)

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


}
