package com.github.nacabaro.vbhelper.species

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import com.github.nacabaro.vbhelper.battle.BattleAssetPaths
import timber.log.Timber

fun bundledOfficialSpriteMatcher(assets: AssetManager): OfficialSpriteMatcher = OfficialSpriteMatcher(
    characterIds = { assets.list(BattleAssetPaths.CHARACTER_SPRITES).orEmpty().toList() },
    loadFrame = { id, frame ->
        val path = BattleAssetPaths.characterFrame(id, frame)
        runCatching {
            assets.open(path).use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return@use null
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    SpriteMatchFrame.fromArgb(bitmap.width, bitmap.height, pixels)
                } finally { bitmap.recycle() }
            }
        }.onFailure { Timber.w(it, "Could not read official species sprite %s", path) }.getOrNull()
    }
)
