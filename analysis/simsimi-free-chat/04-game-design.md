# 04 — Free-Talk DigiLine: player-facing design

**Status:** game-design proposal (pre-implementation) · **Scope:** a SimSimi-*rhythm* free-text conversation with the player's own partner, inside Tamers Lab's DigiLine. It does **not** re-open the legacy LLM path (D10) and does not add a second AI system.
**Inputs:** `DIGILINE_CHAT_SYSTEM_PLAN.md` (§0.1 D2–D4/D9, §3, §5.4–5.5, §6, §7, §11 F11/F19/F21, §12), `PRODUCT.md`, `LORE_FOUNDATION.md`, `DESIGN.md`, `ChatScreen.kt` / `DigilineSceneCard.kt` / `ChatScreenController.kt` / `farm/ui/digiline/DigilineScreen.kt` / `world/ui/WorldChatScreen.kt`, the SimSimi research brief (`simsimi_style_chatbot_research.md`), SimSimi history, and Play policy pages.
**Legend:** **[REC]** = my recommendation, implement unless overruled · **[OPEN]** = product-owner decision. Risk labels are deliberate.

**Hard product-owner constraint, first-class:** the mode must read unmistakably as *conversation with YOUR Digimon inside the Digital World*, never as a generic assistant or a SimSimi bot. §2, §3 and §8 are the enforceable parts of that constraint.

## 0. Pitch and the one tension this design resolves

> **Papo** (pt-BR UI label; "Talk" in en) is the moment your partner has your attention: a short, in-character exchange about its world, your shared work, and what it notices — and when it does not know what to say, it stays in character and hands you an authored DigiLine scene instead of guessing.

The tension: SimSimi is remembered as "it replies to *anything*"; this app is offline, authored and lore-grounded (plan §3 principles 1–3, 7; `PRODUCT.md` source-first contract). Resolution: **intent-first authored topic packs + deterministic retrieval + diegetic handoff**. Variety comes from per-intent, per-personality, per-context *pools*, not from generating text. The partner's ignorance is authored content, not a failure message. This is the whole feature in one sentence: *a partner's voice, not a general-purpose oracle.*

## 1. Pillars and anti-goals

| # | Pillar | Means (testable) |
|---|---|---|
| P1 | **In-world first** | Every line could only be said by a Digimon to its Tamer in the Digital World; the UI is a DigiLine link, not a messenger. |
| P2 | **Partner, not oracle** | It never claims knowledge it has no authored source for. "I don't know" is in character, never an error toast. |
| P3 | **Personality you can hear** | Same species at different 16-type/stage/mood says different things; a blind reader can guess the personality band. |
| P4 | **Short, alive, a little cheeky** | 1–2 lines, ≤ ~120 chars, dry humor allowed, no filler, no stock-assistant warmth. SimSimi's *tempo*, Digimon's *voice*. |
| P5 | **Authored truth** | No invented lore, no invented shared memories, no real-world facts; offline; citations for species claims (plan §5.4, `LORE_FOUNDATION.md`). |
| P6 | **Failure is content** | Every input ends on a line, a chip row or a real authored card — no dead ends, no "try later" wall. |

**Anti-goals:** general-purpose Q&A / search; assistant tasks ("set a reminder"); open-domain coverage promise; user-taught pairs or crowdsourced answers (removes SimSimi's abuse vector); fake typing/thinking; personality shifts from free text; rewards/drops/currency; pay-per-message or chat energy; parasocial guilt/jealousy/deception; claiming to be "AI"; romance/waifu framing; speaking for other Digimon or for the player; free talk on wild contacts in MVP.

**Bounded promise (must appear in onboarding and Help, not fine print):** it talks about itself, its world, your team and what you have done together; it may not understand everything; it has no internet, no memory of outer life, and will not answer schoolwork, news, medical, legal or personal-data questions.

## 2. In-world framing and contextual voice (P1/P2/P3)

**2.1 The channel.** DigiLine is already rendered as an authored scene surface, not a messenger (`DigilineSceneCard.kt` comment: titles/phase badges deliberately absent; `DESIGN.md` roxo/ciano, Oxanium, cut-corner, quiet elevation). Free talk inherits that language: a **link header** for the thread ("DigiLine · LINK" plus the partner's species and a qualitative state), partner rows flagged with a small species glyph / tone tag, player rows as Tamer transmissions, and a **terminal input row** with the cyan send signal — never a rounded chat-app composer with emoji tray. **[REC]** no timestamps or read receipts inside the conversation; the transcript is a record, not a messaging log.

**2.2 Voice = species × stage × personality × mood.** Voice dimensions already exist in code and must drive authoring, not ad-hoc adjectives: `DigimonRoleplayVoice` (energy/formality/playfulness/initiative) and `DigimonSocialProfile` (`domain/personality/`). Stage register (tiers from D12): T1 Baby/Child = short, concrete, one thought; T2 Adult = banter, opinions; T3 Perfect = reflective, weighed; T4 Ultimate = sparse, heavy. Species flavor: only cited facts from `lore_species.json` (attribute, field, signature move, evolution context) — never biography, never invented memories (`LORE_FOUNDATION.md`: conversation is an interaction record, not proof of history). All other species must read perfectly with *zero* species-specific lines.

**2.3 Context variables the writer must support** (all already stored; this is the authoring contract, not new telemetry):

| Variable | Source | How it may change a line | Budget |
|---|---|---|---|
| Species / nickname | species catalog, `UserCharacter` | self-reference, cited species flavor | always available; ≤1 species-specific line per session |
| Stage tier 1–4 | D12 resolution | register + topic depth | flavors every line; ≤1 stage topic per session |
| Personality type 1–16 (+4 cores) | `DigimonPersonalityTraits` | primary selection axis + signature intents | always |
| Mood band LOW/SETTLED/POSITIVE | `PartnerCondition.mood` (D2) | warmth, initiative, gated lines | ≤1 mood-specific line per session |
| Recent real event (battle win/loss, evolution, injury, item, quest stage) | existing event/reaction records | opening callback, factual only | ≤1, first turn only |
| Pending authored scene / quiz due | scheduler state | offer the real card as handoff | ≤1 card per session |
| Known wild contacts (phase 2) | `WildRelationship` | rumor lines + radar hint | ≤1 per session |
| Locale | app setting | language + terminology only | always |

**Precedence:** real-event callback > species > personality signature > mood > neutral. **At most one "special" context per reply**, so replies never read patchwork. **[REC]** a missing variable silently falls back to the next one; the partner must never name state the player cannot see (no numbers, no internal ids, no "your mood value").

**2.4 Out-of-world input is handled diegetically.** The partner has no Earth frame. Inputs about pop culture, news, other games, schoolwork, "what's the weather": answer with authored curiosity/confusion ("Mundo real? Eu só conheço o Digital World. Me conta do seu."), then move to an in-world topic or handoff. Meta/implementation questions (AI, prompts, API, bugs): never discuss internals, never confirm or deny software in an assistant voice; non-committal in-world deflections are authored; real bug reports belong to Settings/Help. **[OPEN]** exact policy for a direct "are you real / are you a robot?" — I recommend a short authored line that stays inside the fiction without asserting real-world facts ("Sou seu parceiro. O resto não importa agora"), plus a plain Help note that the partner is an authored character. Do not joke about surveillance or sentience.

**2.5 Anti-generic guardrails (content lint, blocking).** Ban and reject with a validator error: "As an AI", "I'm just a program", "I can help with that", "How can I help?", "Let me know if", "I'm sorry, I can't", "based on", "não tenho acesso", "estou aqui para ajudar", "posso ajudar com isso", "como posso ajudar", "sou uma IA", "não sou um robô", "me desculpe, mas não posso"; polished summaries, numbered lists and task offers; emoji-only replies; English stock phrases inside pt-BR lines (outside approved move names). **Review gate:** every line must fail the "could a generic assistant have said this?" test — if the same sentence works from a helpdesk bot, rewrite it with a Digimon-specific stake, image (field, data, evolution, training, Radar) or personality angle. **[REC]** Help text and store copy say "conversa escrita", never "chatbot de IA"; the debug legacy LLM stays hidden and labelled as legacy if ever exposed (D10).

## 3. Themed content domains (writer brief)

| Domain | Intents (examples) | Rules |
|---|---|---|
| Self / species | who are you, your attribute/field, favorite move, what you're like | cited species facts only; species ≠ individual |
| Tamer–partner bond | what do you call me, do you trust me, why do you stay, promises | warm, not romantic; no exclusivity/jealousy; no numeric bond |
| Digital World | data, Digitama, fields, other places, what it feels like | generic in-world, no invented geography (LORE_FOUNDATION) |
| Training & battles | last battle, sparring, rivals, injury/recovery, practice | may reference real stored event only |
| Evolution | stage, what comes next, feeling of changing | no promised evolution; no invented memory of past forms |
| Food / Bits / items | what you like to eat, Bits, items found | never promises items or awards them |
| Wild Digimon & Radar | rumors, negotiations, joining, recruiting | phase 2; deep link only to a real available target |
| Device / link | the watch, DIM/BEM, Vitals, DigiLine itself | in-world; never exposes developer terms |
| Quiet moments | rest, day/night rhythm, mood check-ins, "how are you" | safest fallback domain; must not claim real-time awareness |
| Your team | other partners, DigiFarm residents | neutral, no comparison or jealousy |
| **Species-specific flavor** | overlay on self/training/evolution/quiet intents for authored species | top ~30 owned species in MVP; long tail uses universal lines |

**Excluded (authoring + moderation ban):** news, politics, religion, sports, celebrities, other games/IPs; schoolwork/translation/factual answers; medical, legal, financial or crisis advice; sex/romance/waifu; violence detail/gore; gambling/loot; requests for the player's real identity, address, school, phone or photos; meta/implementation; commands to act in the real world. Also excluded: asking the player to keep the partner "secret", comparing the player to other Tamers, guilt for leaving.

**Volume (MVP, per locale):** ~45 universal intents × 4 cores × 2–3 variants ≈ 450 lines; 16 types × 5 signature intents × 2 ≈ 160; top-30 species × 4 ≈ 120; boundary/no-match/handoff/chips ≈ 80 → **~800 authored lines (~60–90 KB)**, intent-first so match cost is intents, not phrasings. Expansion target ~1,500. **[REC]** validate per-intent pool ≥2, per-personality coverage, and top-40-intent coverage of the playtest message set ≥90% before enabling the mode. Content ownership remains the unresolved R11 blocker; free talk cannot ship without a named writer/reviewer. Precedent that this pipeline is realistic: `world/ecosystem/SocialOpenings.kt` already ships authored personality lines in en/pt-BR/ja.

**Content record sketch (new pack collection, e.g. `talk.json`; draft, not a spec):**
```json
{"id":"ft.bond.trust.1","intent":"bond.trust","variants":["…", "…"],
 "tiers":[1,2,3,4],"types":[1,2,3,4],"cores":["PHILANTHROPY"],
 "moodBands":["SETTLED"],"speciesKeys":[],"citation":null,
 "chips":["ft.chip.what_matters"],"handoff":null,"tone":"warm","maxChars":120}
```
Rules: ≥2 variants per intent; `speciesKeys` only with a `citation` from `lore_species.json`; `handoff` must name an existing card key; ≤3 chips; response text ≤2 sentences. Matching technology is the tech workstream's call, but the design requires the research brief's staged-retrieval contract (confidence threshold + authored fallback, §6.4/§8) and the plan's persist-before-display rule (§3.2).

## 4. Placement and session flow

**4.1 Where it lives.** **[REC] Owned DigiLine threads only** (`ChatScreen.kt`), coexisting with the authored card system. Wild contacts stay pure authored negotiation/persuasion (`WorldChatScreen.kt`): free talk there would dilute D6 consent stakes, D11 departure tells and the one-quest rule. Farm/group chat is out of scope. A Home/partner-deck shortcut may reuse the existing DigiLine entry; no new nav surface. **No bond/stage gate in MVP:** the mode is available on first open (discovery matters more than ceremony; the one-time notice is the onboarding), and the authored greeting already respects mood/stage so a new partner sounds appropriately distant rather than overly familiar.

**4.2 Coexistence with authored scenes.** A pending question/quiz/reaction card takes the interaction slot; the composer is hidden while a scene is open, and returns after it resolves or is dismissed (one channel at a time — matches the current `ChatScreen` layout, where the scene card sits above the composer area). Free talk never suppresses, reorders or replaces scheduled scenes (plan §6.1). If the player leaves mid-session, nothing is queued; free talk generates no unread badge and no notification.

**4.3 Session structure.** Entry: thread open, no active scene, content pack valid → partner opens with one authored greeting conditioned on §2.3 and shows ≤3 **topic chips** (e.g. "Como você está?", "Pergunte algo", "Fale do Digital World"). Turn loop: player message → play gates (§7) → normalize → intent match → confidence threshold → seeded variant pick **persisted before display** (plan §3.2/§11 F10) → partner line, optionally one handoff chip/card. Exit: Back always exits; no forced goodbye, no guilt line; session ends and the once-per-session mood credit is applied. **No fake typing:** replies are instant; at most a 150 ms cyan link pulse as send-acknowledgement — never a "typing…"/thinking indicator, never an artificial delay. **[REC]** soft session length: after 10 partner replies the partner offers an authored wrap card (a question card if due, otherwise a "fazer algo juntos" topic), so sessions end on authored content rather than on exhaustion.

**Session state:** `Idle → Greeted → Turn* → (Handoff | Boundary | Wrap) → Closed`. Persist `sessionId/startedAt` on entry and `intentId + variantSeed` for each line before rendering; on force-close, reopen at `Idle` with no pending promise (nothing displayed is lost because no line is generated ahead), so there is nothing to reroll. Mood credit applies once on `Closed` **only if** the ≥4-message rule held; closing after a boundary applies nothing.

**4.4 Handoff matrix (no dead ends).** Low confidence is handled in escalating authored form, not with one generic apology:

| Situation | Partner move |
|---|---|
| 1st miss | in-character curiosity line + one clarifying question |
| 2nd consecutive miss | line + 3 topic chips from the current personality's safe pool |
| 3rd consecutive miss | **handoff card**: the pending authored question/quiz if one exists, else a "tell me about…" topic card with 2–4 authored options |
| Asks for authored content (battle/quest/wild) | offer the real card/link **only if the target currently exists**; never promise features |
| Sensitive (S2/S3, §7) | boundary line + calm session end; recoverable next session |
| Content pack unloadable / locale missing | hide the composer entirely; existing idle hint stays (fail closed) |
| Local DB failure | one in-world retry line + retry action; no technical copy |

## 5. Systems coupling — conservative defaults

| System | Read | Write from free talk | Guard |
|---|---|---|---|
| Mood = bond (D2) | band only (LOW/SETTLED/POSITIVE), never the number | **+1 once per session** if the session had ≥4 player messages; **max +2/day/individual**; never negative; never on a boundary/S2–S3 session; feedback is qualitative only (no "+1" toast) | no mood farm; battles/injuries/quiz remain the main movers (F12/R5 unchanged) |
| Personality (D3) | yes — selects voice/intents | **none in MVP** — no type change and no `PersonalityTendencyEntity` points; shifts happen only through authored question cards reached by handoff | avoids F11 farm and preserves D3 hysteresis/cooldown/native-sync integrity |
| Native save sync (D4) | may show the existing sync label | none | no free-talk writes → nothing to sync, nothing can desync |
| Transcript | — | `ChatMessageEntity` rows with `kind = "free_talk"` (+ small payload for chips/card) | clear per thread; deletion and previews keep working |
| Cadence | — | 10 replies/session soft cap, 1 msg/1.5 s, 160-char input | no background timers, no push, no catch-up spam (F18) |
| Anti-reroll | — | intent + variant seed persisted before display; **no regenerate button** (normal mode) | plan §3.2/F10; legacy resend stays debug-only (R9/D10) |
| Progression/economy | — | zero XP, Bits, items, drops or quest progress | kills "chat to farm" |
| Wild contacts | — | none (MVP) | wild stays authored (§4.1); phase 2 may route flavor through `PersuasionEngine` topics only |

**Memory/continuity.** Session-local only: the partner may reference the last 2–3 player topics within the session via authored topic tags. Across sessions, continuity may use **only real stored facts** (last battle, evolution, quest stage, nickname) through authored templates — never invented shared memories, never claims about what happened off-screen, never a "memory" that changes dialogue/quest state. Persist: transcript, per-intent counters, last session timestamp, daily mood-credit counter. Do **not** persist inferred preferences or new "personality facts".

**Relationship stages.** No new stat: mood bands are the relationship presentation (Low = guarded/terse; Settled = familiar; Positive = warm and takes initiative). Stage/age variants reuse the existing per-individual stable species variant (R8) so the same partner always sounds like the same partner.

## 6. Retention, progression and economy

**Honest hooks [REC]:** a daily authored **assunto do dia** chip (1/day, no reward, expires at logical midnight); real **context callbacks** to what actually happened (last battle/evolution/quest — the same data the partner already reacts to); **discovery** via authored rumors that point at a real available radar/quest target (phase 2, only if the link exists); **cosmetics** — deterministic milestone keepsakes (a DigiLine frame/background or a transcript "card" at bond/session milestones), never purchased, never affecting stats; **seed handoffs** to question/quiz/negotiation content; a diary line that says only "today we talked about X" when true (interaction record, not lore — `LORE_FOUNDATION.md`).

**Avoid:** chat-only bond decay or absence penalties (F12-style punishment, kid-adjacent audience); streaks that punish a missed day; message limits/energy/pay-to-chat; any currency, loot or randomized reward from chatting; "regenerate reply" rerolls; parasocial pressure ("you never visit", "don't leave me", jealousy, exclusivity); fake awareness ("I counted the minutes"); purchase prompts inside the conversation; gating personality/personality shifts behind purchases; ads in chat. Free talk is free, offline, and progression-neutral by design.

## 7. Safety, moderation, ratings, privacy

**7.1 Why this is non-negotiable.** SimSimi was banned in Thailand in 2012 after users taught it profanity and political mockery, and suspended in Brazil in April 2018 over sexual language, bullying and death threats, described as "dangerous" mainly for its popularity with children ([Wikipedia](https://en.wikipedia.org/wiki/SimSimi)). Tamers Lab has the same two risk multipliers (Digimon fans skew young; the product is a companion) and none of the mitigation budget (no server ML). The design answer is structural: **no public corpus, no user teaching, no generation, no network**; all outputs are curated and reviewed, and the only open input is the player's text.

**7.2 Threat model.** Player input (PII, sexual, hate, threats, self-harm, jailbreak/meta); authored outputs that could be manipulative/political/advice-like if unreviewed; local transcript storage; young players; store review perception; a debug LLM path accidentally exposed. **Working assumption: the player may be a child.** Therefore no account, no age request, no external links in conversation, no purchases, no data leaving the device, and no attempt to be a counsellor — the partner is a game character, not a guardian.

**7.3 Input gates (all local, versioned with the content pack).** 160 chars, single line, no URLs/emails/phone/social handles; rate 1 msg/1.5 s and 40 messages/session; repeat-spam detector. Lexicon + pattern classifier assigns a severity: **S0** normal; **S1** rude/teasing → one authored boundary line, no mood effect, 3 in a row ends the session; **S2** sexual/hate/threat/adult → neutral in-world shutdown line, session ends, no engagement, no moralizing, raw text not persisted (store a redacted marker); **S3** self-harm/crisis → short, plain, kind line **outside the fiction** ("Não sei como ajudar com isso. Fale com alguém em quem você confia.") + session end; never play therapist, never diagnose, never imply the partner is a crisis service; **PII** (full name, address, school, phone, photos) → never engage or repeat it back, do not persist it, one calm "guarde isso só para você" redirect (end the session if repeated). Fail closed: classifier/pack error → treat as S2, hide free talk if the pack is unusable. **[OPEN]** legal/product review of the exact S3/PII copy, and whether any local help information is shown at all.

**7.4 Output/content lint (blocking validator).** No manipulative/dependency lines, no political/news claims, no medical/legal/financial advice, no lore claim without a `lore_species.json` citation, no other-IP references, no promises of real actions/rewards, no generic-assistant phrasing (§2.5), neutral-gender lint (§8.3), max 2 sentences, no URLs, no PII-request intents. Every line passes human review; a banned-line push update can *remove* records but not add content (content packs ship with the app — plan §4.3).

**7.5 Store/ratings/privacy.** Treat the text field as UGC-adjacent: satisfy Play's **User Generated Content** expectations (user agreement, in-app report of a bad reply, block/mute free talk, moderation contact) even though nothing is publicly shared ([UGC policy](https://support.google.com/googleplay/android-developer/answer/9876937)); keep the app off the **AI-Generated Content** policy path by not generating content and not marketing the feature as AI ([AI policy](https://support.google.com/googleplay/android-developer/answer/13985936)); declare user interaction/chat honestly in the IARC questionnaire and Data Safety form (no collection, no sharing, no network); check **Families** implications if the app ever targets children ([Families policy](https://support.google.com/googleplay/android-developer/answer/9893335)). **[OPEN]** the report route in an offline app (local "hide this reply"/export transcript through the share sheet) and the final rating answers need product/legal sign-off. **Privacy:** no HTTP in the path; transcripts stay in Room; per-message/thread delete already exists — add "limpar conversa" per partner; the user-initiated database export (`settings/ui/controllers/DatabaseManagementController.kt`, `exportDatabase`) currently copies the whole DB, so free-talk transcripts would travel in it — **[OPEN]** disclose it or exclude text from backups; never log player text; no analytics SDK in this path. **No-network proof:** adopt plan §9.1's instrumented test with a fake HTTP server that fails on any request while exercising the free-talk flow, plus the CI grep gate keeping `OpenRouter`/`ChatRepository` out of this path.

## 8. UX, accessibility, localization

**8.1 Composition [REC].** Keep the current `ChatScreen` structure: qualitative state line, history, scene card on top when active, then the terminal input row + chip row. No generic messenger look — the transcript is a **DigiLine link**, and the authored scene card remains the visual "event". Concrete treatment:

| Element | Treatment |
|---|---|
| Thread header | `DigiLine · LINK` + species · stage + qualitative state; never "online", "last seen" or typing status |
| Partner row | transmission row: cyan tick, small species glyph, optional **tone label** from the already-localized personality names (`DigimonPersonalityTraits`: "Adorável", "Superprotetor", "Astuto e ardiloso"…) — shown only when it adds signal, never a number |
| Player row | small "TAMER" label; no avatar bubbles, no read receipts, no timestamps |
| Input | cut-corner `SurfaceElevatedPurple` terminal field, cyan send signal, `VitalButton` semantics, char counter only near the 160 limit; no emoji tray, attachments or stickers |
| Chips | ≤3 authored topic chips, cut-corner, label typography |
| Scene card | unchanged `DigilineSceneCard` (card language is the consistent authored surface) |
| Motion | 150 ms cyan pulse on send; no typing animation; respects reduced-motion |

Disabled states are always explained by the visible alternative (scene card or chips); the field is never dead without a reason on screen. Respect `DESIGN.md`: Oxanium, 48 dp targets, quiet elevation, roxo/ciano, no numeric readings, no color-only meaning.

**8.2 Accessibility.** New partner lines are a polite live region (as scene cards already do), chips ≥48 dp with content descriptions, no timeouts, no typing animation (motion-independent), supports 1.3×/2× font scale, TalkBack reads the tone marker only if it aids comprehension (otherwise `contentDescription = null`), haptic once per reply max.

**8.3 Localization — opinionated.** The repo is EN-authored with pt-BR/ja resource translations (D9), but this feature's value is *voice*, and the audience/docs are Brazilian (`DESIGN.md`, `values-pt-rBR`): **[REC] pt-BR is the reference locale for free talk and must be authored natively, not translated line-by-line; ship pt-BR with EN (or pt-BR first) and treat ja as later.** Flag: this **deviates from D9's EN-MVP sequencing for this feature** and is **[OPEN]**. Rules: neutral gender per plan §5.5 — address the player as "você"/Tamer, no gendered adjectives/nouns for the player or the Digimon, avoid "obrigado/obrigada"; accept Portuguese + common English move/term aliases in matching; no machine-translated humor; line-length budget +20–30% for pt-BR; native review gate. Also fix the existing violation before shipping: pt-BR strings `digiline_demand_bits` ("Ele quer…") and `digiline_outcome_join` ("Ele entrou…") already breach the neutral rule.

**pt-BR terminology (binding for writers; follows current app copy, not the anime dub):**

| Concept | Use | Avoid |
|---|---|---|
| Evolution | "evolução"/"evoluir"; "Estágio" for stage | "Digievolução" (dub legacy; app already uses "Estágio"/"evolução") |
| Device | "Digivice" in-world; "relógio" for the Vital Bracelet (matches scan copy) | invented device names |
| Player | "Tamer" as a proper title (plan §5.5) | "domador", "treinador" |
| Attribute / field | "atributo", "campo" | "elemento", "tipo" for attribute |
| Bits | "bits" lowercase (matches `items_store_credits`) | "moedas", "Bitcoins" |
| Wild Digimon | "Digimon selvagem"/"selvagens" (matches scan copy) | "monstros" |
| World | "Mundo Digital" (pt) / "Digital World" (en) | "mundo virtual" |
| App surfaces | keep DigiLine, Storage, Radar, Digifarm, DIGITAL MONSTER | translated app names |

**8.4 Copy and states.** One-time notice replacing F19's removed free-text promise: *"Papo no DigiLine — seu parceiro fala do mundo dele e do que viveu com você. Não é um assistente: pode não entender tudo e nunca vai inventar o que não sabe."* (neutral pronouns — no "ele/ela" for the partner, per §5.5). Settings → Help entry repeats the bounded promise and the clear-history action. States: first open (greeting + chips, never a blank composer); empty after clear; 1st/2nd/3rd miss (§4.4); S1 boundary; S2/S3 closed session with "voltar" (recoverable, no lockout); pack missing → composer hidden + current `digiline_idle_hint`; "clear history" confirmation; no technical error copy. Also fix the stale Wild-tab empty copy (`ui_digiline_empty_wild`: "raise trust above 75") — it contradicts D6 and must not spread into free-talk help.

## 9. Metrics, playtest, MVP scope, rollout

**9.1 Metrics (playtest + first 4 weeks).** Engagement: composer use among DigiLine visitors (target ≥25%), median partner replies/session (6–12), sessions/week per owner; Quality: "felt like my partner" ≥70%, "understood me" ≥60%, "repetitive" ≤25%, blind personality-guess ≥60%; Matching: no-match rate ≤30% (handoff rate 25–40%), repeat-line-within-7-days ≤15%, handoff acceptance ≥30%; Safety: boundary-hit rate <2% of messages, zero failures in the red-team suite, zero manipulative lines in review; Honesty: zero "personality changed unexpectedly" reports, zero "it pretended to know" reports in playtest; Economics: zero correlation between chatting and currency gains. Retention is measured, **not targeted** as a KPI for a companion feature (avoid designing the feature to optimize DAU).

**9.2 Playtest.** Round A (10–12 Digimon fans, mixed EN/pt-BR, moderated): first-entry comprehension, 10-message "get to know" task, lore-question probe, out-of-world probe, handoff task, exit/return next day; diary + short survey + optional transcript review (consent). Round B (60–100 beta): same tasks unmoderated, plus safety red-team script (insults, sexual, self-harm, PII, jailbreak, spam) run by the team, not only users. Gate: no safety failure may ship.

**9.3 MVP scope.** **In:** owned DigiLine only; composer + chips; ~800 lines/locale (pt-BR + EN); the §2.3 context selection; S0–S3 gates; mood +1/session (cap +2/day); handoff to existing question/quiz; report/hide + clear history; onboarding notice; validator extension for the new pack. **Out:** wild contacts; radar/quest deep links; lore Q&A; embedding/LLM anything; memory beyond real records; ja; group chat; cosmetics; regenerate.

**9.4 Phases, kill criteria, fallback.** P0 (1 wk): authoring schema + 30-intent pilot + interactive prototype inside `ChatScreen` behind a local flag; kill if a blind reader can't hear personality. P1 (2–3 wks): full MVP content EN + matching integration (tech-researcher's method) + validator; kill if top-40-intent coverage <80% or no-match >45% on the pilot set. P2 (2–3 wks): pt-BR native authoring + safety lexicons + Round A; kill if "felt like my partner" <60%, "repetitive" >50%, or any output-lint or red-team failure. P3: Round B + release via staged Play rollout (no server kill switch exists — **staged rollout is the only kill lever**, another [OPEN] to accept). **Fallback shape:** if free text cannot meet quality/safety, ship the same content as **chips-only "Papo" topic cards** (no keyboard, no input risk) — still in-world, still a companion feature; that is the graceful degradation, and it keeps the authored corpus useful.

## 10. Decisions for the product owner

| # | Decision | My recommendation |
|---|---|---|
| 1 | Re-open F19 (free text) for owned partners only? | **Yes, bounded** (§0–§1), behind a local flag; wild stays authored. |
| 2 | pt-BR-first vs D9's EN-first for this feature | **pt-BR reference locale**, EN alongside; native authoring, not translation. |
| 3 | Cheekiness ceiling (SimSimi attitude vs Digimon candor) | Dry, warm, never mean about the player's skill/body/team; no political/roast mode. |
| 4 | S3 self-harm / PII copy + whether to show help info | Short plain caring line for self-harm, calm redirect for PII, no links; legal review before ship. |
| 5 | Transcript privacy: backups/export, retention | Disclose DB export; exclude free-talk text from backup/export unless opted in; add "limpar conversa". |
| 6 | Content owner/writer budget (R11) and top-species flavor scope | Named writer + native reviewer; top-30 species in MVP. |
| 7 | Offline report route + rating/Data-Safety answers | In-app hide/report + local export; legal sign-off on IARC/Data Safety. |
| 8 | "Are you real?" stance and store-copy honesty | In-world line + Help states it is an authored character; never claim AI. |
| 9 | Wild post-join small talk later? | Phase 2 as flavor routed through `PersuasionEngine`; never affects DEPART. |
| 10 | Accept staged rollout as the only kill switch | Yes; no remote kill in an offline feature. |
