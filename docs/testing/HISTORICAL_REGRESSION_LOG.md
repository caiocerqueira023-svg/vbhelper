# Regression checks

## NDS Story engine-family refactor (W1, schema 55)

- The Digimon World DS adapter is now the first **profile** of a shared NDS Story family instead
  of a standalone codec. New core types: `NdsStoryProfile`/`NdsStoryRecordLayout`/
  `NdsStorySpeciesData`/`NdsStoryTextCodec` and the `NdsStoryProfiles` registry
  (`NdsStoryProfiles.WORLD_DS`); `NdsStorySaveReader`; `NdsStoryRecordCodec`;
  `NdsStoryCopyIntegrity`; `NdsStoryCopySelection`; `NdsStorySaveEditor` with
  `Ds1SaveEditor` (wrapping the audited `WorldDsPartyEdits` byte windows); and
  `NdsStoryConversionPolicies`. Every container size, copy stride/count, integrity rule,
  record-region/field window, species count and glyph codec comes from the profile.
- `WorldDsSaveReader`, `WorldDsRecordCodec` and `WorldDsCopySelection` remain facades with
  their existing constants and signatures (now sourced from the DS1 profile), so app code,
  desktop evidence and tests keep the same contract. `WorldDsPartyEdits` reads its geometry
  and MD5 reseal from the profile and the shared `NdsStoryCopyIntegrity`; its byte windows
  are unchanged.
- `SaveReaderRegistry.defaultReaders()` builds one reader per registered profile plus Time
  Stranger, and `SaveDiscoveryPolicy` derives its size families from the same registry, so a
  second layout never requires editing the registry. The research inspector is layout-driven
  (`inspect(bytes, profile)`, `readBlock(bytes, base, profile)`, `hasSaveSignature(...,
  profile)`) and the desktop CLI gained `--inspect-nds-story`; the DS1 defaults keep the
  `layout_evidence=ADNE` aggregate summary.
- App plumbing: the ten `== WorldDsSaveReader.GAME_ID` equality sites and the library/send
  destination gates now resolve through `TransferGameCapabilities` (profile + editor
  registry). `SaveDocumentWrite` and the experimental journal size buffers from the profile.
  No UI behavior changed while Dawn/Dusk still have no registered profile.
- New tests: core `NdsStoryProfileTest` (9 cases: generic reader/facade byte-identical
  observation, inspector equivalence, triple/profile resolution, copy-header seal/drift,
  copy-selection equivalence, codec equivalence, editor/conversion-policy resolution,
  profile-derived discovery, facade/profile constant sync) and app
  `TransferGameCapabilitiesTest` (3 cases). Deliberate non-goals of this work item: moving
  the ~700-line `WorldDsPartyEdits` body onto the profile object itself, the Dawn/Dusk
  layout profile/species assets, the reviewed title binding (schema 56) and any new
  reader/writer capability.
- Verification: **191 core**, **155 filtered app JVM** (`source.gameSave`, `navigation`,
  `species`, `screens.transferScreen`), **11 companion** and **22 SQLite** checks passed,
  zero failures; integrity Android-test Kotlin compilation and debug assembly passed. Schema
  stays **55**; no migration was added and no save/ROM fixture is committed. All fixtures
  remain synthetic; this is a refactor checkpoint, not new game support.
- Next gate: the Dawn/Dusk evidence pack (plan W2) needs one real USA save per title,
  controlled before/after pairs and the USA ROMs (or extracted JSON) before a second profile
  can be registered.

## Game-grounded identification and unified emulator footers (schema 55)

- Reader recognition is grounded in the matching decompilation
  (`ReGame-Labs/dwds_decomp`, pinned `91efd17c4c05ce106ce0f973155149d23d9de519`) instead
  of single-snapshot header or value shapes. The user's previously failing save was
  finally obtained (raw 65,536-byte DraStic USA save copied off the phone): its two
  copies are internally consistent, and the actual trip was the `lastId < 0x100000`
  cap â€” the file's lastId is 0xACA7BA4F. It was never a `DSDIGI` problem: neither that
  file nor GameFAQs save 21365 carries the signature (bytes at +0x10 are zero).
  `takeNextId` starts ids at a per-save random base (0xACA7BA00 and 0x37720000 observed,
  both 256-aligned) with the handout count in the low bytes, so the cap was removed.
  `identifiesSaveBlock` now accepts a signature-bearing block when
  `lastId in 1 until 0xFFFFFF00` (`ID_RESERVED`), or without a signature when the
  allocator is sane, at least one record slot carries a live id (0 and -1 are the
  game's empty slots, `addDigimon` / `func_0209ff28` semantics), and at least one piece
  of the game's own bookkeeping names a live record: party 0x20C, farm residents and
  each farm's last-visitor reference (0x3E40 stride 0x6C, +0x44/+0x64), Digimon items
  (0x43D8 stride 0x0C, +0x04) or unk443C. Species/level/stat/EXP deviations still only
  flag records, and a block with every record released and no signature is not claimed
  (indistinguishable from unrelated bytes).
- Real-save verification (temporary harness, since deleted): the user's save read
  through `SaveReaderRegistry` yields 4 records â€” 2 copies Ã— 2 (identities
  `save-local:2896673353` and `save-local:2896673359`, i.e. 0xACA7BA49/0xACA7BA4F) with
  `SAVE_BLOCK_SELECTION_REQUIRED`, exactly a real both-copies-populated card; GameFAQs
  save 21365 copy A (bare 0x7DC0 region) yields 31 records with party anchors and no
  ambiguity notice.
- New tests: random-prefix id sequences with bookkeeping references read like real
  saves (incl. a counter crossing a byte boundary); a plausible allocator without
  matching game bookkeeping (references absent, or only dangling ones) is rejected;
  deterministic unstructured noise at a structured size is rejected; the dangling-party
  fixture now keeps a valid anchor plus a realistic dangling reference. Existing tests
  unchanged: signed fixture and zeroed-header fixture are both recognized; `lastId=0`,
  `lastId=0xFFFFFF00` and a cleared record table are rejected; the research inspector's
  signature-only fixture (`lastId=0`) still fails registry identification.
- Container families: canonical 64 KiB image (the two card copies at 0 and 0x7E00);
  bare **0x7DC0** save region (`SAVE_SIZE`,
  the actual per-copy region the game writes to the card); images behind up to 0x400 bytes
  of wrapper slack, probed at 4-byte steps (a 0x20-byte export-header case is tested);
  anything else is `INVALID_CONTAINER`. Both emulator footers share one 40-byte trailer
  (24 metadata + 16-byte cookie; dsv-tool pinned `b669a8448f6ec192c0bf17c5268a8fa6aff6f5d3`):
  the padded-size field at footer+4 slices the payload for DeSmuME and DraStic alike, and
  DeSmuME's 82-byte snip notice is cosmetic. A damaged notice with a valid cookie is now
  readable (payload revision and blocks identical), a binary-only trailer is accepted, and
  cookie files with versionâ‰ 0, padded overflow or written>padded stay `INVALID_CONTAINER`.
  A notice with no cookie remains a malformed-DSV failure, never a silent raw fallback.
- `SaveDiscoveryPolicy` lists both size families (`SAVE_BYTES..SAVE_BYTES+slack+footer`
  and `FILE_BYTES..FILE_BYTES+slack+footer`) under any name; `.sav`/`.dsv` extensions only
  widen entries whose size is unknown.
- Card-copy geometry corrected from real-save evidence: the copies are not two 32 KiB
  sectors. `allocSaveBuffer` rounds `SAVE_SIZE` up to the card's `0x100` page, so one copy
  occupies **0x7E00** and the second sits directly behind the first. A publicly available
  converted save (GameFAQs save 21365) places its newer copy's money/food/playtime/
  last-id/party/records exactly at +0x7E00, with nothing at +0x8000. `WorldDsSaveReader`
  probes `0`/`COPY_STRIDE` and `WorldDsPartyEdits` derives its bases the same way; reader,
  party-edit and inspector fixtures moved to the real stride.
- Consolidated offline verification passed after the identification fix: **128 core
  tests**, **306 filtered app JVM tests**, **11 companion tests**, debug assembly, integrity
  Android-test Kotlin compilation and companion `distZip`, zero failures. Deliverable:
  `app/build/outputs/apk/debug/app-debug.apk` (233,676,976 bytes, 2026-10-18 12:49). Both
  real saves read correctly through the app path (see above); no phone/emulator run was
  performed, so in-game load acceptance of edited/sent saves and re-linking the user's save
  on the device remain the open acceptance checks.

## Card-copy display collapse and species-mapping continuity

- A DS card carries the same save twice, so every Digimon appeared twice in the save's
  Digimon list and roster counts. Lists now collapse per native identity and container:
  the chosen copy's active record wins, then a review record, then the first copy; records
  without a card copy or native identity pass through untouched. Applied in
  `TransferWorkspaceScreen` (grid), `TransferScreen` (roster, source-card counts) and the
  import selection (`onPreviewImport` prunes selections to visibly shown keys). The raw
  source comparison dialog stays uncollapsed on purpose. New JVM test
  `screens.transferScreen.SaveCopyCollapseTest` (6 tests). The reader still reports both
  copies; the copy-review authority flow is unchanged.
- A reviewed species mapping names the species of one native identity, not of one revision.
  `commitRefresh` now carries a `USER_CONFIRMED` mapping from the previous snapshot to the
  refreshed snapshot when the incoming native species is unchanged (evolution still
  requires a fresh review): `NativeMappingContinuity.carried`, wired after
  `archive.insertSnapshot`. Without this, any refresh silently dropped the mapping, which
  `presentationMappings` only returns for a binding's latest snapshot â€” losing save-list
  sprites and archive materialization hints. New JVM test
  `source.gameSave.NativeMappingContinuityTest` (3 tests) and androidTest coverage
  `GameImportAcquisitionTest.aReviewedSpeciesMappingCarriesAcrossRefreshUnlessTheSpeciesChanged`.
- Newly acquired Digimon are now identified instantly from the game's own data:
  `source.gameSave.WorldDsSpeciesTable` ships the USA ROM's species name table
  (426 entries, extracted by `tools/extract_world_ds_species.py` from a ROM whose SHA1
  matches the decompilation's recorded hash; the asset carries ROM SHA1, table addresses
  and the decompilation revision, no ROM bytes). `NativeSpeciesResolution.tableIdentity`
  resolves native ids for the exact game/profile/codec only â€” a reviewed mapping still
  wins, and unused slots, eggs, pods and names the catalog cannot resolve exactly stay
  unidentified. Save lists and archive tiles show sprites immediately, and the
  species-review dialog is prefilled with a note that the game's own data names the
  species (saving still records the review). New JVM tests
  `source.gameSave.WorldDsSpeciesTableTest` (3 tests) and `NativeSpeciesResolutionTest`
  table cases (335 of 426 ids identify through the shipped catalog). Previously mapped
  individuals keep their sprites, now across refreshes too.
- Verification command extended with `--tests "*screens.transferScreen.*"`; consolidated
  offline verification passed with **128 core / 321 filtered app JVM / 11 companion** tests,
  zero failures, debug assembly, integrity Android-test Kotlin compilation and companion
  `distZip`. Deliverable: `app/build/outputs/apk/debug/app-debug.apk` (233,697,090 bytes,
  2026-10-18 17:22).

## Dual-copy native moves and sends (card copies written together)

- The user's current save has **two populated copies**; the old any-multi-copy
  `COPY_AUTHORITY` refusals blocked every real move even after the copy review. Byte
  analysis shows the copies agree across both record windows, the party tables and the
  0x208 id high-water mark; they differ only in headers/counters (+0x1C is a small
  sequence number and is never touched). The new policy writes the same logical edit to
  **every populated copy at once** and refuses divergence: `WorldDsPartyEdits` operates
  per block (bases = reader `imageBase` + `(saveBlock-1)*0x7E00`), validates each block's
  party/ownership/item references, expects per-block party-position shifts, and checks
  byte conservation over the union of both copies' windows. The writer accepts
  `AMBIGUOUS_SAVE_BLOCK` on raw reads (it proves consistency itself), and the move
  blocker is equally review-agnostic â€” ambiguity describes the copy layout, never the
  record's content, so a copy review is not required before moving. Divergence (missing
  counterpart, differing record bytes, non-party container) reports
  `WorldDsMoveBlocker.COPY_DIVERGENCE` with a new string in all three locales; the send
  blocker dropped its copy check entirely.
- Game-side storage records move too: `WorldDsMoveRecordPolicy` accepts an unreferenced
  live record (`UNASSIGNED` container, `UNASSIGNED_LOCATION` among its issues) besides an
  ordinary party member; ambiguity is ignored on both shapes, and usable-Digimon counting
  dedupes record identities across copies so the raw two-records-per-copy read can never
  pretend the sole party member is two members. `WorldDsPartyEdits` clears just that
  record's slot in every populated copy (no party rewrite, no position shifts), and the
  per-chosen-block guard requires one usable Digimon to remain (party moves additionally
  keep the party non-empty). Divergent copies, item/special-selection references, farm
  residents and duplicate party references are still refused. Live ownership flags at
  +0x108 â€” guest (2), lent (4), locked (8), egg (0x10), cross-DS (0x20) â€” are refused for
  both removal and return, while every other bit, including the unmodeled **0x40** on the
  real save's ordinary stored Poyomon, travels verbatim: removal and insertion copy the
  exact 384 bytes and never clear or add bits. The real save's unreferenced slot-1 record
  (`save-local:0xACA7BA4F`, Poyomon, unk108=0x40; its +0x104=0x10 is the game's own
  `createFarm` bookkeeping and is never touched, no party/farm/item/0x443C reference in
  either copy) was byte-verified and the simulated removal changes exactly 270 bytes, all
  inside the two record windows.
- The send gate now takes exact bytes and identity as sufficient
  (`WorldDsSendPolicy.nativeReturnable`): records stored from the game's storage side
  (`UNASSIGNED` + `UNASSIGNED_LOCATION`, formerly "no native record") are returnable
  again, and the send screen's subtitle ordering was fixed so a missing retained record
  reports `transfer_send_no_native` instead of "receiving is not available yet". The
  workspace archive dialog's **Move to game** and the send screen use the same policy.
- `ExperimentalWorldDsMoveRepository.acquire` compares record content
  (`nativeRecordBase64`/identity/container/position) instead of full data-class
  equality, so post-review records no longer fail against raw re-reads.
  `ExperimentalWorldDsSendRepository.commit` accepts one inserted record per copy
  (deterministic lowest block) and stores the snapshot record ambiguity-free, so a sent
  individual can be sent again later.
- Auto-materialization provenance: `saveMapping` takes an authority (`USER_CONFIRMED`
  default / `GAME_TABLE`), `materialize` accepts both, and moves record `GAME_TABLE`
  when the canonical id came from the game's species table
  (`NativeSpeciesPresentationRepository.sources` returns species + provenance);
  startup `materializeRetainedMappings` still upgrades only `USER_CONFIRMED`.
- Storageâ†’game is reachable without materialization: the Storage tab's archive dialog
  has **Move to game** (navigates to the send screen with the archive `individualId`;
  the Transfer route gained an `individualId` argument), and the transfer workspace's
  storage archive dialog wires the same flow through the hub. The send screen resolves
  a partner (characterId) or a raw archive (individualId).
- The move dialog's fallback (when no blocker object is supplied, e.g. archives) mirrors
  the same review-agnostic shapes, so **Move to game** stays enabled for raw ambiguous
  records too.
- New tests: core `WorldDsPartyEditsTest` (20 tests, incl. dual-copy and storage
  removal, an unmodeled-status-bit verbatim round trip and live-flag refusals), app JVM
  `source.gameSave.WorldDsMoveCopyPolicyTest` (11 tests, incl. `WorldDsMoveRecordPolicy`
  and duplicate-identity counting) and `WorldDsSendPolicyTest` (5 tests), androidTest
  `ExperimentalWorldDsMoveTest` review-then-move, all-copies-written and divergence
  cases. Consolidated offline verification passed with **137 core / 337 filtered app
  JVM / 11 companion** tests, zero failures, debug assembly, integrity Android-test
  Kotlin compilation and companion `distZip`. Deliverable:
  `app/build/outputs/apk/debug/app-debug.apk` (233,764,178 bytes, 2026-10-19 08:27).
- Real-save rehearsal (temporary JVM test against the current private save, not committed):
  the raw dual-copy read decodes 4 records (2 identities x 2 copies), the profile matches,
  the sole party member is correctly refused, the stored record is
  removable/consistent/usable, removal changes exactly 270 bytes inside the two record
  windows and re-reads without the record, and the exact 384-byte return inserts into the
  party in both copies with its identity preserved and only `AMBIGUOUS_SAVE_BLOCK` on the
  readback. This exercises the save-reading standard's rule 4 (writes re-derive from exact
  source bytes and verify independently) end to end on real bytes.
- Write-access recovery: `AndroidSaveDocumentWrite.canWrite` accepts a persisted
  read+write grant on the document URI or the granted tree URI, folder linking persists
  the transfer (write-capable) grant instead of a read-only one, and the workspace info
  dialog shows a **Grant write access** action in all three locales when `WRITE_ACCESS`
  blocks a move, so a save linked before write access was requested can be recovered by
  opening it once more.
  Open acceptance checks unchanged: in-game load of an edited save on the device and
  re-linking after the game itself saves again; the last-usable-member guard keeps the
  user's current one-party-member save from moving that member (sending a storage
  Digimon back first gives the party a second member).

## World DS retained-record send and native insertion (schema 55)

- `WorldDsPartyEdits.insertRetainedRecord` is the first native insert writer: it binds to
  a fresh destination revision, refuses a second authoritative save copy, an already
  taken native identity, a full party or record table, and any record slot that is not
  completely zeroed (a zero ID with nonzero tail bytes is not reusable). It appends the
  identity to the destination party table, raises `lastId` only when the identity exceeds
  it (ID high-water-mark hypothesis, not yet observed in-game; removing the last member
  does not lower it), and re-decodes the candidate requiring every byte outside the party
  table window and the chosen slot to be unchanged. `restoreResearchInsertion` recreates
  the original bytes exactly. Appended order follows the insertion, not the record's
  former party position. Five new core tests cover return-into-own-save slot reuse,
  second-copy high-water raise, non-zero-tail slot and identity refusals, full party and
  full table refusals, malformed records and stale revisions; the full core suite passed
  with **120 tests**. Structural party cap is 10 by table size; the game-side limit is
  unverified.
- Schema **54â†’55** adds `GameEndpointPresence` (individual â‡’ source/document/identity/
  operation, FK cascade). Only a completed, readback-verified send records presence;
  copies of a save in other locations never mark presence. Presence hides the archive row
  and blocks materialize/re-import (`isAway`), and is cleared only by a move back whose
  acquire matches the presence's source or document. The move-back acquire that previously
  refused any away individual now accepts the matching return.
- `ExperimentalWorldDsSendRepository` journals every send in the generalized
  `ExperimentalEditJournal` (PREPARED â†’ BACKED_UP â†’ WRITING â†’ DESTINATION_VERIFIED â†’
  ACKNOWLEDGED, plus ABORTED/RECOVERY_REQUIRED) with the pre-write original retained
  as the durable backup. Blockers: missing/review/stale snapshot, copy authority, write
  access, format, already away, identity taken. Every send re-reads the destination after
  writing and requires the revision, container and inserted record to match before
  committing the archive snapshot, provisional binding, container retention and presence
  in one transaction. Recovery/retry rebuilds the candidate from the retained record and
  only finalizes when the destination already shows exactly that candidate; unreadable or
  different destinations stay recovery-required. No acquisition receipt is created at
  send time (departure, not acquisition); the destination refresh later reviews the
  record normally, and the snapshot trigger emits the usual native-import event.
- Transfer â†’ Send now resolves the stored individual, shows the retained-record and away
  state, enables the World DS destination only when a retained record exists and a save
  is writable/reviewable, offers a per-save choose dialog, and reports completion,
  identity-taken, source-changed and recovery outcomes. Backups for sends appear in the
  move backup export screen alongside moves, and opening a workspace runs send recovery
  next to move recovery.
- Consolidated offline verification passed: **120 core tests**, filtered app JVM tests,
  **11 companion tests**, debug assembly, integrity Android-test Kotlin compilation and
  companion `distZip`, zero failures. Room generated schema 55 and its DDL matches the
  hand-written migration. Deliverable: `app/build/outputs/apk/debug/app-debug.apk`.
- In-game acceptance of a sent save is **not validated**; the `lastId` high-water-mark
  behavior, the true game-side party cap and load acceptance of an inserted record remain
  unverified hypotheses. Production move/send capability flags stay experimental/false
  and no phone/emulator run was performed.

## Save-reading standard: any game state, any name, any folder (schema 55)

- `game-save-core/SAVE_READING_STANDARD.md` is now the binding contract for every current
  and future Digimon game reader: reads are content-led (name/folder/size never decide
  support), container families are unwrapped instead of length-matched, hard failures are
  reserved for unrecognizable structure, gameplay-state anomalies become review issues,
  write safety stays independent of read tolerance, revisions hash the exact source
  container including wrappers, and every `SaveReadFailure`/`RecordIssue` maps to a
  distinct user-visible reason.
- Structural changes enforcing the standard: `GameSaveReader.acceptsLength` now defaults
  fail-open (`length <= maxInputBytes`), the registry probes candidates in registration
  order (broad readers first, specific last) and surfaces the most specific failure when
  nothing decodes, and `SaveDiscoveryPolicy` lists any name with an exact known container
  size while `.sav`/`.dsv` extensions only widen unknown-size entries.
- `WorldDsSaveReader` no longer demands one raw 64 KiB file: it unwraps raw, 0x7C-prefixed
  `.dsv`-family (exact size only; the 0x7C inference is unverified and must pass full
  structural validation), DeSmuME footer-annotated, and header+footer combined containers,
  probing the two bounded offsets when both fit. `contentRevision` still hashes the whole
  source container.
- Gameplay tolerance: species/level/aptitude/HP/MP/combat-stat/EXP values outside the
  observed snapshot now flag `IMPLAUSIBLE_VALUES` (record moves to review) instead of
  rejecting the file; missing name terminators are tolerated (up to nine codes decode,
  unknown glyphs stay explicit); dangling party/farm references leave the record listed as
  unassigned; only blank/shapeless files (`UNRECOGNIZED_LAYOUT`) and wrong container
  families (`INVALID_CONTAINER`) still fail. Move/send blockers and the core writers are
  unchanged: review-flagged records remain visible but cannot be moved or sent, and every
  write still re-verifies exact bytes under a fresh revision.
- The rejection class behind the user's post-progress save (exact-size or
  value-range gates rejecting a save that was previously readable) is addressed by
  construction: those gates are gone or converted to review issues. The specific
  user file was never obtained, so its exact tripping check is inferred, not proven;
  re-linking it on a device remains the acceptance check. `INVALID_CONTAINER` and
  `UNRECOGNIZED_LAYOUT` gained their own EN/PT-BR/JA strings so any future rejection
  names its cause instead of one generic message.
- Core suite is now **122 tests**, including container unwrap tests (header, footer,
  combined, via-registry dispatch), tolerance tests (species 900, level 500, max HP 0,
  EXP 100000, dangling party reference, unterminated name) and the updated
  discovery-policy test.
- Consolidated offline verification passed: **122 core tests**, filtered app JVM tests,
  **11 companion tests**, debug assembly, integrity Android-test Kotlin compilation and
  companion `distZip`, zero failures. Deliverable: `app/build/outputs/apk/debug/app-debug.apk`
  (238,034,557 bytes, 2026-10-18 10:33). No phone/emulator run was performed.

## DS-first work and the superseding origin-list specification (2026-10-17)

- Time Stranger transfer development is paused by user request. Actual DS moves begin
  with the World DS / first Story adapter. DigimonNDSRomEditor is pinned at
  `cc3ff499edfd86e382e724d1da7f155b6b830e5f` as the requested Dawn/Dusk ROM-format
  reference; its ROM offsets are not treated as World DS persisted-save offsets.
- `WorldDsPartyEdits` adds in-memory structural party removal and exact return. It
  retains all 384 native bytes, changes only the party-reference table and removed
  record, compacts surviving party positions and verifies unchanged surviving records.
  Return is revision-bound and must recreate the original bytes exactly. Six new tests
  pass, covering either copy, edge/middle removal, stale files, last usable party
  member, ambiguity, farm/guest/duplicate references and item/special references. The
  full core suite passed. This is not general insertion or game-load acceptance;
  native integrity remains unverified and production move capabilities stay false.
- An initial PKForge-inspired cover-grid prototype was built and installed. Galaxy
  A34 portrait captures show game entries, World DS roster selection and Storage
  browsing without an app crash. The app JVM suite and debug assembly passed. Library
  grouping tests make decoded game identity override filename hints and keep other
  games from becoming World DS by filename. The current source adds save-count plurals
  and checkbox state semantics. Landscape/large-font acceptance is not claimed: one
  capture caught rotation and the later device capture showed the sleeping display.
  The original device font scale (1.1) and rotation (0) were restored.
- **The latest user instruction supersedes the cover-grid prototype:** a single origin
  list including Vital Bracelet and every supported origin; actual DS launcher icons;
  automatic species matching and ordinary app sprites; tap individual -> information ->
  one-action actual Move to Storage, preserving nickname and progress, with no routine
  preview/mapping/materialization sequence. Storage can send to any supported game
  supporting the species, independently of origin. These are now recorded in the plan,
  Product, Design and the surface brief; the final UX/native move is not yet implemented.

## Transfer entry: bottom-bar removal, hub-only routing and empty-state crash fix (window)

- The bottom bar no longer carries a Transfer tab: primary destinations are Home,
  Storage, Dex and World, and the overflow sheet lists DigiLine, Quests, Items, Battle
  and Settings. Everything transfer-related lives behind the Home top-left button, which
  opens the Transfer hub (its Game-saves section embeds the former Transfer screen and
  its bracelet card opens the original Vital Bracelet transfer). The bare `Transfer`
  route was the old tab's crash (destination not found, since only the parameterized
  `Transfer?section=...` route is registered); the top-level resolver now maps
  `Transfer?...` and `Scan/...` back to the previous top-level destination or Home.
- Second, unrelated crash when opening the hub: `CyberEmptyState` put a
  `Column(verticalScroll(...))` inside itself, and the hub's empty state renders as a
  `LazyColumn` item â€” lazy items are measured with infinite maximum height, so the
  scrollable node threw `IllegalStateException: Vertically scrollable component was
  measured with an infinity maximum height constraints`. The component now applies its
  internal scroll only when measured with a bounded height (`BoxWithConstraints`
  guard), which also protects the other empty-state call sites that sit in lazy items.
- On-device verification (SM-A346M, debug build installed over wireless adb, 2026-10-17):
  Home top-left button opens the hub with the "No linked saves yet" empty state and
  buttons (link file/folder, refresh, connect local PC) and the bracelet card; the
  bottom bar shows Home/Storage/Dex/World/More; More opens the expected sheet without
  Transfer; the Dex tab opens the card archive; the crash log stayed empty through the
  whole pass. `:app:compileDebugKotlin` and `:app:testIntegrityCheckUnitTest` passed.

## Experimental inactive-tail reactivation candidate (schema 53)

- Corpus analysis of all seven authorized slots shows the roster block is stably
  partitioned: a contiguous active prefix, then a contiguous tail of fully intact
  inactive records (species, level, name, identity hash and chain fields preserved; the
  stored roster index continues the bank numbering), then ~970 zeroed slots. The forward
  chain runs through the tail in slot order and terminates on a zero-hash, zero-pointer
  final record. See the format notes for the full evidence.
- `TimeStrangerEdits.activateInactiveIndividual` (`TimeStrangerWrite.kt`) flips exactly
  the stored flag of the first inactive bank record with an intact identity, so the
  candidate's active prefix is contiguous â€” the shape the game itself writes â€” and
  refuses active, party-boundary, identityless, mid-tail, farm and unknown targets. It
  re-decodes the candidate, requires every byte outside the 4-byte flag word to match and
  requires the re-read to show the same record as active with unchanged stored fields.
  `--edit-activate <save-file> <output-dir> <inactive:N>` is the desktop-only builder
  with the same output-directory, no-source-write and aggregate-only-output contract.
- Desk verification on a copy of the authorized slot-0004 save: `diff_bytes=1
  diff_outside_window=0`, `reread_active=true`; independent tooling confirms exactly one
  changed byte (the flag word, 00->01) with the chain, roster indexes and every other
  record untouched. Two new core cases bring core to **106 tests**; in-game acceptance
  of the candidate is pending user action. No capability flag changed, and the
  transformation is an experiment, not observed game output.

## Rung-2 rename writer: game-equivalent candidate on controlled evidence (schema 53)

- Controlled `name-test` Before/After samples established the game's own rename
  semantics: the record's name window is overwritten from the name offset with the new
  UTF-8 bytes plus one terminator and the remaining window bytes stay untouched, so
  stale tail residue from previously longer names survives ("Gritaria" -> "Kuzuhamon"
  leaves the older trailing "a"); names resolve only up to the first terminator. Seven
  further copies of the old name in a history-looking structure near 0x226480 were not
  rewritten by the game. Digest searches found no stored MD5/SHA-1/SHA-256/CRC32/Adler32
  or per-block CRC tied to the rename; the only other changed bytes were playtime digits,
  the save timestamp and a small live-state tail at 0xfded0/0xfe1a4/0xfe1ac.
- Core `TimeStrangerEdits.renameIndividual` (`TimeStrangerWrite.kt`) is the first field
  writer: it resolves one active party/bank record from a fresh observation, validates
  the new name (1..31 UTF-8 bytes, no terminator, no control characters), refuses farm
  and review-boundary records, mirrors the game's overwrite-plus-terminator semantics,
  and then re-decodes its own candidate â€” every byte outside the window must match the
  source and the re-read observation must show the new name, so a mistake throws instead
  of producing a document.
- `--edit-rename <save-file> <output-dir> <party:N|bank:N> <name-file>` is the desktop
  candidate builder: selector resolution from the fresh observation, refusal of output
  locations inside the source folder, exact name bytes read from a file (never passed on
  the command line), candidate written only into the provided directory, and aggregate
  output only (`edit=rename ... diff_bytes=... diff_outside_window=0`,
  `writes_to_source=false`). No capability flag changed.
- Desk verification against the game's own rename: applying the extracted 9-byte name to
  the `Before.bin` copy (party:1) produced a candidate that differs from the game's
  `After.bin` only in volatile fields (playtime, timestamp, the live-state runs above) â€”
  the 10-byte name difference of the raw Beforeâ†’After comparison vanished entirely. The
  stale `Gritaria` history copies stayed untouched in both files, and SHA-256 checks
  confirm the evidence copies and the real save were never modified.
- In-game acceptance passed (2026-10-16, user-reported): a desk-built slot-0000 candidate
  (party-position 1 rename, exactly 8 changed bytes in one window run, nothing outside it)
  was swapped into the save folder by the user and the retail game loaded it normally with
  the new name displayed. The tool never writes to savedata; acceptance covers the rename
  scope only (no claim for other fields, sidecars or the live-state tail).
- Five new core cases cover window-only change with re-read equality, game-equivalent
  tail residue, the longest fitting name, invalid names/sizes/terminators/targets and
  the farm plus review-boundary refusals. Consolidated offline verification passed:
  **104 core tests**, filtered app JVM tests, **11 companion tests**, **22 SQLite
  checks**, zero failures; debug assembly, integrity Android-test compilation and
  companion ZIP rebuild succeeded. Schema stays **53**.

## Writer-ladder groundwork: commit journal and byte-preserving rehearsal (schema 53)

- Core `TransferCommit.kt` encodes the section 11 commitment order before any writer
  exists: `TransferCommitPlan` binds one source revision to candidate hashes and
  affected-document descriptions; `TransferJournalRecord` advances strictly
  PREPARED â†’ BACKED_UP â†’ CANDIDATE_READY â†’ WRITTEN â†’ VERIFIED â†’ ACKNOWLEDGED, with
  RECOVERY_REQUIRED reachable only before verification and a mandatory backup revision
  before candidate readiness. Source-representation release is allowed only after
  ACKNOWLEDGED. The rung-1 builder (`bytePreservingRewrite`) is the only intent and
  rejects bytes that no longer match the planned revision.
- `TransferRecovery.decide` resumes only an unwritten destination (destination revision
  still equals the source), completes verification for an already-written candidate and
  otherwise demands manual reconciliation; a destination that changed after this
  operation wrote it is never overwritten blindly. Because a rung-1 candidate equals its
  source, the source check deliberately precedes the candidate check (resuming the no-op
  write is the conservative outcome).
- New `--write-rehearsal <save-file> <output-dir>` CLI command rehearses the pipeline on
  copies: verifies a written backup hash, rechecks the source immediately before the
  "write", refuses an output directory inside the source folder, writes candidate copies
  only into the provided directory, then verifies readback hash and full re-parse
  equivalence before acknowledging. Formats with a container codec must first re-encode
  their decoded bytes byte-identically (rung 1) and report `codec_roundtrip=verified`;
  raw formats report `not-applicable`. Output stays hash-free and aggregate-only and
  reports `writes_to_source=false`. Any failed check stops at RECOVERY_REQUIRED with a
  machine-readable reason.
- The internal deterministic `TimeStrangerContainer` (AES-ECB, decode and encode) is the
  first adapter's rung-1 codec: the real authorized `0001.bin` decoded and re-encoded
  byte-identically, so the rehearsal completed `codec_roundtrip=verified` through the
  full journal to ACKNOWLEDGED. It edits no fields; every capability flag still reads
  false.
- Twelve new core cases cover revision binding, illegal/terminal transitions, mandatory
  backup ordering, release gating, the recovery matrix (resume / verify / manual /
  unreadable / acknowledged rejection), the codec round-trip and the rehearsal happy
  path plus output-location guard. No writer capability is enabled: every production
  `canExecute`, source-remove and insert flag still reads false.
- Both real authorized saves rehearsed successfully: the encrypted Time Stranger
  `0001.bin` and the DraStic World DS `.sav` each verified backup, preflight source
  unchanged, readback and re-parse equivalence, and journal completion to ACKNOWLEDGED
  with two output copies in provided temp directories only. Neither game save was
  modified.
- Consolidated offline verification passed: **99 core tests**, **301 filtered app JVM
  tests**, **11 companion tests**, **22 SQLite checks**, zero failures. Debug assembly,
  integrity Android-test compilation and companion ZIP rebuild succeeded. Schema stays
  **53**; no migration was added. Phone runtime acceptance remains deferred.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Batch destination planning (plan 4.4, schema 53)

- Core `DestinationPreviewPlanner.batch` evaluates 1â€“16 selected origins against one
  destination observation. Each item is the same `DestinationPreview` as the single
  flow, sorted display order follows the selection, and the batch fingerprint hashes
  sorted per-item fingerprints so the same partner set never rebuilds spuriously.
  `canExecute` stays false for the batch and for every item; `commonBlockers` is the
  intersection shown as shared restrictions.
- Two selected origins may not share an individual or a native anchor
  (`IllegalArgumentException`). Two different individuals whose identity evidence
  (native or candidate key) collides get `BATCH_IDENTITY_CONFLICT` on exactly those
  items â€” it never merges, replaces or silently drops a partner.
- `DestinationPreviewRepository.prepareBatch` reads the destination exactly once,
  reloads every origin before and after that read and throws
  `DestinationOriginChangedException` if any anchor or its recorded progress changed
  mid-read. `isCurrent` compares the batch fingerprint; duplicate snapshot ids and
  oversized selections are rejected before any write-capable path could exist.
- The destination dialog adds an optional **Include other partners** checklist
  (capped at the planner bound), a batch summary, shared restrictions and a per-partner
  block with name, format verdict, progress status/counts and item-only blockers. The
  single-partner layout is unchanged, including the fields table. EN/PT-BR/JA strings
  added; the desktop probe remains single-origin and non-executable.
- Four new core cases cover duplicate/anchor/oversize rejection, shared-candidate
  flagging, order-insensitive fingerprint binding and per-item restriction isolation.
  Three new app cases cover the single destination read, mid-read origin change,
  recorded-progress invalidation and rejection of duplicates/oversized batches.
- Consolidated offline verification passed: **87 core tests**, **301 filtered app JVM
  tests**, **11 companion tests**, **22 SQLite checks**, zero failures. Debug assembly,
  integrity Android-test compilation and companion ZIP rebuild succeeded. Schema stays
  **53**; no migration was added. Phone runtime/visual acceptance remains deferred.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Progress-aware destination review (schema 53)

- The core `DestinationPreview` now carries a `DestinationProgressAssessment`.
  It reports one of four statuses â€” progress context unavailable, no recorded
  changes, recorded app changes without a validated mapping, or unresolved
  native changes â€” plus the pending-native-review count, destination records
  whose native bytes changed under the same identity evidence, and whether a
  retained native baseline matches the destination format.
- Recorded changes stay in the app's own semantic units (`training.*`, `vitals`,
  `battles.*`, `trophies`). Reading the preview sums synchronized pending
  progress plus unsynchronized app deltas without advancing receipts, revising
  the journey account or writing any row. Raw EXP channels remain in native
  comparisons; nothing converts between units.
- Anchors are the newest retained native snapshots per individual (bounded at
  16) with original-document and native-progress retention flags. When no
  retained baseline matches the destination game/profile/codec, the report adds
  `NATIVE_ANCHOR_UNAVAILABLE`; no other game's anchor is presented as this
  destination's original.
- New blockers â€” context unavailable, missing anchor, unresolved native queue,
  changed destination record, missing mapping â€” join the preview fingerprint
  together with account revision, latest app delta id, latest native snapshot
  id, recorded vector, sorted pending ids and anchor list. Any later app growth
  or native review invalidates `isCurrent`/re-preparation. `canExecute` remains
  false; `PROGRESSION` is reported unmapped while recorded changes exist.
- The destination dialog renders the progress assessment as its own section and
  excludes those five blockers from the generic blocker list to avoid duplicate
  copy. EN/PT-BR/JA strings added.
- Six new core cases cover unavailable context, retained anchor with an empty
  ledger, unmapped recorded units, native queue/destination-difference
  precedence, fingerprint binding (including order-insensitive pending ids) and
  foreign-game anchors.
- Consolidated offline verification passed: **83 core tests**, **298 filtered
  app JVM tests**, **11 companion tests**, **22 SQLite checks**, zero failures.
  Debug assembly, integrity Android-test compilation and companion ZIP rebuild
  succeeded. Schema stays **53**; no migration was added.
- The desktop `--preview-destination` probe intentionally runs without app
  progress context, so its summaries report `PROGRESS_CONTEXT_UNAVAILABLE` for
  eligible records and stay non-executable; the phone flow supplies the real
  context. Phone runtime/visual acceptance remains deferred.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Unified Home transfer hub and native change review (schema 53)

- Home's upper-left action now opens the same Transfer hub as the primary navigation
  destination. Shared tabs cover **Game saves**, **Storage transfers** and **Devices**.
  Partner-context routes validate IDs/section keys and preserve the legacy Scan route.
  Storage's archive and outgoing-transfer shortcuts lead to the hub; actual archive,
  reviewed mapping/APP creation, destination preview and watch-conversion actions are
  hosted there. Settings/application configuration remains ordinary setup, not individual transfer.
- Devices offers an available outgoing-partner chooser plus receive-only mode, uses
  the existing compatible-bracelet conversion and NFC implementations, and stays in
  the hub on completion/cancel with transfer status retained. Tab/partner controls are
  disabled while a read/write is active. NFC payload state is keyed by partner context
  so an old prepared record cannot be reused after selection/availability changes.
- Added core `NativeProgressReview` version 1 and an archive review dialog/repository.
  It compares native records and keeps permanent parameter/EXP-channel differences,
  temporary HP/MP, name/personality edits, location, form/history, identity and unknown
  bytes distinct. Time Stranger raw offsets use record-relative offsets including the
  +4 name base; World DS keeps all eight EXP channels separate.
- Review is not a converter: `canNormalize` stays false; no pending rows, semantic
  accounts, progress receipts or game/app values are changed by viewing it. Raw bytes
  outside mapped fields, unexplained differences, missing data and incompatible codecs
  remain unresolved. UI shows up to 32 owner-scoped pending native changes and total
  pending count, without calling an empty native queue proof of zero app progression.
- Seven new core cases cover native EXP, temporary state, rename-only changes, location,
  form/identity, unavailable/unsupported data and identical records without normalization.
  Two new app JVM cases cover hub/partner routes and invalid arguments. A new real
  SQLite test exercises the exact production pending-review query for owner isolation,
  descending 32-row bounds and zero mutation/receipt generation.
- Consolidated offline verification passed: **77 core tests**, **298 filtered app JVM
  tests**, **11 companion tests**, zero failures/skips. **22 SQLite checks** passed.
  Debug assembly, integrity Android-test Kotlin compilation and companion ZIP rebuild
  succeeded. Schema stays **53**, with no new database migration.
- The authorized controlled rename pair was inspected read-only using the new review:
  **39 conservative unique pairs**, **38 NO_CHANGE**, **1 PERSONAL_EDIT**, **0 changed
  native measures**, **0 normalization-enabled reports**. Aggregate output contains no
  private names, candidate IDs or native record bytes. Native files were not modified.
- Phone runtime/visual/TalkBack and actual NFC acceptance remain deferred. The new
  navigation is source/build verified, not claimed as device-tested. Native semantic
  conversion policies, game writers and real cross-game returns remain unvalidated.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

Verification command:

```powershell
.\gradlew.bat :game-save-core:test :app:testIntegrityCheckUnitTest --tests "*source.gameSave.*" --tests "*navigation.*" --tests "*species.*" --tests "*battle.offline.*" --tests "*NativeSourceContextTest" --tests "*DigimonPersonaBuilderTest" :app:assembleDebug :app:compileIntegrityCheckAndroidTestKotlin :pc-save-companion:test :pc-save-companion:distZip --offline --console=plain --max-workers=1 "-Pkotlin.daemon.jvmargs=-Xmx4g"
```

## World DS provisional party import, copy authority and shared Home (schema 53)

This checkpoint supersedes the earlier schema-52 World DS read-only-acquisition boundary.

- Registered a provisional World DS PARTY-only snapshot policy with source-local ID
  matching. Independent documents with the same local ID are not automatically merged.
  Exact acquisition receipts prevent retry clones; later same-source records use the
  existing identity-confirmed refresh workflow. Farm/unassigned/guest/lent/egg/conflicting
  and other review-only records remain blocked; source insertion/removal stay false.
- Added persistent reviewed copy/revision fields to linked saves and a localized
  **Review save copy** UI. Only the selected copy becomes eligible, and existing record
  issues remain intact. All other copies remain inspectable/review-only. Fresh reads,
  preview preparation and commit recheck authority; changed revisions expire the choice.
  This is reviewed authority, not a verified native checksum or sequence rule.
- Native personality is now nullable in `NativeGameSnapshot`. World DS stores NULL
  plus unchanged native JSON and initializes an app-only trait once from its app UUID.
  Refresh never maps absent native personality onto app edits or regenerates traits.
  Preview/archive/refresh copy distinguishes app personality from missing native data.
- World DS now passes the existing exact-ID reviewed canonical mapping/materialization
  policy. It reuses permanent identity, personality, first-materialization receipts,
  participation/mode/artwork gates and shared DIM Home. Native level range 1..99 feeds
  the existing authored/versioned bounded origin influence; this is app starting-value
  policy, not conversion of the native eight-channel EXP vector or proof of equivalence.
- Schema **52â†’53** preserves native rows, source metadata, canonical mappings, original
  container chunks/bindings, lifetime events/triggers and the autoincrement sequence.
  FK cascades are disabled before the upgrade transaction via the preserving helper,
  and enforcement is restored on open. Migration fixture chains now reach schema 53.
- Desktop checks passed: **70 core tests**, **296 filtered app JVM tests**, **11 companion
  tests**, zero failures/skips. **21 real SQLite checks** passed, including the new native
  nullable-personality upgrade with child-row preservation, trigger restoration and
  an intentionally advanced ID sequence. Debug assembly, integrity Android-test Kotlin
  compilation, companion `installDist` and `distZip` succeeded offline.
- New core tests exercise copy eligibility, stale-revision/profile refusal and choice
  changes without clearing other issues. The new policy test covers deterministic
  app-only traits and source-local identity scope. Android tests for World DS acquisition,
  retry/refresh/personality preservation, independent saves and shared APP materialization
  **compile but have not run on a phone**; physical runtime/UI acceptance remains deferred.
- The exact supplied World DS file was probed read-only with the shared registry/planner:
  **1 eligible main record**, **preview can commit**, **identity review required**, source
  remove/insert both false. No private names, IDs or native bytes are printed or committed.
- Deliverable: `app/build/outputs/apk/debug/app-debug.apk`. Flow: link/refresh the `.sav`,
  review copy if needed, select eligible party records, import snapshots, then Storage â†’
  Game archives â†’ reviewed species/form â†’ Create APP representation â†’ choose partner.

SQLite verification:

```powershell
py -3 -B -m unittest tools.test_participation_schema tools.test_transfer_continuity tools.test_world_ds_acquisition_schema
```

## Phone-local World DS USA/DraStic raw reader (schema 52)

- User supplied an existing, newly created phone-played DraStic save in the private
  `StorySave` evidence folder. It is **65536 bytes**, with one populated save copy at the
  start of the image and an erased remainder (no second copy). The file has **no DSDIGI signature**, so
  the earlier signature-research probe correctly failed and is not the phone reader's
  recognition rule. No private source bytes, names or local IDs entered repository fixtures.
- Registered `WorldDsSaveReader`, profile `world-ds-usa-drastic-raw-v1`, game
  `digimon_world_ds_nds`, codec 1. The exact supplied file reads through the same
  `SaveReaderRegistry` used by Android: **1 active party candidate**, **0 review**,
  **0 bank/farm/unassigned**. Native field ranges, local-ID counter and membership
  references are structurally consistent. Native checksum/global identity remain
  unverified, with explicit notices and all acquisition/write capabilities disabled.
- Phone Transfer now labels this game/profile, decodes supported stored-name glyphs,
  and shows native level/species/position, HP/MP, attack/defense/spirit/speed, aptitude
  and eight separate EXP channels. A newly linked file opens its roster after refresh.
  View-only sources offer browsing without import-selection/actions. Multiple populated
  copies remain distinct review records; no authoritative current block is invented.
- Local/folder/companion discovery recognizes 64 KiB `.sav` files by arbitrary names,
  then validates content. The Android SAF file picker already provides persisted grants,
  bounded reads, cached observations and refresh/relink. No PC is required for local use.
- Shared observations now preserve optional native personality/scalar EXP/talent/bond
  and a separate native parameter object. Legacy Time Stranger JSON remains readable.
  Reviewed acquisition still requires a non-null mapped native personality under its
  compiled policy. Schema stays **52**; source JSON gains no destructive migration.
- **9 new core regressions** cover the observed signature-free shape, exact native data,
  absent fields, both copy positions, multiple-copy ambiguity, farm/unassigned states,
  malformed structures, unknown glyph preservation, native-parameter comparison/fingerprint,
  `.sav` discovery and the final record/independent party order. Fixtures are synthetic.
  **2 new app JVM tests** verify native-parameter/null JSON round-trip and legacy JSON.
- Verification passed: **67 core tests**, **295 filtered app JVM tests**, **11 companion
  tests**, zero failures/skips; `:app:assembleDebug`, integrity Android-test Kotlin
  compilation and companion distributions succeeded. The first consolidated build
  exceeded the 120-second tool window; resuming with a longer timeout completed.
  No phone/emulator instrumentation was run. Android runtime/visual acceptance remains
  separate from the actual-file shared-reader probe and compile checks.
- APK: `app/build/outputs/apk/debug/app-debug.apk`. Phone flow: install/update, open
  Transfer â†’ Link save file, select DraStic's regular `.sav` from an accessible folder
  (for example Documents), then refresh after a normal persisted game save.

## Digimon World DS desktop structural research increment (schema 52)

- Located and pinned published USA (`ADNE`) save-structure evidence in ReGame Labs'
  `dwds_decomp` at `91efd17c4c05ce106ce0f973155149d23d9de519` (MIT). Inspected the
  DeSmuME container description at `06c64b02666a4ac5246aad95fc8e9ab9e69e5a53`.
  The independent parser uses format facts; no decompiled/emulator source or game
  binaries are vendored. Sources and offsets: `game-save-core/WORLD_DS_RESEARCH.md`.
- Added opt-in `WorldDsResearchInspector` for raw backup candidates and validated
  DeSmuME version-0 containers. It finds complete 0x7DC0 `DSDIGI` candidates, keeps
  all candidate blocks separate and reads up to 40 exact 0x180-byte individual records.
  No active block, actual region, checksum, ownership or global identity is inferred.
- Research records retain native stats, aptitude/level, encoded name units, eight
  separate native EXP counters and exact native bytes. There is no fabricated native
  personality, Unicode name, scalar EXP conversion or automatic Bank classification.
  Party/farm references, duplicate IDs, conflicting locations, dangling references,
  guests, lent records, eggs and other-DS flags remain explicit research evidence.
  Farm transfer remains outside scope.
- Inputs are bounded to 8 MiB plus a DSV footer, with at most 32 candidate blocks.
  Recognized damaged DSV footers do not fall back to raw scanning; no$GBA wrappers
  are unsupported. Original-container and unwrapped-payload revisions stay separate.
  Both inspectors use the extracted bounded stream helper, which clears scratch bytes.
- Added `--inspect-world-ds <file>` to the core CLI and packaged PC companion. This
  standalone mode exits offline, creates no companion/pairing state, modifies no
  source and prints aggregate counts/status only. It cannot be mixed with server or
  capture options. The production registry/discovery remains unchanged: no World DS
  roster/import/progress/writer capability has been enabled.
- **13 new core tests** cover native facts/exact-byte preservation, raw/DSV equivalence,
  separate container revision, multiple unselected blocks, membership/identity conflicts,
  guest/lent/egg flags, malformed/truncated/oversized inputs, candidate limits, unsupported
  wrappers, aggregate privacy and exclusion from the production reader registry.
  **3 new companion tests** exercise the actual Main entry point, source preservation,
  option isolation and invalid input without creating server state.
- Consolidated offline verification passed: **58 core tests**, **293 filtered app JVM
  tests**, **11 companion tests**, zero failures/skips. Debug APK assembly and integrity
  Android-test Kotlin compilation passed. Companion `installDist` and `distZip` rebuilt.
  Schema stays **52**. No phone/emulator tests or actual World DS game-save validation
  occurred; an authorized existing save with region/emulator metadata remains necessary.

Verification command:

```powershell
.\gradlew.bat :game-save-core:test :pc-save-companion:test :pc-save-companion:installDist :pc-save-companion:distZip :app:testIntegrityCheckUnitTest --tests "*source.gameSave.*" --tests "*species.*" --tests "*battle.offline.*" --tests "*NativeSourceContextTest" --tests "*DigimonPersonaBuilderTest" :app:assembleDebug :app:compileIntegrityCheckAndroidTestKotlin --offline --console=plain --max-workers=1 "-Pkotlin.daemon.jvmargs=-Xmx4g"
```

## Phone-independent destination evaluation and preview (schema 52)

- User clarified that farm Digimon do not need transfer support. Farm rows remain
  inspection-only, outside transfer scope and not a blocker for party/bank work.
- Added core destination planner version 1, fresh-read Android repository and Storage
  Game archives destination chooser with Party/Bank placement. Reports distinguish
  same-format native return from unavailable cross-format conversion, flag provisional
  identity and possible prior records, and show field-level unvalidated/unmapped data.
  Capacity and version/DLC constraints remain unvalidated. No writer or execution action.
- Fingerprints bind individual/native anchor, exact record facts, destination key,
  revision/profile/codec, placement, capability and restrictions. Preparation rechecks
  the latest local native snapshot after the destination read; World/watch-away and
  superseded snapshots cannot be loaded as local origins. Re-read rebuilds the report;
  cached-source changes/failures are visibly flagged. Drafts are ephemeral, not receipts.
- Ten new core tests cover same-format/cross-format gates, insertion-capability refusal,
  farm exclusion, candidate collisions/review rows, opaque/missing identity, missing native
  data, fingerprint invalidation and aggregate-only desktop probing. Four new app JVM
  tests cover fresh reads, changed source/native anchors, concurrent native refresh and
  unavailable destinations without cached fallback.
- Consolidated offline checks passed: **45 core tests**, **293 filtered app JVM tests**
  and **8 companion tests**, zero failures/skips. App filters: `*source.gameSave.*`,
  `*species.*`, `*battle.offline.*`, `*NativeSourceContextTest`, `*DigimonPersonaBuilderTest`.
  Debug assembly, integrity Android-test Kotlin compilation and companion ZIP rebuild
  succeeded. Schema remains **52**; no database migration was needed.
  The **20 Python SQLite preservation/continuity checks** also passed after the build.
- The actual desktop inspector `--preview-destination` read two authorized existing
  slots without mutation: **41 eligible main records**, **5 excluded farm records**,
  **37 origin records with possible destination matches**, **0 executable previews**.
  All 41 report missing writer/insertion support and provisional identity review.
  No names, native candidate values or bytes appear in the aggregate report or fixtures.
- No physical phone or emulator tests were run, per request. Native layout/TalkBack,
  SAF/Keystore/LAN acceptance and real game-loaded writers/returns remain separate work.

Verification command:

```powershell
.\gradlew.bat :game-save-core:test :app:testIntegrityCheckUnitTest --tests "*source.gameSave.*" --tests "*species.*" --tests "*battle.offline.*" --tests "*NativeSourceContextTest" --tests "*DigimonPersonaBuilderTest" :app:assembleDebug :app:compileIntegrityCheckAndroidTestKotlin :pc-save-companion:test :pc-save-companion:distZip --offline --console=plain --max-workers=1 "-Pkotlin.daemon.jvmargs=-Xmx4g"
```

## Provisional Time Stranger snapshot-copy checkpoint (schema 52)

This checkpoint supersedes earlier view-only/empty-production-registry statements below;
those sections retain historical verification receipts.

- Eleven authorized snapshots (seven slots, two backups and the controlled rename pair)
  were inspected read-only. Occupied main-array candidates were unique per snapshot;
  correlations include form/personality/progression and party/bank-position changes.
  These are corpus observations, not controlled proof of global native identity or
  chronological movement history. Farm/main candidate collisions remain unresolved.
- The production Time Stranger policy now permits provisional PARTY/BANK snapshot
  copies. Planner version 2 limits provisional duplicate scope to eligible active,
  issue-free rows; conservative comparison remains unchanged. Reference preview:
  **41 eligible main records**, commit available with identity review, **5 farm rows
  blocked**, source remove/insert both false. Boundary/tail rows remain review-only.
- Schema **51â†’52** persists draft identity-review requirements and binding confidence;
  legacy bindings default to **PROVISIONAL**. Copies receive app UUIDs and receipts.
  Cross-slot candidate lookup flags possible prior copies, and refreshing provisional
  bindings requires explicit identity-continuity confirmation recorded in the audit.
- Latest verification passed: **35 core JVM tests**, **289 filtered app JVM tests**,
  **20 Python SQLite tests**, and **8 companion tests**; no failures or skips.
  Debug assembly and integrity Android-test Kotlin compilation passed. Companion
  distribution was rebuilt. Android instrumentation tests compiled but were not run:
  no device was connected.
- Artifacts: `app/build/outputs/apk/debug/app-debug.apk` and
  `pc-save-companion/build/distributions/pc-save-companion.zip`.
  Native game writes, actual cross-game returns, farm acquisition and phone/LAN runtime
  acceptance remain unverified. Synthetic round-trip tests are core-invariant evidence.

## Actual controlled Time Stranger rename pair

- User supplied private `name-test/Before.bin` and `name-test/After.bin` copies and
  described one known party-member rename. Both were inspected read-only with the
  shared reader; private save bytes and native identity values were not copied into
  repository fixtures or printed in the report.
- Exactly one old-name record and one new-name record were found, both at **party
  position 1**, **level 86**, and the same record location. The tracked nonzero
  candidate identity was unchanged and occurred **once in each complete decoded
  observation**, including review rows.
- Native species, personality, level, EXP, talent, bond and previous-species values
  were unchanged. Both 336-byte native records differed only at record-relative
  offsets **0x04..0x0D**, inside the name buffer **0x04..0x23**. This supports stored-name
  decoding and rename stability for the candidate in this controlled case; it is
  not a species/form change or progression event.
- Whole-roster comparison: 39 conservative candidate pairs, 4 ambiguous candidates,
  no candidate additions/removals, 2 missing-identity records per observation; exactly
  1 changed stored name and 0 species/personality/location/progression changes.
  Active/review counts remain 41/6 in both copies.
- Added an independently generated synthetic regression for rename-only native byte
  changes, unchanged identity/species/progress and no implicit acquisition capability.
  `:game-save-core:test` passed offline: **33 cases**, zero failures/errors/skips.
- Controlled rename evidence is now recorded. Party reorder, bank/farm movement,
  personality, evolution/de-evolution, duplicate-species, compaction and restored
  branch cases still need their own game-written samples before a general identity
  or writer policy can be enabled. The production verified-profile gate is unchanged.

## Controlled game-written evidence collection (PC companion)

- Added offline `--capture` and `--compare-captures` modes to collect the remaining
  Time Stranger verification evidence. A capture requires one explicitly authorized
  source folder and normal slot filename; it reads/decodes the document, detects
  changed attributes, and preserves an exact encrypted copy under private state.
  No source file, native identity field or ownership marker is modified.
- Capture labels/manifests are immutable. Traversal, symlink sample files, overwritten
  labels and private-state paths inside the active save root are rejected. Manifests
  and reports contain revisions/counts and decoder metadata, not roster names, account
  directory paths or native candidate IDs. Native capture binaries remain private.
- Reports separate normal active-candidate matching from research-only persistence
  across farm/review rows. They include collisions, missing identity evidence,
  changed names/personality/species/positions/progression, raw-record changes and
  regressions. Expected-action flags are advisory user-labelled evidence;
  `establishesNativeIdentity` always remains false.
- Original document hash/size are verified on reanalysis. Reader upgrades are flagged
  without rewriting manifests, allowing captured evidence to survive decoder fixes.
  A changed game identity or tampered native bytes still rejects the sample.
- `:pc-save-companion:test :pc-save-companion:installDist :pc-save-companion:distZip`
  passed offline: **8 tests**, zero failures/errors/skips (6 controlled-evidence,
  2 TLS/server/token-rotation cases). Coverage includes direct packaged Main command
  capture/comparison on an encrypted synthetic layout fixture, returning offline
  without starting a server or creating pairing credentials.
- Companion distribution rebuilt at
  `pc-save-companion/build/distributions/pc-save-companion.zip`.
  Commands and controlled action checklist are in `pc-save-companion/README.md`.
- No private reference save was copied into this repository or used to fabricate
  controlled game changes. Actual game-written before/after samples and their known
  in-game changes are still required to verify native identity/progress policies.

## Consolidated universal intermediary, shared Home and LAN companion (schema 51)

- **Home parity:** removed the separate reduced `AppHomeScreen`. APP/card-independent
  partners render the shared DIM Home composition with the same vitals header,
  interactive portrait, stage/attribute/days, qualitative mood, trophies/countdown,
  battle records, history, favorites, chat/detail actions and mission list. App-owned
  training/rank/item parameters use `AppPartnerProgress`, not fabricated VB/BE rows.
  Schema 51 fills missing APP state/mission slots while preserving existing values.
  APP items now persist their own effects; unsupported use rolls back inventory
  consumption. App wins/vitals update applicable app missions from actual changes.
- **Stat basis:** exact DIM/BEM assignment selects that loaded slot's stage/attribute/
  base HP/BP/AP and unit scale in battle/Home projections. Clearing it restores the
  origin/app basis. Source-native records and progress are independent of that choice.
  Declared source level ranges can supply a bounded, authored v2 origin influence
  (+/-10% before app training); it is frozen from the origin snapshot, stored per
  individual and bypassed by assigned-DIM stats. This numeric bound is an implementation
  policy, not a user-confirmed native conversion formula. Missing origin normalization
  keeps the record archived instead of substituting unrelated default stats.
- **Universal progression:** fixed-point semantic vectors, per-representation native
  anchors and deduplicated observation receipts distinguish actual gains from entry,
  projection, rounding and configuration. App training, vitals/battle changes and
  lifetime trophy gains are recorded independently. Conversion transactions can
  suppress projected changes; observing an unchanged older representation does not
  absorb gains earned elsewhere. Unclassified native changes remain explicitly pending.
  Internal game-progress inputs require a versioned adapter; no real-game progress
  normalizer/writer is enabled by these generic APIs.
- Synthetic A -> Storage/B -> C -> A cases exercise exact original-native-record
  return with no progress despite lossy intermediate scales, one-time actual gains,
  pending per-destination deltas, read-back rebasing and stale/regression rejection.
  These are **core invariants, not proof of game-loaded A/B/C transfers**.
- **Native preservation:** production local/PC transports can capture exact source
  document bytes on acquisition/reviewed refresh. Revision-checked content is stored
  once in <=64 KiB chunks and linked to snapshots, preserving unknown document bytes
  without oversized CursorWindow BLOB rows. This is not a claim that all sidecars,
  writer counts/checksums or native return context have been established.
- **Continuity:** permanent native/app/device-form events survive representation
  deletion. Frozen app projection baselines distinguish authored starting values from
  adopted existing state. App deltas are source-tagged observations, not native EXP or
  physical measurements. Imported/manual personality survives rules upgrades; recovery
  from a missing trait prefers the last reviewed app choice over random regeneration.
  Chat app/native contexts are separately labelled and do not invent source biographies.
- **LAN companion:** new shared `companion-protocol` and Windows/JVM
  `pc-save-companion` modules provide pinned-TLS, token-authenticated, read-only access
  to explicitly authorized save roots. Discovery is bounded and excludes backups/
  symlink files. File reads detect changed attributes, and the shared decoder recognizes
  actual content on the phone. Pairing tokens are encrypted by Android Keystore;
  missing restored keys require re-pairing, not collection reset. Token rotation
  preserves the peer/source identities and revokes old links after server restart.
- **Location lifecycle:** saved tree/PC locations rescan on refresh; per-location
  failure keeps cached sources and does not block already-linked local reads. Explicit
  unlink exclusions win overlapping discovery and are cleared by explicit relinking.
  Stopping discovery retains source/provenance rows; forgetting a PC removes its local
  credentials. Compact navigation is five targets (Home/Storage/Transfer/World/More),
  with Dex reachable through More and existing routes retained.
- Shared codec contracts now accept opaque native identity keys and content-based
  recognition for equal-length formats. This tests format neutrality without claiming
  an unverified Digimon World DS reader. User confirmed local-network access, World DS
  as the next target, and reference counts of 6 party/35 bank/5 farm.
- Consolidated verification:
  - App filtered JVM suites: **289 passed**, no failures/skips (game-save, species,
    offline battle, native-source context and persona builder filters).
  - `game-save-core`: **32 passed**; `companion-protocol`: **4 passed**;
    `pc-save-companion`: **2 passed**. **327 JVM cases total**, zero failures/skips.
  - Companion tests use a real loopback HTTPS server/self-signed identity and verify
    pin rejection, bad-token rejection, write rejection, bounded discovery/read,
    original file-byte preservation, identity persistence and token rotation.
  - `py -3 -B -m unittest tools.test_participation_schema tools.test_transfer_continuity`
    covers old/new schema preservation, independent lifetime history, delta dedup,
    configuration/projection suppression, mission progression, exact DIM stat overrides
    and retained native values: **19 passed**, including lifetime trophy gains across
    phase-counter resets without duplicate awards. Run against the final exported
    schema after KSP; a concurrent run during schema regeneration is not a valid receipt.
  - `:app:assembleDebug`, `:app:compileIntegrityCheckAndroidTestKotlin`,
    `:pc-save-companion:installDist` and `:pc-save-companion:distZip` succeeded offline;
    schema `51.json` is exported. A fixture-only Compose import was corrected during
    integration; production compilation and final native-test compilation passed.
  - New Room checks for container chunk round-trip, lifetime/personality preservation,
    observation namespaces/dedup, projection guards and app-own-anchor accounting,
    plus a shared APP Home layout case, **compile but have not run on Android**.
    No device is connected. Android Keystore/LAN/permission/UI and game acceptance
    are not inferred from desktop or compilation results.
  - The updated reader again inspected the authorized `0001.bin` read-only: 41 active
    candidates (6 party/35 bank), 1 bank-boundary and 5 farm review records. No names,
    native candidate IDs or save bytes were printed or committed as private fixtures.
- Deliverables: `app/build/outputs/apk/debug/app-debug.apk` and
  `pc-save-companion/build/distributions/pc-save-companion.zip`.
  PC startup/pairing/revocation: `pc-save-companion/README.md`.
- Remaining external gates: controlled Time Stranger identity/movement/progression
  saves; concrete World DS region/emulator/fixture; per-title growth/skill/equipment
  mappings and tested writers; actual game-load/recovery tests; Android device and
  phone-to-PC native acceptance. The production acquisition profile registry remains
  empty and the PC service has no game-write endpoint. Neither real-game moves nor
  a real A/B/C return journey are claimed complete.

## Reviewed species mapping and identity-preserving APP materialization (schema 50)

- Storage Game archives now offers exact canonical species/form selection for an
  already acquired native snapshot. `NativeCanonicalMapping` records the native
  species ID, canonical target, `USER_CONFIRMED` authority and review time for that
  exact snapshot. This is a local reviewed reference, not a verified title-wide
  numeric crosswalk. Names, aliases and nicknames are never accepted as canonical
  IDs by the producer; non-Digimon references are rejected. Missing artwork does not
  prevent retaining the reviewed mapping and archived native data.
- `NativeMaterializationRepository` requires the latest acquired snapshot, its
  unchanged reviewed mapping, an enabled verified source profile/identity scheme,
  validated selected artwork and live current-mode eligibility. Away World/watch
  presence blocks creation. Artwork is prepared before the ownership transaction;
  mode revision (including A -> B -> A), snapshot and mapping are rechecked afterward.
- The new APP-factory path preserves the permanent individual, imported/manual
  personality, nickname, creation time and native records. It creates a card-independent
  APP representation with ordinary app starting values, initial app-representation
  history and Dex registration. Native game level/EXP are not used as stage/VP.
  An explicit optional choice can copy the stored source name to an absent app
  nickname; existing app nicknames are protected. No new permanent identity or
  generated personality is created, and the active-partner selection is not taken over.
- Additive 49 -> 50 introduces mapping and one-time `NativeMaterializationReceipt`
  tables. The receipt keeps the original snapshot/canonical/definition/character
  references and projection version after representation export/deletion, so a retry
  cannot recreate a partner or reset progress. Subsequent restore/return behavior is
  a separate lifecycle operation, not replay of first materialization.
- Source archive details remain accessible alongside the resulting APP representation.
  World/watch-away individuals are excluded from the local archive list. Readiness
  follows live mode and imported-card changes. The picker is metadata-only, lazily
  lists canonical entries, retains unavailable records and uses EN/PT-BR/JA copy.
- Selected-asset installation in `ExpandedCatalogRepository.definitionFor` and the
  starter fallback was moved before their definition-creation transaction. Shared
  definition IDs, original card rows, sprite defaults and mode artwork policy remain
  the existing implementation; no whole-catalog asset preparation is added by this flow.
- End-of-work verification:
  - Filtered app JVM suites (`source.gameSave.*`, `species.*`): **82 passed**, zero
    failures/skips, including four new exact-ID/form/kind/missing-art mapping cases.
  - Existing `:game-save-core:test`: **25 cases remain green/up-to-date**.
  - `py -3 -B -m unittest tools.test_participation_schema`: **11 passed**, including
    exported-schema 49 -> 50 comparison, complete old-table preservation, snapshot
    foreign keys and one-time receipt retention/replay rejection after UserCharacter deletion.
  - `:app:assembleDebug` and `:app:compileIntegrityCheckAndroidTestKotlin`: passed
    offline after correcting a fixture attribute from String to the SDK enum. Final
    rebuild after live-card-readiness invalidation also passed; `50.json` is exported
    and existing migration fixtures now include 49 -> 50.
  - Nine new real-Room cases **compile only**: preserved identity/personality/native
    values; mapping without artwork; mode eligibility; deletion/retry protection;
    explicit source-name choice; mode ABA invalidation; changed mapping invalidation;
    disabled-profile rejection; and watch-away rejection/archive filtering. No ADB
    device is connected to execute them or capture native/large-font/TalkBack evidence.
- This does not enable Time Stranger acquisition: the production verified import
  profile registry is still empty pending controlled ownership/identity verification.
  Official numeric crosswalks, source-stat/gameplay conversion and game writers remain
  separate work. The local mapping choice cannot bypass those gates.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Reviewed native refresh and conflict resolution (schema 49)

- Added core three-way name/personality review between the archived native baseline,
  incoming native record and current app values. App nicknames remain distinct from
  stored game names. Source-only personality changes can be recommended; divergent
  app/source edits require explicit keep-app/use-source choices. Keeping app values
  leaves personality metadata untouched instead of regenerating traits.
- `prepareRefresh` requires the same verified adapter policy as acquisition, performs
  a fresh read, checks the revision/selection and resolves every selected record to
  exactly one existing binding. Mixed new, already processed or ambiguous selections
  must be reviewed separately. Bindings, latest native snapshots and app fields are
  loaded in bounded batches; candidate hashes are not promoted to global identities.
- `commitRefresh` rereads the source and rebuilds the review inside the transaction.
  Changes to the source revision, baseline snapshot or reviewed app name/personality
  invalidate confirmation. Resolved choices update only the explicitly selected app
  fields; the same permanent individual receives a new immutable native snapshot and
  per-item receipt. Prior native records, conversations, milestones, history, canonical
  bindings and gameplay stats remain separate. No source save is modified.
- Exact repeated refresh confirmation returns its existing receipt without reapplying
  personality or name changes, including after a subsequent app edit. The native
  snapshot stores a separate review audit with baseline ID, review fingerprint,
  source instance, choices and before/after app values. Additive **48 -> 49** adds
  nullable `reviewAuditJson` without altering source-native record JSON or older rows.
- The eligible import flow now offers Review existing individuals when a verified
  binding is encountered. The review shows baseline/incoming records and localized
  keep-app/use-source choices. Stale reviews require preparation again. Storage
  archives now display the current app nickname/personality alongside source facts.
- Focused end-of-work verification:
  - `:game-save-core:test`: **25 passed**, including five new merge/conflict cases.
  - `:app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.source.gameSave.*"`:
    **6 passed**. Combined JVM coverage for these focused suites: **31 passing cases**.
  - `py -3 -B -m unittest tools.test_participation_schema`: **10 passed**, including
    exported-schema 48 -> 49 comparison, complete old-column preservation, null
    audit defaults and separate append-only native/audit snapshot storage.
  - `:app:assembleDebug` and `:app:compileIntegrityCheckAndroidTestKotlin`: passed
    offline; `49.json` exported and migration fixtures advanced through 49.
  - Five new real-Room refresh cases compile within the **13-case acquisition class**:
    source-only refresh retaining identity and prior native data; explicit conflict
    choices preserving app history/progress; app edits invalidating confirmation;
    idempotent refresh not reapplying personality after later edits; source changes
    rejecting the operation. **Native cases were not executed**: no device is connected.
- EN/PT-BR/JA copy is provided. Native captures, TalkBack/font-scale and actual-device
  timing remain unverified. The production verified-profile registry is still empty:
  Time Stranger acquisition/refresh remains disabled pending controlled occupancy and
  identity evidence. Canonical species mapping, playable representation materialization,
  gameplay conversion and game writers are not enabled by this increment.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Phase D native archival acquisition producer (schema 48)

- Added a transactional `GameImportAcquisitionRepository` for snapshot-copy
  acquisition into an inactive native archive. A compiled verified profile must
  match the exact game/format/codec and provide an explicit native identity scheme
  and personality-number map. Saved previews, UI actions and Debug cannot register
  or override that evidence. The production verified-profile list is currently
  **empty**: Time Stranger still has no acquisition capability.
- Eligible commits reread the source, check the original revision/profile/document,
  recompute the selection/restrictions/fingerprint, and compare decoded native data
  with the reviewed records. One Room transaction creates permanent `IMPORTED`
  individuals, mapped personality traits, source-scoped native bindings, immutable
  native snapshots and per-item receipts. Unknown personality mappings are rejected,
  not randomized. Stored source names remain native names rather than guessed app
  nicknames; unknown canonical species remain unset.
- Exact receipt retries return existing individuals. Additional selections from the
  same revision remain possible. Existing native bindings are checked in a bounded
  batch, including the original document after unlink/reselection; a later observed
  representation requires reviewed refresh instead of a new individual or automatic
  overwrite. Native refresh application/conflict resolution is **not implemented**.
- Additive 47 -> 48 introduces `ExternalIndividualBinding` and `NativeGameSnapshot`.
  Native uniqueness is scoped to source/game/profile/verified identity scheme.
  Bindings and snapshots retain permanent-individual references after source unlink;
  they are not globally unique constraints on an unverified native hash.
- New Storage Game archives panel exposes these source-labelled native representations
  with neutral gray placeholders and inactive accessibility state. No `UserCharacter`,
  fake watch/DIM rows, source-stat-to-VP conversion, evolution events, discoveries,
  gameplay activation or source removal is performed. Canonical gameplay materialization
  remains separate work. Existing watch routes and collection views are preserved.
- Preview commits are available only for a fresh eligible review and a registered
  verified profile. Saved historical reviews have no commit action. Commit errors
  distinguish stale source/selection and identity-review requirements; System Back
  cancels the dialog's coroutine, with Room protecting atomic acquisition.
- End-of-work verification:
  - `:app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.source.gameSave.*"`:
    **6 passed**, zero failures/errors/skips (profile match, explicit personality map,
    missing verification metadata, unknown personality, zero identity, production gate).
  - Existing `:game-save-core:test` **20 cases remain green/up-to-date**.
  - `py -3 -B -m unittest tools.test_participation_schema`: **9 passed**, including
    exported-schema comparison for 48, preservation, scoped identity uniqueness,
    native-snapshot foreign keys and permanent-reference retention constraints.
  - `:app:assembleDebug` and `:app:compileIntegrityCheckAndroidTestKotlin`: passed
    offline; schema `48.json` exported and migration fixtures advanced through 48.
  - Eight new real-Room acquisition cases **compile only**: native/personality
    preservation without gameplay/watch rows and unlink survival; retries/additional
    selection; same-document unlink/reselection; concurrent confirmation; stale input;
    production profile rejection; whole-batch rollback; and bound-identity refresh
    rejection preserving app name/personality. No device is connected to execute them.
- This implements the guarded producer/UI, not validation of real-game acquisition.
  Time Stranger owned counts, identity continuity and numeric species mapping remain
  unverified. Controlled samples and native execution are still required before a
  production profile can be registered. Source writing/managed moves remain disabled.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Archive/commit UI captures,
  accessibility/font-scale and phone performance acceptance remain pending.

## Phase D foundation: revision-bound import review and native snapshots (schema 47)

- Transfer roster rows now support selecting up to 128 records for a snapshot-copy
  review. Preparation performs its own fresh bounded source read; a changed content
  revision invalidates the old selection instead of applying its offsets to a new
  roster. Failed fresh reads never substitute cached content for that required read.
  Selection is scoped to source plus revision. No acquisition or game write is run.
- Shared `SnapshotImportPlanner` preserves native names, numeric species, personality,
  raw progression, locations and restrictions. Format capability, ownership/identity
  research, review-only records, duplicate candidates (including tails/farm collisions)
  and missing native snapshots block proposed acquisition. Missing species mapping
  alone does not discard a confirmed-owned record: eventual archival acquisition must
  keep it source-labelled and inactive. This is planning, not a completed archival
  Storage producer.
- Time Stranger records now retain the exact decoded 0x150/0x158-byte record as
  Base64 in app-private observation/review data. This preserves unknown record fields;
  it is not a full export context or proof of lossless native return. The reader adds
  an explicit identity-research notice, with import/write/remove capabilities disabled.
- Additive **46 -> 47** creates `SaveImportDraft`, per-record `SaveImportDraftItem`
  and `GameImportItemReceipt`. Saved review items retain native bytes and decoded
  values independently of linked-source deletion. Draft fingerprints bind source
  revision/profile/codec/planner version, selection and current receipt/restriction
  outcomes; repeated identical reviews reuse the original draft atomically.
- Successful-import receipts are constrained per source/revision/profile/codec/record
  and require a real permanent individual. The preview path never inserts these
  receipts, individuals, personalities, UserCharacter rows, discoveries or history.
  The schema/DAO and planner receipt handling are implemented; an acquisition commit
  producer, permanent native-identity bindings, inactive Storage representation and
  reviewed-refresh/conflict commit remain later Phase D work.
- Saved import reviews can be reopened from Transfer, including after unlinking the
  source. Historical reviews are explicitly labelled as historical and cannot serve
  as refreshed acquisition confirmations. Preview/restriction copy is EN/PT-BR/JA;
  Time Stranger personality names resolve by the explicit native number, not enum order.
- Verification at the end of implementation:
  - `:game-save-core:test`: **20 tests passed**, zero failures/errors/skips (7 planner,
    7 decoder, 6 identity-evidence comparison). Decoder tests also assert native record
    snapshot lengths; planner tests exercise blocked research cases, receipt-scoped
    retries/additional selection and revision/profile/selection fingerprints.
  - `py -3 -B -m unittest tools.test_participation_schema`: **8 passed**. The new
    real-SQLite 46 -> 47 case compares tables/indexes/FKs with exported Room schema,
    preserves collection data, rejects duplicate/orphan receipts, permits additional
    selected items, and retains review/native data and receipts after source unlink.
  - `:app:assembleDebug` and `:app:compileIntegrityCheckAndroidTestKotlin`: passed
    offline; `47.json` exported and existing migration fixtures advanced through 47.
  - Three new native Room cases compile: idempotent review/native-data preservation
    and unlink survival without acquisition; changed-revision rejection; fresh-read
    failure refusing cached preparation. **Not executed**: no ADB device is connected.
  - The updated reader again opened the authorized reference read-only, yielding
    6 party/35 bank active candidates plus 1 bank-boundary/5 farm review records.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Native UI, large-font,
  TalkBack and phone performance acceptance remain pending. Phase D is partial:
  this delivers previews/provenance/receipt foundations, not the complete import release.

## Linked-save source review and Phase C comparison tooling (2026-10-15)

- Reviewed the linked-save domain, Room migration/DAO, SAF transport, refresh
  repository, decoder and Transfer UI by reading source before running any checks.
  The source pass found incomplete native migration chains, queued duplicate
  refreshes, missing automatic-refresh progress, null-context document detection,
  ignored persistent-grant failures and inaccurate/raw UI status messages.
- Fixed those paths: three concurrent reads at most, per-source overlap
  deduplication and observable busy state, lifecycle-cancelled automatic refresh,
  same-document relink preserving source ID/label/cache, and provider queries with
  cancellation signals and time bounds. Failed reads retain the latest good roster.
  Folder selection is explicitly a bounded discovery action, not a continuously
  linked/rescanned folder location. Local PC connectivity remains unimplemented.
- Transfer now distinguishes initial loading/errors/cached content, renders roster
  rows lazily, and provides read-only comparison between compatible cached sources.
  Status, recovery, containers, records and comparison text use EN/PT-BR/JA resources;
  internal format/error codes and document URIs are not user-facing failure text.
- `IdentityEvidenceComparison` compares candidate evidence across observations with
  the same game/profile/codec version. It records possible name/species/personality/
  location/progression differences without assigning permanent identities. Repeated
  candidates (including collisions with review-only tails/farm rows), zero candidates
  and review-only records cannot produce identity pairs. Neither comparisons nor
  roster browsing acquire partners or merge sources. CLI comparison prints aggregate
  counts only. Fixed exclusion of complete records ending exactly at a region bound.
- End-of-implementation verification:
  - `:game-save-core:test`: **13 tests passed**, no failures/errors/skips (7 decoder,
    6 comparison cases).
  - `py -3 -B -m unittest tools.test_participation_schema`: **7 passed**, including
    real-SQLite 45 -> 46 table/index/FK validation, collection/view preservation,
    duplicate document rejection and isolated observation cascade on unlink.
  - `:app:assembleDebug` and `:app:compileIntegrityCheckAndroidTestKotlin`: passed
    offline; final rebuild after timeout-feedback fixes also passed.
  - Updated five existing Android migration fixture chains through 46. Added five
    real-Room linked-source tests for cache retention/no acquisitions, overlap busy
    state, unlink-during-read, cancellation and same-document access repair. These
    Android cases are **compiled, not executed**; `adb devices` lists no device.
- Authorized local sample inspection used only read streams, with no private
  fixtures copied into the repository and no roster names/candidate IDs printed:
  - `0001.bin`: **41 active candidates** (6 party, 35 bank), **6 review records**
    (1 bank boundary, 5 farm). These are decoder observations, not confirmed ownership.
  - Existing `0001.bin` backup -> reference: 35 possible pairs, 4 ambiguous
    candidates; 7 stored-name/species changes, 1 personality change, 2 location
    changes and 25 progression changes among possible pairs.
  - Reference -> `0002.bin`: 35 possible pairs, 4 ambiguous candidates; 8
    stored-name/species changes, 1 personality change, 2 location changes and 28
    progression changes. Slot order is not asserted to be chronological.
- Phase C's reader/comparison tools are implemented, but acceptance still requires
  in-game party/bank/farm counts and controlled rename/reorder/evolution/branch
  samples. Native hash stability, source lineage, numeric species mapping and farm
  occupancy are unverified. Snapshot imports, save writing and moves remain disabled.
- APK: `app/build/outputs/apk/debug/app-debug.apk`. No new native captures,
  TalkBack/large-font or phone performance acceptance is claimed.

## Multi-game Transfer, phase A/B slice: linked saves and read-only Time Stranger decoding (schema 46)

- New `game-save-core` JVM module holds the shared, UI- and database-free contracts:
  `GameSaveReader`, `SaveReaderRegistry` (bounded reads, oversized-input rejection),
  and an independently implemented read-only Time Stranger layout reader. Main saves
  must be exactly 3,098,176 bytes; inactive bank-boundary/tail rows, unverified farm
  records, and duplicate/zero identity candidates are retained for review, never merged
  or counted as owned. Numeric game IDs are preserved; stored names are never promoted
  into species mappings. Current capability is view-only.
- New Room schema **45 â†’ 46** (`LinkedGameSaveSchema`, preserving factory untouched):
  `LinkedGameSave` plus latest-good `LinkedSaveObservation`. Failed refreshes record
  source health without erasing the last good snapshot; unlinking never deletes
  imported individuals or provenance (no import path exists yet).
- New Transfer tab (bottom nav, alongside the existing Vital Bracelet Scan flow):
  link a save file or folder via Storage Access Framework with persisted grants and
  bounded discovery, refresh on open/resume plus manual refresh, per-source roster
  grouped by party/bank/farm with review states, rename/unlink, and explicit
  view-only capability text. Browsing never creates partners, personalities, or
  discoveries. No Storage import, save writing, or cross-game conversion yet.
- Verification:
  - `:game-save-core:test` â€” **6 passes** (identity-field decoding without save
    mutation, boundary/tail exclusion, duplicate retention, farm-offset handling,
    container rejection, discovery filtering).
  - `py -3 -B -m unittest tools.test_build_dot_sprites tools.test_hatchery_sprites tools.test_participation_schema` â€” **27 passes**.
  - `:app:testIntegrityCheckUnitTest` (species, tamerArena, battle.offline filters) â€” part of
    **279 total cases, zero failures/errors/skips** across 33 suites (incl. game-save-core).
  - `:app:assembleDebug` and `:app:compileIntegrityCheckAndroidTestKotlin` succeeded;
    schema `46.json` exported. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
  - Native Android cases remain compile-only; no connected ADB device is available.
- One online fetch was needed for the new module's Kotlin compiler artifacts; all
  verification above reran offline afterward.

## Species copy semantics and Dex sprite control (October 2026, verified)

- Long-pressing a catalog (JSON) species now opens the template prefilled but
  saves a **separate user-owned copy**: same-name saves are rejected, the
  original stays listed, and existing profiles keep their assignment. Only
  entries the user created are renamed/updated in place (with their profiles).
- The sprite chooser is now reachable directly from the Dex species details
  ("Species sprite" section beside the facts), without opening any editor.
  Base-species appearance is stored as a choice row only, never as a catalog
  override. Added PT/JA translations for the concurrent `game_mode_*` strings.
- **App unit suite: 787 cases, zero failures, three existing private-fixture
  skips** (784 executed) via `:app:testIntegrityCheckUnitTest`.
  Debug/integrity/Android-test APKs assembled; Android-test Kotlin compiled.
- **Lint: zero errors, 282 warnings, seven hints**, baseline unexpanded.
- **Device (Samsung SM-A346M, `.integritycheck` app): 14/14 passed** â€”
  eight `SpeciesCustomizationPersistenceTest` (copy rejection, copy creation
  with untouched original/profiles, owned-entry rename with profile move,
  plus the five pre-existing appearance/migration/browse cases),
  two `SpeciesCatalogUiTest` (blank template; long-press â†’ copy note, blocked
  same-name save, save enabled after renaming), and four
  `DexSpeciesProfileLayoutTest` (including the new sprite-section case).
  The copy test initially used the wrong fixture ID and the Dex case needed a
  scroll; both were corrected without relaxing production behavior.
- Only integrity packages were installed on the device; the regular
  `app-debug.apk` was rebuilt but not installed over the production app.

## Dot matching, mode isolation and loading repair (schema 45)

- Combined three pinned sources: DMC archive, **746 Humulos Digitama Hatchery Color-device animations**
  (6 rosters, September 2026 capture) and **212 Wikimon Color V-Pet animations** (September 2026 uploads).
  Current pack: **1,211 ready Digimon**, **3,368 sets**, **27,419 frames**, **12,995,584 bytes**;
  all 1,662 catalog identities remain. Ten malformed upstream sets remain excluded;
  source revisions, roster snapshots, name-pair evidence and per-set provider/author/device credits are pinned.
  Newly covered examples: **Koemon (Monmon)**, **Red Vegimon (RedVegiemon)**, **Azhdarmon**, **Zeke Greymon (ZeigGreymon)**.
- Broader documented name matching: full Wikimon English regional table (744 pairs) accepted only for
  unambiguous known identities; color qualifiers (Red/Black/...) and X-Antibody spellings match without
  collapsing forms; Kumamon/Mercurymon stage evidence disambiguates Bearmon/Chackmon and Mercurymon/Mercuremon.
- **44 â†’ 45** updates the view so Expanded ignores saved VB normalization and uses
  Dot defaults. Asset-backed portraits, Arena previews/fighters/result forms and saved matches follow
  the same mode policy. Saved VB choices persist and resume in VB mode.
- Arena follows the mode with other-mode fallback: mode-matching teams first, fallback teams last with an
  exhibition-only badge; fallback teams allow exhibitions but cannot enter or resume cups.
- Preparation batches archive reads and database writes, skips installed artwork,
  caches immutable frames, and runs outside Radar mutation transactions. Arena
  indexing no longer reads all image BLOBs just to check renderability.
- Unavailable Storage portraits are grayscale with gray frames/rings, with no
  visible availability message; history access and TalkBack state remain.
- Verification completed:
  - **27 host checks passed** with `py -3 -B -m unittest tools.test_build_dot_sprites tools.test_hatchery_sprites tools.test_participation_schema`.
  - Targeted JVM: species/catalog/artwork plus Arena/Tamer suites passed; full filtered batch
    **271 cases, zero failures/errors/skips** across 32 suites.
  - `:app:assembleDebug` and `:app:compileIntegrityCheckAndroidTestKotlin` succeeded.
    Schema `45.json` exported with identity `cb9bcefb7d8bc6bad3d599a47d31990a`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`. Native tests remain
  compile-only; `adb devices -l` reports no connected device. Actual phone loading
  times, native screenshots and TalkBack behavior have not been measured.

## Schema-44 launch migration repair

- Reproduced a 43 â†’ 44 validation defect with the real migration SQL:
  `CREATE VIEW DisplaySprite AS ...` differed from Room's expected
  `CREATE VIEW \`DisplaySprite\` AS ...`. The earlier host test skipped view
  creation, hiding this mismatch. The migration now uses Room's exact SQL.
- The strengthened host regression installs the real runtime triggers, preserves
  partners, profiles, chat, evolution history, appearance choices and pause
  records through the rebuild, compares the stored view SQL and new table/foreign
  key definitions against schema 44, queries the view, and checks that foreign
  keys and duplicate-individual guards are enforced afterward.
- Native migration fixtures now use the production preserving open-helper
  factory and register the full migration chain through 44. The existing
  41 â†’ 45 case also checks Storage rows, chat, evolution history, view access and
  foreign-key enforcement after upgrade and reopen.
- The strengthened host checks passed in the final batch above. Android runtime
  execution still requires a connected device; host SQLite checks and compilation
  alone do not establish it.

## Expanded mode, milestone 3 step 3: card-independent partners and conversion (schema 44; source/JVM/host scope)

- Shared catalog definitions (`CardCharacter.cardId` optional, no synthetic DIM),
  `UserCharacter.exportCardCharacterId`, `APP` device type with no invented watch
  measurements, full-catalog expanded encounters and quest targets, guided
  first-partner meeting, mode-based artwork defaults, exact-slot bracelet
  conversion with app identity preserved, and expanded onboarding from Home setup.
- Verification (minimal, per scope):
  - `:app:testIntegrityCheckUnitTest` for the `species`, `quests`, `digifarm`,
    `source` and `domain` packages â€” **256 cases, zero failures or errors, three
    pre-existing private-fixture skips**.
  - `py -3 -B -m unittest tools.test_participation_schema` â€” **five earlier host
    sqlite3 checks passed**, but omitted view creation and runtime triggers; the
    stronger regression above supersedes that migration evidence.
  - `:app:assembleDebug` and `:app:compileIntegrityCheckAndroidTestKotlin` â€”
    succeeded; schema `44.json` generated.
  - Native Android cases remain **compile-only**; no connected ADB device is
    available. No lint or full-suite claim. Historical entries below retain their
    original results and do not establish this increment's acceptance.
- Scripted first-partner onboarding, app-training integration and native
  card-free gameplay/conversion acceptance also remain pending; see
  `EXPANDED_MODE_IMPLEMENTATION.md`.

## Expanded mode, milestone 3 step 2: participation enforcement (schema 43; source/JVM/host scope)

- The database-owned `PartnerParticipationGate` is registered in
  `DefaultAppContainer` and shared by repository instances. Participation resolves
  the live current form before the acquisition/last-known `speciesName` snapshot;
  that snapshot is not a permanent current form. Canonical aliases/catalog IDs
  share loaded access, and unidentified imported slots retain VB access.
- Startup mode/DB invalidation reconciliation retains the collection and records,
  clears only ineligible active partners, and preserves a valid active choice when
  another activation is rejected. Storage badges inactive partners; selectors omit
  them; Storage details allow history while disabling gameplay, and chat sending
  is disabled while inactive.
- World/scan/recruitment, eligible quest selections and progress/rewards, live
  fresh/frozen Arena teams and pending brackets, items, activation and watch export
  are gated. Trusted captured mode revisions reach actual quest/recruitment
  transactions. Permanent individual mapping survives replacement Storage IDs
  after a watch round-trip; separate battle/settlement leases block mode switching.
- Farm needs include valid time before the first pause, then freeze. Room
  **42 â†’ 43** adds persistent `ParticipationPause` records for Farm/Adventure pauses
  over restart; Adventure deadlines rebase at resume. Watch import stores source
  measurements without autoactivating an unavailable partner. The **41 â†’ 42** SQL
  explicitly uses nullable `DEFAULT NULL` to match annotated entity defaults during
  chain migration. No destructive reset; current schema export:
  `app/schemas/com.github.nacabaro.vbhelper.database.AppDatabase/43.json`.

### Final verification

- **125 targeted JVM tests passed, zero failures or skips**, covering species,
  quests, Digifarm and `GameModeRepository`.
- `py -3 -B -m unittest tools.test_participation_schema` â€” **four actual Python
  sqlite3 checks passed in <0.1s**: migration, record preservation, nullable defaults
  and remaining Adventure time. These are host checks, not Android Room execution.
- The final affected-package unit run, `:app:assembleDebug` and
  `:app:compileIntegrityCheckAndroidTestKotlin` succeeded after the recruitment
  transaction guard and its regression were added (**36-second incremental batch**).
  Debug APK:
  `app/build/outputs/apk/debug/app-debug.apk`.
- `PartnerParticipationPersistenceTest` has **seven compile-only cases**; none
  have executed on Android. No connected ADB device is available.
- **Source review disposition: ship**, limited to the six reported material findings;
  the final recruitment verdict confirms all six resolved. This is not native approval.
- There is **no new lint or full-suite claim**. Historical entries below retain
  their original verification results and do not establish this increment's acceptance.

The original source-independent partner milestone is still incomplete:
card-independent `UserCharacter` representation, full-catalog encounters,
first-partner onboarding, source-mode default artwork, real external-game/VPet
adapters, permanent universal evolution and compatible non-VB-to-watch conversion
remain implementation work. See `EXPANDED_MODE_IMPLEMENTATION.md`.

### Native acceptance boundary

- Execute the seven persistence cases and real **41 â†’ 42 â†’ 43** Room upgrades,
  validating defaults, preserved records and paused Farm/Adventure state after reopen.
- Exercise live mode/card invalidations, current-form versus old-snapshot resolution,
  alias/ID eligibility and unidentified VB imports. Inspect inactive badges,
  excluded selectors, rejected activation retaining a valid selection, disabled
  gameplay/chat sending and accessible conversation history.
- Verify stale model replies cannot mutate quests or recruit after a mode revision;
  ineligible quest givers/partners/unmet targets and paused progress/rewards stay gated.
- Exercise fresh/frozen teams, pending brackets, watch-round-trip replacement IDs
  and both foreground-battle and pending-settlement mode-switch leases.
- Verify pre-pause Farm time, frozen needs and remaining Adventure time across
  restart/resume; items, activation and export stay gated, while watch import retains
  source measurements without unavailable autoactivation.
- Inspect current native layouts across the inherited palettes and EN/PT-BR/JA,
  including large fonts and TalkBack. **No new native screenshots, TalkBack or
  large-font acceptance** are recorded; earlier native results are historical.

## Expanded mode, milestone 3 step 1: species-binding migration (source scope)

- Added Room **41 â†’ 42** (`SpeciesBindingSchema`): additive `speciesName`,
  `originKind`, `originCardCharacterId` columns on `DigimonIndividual`, with a
  migration-time backfill from the current card slot. `SpeciesBindingRepository`
  performs an idempotent startup backfill and canonical resolution. `IndividualOrigin`
  validates origins. No existing partner, chat, relationship or history is altered.
- Verification executed:
  - `:app:testIntegrityCheckUnitTest --tests "...IndividualOriginTest"` â€” passed.
  - `:app:compileIntegrityCheckAndroidTestKotlin` â€” passed, including the new
    `SpeciesBindingMigrationTest`, which reconstructs the exported version-41 schema
    and opens it through the registered 41 â†’ 42 migration.
  - `:app:assembleDebug` â€” succeeded; schema `42.json` generated.
- **Not executed**: `SpeciesBindingMigrationTest` requires a connected device;
  `adb devices` reported none. The migration SQL and column defaults are verified by
  compilation and schema export only until it runs on hardware. No data-reset is
  required by this change, but existing installs must be upgraded through the
  migration rather than reinstalled.

## Expanded mode, milestone 2: game mode and shared availability policy (source/JVM scope)

- Added `GameMode` + `GameModeRepository` (persisted, defaults to Vital Bracelet),
  the pure `SpeciesEligibility` rule, and `SpeciesAvailabilityIndex` joining the
  bundled canonical catalog with species present on imported cards through the new
  `SpeciesProfileDao.getLoadedSpeciesNames` query. `SpeciesRepository.availabilityIndex()`
  builds the index from real data. Settings now exposes the two modes.
- Verification executed:
  - `:app:testIntegrityCheckUnitTest --tests "...species.*" --tests "...source.*"` â€”
    passed. Nine new JVM cases cover VB eligibility by loaded species regardless of
    origin, rejection when no card provides a species, expanded-mode Dot-art and
    Digimon-only requirements, alias/unknown-species/blank-input resolution, and
    mode persistence/default/fallback.
  - `:app:assembleDebug` â€” succeeded.
- No schema change and no data reset. Individual-level enforcement across owned
  partners, spawns, quests and teams, the first-encounter flow, and the Room
  migration that binds partners to canonical species are the next milestone; see
  `EXPANDED_MODE_IMPLEMENTATION.md`. Native UI/behavioural acceptance still
  requires a connected device.

## Expanded mode, milestone 1: catalog and bundled Dot library (source/pixel scope)

- Added a pinned, generated canonical reference catalog
  (`app/src/main/assets/species_catalog.json`): **1,662** identities, **824**
  Digimon with confirmed Dot artwork, **1,891** validated alternative sets, and a
  **9,525,674-byte** pack of **20,493** unique native pixel buffers
  (`app/src/main/assets/dot_sprites.zip`). Generation is reproducible from pinned
  revisions recorded in the manifest and builder.
- Verification executed for this milestone:
  - `py -3 -B -m unittest tools.test_build_dot_sprites` â€” **10 passes** (alias/form
    identity, stage/Hi-res rejection, deterministic preferred set, stable reserved
    IDs, RGB565+green-chroma conversion incl. visible-green and malformed PNG,
    source-path traversal rejection).
  - `py -3 -B tools/build_dot_sprites.py --check` â€” offline artifact check passed
    over all 20,493 frames with pack and per-frame hashes.
  - `:app:testIntegrityCheckUnitTest --tests "...species.*"` â€” passed, including
    the new canonical-catalog checks and a shipped-asset check that reads the real
    manifest and pack hash, resolves aliases, prefers original device art, and
    keeps non-Digimon references non-ready.
  - `:app:assembleDebug` â€” succeeded.
- New Android persistence checks (`BundledDotSpritePersistenceTest`) were compiled
  but **not executed**: `adb devices` reported no connected device. Native
  execution, the new-tab screenshots, enlarged-font and TalkBack review remain
  pending. Compilation is not visual acceptance.
- **Source review only for UI**: the finish review returned disposition
  **fix** with the following addressed â€” canonical identity used for matching after
  a rename, canonical-name/alias search, filtered-empty vs catalog-empty messaging,
  pagination cancellation scoped to the current request, and installed Dot
  provenance shown in the Loaded list. Native re-capture is still owed once a
  device is available.
- Eight malformed upstream sets (mismatched declared vs actual dimensions, one
  missing source file) were rejected; see `tools/dot_sprite_coverage.json`.
- No database schema change: this milestone needs no migration and no data reset.
  Mode switching, source-independent partners, the first recruitment encounter and
  compatible watch conversion are **not** part of this milestone; see
  `EXPANDED_MODE_IMPLEMENTATION.md`.

## Build repair and species/sprite regression verification (October 2026)

- Reproduced and fixed both reported Kotlin compilation failures: migration bind
  arguments now explicitly use `Array<Any?>`, and the asynchronous preparation
  preview takes a local Bitmap snapshot before the non-null branch.
- Final root `test` task passed. App report: **753 cases, zero failures, three
  existing private-fixture skips** (750 executed). Four new JVM checks exercise
  the twelve-pose ordering and missing/malformed/invalid-dimension fallback.
  DIM/NFC reader test tasks passed through the root task (up-to-date receipts).
- Debug, isolated integrity app and Android-test APKs assembled successfully.
  Room/KSP generated schema `app/schemas/com.github.nacabaro.vbhelper.database.AppDatabase/41.json`.
  The Sprite view reader uses `RewriteQueriesToDropUnusedColumns` for its projection.
- `lintDebug` passed: **zero errors, 272 warnings, seven hints**, existing baseline
  unexpanded. The warning count is not a clean-lint claim.
- Verified **32 distinct Android checks** on the connected Samsung SM-A346M in
  the separate `.integritycheck` application: seven new
  `SpeciesCustomizationPersistenceTest`, two new `SpeciesCatalogUiTest`, five
  `CustomDimSpeciesPersistenceTest`, two `AuditReadinessTest`, one
  `ConditionDetailsTest`, eight `UiRefinementsTest`, three
  `DexSpeciesProfileLayoutTest`, and four `TamerArenaLayoutTest` cases.
- Real Room checks passed for cross-DiM/alias appearance, unchanged original art
  and stats, catalog/choice persistence after reopening, rename/reset, atomic
  rejection of missing artwork, preferred-source deletion fallback, and matched
  versus all-loaded/unassigned browsing. The migration test reconstructs the
  exported version-40 schema and opens it through the registered **40â†’41**
  migration; Room validated the new tables/view and retained the legacy profile.
- Native UI checks verified Add â†’ blank template and species long-press â†’
  prefilled template with a reachable Save. The first batch passed 31/32; the
  remaining selector accidentally matched both search input and list row. It now
  selects the native Button role, and both catalog UI cases passed in the focused
  rerun. No production behavior was relaxed for that test correction.
- Tests installed only the integrity app/test packages. The rebuilt regular APK
  is `app/build/outputs/apk/debug/app-debug.apk`; this pass did not install it over
  the regular app. Full TalkBack traversal, watch/server interactions, every
  rendering surface and tablet/foldable visual acceptance were not exercised.

```powershell
.\gradlew.bat :app:assembleDebug :app:testIntegrityCheckUnitTest :app:compileIntegrityCheckAndroidTestKotlin --offline --console=plain --max-workers=1 "-Pkotlin.daemon.jvmargs=-Xmx4g"
.\gradlew.bat test :app:assembleDebug :app:assembleIntegrityCheck :app:assembleIntegrityCheckAndroidTest --offline --console=plain --max-workers=1 "-Pkotlin.daemon.jvmargs=-Xmx4g"
.\gradlew.bat :app:lintDebug --offline --console=plain --max-workers=1 --no-daemon "-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8"
adb -s <serial> shell am instrument --user 0 -w -e class "com.github.nacabaro.vbhelper.SpeciesCustomizationPersistenceTest,com.github.nacabaro.vbhelper.CustomDimSpeciesPersistenceTest,com.github.nacabaro.vbhelper.ui.SpeciesCatalogUiTest,com.github.nacabaro.vbhelper.ui.AuditReadinessTest,com.github.nacabaro.vbhelper.ui.ConditionDetailsTest,com.github.nacabaro.vbhelper.ui.UiRefinementsTest,com.github.nacabaro.vbhelper.screens.cardScreen.DexSpeciesProfileLayoutTest,com.github.nacabaro.vbhelper.screens.tamerArena.TamerArenaLayoutTest" com.github.nacabaro.vbhelper.integritycheck.test/androidx.test.runner.AndroidJUnitRunner
# After rebuilding/reinstalling the test APK with the corrected selector:
adb -s <serial> shell am instrument --user 0 -w -e class "com.github.nacabaro.vbhelper.ui.SpeciesCatalogUiTest" com.github.nacabaro.vbhelper.integritycheck.test/androidx.test.runner.AndroidJUnitRunner
```

## User species templates, shared sprite appearance and visual setup (source change)

- Added an Add button beside species search, blank/prefilled information templates,
  long-press editing, and ordinary Settings â†’ Species and sprites access. Templates
  persist names, level/type, profile text, moves and an optional shared sprite choice.
- The sprite chooser browses assigned-species or all loaded alternatives, including
  unassigned Sprite rows, with metadata-only 24-row pages, search and raw source
  previews. Save validates identity uniqueness, input bounds and selected artwork,
  then updates local catalog/profile/appearance records transactionally.
- Added Room schema **41** and registered **40â†’41** migration: local catalog and
  sprite-choice tables, canonical appearance keys on SpeciesProfile, and the
  `DisplaySprite` view. The view changes presentation reads rather than original
  Sprite/CardCharacter assignments; missing secondary poses use the chosen idle.
- Display reads now share this projection across Home/Storage, history, Dex/routes,
  scan previews, Adventure, Farm/World and widget data. Asset-backed images, Arena
  portraits, battle base forms and finisher forms also resolve the preference.
  Recognition/re-import comparisons explicitly read original art. A saved choice
  refreshes the widget using its existing generation-safe invalidation hook.
- Inner background arc rotation is now 18 seconds versus the outer 24 seconds.
  The Arena setup shows animated partner portraits, stages and actual battle stats,
  with native format/difficulty choices, context and pinned confirmation.
- **Source review only**, per the request to skip testing. No unit/instrumentation
  tests, compilation, Room migration execution, schema-export generation, lint,
  device screenshots or installation were run for this change. Earlier successful
  reports below predate this work and do not establish current runtime acceptance.

## Arena sprite motion, rotating background and Debug-only partner tags (source change)

- Arena list/detail portraits alternate bundled idle frames 1/2 or imported
  idle buffers. Decoding remains off the UI thread; bundled frame pairs use a
  byte-sized 4MiB cache, and imported frames use the existing preview buffer
  validation. Cadence is staggered per artwork identity. Missing second frames
  remain usable as a static portrait.
- Restored the background partial rings' original 24-second rotation, reading
  animated state inside the isolated Canvas draw block. Both sprite animation
  and background rotation follow the shared foreground/motion preference.
- Partner-origin labels (documented, associated, guest and spirit form),
  combined-partner badges and stat-scaling badges now require the live master
  Debug setting in the Arena briefing.
- Source review only. Tests, builds, device checks and installation were skipped
  as requested; previous results below do not validate these changes.

## Arena entry team confirmation (source change)

- Entering Tamer Arena first opens the existing header-accessible team editor.
  The player can confirm/change partners, format and difficulty, then choose
  "Use this setup" to proceed. Back from the initial confirmation exits the Arena;
  Back from a later header-opened edit returns to the current browsing surface.
- Confirmation state is saved by `TamerArenaScreen`, outside the conditional
  lobby/battle composition, so returning from a fight does not reopen entry setup.
- Source change only; tests, builds and installation skipped as requested.

## Tamer browsing, compact Home mood and chat keyboard layout (source change)

- Kept the Tamer chooser as one flat scrolling list. Removed the pinned chosen
  partner summary, moved team setup to the header, and collapsed filters/reload
  behind a labeled filter button while keeping search visible. Changed searches
  and filters start at the first result; returning from a briefing retains the
  existing list position. First-load/error states reuse `CollectionStatePanel`.
- Removed the numeric mood score from both VB and BE Home cards; the displayed
  value is now only the localized qualitative mood summary.
- Set MainActivity's keyboard mode to `adjustResize` and moved remaining IME
  space ownership to the app content root, where it is consumed before nested
  chat screens lay out their history and composer. This addresses the supplied
  screenshot's composer/keyboard gap without removing the chat draft or controls.
- Source review only. Tests, builds, device checks and installation were skipped
  as requested; earlier results below do not validate these new changes.

## DiM 124 Tamer Arena fallback (source change)

- Arena artwork selection now prefers other available sprites for the same
  canonical species/form. DiM 124 remains eligible only when no alternative
  exists, for both bundled assets and imported cards, including masked BEM IDs.
- Shared Arena species lookup applies the preference to team members, previews
  and Blast/Jogress result forms.
- Tests/builds were not run for this follow-up, as requested. The verified native
  and build results below belong to the preceding visual-uniformity pass.

## Cross-screen visual uniformity (October 2026 native follow-up)

- Extracted shared instrument-style tabs, search fields, framed section bars,
  header actions and spacing in `InstrumentUi.kt` / `TopBanner.kt`. Migrated
  Items, DigiLine, World, Arena navigation, Settings, quests and battle/loadout
  setup to that vocabulary. The binding references remain Home, Storage,
  species-information cards and the rich Tamer briefing, including its emojis.
- Inspected current native VB Lab / English captures on the Samsung SM-A346M:
  Home, populated Storage, Items, Settings, DigiLine, battle entry/preparation,
  Tamer roster/briefing/loadout details, technique and Blast/Jogress tabs, quests,
  and the Agumon species-information dialog. The final comparison caught the
  quest journal's remaining ungrouped layout; its framed objectives/rewards and
  reachable action area were confirmed after the final update.
- Final app unit suite: **749 cases, zero failures, three existing private-fixture
  skips**. Debug, integrity app and Android-test APK assembly succeeded.
- Final `lintDebug`: **zero errors, 268 warnings, seven hints**, with the existing
  baseline unexpanded. This is not a clean-lint claim.
- Verified **18 distinct Android checks** in the isolated `.integritycheck` app:
  two `AuditReadinessTest`, one `ConditionDetailsTest`, eight `UiRefinementsTest`,
  three `DexSpeciesProfileLayoutTest`, and four `TamerArenaLayoutTest` cases.
  They cover loading/retry, visible trust, selected/labeled navigation, restored
  inputs, short windows, 1.3x/2x text, species privacy/footer/profile order,
  and Arena search, challenge, team editing, two-partner briefing and cups.
  Species/Arena fixtures exercise all four palettes; this does not establish a
  visual review of every screen/palette/font combination.
- The first native batch passed 16 cases and exposed two test assumptions:
  exact raw profile whitespace and a challenge tap racing Android IME dismissal.
  Narrative assertions now check both complete sentences, and the Arena test
  checks team readiness/button enablement and waits for the system IME to close
  before the physical tap. Both corrected cases passed; the final focused run
  confirmed the profile case and all four Arena cases (**five passes**).
- Installed the final regular debug APK with `adb install -r`, retaining app data.
  Wireless ADB was recovered after a disconnect; a timed-out integrity APK
  installation completed successfully on retry before instrumentation execution.
- Current manual captures are under
  `C:\Users\julye\AppData\Local\Temp\opencode\uniform-*.png`, including
  `uniform-final-quests.png`, `uniform-final-quests-rewards.png` and
  `uniform-final-species.png`. Native fixture captures remain in the integrity
  app's external-files `dex-profile-layout` and `tamer-arena-polish` directories.
- Full TalkBack speech traversal, predictive Back, physical watch/server flows,
  inspector crash reproduction, launcher/widget performance and tablet/foldable
  acceptance remain outside these executed checks.

```powershell
.\gradlew.bat :app:assembleDebug :app:testIntegrityCheckUnitTest :app:assembleIntegrityCheck :app:assembleIntegrityCheckAndroidTest --offline --console=plain --max-workers=1 "-Pkotlin.daemon.jvmargs=-Xmx4g"
.\gradlew.bat :app:lintDebug --offline --console=plain --max-workers=1 --no-daemon "-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8"
# After installing the integrity app and test APK; use the connected device serial:
adb -s <serial> shell am instrument --user 0 -w -e class "com.github.nacabaro.vbhelper.ui.AuditReadinessTest,com.github.nacabaro.vbhelper.ui.ConditionDetailsTest,com.github.nacabaro.vbhelper.ui.UiRefinementsTest,com.github.nacabaro.vbhelper.screens.cardScreen.DexSpeciesProfileLayoutTest,com.github.nacabaro.vbhelper.screens.tamerArena.TamerArenaLayoutTest" com.github.nacabaro.vbhelper.integritycheck.test/androidx.test.runner.AndroidJUnitRunner
```

## All-screen audit recommendations (October 2026 follow-up)

- Implemented F01â€“F16 using Impeccable's optimize, harden, colorize, native adapt,
  clarify, animate and polish playbooks. Home/Storage identity, four palettes,
  configurable fonts, imported pixel art and recorded gameplay facts are preserved.
- Final app unit run: **749 cases, zero failures, three existing private-fixture
  skips**. The 18 new executed cases cover sprite allocation/buffer boundaries,
  stale asynchronous publication, and conversation-branch revision checks.
- `AuditReadinessTest` adds two Android UI regressions for delayed-first-snapshot
  loading and failure â†’ retry â†’ usable records. Android tests compiled and their
  APK assembled; they were **not executed** because ADB reported no connected device.
- Debug and unsigned release APK assembly completed:
  `app/build/outputs/apk/debug/app-debug.apk` and
  `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Final `lintDebug` passed: **0 errors, 266 warnings, 7 hints** with the existing
  baseline unexpanded. The warning total is not a clean-lint claim.
- Deterministic sRGB checks for repaired online-battle pairs passed the 4.5:1
  normal-text threshold in every palette: Attack **12.58 / 8.44 / 6.47 / 6.74:1**;
  result headers **8.85 / 9.84 / 14.21 / 13.11:1** (Helper / Lab / Arena / Digimon.net).
- Verification was deferred until implementation finished. The final batch exposed
  one Int/Long comparison error, which was fixed, then a Kotlin compiler memory
  failure on the large Radar method. Serial compilation with a temporary 4GB
  Kotlin heap succeeded. Debug lint required a separate single-use 4GB Gradle
  process after earlier analysis timeouts. These were command-line overrides;
  project-wide Gradle/JVM settings were not changed.
- At this technical-pass checkpoint, native acceptance remained pending: current screenshots, TalkBack selection and
  health, keyboard/large-font/short-window layouts, predictive Back, real-collection
  inspector reproduction, and launcher/widget performance. Historical captures
  do not validate the new implementation. The original 11/20 audit score is a
  historical pre-implementation source snapshot, not a fresh acceptance score.
  The subsequent connected-device visual/Android results are recorded above.

Successful verification tasks (completed across the final batch and its
tooling-recovery runs): `:app:testIntegrityCheckUnitTest`, `:app:assembleDebug`,
`:app:assembleRelease`, `:app:assembleIntegrityCheckAndroidTest`, `:app:lintDebug`.

```powershell
# Serial compiler invocation used by the final unit/build batch:
.\gradlew.bat :app:testIntegrityCheckUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleIntegrityCheckAndroidTest --offline --console=plain --max-workers=1 "-Pkotlin.daemon.jvmargs=-Xmx4g"
# The APK tasks completed in this follow-up invocation; lint analysis timed out:
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:assembleIntegrityCheckAndroidTest :app:lintDebug --offline --console=plain --max-workers=1 "-Pkotlin.daemon.jvmargs=-Xmx4g"
# Final successful lint invocation:
.\gradlew.bat :app:lintDebug --offline --console=plain --max-workers=1 --no-daemon "-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8"
```

## In-world companion overhaul (October 2026, source scope)

- Full app unit suite: **731 cases, zero failures, three existing private-fixture
  skips**. New coverage: `DebugSettingsRepositoryTest` (legacy preferences, master
  opt-in, revocation, restart), `RadarCommandGateTest` revocation/stale-location
  cases, `LlmSettingsRepositoryTest` prompt-guard cases, `DiagnosticTextTest`,
  `PartnerConditionTest`, `ResearchTextTest`, `LorebookContextTest`,
  `DigimonPersonaBuilderTest` memory/source cases, plus Android-test additions for
  debug-spawn denial, typed NFC outcomes, and species-override denial (compile-only
  until device execution).
- `test`, Android-test compilation, `lintDebug` (no errors; baseline unexpanded),
  `assembleDebug` (`app-debug.apk`) and `assembleRelease`
  (`app-release-unsigned.apk`, unsigned: no signing config) verified offline.
- Independent source review disposition **fix**; all six findings resolved (see the
  in-world-companion surface brief). No connected device or emulator exists: native
  visual/runtime acceptance (screenshots, TalkBack, motion, radio) is deferred.

## Character-specific tamer histories and genuine-gap stat scaling

- Audited all 223 roster entries against their character/partner histories.
  `TamerCanonicalPartnersTest` passed **22 cases**, including a roster-wide
  synthetic-art check over 350+ documented primary-stage selections, continuity
  differences, mandatory combined partners, mixed-stage component exclusion,
  guest-owned Blast forms and legacy cup refresh without player/seed changes.
- Consolidated offline battle, presentation, NPC replay and Radar-flow regression
  run: **272 tests, zero failures or skips**. The initial run caught a spelling
  alias typo (Grap/Grappu Leomon); it was corrected before the successful rerun.
- Bundled-art availability evidence: **164 Rookie-tier 1Ã—1 teams** and **135
  Champion-tier 2Ã—2 teams**. These counts include explicitly labeled stat-scaled
  documented forms, not invented evolutions. Canonical artwork requirements remain
  enforced when a form at the requested stage is documented.
- Research parser checks: **four passes** covering comment/citation exclusion,
  nested templates and separate Hybrid/DigiXros evidence.
- Debug and integrity APK assembly, Android-test assembly and lint with the
  existing baseline passed. No lint baseline expansion.
- On the connected Android device, both `ArenaPersistenceTest` cases passed
  against actual Room: concurrent settlement credits one reward; an overflowing
  wallet rolls back the result/receipt and permits a clean retry. Only the isolated
  `.integritycheck` application was installed for these checks.

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.battle.offline.*" --tests "com.github.nacabaro.vbhelper.screens.offlineBattle.*" --tests "com.github.nacabaro.vbhelper.world.ecosystem.NpcBattle*Test" --tests "com.github.nacabaro.vbhelper.world.RadarBattleFlowTest" :app:lintDebug :app:assembleDebug :app:assembleIntegrityCheck :app:assembleIntegrityCheckAndroidTest --offline --console=plain --max-workers=1
py -3 -B tools/test_audit_tamer_canon.py
```

See `TAMER_CANON_AUDIT.md` for evidence policy and source gaps, and
`TAMER_ARENA_IMPLEMENTATION.md` for registration/recovery behavior.

## Tamer Arena lobby polish (refinement, same behavior)

- Reworked the lobby into one compact native header, a collapsed team summary
  with a dedicated setup editor, search/franchise/Ready filters, larger
  pixel-sprite roster rows with missing-artwork reasons, and a team briefing that
  leads with the real opponent team (owned/associated/guest/combined/scaled
  labels), tactics, inspected partner stats, attack geometry, finishers, shared
  supplies and exact rewards. Cups use summary cards with round progress and a
  per-round bracket; results keep damage/item/time breakdowns in place while the
  next match prepares, and matching rematches reuse prepared render resources.
- Follow-up compression: the team summary is now a single 48dp row
  (partners/stages, format, difficulty, Change), filters share one scrollable
  chip row, and roster rows dropped to 88dp with smaller sprites, so the list
  owns the screen. Results/finishers/battle rules are unchanged.
- Fusion-duo follow-up: owned DigiXros pairs (Taiki's Shoutmon + Ballistamon,
  Kiriha's Greymon + MailBirdramon, Shoma's Gaioumon + Kuzuhamon) are permitted
  their combined finishers, and the 02 DNA partners complete each other's teams
  as late guest alternatives, so Paildramon, Silphymon and Shakkoumon equip when
  both components field. Shoma is the solo-only variant: alone at Mega he fields
  Alter-B (Noir), beside Kuzuhamon he fields Gaioumon and equips the Jogress.
  Owned companions and guests compete openly for the second slot: completing an
  approved fusion wins, owned beats guest on ties. Absorbed fusion components
  can never reappear beside their own combined form, and the fusion movie stays
  gated on result artwork (Shoutmon X2 and Omegamon Alter-B ship without bundled
  sprites, so Taiki's and Shoma's pairs fight until their cards are imported).
- Hardened the 2Ã—2 challenge path behind a "Failed requirement" report: blank
  stored identities now fall through to usable fallbacks, frozen arena stages
  are coerced to the 0â€“5 tier both sides mirror, and every previously bare
  validation in match setup, settlement and tournament creation names its
  problem. Behavior for valid setups is unchanged.
- Shared `CyberPanel`/`VitalButton` controls replace one-off rounded actions; no
  new raster assets or tamer portraits were added. Results/finishers/battle rules
  are unchanged.
- `ArenaPresentationTest` (3 cases) covers Ready filtering with alias search,
  no-partner browsing, and displayed base rewards matching the settlement rule.
  `TamerArenaLayoutTest` (4 cases, Android) covers search â†’ briefing â†’ challenge,
  team editor at 1.3Ã— font, two-partner briefing with the Challenge action pinned,
  and records/cup creation across all four app themes; it compiles but has not
  executed yet because the wireless device dropped mid-pass (`adb reconnect`
  finds nothing; re-enable wireless debugging to run it).
- Focused suites pass: **52 arena tests, zero failures** (12 arena, 26 canonical,
  2 roster-assets, 1 sweep, 3 presentation, 8 nicknames). `lintDebug`,
  `assembleDebug`, `assembleIntegrityCheck` and `assembleIntegrityCheckAndroidTest`
  pass with the existing baseline unexpanded. Partner nicknames (Digimaru, V.V.,
  Zeromaru, Warg/Melga, Yukimura, Rikka, Sashenka, Digitorin, Black, Catherine,
  AKAGI/NAOMI/DINOGON) resolve per partner, travel with guests, never leak
  across tamers, and appear in battles, briefings, roster rows and search.
- Shoma's **Noir** nickname is restricted to his resolved solo Omegamon Alter-B
  form. `TamerNicknamesTest` also covers loaded Alter-B display/species identity,
  the unnamed 2Ã—2 Gaioumon/Kuzuhamon pair, and the Gaioumon fallback when Alter-B
  artwork is missing.

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.battle.offline.TamerArenaTest" --tests "com.github.nacabaro.vbhelper.battle.offline.TamerCanonicalPartnersTest" --tests "com.github.nacabaro.vbhelper.battle.offline.TamerRosterAssetsTest" --tests "com.github.nacabaro.vbhelper.screens.tamerArena.*" --offline --console=plain --max-workers=1
.\gradlew.bat :app:lintDebug :app:assembleDebug :app:assembleIntegrityCheck :app:assembleIntegrityCheckAndroidTest --offline --console=plain --max-workers=1
```

This pass does not establish on-device visual acceptance of the polished lobby;
run `TamerArenaLayoutTest` on the reconnected device and inspect its
`tamer-arena-polish` captures before calling the polish finished.

## Home/navigation corrections and warm rematch

- Restored the compact cut-corner bottom bar with tab semantics and retained
  overflow navigation; removed the Home VitalWear footer. The three partner stat
  panels again share a square column matching the Digimon portrait's overall height.
- Home retains its latest loaded snapshots across navigation, reads history separately,
  and does not block an existing partner on watch setup, the Dex catalog or history repair.
- Training rematch restarts the simulator from captured definitions/configuration/items,
  retaining base/result art and the arena. Render-resource reuse is limited to ready,
  matching base and transformed assets; renderer failure/different art still reloads.
- Full app JVM run: **599 cases, zero failures, three existing private-fixture skips**.
  Reader tests, lint with the existing baseline and debug APK assembly passed.
- Samsung SM-A346M / Android 15: all eight `UiRefinementsTest` cases passed. The
  focused `OfflineBattleRematchTest` then passed against actual Filament: same scene
  instance and asset generation, the same prepared maps, reset outcome and resumed
  combat, **zero additional fighter GLB uploads**, and no loading overlay on rematch.
- The first combined device run reached eight successful UI cases but the rematch
  test waited for global Compose idleness in a continuously rendered arena. Its action
  now runs directly on the UI thread (the same ViewModel action used by the button),
  and the focused rerun passed. This is a test synchronization correction.

```powershell
.\gradlew.bat test :app:lintDebug :app:assembleDebug :app:assembleIntegrityCheckAndroidTest --offline --console=plain --max-workers=2
.\gradlew.bat :app:connectedIntegrityCheckAndroidTest --offline --console=plain --max-workers=2 "-Pandroid.testInstrumentationRunnerArguments.class=com.github.nacabaro.vbhelper.ui.UiRefinementsTest,com.github.nacabaro.vbhelper.OfflineBattleRematchTest"
```

## UI refinements 1â€“6 (native device verification)

Implemented shared typography/action hierarchy and three-palette Blast controls,
Material navigation and safe inset ownership, actionable Home readiness/setup,
shared transient feedback with persistent transfer status, non-blocking exports
and battle requests, and restored conversation/editor drafts.

```powershell
.\gradlew.bat test :app:lintDebug :app:assembleDebug --offline --console=plain --max-workers=2
.\gradlew.bat :app:connectedIntegrityCheckAndroidTest --offline --console=plain --max-workers=2 "-Pandroid.testInstrumentationRunnerArguments.class=com.github.nacabaro.vbhelper.ui.UiRefinementsTest,com.github.nacabaro.vbhelper.IndividualPersistenceTest,com.github.nacabaro.vbhelper.ui.theme.AppThemeSelectionTest"
```

- Full app JVM evidence: **596 cases, zero failures, three existing private-fixture
  skips**. Reader tests and debug APK assembly passed.
- Lint passed with the existing baseline, **zero unfiltered errors**, 208 warnings
  and seven hints. The baseline was not expanded.
- Real device: **Samsung SM-A346M, Android 15**, separate `.integritycheck`
  installation. The broad final device pass verified 47 cases; the two corrected
  legacy migration fixtures passed in a focused rerun: **49 distinct checks verified**.
- Eight `UiRefinementsTest` cases exercise the real UI components: readiness-driven
  setup and visible fixed actions; saved chat draft; preserved input and safe error
  copy; selected/labeled compact navigation; short-window rail scrolling; small-window
  large-font empty-state actions; non-overlapping scaled stat text; and retained,
  explicitly selected adventure duration. The existing palette selection test also ran.
- Real Room checks cover VB/BE export values, missing-profile rejection, identities,
  receipts, retries, source preservation, and restart behavior. Device execution
  exposed the existing fresh-schema `blastMode` insertion failure: the identity guard
  now supplies NONE explicitly and refreshes atomically, without changing the schema
  or overwriting existing Blast choices. A dedicated fresh-row regression passed.
- The legacy fixtures now exercise actual 17â†’18 receipt and 20â†’22 contact/farm SQL
  against reconstructed old tables. They do not relabel a current schema as an old
  one and therefore do not falsely claim complete historical upgrade-path coverage.
- A wireless-debugging disconnect interrupted one intermediate run; the connection
  was recovered and the device checks above completed afterward.

Native Home capture and app launch were inspected on the physical phone. Physical
watch radio transfer, live battle-server authentication, every foldable posture,
and complete TalkBack speech traversal were not exercised by these checks.

APKs: `app/build/outputs/apk/debug/app-debug.apk` and the isolated device-test build
`app/build/outputs/apk/integrityCheck/app-integrityCheck.apk`.

## Radar social behavior (schema 33 / ecosystem rules 3)

See `RADAR_SOCIAL_IMPLEMENTATION.md` for implementation and verification details.

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2 --tests "com.github.nacabaro.vbhelper.domain.personality.*" --tests "com.github.nacabaro.vbhelper.world.ecosystem.*" --tests "com.github.nacabaro.vbhelper.digifarm.*" --tests "com.github.nacabaro.vbhelper.chat.*"
py -3 -B scripts/test-world-social-db.py
```

Latest full app evidence: **466 cases, zero failures, three existing fixture-dependent
skips**. Root reader tests, debug APK assembly, lint with the existing baseline, and
Android-test compilation passed. Social/interaction/living-world/chat-memory host
SQLite suites passed **31 checks**. The schema-33 migration is compared with Room's
generated export and preserves existing identity, personality labels/timestamps,
private history, player trust, and logical clock.

New regressions verify non-cosmetic personality choices, balanced attacks, neutral
meetings, explicit activity acceptance/refusal, locale-aware opening diversity,
private/public memory separation, persistent social state, need-decay equivalence,
and stale conversation epochs. Real Room approach/handoff, greeting-expiry, and
activity-discussion/refusal cases are compile-only Android coverage. Live GPS,
renderer behavior, and provider response quality still require device execution.

Bugbot could not run because its agent type is unavailable; this change received
a manual code review and corresponding corrective regressions.

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
additive 28â†’29 migration. `ImportedAttackArtTest` checks the actual parsed attack
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

## Attribute-only DW1-inspired offline battle (ruleset 3)

Mechanics and source provenance are documented in `OFFLINE_BATTLE_DW1_IMPLEMENTATION.md`.

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2 --tests "com.github.nacabaro.vbhelper.battle.offline.*" --tests "com.github.nacabaro.vbhelper.world.ecosystem.NpcBattle*Test" --tests "com.github.nacabaro.vbhelper.world.RadarBattleFlowTest"
.\gradlew.bat test :app:lintDebug :app:assembleDebug :app:compileIntegrityCheckAndroidTestKotlin --offline --console=plain --max-workers=2
```

`Dw1BattleMechanicsTest` checks reference damage/rounding/caps, the attribute triangle,
power-dependent readiness, weighted eligibility, stable arena profiles, defensive
reaction during observation, percent-HP ticks, protection/exclusivity, bounded buffs,
replanning without out-of-range damage/resource spending, reactive Counter, accuracy,
purpose-specific RNG, and control recovery. `NpcBattleVersionTest` verifies exact
fixed-step replay of new envelopes and legacy arrays with absent new fields, pinned
configuration/catalog/ruleset, seed validation, and unsupported-version rejection.

`Dw1BattleBalanceMatrixTest` runs 1,000 paired seeds per stage (12,000 battles total),
checking mirror win rates, side swaps, timeouts, duration tails, and incapacitation.
Latest full-app evidence: **429 cases, zero failures, three existing private-fixture
skips**; root reader tests, lint, debug assembly, and Android-test compilation passed.
The mirror matrix measured 47.53â€“51.58% decided win rates, zero paired side difference,
zero timeouts, p50 38.28â€“40.70s, and p95 48.65â€“51.41s. Double KOs were 2.5â€“5.0% by
stage and remain reported separately. Android runtime/renderer acceptance is not
established by compilation.

## Theme-aware 3D environments

Digifarm, Colosseum and Radar use generated four-theme texture bindings and
environment-only material allowlists. Radar floor/grid/boundary factors also
change directly. Compose delivers theme changes to retained native views, and
emissive animation consumes the applied theme's factors. The first Helper load
retains original gltfio textures; Helper source PNGs/factors, natural grass,
transparency and sampler settings are preserved by the asset contract.

```powershell
py -3 -B tools/build_environment_themes.py --check
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.rendering.*" --tests "com.github.nacabaro.vbhelper.ui.theme.*" :app:assembleIntegrityCheck :app:assembleIntegrityCheckAndroidTest :app:assembleDebug --offline --console=plain --max-workers=2
.\gradlew.bat :app:lintDebug --offline --console=plain --max-workers=2
```

Asset validation, focused JVM checks, debug/integrity/test builds and lint passed.
The generated pack contains 43 content-addressed PNGs for 48 texture bindings.
The initial build-only delivery was not executed natively because the device
disconnected. The follow-up reproduced Filament's `GEN_MIPMAPPABLE` precondition:
texture generation aborted and the cosmetic palette readiness gate stalled battle
loading. The corrected textures declare the required usage, and combat readiness
is independent of palette preparation. Light dome emission also has a pale base
to avoid opaque black patches.

All **five** `EnvironmentThemeRuntimeTest` cases and `OfflineBattleRematchTest`
passed on the Samsung SM-A346M running Android 15: **six native passes**. Coverage
includes all four themes on each retained environment, inactive/resume updates,
Colosseum retry/rebinding, actual cold VB Lab combat startup, live battle recoloring
without fighter reloads, and rematch. Sixteen native `TextureView` captures were
collected; representative dark/light farm, arena and Radar views were reviewed.
The corrected debug APK was installed on the connected regular app with `adb
install -r`, retaining app data. Tablet-specific acceptance and performance
profiling remain unverified. See `tools/ENVIRONMENT_THEMES.md` for provenance.

## Mobile species-profile layout

Dex species details follow the supplied Digimon.net mobile reference's structure:
name and ownership, centered framed portrait, divided species facts, Profile,
then base stats, earned scan data, and evolution requirements. All four palettes,
imported name shadows, ownership/grayscale/obscured states, custom species selection,
and pinned Close/Jogress/eligible conversion actions remain shared.

Verification: debug and integrity APKs and Android tests built successfully.
`DexCharacterDetailsTest` (six cases) and `DexSpeciesProfileLayoutTest` (three cases)
passed on the connected Samsung SM-A346M running Android 15. The layout suite was
confirmed again after improving its paragraph capture. Checks cover portrait/name
order and centering, all four themes, long descriptions at 1.3Ã— text, footer actions,
species selection, privacy gating, zero/incomplete/complete scans, and evolution
selection. The layout detector reported no findings.

Native captures were inspected in all four palettes, with paragraph and large-text
views. Captures use existing bundled Pulsemon art and explicitly labeled fixture
profile text; they are not claims about official species data. They are written to
the integrity app's external-files `dex-profile-layout` directory by the layout
test. Tablet-specific visual acceptance and a full TalkBack pass remain unverified.

```powershell
.\gradlew.bat :app:assembleIntegrityCheck :app:assembleIntegrityCheckAndroidTest :app:assembleDebug --offline --console=plain --max-workers=2
```

After installing the integrity app and its Android-test APK, the focused native run is:

```powershell
adb shell am instrument -w -e class "com.github.nacabaro.vbhelper.screens.cardScreen.DexCharacterDetailsTest,com.github.nacabaro.vbhelper.screens.cardScreen.DexSpeciesProfileLayoutTest" com.github.nacabaro.vbhelper.integritycheck.test/androidx.test.runner.AndroidJUnitRunner
```

## Selectable color themes

Settings â†’ Appearance exposes VB Helper (the existing dark purple/cyan palette),
VB Lab (charcoal/cyan), VB Arena (white/silver/green), and Digimon.net
(white/pale blue, royal blue, and lemon yellow). The choice is saved in
`app_preferences` and applied immediately, independently of the phone's night mode.
Typography, geometry, sprites, and motion remain shared.

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.ui.theme.*" :app:compileIntegrityCheckAndroidTestKotlin :app:assembleDebug --offline --console=plain --max-workers=2
.\gradlew.bat :app:lintDebug --offline --console=plain --max-workers=2
```

`AppThemeTest` checks saved-value fallback, all four choices, retained Helper colors,
reference color families, text contrast, and primary action-label contrast.
`ThemeSurfaceContrastTest` checks all five Material tonal surfaces in each palette.
`AppThemeSelectionTest` exercises the real picker and preference store, restoring
each choice and checking composition colors, typography, shapes, and light/dark
configuration independence. Its execution requires a device/emulator; compilation
alone does not establish runtime or screenshot acceptance.

Digimon.net addition: focused theme/typography JVM checks, Android-test compilation,
and debug assembly passed. The two `AppThemeSelectionTest` cases also passed on a
connected Samsung SM-A346M running Android 15, covering selection/restoration and
light/dark configuration independence for all four palettes. A native Storage
capture was inspected for the new white/blue/yellow colors and readable controls;
this does not establish visual acceptance for every screen or tablet layout.

## Card evolution chart (build checks only)

Focused commands, from the repository root in PowerShell:

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --offline --console=plain --max-workers=2 `
  --tests "com.github.nacabaro.vbhelper.screens.cardScreen.*" `
  --tests "com.github.nacabaro.vbhelper.source.Card*Test" `
  --tests "com.github.nacabaro.vbhelper.source.JogressImportReaderTest" `
  --tests "com.github.nacabaro.vbhelper.source.SpeciesOriginQueueTest" `
  --tests "com.github.nacabaro.vbhelper.ui.theme.ThemeSurfaceContrastTest"
py -3 -B scripts/test-dex-evolution-db.py
.\gradlew.bat :app:compileIntegrityCheckAndroidTestKotlin :app:lintDebug :app:assembleDebug --offline --console=plain --max-workers=2
```

`CardEvolutionLayoutTest` covers branches/merges, disconnected and fusion-only
characters, deterministic placement, route deduplication/conditions, and bounds;
shared overlapping buses, including mixed adjacent/skipped routes in the same gap;
and presentation-only final sections for terminal evolution/Jogress results.
Cycles retain imported stages while acyclic downstream terminal chains advance;
results also reached from earlier stages and explicit BEM stages retain their
imported placement rules. Toggling Jogress changes connectors, not node positions.
`CardChartViewportTest` covers minimum 48dp node bounds, centroid-anchored zoom,
bounded pan, fit behavior, and accessible scroll positions.

`JogressImportReaderTest` covers DiM/BEM attribute and partner-specific tables,
deduplication, absent tables, sentinels, and foreign-card endpoint isolation.
`CardReimportPolicyTest` covers named variants, unique content matches, duplicate
names, ambiguous candidates, and unambiguous legacy attack-art recovery. The eight
host SQLite/migration checks execute production SQL for local-card route isolation,
ownership/discovery, progress/order, correct destination art via
`CardCharacter.spriteId`, both same-card Jogress partner edges, foreign partners as
requirements only, automatic/manual name rules, and additive schema 30â†’31 retention.

The 18 new JVM cases comprise seven `CardBatchImporterTest`, four
`SpeciesOriginQueueTest`, four `CardDocumentReadTest`, two `CardImportRunGateTest`,
and one `ThemeSurfaceContrastTest`. They cover mixed/new/re-import batches,
duplicate URIs and aliases, 1,001-file selection without an app count cap,
independent failures, committed progress on Stop, origin reservations/recovery,
queued-name refresh by ID, close-before-persist (including close failure and late
open), stalled-stream cancellation, and stale-run admission/publication ownership.
The contrast test calculates luminance from all four selectable color schemes and
requires `onSurface` against all five `surfaceContainer*` roles to reach 4.5:1.

### Import and requirement contract

Dex selection uses `CardSelectionScaffold` with zero nested system insets;
`TopBanner` places Import at top-left opposite Edit. Dex and Settings share
`OpenMultipleDocuments` with no app-imposed file-count cap. `CardBatchImportPanel`
shows preparation/progress, Stop/Stopping, added/updated/failed totals, dismissible
results, and safe per-file issues (display name or file number, no raw exception/URI).
Origin-bookkeeping issues remain distinct from failed imports of saved cards.

Files run sequentially, with each new import or re-import committed atomically and
returning `CardImportResult(cardId, cardName, isNew)`. Dex queues an origin choice
only for a NEW card after commit; Settings respects its existing prompt toggle.
Duplicate URIs are read once; matching aliases/re-imports add no new choices, but
refresh an already-pending label by card ID. A choice is reserved by ID, its status
saved before the queue entry is cleared, and failed saves release the reservation.
Recovery runs once per retained model, filtering the existing queue to still-existing
UNKNOWN cards; it does not enqueue every unknown card or replay classified choices.

The retained `CardImportViewModel` uses Application services and `SavedStateHandle`
for picker origin policy, so configuration changes retain the job/results without an
Activity reference. `CardImportRunGate` guards admission and publication until the
owning job and cleanup complete; completion supplies a terminal fallback even if
cancellation precedes the lazy body. Provider name queries and opens receive a
`CancellationSignal`; cancellation also closes the active stream. Parsing and close
finish BEFORE persistence, including cleanup when open returns late, so close errors
cannot turn an already-committed file into a failure.

Schema 31 adds `CardSpecificJogress` and `Card.nameIsUserEdited`. DiM and BEM imports
store both attribute and partner-specific Jogress; same-card partners contribute
both local source edges, while foreign partner references remain requirements and
never become false local nodes. Migration adds storage, not missing source routes:
re-import the original card file to recover previously discarded Jogress and
ambiguous legacy BEM adventure requirements.

An unambiguous re-import refreshes ordinary evolution definitions, both Jogress
tables, and attack art for one matching local card, retaining card/character IDs,
owned individual IDs and state, discovery, and card progress. Same-body custom
variants must not receive bulk refreshes; an unresolved match follows the new-card
import path and leaves existing variants intact. A supplied filename refreshes the
automatic card name unless `nameIsUserEdited` is set. Legacy name provenance was
absent: the user explicitly chose to refresh legacy names on re-import, including
names that may have been manually assigned before the flag existed. Future explicit
manual renames set the flag and are protected from filename refresh.

An absent adventure requirement is stored as `-1`. Valid BEM adventure index `0`
displays stage `1`; nonnegative indices display index + 1. Migration normalizes only known
legacy DiM zero values to `-1`, preserves BEM zero, and leaves source recovery to
re-import. The chart's final sections are presentation-only and never rewrite
imported character stages or stats.

### Recorded evidence and limits

- XML reports in `app/build/test-results/testIntegrityCheckUnitTest/` establish
  **18 new tests passing**, with zero failures/errors/skips, in the five suites above.
  Earlier chart evidence records 17 layout, seven viewport, three Jogress-reader,
  and five re-import-policy tests passing. Main verification reports all **eight
  host SQLite/migration checks passing**.
- The latest full-app report at
  `app/build/reports/tests/testIntegrityCheckUnitTest/index.html` records **407
  tests, zero failures, and three private-fixture skips** (404 successful executions).
  An earlier run exposed a pre-existing personality determinism test comparing
  different clocks; both calls now use explicit `now = 1L` (application behavior
  unchanged). Its latest three-case XML report has zero failures/errors/skips.
- Final Android-test compilation, debug assembly, and lint passed. Current checks
  use `--max-workers=2`; lint retains advisory/baselined findings.
  APK output: `app/build/outputs/apk/debug/app-debug.apk`. The living-world results
  below predate this extension.
- Main's latest code review scored all four findings resolved: light-scheme contrast,
  stale cancelled-run ownership, stream close after commit, and blocking-provider
  Stop, including the pre-start terminal fallback. Earlier fixes for ambiguous
  re-import, shared skipped-gap buses, cycle descendants, and BEM index zero remain.
- `DexEvolutionPersistenceTest`, `CustomCardAttackArtTest`,
  `CardEvolutionChartTest`, `DexCharacterDetailsTest`, and
  `CardSelectionImportUiTest` remain compile-only Android coverage.
  Their Room/Compose cases cover reactive ownership, in-place re-import, variant
  isolation, name protection, BEM index zero, selection/navigation/scroll callbacks,
  the disabled Jogress chip and overlay placement, readable requirements, and
  obscured stats with an available Close action. New cases add two selection
  header/body-bottom/Stop checks and two Room atomic-result/rollback checks;
  compilation does not execute these assertions.

**User-selected scope: BUILD CHECKS ONLY.** No installation, Android runtime tests,
screenshots, font-scale checks, or TalkBack evidence. Compilation and semantic-click
test coverage do not establish physical hit-testing or visual approval.

### Deferred native acceptance

- Select mixed DiM/BEM new cards and re-imports, large batches, and aliases from local
  and multiple document providers through Dex and Settings. Check top-left Import
  opposite Edit, disabled admission during a run, preparation/progress/Stop/results,
  and no app count cap. Confirm Dex asks only for committed NEW cards; Settings keeps
  its prior toggle behavior. Re-imports/aliases must not add choices, pending labels
  must follow card IDs, and saving a choice must not consume the next card's prompt.
- Mix valid, invalid, unreadable, and close-error files: later files continue, failed
  files leave no partial rows/prompts, and saved cards retain their success counts
  when origin bookkeeping fails. Stop during provider query/open/read and immediately
  after starting; try a fast restart and rotate during picker/import/choice handling.
  Require terminal Stopped state, cleanup before replacement admission, no stale
  progress/results, retained completed cards, and recovery only of queued existing
  UNKNOWN cards. Provider cancellation responsiveness still needs native evidence.
- In both system appearances, check tonal dialogs and all container levels for
  readable text; the light scheme maps every container role to incumbent purple.
  Info/Jogress dialogs use medium cut-corner Material Cards on `surfaceContainerHigh`,
  capped at 480dp/92% width and 88% height, following `StorageDialog` and
  `TransformationHistoryCard`; info Close/Jogress text actions stay outside scrolling.
- Inspect compact selection cards, logo/name/status/progress and edit controls, plus
  the actual selection-body and chart-footer alignment with bottom navigation in
  both system navigation modes and rotation. Require no duplicate inset gap; source
  geometry and compile-only assertions do not establish rendered bottom alignment.
- Inspect branches/merges with mixed adjacent and stage-skipping routes: every
  shared gap uses an aligned overlapping bus, with shared segments drawn once and
  skipped/backward routes outside unrelated nodes. Shared-path ambiguity is an
  accepted user choice; require neither separate per-route tracks nor arrowheads.
- Tap actual displayed nodes after sustained pan and pinch/button zoom; check
  correct details and usable minimum 48dp node bounds. Check the subtle Fit/zoom
  overlay's Material hit areas, zoom-limit disabled states, and taps near/under the
  overlay so controls and chart gestures do not select unintended characters.
  There is no drag/pinch hint text or separate controls strip.
- Check chart-to-footer continuity; previous/next controls and logo/name remain
  readable at the bottom-navigation boundary.
- Start with Jogress off; keep its labeled chip visible but disabled when no stored
  routes exist (including loading). Toggle dashed yellow Jogress visibility while
  ordinary routes stay solid and nodes stay fixed, including fusion-only results,
  both same-card partners, and foreign partners shown only as requirements in details.
- Check terminal-only ordinary evolution and Jogress results in their final
  presentation section even with Jogress off; acyclic downstream chains advance,
  actual cycle members stay at imported stage, and descendants of cycles advance.
  Earlier-stage incoming routes and explicit BEM stages retain their rules;
  details/stored stats must still show the imported stage.
- Check Official and Custom DiMs have active cyan technical frames, while Unknown
  cards keep their distinct status/origin action; imported logo pixels remain crisp.
- Check previous/next on first, last, and single-card catalogs, with matching
  logo/title and Adventure destination; navigation must stay bounded.
- Return from Adventure and rotate while zoomed/panned; check displayed-card,
  selection, Jogress preference, and viewport restoration. Exercise loading, empty,
  failure/retry, and reactive discovery/availability changes.
- At large fonts and with long localized names/species descriptions/requirements,
  inspect info and Jogress dialogs: clear HP/BP/AP hierarchy, imported name pixels,
  species level/type/profile/moves, correct evolution destination on tap, attribute
  and partner-specific requirements, scrollable content, and pinned Close/Jogress
  actions. Verify the Custom species picker and its unavailable-data state, obscured
  name/stats, absent stats/profile/routes, native Back/outside dismissal, and focus
  return after nested Jogress/species dialogs. Adventure index `0` must visibly say
  stage `1`, and `-1` must show no adventure requirement.
- Check grayscale/static-color/animated ownership and reduced-motion behavior;
  with TalkBack check node index/imported stage/ownership/selection, labeled
  toggle/controls, route-card actions, pinned dialog actions, and consecutive
  two-axis scrolling. Native readability, physical hit areas, and focus remain
  acceptance work rather than inferred passes.
- Re-import DiM/BEM fixtures with changed evolution/Jogress/attack definitions,
  missing legacy data, filename changes, and an explicitly manually renamed card.
  Exercise same-number/same-body custom variants, duplicate names, ambiguous content,
  and missing-art recovery: only an unambiguous existing card is refreshed. Confirm
  the chosen legacy-name refresh and future manual-name lock, retained local
  card/character/individual IDs, owned stats, discovery/progress, and unaffected
  other variants; verify repaired adventure requirements after reopen.

## Digimon scan conversion (build checks only)

### Behavior contract

- Scan belongs to the local `cardCharacterId`: each distinct opposing wild awards
  **+20 percentage points** only for a player `ALLIED_VICTORY`, capped at **100%**.
  Friendly and autonomous encounters, losses, draws, and abandonment award nothing;
  entries on other local cards remain isolated.
- Scan rewards and the result receipt persist atomically. Victory feedback shows
  committed reward receipts, so replay cannot award or announce an uncommitted result.
- Conversion is manual in Dex, with a form/card preview and optional nickname. It
  consumes **100%** into a fresh, inactive Storage individual with the matching VB/BE
  profile: **VB countdown 1; BE countdown 0**. Conversion and Close actions stay
  pinned outside scrolling content. UI strings cover English, Portuguese, and Japanese.
- The persisted master Settings Debug switch (default off, available in every build)
  gates testing overrides: enabling it plus the subordinate instant-scan option awards
  **100 percentage points per eligible defeat**. Legacy one-win scan preferences never
  enable the master switch on their own, and distance overrides are session-only.
- Schema **32** is generated, with the additive **31â†’32** migration registered.

### Recorded verification

Historical scan-conversion implementation verification (before the Storage collection extension):

```powershell
.\gradlew.bat test :app:compileIntegrityCheckAndroidTestKotlin :app:lintDebug :app:assembleDebug --offline --console=plain --max-workers=2
py -3 -B scripts/test-digimon-scan-db.py
```

- The final Gradle command passed with **BUILD SUCCESSFUL**. Full-app HTML at
  `app/build/reports/tests/testIntegrityCheckUnitTest/index.html` records **438
  cases, zero failures, three existing private-fixture skips** (435 executions),
  including **nine new `DigimonScanPolicyTest` passes**. Earlier counts elsewhere
  in this document remain records of their respective verification runs.
- The host SQLite script passed **five tests** covering migration/retention,
  per-entry isolation, guarded consumption, rollback, and receipt uniqueness.
- `DigimonScanPersistenceTest` and the modified `WorldInteractionPersistenceTest`
  and `DexCharacterDetailsTest` compiled only. Actual Room and Compose assertions
  were **not executed**; host SQLite checks do not establish genuine Room migration passes.
- Lint passed against the existing baseline with **174 warnings and seven hints**,
  with no new errors. APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Initial source review required a VB countdown fix; the reviewer subsequently
  scored it **resolved**. The ship verdict covers that source fix only and gives
  **no native approval**. No screenshots, device/runtime evidence, or new shipping
  rasters are claimed.

### Deferred native acceptance

- Complete end-to-end wild victories with normal rewards and the master Debug
  switch off/on; verify eligibility exclusions, local-entry isolation, the cap,
  exactly-once committed feedback, and that legacy instant-scan values alone grant
  no override.
- Convert, restart, and inspect nickname and inactive Storage state; check VB/BE
  export profiles/countdowns and conversion while the farm is full.
- Inspect English/Portuguese/Japanese UI at large fonts and with reduced motion;
  verify pinned Convert/Close, system Back, IME clearance, and TalkBack focus/actions.
- Execute genuine Room upgrades and persistence/reopen/rollback assertions on Android.

## Storage scan collection (build checks only)

### Behavior contract

- Storage exposes a top-left **Scan data** icon chip opposite the existing
  Adventure action. Scan data remains available when Storage has no individuals.
- The collection observes all positive scan percentages across imported local
  `cardCharacterId` entries, not just completed scans or currently owned species.
  Production SQL orders by percentage descending (ready entries first), then
  case-insensitive card name, character index, and local character ID.
- Each row shows imported pixel portrait and name art, canonical species name
  (or the existing fallback), card name, percentage, and cyan progress. Partial
  entries remain visible with Convert disabled; Convert is enabled at **100%**.
  Art and profile identity follow the local imported character rather than a
  shared card number.
- Storage and Dex reuse the extracted `DigimonScanConversionDialog`: form/card
  preview, optional nickname, live readiness, and the existing guarded backend
  conversion transaction. This extension adds no schema change or raster assets.
- Consumption resets the scan to zero; the positive-only Room flow removes that
  entry from the collection. The selected preview snapshot is retained through
  that emission until conversion completes, so removal cannot prematurely
  dismiss the preview. Feedback and Close remain outside the `LazyColumn`,
  including when the last entry disappears.
- Loading, loaded-empty, and read-failure/Retry states are explicit. Conversion
  outcomes are reported separately. Strings cover English, Brazilian Portuguese,
  and Japanese.

### Current focused verification

Evidence supplied by the implementation pass for this documentation update:

```powershell
py -3 -B scripts/test-digimon-scan-db.py
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.source.DigimonScanPolicyTest" :app:compileIntegrityCheckAndroidTestKotlin :app:lintDebug :app:assembleDebug --offline --console=plain --max-workers=2
```

- Host SQLite: **eight passes**. Three newly added checks execute the actual
  production collection SQL for positive-only filtering/order, local imported
  art and profile identity, and consumed-entry disappearance. The five existing
  migration/retention, isolation, guarded-consumption, rollback, and receipt
  invariants also pass. Host SQLite does not execute Room flow delivery on Android.
- Gradle: **BUILD SUCCESSFUL**, with **nine focused `DigimonScanPolicyTest`
  passes**, Android-test Kotlin compilation, lint, and debug assembly passing.
  The historical **438-case** full-app run above was **not rerun in this pass**.
- Lint retains **174 existing warnings and seven hints**, with no errors.
  Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `StorageScanCollectionTest` has **three compile-only cases**: header Scan action
  with Adventure preserved; visible partial versus enabled complete conversion;
  and simulated reactive removal to empty with feedback and Close retained.
  These Compose assertions were not executed and do not establish a live Room
  conversion or actual button-corner geometry.
- Source finish review returned **ship**, with no material introduced bugs.
  That disposition is source-level only: **no visual/runtime approval**, device
  execution, installation, or screenshots are established. This documentation
  update records supplied evidence; it does not rerun the checks.

### Deferred native acceptance

- Tap the actual top-left Scan data chip and check its corner geometry, hit area,
  and placement opposite Adventure. Enter from empty Storage and confirm both
  actions remain usable and Adventure retains its destination.
- Scroll a populated collection at large font scales, with long card/species
  names and EN/PT-BR/JA strings. Inspect pixel art, local-card identity, ready-first
  order, percentages/progress, and feedback/Close outside the scrolling list.
  Confirm partial Convert is disabled and full Convert is enabled.
- Exercise the shared preview from Storage and Dex: cancel, system Back, optional
  nickname, IME clearance, focus/return focus, and guarded controls during conversion.
- Complete one conversion against live Room: verify the new inactive Storage
  individual and nickname, immediate Storage update, consumed-row disappearance,
  selected-preview retention until completion, and feedback/Close after the last
  scan disappears. Verify the existing VB/BE profile/countdown contract.
- Rotate and restart during collection/preview/conversion and after completion;
  verify safe state restoration, persisted scan/individual state, and no duplicate
  consumption or creation.
- Exercise loading, loaded-empty, read failure/Retry, and conversion failure on
  Android. Require usable dismissal and recovery, not merely compiled assertions.

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

The host SQLite checks use the exported v24/v25 schemas and production 24â†’25
migration SQL to verify anchors, neutral NPC emotion, schema compatibility, and
preserved individual identity/private chat/player trust/recruitment data.
`WorldEcosystemPersistenceTest` additionally exercises real Room migration and
checkpoint reopen on Android. Compiling the Android tests does not execute them.

The interaction checks use the production 25â†’26 schema, database guards, and DAO
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

The Radar living-world additions occupy schemas 25â€“28 (26â†’27 movement/social inputs,
27â†’28 transcripts/intents/NPC checkpoints). `test-living-world-migrations.py`
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
read-only private/ended/remote contexts, one shared clock across Radarâ†’chat lease
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

For the gameplay-feedback recheck, exercise FPâ†”2Dâ†”battle and private-chat return
without another manual tab/screen tap; drag pitch at near/far distances; toggle
labels and World tabs; tap overlapping participants and speech/battle markers;
observe changing HP while the scene stays visible; and let local wilds greet or
attack without an Accept prompt. Check preparation failure, changed active
partners, repeated outcomes, stale/out-of-range fixes, and background/replay.
Friendly sparring and voluntary team intervention retain invitation/selection.
Conversation taps now open the normal full-screen chat layout, including group
authors/participants when applicable. Verify Radarâ†’chatâ†’Radar and chatâ†’battle
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

## Debug Radar spawn picker

Tap the debug spawn button to keep its random behavior; hold it to choose any
loaded card character. The searchable picker includes undiscovered characters
and cards excluded from automatic World spawning, identifies variants by their
local character ID, and displays each sprite with its species and card name.
An explicit selection is revalidated inside the spawn transaction; a removed
character does not fall back to a random spawn.

```powershell
py -3 -B scripts/test-debug-spawn-db.py
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.world.WorldSpawnSelectorTest" --tests "com.github.nacabaro.vbhelper.screens.worldScreen.*" :app:compileIntegrityCheckAndroidTestKotlin :app:lintDebug :app:assembleDebug --offline --console=plain --max-workers=2
```

The three host SQLite checks execute the production picker/automatic-spawn DAO
queries for disabled cards, absent discovery/profile rows, manual names, sprite
isolation, duplicate card slots, and card deletion. `DebugWorldSpawnTest` covers
real Room spawning, individual/personality/relationship creation, the 20 m radius,
random-pool eligibility, and stale selection rejection. `DebugSpawnControlsTest`
covers tap versus hold, disabled gestures, and searching/selecting a card variant.
Android tests require a device or emulator to execute; compilation alone does
not establish touch, layout, or persistence runtime acceptance.

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
.\gradlew.bat :hardware-dim:test --rerun-tasks --offline --console=plain
```

The repository does not commit retail card dumps; use only licensed local fixtures. Without environment variables the tests look for `original.bin` and
`BEM_CARD_IMAGE.bin` in the DIM module, and report absent fixtures as skipped.
An explicitly configured path that does not exist fails the test. Sprite changes
are generated in memory; the source images are never modified.

## Lint scope

The pre-existing `app/lint-baseline.xml` remains unchanged. A successful lint run
means there are no errors outside that baseline; it does not mean all historical
warnings or baseline findings have been fixed. Dependency upgrades, resource
removal, and persistence changes should be evaluated separately rather than made
solely to remove advisory warnings.

## Blast Evolution / Jogress cinematics

### Hit depth and shared wind-up effects

The hit-depth follow-up replaces the fixed centre offset with the support plane of
the rendered fighter's transformed bounding box, including turned/mirrored models
and the actual camera direction after arena collision constraints. Large hit sprites
lift within their camera plane so their lower edge clears the floor. Both ordinary
and cinematic impacts use these anchors while retaining scene depth tests.

Ordinary wind-ups reuse prewarmed cinematic glow/data/ripple assets in per-fighter
pools. `activeTechniqueStartupProgress` is a read-only snapshot field, published only
in STARTUP and frozen by simulation pause. The old fixed-size Compose startup rings
are removed. Reduced motion omits orbiting motes and retains a restrained charge cue.
Wind-up colors are extracted once at preparation from the matching small/large attack
sprite's dominant visible hue; transparency, dark outlines, and white cores do not
wash it out, and missing art keeps the existing neutral/support fallbacks.

One final focused check passed: **14 cases** in `BattleImpactVisualTest`,
`BattleStartupVisualTest`, and `BattleAttackSpriteColorTest`, plus Kotlin compilation.
It covers projected clearance of all bounds corners under rotation/mirroring, floor
clearance without changing depth, charge progression, reduced motion, paused startup
progress, sprite-hue extraction, and small/large sprite selection. Native render review
was not performed for this follow-up.

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.screens.offlineBattle.BattleImpactVisualTest" --tests "com.github.nacabaro.vbhelper.screens.offlineBattle.BattleStartupVisualTest" --tests "com.github.nacabaro.vbhelper.screens.offlineBattle.BattleAttackSpriteColorTest" --offline --console=plain --max-workers=1
```

### Finisher lifecycle

The snapshot-owned finisher clock freezes ordinary combat while running focus,
transform, reveal, charge, release, impact, aftermath, and restoration. FORM lasts
6,500ms and JOGRESS 7,000ms after the launch beat was shortened to 600ms. Core commits damage once at IMPACT; the renderer's
150ms hit-stop never changes damage or the combat clock. Killing blows defer the
terminal result until restoration. Jogress suspends both originals and presents
one retained result instance; their actions/queues resume afterward.

```powershell
.\gradlew.bat :app:compileIntegrityCheckKotlin :app:mergeIntegrityCheckResources :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.battle.offline.*" --tests "com.github.nacabaro.vbhelper.screens.offlineBattle.*" --tests "com.github.nacabaro.vbhelper.rendering.*" --tests "com.github.nacabaro.vbhelper.world.ecosystem.*" :app:compileIntegrityCheckAndroidTestKotlin :app:assembleIntegrityCheck :app:lintIntegrityCheck --offline --console=plain --max-workers=2
```

`BattleFinisherTest` covers clock isolation, exactly-once damage/resource accounting,
partner suspension, terminal aftermath, cancellation, later windows, and frame-chunk
determinism. `BattleCinematicTest` covers fusion visibility through the entire attack,
side-by-side staging, reduced motion, aspect-aware framing, restored camera composition,
and visual hit-stop. Profile/transition/UI tests cover both equipped slots, validated
move aliases, result titles, alpha-derived blend silhouettes, and fixed arena/command layouts.

Initial combined check: **295 JVM cases, zero failures/errors/skips** across the four
filtered packages above. Kotlin/resource compilation, Android-test compilation,
integrity-check APK assembly, and lint passed with the existing baseline. Cleanup
regressions also verify that cinematic impacts/misses do not replay on combat resume.

Runtime effects are original procedural planes and imported DIM attack artwork.
Base/result/transition actors and effects are prepared before readiness and retained
through each sequence; transformations toggle visibility instead of recreating GLBs.
Sound/haptics honor system and saved audio settings, pause/background, and consumed
phase guards. No additional distributed character images or music are introduced.

Presentation follow-up: **220 JVM cases, zero failures/errors/skips** across battle,
offline-screen, and rendering packages. APK/test-APK assembly and lint passed.
`BattleAttackMotionTest` checks fresh vector-based facing, the standard left-authored
large-art convention, acceleration into contact, tracking continuity, whole-victim
framing, and transparent-padding removal without modifying source art. Regular hit
sprites expand around a body-sized footprint; special/critical hits are larger.

`OfflineBattleFinisherPresentationTest` passed on Galaxy A34 / SM-A346M in the isolated
`.integritycheck` application. It asserts all nine command bounds and arena bounds
remain unchanged (1px tolerance) before, during, and after successive stronger specials
in portrait and landscape, with no fighter-asset reloads during playback. One batched
inspection plus one confirmation used six native screenshots each, covering pre-launch,
flight, and impact. Confirmation showed correct repeated large-sprite direction,
complete victim framing, foreground impact bursts, and actual committed damage
(286/313 in the two controlled captures). Review captures are in
`C:/Users/julye/AppData/Local/Temp/opencode/finisher-presentation-confirmation`.

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.battle.offline.*" --tests "com.github.nacabaro.vbhelper.screens.offlineBattle.*" --tests "com.github.nacabaro.vbhelper.rendering.*" :app:assembleIntegrityCheck :app:assembleIntegrityCheckAndroidTest :app:lintIntegrityCheck --offline --console=plain --max-workers=2
adb -s <serial> shell am instrument -w -r -e class com.github.nacabaro.vbhelper.OfflineBattleFinisherPresentationTest com.github.nacabaro.vbhelper.integritycheck.test/androidx.test.runner.AndroidJUnitRunner
```

The new native fixture exercises shared POWER-special presentation; imported FORM/
JOGRESS variants, Radar-specific encounters, prolonged frame-time/thermal behavior,
and device audio remain separate acceptance coverage. Pure regressions cover the
shared FORM/JOGRESS lifecycle and retained visibility through aftermath.

## Adaptive battle repositioning (movement rules 2)

Live configurations default to `BattleMovementRules.CURRENT_VERSION` independently
of damage/catalog ruleset 3. `NpcBattleAdapter` explicitly pins older envelopes that
omit `movementRulesVersion` to movement version 1: Gson invokes the configuration's
default constructor, so absence cannot be detected from the decoded value alone.
Explicit version 0 also retains historical movement for unsafe legacy data.

The new policy reevaluates autonomous preparation before wind-up, uses relative
target hysteresis, allows lateral observation/charging footwork, upgrades an unstarted
basic fallback when an equipped move becomes available, and steers around occupied
lanes or clamped arena edges. Explicit orders/focus and committed wind-ups retain
their targets. Rendering follows travel heading while moving, with a simulation-time
hold between unchanged render samples to avoid facing flicker.

Verified: **324 JVM cases, zero failures/errors/skips**, including the 12,000-battle
paired-seed balance matrix. APK assembly, Android-test compilation, and lint passed
with the existing baseline. The first combined packaging attempt stalled in dex;
fresh single-worker packaging recovered successfully.

`BattleRepositioningTest` covers lateral mixed-kit observation, readiness footwork
without extra damage/resource spending, pre-start target/technique reassessment,
explicit focus/order retention, committed wind-up retention, blocked lanes, boundary
escape, and legacy movement. `BattleFighterFacingTest` covers travel versus attack/
knockback facing and unchanged-frame heading retention. `NpcBattleVersionTest`
compares missing-field v3 checkpoint recovery with its historical replay.

```powershell
.\gradlew.bat :app:testIntegrityCheckUnitTest --tests "com.github.nacabaro.vbhelper.battle.offline.*" --tests "com.github.nacabaro.vbhelper.screens.offlineBattle.*" --tests "com.github.nacabaro.vbhelper.rendering.*" --tests "com.github.nacabaro.vbhelper.world.ecosystem.*" --offline --console=plain --max-workers=2
.\gradlew.bat :app:assembleIntegrityCheck :app:assembleIntegrityCheckAndroidTest :app:lintIntegrityCheck --offline --console=plain --max-workers=1
adb -s <serial> shell am instrument -w -r -e class com.github.nacabaro.vbhelper.OfflineBattleRepositioningTest com.github.nacabaro.vbhelper.integritycheck.test/androidx.test.runner.AndroidJUnitRunner
```

The added native fixture samples a real 2v2 session for travelled distance, lateral
spread, playable bounds, and renderer errors, with three staged captures. It is
compile-verified but **not executed**: the Galaxy A34 connection dropped during the
build, and ADB/mDNS currently show no device. Earlier finisher screenshots do not
establish this movement change's visual acceptance.

## Farm home shell â€” Phase 0 + Phase 1 (2026-10-30)

Phase 0 route plumbing and Phase 1 farm-home presentation were implemented and verified on the
connected Galaxy A34 (SM-A346M, Android 15, 1080Ã—2340).

- :app:compileDebugKotlin and :app:assembleDebug pass; the app-level JVM suite (:app:test) is
  green.
- On device: the Digifarm renders as the Home surface, the command deck opens Radar, Arena, Shop
  (store tab) and Partners, the partner slab opens at half/expanded and the back ladder collapses
  before leaving the app, and no fatal exception appears in logcat.
- Farm diorama revision: the roster resolves the active partner plus favourites (12/12), residents are
  built from stored sprites, the island animates while the HUD stays static, and tapping a resident
  opens the cosmetic sheet.
- Captures live in .plan/: inal_home.png, inal_roster.png, inal_half.png,
  inal_expanded.png, inal_radar.png.

Not verified (needs owner input or longer runs): idle battery drain, light-palette contrast over the
bright island, TalkBack order, 1.3 font scale, tablet layout, and the aesthetic verdict itself.

### Farm home shell â€” follow-up round (2026-10-30)

- Top bar rebuilt as a single shelf with Material vectors; Settings reachable from a dedicated gear
  and from the first menu row. Device-verified: gear opens Settings.
- The 3D world now lives in the app shell and is paused/hidden behind places. Device-verified: a
  Home â†’ Radar â†’ Home â†’ Partners â†’ Home round trip produces exactly one scene-ready and one GLB load
  in logcat.
- Resident movement is capped below the renderer's interpolation limit (0.22 units/step) so walk
  steps animate instead of snapping; resident tap still resolves on device.

### Farm home shell â€” feedback round 3 (2026-10-30)

- Farm settings sheet (resident limit 1..12, artwork Mixed/VB only/Dot only) writes to the
  farm-world DataStore; device-verified stepper 12â†’11 and an artwork switch that rebuilt all 11
  residents.
- Camera target locked to the followed resident (no drag override); sampled projection stayed at
  x 519â€“541 of 1080 while the Digimon walked. Follow response 8.0.
- Farm framing 3.20 â†’ 4.20 default, max zoom 4.8 â†’ 8.0.
- Resident collision at 0.115 units plus capped 0.22-unit walk steps; seeded tableaux relaxed.
- Partner overview down-arrow now collapses (Expanded â†’ Half) instead of being a no-op.
- Fixed a blank-world defect: the frame gate never restarted once the texture surface became
  available (retry added); island, residents and animation re-verified on the Galaxy A34.

