package com.tamerslab.transfer.ui.scan.components

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.species.data.ScanRepository
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.getBitmap
import kotlinx.coroutines.flow.first

private fun ByteArray.asTransferBitmap(width: Int, height: Int): ImageBitmap? {
    if (isEmpty() || width <= 0 || height <= 0) return null
    return runCatching { BitmapData(this, width, height).getBitmap().asImageBitmap() }.getOrNull()
}

fun CharacterDtos.CharacterWithSprites.toTransferSubject(
    cardName: String? = null,
    cardBitmap: ImageBitmap? = null,
): TransferSubject = TransferSubject(
    characterId = id,
    name = nickname?.takeIf(String::isNotBlank) ?: speciesName,
    species = speciesName,
    bitmap = spriteIdle.asTransferBitmap(spriteWidth, spriteHeight),
    cardName = cardName,
    cardBitmap = cardBitmap,
)

suspend fun loadTransferSubject(context: Context, characterId: Long): TransferSubject? {
    val application = context.applicationContext as? TamersLabApplication ?: return null
    val character = runCatching {
        application.container.storageRepository.getSingleCharacter(characterId)
    }.getOrNull() ?: return null
    val card = runCatching {
        ScanRepository(application.container.db).getCardDetails(characterId).first()
    }.getOrNull()
    return character.toTransferSubject(
        cardName = card?.name,
        cardBitmap = card?.logo?.asTransferBitmap(card.logoWidth, card.logoHeight),
    )
}

/** Newest imported character above the pre-read id watermark; the read flow's visual subject. */
suspend fun latestImportedSubject(context: Context, baselineMaxId: Long): TransferSubject? {
    val application = context.applicationContext as? TamersLabApplication ?: return null
    val characters = runCatching {
        application.container.storageRepository.getAllCharacters().first()
    }.getOrNull().orEmpty()
    val imported = characters
        .filter { it.id > baselineMaxId }
        .maxByOrNull { it.id }
        ?: characters.maxByOrNull { it.id }
        ?: return null
    return loadTransferSubject(context, imported.id)
}
