package com.github.nacabaro.vbhelper.battle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Paths for the small, app-owned battle presentation pack.
 *
 * The extractor folder is intentionally not referenced here. Everything used by
 * battles is copied into app/src/main/assets before packaging the APK.
 */
object BattleAssetPaths {
    const val BATTLE_ROOT = "battle_sprites"
    const val BATTLE_BACKGROUNDS = "$BATTLE_ROOT/extracted_battlebgs"
    const val ATTACK_SPRITES = "$BATTLE_ROOT/extracted_atksprites"
    const val HIT_SPRITES = "$BATTLE_ROOT/extracted_hit_sprites"
    const val CHARACTER_SPRITES = "$BATTLE_ROOT/extracted_assets/sprites"
    const val CHARACTER_DATA =
        "$BATTLE_ROOT/extracted_digimon_stats/character_data/MonoBehaviour_CharacterData.json"

    const val AUDIO_ROOT = "audio/game_sounds"
    const val HOME_MUSIC = "$AUDIO_ROOT/home.wav"
    const val BATTLE_MUSIC = "$AUDIO_ROOT/battle.wav"
    const val ATTACK_SOUND = "$AUDIO_ROOT/attack.wav"
    const val DAMAGE_SOUND = "$AUDIO_ROOT/damage.wav"

    fun background(fileName: String): String = "$BATTLE_BACKGROUNDS/$fileName"
    fun attack(fileName: String): String = "$ATTACK_SPRITES/$fileName.png"
    fun hit(fileName: String): String = "$HIT_SPRITES/$fileName.png"
    fun characterFrame(characterId: String, frameNumber: Int): String {
        val fileName = "${characterId}_${frameNumber.toString().padStart(2, '0')}.png"
        return "$CHARACTER_SPRITES/$characterId/$fileName"
    }
}

fun Context.decodeBattleAsset(assetPath: String): Bitmap? = runCatching {
    assets.open(assetPath).use { input -> BitmapFactory.decodeStream(input) }
}.getOrNull()
