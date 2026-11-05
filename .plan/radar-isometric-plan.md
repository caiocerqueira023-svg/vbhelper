# Radar place — redesign + isometric 3D radar

Date: 2026-11-03. Owner decisions taken this session:

- **Ambient ground:** reuse the existing Radar grid arena (`Arena/Radar/radar_grid.glb`) — already themed,
  already loaded by the radar first-person/battle path.
- **Legacy 2D radar / first-person radar:** kept in the codebase; reachable only from the Radar options
  sheet while the master Debug switch is on. They are no longer the default view.
- **Verification:** owner will connect the Samsung phone; the build is accepted with real screenshots.

## 1. Why the current Radar reads as a mess

Evidence: owner screenshot + `world/ui/WorldScreen.kt` (`RadarScreen`, 197–2035) and
`battle/ui/offline/OfflineTrainingBattleScreen.kt` (`WorldRadarBattleContent`, 921–1409).

1. **A page frame around a world.** The radar is rendered inside `Scaffold` → `TopBanner` card →
   `Column(verticalScroll)` with 16dp padding (`WorldScreen.kt:963-1005`). Every other Home/Storage
   surface is edge-to-edge; the radar is a form page that happens to contain a radar.
2. **The world is a small bordered box.** `WorldRadarBattleContent` gives the viewport
   `aspectRatio(1f)` + 2dp `SurfaceStroke` border + clip (`OfflineTrainingBattleScreen.kt:1188-1202`).
   On a 1080×2340 phone that is ≈ 40% of the screen; the rest is chrome.
3. **Controls outweigh the thing they control.** Under the square: an "18 nearby Digimon" title, the
   `RadarSessionStatusPanel` panel (reserved 3 text lines + a 48dp action slot, so it is mostly empty
   air saying "Radar active"), two full-width view buttons, an AR battles switch row, a labels switch
   row, a scene hint and zoom chips (`WorldScreen.kt:1415-1500`).
4. **Two compass layers fighting.** The grid rotates with the heading, then 200/400 m rings with
   "200 m" labels, then four cardinal letters, then four arrow triangles repeat the same information
   (`WorldScreen.kt:1249-1313, 1406-1442`). In the screenshot the "200 m" labels sit on the E/W arrows.
5. **No collision system for overlays.** 48dp sprite `Image`s with absolute `offset {}`, activity lunge
   offsets, `RadarOverheadAnnotation` speech bubbles, a top-center biome label and the compass arrows
   all draw into the same box with independent offsets. The screenshot shows a speech bubble covering a
   third of the radar.
6. **Always-on status.** "Radar active" is rendered permanently; the only surface that should be quiet
   when everything is fine is the loudest panel on the page.
7. **No primary object and no hierarchy.** The player is a 28dp purple dot; wild Digimon are 48dp and
   larger than the player, with diamonds and distance rings carrying no legend. Nothing says
   "this is you, this is the world around you".
8. **A permanent configuration bar.** 2D / first-person / AR battles are three preferences rendered as
   permanent controls, for a screen whose job is to show the world.

## 2. Direction — inherit Home's shell, Storage's instrument vocabulary

Home is a full-bleed 3D diorama with detached HUD clusters floating over it (`DESIGN.md`,
"Farm home shell"); Storage is the instrument vocabulary: cut-corner chips, square technical marks,
cyan = live/selected, Oxanium labels, no card-inside-card. Radar adopts both:

- **Full-bleed 3D isometric radar world** — edge to edge under the system bars, no border, no shelf.
- **Floating chrome only:** top cluster (back key · "Radar · NW" label · options key · spawn-settings
  key), the compass ring drawn directly over the world, and a bottom signal band (nearby count,
  status/recovery when not ready, zoom). No full-width rows, no empty reserved panels.
- **One options sheet** replaces the three permanent toggles: labels, AR battles, spawn settings, and
  the Debug-only legacy views (Classic 2D / Classic first-person).
- **One primary object:** the player's active Digimon stands at the center of the radar on the plate;
  wild Digimon stand farther out at their bearing/distance; the compass ring, cardinal letters and
  range labels belong to the world, not to panels.
- **Interaction stays:** tap a Digimon → encounter sheet, long-press → species, speech bubbles from
  world interactions, battle handoff and AR battles unchanged.

## 3. The isometric view

- Renderer: new `RadarIsometricViewport` in `world/ui`, modeled on `RadarFirstPersonViewport`
  (Filament `Engine` + `ModelViewer` + `EnvironmentThemeController` + `SpriteExtrusionGlb`), advertised
  through `RadarRendererOwner.ISOMETRIC` so a single engine exists at a time (battle and legacy
  first-person keep the existing handshake).
- Ambient: `Arena/Radar/radar_grid.glb` (`radar_floor`, `world_grid`, `battle_boundary`) scaled so the
  plate covers the visible radar radius; theme-bound through the arena manifest like first-person.
- Digimon: extruded sprite GLBs (idle/walk poses, same pose-switching contract as first-person) placed
  by bearing/distance from `RadarWorldGeometry.relative`, distance mapped with the existing
  `radarPresentationDistance` legibility curve normalized to the current zoom.
- Player: the active partner's extruded GLB at the center, standing on a marked ring; falls back to a
  ring pin when the partner has no artwork.
- Camera: fixed isometric framing (≈58° elevation) looking at the plate center; the world rotates with
  `radarHeading` so the cardinal ring reads exactly like the old 2D radar. Pinch zoom and the bottom
  band's zoom keys change `visibleRadiusMeters`; tap targets are projected by the renderer into
  `RadarProjection` (same contract as first-person).
- Compose overlay (crisp, theme-aware, above the GL surface): range rings with real metre labels,
  cardinal letters, edge vignette to blend the plate into the page, entity labels/speech bubbles with
  a collision policy (one bubble per entity, clamped inside the viewport, no annotation over the top
  and bottom chrome).

## 4. Files

New (owned by teammates):

- `world/ui/RadarIsometricGeometry.kt` — pure math (camera pose, metre→scene mapping, pose/scale,
  ring radii) + unit tests.
- `world/ui/RadarIsometricViewport.kt` — Filament viewport and scene.
- `world/ui/RadarChrome.kt` — top cluster, compass layer, bottom band, options sheet, entity marker /
  speech bubble. New string resources in `values`, `values-pt-rBR`, `values-ja`.

Changed (owned by Lead):

- `world/ui/WorldScreen.kt` — `RadarScreen` composition: full-bleed world, floating chrome, options
  sheet, legacy views behind Debug, renderer ownership for ISOMETRIC, active partner sprite frames.
- `world/ui/RadarPlaceScreen.kt` — persisted preferences (view mode, labels) handed to the screen.
- `battle/ui/offline/OfflineTrainingBattleScreen.kt` — `WorldRadarBattleContent` gains a full-bleed
  radar branch; battle and AR layouts stay byte-identical.
- `world/ui/RadarFirstPersonGeometry.kt` — `RadarRendererOwner.ISOMETRIC` (renderer teammate).
- `rendering/HybridSceneProfile.kt` — `HybridSceneKind.RADAR_ISO` profile (renderer teammate).
- `DESIGN.md` — Radar place section; `TESTING.md` — evidence and boundaries; `CHANGELOG.md` — entry.

## 5. Verification

1. `.\gradlew.bat :app:compileDebugKotlin` after each deliverable.
2. `.\gradlew.bat :app:testIntegrityCheckUnitTest` (geometry/compass tests) and the app unit suite.
3. `.\gradlew.bat :app:assembleDebug`, install on the connected SM-A346M, capture:
   default radar, options sheet, labels on, a tapped Digimon sheet, battle handoff, reduced motion,
   font scale 1.3, and dark theme (the app's palette is the dark default; flip the app palette where
   available).
4. Boundaries: no connected device means no visual acceptance — recorded in TESTING.md.
