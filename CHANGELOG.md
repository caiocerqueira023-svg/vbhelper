# Changelog

All notable changes to Tamers Lab are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are project
milestones rather than strict SemVer.

## [Unreleased] â€” Alpha 0.7.0

### Added

- **EXP counters now written by Dawn/Dusk and Lost Evolution stories.** Dawn/Dusk record generation
  accepts the normalized EXP channels and writes all nine counters; Lost Evolution writes its single
  EXP word from the tracked story total. Round-trip tests now assert the EXP total survives all three
  story layouts.


- **World DS created-at-level bootstrap, completing the one-time story seed for all three titles.**
  Recovered the DS1 stat-scaling routine from the retail ARM9 (`FUN_020a1c70`: base species block ×
  `(level * growth + 100000) / 100000`, dumped growth rows, 999/9999 clamps), extended the species
  extractor with the creation block (base stats, base level, growth index), added
  `WorldDsCreationStats`, and wired the one-time World DS story seed into the send projection. Provider
  tests cover base-level start, growth and caps; transfer package 165/0.


- **Lost Evolution created-at-level bootstrap.** Recovered the LE level-up data from the retail ARM9:
  growth pairs at `DAT_020cf870 = 0x02142258` (base + random-range per stat), the `0x66666667` 1/10
  fixed-point scale, clamps 9999 (HP/MP) and 999 (combat), and speed copied from the species (cap 99).
  `LeCreationStats` implements the provider with the deterministic expectation of the game formula, and
  the send projection now performs the one-time Lost Evolution story seed for partners that only have
  app/VB progress. Provider tests cover level-1 equality and cap bands.


- **One-time Dawn/Dusk story bootstrap.** The game's own created-at-level data was extracted from the
  retail ROM (growth rows at ARM9 0x0211E298, 1/10 fixed-point scale, 9999/999 caps), implemented as
  `Ds2CreationStats`, and wired into the send projection: a Digimon with app/VB progress but no story
  block yet is seeded exactly once (level from the VP curve, stats from the game formula) before its
  first Dawn/Dusk write. Tests cover base-level equality, growth and the species level cap.


- **Dawn/Dusk round-trip coverage for the story block.** `StoryRoundTripTest` now also writes the tracked
  story stats into a Dawn/Dusk record and reads them back (level, HP/MP/ATK/DEF/SPI/SPD, nickname), so
  all three story layouts have write/read verification for the shared story progression.


- **Story round-trip tests.** New `StoryRoundTripTest` writes a tracked story block into a World DS
  record and into a Lost Evolution record and reads them back: level, HP/MP/ATK/DEF/SPI/SPD, EXP total
  (World DS), Lost Evolution aptitude and the stored nickname all survive the round trip, and a retained
  seed keeps its record identity while the story stats are written.


- **Bootstrap policy seam and engine-formula research for first-time story entry.** `StoryBootstrapPolicy`
  now defines the one-time story seed (level from the VP curve; stats supplied per title by a
  `NativeCreationStats` provider, refusing to invent stats without one), and the Dawn/Dusk per-level
  stat-growth formula was recovered from the decompiled ARM9 (growth rows + RNG + fixed-point scaling +
  clamps) and documented for the provider implementation.


- **Progress-domain foundation for how transfers will track progression.** Database v59 adds
  `story_progress` (one block shared by World DS / Dawn / Dusk / Lost Evolution: level, total EXP,
  HP/MP/ATK/DEF/SPI/SPD, optional aptitude) and `vb_progress` (stage, vital points, training, mood,
  battles, activity, generation/rank), plus the seed-provenance fields that make the first-time
  conversion happen at most once. `ProgressDomainRules` owns the VP→level curve (7500 → level 75),
  clamping, and the seed-once rule; no transfer behaviour changes yet.


- **Digimon Story: Lost Evolution is a supported transfer title.** The third NDS Story adapter
  (Japanese `BLEJ` ROM with the Operation Decoded English v1.1 translation) is recovered entirely
  from the ROM's own save/creator code and validated against the user's retail save: two
  `0x8000`-byte MD5 slots with the `DM3_000` signature, the serialized player state at `+0x20`,
  40 x `0x78` records at `0x1850`, the 8-slot u64 party array, four farm groups, the team
  container, and the game's own next-identity allocator. Saves now decode on the shared reader
  (party and farm residents are imported through the reviewed flow), species identity resolves
  from a ROM-generated 314-species asset with 51 documented localization aliases, native moves
  remove the exact record and every party/farm/team/partner reference, and sends write retained
  records or synthesize new ones (sequence bump, MD5 reseal, readback, byte-exact restore);
  farm residents are part of the transfer scope for this layout (the editor clears the farm
  reference), unlike the party-scope World DS/Dawn-Dusk adapters.
  App-side wiring covers the origin list, capabilities, import profile, stat policy
  (`le-stat-v1`), materialization and move/send branches. Full evidence and the honest
  verification boundary (in-game boot acceptance still pending) are in
  [LOST_EVOLUTION_TRANSFER_PLAN.md](LOST_EVOLUTION_TRANSFER_PLAN.md) and
  [game-save-core/LOST_EVOLUTION_RESEARCH.md](game-save-core/LOST_EVOLUTION_RESEARCH.md).

### Added

- **Choose where a sent Digimon lands (party, bank or farm), per DS title.** The send screen now
  shows destination chips for the selected game: Lost Evolution offers farm (default), party and
  bank using the engine's own add rules; World DS offers party and bank (bank = record stored
  without a party reference); Dawn/Dusk offers party and bank because its save layout does not
  serialize farms. The native editors accept the destination, keep the two-copy mirror, identity
  windows, player-state XOR checksum and byte-exact undo (World DS undo now also restores the farm
  table), the send journal records the destination so recovery rebuilds the same placement, and the
  commit check verifies the record landed in the container the player chose.

### Fixed

- **Partners that exist only in Dawn/Dusk/Lost Evolution still showed as blocked everywhere.** The
  landing projection (`outbound()` with no target) assumed World DS whenever the partner had no source
  game. For a species absent from World DS this returned null, so the send screen treated the partner
  as unprojectable and disabled every destination before the per-game blockers were consulted. When no
  source game exists, the projection now tries World DS, Dawn/Dusk and Lost Evolution in order and
  returns the first title that can actually represent the species — so e.g. Devidramon correctly
  exposes Dawn/Dusk/Lost Evolution and only shows the World DS species blocker.

### Fixed

- **Partners blocked from every story game even though their species exists.** Two causes were fixed:
  (1) the shared story block is backfilled from a partner's retained native snapshot before any
  bootstrap, so pre-tracking imports carry their real progress; (2) story stats are clamped into each
  title's record bands when written, fixing a Lost Evolution crash ("speed outside the record range")
  for story blocks seeded from Dawn/Dusk records whose speed word is 0. Verified against the owner's
  database: every previously blocked partner with a supported species now projects a record for at
  least one title; only species that no supported title contains remain blocked, with the explicit
  species reason.

### Fixed

- **Partners imported before story tracking existed can now be sent.** The send projection backfills
  the shared story block from the individual's own retained native snapshot (World DS, Dawn/Dusk or
  Lost Evolution layout) before attempting a bootstrap. Previously such partners had no story block
  and no app-character-derived seed, so every destination reported "no saved game record". A retained
  record is authoritative story progress, so the send now carries its real level/stats instead of
  being blocked.

### Fixed

- **Corrected the progress model: VB progress is the app's existing progression, not a second table.**
  `story_progress` remains the only new block. The experimental `vb_progress` table and its
  export/import wiring were removed (DB v60 drops it): bracelet transfers again read/write the existing
  app columns exactly as VBHelper always has, story imports only update the tracked story block, and
  the transfer inspector shows the Story-progress group beside the native facts. First-send bootstrap
  (VB/app progression → story) is still the pending piece.

### Fixed

- **Every stored Digimon is now projectable into every wired title unless its species is the reason.**
  The send projection no longer requires an app character representation: stats fall back to a minimal
  valid reading, and the species falls back from the individual field to the retained record's own
  native species name. There is **no species substitution**: a species the target title does not contain
  blocks that destination with an explicit reason, and a Digimon with no species assigned is blocked
  with its own reason. Both reasons are shown in the destination chooser and on the send screen header,
  replacing the old "No retained ... record" message.

### Fixed

- **Dawn/Dusk move-out duplication (record stayed in the game while the app imported it).** The
  Dawn/Dusk editor edited only the newest card copy, but the retail loader can load the other copy -
  so a moved-out Digimon stayed in the game while the app already had it in Storage. The layout now
  applies every logical edit to **all populated copies** (removal clears the party reference and the
  record in each copy, insertion places the record/party reference in each copy, replacement and
  admission touch every copy that holds the identity) and bumps/seals each copy's sequence and MD5,
  like the game's own slot writes. The user's divergent Dawn save was repaired directly (both copies
  now omit the moved Digimon, MD5s valid, sequences 13/15).

- **Lost Evolution encyclopedia registration.** The engine registers each created species in the
  player-state book at `+0x460` (one u64 per species, owned pattern `0x4000000100000000`, set by
  `FUN_020ae72c`/`FUN_020ae7ac` during creation). App-inserted records skipped that step, so sent
  Digimon did not appear in the game's species encyclopedia. The editor now registers the species in
  both card copies before mirroring, preserving any bits the game already set, with a regression
  test (World DS and Dawn/Dusk have no equivalent serialized species book in their layouts).


- **Lost Evolution save writes now load in the game (verified on-device).** After the player-state
  XOR checksum fix, an in-game send of a Digimon was accepted by the retail game - the first
  app-written save the game has loaded. Move-to-Storage (native removal/admission) is enabled for
  Lost Evolution again, since every edit path now recomputes the checksum, mirrors both card copies,
  mirrors the engine's delete routine and protects the current partner.

- **Lost Evolution saves carried a stale player-state checksum - the actual reason the game
  rejected modified saves.** The engine stores an XOR checksum at player-state `+0x4078` (save
  `+0x4098`): it sets the word to `0xFFFFFFFF`, XORs every u32 of the serialized player state
  (`0x7FE0` bytes, save `+0x20..+0x8000`) and stores the result, so a valid state always XORs to
  `0xFFFFFFFF`. Both game-written saves on disk satisfy this in both card copies; every app-written
  save (sends and moves) kept the old checksum word and therefore failed the game's post-load
  validation even though the header tag/MD5 were correct. The editor now recomputes the checksum on
  every edit before mirroring the body to both card copies, with a regression test asserting the
  invariant for removals and both insert kinds.

- **Lost Evolution synthesized records missed a mandatory identity window.** Every game-written
  record repeats the identity's high word at record `+0x08` and initializes the six tail slots at
  `+0x5C` to `0xFFFF` (the engine's `FUN_020b0100`/`FUN_020b15e8`). Records produced by the app kept
  `+0x08 = 0` and an all-zero tail, so written saves carried a record no game-written save ever has -
  the most likely reason the game rejected/ignored them. `buildRecord` now writes the marker window,
  and every insert stamps all identity windows plus the tail exactly like game-created records (this
  also repairs records already stored by older app builds). Regression tests cover both the fresh and
  the legacy-record paths.

### Changed

- **The one-time story conversion is only spent by a successful transfer.** Previews, destination
  blockers and the send screen's landing projection now compute the would-be story block without
  saving it; the seed (VB→story or retained-snapshot backfill) is persisted only after the write is
  verified and the operation is committed. Opening a partner or backing out of the confirmation no
  longer consumes the one-time conversion.


- **Bracelet transfers now use the canonical VB progress block.** Exporting to a VB watch takes stage,
  vital points, mood, age, trophies, wins/losses and activity from `vb_progress` when it exists (the
  legacy app columns remain the fallback), and a watch import records the observed values back into
  that block with the original seed provenance preserved. Story progress is never read or written by
  the bracelet path.


- **Story gains no longer leak into VB progress after the first seed.** Materializing a story-game
  record for the first time now seeds the canonical VB block once (tagged as a story-origin seed);
  later returns update the tracked story block only, so in-game level/stat gains cannot overwrite the
  bracelet-side progression. Seed provenance is preserved across later observations by both domains.


- **Story progress is now the authority for story-game sends.** When a Digimon has a tracked story
  block, World DS, Dawn, Dusk and Lost Evolution writes use its level/stats/aptitude exactly instead of
  re-deriving them from the app/VB block; the old conversion is only used when no story block exists
  yet. World DS also writes the normalized EXP total across its channels. The VB block remains
  untouched by every story send. Known limit: Dawn/Dusk and Lost Evolution record generation still
  zeroes their EXP counters, so EXP is tracked but not yet written there.


- **Story progress is now tracked across all story titles.** Every World DS, Dawn, Dusk and Lost
  Evolution snapshot records one shared story block on first materialization and again whenever the
  game returns: level, normalized total EXP, HP/MP/ATK/DEF/SPI/SPD, and Lost Evolution aptitude. The
  VB block is never read or written by this tracking, so story gains no longer have a path to change
  bracelet progress. Sending still uses the old conversion until the story-authoritative write path
  lands; this pass is observation only.


- **Transfer navigation rethought around one library and one Tools page.** The persistent
  Library/Storage/Tools tab row was removed: the library is the home, non-game origins (Storage,
  Vital Bracelet, V-Pet) continue the same shelf as full-width rows under the game plates, and the header's wrench key opens a dedicated **Tools** page instead of a flat overflow
  menu. Tools groups its rows by what they protect (Saves, Protection, Appearance), each row states
  what it does and shows a live count (linked saves, backups, native records), and the old dropdown
  menu, launcher-icon list and backups list were rebuilt with the same long-row grammar.


- **Transfer screens redesigned as a Digimon library.** The hub is a two-column shelf of game
  plates with launcher art and a party/farm/bank census; Storage and the bracelet are wide plates.
  An opened game is a box: save identity bar, All/Party/Farm/Bank scope tabs with counts, and a
  three-column grid of flat sprite cells with level badges and quiet empty slots. Tapping a Digimon
  opens a full inspector sheet with native stats, a collapsed native-details block and one primary
  Move action. Sends reuse the plates for destination games and the scope chips for Party/Bank/Farm
  placement. Technical copy review moved out of the main path into a quiet save-bar action.


- **Lost Evolution placement and record synthesis now follow the engine's own add paths.**
  Recovered with the ROM analysis: party insert `FUN_020afce8` (six members maximum, three active
  slots searched first, then three reserve slots), farm-group insert `FUN_020ea928` (first empty of
  the group's eight residents), a third eight-slot assignment container at player +0x157C
  (`FUN_020eb3e0` add / `FUN_020eb41c` remove) and the full delete pipeline `FUN_020ae0ac`. The
  editor now applies those exact placement rules, and freshly synthesized records initialize the
  six tail slots at +0x5C to `0xFFFF` exactly like the engine initializer `FUN_020b0100`
  (`FUN_020b15e8`). Sends use farm placement; move-to-Storage stays disabled until an in-game send
  is confirmed.

- **Lost Evolution sends re-enabled with the game's own two-copy write model; moves stay off.**
  Inspection of genuine saves showed the engine writes the *same edited body into both card slots*
  with consecutive sequences (e.g. 14/15) and fresh MD5s; writing only the newest copy left the
  slots divergent, which is what made the game reject/ignore app-written saves. The editor now
  mirrors every edit to both slots exactly like the game writer, and Lost Evolution removal was
  aligned with the game's delete routine (`FUN_020ae0ac`: party zero, farm-resident compaction,
  second farm list compaction, team zero) with the current partner protected. Sends (insert-only,
  farm placement) are enabled again; move-to-Storage for Lost Evolution stays disabled until an
  in-game send is confirmed.

- **Lost Evolution native writes are temporarily disabled (read/import stays available).** Device
  evidence showed the app-written slots were never loaded by the game (the emulator kept its old
  in-memory session), and repeated edits left the roster with holes in front of an untouched older
  copy. A user save that the game then refused to recognize was restored from the genuine
  pre-test backup (`NDS-wrapper-backups/vbhelper-pretest-20261120-1010/le.sav`, the save the game
  itself wrote). The writer is hidden from move/send until the roster-packing and header bookkeeping
  invariants are fully recovered and verified in-game; reads, roster browsing and imports are
  unaffected. The damaged file is preserved for analysis at
  `NDS-wrapper-backups/vbhelper-bad-save-analysis/le-bad.sav`.

### Fixed

- **Lost Evolution "Move to Storage" got stuck in recovery.** The first move actually wrote the
  save, but the post-write check looked at every view of every card copy; the untouched older copy
  legitimately still holds the pre-move roster, so the check failed and the operation was marked
  recovery-required - which then blocked every later action on that save. The check now only looks
  at the copy that was edited, acquisition/materialization failures can no longer turn a completed
  move into recovery, and recovery logs the exact cause. The pending Dorumon move completes
  automatically on the next action.
- **Lost Evolution sends now place the Digimon in the farm, not the battle party.** The party is
  story-gated (the user's save is capped at three members), so a fourth party slot would not be
  shown in-game. Farm/Digimon-list placement is always visible and the player can move the Digimon
  into the party when the story allows it.

- **A send could be silently overwritten by a game session that was already open.** If the
  emulator/game was running with the pre-write save in memory, it never loaded VBHelper's write and
  its next in-game save replaced it; the app then kept treating the individual as away. The
  send-complete message now tells the player to close the game completely before reopening it, and
  every linked-save refresh releases individuals whose native identity no longer exists in that
  save (the retained archive is untouched), so they return to Storage and can be sent again.

- **Lost Evolution send aborted with "Could not update this source".** The Lost Evolution
  identity allocator advanced the game's next-id counter on the *caller's* save bytes instead of
  the planned candidate, so the send journal's backup no longer hashed to the revision it was
  recorded against and the write was refused before touching the file (the source was left
  unchanged). The allocator now works on the candidate only, every planned edit asserts its
  source bytes still hash to the recorded revision, and a regression test covers all planning
  operations. Cause confirmed from the device journal (`files/experimental-game-sends`) and
  logcat (`IllegalStateException: Check failed` in the send journal check).
- **Lost Evolution had no DS launcher icon option.** The title is now one of the verified
  launcher-banner titles (`BLEJ`/`BLE` prefix) and appears in Transfer → Launcher icons, so its
  32x32 DS launcher icon can be imported like World DS/Dawn/Dusk.

- **Dawn/Dusk species showing as absent (Metal Mamemon, Black WereGarurumon, Prince Mamemon...).**
  The game's record/status name table is truncated to ~12 glyphs (`MtlMamemon`, `WGarurumonB`), so
  name-based identity failed and the UI reported those species as not in Dawn/Dusk. The packed
  full-name table in ARM9 is now extracted into the species asset, with 11 documented localization
  aliases (War=Were, Volcanomon=Volcamon, Rapidmon Perfect, Dot Falcomon, DK=Dark, chessmon
  colours, SL=Slash, Tsuchidarumon). Species identification, availability and send target selection
  all use it, so a species the game owns is no longer listed as missing.
- **Agumon (Black) transferring as Agumon.** When an individual's species is a base form but its
  assigned/exported card character is a variant of the same Digimon, the transfer now uses the
  variant; a different Digimon still never overrides the individual's own species.

- **A save whose two card slots are tied at the same counter is now editable.** The Dawn/Dusk
  engine stores counter 1 when it saves into an erased slot, so a save VBHelper wrote (counter 1)
  and the game then re-saved ends with **both** slots at 1 - a normal outcome, not corruption.
  The app previously refused with "The save has no defensible authoritative copy". It now resolves
  a tie automatically when the tied copies hold the same individuals (the World DS profile keeps
  its previous no-auto-choice rule), and an edit bumps the chosen copy into a unique newest slot
  again. Verified against the user's actual current Dawn and Dusk saves, including inserting a
  synthesized record into them.
- **Synthesized sends were routed to the retained inserter.** `Ds2SaveEditor.insertGenerated` delegated to `insertRetainedRecord`, so any
  generated record (identity 0 until the destination assigns one) failed with "A retained record
  must carry a nonzero local identity"; exact retained sends were unaffected. The facade now
  calls `insertGeneratedRecord`, covered by a regression test through the editor registry.
- **Sending to a played Dawn/Dusk save failed with "Could not update this source".** The
  Dawn/Dusk editor assumed a save with exactly one populated card copy; any save the game had
  written twice (both slots valid) was refused. Moves, sends and in-place repairs now edit only
  the authoritative (newest valid) copy and leave the older slot byte-identical, matching the
  game's own alternating slot writes. The send path also decides "replace in place" from the
  authoritative copy instead of both slots.
- **Cross-game sends from Storage are now simple.** A partner in Storage can be sent to any
  linked supported game that owns its species: the same title reuses the exact retained record,
  while a different title synthesizes a fresh record for its own layout. Previously a
  cross-layout send was refused.

Target: Alpha 0.7.0. This entry summarizes the refactor work that is implemented in the
tree; it is not a release announcement and does not claim a verified green build.

- **Digimon World Dawn/Dusk transfer support (read, acquire, native editor).** The NDS Story
  family gained a second verified profile recovered from the user's own USA ROMs by static
  analysis and checked against two retail saves: 64 KiB image, `0x7B2C` copies at `0x0`/`0x7B80`,
  MD5 header over the copy with the header zeroed, u32 sequence at `+0x18`, `DSDIG2` magic, 40
  records of `0x164` at `0x288`, party ids at `0x260`, last id at `0x25C`. Core adds
  `NdsStoryProfiles.DAWN_DUSK`, `WorldDs2SaveReader` and `Ds2SaveEdits`/`Ds2SaveEditor`
  (revision-bound removal, exact retained insertion, replacement, party admission, byte-exact
  restore, sequence bump and header reseal). The app ships the extracted `ds2_species.json`
  (436 species with base data, lineage and EXP curves — identical for Dawn and Dusk),
  `Ds2SpeciesTable`/`NdsStorySpeciesTables` resolution, `Ds2StatConversion` (`ds2-stat-v1`),
  Dawn/Dusk snapshot-import profiles and materialization seeding, and lists Dawn and Dusk in the
  origin list (title resolved from the file name; generic names fall back to Dawn). Full evidence
  and reproduction: `game-save-core/DS2_RESEARCH.md`. The Move/Send repositories resolve the
  profile and editor per observation, so Dawn/Dusk party records use the normal Move to Storage /
  Move to game flow (journal, backup, readback, presence); sends prefer the exact retained
  record and otherwise synthesize a Dawn/Dusk record from the game's species defaults plus the app
  projection (level capped by species max level, native EXP counters zero), with the retained
  record as seed so identity/unknown bytes survive; a cross-layout send is refused. Not included
  yet: a reviewed title-binding prompt, direct cross-title conversion, DigiFarm transfer and
  in-game acceptance of written saves.
### Added

- **DigiLine Free Talk ("Papo") â€” offline, retrieval-based chat with owned partners.** New
  `com.tamerslab.digiline.freechat` package: deterministic integer-Q16 intent retrieval over an authored
  Digimon-contextualized corpus (217 intents / 5,196 replies / 1,797 match phrases / 1,662 generated species-voice
  entries), an intent-fallback rung and a **licensed dataset fallback layer** (3,553 pairs from
  Cornell/OASST1/SODA after safety/language/quality filtering) that answers only when generated content has no
  answer â€” with a matched-only database gate plus an **authored in-world repair move** ("say it again")
  for input that matches nothing (no random dataset quotes: a device playtest showed they read as nonsense).
  Includes PT-BR input aliasing for matching, entity-aware normalization, safety classes with private redaction,
  Room v57 (`FreeChatProfile`, `FreeChatTurn`, migration), inline composer + suggestion chips + clear action on the
  DigiLine thread, persona-tone wiring, and a Debug kill-switch. Generated content always takes priority over the
  database layer; `DISTRIBUTION_GATE.md` documents the mandatory removal of licensed dataset text before any
  distribution. See `DIGILINE_FREE_TALK_PLAN.md` and `FREE_CHAT_IMPLEMENTATION_CONTRACT.md`.

- **VB Arena adventure stage ported into the Adventure screen.** The Adventure route now mirrors the
  composition of the discontinued `com.bandai.vitalbraceletarena` 2.1.0 XAPK: a city viewport with the
  walking team and the return countdown, the "Character list" grid that selects the departing team (up
  to four), the source CHECK dialog (team preview on the extracted ground plates plus the duration
  choice) and one primary action (Depart / Collect / Withdraw). The parallax is the original
  `BattleBg_0013`/`BattleBg_0012` artwork behind `ScrollBackground_Adv`/`ScrollMiddle_Adv`: it is
  recoloured off the main thread with a luminance -> palette gradient map built from the active saved
  palette, so the city keeps many distinct colours, all of them drawn from the theme (Tamers Lab
  violet/cyan, VB Lab charcoal/cyan, VB Arena green/mint, Digimon.net blue/gold) and keeps its
  dark-city-on-light-page polarity instead of inverting in the light palettes. Geometry and timing come
  from the shipped data read from the AssetBundle type trees: the running view uses the exact source
  `CharacterView` rects (224x196 at panel fractions 0.4265/0.7416/0.5168/0.8319 by 0.3936/0.6976 â€” two
  partners on top, two below, all on the right), the adventure walk is `m_CharaMoveAnimId = 12`
  ("Walk": 20 draw frames per pose at 60 fps = 0.333 s), and the background shader speeds are
  0.12/0.13 UV/second; the source Front layer is inactive and is not drawn, and the walk/parallax
  clocks live in the viewport so a departure starts moving immediately. The partner grid reuses the
  Storage character card (idle two-frame breathing), the departure-order badge sits on the card's right
  edge while the countdown/Ready caption moved to the card bottom so the two no longer collide, the
  header carries no exploring count, and the whole screen keeps the selected palette's surfaces,
  signals, typography and reduce-motion behaviour. The
  extracted artwork is used for personal preservation only â€” it is not redistributed â€” and the
  recovery map (scene rect table, animation master data, sprite borders) stays with the local
  reverse-engineering material, outside the repository.

- **Digimon hand-off animation for watch transfers.** The NFC waiting steps now animate the
  character leaving for or arriving from the watch: the original VB Lab loading sprites
  (`loadchara` two-frame pixel art extracted from the discontinued 1.4.6 APK) or the character's
  card artwork are revealed/hidden with a mask wipe over the original polling ring, respecting
  the system reduce-motion setting. The earlier illustrated guide was removed. The extracted
  artwork is used for personal preservation only and is not redistributed.

- **Connection secrets now ship with the app.** The AES key, the two 4-bit substitution ciphers and
  the VBDM/VBC/BE HMAC key pairs used to authenticate with the watch are baked into the build
  (extracted from the official VB Arena 2.1.0 base APK), so a fresh install can scan a watch without
  downloading the Arena APK and running `Import secrets`. They live in
  `security/BundledSecrets.kt` as the fallback of `DataStoreSecretsRepository`, and
  `BundledSecretsTest` pins them by regenerating the known watch test passwords (`be29a87e`,
  `a71dfb22`, `6ea33673`). A manual import in Settings still overrides the built-in values, which is
  the path to use if a future Arena release rotates the keys. The commit also fixes
  `SecretsRepository.getSecrets()`, which called `single()` on the never-completing DataStore flow
  and could hang if it were ever called.

- **The Home Digifarm now lives with each resident's full VB pose set.** Residents breathe on idle,
  stroll with the walk frames, jog with the run frames, work out, sleep occasionally, and pairs walk up
  to each other, turn face to face and share a happy beat before drifting apart. Walking keeps the
  diorama's pre-liveliness rhythm that the owner preferred: one 0.22-unit stride interpolated over
  1.1 s, a short foot-plant pause before the next stride, the walk pose cycling every 250 ms (jog 220
  ms), and no step bob. Pacing is probabilistic rather than directed: every finished beat rolls once
  for the next behaviour,
  with standing (49%) and walking (42%) dominating and a rare jog, workout, happy beat, nap or greeting
  making up the rest â€” no global cycle, schedule or cap on how many residents may do the same thing at
  once, so most of the time the island simply stands and walks about. Workouts and naps are held longer
  than the other beats (10-20 s and 30-60 s), so when one happens it is a state you can actually see.
  Greetings are the rarest roll
  (0.8%) and are held longer (6-9 s face to face), so an interaction is a small event instead of
  constant background behaviour. The cast seeds itself with fresh entropy on every creation, every
  resident owns its own random stream and greeting partners are picked uniformly at random, so no
  session replays the same show. Body collision models each
  resident as a circle the width of its extruded artwork: walkers are pushed around planted neighbours
  and the renderer adds a movers-only visual separation pass, so sprites can never be drawn passing
  through each other and a sleeping resident is never nudged. The roster resolves the complete
  twelve-pose artwork from the `DisplaySprite` view (or the bundled Dot set) instead of faking walk and
  happy with four stored frames, keeps the legacy four-frame fallback for partial data, and freezes to
  a staged tableau under reduced motion. Implementation and unit-test evidence live in `TESTING.md`.

- **Home â†” place transitions are animated instead of cut.** Choosing a place from the farm now
  dissolves the outgoing chrome while the place lifts and fades over the world; the farm itself holds
  the screen as a depth layer, pushing in slightly and staying composited until the new screen is
  opaque, so the world is never cut out from under a half-transparent page. Returning reverses the
  same motion: the place sinks away, the farm settles back to its framing and Home's chrome lands a
  beat later. Place-to-place changes keep the short horizontal slide; reduced motion falls back to
  ~90 ms crossfades. The farm's render loop pauses as soon as it is left, so no two Filament scenes
  run during a transition, and a new `NavigationMotionTest` pins the rule that Home never rides the
  tab slide.

- **Battle screen is now a full-bleed arena.** Arena battles and Radar encounters share one floating
  battle surface instead of a 16dp page gutter, a bordered 1:1 viewport and a half-screen command deck:
  the arena fills the window, a top cluster carries exit/back, the matchup and context line, recenter,
  Debug and pause, opposing Digimon carry projected name+HP plates while the selected partner's
  HP/MP/ESP strip floats bottom-left above the band, and one bottom command band holds the CP
  instrument, the polite event line and the nine 48dp cut-corner keys grouped as menus / orders /
  movement (portrait 3 columns, landscape 5 columns in two rows). The Radar battle keeps the Radar
  world full-bleed from the encounter through the fight â€” the square viewport special case is gone â€”
  and the AR stage deliberately keeps its established square layout. Countdown, loading/transforming,
  pause (with the tactical-pause switch moved into its panel), exit, Blast timing, finisher captions and
  result/rewards all float over the live arena; the duplicate deck/HUD copies are replaced by shared
  chrome, the `"Ã¢â€ â€™"` mojibake in the Blast attacker line is fixed, and the retired split-layout helper
  and its test are removed. Implementation and unit-test evidence live in `TESTING.md`; native
  captures, TalkBack and font-scale acceptance are recorded there after the final gate.

- **Radar place is now a full-bleed isometric world (owner decision 2026-11-03/04).**
  `RadarScreen` no longer uses the World-tab scaffold: the radar grid plate fills the window, the
  active partner stands at its centre as the player token, and wild Digimon stand on the plate as
  their extruded cardboard sprites with the farm's travel-heading/camera-assist treatment. Sprite
  travel is measured from real-world positions only (never from the rotating plate or the zoom), so
  the compass turn rotates the cardboard with the world instead of looking like a direction change;
  standing tokens face the camera like idle island residents and the mirror flag uses a latch. The
  radar clamps the camera assist to Â±15Â° so walking Digimon read front-facing, walk/idle poses use a
  smoothed speed with start/stop hysteresis (the ecosystem's 1 s interpolation steps cannot flicker
  the animation), and visibility is decided by the projected billboard rect instead of the
  visible-radius value, so zooming never hides a Digimon that is still on screen. The
  Radar place draws no distance/range rings (owner decision 2026-11-04): its compass is a large
  minimal instrument ring (120 thin cyan ticks, longer/thicker ticks at the four cardinal directions
  with the longest north, no inner circle or lettering, and a red two-triangle needle â€” the reference
  digivice arrow shape, larger triangle outward over a small gap and a smaller inward triangle â€”
  riding the ring at the nearest wild Digimon's bearing; while the radar is live the needle performs
  an occasional idle search gesture and returns to the true bearing, and the tick nearest that bearing
  is always drawn slightly longer so the reading stays visible (no gesture under reduced motion), and
  a floating top
  cluster + bottom signal band replace the old panel/scroll column. The violet ring under the partner
  token now has the interaction-proximity radius (the same radial fraction as a Digimon at
  `RadarWorldGeometry.INTERACTION_RANGE_METERS`) and a soft feathered halo/core texture instead of a
  hard band: the ring is now a single clean violet line drawn as a real 3D ground decal (depth-buffered
  under the Digimon, no Compose overlay), with a small ladder of pre-baked decal widths alpha-crossfaded
  as the ring radius changes so its thickness stays constant on screen and follows a pinch smoothly
  with no texture rebuild or settle delay and no glow; tapping a Digimon now uses its full projected
  sprite rect (min 48dp) as the hit target with no pressed ripple, like tapping a farm resident,
  instead of a floating box above its head (fixed 34Â° camera; the vertical
  tilt gesture was removed), and the interaction dot that used to appear over a Digimon's head is
  tilt gesture was removed), and the interaction dot that used to appear over a Digimon's head is
  gone. The zoom envelope is pulled in: the neutral view is 200 m visible, the widest zoom-out 400 m
  and the closest zoom-in 50 m (previously 500 m/1000 m/125 m), so the plate fills with nearby Digimon
  instead of empty ground. The plate is a derived
  `Arena/Radar/radar_plate.glb` (generated by
  `tools/build_radar_plate.py`) that drops the battle boundary, sky dome and floating voxel props,
  enlarges the authored floor quad (theme-bound with the same floor material) so the ground never
  ends at low tilt or wide zoom, and keeps the authored radar theme through a scene alias. The
  legacy 2D and first-person radars stay in the codebase and are reachable from the radar options
  sheet only while the master Debug switch is on. Encounter-directory updates come through a shared
  extrusion cache, a worker
  pool and nearest-first GPU uploads; the radar no longer recreates its Room flows on every compass
  tick (which had left the spawn list empty for long stretches) and re-resolves an expired region
  once on entry so its residents get sprite assets again. Characters that only own the neutral
  placeholder sprite are excluded from world population, Debug spawn choices and the radar, and
  stale placeholder encounters are cleaned when a region is prepared.

- **Koin dependency injection.** `appModule`, `databaseModule`, `networkModule`,
  `hardwareModule`, `repositoryModule`, `useCaseModule`, and `viewModelModule` start from
  `TamersLabApplication`; typed use cases (`TransferUseCase`, `BattleUseCase`,
  `SaveImportUseCase`, `ChatUseCase`, `FarmUseCase`) and repository adapters resolve from
  the graph. Covered by a Koin module-graph test and architecture-boundary tests.
- **Type-safe navigation and UI state.** `@Serializable` `AppRoute` destinations, a
  typed `NavHost`, deep-link resolution in `MainActivity`, and shared state contracts
  (`UiState`, `RequestState`, `StreamingUiState`, `UiEvent`, `UiEffect`,
  `OneShotEventQueue`).
- **Unified battle domain.** Shared `TechniqueDefinition`, `CombatantDefinition`,
  `BattleTeam`, `BattleStatusEffect`, and `BattleRules` in `battle/domain`; the offline
  simulator/session layer stays separate and the legacy online PVP/auth client lives in
  `battle/online` behind a flag.
- **Hardware module split.** `:hardware` is the Android NFC library; `:hardware-dim` is
  the pure Kotlin/JVM DIM/BEM card parser exposed through the `hardware` API. The DIM
  sprite parser is now Kotlin.
- **R8 and resource shrinking** for the release build, with explicit keep rules for
  Gson, Retrofit, protobuf-lite, Room, WorkManager, Koin, serialization, and Filament.
- **Baseline profiles.** `:baselineprofile` generator module, `profileinstaller`
  runtime dependency, and committed startup profile artifacts.
- **JaCoCo setup** for `:app` and `:game-save-core` (standalone report tasks; coverage
  not yet measured or threshold-enforced).
- **Performance scaffolding.** `SceneFrameTier` (Paused/Idle/Active/Reduced) and
  `SceneQuality` frame pacing, thermal downgrade via `PowerManager.currentThermalStatus`
  on API 29+, and the `BattleSceneChannel` scene/UI state split behind
  `BuildConfig.BATTLE_SCENE_CHANNEL_ENABLED` (default off).
- **Documentation set.** Module READMEs, ADRs 0001â€“0005, a root documentation index,
  Phase 5/7 decision records, and the Alpha 0.7.0 release checklist.
- **Translations.** Japanese and Brazilian Portuguese string resources for the previously
  missing keys (including a locale farm-home file), removing the 52 lint
  `MissingTranslation` errors.
- **AR battles.** Radar encounters can be fought through the camera. New `com.tamerslab.ar`
  package adds an ARCore 1.57.0 session (`com.google.ar:core`), a GLES 3.0 camera
  background layer, and a hands-free placement model that anchors the existing Filament
  battle on a detected surface: the player only points the phone at a table or the floor,
  and the scanner automatically hit-tests detected planes, ARCore depth points and instant
  placement, derives the scale from the surface, and starts the battle once the pose is
  stable (quality gates prevent premature locks; depth/instant hits are gravity-aligned so
  the arena never appears tilted). The AR scene shows only the two Digimon and the
  `battle_boundary` ring â€” the floor, grid, dome and debris are hidden so the camera feed is
  the backdrop. The battle `TextureView` is hosted on a `CONFIG_TRANSPARENT` swap chain
  (Filament 1.76.1 `ModelViewer` is opaque by default) so the fight composites over the live
  camera. Scale plans (`Auto`/`Tabletop`/`Room`/`Open space`) map the 16-unit radar arena to
  0.9â€“8 m, chosen automatically from the detected surface, and an "AR battles" radar
  preference enables the mode. **AR battles are a developer-only option**: the preference and
  every AR entry point are gated by the build-time `AR_BATTLES_ENABLED` flag (true for
  debug/integrityCheck builds, false for release, so production builds hide and disable the
  mode). Placement and battle share one ARCore session and one
  Filament engine; unsupported devices, a missing ARCore runtime or a denied camera
  permission fall back to the normal Coliseum battle. Radar commands now submit with the
  latest command stamp so ordinary GPS updates no longer cancel interactions as stale
  snapshots. Debug-only `ArDiagnosticsActivity` smoke test and `ArStagePreviewActivity` stage
  host, 44 JVM maths tests (`ArMathTest`, `ArBattleScaleTest`, `ArBattleCameraTest`) and
  `AR_BATTLE_IMPLEMENTATION.md`; camera permission plus an optional ARCore manifest entry.

### Removed

- **Command Points and the Support/incentive command are gone.** The trainer-shared CP pool never
  gated a production technique (every catalog cost was zero) and the cheer window only fed it, so the
  resource, its snapshot fields, events, statistics, failure codes, HUD instrument, loadout cost,
  strings, tests and documentation references were removed together. Checkpoints keep
  `formatVersion = 1`: Gson ignores the old `commandPoints`/`maxCommandPoints` keys and the NPC replay
  never read CP, so active battles and existing saves load unchanged. The partner's HP/MP/ESP readings
  now occupy the header space the CP instrument used to consume.

### Changed

- **The floating voxel cubes collide with each other, and the farm glows the same in every theme.**
  The cubes bob with independent amplitudes, speeds and phases on top of their group's bob, so a
  stacked pair drifts up to ~26mm into itself - the clipping the farm showed. A per-frame solver now
  composes each cube's animated world centre from the known parent chain (group + cube translation; the
  cubes are origin-centred and unscaled), separates overlapping pairs along their axis of least
  penetration (half each, 2mm pad, four passes) and rebuilds from the authored bob every frame.
  `FloatingCubeLayoutTest` replays the real fourteen-cube layout and asserts no frame intersects, and
  also asserts the unsolved bob does overlap so the case cannot silently go stale. Reading
  `TransformManager.getWorldTransform` inside the frame's transform transaction returns identity for
  these children, which is why the first attempt had no effect. Separately, the
  farm's emissive floor and cube accents used to bloom only in the dark palettes: light palettes
  compressed the pulse to 1.15, so the glow vanished. The Digifarm now keeps a 0.94 emissive baseline
  in light themes and its controller is allowed a 1.62 pulse ceiling, so the glow matches the dark
  themes while the floor's body stays saturated - only its bright lines enter the bloom range.
- **The Digifarm island keeps the original Links ground and no longer looks hollow through its own
  cliff.** The top is built from the source atlas again (`tools/digifarm/build_reference_textures.py`):
  the floor is cut into large 16px pieces (a 2x2 block of the authored cells, keeping the source's own
  tile seams inside each piece), and the pieces are laid out in contiguous regions like the reference
  floor - broad patches of one tile, occasional warm path tiles, long runs of the same piece, mirrored
  or flipped for variety, never a per-tile mix. The mosaic is pulled onto the Tamers Lab accent's low-saturation hue (other
  palettes derive their own at build time) while keeping most of the tile variation. The
  irregular exposed cells are a near-black void carrying **one continuous bright square grid**. The
  farm is now **monochromatic per theme**: the turf is derived from that theme's `energy` accent at low
  saturation and a dark value, the grid uses the same accent at full strength (the color the voxel
  blocks already use for their edges), and the cliff's authored strata highlights - the light lines
  near the rim - are lifted to that accent too, so grass, patches, cubes and side lines are one hue
  per theme (violet, cyan, green, blue). The rest of each palette is untouched.
  The cliff is a one-sided skirt that fades to transparent at its foot, so a low camera used to see the
  inside of the far wall through the near one on the island's flanks. The upper 48% of the cliff is
  fully opaque in the authored art, so those bands now render as an alpha-masked material in the opaque
  pass and **write depth**: the near side occludes the inside of the far side (about 70% of the
  artifact, measured offline with a ray/triangle check at five camera yaws), while the fading foot keeps
  its blend and its transparency. An intermediate inset "interior lining" was removed because it
  outlived the fading foot and read as a dark shadow hanging under the island.
  `validate_asset.py` pins the five-material set, the masked upper-cliff material and the three island
  primitives; `tools/ENVIRONMENT_THEMES.md` documents the reproduce steps.
- **"Import watch connection data" is Debug-only.** The connection keys ship in the build
  (`security/BundledSecrets.kt`), so a normal install never needs that row; re-importing is only the
  fallback for a future VB Arena release that rotates the keys. It now renders under
  `Watch connection` only while Debug mode is enabled, so the everyday Settings list starts with
  Card archive. The import itself is unchanged and still overrides the built-in values.

- **Settings is ordered and grouped like a setup flow; every option is unchanged.** Sections now
  run Watch connection â†’ Card archive (import plus the card-origin prompt) â†’ Appearance â†’ Sound â†’
  Tamer profile and preferences â†’ Species (catalog plus the VB-first/Dot-first artwork priority) â†’
  Communication (research notes, and DigiLine while Debug and legacy chat are on) â†’ Backup and
  restore â†’ Debug mode â†’ About and credits. The card-origin toggle moved next to card import,
  research notes and the DigiLine connection left the Tamer section, backup/restore moved above
  Debug and About, the redundant "Digimon graphics" header folded into Species, and the Tamer name
  is edited from its row in a dialog like the font and language instead of an inline field plus a
  separate save row. Row, section and dialog visuals are unchanged.

- **The app is now its own installed app, not a VBHelper update.** `applicationId` moved from
  `com.github.nacabaro.vbhelper` to `com.tamerslab.vbhelper` ([ADR 0006](docs/adr/0006-own-application-id.md),
  superseding ADR 0001), so installing this build no longer replaces or updates VBHelper and an
  upstream VBHelper release is no longer offered as an update here. The two apps install side by
  side. Consequence: a new install does not inherit the old app's local data - move a collection
  with `Settings > Export database` in the old app, then `Settings > Import database` in this one
  (cards live in that database; linked `.sav` saves are relinked; watch secrets ship with the
  build). The `integrityCheck` variant is `com.tamerslab.vbhelper.integritycheck`, and the
  baseline-profile generator, macrobenchmark, instrumentation package assertion and device scripts
  were retargeted. This is a build-configuration change: no APK was built or installed for it in
  this pass.

- **The Digifarm keeps its baked, unlit look; the only thing lighting left behind is a real drop
  shadow.** The island and its voxel blocks are authored emission-baked and the residents are unlit
  extruded pixel art, so a key/fill/rim rig plus image-based ambient was built, reviewed on the
  SM-A346M, and reverted: it read as a different game rather than as the same farm lit better. What
  stayed is the ground shadow each resident was always supposed to have - and that shadow had in fact
  never rendered. With the pinned gltfio 1.76.1 an **unlit** blended primitive of the generated
  resident GLB never reaches the color buffer; the contact shadow is now a blended **lit** material
  whose albedo is black, so it renders as a clean alpha-blended shadow with no lighting dependency.
  The quad is larger and softer than the old hidden one (1.24 x 0.92 local units, 66% alpha, softer
  falloff), so a resident visibly sits on the grass. A JVM test pins the blend/lighting combination so
  the shadow cannot silently disappear again.
- **The last visible remnants of the retired multi-farm system are gone.** DigiLine no longer has a
  "Farms" tab (it lists only Storage and Wild Ones), Storage no longer captions a partner with the farm
  it lives on, and the farm-group chat route/screen, the old `DigifarmScreen` (create another,
  archive, needs, transfer) and the Radar|Digifarm tabs on the legacy `World` route were deleted. The
  `World` alias now opens the Radar world directly. Stored farm data and tables are untouched; only
  the exposed UI was retired.
- **The battle command band is a fixed 3Ã—3 instrument after Re:Digitize Decode.** The float
  disappears: row one is the partner's three equipped techniques (name plus EN cost and live
  availability), row two Items / Special / Strategy, row three Focus / Defend / Move. Move arms a
  one-tap arena destination (move-to-point via the scene's unpick) instead of opening a menu, and the
  selected partner's vitals were folded into the band header so nothing floats over the arena. The
  special/Blast timing is a Decode-style command dial with a dark technical ring, a yellow progress arc,
  a red target arc, ticks and a needle, the ordered move's name in the centre, and a full-screen tap as
  the only input (no Stop button). The nine deck keys are bare HUD keys â€” no fill and no angular
  frame, each settled by a 1dp hairline â€” matching the bare-key instrument vocabulary of the top
  cluster. The same deck is shared by training/Arena, Radar and the AR stage.

- **The Radar ground scrolls as you walk.** The active partner still stays planted at the plate centre,
  but the authored grid now slides opposite to the player's real travel, the way the legacy 2D radar
  scrolls its grid under the centred marker: the phase is anchored to the first location fix, expressed
  at the plate's linear metre scale (one authored cell = 60 m), folded to half a cell so the finite
  authored floor never runs out, and rotated with the compass like the plate and the tokens. The slide
  is low-pass filtered with a time constant scaled by the reported GPS accuracy (0.35 s + 0.08 s per
  metre; coarser than 30 m the grid holds still), so standing in place does not shake the ground.
  Because the authored world grid is a translucent mesh that now slides under the ground markings, the
  metre-ring decal and the interaction ring were given late Filament render priorities (5 and 6) so the
  ring stays above the grid instead of flipping under it as the plate moves. The mapping is covered by
  two `RadarIsometricGeometryTest` cases (direction/rate and the half-cell fold). Slide rate follows
  real metres, so it is subtle at the widest zoom and strongest when zoomed in â€” same as the 2D radar.

- **Home â†” place background now crossfades instead of cutting.** The destination pages are transparent
  over the global device pattern, so during a change the farm layer *is* the arriving page's
  background. It used to be held opaque and dropped at the end of the transition, which made the
  background swap abruptly; it now pushes in while dissolving into the device pattern over 240 ms on the
  way out, and settles back while fading in over 240 ms on the way home.
- **Tamer Arena lobby bars collapse on scroll.** The search field and the Tamers/Tournaments/Records
  section tabs slide up while the opponent roster is scrolled down and return on any upward scroll or at
  the top, mirroring Storage's search bar; entering the team editor, a briefing or the bracket restores
  them. Reduced motion keeps the same behavior without the expand/fade animation.

- **Digifarm roster adds members through the shared partner picker.** The "In the Digifarm" sheet still
  lists current members beside the resident-limit and artwork controls, but "Add member" now opens the
  same full-screen Storage partner picker the Arena uses for choosing a partner â€” search, filters, sort
  and the sprite grid â€” instead of the sheet's bespoke candidate list. The picked partner lands on the
  island immediately, the picker stays open while there is free capacity, and it closes on the last
  slot. `FarmMemberOption` now carries the stored character so the roster can hand the picker real
  sprite data. (Native capture pending a connected device.)

- **One top-header type across the app.** Every boxed screen header now uses the same top-cluster
  material as Home, the Radar place and the battle surface instead of the old filled gradient banner:
  a palette-resolved scrim, a left-aligned title with a single quiet context line, one junction line,
  and bare 48dp keys â€” icon and target only, no fill, no hairline stroke and no cut-corner frame, the
  way Home's HUD keys already were. The back key always owns the left edge (the battle header's left
  key is its exit action); every other action the header was given now sits on the right instead of
  split across both sides, and when a screen passes no back callback the header uses the activity back
  dispatcher so it behaves like the system back gesture. The shared primitives live in
  `components/HudChrome.kt`; `TopBanner` keeps its API, labels and semantics, so no call site changed.
  Tokens resolve through the selected palette, including the light Arena and Digimon.net themes.
  Native captures are pending a connected device.
- **Place switching is lighter on mid-range hardware.** The transition is unchanged in shape, but the
  work that ran inside it is not: collection cards no longer re-decode their sprite on every arrival
  and on every idle-frame swap (a content-keyed, 16 MiB decoded-sprite cache now serves them), a
  destination is told when a route transition is still delivering it and skips its own per-card
  entrance instead of stacking a screenful of alpha/scale layers on top of the page motion, and the
  persistent farm surface keeps the window's full bounds for the shell's lifetime so leaving or
  entering Home no longer resizes and re-lays out the view that owns the Filament swapchain. Items
  that appear later (scrolling, later pages) still animate in. No visual design change; measurement
  is pending a connected device, see `TESTING.md` and `docs/performance/BASELINE.md`.
- **Wildâ†”wild chat and battles are paused for current versions (owner decision 2026-11-04).**
  `WildSocialInteractionGate` unwires the autonomous pair planner, the wild-pair battle
  transfers and their activation while the social planner, dialogue service, NPC battle
  simulation, persistence, history and Radar rendering paths all stay in the codebase. Wild â†’
  player greetings/challenges and every player-directed or joined encounter are unaffected.
  Pairs left open by an earlier build are cancelled with `WILD_SOCIAL_PAUSED` (releasing their
  participation claims) and paused pairs are omitted from the public ecosystem snapshot, so the
  Radar no longer shows wild-on-wild interactions while the feature is off. Re-enabling is the
  single `WildSocialInteractionGate.ENABLED` flag.
- **Package and product rebrand.** Kotlin packages and namespace are `com.tamerslab`; the
  launcher label is **Tamers Lab**, the root Gradle project is `TamersLab`, and the
  application class/theme are `TamersLabApplication` / `TamersLabTheme`. The
  `applicationId` remained `com.github.nacabaro.vbhelper` for upgrade continuity during the
  refactor (see [ADR 0001](docs/adr/0001-keep-application-id.md)); that decision was later
  superseded by [ADR 0006](docs/adr/0006-own-application-id.md), which gives the app its own
  `com.tamerslab.vbhelper` identity â€” see the "Changed" entry above.
- **Feature-based package layout.** `battle`, `farm`, `transfer`, `chat`, `species`,
  `world`, `storage`, `home`, `items`, `settings`, `adventure`, and `quests`, plus shared
  `data`, `domain`, `ui`, `rendering`, `companion`, `di`, `navigation`, `security`,
  `vitalwear`, `utils`, `widget`, `components`, and `audio`. The old top-level
  `screens/`, `daos/`, `database/`, and `source/` packages are gone.
- **Room schema location.** Exported schemas now live under
  `app/schemas/com.tamerslab.data.database.AppDatabase/`; schema version remains 55 with
  preserving migrations.
- **DI cutover.** The manual `DefaultAppContainer` is now a Koin-managed adapter rather
  than the composition root ([ADR 0003](docs/adr/0003-koin-dependency-injection.md)).
- **Battle delivery.** The offline battle screen can publish the full-rate snapshot
  directly to the scene while throttling Compose UI state; old behavior remains the
  default until device validation.
- **Baseline-profile work.** Existing generated profiles were produced before the Phase 6
  package move. They were deterministically re-mapped to the current class names with
  `scripts/remap-baseline-profiles.py` (framework entries untouched, unresolved synthetic
  entries dropped); on-device regeneration with the extended journeys is still pending,
  so the files are not a post-restructure measurement.

- **Home HUD unboxed.** Home's top zone is no longer a bar: it is two clusters floating over the
  Digifarm - resident count with a twelve-slot place gauge plus the bits readout on the left, the
  quests/settings/menu keys on the right - held by a palette scrim, a hairline ground line and HUD
  ink with a soft text shadow. The five place keys are free-standing again, but each one is now a
  56x66 machined key with its caption inside, near-opaque tonal fill, a readable frame, an inner
  bevel and an accent catch-light ([HudChrome.kt](app/src/main/java/com/tamerslab/farm/ui/home/HudChrome.kt)),
  replacing the translucent tiles whose scene bleed read as a false selection. A second pass removed
  the tiles entirely: the places are now icon plus caption on a bottom scrim that fades into the
  partner slab, framed by a single hairline console edge, so the whole home reads as one overlay and
  the slab is the only surface left. Captions auto-size instead of truncating (pt-BR "Parceiros" and
  font scale 1.3 both fit), press/current states use the accent plus a 2dp underline, and the row is
  capped at 460dp and centred so wide windows do not stretch it across the screen. A third pass
  moved the row below the partner slab, so the bottom of the screen now reads slab-then-places, and
  linked the two: the sheet reports how far it is pulled and yields exactly the row's 70dp band while
  opening, so the row fades and drifts out under the sheet's own motion instead of popping on a
  detent edge, with no overlap and no gap between them. The top ground and the place row's ground now
  paint the partner slab's own surface tone at its own opacity, and the slab's frame is drawn without
  its bottom edge, so the three zones read as one material and the slab flows into the place row with
  no hard line; the row's ground fades out at the screen edge the way the top ground fades into the
  sky. Follow-up fixes: the slab keeps only its upper edge (no side or bottom strokes, so it no longer
  disagrees with the full-width place row); the junction between slab and row is marked by the same
  inset hairline the top zone uses; the row's ground starts at the sheet's exact tone so the two fit
  together and only firms up toward the bottom; dragging the sheet is 1:1 under the finger because the sheet owns one height value
  instead of jumping between a detent target and a drag offset; the chevron drops an expanded overview
  all the way to peek; and the top status zone fades away while the overview is fully open. No blur or
  glass pass is applied to chrome: translucency stays a plain tint. The old top scrim was
  built from `colorScheme.background`, which is transparent in every palette, so it never painted;
  it now reads the palette background token. Every action keeps a 48dp target. The partner slab's
  stage separator was also repaired from a double-encoded `Ã‚Â·` to `Â·`. Compile-verified only; the
  device capture pass is still pending a connected phone.

- **Home entry points and boot.** The status zone now carries Transfer and Dex instead of Quests
  (Quests stays in the menu sheet); tapping a Digimon in the farm offers Follow, Set as active and Swap
  for one in Storage; switching the active partner by scrolling over the overview card is retired; and
  Home opens behind a boot cover while the habitat, residents and active partner load.

- **Farm membership (rewritten).** The island now shows the active partner plus an explicit member
  set in the farm DataStore instead of favourites; the set seeds from favourites once on first edit,
  activating a partner adds it, and the resident sheet can remove a partner. The camera follow choice
  is persisted by character id so roster refresh and rotation can no longer re-point it. The "who is
  here" sheet now curates membership next to the quantity control: every stored partner is listed with
  its state and an add/remove action. Superseded
  note: the active partner is no longer forced to be a favourite and a leader that steps down is
  favourited, so promoting someone no longer deletes the previous leader from the island; explicit
  swaps opt out because there the outgoing partner is meant to leave. An artwork filter can no longer
  drop the leader (it falls back to available artwork). Full review: [farm-home-review.md](analysis/farm-home-review.md).

- **Cinematic Digifarm camera.** The diorama now drifts on its own: a slow orbit around the followed
  partner with elevation and distance drifting on independent periods, hard cuts to a different shot
  every 14-26s from a rotating set of low / three-quarter / high shot heights and capped below ~33
  degrees of elevation (higher angles distort the extruded sprites),
  parked for 2.8s by a touch and re-based on the visitor's framing afterwards (never
  snapping back to the previous loop). Disabled under reduced motion.

- **Home entry points reshuffled.** The status-zone key that opened the Dex now opens Settings, the
  place row's DigiLine key now opens the Dex, and the partner slab's chat key opens the whole DigiLine
  list with the active partner's conversation pinned first.

- **Cinematic camera: close-ups and floor collision.** Cuts now include a close-up distance band, the
  grazing angles are weighted more common than the steep one, and the lens is floor-limited so no cut,
  drift or manual drag can put it at or below the island surface.

### Changed

- **The Digifarm floor now glows only where it should: the lightest grass tiles and the wireframe pulse,
  the darker grass stays still.** The island floor used to pulse as one emissive surface (emissive factor
  1.08-1.86), so every grass pixel brightened. The base floor now holds a steady emissive factor (1.45,
  the old pulse's average), and a new glow-overlay layer pulses with the old envelope on top of it: the
  overlay carries the authored colour of the lightest 8% of the 16px grass mosaic tiles plus the bright
  wireframe grid lines (alpha mask, everything else transparent), so those elements breathe in their own
  colour - not white - while the darker grass keeps a constant luminance. The overlay is a duplicated
  island-top primitive (+0.0006 world units above the base, `island_glow_overlay_baked` material,
  alpha-blended) and the base `island_top` texture is byte-identical to the previous art. The floating
  voxel cubes keep their pulse. `tools/digifarm/build_reference_textures.py` generates the overlay,
  `build_assets.py` adds the primitive/material, and `build_environment_themes.py` bakes the same mask in
  each palette (default payload byte-identical to the GLB overlay); the island-side/voxel textures are
  untouched and `validate_asset.py` pins the 6-material/4-primitive overlay contract. GLB
  444,512 -> 592,876 bytes (overlay texture), GPU +1.33 MiB.
- **The Items tab is a grouped catalogue.** Two tabs instead of three: My items and Items store. My
  items is sectioned by function - Training, Evolution, Vitals, Special missions - with the quest
  battle stock folded in as its last section (the separate Battle items tab is gone). Entries are
  compact instrument rows: a 44dp framed glyph well, a two-line name that reserves both lines, the
  effect glyph and only the number that list needs (owned count, stock or store price; an unaffordable
  price turns to the error colour). The old wide cards left a large empty artwork well, locked names
  to three lines and produced ragged heights; the new rows are uniform and roughly half the height,
  and special-mission rows take the full width because their names carry a goal and a window. The
  store opens with one wallet strip (balance plus how many entries are affordable) above the same
  sections. Special-mission items no longer fall through to a question-mark glyph: steps, vitals,
  battle and win missions have their own glyphs. The item dialog repeats the row grammar and puts the
  price and affordability next to the action, so Purchase is disabled with a reason instead of failing
  after the tap. Strings are in the three supported locales and `items_kind_count` is a plural.

### Added

- **Special missions can be assigned straight from the partner overview.** Each of the four mission
  slots on an eligible partner's overview is now actionable: an empty slot reads "Tap to assign" and
  opens a chooser listing the owned special-mission items that fit that slot only (steps, vitals,
  battles, wins), with quantity and an Equip action. Equipping consumes one item through the same
  gameplay path as the Items tab and the slot/progress flow refreshes live, so adding a mission from
  Home no longer requires going through Items -> Use item -> partner picker. Running missions keep
  their removal action and completed ones keep their collect action. Slot/item mapping lives in
  `SpecialMissionSlots` with unit coverage.

### Fixed

- **The special-mission chooser now always offers the kind of mission of the slot that was tapped, and
  equips the slot that was tapped.** The partner overview's four mission cards passed the device-side
  `watchId` where the assign chooser expected a slot index 0..3. `watchId` is only 0..3 for app-created
  partners; watch-imported partners carry the watch's mission id, and scan/world/migration-created
  partners carry `(characterId*4+slot)%65535`. On those partners the chooser could list a different
  mission kind (or nothing, labelled "Wins slot"), and equipping either overwrote another row or failed
  with a `requireNotNull` crash. Slot identity is now the row position among the partner's four missions
  (`UserCharacterDao.getSpecialMissions` orders by id; new pure `SpecialMissionAssignment` helpers), the
  tapped card carries its row id, and `GameplayRepository.applySpecialMission` updates exactly that row
  while preserving its `id`/`characterId`/`watchId` so watch export keeps working. App-created partners
  with `watchId` 0..3 behave exactly as before. Covered by `SpecialMissionAssignmentTest` (10 cases).
- **A failed mission equip now explains itself inside the chooser.** The failure snackbar was posted in
  the activity window while the chooser dialog was still open, so it was dimmed behind the dialog and
  could not be read. The chooser now shows a persistent inline error under the list, cleared on retry or
  dismiss, with the string localized in the three supported locales.
- **Home feedback messages are no longer hidden under the phone's navigation bar.** The farm Home draws
  edge-to-edge and its Scaffold reserves no content insets, so the Material3 snackbar sat flush with the
  window bottom where the 3-button navigation bar covered almost all of it (the reported "message appears
  below the action bar"), with the place dock and partner slab over the rest. The Home snackbar host now
  applies the navigation-bar inset itself and lifts above the place dock band plus the partner peek slab
  while the deck is at Peek; other destinations keep their existing `safeDrawing` placement. Only the
  settled detent is read, so dragging the deck never relayouts the snackbar per frame.
- **A form gained on the watch now survives the next app start.** Reported flow: a Digimon imported
  from World DS went to the watch, evolved there, and came back to the app on its evolved form â€” and
  was then rewritten to the form it had when it last left the app, while the evolved form stayed in the
  Dex. The import itself was right (the app stores the watch's current slot); the World DS archive
  keeps the record it imported, and its species repair/reconcile runs on every startup and save refresh,
  so the stale archived record kept overriding the newer device state. Native records are now
  authoritative only while the game produced something newer: `NativeReconcilePolicy` makes a retained
  watch baseline captured after the archived record supersede it, and makes a re-read of an unchanged
  record a no-op instead of a revert (`reconcileReturnedSpecies`, `applyWorldDsReturn`). A game record
  that really changed â€” the Digimon was played in the game again â€” is still adopted. Covered by
  `NativeReconcilePolicyTest` (5 cases).

- **The Transfer tab reads as one destination again.** The Home transfer action (header key and
  menu entry) opened the Vital Bracelet pane directly; it now opens the overall Transfer tab with the
  origins list first, and the bracelet is one row there. Inside the tab, the embedded watch steps used
  to render their own `TopBanner` under the hub's banner - the reported "two headers on one screen" -
  so `ActionScreen`, `ReadCharacterScreen`, `WriteCardScreen`, `WriteCharacterScreen` and `ChooseCard`
  now take `showHeader` and, when embedded, render their name as an in-flow section heading with the
  exit the dropped banner used to carry (Cancel) instead. The hub also keeps a page back-stack (Back
  returns to the page that opened the current one rather than jumping to the list), the "Manage saves"
  page no longer pushes a second Transfer destination from its watch block (that block is gone; the
  bracelet row in the origins list is the one entry point), and its empty state no longer repeats the
  "Linked saves" heading that the section header above already shows. The outgoing-partner control is
  now one card (label, partner or "Receive only", Change) with a radio-list chooser, and the options
  menu states the current page and carries the same icons as the rows it mirrors. Unit tests green;
  verified on the SM-A346M at 1080x2340 (origins list, watch pane, chooser dialog, embedded write
  step).

- **The bracelet send screen no longer crashes for a partner with an assigned DIM/BEM.** Captured from
  the owner's SM-A346M: `java.lang.IllegalStateException: Cannot access database on the main thread` at
  `CardDao_Impl.getCardById` â† `ScanRepository.getCardDetails`. The send-confirmation screens collect
  that flow from Compose, so its builder ran on the main thread, and the blocking
  `CardDao.getCardById`/`getCardByCharacterIdSync` methods threw there. It only executed for a partner
  with an export target, i.e. a catalog/game-origin partner after a compatible-DiM conversion;
  watch-origin partners took the Room-managed flow path and never hit it. The builder now runs on
  `Dispatchers.IO`, and it resolves to a neutral empty card instead of collecting a single-result query
  that can legitimately have no row.
- **Preparation for a physical watch is recoverable.** Any failure while building the outgoing payload is
  logged as `SCAN_PREPARE` and reported through the existing "could not be prepared" panel with its Retry
  action instead of taking the process down (fatal VM errors are still rethrown), and the pre-export
  reaction baseline is best-effort because a missing baseline only skips the later comparison.
- **A recorded export target that is not a real DIM/BEM no longer enables a send that cannot be
  written.** Materialization can point `exportCardCharacterId` at a card-independent definition while
  no physical slot is associated; bracelet capabilities now only count a card-backed slot, so the
  transfer screen offers the compatible-DiM conversion instead of showing a usable "send" that fails
  during preparation.
- **The individual information shows the DiM/BEM a converted partner is exported through.** A
  catalog/game-origin partner has no card of its own, so the Storage detail line (and the Debug card
  field of its info editor) stayed blank even after a DIM/BEM was assigned. It now falls back to the
  exact slot recorded for bracelet export: on the owner's device the "Marcos" record now reads
  `Metal Mamemon Â· 10 NuMetalEmpire`, and the same conversion renders the write-card screen
  ("You will need 10 NuMetalEmpire card") instead of crashing.

- **Farm walking restored to the diorama's pre-liveliness rhythm.** The resident-life pass had replaced
  the old stride rhythm with continuous movement at a fixed cadence; after device feedback the owner
  asked for the earlier feel back, with the correct frames (walk frames on the walk pose, run frames on
  the jog). `FarmDioramaSimulation` now walks in the old strides again â€” 0.22 world units interpolated
  over 1.1 s, then a 1.15-1.6 s beat before the next one, which leaves the short foot-plant pause the
  old island had â€” and jogs in longer 0.32-unit strides over 0.8 s. The renderer cycles the walk pose at
  a fixed 250 ms and the jog at 220 ms; the speed-derived cadence, the beat latch and the step bob from
  the previous passes are gone. A jog can no longer strobe because one stride fills the whole
  interpolation window.
- **The Radar's biome readout is visible again.** The OpenStreetMap biome system was still resolving and
  still biased spawn attributes, but its only UI was a 10sp label inside the legacy 2D radar, which is
  reachable only while the master Debug switch is on â€” the isometric redesign dropped the display. The
  floating top cluster now carries the same localized "Surroundings: â€¦" line under "Radar Â· NW" (absent
  while the lookup is Null, so unavailable map data never invents surroundings), and `WorldScreen`
  publishes the resolved biome as soon as the lookup returns instead of only after a successful region
  command, so a rejected/stale spawn refresh no longer leaves the readout empty.
- **Full-screen pickers and editors no longer darken the page in the light palettes.** The Arena
  "Choose partner" picker, the species sprite picker and the species catalog editor painted a
  transparent dialog surface (`colorScheme.background` is transparent in this theme, so the global
  device pattern can show through normal pages), which let the screen underneath and the dialog window
  dim show through and made the whole page read as a darker theme. Their windows are now edge-to-edge
  and paint the palette's own opaque page background, with the wrapper handling the system-bar/IME
  insets, so light palettes stay light and dark ones stay dark.
- **Radar encounters no longer crash a few seconds into the battle.** The Radar battle's Filament
  scene is hoisted to the root of the Radar battle surface so Compose can never replace the
  `AndroidView` mid-fight: the previous layout branch was re-composed at the countdown, destroying and
  immediately recreating the Filament engine and tripping a native use-after-free inside
  `libfilament-jni.so` (SIGSEGV, fault address `0x0`). The scene is now composed once per encounter and
  renderer-permission changes only pause its frame loop. Verified on the SM-A346M with two consecutive
  Radar battles that ran to "Radar victory" with a single engine creation each, an empty crash buffer
  and a surviving app process. A settled battle also hands the surface to the result panel now: the
  live top cluster, command band and projected HUD step aside instead of showing inert keys behind the
  outcome and its rewards.
- NFC/auth logging no longer prints callback URIs, decrypted card bytes, tag ids,
  passwords, or full transfer tokens.
- Backup/device-transfer rules exclude `secrets.pb`, battle auth tokens, LLM settings,
  and logs; cleartext traffic is denied by default with a narrow allowlist.
- Removed the unused `CompanionValidatedDatabase` and a duplicate Koin binding for
  `ValidatedCardManager`.
- Lint baseline cleanup and missing-translation work landed; the zero-error state is not
  yet re-verified after the Phase 6 restructure.

### Security

- Online PVP is gated by `BuildConfig.ONLINE_PVP_ENABLED` /
  `OnlineBattleFeature`; the residual HTTPS, Keystore, and App-Link risks are tracked in
  [PHASE4_DECISIONS.md](docs/refactor/PHASE4_DECISIONS.md).
- No secrets, retail card dumps, real saves, pairing links, or controlled captures are
  committed. See [CONTRIBUTING.md](CONTRIBUTING.md#secrets-fixtures-and-private-data).

### Quality and tooling

- detekt and ktlint were scaffolded for Phase 7.1 but the aggregate run could not be
  completed reliably under the constrained build memory settings. They were **removed
  from the build and deferred to a follow-up quality pass** (see
  [docs/refactor/PHASE7_DECISIONS.md](docs/refactor/PHASE7_DECISIONS.md)); the build no
  longer applies those plugins.
- `:app:lint` remains the static-analysis command and is a blocking CI step. The 52
  `MissingTranslation` errors were fixed with real JA/PT-BR resources and
  `app/lint-baseline.xml` was pruned to 62 justified pre-existing warnings; the clean-run
  result is recorded with the final verification.

### Release preparation

- No production signing keystore exists; release smoke builds are unsigned or signed
  with the local debug key only.
- The R8 mapping file must be regenerated and retained for each release candidate.
- Baseline profiles must be regenerated or explicitly re-mapped after the package move.
- Play Console upload has **not** been done.

### Verification

Post-Phase-6 verification ran on the uncommitted working tree on 2026-10-31
(all Gradle commands with `--no-daemon`; the final numbers include the Phase 1/5/7
gap-closing work â€” an earlier pass in the same session with 1,155 tests and a
149,002,850-byte APK is superseded):

- **Unit tests:** 1,157 tests, 0 failures, 0 errors, 7 skipped across
  `:app:testIntegrityCheckUnitTest` (900), `:game-save-core:test` (192),
  `:companion-protocol:test` (4), `:pc-save-companion:test` (11),
  `:hardware-dim:test` (28, 4 intentional skips), `:hardware:testDebugUnitTest` (22).
- **Build:** `:app:assembleDebug` and `:app:assembleRelease` (R8 minify + resource
  shrinking) succeeded with the regenerated profiles; the unsigned release APK is
  149,019,234 bytes (142.12 MiB) and the R8 mapping was regenerated.
- **Lint:** `:app:lint` reports 0 errors, 422 warnings, 38 hints (warnings/hints only;
  25 stale baseline entries remain a hygiene follow-up).
- **Device:** 281/281 connected instrumentation tests passed on the SM-A346M (API 35);
  debug and debug-key-signed release builds installed with `adb install -r` and cold
  launched without crash entries (single `am start -W` smoke launches, not benchmarks).
- **Baseline profiles:** regenerated on the device with startup + best-effort
  scan/battle/farm/transfer journeys (2,723,442 bytes each; 24,408 rules).
- **Coverage:** JaCoCo reports for app and game-save-core; all six named classes are
  above the 80% line target (81.0â€“95.8%). No threshold is enforced yet.
- **Benchmarks (device-gated):** cold-start TTID median 875.0 ms over 5 iterations;
  battle frame CPU duration P95 13.3 ms; the farm 3-second window recorded P95 29.8 ms
  and includes 3D asset loading (steady active/idle and thermal behavior unverified).
- **Not verified:** Room query plans, 30-minute soak, onboarding timing, multi-API
  (28/31/34) instrumentation, production signing, and Play upload. The battle-channel
  flag stays off until a flag-on/flag-off Perfetto A/B run. No commit was created and
  nothing was published.

Useful references: [documentation index](docs/INDEX.md),
[performance baseline and budgets](docs/performance/BASELINE.md),
[Alpha 0.7.0 release checklist](docs/release/RELEASE_CHECKLIST_0.7.0.md).

