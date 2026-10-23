package com.github.nacabaro.vbhelper.battle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.species.AssetSpeciesNames
import com.github.nacabaro.vbhelper.species.SpeciesAppearanceRepository
import com.github.nacabaro.vbhelper.species.displayBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class IndividualSpriteManager(private val context: Context) {
    private val spriteCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val app get() = context.applicationContext as? VBHelper
    private val appearances by lazy { app?.container?.let { container ->
        SpeciesAppearanceRepository(container.db, container.dotSpriteLibrary) { container.spritePreferenceRepository.current }
    } }
    
    /**
     * Load a specific sprite frame for a character
     * @param characterId The character ID (e.g., "dim012_mon03")
     * @param frameNumber The frame number (1-12)
     * @return Bitmap of the sprite frame, or null if not found
     */
    suspend fun loadSpriteFrame(characterId: String, frameNumber: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val name = AssetSpeciesNames.nameFor(context, characterId)
            val preferred = name?.let { appearances?.resolve(it, characterId)?.sprite }
            val cacheKey = "${characterId}_${app?.container?.spritePreferenceRepository?.current}_sprite_${preferred?.id}_frame_${frameNumber}"
            spriteCache[cacheKey]?.let { return@withContext it }
            if (preferred != null) {
                val bitmap = preferred.displayBitmap(frameNumber) ?: return@withContext null
                spriteCache[cacheKey] = bitmap
                return@withContext bitmap
            }
            val assetPath = BattleAssetPaths.characterFrame(characterId, frameNumber)
            val bitmap = context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
            if (bitmap == null) {
                println("Failed to decode sprite asset: $assetPath")
                return@withContext null
            }
            
            // Cache the result
            spriteCache[cacheKey] = bitmap
            
            return@withContext bitmap
            
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("Error loading sprite frame: ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }
    
    /**
     * Get all available sprite frames for a character
     * @param characterId The character ID
     * @return List of frame numbers (1-12) that exist for this character
     */
    fun getAvailableFrames(characterId: String): List<Int> {
        val files = context.assets.list("${BattleAssetPaths.CHARACTER_SPRITES}/$characterId")
            ?: return emptyList()
        return files.mapNotNull { fileName ->
            Regex("${Regex.escape(characterId)}_(\\d{2})\\.png")
                .matchEntire(fileName)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        }.sorted()
    }

    suspend fun getAvailableDisplayFrames(characterId: String): List<Int> = withContext(Dispatchers.IO) {
        val name = AssetSpeciesNames.nameFor(context, characterId)
        val preferred = name?.let { appearances?.resolve(it, characterId)?.sprite }
        if (preferred != null) (1..12).toList() else getAvailableFrames(characterId)
    }
    
    /**
     * Get all available character IDs
     * @return List of character IDs that have sprite directories
     */
    fun getAvailableCharacters(): List<String> {
        return context.assets.list(BattleAssetPaths.CHARACTER_SPRITES)
            ?.filter { characterId ->
                context.assets.list("${BattleAssetPaths.CHARACTER_SPRITES}/$characterId")
                    ?.any { it.endsWith(".png") } == true
            }
            ?.sorted()
            ?: emptyList()
    }
    
    /**
     * Clear the sprite cache
     */
    fun clearCache() {
        spriteCache.clear()
    }
    
    /**
     * Check if a character has sprite files
     * @param characterId The character ID to check
     * @return true if the character has sprite files, false otherwise
     */
    fun hasCharacterSprites(characterId: String): Boolean {
        return getAvailableFrames(characterId).isNotEmpty()
    }
}
