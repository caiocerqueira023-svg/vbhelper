package com.tamerslab.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

data class CollectionLoad<T>(val data: List<T>?, val failed: Boolean, val retry: () -> Unit) {
    val blocking: Boolean get() = data == null || failed && data.isEmpty()
}
private data class CollectionSnapshot<T>(val data: List<T>? = null, val failed: Boolean = false)

@Composable
fun <T> rememberCollectionLoad(key: Any, observe: () -> Flow<List<T>>): CollectionLoad<T> {
    var attempt by remember(key) { mutableIntStateOf(0) }
    val snapshot by produceState(CollectionSnapshot<T>(), key, attempt) {
        value = value.copy(failed = false)
        try { observe().collect { value = CollectionSnapshot(it) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = value.copy(failed = true) }
    }
    return CollectionLoad(snapshot.data, snapshot.failed) { attempt++ }
}

/** True when it rendered the blocking first-load state; refresh failure keeps usable data. */
@Composable
fun CollectionStatePanel(load: CollectionLoad<*>, modifier: Modifier = Modifier): Boolean {
    if (!load.blocking) return false
    if (load.failed) CyberEmptyState(stringResource(R.string.app_load_failed), modifier,
        actionLabel = stringResource(R.string.app_retry), onAction = load.retry)
    else Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.audit_loading_collection), style = MaterialTheme.typography.bodyMedium)
        }
    }
    return true
}

@Composable
fun CollectionRefreshNotice(load: CollectionLoad<*>, modifier: Modifier = Modifier) {
    if (load.failed && load.data != null) Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.app_load_failed), Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = load.retry) { Text(stringResource(R.string.app_retry)) }
    }
}
