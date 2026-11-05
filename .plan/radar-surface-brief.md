# Radar place

## Scope and mode

The Radar place (`app/src/main/java/com/tamerslab/world/ui/RadarPlaceScreen.kt`, composed by
`RadarScreen` in `WorldScreen.kt`): finding nearby wild Digimon in the real world, reading their
bearing/distance, and entering encounters, chat or battle. Mode: **Operate** — the visitor completes a
task; the live world leads and the interface recedes. Native Android (Jetpack Compose + Filament).

## Audience, job, constraints

Existing Tamers Lab players walking around the real world. They need to answer "what is near me, which
way, how far, can I reach it" in one glance and act on it. Constraint set: 48dp targets, edge-to-edge
with system insets, keep battle/AR entry, keep the compass truthful (north-up when uncalibrated),
respect reduced motion, no new permissions. The old 2D and first-person radars must remain in the
codebase but not be the default view.

## Chosen direction

Radar becomes a full-bleed 3D isometric radar world on the reused `Arena/Radar/radar_grid.glb` plate,
matching Home's "world fills the window, chrome floats over it" shell and Storage's instrument
vocabulary. The active partner stands at the center as the player token; wild Digimon stand at their
bearing/distance as extruded sprite 3D models; a minimal digivice-bezel compass (no distance rings) and a
soft edge vignette belong to the world
draw over the world; the floating chrome is a top cluster (back, place · cardinal, options, spawn
settings) and a bottom signal band (nearby count, status/recovery, zoom). Preferences move into one
options sheet; the legacy 2D and first-person views live there behind the master Debug switch.

**Memorable moment:** the compass turns and the whole isometric plate — Digimon included — rotates
under the fix while the Tamer's partner stays planted at the center.

## Unresolved

- Whether legacy first-person also stays reachable (Debug only, alongside Classic 2D) — owner asked
  only that the 2D radar is not deleted; treating both as Debug-only legacy until told otherwise.
- Final ring interval at high zoom (200 m rings collapse at full zoom-out); default 200 m, labelled
  with the mapped radius.

## Direction contract

THESIS: This surface is the real world, not a form about it — one full-bleed isometric radar world with
detached floating chrome; it refuses the page frame, the bordered square viewport and the permanent
toggle stack that made the old screen read as settings.

OWN-WORLD: Tamers Lab dark roxo/ciano: `SpaceBlack`/`DeepPurpleBgAlt` ground, `SurfaceElevatedPurple`
chrome at the home HUD's translucency, `SurfaceStroke` hairlines, `VitalPurpleBright` for the player
ring and `VitalCyan` for live/selected signals; Oxanium labels in HUD ink with a 1dp/3dp soft shadow;
cut-corner 48dp keys; the radar plate is the authored `radar_grid.glb` geometry, theme-bound through
the environment theme controller, never an opaque card.

STORY: The player opens Radar, sees their partner standing at the center of a living grid, reads north
from the compass ring, spots the nearest few Digimon, taps one, and either talks, fights or walks on.
The sprite itself is the tap target (its projected rect, min 48dp, no pressed-ripple overlay), like a
farm resident, and the interaction ring behind it is a single clean line cut behind the sprites.
Every status (locating, populating, empty, approximate, frozen) is one quiet line in the bottom band
with its recovery action beside it, never a panel that owns the screen.

FIRST VIEWPORT: Edge-to-edge portrait. The isometric plate fills the window under the status bar and
navigation bar; a soft dark vignette blends its edges. Top: one 48dp back key, then the place label
"Radar · NW" in HUD ink; two 48dp cut-corner keys at the right (options, spawn settings). The
cardinal compass ring sits at ~86% of the shorter viewport edge as a large minimal instrument ring
(thin dense ticks, longer cardinal ticks, red two-triangle needle at the nearest Digimon); there are
no distance rings and no inner circle (owner decision 2026-11-04). While the radar is live the needle
occasionally performs a short idle search gesture (a full sweep or a searching wiggle) and returns to
the true bearing; the tick nearest that bearing is always slightly longer, so the reading stays
visible during the gesture, and reduced motion keeps the needle static.
The active partner stands at the center on a violet interaction-proximity ring (a single clean line,
sized to `RadarWorldGeometry.INTERACTION_RANGE_METERS`): it is a real 3D ground decal so Digimon occlude
it, and a pre-baked width ladder is alpha-crossfaded so the line stays constant on screen while
pinching; nearby Digimon stand outward.
Bottom: one
translucent band holding the nearby count ("18 nearby"), the zoom keys, and the status line/recovery
action when the state is not READY; the band keeps the 48dp key rhythm and navigation-bar inset.

FORM: Surface-scope structure 1 of 3 (single-world overlay), brief-pinned by the owner: full-bleed
world + floating chrome + one options sheet. seed: none (unavailable).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict,
DESIGN.md, and every shipping raster carrying its provenance.
