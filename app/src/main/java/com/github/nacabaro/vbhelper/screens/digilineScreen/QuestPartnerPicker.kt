package com.github.nacabaro.vbhelper.screens.digilineScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.quests.QuestInstance
import com.github.nacabaro.vbhelper.quests.QuestObjective
import com.github.nacabaro.vbhelper.quests.QuestObjectiveType
import com.github.nacabaro.vbhelper.quests.QuestPartnerOption
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageCharacterPickerDialog
import com.github.nacabaro.vbhelper.source.StorageRepository
import com.github.nacabaro.vbhelper.utils.DeviceType

/** Reuses the app's Storage selector and binds the selection by permanent identity. */
@Composable
fun QuestPartnerPicker(quest: QuestInstance, objectives: List<QuestObjective>, partners: List<QuestPartnerOption>,
                       onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as VBHelper
    val storage = remember(app.container.db) { StorageRepository(app.container.db) }
    val characters by storage.getAllCharacters().collectAsState(initial = null)
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
