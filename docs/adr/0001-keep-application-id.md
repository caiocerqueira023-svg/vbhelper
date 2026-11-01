# ADR 0001: Keep `applicationId` `com.github.nacabaro.vbhelper`

- **Status:** Accepted
- **Date:** 2026-10-31
- **Target:** Alpha 0.7.0
- **Context:** Phase 1.2 package/product rename; recorded for Phase 6.4
- **Related:** [REFACTORING_PLAN.md](../../REFACTORING_PLAN.md) (Decisions Summary),
  [TARGETS.md](../refactor/TARGETS.md), [ADR 0002](0002-hardware-module-merge.md)

## Context

The project is a fork of `lightheel/vbhelper` and previously used
`com.github.nacabaro.vbhelper` for both its Kotlin namespace and its Android
`applicationId`. The refactor renames the source namespace, application class, theme,
root project, and launcher label to **Tamers Lab** under `com.tamerslab`.

The `applicationId` is not a source-layout detail: it is the installed package identity
used by Android, Google Play, `adb`, `FileProvider` authorities, `WorkManager`/Alarm
records, exported components, and the baseline-profile generator. Changing it makes the
new build a different app to the OS and the store.

## Decision

Keep `applicationId = "com.github.nacabaro.vbhelper"` in `app/build.gradle.kts` while
renaming everything else:

- Kotlin namespace and packages: `com.tamerslab`
- Launcher label: `Tamers Lab` (`@string/app_name`)
- Application class: `com.tamerslab.di.TamersLabApplication`
- Compose theme: `TamersLabTheme`
- Root Gradle project: `TamersLab`

The package name and the source namespace are allowed to differ; this is a supported
Android configuration.

## Rationale

- **Upgrade continuity.** The new build installs over the previous one, so the local
  database, linked saves, secrets, and settings are not orphaned by a package change.
  Persisted-name side effects of the source rename (WorkManager workers, deep links,
  provider authorities) are checked separately in the release upgrade smoke.
- **No migration/export tool.** There is no export/import of the full local database
  and secret store today, so a package rename would silently orphan data.
- **Store and signing continuity.** The current Play/listing identity and signing
  history remain valid; no new listing, review, or beta-track reset is required.
- **Fewer moving parts in one release.** Phase 6 is already a large mechanical move;
  combining it with a data migration would make the release hard to verify.

## Consequences

- `adb` commands, crash reports, the baseline-profile generator, and support
  instructions use `com.github.nacabaro.vbhelper`, while code imports use
  `com.tamerslab.*`. Documentation must state both; `app/README.md` and
  [README.md](../../README.md) do.
- The `FileProvider` authority remains `${applicationId}.provider`; do not hardcode
  `com.tamerslab` there.
- Backups, `run-as`, and device-test package filters must use the `applicationId`, not
  the namespace.
- A future rename is a product decision, not a cleanup.

## Revisit at a clean-install boundary

Changing the `applicationId` is acceptable only when the project owner explicitly
accepts one of:

1. a new install that does not inherit existing local data, or
2. shipping an export/import (or server-side) migration first.

If revisited, the change must update `app/build.gradle.kts`, the manifest/provider
authorities, the baseline-profile `packageName`, deep-link tests, release/signing
configuration, and the release checklist, and must be tested as an upgrade from the old
`applicationId`. Until then, treat the current value as frozen.
