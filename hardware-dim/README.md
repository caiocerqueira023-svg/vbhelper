# hardware-dim (`:hardware-dim`)

Pure Kotlin/JVM library that parses and writes DiM / BEM card dumps (`.bin`) for the
Vital Bracelet family. It has no Android dependency, so it runs in unit tests, on the
desktop (`game-save-core` / `pc-save-companion` tooling), and inside the Android app
through the `:hardware` facade.

| Property | Value |
|----------|-------|
| Gradle path | `:hardware-dim` |
| Plugins | `java-library`, `org.jetbrains.kotlin.jvm` |
| Packages | `com.tamerslab.hardware.dim.*` (no Android namespace) |
| Java/Kotlin target | Java 11 |
| Tests | JUnit 5 (`useJUnitPlatform()`) |

## Package map

| Package | Contents |
|---------|----------|
| `card` | `DimReader`, `BemCardReader`, `DimWriter`, `BemCardWriter`, `Card`, `DimCard`, `BemCard` |
| `header` | `DimHeader`, `BemHeader` and their readers/writers |
| `character` | `CharacterStats`, `DimStats`, `BemCharacterStats`, stat readers/writers |
| `adventure` | `AdventureLevels`, `DimAdventures`, `BemAdventureLevels`, readers/writers |
| `fusion` | Attribute and specific-fusion models/readers/writers (including Jogress data) |
| `transformation` | Evolution/transformation requirement models, readers/writers |
| `sprite` | Kotlin sprite model, framing and checksum code (see below) |
| `util` | Byte offsets, NOT-wrapped streams, checksum builders, byte helpers |

## Public entry points

```kotlin
import com.tamerslab.hardware.dim.card.DimReader

val card = DimReader().readCard(inputStream, verifyChecksum = true)
```

- `DimReader.readCard(InputStream, Boolean)` auto-detects a BEM card or a DiM card and
  returns the parsed `Card` model. Set `verifyChecksum = false` only for forensics.
- `DimWriter` / `BemCardWriter` are the write-side counterparts used by the card import
  and inspection tooling.
- `DimHeader` / `BemHeader` and the `character`, `adventure`, `fusion`, and
  `transformation` models expose the decoded card sections to the app.
- `util.DIMChecksumBuilder`, `RawChecksumBuilder`, `ByteUtils`, and the offset streams
  are the reusable primitives behind the readers and writers.

Consumers should read the card once and reuse the immutable result; the readers stream
from the supplied `InputStream` and do not close it.

## Sprite parsing is Kotlin

All twelve sprite parser classes are Kotlin under
`hardware-dim/src/main/kotlin/com/tamerslab/hardware/dim/sprite/`: `SpriteData`,
`SpriteFinder`, `SpriteWriter`, `SpriteWithIndex`, `UnorderedSpriteReader`,
`UnorderedSpriteChecksumHacker`, `SpriteChecksumAreasCalculator`, `SpriteChecksumBuilder`,
`SpritePackageInputStream`, `BemSpriteReader`, `BemSpriteWriter`, and
`SpriteDimentionsWriter` (name preserved from the original API). The legacy Java sprite
classes were removed after the parity tests passed.

Parity is covered by `SpriteReadWriteParityTest`, `SpriteDataRgbTest`, and
`SpriteChecksumAreasCalculatorTest`, plus the fixture-based Java tests in
`src/test/java`. Non-sprite Java remains until it is Kotlinized in a follow-up; do not
add Lombok to new Kotlin code.

## Dependencies and Lombok constraints

From the version catalog: Lombok 1.18.42 (`compileOnly` + `annotationProcessor`, test
scope too), commons-io 2.11.0, slf4j-api 1.7.32 (slf4j-simple for tests),
`at.favre.lib:bytes` 1.5.0, and JUnit Jupiter 5.8.1.

- Lombok is compile-time only; it must not become a runtime dependency of the APK.
- `commons-io` and `slf4j` are ordinary runtime dependencies of this JVM module. The app
  reaches them transitively through `:hardware`; `app/proguard-rules.pro` absorbs the
  `lombok.Generated` and slf4j `StaticLoggerBinder` warnings.
- Do not move Android APIs into this module. If a parser needs Android, it belongs in
  `:hardware`, not here.

## Build and test

```powershell
.\gradlew.bat :hardware-dim:test
.\gradlew.bat :hardware-dim:compileKotlin
```

The module has no instrumentation tests and no device requirement.

## Fixture licensing policy

Retail DiM/BEM card dumps must not be committed. Tests use synthetic R5G6B5/checksum
fixtures; real dumps, if a contributor has licensed ones locally, are supplied only via
the `VBHELPER_TEST_DIM` / `VBHELPER_TEST_BEM` environment variables and are skipped when
absent. Do not paste private card bytes, card-holder data, or roster names into a test,
issue, or screenshot. See
[PHASE4_DECISIONS.md](../docs/refactor/PHASE4_DECISIONS.md) and the optional-fixtures
section of [TESTING.md](../TESTING.md).

## Status

The sprite port is complete and the module is consumed through the `:hardware` facade
([ADR 0002](../docs/adr/0002-hardware-module-merge.md)). Build/test verification is
**not yet re-verified after the Phase 6 restructure**; the suite counts in
[TARGETS.md](../docs/refactor/TARGETS.md) are historical.
