package com.github.nacabaro.vbhelper.screens.offlineBattle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueImpactShape
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueRangeProfile
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueEntry
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueFamily
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueLoadout
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.SurfaceElevatedPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.VitalPurpleBright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TechniqueLoadoutScreen(
    characterId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as VBHelper
    val scope = rememberCoroutineScope()
    var individualId by remember(characterId) { mutableStateOf<String?>(null) }
    var characterName by remember(characterId) { mutableStateOf("Digimon") }
    var selectedIds by remember(characterId) { mutableStateOf(GenericTechniqueCatalog.defaultTechniqueIds) }
    var selectedSlot by remember(characterId) { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var family by remember { mutableStateOf<GenericTechniqueFamily?>(null) }
    var loading by remember(characterId) { mutableStateOf(true) }
    var saving by remember(characterId) { mutableStateOf(false) }
    var error by remember(characterId) { mutableStateOf<String?>(null) }

    LaunchedEffect(characterId) {
        runCatching {
            withContext(Dispatchers.IO) {
                val character = application.container.db.userCharacterDao().getCharacter(characterId)
                val details = application.container.db.userCharacterDao().getCharacterWithSprites(characterId)
                val stored = application.container.db.digimonTechniqueLoadoutDao()
                    .getForIndividual(character.individualId)
                    .map { it.techniqueId }
                Triple(
                    character.individualId,
                    details.nickname?.takeIf { it.isNotBlank() }
                        ?: details.speciesName?.takeIf { it.isNotBlank() }
                        ?: "Digimon #$characterId",
                    GenericTechniqueLoadout.resolve(stored)
                )
            }
        }.onSuccess { loaded ->
            individualId = loaded.first
            characterName = loaded.second
            selectedIds = loaded.third
        }.onFailure {
            error = "Não foi possível carregar as técnicas."
        }
        loading = false
    }

    val visibleEntries = remember(query, family) {
        GenericTechniqueCatalog.selectableEntries.filter { entry ->
            (family == null || entry.family == family) &&
                (query.isBlank() || entry.definition.displayName.contains(query.trim(), ignoreCase = true))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DeepPurpleBgAlt,
        topBar = {
            Surface(color = SurfaceElevatedPurple, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = VitalCyan)
                    }
                    Column {
                        Text("TÉCNICAS", color = TextPrimaryOnDark, fontWeight = FontWeight.Bold)
                        Text(characterName, color = TextSecondaryOnDark, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = SurfaceElevatedPurple, border = BorderStroke(1.dp, SurfaceStroke)) {
                Button(
                    onClick = {
                        val identity = individualId ?: return@Button
                        saving = true
                        error = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    application.container.db.digimonTechniqueLoadoutDao()
                                        .replace(identity, GenericTechniqueLoadout.resolve(selectedIds))
                                }
                            }.onSuccess { onBack() }
                                .onFailure { error = "Não foi possível salvar a seleção." }
                            saving = false
                        }
                    },
                    enabled = !loading && !saving && individualId != null,
                    shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VitalCyan, contentColor = DeepPurpleBgAlt),
                    modifier = Modifier.fillMaxWidth().padding(12.dp).height(52.dp)
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("SALVAR EQUIPAMENTO", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VitalCyan)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Escolha um slot e depois uma técnica. Cada Digimon mantém seu próprio conjunto.",
                    color = TextSecondaryOnDark,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            items(GenericTechniqueLoadout.REGULAR_SLOT_COUNT) { slot ->
                val definition = GenericTechniqueCatalog.definition(selectedIds[slot])
                EquippedTechniqueSlot(
                    slot = slot,
                    technique = definition,
                    selected = selectedSlot == slot,
                    onClick = { selectedSlot = slot }
                )
            }
            item {
                val special = GenericTechniqueCatalog.battleDefinitions
                    .single { it.techniqueId == GenericTechniqueCatalog.specialTechniqueId }
                Surface(
                    color = VitalPurpleBright.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, VitalPurpleBright),
                    shape = CutCornerShape(7.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("ESPECIAL FIXO", color = VitalPurpleBright, style = MaterialTheme.typography.labelSmall)
                        Text(special.displayName, color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
                        Text("${special.energyCost} EN · ${special.commandPointCost} CP · campo inteiro",
                            color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                        TechniqueStatusBadges(special, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Buscar técnica") },
                    shape = CutCornerShape(7.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = family == null, onClick = { family = null }, label = { Text("TODAS") })
                    GenericTechniqueFamily.entries.forEach { option ->
                        FilterChip(
                            selected = family == option,
                            onClick = { family = option },
                            label = { Text(option.label()) }
                        )
                    }
                }
            }
            error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            items(visibleEntries, key = { it.definition.techniqueId }) { entry ->
                val equippedSlot = selectedIds.indexOf(entry.definition.techniqueId)
                val equippedElsewhere = equippedSlot >= 0 && equippedSlot != selectedSlot
                TechniqueCatalogRow(
                    entry = entry,
                    selected = equippedSlot == selectedSlot,
                    enabled = !equippedElsewhere,
                    supportingLabel = if (equippedElsewhere) "Equipada no slot ${equippedSlot + 1}" else null,
                    onClick = {
                        selectedIds = selectedIds.toMutableList().also { it[selectedSlot] = entry.definition.techniqueId }
                    }
                )
            }
        }
    }
}

@Composable
private fun EquippedTechniqueSlot(
    slot: Int,
    technique: com.github.nacabaro.vbhelper.battle.offline.core.TechniqueDefinition,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) VitalCyan.copy(alpha = 0.12f) else SurfaceElevatedPurple,
        border = BorderStroke(1.dp, if (selected) VitalCyan else SurfaceStroke),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("0${slot + 1}", color = if (selected) VitalCyan else TextSecondaryOnDark, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("TÉCNICA REGULAR", color = TextSecondaryOnDark, style = MaterialTheme.typography.labelSmall)
                Text(technique.displayName, color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
                TechniqueStatusBadges(technique, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
private fun TechniqueCatalogRow(
    entry: GenericTechniqueEntry,
    selected: Boolean,
    enabled: Boolean,
    supportingLabel: String?,
    onClick: () -> Unit
) {
    val technique = entry.definition
    Surface(
        color = if (selected) VitalCyan.copy(alpha = 0.1f) else SurfaceElevatedPurple,
        border = BorderStroke(1.dp, if (selected) VitalCyan else SurfaceStroke),
        shape = CutCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    technique.displayName,
                    color = if (enabled) TextPrimaryOnDark else TextSecondaryOnDark,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${technique.energyCost} EN", color = VitalCyan, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "${entry.family.label()} · nível ${entry.rank} · ${technique.rangeProfile.label()} · ${technique.impactShape.label()}",
                color = TextSecondaryOnDark,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                supportingLabel ?: if (technique.power > 0) "Poder ${technique.power}${if (technique.hitCount > 1) " · ${technique.hitCount} acertos" else ""}"
                else "Aprimoramento próprio",
                color = if (supportingLabel == null) Color(0xFF9FE7EE) else TextSecondaryOnDark,
                style = MaterialTheme.typography.labelSmall
            )
            TechniqueStatusBadges(
                technique = technique,
                modifier = Modifier.padding(top = 6.dp),
                enabled = enabled
            )
        }
    }
}

private fun GenericTechniqueFamily.label(): String = when (this) {
    GenericTechniqueFamily.PRESSURE -> "PRESSÃO"
    GenericTechniqueFamily.CONTROL -> "CONTROLE"
    GenericTechniqueFamily.TEMPO -> "RITMO"
    GenericTechniqueFamily.ENDURANCE -> "RESISTÊNCIA"
    GenericTechniqueFamily.ASSAULT -> "ASSALTO"
    GenericTechniqueFamily.TACTICS -> "TÁTICA"
    GenericTechniqueFamily.PRECISION -> "PRECISÃO"
    GenericTechniqueFamily.CHAOS -> "CAOS"
}

private fun TechniqueRangeProfile.label(): String = when (this) {
    TechniqueRangeProfile.CUSTOM -> "alcance próprio"
    TechniqueRangeProfile.SELF -> "usuário"
    TechniqueRangeProfile.CLOSE -> "curto"
    TechniqueRangeProfile.CLOSE_MEDIUM -> "curto–médio"
    TechniqueRangeProfile.MEDIUM_LONG -> "médio–longo"
    TechniqueRangeProfile.ALL_FIELD -> "campo inteiro"
}

private fun TechniqueImpactShape.label(): String = when (this) {
    TechniqueImpactShape.SINGLE_TARGET -> "alvo único"
    TechniqueImpactShape.AROUND_USER -> "ao redor do usuário"
    TechniqueImpactShape.AROUND_TARGET -> "ao redor do alvo"
    TechniqueImpactShape.ALL_OPPONENTS -> "todos os oponentes"
}
