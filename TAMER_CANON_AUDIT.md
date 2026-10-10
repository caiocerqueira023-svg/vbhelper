# Character-specific Tamer Arena partner audit

## Scope and evidence

The audit started from all **223 existing roster entries** in `tamers.tsv` on
2026-10-10. The research pass retrieved 219 distinct character pages and followed
their linked partner/history pages, then investigated nonstandard evolution
templates, conflicting names and narrative-only cases individually.

`tools/audit_tamer_canon.py` is a read-only research helper. It reports character
relationships and character-specific evolution evidence; it does not write the
roster or turn its extraction into approved content automatically. HTML comments,
reference links and generic TCG evolution permissions are excluded. Hybrid and
DigiXros evidence is kept separate from ordinary generation fields.

Approved histories and their source URLs live in `TamerCanonicalPartners.kt`.
Unknown cells mean "not documented", never "the shared species tree is official".
Game characters with variable/large parties also have explicit documented
companions. Species artwork/stat files are an availability source, not evidence
that a character used that species' generic evolution route.

## Representative corrected histories

- **Tsurugi:** Agumon/Agumon (2006), Greymon/GeoGreymon, RizeGreymon,
  **VictoryGreymon** — [Agumon (Next)](https://wikimon.net/Agumon_(Next)).
- **Yuu (Next):** Gaomon, Gaogamon, MachGaogamon, **Z'dGarurumon** —
  [Gaomon (Next)](https://wikimon.net/Gaomon_(Next)).
- **Takumi Hiiragi:** DORUmon, DORUgamon, DORUguremon, **Gaioumon**, with
  Itto Mode as a documented mode — [DORUmon (ReArise)](https://wikimon.net/DORUmon_(ReArise)).
- **Yuuko:** GeoGreymon, RizeGreymon, **Gaioumon** —
  [RizeGreymon (Cyber Sleuth)](https://wikimon.net/Rize_Greymon_(Cyber_Sleuth)).
- **Erika:** Wormmon → **Hudiemon**, rather than Ken's Stingmon route —
  [Wormmon (Cyber Sleuth)](https://wikimon.net/Wormmon_(Cyber_Sleuth)).
- **ReArise:** Michi's LovelyAngemon, Keito's HeavyLeomon, Mayu's Mitamamon,
  Nozomi's NoblePumpmon, and Kazuma's Cerberumon are character-specific, sourced
  forms rather than shared Salamon/Elecmon/Kudamon/Bearmon defaults.
- **Survive:** Aoi's Anubimon, Saki's Vegimon/Ceresmon Medium, Ryo's
  Jewelbeemon/BanchoStingmon, Kaito's Vamdemon/Beelzebumon and Miu's Mermaimon
  follow their specific partner pages. Documented story-route alternatives are
  retained; the representative non-corrupted route is preferred.
- **Liberator:** Yuuki's Punkmon/Loudmon/HeavyMetaldramon, Saikiyo's
  Forgebeemon/Vespamon/Queenbeemon, Ryutaro's MasterTyranomon/Dinomon and Close's
  Pyramidimon have separate histories. Owen's two partners, Shoto's Muchomon and
  newer protagonists likewise use their own recorded forms.
- **Other corrections:** V-Tamer Zeromaru's child form is Agumon, Ruli's babies
  are Pyonmon/Bosamon, Hirokazu's child is Kokuwamon, Juri's is Elecmon, and
  Kosuke's secondary Agumon is the black variant. Inori's child form is Elecmon.
  Beatbreak's named partner histories are distinct from older species defaults.

## Combined partner identities

- **Mirei:** Angewomon + LadyDevimon → **Mastemon** at Mega tier. Her documented
  solo Ofanimon/Lilithmon forms do not replace the preferred combined partner.
  [Angewomon (Re:Digitize)](https://wikimon.net/Angewomon_(Re:Digitize)) and
  [LadyDevimon (Re:Digitize)](https://wikimon.net/Lady_Devimon_(Re:Digitize)).
- **Nokia:** Agumon + Gabumon → **Omegamon** at Mega tier, followed by an
  independent guest in 2×2 — [Agumon (Cyber Sleuth)](https://wikimon.net/Agumon_(Cyber_Sleuth)).
- **Hideto:** Warg + Melga → **Omegamon**, with an independent V-Tamer guest —
  [Omegamon (V-Tamer)](https://wikimon.net/Omegamon_(V-Tamer)).
- **Next Order protagonists:** the documented opening WarGreymon/MetalGarurumon
  fusion is Omegamon — [game character list](https://wikimon.net/Digimon_World_-next_0rder-#Characters).
  A fixed, unshown evolution line is not invented for player-variable partners.
- **Kiriha/Taiki:** documented DigiXros components are consumed by their combined
  form. These identities cannot simultaneously occupy the second slot. Kiriha's
  Greymon (2010 Anime Version) and Taiki's Shoutmon X2 equip as mid-battle
  finishers when both components are fielded: Greymon + MailBirdramon and
  Shoutmon + Ballistamon respectively.
- **Adventure 02 DNA pairs:** Daisuke, Ken, Miyako, Hikari, Iori and Takeru list
  each other as late guest alternatives. When the lead fields an unfused
  component (XV-mon, Stingmon, Aquilamon, Tailmon, Ankylomon, Angemon), the
  fusing partner is preferred for the second slot, so Paildramon, Silphymon and
  Shakkoumon equip. A fusing guest never outranks the lead's own owned pair,
  and non-fusing tiers keep the established guest order.
- **Second-slot competition:** owned companions and allied guests compete openly
  for the second slot. Completing an approved fusion with the lead wins; owned
  beats guest on ties; list order breaks the rest. This is why Hikari fields
  Aquilamon beside Tailmon instead of her associated Wizarmon.
- **Shared partners:** Knuckles' inherited Gankoomon is one entity shared by
  Hajime/Haruka, not two independently fieldable Gankoomon instances.
- **Shoma:** 1×1 at Mega fields **Omegamon Alter-B** (Noir); 2×2 fields Gaioumon
  + Kuzuhamon with Alter-B equipped as their Jogress —
  [Omegamon Alter-B](https://wikimon.net/Omegamon_Alter-B) (Kuzuhamon components
  per [Digimon World Next Order](https://wikimon.net/Digimon_World_-next_0rder-#Characters);
  [Next Order character list](https://wikimon.net/Category:Digimon_World_Next_Order)).
  The combined form is solo-only: beside a partner the components field and
  equip it instead. Alter-B has no bundled battle sprite, so the movie stays
  gated and the solo tier falls back to Gaioumon at tier until its card is
  imported. The **Noir** nickname is restricted to the resolved Alter-B form in
  1×1, so Gaioumon retains its species name in 2×2 and missing-art fallbacks.

Combined/borrowed partner identity sets prevent reusing a component even at a
different stage. NPC Jogress loadouts require both a permitted character-specific
result and the actual fielded species pair. Guest transformations use the guest
owner's history, not the lead tamer's species defaults.

## Genuine gaps and remaining limits

For genuinely missing generations, the arena retains a documented form and scales
HP/AP/BP/energy, ordinary attack budgets and recovery amounts to the selected combat
tier. It does **not** invent an evolution. Form size and identity stay separate
from the combat tier, and preparation identifies scaled partners explicitly.

An unnamed but documented form is not interchangeable with an unrelated named
species. Likewise, unavailable canonical form artwork is a missing asset, not a
reason to substitute a convenient alternative evolution. Imported artwork can
enable these forms later. Verified against the shipped bundle on 2026-10-10:
Shoutmon X2, Lady Devimon, Mastemon, Lilithmon, Ofanimon, Greymon
(2010 Anime Version), Omegamon Alter-B, Titamon and Omegamon Zwart Defeat
live only in the species encyclopedia without battle
sprites. Consequences: Taiki's owned pair fights without the X2 movie until its
card is imported; Mirei's secondary line resolves through guests; Kiriha leads
with the art-backed DigiXros form and an owned army companion; Shoma fields
Gaioumon + Kuzuhamon without the Alter-B movie and falls back to Gaioumon
alone until its card is imported.

The sources are not complete for every character's every generation. Generic
species pages, deck themes and sparse character infoboxes cannot establish a full
partner history; these cases retain documented partners with stat scaling rather
than being represented as completely verified evolution lines.

## Partner nicknames

`tamer_nicknames.tsv` records only names tamers actually use in canon, one row
per (tamer, partner anchor) with its Wikimon source. Nicknames belong to the
individual: they survive form changes and stat scaling, travel with guests, and
never affect species matching. An optional fifth column restricts a nickname to
one resolved species form, so fusion-specific names cannot label their unfused
components or missing-art fallbacks. Battles, briefings, roster rows and search
all show them; species names stay visible alongside.

Verified 2026-10-11 from character/partner infoboxes:

- **Re:Digitize:** Taiga's Agumon **Digimaru**, Rina's V-dramon **V.V.**,
  Nicolai's Gaomon **Sashenka**, Akiho's Piyomon **Digitorin**, Yuuya's Black
  War Greymon (X-Antibody) **Black**, Lili's Numemon **Catherine**. Taiga's
  temporary Plotmon and Nicolai's borrowed Digimaru carry no nickname of their own.
- **V-Tamer:** Taichi's V-dramon **Zeromaru**; Hideto's War Greymon **Warg** and
  Metal Garurumon **Melga**.
- **Next Order:** Kouta's Guilmon **Yukimura**; Himari's Plotmon **Rikka**;
  Shoma's solo Omegamon Alter-B **Noir**, when its artwork is loaded.
- **World 2:** Zudokan's Mega Seadramon **AKAGI**, Lilimon **NAOMI** and
  Triceramon **DINOGON** (story team; his Colosseum Blossomon, Delumon and
  Pumpmon are unnamed).

Deliberately skipped: Shoma's **Luche** (no species listed, cannot anchor);
Liberator tamers, whose character pages use species-only partner
fields (verified for leads Shoto, Arisa and Owen); Frontier/Xros/Hunters/Savers/
Ghost Game/Seekers/Survive and the remaining cast, whose partners are called by
species in canon. Nicknames from other tamers never leak: Nokia's own Agumon
stays unnamed even though Taiga's is Digimaru.

## Verification

The corrected policy passed 22 canonical regressions, including the 350+
documented primary-stage selection sweep, within a 272-case battle regression
run. Current bundled assets support 164 Rookie-tier 1×1 and 135 Champion-tier 2×2
teams (including explicitly stat-scaled real forms). The four research-parser
checks, APK/test builds and lint also passed. Actual Room reward/rollback checks
passed on the connected Android device. See `TESTING.md` for commands and scope.
