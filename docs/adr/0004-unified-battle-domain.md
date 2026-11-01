# ADR 0004: Unified battle domain with offline/online split

- **Status:** Accepted and implemented (Phase 4.1); scene-channel flag still unvalidated
- **Date:** 2026-10-31
- **Target:** Alpha 0.7.0
- **Related:** [REFACTORING_PLAN.md](../../REFACTORING_PLAN.md),
  [SPIKES.md](../refactor/SPIKES.md) (spike 3),
  [PHASE4_DECISIONS.md](../refactor/PHASE4_DECISIONS.md),
  [BASELINE.md](../performance/BASELINE.md)

## Context

Battle code had grown in two directions: a substantial offline simulator and a legacy
online PVP/auth client. Both described the same creatures, techniques, teams, and rules,
but with separate models and DTOs. The Phase 4 goal was one source of truth for battle
definitions without changing offline gameplay or deleting the online feature.

## Decision

Split battle by responsibility and share only the domain definitions:

- **`battle/domain/`** — shared models in `BattleDefinitions.kt`:
  `TechniqueDefinition`, `CombatantDefinition`, `BattleTeam`, `BattleStatusEffect`, plus
  the enums (`BattleSide`, `BattleAttribute`, `BattleStrategy`, `TechniqueKind`, ...).
  `BattleRules` and AI profile types live in `BattleAiProfile.kt`.
- **`battle/offline/`** — the simulator and its session layer:
  `battle/offline/core/BattleSimulator.kt`, `BattleModel.kt` (snapshots/events/config,
  not shared definitions), `TamerTrainerController.kt`,
  `PersonalityBattleProfile.kt`, plus `offline/session/`, `offline/tamers/`, and
  `offline/data/`.
- **`battle/online/`** — legacy auth/PVP transport and DTOs: `ArenaBattleSystem`,
  `PVPService`, `AuthService`, `AuthInterceptor`, `BattleAuthContainer`,
  `RetrofitHelper`, and the request/response models.
- **`battle/ui/`** — `BattlesScreen.kt`, `battle/ui/arena/`, and
  `battle/ui/offline/` consume the typed state; they do not own combat rules.

Behavior changes are gated, not implicit:

- **Online PVP** is behind `BuildConfig.ONLINE_PVP_ENABLED`, read through
  `battle/online/OnlineBattleFeature.ENABLED`. Offline battle is independent of this
  flag.
- **Battle scene/UI state delivery split** is behind
  `BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED`, read through
  `battle/offline/session/BattleSceneChannel.kt`. It defaults to `false`, which keeps the
  original 60 Hz full-snapshot Compose path. The split sends the full-rate snapshot to
  Filament and throttles Compose UI state to 100 ms, with immediate publication for
  result, pause, finisher, blast, and support transitions.

## Behavior preservation

Offline behavior must not change as part of the split. The characterization suite
(`BattleSimulatorTest`, `BattleSessionControllerTest`, `BattleFinisherTest`,
`BlastTimingTest`, `BattleRepositioningTest`, and the arena/tamer tests) is the contract.
A change to those expectations is a gameplay change and needs its own review. The online
contract is covered separately by `OnlineBattleContractTest`.

## Rollback

- Offline PVP/auth surface: set `ONLINE_PVP_ENABLED = false` and rebuild; the entry point
  and network code stay compiled but unreachable.
- Scene/UI split: set `BATTLE_SCENE_CHANNEL_ENABLED = false` and rebuild; the old
  per-tick Compose update returns with no other code change.

Both are compile-time flags, so a rollback is a one-field build change, not a revert.
Remove a flag only after the path it protects is validated and no longer needs an
escape hatch.

## Consequences

- One definition of techniques/teams/attributes; offline and online no longer drift on
  model names or numeric rules.
- The offline simulator remains the only combat authority; online DTOs are transport
  shapes, not a second rule engine.
- The scene-channel optimization is still **not validated** on a device. The flag must
  stay off until Perfetto/FrameMetrics evidence and a gameplay-parity pass are recorded
  in [BASELINE.md](../performance/BASELINE.md).
- Offline play never depends on the online host or auth state.
