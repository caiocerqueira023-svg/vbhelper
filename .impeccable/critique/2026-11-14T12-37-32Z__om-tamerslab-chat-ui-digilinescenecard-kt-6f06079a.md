---
target: digiline / chat / negotiation system
total_score: 31
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:C:\\Users\\julye\\IdeaProjects\\vbhelper\\app\\src\\main\\java\\com\\tamerslab\\chat\\ui\\DigilineSceneCard.kt"
target_fingerprint: "sha256:5b274e888fa074b48d942d70a2ffe1ffd4c617765f6e4c5c9ca0fbc9649dce80"
target_path: "C:\\Users\\julye\\IdeaProjects\\vbhelper\\app\\src\\main\\java\\com\\tamerslab\\chat\\ui\\DigilineSceneCard.kt"
timestamp: 2026-11-14T12-37-32Z
slug: om-tamerslab-chat-ui-digilinescenecard-kt-6f06079a
---
Method: design review only (detector not applicable, native platform)

# DigiLine / Chat / Negotiation — design critique

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Negotiation phase, tier and demand now show as chips; busy/error/retry states exist. No transcript recap inside the in-world card. |
| 2 | Match System / Real World | 4 | Negotiation happens in the Radar world with the camera moving onto the Digimon, which faces the player; language ("Bits", "Tier") stays in-fiction. |
| 3 | User Control and Freedom | 3 | End talk, Later and system back all leave without losing the negotiation; no confirmation, but the state is resumable. |
| 4 | Consistency and Standards | 3 | One shared scene card and HUD tokens across owned chat, wild contact and overworld; related dialogs still use default Material shapes. |
| 5 | Error Prevention | 3 | Options disable while a step is persisting; outcomes are authored; no destructive loss. |
| 6 | Recognition Rather Than Recall | 3 | Entry/resume context sentences explain the scene; phase stays visible. Previous lines are not repeated in the overlay. |
| 7 | Flexibility and Efficiency | 3 | Direct start, short option sets, one tap per step; no skip/auto path. |
| 8 | Aesthetic and Minimalist Design | 3 | Restrained card over the live world; header + card + hint consume meaningful vertical space on short screens. |
| 9 | Error Recovery | 3 | Explicit Try again + Continue on failure; app is offline so failures are rare. |
| 10 | Help and Documentation | 3 | Context/outcome copy explains consequences inline; no dedicated help surface. |
| **Total** | | **31/40** | **Good** |

## Design Specificity Verdict

**LLM assessment:** Authored for Tamers Lab. The exchange is staged inside the live Radar world using the app's own camera and the Digimon's extruded sprite, then continues in the shared HUD card grammar; the earlier version was a generic full-screen chat that any companion app could have shipped. The legacy LLM layer is isolated: normal play shows only authored DigiLine kinds, and trust UI is legacy-gated.

**Deterministic scan:** not applicable (native platform).

## Overall Impression

This now reads as a conversation happening with a Digimon standing in front of you instead of a chat screen opening on top of the map. The largest remaining opportunity is keeping the last spoken line visible while the next node is chosen, so the exchange reads as one conversation rather than a sequence of cards.

## What's Working

1. **World staging with a specific camera language.** The iso camera eases onto the subject, the heading freezes during the exchange, and the token faces the player — a product-specific moment no generic chat screen can reproduce.
2. **One card grammar everywhere.** Owned partner, wild contact and overworld negotiation use the same scene card with title row, phase/tier/demand chips, context sentence and explicit Continue.
3. **Legacy isolation.** Old LLM messages and the trust bar are hidden unless the debug legacy switch is on; the new flow's transcript is written with DigiLine kinds so previews and unread counts stay consistent.

## Priority Issues

- **[P1] The in-world card has no recent-line context.** After two choices the player must remember the previous line because the overlay only renders the current node.
  - Why it matters: the exact "starting in the middle" feeling returns on longer negotiations.
  - Fix: show the last committed line as a muted quotation above the current node (or an expandable "so far" strip).
  - Suggested command: /impeccable clarify

- **[P1] TalkBack behavior is not device-verified.** The scene card now exposes a polite live region and heading semantics, but announcement timing across option taps and camera moves has not been captured on hardware.
  - Why it matters: the core loop can be silent or chatty for screen-reader users.
  - Fix: run a TalkBack pass on a device/emulator through owned question, quiz, wild negotiation and terminal outcomes.
  - Suggested command: /impeccable audit

- **[P2] The bottom-anchored card can cover the subject on short or large-font screens.** Height is capped at 380dp and scrolls internally, but the Digimon sits in the upper half and the card can push it out of view at large font scales.
  - Why it matters: seeing the Digimon is the reason this is an in-world scene.
  - Fix: anchor the card lower (or use a height fraction) and verify at 1.3x/2x font scale and in landscape.
  - Suggested command: /impeccable adapt

- **[P2] Two exits with overlapping meaning.** "End talk" (header) and "Later" (card) both leave and resume later, without stating the difference.
  - Why it matters: players may think one discards progress.
  - Fix: make "Later" the only card exit and keep "End talk" as an explicit close with the same resume guarantee stated once.
  - Suggested command: /impeccable clarify

- **[P3] New UI strings ship in English only** while the app has pt-BR and ja resources; content packs are EN only by decision.
  - Why it matters: mixed-language chrome in a localized app.
  - Fix: translate the new chrome strings now, keep content EN until its locale packs land.
  - Suggested command: /impeccable harden

## Persona Red Flags

- **Jordan (First-Timer):** On JOIN the card disappears after Continue; the only confirmation is a toast plus the contact appearing in the DigiLine Wild tab. Without the toast, the outcome would be invisible.
- **Sam (Accessibility):** Live region announces scene text, but chip changes (phase/demand) may not be re-read, and no device capture proves focus order between the header, card options and Continue.
- **Alex (Power User):** Four to six taps per negotiation with no skip; repeat visits always re-show the scene card before the option set.

## Minor Observations

- The "Talking" chip in the overlay header adds a third status element beside name and phase; phase alone may be enough.
- Owned chat still shows a numeric bond/mood bar; acceptable on this detail surface but worth revisiting against the qualitative Home policy.
- Old LLM threads now show an empty preview until a new authored message arrives; the rows themselves remain useful.
- Demand amount is shown as raw bits with no payer-state hint (wallet balance), so affordability is only discovered from disabled options.

## Questions to Consider

- Should the last two spoken lines stay visible above the current node, turning the overlay into a compact transcript?
- Is the negotiation card better anchored above the bottom third so the Digimon owns more of the frame?
- Would a single "leave" affordance reduce the ambiguity of End talk vs Later?
