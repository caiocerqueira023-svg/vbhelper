# AR battles

A radar encounter can be fought in **AR**: the camera feed becomes the background and the
two Digimon fight on a real surface the user picks (table, floor, park lawn). The arena
scale follows the surface — a table gets a miniature diorama, a park gets a life-size
brawl — and the fight stays anchored to that spot while the user walks around it.

The battle itself is still rendered by the existing offline Filament scene (fighters,
techniques, Blast timing, finishers). AR only adds a camera-passthrough presentation layer
and a placement model; there is no second battle renderer.

## Implementation status

Source implementation is complete on the uncommitted working tree. The pure placement/
camera maths has JVM unit-test coverage and the full unit suite is run as part of the
verification recorded below. On-device acceptance is owned by the Lead; the verified device
evidence available at hand-off is recorded below (transparent Filament over the live camera,
stage scanning UI, compositing controls), while the full plane-tap → anchored-fight path
remains a hands-on acceptance item.

The final tree additionally includes the fixes found during verification: the transparent
swap-chain installer for Filament's `ModelViewer` (the battle scene would otherwise cover
the camera with black), the stage's attached-view release guard, the renderer ownership gate
around the AR battle content (single Filament engine invariant), GL context-loss recreation
in the camera background, the on-device `#version`-first GLSL shader fix,
`rememberUpdatedState` in the stage lifecycle observer, and debug-only Timber logging in the
diagnostics/preview activities. See
[Fixes found during verification](#fixes-found-during-verification) and
[Known limitations](#known-limitations) for what remains.

No database schema change was made. No permission was added beyond `CAMERA`. ARCore is an
*optional* dependency: devices without ARCore keep playing exactly as before.

## Product behaviour

- **Opt-in.** The radar view preferences card has a **"AR battles"** switch
  (`ar_battle_toggle`) with a one-line description. It is off by default, saved with the
  other radar view preferences (same `rememberSaveable` pattern as 2D/first-person and
  entity labels), and does not affect anything until a battle is started manually.
- **Encounter → AR scanner.** With the switch on, tapping **Battle** on a radar encounter
  card does not start the fight immediately. The existing square battle viewport becomes the
  AR stage: live camera feed, outlined detected horizontal planes and a centre reticle.
- **Hands-free placement (default flow).** The player only points the phone at a table or the
  floor — there is nothing to tap or choose. The scanner continuously hit-tests a small cross
  in screen space (real planes first, then ARCore depth points, then instant placement),
  proposes an arena at the surface under the reticle, chooses the scale plan automatically
  from the detected surface, and **starts the battle by itself** once the candidate pose has
  been stable for a few samples. Quality gates keep it from firing too early: a detected
  plane locks after 2 samples, a depth surface after 3 stable samples (≥1.2 s), and the
  surface-less instant-placement fallback only after 4 samples and ≥2.5 s. The only control
  is a small **Cancel** action; the normal **Back** gesture also exits.
- **Auto scale.** `AUTO` is derived from the chosen surface: plane extents when a plane is
  tracked, otherwise the tap distance estimate (shortest extent 0.6–12 m). The plan target
  arena diameter is 0.9 m (tabletop), 2.5 m (room) or 8 m (open space), and `AUTO` uses
  `0.75 ×` the shortest detected extent. Metres-per-scene-unit is clamped to 0.02–1.5 m so a
  bad surface estimate can never produce an unusable placement.
- **Level by construction.** Plane hits keep the plane orientation so the arena follows the
  real surface. Depth and instant-placement hits are camera-oriented under the hood, so the
  anchor is rebuilt gravity-aligned (`+Y` world up, `+Z` toward the viewer) — this is what
  stops the arena/battle from appearing sideways or tilted.
- **Minimal AR scene.** The AR battle shows only the two Digimon plus the ring that delimits
  the battle space. The shared radar arena mesh has three material primitives
  (`radar_floor`, `world_grid`, `battle_boundary`); AR keeps only the `battle_boundary`
  ring (radius 8 scene units = the playable circle), and hides the floor, grid, sky dome and
  voxel debris so the camera image is the whole backdrop.
- **Battle.** Confirming starts the battle without leaving the stage, so the fight appears
  anchored where the scanner locked it in the same ARCore world frame. The user can walk
  around the arena; device pose drives the Filament battle camera, while fighters and
  effects keep their usual animation/billboard behaviour. The normal HUD (HP bars, target
  selection, command deck, Blast timing, results, rewards) is unchanged and drawn above the
  camera. A small **AR** badge marks the presentation; tracking loss/pause shows a banner
  and the camera holds its last pose instead of snapping.
- **Exit / fallback.** Cancelling the scanner returns to the radar. If AR cannot run
  (unsupported device, ARCore missing/outdated, camera permission denied) the stage shows
  an explanatory card with **Fight normally**, and the battle falls back to the regular
  Coliseum renderer after teardown. The radar itself never blocks on AR.

### Scale plans

The radar arena is `2 * playableRadius` scene units wide — **16 scene units** for
`Arena/Radar`. A plan only chooses how wide that arena should be in the real world; the
resulting metres-per-scene-unit value scales every arena scene unit, fighter offset and
camera translation uniformly.

| Plan | Target arena diameter (m) | Typical surface |
|---|---|---|
| `AUTO` | `0.75 ×` the plane's shortest extent, clamped to 0.5–12 m | whatever was tapped |
| `TABLETOP` | 0.9 | table / desk |
| `ROOM` | 2.5 | living-room or bedroom floor |
| `OPEN_SPACE` | 8.0 | park, lawn, plaza |

`AUTO` is chosen automatically from the detected surface: shortest extent < 1.4 m → Tabletop,
< 4 m → Room, otherwise Open space. (The hands-free scanner has no plan chips or pinch
gesture; the plan and arena size are derived from the surface and clamp to the same ranges.)
Every metres-per-scene-unit value is clamped to 0.02–1.5 m so a bad plane estimate can never
produce an unusable placement.

## Architecture

Three stacked layers inside one square `Box` (`aspectRatio(1f)`) — the pre-existing battle
viewport:

```
window / View hierarchy                       Compose / Filament
────────────────────────────────────────      ────────────────────────────────────────────
ArCameraBackgroundView (GLSurfaceView)        camera feed, full-screen OES quad,
  └─ ArSessionController                      owns the one ARCore Session
  └─ ArBackgroundRenderer                     ARCore BackgroundRenderer port (GLES 3.0)
  └─ cameraSource / hitTester                 one ArFrameSnapshot per frame; hit tests
OfflineBattleSceneView (TextureView,          transparent Filament scene: arena floor,
  BlendMode.TRANSLUCENT, hosted in AR on      fighters, VFX
  ArTransparentSwapChainInstaller)
  └─ camera model matrix from ARCore pose     ArBattleCamera (inverse anchor, m→units)
Compose HUD (WorldRadarBattleHud,             HP bars, commands, Blast overlay, results,
  ArPlacementOverlay while scanning)          rewards, AR badge, tracking banners
```

- **One ARCore session per encounter.** Placement and battle run in the same `Session`,
  so the world frame and the placement anchor survive the SCANNING → BATTLE transition.
  `setDisplayGeometry` is refreshed from the GL surface whenever the surface size/rotation
  changes; hit tests are queued to the GL thread and resolved right after
  `Session.update()`.
- **One Filament engine.** The AR stage reuses `OfflineBattleSceneView` — the same view,
  engine, scene and asset pipeline as a normal battle. `setArPresentation()` only toggles
  the presentation (translucent blend mode, transparent clear colour, no skybox, arena
  occluders hidden, AR camera source) plus the transparent swap-chain host; it never
  creates a second engine. The AR presentation destroys its detached viewer exactly once
  (`destroyViewerOnce`) when the stage detaches, and the engine teardown remains on the
  normal `releaseScene()` path.
- **Renderer ownership.** While the AR stage is mounted, `RadarScreen` requests
  `RadarRendererOwner.BATTLE` (even during scanning), which keeps the AR camera layer from
  competing with the first-person radar renderer. The existing generation-stamped release
  handshake (`RadarRendererOwnership`) is reused unchanged; the AR scene reports its
  renderer create/release through the same callbacks as a normal radar battle.
- **Threading.** ARCore `Session` create/configure/resume/pause/close run on the main
  thread. `Session.update()`, the camera-background draw and hit tests run on the GL
  thread. Immutable `ArFrameSnapshot`/`ArPose` values cross threads through an
  `AtomicReference`; the Compose overlay polls at ~30 Hz, the battle scene polls once per
  Choreographer frame.

## File map

New package `com.tamerslab.ar` (`app/src/main/java/com/tamerslab/ar/`):

| File | Role |
|---|---|
| `ArTypes.kt` | Shared contracts: `ArPose`, `ArCameraFrame`, `ArPlaneSnapshot`, `ArHitTestResult`, `ArFrameSnapshot`, `ArCameraSource`, `ArHitTester`, `ArPlacement`, enums. Plain JVM data, no Android/ARCore types. |
| `ArMath.kt` | Column-major matrix helpers, FOV recovery, point projection, plane-local→world, scene near/far planes. |
| `ArBattleScale.kt` | Scale plans, plane size, metres↔scene units, clamps, placement frame (`+Y` normal, `+Z` toward viewer), yaw/rotate helpers. |
| `ArBattleCamera.kt` | `arSceneCamera()`: ARCore pose → Filament camera model matrix, FOV, aspect, clip planes; yaw/pitch/eye helpers shared with the battle camera conventions. |
| `ArAvailability.kt` | ARCore availability mapping, CAMERA permission check, install request, diagnostic strings (English; UI copy is localized). |
| `ArSessionController.kt` | Owns the single `Session`; create/configure/resume/pause/close; display geometry; ARCore type → plain-data converters. |
| `ArBackgroundRenderer.kt` | GLES 3.0 camera background (adapted from ARCore `hello_ar` `BackgroundRenderer`, Apache 2.0). |
| `ArCameraBackgroundView.kt` | `GLSurfaceView` that owns the ARCore session, draws the camera feed, publishes `cameraSource`/`hitTester`; permission/availability lifecycle. |
| `ArBattleScene.kt` | `ArBattlePresentation` / `ArBattleContent` + `ArBattleScene` composable: the transparent Filament battle over the feed. |
| `ArBattleStage.kt` | `ArStagePhase`, the AR stage: camera view, permission/install/unavailable cards, SCANNING overlay, BATTLE scene, AR badge, tracking banner. |
| `ArPlacementOverlay.kt` | Scanning UI: plane outlines, reticle, arena preview, scale chips, pinch/rotate gestures, confirm/cancel. |
| `ArTransparentSwapChain.kt` | `ArTransparentSwapChainInstaller`: hosts a *detached* `ModelViewer(engine, uiHelper)` on a `SwapChainFlags.CONFIG_TRANSPARENT` swap chain created over the `TextureView`'s `SurfaceTexture`, reflects it into `ModelViewer`'s private `swapChain` field and mirrors `UiHelper.mHasSwapChain`, keeps the viewport in sync and flushes/destroys the chain on teardown. Needed because Filament 1.76.1's `ModelViewer` hardcodes an opaque swap chain, which would cover the camera with black. |

New debug-only resources:

| File | Role |
|---|---|
| `app/src/debug/java/com/tamerslab/ar/ArDiagnosticsActivity.kt` | ARCore smoke test/probe: camera feed + transparent Filament sprite overlay + tracking/plane/hit-test HUD. Debug source set only. |
| `app/src/debug/java/com/tamerslab/ar/ArStagePreviewActivity.kt` | Debug host for the real `ArBattleStage` in the SCANNING phase (placement overlay, camera feed, localized strings, simulated confirm). Debug source set only. |
| `app/src/debug/AndroidManifest.xml` | Registers both debug activities in debug builds only. |

Tests (`app/src/test/java/com/tamerslab/ar/`): `ArMathTest` (11), `ArBattleScaleTest` (20),
`ArBattleCameraTest` (11) — 42 JVM cases, no Android/ARCore runtime required.

Modified existing files:

| File | Change |
|---|---|
| `battle/ui/offline/OfflineBattleScene.kt` | `arPresentation` hook: translucent/transparent backdrop, AR camera from ARCore pose, AR touch (fighter taps only), arena occluders hidden, cinematic orbit/focus/zoom skipped in AR; in AR it hosts a detached `ModelViewer` on `ArTransparentSwapChainInstaller` and destroys the viewer exactly once (`destroyViewerOnce`) when the AR stage detaches. |
| `battle/ui/offline/OfflineTrainingBattleScreen.kt` | `WorldRadarBattleContent(arStage = …)`: AR stage composed once at a stable slot inside the square viewport; shared `WorldRadarBattleHud` extracted so AR and normal battles share overlays/test tags; `WorldRadarArStageHooks`. |
| `world/ui/WorldScreen.kt` (`RadarScreen`) | "AR battles" preference, `beginRadarBattle()`, AR stage/session state, `confirmArPlacement()`, `cancelArStage()`, full-screen chrome, AR stage wiring, renderer ownership. |
| `world/ui/RadarPlaceScreen.kt` | Hoists/holds the AR preference like the other radar view preferences. |
| `res/values{,-pt-rBR,-ja}/strings.xml` | 30 `ar_battle_*` strings each (parity-checked, all referenced). |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | `com.google.ar:core:1.57.0` (`libs.arcore`). |
| `app/src/main/AndroidManifest.xml` | `CAMERA` permission, optional `android.hardware.camera.ar`, `<meta-data android:name="com.google.ar.core" android:value="optional"/>`. |

## Coordinates, scale and camera conventions

**ARCore / world space.** Right-handed, `+Y` up, gravity-aligned; the camera looks down its
own `-Z` and its pose is a rigid transform in metres. ARCore `Pose`/`Frame` objects are
converted to plain `ArPose`/`ArCameraFrame` data at the session boundary and never leak
into scene code.

**Placement frame (`ArPlacement.anchor`).** Origin is the tap hit point on the detected
surface; `+Y` follows the surface normal (up for horizontal planes); `+Z` is yawed toward
the device at placement time, so "forward" in the arena points at the user who placed it.
Positive `ArPose.yaw` rotates local `+Z` toward world `+X`, matching the arena's
`cameraYaw = atan2(eyeX - targetX, eyeZ - targetZ)` convention.

**Scale.** `metersPerSceneUnit` (m/unit) is the only bridge between metres and the arena's
scene units. It is derived from the chosen plan and the arena width in scene units
(`metersPerSceneUnit = targetDiameterMetres / arenaWidthUnits`), then clamped to
0.02–1.5. Distances that cross the boundary are always explicit (`battleDiameterMeters`,
`sceneUnitsForBattleDiameter`).

**Scene camera.** `arSceneCamera(frame, placement)` computes
`relative = inverse(anchor) * cameraPose`, keeps the rotation verbatim (both Filament and
ARCore cameras look down `-Z`, `+Y` up) and divides the translation by
`metersPerSceneUnit`. It also returns the vertical FOV recovered from the ARCore
projection (`2·atan(1/P[5])`), the square stage aspect, and scene-space clip planes
derived from 5 cm near / 120 m far in the real world. Filament receives
`camera.setModelMatrix(...)` plus `setProjection(fovY, stageAspect, near, far, VERTICAL)`.
The stage aspect is square because the AR viewport is square **and** ARCore's camera
image is centre-cropped to that same square display geometry, so the Filament projection
and the camera background agree.

**Camera-derived yaw/pitch.** `arSceneCameraYaw`/`arSceneCameraPitch` read the camera's
backward axis (`+Z` column) and reproduce the arena camera conventions used by the
cinematic camera (`cameraYaw = atan2(eye.x - target.x, eye.z - target.z)`, pitch positive
when the eye is above the target). They feed the existing fighter/effect billboard maths so
sprites face the physical device.

## Flow state machine

```
RadarScreen (live radar, "AR battles" toggle saved)
 │
 ├─ toggle off ──────────────────────────────► startRadarBattle(spawn)  [normal battle]
 │
 └─ toggle on, user taps "Battle" on an encounter
      └─ beginRadarBattle(spawn)
           ├─ ARCore UNSUPPORTED (sync check) ─► startRadarBattle(spawn) [normal battle]
           └─ arStageVisible = true
                └─ ArBattleStage.SCANNING   (one ARCore session; camera feed + placement)
                     ├─ permission denied ──► card → "Fight normally" → cancelArStage()
                     ├─ ARCore missing ─────► card → install → retry on ON_RESUME
                     ├─ ARCore unsupported ─► card → "Fight normally" → cancelArStage()
                     ├─ Back ───────────────► cancelArStage()          [no fallback battle]
                     └─ plane tapped → preview + plan/pinch/rotate → "Place battle"
                          └─ confirmArPlacement(placement)
                               └─ startRadarBattle(arSpawn)  (stage stays mounted)
                                    └─ ArBattleStage.BATTLE   (same session + anchor)
                                         ├─ tracking lost/paused → banner, hold last pose
                                         ├─ result → leaveRadarBattle() → tearDownArStage()
                                         └─ exit/back → leaveRadarBattle() → tearDownArStage()
```

`tearDownArStage()` clears stage visibility, placement, spawn and last availability, so
the next encounter starts a fresh placement. `cancelArStage()` only starts the normal
fallback battle when the last availability was `UNSUPPORTED`/`SUPPORTED_NOT_INSTALLED` (the
fallback buttons set that state explicitly); a plain "Back" from placement just returns to
the radar.

## Lifecycle and teardown

- **Stage mount.** The stage is composed inside `WorldRadarBattleContent` at a stable slot
  in the square viewport, before the battle session exists. The same composition survives
  SCANNING → BATTLE, so the ARCore session, world frame and anchor are never recreated for
  the battle.
- **Session.** `startAr()` (after `CAMERA` is granted, with an async availability check)
  → `Session.create` → configure (`HORIZONTAL_AND_VERTICAL` planes, ambient light
  estimation, `LATEST_CAMERA_IMAGE`, auto focus) → `resume()`. `Session.update()` runs on
  the GL thread once per `onDrawFrame`; a change of surface size/rotation re-applies
  `setDisplayGeometry` before the update. If ARCore is missing, the install card calls the
  ARCore install flow (`requestInstall`) and the host retries on `ON_RESUME`.
- **Foreground/background.** A lifecycle observer in the stage pauses the session and the
  GL thread on `ON_PAUSE` and resumes them on `ON_RESUME`; on resume `startAr()` is called
  too, which re-checks availability (covers "user installed ARCore from the Play Store while
  the card was visible") and recovers from a lost camera where possible.
- **Teardown.** When the stage leaves composition, `releaseAr()` queues a GL cleanup, stops
  the GL thread, pauses and closes the ARCore session, drops the last snapshot and listener.
  The stage's lifecycle effect only releases the view when it is no longer attached
  (`!view.isAttachedToWindow`) and otherwise lets `onDetachedFromWindow` do the real release;
  `releaseAr()`/`pauseAr()`/`resumeAr()` are all idempotent. On the Filament side the AR
  presentation destroys its detached viewer exactly once (`destroyViewerOnce`), and the
  engine/scene is released by the normal `OfflineBattleSceneView.releaseScene()` path used by
  every battle.
- **Process death / activity recreation.** AR stage state (`arStageVisible`, placement,
  spawn) is deliberately not persisted: a restored battle session returns to the live
  radar, and the next encounter re-scans. No AR state is written to Room.

## Fallback matrix

| Condition | Detection | User-visible behaviour | Battle result |
|---|---|---|---|
| Device cannot run ARCore | `ArCoreApk.checkAvailability` (sync on entry, async in stage) | "AR unavailable" card in the stage | "Fight normally" → normal Coliseum battle |
| ARCore supported but not installed/outdated | availability `SUPPORTED_NOT_INSTALLED` | "ARCore needed" card with **Install** | Install → Play Store → retry on resume; "Fight normally" → normal battle |
| Camera permission denied | `checkSelfPermission(CAMERA)` / permission launcher | "Camera access needed" card (rationale, then denied copy) | "Allow camera" asks again; "Fight normally" → normal battle |
| Camera busy/unavailable when resuming | `CameraNotAvailableException` on `Session.resume` | session does not resume; tracking banner stays at the last state; user can back out | Back → `cancelArStage()` (no automatic fallback) |
| Tracking paused/lost after placement | ARCore `TrackingState` polled ~2 Hz | banner ("move the phone slowly" / "point back at the surface"); camera holds last pose | battle continues, no automatic fallback |
| Placement tap misses a plane | hit test returns no tracked horizontal plane | "No surface there" feedback for ~2 s | stays in scanning |
| No horizontal plane detected yet | plane list empty while tracking | "No surface detected yet" hint after 2 s | stays in scanning |
| Non-AR battle (toggle off, automatic handoff) | flag checks in `RadarScreen`/`beginRadarBattle` | unchanged radar/Coliseum flow | normal battle |

## Verification

### Unit tests (JVM, no device)

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --max-workers=1 --console=plain
```

**Result (2026-11-01, final frozen tree, serialized after all writers stopped):
BUILD SUCCESSFUL in 1m 59s.** `app/build/test-results/testIntegrityCheckUnitTest/*.xml`:
**159 suites, 943 tests, 0 failures, 0 errors, 3 skipped.** The three AR suites all ran and
passed:

| Suite | Tests | Result |
|---|---|---|
| `com.tamerslab.ar.ArMathTest` | 11 | all pass |
| `com.tamerslab.ar.ArBattleScaleTest` | 20 | all pass |
| `com.tamerslab.ar.ArBattleCameraTest` | 11 | all pass |
| **AR total** | **42** | **42/42** |

The same counts were reproduced in two earlier runs on the pre-freeze tree. The AR tests can
also be run alone with `--tests "com.tamerslab.ar.*"`. Earlier concurrent teammate builds
failed with `OutOfMemoryError: GC overhead limit exceeded` (Gradle daemons share 2 GiB per
JVM); those were environment failures, not test failures, and every recorded run was
executed solo. The AR-specific cases are:

- `ArMathTest` — identity/pose inverse, compose order, yaw convention, FOV recovery and
  fallback, projection/culling, plane-local→world, and the scene clip-plane scale.
- `ArBattleScaleTest` — plan recommendation boundaries, AUTO/fixed diameters and clamps,
  metres-per-scene-unit round-trip and clamps, placement frame (hit point, `+Y` normal,
  `+Z` at the camera, camera directly above), yaw rotation, plane size mapping, invalid
  arena width rejection.
- `ArBattleCameraTest` — anchor-relative camera placement, translation scaling vs rotation
  preservation, yaw/pitch conventions (including pitch sign), FOV/aspect propagation, clip
  planes, degenerate viewports.

Test-tag helpers used by the stage/overlay (for future UI tests): `ar-battle-stage`,
`ar-permission-card`, `ar-permission-grant`, `ar-unavailable-card`, `ar-install-card`,
`ar-install-action`, `ar-fight-normally`, `ar-starting-camera`, `ar-loading-scene`,
`ar-tracking-banner`, `ar-badge`, `ar-placement-overlay`, `ar-confirm-placement`,
`ar-cancel-placement`, `ar-scale-plan-{auto,tabletop,room,open-space}`.

### Build / static checks

```powershell
.\gradlew.bat :app:compileDebugKotlin --max-workers=1 --console=plain
.\gradlew.bat :app:compileIntegrityCheckAndroidTestKotlin --max-workers=1 --console=plain
.\gradlew.bat :app:lintDebug --console=plain                      # optional, long
```

- `:app:compileDebugKotlin` / debug source set — **BUILD SUCCESSFUL** for the frozen tree in
  the final device build (`.plan/ar_session_install8.log`: `BUILD SUCCESSFUL in 2m 37s`,
  `Installed on 1 device`); this compiles `app/src/main` plus the debug-only diagnostics and
  stage-preview activities.
- Final serialized run (`.plan/ar_verify_final_tree.log`):
  `:app:testIntegrityCheckUnitTest :app:compileIntegrityCheckAndroidTestKotlin --max-workers=1`
  → **BUILD SUCCESSFUL in 1m 59s**, unit results above, instrumentation compile passed.
- **`compileDebugAndroidTestKotlin` does not exist in this project.** `app/build.gradle.kts`
  sets `testBuildType = "integrityCheck"`, so the instrumentation source set is compiled by
  `:app:compileIntegrityCheckAndroidTestKotlin` (device runs use
  `:app:connectedIntegrityCheckAndroidTest`). It passed in the final run.
- `:app:lintDebug` was not run (long, optional).
- Debug source set (`ArDiagnosticsActivity`, `ArStagePreviewActivity`, debug manifest) is
  not part of the unit-test variant; it is compiled by debug builds such as
  `:app:installDebug`/`:app:assembleDebug` (covered by the `.plan/ar_session_install8.log`
  device build above).

### Verified tree

The final run started 2026-11-01 17:50:12 on the frozen working tree, after every writer
stopped. AR-relevant file revisions at that moment: `ArBattleStage.kt` 17:49:55 (availability
listener re-registration), `OfflineBattleScene.kt` 17:44:01 (transparent swap-chain host +
AR-only `destroyViewerOnce` teardown), `ArTransparentSwapChain.kt` 17:42:55,
`ArDiagnosticsActivity.kt` 17:43:42, `ArCameraBackgroundView.kt` 17:15:26,
`ArBackgroundRenderer.kt` 17:04:11, `ArMath.kt`/`ArTypes.kt` 15:50:56, tests 15:55–16:00.
No `app/src/main` or `app/src/test` file changed after the run started (only the two docs were
written). `git status` outside this feature contains only the pre-existing uncommitted user
work (environment themes assets/tools, integrityCheck validation activity) and build
artifacts; no tracked file outside the AR ownership set was modified by the AR feature, and
`CHANGELOG.md`/`AR_BATTLE_IMPLEMENTATION.md` are the only non-source additions.

### Fixes found during verification

Device/compositing checks on the Galaxy A34 found real defects that are fixed in the final
tree (recorded here so the root cause is not lost):

- **Filament swap chain was opaque, so the battle TextureView painted solid black over the
  camera.** Root cause: Filament 1.76.1's `ModelViewer` always creates its own swap chain with
  `Engine.createSwapChain(surface)` (opaque `CONFIG_DEFAULT`); `UiHelper.setOpaque(false)` is
  ignored because `ModelViewer$SurfaceCallback` bypasses it, and an opaque swap chain gives
  the `TextureView` no alpha channel. A second EGL window surface cannot be opened on a
  `SurfaceTexture` the viewer already attached (`EGL_BAD_SURFACE`/native crash). Fix:
  `ArTransparentSwapChainInstaller` (`ar/ArTransparentSwapChain.kt`) uses the **detached**
  `ModelViewer(engine, uiHelper)` constructor, owns the `SurfaceTextureListener`, creates one
  `SwapChainFlags.CONFIG_TRANSPARENT` swap chain on the view's `SurfaceTexture`, reflects it
  into `ModelViewer`'s private `swapChain` field, mirrors the ready flag into
  `UiHelper.mHasSwapChain` (reflection, so `ModelViewer.render()` does not early-return),
  keeps the viewport in sync, and flushes/destroys the chain on teardown. `ensure()` gates
  each `viewer.render()` call while AR is active; the debug diagnostics overlay uses the same
  installer. Verified on device (see below): transparent Filament content composites over the
  live camera feed, no black rect.
- **`ArBattleStage` released its camera view right after creation.** The
  `DisposableEffect(lifecycleOwner, cameraView)` re-keys when `cameraView` flips
  `null → instance`; its `onDispose` called `releaseAr()`, closing the just-created ARCore
  session. Fix: `onDispose` only clears the availability listener and releases the view when
  it is no longer attached (`!view.isAttachedToWindow`); the real teardown happens in
  `onDetachedFromWindow` when the stage leaves. `releaseAr()` stays idempotent.
- **Renderer ownership gate.** The AR battle content is now passed to `ArBattleStage` only
  while the battle renderer owns the slot (`owner == BATTLE && requested == BATTLE &&
  !releasing`), matching the normal battlefield's gate and keeping the one-Filament-engine
  rule explicit on the AR path.
- **GL context loss.** `onSurfaceCreated` now releases the cached camera-background
  program/OES texture and recreates + re-attaches them, instead of reusing ids from the
  destroyed EGL context (which left a black feed).
- **GLSL shader compile failure on device.** The `#version` directive must be the first
  shader token; raw indented Kotlin strings started with a newline and failed on device, so
  both shader sources are `.trimIndent()`ed.
- **Stale stage lifecycle flags.** The stage lifecycle observer now reads
  `permissionGranted`/`arStarted` through `rememberUpdatedState`.
- **Debug preview availability stuck at `UNKNOWN`.** The stage now re-registers
  `setAvailabilityListener` whenever its lifecycle effect runs (the previous effect dispose
  cleared it), so the debug preview/session status reflects the live ARCore availability.
- **Availability retry.** The camera background exposes an availability re-check path used
  by the stage/host after returning from the ARCore install flow.

### Device verification (owned by the Lead; acceptance evidence recorded here)

The Galaxy A34 (`SM-A346M`, ARCore installed) pass is the acceptance run. Confirmed on device
after `:app:installDebug` (`.plan/ar_session_install8.log`: BUILD SUCCESSFUL in 2m 37s,
"Installed on 1 device"):

- **Transparent Filament content over the live camera feed works.** A rotating magenta
  `ModelViewer` sprite composited over a live ARCore camera image with no black
  `TextureView` rectangle (`.plan/ar_diag_40_transparent_sprite.png`; logs show
  `ArSession created/configured/resumed`, `camera texture attached`,
  `ArBackgroundRenderer ready`, `Filament transparency overlay ready`). Camera-only control:
  `.plan/ar_diag_41_camera_only.png`. A 50 %-alpha Compose box over the live camera proves
  Compose-over-SurfaceView compositing: `.plan/ar_diag_42_red_over_live.png`.
- **The real `ArBattleStage` SCANNING UI renders over a live camera** (placement overlay,
  permission/availability cards, localized strings) and the tap feedback path works:
  `.plan/ar_stage_preview_30_final.png`, `.plan/ar_stage_preview_31_tap.png`.
- No app FATAL/crash in the device logs (`.plan/ar_session_logcat17.txt`,
  `.plan/ar_session_logcat18.txt`); only the Samsung `screencap` helper process aborts while
  capturing SurfaceView layers, and the captured images are valid.

Not exercised on device (honest gap): actual **horizontal-plane tap-to-place and an anchored
battle**. ARCore plane detection was transient on the stationary low-texture surface used for
the captures and tracking reported `PAUSED` ("Point at a textured surface"); the hit-test
null/feedback paths are verified, but the full scan → place → fight path still needs a
hands-on pass. See [How to verify on device](#how-to-verify-on-device).

### ARCore tracking troubleshooting (Samsung / debuggable builds)

**Symptom:** the AR camera feed is live but ARCore reports `tracking: PAUSED` almost all the
time, `horizontal planes: 0` forever, and no tap ever produces a hit — i.e. "no surface can
be detected", even on a well-lit textured table/floor.

**Known cause (external):** on Samsung/One UI devices, ARCore's tracking and plane finding
degrade or stop working in **debuggable** builds. The same APK with
`android:debuggable="false"` tracks normally; official Play Store AR apps (non-debuggable)
are unaffected. This is a platform/ARCore issue, not an app bug — see
[google-ar/arcore-android-sdk#1750](https://github.com/google-ar/arcore-android-sdk/issues/1750)
("setting `isDebuggable = false` normalizes the tracking state") and
[stackoverflow.com/q/79897217](https://stackoverflow.com/questions/79897217). Verified on the
Galaxy A34 (SM-A346M, Android 15, Play Services for AR 1.57.262640093): the debugging build
sat at `PAUSED` indefinitely, and the non-debuggable build immediately reported `TRACKING`.

**How this repo handles it:** `app/build.gradle.kts` exposes an opt-in flag that builds the
debug variant without the debuggable manifest flag, keeping the debug source set (diagnostics
activities, logs) while fixing ARCore:

```powershell
.\gradlew.bat :app:installDebug -PnonDebuggable --console=plain
```

`adb shell run-as <applicationId>` then fails ("package not debuggable") — that is expected.
Release builds are non-debuggable by definition and are not affected by this bug.

**Remaining environmental requirements** (ARCore needs all of them, independently of the
above): good lighting, a textured surface (bare white tables/glass/very dark floors can
never produce planes), and slow phone movement — plane detection is motion/parallax based,
so sweeping the phone left/right across the surface for a few seconds is required. The
scanner now states this explicitly and shows a live `tracking/planes` readout in debug builds.

**Automatic placement when planes are slow to appear** — the scanner needs no tap at all and
these are layered so a battle still starts in enclosed or low-texture spaces:

1. **Depth hits** — ARCore `DepthMode.AUTOMATIC` is enabled when supported; the centre probe
   can hit a **depth point** on the physical surface before any plane is fitted.
2. **Instant placement** — `Config.InstantPlacementMode.LOCAL_Y_UP` gives a hit in front of
   the camera and refines it while tracking improves (surface-less fallback, delayed).
3. **Five-point probe** — every 140 ms the scanner probes the centre plus a small cross so a
   table edge or small surface under the reticle still produces a hit.
4. **Automatic lock** — the candidate poses must be stable (2 samples on a plane, 3 on
   depth, 4 on instant placement with minimum scan times) before the battle starts, so
   pointing around does not launch a badly placed fight.
5. **Stuck-session recovery** — a session whose tracking is `STOPPED` for seven seconds is
   restarted automatically; during scanning a long `PAUSED` streak (12 s) is also restarted,
   up to three times. Scanning has no anchor yet, so restarting is cheap, and a fresh session
   recovers Samsung/One UI sessions that stay stuck in `PAUSED`.
6. **Tracking-paused survival** — the scanner no longer waits for `TRACKING` to finish: after
   the six-second fallback the battle is placed in front of the camera even if ARCore is
   still `PAUSED`, using the last known camera pose. The fight then holds that pose and
   re-anchors if tracking recovers, instead of leaving the player stuck in a featureless or
   dim room. When tracking is paused the scanner also shows the real cause (too dark / not
   enough detail / moving too fast), localized in EN/pt-BR/ja, so the player can fix the
   environment instead of fighting a generic banner.

### ARCore "tracking is PAUSED most of the time"

If the scanner banner stays on *tracking paused* (or the diagnostics activity shows
`tracking: PAUSED`, `horizontal planes: 0`, failure reason empty/`INSUFFICIENT_FEATURES`),
the environment is the limiting factor, not the app. ARCore needs three things at once:
good light, visible texture/features, and slow device motion (plane detection is based on
parallax). A phone pointed at a plain white/grey blanket, a blank wall or a glass table in a
dim room never reaches `TRACKING` — the camera image is blurred and featureless. Move to a
better-lit area, point at a table edge/floor with pattern (wood grain, tiles, fabric weave),
and sweep the phone slowly left and right; tracking settles within a second or two.

The app-side mitigations above mean a dim/featureless room degrades gracefully (reason shown,
session restarted, battle started from the last pose after six seconds) rather than blocking
the feature. The battle scene now also applies ARCore's last camera pose **even while tracking
is `PAUSED`** (and uses a sensible default framing before the first frame), so a fight started
from a paused scan is visible instead of rendering from ModelViewer's default camera. Both the
camera-background GL view and the AR battle scene are capped at ~30 fps and AR post-processing
is disabled, leaving GPU/CPU headroom for ARCore's tracking pipeline. If tracking is `PAUSED`
the arena holds its last pose until ARCore re-locks and may drift during that window — an
ARCore limitation, not a renderer bug.

### Radar interactions are no longer invalidated by ordinary location updates

Every accepted GPS fix bumps the ecosystem session revision, which changes the published
`RadarCommandStamp`. The radar UI captured that stamp when it composed an action, so an
ordinary location update between rendering and tapping made the command fail as a stale
snapshot — visible to the player as the "Radar changed. Check the encounter and try again."
toast interrupting an interaction. Commands are now built from the **latest** published stamp
at submission time (`latestCommandStamp()`), operations with long gaps before submission build
their command at the point of execution (battle reservations after the preparation DB reads,
region refresh after the biome lookup), and the conversation screen uses
`executeCommandLatest()`, which retries once or twice when the only rejection is
`STALE_SNAPSHOT` (a rejected command was never applied, so retrying cannot duplicate effects
— the existing one-shot stale-ticket guard is preserved for callers that build their own
stamp). The coordinator still revalidates the lease, snapshot status,
claims, range, freshness and event state inside the command transaction, so this does not
weaken the stale-state guards — it only stops plain GPS updates from cancelling interactions.

## Known limitations

- **Scanning viewport vs battle viewport.** The AR scanner now takes the full height available
  in the Radar page (the scroll is lifted while scanning) so the camera shows more of the
  physical surface, which makes pointing at a table/floor easier. Once the battle starts the
  viewport returns to the established 1:1 square so the command deck keeps its space; ARCore
  centre-crops the camera image to that square, so the anchored fight is a square AR window
  rather than full-screen AR.
- **ARCore device requirement.** AR battles need an ARCore-supported device with Google
  Play Services for AR. Everything degrades to the normal battle; nothing is lost, but the
  AR toggle has no effect on unsupported hardware.
- **Samsung/debuggable-build tracking bug.** Test AR on a `-PnonDebuggable` debug build (see
  the troubleshooting section above); a normal debuggable build can make plane detection look
  completely broken on Samsung/One UI devices.
- **Placement is per encounter.** The anchor lives in ARCore's world frame and in Compose
  state (`remember`), not in saved state: a process death or activity recreation drops the
  placement, and a restored battle returns to the live radar instead of re-anchoring.
- **Hands-free scanner verified on device; a full locked battle still needs a hands-on pass.**
  The device run confirmed the automatic flow end-to-end on the scanner: the camera feed
  runs, the centre probe/plane outlines render, and a placement was produced and confirmed
  without any tap (`onPlacementConfirmed` in the debug stage host). The last capture room
  had no usable horizontal surface under the reticle, so the automatic 6-second fallback
  triggered; a real table/floor lock followed by the anchored fight should be re-checked
  with a physically moving phone.
- **Finisher / cinematic camera disabled in AR.** Finisher actors, effects and Blast timing
  keep playing, but the cinematic/orbit camera moves and the touch orbit/zoom gestures are
  skipped — the device pose is the only camera. Fighter taps still select targets.
- **AR entry is the manual encounter card.** Automatic handoffs (wild attack, incoming
  challenge, defend/join battle) keep using the normal renderer even when the toggle is on,
  because they start without a deliberate user action to place an arena.
- **No re-place action while fighting.** The AR badge is display-only; changing the
  placement means leaving the battle and starting the encounter again. The plan mentioned a
  "Re-place" affordance, which is not implemented.
- **Tracking recovery is best-effort.** During scanning the app restarts a stuck ARCore
  session (up to three times) and starts the battle after six seconds even if tracking is
  still `PAUSED`, using the last known pose. During the battle a lost track shows a banner
  and the camera/anchors hold their last good pose; there is no automatic fallback from a
  running AR battle to the normal Coliseum renderer, so a permanently lost track still needs
  Back/exit. If the device is in a dim or featureless environment ARCore may stay `PAUSED`
  and the anchored fight may drift when tracking recovers — an ARCore/environment
  limitation, not a renderer bug.
- **No light estimation applied.** The session requests ambient light estimation, but the
  value is not yet fed into the Filament lighting, so AR fighters keep the arena lighting
  in a dim room.
- **Radar backdrop change outside AR (pre-existing working-tree change).** The radar
  arena's enclosing sky-dome (`Sphere001`) and its drift/energy animation are absent from
  the shared scene in the current working tree, so non-AR radar battles also render without
  the dome (black skybox backdrop). This change pre-dates/parallels the AR work in the
  uncommitted tree (`OfflineBattleScene.kt`, preserved by the AR scene changes); it is
  **not part of this feature's diff** and is listed here only so a reviewer does not
  attribute it to AR.
- **Ownership gate for the AR battle scene — fixed in the final tree.** `ArBattleStage` now
  receives `ArBattleContent` only while the battle renderer owns the slot
  (`rendererState.owner == BATTLE && requested == BATTLE && !releasing`), matching the
  normal battlefield's `rendererAllowed` gate, so a lingering FIRST_PERSON release can never
  overlap a second Filament engine. While the gate is not granted the stage stays on the
  "Preparing the AR battle..." hint.
- **GL context-loss recreation — fixed in the final tree.** The camera background's
  `onSurfaceCreated` now releases the cached program/OES texture before recreating them and
  re-attaches the camera texture, so an EGL context loss recovers instead of showing a black
  feed. The final tree also carries the on-device shader fix: the `#version` directive must
  be the first shader token, so the GLSL sources are `.trimIndent()`ed (the raw indented
  strings failed to compile on the Galaxy A34).
- **v1 lifecycle/a11y polish.** The AR stage's lifecycle observer now reads
  `permissionGranted`/`arStarted` through `rememberUpdatedState` and re-registers its
  availability listener on effect runs (both fixed in the final tree). Remaining accepted v1
  items: `releaseAr()` may block the main thread up to 500 ms while it waits for GL cleanup,
  and the camera `GLSurfaceView`/placement `Canvas` (reticle, arena preview) carry no content
  description/semantics while status hints are not live regions. These are tracked for a
  follow-up polish pass.
- **Diagnostics screens.** `ArDiagnosticsActivity` and `ArStagePreviewActivity` are
  debug-only; the diagnostics screen creates its own Filament engine, so launch it on its
  own (not over a running AR battle) to avoid two Filament engines in one process.

## How to verify on device

Prerequisites: authorized ADB device with ARCore installed (`adb devices`), Android SDK at
`%LOCALAPPDATA%\Android\Sdk` (ADB is not on `PATH` on this machine).

```powershell
# install the debug build (applicationId com.github.nacabaro.vbhelper)
# -PnonDebuggable avoids the Samsung/ARCore debuggable-build tracking bug (see above)
.\gradlew.bat :app:installDebug -PnonDebuggable --console=plain

# launch the app
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start `
    -n com.github.nacabaro.vbhelper/com.tamerslab.MainActivity
```

Then:

1. Open **World → Radar** (the Radar place).
2. Scroll to the view-preference controls and enable **AR battles**.
3. Move until a wild Digimon / encounter card is available and tap its **Battle** button.
4. The square battle viewport becomes the camera feed. Point the phone at a table or the
   floor and sweep it slowly left/right for a few seconds — no tapping or plan selection.
5. When a surface locks, the arena preview appears and the battle starts automatically
   ("Surface found - hold still..." → "Starting the battle...").
6. Confirm the fighters appear anchored on the surface, **only the two Digimon and the
   battle-space ring** render (no arena floor/grid/dome/background), the arena stays level
   (not sideways/tilted), walking around keeps it in place, the AR badge is visible, and
   HP bars/command deck/Blast/finisher all still work.
7. Press Back / Cancel: return to the radar; re-enter to confirm a fresh automatic scan.
8. Fallbacks: deny the camera permission (or disable ARCore support) and confirm the
   "Fight normally" path starts the normal Coliseum battle.

Debug diagnostics/preview activities (debug builds only):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start `
    -n com.github.nacabaro.vbhelper/com.tamerslab.ar.ArDiagnosticsActivity
# camera feed + plane outlines + tap hit marker + tracking HUD + translucent Filament sprite
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" exec-out screencap -p > ar_diagnostics.png

& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start `
    -n com.github.nacabaro.vbhelper/com.tamerslab.ar.ArStagePreviewActivity
# the real ArBattleStage in SCANNING (placement UI + permission/install cards), with a
# "simulate confirm" row and a callback log overlay; no radar battle needed
```

Screenshot/log capture for a device run:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" exec-out screencap -p > ar_battle.png
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -d -s ArSession ArCameraBackgroundView ArBackgroundRenderer
```

## Testing gaps

- No instrumentation/Compose UI tests for the AR stage, placement overlay or permission/
  fallback cards; the test tags above are ready for them.
- No ARCore-runtime tests (session lifecycle, hit tests, tracking banner) — covered only by
  the debug diagnostics activity and manual device pass.
- No test for the radar integration branch (toggle → `beginRadarBattle` → fallback) or the
  `WorldRadarBattleContent(arStage = …)` composition; existing Radar battle-flow tests
  cover the non-AR path only.
- The `ArAvailability` mapping and install/request flow are Android-API-dependent and
  untested on the JVM.
