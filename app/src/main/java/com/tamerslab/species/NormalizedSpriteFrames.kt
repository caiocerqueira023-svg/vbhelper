package com.tamerslab.species

import android.graphics.Bitmap
import com.tamerslab.domain.characters.Sprite
import com.tamerslab.ui.SpritePreviewPolicy
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.createARGBIntArray

fun Sprite.displayFrame(number: Int): ByteArray? {
    val bytes = when (number) {
        1 -> spriteIdle1; 2 -> spriteIdle2; 3 -> spriteWalk1; 4 -> spriteWalk2
        5 -> spriteRun1; 6 -> spriteRun2; 7 -> spriteTrain1; 8 -> spriteTrain2
        9 -> spriteHappy; 10 -> spriteSleep; 11 -> spriteAttack; 12 -> spriteDodge
        else -> spriteIdle1
    }
    return bytes.takeIf { SpritePreviewPolicy.validFrame(width, height, it.size) }
        ?: spriteIdle1.takeIf { SpritePreviewPolicy.validFrame(width, height, it.size) }
}

fun Sprite.displayBitmap(number: Int): Bitmap? = displayFrame(number)?.let {
    Bitmap.createBitmap(BitmapData(it, width, height).createARGBIntArray(), width, height, Bitmap.Config.ARGB_8888)
}
