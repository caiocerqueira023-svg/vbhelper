package com.tamerslab.home.ui.dialogs

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamerslab.R
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.cyberFrame
import com.tamerslab.components.editorDialogBounds
import com.tamerslab.components.showAppFeedback
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.home.ui.HomeScreenController
import com.tamerslab.items.data.ItemsRepository
import com.tamerslab.items.data.SpecialMissionAssignment
import com.tamerslab.items.data.SpecialMissionSlots
import com.tamerslab.items.ui.getIconResource
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/**
 * Equip one owned special-mission item into a partner's mission row.
 *
 * [slot] is the tapped card's position among the partner's mission rows (0..3, never the
 * device-side watchId). It picks the slot label and the eligible item kinds, so the list cannot
 * offer a mission of the wrong kind. [missionId] is the exact row to equip, so the update can
 * never land on a different mission.
 *
 * A failed equip stays inside the dialog: a snackbar is posted in the activity window, which sits
 * behind the dialog, so it would be unreadable while the dialog is open.
 */
@Composable
fun AssignSpecialMissionDialog(
    characterId: Long,
    slot: Int,
    missionId: Long,
    homeScreenController: HomeScreenController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as TamersLabApplication
    val repository = remember(application) { ItemsRepository(application.container.db) }
    val owned by remember(repository) { repository.getUserItems() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val candidates = remember(owned, slot) {
        SpecialMissionAssignment.candidatesForSlot(owned, slot)
    }
    var assigning by remember { mutableStateOf(false) }
    var assignFailed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!assigning) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !assigning,
            dismissOnClickOutside = !assigning,
        ),
    ) {
        CyberPanel(
            modifier = Modifier.editorDialogBounds().fillMaxWidth().padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_special_mission_assign_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimaryOnDark,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.home_special_mission_assign_slot,
                    stringResource(specialMissionSlotLabelRes(slot)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (candidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_special_mission_assign_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryOnDark,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(candidates, key = { it.id }) { item ->
                        CyberPanel(
                            onClick = {
                                if (assigning) return@CyberPanel
                                assigning = true
                                assignFailed = false
                                homeScreenController.assignSpecialMission(
                                    item.id,
                                    characterId,
                                    missionId,
                                ) { applied ->
                                    assigning = false
                                    if (applied) {
                                        onDismiss()
                                        context.showAppFeedback(R.string.home_special_mission_assigned)
                                    } else {
                                        assignFailed = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerLowest,
                                            RectangleShape,
                                        )
                                        .cyberFrame(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(getIconResource(item.itemIcon)),
                                        contentDescription = null,
                                        tint = VitalCyan,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimaryOnDark,
                                        fontWeight = FontWeight.SemiBold,
                                        minLines = 2,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(R.string.items_quantity_badge, item.quantity),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondaryOnDark,
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.home_special_mission_equip),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VitalCyan,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            if (assignFailed) {
                Text(
                    text = stringResource(R.string.home_special_mission_assign_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                VitalButton(
                    onClick = onDismiss,
                    enabled = !assigning,
                    style = VitalButtonStyle.SECONDARY,
                ) {
                    Text(stringResource(R.string.item_dialog_cancel))
                }
            }
            if (assigning) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

/** The slot label shown in the dialog and on the partner overview cards. */
@StringRes
internal fun specialMissionSlotLabelRes(slot: Int): Int = when (slot) {
    SpecialMissionSlots.STEPS -> R.string.special_mission_slot_steps
    SpecialMissionSlots.VITALS -> R.string.special_mission_slot_vitals
    SpecialMissionSlots.BATTLES -> R.string.special_mission_slot_battles
    else -> R.string.special_mission_slot_wins
}
