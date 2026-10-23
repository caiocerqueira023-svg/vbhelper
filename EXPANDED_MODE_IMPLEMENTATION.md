# Expanded Digimon mode — implementation status

**Superseded product policy (2026-10-17):** the owner removed VB/Expanded gameplay
modes and their participation restrictions. Every stored Digimon is usable. The
replacement VB-first/Dot-first preference changes artwork priority only, with mutual
fallback. Historical mode/isolation/gray-partner requirements below are implementation
history, not current acceptance criteria. See MULTI_GAME_TRANSFER_PLAN.md's binding
product contract for universal journeys, progress retention and DIM stat overrides.

**Current increment:** Milestone 3, step 3 adds card-independent definitions through
schema **45**. The launch-migration fix makes the recreated `DisplaySprite` SQL
match Room's expected definition exactly. Full card-free gameplay and native
upgrade acceptance remain pending; see `TESTING.md` for current evidence.

## Dot matching, mode isolation and loading follow-up

- Three pinned sources now attach **3,368 sets to 1,211 ready Digimon**, retaining
  all 1,662 species identities: DMC archive plus Humulos Hatchery (746 Color-device idle animations)
  and Wikimon Color V-Pet sprites (212 animations, September 2026). Examples now ready include
  Koemon/Monmon, Red Vegimon/RedVegiemon, Azhdarmon, Zeke Greymon/ZeigGreymon, Diaboromon/Diablomon and
  Armageddemon/Armagemon. Color qualifiers, X-Antibody spellings and documented regional names resolve
  without collapsing forms; ambiguous Kumamon/Mercurymon artwork uses source stage evidence.
- The current pack contains **27,419 frames / 12,995,584 bytes**. Ten malformed
  upstream sets are excluded. Pinned revisions, roster snapshots, name-pair evidence and per-set
  provider credits are retained.
- User normalization now applies **only in VB mode**. Expanded mode selects the
  bundled Dot default in the database view, asset-backed portraits, Arena previews/fighters
  and result/saved-match forms. **44 → 45** changes only the view, preserving saved choices.
- Arena follows the mode: catalog Dot entries are primary in Expanded, VB assets/imports are primary
  in VB; other-mode artwork is exhibition-only fallback sorted last and barred from cups.
- Initial preparation reads one archive in bounded batches, bulk-inserts missing
  definitions/profiles, skips installed sets and caches immutable Dot frames.
  Radar prepares outside its mutation transaction and does not reinstall artwork
  during population refresh. Arena indexing reads renderability metadata rather
  than every species' twelve image BLOBs.
- Inactive Storage cards use grayscale sprites and gray borders instead of visible
  availability messages, retaining history access and an accessibility state.
- Verification: 27 host checks and 271-case filtered JVM batch passed; debug
  assembly and Android-test compilation succeeded. Native
  loading-time and visual acceptance remain pending a connected device.

## Milestone 1: catalog and offline artwork

Implemented:

- Canonical species IDs stored in `tools/species_identity_registry.json`; repeated
  card entries and approved regional aliases reference a shared identity. Forms
  retain their qualifiers. Card/slot references are metadata, not species IDs.
- Pinned, generated `app/src/main/assets/species_catalog.json`: **1,662** canonical
  reference identities, **824** Digimon with confirmed bundled Dot artwork, and
  **1,891** validated alternative sets. Non-Digimon references are classified
  separately and are not marked expanded-ready.
- `app/src/main/assets/dot_sprites.zip`: **20,493** unique native pixel buffers,
  **9,525,674 bytes**. Each set retains named upstream roles, original URLs,
  original PNG hashes, artist and device provenance. Original device artwork is
  preferred where available.
- Offline Dot browsing in the existing species appearance chooser. Matching uses
  canonical identity, including renamed local templates; search includes regional
  aliases, authors and device sources. All matching alternatives are bundled.
- Only a selected set is materialized in Room. Reserved negative, content-addressed
  Sprite IDs are validated for collisions and inserted without replacement. The
  existing species-wide Save, display projection and asset-backed renderers apply
  the choice. Selection does not create a partner or bypass progression.
- No database schema change or data reset is required for this milestone.

`tools/dot_sprite_coverage.json` lists missing associations, excluded references
and the eight rejected source sets. A confirmed artwork association is not a
claim that every gameplay capability is implemented for that species.

## Rebuild and verify

Python 3 and Pillow are required only for asset maintenance. APK builds and runtime
selection do not require a download, Python, or the upstream website.

```powershell
py -3 -B tools/build_dot_sprites.py --cache-dir "C:\Users\julye\AppData\Local\Temp\opencode\dot-sprite-sources"
# Subsequent reproducible builds can use the pinned source cache:
py -3 -B tools/build_dot_sprites.py --cache-dir "C:\Users\julye\AppData\Local\Temp\opencode\dot-sprite-sources" --offline
py -3 -B tools/build_dot_sprites.py --check
py -3 -B -m unittest tools.test_build_dot_sprites
```

Source revisions are pinned in the builder. Add researched name equivalences to
`tools/dot_sprite_overrides.json`; never remove form/stage qualifiers to obtain a
match. Identity-registry entries remain permanent across regeneration. Bundled
pack and per-frame checksums are also verified at runtime.

## Milestone 2: mode selection and shared availability policy

Implemented:

- `GameMode` (`VITAL_BRACELET`, `EXPANDED`) persisted by `GameModeRepository` in
  app preferences. Existing installations default to Vital Bracelet mode; expanded
  mode is an explicit opt-in from Settings → Digimon source. One collection and all
  progress remain shared; only availability changes.
- `SpeciesEligibility` is the single shared rule. VB mode accepts any individual of
  a species present on an imported card, regardless of that individual's origin,
  and rejects known species no loaded card provides; unidentified imported slots
  retain legacy VB access. Expanded mode rejects non-Digimon
  references and any species without validated bundled Dot art, so an owned VB
  partner without Dot art is inactive while expanded mode is active.
- `SpeciesAvailabilityIndex` joins the bundled canonical catalog with the set of
  species currently present on imported cards (`SpeciesProfileDao.getLoadedSpeciesNames`).
  `SpeciesRepository.availabilityIndex()` builds it from real data. It is pure and
  JVM-tested; the Room query is exercised through catalog/name fixtures.
- Settings exposes the two modes with factual descriptions and a shared-collection
  note. No schema change and no data reset were introduced by this milestone.

Milestone 3, step 2 below now applies this policy to partner participation, guarded
mutations and paused background activity. Mode selection alone does not provide
card-independent partner creation or full-catalog encounters.

## Milestone 3 (step 1): source-independent species binding

Implemented:

- Room **41 → 42** migration (`SpeciesBindingSchema`) adds three additive columns to
  `DigimonIndividual`: a `speciesName` acquisition/last-known reference snapshot,
  an `originKind` (`CARD` / `EXPANDED` / `IMPORTED`), and a soft, nullable
  `originCardCharacterId`.
  No existing column is altered or dropped; partners, chats, relationships and
  evolution history are preserved. The snapshot survives source-card deletion;
  it is **not a permanent current form**. A live current-form binding takes
  precedence when resolving participation.
- The migration backfills existing partners from their current card slot's profile,
  and `SpeciesBindingRepository.backfillMissingBindings()` (run once at startup) fills
  any row the migration could not resolve. Both are idempotent and never overwrite a
  reference that already exists.
- `SpeciesBindingRepository.canonicalIdentityFor()` resolves an individual's snapshot
  to a canonical identity through the bundled catalog, so regional aliases and forms
  resolve without collapsing qualifiers.
- `IndividualOrigin` defines and validates the allowed origins.

## Milestone 3 (step 2): source-mode participation enforcement

Implemented:

- A database-owned `PartnerParticipationGate`, registered in `DefaultAppContainer`,
  is shared by all repository instances. Its snapshot checks the live current form
  before falling back to an older individual species snapshot. Canonical aliases
  and catalog IDs share loaded-card access; unidentified imported slots remain
  usable in VB mode.
- Startup `VBHelper` observes mode changes and database invalidations and reconciles
  participation. It clears an ineligible active partner while retaining the
  collection and records. A rejected activation cannot clear a valid active choice.
- Storage badges inactive partners; selectors exclude them. `StorageDialog`
  disables gameplay actions but keeps conversation history accessible, and
  `ChatScreen` disables sending while the partner is inactive.
- World spawn, interaction, recruitment and scan conversion check mode eligibility.
  Selected quest givers, partners and unmet targets must be eligible; progress and
  rewards stop while paused. Model requests carry a trusted, captured mode revision
  into the actual quest-mutation and recruitment transactions, rejecting stale work.
- Fresh and frozen Arena teams and pending bracket simulations revalidate live
  eligibility. Permanent individual mapping survives replacement Storage IDs after
  a watch round-trip. Foreground battle and separate pending-settlement leases
  prevent mode switching while either operation owns participation.
- Farm needs account for valid elapsed time before the first pause, then freeze.
  Persistent `ParticipationPause` records preserve paused Farm/Adventure intervals
  over restart; Adventure deadlines are rebased at resume to retain remaining time.
- Item use, watch export and current activation cannot bypass the gate. Watch import
  still stores source measurements but does not autoactivate an unavailable partner.
- Room **42 → 43** adds `ParticipationPause`; the current export is
  `app/schemas/com.github.nacabaro.vbhelper.database.AppDatabase/43.json`.
  The **41 → 42** nullable-column SQL now explicitly uses `DEFAULT NULL` to match
  the annotated entity during chain migration. No destructive reset is introduced.

## Milestone 3 (step 3): card-independent partners, encounters, onboarding, conversion

Implemented in source; native gameplay and conversion acceptance remain pending:

- `CardCharacter.cardId` is now optional. Expanded-ready species get one
  app-managed shared definition each (no synthetic DIM or `Card` row), with
  content-stable negative IDs, DIM-range battle stats, Dot artwork and a species
  profile. Imported DIM/BEM rows are untouched, and reimport comparison ignores
  the new binding columns, so no duplicate cards are created.
- `UserCharacter` gains an optional `exportCardCharacterId` plus an `APP` device
  type. App partners have no invented bracelet measurements: no VB/BE rows, no
  watch missions, an individual-level `AppPartnerProgress` table, and a dedicated
  Home presentation without watch timers. Persistent app-training integration
  still needs completion and runtime verification.
- Expanded-mode World encounters, quest targets and recruitment draw from the
  full eligible catalog pool. A first-partner encounter spawns one
  eligible Child-stage partner with high starting trust for partner-less expanded
  play; ordinary creation stays progression-based and manual registration stays
  Debug-only. The current meeting uses ordinary recruitment; a scripted,
  AI-independent onboarding task remains to be implemented.
- Mode-based artwork defaults resolve through `DisplaySprite`: saved species-wide
  choices apply in VB mode; Expanded always uses the bundled Dot default.
  Choosing Dot art never creates a partner.
- Compatible bracelet conversion maps a catalog partner onto an exact loaded
  DIM/BEM slot of the same canonical species and form (never a vague match).
  Watch export projects through the converted slot while app identity, history
  and training stay intact; watch import stores source measurements without
  autoactivating unavailable partners.
- Onboarding offers expanded mode and the first-partner meeting from the Home
  setup panel. Room **43 → 44** rebuilds `CardCharacter` with preserved IDs and
  nullable card binding, and adds the new state tables/columns; current export is
  `app/schemas/com.github.nacabaro.vbhelper.database.AppDatabase/44.json`.

Remaining work:

1. Scripted first-partner onboarding independent of model availability, complete
   app-training integration, and native card-free gameplay/conversion acceptance.
2. Real external-game/VPet save adapters (PC first, then console/emulated saves,
   then V-Pet devices). The import interface and origin kinds already exist.
3. Permanent universal evolution with a separately researched graph and rules.
   Catalog partners currently keep their recruited form; degeneration and
   card-graph repair safely no-op for them.

## Subsequent work

- Concrete PC and console/emulated game-save import adapters, followed by V-Pet
  adapters when supported source protocols are identified.
- Universal permanent evolution with a separately researched graph and rules.
- Additional reviewed Dot associations and corrected upstream artwork.

## Verification checkpoint

Prior implementation evidence: **256 targeted JVM tests** covering species,
quests, Digifarm, `GameModeRepository` and domain passed with **zero failures or
errors and three pre-existing skips**.
The earlier five-check host SQLite batch omitted the recreated view and runtime
guards. Its success did not establish Room upgrade validation: the strengthened
43 → 44 regression reproduced a view-SQL mismatch. The migration now quotes the
view name exactly as Room does; current rerun evidence is recorded in `TESTING.md`.
Prior debug APK assembly and integrity
Android-test compilation succeeded. `PartnerParticipationPersistenceTest` has
**compile-only cases**; no connected ADB device is available. The source review's
final disposition for the enforcement increment was **ship** for its six reported
material findings, all resolved. This does not establish native approval.

## Native acceptance

The new persistence tests are isolated to the integrity-check application.
Native execution, screenshots, enlarged-font and TalkBack review require a
connected device. Compilation and source review do not establish those results.

No ADB device is connected for this increment. Acceptance still needs real Room
chain upgrades/reopen, mode/card-change reconciliation, inactive Storage/chat
states, stale quest/recruitment rejection, Arena/watch-round-trip identity and
battle/settlement leases, and Farm/Adventure pause/resume across restart. No new
native screenshots, TalkBack/large-font acceptance, lint or full-suite result is
recorded. Presentation inherits Material/branding; no new raster assets or global
design-token changes were introduced by this enforcement increment.
