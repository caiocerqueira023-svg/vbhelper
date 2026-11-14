package com.tamerslab.farm.ui.digifarm

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.characters.Sprite
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.farm.FARM_MAX_RESIDENTS
import com.tamerslab.farm.FarmSpriteFilter
import com.tamerslab.species.BundledDotSpriteLibrary
import com.tamerslab.species.CanonicalSpeciesCatalog
import kotlin.random.Random

/**
 * Artwork resolution for the living diorama (`FarmHomeWorld.kt`).
 *
 * The database already stores the complete twelve-pose `Sprite` set for both VB/card artwork (through
 * the `DisplaySprite` view, which resolves the owner's appearance preference and substitutes idle
 * frames where a legacy row has no pose) and Dot artwork (installed from the bundled pack). The farm
 * only draws idle, walk, run, train, happy and sleep; attack/dodge stay battle-only.
 *
 * Selection is deliberately two-phase: availability is decided from cheap columns for *every*
 * candidate, then the full frame rows are loaded only for the residents that survived the artwork
 * filter, the cap and the seeded shuffle (at most [FARM_MAX_RESIDENTS]). A collection with hundreds
 * of stored partners therefore never loads hundreds of twelve-frame rows.
 */

/** Mixed into the hourly rotation bucket so the shuffle seed never equals the bucket itself. */
private const val ROTATION_SEED_XOR = 0x5bd1e995

/** Where a selected resident's full frame set will be read from once the roster is final. */
private sealed interface ArtworkSource {
    /** Card/VB artwork: the `DisplaySprite` row for the character's CardCharacter id. */
    data object Vb : ArtworkSource

    /** Bundled Dot artwork: an installed sprite set id resolved from the species catalog. */
    data class Dot(val setId: String) : ArtworkSource
}

/**
 * Cheap Dot availability probe for a whole selection pass: the bundled catalog is parsed once per
 * process by the library and kept here for the pass, so a Dot-only roster resolves it once instead of
 * once per stored character. No sprite bytes are read.
 */
private class DotCatalog(private val library: BundledDotSpriteLibrary?) {
    private var catalog: CanonicalSpeciesCatalog? = null

    /** The species' default Dot set id, or null when that artwork is not expanded/installed. */
    suspend fun setIdFor(character: CharacterDtos.CharacterWithSprites): String? {
        val source = library ?: return null
        val speciesName = character.speciesName?.takeIf(String::isNotBlank) ?: return null
        val resolved = catalog ?: source.catalog().also { catalog = it }
        val species = resolved.resolve(speciesName) ?: return null
        if (!resolved.isExpandedReady(species.id)) return null
        return species.dotSprites.defaultSetId
    }
}

/**
 * Applies the artwork filter, then the owner's resident limit (the active partner always keeps a
 * place), then maps the selection to renderer residents with their complete frame sets.
 *
 * Must be called on IO: the caller already wraps this in `withContext(Dispatchers.IO)`. The full
 * twelve-pose rows are read for at most [FARM_MAX_RESIDENTS] residents, after selection.
 */
internal suspend fun buildFarmRoster(
    candidates: List<CharacterDtos.CharacterWithSprites>,
    rotationBucket: Long,
    limit: Int,
    filter: FarmSpriteFilter,
    dotLibrary: BundledDotSpriteLibrary?,
    db: AppDatabase,
): List<FarmWorldResident> {
    val dot = DotCatalog(dotLibrary)

    // Phase 1 — availability only, no frame bytes. A stored character whose artwork is missing is
    // dropped exactly like before (except for the active partner, which may borrow the other set).
    val resolved = candidates.mapNotNull { character ->
        val source = preferredSource(character, filter, dot)
            ?: if (character.active) fallbackSource(character, filter, dot) else null
        source?.let { character to it }
    }

    // Phase 2 — cap and hourly seeded rotation, keeping the active partner in place.
    val capped = limit.coerceIn(1, FARM_MAX_RESIDENTS)
    val selected = if (resolved.size <= capped) {
        resolved
    } else {
        val keepActive = resolved.firstOrNull { it.first.active }
        val pool = if (keepActive != null) resolved.filterNot { it === keepActive } else resolved
        val random = Random((rotationBucket xor ROTATION_SEED_XOR.toLong()).toInt())
        val room = capped - (if (keepActive != null) 1 else 0)
        listOfNotNull(keepActive) + pool.shuffled(random).take(room.coerceAtLeast(0))
    }

    // Phase 3 — the complete frame set, for the selected residents only.
    return selected.mapNotNull { (character, source) ->
        val artwork = resolveArtwork(character, source, dotLibrary, db) ?: return@mapNotNull null
        FarmWorldResident(
            renderId = "farm-${character.id}",
            characterId = character.id,
            name = character.nickname?.takeIf(String::isNotBlank)
                ?: character.speciesName?.takeIf(String::isNotBlank)
                ?: "Digimon",
            attribute = character.attribute.name,
            isActivePartner = character.active,
            artwork = artwork,
        )
    }
}

/** The artwork the owner's filter asks for; null when that artwork has nothing for this species. */
private suspend fun preferredSource(
    character: CharacterDtos.CharacterWithSprites,
    filter: FarmSpriteFilter,
    dot: DotCatalog,
): ArtworkSource? = when (filter) {
    FarmSpriteFilter.MIXED, FarmSpriteFilter.VB_ONLY ->
        if (character.spriteIdle.isNotEmpty()) ArtworkSource.Vb else null
    FarmSpriteFilter.DOT_ONLY -> dot.setIdFor(character)?.let { ArtworkSource.Dot(it) }
}

/**
 * The leader is never filtered off the island by an artwork preference: if the chosen set has
 * nothing for it, fall back to whichever artwork exists rather than dropping it.
 */
private suspend fun fallbackSource(
    character: CharacterDtos.CharacterWithSprites,
    filter: FarmSpriteFilter,
    dot: DotCatalog,
): ArtworkSource? = when (filter) {
    FarmSpriteFilter.MIXED, FarmSpriteFilter.VB_ONLY -> dot.setIdFor(character)?.let { ArtworkSource.Dot(it) }
    FarmSpriteFilter.DOT_ONLY -> if (character.spriteIdle.isNotEmpty()) ArtworkSource.Vb else null
}

/** Reads a selected resident's complete frame set; null means the resident cannot be drawn. */
private suspend fun resolveArtwork(
    character: CharacterDtos.CharacterWithSprites,
    source: ArtworkSource,
    dotLibrary: BundledDotSpriteLibrary?,
    db: AppDatabase,
): FarmArtwork? = when (source) {
    ArtworkSource.Vb -> vbArtwork(character, db)
    is ArtworkSource.Dot -> {
        // Accept whatever frames the Dot Sprite carries: Dot sets map walk_1/walk_2 to idle frames
        // and may repeat idle for run, and the renderer dedupes repeated frames into one mesh.
        val dot = runCatching { dotLibrary?.sprite(source.setId) }.getOrNull()
            ?.let(::farmArtworkFromSprite)
        // On failure the leader falls back to VB artwork instead of disappearing; everyone else is
        // dropped, exactly like the previous four-frame resolver.
        dot ?: if (character.active) vbArtwork(character, db) else null
    }
}

/**
 * VB artwork: the `DisplaySprite` row resolves the owner's chosen appearance and fills every missing
 * pose with an idle frame. If no display row exists (old/partial data), the legacy four columns still
 * render the resident rather than dropping it.
 */
private suspend fun vbArtwork(
    character: CharacterDtos.CharacterWithSprites,
    db: AppDatabase,
): FarmArtwork? {
    val full = runCatching { db.spriteDao().getForCharacter(character.charId) }.getOrNull()
    if (full != null) return farmArtworkFromSprite(full)
    return if (character.spriteIdle.isNotEmpty()) farmArtworkFromCharacter(character) else null
}

/** Maps a stored or installed twelve-pose `Sprite` row to the frames the diorama draws. */
internal fun farmArtworkFromSprite(sprite: Sprite): FarmArtwork = FarmArtwork(
    idle1 = sprite.spriteIdle1,
    idle2 = sprite.spriteIdle2,
    walk1 = sprite.spriteWalk1,
    walk2 = sprite.spriteWalk2,
    run1 = sprite.spriteRun1,
    run2 = sprite.spriteRun2,
    train1 = sprite.spriteTrain1,
    train2 = sprite.spriteTrain2,
    happy = sprite.spriteHappy,
    sleep = sprite.spriteSleep,
    width = sprite.width,
    height = sprite.height,
)

/**
 * Legacy fallback for [CharacterDtos.CharacterWithSprites], which carries only four frames:
 * idle1/idle2 and run1/run2. Walk and train reuse the run frames, happy reuses idle and sleep reuses
 * idle2 — the exact mapping the farm used before the full pose set was available.
 */
internal fun farmArtworkFromCharacter(character: CharacterDtos.CharacterWithSprites): FarmArtwork = FarmArtwork(
    idle1 = character.spriteIdle,
    idle2 = character.spriteIdle2,
    walk1 = character.spriteRun1,
    walk2 = character.spriteRun2,
    run1 = character.spriteRun1,
    run2 = character.spriteRun2,
    train1 = character.spriteRun1,
    train2 = character.spriteRun2,
    happy = character.spriteIdle,
    sleep = character.spriteIdle2,
    width = character.spriteWidth,
    height = character.spriteHeight,
)
