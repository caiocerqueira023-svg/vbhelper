# Plan: AR Battle mode (radar battles seen through the camera)

**Status:** implementation in progress (feature branch: `main`, changes uncommitted).

## Product goal

A radar encounter can be fought in **AR**: the camera feed is the background, and the
two Digimon fight on a **surface the user chooses** (table, floor, park lawn). The battle
scale adapts to the surface: a table gets a miniature diorama (~1 m), an open park gets a
life-size brawl (multi-metre). The user moves around while the fight stays anchored where
they placed it, so every angle is reachable.

## Confirmed decisions

1. **Tech:** ARCore (`com.google.ar:core:1.57.0`) for motion tracking, horizontal plane
   detection and anchors. Filament 1.76.1 keeps rendering the actual battle (fighters,
   techniques, finishers) — we do **not** write a second battle renderer.
2. **Compositing:** two stacked views inside one square stage:
   - bottom: `ArCameraBackgroundView` — a `GLSurfaceView` that owns the ARCore session and
     draws the camera background (full-screen NDC quad, UVs from
     `frame.transformCoordinates2d`), exactly like ARCore's `BackgroundRenderer` sample;
   - top: the existing `OfflineBattleScene` Filament `TextureView`, now transparent
     (`View.BlendMode.TRANSLUCENT`, clear alpha 0, no skybox, arena dome hidden), with the
     Filament camera driven by the ARCore pose.
   A `SurfaceView` punches a hole in the window, so the transparent `TextureView` above it
   composites correctly. Compose HUD sits above both.
3. **One ARCore session for the whole encounter.** Placement (scan → tap surface) and the
   battle run in the same session, so the world frame and the anchor survive into the
   battle. The AR stage therefore mounts *before* the battle starts and stays until exit.
4. **Placement flow:** user taps "AR battle" on a radar encounter → the battle viewport
   becomes the AR stage (battle not started yet) → scan, tap a detected horizontal plane →
   choose surface/scale plan (Auto / Tabletop / Room / Open space) and pinch to fine-tune →
   Confirm → `startRadarBattle()` runs and the battle is rendered on the anchored surface.
5. **Scale mapping:** the radar arena is `2 * playableRadius` scene units wide (16 units for
   `Arena/Radar`). The plan chooses the arena width in metres; `metersPerSceneUnit =
   targetMetres / arenaWidthUnits`. Tabletop targets ~0.9 m, Room ~2.5 m, Open space ~8 m,
   Auto derives the target from the detected plane extents (clamped 0.5–12 m).
6. **Battle camera:** ARCore camera pose (metres) is converted into scene units relative to
   the placement anchor: `sceneCamera = inverse(anchor) * camera`, translation divided by
   `metersPerSceneUnit`. Filament gets `camera.setModelMatrix(...)` and a vertical FOV
   derived from ARCore's projection matrix (`fovY = 2*atan(1/P[5])`) with the stage aspect.
   The cinematic/orbit camera and the touch camera are disabled in AR; finisher effects keep
   playing but no longer move the camera.
7. **Fallback:** missing ARCore / denied camera permission / ARCore unsupported device →
   the battle starts normally in the Coliseum renderer. The radar game never blocks on AR.
8. **Testing:** pure math (`ArMath`, `ArBattleCamera`, `ArBattleScale`) is JVM-unit-tested.
   Device: `ArDiagnosticsActivity` (debug-only) and the real radar flow on the connected
   Galaxy A34 (ARCore installed). Screenshots via `adb exec-out screencap -p`.

## Shared contracts (owned by Lead, do not change without asking)

Package `com.tamerslab.ar`.

```kotlin
enum class ArTrackingState { TRACKING, PAUSED, STOPPED }
enum class ArPlaneType { HORIZONTAL_UPWARD, HORIZONTAL_DOWNWARD, VERTICAL, UNKNOWN }
enum class ArAvailability { SUPPORTED, SUPPORTED_NOT_INSTALLED, UNSUPPORTED, UNKNOWN }

class ArPose(translation: FloatArray /*3*/, rotation: FloatArray /*quat x,y,z,w*/) {
    fun matrix16(): FloatArray                 // column-major TRS
    fun inverse(): ArPose
    fun compose(other: ArPose): ArPose
    fun transformPoint(p: FloatArray): FloatArray
    fun rotateVector(v: FloatArray): FloatArray
}

data class ArCameraFrame(
    val timestampNanos: Long,
    val tracking: ArTrackingState,
    val pose: ArPose,                 // camera in AR world
    val viewMatrix: FloatArray,       // world -> camera, column-major
    val projectionMatrix: FloatArray, // column-major
    val viewportWidth: Int, val viewportHeight: Int,
    val displayRotation: Int,
)

data class ArPlaneSnapshot(
    val id: String, val type: ArPlaneType, val tracking: ArTrackingState,
    val centerPose: ArPose, val extentX: Float, val extentZ: Float,
    val polygon: FloatArray,          // plane-local (x,z) pairs
)

data class ArHitTestResult(
    val pose: ArPose, val distanceMeters: Float,
    val planeId: String?, val planeType: ArPlaneType,
)

data class ArFrameSnapshot(
    val camera: ArCameraFrame?, val planes: List<ArPlaneSnapshot>,
    val tracking: ArTrackingState, val trackingFailure: String? = null,
)

interface ArCameraSource { fun latestFrame(): ArFrameSnapshot? }
fun interface ArHitTester { fun hitTest(xPx: Float, yPx: Float, callback: (ArHitTestResult?) -> Unit) }

/** Stage frame: origin on the chosen surface, +Y along the surface normal, +Z toward the
 *  viewer at placement time. */
data class ArPlacement(val anchor: ArPose, val metersPerSceneUnit: Float)
```

## File ownership (avoid overlapping writes)

| Owner | Files |
|---|---|
| Lead | `ar/ArTypes.kt`, `ar/ArMath.kt`, `ar/ArCameraMathTest.kt`, `.plan/ar-battle.md`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/debug/**` |
| `ar-session` | `ar/ArAvailability.kt`, `ar/ArSessionController.kt`, `ar/ArCameraBackgroundView.kt`, `ar/ArBackgroundRenderer.kt`, `debug/ArDiagnosticsActivity.kt` (debug manifest entry) |
| `ar-scene` | `battle/ui/offline/OfflineBattleScene.kt` (AR hook), `ar/ArBattleCamera.kt`, `ar/ArBattleScene.kt`, `ArBattleCameraTest.kt` |
| `ar-placement` | `ar/ArBattleScale.kt`, `ar/ArBattleScaleTest.kt`, `ar/ArBattleStage.kt`, `ar/ArPlacementOverlay.kt`, `res/values*/strings.xml` |
| `ar-integration` | `world/ui/WorldScreen.kt`, `world/ui/RadarPlaceScreen.kt`, `battle/ui/offline/OfflineTrainingBattleScreen.kt` (`WorldRadarBattleContent` AR branch), `world/ui/RadarFirstPersonGeometry.kt` if needed |
| `ar-verify` | `AR_BATTLE_PLAN.md` / docs, review notes, extra tests (read-only elsewhere) |

Cross-team API surface (must exist even if internals evolve):

```kotlin
// ar-session
class ArCameraBackgroundView(context: Context) : GLSurfaceView {
    val cameraSource: ArCameraSource
    val hitTester: ArHitTester
    fun setAvailabilityListener(onStatus: (ArAvailability) -> Unit)   // optional
    fun startAr()          // start session once permission granted
    fun pauseAr() / resumeAr()
    fun releaseAr()
    fun requestInstall(activity: Activity): Boolean
}

// ar-scene
@Composable
fun ArBattleScene(
    cameraSource: ArCameraSource,
    placement: ArPlacement,
    battle: ArBattleContent,
    onSceneViewReady: (OfflineBattleSceneView?) -> Unit,
    onSceneReady: () -> Unit,
    onProjectionChanged: () -> Unit,
    onFighterTapped: (String) -> Unit,
    onRendererReleased: () -> Unit,
    onRendererCreated: () -> Unit,
    onAssetError: (String) -> Unit,
    modifier: Modifier = Modifier,
)
data class ArBattleContent(
    val snapshot: BattleSnapshot?,
    val fighters: Map<String, BattleFighterPresentation>,
    val manifest: OfflineArenaManifest?,
    val sessionId: String?,
    val preparedForms: Map<String, BattleFighterPresentation>,
    val allowMotion: Boolean,
    val sceneSnapshotFlow: StateFlow<BattleSnapshot?>?,
)

// ar-placement
@Composable
fun ArBattleStage(
    phase: ArStagePhase,                 // SCANNING or BATTLE
    battle: ArBattleContent?,            // null while scanning
    onPlacementConfirmed: (ArPlacement) -> Unit,
    onCancel: () -> Unit,
    onSceneViewReady: (OfflineBattleSceneView?) -> Unit,
    onSceneReady: () -> Unit,
    onProjectionChanged: () -> Unit,
    onFighterTapped: (String) -> Unit,
    onRendererReleased: () -> Unit,
    onRendererCreated: () -> Unit,
    onAssetError: (String) -> Unit,
    onAvailabilityChanged: (ArAvailability) -> Unit,
    modifier: Modifier = Modifier,
)
enum class ArStagePhase { SCANNING, BATTLE }
```

## UX

- **Scanning phase** (inside the existing battle viewport slot): camera feed, detected
  horizontal planes outlined, centre reticle; instruction text ("Point at a table or the
  floor and tap to place the battle"); a disabled "Confirm" until a hit lands. On hit: show
  a translucent arena disc at the hit point, plan chips `Auto | Tabletop | Room | Open
  space`, pinch-to-resize, drag-to-rotate, "Place battle" + "Back". AR unavailable/denied →
  explanatory card + "Fight normally" (fallback) / "Try again".
- **Battle phase:** the existing square viewport now shows the camera feed with the fight
  anchored on it; HUD overlays (HP bars, VFX, results, command deck) stay unchanged
  underneath the stage. A small "AR" badge + "Re-place" action is available while the
  battle is paused.
- AR preference is a saved radar control toggle ("Battles in AR"), same pattern as the
  current 2D/First-person toggle. Default off until the user enables it.

## Verification

- `gradlew.bat :app:compileDebugKotlin`
- `gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.tamerslab.ar.*"`
- `gradlew.bat :app:installDebug` + `adb shell am start -n com.github.nacabaro.vbhelper/.MainActivity`
- ARCore smoke: debug diagnostics activity → camera feed visible, planes outlined, tap
  creates a marker, tracking status updates; battle scene composited over the feed.
- Existing suites stay green (`RadarBattleFlowTest`, `OfflineTrainingBattleScreenTest`,
  `EnvironmentThemeCatalogTest`, etc.).

## Risks

- GL/passthrough compositing (SurfaceView behind a translucent Filament TextureView) — the
  diagnostics activity proves it first; fallback is a `TextureView`-based camera renderer.
- One Filament engine rule: the AR stage still goes through `RadarRendererOwnership`
  (battle), and the camera view is not Filament, so no extra engine is created.
- ARCore lifecycle (pause/resume, camera permission, device rotation) can be iterated on
  the attached device.
- Device date/time and dependency downloads are available (ARCore 1.57.0 resolved).
