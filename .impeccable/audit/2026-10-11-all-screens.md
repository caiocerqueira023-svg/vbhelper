# VBHelper — all-screen native audit

## Implementation follow-up — October 2026

**F01–F16 have implementation changes in place.** The score, findings and original
line citations below are the pre-implementation snapshot; they are retained for
traceability rather than presented as current unresolved code. See `TESTING.md`
for final verification receipts and the remaining native acceptance boundary.

- **Optimize (F01/F10/F11):** SQL-bounded four-record inspector pages, frame-size
  validation, visible-frame decoding and a 4MiB cache; off-thread widget rendering,
  reusable buffers, partial/coalesced updates, empty/screen-off throttling and
  generation-checked publication.
- **Harden (F03/F04/F05/F12/F14/F16):** native battle selection actions and semantic
  health, named/selected controls, explicit destructive branch confirmation,
  staged successful-reply replacement with concurrency checks, reading-position
  preservation, live Debug checks and consistent preparation Back. Regeneration
  does not repeat quest/battle actions or re-apply relationship changes.
- **Colorize/adapt (F02/F06/F07/F09):** existing palette foreground pairs restored,
  short-window scrolling and bounded action dialogs, growing large-text Home
  readouts, explicit loadout IME ownership and shared-height Storage actions.
- **Clarify/animate/readiness (F08/F13/F15):** direct numeric Home mood and persistent
  DigiLine trust, lifecycle/system-observed motion preference, and distinct first
  loading/failure/retry/confirmed-empty states with cached data retained.
- **Final local verification:** 749 unit cases, 0 failures, 3 existing fixture skips;
  debug/release/test APKs assembled; debug lint passed with 0 errors, 266 warnings
  and 7 hints. Android UI tests compiled, but no device was connected for execution.

Native screenshots, TalkBack, predictive Back, real inspector crash reproduction
and launcher performance remain unverified. No new on-device score is claimed.

## Platform conformance verdict

**PASS for native architecture; substantial exceptions need repair.** This is a Compose Android app with Material controls, a compact five-destination navigation group, an expanded-width rail, real system pickers, and deliberate inset ownership. It does not read as a ported website. Home and Storage remain the approved design references.

The exceptions are concrete: embedded battle preparation has a different system-Back hierarchy from its visible Back button (F16); the technique editor does not own its IME inset (F07); several secondary windows cannot scroll overflowing content (F06); and chat's destructive actions do not disclose their actual scope (F04). This verdict is not on-device release approval.

## Executive summary

- **Audit Health Score: 11/20 — Acceptable (significant work needed).** Scores are source-based rubric judgments, not measured frame rates or a live TalkBack verdict.
- **16 grouped findings:** 0 confirmed P0 blockers, 8 P1 major findings, 8 P2 minor findings, 0 P3 polish-only findings. Repeated instances of a systemic problem share a finding ID.
- **Coverage:** all 24 registered navigation destinations, their embedded screens and dialogs, the widget, entry/import handling, and shared presentation. Dormant companion activities and validation hosts are accounted for separately below.
- **Most important repairs:** bound inspector data before loading it; restore online-battle contrast in the light palettes; provide accessible combatant selection and health; explain/protect chat history truncation; make secondary windows and the technique editor work in short/keyboard-visible windows.
- **Recent changes still incomplete:** Home hides numeric mood; DigiLine omits numeric trust; Storage's paired actions have equal widths but no equal-height contract. These are F08/F09, not reasons to replace either favorite screen's design.
- **Preserve:** the Home/Storage hierarchy, four saved palettes, configurable fonts, crisp imported pixel art, source-aware research, and ordinary in-world language. Other farm-care values can remain in normal Details; the latest preference specifically requires directly visible mood and trust.

## Evidence and scope

- Snapshot: **2026-10-11**, current uncommitted worktree, base Git revision **42cdb22f**.
- Rubric: Impeccable `audit.native.md` and `android.md`; five dimensions, each scored 0–4.
- Authority: current source, `PRODUCT.md`, `DESIGN.md`, the latest user preference, and the Home/Storage implementations. `PRODUCT.md:52` and `DESIGN.md:279` still describe the superseded mood/trust details-first rule; this audit follows the newer preference.
- `build/device-home-polish.png` was inspected as historical identity evidence. Its navigation order, title sizing, and numeric mood differ from current source, so it is not a current acceptance capture.
- The existing Graphify map is dated 2026-09-21. It helped locate relationships; all cited findings were checked in current source rather than relying on graph line numbers or removed screens.
- No connected device was available; the previous ADB device listing was empty and no AVD was configured. TalkBack traversal, actual hit regions, predictive Back animation, large-font screenshots, foldable posture, NFC, renderer performance, and the inspector crash remain device-validation work.
- This pass ran source inspection and deterministic contrast calculations. No Android builds or app tests were rerun. Earlier tests/builds recorded in `TESTING.md` predate the recent fast fixes and are not new audit evidence.
- Below, Kotlin paths are relative to **`app/src/main/java/com/github/nacabaro/vbhelper/`**, unless a path explicitly begins with `app/`, `PRODUCT.md`, or `DESIGN.md`. Line references identify the current worktree snapshot.

## Audit health score

- **Accessibility — 2/4.** Material labels/roles, heading semantics, accessible chart scrolling, and Home favorite actions exist. Combatant selection/health, unnamed switches, color-only selections, and meaningful image names still have major gaps (F03/F05/F13).
- **Performance — 2/4.** Main collection screens use keyed lazy lists/grids, farm mesh generation is off-thread, and Arena art has an IO cache. Inspector loading remains unbounded, and widget raster/Binder work repeats every 220 ms (F01/F10).
- **Appearance & Theming — 2/4.** Four semantic palettes and shared typography are well established. The active online-battle flow bypasses foreground roles and becomes nearly unreadable in light palettes (F02).
- **Platform Conformance — 3/4.** Native shell, controls, system pickers, and localized in-world feedback are strong. Embedded Back behavior, destructive scope, and delayed diagnostic feedback are inconsistent (F04/F14/F16).
- **Adaptivity — 2/4.** Expanded navigation, adaptive grids, scrollable editors, and wide battle layouts exist. Short windows, large text, and a keyboard-visible technique editor still expose fixed/unscrollable layouts (F06/F07/F09).
- **Total — 11/20: Acceptable.** The identity is worth preserving; these are targeted technical repairs rather than a new visual world.

## Detailed findings — P1 major

### F01 — Inspector limits decoding after unbounded data loading

- **Location:** Asset inspector; `screens/spriteViewer/SpriteViewer.kt:65–70`, `screens/spriteViewer/SpriteViewerControllerImpl.kt:15–32`, `daos/SpriteDao.kt:14–15`.
- **Category:** Performance.
- **Evidence:** `SELECT * FROM Sprite` materializes every row and every frame blob. Only afterward does the UI call `.take(40)`. Each retained row allocates up to twelve bitmaps; dimensions are checked only for being positive, with no maximum dimension or byte budget. Moving this work off the UI thread does not bound memory.
- **Impact:** Large imports or oversized sprite metadata can still produce excessive allocations or an out-of-memory failure. The reported crash was not reproduced, so this is a verified unbounded allocation path, not a proven crash diagnosis.
- **Guideline:** Bound image loading/decoding; avoid full data/image materialization for a thumbnail inspector.
- **Recommendation:** Count separately, page/limit in the DAO before reading blobs, decode visible frames with a byte-sized cache, validate dimensions and buffer lengths before allocation, and make failure retryable. Preserve the runtime Debug gate.
- **Suggested command:** `$impeccable optimize`.

### F02 — Online-battle foreground colors fail in both light palettes

- **Location:** Online battle and result; `screens/BattlesScreen.kt:1162–1168`, `screens/BattlesScreen.kt:2623–2659`, `ui/theme/AppTheme.kt:140–192`.
- **Category:** Appearance & Theming / Accessibility.
- **Evidence:** Attack sets its container to the theme-resolved `VitalCyan` but forces black text. Result headers use theme-resolved `StatusRedDim` with white names and health text. Calculated opaque sRGB/WCAG contrast ratios are **3.25:1** for Arena Attack, **3.11:1** for Digimon.net Attack, **1.24:1** for Arena result text, and **1.21:1** for Digimon.net result text. These 11–14sp labels require 4.5:1.
- **Impact:** An essential action and result facts become hard to read; light-theme users get a weaker, inconsistent version of the same game.
- **Guideline:** Pair semantic surface/container roles with their matching foreground roles; normal text must reach 4.5:1.
- **Recommendation:** Use the existing palette's paired foreground roles and shared controls, including opponent-empty/error states. Keep the four palette identities and imported art. Localize remaining ordinary labels such as `Enemy HP` while consolidating the flow.
- **Suggested command:** `$impeccable colorize`.

### F03 — Live battle selection and enemy health are not accessible controls/data

- **Location:** Training, Arena and Radar combat; `screens/offlineBattle/OfflineBattleScene.kt:68–98`, `screens/offlineBattle/OfflineBattleScene.kt:311`, `screens/offlineBattle/OfflineTrainingBattleScreen.kt:528–531`, `screens/offlineBattle/OfflineTrainingBattleScreen.kt:1799–1820`, `screens/offlineBattle/OfflineTrainingBattleScreen.kt:1951–2029`.
- **Category:** Accessibility.
- **Evidence:** Combatant selection is a TextureView touch callback. The command deck has actions for the already-selected ally/target, but no equivalent named ally/target selector. Enemy health is a drawn 66×9dp bar without a semantic name, range, or value. No accessibility node provider or fighter actions were found in the renderer.
- **Impact:** TalkBack/switch-access users cannot intentionally switch combatants in a multi-participant battle or read opponents' health. The default first selection is not an equivalent way to perform these tasks.
- **Guideline:** Every meaningful interactive operation and information state needs an accessible equivalent; pixels in an AndroidView do not create virtual accessibility nodes automatically.
- **Recommendation:** Add compact named ally/target selection and a semantic battle-status summary outside the renderer, or equivalent virtual nodes. Announce selection and important command results without announcing every simulation tick. Preserve scene taps for sighted users.
- **Suggested command:** `$impeccable harden`.

### F04 — Chat Delete/Resend silently truncate the rest of the conversation

- **Location:** Owned and wild private chat; `screens/chatScreen/ChatScreen.kt:176–196`, `screens/worldScreen/WorldChatScreen.kt:226–255`, `screens/chatScreen/ChatScreenControllerImpl.kt:39–57`, `daos/ChatDao.kt:32–33`.
- **Category:** Platform Conformance.
- **Evidence:** The UI presents `Delete` for one selected message. The DAO deletes **all messages with `id >= messageId`**. `Resend` performs the same deletion before a replacement request succeeds. Neither action explains the subsequent-message scope, confirms it, or offers undo.
- **Impact:** A user can lose an entire later conversation when expecting a single-message deletion; a failed resend can erase later history without replacing it.
- **Guideline:** Destructive actions must make their consequences and scope clear and protect against accidental irreversible loss.
- **Recommendation:** Name the actual branch/history action, preview the affected scope, confirm or support undo, and preserve the existing branch until a resend has safely succeeded. Ordinary single-message deletion should behave as named if that remains the intended feature.
- **Suggested command:** `$impeccable harden`, followed by `$impeccable clarify` for the scope wording.

### F05 — Semantic names and selection states are inconsistent across secondary flows

- **Location:** `screens/settingsScreen/SettingsScreen.kt:197–213`; `screens/lorebookScreen/LorebookScreen.kt:119–145`; `screens/digilineScreen/FarmGroupScreen.kt:165–180`; `screens/offlineBattle/TechniqueLoadoutScreen.kt:458–510`; `screens/worldScreen/WorldScreen.kt:1301–1304`; `screens/digifarmScreen/DigifarmScreen.kt:879–893`; `components/CharacterEntry.kt:112,273–296`; `screens/cardScreen/CardAdventureEntry.kt:75–115`.
- **Category:** Accessibility.
- **Evidence:** Several switches are separate focusable controls beside, rather than semantically associated with, their labels. Group-recipient and loadout selections change border/color without selected/toggle semantics. Storage/picker portraits inherit `Icon`/`Card icon`; meaningful unlocked adventure name artwork has `contentDescription = null` and no textual name alternative. In contrast, the theme/language pickers and master Debug rows demonstrate the correct semantic approach.
- **Impact:** Users hear an unnamed on/off control, cannot establish selected recipients/equipment reliably, or cannot identify meaningful sprite/name content. Color alone does not communicate the saved selection.
- **Guideline:** Accessible names, roles, checked/selected state, and text alternatives must accompany controls and meaningful images.
- **Recommendation:** Reuse labeled `toggleable`/`selectable` row patterns, merge related descendants deliberately, expose the selected state, and attach actual partner/catalog names to meaningful image actions. Use a truthful card/slot fallback when species identity is unknown; do not invent a name from artwork.
- **Suggested command:** `$impeccable harden`.

### F06 — Secondary windows and fixed-height readouts have no overflow fallback

- **Location:** Provider picker `screens/settingsScreen/dialogs/LlmSettingsDialog.kt:211–240`; font picker `screens/settingsScreen/SettingsScreen.kt:352–390`; origin/rename/item dialogs `screens/cardScreen/dialogs/CardOriginDialog.kt:33–59`, `screens/cardScreen/dialogs/CardRenameDialog.kt:37–69`, `screens/itemsScreen/ItemDialog.kt:50–132`; NFC instructions `screens/scanScreen/screens/ReadCharacterScreen.kt:44–83`, `screens/scanScreen/screens/WriteCardScreen.kt:71–150`, `screens/scanScreen/screens/WriteCharacterScreen.kt:73–150`; Home `screens/homeScreens/screens/BEHomeScreen.kt:94–118`, `components/VitalsPanel.kt:149–180`.
- **Category:** Adaptivity / Accessibility.
- **Evidence:** Provider/font lists and several dialog/instruction bodies are ordinary unscrollable Columns. The provider list has seven 48dp Material targets before its title/footer. Home's right-hand square divides its height across three fixed stat rows; at 360dp width, each has roughly 49dp, while the 16sp+20sp stacked line heights require 72dp under linear 2.0 font scaling. Other parts of the app already use bounded scrollable bodies and persistent actions.
- **Impact:** Short landscape/multi-window/IME-visible windows can hide choices or actions. Large text can clip Home's facts and secondary-screen content. The exact appearance on current hardware remains unverified.
- **Guideline:** Text scaling and available window height must not make task-critical content unreachable.
- **Recommendation:** Use current window bounds, scrollable content with persistent actions, and wrapping/stacked action groups. Retain Home's approved square composition at normal size, with an intrinsic-height or stacked fallback when larger text cannot fit.
- **Suggested command:** `$impeccable adapt`.

### F07 — Technique loadout does not reserve the keyboard inset

- **Location:** Technique editor; `navigation/AppNavigation.kt:188–190`, `screens/offlineBattle/TechniqueLoadoutScreen.kt:181–246`, `screens/offlineBattle/TechniqueLoadoutScreen.kt:304–313`.
- **Category:** Adaptivity.
- **Evidence:** The outer shell explicitly excludes IME and delegates it to editable screens. Loadout has a search field and bottom Save bar, but no `imePadding`/IME inset ownership on either its scaffold or bottom bar. The chat and main Settings screens already implement that responsibility.
- **Impact:** The keyboard-visible layout can cover Save and the lower technique list; users must dismiss the keyboard to regain controls. Native screenshots are still needed to confirm the device-specific overlap.
- **Guideline:** Edge-to-edge editable screens must reserve IME insets for fields and primary actions.
- **Recommendation:** Give the loadout screen explicit IME ownership and keep Save accessible while filtering, consuming inherited insets once. Check compact portrait, landscape, and multi-window with the real font options.
- **Suggested command:** `$impeccable adapt`.

### F16 — Embedded battle preparation does not share its visible Back hierarchy

- **Location:** `screens/BattlesScreen.kt:1551`, `screens/BattlesScreen.kt:2391–2404`, `screens/offlineBattle/TrainingBattlePreparation.kt:91,231–237`.
- **Category:** Platform Conformance.
- **Evidence:** Preparation is a `currentView` branch inside the single `Battle` navigation destination. Its visible Back button changes that branch to `main`; preparation registers no corresponding Back handler/child destination. The navigation controller therefore handles system Back at the outer destination instead. Arena lobby and live offline battle already own their internal Back steps.
- **Impact:** Button Back and Android Back can lead to different screens; returning to the saved Battles destination can restore the abandoned preparation branch.
- **Guideline:** System Back must follow the same navigational hierarchy as the screen's visible affordance.
- **Recommendation:** Represent the embedded step in navigation, or wire its Back to the same state transition using the native Back APIs. Preserve live-battle exit confirmation and result-recording behavior. Validate predictive Back on hardware after repair.
- **Suggested command:** `$impeccable harden`.

## Detailed findings — P2 minor

### F08 — Mood/trust visibility still follows the superseded rule

- **Location:** `screens/homeScreens/screens/VBDiMHomeScreen.kt:144–148`, `screens/homeScreens/screens/BEHomeScreen.kt:132–136`, `components/CharacterEntry.kt:317–361`, `screens/digilineScreen/DigilineScreen.kt:176–178`.
- **Category:** Platform Conformance — product/immersion consistency.
- **Evidence:** Home places the number in expandable `detailText`. Wild thread trust is only a summary fallback, and an existing last message replaces even that summary. Shared chat status now correctly shows summary, exact label and progress together (`components/ChatConversationUi.kt:76–94`).
- **Impact:** Users cannot compare these readings directly across everyday screens as requested. Opening Details or the conversation is a workaround.
- **Guideline:** The latest brief requires directly visible mood and trust; exact farm-care needs are still allowed in normal Details.
- **Recommendation:** Show Home mood directly and a separate persistent trust/rapport reading on wild threads, independent of the message preview. Replicate the corrected chat status pattern. Note the stale documentation without repairing unrelated Impeccable artifacts as a side effect.
- **Suggested command:** `$impeccable clarify`.

### F09 — Equal-width Storage actions still have independent heights

- **Location:** `screens/storageScreen/StorageDialog.kt:233–255`, `components/CyberUi.kt:207–244`.
- **Category:** Adaptivity.
- **Evidence:** Both actions now use `.weight(1f)` with an 8dp gap. Each Material button still measures its own wrapped text; the row has neither a shared intrinsic height nor matching fill-height modifiers.
- **Impact:** Longer localized text or wider font choices can recreate unequal heights. The earlier width fix is real; current pixel-level height mismatch has not been device-confirmed.
- **Guideline:** Adjacent actions should remain aligned and readable under localization/text scaling.
- **Recommendation:** Give the pair a shared content-driven height, or stack them when width/font scale makes a two-column pair unsuitable. Preserve the approved primary/secondary hierarchy.
- **Suggested command:** `$impeccable layout` / `$impeccable adapt`.

### F10 — Widget animation repeatedly rebuilds full RemoteViews bitmaps

- **Location:** `widget/DigimonWidgetProvider.kt:74–126`, `widget/DigimonWidgetProvider.kt:277–359`.
- **Category:** Performance.
- **Evidence:** A main-looper timer runs every 220 ms while the process remains alive. Every widget update allocates a full-size ARGB bitmap, creates RemoteViews/PendingIntent state, and calls `updateAppWidget`; it continues even for a resolved-empty widget. Loading moved off-thread, but rendering/update churn did not.
- **Impact:** Large/resized or multiple widgets create avoidable allocations and Binder traffic that share the app's UI thread. CPU/battery/frame-time impact was not measured.
- **Guideline:** Cache image/rendering state and avoid repeated heavy work on the UI thread.
- **Recommendation:** Cache size/theme/static RemoteViews, reuse frame/render resources, stop animation for empty state, and coalesce partial updates. Preserve the intended walking character; measure the bounded optimized path on a real launcher.
- **Suggested command:** `$impeccable optimize`.

### F11 — Widget invalidation cannot reject an older asynchronous result

- **Location:** `widget/DigimonWidgetProvider.kt:147–152`, `widget/DigimonWidgetProvider.kt:200–205`, `widget/DigimonWidgetProvider.kt:249–264`.
- **Category:** Platform Conformance — state consistency.
- **Evidence:** Partner change clears caches but leaves the existing load in flight. That load unconditionally publishes its earlier snapshot and sets `spritesResolved = true`; there is no generation/identity check. A change between its transaction and publication can restore the previous partner until the roughly 26-second refresh.
- **Impact:** The widget can show the wrong active partner immediately after an intentional change. This race window is source-confirmed; it was not runtime-reproduced.
- **Guideline:** Delayed UI results must not overwrite newer user state.
- **Recommendation:** Version invalidations and reject stale publications, scheduling a current reload when necessary; publish identity/name/art together. Keep resolved-empty caching.
- **Suggested command:** `$impeccable harden`.

### F12 — Owned chat steals the reading position on every history-size change

- **Location:** `screens/chatScreen/ChatScreen.kt:102–104`; compare `screens/worldScreen/WorldChatScreen.kt:88–91,140–141` and `screens/digilineScreen/FarmGroupScreen.kt:83–93`.
- **Category:** Platform Conformance.
- **Evidence:** Owned chat always animates to `messages.size - 1`; it does not check whether the user is reading older history. Wild and farm chats already conditionally follow the bottom.
- **Impact:** Incoming messages or deletion changes interrupt reading and can move the screen-reader user's context unexpectedly.
- **Guideline:** Preserve user-controlled reading/focus position during asynchronous updates.
- **Recommendation:** Reuse bottom-follow behavior, with a deliberate new-message affordance when the user has scrolled away. Apply the motion preference to optional scrolling.
- **Suggested command:** `$impeccable harden`.

### F13 — Manually driven motion does not consistently honor Remove animations

- **Location:** `components/CyberUi.kt:259–263`, `components/CharacterEntry.kt:133–141`, `screens/BattlesScreen.kt:205–227`, `screens/worldScreen/WorldScreen.kt:1779–1784`.
- **Category:** Accessibility.
- **Evidence:** The shared motion helper remembers the first `areAnimatorsEnabled()` result without a resume/settings refresh. Some sprite loops use that cached value; the online damage-number and Radar species-dialog loops do not consult the preference at all. Widget explicitly checks the live setting, which is a useful positive counterexample.
- **Impact:** Nonessential motion can continue after the system preference changes or be animated even when the screen was opened with animations disabled.
- **Guideline:** Honor the Android Remove animations setting for nonessential visual motion.
- **Recommendation:** Observe/refresh the setting on lifecycle resume and apply it to manually timed decorative motion. Retain functional timing/input such as the Blast challenge; disabling decoration must not auto-complete gameplay.
- **Suggested command:** `$impeccable animate`.

### F14 — Delayed Dex feedback captures an old Debug permission

- **Location:** `screens/cardScreen/CardsScreen.kt:53,111–121`.
- **Category:** Platform Conformance — product boundary.
- **Evidence:** `retrySpeciesMatch` completion passes the composable's captured `debugEnabled` to `DiagnosticText.feedback`. Turning Debug off in Settings while the operation is pending does not change that existing callback's value.
- **Impact:** Technical feedback can appear after Debug has been disabled, even though most screens correctly react to revocation. Redaction remains in place; this is an authorization/immersion inconsistency.
- **Guideline:** The established master switch revokes diagnostics immediately, including delayed callbacks.
- **Recommendation:** Check the live shared repository at callback/publication time and route ordinary feedback through the shared feedback host. Do not treat the launch-time snapshot as lasting authorization.
- **Suggested command:** `$impeccable harden`.

### F15 — Several collection screens initially claim to be empty rather than loading

- **Location:** `screens/storageScreen/StorageScreen.kt:90,283–286`, `screens/cardScreen/CardsScreen.kt:47,64–68`, `screens/itemsScreen/MyItems.kt:33–38`, `screens/adventureScreen/AdventureScreen.kt:45,79–83`, `screens/digilineScreen/DigilineScreen.kt:68–70`, `screens/digifarmScreen/DigifarmScreen.kt:123–136`, `navigation/AppNavigation.kt:461–487`.
- **Category:** Platform Conformance — readiness/recovery.
- **Evidence:** These screens collect a database flow with `emptyList()` as the initial value, then present their empty state (or Create farm) before the first snapshot. There is no separate loading/error state at these UI boundaries. The registered `WorldChat/{spawnId}` alias also keeps its spinner indefinitely when the one-shot spawn lookup returns null. Home, CardView and Storage Scan Collection already distinguish loading, empty, and failure.
- **Impact:** Navigation can flash misleading absence/creation prompts. A slow, failed or unavailable lookup lacks the clear readiness/recovery vocabulary already established in Home.
- **Guideline:** Loading, genuinely empty data and failure are distinct states; preserve cached usable data while refreshing.
- **Recommendation:** Reuse nullable/typed loading state or cached snapshots, show actionable failure/unavailable recovery, and offer creation only after confirmed empty data. Keep the existing lazy collections and empty-state identity.
- **Suggested command:** `$impeccable harden`.

## All-screen coverage

**Reviewed means source inspected, not a runtime pass.** A destination can contain several screens; aliases can share one composable. References below cover each registered route and its subordinate surfaces. The [interactive audit](C:/Users/julye/.cursor/projects/empty-window/canvases/vbhelper-screen-audit.canvas.tsx) provides the filterable coverage matrix and source-opening controls.

### App shell and Home

- **Shell / More / expanded rail / Snackbar / incoming character import:** `navigation/AppNavigation.kt`, `navigation/BottomNavigationBar.kt`, `MainActivity.kt`, `components/TopBanner.kt`, `components/TechBackground.kt`, `ui/theme/Theme.kt`, `ui/theme/AppTheme.kt`, `ui/theme/Type.kt`. Compact selected-tab semantics, expanded rail, heading labels and inset ownership are strong; secondary route behavior is F07/F16.
- **`Home`:** loading, setup checklist, skip/resume, empty collection, partner selection, unavailable details, cached ready state, VB DiM, BE DiM and BE Memory variants. Device-specific variants share the BE implementation; F06/F08. Preserve the hero/vitals/square-stat arrangement and favorite actions.
- **Home subordinate views:** Dex details on long press, transformation history/reversion, weekly vitals, special missions/removal, mission completion and rewards; `screens/homeScreens/dialogs/`, `components/VitalsPanel.kt`, `components/TransformationHistoryCard.kt`, `components/ActiveDigimonCard.kt`, `components/CharacterEntry.kt`. Explicit reversion confirmation and favorite accessibility actions are positive; small-window dialog/readout risk is F06.

### Storage and selectors

- **`Storage`:** search, filters, sort, no-results, favorite/active/farm metadata, adventure diversion, adaptive keyed grid; `screens/storageScreen/StorageScreen.kt`. Retain this as the selection model; F05/F13/F15.
- **Partner details / actions / nickname and Debug species editor:** `screens/storageScreen/StorageDialog.kt`, `DigimonInfoEditDialog.kt`. Scrollable bounded body, persistent Close, nickname/species separation and delete confirmation are positive; F09.
- **Adventure duration / delete confirmation / scan collection / scan conversion:** `StorageAdventureTimeDialog.kt`, `StorageScanCollectionDialog.kt`, shared conversion dialog. Radio semantics, loading/failure/retry, guarded conversion and live-region feedback are positive.
- **Shared full-screen picker / picker dialog:** `StorageCharacterPicker.kt`, reused by item application, battle preparation, Arena, quests and farm residents. Adaptive keyed selection is strong; image/action naming and motion refresh share F05/F13.

### Dex and card flows

- **`Dex`:** card list, import/edit affordances, batch progress/stop/completion/issues, origin choice, rename and delete; `screens/cardScreen/CardsScreen.kt`, `CardSelectionScaffold.kt`, `CardBatchImportPanel.kt`, `dialogs/CardOriginDialog.kt`, `CardRenameDialog.kt`, `CardDeleteDialog.kt`. Keyed list and real import progress are positive; F06/F14/F15.
- **`Card/{cardId}`:** card selection, evolution/fusion chart, discovery states, bounded pan/zoom, fit/zoom buttons, chart loading/empty/retry, card navigation; `CardViewScreen.kt`, `CardEvolutionChart.kt`, `CardChartViewport.kt`. The chart already has scroll-axis semantics, selection state and a 48dp minimum node scale. Preserve those implementations.
- **Dex profile / Jogress / species picker / scan conversion:** `dialogs/DexCharaDetailsDialog.kt`, `DexCharacterDetailsContent.kt`, `DexCharaFusionsDialog.kt`, `DigimonScanContent.kt`, `DigimonScanConversionDialog.kt`, `components/SpeciesPickerDialog.kt`. Source-aware profile reading order, ordinary references, wrapping facts, window bounds and external Close are positive.
- **`CardAdventure/{cardId}`:** locked/unlocked card missions, artwork/stats/steps; `CardAdventureScreen.kt`, `CardAdventureEntry.kt`. Virtualized list and privacy states are positive; meaningful name alternatives share F05.

### Items and adventures

- **`Items` with `MyItems`, `ItemsStore`, and battle-supply tabs:** `screens/itemsScreen/ItemsScreen.kt`, `MyItems.kt`, `ItemsStore.kt`, `QuestBattleItems.kt`, `ItemElement.kt`. Adaptive keyed grids, visible bits balance, and scoped supply descriptions preserve the approved vocabulary; F06/F15.
- **Item use/purchase dialog / acquired-item reward:** `ItemDialog.kt`, `ObtainedItemDialog.kt`. Real quantities/cost/rewards are retained; short-window overflow is F06.
- **`ApplyItem/{itemId}`:** device-eligible character picker, applying guard, result return; `ChooseCharacterScreen.kt` plus shared Storage selector. Shares F05/F15-style recovery concerns; selector identity stays consistent.
- **`Adventure`:** empty/list, ticking countdown, collect rewards and cancel confirmation; `screens/adventureScreen/AdventureScreen.kt`, `AdventureEntry.kt`, `CancelAdventureDialog.kt`. Keyed lazy list and destructive confirmation are positive; F06/F15.

### NFC

- **`Scan/{characterId}`:** direction selection, connection readiness, preparation failure/retry, transfer status; `screens/scanScreen/ScanScreen.kt`, `ChooseConnectionScreen.kt`. Status has a polite live region and recovery; inspect under hardware as a follow-up.
- **Read preparation / active reading / ambiguous-card selection:** `screens/ReadingScreen.kt`, `ReadCharacterScreen.kt`, `ActionScreen.kt`, `cardSelect/ChooseCard.kt`, `ScanCardEntry.kt`. Real device instructions and cancellable reading remain intact; F06.
- **Write-card preparation / card check / write-character confirmation / active writing:** `screens/WritingScreen.kt`, `WriteCardScreen.kt`, `WriteCharacterScreen.kt`, `ActionScreen.kt`. Lifecycle cancellation and persistence-before-completion are preserved; F06.
- Typed `NfcImportResult` is a positive data boundary; no synthetic transfer animation is counted as a shipped progress view.

### Communication and quests

- **`Digiline`:** stored, wild and farm threads, unread badges and empty lists; `screens/digilineScreen/DigilineScreen.kt`. Keyed threads and recognizable shared surfaces are positive; F08/F15.
- **`Chat/{characterId}`:** species loading/gate, mood, messages, composer/error recovery, copy/resend/delete and Debug manual species editor; `screens/chatScreen/ChatScreen.kt`, `dialogs/SpeciesManualEditDialog.kt`, shared `ChatConversationUi.kt`. Draft retention and direct numeric status are positive; F04/F05/F12.
- **`WorldChat/{spawnId}` and `WildContact/{individualId}/{cardCharacterId}`:** shared private wild chat, trust, journal notices, quest links, challenge/recruitment/pending/vanished outcomes; `screens/worldScreen/WorldChatScreen.kt`, `navigation/AppNavigation.kt:461–487`. Shared IME handling and bottom-follow policy are positive; F04/F05. A missing spawn on the former route needs an unavailable/recovery state rather than its current indefinite spinner; included in the readiness recovery work for F15.
- **`WorldConversation/{interactionId}`:** public/group participants, ended/stale/distance states, challenges and composer; `WorldConversationScreen.kt`. Author attribution, bounded input and explicit participation eligibility are positive.
- **`FarmGroup/{farmId}`:** resident recipients, audience snapshots, conversation, autonomous-dialogue failure, composer; `screens/digilineScreen/FarmGroupScreen.kt`. Draft retention, lifecycle-managed sessions and conditional bottom following are positive; F05.
- **`Quests` and `WorldRecruits`:** current/history/category/focus, objectives, rewards, partner binding, watch preparation/discontinuity, availability, follow-up chains, accept/decline/abandon/turn-in/track; `screens/questScreen/QuestScreen.kt`, `screens/digilineScreen/QuestJournal.kt`, `QuestPartnerPicker.kt`, `screens/worldScreen/WorldRecruitsScreen.kt`. Requirements and recovery remain visible; long content uses lazy lists.

### Radar and Digifarm

- **`World` hub with Radar/Farm tabs:** `screens/worldScreen/WorldHubScreen.kt`. Material tabs preserve clear selection and the common palette.
- **Radar 2D / first-person / session/compass/location recovery / label and look controls:** `WorldScreen.kt`, `RadarFirstPersonViewport.kt`, `RadarSessionStatusPanel.kt`, `WorldCompass.kt`, `RadarOverheadAnnotation.kt`. Marker controls have names, first-person targets are at least 48dp, and explicit look/zoom controls exist; do not misclassify the entire map as an inaccessible bare Canvas. F05/F13 apply to secondary controls.
- **Encounter action sheet / spectating interaction / side selection / challenge preview:** `WorldScreen.kt`, `WorldInteractionOverlay.kt`, `QuestTargetActions.kt`. Blocked reasons and team previews are visible; live battle shares F03/F06.
- **Spawn-card settings / species list / Debug spawn picker:** `WorldScreen.kt`, `DebugSpawnPickerDialog.kt`. Lazy lists, runtime-gated picker and retries are positive; F05/F13.
- **Farm empty/create / selected 3D farm / resident strip/list / settings / care/actions / transfer/archive:** `screens/digifarmScreen/DigifarmScreen.kt`, `Digifarm3dViewport.kt`. Mesh preparation is off-thread; named resident-list alternatives exist; bounded scrollable sheets and ordinary care Details are positive. F05/F15 apply. Scene hotspots should also receive each resident's name instead of only a generic action label.

### Battles and Arena

- **`Battle` hub / offline entry / authentication / online opponents / resume-quit / online live battle / result:** `screens/BattlesScreen.kt`, `OfflineBattleEntryPanel.kt`, `BattleAuthenticationPrompt.kt`. Offline and Arena are available independently of online authentication; real opponent-load recovery exists. F02/F06/F13/F16 apply.
- **Training preparation / 1×1, 1×2, 2×2 / participant selection:** `screens/offlineBattle/TrainingBattlePreparation.kt`, shared picker. Persistent participant IDs and distinct-team start guards are positive; F05/F06/F16.
- **`TechniqueLoadout/{characterId}`:** equipped slots, special/Blast/Jogress choices, searchable catalog, filters, saving/failure; `TechniqueLoadoutScreen.kt`, `TechniqueStatusBadges.kt`. Keyed catalog and palette-aware Save are positive; F05/F06/F07.
- **Live training / Arena / Radar battle:** loading/renderer error/retry, portrait/wide scene/deck, pause/tactical pause, camera, strategy/technique/item dialogs, Blast timing, finisher overlays, exit confirmation, result/rematch/rewards/recording; `OfflineTrainingBattleScreen.kt`, `OfflineBattleScene.kt`, `BattleFinisherOverlay.kt`, `DigimonScanBattleRewards.kt`. Renderer lifecycle, reserved command/resource states, genuine outcomes and prepared rematches are positive; F03/F05/F06.
- **Tamer Arena:** lobby/roster/search/series/ready filters, team editor, briefing/loadout/source, cups/pool/saved runs, round brackets/withdrawal, records/trophies, settlement/result/rematch/next round; `screens/tamerArena/TamerArenaScreen.kt`, `TamerArenaLobby.kt`, `ArenaBriefing.kt`, `ArenaCups.kt`, `ArenaResult.kt`, `ArenaWidgets.kt`. Virtualized roster, IO-cached art, explicit missing-art recovery, native internal Back, and reconstructive/associated/guest framing are positive. Detailed stat/action rows need the F06 large-text pass.

### Settings, research and entry surfaces

- **`Settings`:** device/card import, palette/font, audio, language/Tamer name, communication, research, card-origin setting, master Debug/instant scan/raw templates/inspector, About/Credits and backup/restore; `screens/settingsScreen/SettingsScreen.kt`, `AppThemePicker.kt`, `dialogs/LanguageDialog.kt`, `LlmSettingsDialog.kt`, `PromptTemplateDialog.kt`, `components/BackgroundMusicControlPanel.kt`. Saved palette selection is intentional; absence of automatic wallpaper/night-mode switching is not a defect. F05/F06.
- **Backup/file selection/restore confirmation:** `SettingsScreen.kt`, `SettingsScreenControllerImpl.kt`, `controllers/DatabaseManagementController.kt`; native system document/tree pickers and the explicit restore decision are retained.
- **`Lorebook`:** published reference library, personal notes, enable/edit/delete, create/edit and Debug retrieval priority; `screens/lorebookScreen/LorebookScreen.kt`, `LorebookEntryDialog.kt`, `components/ResearchLibrary.kt`. One outer lazy scroll, ordinary source access, scoped research and note deletion confirmation are positive; F05/F15.
- **`Credits`:** `screens/settingsScreen/CreditsScreen.kt`. Scrollable reading layout reuses Settings typography; no distinct confirmed defect beyond shared concerns.
- **`Viewer`:** Debug asset inspector with revoked-route exit, loading/empty/failure/truncation; `screens/spriteViewer/SpriteViewer.kt`, `SpriteViewerControllerImpl.kt`. Runtime authorization and off-thread decoding are positive; F01.
- **MainActivity incoming character import/auth entry:** `MainActivity.kt`, `app/src/main/AndroidManifest.xml`. Platform entry/import handling inspected; authentication, transport and fresh process/restore behavior require device checks.
- **Home widget:** `widget/DigimonWidgetProvider.kt`, `app/src/main/res/layout/digimon_widget.xml`, `app/src/main/res/xml/digimon_widget_info.xml`. Actual partner names, palette-resolved empty recovery, background loading and live motion checks are positive; F10/F11.

### Dormant and validation-only surfaces

- **Five companion activities:** card import, card validation, firmware transfer, watch-log confirmation/loading, and the compatibility character-import toast/finish endpoint. Their source under `companion/` was inspected. None is declared in the main/library manifests or launched from the current ordinary Settings screen; only `MainActivity` is registered. They are dormant source, not five additional shipped destinations. Their standalone theming/error/scaling debt is not used to lower this release UI's score.
- **Unused Home/transfer presentations:** `UnifiedHomeScreen`, `BetaWarning`, `TransferAnimationScreen`, `TransferCompleteScreen` have definitions but no current source callers. The synthetic percent loop in the unused transfer animation is not attributed to the active NFC flow.
- **Validation hosts:** `OfflineBattleValidationActivity`, `EnvironmentThemeValidationActivity` are declared only in `app/src/integrityCheck/AndroidManifest.xml`. They are test infrastructure, not user navigation screens.

## Patterns and systemic issues

- **Strong primitives, uneven adoption.** Theme roles, labeled selectable/toggleable rows, bounded editor bodies, and readiness states exist, but secondary flows often duplicate older patterns instead of reusing them.
- **Asynchronous work needs publication rules.** Moving work to IO/Default is only half the contract: inspector limits must precede materialization, widget results need generations, and diagnostic callbacks need live authorization.
- **Visual pixels are not semantic state.** An image name, highlight border, selected recipient, opponent bar or scene tap needs a meaningful accessible equivalent.
- **Available height is the missing adaptation axis.** Width-adaptive grids/rails are already good. Height, larger text and IME ownership need consistent treatment.
- **In-world phrasing must retain usable readings and truthful scope.** Mood/trust visibility and conversation deletion consequences should be explicit; actual research, memories, watch facts and training rules must stay distinguishable.

## Positive findings to preserve and replicate

1. **Home and Storage are the reference system.** Shared typography, restrained frames, sprite-first hierarchy and direct operational actions provide continuity worth extending.
2. **Palette architecture is deliberate.** Four user-chosen light/dark schemes, native system-bar treatment, paired foreground tokens and all 15 configurable type roles exist. Dynamic Color is deliberately outside this brief.
3. **Main collection performance patterns are sound.** Storage/Dex/threads/quests/roster use keyed lazy collections; grid density adapts; farm meshes and Arena art are prepared off-thread.
4. **Several accessibility implementations are exemplary.** Theme/language/master Debug controls expose roles/state; TopBanner has labeled 48dp actions and heading semantics; evolution-chart scrolling and Home favorite custom actions are explicit.
5. **Readiness already has good exemplars.** Home distinguishes real setup/loading/empty/unavailable states. CardView and Storage Scan Collection provide loading, failure and retry. These are the model for F15.
6. **Editors and core dialogs already have reusable bounded patterns.** Nickname/species, notes and raw prompts use window bounds, scrolling and wrapping actions. Storage Close and Dex Close remain outside their scroll bodies.
7. **Chat has useful shared infrastructure.** Composer labels, IME handling, draft retention, polite errors and guarded sending are present. Wild/farm bottom-follow behavior is better than owned chat's and can be reused.
8. **Research/identity boundaries are visible.** Ordinary profile/catalog/reference access is separate from technical provenance. `WorldChatMemoryRepository` explicitly skips SYSTEM adoption and stores battle context in memory rows outside visible chat.
9. **Game/transport truth is retained.** Typed NFC results, persistence-aware completion, real scan progress, battle resource reservations, missing-art recovery and truthful Arena result/recording states are positive.
10. **Debug mostly has the right architecture.** A shared default-off switch, reactive surface gates and repository/action guards exist across builds. F14 is a delayed-publication exception, not an argument for build-flag-only authorization.

## Recommended actions

1. **[P1] `$impeccable optimize`** — F01 first: bounded inspector queries/decoding; include the scoped widget render/cache work in F10.
2. **[P1] `$impeccable harden`** — F03/F04/F05/F16: accessible battle selection/health, stateful-control semantics, safe/explicit chat branching, and native internal Back. Include delayed-publication/readiness fixes F11/F12/F14/F15.
3. **[P1] `$impeccable colorize`** — F02: correct the online-battle foreground/container pairings inside the existing palettes.
4. **[P1] `$impeccable adapt`** — F06/F07: short-window scrolling, large-text fallback and technique-editor IME ownership; include F09's equal-height/stacking behavior.
5. **[P2] `$impeccable clarify`** — F08 and F04: directly visible mood/trust plus truthful destructive-action scope, preserving factual lore and the Home/Storage voice.
6. **[P2] `$impeccable animate`** — F13: refresh the system motion preference and gate nonessential manual animation while keeping functional gameplay timing.
7. **Final `$impeccable polish`** — one bounded native acceptance pass after those targeted repairs, preserving the approved reference screens.

You can ask me to run these one at a time, all at once, or in any order you prefer.

Re-run `$impeccable audit` after fixes to see your score improve.

## Device acceptance still needed

When a device is connected, cover compact portrait/landscape and a short/expanded window, all four palettes, the actual font options, English/Portuguese/Japanese, font scaling, keyboard-visible editing, TalkBack combatant/recipient/setting selection, and system Back through embedded battle steps. Reproduce the inspector problem with the user's actual collection/logs and measure widget/rendering work. Historical screenshots and prior compilation are not substitutes for these checks.
