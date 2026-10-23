package com.github.nacabaro.vbhelper.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.annotation.StringRes
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.source.DebugSettings
import kotlinx.coroutines.flow.MutableStateFlow

/** Previews and standalone validation hosts are fail-closed unless backed by the real app. */
@Composable
fun rememberDebugSettings(): DebugSettings {
    val application = LocalContext.current.applicationContext as? VBHelper
    val state = remember(application) {
        application?.container?.debugSettingsRepository?.state ?: MutableStateFlow(DebugSettings())
    }
    return state.collectAsState().value
}

@Composable
fun DebugDetails(details: String?) {
    if (!rememberDebugSettings().enabled || details.isNullOrBlank()) return
    var expanded by remember(details) { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.immersion_technical_details)) }
        if (expanded) Text(com.github.nacabaro.vbhelper.source.DiagnosticText.sanitize(details),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun Context.operationFailureMessage(@StringRes summary: Int, details: String?): String =
    com.github.nacabaro.vbhelper.source.DiagnosticText.feedback(getString(summary), details,
        (applicationContext as? VBHelper)?.container?.debugSettingsRepository?.enabled == true,
        getString(R.string.immersion_technical_details))
