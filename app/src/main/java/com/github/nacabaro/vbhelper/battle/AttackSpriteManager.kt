package com.github.nacabaro.vbhelper.battle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class AttackSpriteManager(private val context: Context) {
    fun getAttackSprite(characterId: String, isLarge: Boolean = false): Bitmap? {
        println("AttackSpriteManager: Getting attack sprite for characterId=$characterId, isLarge=$isLarge")
        try {
            val characterData = BattleCharacterCatalog.find(context, characterId)
            val attackFileName = when {
                characterData == null && isLarge -> "atk_l_04"
                characterData == null -> "atk_s_02"
                isLarge -> characterData.largeAttackFile
                else -> characterData.smallAttackFile
            }
            
            // Skip if no attack file
            if (attackFileName == "0") {
                println("AttackSpriteManager: Skipping attack file (filename is '0')")
                return null
            }
            
            return loadAttackAsset(attackFileName)
                ?: loadAttackAsset(if (isLarge) "atk_l_04" else "atk_s_02")
        } catch (e: Exception) {
            println("AttackSpriteManager: Exception occurred: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun loadAttackAsset(fileName: String): Bitmap? = runCatching {
        context.assets.open(BattleAssetPaths.attack(fileName)).use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull()
}
