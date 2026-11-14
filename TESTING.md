# Testing and verification

This is the current testing guide for Tamers Lab. The old 200 KB chronological
regression log was archived at
[docs/testing/HISTORICAL_REGRESSION_LOG.md](docs/testing/HISTORICAL_REGRESSION_LOG.md);
it is historical evidence, not the current result. Performance evidence lives in
[docs/performance/BASELINE.md](docs/performance/BASELINE.md); quality-tooling decisions
live in [docs/refactor/PHASE7_DECISIONS.md](docs/refactor/PHASE7_DECISIONS.md).

## Single-farm cleanup: no multi-farm UI leftovers (2026-11-07, same tree)

Owner request: two places still behaved as if the app had multiple Digifarms — the DigiLine had a
farm section, and Storage captioned some partners with "in *farm name*". Removed every exposed
multi-farm surface; stored data and the legacy farm tables are untouched.

- **DigiLine.** The third "Farms" tab is gone; the screen now lists only Storage and Wild Ones, and the
  farm-thread query behind it (`observeFarmThreads`, `DigilineFarmThread`, `FarmThreadList`) was
  deleted with it.
- **Storage.** The `in <farm name>` caption under partner tiles is gone; the screen no longer observes
  `FarmAssignment`s at all (query and DTO removed). Character tiles now show only the partner name, the
  active marker, the favourite star and the away-source line.
- **Farm group chat.** `FarmGroupScreen`, its `AppRoute.FarmGroup` destination, the matching
  `NavigationItems` entry and the bottom-bar/back-stack mappings are deleted.
- **Legacy World alias and the old farm screen.** `DigifarmScreen` (the multi-farm list with create
  another/archive/resident needs/transfer) and the Radar|Digifarm tab row on the legacy `World` route
  are deleted; the `World` alias now opens the Radar world directly. `WorldSectionTabs` is gone and
  `RadarScreen` no longer carries tab parameters.
- Docs updated: `DESIGN.md` → Farm home shell, and the follow-up note in
  `PRESENTATION_REDESIGN_PLAN.md`.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1019 tests, 0 failures, 0 errors, 3 skipped |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here: DigiLine two-tab layout, Storage tiles without the caption and the World alias need a device look |

## Command Points removed; Decode-inspired 3×3 command band and tap-to-move (2026-11-07, same tree)

Owner request: remove the Command Points resource (it never gated anything), redesign the battle
command band after Re:Digitize Decode — row 1 the partner's three equipped techniques, row 2 Items /
Special / Strategy, row 3 Focus / Defend / Move — move becomes a one-tap arena destination instead of
approach/fall-back, the partner's HP/MP/ESP readings move into the band header where the CP instrument
used to sit (no floating vitals strip over the arena), and the special/Blast timing becomes a
Decode-style command dial with a yellow progress arc, a red target arc and no Stop button (any touch
answers it).

What changed (source of truth: `DESIGN.md` → Battle screen):

- Removed from the core: `TechniqueDefinition.commandPointCost`; `BattleConfiguration`
  `commandPoints`/`maxCommandPoints`; `BattleSnapshot` CP/support fields; `BattleStatistics.supportCommands`;
  `TrainerAction.Support`; `OrderFailure.SUPPORT_*`; `SupportWindowOpened`/`SupportSucceeded`; the
  cheer window, CP balances, reservations, gates and HUD instrument.
- Added `TrainerAction.MoveTo(x, z)` as an immediate trainer action: finite-coordinate validation,
  playable-radius clamp, 8 s timeout, arrival cleanup, and cancellation by defend/technique/queued
  order; the renderer exposes `OfflineBattleSceneView.pickArenaPoint(x, y)` (projects arena floor
  candidates with the HUD's forward matrix and returns the visible point nearest the tap).
- Rebuilt the band as a fixed 3×3 deck shared by training/Arena, Radar and the AR stage; the partner
  instruments and deck are capped at 520dp and centred on wide windows; the duplicate AR command
  header/bar, the floating partner vitals strip and the redundant Techniques dialog are gone.
- Blast timing dial: dark technical ring with tick marks, a yellow progress arc and a red target arc
  from `BLAST_RED_ZONE_START`, a live needle, and the ordered move's name plus a small BLAST label in
  the centre; no Stop button, full-screen touch remains the input and the timing/`blastNeedleHit`
  logic is unchanged.
- Final visual pass (owner reference): the timing overlay was replaced by the Decode-style command
  dial above (ring, arcs, 48 ticks, needle, centred move name), and the instruction now names the ring
  instead of a bar.
- Checkpoint compatibility: `formatVersion` stays `1`; Gson ignores the removed
  `commandPoints`/`maxCommandPoints` keys in old envelopes, and NPC replay never read CP, so active
  battles keep loading. No Room migration.
- Follow-up fixes after the first device pass (2026-11-07): the three technique keys now resolve
  locale-aware names through `battleTechniqueName` (the catalogue's authored pt-BR fallback no longer
  leaks into en/ja); tap-to-move resolves the destination by projecting arena floor candidates with
  the same forward matrix the projected HUD uses (the inverse ray was mapping far taps closer) and the
  marked walk suspends autonomous planning — guards, counters and autonomous attacks no longer clear
  it — until arrival or timeout; warm rematches reset the visible session (snapshot/fighters/countdown)
  and the scene's stale guard now keys each accepted snapshot to its session, so the new battle no
  longer keeps the previous positions or renders invisible fighters.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed (final tree) |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1019 tests, 0 failures, 0 errors, 3 skipped; the deck test covers the nine fixed slots, `moveToKeepsWalkingWhileAutonomyWantsToAttack` covers the autonomy lock, and `everyCatalogTechniqueHasALocaleAwareDisplayName` covers the localized technique keys |
| `:app:lintDebug` | Passed on the changed tree (before the follow-up fixes); no new lint findings were expected, but the follow-up pass was build+test only |
| `:app:compileIntegrityCheckAndroidTestKotlin` | Passed |
| Checkpoint/replay | Unchanged policy (`formatVersion = 1`, unknown Gson keys ignored); no migration required |
| Native capture | **Pending by owner request.** The device was only used for the first pass; the owner asked that it not be used for further captures. Portrait/landscape phone captures, the AR compact layout, TalkBack and font-scale acceptance remain owed before native acceptance — compilation is not native approval. |

## Farm long beats: workouts and naps take longer (2026-11-07, same tree)

Owner request: interactions like sleeping and training should take longer.

- **Training** now holds **10-20 s** (was 4-7.5 s).
- **Sleeping** now holds **30-60 s** (was 15-30 s).
- Because those states now occupy more wall-clock time, their roll probability was trimmed slightly to
  keep the island calm rather than busy: training 4% → 2%, sleep unchanged at 1%, standing 47.2% →
  49.2%. Everything else (walking 42%, jog 3%, happy 2%, greeting 0.8%) is unchanged, so ~91% of rolls
  are still standing/walking, but a workout or nap that happens is now a state you can actually see.
- No other behaviour changed.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1014 tests, 0 failures, 0 errors, 3 skipped; `FarmDioramaSimulationTest` (10) and `ResidentPoseAnimationTest` (14) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here; the longer workout/nap holds need a device look |

## Farm walking restored to the pre-liveliness rhythm (2026-11-07, same tree)

Owner request: look at git to see how walking worked before the resident-life pass and restore that
feel, while explicitly keeping **walk frames on the walk pose and run frames on the run pose**.

- **What git showed.** Before the resident-life pass the diorama walked in discrete strides: one step
  of up to 0.22 world units was handed to the renderer, interpolated over 1.1 s, and the next stride
  started after a 1.15-1.6 s beat — leaving a short foot-plant pause between steps. The walk pose was a
  fixed 250 ms cycle, and there was no step bob. (That version bound the run frames to the walk pose
  only because the old four-frame data had no walk frames; the owner asked for the correct frames now.)
- **Restored.** `FarmDioramaSimulation` moves walkers/joggers in the old stride rhythm again: 0.22-unit
  walk strides interpolated over 1.1 s with a 1.15-1.6 s beat, and 0.32-unit jog strides over 0.8 s
  with a 0.9-1.2 s beat. The renderer cycles the walk pose at a fixed **250 ms** and the jog at
  **220 ms**; the speed-derived cadence, the beat latch and the step bob from the previous passes are
  removed. The pose mapping is the requested one: `walk`/`walk2` use the artwork's walk frames,
  `run`/`run2` its run frames. The old pause reads as the sprite coming to rest between steps, and the
  run can no longer strobe because one stride fills the whole 1.1 s window.
- Kept from the later passes: probability pacing, rarer/longer greetings, fresh entropy and per-
  resident random streams, collision, and the removal of the head glyphs.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1014 tests, 0 failures, 0 errors, 3 skipped; `FarmDioramaSimulationTest` (10) and `ResidentPoseAnimationTest` (14) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here; the restored stride rhythm and frame mapping need a device look |

## Farm randomness: no replayable seed (2026-11-07, same tree)

Owner report: interactions looked like they repeated the same pattern "every time", asking for truly
randomized behaviour per Digimon instead of a fixed seed.

- **Cause.** `FarmHomeWorld` seeded `FarmDioramaSimulation` with `rosterKey.hashCode()`, so every
  session (and every roster/art change) replayed the exact same deterministic script: same opening
  layout, same sequence of greetings, same pairs.
- **Fix.** The production host now seeds each newly created cast with `Random.nextLong()` (fresh
  entropy), while every resident additionally owns its **own** random stream derived from that seed —
  one Digimon's decisions are independent of another's and of cast iteration order. Greeting partners
  are now picked **uniformly at random** among everyone standing within reach instead of always the
  nearest neighbour. The deterministic `seed` constructor parameter remains, but it is now a test-only
  path; `differentSeedsDoNotReplayTheSameShow` pins that different seeds really produce different
  shows, alongside the existing same-seed determinism test.
- A session therefore never repeats itself: start the app twice with the same farm and the greetings,
  pairings and timings differ.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1017 tests, 0 failures, 0 errors, 3 skipped; `FarmDioramaSimulationTest` (10) and `ResidentPoseAnimationTest` (17) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here; two app sessions must be compared on device to confirm the shows differ |

## Farm greetings: rarer and longer (2026-11-07, same tree)

Owner request: make the interactions rarer and last longer.

- **Rarity.** The greeting roll is now an explicit probability instead of "whatever is left over":
  standing 47.2%, walking 42%, jog 3%, workout 4%, happy beat 2%, nap 1%, greeting **0.8%**
  (previously 2%). A greeting also only starts when a standing neighbour is within reach; otherwise
  the roll becomes an ordinary walk, so the effective frequency is even lower. With twelve residents
  this works out to roughly one greeting every ~40-45 s across the island.
- **Duration.** The face-to-face celebration was 2.4-4.0 s; it is now **5.5-8.5 s**, so the whole beat
  (walk over, celebrate, walk away) lasts about 10-14 s. When a greeting happens it is now a small
  moment the eye can settle on instead of a blink.
- `FarmDioramaSimulationTest.socialBeatHappensAndLingers` was strengthened: it detects the pair on the
  first celebration step and then counts the held beats, requiring at least 4.5 s of celebration.
- No other behaviour, gait or collision code changed.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1016 tests, 0 failures, 0 errors, 3 skipped; `FarmDioramaSimulationTest` (9) and `ResidentPoseAnimationTest` (17) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here; greeting frequency and its longer hold need a device look |

## Farm walk cadence calibration (2026-11-07, same tree, after device feedback)

> **Historical.** Superseded by "Farm walking restored to the pre-liveliness rhythm" above: the
> speed-derived cadence and step bob described here were removed when the old stride rhythm returned.

Owner report after the gait pass: the walk "still feels like it doesn't follow the walking animation
pace", and reads too fast.

- **Measured the artwork instead of estimating.** Across the extracted sprite sets (1137 sets) the
  horizontal distance between the two drawn feet at the widest walk frame has a median of **0.427 body
  heights** (0.412 for the run pair). The previous constant was 0.38, and on top of that the stroll
  speed (0.18–0.23 world units/s) put the legs at roughly 2.5 strides a second — noticeably faster than
  the drawn stride implies.
- **Recalibrated.** `WALK_STRIDE_BODY_HEIGHTS` is now 0.43 (the measured stance) and
  `RUN_STRIDE_BODY_HEIGHTS` 0.60, so the cadence is solved from the distance the artwork actually
  depicts. The stroll was slowed to **0.13–0.17 units/s** (jog 0.30–0.38): at the typical 0.15 units/s
  the walk now holds each pose for ~370–430 ms instead of ~250 ms, which is what makes the legs read in
  step with the ground instead of scurrying under a sliding body. Crossing the island takes ~9 s.
- No animation, gait-state or collision code changed; only the stride constants and the two speed
  ranges. `ResidentPoseAnimationTest`'s cadence cases were updated to the measured stride (17 tests).

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1016 tests, 0 failures, 0 errors, 3 skipped; `ResidentPoseAnimationTest` (17) and `FarmDioramaSimulationTest` (9) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here; the slower cadence and stride match need a device look |

## Farm pacing: probabilities instead of cycles, and no head glyphs (2026-11-07, same tree)

> **Historical numbers.** The probability table and greeting length below were superseded by the
> "Farm greetings: rarer and longer" section above (greeting 0.8%, celebration 5.5-8.5 s). The model
> and glyph removal still describe the current implementation.

Owner request: the residents were "doing a lil too much"; instead of working with cycles of what a
Digimon does and limits on how many may do something at the same time, pace them by **probability of
doing a new thing**, where most of the time they just walk or stand; and remove the glyphs that appear
above a Digimon's head ("♪", "Zzz") completely.

**Change:**

- **Pacing model.** The simulation no longer keeps global budgets, a social cool-down, or a
  per-activity director. Every resident that finishes a beat rolls once, independently:
  `46%` stand (2.5–8 s), `42%` walk, `3%` jog, `4%` train, `2%` happy beat, `1%` nap (15–30 s), and the
  remaining `2%` is a greeting that walks to a standing neighbour, faces them, celebrates briefly and
  parts — falling back to a walk when nobody is in reach. No cap stops several residents from walking,
  training or napping at the same time; the calm comes from the odds (≈88% of rolls are standing or
  walking) rather than from counting actors. Arriving at a spot no longer forces a held idle beat: the
  resident rolls for its next beat in the same simulation step, so the island never dead-stops.
- **Head glyphs removed.** `FarmDioramaEmotes.kt` is deleted, the `FarmDioramaEmotes` mount is gone from
  `FarmHomeWorld`, and `FarmDioramaFrame` no longer carries an interaction id (`FarmDioramaInteraction`
  is removed). Nothing is drawn above a resident's head any more; sleeping and social beats are read
  from the poses alone (held sleep pose with its slow breath; two residents facing each other with the
  happy pose).
- `FarmDioramaSimulationTest` was rewritten for the new contract: most actor-beats are standing or
  walking, several residents can move at once (proving the caps are gone), sleep stays occasional,
  greetings still produce a face-to-face happy pair, sleepers stay planted, and no one ever overlaps or
  teleports.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1016 tests, 0 failures, 0 errors, 3 skipped; `FarmDioramaSimulationTest` (9 tests) and `ResidentPoseAnimationTest` (17) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here: the softened pacing and the clean heads still need a device look |

Boundaries: only the farm's pacing model and its overlay changed; gait/cadence, collision, the roster
and every other surface are untouched.

## Farm gait feel: sliding walk and strobing run (2026-11-07, same tree)

> **Historical.** Superseded by "Farm walking restored to the pre-liveliness rhythm" above; the
> speed-derived cadence, beat latch and step bob from this pass no longer exist.

Owner report after the resident-life pass: the way the Digimon walk "looks worse than before — they
look like they are sliding on the ground", and when running their sprites "change crazy fast".

**Diagnosis** (renderer code + the actual extracted artwork):

- **Run strobe.** Poses are pushed at 10 Hz, but run steps interpolated over 90 ms. For part of every
  cycle the movement segment was already finished, `updateResidentMotion` reported `walking = false`
  for a frame, the activity token `RUN` fell through to the idle family, and the pose bound
  `run → idle → run` ten times a second. Walk steps (140 ms) only masked the same issue because their
  window outlived the 100 ms beat.
- **Sliding walk.** The frame cadence was fixed (260 ms) for every resident while each travels at its
  own speed, and nothing tied "distance covered" to "frame shown", so the planted foot could never
  keep pace with the ground. The artwork is not the problem: across the extracted sets the walk pairs
  are real two-frame leg swaps (mean 28.6% of pixels differ; only 10.6% of sets are identical walk
  frames).

**Fix:**

- Every gait step now interpolates in 105 ms — slightly longer than the 100 ms beat — so a segment can
  never run dry between poses; the walk/run pose is additionally latched across beat gaps for 260 ms.
- The renderer solves the cadence from the resident's measured ground speed and the artwork's authored
  stride (`frameMs = stride / speed`): 0.38 body heights per walk frame, 0.62 per run frame, clamped to
  170–420 ms (walk) and 130–300 ms (run). A slow resident visibly takes slower steps than a fast one,
  and foot pace matches the ground by construction instead of being a fixed cycle over variable travel.
- A small phase-locked step bob (3.5% of body height walking, 7% running, zero at each footfall) gives
  the sprite a weighted step instead of a glide.
- Walk/jog speeds were recalibrated around the previous farm's ~0.19 units/s stroll with a jog about
  twice as fast (jog 0.38–0.48 units/s, steps 105 ms). `ResidentPoseAnimationTest` gained 3 cases for
  the stride/cadence math.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1015 tests, 0 failures, 0 errors, 3 skipped; `ResidentPoseAnimationTest` (17, including the new stride/cadence cases) and `FarmDioramaSimulationTest` (8) pass |
| `:app:lint` | Not re-run (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD here. The report came from real play, so the corrected gait cadence, the step bob and the absence of run strobing still need a device look |

Boundaries: only the farm's renderer policy/timing and the simulation's gait speeds changed; collision,
social beats, sleep, emotes and every other surface are untouched.

## Farm resident life: full pose set, gaits, sleep, social beats and collision (2026-11-06, same tree)

> **Historical pass.** Its budgeted pacing, head glyphs and "at most one sleeper/pair" rules were
> superseded by the two 2026-11-07 sections above (probability pacing and glyph removal). The artwork,
> collision, roster and reduced-motion details below still describe the current implementation.

Owner request: make the Home Digifarm lively with the assets a VB Digimon actually has — idle,
walking when slow, running when faster, occasional sleep, interactions and the happy animation — without
the island becoming chaos, and stop residents passing through each other.

- **Artwork.** The farm used to draw four frames per resident: `CharacterWithSprites` only carries
  idle/idle2/run1/run2, so walk was faked with run frames and happy/sleep with idle. `FarmRosterArtwork.kt`
  now resolves the complete twelve-pose `Sprite` row — VB through the existing `DisplaySprite` view
  (owner appearance preference and idle fallbacks included) via `SpriteDao.getForCharacter(character.charId)`,
  Dot through `BundledDotSpriteLibrary.sprite(setId)` — while keeping selection two-phase: cheap
  availability probes decide the artwork filter, cap and hourly shuffle first, and the full frame rows
  are read only for the ≤12 selected residents. The old four-frame mapping survives as the fallback for
  partial rows, so a resident never disappears for missing data. `FarmArtwork` carries idle1/2,
  walk1/2, run1/2, train1/2, happy and sleep plus `contentKey` (the model-rebuild key), `bodyAspect`
  and `bodyRadius`.
- **Simulation.** `FarmDioramaSimulation` (pure Kotlin, `farm/diorama/`) replaces the twelve independent
  actor coroutines with one deterministic cast stepped at 10 Hz by `FarmHomeWorld`: staged opening
  tableau, per-resident temperament, walk and jog gaits, training, rare sleep, solo happy beats, and a
  social state machine (approach → face → celebrate → part). Calm budgets: movers ≤ min(3, cast/4), one
  jogger, one sleeper, one pair, and no solo celebration during a pair. Time is clamped per step so a
  paused host cannot teleport anyone, and the same seed replays the same show. The driver stops while
  `isActive` is false (place open or partner sheet expanded) and resumes from the last frame.
- **Collision.** Residents are circles of artwork half-width plus a 12 mm margin, resolved after every
  step: the solver pushes movers only (a resident that has just arrived counts as a mover for that
  step), never plants drift, clamps to the island and runs up to eight relaxation passes. The renderer
  adds `separateOverlappingResidents()`, a movers-only pass over the interpolated visual positions, so
  even mid-step interpolation can never draw two sprites inside each other.
- **Renderer.** `ResidentPoseAnimation` (pure, unit-tested) picks the pose family and cadence: run/run2
  at 160 ms while jogging (falling back to walk when the artwork has no run frames), walk/walk2 at
  260 ms, held sleep with a feet-planted breath, train/train2 at 360 ms, held happy with a decaying hop,
  idle/idle2 at 900 ms. Standing social beats turn a resident toward its partner at 4.5 rad/s inside a
  dead band; reduced motion freezes residents on idle art with no cycling or gestures.
- **Emotes.** `FarmDioramaEmotes` draws at most two glyphs, projected with the scene camera and refreshed
  per frame only while visible: a quiet "Zzz" over the single sleeper (3 dp bob) and one "♪" over the
  celebrating pair (300 ms fade). Decorative semantics only, no pointer input, and the simulation frame is
  read inside that layer so the farm surface never recomposes per beat.
- **Docs:** `DESIGN.md` **Farm home shell** → Content rule and Frame budget.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 1012 tests, 0 failures, 0 errors, 3 skipped; new `FarmDioramaSimulationTest` (8 tests), `ResidentPoseAnimationTest` (14) and `FarmArtworkTest` (5) pass |
| `:app:lint` | Not re-run for this pass (no new Android APIs); previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native captures | **Pending.** No ADB device/AVD: gait cadence, the calm pacing across a long idle session, on-screen collision and the two emotes are not captured yet |

Boundaries: battle, Radar and AR surfaces are untouched; the legacy DB-driven `DigifarmScreen` and its
stored data are untouched; the farm stays presentation-only (nothing persisted).

## Home ↔ place transitions (2026-11-06, uncommitted tree)

This pass gives the farm home an authored transition to and from every place instead of a plain route
crossfade. The farm is a shell-level world (not a `NavHost` destination), so the motion is split: the
route transition animates the pages, and the shell animates the world layer underneath them. Evidence
for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 982 tests, 0 failures, 0 errors, 3 skipped; the new `NavigationMotionTest` (3 tests) passes |
| `:app:lint` | 0 errors, 474 warnings, 40 hints (warnings never gate; no issue in `AppNavigation.kt`) |
| Native capture | **Pending.** No ADB device and no AVD were available in this pass (`adb devices` empty), so the transition feel, reduced motion and a screen recording of Home → place → Home are not captured yet |

### Motion contract

- **Home → place:** the outgoing chrome fades in 150 ms; the arriving place fades in over 220 ms while
  lifting ~2% of the window height over 320 ms; the farm layer keeps full opacity and pushes in
  1 → 1.05 over 280 ms, then the surface is dropped - after the place is opaque, so the world is never
  cut out from under a half-transparent screen. The farm's frame loop pauses as soon as Home is left,
  so no second Filament scene renders during the change.
- **Place → Home:** the place sinks ~2% while dissolving (fade 220 ms / travel 240 ms); the farm
  settles 1.05 → 1 over 360 ms; Home's chrome enters with a 40 ms delay so the world lands first.
- **Place → place:** unchanged short shared-axis horizontal slide (280 ms enter / 220 ms exit); routes
  outside the primary row keep the 200/140 ms crossfade.
- **Reduced motion** (system animator scale off): all of the above become ~90 ms crossfades with no
  scale and no travel; the world still holds through the fade instead of cutting.
- **Where:** `AppNavigation.kt` (`routeEnterMotion` / `routeExitMotion`, `FARM_HANDOFF_MS`,
  `FARM_DEPTH`, the farm layer's `graphicsLayer`); design rules in `DESIGN.md` **Farm home shell** →
  Transitions and **Navigation** → Motion.
- **Boundaries:** the AR stage, the partner sheet's own detents, the Radar place and the battle screens
  are untouched; this pass only decides how the shell swaps them.

## Radar biome readout (2026-11-06, same tree)

Owner report: no biome has appeared on the Radar in a long time. Audit result — the system was working,
the display was not:

- **Working:** `WorldRepository.currentBiome` is still resolved by `OpenStreetMapBiomeDetector`
  (Overpass, 250 m radius, cached by ~220 m cell for 15 min; failures cached 30 s) inside
  `requestPopulationRefresh` (triggered by accepted GPS fixes after ~60 m of movement, on entry when
  spawns are empty, and on recovery), and the resolved biome still feeds `ensureSpawnsLocked`'s
  `favoredAttribute` spawn bias.
- **Broken display:** the only consumer of `currentBiome` in UI was the 10sp
  "Surroundings: …" label inside the **legacy 2D radar** branch, which is selectable only while the
  master Debug switch is on. The isometric radar chrome never showed it, so the redesign silently lost
  the readout.
- **Also weakened:** `publishSpawnBiome` ran only after the region command applied, so a rejected or
  stale refresh left the flow at `NULL` even when the lookup succeeded.

Fix: `RadarTopChrome` takes `biomeText` and renders it as the top cluster's quiet context line under
"Radar · NW", fed by the localized `ui_world_biome_label` ("Surroundings: %1$s") only while the biome
is not `NULL`; `WorldScreen` publishes the biome immediately after the lookup, before the spawn command.
The legacy 2D label is untouched.

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed (APK 21:03) |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 985 tests, 0 failures, 0 errors, 3 skipped |
| `:app:lint` | Not re-run for this pass; previous run on this tree: 0 errors, 474 warnings, 40 hints |
| On-device | **Pending.** The readout depends on a reachable Overpass endpoint and on the biome cells that map to the player's surroundings; no device/AVD is attached to confirm the line appears outdoors |

Boundary: `NULL` remains the honest fallback (unreachable/slow Overpass, unmapped areas), so the line can
legitimately stay absent; the Overpass timeout is 1.5 s with one building-footprint fallback query.

## Radar ground scroll (2026-11-06, same tree)

The partner token always sits at the plate centre, so walking was previously invisible on the isometric
radar: only the wild Digimon moved. The authored plate now slides opposite to the player's real travel,
matching the legacy 2D radar's scrolling grid.

- **Math** lives in `RadarIsometricGeometry.radarIsometricGroundPhase(anchor, player)`: the displacement
  from the first fix is returned in north-up scene metres (east = +x, north = -Z), folded by
  `foldToHalfCell` into `[-cell/2, cell/2]` with one cell = `RADAR_ISO_GRID_CELL_METERS` (60 m), so the
  finite authored floor never slides out from under the camera.
- **Rendering** (`RadarIsometricViewport`): the viewport keeps `groundPhaseMeters` from the first
  `radarPlayerPosition` fix, converts it to plate units with
  `radarIsometricPlateUnitsPerMeter(displayedVisibleRadius)` (so the phase stays correct while the plate
  re-scales for zoom), rotates it with the compass via `radarIsometricRotate`, and applies it as
  `T(offset) · R(heading) · S(scale)` to the ambient plate root. Change detection now includes the
  offset so the transform is only rewritten when something actually moved.
- **GPS-wander filter:** the raw fix is low-passed before it reaches the plate, with
  `tau = 0.35 s + 0.08 s × accuracyMeters` (a 10 m fix follows in ~1.15 s) and a hold above 30 m
  reported accuracy, so a standing player does not shake the grid; the filter state is unbounded
  (unwrapped) and only the output is folded to half a cell, so walking still reads within a step or two.
- **Ground-marking draw order:** the authored `world_grid` is a BLEND mesh, so Filament depth-sorts it
  with the translucent ground markings and the sliding plate flipped that order with walking direction
  (the interaction ring appeared under the grid). The interaction ring and metre-ring decals now set
  `RenderableManager.setPriority` to 6 and 5 (default 4, 7 renders last) so they always draw after the
  grid, and the metre-ring decal's height is derived from the plate scale (grid top + lift) instead of a
  fixed scene height, so it also clears the grid at the closest zoom.
- **Scale is honest:** the plate is the radar's linear metre reference for the metre rings, so the
  slide is 1:1 with walked distance and reads strongly when zoomed in, subtly at the widest zoom — the
  same trade-off the 2D radar has. Marker motion keeps the accepted compressed presentation curve.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed (APK 20:26) |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 985 tests, 0 failures, 0 errors, 3 skipped; both new ground-phase cases pass |
| `:app:lint` | Not re-run for this pass; previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native capture | **Pending.** No ADB device/AVD: the scroll feeling while actually walking (and how GPS jitter reads when standing) is not verified on hardware |

## Transition background and Arena lobby scroll (2026-11-06, same tree)

- **Farm background crossfade.** `AppNavigation` used to hold the farm layer fully opaque and drop it
  once the arriving place was opaque, but the destination pages are transparent over the global device
  pattern, so the farm is in fact the page background during the change and the drop read as an abrupt
  background swap at the end of the transition. `farmAlpha` now animates 1 → 0 (leaving) and 0 → 1
  (returning) over 240 ms, crossfading the farm with the device pattern in step with the page; reduced
  motion keeps the instant switch. `FARM_BACKGROUND_FADE_MS` replaces the old hold/snap constants.
- **Tamer Arena lobby bars.** `TamerArenaLobby` now collapses the search field and the
  Tamers/Tournaments/Records tab row while the opponent roster scrolls down (96 px of downward travel),
  and brings them back on any upward scroll (12 px) or at the top, using the same
  `snapshotFlow`/`AnimatedVisibility` pattern as Storage's search bar, with `motionEnabled()` deciding
  whether the expand/fade runs. Entering the team editor, a briefing or the bracket restores them.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 983 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleIntegrityCheckAndroidTest` | Passed (source set compiles) |
| `:app:lint` | Not re-run for this UI-only pass; previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native captures | **Pending.** No ADB device/AVD: neither the background crossfade in the two light palettes nor the Arena collapse-on-scroll is captured yet |

## Partner picker contrast and farm roster reuse (2026-11-06, same tree)

- **Light-palette picker fix.** `StorageCharacterPickerDialog` (the Arena "Choose partner" surface,
  also used by training preparation and the legacy farm screen), the species sprite picker and the
  species catalog editor painted `MaterialTheme.colorScheme.background`, which this theme defines as
  `Color.Transparent` so ordinary pages show the global device pattern. In a full-screen dialog that
  meant the screen underneath plus the dialog window's dim were visible, so the two light palettes read
  as a darkened page. All three now open edge-to-edge (`decorFitsSystemWindows = false`) and paint
  `DeepPurpleBg` (the palette's own opaque background), with the dialog wrapper supplying the
  system-bar/IME insets. `StorageCharacterPickerScreen`'s Scaffold no longer resolves insets itself:
  the app shell already consumes them for the routed `ChooseCharacterScreen` use, and the dialog wrapper
  pads its own window.
- **Digifarm add-member reuses that picker.** `FarmRosterSheet` still lists the members beside the
  resident-limit and artwork controls, but "Add member" now opens `StorageCharacterPickerDialog` with
  the non-member partners instead of the sheet's inline candidate list: search, filters, sort, sprite
  grid and the same 48dp tiles as Arena/Storage. Selecting adds the partner immediately, the picker
  stays open while capacity remains and closes on the last free slot. `FarmMemberOption` carries the
  stored character so the roster hands the picker real sprite data.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 983 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleIntegrityCheckAndroidTest` | Passed (source set compiles) |
| `:app:lint` | Not re-run for this UI-only pass; previous run on this tree: 0 errors, 474 warnings, 40 hints |
| Native captures | **Pending.** No ADB device/AVD: the light-palette picker page and the farm picker flow are not captured yet |

## Top-header alignment (2026-11-06, same tree)

Owner request: every screen whose header does not use the same type as Home, the Radar place and the
battle surface. Audit of every header entry point in `app/src/main` (`TopBanner` call sites, the
`TopAppBar`/`CenterAlignedTopAppBar` variants, and the clusters that pad the status bar themselves):

| Surface | Header before this pass | Resolution |
|---|---|---|
| Home (`FarmHomeScreen` → `HomeTopHud`) | floating status cluster over the farm (scrim, HUD ink, 48dp keys, hairline) | reference — unchanged |
| Radar place (`RadarPlaceScreen`) and World place (`WorldScreen`) | `RadarTopChrome`: floating cluster, same scrim/keys/hairline | reference — unchanged (WorldScreen only had a dead `TopBanner` import, removed) |
| Battle surfaces (`OfflineTrainingBattleScreen`, Arena sessions → `BattleTopCluster`) | floating cluster over the arena | reference — unchanged |
| Every other screen / dialog header | `TopBanner`: gradient shelf, centered `titleLarge`, filled purple/cyan chips, surface-to-background gradient | rebuilt as the reference type |

`TopBanner` is now the page form of the same top-cluster type: a left-aligned `titleMedium` title
carrying a single `labelMedium` context line, **bare 48dp keys** (icon plus target only — no fill, no
hairline stroke, no visible cut-corner frame; the shape only bounds the ripple, as Home's HUD keys
already did), one junction line, and a palette-resolved scrim instead of the shelf. The back key always
owns the left edge and is resolved from the explicit `onBackClick` or, when absent, from the activity
back dispatcher (so it matches the system back gesture, including screens with their own back
handling); every other action the header receives — transfer, scan data, import, scan, modify,
adventure, gear and the `trailingAction` slot — renders on the right in that order. The battle
header's left key is already its exit action; the Radar/World header's left key is already back.
Shared primitives are in `components/HudChrome.kt` (`hudTopScrim`, `hudInkShadow`/`hudInk`, `HudKey`,
`HudHairline`). The public API, labels, `importEnabled` and every semantic are unchanged, so the ~38
call sites across 34 files were aligned without edits.

Screens aligned through that single component: Adventure, Items, Storage and its character picker,
Dex (`CardSelectionScaffold`, `CardViewScreen`, `CardAdventureScreen`, `ChooseCard`), DigiLine and farm
group, Quests, Settings, Credits, Lorebook, Chat and the world chat/conversation/recruits screens,
Arena lobby (`TamerArenaLobby`), battle hub (`BattlesScreen`), training preparation, Technique loadout,
Transfer hub and the transfer/scan flows, Sprite viewer, and the bracelet-conversion, species-sprite
and species-catalog dialogs. Legacy `home/ui/HomeScreen.kt` and `DigifarmScreen.kt` are not routed but
use the same component.

Evidence for this working tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 983 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleIntegrityCheckAndroidTest` | Passed; `StorageScanCollectionTest` still finds both header keys by content description |
| `:app:lint` | Not re-run after the key/back pass (UI-only edits); the previous run on this tree reported 0 errors, 474 warnings, 40 hints |
| Native captures | **Pending.** No ADB device and no AVD in this pass, so no portrait/landscape, light-palette or font-scale captures of the aligned header exist yet |

Boundaries: the Home/Radar/Battle clusters were not refactored onto the new primitives in this pass (the
Home status cluster is unchanged and previously device-verified; the Radar and Battle clusters only had
their key frames removed, matching Home). 48dp targets, heading semantics, content descriptions and back
behaviour are preserved, but TalkBack and 1.3× font-scale acceptance for the new header are still
unrecorded. Home is the shell root and has no back destination, so its left cluster stays the farm
identity rather than a back arrow.

## Place-switch performance pass (2026-11-06, same tree)

The owner reported place switching feeling clunky and low-framerate on the SM-A346M, both before and
after the transition motion pass. This pass removes work from the switch instead of changing the
motion. **It is not measured**: no ADB device and no AVD were available (`adb devices` empty), so the
evidence is code-level plus the standing gates; the on-device procedure is in
`docs/performance/BASELINE.md` §4.8 and the result is still owed.

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 983 tests, 0 failures, 0 errors, 3 skipped; the new `RouteArrivalTest` passes |
| `:app:lint` | 0 errors, 474 warnings, 40 hints (warnings never gate; no new finding in the changed files) |
| On-device frame timing | **Not measured.** No device/AVD attached; a debug build is not representative, so compare a `-PnonDebuggable` debug build or a release build with §4.8 |

What was removed from the switch:

- **Repeated sprite decodes.** `CharacterEntry` rebuilds its bitmap whenever the idle frame swaps (the
  `remember` key changes with the frame) and on every arrival at a collection screen. Each rebuild was
  a per-pixel RGB loop plus a hardware-bitmap allocation on the main thread. `BitmapData.getBitmap()` /
  `getObscuredBitmap()` now serve a 16 MiB content-keyed `LruCache` (`utils/BitmapData.kt`), so a frame
  swap or a revisit is a cache hit.
- **Stacked entrances.** Every visible grid card ran its own 260 ms alpha+scale entrance on arrival, on
  top of the page transition. The shell now raises a process-wide, non-observable `RouteArrival` flag
  during composition of a route change and lowers it once the enter motion settles; arriving cards
  start fully visible, while items that appear later (scrolling, later pages, filters) keep the
  entrance. `DESIGN.md` **Farm home shell** → Transitions records the rule.
- **Persistent surface relayout.** The farm surface sat inside the inset-padded container, so every
  Home↔place change re-measured it and resized the `TextureView` that owns the Filament swapchain in the
  same frames as the transition. The shell is now two layers: the world keeps the window's full bounds
  for the shell's lifetime and only the `NavHost` takes the system-bar/IME insets and padding; the world
  also gets a stable modifier, so a route change no longer recomposes that subtree.

Known remaining hotspots, left alone because ranking them needs a device:

- Entering a full-bleed 3D place (`Radar`, `World`) constructs a fresh Filament `Engine` in the view
  constructor (`RadarIsometricViewport`, `Digifarm3dViewport`, `OfflineBattleScene`) and uploads the GLB
  on the main thread; moving that off the transition frame was not attempted unverified.
- The battle/Arena hub composes its whole animated surface on entry.

## Battle screen full-bleed redesign (2026-11-05, uncommitted tree)

This pass replaced the framed battle page (16dp gutter, bordered 1:1 viewport, half-screen command
deck) with one full-bleed battle surface shared by Arena/training battles and Radar encounters, and
stopped the Radar battle from reverting to the square window. A Radar-battle crash found after the
first gate is fixed in the same tree (see the crash-fix subsection below). Evidence for this working
tree only:

| Check | Result |
|---|---|
| `:app:assembleDebug` | Passed; debug APK rebuilt after the crash-fix cleanup (`app/build/outputs/apk/debug/app-debug.apk`, 2026-11-05 20:40) |
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 979 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleIntegrityCheckAndroidTest` | Passed; the androidTest source set compiles again after the stale `radarControls` parameter, and `OfflineTrainingBattleScreenTest` asserts the full-bleed invariants |
| `:app:lint` | 0 errors, 468 warnings, 38 hints (25 baseline entries are now stale/no longer found; warnings never gate) |
| Radar battle on device (crash fix) | Two consecutive Radar encounters on the SM-A346M ran the countdown → full fight → "Radar victory" (49 s and a shorter win), with one Filament engine creation per battle, the battle scene composed once, an empty `logcat -b crash` buffer and the app process alive throughout. The earlier instrumented build reproduced the native crash every time at the countdown (~3–5 s); hoisting the scene is the structural difference. |
| Arena battle on device | The earlier pass verified the full-bleed arena live on the same device (scene fill, enemy plate, HP/MP/ESP strip, projectiles); the crash fix does not touch that layout. |
| Native captures (this tree) | `.impeccable/review/battle-live-radar-fixed.png` (Radar victory state before the settled-chrome trim) and `.impeccable/review/battle-radar-second.png`. Portrait captures of the final shell are still pending. |

### Radar battle crash fix (2026-11-05, same tree)

- **Symptom.** Starting a Radar encounter battle crashed natively ~3–5 s in, at the countdown, with
  `SIGSEGV` in `libfilament-jni.so` (fault address `0x0`). Arena/training battles never crashed.
- **Diagnosis.** Temporary lifecycle logging showed the battle scene subtree being disposed and
  re-composed mid-battle and a second `scene engine created` inside one fight: Compose was replacing
  the `AndroidView` that owns the Filament engine. The native crash is the engine's component managers
  being destroyed/recreated out of order (use-after-free), not a bad asset or a decal precondition.
- **Fix.** The battle `OfflineBattleScene` was hoisted out of the radar/battle layout branch to the root
  of `WorldRadarBattleContent` (`OfflineTrainingBattleScreen.kt`), so it is composed exactly once per
  encounter; renderer-ownership changes only pause its frame loop. The settled-battle chrome trim
  (result panel owns the surface, live top cluster / command band / projected HUD step aside) landed in
  the same change; see `DESIGN.md` **Battle screen** → States.
- **Cleanup and re-verification.** The diagnostic logs, engine stack traces, `debugTag` parameter,
  deferred engine creation and the 250 ms handoff delay were removed before the final rebuild. The
  device pass above ran on the instrumented build; the cleaned APK (20:40) is
  compile/unit-test/lint verified, and a final on-device pass on that exact APK is pending because the
  SM-A346M's wireless ADB dropped and could not be reconnected in this session.

Boundaries and remaining acceptance work:

- The AR stage deliberately keeps the established square scanning/battle layout and its transparency
  contract; it is the legacy exception, not part of the new shell. The Blast overlay it shares was
  dimmed for legibility.
- Arena lobby/briefing/team setup, the Battle hub, legacy online PvP and the Radar encounter sheet are
  untouched in this pass.
- The finisher movie/camera sequence is kept as authored; only its captions and surrounding chrome were
  re-fitted.
- No TalkBack or large-font (1.3×) capture is recorded yet; the new chrome keeps 48dp targets and the
  existing accessibility actions.

## Radar place redesign (2026-11-04, uncommitted tree)

This pass replaced the Radar place's 2D/first-person split with a full-bleed isometric radar world and
fixed the encounter-asset starvation that made nearby Digimon slow or absent. Evidence for this
working tree only:

| Check | Result |
|---|---|
| JVM unit tests (`:app:testIntegrityCheckUnitTest`) | 984 tests, 0 failures, 0 errors, 3 skipped |
| `:app:assembleDebug` | Passed; installed on Samsung SM-A346M (wireless ADB), API 35 |
| Radar flow on device | Home → Radar opens the isometric plate with the partner token, wild cardboard sprites and the digivice-bezel compass (tick ring, cardinal beacons, nearest-Digimon needle; no distance rings); zoom, vertical tilt, labels, options sheet and the Debug-only legacy views exercised |
| Encounter loading | Spawn assets appear immediately after the remembered Room flow emits (empty-list restarts removed); shared GLB cache + parallel builders + nearest-first uploads cover a 16–20 Digimon roster; expired-region recovery repopulates sprite rows on entry and stale placeholder encounters are removed |
| Screen captures (this tree) | `.impeccable/review/radar-18-final100.png`, `radar-19-spread.png`, `radar-17-wide.png`, `radar-20-options.png`, `radar-21-labels.png` (light palette); `radar-11-ready.png` (dark palette). The label-collision pass that followed these captures is compile/unit-test verified only. |
| `:app:lint` | 0 errors, 471 warnings, 38 hints (32 warnings and 5 hints filtered by `lint-baseline.xml`). A first lint run crashed inside the K2 UAST resolver before reporting; the clean rerun above supersedes it. |

Boundaries and remaining acceptance work:

- The battle handoff and AR stage were not re-exercised on device in this pass. Their code paths are
  unchanged except for the `WorldRadarBattleContent` full-bleed radar branch, which keeps the
  established battle/AR layout.
- The legacy 2D and first-person renderers are untouched and remain reachable only with the master
  Debug switch on (Radar options → Legacy view). A native capture of the legacy 2D view is pending.
- No TalkBack or large-font (1.3×) capture of the new radar was made in this pass; the chrome keeps
  48dp targets and the existing semantics contract.
- Sprite settle time was measured during development (first sprite ~1 s, a 20-Digimon roster ~3.7 s
  on a cold process, including plate load and GLB build) with temporary logging that has been
  removed; no benchmark entry was recorded.
- **Wild↔wild social pause (2026-11-04).** `WildSocialInteractionGate` is off, so autonomous wild
  pair chat/battles are not created, activated or shown; the retained planner/dialogue/combat code
  stays compiled. Covered by `WildSocialInteractionGateTest` (family matching, player encounters
  unaffected, re-enable path). Wild → player and player-directed encounters were not behaviourally
  re-exercised on device after this change; the gate keeps them out of scope by construction.
- **Radar cardboard orientation (2026-11-04).** Travel is derived from real-world positions and the
  display orientation from a pure helper (`radarCardboardOrientation`), so compass/zoom motion cannot
  flip sprites. Covered by three JVM tests: standing Digimon ignore compass motion, the four travel
  directions match the farm' camera-assisted profile/mirror behaviour, and a turning compass rotates
  the sprite with the plate without chatter at the mirror latch.

## Current verified state (2026-10-31, uncommitted tree)

| Check | Result |
|---|---|
| JVM unit tests (all modules) | 1,157 tests, 0 failures, 0 errors, 7 skipped |
| Debug + R8 release assembly | Passed (minified APK 149,019,234 bytes / 142.12 MiB, regenerated profiles packaged) |
| `:app:lint` | 0 errors (422 warnings, 38 hints; warnings never gate) |
| Connected instrumentation (`:app:connectedIntegrityCheckAndroidTest`) | 281/281 passed on Samsung SM-A346M, API 35 |
| Install + cold launch smoke | Debug and debug-signed R8 release installed and launched; no crash-buffer entries |
| Coverage (named classes) | 81.0–95.8% line coverage; see [BASELINE.md](docs/performance/BASELINE.md) |
| Benchmarks (device-gated) | Cold-start TTID median 875 ms; battle frame CPU P95 13.3 ms; farm 3-second window P95 29.8 ms (includes loading) |

These are current for this working tree only. Do not quote older suite counts from the
archive or [docs/refactor/TARGETS.md](docs/refactor/TARGETS.md) as current.

## Commands (Windows PowerShell, repository root)

```powershell
# JVM/library module suites
.\gradlew.bat :game-save-core:test :companion-protocol:test :pc-save-companion:test `
    :hardware-dim:test :hardware:testDebugUnitTest

# App JVM unit tests (integrityCheck test build type)
.\gradlew.bat :app:testIntegrityCheckUnitTest

# Static analysis (blocking gate; must stay at zero errors)
.\gradlew.bat :app:lint

# Builds
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease        # R8; mapping at app/build/outputs/mapping/release/

# Device instrumentation (device required)
.\gradlew.bat :app:connectedIntegrityCheckAndroidTest

# Baseline-profile regeneration (device required; startup + best-effort journeys)
.\gradlew.bat :app:generateBaselineProfile

# Macrobenchmarks (device required; not part of CI). These live in :benchmark, a separate
# test module, so :app:generateBaselineProfile only runs the profile generator.
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tamerslab.benchmark.StartupBenchmark"
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tamerslab.benchmark.FrameTimingBenchmark"

# Optional optimization instrumentation (no behaviour change by default)
.\gradlew.bat :app:assembleRelease -PcomposeCompilerMetrics   # Compose compiler reports
.\gradlew.bat :app:assembleRelease -PabiSplits                 # per-ABI APKs for size checks
.\gradlew.bat :app:assembleRelease -PbattleSceneChannel=true   # battle channel A/B build

# Coverage (opt-in; no threshold is enforced yet)
.\gradlew.bat :app:jacocoTestReport :game-save-core:jacocoTestReport
```

The device-visible tasks require an authorized ADB device. On this machine the SDK is
`%LOCALAPPDATA%\Android\Sdk` and ADB is not on `PATH`; wireless debugging can drop
during long builds — verify with `adb devices` before a device task and reconnect with
`adb connect`/`adb mdns services` if it disappeared.

## Suite layout

| Area | Location | Notes |
|---|---|---|
| Save parsing/conversion | `game-save-core/src/test` | pure JVM; golden/characterization tests for DS record codecs |
| DIM/BEM card parsing | `hardware-dim/src/test` | pure JVM; synthetic R5G6B5/checksum fixtures only |
| NFC protocol | `hardware/src/test` | JVM with a fake `NfcTransport` |
| Companion protocol | `companion-protocol/src/test` | pure JVM JSON protocol |
| PC companion | `pc-save-companion/src/test` | pure JVM |
| App JVM | `app/src/test/java/com/tamerslab` | use cases, Koin graph, architecture boundaries, state/navigation contracts, battle/transfer/save logic |
| Device/instrumentation | `app/src/androidTest/java/com/tamerslab` | Room persistence + migration checks, Compose UI smoke tests, battle/radar/transfer/quest flows |

Architecture boundaries are enforced by
`app/src/test/java/com/tamerslab/architecture/ArchitectureBoundaryTest.kt` (domain APIs
must stay Android/Room-free, library modules must not import the app shell, repository
interfaces must not import Room). Koin graph verification is in
`app/src/test/java/com/tamerslab/di/KoinModuleGraphTest.kt`.

## Fixtures and private data

- Never commit real game saves, ROMs, retail DIM/BEM card dumps, pairing links, tokens,
  `secrets.pb`, keystores, or mapping files with private symbols.
- Use synthetic fixtures. Licensed local card fixtures are read only through the
  `VBHELPER_TEST_DIM` / `VBHELPER_TEST_BEM` environment variables; tests skip when they
  are absent.
- The archived regression log documents the provenance of earlier fixtures; the policy
  itself is restated in [CONTRIBUTING.md](CONTRIBUTING.md#secrets-fixtures-and-private-data).

## Performance and benchmark tests

Performance is a separate manual gate: budgets, procedures, and recorded
measurements/status are in [docs/performance/BASELINE.md](docs/performance/BASELINE.md).
The `baselineprofile` module contains the device-gated baseline-profile generator and
the macrobenchmark harness; generation is not a unit test and must not run in CI without
a device. The battle channel optimization
(`BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED`) stays disabled until a measured Perfetto
run justifies it.

## CI gates

[.github/workflows/ci.yml](.github/workflows/ci.yml) runs module unit tests,
`:app:assembleDebug`, `:app:assembleRelease`, and `:app:lint` as required checks.
`:app:jacocoTestReport` is advisory and no coverage threshold exists yet. CI has no
Android device; instrumentation and benchmark runs are a documented manual gate.

## Adding tests

- Every behavior change/refactor lands with its own tests; do not rely on the historical
  log as a regression suite.
- Characterization/golden values are behavior contracts: change one only for an
  intentional behavior change and explain why in the PR.
- Keep JVM tests deterministic (inject clocks, dispatchers, seeds).
- Room schema changes need a version bump, a `MigrationTestHelper` test, and a new JSON
  under `app/schemas/com.tamerslab.data.database.AppDatabase/`; never use destructive
  migrations.
- See [CONTRIBUTING.md](CONTRIBUTING.md) for PR expectations and device-gated checks.

## DigiLine / negotiation UX acceptance (manual, device-gated)

No device or emulator run is recorded for this increment; compilation and the JVM suites
are green, which is not native approval. Walk this list on a device before shipping:

- **Owned DigiLine chat:** the question/quiz scene card announces its heading and a polite
  live region; answer reactions show a Continue card before the transcript updates.
- **Wild contact (joined):** persuasion scenes show stage and tier; the trust bar and old
  LLM messages are absent unless the master Debug switch and the legacy LLM switch are on.
- **Radar overworld negotiation:** the camera eases onto the target, the compass heading
  freezes and the Digimon faces the player; the card shows entry/resume context, phase,
  tier, demand and the last spoken line above the current node. **Leave for now** returns
  to the world and reopens with the resume line; system Back leaves the conversation
  before it leaves the Radar destination.
- **Legacy isolation:** with Debug + Legacy LLM chat off, no legacy transcript row, trust
  reading or DigiLine preview remains; switching both on restores them for comparison.
- **TalkBack:** traverse header → card body → options → leave; scene changes announce
  once without stealing focus, and no duplicate reading comes from the quote line.
- **Large font (1.3× and 2×):** the scene card scrolls internally, options keep 48dp
  targets, and the Digimon stays visible above the card.
- **Landscape:** the card stays inside the window and does not cover the conversation
  subject.

