# ADR 0006: Own `applicationId` `com.tamerslab.vbhelper`

- **Status:** Accepted
- **Date:** 2026-11-14
- **Supersedes:** [ADR 0001](0001-keep-application-id.md)
- **Target:** Alpha 0.7.0
- **Related:** [ADR 0001](0001-keep-application-id.md), [RELEASE_CHECKLIST_0.7.0.md](../release/RELEASE_CHECKLIST_0.7.0.md)

## Context

ADR 0001 kept `applicationId = "com.github.nacabaro.vbhelper"` so the refactored build
would install as an upgrade of the upstream app and inherit its local data. That decision
assumed the project was still a drop-in replacement for VBHelper.

It is not distributed that way any more. This tree ships its own builds (the
VBHLLM/Tamers Lab line) from its own repository. With the upstream package id:

- installing this APK is an **update of VBHelper** on any device that already has VBHelper,
  replacing it and keeping its data, which is not what the owner wants;
- an upstream VBHelper release would likewise be offered as an update of this app;
- the two products cannot be installed side by side, and the owner cannot tell testers to
  keep both.

The owner explicitly accepted a new install that does not inherit existing local data, and
the app already ships `Settings > Export database` / `Import database`, so the migration
path ADR 0001 said was missing now exists.

## Decision

`applicationId = "com.tamerslab.vbhelper"` (`app/build.gradle.kts`).

Unchanged:

- Kotlin namespace, packages, `R`/`BuildConfig` package: `com.tamerslab`.
- Room schema export path: `app/schemas/com.tamerslab.data.database.AppDatabase`.
- Launcher label: `Tamers Lab`.
- The `vbhelper://auth` deep-link scheme (see consequences).

With the existing `applicationIdSuffix = ".integritycheck"`, the integrity variant becomes
`com.tamerslab.vbhelper.integritycheck` (test package
`com.tamerslab.vbhelper.integritycheck.test`).

## Consequences

- **Separate app.** The APK installs as its own app next to VBHelper and next to older
  `com.github.nacabaro.vbhelper` installs. Neither product updates the other, and neither
  uninstall affects the other.
- **No data inheritance.** A new install starts empty. Migration from the old package:
  in the old app `Settings > Export database`, then in this app `Settings > Import
  database`. Cards and collection rows live in that database, so they move with it.
  Linked game saves (`.sav` files on external storage) are re-linked in the new app.
  Watch connection secrets need no migration: they ship with the build. Theme, language and
  other preferences are re-selected.
- **Distribution.** Release APKs from this repository are a new app identity; users on the
  upstream `com.github.nacabaro.vbhelper` app keep that app until they choose to migrate.
  Release signing must use this project's key (the debug key is not distributable); the
  certificate is independent of the old identity.
- **Deep link collision.** `vbhelper://auth` remains registered by both apps. With both
  installed, Android may show a chooser for the OAuth callback, or route to the default
  handler. Changing the scheme would need a server-side redirect update.
- **No hardcoded authorities to change.** The manifest uses `${applicationId}.provider` and
  the code uses `context.packageName`, so `FileProvider`, the widget and `WorkManager`
  records follow the new id automatically.
- **Test and tooling updates.** Baseline-profile generator, macrobenchmark target package,
  `ExampleInstrumentedTest`, `scripts/test-individuals-android.ps1` and
  `scripts/check-device-individuals.py` were updated; `docs/performance/BASELINE.md` and the
  release checklist now use the new id. Historical plan/log documents (`.plan/`,
  `docs/testing/HISTORICAL_REGRESSION_LOG.md`, `AR_BATTLE_IMPLEMENTATION.md`, older
  `*_IMPLEMENTATION.md`) still mention the old id and describe runs under it.
- **Rollback.** Switching back to `com.github.nacabaro.vbhelper` is another identity change:
  it would abandon data created under the new id (export it first) and re-introduce the
  update coupling.

## Verification required after a build

1. `apkanalyzer manifest application-id app/build/outputs/apk/debug/app-debug.apk` returns
   `com.tamerslab.vbhelper`.
2. Fresh install on a device that also has VBHelper: two launcher entries, no update prompt.
3. `Settings > Export database` on the old app, then `Import database` on the new one,
   restores the collection.
4. Instrumentation targets `com.tamerslab.vbhelper.integritycheck`.

This ADR does not claim any of those device steps was run in the change that introduced it.

## Alternatives considered

- **Keep the old id and only relabel the app.** Rejected: the install/update semantics the
  owner objects to are the package identity, not the label.
- **Keep the old id and ship an automatic migration first.** Rejected for now: the database
  export/import already exists, and the owner wants the separation immediately.
