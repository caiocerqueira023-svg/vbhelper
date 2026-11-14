# Mindlink × DMO maps — viability analysis

**Question:** can Tamers Lab build a "Mindlink" feature that lets the user explore
Digimon Masters Online (DMO) maps, and should it?

| Field | Value |
|---|---|
| Date / revision | 2026-11-14 · rev 2 (post-review) |
| Status | Draft for decision — no code changes proposed or made |
| Input research | `DMO_private_server_resources.md`, supplied by the requester out-of-repo (not attached; see Appendix A) |
| Companion evidence | [recon-technical.md](recon-technical.md) — repository envelope, measured · [recon-context.md](recon-context.md) — external facts, 31 cited sources · [REVIEW.md](REVIEW.md) — independent review |
| Requester context | GameKing employee (`[USER]`); unofficial Tamers Lab project (`[REPO]`) |
| Counsel review | **None.** No lawyer has reviewed this document. |

**Provenance legend.** Every non-obvious claim carries a tag:

`[NOTE]` supplied research note (unverifiable at review) · `[REPO]` observed/measured in
this repository (paths/lines in recon-technical) · `[EXT]` external source (`[S#]` in
recon-context §8; all accessed 2026-11-14) · `[USER]` requester-supplied · `[EST]`
author's estimate (planning range) · `[ASSUME]` assumption required to reason · `[COUNSEL]`
needs a lawyer's opinion before relying on it.

---

## Decision summary (one page)

**Answer.** Do **not** build "DMO's maps". Build Mindlink as an **original-art explorer of
Digital World places**, with DMO fidelity kept as a separately gated, first-party option
that needs written permission the requester cannot grant. Every path that copies or
redistributes DMO's client art is blocked, including the "read the user's own install" and
"download converted zones" variants.

**Recommended now (safe, useful either way):**

1. **1-week demand test** — a static/2D atlas concept, 5–10 target users, testing the
   honest question the review raised: does "lore places + partner" substitute for the
   "DMO maps I remember" the requester actually wants? `[EST]`
2. **Atlas (T1)** — 2–3 eng-weeks total, isometric only, sourced from the app's lore ledger
   plus factual zone names/links with a written provenance rule (§8.1). No client
   extraction, no copied wiki prose or images.
3. **Own-art zone pilot (T2)** — 3–5 eng-weeks (+2–3 if the user must walk), authored into
   the renderer's proven envelope, no new renderer capabilities.

**Needs written answers before any further spend (C1 and friends):**

- Which asset universe is permitted, and **which legal entity employs the requester**
  (unresolved externally: Move Games / Movegames Inc. / MOVEINTERACTIVE / Move Interactive).
- HR/legal clearance for the unofficial project and its declaration.
- A "C1-lite" provenance decision for the atlas fields (wiki licence, database rights).
- Start the **first-party conversation at P0**, not after a pilot: it is the only route to
  the feature the requester asked for, and it has the longest lead time.

**Do not start:** any extraction or conversion of DMO content; any bundled or downloaded
DMO asset; any published pack/asset tool; any live-client overlay.

**Top three risks:** no authorization exists and cannot be self-granted; provenance taint
from the note's ecosystem is permanent once code/data enters the repo; the safe substitute
may not satisfy DMO nostalgia — validate before spending on art.

**Cost shape:** see the canonical model in §8.2 (engineer-days, artist-days, counsel
hours, spend cap). No money figures are given here because rates, artist availability and
counsel quotes are unknown; the decision memo must fix a cap in person-days (`[EST]`).

---

## 0. Verdict

**Conditional GO — on the own-art path only.**

| Dimension | Verdict | Why (evidence strength) |
|---|---|---|
| Legal — real DMO assets | **RED** until written permission exists | Assets are licensed IP (footers credit Toei Animation/Hongo + MOVEINTERACTIVE), not GameKing-owned `[EXT: S2,S6]`; the note's ecosystem is private-server/leak-adjacent `[NOTE]` `[EXT: S21]`; employment ≠ reuse licence `[ASSUME][COUNSEL]` |
| Legal — own-art Mindlink | **AMBER**: same franchise-IP review as the rest of the app, plus new metadata-provenance questions | Own art avoids third-party *asset files*, not franchise IP; T1 sourcing needs a written rule (§8.1) `[COUNSEL]` |
| Technical — own-art zone | **GREEN–AMBER**: feasible inside the proven envelope; +2–3 weeks for free walking | Proven class: GLB ≤2.76 MiB, 1,590–4,159 triangles, 2–4 textures, unlit, whole-file load `[REPO]` |
| Technical — faithful DMO zone | **RED–AMBER**: a 10–14 eng-week capability program before per-zone work | Needs NIF/DDS conversion, lit/lightmap rendering, streaming/LOD, collision/nav/locomotion, delivery — none exist `[REPO]` `[EXT]` |
| Content economics | **RED** at "all maps"; **GREEN** at 1–3 curated zones | Linear per-zone art work; map count is unknown and the note's 245 is not usable for planning (§3.1) |
| APK / delivery | **AMBER**: ≈10.17 MiB estimated HEAD headroom; no download pipeline | One faithful map is tens of MiB; APK is assets-dominated `[REPO]` |
| Product fit | **AMBER**: plausible, unvalidated | Mindlink is official lore and the app owns partner identity/Dex `[REPO]`; no evidence the app's users want map exploration (F6) |
| Effort to a user-visible pilot | 1 week demand test → 2–3 weeks atlas → 3–5 weeks + art for a zone `[EST]` | §8.2 |

**Four legal conclusions, stated separately from the technical ones** (`[COUNSEL]`):

1. **Redistribution of DMO client assets is blocked** absent written permission from the
   entities that hold the rights — not merely "risky".
2. **The user-supplied/PC-companion route is not a safe harbour.** It avoids shipping
   assets but requires reverse-engineering/decryption the EULA may prohibit, and its
   primary purpose is still extracting proprietary content.
3. **An employee cannot self-authorize.** Employment creates duties toward one licensee; it
   is not a licence from the IP holders or the asset-owning developer.
4. **Own art removes one asset class, not the franchise-IP question.** Names, locations,
   creature designs and "Mindlink" branding remain subject to the app's existing fan-content
   posture and to trademark review.

**The one-paragraph answer.** Tamers Lab can ship a Mindlink feature where the user "links
in" and explores places of the Digital World: a data-driven scene host, manifest contract,
type-safe route and Room persistence already exist `[REPO]`, and an own-art zone authored
inside the proven scene class fits both the renderer and the remaining APK budget `[REPO]`.
What it cannot do — as an unofficial app, and especially not while its author is a GameKing
employee — is treat DMO's shipped map assets as free raw material. The supplied note's
bundles are described as leaked or stolen, and its map-count reference is a private server
of a kind the game's own operator publicly campaigns against `[NOTE]` `[EXT: S4,S5,S21]`.
The viable move is to separate the experience from the asset source: validate demand, ship
the atlas on original art, prove one zone inside the proven envelope, escalate the
first-party conversation immediately, and only touch real DMO assets inside a sanctioned
GameKing project with the licensor's written agreement.

---

## 1. What is actually being decided

Three decisions are tangled in the request and have different owners:

1. **Product** — atlas of places (T1) or an explorable place (T2)? Tamers Lab owner; the
   1-week demand test de-risks this.
2. **Assets** — where does content come from? Legal/licensing at GameKing, and for real DMO
   assets the licensor. Blocks §3–§6.
3. **Scope** — one curated zone, a small set, or "the maps"? Product owner + content budget;
   priced by §8.2 and gated by §10.

### 1.1 What Mindlink means in official lore (and why it helps)

Official Seekers material defines **Mindlink** as the technology that converts the Digimon
Linker user's consciousness into digital data and transfers it into the Digimon's DigiCore,
letting the user act in digital space `[EXT: S10]`. Two further official details matter
`[EXT: S10]`:

- Mindlink has a **time limit**, which **shortens as the Digimon's stage rises**; exceeding
  it causes **DMIA** — the mind does not return.
- Ryusenji distinguishes a **visualisation generated from observations** from **directly
  perceiving** the Digital World: the shown scenery is "an imagined image of the true
  Digital World generated by AI based on observation data" `[EXT: S11]`.

This gives the feature a diegetic licence to be an *observed reconstruction* rather than a
re-host of another game's world — the framing that avoids the asset trap — and a
lore-justified mechanic: a **link-stability session limit** derived from the partner's
stage. It is cheap, uses stats the app already owns, and makes short sessions intentional
`[EST]`.

**Tone guardrails for that mechanic** (from the review): DMIA is a horror/tragedy state in
canon (mind lost, body vegetative). Use "link stability", never gamify DMIA, never sell
limit extension, never imply the user's real mind is at risk, and keep the ledger's
statement that Mindlink is not a synonym for NFC `[REPO: LORE_FOUNDATION.md:41–47]`.

Boundaries: do not present the app as the Mindlink/Linker *product*, and do not lift
Seekers/Digimon Web text or imagery beyond short attributed paraphrase — the official site
forbids unauthorised reproduction `[EXT: S10]`.

### 1.2 What is ruled out before starting

- **Not a second game client.** No combat, no real spawns, no competing way to play DMO.
- **Not a datamining surface.** No unreleased content, drop tables, or client internals;
  the atlas will carry visible provenance labels (§8.1).
- **Not a private-server tool or distribution channel** for the ecosystem in the note.
- **No runtime link to the live client.** No injection, overlay or memory reading.

---

## 2. Product definition — the tier ladder

| Tier | Experience | Content source | Effort `[EST]` | Status |
|---|---|---|---|---|
| **P1a Demand test** | Static/2D atlas concept to 5–10 target users | Own writing + factual names | 1 week | **Start now** |
| **T1 Atlas** | Zone browser: names, links, landmark notes, lore refs, partner reactions | Own lore ledger + factual metadata; provenance rule §8.1 | 2–3 weeks total (incl. P1a) | **Recommended now** |
| **T1+ Data-space** | Atlas rendered as abstract "data topology" in the **isometric** viewport | Own art | +1–2 weeks | Optional; **isometric only** in v1 (first-person radar is Debug-only `[REPO: DESIGN.md:543–546]`) |
| **T2 Diorama** | One explorable own-art zone: terrain silhouette, paths, props, ambience, partner present | Original art + official lore geography; DMO layout only as untraced reference after C3 | 3–5 weeks, +2–3 if the user walks freely | **Pilot after C1-lite + demand test** |
| **O4.5 Promo-asset atlas** | Recognisable DMO places using **GameKing-authorised promotional/2D map imagery** | Licensed marketing assets, not client files | 1–3 weeks content if permission exists | **Ask at C1/C4** — cheapest recognisability path (F8) |
| **T3 Re-host** | Faithful DMO geometry, textures, lightmaps, props, NPCs, animations | Real DMO assets | ~13–20 weeks first zone, then 2.5–5.5 each | **Blocked** — authorization + capability program |
| **T4 Live link** | Mirror/overlay of the user's running DMO client | Real client at runtime | Months + platform work | **Rejected** — anti-cheat, security, ToS |

### 2.1 Why the atlas is not a consolation prize

The app already knows the user's partners — Storage, Dex, mood/personality, transfers, the
`Radar` world layer `[REPO]`. A map explorer that says *"your partner came from here, and
this is what the data says about it"* uses data the app owns. A faithful 3D DMO zone adds
fidelity but not identity.

### 2.2 The substitution this document is asking for — stated honestly

The requester asked for **DMO's maps**. The safe alternative is **original places of the
Digital World**. Those are different products, and this must not be blurred:

- File Island and Server Continent have been rendered as explorable 3D in Digimon World-era
  games and in DMO itself `[EXT: S22]` `[ASSUME]`; they are not unrepresented in the
  franchise. The earlier claim that these places are "novel" was overstated and is withdrawn.
- What is defensible is narrower: the app's own reference set (Digimon Web, Profile,
  Iliad/Shambhala material) names regions that the app has never *shown*, and the app can
  show them with its own art and its own source discipline `[REPO]`.
- Own art removes an **asset-file** problem; it does **not** remove franchise IP, names or
  trademark questions `[COUNSEL]`.
- Therefore the substitute needs validation before art spend: if users want the DMO
  memories specifically, only the first-party route (O7) delivers them, and that
  conversation should start now rather than after a pilot (F8).

### 2.3 Positioning against the existing Lorebook

The atlas must not duplicate `AppRoute.Lorebook` `[REPO]`. The atlas adds what the Lorebook
cannot: spatial structure (what connects to what), landmark orientation, and partner
presence in a place. If it cannot show that value in the 1-week demand test, the 3D tiers
should not be funded.

### 2.4 The product win to protect

Mindlink should make the collection feel *placed*: zone ↔ species ↔ Dex ↔ metadata ↔
partner memory. That integration is cheap, ownable and independent of any external asset —
the part wikis and model viewers cannot copy from the app's data model `[EST]`.

---

## 3. What "using DMO maps" actually requires

### 3.1 Asset reality

| Item | Figure | Source / confidence | Consequence for Tamers Lab |
|---|---|---|---|
| Pack01 (models, textures, animations, shared resources) | ≈10 GB for the v487 client, version-specific | `[NOTE §5]` — unverifiable at review | ≈66× the whole release-APK budget (10 GiB ÷ 155.5 MiB); nothing of this ships as-is |
| Pack03 (game tables) | ≈70 MB | `[NOTE §5]` — unverifiable | Parsable and small; the metadata layer an atlas would want — but client extraction is a rights question (§6) |
| Map count | **No usable figure.** The note reports "≈245 maps" and attributes it to Digital Realm (community documentation lists ≈42 named locations / 32 location articles) `[NOTE §5]` `[EXT: S22]` | The note→Digital Realm attribution is `[UNVERIFIED]`; Digital Realm self-describes as a DMO private server `[EXT: S21]` | Plan in the tens, not hundreds; do not use 245 in any estimate |
| Other note figures | 863 Digimon, 29,469 items, 3,845 quests | `[NOTE §5]` — reported as Digital Realm figures, not independently verified | Context only; no product decision depends on them |
| Client engine | Gamebryo 2009–2022, Unity 2022–; a Unity **remaster** was announced in 2022 and was still unreleased as of 2026-08 | `[EXT: S1]` (encyclopedia, medium) | Two or three client generations may exist; formats reset per generation (§3.3) |
| "Gamebryo 2.3" and "v487 source" | Not established by any public primary document | `[NOTE §2]` `[EXT: §3.1]` | Do not plan a converter against "the DMO client" in the abstract |
| Tooling | Community pack/BIN tools; a retail GameKing unpacker; a modern pack tool that does not cover every legacy format | `[NOTE §3]` — unverifiable | Version matching is a permanent tax; unsigned forum binaries are a security problem |
| Scene format | Not documented anywhere accessible | `[NOTE]` `[EXT]` — unknown | The single biggest unknown; Spike B exists to answer it |

### 3.2 What the note cannot answer

Every schedule in §8 is a range until these are answered `[NOTE]`:

| Unknown | Why it matters |
|---|---|
| Map scene format and where zone data lives | Days vs months of extraction work |
| How Pack03 tables map to Pack01 asset folders/sets | A zone can be *named* long before it can be *rendered* |
| Zone boundaries: fields vs instances vs layered interiors | Determines streaming design and content count |
| Lightmap/collision conventions, units, scale | Decides whether converted zones look and behave correctly |
| Whether "maps" means client zones, dungeon variants or private-server rows | Scope can swing by an order of magnitude |
| Licensing of every item above | Universe decision (§6) |

### 3.3 The client-generation problem

Public evidence describes a legacy Gamebryo-era client, a "2.0" client line referenced by a
community Rust server emulator, and an announced Unity remaster `[NOTE §2, §6]` `[EXT: S1,S31]`:

1. **Never estimate "the converter" in the abstract** — each generation resets formats.
2. **A remaster would invalidate an asset pipeline** built on the legacy client `[EST]`.
3. **This is another reason to start with own art**, which is generation-independent.

### 3.4 What the note reports about community extraction

The note reports 2026 community tooling aimed at retail GameKing pack formats — a Python
GameKing Pack01/Pack03 unpacker, a BIN editor reported to support newer retail
GDMO/GameKing BIN files `[NOTE §3.B, §3.D]`, and a modern pack tool that does not cover
every legacy format `[NOTE §3.A]`. These are unverified reports, but they support three
conclusions:

1. **No moat.** A map explorer cannot be differentiated by "having the data".
2. **A leak surface that already exists**, so an internal proposal must not rely on obscurity.
3. **Clean provenance is the durable asset** — it is the first thing examined if the project
   ever grows (§6.1).

---

## 4. What Tamers Lab has — and has never done

Full measurements, line references and method: [recon-technical.md](recon-technical.md).

### 4.1 Proven capability

| Capability | State in repo | Relevance |
|---|---|---|
| Renderer / scene hosts | Filament `1.76.1` + `gltfio`; four scene hosts (`TextureView` + `ModelViewer` + `Choreographer` + frame tiers) `[REPO: recon §1.1–1.2]` | A fifth "place" repeats an existing pattern |
| Scene manifest | `OfflineArenaManifest` — versioned, validated camera/bounds/up-axis contract `[REPO: recon §5]` | Generalises to a **Zone manifest** |
| Proven scene envelope | **Per dimension:** GLB ≤2.76 MiB · 1,590–4,159 triangles · 2–4 embedded PNGs · static, no skins/animations · unlit/emissive materials · whole-file load `[REPO: recon §1.2–1.3, §2.1, §4.1]` | This — not one number — is the budget a v1 zone must hit |
| Theme system | 43 baked PNGs, allowlisted material overrides; unknown material set fails the bind `[REPO: recon §1.4]` | Biome palette exists, but theming multiplies a new map's texture payload unless it opts out |
| Persistence | Room schema **56**, exportSchema, migration tests, checked-in schema JSONs `[REPO: recon §1.5]` | A Mindlink table is a **56 → 57** bump |
| Navigation | Type-safe `AppRoute` + `NavHost` blocks `[REPO: recon §1.5]` | Adding a screen is cheap; adding movement is not |
| Asset budget | Budget ≤155.50 MiB (163,053,568 B); recorded release 142.12 MiB (149,019,234 B); estimated **HEAD headroom ≈10.17 MiB** `[REPO+EST: recon §2.3]` | About one 3–4 MiB own-art scene, not a map |
| Perf budgets | Battle p95 ≤16.7 ms; farm 60/30 fps; no-OOM + 30-min soak; SM-A346M (API 35); minSdk 28 `[REPO: recon §1.7]` | Must be measured; no soak evidence exists yet |
| "World" today | A **GPS radar**: spawns around the real location, biome from OpenStreetMap `[REPO: recon §1.3]` | There is no authored, walkable world in the app |
| Remote content | **None** — no `DownloadManager`, asset packs or remote asset cache; `tools/hatchery_sprites.py:4`: "the app never downloads art" `[REPO: recon §1.6]` | Any download design is a new subsystem plus a server |

### 4.2 What the renderer has never done

The shipped scenes are **baked and unlit**: no Filament lights, no IBL, no shadows, no
lightmaps; ambient occlusion off in all four scene profiles; Colosseum `Ground` is
`KHR_materials_unlit` and the farm is emissive-only `[REPO: recon §1.2, §4.2]`. Loading is
whole-file (≈2× transient), with no streaming, LOD, culling, physics, navmesh or walk
controller `[REPO: recon §1.2]`.

A faithful DMO map therefore needs four capability families, none present:

| # | Missing capability | Needed for |
|---|---|---|
| 1 | NIF/DDS import + lightmap bake + texture pipeline (KTX2/ASTC unproven, no build step) | Real DMO geometry and textures |
| 2 | Lit rendering path (lightmaps or lights/IBL/shadows) | A DMO field map's look |
| 3 | Large-scene memory, streaming, LOD, culling | Zones beyond a handful of meshes |
| 4 | Collision/heightfield, navmesh, player locomotion + free-look | Walking rather than orbiting |

**Implication.** "Rendering is solved" is true only for small, unlit, pre-baked dioramas.
The engineering risk is content acquisition/conversion **plus** four new runtime
capabilities — not the scene host. Good for T1 and a v1 own-art zone; bad for anything that
assumed the viewer is the whole job.

**Texture-memory caution.** The proven decode ceiling is 10–16 MiB per themed scene bind
`[REPO: recon §2.5]`. Four 1024² ARGB_8888 textures already decode to ≈16 MiB before
mipmaps, so the v1 zone should use **≤2 × 1024² or 4 × 512²** (≈4 MiB decoded), not the
naive "≤4 PNG ≤1024²" reading.

### 4.3 Lighting: can the current 3D be made to look lit like DMO?

**Yes — but "turn on a light" would light nothing today, and "like DMO" is a different
technique from dynamic lighting.**

Repo evidence `[REPO]`:

- No `LightManager`, `DirectionalLight`, `IndirectLight`, `shadowOptions` or
  `setShadowingEnabled` call exists anywhere in `app/src/main/java`.
- Ambient occlusion is explicitly disabled in all four scene profiles, with a code comment
  explaining it fringes sprite edges in the radar view — the app has been tuned for cheap,
  predictable mobile frames, not for lit scenes.
- Every bundled GLB carries `NORMAL` and `TEXCOORD_0` (so geometry *could* be lit), but the
  materials are authored unlit: `KHR_materials_unlit` on the Colosseum `Ground`, `radar_plate`
  and `radar_grid`; the Digifarm uses fully baked emissive materials (white emissive, black
  base colour). Unlit materials ignore lights by definition.
- No GLB has `TEXCOORD_1`, so no lightmap UV set exists yet.

Three routes, in increasing cost:

| Route | What it is | Engine work | Look vs DMO | Cost / risk `[EST]` |
|---|---|---|---|---|
| **A. Bake into textures** (recommended for bundles) | Light + AO baked offline into albedo/emissive (or vertex colours — the farm already ships `COLOR_0/COLOR_1`); keep the unlit pipeline | **None** | Closest: DMO is a baked-lit Gamebryo game | Per-map bake 1–3 art-days; zero runtime cost; one extra texture/vertex payload |
| **B. True lightmaps** | UV1 lightmap atlas + custom Filament material (glTF has no core lightmap slot; `occlusionTexture`/UV1 can be repurposed, or a `.filamat` custom material), compressed lightmaps | 2–4 eng-weeks pipeline | Faithful, supports painted shadows/AO | Adds texture memory (1024² RGBA lightmap ≈4 MiB decoded; 2048² ≈16 MiB) and a material pipeline; needs UV1 at export (absent today) |
| **C. Dynamic lights** | `DirectionalLight` + IBL (KTX cubemap via `IndirectLight`) + optional shadow maps; convert materials from unlit to lit PBR | 1–2 eng-weeks | "Modern lit", **not** DMO's baked look on a static map | Mobile perf risk: shadows/IBL are the expensive parts and AO was already disabled; re-baselining ADR 0005 evidence is mandatory |

**Recommendation for one-map-at-a-time bundling: Route A.** DMO's own look comes from
pre-baked lightmaps and vertex lighting, not real-time lights; baking into textures
reproduces that at zero frame cost, keeps memory predictable, and avoids turning a 10–60 MiB
static map into a GPU load test. Use Route C only for small hero scenes where a moving light
adds value (e.g. a dynamic sky), and Route B only if dynamic time-of-day or re-lightable zones
become a product requirement.

**Deciding number for any route:** the converted map's material/UV state — does the GLB carry
lit PBR materials and a lightmap UV set, or unlit materials only? That determines whether the
lighting work is "bake and ship" or "build a pipeline".

---

## 5. Options matrix

**Scoring convention: every column is 1 = worst and 5 = best.** Legal "5" means no further
approval needed; "1–2" means it requires rights the project cannot obtain. Fidelity is
judged only where the option has a meaningful one.

| # | Option | Legal | Tech risk | Content cost | Fidelity | Ship speed | Verdict |
|---|---|---|---|---|---|---|---|
| O0 | **Do nothing / wait for C1** | 5 | 5 | 5 | n/a | 5 | **Scored baseline** — costs only time-to-feedback |
| O1 | Bundle converted DMO maps in the APK | 1 | 1 | 1 | 5 | 1 | **Reject** — redistribution + APK wall |
| O2 | Download DMO-derived zones on demand | 1 | 1 | 2 | 5 | 2 | **Reject without licence** |
| O3 | PC companion reads the user's own DMO install | 2 | 1 | 3 | 4–5 | 1 | **Reject for this project** (EULA + employee + no licence); retain as research note |
| O4 | Original zones from official lore, own art | 4 | 2 | 2 | 3 | 3 | **Recommended pilot**, gated by C1-lite + demand test |
| O4.5 | GameKing-authorised promotional/2D map imagery in an atlas | 3 | 5 | 3 | 4 | 4 | **Ask at C1/C4** — cheapest recognisability path |
| O5 | DMO-inspired reconstruction (untraced layout reference, own art) | 3 | 3 | 3 | 3–4 | 2 | **Optional**, one flagship, only after C3 |
| O6 | Atlas from own lore + factual metadata (provenance-gated) | 4 | 5 | 4 | 1–2 | 5 | **Recommended now** after C1-lite |
| O7 | First-party GameKing product with licensor approval | 4→5 **if granted**, 1 if not | 2 | 2 | 5 | 1 | **Recommended long-term**; start conversations at P0 |
| O8 | Live overlay / client memory reading | 1 | 1 | 1 | 5 | 1 | **Reject** |

Reading the matrix: only O6/O4 sit above the legal gate without a new permission; O4.5 is
the cheapest "I recognise this place" option but needs a marketing/companion permission that
is *more* attainable than the client-asset chain; O3's "Hold" from rev 1 is withdrawn — it
is a rejection for this project, not a deferred option.

### 5.1 Why O3 is not the clean "user-supplied" loophole people expect

The pattern — the tool ships no assets, it reads files the user already installed —
improves posture compared with O2 but is not a free pass:

- It needs the proprietary pack format **and, in practice, its decryption** reverse-engineered
  — commonly prohibited by EULAs and framable as circumvention `[ASSUME][COUNSEL: the DMO
  EULA text was not readable — recon-context gap G2]`.
- Distributed publicly, the tool's **primary purpose is extracting proprietary assets** —
  the fact pattern enforcement targets. The precedents are risk, not licence: Take-Two
  issued a cease-and-desist against OpenIV in 2017 (it later returned after backlash);
  Nintendo's content guidelines permit captures, not extracted assets `[EXT: S25,S26]`.
- Even the format work is unproven: Assimp has no NIF importer, the community NIF tools are
  Bethesda-scoped, and the only public DMO signal reports custom NIF classes (`CsNiNode`)
  `[EXT: §3.2; S20 is snippet-only, low confidence]`.
- It is coupled to the live client, and any anti-cheat surface is a risk even offline `[ASSUME]`.
- For a **GameKing employee**, publishing a DMO asset-extraction tool is an obvious conflict
  of interest whatever its purpose `[EST]` `[ASSUME: depends on contract, gap G8]`.

### 5.2 Why original lore geography (O4) is the pilot

It keeps the experiential core — *a place you can walk around, with your partner, that feels
like Digimon* — while every rendered pixel is the project's own art `[EST]`. It matches the
app's visual language better than photoreal DMO geometry would, it fits the renderer's
proven envelope (§4), and its geography can come from the app's ledger (Iliad, Shambhala,
Witchelny, the Dark Area and Cocytus `[REPO: LORE_FOUNDATION.md:26–37]`).

It does **not** remove franchise-IP questions — names, species and lore are already exposed
throughout the app and need the same review as the rest of it `[REPO: PRODUCT.md:50–58]`
`[COUNSEL]`. What O4 removes is the large, conspicuous, third-party-owned *asset-file* class.

### 5.3 When DMO-inspired layout (O5) is worth the extra risk

O5 buys one thing O4 cannot: recognisability for DMO players ("I know this beach"). It pays
with a legal review (C3) and a content dependency on another game's layouts. Recommendation:
at most **one flagship zone**, only after C1/C3, ideally inside an authorized GameKing
prototype where the result can become O7.

The honest caveat: reproducing a map's layout is a derivative work `[ASSUME][COUNSEL]`. O5
must be a stylised interpretation with original art and a reviewed naming policy, not a
tracing exercise.

### 5.4 Sequencing: the pitch is not a reward

Rev 1 put the first-party pitch after a 6–8 week pilot. That is backwards: C1 and the
rights chain are the critical path, and the pitch is the only route to the requester's actual
goal. **Start the internal conversation and the C4 path at P0**, with a 4-week deadline to
learn whether real-asset work is possible at all; the pilot proceeds in parallel and does
not depend on the answer.

---

## 6. Rights, compliance and the employee dimension (decisive)

*None of this is legal advice; every conclusion carries `[COUNSEL]`. See §6.5 for the inputs
a lawyer will need.*

### 6.1 Universe A — the community/private-server ecosystem (the research note)

The note's own warnings `[NOTE: provenance note, §1.B, §7]`:

- Some bundles are **described by their distributors as leaked or stolen**; provenance and
  redistribution rights are questionable.
- A 2024 "complete files" bundle was **alleged** to be staff-stolen and bought over Discord
  — an allegation, not independently verified.
- The public v487 client source, pack/BIN tools and extracted-resource shares are
  redistribution-unlicensed.
- The ecosystem's purpose is largely to run **private servers**.

Two external findings sharpen the risk picture `[EXT]`:

1. **The operator actively polices private servers.** Notices call them "strictly an ILLEGAL
   private server" and describe legal measures to shut them down (2020-12-17), and announce
   strengthened monitoring, evidence collection and "cooperation with relevant platforms to
   identify, report, and shut down these illegal services"; use and **promotion** are
   prohibited, and accounts may be penalised (2025-05-27) `[EXT: S4,S5]`. (The 2020 notice's
   explicit ban language relates to fraud-linked accounts; do not overstate it.)
2. **The note's own data reference is one of them.** Digital Realm — the site the note names
   for "≈245 maps / 863 Digimon / 29,469 items" `[NOTE §5]` — **self-describes as a DMO
   private server** `[EXT: S21]`. The note→site attribution remains `[UNVERIFIED]`, but the
   site's own description is enough to disqualify its figures for planning, and to treat the
   note's §5 recommendation with the same caution as its §1.

For the requester, this is a **conflict and provenance risk that needs written clearance**,
not a question of taste `[ASSUME][COUNSEL]`:

- **Employment exposure** — asset handling, NDA and external-project rules; using leaked
  material may be misconduct even for a personal deliverable (`[ASSUME → confirm, gap G8]`).
- **Provenance taint** — code or data derived from leaked source can poison a later official
  product during IP diligence and is hard to remove once integrated `[EST]`.
- **Security exposure** — unsigned forum binaries; the note itself says not to run unknown
  executables on a primary machine `[NOTE: security reminder]`.
- **Reputational exposure** — an employee-adjacent release of DMO-derived work is a story the
  community and licensor will notice `[EST]`.

**Do-not list** (a hard project rule, written into the repo): no server/client bundles or
private-server datasets; no leaked client source, even "for study"; no repacking retail
packs into a published app; no community binaries on the build machine; no assets committed
to this repo or its history.

### 6.2 Universe B — GameKing's licensed DMO assets

The rights chain is a **licence chain, not a GameKing-owned asset pool** `[EXT: recon-context
§1.1–1.2]`:

- DMO's global and Korean footers credit **"© Akiyoshi Hongo, Toei Animation © 2009
  MOVEINTERACTIVE Co., Ltd."** `[EXT: S2,S6]`.
- A 2015 official notice states Joymax transferred DMO **operation and customer support** to
  Movegames Inc., and that "platform and license **will be** transferred to Movegames after
  the operation and customer support transfer has been completed" — whether that completed
  is not public `[EXT: S3]`.
- Bandai Namco Korea's own site lists DMO among the Digimon-IP games it develops/services
  `[EXT: S7]`; the official Digimon game portal is a Bandai Namco Entertainment property
  `[EXT: S29]`.
- GameKing is the **operating portal** for the global service `[USER]` `[EXT: S8,S9]`.
- Public evidence does **not** show GameKing owning reusable rights to client art, and the
  exact contracting entity among Move Games / Movegames Inc. / MOVEINTERACTIVE / Move
  Interactive is unresolved `[EXT: gap G1]`.

**Finding (promoted from assumption): an employment contract is a duty relationship with one
licensee, not a licence from the IP holders or the asset-owning developer** `[ASSUME]`
`[COUNSEL]`. Asset reuse outside the licensed service is a new derivative work requiring the
rights holders' written permission, typically with restrictions `[ASSUME]`. An internal
prototype may be allowed by company policy but still needs asset-handling authorization, and
its output cannot simply be published `[ASSUME]`.

### 6.3 Universe C — the user's own copy (O3)

See §5.1. A distinct act with a better posture, but not permission, and still a conflict if
published by an employee.

### 6.4 The third-party app's own rights posture and store publishing

Tamers Lab already lives with IP risk: a Digimon fan app bundling user-imported card data, a
credited sprite library and a lore ledger sourced from official pages `[REPO: PRODUCT.md:50,
LORE_FOUNDATION.md]`. Adding real DMO assets would add a large, conspicuously-owned asset
class and move the project toward "game content redistribution" `[EST]`. Publisher
fan-content policies in this space permit captures, not assets `[EXT: S26]` — and that is a
publisher policy, **not** a store policy. **Store policy (Google Play IP/DMCA process,
listing metadata, screenshots, trademark use of "Digimon"/"Mindlink") has not been analysed
and needs its own review** `[COUNSEL]`.

### 6.5 Legal inputs a lawyer will need (none analysed here)

| Input | Status |
|---|---|
| Copyright / derivative-work position for own-art interpretations of named places | `[COUNSEL]` |
| DMO EULA/ToS on extraction, reverse engineering, jurisdiction | Not read (recon-context gap G2) `[COUNSEL]` |
| Contract chain: Toei/Hongo ↔ Bandai Namco ↔ Move Interactive/Movegames ↔ GameKing; which entity signs what | Unresolved (G1) `[COUNSEL]` |
| Employment contract: NDA, IP assignment, external-project and declaration rules | Unknown (G8) `[COUNSEL]` |
| Jurisdiction(s) | Unknown — affects copyright exceptions, database rights, enforcement |
| Trademark: "Mindlink", "Digimon", "DMO", store listing, in-app branding | Not analysed `[COUNSEL]` |
| Wiki licence / CC-BY-SA / ShareAlike and database rights for atlas metadata | Not analysed (F2) `[COUNSEL]` |
| Interoperability/reverse-engineering exceptions | Jurisdiction-dependent `[COUNSEL]` |
| Store policy and takedown process | Not analysed `[COUNSEL]` |

### 6.6 Compliance gates to require, in writing

1. **C1 — Universe decision:** which assets may this project use, and **which entity** may
   answer? Own art only is the default and needs no further permission; internal-asset and
   user-supplied routes need written clearance. No DMO file enters the repo before C1.
2. **C1-lite — Atlas provenance decision:** a written rule for every T1 field (source,
   licence, attribution, database-rights position) before the atlas is published (§8.1).
3. **C2 — Clean-room log:** every asset, document and binary used is recorded with source
   and permission.
4. **C3 — Legal review of O5** (DMO-inspired reconstruction) and O4.5 (promotional assets):
   is the derived layout/imagery acceptable, and under what attribution?
5. **C4 — Licensor flow-down** for O7/T3: which client generation, which entity signs.
6. **C5 — Publication review:** store listing, trademarks, attribution page, takedown owner.

---

## 7. Architecture

### 7.1 What a v1 own-art zone looks like (fits today's repo)

Scope it as **one authored, own-art scene per release, inside the proven envelope** — not a
DMO importer `[REPO: recon §7]`:

- **Packages** (no new Gradle module; `:app` already owns scenes):
  `world/mindlink/MindlinkZoneManifest.kt` (schemaVersion, assetPath, bounds, spawn, POIs,
  camera, material allowlist, validated like `OfflineArenaManifest`);
  `world/ui/mindlink/MindlinkSceneViewport.kt` (copy the radar-iso scene-host skeleton, add a
  `HybridSceneKind.MINDLINK` profile); `MindlinkScreen.kt` + `AppRoute.Mindlink` + one
  `NavHost` block; `MindlinkSchema` raw-SQL object + `AppDatabase` **57** + migration test +
  schema JSON.
- **Content:** `assets/mindlink/<zoneId>/zone.glb` + `zone.json`, authored offline by a
  `tools/mindlink/` Blender script with a `--check` validator and recorded hashes — the
  established `tools/build_environment_themes.py` pattern `[REPO]`.
- **Budgets:** GLB ≤2.8 MiB; ≤2×1024² or 4×512² textures (≈4 MiB decoded); ≤~5k triangles;
  unlit/baked materials; APK delta ≤4 MiB; no OOM in a 30-minute soak; cold load ≤8 s
  `[REPO: recon §7]` `[EST]`.
- **Out of scope for v1:** KTX2, runtime lights/IBL, streaming, navmesh, on-device download,
  DMO format import, more than one bundled zone, first-person radar promotion.
- **Provenance in UI:** each atlas row carries a visible source label (official / app lore /
  community) so the "not a datamining surface" promise is checkable.

### 7.2 What a faithful path would add

Beyond per-zone content, it needs the four capability families from §4.2 plus a pipeline:

```
authorized source (version-pinned)
   ↓ extract (VM; hash-logged) → scene units: geometry, materials, lightmaps, collision, spawns
   ↓ convert: NIF→glTF (custom dialect work likely), DDS→mobile textures, decimate/LOD
   ↓ author: camera bounds, POIs, collision proxy, walkable graph, ambience, metadata
   ↓ bake: lighting strategy for a new lit or baked pipeline
zone bundle (manifest + GLB + textures) ──► delivery decision: bundle ≤4 MiB or new download system
```

Runtime additions: lit/baked-lighting strategy, streaming/LOD, collision/heightfield,
navmesh/walkable graph, locomotion + free-look, content delivery, content QA gate
`[REPO: recon §6]`. This is a program; its first deliverable is a feasibility decision, not a
zone.

### 7.3 Non-goals

No combat, no NPC AI, no netcode, no anti-cheat interaction, no second game client, no
data-mining surface.

---

## 8. Product viability and scope economics

### 8.1 T1 sourcing — the load-bearing legal premise

T1 is the only work recommended **now, without new permission**, so its sourcing must be
explicit. Rev 1's "maps are facts, no extraction needed" was too loose; this is the
replacement:

| T1 field | Source | Obligation / status |
|---|---|---|
| Zone names and links | App's lore ledger + official sources + a factual community list `[EXT: S22]` | Names/links are likely non-copyrightable `[ASSUME][COUNSEL]`; compilation/database rights possible; credit the community source if used |
| Landmark/orientation notes | **App's own writing**, from official lore | No copied wiki prose; rewrite every row |
| Spawns / monster lists | **Deferred** — would be client- or community-database-derived | Move under C1; not in v1 |
| Map images/thumbnails | **Own art only** | No wiki map images (their provenance is unstated) |
| Partner lines/reactions | App systems | Already covered by app lore rules |
| Link-stability limit | Official Seekers material, paraphrased `[EXT: S10]` | Short attributed paraphrase only |

Publication of the atlas is gated on **C1-lite**: a written "these fields, these sources,
this attribution" decision. Until then the atlas stays internal/behind a flag.

**Demand test first.** Before art spend, a 1-week static/2D concept (5–10 target users)
answers the real question: does "lore places + partner" substitute for DMO nostalgia? If
not, the honest answer is that only O7 delivers the request, and the atlas is a different
feature.

### 8.2 Canonical effort and cost model

All numbers `[EST]`, in engineer-weeks (eng-w) unless noted. This table is the single source
for every effort figure in this document.

| Work item | First time | Repeats | Includes |
|---|---|---|---|
| P1a static/2D demand test | 1 eng-w + design days | — | Concept, 5–10 interviews, write-up |
| T1 atlas (full) | 2–3 eng-w total | — | P1a + catalogue, provenance labels, partner reactions, Room 57 |
| T1+ isometric data-space | +1–2 eng-w | — | Iso only; first-person promotion not included |
| Own-art zone, orbit/POI (T2 v1) | 3–5 eng-w + 10–20 artist-days | 2–3 eng-w each | GLB, manifest, viewport, POIs, persistence |
| Free walking add-on | +2–3 eng-w | — | Collision/heightfield, navmesh, locomotion, camera |
| Faithful capability program | 10–14 eng-w | — | Lit/bake path, streaming/LOD, collision/nav/locomotion, delivery, **conversion tooling (2–4)** |
| Per faithful zone after program | — | 2.5–5.5 eng-w each | Cleanup, authoring, QA |
| **First faithful zone, all-in** | **13–20 eng-w** | — | Program + zone |
| 6 faithful zones | — | ≈25–48 eng-w | Program + 6 zones |
| 12 faithful zones | — | ≈40–80 eng-w | Program + 12 zones |

Resourcing and cost (placeholders — rates/availability unknown, get quotes):

| Resource | Where needed | Estimate |
|---|---|---|
| Engineer | All phases | 1 FTE-part-time; capability program is the long pole |
| 3D/2D artist | T2, O4.5, T3 content | 10–20 artist-days per zone; availability is a project risk |
| Counsel | C1, C1-lite, C3, trademark/store review | Hours, not weeks — but must be budgeted before P2/T3 spend |
| Device lab | Spikes A/B | SM-A346M already used for budgets `[REPO]` |
| **Spend cap / stop date** | Decided in the P0 memo | e.g. cap the pre-C1 work at the demand test + atlas (≤4 eng-w) and stop for a C1 answer `[EST]` |

### 8.3 KPIs, leading indicators and kill criteria

**Success (6 months):** atlas opt-in ≥15% of monthly active users; ≥2 sessions/user/month;
median session ≥3 min; crash-free ≥99.5% for the feature; install-size delta within budget;
**quarterly IP-complaint review recorded** (replacing the unmeasurable "zero complaints")
`[EST]`.

**Leading indicator:** demand-test conversion (concept judged worth building by ≥6/10
target users) before art spend.

**Kill/park:** no C1 → atlas only; demand test fails → do not fund 3D, escalate O7 instead;
zone misses frame/size/OOM gates → cut scene complexity, not add capabilities; faithful
spike fails conversion or performance → park T3; any licensing objection → stop and remove
derived material.

---

## 9. Risk register

**L** = likelihood (Low/Medium/High), **I** = impact (Low/Medium/High/Critical).

| # | Risk | L | I | Mitigation / owner |
|---|---|---|---|---|
| 1 | No authorization for DMO assets; cannot be self-granted | High | Critical | C1; default O6/O4; owner: requester + company counsel |
| 2 | Assets derived from leaked/private-server material | Medium | Critical | Do-not list §6.1; C2 log; no bundle/private-server downloads |
| 3 | Employment/NDA/declaration breach | Medium | High | Written HR/legal clearance (G8); declare project before spend |
| 4 | EULA/ToS breach via pack reverse engineering (O3) | Medium | High | Reject O3; keep research inside company authorization |
| 5 | Operator anti-private-server enforcement | Medium | High | No ecosystem contact; explicit provenance statement |
| 6 | Anti-cheat / client-integrity entanglement | Low–Med | High | No runtime link/overlay; offline read-only at most |
| 7 | Supply-chain malware from community tools | Medium | High | No unsigned binaries; VM; source-only; hashes |
| 8 | DMO map format/NIF dialect unknown; converter unproven | High | High | Spike B; format investigation before estimation |
| 9 | Client-generation ambiguity (legacy/2.0/Unity remaster) | High | High | Never estimate "the converter"; own art is generation-independent |
| 10 | Renderer never lit; KTX2 unproven; texture memory at ceiling | High | Medium | v1 baked/unlit within envelope; prove lit path only in program |
| 11 | APK/install size blowout (~10.17 MiB HEAD headroom) | High | Medium | ≤4 MiB bundled zone; CI size gate; App Bundle basis change needs owner approval |
| 12 | Mobile perf/OOM on larger scenes | Medium | High | Triangle/texture budgets; streaming/LOD before scaling; SM-A346M; 30-min soak |
| 13 | Content scale underestimated | High | High | Curated 1–3 zones; per-zone budget; Gate 2 |
| 14 | Maintenance coupling to another game's updates/remaster | Medium | Medium | Versioned manifest + pipeline; own art avoids it |
| 15 | Unreleased-content leakage / datamining optics | Medium | High | Published places only; provenance labels; no datamined tables |
| 16 | Community/PR backlash (private-server adjacency) | Medium | Medium | Public provenance statement; no repacked assets |
| 17 | Store takedown / IP complaint / trademark issue | Low–Med | Critical | C5 publication review; takedown owner; contingency |
| 18 | Scope creep into a second game client | High | Medium | Non-goals §7.3; T4 rejected |
| 19 | Substitute fails demand (no DMO nostalgia) | Medium | High | 1-week demand test; escalate O7 if it fails |
| 20 | Artist availability / art cost overrun | Medium | Medium | Prototype with placeholders; cap artist-days per zone |
| 21 | Wiki licence / database-rights problem in atlas metadata | Medium | Medium | C1-lite; own writing; attribution; counsel |
| 22 | Employer instructs project to stop | Low–Med | High | Declare early; define unwind (remove derived material, keep own-art work) |

---

## 10. Recommended plan with gates

Every gate has a named **owner**, an **adjudicator who is not the author**, and a recorded
date/re-test; a gate passed solo by the feature's author does not count. "Credible fix"
means a written plan with a named owner and a re-test date — not a judgement call.

### 10.1 Spike A — own-art capability (no authorization needed, start now)

| ID | Test | Pass criterion |
|---|---|---|
| A1 | Author one own-art zone (Blender → GLB + manifest) with `tools/mindlink/ --check` | Reproducible bytes from recorded hashes; no external binaries |
| A2 | Render in a Mindlink prototype screen on SM-A346M | No fatal material/geometry artifact |
| A3 | Size | GLB ≤2.8 MiB; ≤2×1024² or 4×512² textures; APK delta ≤4 MiB |
| A4a | Idle frame | 30 fps idle tier respected; p95 frame ≤33.3 ms |
| A4b | Interactive frame | 60 fps interactive target; p95 frame ≤16.7 ms, or a written downgrade decision |
| A5 | Memory/stability | 30-min soak, no OOM, PSS delta <100 MiB; cold load ≤8 s |
| A6 | Interaction | POI selection, camera bounds respected, partner token projects correctly |

Fail on A3/A4/A5 → simplify the scene, not the ambition; do not add renderer capabilities to
rescue it.

### 10.2 Spike B — faithful-zone feasibility (only with an authorized source and C1)

| ID | Test | Pass criterion |
|---|---|---|
| B0 | Name the client generation and pin the build | One generation, one build id, documented |
| B1 | Extract one field zone; enumerate scene units | Repeatable procedure; no undocumented manual step |
| B2 | Convert to a runtime GLB; blind recognisability test | ≥5 blind reviewers vs fixed reference set; two-part pass: recognisable **and** not a reproduction |
| B3 | Size after cleanup/LOD | ≤8 MiB GLB; packaged bundle documented as *download*, not a bundle |
| B4 | Performance and memory | Walking p95 ≤33.3 ms; ≤100 MiB PSS delta; no OOM in soak |
| B5 | Pipeline reproducibility and provenance | Second person regenerates; zero external binaries; C2 log complete |
| B6 | Delivery decision | Written bundle-vs-download comparison with measured APK/AAB sizes and an owner decision |

Kill rule: B2/B3/B4 failing without a credible fix parks the faithful route and keeps
Mindlink at the atlas/own-art tier.

### 10.3 Phased plan

| Phase | Duration `[EST]` | Ships | Gate / adjudicator |
|---|---|---|---|
| **P0 — Decisions** | 1 week | Written C1 (universe, entity, generation); HR/legal declaration; do-not list committed; C4 pitch started with a 4-week deadline; spend cap fixed | C1 — company counsel + requester's manager |
| **P1a — Demand test** | 1 week | Static/2D atlas concept to 5–10 target users; DMO vs lore-positioning answer | Product owner (not the author) |
| **P1 — Atlas** | 2–3 weeks | T1 with provenance labels, par­tner reactions, Room 57, opt-in entry | C1-lite + product review + KPI baseline |
| **P2 — Own-art zone** | 3–5 weeks (+2–3 walking) | One explorable zone in the proven envelope | Spike A + recognisability review |
| **P3 — Faithful decision** | 2–3 weeks, only if C1 allows | Spike B evidence pack | Spike B gate |
| **P4 — Own-art set** | 7–11 weeks | 3 zones, links between them, atlas integration | Gate 2: content budget + rights owner |
| **P5 — First-party escalation** | parallel from P0 | Internal proposal + C4/licensor path | Business approval + licensor terms |

**Fallbacks.** C1 negative → P1a/P1/P4 only; the atlas and own-art places are the feature. Spike
B fails → close the faithful question on evidence. Unity remaster ships → re-decide the
faithful target; own-art unaffected.

### 10.4 The next three actions

1. Write the **Universe decision memo** (one page): permitted universe, the employing legal
   entity, which client generation if any, **and the spend cap/stop date**. Only blocking item.
2. Commit the **do-not list** to `CONTRIBUTING.md`.
3. Run **P1a** and start **P0's C1/C4 conversations** — both are cheap and unblock everything.

---

## 11. Decision asks (for the requester)

1. **Which universe is permitted** — own art only (default, no new permission), GameKing
   internal assets under written authorization, or no project? Who signs? *(Blocking.)*
   Note: "user-supplied at runtime" is **not** offered as an approvable universe — §5.1/§6.3
   explain why.
2. **Which legal entity employs you**, and is it the DMO operating/asset entity? Does HR/legal
   permit the unofficial project, and is there a declaration duty?
3. Is this a **personal community feature** or an **internal GameKing prototype**? The rules
   and what may be published differ.
4. Is the requirement literally **"DMO's maps"**, or **"recognisable Digital World places the
   user can explore"**? Only the first needs real DMO assets and the faithful program; the
   demand test must answer which the users actually want.
5. If real assets are wanted: **which client generation**, and who inside GameKing can
   authorize an extraction spike? Start this conversation now (C4), with a deadline.
6. Accept **one curated own-art zone** as the unit of decision, and a rebuilt HEAD release APK
   as the size measurement?
7. If a bundled zone cannot fit: does the owner approve **changing the app's delivery basis**
   (App Bundle / per-ABI, ≈21.56 MiB install relief) — noting it changes the budget basis and
   needs approval — or should content be **downloaded through a new capability**? These are
   two separate decisions.
8. Who owns the **content/art budget**, accepts **residual franchise-IP risk**, approves the
   **spend cap and stop date**, and signs the publication/trademark review?

---

## Appendix A — Evidence provenance and limitations

| Claim class | Source | Trust |
|---|---|---|
| Pack sizes, tooling reports, ecosystem warnings, note figures (245 maps / 863 Digimon / 29,469 items / 3,845 quests), engine names | Supplied note, **not attached to this repo** (path in the requester's Downloads; received 2026-11-14) | `[NOTE]` — **unverifiable at review**; used only to size the problem |
| Digital Realm being a private server; operator notices; rights chain; NIF tooling scopes; enforcement precedents; Mindlink lore; zone list; wiki licence | External research, 31 sources `[S#]` | `[EXT]` — per-claim confidence in [recon-context.md](recon-context.md) §1–§9 |
| Filament, scene envelope, asset/APK measurements, Room schema, routes, missing capabilities, perf budgets | This repository, measured/read | `[REPO]` — line references in [recon-technical.md](recon-technical.md) |
| Effort, size, perf, cost and artist-day ranges | Author's estimates anchored to repo budgets | `[EST]` — planning ranges |
| Employment, approval and derivative-work positions | Inference, not verified | `[ASSUME]` `[COUNSEL]` — confirmed only by C1/C3/G1/G8 |

**Known limitations.** The note cannot be opened by a reader of this repo; if this analysis
goes to a manager or counsel, attach a redacted source log (title, date, author, exact URLs)
rather than the note itself. No DMO artifact was inspected by anyone on this team. No release
APK was rebuilt; headroom is an estimate. No device FPS/OOM/soak has ever been recorded for a
map-sized scene. No counsel has reviewed this document.

## Appendix B — Questions a rights check must answer

1. Who owns the DMO client assets and who can license their reuse? (Toei Animation/Hongo,
   Bandai Namco, Move Interactive/Movegames — which entity signs?)
2. What does the operating licence allow outside the service, under what terms?
3. Employee handbook/NDA: external projects, asset handling, declaration duty, IP assignment.
4. Are map names, layouts and spawn tables protectable — and is a stylised reconstruction
   acceptable? Does a database/compilation right apply to an atlas built from metadata?
5. What attribution/notice policy applies to DMO-derived material?
6. Is there an internal precedent for a companion/second-screen feature, and who owns it?
7. Which DMO client generation is current, and is the Unity remaster still planned?
8. Which jurisdiction(s) govern? Are there interoperability exceptions relevant to O3?
9. Trademark: can the app use "Mindlink" in UI copy and store listing without implying
   official affiliation?
10. Store policy and takedown process for the target store.

## Appendix C — Glossary

- **DMO** — Digimon Masters Online, the MMORPG whose maps are the candidate asset source.
- **Pack01 / Pack03** — DMO client archives: 3D/shared resources vs game tables `[NOTE]`.
- **Mindlink** — official Seekers technology transferring a Linker user's consciousness into a
  DigiCore, with a stage-dependent time limit and DMIA as its failure state `[EXT: S10]`.
- **Zone** — one explorable place here: manifest + geometry + textures.
- **T1–T4 / O1–O8 / Universe A–C** — the ladders in §2, §5, §6.
- **HEAD headroom** — estimated remaining release-APK budget after un-built asset growth
  (≈10.17 MiB); an estimate, not a recorded figure `[REPO+EST]`.

## Appendix D — Open technical unknowns

1. DMO map scene format, where zone data lives, NIF/DDS conversion feasibility (Assimp has no
   NIF importer; custom NIF classes reported).
2. Which DMO client generation "maps" would mean, and whether the Unity remaster resets it.
3. Whether an own-art zone can hold idle 30 / interactive 60 fps and the 30-minute soak on
   SM-A346M.
4. Whether a manifest-driven scene host can reuse the radar skeleton without forking it.
5. Whether KTX2 works on Filament 1.76.1/gltfio on device (repo audit says unproven).
6. What delivery model is acceptable: bundled ≤4 MiB zone, App Bundle basis change, or a new
   download capability.
7. Whether the theme catalog must cover a new zone (texture payload × theme count) or the zone
   opts out.

## Appendix E — Candidate places for a vertical slice

Public community documentation lists ≈42 named locations `[EXT: S22]`; selection judgement is
the author's `[EST]`. **None of these is in the app's current lore ledger** (which covers the
Digital World's realms, not DMO zones) — every one would need new sourcing under C1-lite.
Names ≠ asset availability, and for the own-art route these are *references*, each needing the
same review as any other Digimon reference.

| # | Place | Why it is a good slice | Source status |
|---|---|---|---|
| 1 | Yokohama Village | The real-world starting hub most DMO players saw first | New sourcing needed |
| 2 | DATS Center | Franchise-iconic agency HQ and the game's home base | New sourcing |
| 3 | D-Terminal | Transit hub between the real and digital worlds | New sourcing |
| 4 | File Island — Village of the Beginning | Franchise-iconic entry point; compact | Franchise lore + new sourcing |
| 5 | File Island — Silver Lake | Classic field area with a strong silhouette | Franchise lore + new sourcing |
| 6 | File Island — Infinite Mountain | Signature vertical space; tests camera/bounds | New sourcing |
| 7 | Server Continent — Desert | Core Adventure-era geography; cheap to build | Franchise lore + new sourcing |
| 8 | Digimon Maze (F1–F4 / B1–B2) | Signature multi-floor complex; nav showcase | New sourcing |
| 9 | Ice Area — Snowstorm Village / Frozen Ground | Most visually distinct biome; the "wow" frame | New sourcing |
| 10 | Dark Tower Wasteland + Kaiser's Laboratory | Adventure 02 iconography; ruin + interior pair | New sourcing |

For the own-art route the strongest first two are **File Island — Silver Lake** (open field,
readable, fits a small triangle budget) and **Yokohama Village** (hub, recognisable, cheap
geometry) — subject to the sourcing table above `[EST]`.

## Appendix G — Bundling feasibility (rev 3 addendum, 2026-11-14)

**Question narrowed by the requester:** the rights question is treated as settled; can the
**existing DMO maps be bundled inside the app?**

**Short answer: not "the existing maps".** Under the project's current release gate there is
about **10 MiB** of APK headroom, and bundled content costs ≈1:1 in APK bytes. That is room
for roughly **two or three micro-scenes in the proven class (3–4 MiB each)** — not for one
faithful DMO map, whose converted size is estimated at **15–60 MiB** `[EST]`, from a raw
average of ≈40 MiB per map (10 GiB ÷ ~245 maps `[NOTE][ASSUME]`).

| Line | Value |
|---|---|
| Release APK budget (ADR 0005) | 155.50 MiB / 163,053,568 B |
| Last recorded release APK | 142.12 MiB / 149,019,234 B |
| Recorded headroom | 13.38 MiB |
| Already-authored, unbuilt asset growth | ≈ +3.21 MiB compressed |
| **Estimated HEAD headroom** | **≈10.17 MiB** `[REPO+EST]` |
| Bundled-content cost | ≈1:1 APK bytes (measured: GLB 96.3 %, PNG 99.86 % of raw in APK) |
| Proven scene class | ≤2.76 MiB GLB · 1,590–4,159 tris · 2–4 textures · unlit |
| **Max bundleable own-art micro-scenes** | **2–3** (3–4 MiB each) |
| One converted DMO map (estimate) | **15–60 MiB** → 1.5–6× the headroom `[EST]` |
| Six converted maps | ≈90–360 MiB → 9–35× headroom; 60–230 % of the entire current app `[EST]` |
| ~42 documented locations | Not bundleable at any realistic fidelity |

**What would have to be true to bundle one real map.** The map would have to be *re-authored*
for mobile rather than merely converted: heavy decimation, 512–1024 textures with a new
compression pipeline (KTX2 unproven here), lighting baked into unlit/emissive materials,
props and animated elements stripped, and LODs authored. At that point the result is a
**new mobile-authored version of the map**, and even then it only fits if it lands under the
~10 MiB line — which is a small zone, not a field map.

**Non-size blockers that bundling does not solve** (from §4.2):

- Whole-file GLB load (`ByteArray` → direct `ByteBuffer`, ≈2× transient) means a 30–60 MiB
  map risks OOM on the reference device; streaming/chunked loading does not exist.
- The renderer has never rendered lit/lightmapped content; DMO maps are baked-lit scenes.
- There is no collision, navmesh or locomotion, so a bundled map is an **orbit diorama**, not
  something the user explores, until that work is done (+2–3 eng-weeks for basic walking).
- The theme catalog multiplies a new scene's texture payload by the four themes unless the
  zone opts out.
- Every bundled map ships with an app release, so content iteration and map fixes cost
  store submissions.

**Delivery routes, if the goal is real DMO maps:**

| Route | APK gate | User experience | Work `[EST]` |
|---|---|---|---|
| A. Bundle inside the APK | **Fails** for real maps; fits 2–3 micro-scenes | Offline immediately | 3–5 eng-wks per authored scene |
| B. Play Asset Delivery — **install-time** pack | Leaves the APK budget; counts in download size | Installed with the app, offline | 2–4 eng-wks infrastructure + conversion + capability program |
| C. Play Asset Delivery — **on-demand** packs | Leaves the APK budget | Optional per-map download, offline after fetch | Same infrastructure + storage/version management |
| D. Bespoke CDN download | Leaves the APK budget | Custom flow, own hosting | 4–6 eng-wks + hosting/ops |
| E. App Bundle / per-ABI split only | Frees ≈21.56 MiB of install (drops 3 ABIs) | No user-facing change | Budget-basis change needs owner approval; fits ~1 small map at most |

**Recommendation.** If "bundled" means "works offline out of the box", **B (install-time
asset pack)** is the standard Android answer, keeps the APK gate intact and is the only
delivery route worth designing for. If it means "inside the APK file", the answer is **no**
for real maps. Any switch to AAB/asset packs also changes the ADR 0005 budget basis
(universal APK ≤155.5 MiB) and therefore needs owner approval `[REPO]`.

**The one number that decides this:** the converted size of one map (GLB + textures, in MiB)
and how many maps are wanted. With those two figures, fit is arithmetic; until then all
per-map sizes here are estimates.

## Appendix F — Revision and review log

| Rev | Date | Change |
|---|---|---|
| 1 | 2026-11-14 | Initial analysis, unvalidated |
| 2 | 2026-11-14 | Post-review: fixed note/provenance chain and 245 status (F1); added T1 sourcing table + C1-lite (F2); recalibrated legal claims, exact S3/S4/S5 wording, added legal-inputs table (F3); rescored matrix with O0/O4.5 and fixed legend (F4, F8); canonical effort/cost model (F5, F10); downgraded novelty/substitution claims and added demand test and Lorebook positioning (F6, F7); moved first-party pitch to P0 and added promo-asset option (F8); gate owners/adjudicators, split frame gates, defined recognisability protocol (F9); stated no-counsel status and added spend cap/unwind (F10); fixed tags, envelopes, texture ceiling, DMIA guardrails and provenance labels (F11–F14) |
| 3 | 2026-11-14 | Added Appendix G (bundling feasibility): APK headroom math, per-map size estimates, non-size blockers, delivery routes (APK vs Play Asset Delivery vs App Bundle) and the one number that decides it |
| 4 | 2026-11-14 | Added §4.3 (lighting): repo evidence that all bundled materials are unlit, and the three routes (bake / lightmap / dynamic) with costs |

Reviewed independently in [REVIEW.md](REVIEW.md) (verdict: accept-with-changes; the findings
above are addressed in this revision). Review is not legal review.
