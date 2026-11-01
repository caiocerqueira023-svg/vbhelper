# Security review and sign-off record — Alpha 0.7.0

Date: 2026-10-31
Scope: the post-Phase-6 refactor tree (uncommitted), Phase 4.6 findings and their
current status.
Related: [PHASE4_DECISIONS.md](../refactor/PHASE4_DECISIONS.md) (original findings),
[release checklist](../release/RELEASE_CHECKLIST_0.7.0.md) §8,
[ADR 0004](../adr/0004-unified-battle-domain.md).

## Status

**Not signed off.** This document records the technical review evidence. The owner /
second reviewer sign-off required by the release checklist is still pending; nothing
here should be read as an approval to publish.

No security-sensitive feature code changed in the Phase 5–7 work beyond R8 keep rules
and the documented quality/lint changes. The review below re-verified the existing
controls against the current tree.

## Controls verified in the current tree

| Control | Evidence | Status |
|---|---|---|
| Cleartext denied by default | `app/src/main/res/xml/network_security_config.xml`: `<base-config cleartextTrafficPermitted="false" />` plus a 4-host allowlist (`battle.io-void.com`, `auth.nacatech.es`, `localhost`, `127.0.0.1`); manifest sets `android:usesCleartextTraffic="false"` | Verified |
| Backup/device-transfer exclusions | `backup_rules.xml` and `data_extraction_rules.xml` exclude `datastore/secrets.pb`, `battle_auth_preferences.preferences_pb`, `llm_settings.preferences_pb`, `logs/` | Verified |
| FileProvider not exported | Manifest: `${applicationId}.provider`, `android:exported="false"`, `grantUriPermissions="true"`, scoped `provider_paths.xml` | Verified |
| Exported components are intentional | Manifest: `MainActivity` exported (launcher; `vbhelper://auth` VIEW; localhost/127.0.0.1:8080 `/authenticate` VIEW; VitalWear `ACTION_SEND` import) and `DigimonWidgetProvider` exported (APPWIDGET_UPDATE only). No other exported components. | Verified; residual risks below |
| No committed secrets/keystores | No `secrets.pb`, `*.jks`, or `*.keystore` under the repository outside build output; backup rules exclude the secret stores | Verified |
| No secret-bearing log statements found | Static scan of `app/src/main/java` for `Log.*`/`println` with token/secret/password/card/decrypt patterns | No matches that log secret material; see noise note |
| Online PVP is feature-gated | `BuildConfig.ONLINE_PVP_ENABLED` via `battle/online/OnlineBattleFeature` | Verified |
| Auth tokens not in cloud backup | Excluded in both backup rule files | Verified |

## Residual risks (tracked, unchanged from Phase 4)

1. **Cleartext legacy hosts.** The battle/auth APIs on `battle.io-void.com` and
   `auth.nacatech.es` are still allowlisted for cleartext HTTP; migrating to HTTPS needs
   a server-side rollout.
2. **Plaintext secret storage at rest.** Battle auth tokens and imported NFC/LLM secrets
   are stored in app-private DataStore files without Android Keystore wrapping; backup is
   excluded, but device-local extraction by a privileged attacker is not mitigated.
3. **Custom callback scheme.** `vbhelper://auth` is not App-Link verified; the server
   should add `state`/PKCE binding.
4. **Exported VitalWear import.** `MainActivity` intentionally exports the
   `ACTION_SEND` import for `application/x-vitalwear-character`; content is accepted
   without an additional confirmation dialog.
5. **Companion compatibility activities.** Companion import/validation activities remain
   undeclared compatibility code; reachable scan/transfer paths do not depend on them.
6. **Log noise.** Several legacy `println` calls remain in battle sprite/audio helpers
   (paths, frame numbers, character ids, error messages). They do not contain secrets,
   tokens, or save bytes, but they are unpolished and should move to Timber or be
   removed in a cleanup pass.

## Sign-off

| Role | Name | Date | Verdict |
|---|---|---|---|
| Technical review | refactor agent (this pass) | 2026-10-31 | Pending owner decision — evidence recorded above |
| Owner / second reviewer | — | — | **Pending** |

Sign-off requires: confirmation that the residual risks are accepted or scheduled, that
no new secrets are logged, and that the cleartext allowlist is unchanged or justified.
The release remains **not approved for publication** until this table is filled.
