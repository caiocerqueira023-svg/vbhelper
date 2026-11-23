package com.tamerslab.species

import com.tamerslab.domain.characters.Sprite
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.dtos.WorldDtos

/**
 * Byte-based artwork projections that predate the PNG path.
 *
 * Stored partners and world spawns carry raw RGB565 sprite bytes resolved by the `DisplaySprite` SQL
 * view. That view only knows database artwork, so an un-owned species with shipped VB frames still
 * reads as Dot there. These helpers re-apply the same artwork priority in Kotlin and swap in the
 * shipped VB frames when [SpeciesAppearanceRepository.bundledSprite] picks them; null leaves the
 * original projection untouched, so Dot-first and loaded VB art keep the database answer.
 *
 * Call on IO. One lookup per distinct species keeps a roster or a radar population cheap.
 */
object BundledVbArtwork {
    suspend fun characters(characters: List<CharacterDtos.CharacterWithSprites>,
        appearances: SpeciesAppearanceRepository?): List<CharacterDtos.CharacterWithSprites> {
        if (appearances == null || characters.isEmpty()) return characters
        val bySpecies = mutableMapOf<String, Sprite?>()
        return characters.map { character ->
            // Imported cards already resolve to their own loaded VB artwork in the display view.
            if (character.cardId != null) return@map character
            val name = character.speciesName?.takeIf(String::isNotBlank) ?: return@map character
            val sprite = bySpecies.getOrPut(name) {
                appearances.bundledSprite(name, VB_PARTNER_POSES)
            } ?: return@map character
            character.withBundledVbArtwork(sprite)
        }
    }

    suspend fun character(character: CharacterDtos.CharacterWithSprites,
        appearances: SpeciesAppearanceRepository?): CharacterDtos.CharacterWithSprites {
        appearances ?: return character
        if (character.cardId != null) return character
        val name = character.speciesName?.takeIf(String::isNotBlank) ?: return character
        val sprite = appearances.bundledSprite(name, VB_PARTNER_POSES) ?: return character
        return character.withBundledVbArtwork(sprite)
    }

    suspend fun spawns(spawns: List<WorldDtos.SpawnWithDetails>,
        appearances: SpeciesAppearanceRepository?): List<WorldDtos.SpawnWithDetails> {
        if (appearances == null || spawns.isEmpty()) return spawns
        val bySpecies = mutableMapOf<String, Sprite?>()
        return spawns.map { spawn ->
            // Imported-card spawns already resolve to their own VB artwork in the database view.
            if (spawn.cardId != null) return@map spawn
            val name = spawn.speciesName?.takeIf(String::isNotBlank) ?: return@map spawn
            val sprite = bySpecies.getOrPut(name) {
                appearances.bundledSprite(name, VB_WALK_POSES)
            } ?: return@map spawn
            spawn.withBundledVbArtwork(sprite)
        }
    }

    suspend fun spawn(spawn: WorldDtos.SpawnWithDetails,
        appearances: SpeciesAppearanceRepository?): WorldDtos.SpawnWithDetails {
        if (appearances == null || spawn.cardId != null) return spawn
        val name = spawn.speciesName?.takeIf(String::isNotBlank) ?: return spawn
        val sprite = appearances.bundledSprite(name, VB_WALK_POSES) ?: return spawn
        return spawn.withBundledVbArtwork(sprite)
    }

    /** Encounter-detail profile: Dot rows are replaced by the shipped VB frames in VB-first mode. */
    suspend fun progress(progress: CharacterDtos.CardCharaProgress?, speciesName: String?,
        appearances: SpeciesAppearanceRepository?): CharacterDtos.CardCharaProgress? {
        if (progress == null || appearances == null) return progress
        val name = speciesName?.takeIf(String::isNotBlank) ?: return progress
        val sprite = appearances.bundledSprite(name, VB_IDLE_POSES) ?: return progress
        return progress.withBundledVbArtwork(sprite)
    }

    /** Scan-collection rows: same Dot-to-shipped-VB replacement, one lookup per species. */
    suspend fun scanEntries(entries: List<com.tamerslab.dtos.DigimonScanEntry>,
        appearances: SpeciesAppearanceRepository?): List<com.tamerslab.dtos.DigimonScanEntry> {
        if (appearances == null || entries.isEmpty()) return entries
        val bySpecies = mutableMapOf<String, Sprite?>()
        return entries.map { entry ->
            val name = entry.speciesName?.takeIf(String::isNotBlank) ?: return@map entry
            val sprite = bySpecies.getOrPut(name) {
                appearances.bundledSprite(name, VB_IDLE_POSES)
            } ?: return@map entry
            entry.copy(character = entry.character.withBundledVbArtwork(sprite))
        }
    }

    /** DigiLine thread rows: the list avatar is one idle frame. */
    suspend fun digilineThreads(threads: List<com.tamerslab.dtos.DigilineStorageThread>,
        appearances: SpeciesAppearanceRepository?): List<com.tamerslab.dtos.DigilineStorageThread> {
        if (appearances == null || threads.isEmpty()) return threads
        val bySpecies = mutableMapOf<String, Sprite?>()
        return threads.map { thread ->
            if (thread.cardId != null) return@map thread
            val name = thread.speciesName?.takeIf(String::isNotBlank) ?: return@map thread
            val sprite = bySpecies.getOrPut(name) {
                appearances.bundledSprite(name, VB_IDLE_POSES)
            } ?: return@map thread
            thread.withBundledVbArtwork(sprite)
        }
    }
}

/** Stored-partner projection over the four frames its UI consumers animate. */
internal fun CharacterDtos.CharacterWithSprites.withBundledVbArtwork(
    sprite: Sprite,
): CharacterDtos.CharacterWithSprites = copy(
    spriteIdle = sprite.spriteIdle1,
    spriteIdle2 = sprite.spriteIdle2,
    spriteRun1 = sprite.spriteRun1,
    spriteRun2 = sprite.spriteRun2,
    spriteWidth = sprite.width,
    spriteHeight = sprite.height,
)

/** World-spawn projection over the idle/walk/run frames the radar and encounter UIs animate. */
internal fun WorldDtos.SpawnWithDetails.withBundledVbArtwork(
    sprite: Sprite,
): WorldDtos.SpawnWithDetails = copy(
    spriteIdle = sprite.spriteIdle1,
    spriteIdle2 = sprite.spriteIdle2,
    spriteWalk = sprite.spriteWalk1,
    spriteWalk2 = sprite.spriteWalk2,
    spriteRun = sprite.spriteRun1,
    spriteRun2 = sprite.spriteRun2,
    spriteWidth = sprite.width,
    spriteHeight = sprite.height,
)

/** Home-screen widget projection over the two idle and two walk frames it alternates. */
internal fun CharacterDtos.WidgetSprites.withBundledVbArtwork(
    sprite: Sprite,
): CharacterDtos.WidgetSprites = CharacterDtos.WidgetSprites(
    spriteIdle1 = sprite.spriteIdle1,
    spriteIdle2 = sprite.spriteIdle2,
    spriteWalk1 = sprite.spriteWalk1,
    spriteWalk2 = sprite.spriteWalk2,
    width = sprite.width,
    height = sprite.height,
)

/** Encounter-detail projection: the radar's long-press profile shows the two idle frames. */
internal fun CharacterDtos.CardCharaProgress.withBundledVbArtwork(
    sprite: Sprite,
): CharacterDtos.CardCharaProgress = copy(
    spriteIdle = sprite.spriteIdle1,
    spriteIdle2 = sprite.spriteIdle2,
    spriteWidth = sprite.width,
    spriteHeight = sprite.height,
)

/** DigiLine thread avatar: one shipped idle frame. */
internal fun com.tamerslab.dtos.DigilineStorageThread.withBundledVbArtwork(
    sprite: Sprite,
): com.tamerslab.dtos.DigilineStorageThread = copy(
    spriteIdle = sprite.spriteIdle1,
    spriteWidth = sprite.width,
    spriteHeight = sprite.height,
)
