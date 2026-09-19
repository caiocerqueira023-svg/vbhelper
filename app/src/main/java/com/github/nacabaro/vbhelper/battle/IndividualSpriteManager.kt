package com.github.nacabaro.vbhelper.battle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class IndividualSpriteManager(private val context: Context) {
    private val spriteCache = mutableMapOf<String, Bitmap>()
    
    /**
     * Load a specific sprite frame for a character
     * @param characterId The character ID (e.g., "dim012_mon03")
     * @param frameNumber The frame number (1-12)
     * @return Bitmap of the sprite frame, or null if not found
     */
    fun loadSpriteFrame(characterId: String, frameNumber: Int): Bitmap? {
        val cacheKey = "${characterId}_frame_${frameNumber}"
        
        // Check cache first
        if (spriteCache.containsKey(cacheKey)) {
            return spriteCache[cacheKey]
        }
        
        try {
            val assetPath = BattleAssetPaths.characterFrame(characterId, frameNumber)
            val bitmap = context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
            if (bitmap == null) {
                println("Failed to decode sprite asset: $assetPath")
                return null
            }
            
            // Cache the result
            spriteCache[cacheKey] = bitmap
            
            return bitmap
            
        } catch (e: Exception) {
            println("Error loading sprite frame: ${e.message}")
            e.printStackTrace()
            return null
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
