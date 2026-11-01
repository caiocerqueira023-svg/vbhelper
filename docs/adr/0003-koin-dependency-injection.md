# ADR 0003: Hard cutover to Koin for dependency injection

- **Status:** Accepted and implemented (Phase 2.1)
- **Date:** 2026-10-31
- **Target:** Alpha 0.7.0
- **Related:** [REFACTORING_PLAN.md](../../REFACTORING_PLAN.md),
  [SPIKES.md](../refactor/SPIKES.md) (spike 4),
  [app/README.md](../../app/README.md)

## Context

Before the refactor, `TamersLabApplication.onCreate()` constructed a single manual
`DefaultAppContainer`, which owned a large lazy graph of the database, repositories,
services, coordinators, and workers. `WorldAfkWorker` could construct a second
`DefaultAppContainer`, so the "one app graph" invariant was not guaranteed by the code.
There was no DI framework, and constructor dependencies were resolved by reaching
through the container.

The Phase 0 spike asked whether Koin could replace the manual container without
duplicating the database or regressing startup. Koin was chosen over annotation-based
frameworks because it is Kotlin-first and needs no annotation processing, which keeps
the build simple and fast on the constrained Windows memory settings.

## Decision

Perform a **hard cutover** to Koin; do not run two DI systems indefinitely.

- Modules live in `app/src/main/java/com/tamerslab/di/AppModule.kt`:
  `appModule`, `databaseModule`, `networkModule`, `hardwareModule`,
  `repositoryModule`, `useCaseModule`, `viewModelModule`, aggregated as `appModules`.
- Koin starts once in `TamersLabApplication.onCreate()` via
  `startKoin { androidContext(this@TamersLabApplication); modules(appModules) }`.
- `DefaultAppContainer` remains the owner of the database and the existing object graph,
  but it is now a Koin-managed **adapter**: `appModule` provides it as a singleton and
  exposes it as `AppContainer`. No new dependencies should be added to it as a second
  registration path.
- Feature code resolves through typed constructor parameters: repository interfaces in
  `domain/repository/`, adapter implementations in `di/repository/`, and the five use
  cases (`TransferUseCase`, `BattleUseCase`, `SaveImportUseCase`, `ChatUseCase`,
  `FarmUseCase`) in `domain/usecase/FeatureUseCases.kt`.
- No annotation processing or KSP is used for DI.
- Verification is by `KoinModuleGraphTest` (the use-case graph resolves with fake
  repositories) and `ArchitectureBoundaryTest` (domain/repository code stays free of
  Android, Room, database, and DI imports).

## Consequences

- One app-owned graph; a worker or screen cannot silently build a second container.
- Dependencies are explicit in constructors, which makes JVM tests straightforward:
  `KoinModuleGraphTest` uses `koinApplication { modules(fakes, useCaseModule) }`.
- The container is a migration adapter, not the target architecture. Adding a new
  dependency should mean a Koin module binding and constructor injection, not another
  container field.
- Koin adds a small runtime/startup cost. It was not benchmarked; the spike required a
  startup comparison, and that comparison is still **not measured** after the Phase 6
  restructure (see [BASELINE.md](../performance/BASELINE.md)).
- Removing the container entirely is follow-up work, not an Alpha 0.7.0 gate.

## Alternatives considered

- **Keep the manual container.** Rejected: the worker could still create a second
  database, and test seams stayed implicit.
- **Hilt/Dagger.** Rejected for this release: annotation processing adds build cost and
  configuration on a project already constrained by Gradle daemon memory.
- **Full manual service locator.** Rejected: it preserves the hidden-dependency problem
  the refactor is trying to remove.
