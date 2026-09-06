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
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile

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
                Text("Informações do Digimon", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Card \"$cardName\". O apelido pertence ao indivíduo; os demais campos pertencem à espécie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editedNickname,
                    onValueChange = { editedNickname = it },
                    label = { Text("Apelido") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = speciesName,
                    onValueChange = { speciesName = it },
                    label = { Text("Nome da espécie *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = level,
                    onValueChange = { level = it },
                    label = { Text("Nível") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Tipo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Perfil") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = specialMoves,
                    onValueChange = { specialMoves = it },
                    label = { Text("Golpes especiais, separados por vírgula") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
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
                        Text("Salvar")
                    }
                }
            }
        }
    }
}
