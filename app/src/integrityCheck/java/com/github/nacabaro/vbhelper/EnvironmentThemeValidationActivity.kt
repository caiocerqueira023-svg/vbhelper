package com.github.nacabaro.vbhelper

import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.screens.digifarmScreen.Digifarm3dSceneView
import com.github.nacabaro.vbhelper.screens.digifarmScreen.Digifarm3dViewport
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineArenaManifest
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineBattleScene
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineBattleSceneView
import com.github.nacabaro.vbhelper.screens.worldScreen.RadarFirstPersonSceneView
import com.github.nacabaro.vbhelper.screens.worldScreen.RadarFirstPersonViewport
import com.github.nacabaro.vbhelper.ui.theme.AppTheme
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemSnapshot
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemStatus
import com.github.nacabaro.vbhelper.world.ecosystem.WorldPlayerFix

/** Isolated native host for the real Compose→Filament theme-delivery path. */
class EnvironmentThemeValidationActivity : ComponentActivity() {
    var previewTheme by mutableStateOf(AppTheme.VB_LAB)
    var renderingActive by mutableStateOf(true)
    var renderingFailure: String? = null
        private set
    lateinit var environment: String
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        environment = intent.getStringExtra("environment") ?: "farm"
        val arena = if (environment in setOf("colosseum", "radar")) OfflineArenaManifest.read(this,
            if (environment == "radar") OfflineArenaManifest.RADAR_MANIFEST_PATH else OfflineArenaManifest.DEFAULT_MANIFEST_PATH) else null
        val snapshot = EcosystemSnapshot(status = EcosystemStatus.READY,
            playerFix = WorldPlayerFix(GeoPoint(0.0, 0.0), System.currentTimeMillis()))
        setContent {
            VBHelperTheme(appTheme = previewTheme) {
                Column(Modifier.fillMaxSize().background(previewTheme.palette.background).safeDrawingPadding()) {
                    Text("$environment · ${previewTheme.displayName}", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(16.dp))
                    val viewport = Modifier.fillMaxWidth().weight(1f)
                    when (environment) {
                        "farm" -> Digifarm3dViewport(viewport, onAssetError = { renderingFailure = it })
                        "radar_fp" -> RadarFirstPersonViewport(snapshot, emptyList(), 0f, 0.2f,
                            renderingActive, false, {}, { renderingFailure = "Radar renderer failed" }, {}, modifier = viewport)
                        else -> OfflineBattleScene(null, emptyMap(), arena, "theme-preview", {}, {}, {}, {},
                            { renderingFailure = it }, renderingEnabled = renderingActive, modifier = viewport)
                    }
                }
            }
        }
    }

    internal fun nativeView(): TextureView? = findViewport(window.decorView)
    internal fun probe(): EnvironmentThemeProbe? = when (val view = nativeView()) {
        is Digifarm3dSceneView -> EnvironmentThemeProbe(view.appliedEnvironmentTheme, view.environmentThemeFailure,
            view.environmentMaterialNames, view.environmentLoadCount)
        is OfflineBattleSceneView -> EnvironmentThemeProbe(view.appliedEnvironmentTheme, view.environmentThemeFailure,
            view.environmentMaterialNames, view.environmentLoadCount)
        is RadarFirstPersonSceneView -> EnvironmentThemeProbe(view.appliedEnvironmentTheme, view.environmentThemeFailure,
            view.environmentMaterialNames, view.environmentLoadCount)
        else -> null
    }

    private fun findViewport(view: View): TextureView? {
        if (view is TextureView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findViewport(view.getChildAt(i))?.let { return it }
        return null
    }
}

internal data class EnvironmentThemeProbe(val theme: AppTheme?, val failure: String?, val materials: Set<String>, val loads: Int)
