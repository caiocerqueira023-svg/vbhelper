# Read-only Time Stranger layout profile

Digimon World DS now has a registered read-only phone profile for the observed USA
DraStic 64 KiB raw save, plus a separate opt-in desktop signature-research inspector;
see [WORLD_DS_RESEARCH.md](WORLD_DS_RESEARCH.md). World DS now permits provisional
party snapshot copies with source-local identity and reviewed copy authority; native
writes remain disabled. The format profile below remains specifically Time Stranger.

This module is an independently implemented reader, not a vendored editor or writer.
It has no Android, Room, network, or save-file mutation dependencies. The application
and the PC inspector use the same reader.

Format evidence was inspected at the following pinned public research revision:

- https://github.com/DotingDavid/DSTS-Save-Editor/blob/9447d574129d2d5d782e82447e4885b935afc909/save_layout.py
- https://github.com/DotingDavid/DSTS-Save-Editor/blob/9447d574129d2d5d782e82447e4885b935afc909/save_crypto.py
- https://github.com/DotingDavid/DSTS-Save-Editor/blob/9447d574129d2d5d782e82447e4885b935afc909/save_data.py

The 3,098,176-byte PC container is entirely AES-128-ECB encoded using a public,
game-format-wide constant. This is not a user's Steam credential. Its decoded
CSV header reports a 3,097,152-byte payload. Header recognition and structural
checks are not cryptographic authentication or proof of acceptance by the game.

The profile searches for a unique strided alignment in the known party/bank and
farm regions. It preserves numeric species, the stored UTF-8 name, personality,
raw progression values, and candidate creation hashes separately. A stored name
can be a nickname, so it is never promoted into an authoritative species mapping.
Record keys are locations in an observation, not permanent individual identities.

The first two main-array positions are treated as sentinels; the following six
are party positions. Active bank records before the inactive boundary are listed
as active records, not as independently proven in-game ownership. The inactive
boundary and any surviving tail records are retained for review, not counted as
owned. Farm records remain review-only until controlled game-written samples
verify their occupancy semantics. Duplicate/zero identity candidates are flagged.
No records are merged and no native marker or UUID is written to any save.

Current capability includes **provisional party/bank snapshot copies**. They receive
app-owned permanent identity and receipts, not verified game-wide native identity.
Farm/boundary/tail records remain review-only. Canonical numeric crosswalks, native
save writing and true moves remain separate milestones in `MULTI_GAME_TRANSFER_PLAN.md`.

Synthetic decoder fixtures contain no private saves. To inspect an authorized
local sample without changing it, run the `:game-save-core:run` task with the
save path as its sole argument. The inspector prints aggregate counts only.

## Comparison tooling

The same CLI also accepts `--compare <before-save> <after-save>`. Both files are
opened read-only. The output contains aggregate possible-match, ambiguity and
field-change counts, never names, candidate hashes or native record bytes.

`IdentityEvidenceComparison` requires the same game, format profile and decoder
version. A possible pair requires one nonzero candidate appearing exactly once in
each observation, on an active candidate with no review issues. A repeated value
anywhere, including an inactive tail or farm record, makes that candidate ambiguous.
Names, species, offsets and positions do not substitute for candidate evidence.
Even an unchanged file or a unique candidate pair does not establish permanent
identity, common playthrough lineage, or ownership. Comparisons do not mutate
observations, create bindings or import individuals.

Read-only inspection of the authorized reference `0001.bin` produced 6 party and
35 bank active candidates, with one bank-boundary and five farm records retained
for review. Existing backup/reference and cross-slot comparisons were exercised;
see `TESTING.md` for aggregate receipts. These observations agree with earlier
structural research; the user subsequently confirmed the 6 party / 35 bank / 5 farm counts.
The private saves remain outside the repository.

### Controlled rename evidence

An actual user-labelled before/after rename pair was decoded read-only. The tracked
party-position-1, level-86 record retained its nonzero candidate identity, with one
occurrence in each decoded observation. Species, personality, EXP/talent/bond/history
and location were unchanged. Only record-relative bytes 0x04..0x0D changed within the
name buffer; the rest of the 0x150-byte record was byte-identical. Whole-observation
comparison likewise found one name change and no decoded progression change.

This is direct evidence for name-field interpretation and candidate persistence across
that rename. It does not prove stability through movement, evolution, recruitment,
compaction or branches, nor does uniqueness in these two snapshots establish global
native uniqueness. Private names, candidate values and save contents are not shipped
as fixtures; a separate synthetic regression captures the observed invariant.

### Corpus validation across seven save slots

All seven authorized save slots (0000–0006; autosave plus progress variants spanning 11
to 41 owned records) parse with the same record phase, zero species-parity mismatches
(record db_id always equals its +0x104 duplicate), unique nonzero creation hashes and
sequential 1..N farm slot indices (with one zero-hash farm record present in every save).
Personality skills span 1..74, superseding the README's "40 skills" note with the
layout's 74-entry table; talent fields spread ~11000..100000 consistent with a ×1000
display scale (100000 = 100); the scan table stays inside its documented region with
percentages ≤200% and no anomalies; every header parses as the expected CSV.

One baseline claim failed at corpus scale: the forward linked list (name+0x14C,
"next_db_id") is mostly maintained but not authoritative — slot 0002 holds two active
records whose pointer contains non-species values (94221, 435221) while the remaining
corpus links cleanly (40/40, 39/39, 10/10, 18/18, 16/16). The reader deliberately
ignores that field; future writers must rebuild any chain they need instead of trusting
it. The corpus also retires several controlled-sample asks: new samples are only needed
for operations the seven slots cannot demonstrate (fresh recruit/insert and removal).

In-game load acceptance subsequently passed (2026-10-16): a desk-built candidate for slot
0000 (party-position 1 name, exactly 8 changed bytes in one window run) was swapped into
the save folder by the user and loaded by the retail game, which displayed the new name
and kept the save working. The tool never writes to savedata; the swap was user-performed.
This accepts the container writing and this first field edit for the rename scope only —
no claim is made yet for other fields, sidecars or the battle/live-state tail.

### Roster structure and the inactive tail

The roster block is stably partitioned: active records occupy a contiguous slot prefix
(party positions then bank), followed by a contiguous tail of inactive records, then
roughly 970 zeroed slots to the region end. Inactive tail records stay fully intact —
species, level, name, personality, identity hash and chain fields are all preserved — and
their stored roster index simply continues the bank numbering (party positions store
1..5 and one 0 across the six party slots; bank counts 1..N across actives and the tail).
The forward chain (name+0x14C) runs through the active prefix and the tail in slot order
and is terminated by the final record, which always carries a zero identity hash and a
zero pointer; every other tail record in every observed save holds a real identity. That
tail is the reactivation target: flipping the stored flag on the first inactive bank
record returns the roster to an active prefix the game itself would have written, which
is exactly what the experimental `--edit-activate` candidate does (one byte, awaiting
in-game acceptance). Reviving a mid-tail record instead would leave an inactive hole
inside the active prefix, so the reader flags it and the writer refuses it.

## Snapshot-import review boundary

Decoded records now include an exact native-record Base64 snapshot: 0x150 bytes
for main records and 0x158 bytes for farm records. Unknown record bytes are retained
alongside decoded facts. Source-global tables, container context and a verified
writer are still required for native return; record retention is not a lossless
individual export guarantee.

`SnapshotImportPlanner` builds immutable revision/profile/codec/planner-bound
reviews for explicit record selections. An adapter's view-only capability,
ownership or identity research notices, uncertain records, candidate collisions
and missing native snapshots restrict acquisition. Species mapping pending is
preserved separately because a confirmed-owned but unmapped individual must
eventually be archivable inactive, without guessing a canonical species.

Exact source/revision/profile/codec/record receipts distinguish previously imported
items from additional selections. Their status and restrictions participate in
the preview fingerprint. A receipt does not establish native identity across
another source or revision. Android now has a separate guarded archival-acquisition
producer; the core planner itself does not acquire individuals or write source saves.

Native refresh has a separate three-way name/personality review. It preserves source
stored names independently of app nicknames and distinguishes source-only changes,
app-only changes, convergent changes and conflicts. Applying an Android refresh
requires a compiled policy, a fresh source read and an unchanged archived baseline
and app field state. Choices and before/after app values are audited separately from
the exact native record bytes. Earlier snapshots remain immutable, and repeat
confirmation does not reapply app changes. Time Stranger has an explicitly provisional
production copy policy; refreshing its bindings requires identity-continuity confirmation.
This machinery does not validate global native identity.

Android's canonical mapping/materialization step is separate from the decoder and
snapshot acquisition. A user may review an exact canonical Digimon/form for an owned
native snapshot; that local reference is not an official numeric species crosswalk.
Materialization requires a permitted source policy, unchanged latest snapshot/mapping,
selected trusted artwork and live mode eligibility. It reuses the permanent identity
and personality and retains the native representation. App starting values are versioned
separately from native level/EXP, and a durable first-materialization receipt prevents
representation-creation replay. These application capabilities do not grant the Time
Stranger reader ownership, identity, numeric mapping or writer verification.

## Universal progress and original-document retention

`UniversalProgress` keeps semantic fixed-point changes separate from representation
entry and native anchors. A target checkpoint records its actual encoded/read-back
values, including rounding, so unchanged B/C records add no progression. Progress
earned elsewhere remains pending when observing an unchanged older representation.
No-progress return uses the retained native record, not an inverse of a lossy app
projection. Synthetic A/B/C tests exercise zero-change fidelity, real gains, stale
observations and reviewed regressions; they are not game-loaded writer evidence.

Production Android transports can capture exact source-document bytes at acquisition/
reviewed refresh. Those bytes are revision-checked and stored once in <=64 KiB chunks,
with per-native-snapshot bindings, avoiding oversized Room cursor rows. This preserves
unknown original document content. It does not assert that all sidecars, insertion
metadata, save variants, conversion curves or native return encodings are known.

Shared contracts also accept opaque adapter-native identity keys and content-based
recognition when several formats share a length. Digimon World DS is selected next,
but no DS reader is registered until its concrete region/container fixture is known.
Time Stranger permits provisional main-array copies while farm import, native
insertion/removal and unverified progression/return mappings remain gated.

For controlled verification, keep separate game-written before/after copies for
unchanged resaves, slot changes, renames, independent personality changes, party/
bank/farm moves, evolution/de-evolution, two same-species individuals and restored
branches. Compare one known change at a time. A changed stored name alongside a
species change is not automatically evidence of a user rename. Reported location
changes are observation changes, not a proven movement history. Confirm actual
container counts in the game before extending occupancy support.

## Existing-corpus evidence and provisional policy (schema 52)

Eleven authorized snapshots (seven slots, two backups and the rename pair) were
inspected read-only. Occupied main-array candidates were unique within each snapshot;
candidate persistence correlated with observed form, personality, progression and
party/bank-position changes. Slot order was not assumed chronological. Farm candidates
collide with different main records, so farm matching/ownership remains unresolved.
Private snapshots, names and candidate values remain outside repository fixtures.

Planner version 2 scopes provisional-copy duplicate checks to eligible, active,
issue-free records. Conservative evidence comparison still checks the whole observation.
The reference preview admits 41 party/bank candidates, requires identity review, blocks
all five farm rows and enables neither source removal nor insertion. Cross-slot
candidate lookup is a possible-match guard, not proof of globally unique native IDs.

## Read-only destination evaluation

`DestinationPreviewPlanner` evaluates a retained native anchor against one destination
observation and Party/Bank placement. Farm transfer is excluded by user choice. Same
game/profile/codec permits a native-return comparison, not execution or a lossless-return
claim. A coincident candidate in another format is not identity evidence; same-format
matches, including review rows, require review rather than automatic replacement.

Every report retains a versioned fingerprint of its native anchor, destination identity
and revision, placement and evaluated restrictions. No writer or conversion policy is
registered; `canExecute` is always false, even if a synthetic adapter advertises insertion.
Capacity and version/DLC constraints remain unvalidated. Unknown mappings are explicitly
unmapped/unvalidated; exact bytes retained in Storage are not a destination guarantee.

The inspector also accepts `--preview-destination <origin-save> <destination-save>`.
It reads both files, evaluates eligible main records for Bank placement and prints only
aggregate counts/restrictions. Probe keys are synthetic local keys; it creates no real
Storage identities, bindings, progress or transfer receipts. Both files remain intact.

## Progress context in destination evaluation

`TransferProgressContext` adds the app-side planning half without weakening the read-only
rule: recorded changes in the app's semantic units (`training.*`, `vitals`, `battles.*`,
`trophies`), the owner-scoped pending native-review ids (bounded at 32), and up to 16
retained native anchors with original-document and native-progress retention flags.
The repository sums synchronized progress plus unsynchronized deltas as a pure read —
no receipt, journey revision or row is written — and the fingerprinted report binds
account revision, latest app delta, latest native snapshot id, the recorded vector,
sorted pending ids and anchors. Any later growth or native review therefore rebuilds
the report on re-read.

The assessment never blends units: recorded app changes are `RECORDED_CHANGES_UNMAPPED`
until a per-game adapter exists, a non-empty native queue (or a destination record
whose native bytes changed under the same identity evidence) is
`NATIVE_CHANGES_UNRESOLVED`, and a foreign-game anchor is reported as unavailable
rather than reused. `canExecute` stays false in every status. The desktop probe runs
without app context and therefore reports `PROGRESS_CONTEXT_UNAVAILABLE` by design.

## Batch planning

`DestinationPreviewPlanner.batch` extends the same read-only evaluation to 1–16
selected origins against one destination observation. Items reuse the exact
single-individual preview; the batch fingerprint hashes sorted item fingerprints, so
selection order never rebuilds a report while any per-item change does. Origins must
not share an individual or a native anchor, and two distinct individuals whose
identity evidence collides receive `BATCH_IDENTITY_CONFLICT` without touching the
other items. `commonBlockers` exposes the intersection as shared restrictions, and
`canExecute` remains false for the batch and every item. The repository reads the
destination exactly once and re-checks all origins before and after that read.

## Writer commit journal and write rehearsal

`TransferCommit.kt` starts the section 11 writer ladder without claiming any writer.
Rung 1 is `NOOP_REWRITE`: planning binds one source revision to the candidate output
hash and document description, and the journal walks PREPARED → BACKED_UP →
CANDIDATE_READY → WRITTEN → VERIFIED → ACKNOWLEDGED with a mandatory backup revision
before candidate readiness and RECOVERY_REQUIRED only before verification.
`TransferRecovery` resumes only an unwritten destination, re-verifies an already
written candidate, and demands manual reconciliation for any destination that changed
after this operation wrote it — it never blind-overwrites. A rung-1 candidate equals
its source, so the source check precedes the candidate check and resuming the no-op
write is the conservative outcome.

The inspector's `--write-rehearsal <save-file> <output-dir>` rehearses that pipeline on
copies: it verifies a written backup, rechecks the source immediately before the write,
refuses output inside the source folder, writes candidate copies only into the provided
directory, then verifies readback hash and re-parse equivalence before acknowledging.
Formats with a container codec must first re-encode their decoded bytes byte-identically:
the internal deterministic `TimeStrangerContainer` (AES-ECB decode/encode, shared by the
reader path) edits no fields, and the supplied `0001.bin` produced
`codec_roundtrip=verified`. Raw formats copy bytes directly (`not-applicable`). Summary
output stays aggregate-only with no hashes. Both supplied saves (encrypted Time Stranger
`0001.bin`, DraStic World DS `.sav`) rehearsed backup-verified/readback-verified through
ACKNOWLEDGED with zero writes to the originals.

### The first field edit: stored-name rename

The controlled rename pair (before/after saves of one renamed party record) established
the exact game behavior: renaming overwrites from the name offset with the new UTF-8
bytes plus one terminator and does not clear the rest of the 32-byte window, so stale
tail residue from previously longer names remains after the terminator ("Gritaria"
becomes "Kuzuhamon" and the older, longer name's trailing "a" survives). The reader
resolves names only up to the first terminator. Seven further copies of the old name
inside a history-looking structure around 0x226480 were not rewritten by the game on
rename, so the live name resolves from the record window only.

Digest searches across plausible regions found no stored MD5/SHA-1/SHA-256/CRC32/Adler32
and no per-block CRC tied to the rename; the only other changed bytes were playtime
digits, the save timestamp and a small live-state tail (0xfded0, 0xfe1a4, 0xfe1ac).
`TimeStrangerEdits.renameIndividual` (`TimeStrangerWrite.kt`) mirrors the overwrite
semantics exactly, refuses farm and review-boundary records, and re-verifies its own
candidate (outside-window byte equality plus re-read name). The desktop-only
`--edit-rename <save-file> <output-dir> <party:N|bank:N> <name-file>` mode applies one
rename to a copy outside the source folder, taking exact name bytes from a file so names
never appear in command lines or logs. Writing the extracted 9-byte name onto the
`Before.bin` copy produced a candidate matching the game's own `After.bin` everywhere
except volatile fields; no game save was written.
