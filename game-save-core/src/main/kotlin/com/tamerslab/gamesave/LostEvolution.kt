package com.tamerslab.gamesave

/**
 * Digimon Story: Lost Evolution (Japan with the Operation Decoded English translation) — layout
 * facts recovered from the ROM's own code.
 *
 * Evidence: `LOST_EVOLUTION_RESEARCH.md`. Container: 64 KiB, two 0x8000-byte card slots, MD5
 * header over the slot with the 16 header bytes zeroed, `"DM3_000"` signature at +0x10, u32 write
 * sequence at +0x18. Everything after +0x20 is the game's player-state image; the 40 individual
 * records sit at 0x1850 (player-state +0x1830) with stride 0x78. Records carry a u64 identity
 * (low32 = unique id, high32 = 0x030405F0 marker), the species id at +0x0C, an 18-byte name at
 * +0x0E, explicit level at +0x3C, and stat words at +0x28..+0x34/+0x3D.
 *
 * Record creation code: ARM9 `FUN_020b0100` (species parameter block: HP@+6, MP@+8, ATK@+0xA,
 * DEF@+0xC, SPI@+0xE, SPD@+0x11, six skills@+0x1E; name record at +0xE of the paired table).
 * Roster accessor `FUN_020ade64`/`FUN_020ade88`, roster init `FUN_020aff40` (40 x 0x78).
 * Party container: player-state +0x3AC (8 x u64 identities at +0x1C + i*8, functions
 * `FUN_020afbc8`/`FUN_020afc54`/`FUN_020afc90`). Farm: player-state +0x11D0 (group count, 4 groups
 * x 0xD0, 8 resident identities at +0x18). Team container: player-state +0x157C (8 x u64).
 * Next identity: u64 at player-state +0x4070 (8 bytes: low32 next id, high32 marker).
 */
object LostEvolution {
    const val GAME_ID: String = "digimon_story_lost_evolution_nds"
    const val FORMAT_PROFILE: String = "le-jp-en-raw-v1"
    const val CODEC_VERSION: Int = 1

    const val FILE_BYTES: Int = 65536
    const val SAVE_BYTES: Int = 0x8000
    const val COPY_STRIDE: Int = 0x8000
    const val HEADER_BYTES: Int = 16
    const val TAG_OFFSET: Int = 0x10
    /** ASCII signature the writer stores at tagOffset; verification only, never a guess. */
    val TAG: ByteArray = byteArrayOf('D'.code.toByte(), 'M'.code.toByte(), '3'.code.toByte(),
        '_'.code.toByte(), '0'.code.toByte(), '0'.code.toByte(), '0'.code.toByte(), 0)
    const val SEQUENCE_OFFSET: Int = 0x18
    /** Save bytes before the serialized player state begins. */
    const val STATE_BASE: Int = 0x20

    // Record windows (relative to one record).
    const val RECORD_BYTES: Int = 0x78
    const val RECORD_COUNT: Int = 40
    const val OFFSET_IDENTITY: Int = 0x00
    /** u32 copy of the identity's high word; every game-written record repeats it here. */
    const val OFFSET_IDENTITY_MARKER: Int = 0x08
    const val OFFSET_IDENTITY_COPY: Int = 0x54
    const val OFFSET_SPECIES: Int = 0x0C
    const val OFFSET_NAME: Int = 0x0E
    const val NAME_BYTES: Int = 18
    const val OFFSET_VARIANT: Int = 0x20
    const val OFFSET_PENDING_SKILLS: Int = 0x21
    const val OFFSET_FLAGS: Int = 0x24
    const val OFFSET_HP: Int = 0x28
    const val OFFSET_MAX_HP: Int = 0x2A
    const val OFFSET_MP: Int = 0x2C
    const val OFFSET_MAX_MP: Int = 0x2E
    const val OFFSET_ATTACK: Int = 0x30
    const val OFFSET_DEFENSE: Int = 0x32
    const val OFFSET_SPIRIT: Int = 0x34
    const val OFFSET_FRIENDSHIP: Int = 0x38
    const val OFFSET_APTITUDE: Int = 0x3A
    const val OFFSET_RANK: Int = 0x3B
    const val OFFSET_LEVEL: Int = 0x3C
    const val OFFSET_SPEED: Int = 0x3D
    const val OFFSET_EXP: Int = 0x40
    const val OFFSET_SKILLS: Int = 0x46
    const val SKILL_SLOTS: Int = 6
    const val OFFSET_TAIL_SLOTS: Int = 0x5C
    const val MAX_LEVEL: Int = 99

    const val IDENTITY_MARKER: Long = 0x030405F0L

    /**
     * Species encyclopedia book: one u64 per species at player-state +0x460 + species*8. Every
     * registered/owned species carries 0x4000000100000000 (verified in genuine saves and in the
     * record the game writes when its own creation path registers a Digimon).
     */
    const val SPECIES_BOOK_BASE: Int = 0x460
    const val SPECIES_BOOK_SAVE_BASE: Int = STATE_BASE + SPECIES_BOOK_BASE
    const val SPECIES_BOOK_OWNED: Long = 0x4000000100000000L

    /** Serialized player-state length measured by the engine's checksum (save +0x20..+0x8000). */
    const val PLAYER_STATE_SLOT_BYTES: Int = 0x7FE0
    /** Player-state word holding the XOR checksum (save offset +0x4098). */
    const val STATE_CHECKSUM_OFFSET: Int = 0x4078
    /** Every genuine game-written slot satisfies XOR(all player-state words) == 0xFFFFFFFF. */
    const val STATE_CHECKSUM_XOR: Int = -1

    // Player-state windows (relative to the state start; save offset = STATE_BASE + this).
    const val ROSTER_BASE: Int = 0x1830
    const val NEXT_ID: Int = 0x4070
    const val PARTNER: Int = 0x3A8
    const val PARTY_BASE: Int = 0x3C8
    const val PARTY_SLOTS: Int = 8
    const val FARM_BASE: Int = 0x11D0
    const val FARM_GROUPS_MAX: Int = 4
    const val FARM_GROUP_STRIDE: Int = 0xD0
    const val FARM_RESIDENTS: Int = 8
    const val FARM_RESIDENT_OFFSET: Int = 0x18
    const val TEAM_BASE: Int = 0x157C
    const val TEAM_SLOTS: Int = 8
    /** Second farm identity list (8 x 0xC: low, high, flags, 0xFF empty marker at +0xA). */
    const val FARM_VISITORS_BASE: Int = 0x1514
    const val FARM_VISITORS_SLOTS: Int = 8
    const val FARM_VISITORS_STRIDE: Int = 0x0C
    const val FARM_VISITORS_EMPTY: Int = 0xFF
    const val PLAYER_STATE_BYTES: Int = 0x407C

    /** Save offsets, i.e. [STATE_BASE] + player-state window. */
    const val ROSTER_SAVE_BASE: Int = STATE_BASE + ROSTER_BASE
    const val PARTY_SAVE_BASE: Int = STATE_BASE + PARTY_BASE
    const val FARM_SAVE_BASE: Int = STATE_BASE + FARM_BASE
    const val TEAM_SAVE_BASE: Int = STATE_BASE + TEAM_BASE
    const val FARM_VISITORS_SAVE_BASE: Int = STATE_BASE + FARM_VISITORS_BASE
    const val PARTNER_SAVE_OFFSET: Int = STATE_BASE + PARTNER
    const val NEXT_ID_SAVE_OFFSET: Int = STATE_BASE + NEXT_ID

    /** Species id space: 0..53 are DigiEggs, Digimon start at 54; the id table has 743 entries. */
    const val FIRST_DIGIMON: Int = 54
    const val SPECIES_COUNT: Int = 743

    /** True when a record slot holds an individual (empty slots are zero-filled). */
    fun isPopulated(record: ByteArray): Boolean =
        record.size == RECORD_BYTES && (u64(record, OFFSET_IDENTITY) != 0L)

    /** The u64 identity of one record. Low32 is the unique id, high32 the 0x030405F0 marker. */
    fun identity(record: ByteArray): Long = u64(record, OFFSET_IDENTITY)

    /**
     * Marks [species] as registered in the encyclopedia, preserving bits the game already set
     * (creation calls `FUN_020ae72c`/`FUN_020ae7ac` on this book). [image] is one complete save and
     * [copyBase] the card copy being edited.
     */
    fun registerSpecies(image: ByteArray, copyBase: Int, species: Int) {
        require(species in 0 until SPECIES_COUNT) { "Species outside the Lost Evolution range" }
        val at = copyBase + SPECIES_BOOK_SAVE_BASE + species * 8
        putU64(image, at, u64(image, at) or SPECIES_BOOK_OWNED)
    }

    /**
     * Recomputes the player-state XOR checksum the engine stores at player +0x4078
     * (`FUN_020ace5c`/`FUN_020ad328`): the game sets the word to 0xFFFFFFFF, XORs every u32 of the
     * 0x7FE0-byte serialized state and stores the result, so a valid state XORs to 0xFFFFFFFF.
     * A modified save that leaves this word stale is rejected after loading.
     *
     * [slot] is one complete 0x8000-byte card copy.
     */
    fun recomputeStateChecksum(slot: ByteArray) {
        require(slot.size >= STATE_BASE + PLAYER_STATE_SLOT_BYTES) { "A slot is 0x8000 bytes" }
        putU32(slot, STATE_BASE + STATE_CHECKSUM_OFFSET, 0xFFFFFFFFL)
        var accumulator = 0
        var offset = STATE_BASE
        val end = STATE_BASE + PLAYER_STATE_SLOT_BYTES
        while (offset < end) {
            accumulator = accumulator xor u32(slot, offset).toInt()
            offset += 4
        }
        putU32(slot, STATE_BASE + STATE_CHECKSUM_OFFSET, accumulator.toLong() and 0xFFFFFFFFL)
    }

    /** Whether a slot's player-state XOR checksum equals the engine's 0xFFFFFFFF invariant. */
    fun stateChecksumValid(slot: ByteArray): Boolean {
        var accumulator = 0
        var offset = STATE_BASE
        val end = STATE_BASE + PLAYER_STATE_SLOT_BYTES
        while (offset < end) {
            accumulator = accumulator xor u32(slot, offset).toInt()
            offset += 4
        }
        return accumulator == STATE_CHECKSUM_XOR
    }

    /**
     * Writes every identity window of a record exactly like the engine: the u64 at +0x00, the marker
     * copy at +0x08, the u64 copy at +0x54/+0x58, and (for legacy records that still have the
     * all-zero tail) the six 0xFFFF tail slots. Game-written records always satisfy these.
     */
    fun stampIdentity(record: ByteArray, identity: Long) {
        putU64(record, OFFSET_IDENTITY, identity)
        putU32(record, OFFSET_IDENTITY_MARKER, (identity ushr 32) and 0xFFFFFFFFL)
        putU32(record, OFFSET_IDENTITY_COPY, identity and 0xFFFFFFFFL)
        putU32(record, OFFSET_IDENTITY_COPY + 4, (identity ushr 32) and 0xFFFFFFFFL)
        if ((0 until SKILL_SLOTS).all { u16(record, OFFSET_TAIL_SLOTS + it * 2) == 0 }) {
            for (slot in 0 until SKILL_SLOTS) putU16(record, OFFSET_TAIL_SLOTS + slot * 2, 0xFFFF)
        }
    }

    fun identityText(identity: Long): String = "save-local:" + identity.toString(16)

    fun parseIdentityText(text: String?): Long? =
        text?.removePrefix("save-local:")?.toLongOrNull(16)

    fun species(record: ByteArray): Int = u16(record, OFFSET_SPECIES)

    fun level(record: ByteArray): Int = record[OFFSET_LEVEL].toInt() and 0xFF

    fun exp(record: ByteArray): Long = u32(record, OFFSET_EXP)

    fun nameBytes(record: ByteArray): ByteArray {
        val raw = record.copyOfRange(OFFSET_NAME, OFFSET_NAME + NAME_BYTES)
        val end = raw.indexOf(0)
        return if (end < 0) raw else raw.copyOf(end)
    }

    /** The record's own stat words, read in the order the engine stores them. */
    fun stats(record: ByteArray): NdsStoryNativeValues = NdsStoryNativeValues(
        hp = u16(record, OFFSET_HP),
        maxHp = u16(record, OFFSET_MAX_HP),
        mp = u16(record, OFFSET_MP),
        maxMp = u16(record, OFFSET_MAX_MP),
        attack = u16(record, OFFSET_ATTACK),
        defense = u16(record, OFFSET_DEFENSE),
        spirit = u16(record, OFFSET_SPIRIT),
        speed = u16(record, OFFSET_SPEED),
        aptitude = record[OFFSET_APTITUDE].toInt() and 0xFF,
    )

    fun u16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    fun u32(bytes: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { value, index ->
            value or ((bytes[at + index].toLong() and 0xFF) shl (8 * index))
        }

    fun u64(bytes: ByteArray, at: Int): Long =
        (0 until 8).fold(0L) { value, index ->
            value or ((bytes[at + index].toLong() and 0xFF) shl (8 * index))
        }

    fun putU16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value and 0xFF).toByte()
        bytes[at + 1] = ((value shr 8) and 0xFF).toByte()
    }

    fun putU32(bytes: ByteArray, at: Int, value: Long) {
        for (index in 0 until 4) bytes[at + index] = ((value shr (8 * index)) and 0xFF).toByte()
    }

    fun putU64(bytes: ByteArray, at: Int, value: Long) {
        for (index in 0 until 8) bytes[at + index] = ((value shr (8 * index)) and 0xFF).toByte()
    }


    /**
     * Builds one 0x78 record the way the engine's creation code (ARM9 FUN_020b0100) shapes it.
     * A [seed] keeps the record's identity and every unknown window; only the documented fields are
     * rewritten. A fresh record has identity 0, which the editor replaces with the next game id.
     */
    fun buildRecord(
        speciesId: Long,
        displayName: String,
        fallbackName: String,
        level: Int,
        hp: Int,
        maxHp: Int,
        mp: Int,
        maxMp: Int,
        attack: Int,
        defense: Int,
        spirit: Int,
        speed: Int,
        aptitude: Int,
        skills: List<Int>,
        exp: Long = 0,
        seed: ByteArray? = null,
    ): ByteArray {
        require(speciesId in 0 until SPECIES_COUNT.toLong()) { "Species outside the Lost Evolution range" }
        require(level in 1..MAX_LEVEL) { "Level outside 1..99" }
        require(maxHp in 1..9999) { "HP outside the record range" }
        require(maxMp in 0..9999) { "MP outside the record range" }
        require(listOf(attack, defense, spirit).all { it in 1..999 }) { "Combat stats outside the record range" }
        require(speed in 1..99) { "Speed outside the record range" }
        val bytes = seed?.copyOf() ?: ByteArray(RECORD_BYTES)
        require(bytes.size == RECORD_BYTES) { "A seeded record is exactly 0x78 bytes" }
        val speciesChanged = seed == null || species(bytes) != speciesId.toInt()
        putU16(bytes, OFFSET_SPECIES, speciesId.toInt())
        if (speciesChanged) {
            val name = encodeName(displayName) ?: encodeName(fallbackName)
                ?: throw IllegalArgumentException("The record name has no encodable characters")
            name.copyInto(bytes, OFFSET_NAME)
            bytes[OFFSET_VARIANT] = 0
            putU32(bytes, OFFSET_FLAGS, 0x40)
            putU16(bytes, OFFSET_FRIENDSHIP, 100)
            bytes[OFFSET_APTITUDE] = aptitude.coerceIn(1, 255).toByte()
            bytes[OFFSET_RANK] = 5
            putU32(bytes, OFFSET_EXP, 0)
            for (slot in 0 until SKILL_SLOTS) putU16(bytes, OFFSET_SKILLS + slot * 2, 0xFFFF)
            // The engine initializes the six-slot tail sub-structure to 0xFFFF (FUN_020b15e8).
            for (slot in 0 until SKILL_SLOTS) putU16(bytes, OFFSET_TAIL_SLOTS + slot * 2, 0xFFFF)
            skills.filter { it in 0..0xFFFE }.take(SKILL_SLOTS).forEachIndexed { index, skill ->
                putU16(bytes, OFFSET_SKILLS + index * 2, skill)
            }
        }
        putU16(bytes, OFFSET_HP, hp.coerceIn(0, maxHp))
        putU16(bytes, OFFSET_MAX_HP, maxHp)
        putU16(bytes, OFFSET_MP, mp.coerceIn(0, maxMp))
        putU16(bytes, OFFSET_MAX_MP, maxMp)
        putU16(bytes, OFFSET_ATTACK, attack)
        putU16(bytes, OFFSET_DEFENSE, defense)
        putU16(bytes, OFFSET_SPIRIT, spirit)
        bytes[OFFSET_SPEED] = speed.toByte()
        bytes[OFFSET_LEVEL] = level.toByte()
        putU32(bytes, OFFSET_EXP, exp.coerceIn(0, 0xFFFFFFFFL))
        if (seed == null) {
            // A fresh record has identity 0; the editor stamps the allocated identity later. The
            // marker window still carries the game's marker word.
            putU32(bytes, OFFSET_IDENTITY_MARKER, IDENTITY_MARKER)
        } else {
            putU32(bytes, OFFSET_IDENTITY_MARKER,
                (u64(bytes, OFFSET_IDENTITY) ushr 32) and 0xFFFFFFFFL)
            if ((0 until SKILL_SLOTS).all { u16(bytes, OFFSET_TAIL_SLOTS + it * 2) == 0 }) {
                for (slot in 0 until SKILL_SLOTS) putU16(bytes, OFFSET_TAIL_SLOTS + slot * 2, 0xFFFF)
            }
        }
        return bytes
    }

    private fun encodeName(text: String): ByteArray? {
        val codes = LostEvolutionTextCodec.encode(text, NAME_BYTES - 1).codes
        if (codes.isEmpty()) return null
        return ByteArray(NAME_BYTES) { index -> if (index < codes.size) codes[index].toByte() else 0 }
    }

    fun base64Encode(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    fun base64Decode(text: String): ByteArray = java.util.Base64.getDecoder().decode(text)

    /** The display name stored in a record's 18-byte window. */
    fun decodeName(record: ByteArray): String =
        LostEvolutionTextCodec.decode((0 until NAME_BYTES).map {
            record[OFFSET_NAME + it].toInt() and 0xFF
        })

    fun profile(): NdsStoryProfile = NdsStoryProfile(
        gameId = GAME_ID,
        formatProfile = FORMAT_PROFILE,
        codecVersion = CODEC_VERSION,
        layoutId = "le-jp-en-raw-v1",
        layoutEvidence = "BLEJ",
        fileBytes = FILE_BYTES,
        saveBytes = SAVE_BYTES,
        copyStride = COPY_STRIDE,
        copyCount = 2,
        wrapperSlackBytes = 0,
        imageStep = 4,
        copyHeader = NdsStoryCopyHeaderRule(digest = "MD5", headerBytes = HEADER_BYTES,
            sequenceOffset = SEQUENCE_OFFSET, sequenceBytes = 4),
        signature = TAG.toList(),
        recordRegion = NdsStoryRecordRegion(base = ROSTER_SAVE_BASE, stride = RECORD_BYTES,
            count = RECORD_COUNT),
        partyReferenceBase = PARTY_SAVE_BASE,
        partySlots = PARTY_SLOTS,
        partyVisibleCap = PARTY_SLOTS,
        farm = null,
        item = null,
        miscReferenceOffset = null,
        liveFlagBits = 0,
        requireSignature = true,
        resolveEquivalentCopyTie = true,
        lastIdOffset = NEXT_ID_SAVE_OFFSET,
        idReserved = 0xFFFFFF00L,
        emptyRecordId = 0L,
        speciesCount = SPECIES_COUNT,
        record = recordLayout(),
        speciesData = LostEvolutionSpeciesData,
        text = LostEvolutionTextCodec,
        nativeWriterAvailable = true,
        farmRecordsMovable = true,
    )

    private fun recordLayout(): NdsStoryRecordLayout = NdsStoryRecordLayout(
        bytes = RECORD_BYTES,
        nameSlots = NAME_BYTES,
        nameTerminator = 0,
        formSlots = 0,
        skillSlots = SKILL_SLOTS,
        abilitySlots = 0,
        resistanceSlots = 0,
        expChannels = 1,
        unk70Native = 0,
        unk108Native = 0,
        offsetLocalId = OFFSET_IDENTITY,
        offsetSpeciesId = OFFSET_SPECIES,
        offsetName = OFFSET_NAME,
        offsetUnk1A = 0,
        offsetExpCurve = 0,
        offsetGrowth = 0,
        offsetUnk1D = 0,
        offsetStage = OFFSET_VARIANT,
        offsetAbilities = 0,
        offsetAbilitySources = 0,
        offsetHp = OFFSET_HP,
        offsetMaxHp = OFFSET_MAX_HP,
        offsetMp = OFFSET_MP,
        offsetMaxMp = OFFSET_MAX_MP,
        offsetAttack = OFFSET_ATTACK,
        offsetDefense = OFFSET_DEFENSE,
        offsetSpirit = OFFSET_SPIRIT,
        offsetSpeed = OFFSET_SPEED,
        offsetUnk50 = 0,
        offsetMaxLevel = OFFSET_APTITUDE,
        offsetSkills = OFFSET_SKILLS,
        offsetUnk70 = 0,
        offsetLevel = OFFSET_LEVEL,
        offsetExp = OFFSET_EXP,
        offsetExpToNext = 0,
        offsetHistory = 0,
        offsetUnk100 = 0,
        offsetFlags = OFFSET_FLAGS,
        offsetUnk108 = 0,
        historyUnk0 = 0,
        historyForms = 0,
        historyStage = 0,
        historyUnk4F = 0,
        historyUnk50 = 0,
        historyResistances = 0,
    )
}

/** Native stat window values read from a Lost Evolution record (units are the game's own). */
data class NdsStoryNativeValues(
    val hp: Int,
    val maxHp: Int,
    val mp: Int,
    val maxMp: Int,
    val attack: Int,
    val defense: Int,
    val spirit: Int,
    val speed: Int,
    val aptitude: Int,
)

/**
 * Names are stored as bytes: ASCII for the translated names, full-width Shift-JIS for names the
 * player typed. Decoding reproduces the stored text; encoding writes plain ASCII, which the
 * translation renders correctly and the game accepts as a name.
 */
object LostEvolutionTextCodec : NdsStoryTextCodec {
    override fun decode(codes: List<Int>): String {
        val raw = codes.take(LostEvolution.NAME_BYTES).map { (it and 0xFF).toByte() }.toByteArray()
        val end = raw.indexOf(0)
        val bytes = if (end < 0) raw else raw.copyOf(end)
        if (bytes.isEmpty()) return ""
        if (bytes.any { (it.toInt() and 0xFF) in 0x81..0x9F || (it.toInt() and 0xFF) in 0xE0..0xEF }) {
            return runCatching { String(bytes, charset("Shift_JIS")) }
                .getOrElse { bytes.map { (it.toInt() and 0xFF).toChar() }.joinToString("") }
        }
        return bytes.map { (it.toInt() and 0xFF).toChar() }.joinToString("")
    }

    override fun encode(text: String, maxChars: Int): NdsStoryNameEncoding {
        val codes = mutableListOf<Int>()
        var fallback = false
        for (char in text) {
            if (codes.size == maxChars) break
            if (char.code in 0x20..0x7E) codes += char.code else fallback = true
        }
        return NdsStoryNameEncoding(codes, fallback, text.length > maxChars)
    }

    /** Bytes written into the fixed 18-byte name window (null-terminated, ASCII only). */
    fun encodeBytes(text: String): ByteArray {
        val codes = encode(text, LostEvolution.NAME_BYTES - 1).codes
        return ByteArray(LostEvolution.NAME_BYTES) { index ->
            if (index < codes.size) codes[index].toByte() else 0
        }
    }
}

/**
 * Stage/curve facts for Lost Evolution. The app-level species asset carries the real tables; the
 * profile only needs the identity bands so a record's species id can be validated.
 */
object LostEvolutionSpeciesData : NdsStorySpeciesData {
    override fun nativeStage(speciesId: Long): Int = when (speciesId) {
        in 0L..53L -> 0            // DigiEggs (ids 0..53)
        in 54L..76L -> 0           // in-training / baby (stage-ordered ROM table)
        in 77L..116L -> 1          // rookie
        in 117L..176L -> 2         // champion
        in 177L..300L -> 3         // ultimate
        in 301L..367L -> 4         // mega
        else -> 0
    }

    override fun recordStage(speciesId: Long): Int = nativeStage(speciesId)
    override fun expCurve(speciesId: Long): Int = 0
    override fun growth(speciesId: Long): Int = 0
    override fun expToNext(curve: Int, level: Int, maxLevel: Int): Int = 0
}
