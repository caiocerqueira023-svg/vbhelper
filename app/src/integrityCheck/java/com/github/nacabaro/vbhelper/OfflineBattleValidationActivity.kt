package com.github.nacabaro.vbhelper

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.nacabaro.vbhelper.screens.assetOfflineBattleParticipant
import com.github.nacabaro.vbhelper.screens.offlineBattle.*
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import com.github.nacabaro.vbhelper.ui.theme.AppTheme

/** Test-only host: exercises the real screen/renderer, without onboarding or user data. */
class OfflineBattleValidationActivity : ComponentActivity() {
    val battleModel by lazy { offlineBattleViewModel(this) }
    var previewTheme by mutableStateOf(AppTheme.VB_HELPER)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewTheme = AppTheme.fromPreference(intent.getStringExtra("theme"))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (battleModel.state.value.sessionId == null) {
            val a = assetOfflineBattleParticipant("dim000_mon03", "Pulsemon", 8, 3, stage = 3)
            val b = assetOfflineBattleParticipant("dim012_mon03", "Agumon", 3000, 1100, stage = 3)
            battleModel.start(this, "runtime", listOf(a), listOf(b), 7)
        }
        setContent {
            VBHelperTheme(appTheme = previewTheme) {
                OfflineTrainingBattleScreen(battleModel, { finish() },
                    Modifier.fillMaxSize().background(DeepPurpleBgAlt).statusBarsPadding())
            }
        }
    }
}
