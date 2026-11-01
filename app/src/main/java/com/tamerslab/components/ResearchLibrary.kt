package com.tamerslab.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.chat.lorebook.ResearchReference
import com.tamerslab.chat.lorebook.ResearchSources

/** Published source access belongs in ordinary research views; implementation provenance belongs in Debug. */
@Composable
fun ResearchLibrary(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.immersion_hide_references else R.string.immersion_references))
        }
        if (expanded) {
            Text(stringResource(R.string.immersion_primary_sources), style = MaterialTheme.typography.titleSmall)
            ResearchSources.aboutTab.forEach { reference ->
                ReferenceLink(reference) { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .onFailure { context.showAppFeedback(R.string.immersion_reference_open_failed) }
                }
            }
            Text(stringResource(R.string.immersion_supplementary_sources), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp))
            ResearchSources.supplementary.forEach { reference ->
                ReferenceLink(reference) { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .onFailure { context.showAppFeedback(R.string.immersion_reference_open_failed) }
                }
            }
            Text(stringResource(R.string.immersion_reference_scope), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReferenceLink(reference: ResearchReference, onOpen: (String) -> Unit) {
    val title = when (reference) {
        ResearchSources.overview -> stringResource(R.string.immersion_source_overview)
        ResearchSources.profiles -> stringResource(R.string.immersion_source_profiles)
        ResearchSources.deviceHistory -> stringResource(R.string.immersion_source_devices)
        ResearchSources.gameHistory -> stringResource(R.string.immersion_source_games)
        ResearchSources.iliad -> "Digital World · Iliad"
        ResearchSources.shambhala -> "Digital World · Shambhala"
        else -> reference.title
    }
    TextButton(onClick = { reference.url?.let(onOpen) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
    }
}
