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
        return when (SpeciesArtworkPolicy.choose(current, chosenDot != null || dot != null, chosenVb != null, imported != null, vbAssetId != null)) {
            SpeciesArtworkSource.VB_CHOICE -> Artwork(sprite = chosenVb)
            SpeciesArtworkSource.VB_IMPORTED -> Artwork(sprite = imported)
            SpeciesArtworkSource.VB_ASSET -> Artwork(assetId = vbAssetId)
            SpeciesArtworkSource.DOT -> Artwork(sprite = chosenDot ?: library.sprite(requireNotNull(dot)))
            else -> null
        }
    }

    suspend fun spriteFor(name: String): Sprite? = resolve(name)?.sprite

    /** Shared read-only portrait path: source rosters must not install artwork rows just to browse. */
    suspend fun portrait(nameOrId: String): Bitmap? = withContext(Dispatchers.IO) {
        val catalog = library.catalog()
        val species = catalog.resolve(nameOrId) ?: return@withContext null
        val dot = species.dotSprites.defaultSetId
        val names = (listOf(species.name) + species.aliases).map(SpeciesCustomizationRepository::key).distinct()
        val chosen = names.firstNotNullOfOrNull { db.speciesCustomizationDao().getChosenSprite(it) }
        val imported = db.spriteDao().getOriginalForSpecies(species.id, names)
        val sprite = when {
            preference() == SpritePreference.DOT_FIRST && chosen != null && chosen.id < 0 -> chosen
            preference() == SpritePreference.DOT_FIRST && dot != null -> return@withContext library.portrait(dot)
            chosen != null && chosen.id >= 0 -> chosen
            imported != null -> imported
            chosen != null -> chosen
            else -> null
        }
        if (sprite != null) Bitmap.createBitmap(BitmapData(sprite.spriteIdle1, sprite.width, sprite.height).createARGBIntArray(),
            sprite.width, sprite.height, Bitmap.Config.ARGB_8888)
        else dot?.let { library.portrait(it) }
    }
}
