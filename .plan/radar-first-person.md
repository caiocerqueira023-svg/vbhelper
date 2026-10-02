# Plan: First-Person Radar Mode (FP view inside Radar tab)

**Status:** plan only, not implemented.

## Goal

A new rendering mode of the existing radar mode: everything works the same
(same GPS, compass, spawns, encounter sheet, battle flow), but the square
viewport renders a **first-person 3D environment** instead of the 2D radar
canvas. The camera yaw follows the phone's compass heading, and spawned
individuals appear in the 3D world in front of the user at their real bearing
and (compressed) distance.

## Confirmed decisions

1. **Entry:** toggle inside the Radar tab (segmented "2D / FP" control), not a
   new hub tab.
2. **Environment:** reuse `app/src/main/assets/Arena/Radar/radar_grid.glb`
   (+ `arena.json`), no new assets.
3. **Camera:** compass heading only — yaw from the existing
   `rememberWorldCompass()` pipeline; fixed eye height, fixed slight-down
   pitch, no drag/pinch, no tilt sensors.

## Current architecture (relevant seams)

- `screens/worldScreen/WorldScreen.kt:143` — `RadarScreen` monolith: GPS,
  compass (`:169-175`), spawns flow (`:181`), 2D viewport lambda (`:478-773`),
  controls (`:797-843`), battle launch (`:354-396`).
- `screens/worldScreen/WorldCompass.kt` + `CompassHeading.kt` — sensor →
  smoothed heading (pure, unit-tested). Reuse unchanged.
- `screens/offlineBattle/OfflineTrainingBattleScreen.kt:742` —
  `WorldRadarBattleContent(..., radarViewport, radarControls, ...)`; cross-fades
  to the 3D battle scene when `battleActive`.
- 3D tech: Google Filament 1.76.1 via `ModelViewer` in a `TextureView`
  (no scene wrapper). Two existing, near-duplicate implementations:
  `screens/offlineBattle/OfflineBattleScene.kt` and
  `screens/digifarmScreen/Digifarm3dViewport.kt`.
- Shared: `rendering/HybridSceneProfile.kt`, `rendering/SceneMotion.kt`,
  `rendering/sprite3d/SpriteExtrusionGlb.kt`, `SpriteFacing.kt`.
- Spawn geometry (flat-earth, 111_320 m/deg) at `WorldScreen.kt:657-686`;
  interaction range 40 m; spawns 30 m–1 km; ~20 active entities.

## Design

### 1. Mode toggle (state + UI)

- `RadarScreen`: add `var fpViewEnabled by rememberSaveable { mutableStateOf(false) }`
  (WorldScreen.kt, near `:190` zoom state).
- Toggle control in the `radarControls` lambda (WorldScreen.kt:797-843):
  a `SingleChoiceSegmentedButtonRow` "Radar 2D | Primeira pessoa". Hidden while
  `battleActive` (battle already owns the viewport via `AnimatedContent`).
- The `radarViewport` lambda passed to `WorldRadarBattleContent`
  (OfflineTrainingBattleScreen.kt:747) becomes:
  `if (fpViewEnabled && !battleActive) RadarFirstPersonViewport(...) else { existing 2D canvas }`.
- **Critical:** the FP view must be removed from composition before
  `battleActive` flips true so its Filament `Engine` is released before
  `OfflineBattleScene` creates its own (app must never run two engines).

### 2. New file: `screens/worldScreen/RadarFirstPersonViewport.kt`

Composable that:
- hosts `AndroidView { RadarFirstPersonSceneView(context) }`,
- passes in `heading`, spawn list + sprite frames, location, biome, compass
  status, and callbacks `onSpawnTap(spawn)` / `onSpawnLongPress(spawn)`
  (reusing the same handlers the 2D canvas uses for
  `WorldEncounterActionSheet` / dex),
- draws the Compose HUD overlay on top (see §6).

### 3. New file: `screens/worldScreen/RadarFirstPersonSceneView.kt`

A `TextureView` subclass modeled on `Digifarm3dViewport.kt` (simplest of the
two existing views):

- JNI init (`Filament.init()` + `gltfio-jni`), `Engine.create()`,
  `ModelViewer`, skybox, `applyHybridSceneProfile(...)`.
- Environment: load `Arena/Radar/radar_grid.glb` on a background thread
  (pattern: `OfflineBattleScene.loadArena` :228-269), `clearRootTransform()`.
  **Hide the dome/`Sphere001` and far voxel fragments** (node filtering like
  `OfflineBattleScene` :496-586) so the user stands on the radar grid floor
  under the skybox, not inside the battle dome. Verify grid axis alignment
  (north = which axis) with a debug marker; rotate root once if needed.
- Camera: fixed eye `(0, EYE_Y, 0)`; `lookAt` eye + forward where forward is
  derived from heading using the same convention as the 2D radar rotation
  (rotate world by `-heading`); fixed pitch ≈ -0.10 rad; focal length ~30 mm
  (wide FOV), `near = 0.05`, `far ≈ 600`. No touch orbit — taps only
  (projection hit-test, digifarm pattern :614-636).
- Yaw smoothing: ease camera yaw toward compass heading with
  `approachSceneAxis()` (rendering/SceneMotion.kt) so sensor jitter doesn't
  shake the world; heading source = the existing `radarHeading` (already
  frozen during battle).
- Choreographer frame loop: update camera → update entity transforms/poses →
  render → projection tick for the HUD (digifarm :222-235).
- Lifecycle/teardown: pause on `ON_PAUSE`, full engine teardown ordering
  copied from `Digifarm3dViewport` (:1031-1052); release when detached.

### 4. World geometry (pure Kotlin, new `world/RadarFpGeometry.kt`)

Per spawn, recompute from lat/lon relative to the **live** location (player =
camera), same flat-earth math as WorldScreen.kt:657-686:

- `northM = (spawn.lat - loc.lat) * 111_320`, `eastM = ... * cos(lat)`;
- `bearingDeg = (degrees(atan2(eastM, northM)) + 360) % 360`;
- `distanceM = hypot(eastM, northM)`.

**Distance compression (key design choice):** real distances (30 m–1 km) don't
map to an 8-unit arena. Map distance radially with a compressive curve so
direction stays true but the world fits:

- `units = FP_MAX_UNITS * (distanceM / 1000)^0.5` (tunable; constants:
  `FP_MAX_UNITS ≈ 40`, interaction range 40 m ≈ 2.5 units, 350 m ≈ 24 units),
- world position `x = eastM_dir * units`, `z = northM_dir * units`
  (sign flipped to match Filament/arena axis after §3 alignment check),
- cull: behind camera, outside `FP_RENDER_RADIUS`, or beyond `1000 m`.

Also exported: FOV-relative screen ordering, and a `isVisible(bearingDiffDeg)`
helper for HUD edge arrows. Unit-tested (§9).

### 5. Entities in the FP world

- Reuse the sprite pipeline: build per-spawn GLB via `SpriteExtrusionGlb.build()`
  from the spawn's idle frames (`SpawnWithDetails.frameFor`, same source as
  WorldScreen.kt:213-227), exactly like `DigifarmScreen.kt:338-378`
  (`ResidentFrames`), with `SpriteExtrusionGlb`'s SHA-256 LRU cache preventing
  rebuilds.
- Load at most one new GL asset per frame (digifarm `loadNextResident` pattern).
- Placement each frame: translate to §4 position, `y = 0`, scale by stage
  multiplier (baby stages smaller, cf. `battleFighterScaleMultiplier`),
  alternate `idle/idle2` pose layers every ~950 ms (digifarm pattern).
  Distance is conveyed by perspective; if far entities become unreadable,
  apply a mild distance-based size boost (decide during implementation).
- Facing: always roughly toward the user via `cameraAssistedSpriteYaw(worldYaw,
  cameraYaw)` with `worldYaw =` camera yaw (they're facing you) — realistic
  "standing in front of you", same helper as battle/digifarm.
- Baked contact shadow from `SpriteExtrusionGlb` stays; Filament shadows remain
  disabled (project convention).

### 6. HUD overlay (Compose, over the TextureView)

- Top banner: current cardinal (`cardinalDirection`, WorldScreen.kt:1236) +
  compass status (`CompassReading.status` → existing strings), mirroring the
  2D banner.
- Per-entity labels at projected pixels: `NE · 120 m`
  (reuse `world_cardinal_directions_8`), with `INTERACTION_RANGE` spawns (≤40 m)
  highlighted (VitalCyan) to signal they're encounter-ready.
- Entities outside the FOV: small edge arrows at rim bearing
  `(bearing - heading - 90°)` — reuse the arrow math from WorldScreen.kt:733-770.
- Biome label + existing debug controls stay outside/above the viewport as today.

### 7. Interactions

- Tap on projected entity footprint → within 40 m: open
  `WorldEncounterActionSheet` (chat/fight, unchanged, → `startRadarBattle()`);
  beyond: existing "too far" toast. Long-press → dex. Same callbacks as 2D.
- Battle hand-off unchanged: `WorldRadarBattleContent` cross-fades to
  `OfflineBattleScene`; FP view disposed first (§1).

### 8. Strings & i18n

Add to `values/strings.xml`, `values-pt-rBR/strings.xml`,
`values/ja/strings.xml`: toggle labels ("Radar 2D" / "Primeira pessoa" / FP
equivalents), FP distance-label format, FP content description. Reuse
`world_cardinal_directions_8`, `ui_world_compass_waiting`, etc.

## Files touched

**New**
- `screens/worldScreen/RadarFirstPersonViewport.kt` (composable + HUD)
- `screens/worldScreen/RadarFirstPersonSceneView.kt` (Filament view)
- `world/RadarFpGeometry.kt` (pure geometry)
- `app/src/test/.../world/RadarFpGeometryTest.kt`

**Modified**
- `screens/worldScreen/WorldScreen.kt` — toggle state, controls, viewport
  branching, callbacks into FP view
- `rendering/HybridSceneProfile.kt` — add `HybridSceneKind.RADAR_FP`
  (bloom/post settings ≈ BATTLE)
- `res/values{,-pt-rBR,-ja}/strings.xml`
- possibly `screens/offlineBattle/OfflineTrainingBattleScreen.kt` only if the
  viewport/controls lambdas need extra params (prefer not)

## Implementation order

1. `RadarFpGeometry.kt` + unit tests (bearing/distance/compression/culling).
2. FP scene shell: environment load, node filtering, compass-driven camera,
   skybox, profile — verify grid alignment and look.
3. Entity pipeline: GLB build, one-load-per-frame, placement, animation, facing.
4. HUD overlay + tap/long-press interactions.
5. Toggle wiring in `WorldScreen`, engine-release-before-battle guard,
   strings/i18n, lifecycle/teardown polish.

## Verification

- `gradlew :app:testDebugUnitTest` (new `RadarFpGeometryTest` +
  keep green: `CompassHeadingTest`, `RadarBattleFlowTest`,
  `RadarArenaAssetTest`, `OfflineArenaGeometryTest`,
  `OfflineTrainingBattleScreenTest` tags `world-radar-battle-viewport`).
- `gradlew :app:compileDebugAndroidTestKotlin` / lint if configured.
- Manual: device with magnetometer — rotate phone, world and rim arrows track
  heading; spawns appear at correct bearing; ≤40 m spawns open encounter sheet;
  start battle from FP mode (engine hand-off, no crash); toggle back and forth;
  background/foreground the app; process-death restore (`rememberSaveable`).

## Risks / open points

- `radar_grid.glb` was built as a battle arena; from eye height inside it may
  read as a dome/grid cage — mitigation: hide dome + far fragments, rely on
  skybox. If it still looks wrong, fall back to a minimal procedural ground
  grid (decision already leans to reusing the asset).
- Axis alignment of the GLB vs. compass north needs a one-time verification.
- Distance compression is a feel decision — constants are tunable, unit test
  pins the curve shape.
- Two Filament engines must never coexist (§1 guard).
- Battery/sensor load unchanged (compass already runs in radar mode).
