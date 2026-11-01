# ADR 0005: Performance budgets and release gating

- **Status:** Accepted; measurement status is mostly blocked/unmeasured
- **Date:** 2026-10-31
- **Target:** Alpha 0.7.0
- **Related:** [REFACTORING_PLAN.md](../../REFACTORING_PLAN.md) (Success Metrics),
  [TARGETS.md](../refactor/TARGETS.md),
  [BASELINE.md](../performance/BASELINE.md) (procedures and evidence),
  [ROOM_INDEX_AUDIT.md](../refactor/ROOM_INDEX_AUDIT.md)

## Context

The refactor plan commits to numeric performance budgets rather than assumed wins. The
original baseline predates the refactor and most device metrics were never captured. The
risk is claiming "faster" from code presence, a stale run, or a smoke launch; this ADR
fixes the budgets, the evidence rule, and the release gate.

## Budgets

| # | Metric | Budget | Source |
|---|--------|--------|--------|
| 1 | Cold start (release build) | >=15% faster than the Phase 0 release baseline (macrobenchmark, 5 runs, same device) | REFACTORING_PLAN Success Metrics |
| 2 | Battle frame time | p95 <=16.7 ms on the reference mid-range device | REFACTORING_PLAN Success Metrics |
| 3 | Farm rendering | 60 fps active, 30 fps idle | REFACTORING_PLAN Success Metrics |
| 4 | Release APK size | <=155.5 MiB (163,053,568 bytes), i.e. >=15% smaller than the 182.95 MiB Phase 0 baseline | TARGETS.md |
| 5 | Room query plans | No unexpected `SCAN` for the top 20 queries | REFACTORING_PLAN Success Metrics |
| 6 | Static analysis | Zero lint errors (baseline entries only where justified) | REFACTORING_PLAN Success Metrics |
| 7 | Test coverage | 80%+ on `WorldDsRecordCodec`, `NdsStoryRecordCodec`, `WorldDsPartyEdits`, `BattleSimulator`, `TamerTrainerController`, `PersonalityBattleProfile` | REFACTORING_PLAN task 7.2 |
| 8 | Stability | No on-device OOM; stable 30-minute soak | REFACTORING_PLAN Success Metrics |

The cold-start budget is relative to a baseline that does not exist yet, so its absolute
target is undefined. The coverage list is the named-class scope, not whole-app coverage.

## Evidence rule

1. A result is valid only for the commit, build type, device, and command it was
   captured on. Any change to the build script, ProGuard rules, baseline profile,
   rendering/farm/battle scene code, or asset pipeline invalidates it.
2. Every entry in [BASELINE.md](../performance/BASELINE.md) must state commit, build
   type, device, exact command, date, and artifact path. A number without those fields is
   not evidence.
3. Smoke numbers are labelled smoke numbers. A single `am start -W` run is not a
   benchmark and never closes a budget.
4. "Not measured" is the default. Emulator results are labelled `emulator` and never
   close a budget; do not infer "passed" for an unmeasured budget.

## Current status

- **Cold start:** not measured. No release baseline exists; the Phase 0/TARGETS launch
  figures are single smoke launches.
- **Battle p95:** not measured. Needs a Perfetto or `gfxinfo framestats` capture during a
  30-second battle.
- **Farm FPS:** not measured. The frame tiers and thermal downgrade exist in code but
  have no device frame metrics.
- **APK size:** the only recorded value is the Phase 1 minified release at 140.51 MiB
  (147,335,418 bytes) on commit `5f98ca85`, which met the target then. It is **not** a
  current-HEAD measurement and was **not yet re-verified after the Phase 6 restructure**.
- **Room query plans:** not measured; needs a runtime database and the authored top-20
  query list.
- **Lint:** the Phase 0 record was 52 `MissingTranslation` errors, 374 warnings, and 11
  hints. Phase 7.1 fixed the errors with real JA/PT-BR resources and pruned the baseline
  to 62 warnings; the clean post-Phase-6 `:app:lint` run is still pending, so the
  zero-error budget is not yet evidenced.
- **Coverage:** JaCoCo is configured for `:game-save-core` and `:app` (standalone report
  tasks), but no coverage value is recorded and no threshold is enforced.
- **Soak:** not measured; no 30-minute run exists.

No post-Phase-5 measurement exists. The Phase 5 work (frame pacing tiers, battle
scene/UI channel split, deterministic baseline-profile remapping) is **implemented but
unproven** and must not be described as a measured win.

## How budgets gate releases

An Alpha 0.7.0 release candidate is accepted only when every must-ship metric has a
dated entry in [BASELINE.md](../performance/BASELINE.md) captured on the same build as
the candidate, with command, device, and artifact. A missing or stale entry means "not
measured", not "passed"; the release checklist records the gap explicitly. CI
(`.github/workflows/ci.yml`) has no device and no benchmark step today, so performance
is a manual gate (or Firebase Test Lab) until a device-backed CI job exists.

Changing a budget is a plan change and needs project-owner approval. A new measurement
procedure is documented in BASELINE.md first; it must not be invented in a release note.
