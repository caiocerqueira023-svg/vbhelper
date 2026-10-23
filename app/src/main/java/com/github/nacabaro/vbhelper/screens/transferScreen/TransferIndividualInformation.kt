package com.github.nacabaro.vbhelper.screens.transferScreen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.VitalButtonStyle
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.RecordIssue
import com.github.nacabaro.vbhelper.gamesave.RecordMembership
import com.github.nacabaro.vbhelper.gamesave.SaveContainer
import com.github.nacabaro.vbhelper.species.CanonicalSpeciesDto
import com.github.nacabaro.vbhelper.species.SpeciesAppearanceRepository
import kotlinx.coroutines.CancellationException

@Composable
internal fun rememberTransferArtworkVersion(): Any {
    val app = LocalContext.current.applicationContext as VBHelper
    val choices by remember(app) { app.container.db.speciesCustomizationDao().observeSpriteChoices() }.collectAsState(emptyList())
    val mode by app.container.spritePreferenceRepository.preference.collectAsState(initial = null)
    return mode to choices
}

@Composable
internal fun TransferSpeciesPortrait(species: CanonicalSpeciesDto?, artworkVersion: Any, modifier: Modifier) {
    val app = LocalContext.current.applicationContext as VBHelper
    val appearance = remember(app) { SpeciesAppearanceRepository(app.container.db, app.container.dotSpriteLibrary,
        { app.container.spritePreferenceRepository.current }) }
    val portrait by produceState<Bitmap?>(null, species?.id, artworkVersion) {
        value = null
        val id = species?.id ?: return@produceState
        try { value = appearance.portrait(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (portrait != null) Image(requireNotNull(portrait).asImageBitmap(), contentDescription = null,
            modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
        else Icon(Icons.Default.HelpOutline, stringResource(R.string.transfer_species_unresolved),
            modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun TransferIndividualTile(nickname: String, level: Int?, species: CanonicalSpeciesDto?, artworkVersion: Any,
    onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TransferSpeciesPortrait(species, artworkVersion, Modifier.size(72.dp))
            Text(nickname.ifBlank { species?.name ?: stringResource(R.string.transfer_unnamed_record) },
                style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            species?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            level?.let { Text(stringResource(R.string.transfer_workspace_level, it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
internal fun TransferIndividualInformation(record: ObservedDigimon, nickname: String, species: CanonicalSpeciesDto?,
    artworkVersion: Any, moveIntoStorage: Boolean, moving: Boolean = false,
    moveBlocker: Int? = R.string.transfer_native_move_unavailable, onMove: (() -> Unit)? = null,
    onGrantAccess: (() -> Unit)? = null, onAdmitToParty: (() -> Unit)? = null, admitBlocker: Int? = null,
    onDismiss: () -> Unit) {
    val blocked = moveBlocker ?: when {
        record.container == SaveContainer.FARM -> R.string.destination_farm_excluded
        record.container == SaveContainer.UNASSIGNED && record.issues.isNotEmpty() &&
            record.issues.all { it == RecordIssue.UNASSIGNED_LOCATION || it == RecordIssue.AMBIGUOUS_SAVE_BLOCK } -> null
        record.container == SaveContainer.PARTY && record.issues.isNotEmpty() &&
            record.issues.all { it == RecordIssue.AMBIGUOUS_SAVE_BLOCK } -> null
        record.membership != RecordMembership.ACTIVE_RECORD || record.issues.isNotEmpty() -> R.string.transfer_record_needs_review
        species == null -> R.string.transfer_species_unresolved
        else -> null
    }
    AlertDialog(onDismissRequest = { if (!moving) onDismiss() },
        title = { Text(nickname.ifBlank { species?.name ?: stringResource(R.string.transfer_unnamed_record) }) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        TransferSpeciesPortrait(species, artworkVersion, Modifier.size(144.dp))
                        Text(species?.name ?: stringResource(R.string.transfer_species_unresolved),
                            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
                blocked?.let { item { Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                if (moveBlocker == R.string.transfer_move_write_access && onGrantAccess != null) item {
                    TextButton(onClick = onGrantAccess, enabled = !moving) {
                        Text(stringResource(R.string.transfer_move_grant_write))
                    }
                }
                if (moveIntoStorage && (admitBlocker != null || onAdmitToParty != null)) item {
                    TextButton(onClick = { onAdmitToParty?.invoke() }, enabled = onAdmitToParty != null && !moving) {
                        Text(stringResource(R.string.transfer_move_to_party))
                    }
                    admitBlocker?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (moveIntoStorage) item { Text(stringResource(R.string.transfer_move_experimental),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { InformationRow(stringResource(R.string.native_measure_level), record.level.toString()) }
                record.nativeParameters?.let { native ->
                    item { InformationRow(stringResource(R.string.native_measure_hp), "${native.hp} / ${native.maxHp}") }
                    item { InformationRow(stringResource(R.string.native_measure_mp), "${native.mp} / ${native.maxMp}") }
                    item { InformationRow(stringResource(R.string.native_measure_attack), native.attack.toString()) }
                    item { InformationRow(stringResource(R.string.native_measure_defense), native.defense.toString()) }
                    item { InformationRow(stringResource(R.string.native_measure_spirit), native.spirit.toString()) }
                    item { InformationRow(stringResource(R.string.native_measure_speed), native.speed.toString()) }
                    item { InformationRow(stringResource(R.string.native_measure_aptitude), native.aptitude.toString()) }
                    native.expCounters.forEachIndexed { index, exp -> item {
                        InformationRow(stringResource(R.string.native_progress_exp_channel, index + 1), exp.toString())
                    } }
                }
                record.totalExp?.let { exp -> item { InformationRow(stringResource(R.string.native_measure_exp), exp.toString()) } }
            }
        },
        confirmButton = {
            VitalButton(onClick = { onMove?.invoke() }, enabled = onMove != null && !moving, style = VitalButtonStyle.PRIMARY) {
                if (moving) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                Text(stringResource(if (moveIntoStorage) R.string.transfer_move_to_storage else R.string.transfer_move_to_game))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !moving) { Text(stringResource(R.string.transfer_close)) } })
}

@Composable
private fun InformationRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
