---
primaryTarget: app/src/main/java/com/github/nacabaro/vbhelper/components/SpeciesSpritePickerDialog.kt
relatedTargets:
  - app/src/main/java/com/github/nacabaro/vbhelper/components/BundledDotSpriteChoices.kt
mode: Operate
---

## Direction contract

THESIS: Extend the existing species appearance chooser with genuinely offline Dot alternatives. Choosing artwork does not create a partner or bypass recruitment.

OWN-WORLD: Inherit the existing TopBanner, Material tabs, search, square pixel portraits, palette roles and radio-row interaction. This is a local extension, not a replacement visual world.

STORY: The user opens a species template, switches to Dot sprites, sees artist/device provenance, chooses an alternative, and saves the species-wide appearance. Explicit search and matching/all filters remain available.

FIRST VIEWPORT: Existing header and search stay in place; three concise source tabs precede a matching/all filter and a scrollable list of 72dp crisp portraits, wrapping names and provenance, and native radio selection. Loading, retry and empty states occupy the list, not a blocking dialog.

FORM: Existing-surface extension, shaped directly from the approved offline/species-wide selection requirements; no concept seed is required for a local addition. Selection materializes one validated set, with an inline progress/error state.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance.

## Evidence boundary

Source sprites retain pinned revision, original URLs, author/device metadata and original file hashes in the shipped catalog. Runtime previews decode off-main with a byte-bounded cache. Native screenshots are required for visual acceptance when a connected device is available; compilation alone is not visual acceptance.
