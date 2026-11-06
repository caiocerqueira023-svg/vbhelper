# Battle screen redesign — plan

Status: **implemented in this working tree** (2026-11-05); final build/test gate results are recorded
in `TESTING.md` (debug build passed, 979 JVM tests 0 failures/0 errors/3 skipped, androidTest source set
compiles, lint 0 errors; native captures are pending because the connected SM-A346M dropped off ADB).
A post-implementation crash fix landed the same day (Radar battles crashed at the countdown): the battle
scene is now hoisted out of the radar/battle layout branch so Compose cannot recreate the Filament
engine mid-fight; two consecutive Radar battles ran to "Radar victory" on the SM-A346M with one engine
creation each and no native crash. The instrumented diagnostics used to find it were removed before the
final rebuild. The settled-battle chrome trim (result panel owns the surface) landed with that cleanup.
The six phases below were executed in one pass as requested (no per-phase build gate), and the design
documentation was updated at the end: `DESIGN.md` **Battle screen**, `CHANGELOG.md`, `TESTING.md`, and
`.impeccable/surfaces/ui-offline-offlinetrainingbattlescreen-kt-748dab41.md`.
Impeccable mode: **Operate**, code-led (native Android, Compose + Filament).
Precedent: the Radar place redesign (2026-11-04) — same process, same finish bar.

## 1. Scope

**In scope**
- The in-battle surface shared by **Arena fights** (exhibition + cups) and **Radar encounters**
  (`OfflineTrainingBattleScreen`, and the Radar branch `WorldRadarBattleContent`):
  scene framing, top chrome, combatant HUD, command deck, pause/exit, blast timing,
  finisher captions, countdown, load/transform, result, error/retry.
- Both routes must end up on the **same full-bleed shell**; the Radar battle keeps the
  Radar world on screen through the encounter → battle → radar round trip.

**Out of scope (confirmed / proposed)**
- **AR battle stage**: untouched. AR scanning and AR battle keep today's layout and
  transparency contract (the AR branch is isolated, not restyled).
- Arena lobby / team setup / briefing (`TamerArenaLobby`, `ArenaBriefing`) — not redesigned
  in this pass. The "Challenge" handoff into the new battle shell is reviewed, not rebuilt.
- Battle hub / training prep / legacy online PvP (`BattlesScreen` routes, `BattleScreen`) —
  untouched.
- Radar encounter sheet (`WorldEncounterActionSheet`) — untouched.
- Finisher **movie/camera sequence** — kept as authored; only its captions and surrounding
  chrome are re-fitted. Reworking the sequence is a separate pass if wanted.

## 2. What exists today (evidence)

- **A page, not a world.** Battle is a `Column` with 16dp gutters and 4dp vertical page padding
  (`OfflineTrainingBattleScreen.kt:434-435`); the arena is a bordered 1:1 box
  (`:512-517`) and the Radar battle reverts to the same square box after arriving from a
  full-bleed world (`:1193-1210`; only `fullBleedRadar && !battleActive` is full-bleed, `:1189`).
- **Half the screen is the deck.** Portrait splits the height: deck = `max(available/2, 248dp)`,
  arena = the rest (`calculateBattlePortraitPanels`, `:168-197`). Landscape uses a 44% side
  panel (`:758-771`); the Radar battle has **no landscape layout at all**.
- **No identity in the fight.** The title/format/difficulty bar is hidden for the whole live
  fight (`:433, :438-506`). Enemy HP is a 66×9dp projected bar with no name (`:1947-1977`);
  the partner HUD is a 184dp unlabeled HP/MP/ESP stack (`:1979-2031`). In 2v2 you cannot tell
  whose bar is whose.
- **Deck is a flat slab.** Header with CP gauge + Pause + "Tactics ON/OFF" toggles
  (`:2033-2104`), then nine equal-weight Material buttons in a 3×3 grid (`:2106-2188`);
  menu-openers and instant orders look identical. One ellipsized feedback line, absent in
  landscape (`:739-751`, `:464-465`).
- **States live inside the viewport.** Countdown/loading/result sit as centered cards inside the
  1:1 box (`:624-683`, `:1593-1604`); the blast-timing overlay is a full-screen invisible tap
  target with an unannounced gauge (`:2224-2392`).
- **Duplication.** Two ~700-line parallel copies of the state machine, blast overlay, dialogs,
  HP overlays and result panel (`:199-916` vs `:923-1463`); two result panels; a dead
  `BattleFinisherStatusStrip` (`BattleFinisherOverlay.kt:117-167`).
- **Off the design system.** Zero `VitalButton`/`CyberPanel`/`cyberFrame`; Material
  `Button`/`OutlinedButton`/`AlertDialog`; ad-hoc corner sizes 5/6/8/9/10dp; raw 10/18/21sp;
  battle-only palette; mojibake arrow `"â†’"` in the blast/finisher overlay (`:613, :1535`).
- **Constraints that bind the redesign.** Session API fixed (`OfflineBattleSessionViewModel`:
  `issueOrder`, `setPaused(reason)`, `abandon`, `finishSession`, `retry`; pause keys are
  load-bearing). Every readout is snapshot-driven. One Filament engine, scene never remounted
  mid-session, AR transparency contract (`:1216-1220`), reduced-motion gates, accessibility
  semantics, testTags and layout invariants asserted by instrumentation tests.
- **Known broken guard.** `OfflineTrainingBattleScreenTest.kt:96-98` passes a `radarControls`
  parameter that no longer exists; the androidTest source set does not compile today.

## 3. Chosen direction (owner-pinned)

**The fight is the world, not a page about it.** One full-bleed arena; battle chrome floats
over it in the Radar place's vocabulary (top cluster + bottom band + scrims + HUD ink),
never a bordered widget inside page gutters. The Radar battle keeps the Radar world on screen:
encounter → arena → radar is one continuous full-bleed surface. AR stays as it is.

**Memorable moment:** the encounter sheet closes and the Radar world is suddenly a battle
arena — same full-bleed frame, chrome swapping in — instead of dropping into a square window.

## 4. First viewport (intent, not CSS)

**Portrait phone**
- Scene: the arena (Colosseum or Radar arena GLB) edge-to-edge under the system bars, with a
  soft top scrim and a bottom scrim only where chrome sits; a light vignette blends the plate
  edges, as in Radar. No page padding, no border, no panel around the world.
- Top cluster (single top scrim, no shelf):
  - left: 48dp back/exit cut-corner key;
  - beside it: matchup "AGUMON vs GABUMON" in HUD ink (Oxanium, primary ink, soft shadow),
    one quiet line under it — "Cup · Quarter-final · Hero" or "Wild encounter · Champion";
    while paused the quiet line becomes the pause reason in cyan;
  - right: 48dp pause key; the Debug AI dialog key appears here only with Debug on.
- Combatant HUD:
  - each opposing Digimon carries a compact projected plate above it (name + HP), enemy red,
    cyan hairline on the selected target, collision-aware so 2v2 and the top cluster never overlap;
  - the selected partner is a floating vitals strip bottom-left above the band: name + HP/MP/ESP
    with labels and readable values; in 2v2 an ally chip row selects which partner the deck orders.
  - tapping a fighter in the scene still selects it (existing `onFighterTapped`).
- Bottom: one full-width command band over its own scrim, navigation-bar inset respected:
  - CP instrument (label + value + thin cyan gauge) and the selected partner's name on one line;
  - nine cut-corner 48dp keys grouped men us / orders / movement (Techniques primary fill,
    menu-openers carry a stack/chevron glyph, movement keys quiet), disabled states legible;
  - one quiet event/feedback line above the keys, polite live region, not clipped in landscape.
- No viewport frame, no "Commands" shelf, no permanent toggle rows.

**Landscape / wide**: same full-bleed scene; top cluster unchanged; command band becomes two
rows of keys across the bottom (5 + 4) keeping 48dp; partner vitals anchored bottom-left above
the band. No 44% side panel, no square arena.

**Radar battle**: same shell. `fullBleedRadar` becomes unconditional — the square/column
special case is deleted; battle chrome crossfades over the Radar world; exit/result returns to
the radar place. AR branch (`arStage != null`) keeps the current scanning/battle layout verbatim.

## 5. Interaction and states

- **Countdown**: scene-first — the arena is visible, "GET READY" and the number in HUD ink over a
  small local scrim; static under reduced motion.
- **Loading / transforming**: one quiet status line in the bottom band + hairline progress
  (Radar keeps its world visible instead of a centered card); first-load may keep a compact
  center indicator if the scene is black.
- **Pause**: full scrim + square technical panel (cyberFrame) with the pause reason, Resume and
  Exit; Back opens this panel. "Tactics pause" (auto-pause when a tactical menu opens) moves
  from a permanent deck toggle into this panel as a switch (default on, behavior unchanged).
- **Exit**: native confirmation dialog, destructive styling; Back from pause cancels.
- **Blast timing**: dim scrim, centered gauge + instruction, announced to TalkBack; the tap target
  is the gauge region, not the whole screen; the timing minigame keeps ignoring reduced motion.
- **Finisher**: commands stay visible but inert and pause/exit stay reachable (existing contract);
  captions re-fitted to the new top/bottom chrome. No movie rework.
- **Result**: floats over the still-visible arena as a technical panel (training uses the
  restyled `BattleResultPanel`; Arena keeps its injected `resultContent = ArenaResult`; Radar
  keeps `rewardContent`). Scrollable, 48dp actions, never squeezed into a 44% box.
- **Error / retry**: one line + recovery key in the band, Debug details only under Debug.
- **Battle log**: the single feedback line remains the event surface; it must not disappear in
  landscape.

## 6. Execution phases

Each phase ends at a gate: `:app:assembleDebug` + `:app:testIntegrityCheckUnitTest` green,
and a device capture reviewed before the next phase. No phase ships without its capture.

### Phase 0 — Brief + baseline (small)
- Persist the surface brief (`.impeccable/surfaces/`) with the direction contract and FINISH line.
- Capture the incumbent on Samsung SM-A346M: Arena portrait (live, finisher, result, pause),
  Radar battle portrait, training landscape, one light palette, font scale 1.3.
  Files → `.impeccable/review/battle-before-*.png`.
- Repair the non-compiling androidTest file as a baseline (or annotate it) so later gates mean
  something.

### Phase 1 — Full-bleed shell
- Replace the root `Column` + 16dp page padding + bordered viewport in both routes with one
  `Box(fillMaxSize)` battle shell; scene `fillMaxSize`; chrome consumes insets only.
- New top cluster (back/exit, matchup + context line, pause, Debug key) replaces the bespoke
  title bar; delete the split-height layout (`calculateBattlePortraitPanels`, arena/deck heights)
  and the Radar square-viewport path.
- Keep the deck visible but temporarily re-hosted in a bottom band so the shell can be judged
  before restyling; landscape gets the same shell.
- AR branch isolated and unchanged; `fullBleedRadar` special case removed.
- Retire the old layout helper + `BattlePortraitLayoutModelTest`; rewrite
  `OfflineTrainingBattleScreenTest` invariants (scene fills window, 48dp keys, 9 slots, band
  above nav inset, finisher keeps arena + all command bounds stable).
- Gate: build/tests + arena and radar portrait captures.

### Phase 2 — Combatant HUD
- Enemy plates: name + HP, selected-target cyan hairline, collision-aware placement against the
  top cluster and each other (keep projection math; re-derive `pauseStatusBottom` for full-bleed).
- Partner vitals strip + ally selection chips (2v2), readable values, full semantics.
- Gate: build/tests + live 1v1 and 2v2 captures (portrait + landscape).

### Phase 3 — Command band
- Restructure the nine slots into intent groups (menus / orders / movement); CP instrument;
  pause moved to the top cluster; tactics toggle moves into the pause panel; feedback line
  survives landscape.
- Shared band composable replaces both duplicated decks; 48dp targets, font-scale-safe labels.
- Gate: build/tests + live-fight capture with keys pressed/disabled.

### Phase 4 — States, moments, Radar transition
- Countdown, loading/transforming, pause panel, exit dialog, blast overlay, result placement,
  error/retry, finisher captions.
- Radar: continuous full-bleed transition into and out of battle (crossfade, chrome swap);
  verify no flash of framed layout; AR untouched.
- Gate: captures of countdown, blast, finisher, result, pause, radar transition.

### Phase 5 — Motion and adaptation
- Entry/exit motion for the battle shell, HP/damage cues, key feedback; reduced motion honored
  everywhere except the blast input minigame.
- Portrait, landscape (including the Radar battle's first landscape path), wide ≥480dp,
  font scale 1.3, dark palette + one light palette (VB Arena / Digimon.net).
- Gate: capture matrix from Phase 0 re-shot on the new build.

### Phase 6 — Cleanup, docs, finish
- Extract remaining shared composables, delete the second ~700-line copy, remove the dead
  `BattleFinisherStatusStrip`, fix the `"â†’"` mojibake, dedupe test tags.
- Update instrumentation tests (compile fix + new invariants) and add JVM tests for any new
  pure layout logic (band insets, minimum key sizing, plate collision).
- DESIGN.md: replace the battle fragments with a **Battle screen** section (shell, top cluster,
  combatant HUD, command band, states, finisher chrome, landscape, Radar transition).
- CHANGELOG entry, TESTING.md evidence row + boundaries, surface brief FINISH discharged.
- Spawn the finish reviewer against the captured matrix; fix material findings; final capture for
  the record.

## 7. Verification plan

| Gate | Check |
|---|---|
| Build | `.\gradlew.bat :app:assembleDebug` |
| JVM | `.\gradlew.bat :app:testIntegrityCheckUnitTest` — 984+ tests, 0 failures, 0 errors, 3 skipped |
| Lint | `.\gradlew.bat :app:lint` — no new errors |
| Device | install on Samsung SM-A346M (API 35), captures portrait/landscape, dark+light, live/finisher/result/pause/blast/transition, font scale 1.3 |
| Instrumentation | fix and run `OfflineTrainingBattleScreenTest`, `OfflineBattleFinisherPresentationTest` when the device is attached |
| Accessibility | 48dp targets, TalkBack pass over deck/HUD/blast/result, live regions, reduced motion |
| Performance | no new per-frame recomposition: HUD stays snapshot-batch driven; reused scene instance across finishers |

## 8. Risks

1. **Big-file surgery.** 2,789-line screen + 1,726-line scene; the state machine must not be
   rewritten. Mitigate with incremental phases, one writer per file, tests after each phase.
2. **Projection HUD assumptions** (66×9 plates, 44dp top margin) are viewport-bound; full-bleed
   needs new collision bounds or plates will collide with the top cluster.
3. **Landscape is a new path** for the Radar battle; keep it to one band layout, verify before
   styling polish.
4. **AR regressions** — isolate the branch and re-run the AR smoke check.
5. **Test churn** — the old half-screen invariants must be replaced, not deleted; the androidTest
   source set must compile before the first gate.
6. **Palette variants** (light environments) may reduce scene/chrome contrast; verify with the
   Arena and Digimon.net palettes.
7. **Finisher** — keep the movie; only captions are touched, so the finisher tests
   (`OfflineBattleFinisherPresentationTest`) remain the guard.

## 9. Open decisions (recommended unless owner objects)

- Command-band grouping: menus (`Techniques · Items · Strategy`), orders (`Defend · Support ·
  Special`), movement (`Focus · Approach · Keep distance`) — replaces today's mixed 3×3 order.
- Tactics pause moves from a standing toggle into the pause panel as a switch.
- Result stays a centered technical panel over the arena (training/Arena) and keeps the injected
  Radar reward panel.
- Landscape band is two rows (5 + 4) across the bottom.
- Arena lobby/briefing stay untouched this pass; revisit the Challenge handoff once the new
  battle shell is on device.

## 10. Direction contract

THESIS: The fight is the world, not a page about it — one full-bleed arena with floating battle
chrome; it refuses the 16dp page gutter, the bordered 1:1 viewport and the half-screen command
slab that made battle read as an embedded widget.

OWN-WORLD: Tamers Lab dark roxo/ciano over the authored Colosseum / Radar arena environment
theme: the scene fills the window under soft top and bottom scrims; chrome in HUD ink
(`TextPrimaryOnDark`, Oxanium roles, 1dp/3dp soft shadow) with 48dp cut-corner keys,
`SurfaceStroke` hairlines, `VitalCyan` for live/selected and CP, `VitalYellow` for special, red
only for enemy health; square technical panels for pause/result, never rounded cards floating
over the scene; no glow.

STORY: A player enters from the Arena briefing or a Radar encounter; in Radar the world they were
already in becomes the arena. They read the matchup and both sides' vitals at a glance, spend CP
through a compact deck, watch the scene carry the hits, and the result lands over the same world
without ever returning to a framed page.

FIRST VIEWPORT: Edge-to-edge arena under the system bars. Top scrim: 48dp back/exit key left,
"AGUMON vs GABUMON" in HUD ink with one quiet context line under it, 48dp pause key (Debug key
only under Debug) right. Core: arena with projected enemy name+HP plates, cyan hairline on the
selected target, selected partner's HP/MP/ESP strip bottom-left above the band. Bottom: one
full-width command band over its own scrim — CP instrument, nine cut-corner 48dp keys grouped
menus/orders/movement, one quiet event line; navigation-bar inset respected. No page padding, no
viewport border, no shelf wrapping the arena.

FORM: Surface-scope structure 1 of 3 (single-world overlay), brief-pinned by the owner:
full-bleed arena + floating chrome. seed: none (unavailable).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the
verdict, DESIGN.md, and every shipping raster carrying its provenance.

## 11. Baseline evidence to capture (Phase 0)

| Capture | Where |
|---|---|
| Arena battle live, portrait | training 1v1 via Battle hub → Enter the arena |
| Arena battle result | finish a 1v1 |
| Radar battle live, portrait | Radar → encounter → Battle |
| Radar battle entry transition | sheet → battle handoff |
| Training landscape | same fight with device rotated |
| Light palette | Settings → Appearance → VB Arena |
| Font scale 1.3 | `adb shell settings put system font_scale 1.3` |
