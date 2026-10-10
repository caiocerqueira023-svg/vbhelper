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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.chat.PromptLocalization
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.editorDialogBounds
import androidx.compose.foundation.layout.FlowRow
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits

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
    var editedNickname by rememberSaveable(cardName) { mutableStateOf(nickname.orEmpty()) }
    var speciesName by rememberSaveable(cardName) { mutableStateOf(profile?.speciesName.orEmpty()) }
    var level by rememberSaveable(cardName) { mutableStateOf(profile?.level.orEmpty()) }
    var type by rememberSaveable(cardName) { mutableStateOf(profile?.type.orEmpty()) }
    var description by rememberSaveable(cardName) { mutableStateOf(profile?.profileDescription.orEmpty()) }
    var specialMoves by rememberSaveable(cardName) {
        mutableStateOf(profile?.specialMoves?.joinToString(", ").orEmpty())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, decorFitsSystemWindows = false)
    ) {
        Card(modifier = Modifier.editorDialogBounds()) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.ui_digimon_info), style = MaterialTheme.typography.titleMedium)
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
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 2.dp
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            val languageTag = PromptLocalization.currentLanguageTag()
                            Text(
                                stringResource(
                                    R.string.ui_personality_type,
                                    personality.personalityType.displayName(languageTag)
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.ui_personality_rules),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                personality.personalityType.promptInstruction(languageTag),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
                    VitalButton(
                        style = com.github.nacabaro.vbhelper.components.VitalButtonStyle.PRIMARY,
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
