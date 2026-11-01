package com.tamerslab.companion.logs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.tamerslab.R
import com.tamerslab.companion.ui.CompanionConfirmation
import com.tamerslab.companion.ui.CompanionLoading
import com.tamerslab.di.TamersLabApplication

class CompanionWatchLogsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = this
        setContent {
            var accepted by remember { mutableStateOf(false) }
            if (!accepted) {
                CompanionConfirmation(prompt = stringResource(R.string.companion_logs_warning)) {
                    if (it) {
                        accepted = true
                    } else {
                        finish()
                    }
                }
            } else {
                CompanionLoading(loadingText = stringResource(R.string.settings_companion_send_watch_logs_title))
                LaunchedEffect(true) {
                    (application as TamersLabApplication).companionLogService.fetchWatchLogs(activity)
                }
            }
        }
    }
}


