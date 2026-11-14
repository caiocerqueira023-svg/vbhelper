package com.tamerslab.battle.ui.offline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tamerslab.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamerslab.battle.domain.TechniqueImpactShape
import com.tamerslab.battle.domain.TechniqueRangeProfile
import com.tamerslab.battle.offline.data.BlastEvolutionRepository
import com.tamerslab.battle.offline.data.BlastSoloForm
import com.tamerslab.battle.offline.data.DexJogressResolver
import com.tamerslab.battle.offline.data.GenericTechniqueCatalog
import com.tamerslab.battle.offline.data.GenericTechniqueEntry
import com.tamerslab.battle.offline.data.GenericTechniqueFamily
import com.tamerslab.battle.offline.data.GenericTechniqueLoadout
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.device_data.BlastEvolutionSlot
import com.tamerslab.ui.theme.DeepPurpleBgAlt
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.ui.theme.OnVitalAccent
import com.tamerslab.ui.theme.LocalAppPalette
import com.tamerslab.ui.theme.TextSignalHint
import com.tamerslab.ui.theme.VitalPurpleBright
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.VitalSearchField
import com.tamerslab.components.cyberFrame
import com.tamerslab.components.VitalSectionBar
import com.tamerslab.components.VitalTabs
import com.tamerslab.components.InstrumentSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TechniqueLoadoutScreen(
    characterId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val scope = rememberCoroutineScope()
    var individualId by remember(characterId) { mutableStateOf<String?>(null) }
    val fallbackName = stringResource(R.string.ui_battle_loadout_fallback_name, characterId)
    var characterName by remember(characterId, fallbackName) { mutableStateOf(fallbackName) }
    var speciesSpecialName by remember(characterId) { mutableStateOf<String?>(null) }
    var speciesName by remember(characterId) { mutableStateOf<String?>(null) }
    var blastMode by remember(characterId) { mutableStateOf(BlastEvolutionSlot.NONE) }
    var blastTarget by remember(characterId) { mutableStateOf<String?>(null) }
    var jogressResult by remember(characterId) { mutableStateOf<String?>(null) }
    var dimFormTargets by remember(characterId) { mutableStateOf(emptyList<String>()) }
    var universalForms by remember(characterId) { mutableStateOf(emptyList<BlastSoloForm>()) }
    var jogressOptions by remember(characterId) { mutableStateOf(emptyList<JogressOption>()) }
    var selectedIds by remember(characterId) { mutableStateOf(GenericTechniqueCatalog.defaultTechniqueIds) }
    var selectedSlot by remember(characterId) { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var section by rememberSaveable(characterId) { mutableIntStateOf(0) }
    var family by remember { mutableStateOf<GenericTechniqueFamily?>(null) }
    var loading by remember(characterId) { mutableStateOf(true) }
    var saving by remember(characterId) { mutableStateOf(false) }
    var errorRes by remember(characterId) { mutableStateOf<Int?>(null) }

    LaunchedEffect(characterId) {
        runCatching {
            withContext(Dispatchers.IO) {
                val db = application.container.db
                val character = db.userCharacterDao().getCharacter(characterId)
                val details = db.userCharacterDao().getCharacterWithSprites(characterId)
                val stored = db.digimonTechniqueLoadoutDao()
                    .getForIndividual(character.individualId)
                    .map { it.techniqueId }
                val speciesSpecial = db.speciesProfileDao()
                    .getByCardCharacterId(character.charId)?.specialMoves?.firstOrNull()
                val individual = db.digimonIndividualDao().getIndividual(character.individualId)
                val species = details.speciesName?.takeIf { it.isNotBlank() }
                val dimTargets = db.characterDao().getTransformationsFrom(character.charId)
                    .mapNotNull { it.toCharaId }
                    .mapNotNull { toId -> db.speciesProfileDao().getByCardCharacterId(toId) }
                    .mapNotNull { it.speciesName?.takeIf { name -> name.isNotBlank() } ?: it.matchedName?.takeIf { name -> name.isNotBlank() } }
                    .filter { it.lowercase() != species?.lowercase() }
                    .distinct()
                val blastData = BlastEvolutionRepository.load(application)
                val present = BlastEvolutionRepository.presentSet(
                    blastData, db.speciesProfileDao().getPresentSpeciesNames())
                val jogressEntries = BlastEvolutionRepository.availableJogress(blastData, species, present)
                val norm: (String) -> String = { BlastEvolutionRepository.normalize(blastData, it) }
                val universalOptions = jogressEntries.mapNotNull { entry ->
                    entry.partnerFor(species.orEmpty(), norm)?.let { partner ->
                        JogressOption(result = entry.result, partner = partner)
                    }
                }
                val dexSpec = DexJogressResolver.specificTargets(db, character.charId)
                    .map { (result, partner) -> JogressOption(result = result, partner = partner, fromDex = true) }
                val dexAttr = DexJogressResolver.attributeTargets(db, character.charId)
                    .map { (result, attribute) ->
                        JogressOption(result = result, partner = "", partnerAttribute = attribute, fromDex = true)
                    }
                BlastLoadoutData(
                    individualId = character.individualId,
                    displayName = details.nickname?.takeIf { it.isNotBlank() }
                        ?: species
                        ?: fallbackName,
                    techniques = GenericTechniqueLoadout.resolve(stored),
                    species = species,
                    speciesSpecial = speciesSpecial,
                    blastMode = individual?.blastMode?.takeIf(BlastEvolutionSlot::isValid) ?: BlastEvolutionSlot.NONE,
                    blastTarget = individual?.blastTargetSpecies,
                    jogressResult = individual?.jogressResultSpecies,
                    dimTargets = dimTargets,
                    universalForms = BlastEvolutionRepository.availableForms(blastData, species, present),
                    jogressOptions = mergeJogressOptions(universalOptions, dexSpec + dexAttr)
                )
            }
        }.onSuccess { loaded ->
            individualId = loaded.individualId
            characterName = loaded.displayName
            selectedIds = loaded.techniques
            speciesSpecialName = loaded.speciesSpecial
            speciesName = loaded.species
            blastMode = loaded.blastMode
            blastTarget = loaded.blastTarget
            jogressResult = loaded.jogressResult
            dimFormTargets = loaded.dimTargets
            universalForms = loaded.universalForms
            jogressOptions = loaded.jogressOptions
        }.onFailure {
            errorRes = R.string.ui_battle_loadout_load_error
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
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
            TopBanner(stringResource(R.string.app_techniques), onBackClick = onBack, subtitle = characterName)
            VitalTabs(section, listOf(stringResource(R.string.app_techniques), stringResource(R.string.uniform_finishers)), { section = it })
            }
        },
        bottomBar = {
            Surface(color = SurfaceElevatedPurple, border = BorderStroke(1.dp, SurfaceStroke)) {
                VitalButton(
                    style = VitalButtonStyle.PRIMARY,
                    onClick = {
                        val identity = individualId ?: return@VitalButton
                        saving = true
                        errorRes = null
                        val mode = blastMode.takeIf(BlastEvolutionSlot::isValid) ?: BlastEvolutionSlot.NONE
                        val target = blastTarget?.takeIf { mode == BlastEvolutionSlot.FORM }
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val db = application.container.db
                                    db.digimonTechniqueLoadoutDao()
                                        .replace(identity, GenericTechniqueLoadout.resolve(selectedIds))
                                    db.digimonIndividualDao().updateBlastChoice(identity, mode, target)
                                    db.digimonIndividualDao().updateJogressChoice(identity, jogressResult)
                                }
                            }.onSuccess { onBack() }
                                .onFailure { errorRes = R.string.ui_battle_loadout_save_error }
                            saving = false
                        }
                    },
                    enabled = !loading && !saving && individualId != null,
                    modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 52.dp)
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_battle_loadout_save), fontWeight = FontWeight.Bold)
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
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(InstrumentSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (section == 1) {
                item {
                    BlastSlotSection(blastMode, {
                        blastMode = it
                        if (it != BlastEvolutionSlot.FORM) blastTarget = null
                    }, dimFormTargets, universalForms.map { it.target }.distinct(), blastTarget, { blastTarget = it })
                }
                item { JogressSlotSection(jogressOptions, jogressResult, { jogressResult = it }) }
                errorRes?.let { error -> item { Text(stringResource(error), color = MaterialTheme.colorScheme.error) } }
                return@LazyColumn
            }
            item {
                Text(
                    stringResource(R.string.ui_battle_loadout_hint),
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
                    color = SurfaceElevatedPurple,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth().cyberFrame()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.ui_battle_loadout_fixed_special), color = VitalPurpleBright, style = MaterialTheme.typography.labelSmall)
                        Text(speciesSpecialName ?: special.displayName, color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.ui_battle_loadout_cost, special.energyCost),
                            color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                        TechniqueStatusBadges(special, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            item {
                VitalSearchField(query, { query = it }, stringResource(R.string.uniform_search_techniques))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = family == null, onClick = { family = null }, label = { Text(stringResource(R.string.ui_battle_loadout_all)) })
                    GenericTechniqueFamily.entries.forEach { option ->
                        FilterChip(
                            selected = family == option,
                            onClick = { family = option },
                            label = { Text(option.label()) }
                        )
                    }
                }
            }
            errorRes?.let { res ->
                item { Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            items(visibleEntries, key = { it.definition.techniqueId }) { entry ->
                val equippedSlot = selectedIds.indexOf(entry.definition.techniqueId)
                val equippedElsewhere = equippedSlot >= 0 && equippedSlot != selectedSlot
                TechniqueCatalogRow(
                    entry = entry,
                    selected = equippedSlot == selectedSlot,
                    enabled = !equippedElsewhere,
                    supportingLabel = if (equippedElsewhere) stringResource(R.string.ui_battle_loadout_equipped, equippedSlot + 1) else null,
                    onClick = {
                        selectedIds = selectedIds.toMutableList().also { it[selectedSlot] = entry.definition.techniqueId }
                    }
                )
            }
        }
    }
}

@Composable
private fun BlastSlotSection(
    mode: String,
    onModeChange: (String) -> Unit,
    dimTargets: List<String>,
    universalTargets: List<String>,
    selectedTarget: String?,
    onTargetChange: (String?) -> Unit
) {
    Surface(
        color = SurfaceElevatedPurple,
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth().cyberFrame()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_battle_loadout_blast_title), color = VitalPurpleBright, style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.ui_battle_loadout_blast_hint), color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlastModeChip(BlastEvolutionSlot.NONE, stringResource(R.string.ui_battle_blast_mode_none), mode, onModeChange)
                BlastModeChip(BlastEvolutionSlot.POWER, stringResource(R.string.ui_battle_blast_mode_power), mode, onModeChange)
                BlastModeChip(BlastEvolutionSlot.FORM, stringResource(R.string.ui_battle_blast_mode_form), mode, onModeChange)
            }
            if (mode == BlastEvolutionSlot.FORM) {
                Text(stringResource(R.string.ui_battle_loadout_blast_targets), color = TextPrimaryOnDark, style = MaterialTheme.typography.labelMedium)
                val dimSet = dimTargets.map { it.lowercase() }.toSet()
                val rows = dimTargets.map { it to true } +
                    universalTargets.filter { it.lowercase() !in dimSet }.map { it to false }
                if (rows.isEmpty()) {
                    Text(stringResource(R.string.ui_battle_prep_empty), color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
                }
                rows.forEach { (target, isDim) ->
                    val selected = selectedTarget?.equals(target, ignoreCase = true) == true
                    Surface(
                        color = if (selected) VitalCyan.copy(alpha = 0.12f) else SurfaceElevatedPurple,
                        border = BorderStroke(1.dp, if (selected) VitalCyan else SurfaceStroke),
                        shape = CutCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected, role = Role.RadioButton, onClick = { onTargetChange(if (selected) null else target) })
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(target, color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isDim) stringResource(R.string.ui_battle_loadout_blast_dim_tag)
                                else stringResource(R.string.ui_battle_loadout_blast_universal_tag),
                                color = VitalCyan, style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlastModeChip(mode: String, label: String, selected: String, onSelect: (String) -> Unit) {
    FilterChip(selected = selected == mode, onClick = { onSelect(mode) }, label = { Text(label) })
}

@Composable
private fun battleAttributeName(attribute: com.tamerslab.battle.domain.BattleAttribute): String =
    when (attribute) {
        com.tamerslab.battle.domain.BattleAttribute.VIRUS -> stringResource(R.string.ui_battle_attr_virus)
        com.tamerslab.battle.domain.BattleAttribute.DATA -> stringResource(R.string.ui_battle_attr_data)
        com.tamerslab.battle.domain.BattleAttribute.VACCINE -> stringResource(R.string.ui_battle_attr_vaccine)
        com.tamerslab.battle.domain.BattleAttribute.FREE -> stringResource(R.string.ui_battle_attr_free)
        com.tamerslab.battle.domain.BattleAttribute.NONE -> stringResource(R.string.ui_battle_attr_none)
    }

@Composable
private fun JogressSlotSection(
    options: List<JogressOption>,
    selectedResult: String?,
    onResultChange: (String?) -> Unit
) {
    Surface(
        color = SurfaceElevatedPurple,
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth().cyberFrame()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui_battle_loadout_jogress_title), color = VitalPurpleBright, style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.ui_battle_loadout_jogress_hint), color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall)
            JogressRow(
                title = stringResource(R.string.ui_battle_loadout_jogress_none),
                subtitle = null,
                selected = selectedResult == null,
                onClick = { onResultChange(null) }
            )
            options.forEach { option ->
                val partnerText = option.partnerAttribute?.let { attribute ->
                    stringResource(R.string.ui_battle_loadout_jogress_any, battleAttributeName(attribute))
                } ?: option.partner
                JogressRow(
                    title = option.result,
                    subtitle = stringResource(R.string.ui_battle_loadout_jogress_with, partnerText) +
                        if (option.fromDex) " · " + stringResource(R.string.ui_battle_loadout_blast_dim_tag)
                        else " · " + stringResource(R.string.ui_battle_loadout_blast_universal_tag),
                    selected = selectedResult?.equals(option.result, ignoreCase = true) == true,
                    onClick = { onResultChange(if (selectedResult?.equals(option.result, ignoreCase = true) == true) null else option.result) }
                )
            }
        }
    }
}

@Composable
private fun JogressRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) VitalCyan.copy(alpha = 0.12f) else SurfaceElevatedPurple,
        border = BorderStroke(1.dp, if (selected) VitalCyan else SurfaceStroke),
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(title, color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = TextSecondaryOnDark, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun EquippedTechniqueSlot(
    slot: Int,
    technique: com.tamerslab.battle.domain.TechniqueDefinition,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) VitalCyan.copy(alpha = 0.12f) else SurfaceElevatedPurple,
        border = BorderStroke(1.dp, if (selected) VitalCyan else SurfaceStroke),
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("0${slot + 1}", color = if (selected) VitalCyan else TextSecondaryOnDark, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.ui_battle_loadout_regular), color = TextSecondaryOnDark, style = MaterialTheme.typography.labelSmall)
                Text(battleTechniqueName(technique.techniqueId, technique.displayName), color = TextPrimaryOnDark, fontWeight = FontWeight.SemiBold)
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
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    battleTechniqueName(technique.techniqueId, technique.displayName),
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
                "${entry.family.label()} · ${stringResource(R.string.ui_battle_loadout_rank, entry.rank)} · ${technique.rangeProfile.label()} · ${technique.impactShape.label()}",
                color = TextSecondaryOnDark,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.ui_battle_loadout_power,
                    technique.power,
                    if (technique.hitCount > 1) stringResource(R.string.ui_battle_loadout_hits, technique.hitCount) else ""
                ).takeIf { supportingLabel == null && technique.power > 0 }
                    ?: supportingLabel
                    ?: stringResource(R.string.ui_battle_loadout_self_buff),
                color = if (supportingLabel == null) TextSignalHint else TextSecondaryOnDark,
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

private data class BlastLoadoutData(
    val individualId: String,
    val displayName: String,
    val techniques: List<String>,
    val species: String?,
    val speciesSpecial: String?,
    val blastMode: String,
    val blastTarget: String?,
    val jogressResult: String?,
    val dimTargets: List<String>,
    val universalForms: List<BlastSoloForm>,
    val jogressOptions: List<JogressOption>
)

internal data class JogressOption(
    val result: String,
    val partner: String,
    val fromDex: Boolean = false,
    /** Set for Dex attribute routes; rendered as "any X" in the current locale. */
    val partnerAttribute: com.tamerslab.battle.domain.BattleAttribute? = null
)

/** Universal options first, then Dex card-specific routes; deduped by result. */
internal fun mergeJogressOptions(
    universal: List<JogressOption>,
    dex: List<JogressOption>
): List<JogressOption> {
    val seen = linkedSetOf<String>()
    return (universal + dex).filter { seen.add(it.result.lowercase()) }
}

@Composable
private fun GenericTechniqueFamily.label(): String = when (this) {
    GenericTechniqueFamily.PRESSURE -> stringResource(R.string.ui_battle_family_pressure)
    GenericTechniqueFamily.CONTROL -> stringResource(R.string.ui_battle_family_control)
    GenericTechniqueFamily.TEMPO -> stringResource(R.string.ui_battle_family_tempo)
    GenericTechniqueFamily.ENDURANCE -> stringResource(R.string.ui_battle_family_endurance)
    GenericTechniqueFamily.ASSAULT -> stringResource(R.string.ui_battle_family_assault)
    GenericTechniqueFamily.TACTICS -> stringResource(R.string.ui_battle_family_tactics)
    GenericTechniqueFamily.PRECISION -> stringResource(R.string.ui_battle_family_precision)
    GenericTechniqueFamily.CHAOS -> stringResource(R.string.ui_battle_family_chaos)
}

@Composable
private fun TechniqueRangeProfile.label(): String = when (this) {
    TechniqueRangeProfile.CUSTOM -> stringResource(R.string.ui_battle_range_custom)
    TechniqueRangeProfile.SELF -> stringResource(R.string.ui_battle_range_self)
    TechniqueRangeProfile.CLOSE -> stringResource(R.string.ui_battle_range_close)
    TechniqueRangeProfile.CLOSE_MEDIUM -> stringResource(R.string.ui_battle_range_close_medium)
    TechniqueRangeProfile.MEDIUM_LONG -> stringResource(R.string.ui_battle_range_medium_long)
    TechniqueRangeProfile.ALL_FIELD -> stringResource(R.string.ui_battle_range_all_field)
}

@Composable
private fun TechniqueImpactShape.label(): String = when (this) {
    TechniqueImpactShape.SINGLE_TARGET -> stringResource(R.string.ui_battle_shape_single)
    TechniqueImpactShape.AROUND_USER -> stringResource(R.string.ui_battle_shape_around_user)
    TechniqueImpactShape.AROUND_TARGET -> stringResource(R.string.ui_battle_shape_around_target)
    TechniqueImpactShape.ALL_OPPONENTS -> stringResource(R.string.ui_battle_shape_all_opponents)
}
