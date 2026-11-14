# Adversarial review — Mindlink × DMO maps viability analysis

| Field | Value |
|---|---|
| Reviewer | `doc-reviewer` (independent; read-only review) |
| Reviewed | `README.md` (SHA-256 `070C26E9…442B64`, 715 lines), `recon-technical.md` (`D8A0648A…856239`, 443), `recon-context.md` (`78C901A8…F2CE1C`, 345) |
| Snapshot | 2026-11-14T11:45-03:00. **README.md was edited during this review** (11:45; §8.2 faithful-zone totals changed). Findings target the hash above. If the author edits again, re-check the affected findings. |
| Method | Read all three files; independently re-measured `app/src/main/assets` (totals, per-folder, 5 GLBs parsed), the release APK (composition, per-entry compressed sizes, tree-vs-APK diff, independent deflate projection), and re-read >25 cited repo files/lines; independently re-fetched `[S1]`, `[S3]`, `[S4]`, `[S5]`, `[S10]`, `[S11]`, `[S22]` (+ wiki rights API), `[S26]`. No DMO, leaked or private-server material was downloaded, opened or executed; no repository file other than this one was modified. |
| Verdict | **ACCEPT-WITH-CHANGES** — the analysis is materially better than a typical feasibility memo and its core conclusion is defensible, but it must not go to the requester's manager/legal team until the two blockers and the legal-calibration items below are fixed. It should not be rejected: the technical recon, the no-redistribution conclusion and the tier/gate structure are worth keeping. |

## What is sound (so the author knows what not to touch)

- The **technical recon is the strongest part**: every headline size/number I re-measured reproduced exactly (assets 125,302,503 B / 13,891 files; Colosseum 2,893,360 B; 5 GLBs 8,069,591 B; APK 149,019,234 B; assets 113.82/95.69 MiB; arm64 lib 7.32 MiB; Room 56; 110 uncommitted entries; GLB feature counts). The "Filament is there but has never rendered a lit pixel" correction is true and important.
- **"Redistribution of real DMO assets is RED without written permission" is the right conclusion**, and the doc correctly separates employment from licensing, rejects the live-client path (T4), and quarantines the private-server ecosystem.
- **The tier ladder and Spikes A/B are the best decision aids in the document**; A1–A5 / B0–B6 are mostly falsifiable and cheap, and the "own art now, faithful gated" sequencing is directionally the right way to protect the requester.
- The **APK/delivery arithmetic is internally consistent and reproducible** (~10.17 MiB headroom; 155.50 MiB budget; 21.56 MiB App-Bundle install relief). My independent recomputation differed by only ~45 KB (see F12/claims list) — the conclusion is robust.

## Findings

Numbered by severity. Locations are sections in the snapshot above, with line numbers where stable. "Fix" is what I would require before approval.

### Blockers

**F1 — BLOCKER — The evidence chain for the supplied note is broken; the "245 maps → Digital Realm" provenance is upgraded from UNVERIFIED to fact, and two figures are unsupported.**
Location: README §0 (L55, L58), §3.1 (L184), §6.1 (L364–367); `recon-context.md` §2.1 (L91–99) vs §9 (L331–335); input row L10–11. `DMO_private_server_resources.md` is not present anywhere in the repository/workspace.
Problem:
1. All `[NOTE]` claims (Pack01 ≈10 GB, Pack03 ≈70 MB, "≈245 maps", the 2024 "complete files" allegation, "public v487 client source", the Pack/BIN tooling, the note's §5 recommendation) trace to a document the decision-maker cannot open, and no excerpt/source log is attached.
2. README states as fact that *"The note's '≈245 maps' traces to Digital Realm, which self-describes as a DMO private server"* (`§3.1`). `recon-context.md` §2.1 records the opposite confidence: the note→Digital Realm link is **UNVERIFIED** (`Internal note (not this pass)`). `recon-context` §9 then elevates it to one of "three facts that most change the verdict", and README inherits the elevation. The underlying conclusion (the 245 figure must not be used) is right; its stated proof is not.
3. README §6.1 (L365) says Digital Realm is the source of the *"≈245 maps / 863 Digimon / 29,469 items"* figures. **863 and 29,469 appear in neither recon file** — they are unattributed, uncited numbers sitting inside a `[EXT: S21]` sentence.
4. "Inadmissible" is a legal term applied to an epistemic problem; it invites an argument that is not needed (the figure is simply unverified/private-server-derived and must not be used).
Why blocking: the RED legal pillar and the "plan in the tens, not hundreds" scoping decision both lean on this chain. A decision-maker (or their legal team) who checks the footnote will find it does not resolve, which discredits the sound conclusions around it.
Fix: attach the supplied note (or a redacted source log: title, date, author, exact URLs used) as an appendix or separate file; state the chain with separate confidence: `[NOTE]` the note attributes the 245 figure to Digital Realm; `[EXT]` Digital Realm self-describes as a private server; `[UNVERIFIED]` the note→source link (G6). Delete 863/29,469 or cite the note's exact text and mark them `[NOTE]`. Replace "inadmissible" with "must not be used for planning". Cite the exact `realm-dr.com` URL in the README, not only in recon-context. Until the note is attached, mark every `[NOTE]` figure in §0/§3.1 as "unverifiable at review".

**F2 — BLOCKER — T1's "no authorization needed" legal basis is under-analysed: CC-BY-SA obligations, "maps are facts", DMO-derived metadata, and a conflict with the doc's own derivative-work assumption.**
Location: README §8.1 (L494–498), §5.3 (L334–336), §2.2 (L160–165), §0 ("legally workable"); repo `LICENSE` is **MIT**.
Problem:
1. "Community wiki content is CC-BY-SA and maps are facts, not client files" is stated without a citation to the wiki's licence, and the analysis does not address the consequences: CC-BY-SA content copied into an MIT-licensed app creates **attribution obligations and ShareAlike compatibility questions**. (The licence itself is real — the wiki's `rightsinfo` API returns CC-BY-SA — but that is a *page-content* licence, and the Maps page also embeds uploaded map images whose provenance is not stated, so "maps are facts" cannot cover them.)
2. "Maps are facts" is a legal conclusion. The doc itself assumes two pages later that reproducing a map's *layout* is a derivative work (`§5.3`, `[ASSUME]`). The plan's fields ("names, connectivity and landmark notes, spawns") mix non-copyrightable facts, community prose and DMO-derived data; database/compilation rights (which exist in the EU/UK/Korea) are never considered.
3. The claim "**no client extraction needed**" is true in the narrow sense that the project would not run an extractor, but the data would be second-hand client-derived metadata; the distinction that protects the project is provenance discipline, not the absence of a tool.
4. T1 is the only work the doc recommends starting **now, without authorization** — so this is the load-bearing legal premise of the recommendation.
Why blocking: if T1's sourcing is challenged, the "safe, start-now" tier becomes the same risk class the doc is protecting against, and the requester's manager/legal team will stop the whole plan at the first review.
Fix: add a **per-field content-source table** for T1 (field → source → licence → attribution obligation → counsel question). Restrict the start-now scope to the app's own lore ledger plus factual names/links, with **no copied wiki prose or images**; decide explicitly whether any CC-BY-SA text is used and how attribution/ShareAlike is handled; tag the "facts" position `[ASSUME → counsel]`; add database-rights and CC-BY-SA/ShareAlike questions to Appendix B and gap G12; make T1 publication gated on a written "C1-lite" provenance decision, not on nobody noticing.

### Majors

**F3 — MAJOR — Legal certainty is overstated on the employee/conflict dimension, and the legal reasoning is missing several inputs.**
Location: README §6.1 L369 ("For a GameKing employee this is not a grey area"), §5.1 L309–310 ("direct conflict of interest whatever its purpose `[USER]`"), §6.1 L359–363 ("bans for users/promoters"), §6.2 L392–394 (2015 quote), §6.4 L422 (store policy citing S26).
Problem:
1. "Not a grey area" contradicts the doc's own `[ASSUME → confirm, gap G8]` three lines earlier. Whether a personal project breaches an NDA/moonlighting/IP-assignment clause depends on the contract, the employer entity and the jurisdiction — all unverified. The doc should claim *risk*, not *certainty*.
2. `[USER]` is defined as "supplied by the requester". The conflict-of-interest conclusion is analyst inference, not requester-supplied — a tag misuse on the most accusatory sentence in the document.
3. **Mis-citation of enforcement evidence.** I verified S5 (2020-12-17): it says the private server is "strictly an ILLEGAL private server", that the operator is "taking legal measures to shut down" it, and separately that **fraud** ties lead to permanent bans. It does **not** say private-server users/promoters face bans. S4 (2025-05-27) says use/promotion is prohibited and accounts "**may be subject to penalties**". README/recon-context's "bans for users/promoters" overstates the source. (The direction of risk is still correct; the paraphrase is not.)
4. **2015 notice quote changed in meaning.** S3 reads "Platform and license **will be** transferred to Movegames after the operation and customer support transfer has been completed." README's ellipsis drops "will be" and presents the licence transfer as a completed fact, while §6.2 says the entity/licence allocation is unresolved. Pick one.
5. `§6.4` uses Nintendo's **video/image-sharing fan guidelines** (S26) as evidence for "store policy (IP takedown)". S26 is a publisher fan-content policy, not an app-store policy; it explicitly says other uses are "subject to the relevant laws". No Google Play/Apple policy is cited anywhere.
6. Missing legal inputs: **jurisdiction** (Korea vs US vs Brazil vs the developer's country), **DMO EULA/ToS** (G2: not read), **trademark** use of "Mindlink"/"DMO" in app title, store listing, screenshots and UI, **anti-cheat/integrity** terms, and **interoperability/reverse-engineering exceptions** that may exist in some jurisdictions (counsel question, not an assumption).
7. The "employee cannot self-authorize" conclusion is correct, but §0 tags it `[ASSUME→confirm]` and the body never states it as a finding with its reason (employment = duty relationship with one licensee ≠ licence from IP holders). A decision-maker will miss it.
Fix: rewrite §6.1 in risk language (`[ASSUME]`/`[COUNSEL]`, not assertion); restore the exact S5/S4 wording and the "will be" in S3; replace the `[USER]` tag with `[EST]/[ASSUME]`; cite the actual store policy or delete the store-policy claim; add jurisdiction, EULA, trademark and interoperability rows to §6/Appendix B; add a one-line "Legal questions (a)–(d)" box in §0 with the four conclusions and their confidence. The top-of-file "not legal advice; counsel must confirm" is good — repeat it at each legal conclusion and make C1/C3 explicitly counsel-gated.

**F4 — MAJOR — The option-matrix scoring legend is self-contradictory, and O3/O4/O5 legal scores do not match the analysis.**
Location: README §5 L280 (legend), L283–292 (matrix), §5.1 L294–312, §5.2 L314–325, §5.3 L327–336.
Problem:
1. "Scored 1 (bad) – 5 (good); ↓ columns are 'lower is better'." These are opposite conventions and no column is marked ↓. A reader who takes "lower is better" would read O1 legal 1 as *best* and O4 legal 5 as *worst* — the exact inversion. The usage elsewhere (1–2 = "not startable") shows 1=bad is intended, so the sentence is simply wrong.
2. **O3** gets Legal **3** and a status of "**Hold**", above the doc's own "1–2 not startable" gate — yet §5.1 concludes "keep it as a research note, not a plan, unless the company itself sanctions it". "Hold" has no owner, trigger or expiry. It should be "Reject for this project while the requester is a GameKing employee; retain as a note".
3. **O4/O6** get Legal **5** while the doc itself says own-art Digimon locations "do not remove all IP questions" and wiki-sourced T1 is unresolved (F2). Legal 5 ("clean") is not defensible; AMBER (3–4, gated) is.
4. **O5** gets Legal **4** while §5.3 assumes layout reproduction may be a derivative work — O5 is *less* legally clean than O6/O4 but is scored as if it were nearly clean.
5. The matrix has no "defer / do nothing / gather authorization first" baseline, so the reader cannot see the cost of inaction.
Fix: state each column's direction explicitly (e.g., "Legal and Fidelity: 1 = worst, 5 = best; Risk, Content cost, Ship time: 1 = highest/worst, 5 = lowest/best"), mark the columns, and leave fiction scores out (O4 fidelity is "n/a"). Re-score O4/O5/O6 as AMBER with the gate that lifts them, convert O3 to "Reject (employee + EULA + no licence)" with the research-note caveat, and add O0 "do not build / wait for authorization" as a scored baseline.

**F5 — MAJOR — Effort arithmetic does not reconcile across §0/§2/§8.2/§10.3, and the recon cross-references are stale; there is no cost/staffing model at all.**
Location: README L61; §2 L133–137; §8.2 L513–521; §10.3 L603–608; `recon-technical.md` §7 L379–382 and §2.4 L216–218.
Problem (verified against the current text):
1. **Atlas**: "1–2 weeks" (§0/L61), T1 1–2 + T1+ 1–2 = **2–4** (§2/§8.2), P1 **2–3** (§10.3). Three answers for one deliverable.
2. **First faithful zone**: components are program 8–12 (parenthetical already includes "conversion tooling") + per-zone 2.5–5.5 → **10.5–17.5**; if tooling is separate (2–4) → **12.5–21.5**. The stated **12–19** is derivable under neither reading.
3. **Curated faithful set** (current edit): 6 zones = program-ish 10–16 + 6×2.5–5.5 → **25–49**; 12 zones → **40–82**. The doc now says **25–45 / 40–80** — maxima are still understated and the model mixes "first" and "repeat" costs.
4. **P4** is 6–8 weeks for 3–4 own-art zones, but §8.2's repeat cost is 2–3/zone → 3 zones 6–9, 4 zones 8–12. Only the best case fits.
5. **recon-technical §7 (L379–382)** cites `README.md:434–445` for "4–8 eng-weeks per zone". The current README says 2.5–5.5 per zone / 12–19 first zone; lines 434–445 are the §6.5 gates. `recon-technical §2.4` similarly cites `README.md:493` for a "zone bundle ≤40 MB" target that does not exist. These are stale cross-references from an earlier draft — they will be found by any careful reader.
6. No FTE count, artist days, dependency/critical-path, money, or spend cap anywhere. "eng-weeks" is a unit, not a budget.
Fix: one canonical effort model with explicit inclusion/exclusion (does the program include conversion tooling?), a first/repeat split that sums, and a note that ranges are `[EST]`. Reconcile §0/§2/§8.2/§10.3 to that model. Add a cost/staffing table (engineer-days, artist-days, FTE, device-lab, counsel cost) and a spend cap. Fix or delete the stale recon cross-references (cite sections, not lines, if the README is still changing).

**F6 — MAJOR — The product thesis overreaches: "novel", "no asset problem", "uniquely able" are not established, and the substitute may not satisfy the requester's actual desire.**
Location: README §2.2 L156–165, §2.1, §8.1 L499–507.
Problem:
1. "Almost none of those places has ever been realised as an explorable 3D place in a game `[ASSUME]`" is doubtful as stated: File Island/Server Continent are the settings of Digimon World (1999) and later Digimon World games, and DMO itself realises them in 3D; the app is not "uniquely able" to render places the franchise has rendered before. The `[ASSUME]` tag keeps this honest, but the surrounding paragraph ("novel feature", "inverts the premise") treats it as a finding.
2. "No asset problem" conflates *asset licensing* with *franchise IP*. An own-art reconstruction of a named Digimon location is still a derivative work of the franchise unless counsel says otherwise; that is exactly the O4/IP question the doc leaves open elsewhere.
3. **The recommended substitute does not deliver the stated desire.** The requester (and their likely DMO-playing audience) asked for the maps they remember *from DMO*; the doc substitutes lore geography (Iliad, Shambhala, Witchelny) that most DMO players have never seen. That is a legitimate product, but it is a **different product**, and the doc is not explicit enough that it may not satisfy the requester.
4. The app's own audience is Vital Bracelet/companion users; there is no evidence that they want DMO or lore maps, no market/competitive check (DMO wikis, YouTube map tours, the games themselves), and no pre-build user validation — the first user feedback arrives after P1/P2 spend.
5. The atlas overlaps the app's existing **Lorebook** route (`AppRoute.Lorebook`); the differentiated value ("zone ↔ species ↔ Dex ↔ spawn/quest metadata ↔ partner memory") is asserted `[EST]`, not validated.
Fix: downgrade novelty claims to `[ASSUME]` with the counter-examples acknowledged; replace "no asset problem" with "no third-party asset-file distribution, same franchise-IP review as the rest of the app"; add a 1-week user-validation step (5–10 target users, concept test including "DMO maps vs lore places") before T2; add a competitive check; position the atlas explicitly relative to Lorebook.

**F7 — MAJOR — The content-source inventory for T1/T2 is missing: the app's lore ledger does not contain the geography the candidates need.**
Location: README §2.2 L157–158, §5.2 L319, §8.1 L494–498, Appendix E L694–715; `LORE_FOUNDATION.md:9–37`.
Problem:
1. `LORE_FOUNDATION.md` names the network-built world, Iliad/Olympos XII, Shambhala/Genshi/Ashino/Yomi, Witchelny, the Dark Area/Cocytus. It does **not** contain File Island, Server Continent, Yokohama Village, DATS Center, D-Terminal, Ice Area, Digimon Maze etc. — i.e. every candidate in Appendix E except the general lore regions. "The geography is already sourced `[REPO: LORE_FOUNDATION.md:29–37]`" is therefore partly false for the candidates, and the cited line range doesn't even include Witchelny/Dark Area (lines 26–27).
2. T1's "spawns" have **no source at all**: the app's spawns are GPS-generated (`world/WorldRepository.kt:41,74–105`), not DMO spawn tables. They would come from the wiki/client metadata — the F2 problem.
3. The README's own §2.2 says "official lore for geography" but does not say *which* official sources (Digimon Web? the anime? the app ledger?) or whether the app's source discipline (`LORE_FOUNDATION` is a source-first ledger) extends to them.
Fix: add a per-candidate-zone source table (source, type, confidence, review status) and extend/cite the ledger for any new official source; either drop "spawns" from T1 v1 or move it under C1-lite; correct the LORE line range and add File Island/Server Continent/DMO-only places to the "needs new sourcing" column.

**F8 — MAJOR — The option set misses the cheapest recognisability path (sanctioned promotional / 2D map imagery) and the sequencing of the first-party pitch contradicts the critical path.**
Location: README §5 O1–O8 L283–292; §10.3 P5 L608; §10.4 L616–621.
Problem:
1. The ladder jumps from own-art to untraced-layout reference to a full first-party client-asset program. It skips **O4.5/O7-lite: GameKing-authorised official promotional art, press-kit screenshots or official 2D map images** (DMO website/guide/marketing assets) used in an atlas or diorama. For an employee-adjacent request this is a plausible, far cheaper route to "I recognise this place", and it does not require NIF conversion. It still needs written permission, but from a different, more attainable rights bucket (marketing/companion use) — a question for C1/C4 rather than the client-asset chain.
2. The **first-party pitch is gated on P4 success** ("P5 — if P4 succeeds"), even though the doc says C1 and the rights chain are the critical path and the only route to the requester's actual goal. If the pitch waits ~6–12 months behind P1–P4, the requester cannot learn whether the real feature is possible until after the substitute has been built. The C1/conversation should start at P0 in parallel with T1; P5 should be "internal pitch + C4 licensor path" with a deadline, not a reward for P4.
3. A cheaper first step is available: a **1-week static/2D atlas MVP** (no T1+ renderer, no Room 57) to test demand before committing 2–4 weeks.
Fix: add O4.5/O7-lite to the matrix with its legal owner; move the internal pitch/C1 escalation into P0/P1 with a deadline; add the 1-week demand test as P1a; state explicitly that the doc's recommendation is the best *safe* plan, not the best plan for fidelity.

**F9 — MAJOR — Gates and decision asks are not fully falsifiable or answerable; owners, adjudicators and stop-loss are missing.**
Location: README §10.1 L568–582, §10.2 L584–597, §11 L626–643.
Problem:
1. **A4 gates p95 ≤33.3 ms (= 30 fps)**, while recon §7's frame target says "60 fps interactive / 30 fps idle". The interactive/60 fps requirement is never gated, so Spike A can pass at 30 fps while the document claims 60 fps active.
2. **B2 "recognisability ≥4/5 internal blind test"** has no protocol: panel size, reference images, blind procedure, "not-a-copy" check, or who adjudicates. A gate that can be passed by the author alone is not a gate.
3. "Credible fix" (kill rules A and B) is undefined — it lets a failing spike be kept alive by judgement.
4. No gate has an **owner, date or adjudicator**; "Product review + KPI baseline", "Spike A gate", "Gate 2: content budget + rights owner" are named but not assigned.
5. **Q1 offers "user-supplied-at-runtime" as a permitted universe**, although §5.1/§6.3 say it is not a safe harbour and is an employee conflict. Asking the company to choose it invites the exact decision the doc says is unsafe.
6. **Q7 mixes App-Bundle install relief with the APK budget basis**; the relief does not create universal-APK headroom unless the owner changes the budget basis (which the doc itself says requires approval).
7. Missing asks: written approval/declaration for the unofficial project (and consequences if refused), a spend cap and stop date, who accepts residual own-art franchise-IP risk, and publication/store-listing review.
Fix: add Owner/Date/Adjudicator columns to A1–A5, B0–B6 and P0–P5; split A4 into 30 fps idle and 60 fps interactive; define B2's protocol (≥5 blind reviewers, fixed reference set, "recognisable but not a reproduction" two-part pass); define "credible fix" as "a written plan with a named owner and a re-test date"; reword Q1 to remove user-supplied as approvable and add "nothing published until C1-lite"; add the approval/spend-cap/residual-IP asks.

**F10 — MAJOR — Commercial and organisational substance a decision-maker (and their manager/legal) will demand is absent.**
Location: README §8.3 L528–537, §9 L541–562, §10, §11.
Problem: no FTE/staffing, artist cost, counsel cost, dependency list or critical path; `[EST]` ranges are used as if resourcing were free. The success KPI "**zero takedown/IP complaints**" is unmeasurable and uncontrollable (it depends on third parties). The L/I scales are undefined. The risk register omits risks that are discussed in prose but not scored/managed: trademark/brand risk in store metadata, Google Play IP/DMCA process, jurisdiction, HR declaration/disciplinary risk, artist availability, wiki/database rights (F2), and the possibility that the employer instructs the project to stop. There is no "what has been spent / what NO costs", no decision-reversal path, and no statement that **no counsel has reviewed this document**.
Fix: add a one-page cost/staffing/dependency table (with money and FTE), define L/I, add the missing risks with owners/mitigations, replace "zero complaints" with a leading indicator (e.g., quarterly IP-complaint review), add a stop-loss/spend cap and a "if the answer is NO" paragraph stating what is already committed and how it unwinds, and state the no-counsel-review status explicitly next to the verdict.

### Minors

**F11 — MINOR — Tag inflation and small mis-citations.**
- §3.1 L182: "**≈66×** the whole release-APK budget `[REPO]`" — this is arithmetic on a `[NOTE]` figure, and 66× only holds if 10 GB = 10 GiB (10,240/155.5 = 65.9); with decimal GB it is ≈61×. Re-tag `[NOTE→arith]` and state the unit convention.
- §3.4 heading L217 calls the note's client-content claims "**A fact the note does establish**" — everything `[NOTE]` is unverified; "established" is too strong (and the bullets build conclusions on it).
- Appendix C L678 tags HEAD headroom `[REPO]` though the doc/recon call it an estimate; use `[REPO+EST]`.
- §5.2 L320 cites `LORE_FOUNDATION.md:29–37` for Witchelny/Dark Area/Cocytus; those are lines 26–27. §4.1 cites recon §2.1 for triangle/texture counts that are in recon §0.2/§4.1. §6.4's `PRODUCT.md:50–58` citation supports imported content/source-first discipline, not "needs the same review".
- Several `[EXT]` tags point to recon sections rather than `[S#]` (e.g. `[EXT: recon-context §3.2]`); acceptable, but the legend promises `[S#]` and the trail should be uniform.
Fix: re-tag the above; prefer `[EXT: S# (recon §x)]`; drop "fact"/"established" language for `[NOTE]`.

**F12 — MINOR — recon-technical internal errors that the README inherits or should not lean on.**
- `recon §4.2` says "Largest bundled scene is 3,676 tris / 2 textures"; measurement gives **4,159 tris / 4 textures** (`digi_farm_3d.glb`) — §0.2 and the README's 1,590–4,159 range are right; §4.2's "largest" is wrong (it is the Colosseum specifically), which matters because §4.2's "≫4k tris" threshold is already crossed.
- `recon §7`'s texture budget "≤4 PNG ≤1024² (≈ ≤6 MiB decoded)" is arithmetically wrong: 4 × 1024² ARGB_8888 = **16 MiB** before mipmaps. The proven decode ceiling is 10–16 MiB, so the proposed zone budget sits *at* the ceiling — a real risk the sentence hides.
- `recon §1.7` cites `BASELINE.md:44–45` for the emulator rule (the rule is at 31–33; 44–45 is Gradle memory).
- `recon §2.3` describes the tree delta as "32 added / 25 removed" but omits one **size-changed** file (`environment_themes/manifest.json`, +75 B); the net +5,950,108 B is still right only because of that +75.
- `recon §2.2` "other" row 0.27/0.08 MiB vs measured 0.28/0.09 MiB (rounding), and `recon §7` / §2.4 cite stale README lines (F5).
Fix: correct these in recon-technical (or annotate) before the pack is attached to the decision; state the zone texture budget in MiB and compare it to the 10–16 MiB proven ceiling explicitly.

**F13 — MINOR — Tier-ladder and product-mechanic details.**
- T1+ includes a "**first-person**" viewport, but the app's first-person radar is a **Debug-only legacy renderer** (`DESIGN.md:543–546`; recon §1.3). Promoting it is work not costed in "+1–2 weeks". Either scope T1+ v1 to isometric only or cost the promotion.
- "GLB ≤2.76 MiB" is the largest *file*; the largest *triangle* scene is the Digifarm (4,159 tris, 0.52 MiB). State the proven envelope per dimension ({file ≤2.8 MiB, 1.6k–4.2k tris, 2–4 textures, unlit, whole-file load}) instead of one number that mixes them.
- The lore-derived **link-stability mechanic** is a good idea, but DMIA is a horror/tragedy state in canon (mind lost, vegetative body); using it as a session timer needs tone guardrails: don't gamify DMIA, don't monetise limit extension, don't imply the user's real mind is at risk, and don't contradict the app's own ledger note (`LORE_FOUNDATION.md:41–47` — Mindlink is not a synonym for NFC). The doc has the lore, not the guardrails.
- "Not a datamining surface" is in tension with a wiki-sourced atlas; define a visible provenance/attribution policy (source per row, "official/lore/community" labels) so the claim is true and checkable.
Fix: add these four points to §2/§7/§8.1 and the non-goals.

**F14 — MINOR — Structure/decision-usefulness.**
- The document is ~715 lines for an eight-question decision; there is no one-page executive summary answering the literal ask ("**NO real-DMO-asset path without first-party written permission; GO on the substitute**"), the recommended action, cost/owner, top three risks and next step. "Conditional GO" can be misread as a GO on DMO maps.
- Appendix A's "trust" table is not propagated into the verdict table or risks (a `[NOTE]`-derived figure and a measured `[REPO]` figure look equally authoritative), and recon's per-claim `high/medium/low/UNVERIFIED` confidence is dropped from README.
- No revision log, no "reviewed by counsel: no" marker, no record of the supplied note's date/author.
Fix: add a one-page decision summary + confidence column; carry recon confidence into the README evidence tables; add revision/date/author/reviewer fields.

## Direct answers to the six review questions

1. **Evidence fidelity — partially acceptable, not yet decision-grade.** >20 repo citations I re-read were accurate (the technical recon deserves credit), and the 8 external sources I re-fetched matched the recon's characterisations with the exceptions in F1/F3. The problems are concentrated exactly where the doc is least checkable: the `[NOTE]` chain, the 245/863/29,469 provenance, the wiki-licence claim, and a handful of tag upgrades.
2. **Arithmetic — mostly consistent but not internally reproducible.** The APK/asset/headroom math, Colosseum 2.76 MiB, 1,590–4,159 tris, Room 56→57 and ≈42 named locations all check out (42 counted from the wiki's raw page). The effort model does not (F5), and the recon's 3,676/4,159 and 6 MiB/16 MiB contradictions (F12) must be fixed.
3. **Legal — right conclusions, overconfident calibration, missing inputs.** (a) Redistribution is correctly treated as blocked *absent written permission*; the doc wisely avoids saying "illegal" absolutely, but should separate copyright, contract/EULA (never read) and trademark, and note jurisdiction. (b) The user-supplied/PC-companion path is correctly rejected as a safe harbour; the OpenIV/Nintendo precedents are real but weaker than presented (OpenIV returned after backlash; Nintendo's guidelines concern sharing, not extraction) — cite them as "risk, not licence" and add the interoperability question for counsel. (c) Employee cannot self-authorize: correct, and it should be promoted from an `[ASSUME]` into its own legal finding, because the employment contract is a duty relationship with one licensee, not a licence from Toei/Hongo/Bandai/Move Interactive. (d) The 245-map source **must not be used**, but the documented chain does not prove it came from Digital Realm (F1) — the safe and stronger statement is "unverified note, private-server-adjacent, not usable for planning". The biggest missing risks are EULA/ToS (G2), jurisdiction, trademark/store metadata, and the wiki/database rights; the doc's "not legal advice, counsel must confirm" framing is right and should be repeated at every legal conclusion rather than once at the top.
4. **Viability — the verdict follows from the evidence for the safe path, but the option scores and effort model do not fully follow.** The tier ladder is coherent and correctly ordered; the "four missing capability families" are well evidenced; "own-art Atlas now + pilot zone + gated faithful spike" is the best *safe* plan and I did not find a cheaper safe substitute — but the doc misses the **GameKing-sanctioned promotional/2D map asset** middle path and delays the first-party pitch behind P4 (F8). The recommendation does not address the requester's literal desire and should say so.
5. **Product coherence — mostly yes, with two caveats.** Mindlink as designed avoids a second game client (no combat/netcode) and the non-goals are explicit; but a wiki-sourced atlas weakens the "not a datamining surface" claim (provenance policy needed), and the DMIA-derived timer needs tone guardrails. The value proposition is plausible for a Digimon-lore audience but unvalidated for the app's actual users and for DMO nostalgia (F6).
6. **Decision usefulness — good raw material, not yet answerable.** Spikes A/B are the right instrument, but gates lack owners/dates/adjudicators and one 60 fps criterion (F9); the decision asks include an unsafe universe and omit the declaration/spend-cap/residual-IP questions (F9/F10); and the cost/staffing/legal-review status a manager and legal team will demand are absent (F10). A decision-maker can approve *Spike A with a spend cap* after F1/F2/F5/F9 are fixed; they cannot approve Atlas publication before F2.

## Claims I could not verify

1. **Everything tagged `[NOTE]`** — Pack01 ≈10 GB, Pack03 ≈70 MB, ≈245 maps, the 2024 stolen-bundle allegation, the "public v487 client source", the Pack/BIN tooling descriptions, the note's §5 recommendation. The supplied note `DMO_private_server_resources.md` is not in the workspace and no excerpt/source log is attached.
2. **"863 Digimon / 29,469 items"** and that Digital Realm is their source — absent from both recon files; the 245→Digital Realm link is itself `UNVERIFIED` in recon-context §2.1.
3. **Digital Realm's private-server self-description** — recon-context S21 is a high-confidence recorded observation, but I did not re-fetch the private-server site (quarantine rule).
4. **DMO's actual EULA/ToS** — extraction, reverse-engineering, reuse and jurisdiction terms; recon-context G2 says the page body was not readable.
5. **GameKing's contractual rights, the exact contracting entity** among Move Games / Movegames Inc. / MOVEINTERACTIVE / Move Interactive, and the **requester's employment/NDA terms** (G1/G3/G8).
6. **That "almost none of those [lore] places has ever been realised as an explorable 3D place in a game"** — not verified; File Island/Server Continent appear as explorable settings in Digimon World (1999) and later games, and DMO itself renders them.
7. **Any DMO client/asset fact** — no DMO artifact was inspected by this review or by the recon; map format, NIF dialect, encryption, scene counts remain unknown (G4/G5).
8. **The wiki content licence as applied to the map data/images** — I confirmed the wiki's `rightsinfo` is CC-BY-SA, but not that this covers DMO-derived images/data, nor the attribution/ShareAlike implications for an MIT app (counsel question).
9. **The exact HEAD-APK headroom** — APK was not rebuilt (recon admits); my independent deflate projection gives 3,325,956 B of added compressed assets vs recon's 3,371,094 B (Δ ≈45 KB → 10.21 vs 10.17 MiB). Conclusion robust; the byte-level figure is not reproducible without a build.
10. **KTX2 device support, 30-minute soak, on-device frame/memory for any new scene** — no measurement exists (acknowledged by recon).
11. **The S20 "CsNiNode" forum signal** — search-snippet-only, low confidence, not re-read (as recon-context itself says).
12. **"No official DMO API/companion exists"** — absence of evidence only (recon-context flags it).
13. **Financial cost, team size, artist availability, and who would actually approve C1/C3** — not stated anywhere.

## What I would ask before approving

1. **Attach the supplied note** (or a redacted source log with exact URLs) and fix the 245/Digital Realm tagging; delete or source 863/29,469. *(F1 — blocks all review.)*
2. **A written T1 content-source + licence matrix**: field, source, licence, attribution/ShareAlike handling, database-rights position, and a counsel-reviewed "facts/metadata OK" note; or scope T1 to own-lore/factual names only for v1. *(F2 — blocks Atlas publication.)*
3. **Written confirmation from GameKing HR/legal** that the unofficial project and the internal declaration are permitted (or the instruction to stop), plus which entity employs the requester. *(F3/F10.)*
4. **A named C1 role and a written "what GameKing can and cannot authorise" answer** from company counsel, including the licensor/dev chain and the first-party route; C1 cannot authorise DMO art if GameKing does not hold that right. *(F1/F3/F8.)*
5. **A corrected effort/cost model**: engineer-days, artist-days, FTE, counsel cost, spend cap, stop date; all §0/§2/§8.2/§10.3 numbers generated from one table. *(F5.)*
6. **Gate ownership and protocol**: owner/date/adjudicator for A1–A5 and B0–B6; 60 fps interactive gated; B2 blind-test protocol; "credible fix" defined. *(F9.)*
7. **A pre-build validation test** with 5–10 target users (does the atlas/lore-place concept substitute for DMO-map nostalgia?) before T2 spend, and a decision on the answer. *(F6.)*
8. **A store-listing/trademark/publication review** (app name, "Digimon", "Mindlink", screenshots, attribution page, takedown contingency owner) and a "nothing published before C1-lite" commitment. *(F3/F10.)*
9. **Explicit acknowledgement that no counsel has reviewed this analysis** and that C1/C3 must precede any spend beyond Spike A; then approve **Spike A + P1 (iso atlas only)** with the above conditions — do not approve T2 or any real-asset work.

## Appendix — spot-check log (claim → source → result)

| # | Claim in README / recon | Checked against | Result |
|---|---|---|---|
| 1 | Filament 1.76.1 + gltfio + filament-utils `[REPO]` | `gradle/libs.versions.toml:30,103–105`; `app/build.gradle.kts:266–268` | PASS (exact) |
| 2 | compileSdk 37 / minSdk 28 / targetSdk 37; R8 + resource shrink | `app/build.gradle.kts:24,28,29`; `:59–66` | PASS |
| 3 | Four scene kinds; AO off in all four; TAA off; bloom/FXAA/dithering | `HybridSceneProfile.kt:5,16–48` | PASS |
| 4 | No Filament lights/IBL; GLBs have no `KHR_lights_punctual`; scenes unlit/emissive | grep `LightManager/IndirectLight/DirectionalLight`; parsed all 5 GLB JSON chunks | PASS (no matches; no lights extension; Colosseum `KHR_materials_unlit`) |
| 5 | `OfflineArenaManifest` versioned; `schemaVersion==1`, Y-up, XZ plane; validated camera/bounds | `battle/ui/offline/OfflineArenaManifest.kt:41–76` | PASS |
| 6 | Room schema **56**, `exportSchema=true`, `MIGRATION_55_56`; schema JSONs + migration tests checked in | `data/database/AppDatabase.kt:95–98,266`; `app/schemas/.../56.json`; androidTest files | PASS |
| 7 | Type-safe `@Serializable sealed interface AppRoute` + `NavHost` blocks | `navigation/AppRoute.kt:10–63`; `AppNavigation.kt:281–283,360+` | PASS |
| 8 | 5 bundled GLBs; Colosseum **2,893,360 B = 2.76 MiB**; all GLBs 8,069,591 B = 7.70 MiB | measured `app/src/main/assets` | PASS (exact) |
| 9 | Proven class 1,590–4,159 tris; 2–4 embedded PNGs; static/no skins | parsed GLB JSON: radar_plate 1,590; radar_grid 1,782; Colosseum 3,676; digifarm 4,159; resident 4; images 2–4; no skins/animations | PASS — but `recon §4.2`'s "largest 3,676" is wrong (F12) |
| 10 | Assets 125,302,503 B / **13,891 files**; env themes 43 PNGs; Arena 10,705,202 B / 11 files | measured tree (Python-free PowerShell) | PASS (exact) |
| 11 | Release APK **149,019,234 B**; assets 113.82/95.69 MiB; lib 28.88 MiB; dex 12.08 MiB; zip/signing ≈3.3 MiB | opened APK as zip | PASS (exact; "other" row rounding, F12) |
| 12 | Colosseum in APK 2,785,152 B (96.3%); theme PNGs 22,499,616 B (99.86%); species catalog 26.0% | APK per-entry compressed sizes | PASS (exact) |
| 13 | 4 ABIs shipped; arm64 = 7.32 MiB; App-Bundle relief ≈21.56 MiB | APK `lib/*` grouped by ABI (7,679,584 B) | PASS (exact arithmetic) |
| 14 | Recorded headroom 14,034,334 B = 13.38 MiB; tree delta +5,950,108 B raw / +3,371,094 B compressed; HEAD ≈10.17 MiB | tree-vs-APK diff + independent deflate | PASS-with-note: delta components are 32 added / **1 changed (+75 B)** / 25 removed; my deflate gives 3,325,956 B → 10.21 MiB (Δ ≈45 KB) — conclusion robust, byte figure not exactly reproducible |
| 15 | No DownloadManager / asset-pack / PlayAssetDelivery; JSON-only networking; "the app never downloads art" | grep `app/src/main/java`; `tools/hatchery_sprites.py:4` | PASS (no matches; quote exact) |
| 16 | Theme system: 43 baked PNGs, 4 themes × 3 scenes, unknown material fails bind; generator SHA-256 + `--check` | `environment_themes/` (43 PNGs), `AppTheme` 4 values, `EnvironmentThemeController.kt:149–152`, `tools/ENVIRONMENT_THEMES.md:8–38`, `build_environment_themes.py` | PASS |
| 17 | GPS radar: spawns around real location, OSM biome; no authored world | `world/WorldRepository.kt:41,74–105` | PASS |
| 18 | Perf budgets: battle p95 ≤16.7 ms, farm 60/30, APK ≤155.5 MiB (163,053,568 B), no OOM/30-min soak; device SM-A346M API 35; battle p95 recorded 13.3; farm p95 29.8 in 3-s window; no soak | `docs/adr/0005-performance-budgets.md:20–45`; `docs/performance/BASELINE.md:29–33,70–76,313–314,317–325` | PASS (BASELINE 44–45 mis-cite, F12) |
| 19 | KTX2 unproven/no-go; no KTX2 asset or build step | `docs/refactor/SPIKES.md:40–60`; `PHASE5_DECISIONS.md:65–67`; asset scan | PASS |
| 20 | LORE ledger geography; PRODUCT.md imported card data/sprite library; "geography already sourced" | `LORE_FOUNDATION.md:9–37`; `PRODUCT.md:50–58` | PARTIAL: ledger covers Iliad/Shambhala/Witchelny/Dark Area/Cocytus, **not** File Island/Yokohama/DATS/Ice Area/Maze; §5.2 line range mis-cited (F7/F11) |
| 21 | 31 cited sources; S1 engine/launch/remaster; S3 2015 transfer; S4/S5 private-server notices; S10 Mindlink/DMIA/terms; S11 observation-vs-perception; S22 ≈42 locations | re-fetched S1, S3, S4, S5, S10, S11, S22; counted 42 named locations on the raw Maps page; wiki `rightsinfo` = CC-BY-SA | PASS on facts; **FAIL on two paraphrases**: S5 does not say private-server users/promoters are banned (fraud sentence); S3 quote drops "will be" (F3) |
| 22 | S26 "captures, not assets"; used as store-policy evidence | re-fetched Nintendo guidelines | PARTIAL: source says what recon says, but it is a video-sharing fan policy, not store policy (F3) |
| 23 | Tree `397ef846` + 110 uncommitted entries; no files built/committed | `git rev-parse --short HEAD`; `git status --porcelain` count | PASS |
| 24 | "≈42 named locations / 32 location articles"; "245 inadmissible" | raw `dmo.fandom.com/wiki/Maps`; category API described but not independently counted; recon §2.1 vs §9 | 42 PASS; **245 provenance UNVERIFIED** (F1) |

---

## Status note (Lead, 2026-11-14)

This review targets README snapshot `070C26E9…442B64` (rev 1). The author revised the
analysis to **rev 2** after receiving these findings; the addressing changes are itemised in
README Appendix F ("Revision and review log"). In summary: F1–F2 (provenance chain and T1
licensing basis) were rewritten as tagged, confidence-separated statements plus a per-field
T1 source/licence table and a C1-lite gate; F3 legal calibration was corrected against the
exact S3/S4/S5 wording and extended with a legal-inputs table; F4–F5 scoring and effort
models were rebuilt into single canonical tables; F6–F8 product claims were downgraded and
the demand test, promotional-asset option and P0 pitch sequencing added; F9–F10 gates gained
owners/adjudicators, a 60 fps criterion, a blind-test protocol, a cost/staffing table, spend
cap and published-risk gaps; F11–F14 tags, envelope, texture ceiling, DMIA guardrails and
decision-summary items were fixed. The reviewer did not re-review rev 2; this note is the
author's account, not a second opinion.
