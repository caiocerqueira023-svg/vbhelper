package com.tamerslab.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.species.PartnerParticipationSnapshot
import kotlinx.coroutines.flow.flowOf

data class PartnerParticipationOverride(val snapshot: PartnerParticipationSnapshot?)

val LocalPartnerParticipationOverride = staticCompositionLocalOf<PartnerParticipationOverride?> { null }

/** Rendering annotation only. Repository transactions always perform their own live check. */
@Composable
fun rememberPartnerParticipation(): PartnerParticipationSnapshot? {
    LocalPartnerParticipationOverride.current?.let { return it.snapshot }
    val app = LocalContext.current.applicationContext as? TamersLabApplication
    val source = remember(app) { app?.container?.db?.participation?.observe() ?: flowOf(null) }
    val snapshot by source.collectAsStateWithLifecycle(initialValue = null)
    return snapshot
}

