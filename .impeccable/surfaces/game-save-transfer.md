# Linked game saves and Transfer

## Storage → World DS send, first implemented increment (2026-10-18)

- The direct Tap → Move flow above is still the target presentation; this increment
  implements the Storage → World DS direction only, and the Transfer → Send list keeps
  the covered prototype presentation (bracelet card plus one row per origin).
- Send shows the selected stored individual's name and whether its retained native record
  exists and whether it is away in a game. The World DS row enables only when a retained
  record exists and at least one linked World DS save is writable and reviewable; other
  origins stay visibly "not available yet".
- Choosing World DS opens a per-save list with blocked rows disabled and a localized
  one-line reason (no retained record, away, review needed, copy authority, write access,
  identity taken, stale source). Sending is one action with progress and result feedback:
  completed (with the in-game-loading caveat), source changed, recovery required, failure.
  No preview/mapping wizard, and no species prompt — species mapping is not re-asked.
- Away state: a completed send hides the individual from other game archives and blocks
  re-import until a verified move back; backup export lists move and send journals together.
- Sending remains experimental: capability flags stay false, in-game load acceptance is
  not claimed, and the source surface wording must not imply verified game loading.

## Universal Digimon home — owner clarification (2026-10-17)

- One permanent individual travels through arbitrary supported games and V-Pets via
  Storage, retaining all identity, native state and conversion/growth information.
- Normal Storage/Home shows DIM-style VBHelper information and balanced app stats.
  Raw product fields belong in that product's Transfer individual-information surface.
- There are no VB/Expanded participation modes. VB-first/Dot-first is cosmetic,
  with mutual artwork fallback and a neutral placeholder when neither exists.
- Exact DIM/BEM association is required for bracelet export and enforces normal card
  stat rules while assigned. Extra app/cross-product bonuses are suppressed, not erased.
- Device state baselines outlive transfer tokens and local representation deletion.
  Existing mapped local archives become playable automatically without repeating the
  old mapping/materialization wizard or rerolling identity/personality.

## Definitive direction: unified origin list and one-action moves (2026-10-17)

- Latest user clarification overrides the game-card prototype and the earlier tabbed
  hub below: one vertical **list** containing games, Vital Bracelet and every supported
  origin. DS games use their actual DS menu/launcher icons, not box covers.
- Opening a game shows its saved individuals using verified automatic native species/
  form matching and the app's normal sprites. Respect sprite preferences; do not infer
  species from a nickname or borrow another game's numeric ID mapping.
- Tap a Digimon -> information -> **Move to Storage**. The normal supported action
  moves it into ordinary Storage/Home in one operation, preserving nickname, continuing
  identity and progress. No required import preview, second confirmation, manual species
  choice, source-name checkbox or separate APP-materialization workflow.
- Run validation, backup, journal and readback internally; use brief progress/result
  feedback. Source removal and Storage acquisition must complete together logically;
  do not claim success while only a snapshot was copied.
- Only origin ↔ VBHelper Storage. A stored individual may enter any supported game
  that supports its species; origin game is not a lock. An already-open receiving
  game's context avoids repeating destination selection.
- Keep technical management/history/review secondary. Device origins belong in the
  same main list, not isolated below a game grid. Farm transfer stays excluded.
- The installed cover-grid, placeholder portraits and copy-review prototype does not
  yet meet these requirements. See MULTI_GAME_TRANSFER_PLAN.md sections 2, 4 and 20
  for the authoritative contract and implementation gaps.

## Earlier game-library prototype (superseded presentation)

- Mode: Operate. This direction supersedes the earlier three-section hub description.
- Primary target: `app/src/main/java/com/github/nacabaro/vbhelper/screens/transferScreen/TransferHubScreen.kt`.
  Related: `TransferGameLibraryScreen.kt`, `TransferWorkspaceScreen.kt`,
  `navigation/TransferGameCatalog.kt` and `storageScreen/DestinationPreviewDialog.kt`.
- Use PKForge's `home.png`, `editor.png` and `bank.png` screenshots as the concrete
  interaction reference (repository revision `799dfc310c94bb978962dcd31ddf7d39e6d8cba3`).
  Library first: game cover → game's individuals → selection/action. Match Android
  touch/Back/scroll behavior and the saved VBHelper theme rather than porting gamepad chrome.
- Only game → VBHelper Storage and Storage → game. Any receiving game with validated
  support for the species may receive the individual; its original game is not a lock.
  The opened game is already the destination for its Storage side. Keep all native
  origins visible; compatibility belongs to validated adapter/conversion policies.
- Game and Storage grids switch on a phone and share expanded width. Selection is
  scoped to an observed save revision. Review-only/farm rows open details and cannot
  join acquisition selections. The currently implemented action stays Copy to Storage;
  actual DS moving is under development and cannot be implied by new labels.
- Use compact cover tiles with original game art (credits in the bundled asset file),
  native stored names and levels. No guessed species portrait from a nickname or foreign
  game's numeric species ID. Unknown format groups remain clearly unreadable.
- Manage saves, Storage tools and Vital Bracelet live in the overflow menu. Preserve
  old review/history/mapping flows as secondary tools. Bottom bar remains
  Home/Storage/Dex/World/More; Transfer is reached from Home's upper-left action.
- Pause Time Stranger transfer research. DS native moves begin with the World DS
  adapter and requested DigimonNDSRomEditor reference for subsequent Dawn/Dusk support.

## World DS phone reading

- The observed USA DraStic 64 KiB raw `.sav` profile is available directly through
  Transfer's Android file picker and linked-folder scans. Newly linked files expand
  their roster after refresh. The PC connection is optional for this workflow.
- Native World DS rows show decoded stored name, level/species/position, HP/MP,
  attack/defense/spirit/speed, aptitude and eight EXP channels in original order.
  There is no invented personality, talent, bond or scalar EXP value.
- The source remains read-only while eligible party snapshots can be copied to Storage.
  Show provisional identity, app-only personality initialization and retained originals.
  Game archives reuses reviewed species/form mapping and shared Home materialization.
- Multiple populated copies have a focused roster/radio review using native Material
  controls. Choice is source/revision-bound; nonselected copies remain inspectable and
  ineligible, and a changed file needs renewed review. Keep checksum limits visible;
  unassigned records are not Bank. Existing palettes, scrolling and localization remain
  authoritative. Phone visual/runtime acceptance is still deferred.

## Destination preview extension

- Operate: extend Storage Game archives using the existing Material dialog, text
  actions, typography and saved palettes. No new visual system or raster artwork.
- Choose one linked save and Party or Bank, then inspect a freshly read, revision-bound
  planning report. Farm transfer is out of scope, not a prerequisite for main records.
- Hierarchy: partner and planning-only explanation, destination choices, target container,
  format verdict, recorded-progress/native-change assessment, remaining capability
  blockers, then field-by-field unvalidated/unmapped effects. Native archive
  retention must never be presented as verified destination preservation.
- The progress section states context availability, unresolved native reviews, changed
  destination records and anchor presence as text (error-colored only for unresolved
  native changes). It shows counts, never unit values or converted numbers, and its
  five dedicated blockers stay out of the generic blocker list to avoid duplicate copy.
- An optional partner checklist (capped at the 16-item planner bound, current partner
  disabled-checked) adds partners to the same fresh destination read. Multi-partner
  reports show a count summary, shared restrictions once, then per-partner rows with
  name, format verdict, progress status/counts and item-only blockers; the full field
  table stays single-partner to avoid visual overload. A shared-candidate conflict is
  one localized sentence, never a merge prompt.
- Re-read and Close remain outside the scrolling body. Loading, no linked saves,
  read failure, source changes and rebuilt/unchanged reports have localized copy.
- Finish boundary: source/desktop build review only in this pass; native visual,
  accessibility and device acceptance remain pending by the user's instruction.

- Mode: Operate.
- Primary target: `app/src/main/java/com/github/nacabaro/vbhelper/screens/transferScreen/TransferHubScreen.kt`.
- Game-save section: `app/src/main/java/com/github/nacabaro/vbhelper/screens/transferScreen/TransferScreen.kt`.
- Related target: `app/src/main/java/com/github/nacabaro/vbhelper/screens/transferScreen/TransferDetails.kt`.
- Inherit the incumbent TopBanner, CyberPanel, VitalButton, Material typography,
  saved palettes, system insets and native Back behavior. Preserve watch Scan routes.
- Home's upper-left Transfer action and the primary Transfer navigation target open
  the same hub. Three shared VitalTabs: Game saves, Storage transfers, Devices.
  Storage routes transfer actions here with partner context; archive/materialization/
  destination/progress workflows live in Storage transfers, NFC preparation in Devices.
- Device tabs/partner changes are disabled during active NFC work. Prepared native
  payloads reset with partner context; completing a hub operation stays in the hub
  with its status. Legacy Scan routes remain compatible operational subroutes.
- Native change review is read-only and bounded. Distinguish temporary HP/MP, native
  parameter/EXP changes, personal edits, location, form, identity and unknown bytes.
  It cannot acknowledge pending changes or normalize/award app progress. No native
  screenshots or phone acceptance are implied by build/source checks.
- Primary task: link authorized saves, refresh their persisted contents, inspect
  native roster candidates and compare observations from separate sources.
- Distinguish loading, empty, failed and cached states. Busy state comes from the
  shared repository, including automatic refresh. Show last successful read time.
- File relinking repairs access to the same document; different documents stay
  distinct sources. Folder selection discovers files within explicit limits;
   persisted locations are rescanned on refresh. This is not live game-memory access.
- Expanded rosters remain one lazy scrolling surface with localized container
  headings and review states. No species-art materialization for browsing.
- Comparisons use selected cached observations. Show source labels, possible
  matches, ambiguities and field changes without promising permanent identity.
- Roster selection is scoped to source and observed revision, with at most 128
  records per import review. Preparing a review rereads the source; a changed file
  requires reselection. Review-only records may be selected to inspect restrictions.
- Import reviews retain native snapshots in separate app-private draft/item rows.
  Saved reviews remain accessible after source unlink and are labelled historical
  when reopened. Show native personality by explicit number and localized name.
- Fresh eligible reviews can commit only through a compiled adapter policy;
  historical reviews have no commit action. Draft storage is not an acquisition receipt
   or an owned Storage partner. Time Stranger's party/bank policy is explicitly provisional:
   show snapshot-copy/source-intact wording, retain confidence labels and block farm rows.
- Inactive native representations are exposed through Storage Game archives, preserving
  source labels/names/personality and unknown native fields with neutral gray placeholders.
  Eligible APP materialization now has a separate reviewed, snapshot-local canonical
  species/form mapping, with `USER_CONFIRMED` provenance rather than a claimed official
  numeric crosswalk. Unavailable artwork/modes retain the archive and mapping. Creation
  reuses the permanent individual/personality, offers an explicit source-name nickname
  choice, and keeps a one-time receipt after representation deletion/export. Native
  source details stay accessible alongside the APP representation; World/watch-away
  individuals do not reappear as locally owned archives. Reviewed native refresh preserves
  permanent identity and earlier native snapshots, compares the native baseline with
  source/app fields, and exposes explicit choices for conflicting name/personality edits.
  Source, baseline or reviewed app-field changes invalidate confirmation. Choice audits
  stay separate from native bytes, and repeat confirmation does not reapply changes.
- Current capability includes provisional party/bank copies with explicit reviewed
   continuity for later matches. Ownership/mapping uncertainty stays visible;
  browsing/comparing does not create partners, discoveries or provenance bindings.
- Error/recovery copy and roster details are localized for English, PT-BR and JA.
  Internal format codes, private document URIs and exception messages are not ordinary
  failure copy. Controls wrap rather than clipping long translated labels.
- Evidence: debug APK and Android-test compilation pass; native device captures,
  all-palette/large-font/TalkBack and phone performance review are pending.

## Consolidated continuation (schema 52)

- Selected local-network PC access uses explicit pairing, pinned TLS and read-only
  endpoints. Pairing material is masked on entry and the token is encrypted with
  Android Keystore. No game writer is implied by network access.
- Persist linked folders and PCs as discovery locations, rescan on refresh, retain
  cached source observations on failure and respect explicit unlink exclusions,
  including overlapping discovery. Stopping discovery retains source/history records.
- Permanent history distinguishes native observations, app representation events,
  physical-device observations and app changes. Original source documents are retained
  in bounded chunks for acquired snapshots when the transport supplies them.
- Universal progress uses semantic fixed-point vectors, per-representation checkpoints
  and deduplicated receipts. Projection/DIM selection adds no progression; actual gains
  remain pending for destinations that have not received them. Unknown native changes
  remain explicitly unresolved rather than being silently treated as zero.
- Full DIM-style Home/stat parity is required for card-independent actors. A selected
  DIM supplies its stat basis; origin data remains retained and separately projected.
- Native/network/UI and real-game acceptance remain separate from JVM/schema checks.
