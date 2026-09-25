package com.github.nacabaro.vbhelper.screens.offlineBattle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.nacabaro.vbhelper.battle.BattleAssetPaths
import com.github.nacabaro.vbhelper.battle.decodeBattleAsset
import com.github.nacabaro.vbhelper.components.CharacterEntry
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageCharacterPickerDialog
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageCharacterPickerOption
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.SurfaceElevatedPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.VitalPurpleBright
import com.github.nacabaro.vbhelper.utils.BitmapData

object TrainingBattleTags {
    const val AllyOneSlot = "offline-battle-slot-ally-one"
    const val AllyTwoSlot = "offline-battle-slot-ally-two"
    const val OpponentOneSlot = "offline-battle-slot-opponent-one"
    const val OpponentTwoSlot = "offline-battle-slot-opponent-two"
}

private enum class TrainingFormat(val id: String, val label: String, val allies: Int, val opponents: Int) {
    ONE_V_ONE("1x1", "1 × 1", 1, 1),
    ONE_V_TWO("1x2", "1 × 2", 1, 2),
    TWO_V_TWO("2x2", "2 × 2", 2, 2)
}

private enum class BattleTeamSlot(
    val key: String,
    val title: String,
    val tag: String,
    val allied: Boolean
) {
    ALLY_ONE("ally-one", "Parceiro ativo", TrainingBattleTags.AllyOneSlot, true),
    ALLY_TWO("ally-two", "Segundo parceiro", TrainingBattleTags.AllyTwoSlot, true),
    OPPONENT_ONE("opponent-one", "Oponente 1", TrainingBattleTags.OpponentOneSlot, false),
    OPPONENT_TWO("opponent-two", "Oponente 2", TrainingBattleTags.OpponentTwoSlot, false)
}

@Composable
fun TrainingBattlePreparation(
    activePartner: OfflineBattleParticipant?,
    availablePartners: List<OfflineBattleParticipant>,
    availableOpponents: List<OfflineBattleParticipant>,
    onBack: () -> Unit,
    onStart: (List<OfflineBattleParticipant>, List<OfflineBattleParticipant>) -> Unit,
    modifier: Modifier = Modifier
) {
    var formatId by rememberSaveable { mutableStateOf(TrainingFormat.ONE_V_ONE.id) }
    val format = TrainingFormat.entries.first { it.id == formatId }
    val partnerOptions = availablePartners.distinctBy { it.stableId }
    val opponentOptions = availableOpponents.distinctBy { it.stableId }
    var allyOneId by rememberSaveable(activePartner?.stableId) { mutableStateOf(activePartner?.stableId) }
    var allyTwoId by rememberSaveable(activePartner?.stableId) {
        mutableStateOf(partnerOptions.firstOrNull { it.stableId != activePartner?.stableId }?.stableId)
    }
    var opponentOneId by rememberSaveable(activePartner?.stableId) { mutableStateOf(opponentOptions.firstOrNull()?.stableId) }
    var opponentTwoId by rememberSaveable(activePartner?.stableId) {
        mutableStateOf(opponentOptions.getOrNull(1)?.stableId)
    }
    var pickerSlotKey by rememberSaveable { mutableStateOf<String?>(null) }

    fun selected(slot: BattleTeamSlot): OfflineBattleParticipant? {
        val id = when (slot) {
            BattleTeamSlot.ALLY_ONE -> allyOneId
            BattleTeamSlot.ALLY_TWO -> allyTwoId
            BattleTeamSlot.OPPONENT_ONE -> opponentOneId
            BattleTeamSlot.OPPONENT_TWO -> opponentTwoId
        }
        val options = if (slot.allied) partnerOptions else opponentOptions
        return options.firstOrNull { it.stableId == id }
    }
    fun setSelected(slot: BattleTeamSlot, id: String) {
        when (slot) {
            BattleTeamSlot.ALLY_ONE -> allyOneId = id
            BattleTeamSlot.ALLY_TWO -> allyTwoId = id
            BattleTeamSlot.OPPONENT_ONE -> opponentOneId = id
            BattleTeamSlot.OPPONENT_TWO -> opponentTwoId = id
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Arena de treino", style = MaterialTheme.typography.headlineSmall,
                color = TextPrimaryOnDark, fontWeight = FontWeight.Bold)
            Text("Monte as equipes e acompanhe o combate em tempo real.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondaryOnDark,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceElevatedPurple,
            shape = CutCornerShape(9.dp),
            border = BorderStroke(1.dp, SurfaceStroke)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Formato da luta", style = MaterialTheme.typography.labelLarge,
                    color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TrainingFormat.entries.forEach { option ->
                        val isSelected = option == format
                        Button(
                            onClick = { formatId = option.id },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = CutCornerShape(6.dp),
                            border = if (isSelected) null else BorderStroke(1.dp, SurfaceStroke),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) VitalPurpleBright else DeepPurpleBgAlt,
                                contentColor = if (isSelected) Color(0xFF0A0812) else TextPrimaryOnDark
                            )
                        ) {
                            Text(option.label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TeamSlotButton(
                slot = BattleTeamSlot.ALLY_ONE,
                participant = selected(BattleTeamSlot.ALLY_ONE),
                onClick = { pickerSlotKey = BattleTeamSlot.ALLY_ONE.key }
            )
            if (format.allies == 2) {
                TeamSlotButton(
                    slot = BattleTeamSlot.ALLY_TWO,
                    participant = selected(BattleTeamSlot.ALLY_TWO),
                    onClick = { pickerSlotKey = BattleTeamSlot.ALLY_TWO.key }
                )
            }
            TeamSlotButton(
                slot = BattleTeamSlot.OPPONENT_ONE,
                participant = selected(BattleTeamSlot.OPPONENT_ONE),
                onClick = { pickerSlotKey = BattleTeamSlot.OPPONENT_ONE.key }
            )
            if (format.opponents == 2) {
                TeamSlotButton(
                    slot = BattleTeamSlot.OPPONENT_TWO,
                    participant = selected(BattleTeamSlot.OPPONENT_TWO),
                    onClick = { pickerSlotKey = BattleTeamSlot.OPPONENT_TWO.key }
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DeepPurpleBgAlt,
                shape = CutCornerShape(7.dp),
                border = BorderStroke(1.dp, SurfaceStroke)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Treino sem consequências", color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge)
                    Text("HP, energia, itens e resultado valem só nesta sessão; o Storage permanece intacto.",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondaryOnDark)
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        val selectedAllyOne = selected(BattleTeamSlot.ALLY_ONE)
        val selectedAllyTwo = selected(BattleTeamSlot.ALLY_TWO)
        val selectedOpponentOne = selected(BattleTeamSlot.OPPONENT_ONE)
        val selectedOpponentTwo = selected(BattleTeamSlot.OPPONENT_TWO)
        val selectedAllies = buildList {
            selectedAllyOne?.let(::add)
            if (format.allies == 2) selectedAllyTwo?.let(::add)
        }
        val selectedOpponents = buildList {
            selectedOpponentOne?.let(::add)
            if (format.opponents == 2) selectedOpponentTwo?.let(::add)
        }
        val allActiveParticipants = selectedAllies + selectedOpponents
        val canStart = selectedAllyOne != null && selectedOpponentOne != null &&
            (format.allies < 2 || selectedAllyTwo != null) &&
            (format.opponents < 2 || selectedOpponentTwo != null) &&
            allActiveParticipants.map { it.stableId }.distinct().size == allActiveParticipants.size

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(0.8f).height(52.dp),
                shape = CutCornerShape(7.dp),
                border = BorderStroke(1.dp, SurfaceStroke)
            ) { Text("Voltar", color = TextPrimaryOnDark) }
            Button(
                onClick = {
                    onStart(selectedAllies, selectedOpponents)
                },
                enabled = canStart,
                modifier = Modifier.weight(1.2f).height(52.dp),
                shape = CutCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VitalPurpleBright,
                    contentColor = Color(0xFF0A0812)
                )
            ) { Text("Entrar na arena", fontWeight = FontWeight.Bold, maxLines = 1) }
        }
    }

    val pickerSlot = BattleTeamSlot.entries.firstOrNull { it.key == pickerSlotKey }
    if (pickerSlot != null) {
        val choices = if (pickerSlot.allied) partnerOptions else opponentOptions
        val currentId = when (pickerSlot) {
            BattleTeamSlot.ALLY_ONE -> allyOneId
            BattleTeamSlot.ALLY_TWO -> allyTwoId
            BattleTeamSlot.OPPONENT_ONE -> opponentOneId
            BattleTeamSlot.OPPONENT_TWO -> opponentTwoId
        }
        val occupiedByOtherSlots = when (pickerSlot) {
            BattleTeamSlot.ALLY_ONE -> listOfNotNull(allyTwoId)
            BattleTeamSlot.ALLY_TWO -> listOfNotNull(allyOneId)
            BattleTeamSlot.OPPONENT_ONE -> listOfNotNull(opponentTwoId)
            BattleTeamSlot.OPPONENT_TWO -> listOfNotNull(opponentOneId)
        }.toSet()
        val availableChoices = choices.filter { it.stableId !in occupiedByOtherSlots || it.stableId == currentId }
        val dismissPicker = { pickerSlotKey = null }
        val selectParticipant: (OfflineBattleParticipant) -> Unit = { participant ->
            setSelected(pickerSlot, participant.stableId)
            pickerSlotKey = null
        }
        val supplementalOptions = availableChoices
            .filter { it.character == null }
            .map { participant ->
                StorageCharacterPickerOption(
                    id = participant.stableId,
                    displayName = participant.displayName,
                    searchableTerms = listOfNotNull(
                        participant.assetCharacterId,
                        participant.externalCharacterId,
                        "estágio ${participant.stage}"
                    ),
                    content = { onClick ->
                        ParticipantPickerTile(
                            participant = participant,
                            selected = participant.stableId == currentId,
                            onClick = onClick
                        )
                    }
                )
            }
        StorageCharacterPickerDialog(
            characters = availableChoices.mapNotNull { it.character },
            title = "Escolher ${pickerSlot.title}",
            emptyMessage = "Nenhum Digimon disponível para este time.",
            supplementalOptions = supplementalOptions,
            onDismiss = dismissPicker,
            onCharacterSelected = { characterId ->
                availableChoices.firstOrNull { it.character?.id == characterId }
                    ?.let(selectParticipant)
            },
            onSupplementalSelected = { stableId ->
                availableChoices.firstOrNull { it.stableId == stableId }
                    ?.let(selectParticipant)
            }
        )
    }
}

@Composable
private fun TeamSlotButton(
    slot: BattleTeamSlot,
    participant: OfflineBattleParticipant?,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(70.dp).testTag(slot.tag),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 7.dp),
        shape = CutCornerShape(7.dp),
        border = BorderStroke(1.dp, if (participant != null) VitalCyan else SurfaceStroke)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(slot.title.uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = if (participant != null) VitalCyan else TextSecondaryOnDark,
                    fontWeight = FontWeight.Bold)
                Text(participant?.displayName ?: "Adicionar Digimon", style = MaterialTheme.typography.titleSmall,
                    color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                participant?.let {
                    val stats = it.trainingStats()
                    Text("Estágio ${it.stage} · HP ${stats.health} · AP ${stats.attack} · BP ${stats.defense}",
                        style = MaterialTheme.typography.labelSmall, color = TextSecondaryOnDark,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } ?: Text("Escolha no Storage", style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryOnDark, maxLines = 1)
            }
            Text(if (participant == null) "SELECIONAR" else "TROCAR",
                style = MaterialTheme.typography.labelSmall, color = VitalCyan, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ParticipantPickerTile(
    participant: OfflineBattleParticipant,
    selected: Boolean,
    onClick: () -> Unit
) {
    val character = participant.character
    val idle = character?.spriteIdle?.takeIf { it.isNotEmpty() }
        ?: character?.spriteRun1?.takeIf { it.isNotEmpty() }
    val idle2 = character?.spriteIdle2?.takeIf { it.isNotEmpty() }
        ?: character?.spriteRun2?.takeIf { it.isNotEmpty() }
    val hasDatabaseSprite = character != null && idle != null && character.spriteWidth > 0 && character.spriteHeight > 0
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 174.dp).testTag("offline-battle-picker-option-${participant.stableId}"),
        onClick = onClick,
        color = if (selected) VitalCyan.copy(alpha = 0.12f) else SurfaceElevatedPurple,
        shape = CutCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) VitalCyan else SurfaceStroke)
    ) {
        Column(Modifier.fillMaxWidth().padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (hasDatabaseSprite) {
                CharacterEntry(
                    icon = BitmapData(idle, character.spriteWidth, character.spriteHeight),
                    idleFrame2 = idle2?.let { BitmapData(it, character.spriteWidth, character.spriteHeight) },
                    animationKey = participant.stableId,
                    shape = RectangleShape,
                    vitalPoints = character.vitalPoints,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClick
                )
            } else {
                AssetParticipantPreview(participant)
            }
            Text(participant.displayName, color = TextPrimaryOnDark,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val stats = participant.trainingStats()
            Text("HP ${stats.health} · AP ${stats.attack} · BP ${stats.defense}",
                color = TextSecondaryOnDark, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AssetParticipantPreview(participant: OfflineBattleParticipant) {
    val context = LocalContext.current
    val assetId = participant.assetCharacterId ?: participant.externalCharacterId
    val bitmap = androidx.compose.runtime.remember(assetId) {
        assetId?.let { context.decodeBattleAsset(BattleAssetPaths.characterFrame(it, 1)) }
    }
    Box(
        Modifier.fillMaxWidth().height(112.dp).background(DeepPurpleBgAlt, CutCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = participant.displayName,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(5.dp))
        } else {
            Text(participant.displayName.take(1).uppercase(), color = VitalCyan,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}
