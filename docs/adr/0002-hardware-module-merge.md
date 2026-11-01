# ADR 0002: Keep `hardware` and `hardware-dim` as separate modules

- **Status:** Accepted (fallback implemented in Phase 1.1; sprite port completed in Phase 4)
- **Date:** 2026-10-31
- **Target:** Alpha 0.7.0
- **Related:** [REFACTORING_PLAN.md](../../REFACTORING_PLAN.md),
  [SPIKES.md](../refactor/SPIKES.md) (spike 1),
  [PHASE4_DECISIONS.md](../refactor/PHASE4_DECISIONS.md),
  [hardware/README.md](../../hardware/README.md),
  [hardware-dim/README.md](../../hardware-dim/README.md)

## Context

Two vendored projects covered watch communication:

- `vb-nfc-reader` — Android NFC transport/parsing for VB, VH, VBC, and BE.
- `vb-dim-reader` — a standalone Java 11 JVM library that parses DiM/BEM card dumps,
  using Lombok, commons-io, slf4j, and `at.favre.lib:bytes`.

They solve different problems: NFC tag I/O needs Android, while card parsing is pure
byte manipulation that must also run in desktop tests/tools. The Phase 0 spike asked
whether they could become one Android library. The blocking facts were Lombok
annotation processing and the JVM-only dependencies (commons-io, slf4j) leaking into an
Android library, plus JUnit 5 vs Android's JUnit 4 test setup.

## Decision

Keep two Gradle modules with one public boundary:

1. **`:hardware`** — Android library (`com.tamerslab.hardware`) for `android.nfc`,
   `TagCommunicator`, transports, translators, and the character models.
2. **`:hardware-dim`** — pure Kotlin/JVM `java-library` (`com.tamerslab.hardware.dim`)
   for card parsing/writing, Java 11, JUnit 5.

`:hardware` exposes `hardware-dim` through `api(project(":hardware-dim"))`. The app
depends only on `:hardware` and can use `com.tamerslab.hardware.dim.*` without knowing
about the extra module. This is the facade: NFC I/O in the Android module, card parsing
in the JVM module.

## Why not merge the sources now

- Lombok is used by the remaining non-sprite Java classes. Moving them into an Android
  library would require either removing Lombok first or carrying its annotation
  processor and generated members through Android packaging.
- `commons-io` and `slf4j` are ordinary runtime dependencies of the JVM library; pulling
  them into the app's Android dependency graph is an APK and shrinker risk that the
  spike could not justify.
- The two modules use different test frameworks (JUnit 5 vs JUnit 4) and different
  consumers; a merge would not simplify either.
- A full merge is only worth doing after the remaining Java is Kotlinized (or Lombok is
  removed) and an APK dependency report shows no unwanted runtime libraries.

## Consequences

- The module split survives Alpha 0.7.0. It is intentional, not leftover scaffolding.
- The sprite parser (twelve classes) is already Kotlin in `hardware-dim`; non-sprite Java
  still uses Lombok and stays until a follow-up Kotlinization.
- The app's `proguard-rules.pro` carries `-dontwarn lombok.Generated` and
  `org.slf4j.impl.StaticLoggerBinder` for the DIM dependency; `hardware`'s
  `consumer-rules.pro` is empty because there is no reflective public contract.
- New code goes to the correct side of the boundary: Android/NFC APIs in `:hardware`,
  byte parsing and pure models in `:hardware-dim`.
- Revisit only with a compile/package spike that proves the merged module builds, keeps
  the tests green, and keeps the APK dependency report clean.
