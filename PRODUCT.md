# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Players of Digimon games and owners of V-Pets/Vital Bracelet devices who want one
continuing home for their individual Digimon across supported products.

## Product Purpose

VBHelper is a universal Digimon home and in-world Tamer companion: individuals move
through supported games and V-Pets via Storage while retaining identity and progress.
The app tracks and converts growth between products and presents each stored partner
through its familiar DIM-style VBHelper experience. The whole Digimon.net About tab
(overview, device history, game history, Profile reports, Iliad, Shambhala) is the primary
lore source set, supplemented by official Seekers, Re:Digitize, and Liberator observations
kept in their own scope; see LORE_FOUNDATION.md.

## Positioning

Beyond character storage and watch interaction, VBHelper adds AI chat and real-world Digimon encounters to make the companion experience more immersive.

## Operating Context

The app is used on Android with Vital Bracelet-series devices. Core workflows include reading characters, storing and inspecting them, sending them back to the watch, applying items and missions, browsing the Dex, and using the chat and real-world encounter systems.

## Capabilities and Constraints

- Native Android app built with Kotlin and Jetpack Compose.
- Home's upper-left Transfer entry is intended to open one origin **list** containing
  games, Vital Bracelet and every other supported Digimon origin. DS entries use their
  actual DS launcher icons. Open a game to see saved individuals with automatically
  resolved species and normal app sprites; tap one for information and Move to Storage.
  A normal supported move is one action, preserving nickname, identity and progress,
  with no required preview/mapping/materialization wizard. Backups and native-write
  verification run internally. The current game-cover/copy prototype is groundwork,
  not acceptance of this final flow.
  A stored individual may enter any supported game that supports its species, not
  only its origin game. Native conversion/writer support is validated per title.
  Save management and technical native review remain secondary tools; device origins
  participate in the same main list rather than a separate origin category.
  Storage forwards transfer actions here with partner context. Time Stranger transfer
  development is paused while actual DS moves are developed, starting with World DS.
- NFC and location permissions support watch interaction and real-world encounter features.
- Card content and device secrets are imported by the user. A credited, offline Dot sprite library is bundled for species appearance selection.
- Database upgrades and representation changes must preserve collection and progress rather than requiring app-data resets. Verification boundaries are recorded in TESTING.md.

## Brand Commitments

- Preserve the Vital Bracelet / Digimon companion-app context.
- Keep the experience simple and practical while supporting a more immersive Digimon-world feeling.
- Existing visual references and the project's dark roxo/ciano interface are binding only where explicitly requested by the product owner.
- Source-first contract: published research, individual history, Tamer notes, device measurements, and app training rules stay distinguishable; research knowledge never becomes an individual's invented memory.
- A default-off master Debug switch exists in every build and gates diagnostics, technical editors, and testing overrides; ordinary use never exposes them.

## Evidence on Hand

- [README.md](README.md) documents NFC workflows, items/missions, Storage/Dex, card import, preserving upgrades and local PC save access.
- The repository contains the Android application and its Compose UI implementation.
- The bundled Dot library records its source revisions, artist/device provenance and validated species associations in `app/src/main/assets/species_catalog.json`. Users import their own watch card contents.

## Product Principles

- Make core Digimon management direct and dependable.
- Storage is a universal progress intermediary: retain each game's native baseline, translate actual gains through versioned policies, and preserve zero-progress multi-game return fidelity.
- Card-independent, game-origin and V-Pet partners share the complete DIM-style
  Home/stat model. Balanced app stats reflect retained cross-product growth. Raw
  product-specific data belongs in the individual's information under that product's
  Transfer entry, not ordinary Home/Storage.
- Every stored Digimon is usable; there are no VB/Expanded gameplay modes. VB first
  and Dot first are artwork priorities with mutual fallback, never participation rules.
- Vital Bracelet export requires an exact loaded DIM/BEM association. While assigned,
  that card's normal stat rules override extra app/cross-product bonuses. All baselines,
  earned growth and conversion information remain retained and recover their balanced
  app influence when the association is removed. Configuration never awards progression.
- Treat immersion features as an enhancement to, not a replacement for, practical device workflows.
- Respect the user-owned nature of character and card assets.
- Preserve Android-native interaction expectations and accessibility.
- Users can add/edit their own species reference records and select a shared sprite set per species across DiMs. These catalog and appearance features are ordinary personalization, available outside Debug.
- Home shows a short qualitative mood summary without a numeric score, keeping its card compact alongside neighboring stats. Trust retains its summary and exact reading in everyday views. Other care readings remain available in normal Details; keep useful game data visible and move only implementation detail behind Debug.
