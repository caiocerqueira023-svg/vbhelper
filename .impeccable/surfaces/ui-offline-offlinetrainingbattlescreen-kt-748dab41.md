---
version: 1
slug: "ui-offline-offlinetrainingbattlescreen-kt-748dab41"
primary_target: "app/src/main/java/com/tamerslab/battle/ui/offline/OfflineTrainingBattleScreen.kt"
related_targets: ["app/src/main/java/com/tamerslab/battle/ui/offline/BattleChrome.kt"]
---

---
version: 1
slug: "ui-offline-offlinetrainingbattlescreen-kt-748dab41"
primary_target: "app/src/main/java/com/tamerslab/battle/ui/offline/OfflineTrainingBattleScreen.kt"
related_targets: ["app/src/main/java/com/tamerslab/battle/ui/offline/BattleChrome.kt"]
---

# Battle screen

## Scope and mode

The in-battle surface shared by Arena fights (training, exhibition/cups) and Radar encounters:
`OfflineTrainingBattleScreen` and the Radar branch `WorldRadarBattleContent` in
`app/src/main/java/com/tamerslab/battle/ui/offline/OfflineTrainingBattleScreen.kt`, with the floating
chrome in `BattleChrome.kt`. Out of scope: Arena lobby/briefing/team setup, the Battle hub and legacy
online PvP, the Radar encounter sheet, and the AR battle stage. Mode: **Operate** — the visitor
completes a fight; the live arena leads and the interface recedes. Native Android (Jetpack Compose +
Filament), code-led.

## Audience, job, constraints

Existing Tamers Lab players fighting in the Colosseum or in a Radar encounter. They need to read the
matchup and their partner's vitals at a glance, issue orders with one thumb, and watch the arena carry
the hits. Constraints: 48dp targets, safe-area insets, one Filament engine per screen with the scene
never remounted mid-session, snapshot-only HUD, pause-reason keys fixed by the session API, reduced
motion, TalkBack semantics and test tags, every saved palette. The AR stage keeps its established square
scanning/battle layout and inherits the same command vocabulary.

## Chosen direction

The fight is the world, not a page about it: one full-bleed arena with floating chrome (top cluster +
enemy plates + one bottom command instrument) in the Radar place's vocabulary. The bottom instrument
replaces the dead Command Points meter with the partner's own instruments and reorganizes the ten
origins of trainer input into a fixed 3×3 deck borrowed from Re:Digitize Decode's command wheel and
timing panel: the partner's three equipped techniques on the top row, then Items / Special / Strategy,
then Focus / Defend / Move. Move is a modal one-tap gesture: arm it, then touch any point of the arena
to send the partner there. There is no floating vitals strip over the scene; HP, MP and ESP live in the
band header where the CP instrument used to be. The special/Blast timing is a Decode-style command
dial — dark technical ring, yellow progress arc, red target arc, ticks and needle, the ordered move's
name and a small BLAST label in the centre — and any touch on the screen answers it.

**Memorable moment:** the band becomes the partner's instrument panel — vitals and the 3×3 command deck
occupying the space the dead CP meter wasted — and the Blast timing resolves with one touch anywhere,
landing the red line like the Decode panel.

## Unresolved

- Arena lobby/briefing are untouched in this pass; the Challenge handoff into the new shell is
  reviewed, not rebuilt.
- The finisher movie/camera sequence is kept as authored; only its captions and surrounding chrome are
  re-fitted.
- The scenario invariant "the scene never remounts mid-session" remains: the battle scene stays hoisted
  out of the radar/battle branch, and a settled battle hands the surface to the result panel (live top
  cluster, command band and projected HUD step aside). This is recorded in `TESTING.md` and
  `DESIGN.md` (Battle screen → States).
- Move is accepted only inside the arena's playable bounds; taps on horned/outside regions clamp to the
  playable radius. The destination has no persistent 3D marker in this pass; feedback is the band line
  and the partner's own movement.
- Device captures, TalkBack and font-scale acceptance are recorded in TESTING.md after the final gate.

## Direction contract

THESIS: The fight is the world, and the trainer's hand is one instrument — a fixed 3×3 command deck
inheriting Re:Digitize Decode's command-wheel and timing-panel grammar; it refuses the floating vitals
strip that hid the arena and refuses a meter (CP) that never gated anything.

OWN-WORLD: Tamers Lab dark roxo/ciano over the authored Colosseum / Radar arena environment theme: HUD
ink (`TextPrimaryOnDark`, Oxanium roles, 1dp/3dp soft shadow), bare 48dp keys with a 1dp hairline
under each, `SurfaceStroke`
hairlines, `VitalCyan` for live/selected, `VitalPurpleBright` for MP, `VitalYellow` for Special/ESP and
the Blast target line, red only for enemy health and the timing target; one slab over its own scrim;
no glow, no nested cards, no rounded card floating over the scene.

STORY: A player enters from the Arena briefing or a Radar encounter; they read the matchup, their
partner's HP/MP/ESP in the band header, tap one of three equipped moves or Items/Special/Strategy,
Focus, Defend, or arm Move and touch the arena; the scene carries the hits; the Blast timing is
answered by touching anywhere as the bar crosses the red line; the result lands over the same world.

FIRST VIEWPORT: Edge-to-edge arena under the safe area. Top scrim: 48dp exit/back key left, matchup
"AGUMON vs GABUMON" with one quiet context line, 48dp pause key (recenter/Debug beside it) right. Core:
arena with projected enemy name+HP plates and the cyan selected-target hairline; no partner vitals
overlay. Bottom: one full-width band over its own scrim — header row with partner name and compact
HP/MP/ESP instruments, one quiet feedback line, then a fixed 3×3 grid of bare 48dp keys, each settled
by a 1dp hairline: row 1
three equipped technique keys (name + EN cost + state), row 2 Items / Special / Strategy, row 3 Focus /
Defend / Move; safe-area inset respected. The Blast timing overlay is a Decode-style command dial with
a dark technical ring, tick marks, a yellow progress arc, a red target arc and a needle, the ordered
move's name and a small BLAST label in the centre, and one instruction line; it has no Stop button —
the whole screen is the input.

FORM: Surface-scope structure 1 of 3 (single-world overlay), brief-pinned by the owner: full-bleed
arena + floating chrome. seed: none (unavailable).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the
verdict, DESIGN.md, and every shipping raster carrying its provenance.
