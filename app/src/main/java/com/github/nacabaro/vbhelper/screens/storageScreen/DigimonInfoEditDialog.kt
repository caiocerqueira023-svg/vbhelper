package com.github.nacabaro.vbhelper.screens.storageScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits
import com.github.nacabaro.vbhelper.domain.personality.Temperament
import com.github.nacabaro.vbhelper.domain.personality.SocialStyle
import com.github.nacabaro.vbhelper.domain.personality.SpeechQuirk

data class DigimonInfoEditResult(
    val nickname: String?,
    val speciesName: String,
    val level: String?,
    val type: String?,
    val profile: String?,
    val specialMoves: List<String>
)

@Composable
fun DigimonInfoEditDialog(
    cardName: String,
    nickname: String?,
    profile: SpeciesProfile?,
    personality: DigimonPersonalityTraits?,
    onDismiss: () -> Unit,
    onSave: (DigimonInfoEditResult) -> Unit
) {
    var editedNickname by remember { mutableStateOf(nickname.orEmpty()) }
    var speciesName by remember { mutableStateOf(profile?.speciesName.orEmpty()) }
    var level by remember { mutableStateOf(profile?.level.orEmpty()) }
    var type by remember { mutableStateOf(profile?.type.orEmpty()) }
    var description by remember { mutableStateOf(profile?.profileDescription.orEmpty()) }
    var specialMoves by remember {
        mutableStateOf(profile?.specialMoves?.joinToString(", ").orEmpty())
    }

    fun Temperament.resourceId(): Int = when (this) {
        Temperament.CALM -> R.string.personality_calm
        Temperament.ENERGETIC -> R.string.personality_energetic
        Temperament.TEMPERAMENTAL -> R.string.personality_temperamental
        Temperament.DREAMY -> R.string.personality_dreamy
        Temperament.ANXIOUS -> R.string.personality_anxious
    }

    fun SocialStyle.resourceId(): Int = when (this) {
        SocialStyle.LOYAL_WARM -> R.string.personality_loyal_warm
        SocialStyle.PLAYFUL_SARCASTIC -> R.string.personality_playful_sarcastic
        SocialStyle.FORMAL_POLITE -> R.string.personality_formal_polite
        SocialStyle.TOUGH_RUSTIC -> R.string.personality_tough_rustic
        SocialStyle.CURIOUS_TALKATIVE -> R.string.personality_curious_talkative
    }

    fun SpeechQuirk.resourceId(): Int = when (this) {
        SpeechQuirk.SHORT_DIRECT -> R.string.personality_short_direct
        SpeechQuirk.EXCLAMATIONS -> R.string.personality_exclamations
        SpeechQuirk.PHILOSOPHICAL -> R.string.personality_philosophical
        SpeechQuirk.CATCHPHRASE -> R.string.personality_catchphrase
        SpeechQuirk.BATTLE_COMPARISONS -> R.string.personality_battle_comparisons
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Card {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.ui_digimon_info), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.ui_card_fields_note, cardName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editedNickname,
                    onValueChange = { editedNickname = it },
                    label = { Text(stringResource(R.string.ui_nickname)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = speciesName,
                    onValueChange = { speciesName = it },
                    label = { Text(stringResource(R.string.ui_species_name_required)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = level,
                    onValueChange = { level = it },
                    label = { Text(stringResource(R.string.ui_level)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(stringResource(R.string.ui_type)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.ui_profile)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = specialMoves,
                    onValueChange = { specialMoves = it },
                    label = { Text(stringResource(R.string.ui_special_moves)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (personality != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.ui_individual_personality), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.ui_temperament, stringResource(personality.temperament.resourceId())))
                    Text(stringResource(R.string.ui_social_style, stringResource(personality.socialStyle.resourceId())))
                    Text(stringResource(R.string.ui_speech_quirk, stringResource(personality.speechQuirk.resourceId())))
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
                    Button(
                        enabled = speciesName.isNotBlank(),
                        onClick = {
                            onSave(
                                DigimonInfoEditResult(
                                    nickname = editedNickname.trim().takeIf { it.isNotEmpty() },
                                    speciesName = speciesName.trim(),
                                    level = level.trim().ifBlank { null },
                                    type = type.trim().ifBlank { null },
                                    profile = description.trim().ifBlank { null },
                                    specialMoves = specialMoves.split(",")
                                        .map(String::trim)
                                        .filter(String::isNotEmpty)
                                )
                            )
                        }

                    ) {
                        Text(stringResource(R.string.ui_save))
                    }
                }
            }
        }
    }
}
