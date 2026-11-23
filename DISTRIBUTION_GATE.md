# Distribution Gate — required before any public distribution

The DigiLine Free Talk ("Papo") feature is developed for a **personal, non-distributed build**. No shipping,
publishing, store upload, APK/asset sharing, or public demo happens until every item below is resolved.

The owner takes responsibility for the current use of third-party material and accepts that licensed content/assets
will be removed or rewritten before distribution.

## 1. Why this exists

Free Talk uses a **hybrid corpus**: public dialogue corpora as offline **seeds/patterns**, authored frames and
generated lines, plus Digimon game-data atoms from the app's own catalog. Some seed corpora have licenses that do not
allow redistribution in an app (e.g. CC BY-NC / research-only / copyrighted). The runtime asset
(`app/src/main/assets/freechat/**`) must therefore contain **only project-owned text** derived and rewritten from
those seeds — never verbatim dataset text.

## 2. Required gate checklist (all must pass)

- [ ] `tools/freechat/seed_provenance.json` lists every seed source (name, URL, license, usage, date retrieved).
- [ ] No verbatim/near-verbatim third-party text in `app/src/main/assets/freechat/**`, verified by originality
      hashing against the seed corpora (`tools/freechat/validate_freechat.py` + a reference corpus hash list).
- [ ] Replace/remove any seed-derived line whose provenance is NC/research-only/unclear: either rewrite it from
      scratch or delete it, then regenerate `free_chat.json` and re-run the validator.
- [ ] `tools/freechat/data/` contains only project-authored frame/phrase banks; seed downloads are not committed
      (keep them ignored by git, e.g. `tools/freechat/_seeds/`).
- [ ] Digimon game-data facts shipped in `species_voice.json` are limited to project-authored fact atoms derived
      from the app's own bundled catalog; review `LORE_FOUNDATION.md` boundaries.
- [ ] Third-party art/audio referenced by this feature: none (text-only feature). If a later version adds any,
      record it here and remove before distribution.
- [ ] Re-run: `python tools/freechat/build_freechat.py && python tools/freechat/validate_freechat.py`.
- [ ] Confirm the final APK/asset bundle contains no licensed seed text (scan the built APK's
      `assets/freechat/**` with the originality hash list).
- [ ] Update `README.md`/`PRODUCT.md`/store copy to describe the feature as authored/retrieved, not "AI-generated".

## 3. Temporary assets currently allowed in the personal build

| Item | Where | Status |
|---|---|---|
| Public dialogue seed corpora (Cornell/DailyDialog/SODA/OASST/…) | `tools/freechat/_seeds/` (build-time only, git-ignored) | Temporary, personal build only; never shipped verbatim |
| **`app/src/main/assets/freechat/v1/en/database_fallback.json`** | runtime (licensed dataset dialogue used only as the last-resort answer layer) | **REMOVE OR REWRITE BEFORE DISTRIBUTION** — this is raw third-party dataset text (Cornell research-only, OASST1 Apache-2.0, SODA CC BY 4.0). Regenerate the pack without `databaseFallback`/the file, or replace every pair with project-authored text, then re-run build + validator. The engine falls back to the generated ladder when the layer is absent. |
| `app/src/main/assets/digiline/v1/en/*.json` (existing DigiLine v2 packs) | runtime | Project-authored content; keep |
| `app/src/main/assets/species_catalog.json` | runtime | Bundled game data already used by the app; keep |

## 4. Failure policy

If any checkbox cannot be satisfied, **do not distribute**. Disable Free Talk via its build flag
(`FREE_CHAT_ENABLED=false`) or the Debug kill-switch, and ship the authored card scenes only.

_Last reviewed: 2026-11-17 (created with the Free Talk implementation)._
