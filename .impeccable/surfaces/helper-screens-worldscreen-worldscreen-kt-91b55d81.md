---
version: 1
slug: "helper-screens-worldscreen-worldscreen-kt-91b55d81"
primary_target: "app/src/main/java/com/github/nacabaro/vbhelper/screens/worldScreen/WorldScreen.kt"
related_targets: ["app/src/main/java/com/github/nacabaro/vbhelper/screens/offlineBattle/OfflineTrainingBattleScreen.kt","app/src/main/assets/Arena/Radar/arena.json"]
---

# World Radar battle surface brief

## Product intent

Turn a nearby World encounter into a battle without navigating away from the Radar. Tapping a Digimon opens a Talk/Battle choice. Battle keeps the World shell visible, replaces the square 2D Radar with the same-size 3D arena, and replaces the Radar controls below it with the existing battle commands.

## Visual continuity

- Preserve the existing dark purple/cyan, cut-corner VBHelper language.
- The 2D World grid becomes an unlit 3D ground plane with purple grid lines.
- A cyan circle marks the playable boundary; no colosseum props or unrelated scenery.
- Keep the World shell, square frame, header, and tabs stationary throughout the transition.
- Cross-fade and subtly scale only the contents inside the square viewport: the Radar scene out, then the 3D arena in.
- Match the 3D floor and grid colors/opacity to the Radar palette, and continue the grid to the camera horizon.

## Interaction and state

- Nearby encounter sheet offers Talk and Battle as equal, 48dp-plus actions.
- During battle, GPS updates, compass sensors, Radar pulse animation, spawn generation, tab changes, and settings actions are suspended.
- The active Digimon remains autonomous; strategies, techniques, defend, support, movement, pause, and tactical pause use the established battle HUD.
- Victory removes the defeated World spawn and records a win. Defeat records a loss and leaves the wild Digimon. Draw and abandonment do not affect win rate.

## Data contract

- Both fighters use the existing integrated simulator and ruleset.
- Wild HP, BP, AP, DiM/BEM scale, attribute, mood, individual ID, and database sprites come from the selected World spawn.
- The active Digimon uses its stored card profile, training values, mood, vitals, and persisted individual personality.

## Responsive behavior

- The battle arena remains a 1:1 square at the same width as the Radar.
- Commands flow below the square and remain vertically scrollable on compact portrait devices.
- The existing World top banner and tabs remain visible as context but are inert while combat is active.

## Verification boundary

Compilation, unit tests, asset structure, and APK packaging can be verified locally. Camera composition, Filament rendering, touch targets, and transition feel require an approved device installation or emulator capture.
