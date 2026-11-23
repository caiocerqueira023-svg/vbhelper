package com.tamerslab.species

import android.graphics.Bitmap
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.characters.Sprite
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.createARGBIntArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared cosmetic priority for portraits, Arena fighters and result forms. Call on IO. */
class SpeciesAppearanceRepository(
    private val db: AppDatabase,
    private val library: BundledDotSpriteLibrary,
    private val preference: () -> SpritePreference,
) {
    data class Artwork(val sprite: Sprite? = null, val assetId: String? = null, val fallback: Boolean = false)

    suspend fun resolve(name: String, vbAssetId: String? = null): Artwork? {
        val catalog = library.catalog()
        val species = catalog.resolve(name) ?: db.speciesCustomizationDao()
            .getEntry(SpeciesCustomizationRepository.key(name))?.originalKey?.let(catalog::resolve)
        val current = preference()
        val dot = species?.takeIf { catalog.isExpandedReady(it.id) }?.dotSprites?.defaultSetId
        val choices = db.speciesCustomizationDao()
        val key = SpeciesCustomizationRepository.key(species?.name ?: name)
        val chosen = choices.getChosenSprite(key)
            ?: name.takeIf { SpeciesCustomizationRepository.key(it) != key }
                ?.let { choices.getChosenSprite(SpeciesCustomizationRepository.key(it)) }
        val chosenVb = chosen?.takeIf { it.id >= 0 }
        val chosenDot = chosen?.takeIf { it.id < 0 }
        val keys = (species?.aliases.orEmpty() + listOfNotNull(species?.name, name))
            .map(SpeciesCustomizationRepository::key).distinct()
        val imported = db.spriteDao().getOriginalForSpecies(species?.id, keys)
        // Loaded VB art wins; otherwise the VB frames shipped in the APK are still VB artwork and
        // outrank the Dot fallback in VB-first mode for a species the owner has not imported. Only
        // look the shipped artwork up when it can actually win, so loaded art/dot resolves stay cheap.
        val vbAsset = vbAssetId ?: when {
            current == SpritePreference.VB_FIRST && chosenVb == null && imported == null ->
                library.bundledVbSprites.assetIdFor(species)
            current == SpritePreference.DOT_FIRST && chosenDot == null && dot == null ->
                library.bundledVbSprites.assetIdFor(species)
            else -> null
        }
        return when (SpeciesArtworkPolicy.choose(current, chosenDot != null || dot != null, chosenVb != null, imported != null, vbAsset != null)) {
            SpeciesArtworkSource.VB_CHOICE -> Artwork(sprite = chosenVb)
            SpeciesArtworkSource.VB_IMPORTED -> Artwork(sprite = imported)
            SpeciesArtworkSource.VB_ASSET -> Artwork(assetId = vbAsset)
            SpeciesArtworkSource.DOT -> Artwork(sprite = chosenDot ?: library.sprite(requireNotNull(dot)))
            else -> null
        }
    }

    suspend fun spriteFor(name: String): Sprite? = resolve(name)?.sprite

    /**
     * The shipped VB frames as a byte-based [Sprite] when the artwork priority picks them (VB-first,
     * no loaded VB art), else null so byte projections keep their database artwork. [poses] limits the
     * decoded frames to what the caller renders. Species without shipped frames are rejected from the
     * in-memory catalog before any preference query, so Dot-only species stay cheap. Call on IO.
     */
    suspend fun bundledSprite(name: String, poses: IntArray): Sprite? {
        val catalog = library.catalog()
        val species = catalog.resolve(name) ?: return null
        if (catalog.bundledVbAssetIds(species).isEmpty()) return null
        val assetId = resolve(name)?.assetId ?: return null
        return withContext(Dispatchers.IO) { library.bundledVbSprites.sprite(assetId, poses) }
    }

    /** Shared read-only portrait path: source rosters must not install artwork rows just to browse. */
    suspend fun portrait(nameOrId: String): Bitmap? = withContext(Dispatchers.IO) {
        val catalog = library.catalog()
        val species = catalog.resolve(nameOrId) ?: return@withContext null
        val names = (listOf(species.name) + species.aliases).map(SpeciesCustomizationRepository::key).distinct()
        val chosen = names.firstNotNullOfOrNull { db.speciesCustomizationDao().getChosenSprite(it) }
        val chosenVb = chosen?.takeIf { it.id >= 0 }
        val chosenDot = chosen?.takeIf { it.id < 0 }
        val imported = db.spriteDao().getOriginalForSpecies(species.id, names)
        val dot = species.takeIf { catalog.isExpandedReady(species.id) }?.dotSprites?.defaultSetId
        val current = preference()
        val vbAsset = when {
            current == SpritePreference.VB_FIRST && chosenVb == null && imported == null ->
                library.bundledVbSprites.assetIdFor(species)
            current == SpritePreference.DOT_FIRST && chosenDot == null && dot == null ->
                library.bundledVbSprites.assetIdFor(species)
            else -> null
        }
        when (SpeciesArtworkPolicy.choose(current, chosenDot != null || dot != null, chosenVb != null,
            imported != null, vbAsset != null)) {
            SpeciesArtworkSource.VB_CHOICE -> chosenVb?.let(::spriteBitmap)
            SpeciesArtworkSource.VB_IMPORTED -> imported?.let(::spriteBitmap)
            // A shipped frame that fails to decode must not hide the species completely.
            SpeciesArtworkSource.VB_ASSET -> vbAsset?.let { library.bundledVbSprites.portrait(it) }
                ?: chosenDot?.let(::spriteBitmap) ?: dot?.let { library.portrait(it) }
            // Dot browsing reads the bundled pack directly; installing a row is a chooser action.
            SpeciesArtworkSource.DOT -> if (chosenDot != null) spriteBitmap(chosenDot)
                else dot?.let { library.portrait(it) }
            else -> null
        }
    }

    private fun spriteBitmap(sprite: Sprite): Bitmap = Bitmap.createBitmap(
        BitmapData(sprite.spriteIdle1, sprite.width, sprite.height).createARGBIntArray(),
        sprite.width, sprite.height, Bitmap.Config.ARGB_8888)
}
