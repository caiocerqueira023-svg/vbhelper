package com.tamerslab

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
import com.tamerslab.farm.ui.digifarm.Digifarm3dSceneView
import com.tamerslab.farm.ui.digifarm.Digifarm3dViewport
import com.tamerslab.battle.ui.offline.OfflineArenaManifest
import com.tamerslab.battle.ui.offline.OfflineBattleScene
import com.tamerslab.battle.ui.offline.OfflineBattleSceneView
import com.tamerslab.world.ui.RadarFirstPersonSceneView
import com.tamerslab.world.ui.RadarFirstPersonViewport
import com.tamerslab.ui.theme.AppTheme
import com.tamerslab.ui.theme.TamersLabTheme
import com.tamerslab.world.GeoPoint
import com.tamerslab.world.ecosystem.EcosystemSnapshot
import com.tamerslab.world.ecosystem.EcosystemStatus
import com.tamerslab.world.ecosystem.WorldPlayerFix

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
        previewTheme = intent.getStringExtra("theme")?.let(AppTheme::fromPreference) ?: AppTheme.VB_LAB
        val arena = if (environment in setOf("colosseum", "radar")) OfflineArenaManifest.read(this,
            if (environment == "radar") OfflineArenaManifest.RADAR_MANIFEST_PATH else OfflineArenaManifest.DEFAULT_MANIFEST_PATH) else null
        val snapshot = EcosystemSnapshot(status = EcosystemStatus.READY,
            playerFix = WorldPlayerFix(GeoPoint(0.0, 0.0), System.currentTimeMillis()))
        setContent {
            TamersLabTheme(appTheme = previewTheme) {
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
