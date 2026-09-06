package com.github.nacabaro.vbhelper.screens.storageScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
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
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits


@Composable
fun StorageDialog(
    characterId: Long,
    onDismissRequest: () -> Unit,
    onClickDelete: () -> Unit,
    onSendToBracelet: () -> Unit,
    onClickSetActive: () -> Unit,
    onClickSendToAdventure: (time: Long) -> Unit,
    onClickChat: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val application = LocalContext.current.applicationContext as VBHelper
    val storageRepository = StorageRepository(application.container.db)
    val character = remember { mutableStateOf<CharacterDtos.CharacterWithSprites?>(null) }
    val characterSprite = remember { mutableStateOf<BitmapData?>(null) }
    val characterName = remember { mutableStateOf<BitmapData?>(null) }
    var onSendToAdventureClicked by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var cardName by remember { mutableStateOf("") }
    var showInfoEditor by remember { mutableStateOf(false) }
    var speciesProfile by remember { mutableStateOf<com.github.nacabaro.vbhelper.domain.species.SpeciesProfile?>(null) }
    var personality by remember { mutableStateOf<DigimonPersonalityTraits?>(null) }
    val speciesRepository = SpeciesRepository(application.container.db, application.container.speciesSettingsRepository)

    LaunchedEffect(storageRepository) {
        coroutineScope.launch {
            character.value = storageRepository.getSingleCharacter(characterId)
            nickname = character.value!!.nickname.orEmpty()
            cardName = application.container.db.cardDao()
                .getCardByCharacterIdSync(character.value!!.id)?.name.orEmpty()
            speciesProfile = speciesRepository.getProfileForCharacter(character.value!!.charId)
            val individualId = application.container.db.userCharacterDao()
                .getCharacter(characterId).individualId
            personality = application.container.db.digimonIndividualDao()
                .getPersonality(individualId)
            characterSprite.value = BitmapData(
                bitmap = character.value!!.spriteIdle,
                width = character.value!!.spriteWidth,
                height = character.value!!.spriteHeight
            )
            characterName.value = BitmapData(
                bitmap = character.value!!.nameSprite,
                width = character.value!!.nameSpriteWidth,
                height = character.value!!.nameSpriteHeight
            )
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
            shape = RoundedCornerShape(16.dp)
        ) {
            Column (
                modifier = Modifier
                    .padding(16.dp)
            ) {
                if (character.value != null &&
                    characterSprite.value != null &&
                    characterName.value != null
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val bitmap = remember (characterSprite.value!!) { characterSprite.value!!.getBitmap() }
                        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                        val density: Float = LocalContext.current.resources.displayMetrics.density
                        val dpSize = (characterSprite.value!!.width * 4 / density).dp
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = stringResource(R.string.storage_character_image_description),
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .size(dpSize)
                        )
                        val nameBitmap = remember (characterName.value!!) { characterName.value!!.getBitmap() }
                        val nameImageBitmap = remember(nameBitmap) { nameBitmap.asImageBitmap() }
                        val nameDpSize = (characterName.value!!.width * 4 / density).dp
                        Image(
                            bitmap = nameImageBitmap,
                            contentDescription = stringResource(R.string.storage_character_image_description),
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .size(nameDpSize)
                        )
                        IconButton(onClick = { showInfoEditor = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Editar informações do Digimon"
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Button(
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
                    Button(
                        onClick = onClickSetActive,
                    ) {
                        Text(text = stringResource(R.string.storage_set_active))
                    }
                }
                Button(
                    onClick = onClickChat,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.storage_chat_with_digimon))
                }
                Button(
                    onClick = {
                        onSendToAdventureClicked = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.storage_send_on_adventure))
                }
                Button(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = onClickDelete
                ) {
                    Text(text = stringResource(R.string.storage_delete_character))
                }
                Button(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = onDismissRequest
                ) {
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
}