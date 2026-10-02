# Regression checks

Run from the repository root in PowerShell:

```powershell
.\gradlew.bat test :app:lintDebug :app:assembleDebug --offline --console=plain
.\scripts\test-watch-identity.ps1
python scripts/test-evolution-history-db.py
python scripts/test-offline-battle-art-db.py
```

The app unit-test variant is `integrityCheck`; its explicit task is
`:app:testIntegrityCheckUnitTest`. The root `test` task also checks both NFC
library variants and the DIM reader. The first run may require online dependency
resolution (omit `--offline`).

The offline battle art check executes the production storage/radar DAO queries
against SQLite with local card keys that differ from DiM/BEM numbers. It verifies
catalog identities, custom-card attack isolation, embedded BEM pixels, and the
additive 28→29 migration. `ImportedAttackArtTest` checks the actual parsed attack
IDs, including zero, missing IDs, and BEM-specific sprite boundaries.
`CustomCardAttackArtTest` exercises import/re-import against real Room on Android;
re-importing a matching file restores legacy assignments without recreating owned
individuals. `BattleImpactVisualTest` checks the 180ms flash lifetime (including
paused simulation), per-victim multi-hit deduplication, and scene placement;
billboard GLB tests verify alpha-preserving art without outlines or ground shadows.
Actual depth occlusion and flash timing require Android renderer verification.
`RadarTabNavigationTest` exercises the actual World/Radar composition while
switching to Home, Storage, Dex, Battle, and Settings during animated transitions.
The World destination's entry owns its saved state throughout the outgoing frame;
the test requires Android execution to verify the former missing-back-stack crash.

## Android persistence checks

### Radar living-world foundation

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.world.*" --offline --console=plain
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.screens.worldScreen.RadarPresentationTest" --offline --console=plain
python scripts/test-radar-ecosystem-db.py
python scripts/test-world-interactions-db.py
python scripts/test-living-world-migrations.py
python scripts/test-world-chat-memory-db.py
.\gradlew.bat :app:compileIntegrityCheckAndroidTestKotlin --offline --console=plain
```

The host SQLite checks use the exported v24/v25 schemas and production 24→25
migration SQL to verify anchors, neutral NPC emotion, schema compatibility, and
preserved individual identity/private chat/player trust/recruitment data.
`WorldEcosystemPersistenceTest` additionally exercises real Room migration and
checkpoint reopen on Android. Compiling the Android tests does not execute them.

The interaction checks use the production 25→26 schema, database guards, and DAO
SQL to verify unique participation, protected expiry/recruitment, card/individual
deletion, transaction rollback, result deduplication, recovery, and retention.
`WorldInteractionPersistenceTest` covers the actual Room command repository,
including concurrent reservations, stale input, private-chat claims, and NPC
result isolation. It requires authorized Android execution for runtime evidence.

`RadarCommandGateTest` checks session lease/revision/readiness and transactional
rollback with a fake clock. `RadarPresentationTest` checks snapshot-authoritative
geometry/assets, pose selection, and status priority. Android cases additionally
cover atomic gated reservation/checkpoint failure and the status panel's recovery
controls and layout slot. Verify real GPS freshness/stationary updates, large
fonts, TalkBack, and pending-battle retry on an authorized device/emulator.

The Radar living-world additions occupy schemas 25–28 (26→27 movement/social inputs,
27→28 transcripts/intents/NPC checkpoints). `test-living-world-migrations.py`
compares the production additive SQL with the fresh Room schema and verifies
private history and prior clock/identity preservation. New JVM suites cover
geographic replay/bounds, FP compression/renderer generations, structured
dialogue validation, initial injuries, Radar 2v1, and complete NPC simulator replay.
Actual FP composition, engine-release ownership, group/challenge/team flows,
physical compass/range and workload budgets need authorized Android verification.

`WorldDialoguePolicyTest` verifies reciprocal, unexpired sparring consent, inherited
sparring stakes, attributed refusal, rejected evidence, and the trainer consent
boundary. Coordinator/command tests cover monotonic replay revisions, input tick
ordering, checkpoint rollback, and hidden/frozen input rejection. FP geometry
tests cover the shared close-range display/hit-target offset and missing-frame
fallback. Room cases also cover missed joined-preparation timeout and explicit
process recovery; host recovery checks execute the production database-open SQL.

`WorldWildInitiationPolicyTest` covers unrequested greetings/hostile attacks,
the required owned partner for combat, fresh/ranged/live presence, no offscreen
player encounters, public participant tap lookup, and expiring speech without
exposing private chat. FP cases cover lower/bounded pitch, close-range clearance,
observable handoff, participant spacing, and reduced-motion lunges. The new
`RadarRendererOwnershipTest` checks Compose updates after request/detach without
a second screen action. Room cases cover a single-wild chat, attack claim
transfer/restoration, exactly-once defense results, and active-partner changes
during preparation. Android cases remain compile-only.

The conversation-screen regressions verify public/ready/ranged eligibility,
read-only private/ended/remote contexts, one shared clock across Radar→chat lease
handoff, reversed vertical drag, measured label clearance/edge clamping, and
readable dialogue despite invalid intent metadata. Historical structured replies
are cleaned for display without rewriting stored history. New Android cases
check long labels at large font scale and single-wild sparring handoff; they are
compiled, not executed.

First-person Radar billboarding has geometry regressions for front normals
pointing toward the player's eye at varied positions/heights, projection corners
sharing the mesh's rotated basis, and finite coincident-coordinate handling.
The billboard helper is called only by the FP Radar viewport; the full unit suite
also retains the existing shared sprite-facing regressions.

Unified 1:1 chat and wager cases verify acceptance of the current user turn,
nonlethal agreed duels despite aggressive voice, invitation/refusal boundaries,
friendly opponent retention, saved stakes and winner-specific reaction prompts,
and a private-chat lease that keeps time while disabling unrelated autonomous
interruptions. Room cases cover greeting deduplication into the Talk history,
exactly-once friendly results/memories, and an existing-v29 upgrade to schema 30.
They remain compile-only. The five `test-world-chat-memory-db.py` checks execute
the production migration and verify durable memories after event cleanup,
import provenance, reaction rollback, and individual deletion.

### Latest living-world verification

- Full `:app:testIntegrityCheckUnitTest`: 357 tests reported, zero failures,
  three existing private-fixture skips (354 successful executions).
- `:app:assembleDebug`, `:app:compileIntegrityCheckAndroidTestKotlin`, and
  `:app:lintDebug` pass. Lint still reports advisory/baselined findings.
- Host suites: five Radar migration checks, 16 interaction checks, and three
  living-world migration checks, and five chat-memory checks pass (29 total).
- `git diff --check` passes.

**Device acceptance is deferred at the user's request.** The authorized isolated
emulator attempt could not boot: no connected device/AVD, no installed hypervisor
driver, and the available system-image folders lack the kernel/system image.
No APK was installed. Android instrumentation, first-person screenshots,
TalkBack/large-font checks, compass/range checks, and resource/workload measurements
have not run. These require a separately authorized, working runtime environment.

For the gameplay-feedback recheck, exercise FP↔2D↔battle and private-chat return
without another manual tab/screen tap; drag pitch at near/far distances; toggle
labels and World tabs; tap overlapping participants and speech/battle markers;
observe changing HP while the scene stays visible; and let local wilds greet or
attack without an Accept prompt. Check preparation failure, changed active
partners, repeated outcomes, stale/out-of-range fixes, and background/replay.
Friendly sparring and voluntary team intervention retain invitation/selection.
Conversation taps now open the normal full-screen chat layout, including group
authors/participants when applicable. Verify Radar→chat→Radar and chat→battle
return, GPS freshness/range changes while typing, reversed drag direction,
names/distances fully above sprite bounds at large font sizes, and the screenshot
case of a structured reply with invalid intent metadata: spoken prose should
remain visible, with no JSON envelope or executable invalid intent.
In FP, confirm sprites remain front-facing while moving/turning the view and
while chatting/battling, with labels and touch targets aligned to the billboard.
Verify wild-initiated 1:1 chat and Talk show the same greeting/history/trust,
then offer the screenshot's name/obedience wager. After the Digimon accepts,
combat should start without another acceptance prompt. Check both win branches:
friendly defeats retain the wild; a Hackmon win asks for the promised name, and
a player win acknowledges his promise. After combat, return to the same chat,
verify the result/reaction appears once, and verify later private conversations
can recall the battle without leaking its wager into other NPCs' public prompts.

### Device persistence suite

Connect an Android device through ADB, then run:

```powershell
.\scripts\test-individuals-android.ps1
```

The script verifies the APK package names and installs only
`com.github.nacabaro.vbhelper.integritycheck` and its test APK. It does not update
the primary application. Tests use a separate database and cover storage,
World recruitment, migration, replay protection, interrupted transfers,
VB/BE export profiles and the current VitalWear converter interfaces.

NFC protocol tests use an in-memory transport with the production encryption,
checksums and packet handling. They do not replace validation with a physical
Digivice V. The JVM Android logger shim is confined to test source sets.

Physical-watch identity protection allows only one outstanding exported individual
per watch UID. A second send is rejected before character data is written. Returning
the first individual releases that restriction; retrying the same transfer remains
allowed. Separate watches can have independent outstanding transfers.

Unknown/erased return tokens with an outstanding export, contradictory tokens and
ambiguous legacy backup occupants are rejected before import acknowledgement.
Neither choosing a DIM manually nor matching species/stats bypasses this check.
An exact already-committed receive can be retried without duplicating or resetting
the saved individual. Historical unresolved transfers are preserved for deliberate
recovery, not automatically reassigned to a similar-looking individual. This is a
conservative software safeguard, not a claim that backup-slot firmware behavior
has been verified or corrected.

## Optional private fixtures

Three official-APK integration tests are reported as skipped when these private
files are absent from `app/src/test/resources/com/github/nacabaro/vbhelper/source/`:

- `com.bandai.vitalbraceletarena.apk`
- `classes.dex` extracted from that APK

Synthetic negative-key validation and APK structure tests run without those files.
Do not commit the official APK or its extracted data.

DIM/BEM integration tests accept local images through environment variables:

```powershell
$env:VBHELPER_TEST_DIM = 'C:/path/to/custom-dim.bin'
$env:VBHELPER_TEST_BEM = 'C:/path/to/bem.bin'
.\gradlew.bat :vb-dim-reader:test --rerun-tasks --offline --console=plain
```

Without environment variables they look for `original.bin` and
`BEM_CARD_IMAGE.bin` in the DIM module, and report absent fixtures as skipped.
An explicitly configured path that does not exist fails the test. Sprite changes
are generated in memory; the source images are never modified.

## Lint scope

The pre-existing `app/lint-baseline.xml` remains unchanged. A successful lint run
means there are no errors outside that baseline; it does not mean all historical
warnings or baseline findings have been fixed. Dependency upgrades, resource
removal, and persistence changes should be evaluated separately rather than made
solely to remove advisory warnings.
