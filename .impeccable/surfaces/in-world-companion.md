---
version: 1
slug: in-world-companion
primary_target: app/src/main/java/com/github/nacabaro/vbhelper/screens/settingsScreen/SettingsScreen.kt
related_targets: [app/src/main/java/com/github/nacabaro/vbhelper/components/TopBanner.kt, app/src/main/java/com/github/nacabaro/vbhelper/components/CyberUi.kt, app/src/main/java/com/github/nacabaro/vbhelper/ui/theme/Type.kt, app/src/main/java/com/github/nacabaro/vbhelper/screens/homeScreens/HomeScreen.kt, app/src/main/java/com/github/nacabaro/vbhelper/screens/storageScreen/StorageDialog.kt]
---

# In-world companion refinement

Mode: Operate; reference profiles use Read. Confirmed by the owner: refine the incumbent identity, put exact social/care values in normal details, and provide a default-off Debug switch in all builds.

## Direction contract

THESIS: A dependable Tamer companion and field-research tool speaks about individual Digimon, published research, communications, and training in one professional voice.
OWN-WORLD: Retain all four selected palettes, configurable fonts, angular device shapes, and crisp imported pixel artwork. Reduce heading weight, repeated frames, and decorative motion; accent communicates action, selection, or state.
STORY: Identify a partner, understand its condition, take an everyday action, and inspect exact records or research when needed. Developer machinery requires explicit Debug opt-in.
FIRST VIEWPORT: Compact screen context, a clear partner or task identity, readable current state, then relevant actions. Settings groups everyday configuration before a labeled Debug switch; subordinate testing controls appear only while enabled.
FORM: Precisely scoped, code-led refinement of existing Android surfaces. Preserve native navigation, pinned dialog actions, touch targets, insets, saved appearance choices, and existing game capabilities.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

Digimon.net Profile research establishes general lore; secondary setting-specific observations retain their scope. Research is knowledge, not an individual's invented history. Ordinary failures retain actionable recovery; raw identifiers and diagnostics require Debug. No new shipping raster assets are planned.

## Finish disposition and evidence

Independent source review returned **fix** with six material findings; all six were addressed: transactional debug-gated species writes with commit-time revocation checks, async widget loading with a cached resolved-empty state, a hard lore budget over the serialized reference block with first-entry truncation, a single scrolling research-notes surface, nickname-only saves without forced species names, and role-aware Journal notices in partner chat. The species-catalog null-safety regression (`SpeciesRepositoryTest.fetchDatabase_fallsBackToAssetLoaderWhenNetworkFails`) was fixed.

Verification: **731 unit cases, zero failures, three existing private-fixture skips**; Android-test compilation, `lintDebug` with no errors (baseline unexpanded), `app-debug.apk` and `app-release-unsigned.apk` assembled. Source scope only: no connected device or emulator exists, so native visual/runtime acceptance (screenshots, TalkBack, motion, radio behavior) remains deferred.
