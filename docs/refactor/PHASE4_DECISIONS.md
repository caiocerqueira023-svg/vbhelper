# Phase 4 Decisions

## Upstream resource/test port

| Upstream path | Decision |
|---|---|
| `ToNfcConverterProfileSelectionTest.kt` | Not re-ported. The current converter requires a Room database and participation gate; its behavior is covered by `CustomDimSpeciesPersistenceTest`, `CustomCardAttackArtTest`, and the transfer unit suite. A synthetic test would duplicate those fixtures. |
| `VitalWearCharacterExporterTest.kt` | Not re-ported. The exporter is proto/Android-context bound; transfer persistence is covered by `IndividualPersistenceTest` and the share path is exercised in instrumented smoke tests. |
| Legacy drawable | Not restored; no current caller or resource reference. |
| Ten launcher `.webp` variants | Not restored; replaced by the current PNG/adaptive icon set. |

## DIM sprite fixtures

The Kotlin sprite port is verified with synthetic R5G6B5/checksum fixtures. Real DIM/BEM card dumps are intentionally not committed; the pre-existing `VBHELPER_TEST_DIM` and `VBHELPER_TEST_BEM` environment variables remain the only path for licensed local fixtures. See `TESTING.md` for the fixture policy.

## Companion validation

`ValidatedCardEntity`, `ValidatedCardDao`, and `ValidatedCardManager` remain in `app/companion/validation` because they are Room/app-state concerns. `CompanionValidatedDatabase` was unused and duplicated `AppDatabase` ownership, so it was removed. The duplicate Koin `ValidatedCardManager` binding was removed; the container owns the single manager.

## Security review findings

Fixed in this phase:

- Full auth callback URIs are no longer printed; only scheme/host is logged.
- NFC decrypted bytes, character stats, tag IDs, password-auth results, and full transfer tokens are no longer logged.
- Cloud backup/device transfer now excludes `secrets.pb`, battle auth tokens, LLM settings, and logs.
- Cleartext is denied by default; only the legacy battle host, auth host, and localhost callback hosts are allowlisted in `network_security_config.xml`.
- Online PVP entry is behind `BuildConfig.ONLINE_PVP_ENABLED` / `OnlineBattleFeature`.

Tracked residual risks:

- The legacy battle/auth APIs still use cleartext HTTP on their allowlisted hosts. Migrating them to HTTPS requires a server-side rollout.
- Battle auth tokens and imported NFC/LLM secrets remain plaintext inside their app-private DataStore files; Android Keystore wrapping is a follow-up.
- The custom `vbhelper://auth` callback scheme is not App-Link verified; the server should add `state`/PKCE binding.
- `MainActivity` intentionally exports the VitalWear `ACTION_SEND` import; content is still accepted without an additional confirmation dialog.
- Companion import/validation activities remain undeclared compatibility code; the reachable scan/transfer paths do not depend on them.
