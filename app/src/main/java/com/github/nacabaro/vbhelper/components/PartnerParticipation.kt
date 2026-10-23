package com.github.nacabaro.vbhelper.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.species.PartnerParticipationSnapshot
import kotlinx.coroutines.flow.flowOf

/** Rendering annotation only. Repository transactions always perform their own live check. */
@Composable
fun rememberPartnerParticipation(): PartnerParticipationSnapshot? {
    val app = LocalContext.current.applicationContext as? VBHelper
    val source = remember(app) { app?.container?.db?.participation?.observe() ?: flowOf(null) }
    val snapshot by source.collectAsState(initial = null)
    return snapshot
}
