package com.tamerslab.farm.ui.digiline

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tamerslab.R
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.quests.QuestInstance
import com.tamerslab.quests.QuestObjective
import com.tamerslab.quests.QuestObjectiveType
import com.tamerslab.quests.QuestPartnerOption
import com.tamerslab.storage.ui.StorageCharacterPickerDialog
import com.tamerslab.storage.data.StorageRepository
import com.tamerslab.utils.DeviceType

/** Reuses the app's Storage selector and binds the selection by permanent identity. */
@Composable
fun QuestPartnerPicker(quest: QuestInstance, objectives: List<QuestObjective>, partners: List<QuestPartnerOption>,
                       onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as TamersLabApplication
    val storage = remember(app) { app.container.storageRepository }
    val characters by storage.getAllCharacters().collectAsStateWithLifecycle(initialValue = null)
    val requiresVb = objectives.any { it.type == QuestObjectiveType.WATCH_TROPHIES }
    val requiresBe = objectives.any { it.type in setOf(QuestObjectiveType.WATCH_PP_EARN, QuestObjectiveType.WATCH_PP_REACH) }
    val watchCard = objectives.firstOrNull { it.type == QuestObjectiveType.WATCH_ADVENTURE_ADVANCE }?.watchCardId
    val candidates = partners.filter { option ->
        (quest.partnerId == null || quest.partnerId == option.individualId) &&
            (!requiresVb || option.deviceType == DeviceType.VBDevice) &&
            (!requiresBe || option.deviceType == DeviceType.BEDevice) && (watchCard == null || option.cardId == watchCard)
    }.associateBy { it.characterId }
    StorageCharacterPickerDialog(
        characters = characters.orEmpty().filter { it.id in candidates && !it.isInAdventure },
        title = stringResource(R.string.quest_choose_partner),
        emptyMessage = stringResource(if (quest.partnerId != null) R.string.quest_partner_resume_missing else R.string.quest_partner_empty),
        isLoading = characters == null,
        onDismiss = onDismiss,
        onCharacterSelected = { characterId -> candidates[characterId]?.let { onSelected(it.individualId) } }
    )
}

