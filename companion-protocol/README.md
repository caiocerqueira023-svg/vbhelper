# companion-protocol (`:companion-protocol`)

Kotlin/JVM library for the local-network pairing and listing protocol used between the
Tamers Lab Android app and the desktop `pc-save-companion`. It is deliberately small:
Gson models, a pinned-TLS HTTPS connection helper, bounded reads, and strict validation
of every remote field.

| Property | Value |
|----------|-------|
| Gradle path | `:companion-protocol` |
| Plugin | `org.jetbrains.kotlin.jvm` |
| Package | `com.tamerslab.companionprotocol` |
| Java/Kotlin target | Java 11 |
| Runtime dependency | Gson (version catalog) |
| Protocol version | `CompanionProtocol.VERSION = 1` |

## Public API

`CompanionProtocol.kt` exposes:

| Type / function | Role |
|-----------------|------|
| `CompanionPairing(serverId, host, port, pin, token)` | Validated pairing material. `link()` emits `vbhelper-companion://...`; `parse(link)` accepts only that scheme and exactly the four expected query keys. `toString()` redacts the token. |
| `CompanionProtocol.connection(pairing, path)` | Opens a pinned-TLS `HttpsURLConnection` to a local-network address. Allowed paths: `/v1/sources` and `/v1/saves/<64 hex>`. |
| `CompanionProtocol.listing(bytes, pairing)` | Parses and validates a source listing (version, server id, at most 32 unique sources, 1–160 char names without control characters, 1 byte–64 MiB sizes). |
| `CompanionProtocol.bounded(input, max)` | Reads at most `max` bytes; refuses overlong input. |
| `CompanionProtocol.MAX_LISTING_BYTES` | 256 KiB hard cap for a listing response. |
| `CompanionProtocol.sha256`, `SOURCE_ID`, `local(address)` | Hash/source-id helpers and the local-address predicate. |
| `RemoteSave(id, name, bytes)` | One discovered save source. |
| `CompanionListing(version, serverId, saves, truncated)` | Validated listing payload. |

The library is JSON/Gson, not protobuf. The app's `vitalwear` and `security` protobuf
schemas are unrelated to this transport.

## Security contract

- The certificate **pin** is the peer identity; the host/address is only a locator.
  `connection` rejects non-local addresses and disables redirects.
- The bearer token is sent only in the `Authorization` header of a pinned connection.
- Timeouts are 10 s connect / 15 s read; listing size, source count, name length, and
  source size are all bounded before use.
- Pairing links grant read access and contain the token; they must not be logged,
  committed, or pasted into issues.
- The transport is read-only. There is no write endpoint and no game-save mutation in
  this protocol.

## Consumers

- The app's `transfer/data/gameSave/LocalCompanionClient.kt` uses the pairing/listing
  types to refresh linked sources.
- `pc-save-companion`'s `ReadOnlyCompanionServer` serves the same contract over pinned
  HTTPS. See [pc-save-companion/README.md](../pc-save-companion/README.md).

## Build and test

```powershell
.\gradlew.bat :companion-protocol:test
```

The test suite covers pairing-link round-trips and redaction, duplicate-field/scheme
rejection, local-address restrictions, and the bounded-read guard. It has no Android
dependency and needs no device.

## Status

The module is a leaf dependency of both the app and the PC companion. Build/test
verification is **not yet re-verified after the Phase 6 restructure**; see
[docs/INDEX.md](../docs/INDEX.md) for the current documentation and verification state.
