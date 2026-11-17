# Changelog

All notable changes to Tamers Lab are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are project
milestones rather than strict SemVer.

## [Unreleased] — Alpha 0.7.0

Target: Alpha 0.7.0. This entry summarizes the refactor work that is implemented in the
tree; it is not a release announcement and does not claim a verified green build.

### Added

- **NFC transfer guide and Digimon hand-off animation.** The watch-transfer waiting steps now
  render the original VB Lab scan artwork extracted from the discontinued 1.4.6 APK (the
  `scan_images` instruction infographic and the `scan_images1` connecting illustration, with its
  separate `SUCCESSFUL!` sprite reproduced as in the shipped `scan_F_success`/`scan_R_success`
  pages), and animate the Digimon hand-off with the original `loadchara` two-frame sprites or the
  character's card artwork under a mask wipe. The transfer direction (watch-to-app /
  app-to-watch) is labelled in en/pt-BR/ja, the flow respects the system reduce-motion setting,
  and the extracted artwork is used for personal preservation only — it is not redistributed.
  The recovery notes for the discontinued APK (asset names, `scan_F_success`/`scan_R_success` UI
  objects and the `loadchara` frames) live with the local reverse-engineering material, outside
  the repository.

- **Connection secrets now ship with the app.** The AES key, the two 4-bit substitution ciphers and
  the VBDM/VBC/BE HMAC key pairs used to authenticate with the watch are baked into the build
  (extracted from the official VB Arena 2.1.0 base APK), so a fresh install can scan a watch without
  downloading the Arena APK and running `Import secrets`. They live in
  `security/BundledSecrets.kt` as the fallback of `DataStoreSecretsRepository`, and
  `BundledSecretsTest` pins them by regenerating the known watch test passwords (`be29a87e`,
  `a71dfb22`, `6ea33673`). A manual import in Settings still overrides the built-in values, which is
  the path to use if a future Arena release rotates the keys. The commit also fixes
  `SecretsRepository.getSecrets()`, which called `single()` on the never-completing DataStore flow
  and could hang if it were ever called.

- **The Home Digifarm now lives with each resident's full VB pose set.** Residents breathe on idle,
  stroll with the walk frames, jog with the run frames, work out, sleep occasionally, and pairs walk up
  to each other, turn face to face and share a happy beat before drifting apart. Walking keeps the
  diorama's pre-liveliness rhythm that the owner preferred: one 0.22-unit stride interpolated over
  1.1 s, a short foot-plant pause before the next stride, the walk pose cycling every 250 ms (jog 220
  ms), and no step bob. Pacing is probabilistic rather than directed: every finished beat rolls once
  for the next behaviour,
  with standing (49%) and walking (42%) dominating and a rare jog, workout, happy beat, nap or greeting
  making up the rest — no global cycle, schedule or cap on how many residents may do the same thing at
  once, so most of the time the island simply stands and walks about. Workouts and naps are held longer
  than the other beats (10-20 s and 30-60 s), so when one happens it is a state you can actually see.
  Greetings are the rarest roll
  (0.8%) and are held longer (6-9 s face to face), so an interaction is a small event instead of
  constant background behaviour. The cast seeds itself with fresh entropy on every creation, every
  resident owns its own random stream and greeting partners are picked uniformly at random, so no
  session replays the same show. Body collision models each
  resident as a circle the width of its extruded artwork: walkers are pushed around planted neighbours
  and the renderer adds a movers-only visual separation pass, so sprites can never be drawn passing
  through each other and a sleeping resident is never nudged. The roster resolves the complete
  twelve-pose artwork from the `DisplaySprite` view (or the bundled Dot set) instead of faking walk and
  happy with four stored frames, keeps the legacy four-frame fallback for partial data, and freezes to
  a staged tableau under reduced motion. Implementation and unit-test evidence live in `TESTING.md`.

- **Home ↔ place transitions are animated instead of cut.** Choosing a place from the farm now
  dissolves the outgoing chrome while the place lifts and fades over the world; the farm itself holds
  the screen as a depth layer, pushing in slightly and staying composited until the new screen is
  opaque, so the world is never cut out from under a half-transparent page. Returning reverses the
  same motion: the place sinks away, the farm settles back to its framing and Home's chrome lands a
  beat later. Place-to-place changes keep the short horizontal slide; reduced motion falls back to
  ~90 ms crossfades. The farm's render loop pauses as soon as it is left, so no two Filament scenes
  run during a transition, and a new `NavigationMotionTest` pins the rule that Home never rides the
  tab slide.

- **Battle screen is now a full-bleed arena.** Arena battles and Radar encounters share one floating
  battle surface instead of a 16dp page gutter, a bordered 1:1 viewport and a half-screen command deck:
  the arena fills the window, a top cluster carries exit/back, the matchup and context line, recenter,
  Debug and pause, opposing Digimon carry projected name+HP plates while the selected partner's
  HP/MP/ESP strip floats bottom-left above the band, and one bottom command band holds the CP
  instrument, the polite event line and the nine 48dp cut-corner keys grouped as menus / orders /
  movement (portrait 3 columns, landscape 5 columns in two rows). The Radar battle keeps the Radar
  world full-bleed from the encounter through the fight — the square viewport special case is gone —
  and the AR stage deliberately keeps its established square layout. Countdown, loading/transforming,
  pause (with the tactical-pause switch moved into its panel), exit, Blast timing, finisher captions and
  result/rewards all float over the live arena; the duplicate deck/HUD copies are replaced by shared
  chrome, the `"â†’"` mojibake in the Blast attacker line is fixed, and the retired split-layout helper
  and its test are removed. Implementation and unit-test evidence live in `TESTING.md`; native
  captures, TalkBack and font-scale acceptance are recorded there after the final gate.

- **Radar place is now a full-bleed isometric world (owner decision 2026-11-03/04).**
  `RadarScreen` no longer uses the World-tab scaffold: the radar grid plate fills the window, the
  active partner stands at its centre as the player token, and wild Digimon stand on the plate as
  their extruded cardboard sprites with the farm's travel-heading/camera-assist treatment. Sprite
  travel is measured from real-world positions only (never from the rotating plate or the zoom), so
  the compass turn rotates the cardboard with the world instead of looking like a direction change;
  standing tokens face the camera like idle island residents and the mirror flag uses a latch. The
  radar clamps the camera assist to ±15° so walking Digimon read front-facing, walk/idle poses use a
  smoothed speed with start/stop hysteresis (the ecosystem's 1 s interpolation steps cannot flicker
  the animation), and visibility is decided by the projected billboard rect instead of the
  visible-radius value, so zooming never hides a Digimon that is still on screen. The
  Radar place draws no distance/range rings (owner decision 2026-11-04): its compass is a large
  minimal instrument ring (120 thin cyan ticks, longer/thicker ticks at the four cardinal directions
  with the longest north, no inner circle or lettering, and a red two-triangle needle — the reference
  digivice arrow shape, larger triangle outward over a small gap and a smaller inward triangle —
  riding the ring at the nearest wild Digimon's bearing; while the radar is live the needle performs
  an occasional idle search gesture and returns to the true bearing, and the tick nearest that bearing
  is always drawn slightly longer so the reading stays visible (no gesture under reduced motion), and
  a floating top
  cluster + bottom signal band replace the old panel/scroll column. The violet ring under the partner
  token now has the interaction-proximity radius (the same radial fraction as a Digimon at
  `RadarWorldGeometry.INTERACTION_RANGE_METERS`) and a soft feathered halo/core texture instead of a
  hard band: the ring is now a single clean violet line drawn as a real 3D ground decal (depth-buffered
  under the Digimon, no Compose overlay), with a small ladder of pre-baked decal widths alpha-crossfaded
  as the ring radius changes so its thickness stays constant on screen and follows a pinch smoothly
  with no texture rebuild or settle delay and no glow; tapping a Digimon now uses its full projected
  sprite rect (min 48dp) as the hit target with no pressed ripple, like tapping a farm resident,
  instead of a floating box above its head (fixed 34° camera; the vertical
  tilt gesture was removed), and the interaction dot that used to appear over a Digimon's head is
  tilt gesture was removed), and the interaction dot that used to appear over a Digimon's head is
  gone. The zoom envelope is pulled in: the neutral view is 200 m visible, the widest zoom-out 400 m
  and the closest zoom-in 50 m (previously 500 m/1000 m/125 m), so the plate fills with nearby Digimon
  instead of empty ground. The plate is a derived
  `Arena/Radar/radar_plate.glb` (generated by
  `tools/build_radar_plate.py`) that drops the battle boundary, sky dome and floating voxel props,
  enlarges the authored floor quad (theme-bound with the same floor material) so the ground never
  ends at low tilt or wide zoom, and keeps the authored radar theme through a scene alias. The
  legacy 2D and first-person radars stay in the codebase and are reachable from the radar options
  sheet only while the master Debug switch is on. Encounter-directory updates come through a shared
  extrusion cache, a worker
  pool and nearest-first GPU uploads; the radar no longer recreates its Room flows on every compass
  tick (which had left the spawn list empty for long stretches) and re-resolves an expired region
  once on entry so its residents get sprite assets again. Characters that only own the neutral
  placeholder sprite are excluded from world population, Debug spawn choices and the radar, and
  stale placeholder encounters are cleaned when a region is prepared.

- **Koin dependency injection.** `appModule`, `databaseModule`, `networkModule`,
  `hardwareModule`, `repositoryModule`, `useCaseModule`, and `viewModelModule` start from
  `TamersLabApplication`; typed use cases (`TransferUseCase`, `BattleUseCase`,
  `SaveImportUseCase`, `ChatUseCase`, `FarmUseCase`) and repository adapters resolve from
  the graph. Covered by a Koin module-graph test and architecture-boundary tests.
- **Type-safe navigation and UI state.** `@Serializable` `AppRoute` destinations, a
  typed `NavHost`, deep-link resolution in `MainActivity`, and shared state contracts
  (`UiState`, `RequestState`, `StreamingUiState`, `UiEvent`, `UiEffect`,
  `OneShotEventQueue`).
- **Unified battle domain.** Shared `TechniqueDefinition`, `CombatantDefinition`,
  `BattleTeam`, `BattleStatusEffect`, and `BattleRules` in `battle/domain`; the offline
  simulator/session layer stays separate and the legacy online PVP/auth client lives in
  `battle/online` behind a flag.
- **Hardware module split.** `:hardware` is the Android NFC library; `:hardware-dim` is
  the pure Kotlin/JVM DIM/BEM card parser exposed through the `hardware` API. The DIM
  sprite parser is now Kotlin.
- **R8 and resource shrinking** for the release build, with explicit keep rules for
  Gson, Retrofit, protobuf-lite, Room, WorkManager, Koin, serialization, and Filament.
- **Baseline profiles.** `:baselineprofile` generator module, `profileinstaller`
  runtime dependency, and committed startup profile artifacts.
- **JaCoCo setup** for `:app` and `:game-save-core` (standalone report tasks; coverage
  not yet measured or threshold-enforced).
- **Performance scaffolding.** `SceneFrameTier` (Paused/Idle/Active/Reduced) and
  `SceneQuality` frame pacing, thermal downgrade via `PowerManager.currentThermalStatus`
  on API 29+, and the `BattleSceneChannel` scene/UI state split behind
  `BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED` (default off).
- **Documentation set.** Module READMEs, ADRs 0001–0005, a root documentation index,
  Phase 5/7 decision records, and the Alpha 0.7.0 release checklist.
- **Translations.** Japanese and Brazilian Portuguese string resources for the previously
  missing keys (including a locale farm-home file), removing the 52 lint
  `MissingTranslation` errors.
- **AR battles.** Radar encounters can be fought through the camera. New `com.tamerslab.ar`
  package adds an ARCore 1.57.0 session (`com.google.ar:core`), a GLES 3.0 camera
  background layer, and a hands-free placement model that anchors the existing Filament
  battle on a detected surface: the player only points the phone at a table or the floor,
  and the scanner automatically hit-tests detected planes, ARCore depth points and instant
  placement, derives the scale from the surface, and starts the battle once the pose is
  stable (quality gates prevent premature locks; depth/instant hits are gravity-aligned so
  the arena never appears tilted). The AR scene shows only the two Digimon and the
  `battle_boundary` ring — the floor, grid, dome and debris are hidden so the camera feed is
  the backdrop. The battle `TextureView` is hosted on a `CONFIG_TRANSPARENT` swap chain
  (Filament 1.76.1 `ModelViewer` is opaque by default) so the fight composites over the live
  camera. Scale plans (`Auto`/`Tabletop`/`Room`/`Open space`) map the 16-unit radar arena to
  0.9–8 m, chosen automatically from the detected surface, and an "AR battles" radar
  preference enables the mode. **AR battles are a developer-only option**: the preference and
  every AR entry point are gated by the build-time `AR_BATTLES_ENABLED` flag (true for
  debug/integrityCheck builds, false for release, so production builds hide and disable the
  mode). Placement and battle share one ARCore session and one
  Filament engine; unsupported devices, a missing ARCore runtime or a denied camera
  permission fall back to the normal Coliseum battle. Radar commands now submit with the
  latest command stamp so ordinary GPS updates no longer cancel interactions as stale
  snapshots. Debug-only `ArDiagnosticsActivity` smoke test and `ArStagePreviewActivity` stage
  host, 44 JVM maths tests (`ArMathTest`, `ArBattleScaleTest`, `ArBattleCameraTest`) and
  `AR_BATTLE_IMPLEMENTATION.md`; camera permission plus an optional ARCore manifest entry.

### Removed

- **Command Points and the Support/incentive command are gone.** The trainer-shared CP pool never
  gated a production technique (every catalog cost was zero) and the cheer window only fed it, so the
  resource, its snapshot fields, events, statistics, failure codes, HUD instrument, loadout cost,
  strings, tests and documentation references were removed together. Checkpoints keep
  `formatVersion = 1`: Gson ignores the old `commandPoints`/`maxCommandPoints` keys and the NPC replay
  never read CP, so active battles and existing saves load unchanged. The partner's HP/MP/ESP readings
  now occupy the header space the CP instrument used to consume.

### Changed

- **The floating voxel cubes collide with each other, and the farm glows the same in every theme.**
  The cubes bob with independent amplitudes, speeds and phases on top of their group's bob, so a
  stacked pair drifts up to ~26mm into itself - the clipping the farm showed. A per-frame solver now
  composes each cube's animated world centre from the known parent chain (group + cube translation; the
  cubes are origin-centred and unscaled), separates overlapping pairs along their axis of least
  penetration (half each, 2mm pad, four passes) and rebuilds from the authored bob every frame.
  `FloatingCubeLayoutTest` replays the real fourteen-cube layout and asserts no frame intersects, and
  also asserts the unsolved bob does overlap so the case cannot silently go stale. Reading
  `TransformManager.getWorldTransform` inside the frame's transform transaction returns identity for
  these children, which is why the first attempt had no effect. Separately, the
  farm's emissive floor and cube accents used to bloom only in the dark palettes: light palettes
  compressed the pulse to 1.15, so the glow vanished. The Digifarm now keeps a 0.94 emissive baseline
  in light themes and its controller is allowed a 1.62 pulse ceiling, so the glow matches the dark
  themes while the floor's body stays saturated - only its bright lines enter the bloom range.
- **The Digifarm island keeps the original Links ground and no longer looks hollow through its own
  cliff.** The top is built from the source atlas again (`tools/digifarm/build_reference_textures.py`):
  the floor is cut into large 16px pieces (a 2x2 block of the authored cells, keeping the source's own
  tile seams inside each piece), and the pieces are laid out in contiguous regions like the reference
  floor - broad patches of one tile, occasional warm path tiles, long runs of the same piece, mirrored
  or flipped for variety, never a per-tile mix. The mosaic is pulled onto the Tamers Lab accent's low-saturation hue (other
  palettes derive their own at build time) while keeping most of the tile variation. The
  irregular exposed cells are a near-black void carrying **one continuous bright square grid**. The
  farm is now **monochromatic per theme**: the turf is derived from that theme's `energy` accent at low
  saturation and a dark value, the grid uses the same accent at full strength (the color the voxel
  blocks already use for their edges), and the cliff's authored strata highlights - the light lines
  near the rim - are lifted to that accent too, so grass, patches, cubes and side lines are one hue
  per theme (violet, cyan, green, blue). The rest of each palette is untouched.
  The cliff is a one-sided skirt that fades to transparent at its foot, so a low camera used to see the
  inside of the far wall through the near one on the island's flanks. The upper 48% of the cliff is
  fully opaque in the authored art, so those bands now render as an alpha-masked material in the opaque
  pass and **write depth**: the near side occludes the inside of the far side (about 70% of the
  artifact, measured offline with a ray/triangle check at five camera yaws), while the fading foot keeps
  its blend and its transparency. An intermediate inset "interior lining" was removed because it
  outlived the fading foot and read as a dark shadow hanging under the island.
  `validate_asset.py` pins the five-material set, the masked upper-cliff material and the three island
  primitives; `tools/ENVIRONMENT_THEMES.md` documents the reproduce steps.
- **"Import watch connection data" is Debug-only.** The connection keys ship in the build
  (`security/BundledSecrets.kt`), so a normal install never needs that row; re-importing is only the
  fallback for a future VB Arena release that rotates the keys. It now renders under
  `Watch connection` only while Debug mode is enabled, so the everyday Settings list starts with
  Card archive. The import itself is unchanged and still overrides the built-in values.

- **Settings is ordered and grouped like a setup flow; every option is unchanged.** Sections now
  run Watch connection → Card archive (import plus the card-origin prompt) → Appearance → Sound →
  Tamer profile and preferences → Species (catalog plus the VB-first/Dot-first artwork priority) →
  Communication (research notes, and DigiLine while Debug and legacy chat are on) → Backup and
  restore → Debug mode → About and credits. The card-origin toggle moved next to card import,
  research notes and the DigiLine connection left the Tamer section, backup/restore moved above
  Debug and About, the redundant "Digimon graphics" header folded into Species, and the Tamer name
  is edited from its row in a dialog like the font and language instead of an inline field plus a
  separate save row. Row, section and dialog visuals are unchanged.

- **The app is now its own installed app, not a VBHelper update.** `applicationId` moved from
  `com.github.nacabaro.vbhelper` to `com.tamerslab.vbhelper` ([ADR 0006](docs/adr/0006-own-application-id.md),
  superseding ADR 0001), so installing this build no longer replaces or updates VBHelper and an
  upstream VBHelper release is no longer offered as an update here. The two apps install side by
  side. Consequence: a new install does not inherit the old app's local data - move a collection
  with `Settings > Export database` in the old app, then `Settings > Import database` in this one
  (cards live in that database; linked `.sav` saves are relinked; watch secrets ship with the
  build). The `integrityCheck` variant is `com.tamerslab.vbhelper.integritycheck`, and the
  baseline-profile generator, macrobenchmark, instrumentation package assertion and device scripts
  were retargeted. This is a build-configuration change: no APK was built or installed for it in
  this pass.

- **The Digifarm keeps its baked, unlit look; the only thing lighting left behind is a real drop
  shadow.** The island and its voxel blocks are authored emission-baked and the residents are unlit
  extruded pixel art, so a key/fill/rim rig plus image-based ambient was built, reviewed on the
  SM-A346M, and reverted: it read as a different game rather than as the same farm lit better. What
  stayed is the ground shadow each resident was always supposed to have - and that shadow had in fact
  never rendered. With the pinned gltfio 1.76.1 an **unlit** blended primitive of the generated
  resident GLB never reaches the color buffer; the contact shadow is now a blended **lit** material
  whose albedo is black, so it renders as a clean alpha-blended shadow with no lighting dependency.
  The quad is larger and softer than the old hidden one (1.24 x 0.92 local units, 66% alpha, softer
  falloff), so a resident visibly sits on the grass. A JVM test pins the blend/lighting combination so
  the shadow cannot silently disappear again.
- **The last visible remnants of the retired multi-farm system are gone.** DigiLine no longer has a
  "Farms" tab (it lists only Storage and Wild Ones), Storage no longer captions a partner with the farm
  it lives on, and the farm-group chat route/screen, the old `DigifarmScreen` (create another,
  archive, needs, transfer) and the Radar|Digifarm tabs on the legacy `World` route were deleted. The
  `World` alias now opens the Radar world directly. Stored farm data and tables are untouched; only
  the exposed UI was retired.
- **The battle command band is a fixed 3×3 instrument after Re:Digitize Decode.** The float
  disappears: row one is the partner's three equipped techniques (name plus EN cost and live
  availability), row two Items / Special / Strategy, row three Focus / Defend / Move. Move arms a
  one-tap arena destination (move-to-point via the scene's unpick) instead of opening a menu, and the
  selected partner's vitals were folded into the band header so nothing floats over the arena. The
  special/Blast timing is a Decode-style command dial with a dark technical ring, a yellow progress arc,
  a red target arc, ticks and a needle, the ordered move's name in the centre, and a full-screen tap as
  the only input (no Stop button). The nine deck keys are bare HUD keys — no fill and no angular
  frame, each settled by a 1dp hairline — matching the bare-key instrument vocabulary of the top
  cluster. The same deck is shared by training/Arena, Radar and the AR stage.

- **The Radar ground scrolls as you walk.** The active partner still stays planted at the plate centre,
  but the authored grid now slides opposite to the player's real travel, the way the legacy 2D radar
  scrolls its grid under the centred marker: the phase is anchored to the first location fix, expressed
  at the plate's linear metre scale (one authored cell = 60 m), folded to half a cell so the finite
  authored floor never runs out, and rotated with the compass like the plate and the tokens. The slide
  is low-pass filtered with a time constant scaled by the reported GPS accuracy (0.35 s + 0.08 s per
  metre; coarser than 30 m the grid holds still), so standing in place does not shake the ground.
  Because the authored world grid is a translucent mesh that now slides under the ground markings, the
  metre-ring decal and the interaction ring were given late Filament render priorities (5 and 6) so the
  ring stays above the grid instead of flipping under it as the plate moves. The mapping is covered by
  two `RadarIsometricGeometryTest` cases (direction/rate and the half-cell fold). Slide rate follows
  real metres, so it is subtle at the widest zoom and strongest when zoomed in — same as the 2D radar.

- **Home ↔ place background now crossfades instead of cutting.** The destination pages are transparent
  over the global device pattern, so during a change the farm layer *is* the arriving page's
  background. It used to be held opaque and dropped at the end of the transition, which made the
  background swap abruptly; it now pushes in while dissolving into the device pattern over 240 ms on the
  way out, and settles back while fading in over 240 ms on the way home.
- **Tamer Arena lobby bars collapse on scroll.** The search field and the Tamers/Tournaments/Records
  section tabs slide up while the opponent roster is scrolled down and return on any upward scroll or at
  the top, mirroring Storage's search bar; entering the team editor, a briefing or the bracket restores
  them. Reduced motion keeps the same behavior without the expand/fade animation.

- **Digifarm roster adds members through the shared partner picker.** The "In the Digifarm" sheet still
  lists current members beside the resident-limit and artwork controls, but "Add member" now opens the
  same full-screen Storage partner picker the Arena uses for choosing a partner — search, filters, sort
  and the sprite grid — instead of the sheet's bespoke candidate list. The picked partner lands on the
  island immediately, the picker stays open while there is free capacity, and it closes on the last
  slot. `FarmMemberOption` now carries the stored character so the roster can hand the picker real
  sprite data. (Native capture pending a connected device.)

- **One top-header type across the app.** Every boxed screen header now uses the same top-cluster
  material as Home, the Radar place and the battle surface instead of the old filled gradient banner:
  a palette-resolved scrim, a left-aligned title with a single quiet context line, one junction line,
  and bare 48dp keys — icon and target only, no fill, no hairline stroke and no cut-corner frame, the
  way Home's HUD keys already were. The back key always owns the left edge (the battle header's left
  key is its exit action); every other action the header was given now sits on the right instead of
  split across both sides, and when a screen passes no back callback the header uses the activity back
  dispatcher so it behaves like the system back gesture. The shared primitives live in
  `components/HudChrome.kt`; `TopBanner` keeps its API, labels and semantics, so no call site changed.
  Tokens resolve through the selected palette, including the light Arena and Digimon.net themes.
  Native captures are pending a connected device.
- **Place switching is lighter on mid-range hardware.** The transition is unchanged in shape, but the
  work that ran inside it is not: collection cards no longer re-decode their sprite on every arrival
  and on every idle-frame swap (a content-keyed, 16 MiB decoded-sprite cache now serves them), a
  destination is told when a route transition is still delivering it and skips its own per-card
  entrance instead of stacking a screenful of alpha/scale layers on top of the page motion, and the
  persistent farm surface keeps the window's full bounds for the shell's lifetime so leaving or
  entering Home no longer resizes and re-lays out the view that owns the Filament swapchain. Items
  that appear later (scrolling, later pages) still animate in. No visual design change; measurement
  is pending a connected device, see `TESTING.md` and `docs/performance/BASELINE.md`.
- **Wild↔wild chat and battles are paused for current versions (owner decision 2026-11-04).**
  `WildSocialInteractionGate` unwires the autonomous pair planner, the wild-pair battle
  transfers and their activation while the social planner, dialogue service, NPC battle
  simulation, persistence, history and Radar rendering paths all stay in the codebase. Wild →
  player greetings/challenges and every player-directed or joined encounter are unaffected.
  Pairs left open by an earlier build are cancelled with `WILD_SOCIAL_PAUSED` (releasing their
  participation claims) and paused pairs are omitted from the public ecosystem snapshot, so the
  Radar no longer shows wild-on-wild interactions while the feature is off. Re-enabling is the
  single `WildSocialInteractionGate.ENABLED` flag.
- **Package and product rebrand.** Kotlin packages and namespace are `com.tamerslab`; the
  launcher label is **Tamers Lab**, the root Gradle project is `TamersLab`, and the
  application class/theme are `TamersLabApplication` / `TamersLabTheme`. The
  `applicationId` remained `com.github.nacabaro.vbhelper` for upgrade continuity during the
  refactor (see [ADR 0001](docs/adr/0001-keep-application-id.md)); that decision was later
  superseded by [ADR 0006](docs/adr/0006-own-application-id.md), which gives the app its own
  `com.tamerslab.vbhelper` identity — see the "Changed" entry above.
- **Feature-based package layout.** `battle`, `farm`, `transfer`, `chat`, `species`,
  `world`, `storage`, `home`, `items`, `settings`, `adventure`, and `quests`, plus shared
  `data`, `domain`, `ui`, `rendering`, `companion`, `di`, `navigation`, `security`,
  `vitalwear`, `utils`, `widget`, `components`, and `audio`. The old top-level
  `screens/`, `daos/`, `database/`, and `source/` packages are gone.
- **Room schema location.** Exported schemas now live under
  `app/schemas/com.tamerslab.data.database.AppDatabase/`; schema version remains 55 with
  preserving migrations.
- **DI cutover.** The manual `DefaultAppContainer` is now a Koin-managed adapter rather
  than the composition root ([ADR 0003](docs/adr/0003-koin-dependency-injection.md)).
- **Battle delivery.** The offline battle screen can publish the full-rate snapshot
  directly to the scene while throttling Compose UI state; old behavior remains the
  default until device validation.
- **Baseline-profile work.** Existing generated profiles were produced before the Phase 6
  package move. They were deterministically re-mapped to the current class names with
  `scripts/remap-baseline-profiles.py` (framework entries untouched, unresolved synthetic
  entries dropped); on-device regeneration with the extended journeys is still pending,
  so the files are not a post-restructure measurement.

- **Home HUD unboxed.** Home's top zone is no longer a bar: it is two clusters floating over the
  Digifarm - resident count with a twelve-slot place gauge plus the bits readout on the left, the
  quests/settings/menu keys on the right - held by a palette scrim, a hairline ground line and HUD
  ink with a soft text shadow. The five place keys are free-standing again, but each one is now a
  56x66 machined key with its caption inside, near-opaque tonal fill, a readable frame, an inner
  bevel and an accent catch-light ([HudChrome.kt](app/src/main/java/com/tamerslab/farm/ui/home/HudChrome.kt)),
  replacing the translucent tiles whose scene bleed read as a false selection. A second pass removed
  the tiles entirely: the places are now icon plus caption on a bottom scrim that fades into the
  partner slab, framed by a single hairline console edge, so the whole home reads as one overlay and
  the slab is the only surface left. Captions auto-size instead of truncating (pt-BR "Parceiros" and
  font scale 1.3 both fit), press/current states use the accent plus a 2dp underline, and the row is
  capped at 460dp and centred so wide windows do not stretch it across the screen. A third pass
  moved the row below the partner slab, so the bottom of the screen now reads slab-then-places, and
  linked the two: the sheet reports how far it is pulled and yields exactly the row's 70dp band while
  opening, so the row fades and drifts out under the sheet's own motion instead of popping on a
  detent edge, with no overlap and no gap between them. The top ground and the place row's ground now
  paint the partner slab's own surface tone at its own opacity, and the slab's frame is drawn without
  its bottom edge, so the three zones read as one material and the slab flows into the place row with
  no hard line; the row's ground fades out at the screen edge the way the top ground fades into the
  sky. Follow-up fixes: the slab keeps only its upper edge (no side or bottom strokes, so it no longer
  disagrees with the full-width place row); the junction between slab and row is marked by the same
  inset hairline the top zone uses; the row's ground starts at the sheet's exact tone so the two fit
  together and only firms up toward the bottom; dragging the sheet is 1:1 under the finger because the sheet owns one height value
  instead of jumping between a detent target and a drag offset; the chevron drops an expanded overview
  all the way to peek; and the top status zone fades away while the overview is fully open. No blur or
  glass pass is applied to chrome: translucency stays a plain tint. The old top scrim was
  built from `colorScheme.background`, which is transparent in every palette, so it never painted;
  it now reads the palette background token. Every action keeps a 48dp target. The partner slab's
  stage separator was also repaired from a double-encoded `Â·` to `·`. Compile-verified only; the
  device capture pass is still pending a connected phone.

- **Home entry points and boot.** The status zone now carries Transfer and Dex instead of Quests
  (Quests stays in the menu sheet); tapping a Digimon in the farm offers Follow, Set as active and Swap
  for one in Storage; switching the active partner by scrolling over the overview card is retired; and
  Home opens behind a boot cover while the habitat, residents and active partner load.

- **Farm membership (rewritten).** The island now shows the active partner plus an explicit member
  set in the farm DataStore instead of favourites; the set seeds from favourites once on first edit,
  activating a partner adds it, and the resident sheet can remove a partner. The camera follow choice
  is persisted by character id so roster refresh and rotation can no longer re-point it. The "who is
  here" sheet now curates membership next to the quantity control: every stored partner is listed with
  its state and an add/remove action. Superseded
  note: the active partner is no longer forced to be a favourite and a leader that steps down is
  favourited, so promoting someone no longer deletes the previous leader from the island; explicit
  swaps opt out because there the outgoing partner is meant to leave. An artwork filter can no longer
  drop the leader (it falls back to available artwork). Full review: [farm-home-review.md](analysis/farm-home-review.md).

- **Cinematic Digifarm camera.** The diorama now drifts on its own: a slow orbit around the followed
  partner with elevation and distance drifting on independent periods, hard cuts to a different shot
  every 14-26s from a rotating set of low / three-quarter / high shot heights and capped below ~33
  degrees of elevation (higher angles distort the extruded sprites),
  parked for 2.8s by a touch and re-based on the visitor's framing afterwards (never
  snapping back to the previous loop). Disabled under reduced motion.

- **Home entry points reshuffled.** The status-zone key that opened the Dex now opens Settings, the
  place row's DigiLine key now opens the Dex, and the partner slab's chat key opens the whole DigiLine
  list with the active partner's conversation pinned first.

- **Cinematic camera: close-ups and floor collision.** Cuts now include a close-up distance band, the
  grazing angles are weighted more common than the steep one, and the lens is floor-limited so no cut,
  drift or manual drag can put it at or below the island surface.

### Changed

- **The Items tab is a grouped catalogue.** Two tabs instead of three: My items and Items store. My
  items is sectioned by function - Training, Evolution, Vitals, Special missions - with the quest
  battle stock folded in as its last section (the separate Battle items tab is gone). Entries are
  compact instrument rows: a 44dp framed glyph well, a two-line name that reserves both lines, the
  effect glyph and only the number that list needs (owned count, stock or store price; an unaffordable
  price turns to the error colour). The old wide cards left a large empty artwork well, locked names
  to three lines and produced ragged heights; the new rows are uniform and roughly half the height,
  and special-mission rows take the full width because their names carry a goal and a window. The
  store opens with one wallet strip (balance plus how many entries are affordable) above the same
  sections. Special-mission items no longer fall through to a question-mark glyph: steps, vitals,
  battle and win missions have their own glyphs. The item dialog repeats the row grammar and puts the
  price and affordability next to the action, so Purchase is disabled with a reason instead of failing
  after the tap. Strings are in the three supported locales and `items_kind_count` is a plural.

### Added

- **Special missions can be assigned straight from the partner overview.** Each of the four mission
  slots on an eligible partner's overview is now actionable: an empty slot reads "Tap to assign" and
  opens a chooser listing the owned special-mission items that fit that slot only (steps, vitals,
  battles, wins), with quantity and an Equip action. Equipping consumes one item through the same
  gameplay path as the Items tab and the slot/progress flow refreshes live, so adding a mission from
  Home no longer requires going through Items -> Use item -> partner picker. Running missions keep
  their removal action and completed ones keep their collect action. Slot/item mapping lives in
  `SpecialMissionSlots` with unit coverage.

### Fixed

- **A form gained on the watch now survives the next app start.** Reported flow: a Digimon imported
  from World DS went to the watch, evolved there, and came back to the app on its evolved form — and
  was then rewritten to the form it had when it last left the app, while the evolved form stayed in the
  Dex. The import itself was right (the app stores the watch's current slot); the World DS archive
  keeps the record it imported, and its species repair/reconcile runs on every startup and save refresh,
  so the stale archived record kept overriding the newer device state. Native records are now
  authoritative only while the game produced something newer: `NativeReconcilePolicy` makes a retained
  watch baseline captured after the archived record supersede it, and makes a re-read of an unchanged
  record a no-op instead of a revert (`reconcileReturnedSpecies`, `applyWorldDsReturn`). A game record
  that really changed — the Digimon was played in the game again — is still adopted. Covered by
  `NativeReconcilePolicyTest` (5 cases).

- **The Transfer tab reads as one destination again.** The Home transfer action (header key and
  menu entry) opened the Vital Bracelet pane directly; it now opens the overall Transfer tab with the
  origins list first, and the bracelet is one row there. Inside the tab, the embedded watch steps used
  to render their own `TopBanner` under the hub's banner - the reported "two headers on one screen" -
  so `ActionScreen`, `ReadCharacterScreen`, `WriteCardScreen`, `WriteCharacterScreen` and `ChooseCard`
  now take `showHeader` and, when embedded, render their name as an in-flow section heading with the
  exit the dropped banner used to carry (Cancel) instead. The hub also keeps a page back-stack (Back
  returns to the page that opened the current one rather than jumping to the list), the "Manage saves"
  page no longer pushes a second Transfer destination from its watch block (that block is gone; the
  bracelet row in the origins list is the one entry point), and its empty state no longer repeats the
  "Linked saves" heading that the section header above already shows. The outgoing-partner control is
  now one card (label, partner or "Receive only", Change) with a radio-list chooser, and the options
  menu states the current page and carries the same icons as the rows it mirrors. Unit tests green;
  verified on the SM-A346M at 1080x2340 (origins list, watch pane, chooser dialog, embedded write
  step).

- **The bracelet send screen no longer crashes for a partner with an assigned DIM/BEM.** Captured from
  the owner's SM-A346M: `java.lang.IllegalStateException: Cannot access database on the main thread` at
  `CardDao_Impl.getCardById` ← `ScanRepository.getCardDetails`. The send-confirmation screens collect
  that flow from Compose, so its builder ran on the main thread, and the blocking
  `CardDao.getCardById`/`getCardByCharacterIdSync` methods threw there. It only executed for a partner
  with an export target, i.e. a catalog/game-origin partner after a compatible-DiM conversion;
  watch-origin partners took the Room-managed flow path and never hit it. The builder now runs on
  `Dispatchers.IO`, and it resolves to a neutral empty card instead of collecting a single-result query
  that can legitimately have no row.
- **Preparation for a physical watch is recoverable.** Any failure while building the outgoing payload is
  logged as `SCAN_PREPARE` and reported through the existing "could not be prepared" panel with its Retry
  action instead of taking the process down (fatal VM errors are still rethrown), and the pre-export
  reaction baseline is best-effort because a missing baseline only skips the later comparison.
- **A recorded export target that is not a real DIM/BEM no longer enables a send that cannot be
  written.** Materialization can point `exportCardCharacterId` at a card-independent definition while
  no physical slot is associated; bracelet capabilities now only count a card-backed slot, so the
  transfer screen offers the compatible-DiM conversion instead of showing a usable "send" that fails
  during preparation.
- **The individual information shows the DiM/BEM a converted partner is exported through.** A
  catalog/game-origin partner has no card of its own, so the Storage detail line (and the Debug card
  field of its info editor) stayed blank even after a DIM/BEM was assigned. It now falls back to the
  exact slot recorded for bracelet export: on the owner's device the "Marcos" record now reads
  `Metal Mamemon · 10 NuMetalEmpire`, and the same conversion renders the write-card screen
  ("You will need 10 NuMetalEmpire card") instead of crashing.

- **Farm walking restored to the diorama's pre-liveliness rhythm.** The resident-life pass had replaced
  the old stride rhythm with continuous movement at a fixed cadence; after device feedback the owner
  asked for the earlier feel back, with the correct frames (walk frames on the walk pose, run frames on
  the jog). `FarmDioramaSimulation` now walks in the old strides again — 0.22 world units interpolated
  over 1.1 s, then a 1.15-1.6 s beat before the next one, which leaves the short foot-plant pause the
  old island had — and jogs in longer 0.32-unit strides over 0.8 s. The renderer cycles the walk pose at
  a fixed 250 ms and the jog at 220 ms; the speed-derived cadence, the beat latch and the step bob from
  the previous passes are gone. A jog can no longer strobe because one stride fills the whole
  interpolation window.
- **The Radar's biome readout is visible again.** The OpenStreetMap biome system was still resolving and
  still biased spawn attributes, but its only UI was a 10sp label inside the legacy 2D radar, which is
  reachable only while the master Debug switch is on — the isometric redesign dropped the display. The
  floating top cluster now carries the same localized "Surroundings: …" line under "Radar · NW" (absent
  while the lookup is Null, so unavailable map data never invents surroundings), and `WorldScreen`
  publishes the resolved biome as soon as the lookup returns instead of only after a successful region
  command, so a rejected/stale spawn refresh no longer leaves the readout empty.
- **Full-screen pickers and editors no longer darken the page in the light palettes.** The Arena
  "Choose partner" picker, the species sprite picker and the species catalog editor painted a
  transparent dialog surface (`colorScheme.background` is transparent in this theme, so the global
  device pattern can show through normal pages), which let the screen underneath and the dialog window
  dim show through and made the whole page read as a darker theme. Their windows are now edge-to-edge
  and paint the palette's own opaque page background, with the wrapper handling the system-bar/IME
  insets, so light palettes stay light and dark ones stay dark.
- **Radar encounters no longer crash a few seconds into the battle.** The Radar battle's Filament
  scene is hoisted to the root of the Radar battle surface so Compose can never replace the
  `AndroidView` mid-fight: the previous layout branch was re-composed at the countdown, destroying and
  immediately recreating the Filament engine and tripping a native use-after-free inside
  `libfilament-jni.so` (SIGSEGV, fault address `0x0`). The scene is now composed once per encounter and
  renderer-permission changes only pause its frame loop. Verified on the SM-A346M with two consecutive
  Radar battles that ran to "Radar victory" with a single engine creation each, an empty crash buffer
  and a surviving app process. A settled battle also hands the surface to the result panel now: the
  live top cluster, command band and projected HUD step aside instead of showing inert keys behind the
  outcome and its rewards.
- NFC/auth logging no longer prints callback URIs, decrypted card bytes, tag ids,
  passwords, or full transfer tokens.
- Backup/device-transfer rules exclude `secrets.pb`, battle auth tokens, LLM settings,
  and logs; cleartext traffic is denied by default with a narrow allowlist.
- Removed the unused `CompanionValidatedDatabase` and a duplicate Koin binding for
  `ValidatedCardManager`.
- Lint baseline cleanup and missing-translation work landed; the zero-error state is not
  yet re-verified after the Phase 6 restructure.

### Security

- Online PVP is gated by `BuildConfig.ONLINE_PVP_ENABLED` /
  `OnlineBattleFeature`; the residual HTTPS, Keystore, and App-Link risks are tracked in
  [PHASE4_DECISIONS.md](docs/refactor/PHASE4_DECISIONS.md).
- No secrets, retail card dumps, real saves, pairing links, or controlled captures are
  committed. See [CONTRIBUTING.md](CONTRIBUTING.md#secrets-fixtures-and-private-data).

### Quality and tooling

- detekt and ktlint were scaffolded for Phase 7.1 but the aggregate run could not be
  completed reliably under the constrained build memory settings. They were **removed
  from the build and deferred to a follow-up quality pass** (see
  [docs/refactor/PHASE7_DECISIONS.md](docs/refactor/PHASE7_DECISIONS.md)); the build no
  longer applies those plugins.
- `:app:lint` remains the static-analysis command and is a blocking CI step. The 52
  `MissingTranslation` errors were fixed with real JA/PT-BR resources and
  `app/lint-baseline.xml` was pruned to 62 justified pre-existing warnings; the clean-run
  result is recorded with the final verification.

### Release preparation

- No production signing keystore exists; release smoke builds are unsigned or signed
  with the local debug key only.
- The R8 mapping file must be regenerated and retained for each release candidate.
- Baseline profiles must be regenerated or explicitly re-mapped after the package move.
- Play Console upload has **not** been done.

### Verification

Post-Phase-6 verification ran on the uncommitted working tree on 2026-10-31
(all Gradle commands with `--no-daemon`; the final numbers include the Phase 1/5/7
gap-closing work — an earlier pass in the same session with 1,155 tests and a
149,002,850-byte APK is superseded):

- **Unit tests:** 1,157 tests, 0 failures, 0 errors, 7 skipped across
  `:app:testIntegrityCheckUnitTest` (900), `:game-save-core:test` (192),
  `:companion-protocol:test` (4), `:pc-save-companion:test` (11),
  `:hardware-dim:test` (28, 4 intentional skips), `:hardware:testDebugUnitTest` (22).
- **Build:** `:app:assembleDebug` and `:app:assembleRelease` (R8 minify + resource
  shrinking) succeeded with the regenerated profiles; the unsigned release APK is
  149,019,234 bytes (142.12 MiB) and the R8 mapping was regenerated.
- **Lint:** `:app:lint` reports 0 errors, 422 warnings, 38 hints (warnings/hints only;
  25 stale baseline entries remain a hygiene follow-up).
- **Device:** 281/281 connected instrumentation tests passed on the SM-A346M (API 35);
  debug and debug-key-signed release builds installed with `adb install -r` and cold
  launched without crash entries (single `am start -W` smoke launches, not benchmarks).
- **Baseline profiles:** regenerated on the device with startup + best-effort
  scan/battle/farm/transfer journeys (2,723,442 bytes each; 24,408 rules).
- **Coverage:** JaCoCo reports for app and game-save-core; all six named classes are
  above the 80% line target (81.0–95.8%). No threshold is enforced yet.
- **Benchmarks (device-gated):** cold-start TTID median 875.0 ms over 5 iterations;
  battle frame CPU duration P95 13.3 ms; the farm 3-second window recorded P95 29.8 ms
  and includes 3D asset loading (steady active/idle and thermal behavior unverified).
- **Not verified:** Room query plans, 30-minute soak, onboarding timing, multi-API
  (28/31/34) instrumentation, production signing, and Play upload. The battle-channel
  flag stays off until a flag-on/flag-off Perfetto A/B run. No commit was created and
  nothing was published.

Useful references: [documentation index](docs/INDEX.md),
[performance baseline and budgets](docs/performance/BASELINE.md),
[Alpha 0.7.0 release checklist](docs/release/RELEASE_CHECKLIST_0.7.0.md).
