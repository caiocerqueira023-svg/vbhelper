package com.tamerslab.world.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.species.ui.card.dialogs.DexSpritePortrait
import com.tamerslab.species.data.DigimonScanPolicy
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.utils.BitmapData

/** Observe receipts, never infer or award progress from a rendered outcome. */
@Composable
internal fun DigimonScanBattleRewards(database: AppDatabase, interactionId: String) {
    val rewardsFlow = remember(database, interactionId) { database.digimonScanDao().observeRewardDetails(interactionId) }
    val rewards by rewardsFlow.collectAsStateWithLifecycle(emptyList())
    if (rewards.isEmpty()) return
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(stringResource(R.string.digimon_scan_rewards_title), style = MaterialTheme.typography.titleSmall)
    rewards.forEach { reward ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DexSpritePortrait(BitmapData(reward.spriteIdle, reward.spriteWidth, reward.spriteHeight), Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(reward.speciesName ?: stringResource(R.string.digimon_scan_species_fallback, reward.charaIndex + 1),
                    style = MaterialTheme.typography.labelLarge)
                Text(reward.cardName, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (reward.percentageBefore == DigimonScanPolicy.CONVERSION_PERCENTAGE)
                    stringResource(R.string.digimon_scan_at_capacity)
                    else stringResource(R.string.digimon_scan_reward_progress,
                        reward.percentageBefore, reward.percentageAfter, reward.percentageAfter - reward.percentageBefore),
                    color = VitalCyan, style = MaterialTheme.typography.bodySmall)
                if (reward.percentageAfter == DigimonScanPolicy.CONVERSION_PERCENTAGE && reward.percentageBefore < reward.percentageAfter)
                    Text(stringResource(R.string.digimon_scan_ready), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

