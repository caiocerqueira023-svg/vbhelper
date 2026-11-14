# External context recon — DMO maps × "Mindlink"

**Scope:** external facts the viability analysis (`README.md` in this folder) needs: DMO
corporate/licensing chain, zone inventory, engine/format facts, enforcement precedents,
official Mindlink lore, companion-app precedent, vertical-slice candidates and evidence gaps.

| Field | Value |
|---|---|
| Date of access for **every** source below | **2026-11-14** |
| Method | Public web pages and public repositories, fetched during this pass. No leaked/private-server bundle was downloaded, opened or relied on. |
| Confidence legend | **high** = primary/official source states it; **medium** = reputable secondary or explicit but unofficial source; **low** = single community source, weak provenance; **UNVERIFIED** = not established, or the page could not be read. |
| Citation convention | `[S#]` resolves in the source ledger (§8), where each entry carries URL, access date and confidence. Claims carry their confidence inline. A figure in `code style` is a label, not a verified number. |

**Provenance firewall.** Two source classes are quarantined in this note:
(i) the "Digital Realm" site named as the origin of the `~245 maps` figure self-describes as a
**DMO private server companion** `[S21]`; (ii) the leaked "v487 client source" appears only in
leaked-bundle distribution contexts `[S20]`. Neither is used as evidence of technical fact here;
they are recorded only as provenance warnings (see §7, gaps G3/G5/G6).

---

## 1. Corporate and licensing chain

### 1.1 Who makes/operates DMO

| Claim | Evidence | Confidence |
|---|---|---|
| *Digimon Masters* ("DMO") is a free-to-play Digimon MMORPG; KR launch **2009-10-30**, global launch **2011-10-20**; developer "movegames"; publishers movegames, Joymax (English, 2011–2016) and GameKing (English, 2016–present) | Wikipedia infobox/prose `[S1]` | medium |
| Korean official site says the game's operating entity is **㈜무브인터렉티브 = Move Interactive Co., Ltd.** (Seoul, Mapo-gu; CEO Kim Dong-seong; business reg. 314-81-48576; game classification 2010-09-03, no. CC-NP-100903-027) | KR official DMO footer `[S6]` | high |
| The global site's footer credits **"© Akiyoshi Hongo, Toei Animation © 2009 MOVEINTERACTIVE Co., Ltd. All Rights Reserved"**, links moveint.io, Toei and the Bandai logo | Global DMO footer `[S2]` | high |
| Joint 2025-05-27 notice was issued by "Dats Center" on the global site, i.e. the operator speaks through the GameKing-hosted DMO site | `[S4]` | high |
| The global portal **GameKing** lists DMO and Digimon RPG, and its own footer carries the same **MOVEINTERACTIVE** copyright and the moveint.io / Toei / Bandai links | `[S8]` | high (page); medium (interpretation) |
| 2015-08-07 notice: Joymax transferred DMO **operation and customer support to Movegames Inc.** (341 Gangnam-daero, Seocho-gu, Seoul) on 2015-08-11; *"Platform and license will be transferred to Movegames after the operation and customer support transfer has been completed."* | Official notice `[S3]` | high |
| Steam release: DMO launched on Steam 2016-11-01 with a new server for "Gameking and Steam users"; the game had been "up and running through publisher Gameking for years"; global version lagged the Korean version | Trade press `[S9]` | medium |
| **Bandai Namco Korea's own business page** lists 디지몬 마스터즈 among the Digimon-IP games it develops/services and says its Digimon online business started in 2003 (Digimon RPG; DMO "service began November 2009" — Wikipedia says 2009-10-30; minor discrepancy noted) | `[S7]` | high (page); medium (exact legal role) |
| The official Digimon game portal (Bandai Namco Entertainment) is the publisher-side index of Digimon games; its footer is `©Bandai Namco Entertainment Inc.` | Official game portal `[S29]` | high |

**Open naming problem — do not paper over it.** Four names circulate: `Move Games`
(Wikipedia, press), `Movegames Inc.` (2015 Joymax notice), `MOVEINTERACTIVE Co., Ltd.`
(copyright lines) and `Move Interactive` (Korean entity). They are plausibly one corporate
family/rebrand, but no public document I found states the chain. Also, the English Wikipedia
article's "Move Interactive" link resolves to a **defunct Portuguese studio** — an unrelated
company (`[S13]`-adjacent; link observed on `[S1]`). Treat the exact contracting entity as
**UNVERIFIED**; ask the requester which legal entity employs them and whether it is the DMO
operating entity `[analysis]`.

### 1.2 Who owns the IP

- The DMO global and Korean footers credit **Toei Animation** (and Akiyoshi Hongo, the Digimon
  creator) plus Move Interactive — not Bandai Namco Entertainment as copyright owner `[S2][S6]`.
- Bandai Namco's mark appears on the DMO/GameKing portals `[S2][S8]`, and Bandai Namco Korea's
  own site claims DMO development/service under the Digimon IP `[S7]`.
- The official Digimon web portal and game portal are Bandai Namco Entertainment properties and
  are copyright-credited to Bandai Namco Entertainment `[S29]`; Wikipedia lists *Digimon* among
  Bandai Namco Entertainment's owned franchises `[S30]`.

**Consequence for reuse (analysis, not a legal opinion).** DMO's map/asset rights sit in a
**licence chain** — Digimon IP licensors (Bandai Namco / Toei / Hongo) → operating licence →
Move Interactive/Movegames (developer/operator) — and the shipped client art is a licensed
derivative work. Nothing public says GameKing (the portal brand) **owns** reusable rights to
that art; the 2015 notice says the *platform and licence* moved to Movegames `[S3]`. Therefore:
a GameKing employment contract is a duty/authority relationship with one licensee, **not** a
licence from the Digimon rights holders or from the asset-owning developer `[analysis,
high-confidence reasoning]`. Any real-asset path needs written permission from the entities that
can grant it — identified in gap G1 (§7).

### 1.3 Products, client lines, current status

| Claim | Evidence | Confidence |
|---|---|---|
| Engine: **Gamebryo (2009–2022)**, then **Unity (2022–)** according to the article infobox | Wikipedia `[S1]` | medium |
| Developer announced a **remaster** in June 2022 (Unity, target H2 2023); as of August 2026 not released; the game has suffered recurring DDoS attacks and the ToS was updated about compensation | Wikipedia `[S1]` | medium |
| DMO remains **live**: Korean official site homepage lists notices/updates dated 2026-11-10 (including an operation-policy ban notice) | Korean official site `[S6]` | high |
| Steam store page for app 537180 is **region-unavailable** from this session; no Steam publisher details could be read | Store attempt (HTTP error page) | UNVERIFIED |
| A community "**DMO 2.0 client**" exists (open-source Rust server emulator targets it); that project describes replacing "legacy C++ components and **leaked database formats**" and states no affiliation with Bandai Namco/Move Games/Gameking | `[S31]` | medium (project is public and open) |
| "**v487**" client / "v487 client source with NLIB Gamebryo" appears only in leaked-release forum descriptions; licence and provenance unverified; **not inspected here** | Search snippet of a release forum category `[S20]` | low / UNVERIFIED |

**Verdict-relevant:** two client generations may exist (legacy Gamebryo `v487`; a "2.0"
client; a Unity remaster announced). Which generation the "DMO maps" would come from is
**UNVERIFIED**, and the format risk resets per generation. Do not plan a converter against
"the DMO client" in the abstract `[analysis]`.

---

## 2. Zone inventory and vertical-slice candidates

### 2.1 Inventory size

| Figure | Source | Confidence |
|---|---|---|
| The supplied research note's **`~245 maps`** with "Digital Realm" as reference | Internal note (not this pass) | **UNVERIFIED** |
| The site named as that reference (**realm-dr.com**) self-describes as "Digimon Masters Online **private server**" and offers map/raid/download sections | `[S21]` | high (self-description) |
| Community wiki lists **~42 named locations** across Real World, Digital World areas, dungeons and (kDMO-only) Green Zone — counted from the wiki's raw map page | `[S22]` | medium |
| Community wiki's Location category contains **33 category entries**, of which 32 are location articles and 1 is the Maps index (API count, 2026-11-14) | `[S22]` | medium |

**Interpretation.** The published/community-documented map set is in the **tens**, not the
hundreds. `245` may count instance/dungeon variants, kDMO-only content, or private-server
data rows, but its provenance is a private server `[S21]`; it must not be used as a planning
number until independently confirmed (gaps G5/G6) `[analysis]`.

### 2.2 Representative zones (from the community map list `[S22]`)

Real World: Dats Center, D-Terminal (+ dungeon B1/B2), Yokohama Village, Yokohama East
Village, Oil Refinery 1–3.
Digital World: Ice Area (Snowstorm Village, Frozen Ground, Snowman Village, Distorted Data
Village, Infinite Ice Wall); Western Village; File Island (Village of the Beginning, Silver
Lake, Silent Forest, Lost Historic Site, Ancient Ruins of Secret, File Island Waterfront,
Infinite Mountain + dungeon, Server Continent–Desert); East Area (Western Area: Outskirts/East,
Wilderness Area, Wind Valley, Digimon Farm, Ruined Historic); West Area (Western Area: West,
Dark Tower Wasteland, Kaiser's Laboratory instance, Digimon Maze Entrance); Digimon Maze
B1/B2/F1–F4; Green Zone (kDMO): Village of Smiles `[S22, medium]`.

### 2.3 "What a vertical slice needs" — 10 zones worth modelling first

Criteria used: in-game role documented in the map list `[S22]`, visual identity, and
recognisability to Digimon/DMO players (the last is **analyst judgement**, flagged `[est]`).
Names ≠ asset availability; verifying which zones actually have usable scene data is spike work
(gap G4).

| # | Zone | Why this one (1 line) |
|---|---|---|
| 1 | **Yokohama Village** | The real-world starting hub; the first DMO map most players ever saw `[est]`. |
| 2 | **DATS Center** | *Digimon Savers* agency HQ; immediately recognisable interior and the DMO "home base" `[est]`. |
| 3 | **D-Terminal** | The digital gateway/transit hub between the two worlds in DMO `[est]`. |
| 4 | **File Island — Village of the Beginning** | Franchise-iconic File Island entry point and a compact, readable opening zone `[est]`. |
| 5 | **File Island — Silver Lake** | Classic File Island field/lake area; strong silhouette and easy POI placement `[est]`. |
| 6 | **File Island — Infinite Mountain** | Signature vertical dungeon; tests streaming/LOD on a vertical space `[est]`. |
| 7 | **Server Continent–Desert** | "Server Continent" is core Adventure-era geography; barren desert is cheap to reconstruct `[est]`. |
| 8 | **Digimon Maze (F1–F4/B1–B2)** | The game's signature multi-floor dungeon complex — navigation showcase `[est]`. |
| 9 | **Ice Area — Snowstorm Village / Frozen Ground** | The most visually distinct DMO biome; the obvious "wow" screenshot `[est]`. |
| 10 | **Dark Tower Wasteland + Kaiser's Laboratory** | Adventure 02 Kaiser iconography; ruin + instance pair tests a two-scene bundle `[est]`. |

Alternative/additional 11–12 if the slice wants a social and a mundane contrast:
**Digimon Farm** (player hub) and **Oil Refinery 1–3** (real-world industrial set) `[S22]`.

**Recommendation.** Pick one zone for the technical spike (a **field** map, because it exercises
terrain/lighting) and 2–3 zones for the vertical slice only after conversion yields a repeatable
pipeline; a hub (Yokohama/DATS) plus a dungeon threshold **Digimon Maze** shows the product's range
without building a world `[analysis]`.

---

## 3. Engine and asset-format facts

### 3.1 Engine

| Claim | Evidence | Confidence |
|---|---|---|
| Gamebryo is a **proprietary C++ engine**, formerly NetImmerse (until 2003), owned by Gamebase Co., Ltd./Gamebase USA | `[S13]` | high |
| Wikipedia's Gamebryo article samples many licensees (Oblivion, Fallout 3, Civ IV, Atlantica Online…) but **does not list DMO** | `[S13]` | high (for the list) |
| DMO used Gamebryo 2009–2022 and Unity 2022– | `[S1]` | medium |
| "**Gamebryo 2.3**" specifically, and the "v487 source", are **not established by any public primary document** found here | — | **UNVERIFIED** |

### 3.2 File formats and open-source tooling

Gamebryo-line games typically ship **NIF** (scene/geometry), **KF/KFM** (keyframe animation),
**DDS** (textures) and engine-specific lightmap/land data `[S13][S14]`. Open tooling:

| Tool | What it actually claims | Limit relevant to DMO | Source |
|---|---|---|---|
| **NifSkope** | Opens/edits NetImmerse NIF; README names Morrowind, Oblivion, Skyrim, Fallout 3/NV, Civ IV | No mention of DMO or of Gamebryo-2.3-era Asian licensees; NIF is versioned, unknown versions/classes fail | `[S14]`, high (repo) |
| **PyFFI** | Python library for NetImmerse/Gamebryo **NIF and KFM**, plus DDS/TGA; optimizers/tangent tools | Version coverage driven by nifxml schema; custom DMO blocks would be unsupported | `[S15]`, high (repo) |
| **Blender NifTools Add-on** | Imports/exports **.nif, .kf, .egm** (NetImmerse/Gamebryo); v1.0 still in progress, docs hosted separately | Bethesda-centric; unknown/custom blocks and shaders are the usual failure mode | `[S16]`, high (repo) |
| **nifly / PyNifly** | Clean-room C++ NIF library for Gamebryo/NetImmerse/Creation Engine; PyNifly supports Skyrim LE/SE, Fallout 4 | Explicitly Bethesda-generation scope; not a generic Gamebryo-2.3 converter | `[S17][S18]`, high (repo descriptions) |
| **Assimp** | "40+ formats" under BSD | Current source tree (`code/AssetLib`, checked 2026-11-14) contains **no NIF importer directory**; do not assume Assimp converts NIF | `[S19]`, medium (listing high, conclusion medium) |

**DMO-specific NIF risk.** A community thread reports DMO `.nif` map files failing to open in
Gamebryo tooling with `CsNiNode: cannot find create function` — i.e. **custom/unknown NIF
classes** rather than stock Gamebryo blocks `[S20]`. That is low-quality evidence (forum, not
re-read in full), but it is the only public signal, and it points the same way as the tooling
scopes above: the DMO dialect is **UNVERIFIED** and cannot be assumed compatible. Lightmaps,
material conventions, scale/units and collision are **UNVERIFIED** too; DDS textures and baked
lighting normally need re-baking or re-authoring for a mobile PBR pipeline `[analysis]`.

**Practical reading.** "Open-source NIF tooling exists" is true for the **Bethesda-era**
ecosystem. For DMO it is a *starting point for a spike*, not a proven pipeline — and the
Assimp assumption should be dropped. Budget for a small format-investigation before any
zone-level estimate `[analysis]`.

---

## 4. Precedents and enforcement

### 4.1 Tools that read the user's own installed game

| Case | What happened | Reading for Tamers Lab | Source |
|---|---|---|---|
| **Take-Two vs OpenIV** (GTA V mod/file tool, June 2017) | Publisher sent a **cease-and-desist**; distribution halted, then the tool **returned** after public backlash/Rockstar clarification ("single-player mods not under threat") | Reading/editing your own install is *tolerated in some communities, not licensed*; enforcement is discretionary and can hit a tool itself | `[S25]`, medium |
| **Nintendo game-content guidelines** (2018, updated 2024) | Permits gameplay video/screenshots on sharing platforms only (with limits, no selling); expressly lists content "**extracted through game software via data mining**" and circumvention/emulator content as out of scope | The closest thing to a publisher "fan-content licence" is narrow: **captures, not assets** | `[S26]`, high |
| NifSkope / PyFFI / Blender add-on | Neutral format tools distributed openly; legality of use depends on the assets, not the tool | Tool legality ≠ asset legality; the repo should not depend on either for shipped content | `[S14]`–`[S16]`, high |

### 4.2 Private servers and asset redistribution

| Case | Date / outcome | Source |
|---|---|---|
| **DMO official notice: illegal private servers** | 2020-12-17: "this is strictly an ILLEGAL private server"; operator says it is "taking legal measures to shut down" them; users/promoters face bans | `[S5]`, high |
| **DMO official notice: private-server crackdown** | 2025-05-27: strengthened monitoring, evidence collection, "cooperation with relevant platforms to identify, report, and **shut down** these illegal services"; use and **promotion** prohibited | `[S4]`, high |
| **Nintendo vs Tropic Haze (Yuzu)** | Filed late Feb 2024; settlement announced 2024-03-04 for **$2.4M**; emulator development/distribution to end; settlement approved 2024-03-06 | `[S23]`, high (headline/lede facts) |
| **Nintendo vs RomUniverse** | May 2021 US federal court: Nintendo entitled to **over $2M** damages for the ROM site (reported as ~$2.1M); site already defunct | `[S24]`, medium-high |

**Analyst note.** No public Bandai Namco/Toei court filing or takedown against a *non-commercial
Digimon map/model viewer* was found in this pass `[UNVERIFIED — absence of evidence, not
tolerance]`. The only DMO-specific enforcement evidence is the operator's own anti-private-server
notices `[S4][S5]`. Combined with the fact that the research note's asset/data source is a
private server `[S21]`, the risk profile is not "abandonware grey zone"; it is an actively
policed ecosystem `[analysis]`.

### 4.3 Official DMO API or companion app?

- No DMO API, developer portal, or companion-app section exists in the official global site
  navigation (News/Guide/Download/ItemMall/Community/Support only) `[S2]` (high for nav).
- No official DMO API/companion documentation surfaced in searches; the only community "API"-like
  project is an emulator `[S31]` `[UNVERIFIED absence]`.
- **Conclusion:** assume there is **no sanctioned data channel**; any metadata must come from
  the app's own sources or an authorised first-party arrangement `[analysis]`.

---

## 5. Mindlink lore (official Digimon Web / Seekers)

| Claim | Evidence | Confidence |
|---|---|---|
| **Mindlink (マインドリンク)** is the name of the technology/method that converts the **Digimon Linker user's consciousness into digital data and transfers it into the Digimon's DigiCore**; linking lets the user become one with the Digimon and act in digital space | Official Seekers keyword page `[S10]` (JP) | high |
| The **Digimon Linker** is Abadin Electronics' smartwatch-type Digimon Dock; it digitises and transfers the user's mind | `[S10]` | high |
| Mindlink has a **time limit**, which **shortens as the Digimon's stage rises**, so over-evolving compresses activity time | `[S10]` | high |
| Exceeding the limit produces **DMIA (Digital Missing In Action)**: the mind cannot return, is left in the Digital World/network, human ego is lost, and the body is left in a vegetative state | `[S10]` | high |
| Ryusenji distinguishes a **visualisation generated from observations** from **directly perceiving** the Digital World: he calls the shown scenery "an **imagined image of the true Digital World generated by AI based on observation data**", and states his life's research goal is to grasp the Digital World **directly with human five senses, not via virtual monitors and observation data** | Official Seekers novel ch.1-4 `[S11]` (JP; translation mine) | high (text), high (paraphrase) |
| Official Digimon Web terms: "Unauthorized use or reproduction of any imagery, text, or data contained in this website is strictly prohibited" | Digimon Web footer `[S10]` | high |
| Seekers fan-art programme (closed) accepted **illustrations only**, via official hashtags, excluded third-party copyrighted works, and made disputes the submitter's responsibility | Fan-art page `[S12]` | high |

**Product reading (analysis).** The lore gives the feature a defensible frame: a Mindlink
explorer is an **observation-based visualisation** ("this is what the data resolves to"), *not*
a claim of direct perception and *not* a re-host of another game's world. That framing is
compatible with original-art reconstruction and incompatible with presenting DMO geometry as if
it were the Digital World itself. Two guardrails follow: (a) do not imply the app is the
Mindlink/Linker product (trademark + lore boundary); (b) do not borrow Seekers/Digimon Web text
or imagery in UI copy beyond short, attributed paraphrase — the official site forbids
unauthorised reproduction `[S10]`.

---

## 6. Companion / second-screen precedents (what they did and did not do)

| Product | What shipped | What it did **not** do | Source |
|---|---|---|---|
| **FINAL FANTASY XIV Companion** (Square Enix, iOS/Android, announced 2018) | Official app tied to a subscribed service account: chat/events, market board, retainer ventures, inventory/saddlebag, fellowships, home-screen widgets, in-game bonuses; paid premium plan | It does **not** ship game 3D map geometry or extracted client assets; it displays **server/account data** in native UI | `[S27]`, high (feature list); analysis for the negative |
| **WoW Companion App** (Blizzard) | Official app with expansion features; **retired** with *The War Within* pre-expansion update (2024) — no longer downloadable/usable | Same: account/community features, not a re-host of Azeroth assets | `[S28]`, medium-high (announcement page truncated; retirement date from announcement context) |

**Takeaway.** The industry pattern for an official second-screen is **your own account/API data
in your own UI**, under the publisher's own licence. None of the examples ships proprietary 3D
map assets to a separate client; the map surface stays in the game or in a publisher-hosted codex
`[analysis]`. A fan-made app that ships DMO geometry would be an outlier in every sense.

---

## 7. Evidence gaps — what a 2-week spike / legal check must confirm

| # | Open question | Why it changes the verdict | How to confirm |
|---|---|---|---|
| **G1** | Exact rights allocation for DMO client assets: developer/licensor (Move Interactive/Movegames), Digimon IP holders (Bandai Namco, Toei Animation, Hongo), and GameKing's operating rights | Decides whether *any* real-asset path (T2/T3) is possible, and who must sign | Written answer from GameKing licensing/legal with the underlying licence/contract references; a one-page rights map signed by the company |
| **G2** | DMO's actual EULA/ToS text on extraction, reverse engineering, reuse, and jurisdiction — the web ToS page renders empty for text extraction `[S2]` | Determines whether even an internal extraction spike is permitted | Retrieve the EULA shown by the installer/client, archive a copy, have counsel read it |
| **G3** | Whether GameKing's global operating role includes any asset-reuse right outside the service | Employee status is not permission; the answer sets the boundary | Same as G1; ask explicitly "may GameKing employees reuse DMO art in a separate app, and under what terms?" |
| **G4** | DMO NIF dialect/version, scene/map format, lightmap + material conventions | Decides whether conversion is days or months; determines spike pass/fail | Authorised spike: inspect NIF headers/versions, attempt NifSkope/PyFFI/Blender imports, log unknown block types; test one field map end-to-end |
| **G5** | Whether `245 maps` is a real client-zone count, and how zones map to scene assets | Drives content cost and scope (a curated 12 may still be 40 scene units) | Authorised inventory of pack tables; reconcile with community list (~42 named) `[S22]`; dedupe instance variants |
| **G6** | Provenance of the research note's map-count source ("Digital Realm") | If it is the private server `[S21]`, the figure is inadmissible for planning and the note's asset claims need independent sourcing | Ask the note's author for the exact URL/path and whether it came from a private-server dataset; exclude if so |
| **G7** | Which client generation "DMO maps" means (legacy v487 / "2.0" / Unity remaster) and its version drift | Converter and art pipeline differ per generation; remaster could reset formats | Official release notes + authorised build metadata; confirm with GameKing operations |
| **G8** | Employee obligations: NDA, external-project policy, asset-handling rules, declaration duty | Turns a licensing question into an employment-risk question | HR/legal written confirmation; internal declaration before any work starts |
| **G9** | Whether Bandai Namco/Toei have taken action against Digimon fan tools (court records, DMCA/Lumen) | Calibrates takedown risk; absence of public action is not tolerance `[UNVERIFIED]` | Counsel search of court dockets + Lumen/DMCA; check Toei/Bandai enforcement pages; re-check store policies |
| **G10** | Whether any official DMO data channel exists (API, partner feed, companion) | Would change the atlas (T1) design from scraping to sanctioned feed | Direct question to GameKing product/ops; re-check official site and press channels |
| **G11** | English/localised Mindlink wording for UI copy | Copy must not invent terminology or imply official affiliation | Official English Seekers translations; keep JP source `[S10]` as authority and mark EN as UNVERIFIED |
| **G12** | Whether map names/layouts/spawn tables are protectable and whether a stylised reconstruction is acceptable | Affects O5 (DMO-inspired reconstruction) and naming policy | Counsel opinion; compare with the app's existing fan-content posture |

---

## 8. Source ledger

All URLs accessed **2026-11-14** unless stated.

| ID | Source (URL) | Type | Confidence |
|---|---|---|---|
| S1 | https://en.wikipedia.org/wiki/Digimon_Masters | Encyclopedia (orientation; cites official pages) | medium |
| S2 | https://dmo.gameking.com/Policy/TermsofUse.aspx (global site; footer + nav; terms body not rendered as text) | Official site | high (footer/nav), UNVERIFIED (terms text) |
| S3 | https://dmo.gameking.com/News/NoticeView.aspx?idx=1450 (2015-08-07, Joymax → Movegames transfer) | Official notice | high |
| S4 | https://dmo.gameking.com/News/NoticeView.aspx?idx=4019 (2025-05-27, private servers) | Official notice | high |
| S5 | https://dmo.gameking.com/News/NoticeView.aspx?idx=3256 (2020-12-17, illegal private server) | Official notice | high |
| S6 | https://www.digimonmasters.com/index.aspx (Korean official site; operator entity + classification) | Official site | high |
| S7 | https://m.bandainamcokorea.co.kr/mw/company/business.do (Bandai Namco Korea business page; lists DMO) | Official corporate site | high (page), medium (legal role) |
| S8 | https://www.gameking.com/main/main.aspx (portal; footers/links) | Official portal | high |
| S9 | https://mmos.com/news/digimon-masters-online-launch-steam-november-1st (2016-10-30) | Trade press | medium |
| S10 | https://digimon.net/digimonseekers/ (Seekers keyword block: Dock/Linker/Mindlink/DMIA; site terms; footer copyrights) | Official (Digimon Web) | high |
| S11 | https://digimon.net/digimonseekers/novel/1-4.php (ch.1-4; observation-vs-direct-perception passage) | Official novel (JP) | high |
| S12 | https://digimon.net/digimonseekers/fanart/index.php (closed fan-art programme; rules) | Official | high |
| S13 | https://en.wikipedia.org/wiki/Gamebryo | Encyclopedia | high (engine facts), medium (DMO link) |
| S14 | https://github.com/niftools/nifskope (README on `develop`) | Open-source repo | high |
| S15 | https://github.com/niftools/pyffi (README) | Open-source repo | high |
| S16 | https://github.com/niftools/blender_niftools_addon (README.rst + repo metadata) | Open-source repo | high |
| S17 | https://github.com/ousnius/nifly (repo description) | Open-source repo | high |
| S18 | https://github.com/BadDogSkyrim/PyNifly (repo description: Skyrim LE/SE, FO4) | Open-source repo | high |
| S19 | https://assimp.org/ + https://github.com/assimp/assimp/tree/master/code/AssetLib (directory listing checked) | Open-source project | high (site), medium (NIF conclusion) |
| S20 | https://forum.ragezone.com/threads/help-opening-nif-map-digimon-masters-file-gamebryo.1141895/ (search snippet; thread not re-opened) and the leaked-release forum category `.../community/digimon-masters-releases.1083/` (snippet only; **not downloaded/used**) | Community/leak-adjacent forum | low / UNVERIFIED |
| S21 | https://realm-dr.com/ and https://realm-dr.com/maps.php (self-described "Digimon Masters Online private server"; map section loads via script) | Private-server site (provenance warning) | high (self-description), UNVERIFIED (data) |
| S22 | https://dmo.fandom.com/wiki/Maps (raw: `?action=raw`) and API `.../api.php?action=query&list=categorymembers&cmtitle=Category:Location&cmlimit=500&format=json` | Community wiki | medium |
| S23 | https://www.theverge.com/2024/3/4/24090357/nintendo-yuzu-emulator-lawsuit-settlement and https://www.theverge.com/2024/3/6/24092680/yuzus-2-4m-settlement-with-nintendo-has-been-approved-by-a-judge | Reputable news | high (headline facts) |
| S24 | https://www.reuters.com/legal/transactional/nintendo-wins-over-2-million-operator-pirate-games-site-2021-05-27/ | Reputable news | medium-high |
| S25 | https://www.eurogamer.net/gta-modding-tool-openiv-resurfaces-following-take-twos-cease-and-desist and https://www.ign.com/articles/2017/06/19/e3-2017-rockstar-addresses-take-twos-decision-to-shut-down-gta-modding-software-openiv | Games press | medium |
| S26 | https://www.nintendo.co.jp/networkservice_guideline/en/index.html (2018-11-29, updated 2024-09-02) | Official publisher policy | high |
| S27 | https://na.finalfantasyxiv.com/companion/ | Official product page | high |
| S28 | https://news.blizzard.com/en-us/article/24061010/the-wow-companion-app-retiring (body truncated at fetch; retirement tied to *The War Within* pre-expansion update) | Official announcement | medium-high |
| S29 | https://digimon-gameportal.bn-ent.net/product/ (Bandai Namco Entertainment official Digimon game portal; footer copyright) | Official | high |
| S30 | https://en.wikipedia.org/wiki/Bandai_Namco_Entertainment (lists Digimon among owned franchises) | Encyclopedia | medium |
| S31 | https://odmo.dev/ (community open-source DMO 2.0 server emulator; self-described "leaked database formats"; states no affiliation) | Community project | medium (existence/claims) |

**Quarantine list (do not treat as evidence of fact, do not bring into the repo):** leaked
client/server source and "complete files" bundles; private-server downloads and datasets
(including `realm-dr.com`); leaked-release forum threads `[S20][S21]`. The community emulator
project `[S31]` is cited only for its public self-description (it names leaked database formats
as what it replaces) — not as an asset source. This note's claims about all of these are limited
to what the public sites say about themselves.

---

## 9. Three facts that most change the viability verdict

1. **The rights chain is a licence, not an ownership chain the requester can grant.** DMO is
   credited to Toei Animation/Hongo + MOVEINTERACTIVE `[S2][S6]`, with Bandai Namco Korea claiming
   DMO development/service under the Digimon IP `[S7]`; the 2015 notice says platform and licence
   moved to Movegames `[S3]`. GameKing is the operating portal `[S8][S9]`. **An employment
   relationship is not a reuse licence** — real-asset reuse needs written permission from the
   rights holders/asset owner (G1/G3). This keeps any T2/T3 real-asset path red until then
   `[analysis]`.
2. **The ecosystem the research note draws from is actively policed, and the `245` figure's
   source is a private server.** The operator's 2020 and 2025 notices call private servers
   illegal and describe shut-down cooperation `[S4][S5]`; "Digital Realm" self-describes as a
   private server `[S21]`. Community-documented locations number in the **tens** (`~42` named,
   33 category pages) `[S22]`, so `245` is both legally and epistemically unsafe (G5/G6).
3. **"Gamebryo tooling exists" is a Bethesda-era fact, not a DMO fact.** NifSkope/PyFFI/Blender
   add-on/nifly cover the well-known NetImmerse/Gamebryo dialects `[S14]`–`[S18]`; Assimp in its
   current tree has **no NIF importer** `[S19]`; the only public DMO signal reports custom NIF
   classes `[S20]`. DMO's dialect, scene format, lightmaps and client generation (v487 vs "2.0"
   vs Unity remaster) are all **UNVERIFIED** `[S1][S31]` — the faithful-zone path must be
   spiked before it is estimated (G4/G7).

*Positive counterweight:* the official Mindlink material makes an **observation-based
visualisation** a lore-honest product frame `[S10][S11]`, which supports the original-art atlas /
reconstruction route independently of the asset question `[analysis]`.
