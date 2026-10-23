package com.github.nacabaro.vbhelper.screens.transferScreen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CharacterEntry
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.VitalButtonStyle
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.source.gameSave.NativeArchiveView
import com.github.nacabaro.vbhelper.source.gameSave.NativeSpeciesPresentationRepository
import com.github.nacabaro.vbhelper.source.gameSave.AwayNativeRecord
import com.github.nacabaro.vbhelper.source.gameSave.WorldDsSpeciesTable
import com.github.nacabaro.vbhelper.species.CanonicalSpeciesDto
import com.github.nacabaro.vbhelper.species.SpeciesAppearanceRepository
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.utils.BitmapData
import kotlinx.coroutines.CancellationException

@Composable
private fun archiveSpecies(archive: NativeArchiveView): CanonicalSpeciesDto? {
    val app = LocalContext.current.applicationContext as VBHelper
    val repository = remember(app) { NativeSpeciesPresentationRepository(app.container.db, app.container.dotSpriteLibrary,
        WorldDsSpeciesTable.load(app)) }
    val species by produceState<CanonicalSpeciesDto?>(null, archive.snapshot.id) {
        try { value = repository.archive(archive.snapshot.id, archive.record) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
    }
    return species
}

@Composable
internal fun NativeArchiveStorageTile(archive: NativeArchiveView, onClick: () -> Unit) {
    TransferIndividualTile(archive.nickname?.takeIf(String::isNotBlank) ?: archive.record?.storedName.orEmpty(),
        null, archiveSpecies(archive), rememberTransferArtworkVersion(), onClick)
}

@Composable
internal fun AwayNativeRecordTile(record: AwayNativeRecord, onClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as VBHelper
    val appearance = remember(app) { SpeciesAppearanceRepository(app.container.db, app.container.dotSpriteLibrary,
        { app.container.spritePreferenceRepository.current }) }
    val portrait by produceState<android.graphics.Bitmap?>(null, record.species?.id) {
        value = null
        val id = record.species?.id ?: return@produceState
        try { value = appearance.portrait(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
    }
    val bitmap = portrait
    if (bitmap != null) {
        CharacterEntry(
            icon = bitmap.toSpriteData(),
            animationKey = record.recordKey,
            iconDescription = record.name,
            shape = androidx.compose.ui.graphics.RectangleShape,
            fitSpriteToBounds = true,
            onClick = onClick,
        )
    } else {
        Surface(onClick = onClick, shape = androidx.compose.ui.graphics.RectangleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceStroke),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.HelpOutline, stringResource(R.string.transfer_species_unresolved),
                    modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun AwayNativeRecordInformation(record: AwayNativeRecord, onDismiss: () -> Unit) {
    val version = rememberTransferArtworkVersion()
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(record.name) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TransferSpeciesPortrait(record.species, version, Modifier.fillMaxWidth().height(120.dp))
            record.species?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
            Text(stringResource(R.string.storage_away_native_info, record.sourceLabel),
                style = MaterialTheme.typography.bodyMedium)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_close)) } })
}

@Composable
internal fun NativeArchiveStorageInformation(archive: NativeArchiveView, onMoveToGame: (() -> Unit)? = null, onDismiss: () -> Unit) {
    val species = archiveSpecies(archive)
    val version = rememberTransferArtworkVersion()
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(archive.nickname?.takeIf(String::isNotBlank) ?: archive.record?.storedName.orEmpty()) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TransferSpeciesPortrait(species, version, Modifier.fillMaxWidth().height(120.dp))
            species?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
            Text(stringResource(R.string.storage_retained_partner_info), style = MaterialTheme.typography.bodyMedium)
        } },
        confirmButton = {
            if (onMoveToGame != null) VitalButton(onClick = onMoveToGame, style = VitalButtonStyle.PRIMARY) {
                Text(stringResource(R.string.transfer_move_to_game))
            } else TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_close)) }
        },
        dismissButton = if (onMoveToGame != null) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_close)) } }
        } else null)
}

/** Storage tiles consume 16-bit R5G6B5 sprites; alpha-0 pixels use the green transparency key. */
private fun android.graphics.Bitmap.toSpriteData(): BitmapData {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val bytes = ByteArray(pixels.size * 2)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        val transparent = (pixel ushr 24) and 0xFF == 0
        val red = if (transparent) 0 else ((pixel ushr 19) and 0x1F)
        val green = if (transparent) 0x3F else ((pixel ushr 10) and 0x3F)
        val blue = if (transparent) 0 else (pixel ushr 3) and 0x1F
        val packed = (red shl 11) or (green shl 5) or blue
        bytes[index * 2] = (packed and 0xFF).toByte()
        bytes[index * 2 + 1] = (packed ushr 8).toByte()
    }
    return BitmapData(bytes, width, height)
}
