---
version: 1
slug: farm-home
primary_target: app/src/main/java/com/tamerslab/farm/ui/home/FarmHomeScreen.kt
related_targets: ["app/src/main/java/com/tamerslab/farm/ui/home/FarmHomeHud.kt","app/src/main/java/com/tamerslab/farm/ui/home/PlaceDock.kt","app/src/main/java/com/tamerslab/home/ui/PartnerDeck.kt","app/src/main/java/com/tamerslab/home/ui/PartnerDeckContract.kt","app/src/main/java/com/tamerslab/farm/ui/digifarm/FarmHomeWorld.kt","app/src/main/java/com/tamerslab/navigation/AppNavigation.kt","app/src/main/java/com/tamerslab/components/HudChrome.kt","app/src/main/java/com/tamerslab/ui/theme/AppTheme.kt"]
---

# Farm home surface brief

Mode: Operate, with an Experience layer: the visitor is in their own habitat, and the app is the
tool beside it. Plan: `PRESENTATION_REDESIGN_PLAN.md`.

## Direction contract

THESIS: The companion comes first and the software follows — the app opens inside a living Digifarm,
with your partner in frame, and every place you need is one thumb away.

OWN-WORLD: Keep the dark purple/cyan instrument identity, the four saved palettes, Oxanium, the
cut-corner shape family and the `cyberFrame` vocabulary. Add no new palette and no second visual
language; the game layer is a composition and motion change, expressed with existing tokens on
gradient scrims.

STORY: Open the app and see who is with you and how they are — vitals, mood, the next evolution,
what they are doing — then go somewhere: Radar for the world outside, Arena to fight, Transfer to
move individuals between games and devices, Items to spend bits and manage stock, Partners to manage
the collection. Pull the slab for the full record; Dex, Quests, the Tamer profile and the remaining
destinations live in the header or the menu.

FIRST VIEWPORT: The island fills the screen and the active Digimon is framed between 30% and 52% of
the viewport height. Top: the farm chip (resident count with its place gauge, rank), then Dex,
Settings and menu keys. Bottom: five place overlays (Radar, Arena, Transfer, Items, Partners) above a
partner slab that carries portrait, name, level, vitals and mood. Nothing else on the first screen.

FORM: Android/Compose, edge-to-edge for this route only, Material 3 semantics and back behaviour,
48dp targets, three-detent non-modal sheet with an explicit back ladder, one paused render loop and
a reduced-motion path that the 3D scene must honour. Pure presentation: no new gameplay, currency or
progression systems; every existing module keeps a reachable home.

FINISH: unreviewed and undocumented is unfinished. This surface ends with a device capture on a
connected phone, a light-palette contrast pass, a measured idle-drain number, DESIGN.md's navigation
section rewritten, and the previous 5-tab shell described as superseded.

## What this surface refuses

- Fake backdrop blur over a live 3D surface; chrome is gradient scrim + 1dp rim light.
- A Material 5-destination bottom bar sitting on top of the world — either the dock owns navigation
  or the app is still a panel with a render behind it.
- Hidden gestures on the world (no long-press to reveal, no swipe-to-place).
- Rounded Material sheet chrome (28dp) next to cut-corner panels.
- Decorative badges; a badge must mean something is waiting and it must clear at the source.

## Open decisions owned by the product owner

Dock composition, Shop vs Exchange naming, quests placement, signposts, landscape, battery target,
reference device. Tracked as Q1–Q20 in the plan's §14.
