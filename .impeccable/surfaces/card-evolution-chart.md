---
version: 1
slug: "card-evolution-chart"
primary_target: "app/src/main/java/com/github/nacabaro/vbhelper/screens/cardScreen/CardViewScreen.kt"
related_targets: ["app/src/main/java/com/github/nacabaro/vbhelper/screens/cardScreen/CardEvolutionChart.kt","app/src/main/java/com/github/nacabaro/vbhelper/screens/cardScreen/CardEvolutionLayout.kt","app/src/main/java/com/github/nacabaro/vbhelper/screens/cardScreen/CardEntry.kt","app/src/main/java/com/github/nacabaro/vbhelper/screens/cardScreen/dialogs/DexCharacterDetailsContent.kt","app/src/main/java/com/github/nacabaro/vbhelper/screens/cardScreen/dialogs/DexCharaDetailsDialog.kt","app/src/main/java/com/github/nacabaro/vbhelper/screens/cardScreen/dialogs/DexCharaFusionsDialog.kt","app/src/main/java/com/github/nacabaro/vbhelper/screens/settingsScreen/controllers/CardImportController.kt","app/src/main/java/com/github/nacabaro/vbhelper/screens/cardScreen/CardsScreen.kt","app/src/main/java/com/github/nacabaro/vbhelper/screens/cardScreen/CardSelectionScaffold.kt","app/src/main/java/com/github/nacabaro/vbhelper/screens/cardScreen/CardBatchImportPanel.kt","app/src/main/java/com/github/nacabaro/vbhelper/components/TopBanner.kt","app/src/main/java/com/github/nacabaro/vbhelper/screens/settingsScreen/controllers/CardImportViewModel.kt"]
---

# Dim evolution dex

Mode: Operate. Owners browse every character on an imported DiM/BEM, follow evolution/Jogress routes, and inspect readable stats, species information, and requirements.

Confirmed: the reference's vertical evolution chart replaces the grid; shared overlapping connector buses are intentional and their route ambiguity is acceptable. Reuse grayscale/static-color/animated-color ownership states; keep Jogress initially off with an always-visible labeled toggle, disabled without stored routes. Fit/zoom sits subtly over the chart; remove drag/pinch text and the separate controls strip. The imported-logo footer meets the app bottom navigation without a gap. Selection cards keep active cyan technical frames; info/Jogress dialogs use medium cut-corner tonal Material Cards and pinned actions. Dex gains top-left multi-file Import opposite Edit, sharing Settings' importer.

Scope: ordinary extension of the incumbent Android world documented in DESIGN.md; global design identity stays incumbent.

## Direction contract

THESIS: A stage-by-stage map with aligned shared buses makes branching and merging routes the primary content; dialogs carry precise requirements when the shared map is ambiguous.

OWN-WORLD: Inherit VBHelper's dark purple surfaces, square technical panels, configurable Material/Oxanium type roles, cyan selection/progress, and native Material controls. Ordinary routes are solid; Jogress is dashed yellow and controlled by the labeled chip.

STORY: Import a batch, classify only newly added cards, see collection progress, follow stored routes, tap a sprite for stats/species/evolution requirements and Jogress partners, then browse adjacent imported cards directly.

FIRST VIEWPORT: Back/name/Adventure header, compact progress/Jogress row, dominant chart with centered stage rows and a quiet bottom-end Fit/zoom overlay, then card-logo navigation flush to the app bottom bar. Sprite nodes retain at least 48dp logical bounds at minimum zoom; wide/tall charts pan. Terminal-only results have a presentation-only final section regardless of Jogress visibility.

FORM: User-pinned reference topology, extended inside the established Android visual system. Code-led native implementation using imported assets; no generated raster assets.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

Delivery scope: the refinement is documented here and in TESTING.md. Main's latest code review scored all four findings resolved after batched fixes. DESIGN.md remains the inherited visual authority; native visual/runtime acceptance is deferred under the user's BUILD CHECKS ONLY scope.

Verification: BUILD CHECKS ONLY — the final full-app report establishes 407 tests, zero failures/errors, and three private-fixture skips (404 successful executions). XML reports establish 18 new JVM passes (seven batch, four origin-queue, four stream, two gate, one actual-scheme contrast); all eight host SQLite/migration checks pass. Final Android-test compilation, debug APK assembly, and lint pass with --max-workers=2. The pre-existing personality determinism test now supplies the same explicit clock value for both seeded calls, with application behavior unchanged. Compose/Room cases remain compile-only; native appearance and provider interaction are deferred.

## Implemented extension

- `CardViewScreen.kt` retains the existing Back/name/Adventure `TopBanner` and
  reactive character selection. Its nested Scaffold uses zero content insets because
  the app navigation Scaffold already reserves system bars and its bottom bar;
  the card-logo footer therefore meets the app navigation without a duplicate gap.
  Loading, empty, and retry states are explicit.
- `CardEvolutionChart.kt` uses the incumbent purple surfaces, quiet technical grid,
  cyan progress/selection, solid ordinary routes, and dashed yellow Jogress routes.
  The Jogress chip is always visible, initially off, and disabled while no graph or
  stored Jogress routes are available. It changes connector visibility without moving
  nodes or hiding disconnected/fusion-only characters. Fit/zoom IconButtons sit in
  a quiet translucent bottom-end overlay with small glyphs and Material touch bounds;
  there is no instructional drag/pinch text or separate controls strip.
- `CardEvolutionLayout.kt` aligns adjacent routes to the midpoint of each stage gap;
  skipped/backward routes share an outside lane and the same departure/arrival gap
  buses. Same-stage routes share a bus above the row. Collinear overlapping segments
  are merged per route style and drawn once, without arrowheads or per-route tracks.
  This intentionally accepts shared-path ambiguity rather than expanding the map.
- Terminal imported stage-VI results reached exclusively from VI-or-later sources
  receive presentation-only rows VII onward for ordinary evolution and Jogress.
  Acyclic terminal chains advance, including descendants of cycles; actual cycle
  members stay at imported stage. Results also reached from earlier stages stay in
  their imported row, and explicit BEM VII stages are preserved. Layout uses all
  stored links even with Jogress off; imported stages/stats and detail labels are
  never rewritten by these final sections.
- Shared `CharacterEntry.kt` supplies square `cyberFrame` sprite panels and inherited
  grayscale (never obtained), static color (previously obtained), and animated color
  (currently available) states, respecting the existing Android reduced-motion gate.
  `CardEntry.kt` activates cyan technical frames for both Official and Custom cards;
  Unknown cards retain their distinct origin/status treatment. Sprites, imported
  name images, and logos preserve pixel filtering; no shipping raster assets were created.
- Typography uses existing configurable Material theme roles: `titleSmall` progress,
  `labelSmall` stage labels, `labelLarge` footer/metadata, and readable title/body
  roles in dialogs. Both system appearances map all five `surfaceContainer*` roles
  to incumbent purple colors. `ThemeSurfaceContrastTest` uses the actual schemes'
  luminance and requires at least 4.5:1 for `onSurface` on each container in both modes.
- Layout and `CardChartViewport.kt` share logical coordinates with
  connector drawing and measured Compose node placement. Nodes start at 72dp and
  retain at least 48dp bounds at minimum zoom; wide charts pan. Pinch/pan, fit/zoom
  buttons, named node index/imported-stage/ownership/selection semantics, and two-axis
  accessible scrolling are implemented. Displayed-card, character selection, Jogress
  preference, and viewport use saveable state; the previous/next footer stays bounded
  to imported cards. Native restoration and physical hit-testing remain acceptance cases.

## Info and Jogress dialogs

- `DexCharaDetailsDialog.kt` and `DexCharaFusionsDialog.kt` use native Compose Dialogs
  containing medium cut-corner Material Cards (10dp) on tonal `surfaceContainerHigh`,
  inspired by the existing `StorageDialog` shape and `TransformationHistoryCard`
  surface. Width is capped at 480dp within 92% of the window; height at 88%.
  Scrollable content leaves actions pinned outside it, with at least 48dp height.
  Selection cards retain their square cyan technical frames.
- `DexCharacterDetailsContent.kt` groups portrait/imported name, species name,
  imported stage/attribute, and ownership status; HP/BP/AP have clear label/value
  hierarchy. Species level/type/profile/moves and the Custom species picker use
  existing Material type and controls. Obscured names/stats retain the unknown
  treatment, and unavailable stats/profile content is handled explicitly.
- Evolution route cards pair destination pixels with readable time, vitals, trophies,
  battles, win rate, and adventure requirements; tapping resolves the destination
  character ID. Close/Jogress text actions stay pinned, with Jogress available when
  the character has stored attribute or partner-specific definitions. The Jogress
  dialog lists both kinds of requirement, including partner card number and one-based character index,
  and has its own pinned Close action. Long-font readability, nested focus, and native
  dismissal remain deferred acceptance checks.

## Card selection and batch import

- `CardsScreen.kt` uses `CardSelectionScaffold.kt` with zero nested system insets;
  the parent navigation Scaffold owns system/bottom-bar clearance. `TopBanner.kt`
  places a 48dp Import action at top-left opposite Edit, disabled during import.
  The list retains compact logo/name/status/progress cards and existing cyan frames.
- Dex and Settings share `OpenMultipleDocuments` and `CardImportViewModel.kt`, with
  no app count cap. Files run sequentially; `CardImportController.kt` commits each
  new/re-import atomically and returns `CardImportResult(cardId, cardName, isNew)`.
  Duplicate URIs are read once; matching aliases use the same re-import policy.
- Dex queues origin prompts only for committed NEW cards; Settings honors its prior
  prompt toggle. Re-imports/aliases add no new choices; pending labels refresh by ID.
  Choices reserve a card ID and save its status before clearing that queue entry;
  failed saves release the reservation. Recovery runs once per retained model and
  keeps only existing queued UNKNOWN cards, without recreating classified choices
  or prompting for every unknown card.
- `CardBatchImportPanel.kt` presents preparation/progress, Stop/Stopping,
  added/updated/failed counts, dismissible results, and safe per-file issues using
  display names/file numbers rather than raw exceptions/URIs. An origin-bookkeeping
  issue does not misreport a committed card as a failed import; other files continue.
- The retained Application/`SavedStateHandle` model preserves the job/results across
  configuration changes without holding an Activity; the handle stores picker origin
  policy. `CardImportRunGate.kt` guards admission and publication until the owning
  job/cleanup finish. Completion publishes a terminal fallback even if cancellation
  occurs before the lazy coroutine body starts; stale runs cannot own a replacement.
- Provider query/open uses `CancellationSignal`; cancellation closes the active
  stream. `CardDocumentRead.kt` parses and closes BEFORE database persistence,
  including cleanup of late-opened streams. A close failure cannot follow commit.
  Stop retains committed cards and their origin bookkeeping, then ends the remainder.

## Source data and re-import contract

- `JogressImportReader.kt` imports both attribute and partner-specific Jogress from
  DiM and BEM tables, deduplicates valid entries, and excludes sentinel/foreign source
  or destination slots. Stored foreign partners remain requirements. `DexDao.kt`
  connects both same-card partner sources to the result inside the same local card,
  without leaking into another custom card sharing its DiM/BEM number.
- Schema 31's additive `CardJogressSchema.kt` adds `CardSpecificJogress` and
  `Card.nameIsUserEdited`. Missing old Jogress and ambiguous BEM adventure metadata
  are recovered by re-importing the source file, not guessed during migration.
- `CardReimportPolicy.kt` selects one unambiguous body-matching card using name and
  attack-content evidence, including unique legacy missing-art recovery.
  `CardImportController.kt` refreshes attack art and ordinary/Jogress definitions
  transactionally for that card, retaining local card/character IDs, individual IDs
  and state, discovery, and card progress. Ambiguous custom variants are not updated;
  an unresolved match follows the normal new-card import path.
- A supplied filename refreshes an automatic name unless `nameIsUserEdited` is set.
  Legacy names lacked provenance: the user explicitly chose refresh on re-import,
  even for potentially manually named legacy cards. Future explicit manual renames
  set the flag and are locked against filename refresh.
- The no-adventure sentinel is `-1`; valid BEM index `0` displays stage `1`, and
  nonnegative indices display index + 1. Migration normalizes only known legacy DiM
  zero values to `-1`, preserving BEM zero until source re-import resolves ambiguity.

## Review scope and verdict

Source comparison checked PRODUCT.md/DESIGN.md, the existing brief, chart/layout,
card selection/scaffold/header/panel, info/Jogress dialogs, retained import model,
batch/document/gate/controller, and origin queue against test sources and reports.
`impeccable/reference/document.md` guides incumbent-system documentation. JVM XML
establishes the 18 new passes; host/build evidence is attributed to main verification.
Review and verdict are source/report scope; native rendering remains unverified.

Main's latest code review scored all four findings **resolved after batched fixes**:

1. **Contrast:** every light-scheme container role maps to incumbent purple;
   actual light/dark `onSurface`/container contrast reaches at least 4.5:1 in the test.
2. **Stale cancelled-run ownership:** the owning-job gate blocks replacement
   admission through cleanup and rejects stale publication/completion.
3. **Close after commit:** parsing and stream close precede persistence; close errors
   never change the outcome of a committed card, including late-open cleanup.
4. **Blocking-provider Stop:** query/open cancellation signals and active-stream
   close unblock cooperative providers; completion also handles pre-start cancellation.

Earlier resolved findings remain: one unambiguous re-import candidate, shared
skipped-gap buses, advancing cycle descendants without moving actual cycle members,
and the `-1`/valid-BEM-zero adventure boundary. Same-card identity, manual-name
protection, and Jogress isolation rules remain covered by policy/SQLite evidence.
New compile-only cases add two selection header/body-bottom/Stop checks and two
Room atomic-result/rollback checks; they do not establish native assertion passes.

Earlier gesture/accessibility fixes remain covered: callbacks synchronously update
one authoritative saveable viewport, and accessible scrolling converts pixel/dp units
with bounded ranges and zero for centered axes. All verdicts are code-review scope.

Verdict: the documented refinement is consistent with the inherited visual system
and explicit user choices at source level. Visual/runtime approval is deferred:
no installation, instrumentation execution, screenshots, large-font, or TalkBack
evidence was collected for this refinement. TESTING.md records the focused commands,
evidence provenance, and deferred checks for aligned buses, overlay/footer hit areas,
Jogress/final sections, long/obscured/native dialog states, re-import retention,
mixed/large/multi-provider batches, new-vs-re-import origins, invalid/close-error
files, Stop/fast restart/rotation, light appearance, compact cards, and actual bottom
alignment. Final build/lint/Android-compilation acceptance awaits main verification.
