package com.github.nacabaro.vbhelper.screens.storageScreen

import androidx.compose.foundation.Image
import com.github.nacabaro.vbhelper.components.DimLogo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.github.nacabaro.vbhelper.components.VitalButtonStyle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.source.StorageRepository
import com.github.nacabaro.vbhelper.species.SpeciesRepository
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits
import com.github.nacabaro.vbhelper.components.motionEnabled
import com.github.nacabaro.vbhelper.components.VitalButton

private data class LoadedStorageCharacter(
    val character: CharacterDtos.CharacterWithSprites,
    val cardName: String,
    val speciesProfile: com.github.nacabaro.vbhelper.domain.species.SpeciesProfile?,
    val personality: DigimonPersonalityTraits?
)

@Composable
fun StorageDialog(
    characterId: Long,
    onDismissRequest: () -> Unit,
    onClickDelete: () -> Unit,
    onSendToBracelet: () -> Unit,
    onClickSetActive: () -> Unit,
    onClickSendToAdventure: (time: Long) -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onClickChat: () -> Unit,
    onClickTechniques: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val application = LocalContext.current.applicationContext as VBHelper
    val storageRepository = remember { StorageRepository(application.container.db) }
    val character = remember { mutableStateOf<CharacterDtos.CharacterWithSprites?>(null) }
    val characterSprite = remember { mutableStateOf<BitmapData?>(null) }
    val characterName = remember { mutableStateOf<BitmapData?>(null) }
    var onSendToAdventureClicked by rememberSaveable(characterId) { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var cardName by remember { mutableStateOf("") }
    var showInfoEditor by rememberSaveable(characterId) { mutableStateOf(false) }
    var speciesProfile by remember { mutableStateOf<com.github.nacabaro.vbhelper.domain.species.SpeciesProfile?>(null) }
    var personality by remember { mutableStateOf<DigimonPersonalityTraits?>(null) }
    var showDeleteConfirmation by rememberSaveable(characterId) { mutableStateOf(false) }
    var loadFailed by remember(characterId) { mutableStateOf(false) }
    var reload by remember(characterId) { mutableIntStateOf(0) }
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.88f }
    var idleFrame by remember { mutableIntStateOf(0) }
    var isFavorite by remember { mutableStateOf(false) }
    val motionEnabled = motionEnabled()
    val speciesRepository = remember {
        SpeciesRepository(application.container.db, application.container.speciesSettingsRepository)
    }

    LaunchedEffect(characterId, reload) {
        loadFailed = false
        try {
        val loaded = withContext(Dispatchers.IO) {
            val loadedCharacter = storageRepository.getSingleCharacter(characterId)
            val loadedCardName = application.container.db.cardDao()
                .getCardByCharacterIdSync(loadedCharacter.id)?.name.orEmpty()
            val loadedSpeciesProfile = speciesRepository
                .getProfileForCharacter(loadedCharacter.charId)
            val loadedPersonality = storageRepository.getOrCreatePersonality(characterId)
            LoadedStorageCharacter(
                character = loadedCharacter,
                cardName = loadedCardName,
                speciesProfile = loadedSpeciesProfile,
                personality = loadedPersonality
            )
        }
        character.value = loaded.character
        nickname = loaded.character.nickname.orEmpty()
        cardName = loaded.cardName
        speciesProfile = loaded.speciesProfile
        personality = loaded.personality
        isFavorite = loaded.character.isFavorite
        characterSprite.value = BitmapData(
            bitmap = loaded.character.spriteIdle,
            width = loaded.character.spriteWidth,
            height = loaded.character.spriteHeight
        )
        characterName.value = BitmapData(
            bitmap = loaded.character.nameSprite,
            width = loaded.character.nameSpriteWidth,
            height = loaded.character.nameSpriteHeight
        )
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { loadFailed = true }
    }

    LaunchedEffect(character.value?.id, motionEnabled) {
        if (!motionEnabled) {
            idleFrame = 0
            return@LaunchedEffect
        }
        val animationOffset = ((character.value?.id ?: 0L) and 0x7fff_ffffL) % 750L
        idleFrame = if (animationOffset > 375L) 1 else 0
        if (character.value != null) {
            delay(animationOffset)
            while (true) {
                delay(750L)
                idleFrame = 1 - idleFrame
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            shape = MaterialTheme.shapes.medium
        ) {
            Column (
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .padding(16.dp)
            ) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loadFailed) {
                    Text(stringResource(R.string.app_details_failed), style = MaterialTheme.typography.bodyMedium)
                    VitalButton(onClick = { reload++ }) { Text(stringResource(R.string.app_retry)) }
                } else if (character.value == null) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.app_loading_partner))
                }
                if (character.value != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (characterSprite.value != null && characterName.value != null) {
                        val displayedSprite = if (idleFrame == 1) {
                            BitmapData(
                                bitmap = character.value!!.spriteIdle2,
                                width = character.value!!.spriteWidth,
                                height = character.value!!.spriteHeight
                            )
                        } else {
                            characterSprite.value!!
                        }
                        val bitmap = remember(displayedSprite) { displayedSprite.getBitmap() }
                        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                        val density: Float = LocalContext.current.resources.displayMetrics.density
                        val dpSize = (displayedSprite.width * 4 / density).dp
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = stringResource(R.string.storage_character_image_description),
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .size(dpSize)
                        )
                        val nameBitmap = remember (characterName.value!!) { characterName.value!!.getBitmap() }
                        val nameImageBitmap = remember(nameBitmap) { nameBitmap.asImageBitmap() }
                        DimLogo(
                            bitmap = nameImageBitmap,
                            contentDescription = stringResource(R.string.storage_character_image_description),
                            filterQuality = FilterQuality.None,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .weight(1f)
                                .size(width = 160.dp, height = 64.dp)
                        )
                        }
                        IconButton(onClick = { showInfoEditor = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.storage_edit_digimon_info)
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    VitalButton(
                        style = VitalButtonStyle.PRIMARY,
                        enabled = character.value != null,
                        onClick = onSendToBracelet,
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        Text(text = stringResource(R.string.storage_send_to_watch))
                    }
                    Spacer(
                        modifier = Modifier
                            .padding(4.dp)
                    )
                    VitalButton(
                        enabled = character.value != null,
                        onClick = onClickSetActive,
                    ) {
                        Text(text = stringResource(R.string.storage_set_active))
                    }
                }
                VitalButton(
                    enabled = character.value != null,
                    onClick = onClickChat,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.storage_chat_with_digimon))
                }
                VitalButton(
                    enabled = character.value != null,
                    onClick = onClickTechniques,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.app_techniques))
                }
                VitalButton(
                    onClick = {
                        isFavorite = !isFavorite
                        onToggleFavorite(isFavorite)
                    },
                    enabled = character.value != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(
                            if (isFavorite) R.string.storage_unpin_character
                            else R.string.storage_pin_character
                        )
                    )
                }
                VitalButton(
                    enabled = character.value != null,
                    onClick = {
                        onSendToAdventureClicked = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.storage_send_on_adventure))
                }
                VitalButton(
                    style = VitalButtonStyle.DESTRUCTIVE,
                    enabled = character.value != null,
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = { showDeleteConfirmation = true }
                ) {
                    Text(text = stringResource(R.string.storage_delete_character))
                }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.storage_close))
                }
            }
        }
    }

    if (onSendToAdventureClicked) {
        StorageAdventureTimeDialog(
            onClickSendToAdventure = { time ->
                onClickSendToAdventure(time)
            },
            onDismissRequest = { onSendToAdventureClicked = false }
        )
    }

    if (showInfoEditor && character.value != null) {
        DigimonInfoEditDialog(
            cardName = cardName,
            nickname = nickname,
            profile = speciesProfile,
            personality = personality,
            onDismiss = { showInfoEditor = false },
            onSave = { result ->
                coroutineScope.launch {
                    storageRepository.updateNickname(characterId, result.nickname)
                    speciesRepository.saveManualProfile(
                        cardCharacterId = character.value!!.charId,
                        name = result.speciesName,
                        level = result.level,
                        type = result.type,
                        profile = result.profile,
                        specialMoves = result.specialMoves
                    )
                    nickname = result.nickname.orEmpty()
                    speciesProfile = speciesRepository.getProfileForCharacter(character.value!!.charId)
                    showInfoEditor = false
                }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.storage_delete_character)) },
            text = { Text(stringResource(R.string.storage_delete_character_confirmation)) },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
            confirmButton = {
                VitalButton(
                    style = VitalButtonStyle.DESTRUCTIVE,
                    onClick = {
                        showDeleteConfirmation = false
                        onClickDelete()
                    }
                ) {
                    Text(stringResource(R.string.ui_delete))
                }
            }
        )
    }
}
