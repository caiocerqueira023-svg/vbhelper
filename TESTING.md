# Regression checks

Run from the repository root in PowerShell:

```powershell
.\gradlew.bat test :app:lintDebug :app:assembleDebug --offline --console=plain
.\scripts\test-watch-identity.ps1
python scripts/test-evolution-history-db.py
```

The app unit-test variant is `integrityCheck`; its explicit task is
`:app:testIntegrityCheckUnitTest`. The root `test` task also checks both NFC
library variants and the DIM reader. The first run may require online dependency
resolution (omit `--offline`).

## Android persistence checks

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
