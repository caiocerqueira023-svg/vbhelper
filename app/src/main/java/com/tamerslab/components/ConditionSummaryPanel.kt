package com.tamerslab.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tamerslab.R
import com.tamerslab.ui.*

@Composable
fun ConditionSummaryPanel(summary: String, modifier: Modifier = Modifier, details: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.immersion_hide_details else R.string.immersion_details))
            }
        }
        if (expanded) details()
    }
}

@Composable
fun rapportSummary(value: Int): String = stringResource(when (PartnerCondition.rapport(value)) {
    RapportCondition.LIMITED -> R.string.immersion_rapport_limited
    RapportCondition.DEVELOPING -> R.string.immersion_rapport_developing
    RapportCondition.ESTABLISHED -> R.string.immersion_rapport_established
})

@Composable
fun moodSummary(value: Int): String = stringResource(when (PartnerCondition.mood(value)) {
    MoodCondition.LOW -> R.string.immersion_mood_low
    MoodCondition.SETTLED -> R.string.immersion_mood_settled
    MoodCondition.POSITIVE -> R.string.immersion_mood_positive
})

@Composable
fun careSummary(energy: Int, satiety: Int, social: Int, funLevel: Int): String = stringResource(
    when (PartnerCondition.care(energy, satiety, social, funLevel)) {
        CarePriority.REST -> R.string.immersion_care_rest
        CarePriority.FEED -> R.string.immersion_care_feed
        CarePriority.SOCIALIZE -> R.string.immersion_care_socialize
        CarePriority.PLAY -> R.string.immersion_care_play
        CarePriority.NONE -> R.string.immersion_care_settled
    }
)
