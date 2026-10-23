# Save-reading standard (binding for every Digimon game reader)

Every current and future game reader must uphold this standard. It exists because a reader calibrated on one
save snapshot rejected a perfectly valid save after normal play: it demanded one exact file length and threw
on any record value outside the observed ranges. The user-facing rule is:

> The app reads any save at any point of the game, named in any way, inside any folder, as long as the file
> contains a structurally recognizable save for that game.

## Rules

1. **Reads are content-led.** File name, folder and size never decide support by themselves. Discovery lists
   cheap candidates; the registry probes candidates of plausible size; `read()` decides by structure.
   - `GameSaveReader.acceptsLength` is a cheap gate only and defaults fail-open (`length <= maxInputBytes`).
     Narrow it only when probing a demonstrably foreign size is costly. Multiple decoders must stay
     `AMBIGUOUS_FORMAT`, never a silent pick.
   - Registration order is a contract: broad readers first, specific readers last; when nothing decodes, the
     most specific candidate's failure reason is the one shown to the user.
2. **Container families, not one container.** Accept every wrapper variant the game and its emulators are
   known to produce: raw image, header-prefixed exports, footer-annotated exports (DeSmuME), and
   header + footer combinations. Unwrap to the payload window, or fail with `INVALID_CONTAINER` /
   `UNSUPPORTED_FORMAT`. Unverified wrapper assumptions must remain bounded (exact sizes only) and the
   unwrapped window must still pass full structural validation.
3. **Structure vs. gameplay state.** A hard failure (`UNRECOGNIZED_LAYOUT`, `INVALID_CONTAINER`,
   `INPUT_TOO_LARGE`) is reserved for files with no recognizable structure at all. Anything structurally
   recognizable must be reported, even when content is anomalous: new species IDs, higher level caps,
   missing name terminators, dangling references, unknown flags and implausible values become
   `RecordIssue`s / `ObservationNotice`s with `NEEDS_REVIEW` membership — never a whole-file rejection.
   Unknown text codes stay explicit (`\uFFFD`) and exact bytes are always retained.
4. **Write safety is independent of read tolerance.** Every write path re-derives its own observations from
   exact source bytes under a content revision and independently verifies the bytes it edits. Records with
   review issues are blocked from moves/sends; capability flags stay experimental/false until verified
   in-game. Tolerance in reads must never loosen writes.
5. **Revision binding.** `contentRevision` hashes the exact source-container bytes, including any wrapper,
   because that is what the user actually keeps on disk.
6. **Discovery is per game and content-first.** Known exact container sizes are candidates under any name;
   extension matching (`.sav`, `.dsv`, game-specific patterns) only widens discovery when the folder entry
   reports no size. Wrong sizes are listed only through explicit manual linking, where `read()` decides.
7. **Error reasons stay granular end to end.** `SaveReadFailure` values map to distinct user-visible strings
   (format, container, layout, too large, ambiguous) so a rejection is diagnosable from one screenshot.

## New-reader checklist

- [ ] Container variants enumerated from real files, each covered by a test (raw, wrapped, combined).
- [ ] Gameplay-tolerance tests: anomalous (but structurally valid) saves read successfully and are flagged,
      not rejected.
- [ ] No reachable `SaveReadException` for value/state anomalies inside a recognized structure.
- [ ] `SaveDiscoveryPolicy` entries for the game's sizes (and extensions only for unknown-size entries).
- [ ] Error-code to UI-string mapping for all `SaveReadFailure` values and new `RecordIssue` values in
      EN / PT-BR / JA.
- [ ] Capability flags remain false until in-game insertion/removal is verified.
- [ ] Standard referenced in the game's reader KDoc and in TESTING.md.

## Reference implementation

`WorldDsSaveReader` (`game-save-core/src/main/kotlin/.../WorldDsSaveReader.kt`) with
`WorldDsSaveReaderTest` demonstrates the standard: 64 KiB raw, `0x7C`-prefixed and DeSmuME
footer-annotated containers; per-record `IMPLAUSIBLE_VALUES` flagging; blank-copy vs. shapeless-file
distinction; size-family discovery under any name.
