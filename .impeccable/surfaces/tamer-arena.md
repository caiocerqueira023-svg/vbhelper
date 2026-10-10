---
version: 1
slug: tamer-arena
primary_target: app/src/main/java/com/github/nacabaro/vbhelper/screens/tamerArena/TamerArenaScreen.kt
related_targets: [app/src/main/java/com/github/nacabaro/vbhelper/screens/BattlesScreen.kt, app/src/main/java/com/github/nacabaro/vbhelper/screens/offlineBattle/OfflineTrainingBattleScreen.kt]
---

# Tamer Arena

## Direction contract

- **THESIS / Operate:** find a playable opponent quickly, inspect one clear team briefing, and enter the existing Colosseum. Canonical forms, guests and genuinely stage-scaled stats are explicit.
- **OWN-WORLD:** inherit Material components, theme roles, Oxanium typography, cut-corner surfaces, and crisp existing partner sprites. No new raster assets or tamer portraits are required.
- **STORY:** choose owned partners and format, search/filter the roster, inspect attacks/Blast/Jogress/items, then fight; tournament rounds and safe rewards persist.
- **FIRST VIEWPORT:** one compact native header and tabs, a one-line shared team summary with a labeled Change action, search plus a single scrollable franchise/Ready filter row, and compact sprite/name/series opponent rows. Team settings occupy a dedicated inline editor rather than competing with the roster. Briefing leads with the real opponent team, then tactics, inspected partner stats/attacks/finishers, supplies and rewards; Challenge stays fixed below the scroll.
- **FORM:** refine the existing roster/preparation/cup flow within the current design system. Shared CyberPanel/VitalButton controls replace one-off rounded actions. Saved cups are summaries; bracket details show one selected round. Results stay in place while the next match prepares, and matching rematches reuse prepared render resources.
- **FINISH:** unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance.

## Confirmed rules

Allied guests are allowed after owned/associated partners. Character-specific official forms take precedence. Genuinely missing generations retain a documented form with stat/technique scaling; missing canonical artwork blocks that team. Battles use session supplies and award Bits/records/trophies with full round recovery. Item use is finite and team-owned. Checks and native review are bounded and batched after integration, as requested.
