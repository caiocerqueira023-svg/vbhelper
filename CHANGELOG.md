# Changelog

All notable changes to Tamers Lab are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are project
milestones rather than strict SemVer.

## [Unreleased] — Alpha 0.7.0

Target: Alpha 0.7.0. This entry summarizes the refactor work that is implemented in the
tree; it is not a release announcement and does not claim a verified green build.

### Added

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
  preference enables the mode. Placement and battle share one ARCore session and one
  Filament engine; unsupported devices, a missing ARCore runtime or a denied camera
  permission fall back to the normal Coliseum battle. Radar commands now submit with the
  latest command stamp so ordinary GPS updates no longer cancel interactions as stale
  snapshots. Debug-only `ArDiagnosticsActivity` smoke test and `ArStagePreviewActivity` stage
  host, 44 JVM maths tests (`ArMathTest`, `ArBattleScaleTest`, `ArBattleCameraTest`) and
  `AR_BATTLE_IMPLEMENTATION.md`; camera permission plus an optional ARCore manifest entry.

### Changed

- **One top-header type across the app.** Every boxed screen header now uses the same top-cluster
  material as Home, the Radar place and the battle surface instead of the old filled gradient banner:
  a palette-resolved scrim, one leading 48dp cut-corner key, a left-aligned title with a single quiet
  context line, trailing cut-corner keys with a hairline stroke (cyan only while pressed) and one
  junction line. The shared primitives live in `components/HudChrome.kt`; `TopBanner` keeps its API,
  its action priority, labels and semantics, so no call site changed. Tokens resolve through the
  selected palette, including the light Arena and Digimon.net themes. Native captures are pending a
  connected device.
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
  `applicationId` remains `com.github.nacabaro.vbhelper` for upgrade continuity (see
  [ADR 0001](docs/adr/0001-keep-application-id.md)).
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

### Fixed

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
