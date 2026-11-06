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
matchup and both sides' vitals at a glance, issue orders with one thumb, and watch the arena carry the
hits. Constraints: 48dp targets, safe-area insets, one Filament engine per screen with the scene never
remounted mid-session, snapshot-only HUD, pause-reason keys fixed by the session API, reduced motion,
TalkBack semantics and test tags, every saved palette. The AR stage keeps its established square
scanning/battle layout.

## Chosen direction

The fight is the world, not a page about it: one full-bleed arena with floating chrome (top cluster +
combatant HUD + bottom command band) in the Radar place's vocabulary, replacing the page gutter,
bordered 1:1 viewport and half-screen command slab. The Radar battle keeps the Radar world full-bleed
through the encounter, the arena replacing it in the same frame, and returns to the Radar place; AR is
the explicit legacy exception.

**Memorable moment:** the encounter sheet closes and the Radar world becomes the arena in the same
full-bleed frame — chrome swapping in, no drop into a square window.

## Unresolved

- Arena lobby/briefing are untouched in this pass; the Challenge handoff into the new shell is
  reviewed, not rebuilt.
- The finisher movie/camera sequence is kept as authored; only its captions and surrounding chrome are
  re-fitted.
- Device captures, TalkBack and font-scale acceptance are recorded in TESTING.md after the final gate.

## Direction contract

THESIS: The fight is the world, not a page about it — one full-bleed arena with floating battle chrome;
it refuses the 16dp page gutter, the bordered 1:1 viewport and the half-screen command slab that made
battle read as an embedded widget.

OWN-WORLD: Tamers Lab dark roxo/ciano over the authored Colosseum / Radar arena environment theme: the
scene fills the window under soft top and bottom scrims; chrome in HUD ink (`TextPrimaryOnDark`,
Oxanium roles, 1dp/3dp soft shadow) with 48dp cut-corner keys, `SurfaceStroke` hairlines, `VitalCyan`
for live/selected and CP, `VitalYellow` for special, red only for enemy health; square technical
panels for pause/result, never rounded cards floating over the scene; no glow.

STORY: A player enters from the Arena briefing or a Radar encounter; in Radar the world they were
already in becomes the arena. They read the matchup and both sides' vitals at a glance, spend CP
through a compact deck, watch the scene carry the hits, and the result lands over the same world
without ever returning to a framed page.

FIRST VIEWPORT: Edge-to-edge arena under the safe area. Top scrim: 48dp exit/back key left, the
matchup "AGUMON vs GABUMON" in HUD ink with one quiet context line under it, 48dp pause key (Debug and
recenter keys beside it) right. Core: the arena with projected enemy name+HP plates, cyan hairline on
the selected target, the selected partner's HP/MP/ESP strip bottom-left above the band. Bottom: one
full-width command band over its own scrim — CP instrument, nine cut-corner 48dp keys grouped
menus/orders/movement, one quiet event line; safe-area inset respected. No page padding, no viewport
border, no shelf wrapping the arena.

FORM: Surface-scope structure 1 of 3 (single-world overlay), brief-pinned by the owner: full-bleed
arena + floating chrome. seed: none (unavailable).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the
verdict, DESIGN.md, and every shipping raster carrying its provenance.
