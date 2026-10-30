# Presentation redesign: the farm is the home

Status: **plan for approval** · Surface: Android app shell + new Home · Mode: Operate with an
Experience layer (the world is the app's front door) · Design system: existing `DESIGN.md`
(evolved, not replaced) · Impeccable surface brief: `.impeccable/surfaces/farm-home.md`

---

## 1. The one-paragraph version

VBHelper currently opens as a stack of instrument panels: Home is a "Partner overview" page of
vitals and stat cards, the Digifarm is a square 1:1 viewport buried as tab 2 of the World tab, and
places like Arena, DigiLine, Shop and Radar are hidden behind a "More" sheet. The redesign turns
the app's front door into a **place**: the Digifarm fills the screen, the camera trails your active
Digimon, and the world carries a compact command deck of five places plus a partner slab that pulls
up into the full partner overview. Nothing is removed from the product — every module keeps a home —
but the app stops presenting itself as a database with a companion attached and starts reading as a
companion with a database behind it.

Two recon facts make this cheap rather than heroic: the farm **already renders a full 3D island with
walking residents, camera follow, tap targets and Compose overlays above the surface**
(`Digifarm3dViewport.kt`, `DigifarmScreen.kt`), and the World tab is nothing but a two-section
switcher (`WorldHubScreen.kt:34-71`), so "remove the World tab" is mostly deleting a wrapper.

---

## 2. Evidence: what already exists (do not rebuild)

| Capability | Where it lives | State |
|---|---|---|
| 3D island, themed materials, 4 palettes | `digifarm/3d/digi_farm_3d.glb` + `EnvironmentThemeController` | ships, themed |
| Residents as extruded billboards, 9 poses each | `SpriteExtrusionGlb.kt`, `DigifarmScreen.kt:377-393` | works, 8 MB LRU mesh cache |
| Continuous camera follow by resident id | `updateFollowTarget` `Digifarm3dViewport.kt:848-863` | **already continuous** |
| Orbit + pinch gestures | `onTouchImpl` `:748-816`, clamp `1.2..4.8` | works |
| Compose UI layered above the 3D surface | `TextureView` chosen for this `:142-147`, `projectResident`, speech bubbles | works |
| Tap targets per resident | projected invisible `clickable` boxes `DigifarmScreen.kt:476-499` | works, needs hit-test rework |
| Farm management (create, rename, switch, archive, residents, activities) | `DigifarmScreen.kt` + `DigifarmRepository.kt` | complete |
| Active partner (single source) | `UserCharacter.isActive`, `UserCharacterDao.getActiveCharacter()` | exists, **unused by farm code** |
| Camera persistence | `Farm.cameraYaw/Pitch/Distance/TargetX/Z` + `DigifarmRepository.save3dCamera` | columns exist, **zero callers** |
| Full-bleed chrome hiding precedent | `battleFullScreen` in `AppNavigation.kt:128,193-215` | works for bar/rail only |
| Motion preference | `LocalMotionEnabled`, `motionEnabled()` | exists, **3D scene ignores it** |
| Partner overview content (21 modules) | `VBDiMHomeScreen` + `BEHomeScreen` | complete, re-hostable |

What does **not** exist and must be built: farm as a navigation destination; a home HUD; the
partner sheet; active-partner resolution in farm code; follow/override contract; render pause and
frame cap; edge-to-edge transparent chrome for this one route; resident tap → details.

---

## 3. Direction: what we borrow, what we refuse

**References and what exactly we take from each:**

- **Digimon Links / Re:Digitize farm (the source of the shipped art)** — a farm is a *place* you
  look at, not a menu. Take: spatial continuity, residents doing things while you watch, the island
  as the app's identity.
- **Animal Crossing: Pocket Camp** — the world is the home, chrome is a thin bottom deck with
  round-or-square place buttons and a persistent "who you are" strip. Take: home chrome discipline,
  always-visible partner identity, badge-driven return.
- **Pokémon GO** — a full-screen world with a three-detent sheet for details; peek/half/expanded.
  Take: the detent ladder and its dismissal contract (back collapses, never exits).
- **Clash of Clans / Hay Day** — place buttons are *anchored props in the world*. Rejected outright
  (Q10): navigation stays HUD-only, so the world carries no labels and no occlusion risk.
- **Genshin Impact / HSR** — the inverted-L HUD: identity top-left, currency/util top-right, action
  cluster bottom-right. Take: zone assignment and thumb-reach economics.
- **Material 3 (binding)** — 48dp targets, predictive back, insets, dark theme as a first-class
  scheme, sheet semantics. Take all of it literally; the game layer is a *tone*, not an exemption.

**We refuse:**

- A full reskin. The dark purple/cyan instrument identity, the four palettes, cut-corner shapes and
  Oxanium are the brand; the game feel comes from composition, presence and motion, not new colors.
- Fake glass/backdrop blur (no reliable Android backdrop blur at this minSdk; a blurred-image
  imitation would break over a live 3D scene). Chrome uses gradient scrims + 1dp rim light.
- Currency/economy invention, stamina, energy gates or any new system. The HUD only surfaces what
  already exists: bits, quests, unread DigiLine, pending recruits, vitals, mood.
- Keeping five simultaneous navigation affordances. The bottom bar, the More sheet, per-screen
  banners and now a dock cannot all exist; the bar goes, the dock replaces it.

---

## 4. Information architecture

### 4.1 The five places (dock) — my decision on the dock you delegated

| # | Place | Opens | Why it earns a slot |
|---|---|---|---|
| 1 | **Radar** | `Radar` route (today's `WorldScreen` Radar section verbatim) | The world-facing feature: spawns, first-person, wild chat, radar battles, recruits. It is the only place with GPS/physical play, and it is the reason the World tab cannot simply vanish. |
| 2 | **Arena** | `Battle` route (hub: sparring, Tamer Arena, legacy PVP) | Daily loop, highest-frequency return. |
| 3 | **DigiLine** | `Digiline` route (threads: Storage, Wild Ones, Farm groups) | Social/AI presence — the app's emotional hook and the second-highest unread surface. |
| 4 | **Shop** | `Items` route opened on the Items store tab | Bits have an outlet; today it is 3 taps deep in a sheet. |
| 5 | **Partners** | `Storage` route (roster) with a Dex chip in its banner | Storage is the collection *and* the switch/transfer surface; Dex hangs off it. Both leave the bottom bar without losing a door. |

Everything else lives in the **menu (≡)** in the top-right util cluster: Quests, Dex, Inventory (My
items), Adventure, Transfer/NFC, Lorebook, Sprite viewer, Settings, Credits, plus Debug-gated tools.
Quests also gets a badge chip at the top (see 5.1), because a quest journal you can only reach
through a menu gets ignored.

**Naming:** dock labels stay literal (Radar, Arena, DigiLine, Shop, Partners) for i18n and
discoverability; each place screen adds a flavour subtitle ("Colosseum", "Exchange", "Vault") in the
place header only. One string set, two registers — see open decision Q3.

### 4.2 What happens to the World tab

Delete `WorldHubScreen` and the Radar|Digifarm `VitalTabs` (`WorldHubScreen.kt:34-71`). The Digifarm
becomes Home; the Radar becomes a place. Two invariants must be preserved because code depends on
them:

1. `WorldConversationScreen.kt:72-74` and `DigilineScreen.kt:121-125` call
   `getBackStackEntry(NavigationItems.World.route)` **unguarded**. Either keep a `Radar` route id and
   rewrite those three call sites + `WorldChatScreen.kt:261-263,300-304`, or keep `World` as a route
   alias. Do the rewrite in Phase 0 while both routes still exist.
2. `topLevelRoute()` (`BottomNavigationBar.kt:36-47`) is the only thing that keeps a tab highlighted
   for `WorldChat/`, `WorldConversation/`, `WorldRecruits`, `WildContact/`, `FarmGroup/`. It is
   replaced by `placeOf(route)` in the new HUD (returns the place a route belongs to, or null).

### 4.3 Route map

| Route | Content | Change |
|---|---|---|
| `Home` | **`FarmHomeScreen`**: world + HUD + partner sheet | replaces the partner-overview page; **keeps the route id** so `ScanScreen.kt:91`, `StorageScreen.kt:586` and the start-destination contract keep working |
| `Radar` | `RadarScreen` (extracted from `WorldScreen`) | new route id, same composable; `World` kept as alias for one release |
| `Battle` | unchanged | dock → hub |
| `Digiline` | unchanged | dock |
| `Items?tab=store` | `ItemsScreen` | optional arg, default `mine`; dock passes `store` |
| `Storage` / `Dex` | unchanged | dock → Storage; Dex via Storage chip + menu |
| `Partner` | *not created* | the overview is a sheet, not a route (see 6) |
| everything else | unchanged | menu |

**Farms beyond the first** stay a farm-switcher inside the farm chip (top-left), not a place. The
`FarmGroup/{farmId}` chat keeps working and its back target becomes `Digiline` (already its mapping).

---

## 5. Home anatomy

Portrait reference frame 411 × 891 dp. Three chrome zones, one continuous piece of scenery between
them. All dimensions are minimums that grow with font scale (`sp`, `heightIn`, never fixed heights).

```
┌────────────────────────────────────────────────────────────┐
│ ░░ top scrim (transparent → 62% bg over 88dp) ░░░░░░░░░░░░░ │
│ ┌───────────────┐  ┌──────┐ ┌────┐ ┌────┐                 │
│ │ ◈ Thornhill ▾ │  │◉ 1.2k│ │ ⚑3 │ │ ≡  │   64dp zone    │
│ │   farm chip   │  │ bits │ │quest│ │menu│                 │
│ └───────────────┘  └──────┘ └────┘ └────┘                 │
│                                                            │
│                    the island (3D)                         │
│                                                            │
│                  ▓ active Digimon ▓  ● bubble               │
│                                                            │
│                        (free camera)                       │
│                                                            │
│ ░░ bottom scrim (transparent → 78% bg over 232dp) ░░░░░░░░░ │
│      ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐     │
│      │Radar  │ │Arena  │ │DigiLine│ │ Shop │ │Partners│    │ 64dp tiles
│      └───────┘ └───────┘ └───────┘ └───────┘ └───────┘     │ +labels
│  ┌──────────────────────────────────────────────────────┐  │
│  │ [portrait] Monodramon · Rookie   Lv 12   ▮▮▮▮▮▯ 82%  >│  │ slab 88dp
│  └──────────────────────────────────────────────────────┘  │
│ ░░░ navigation bar inset ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
└────────────────────────────────────────────────────────────┘
```

### 5.1 Top zone (status)

- **Farm chip** (left, cut-corner, 48dp tall): `◈ <farm name> ▾` + resident count (`3/12`). Tap =
  farm switcher sheet (create, rename, switch, archive, farm settings). This replaces the farm's old
  `TopBanner` (`DigifarmScreen.kt:313-322`) and keeps all of its capability.
- **Util cluster** (right, 40dp visual / 48dp hit boxes): bits pill (`items_store_credits`), quests
  chip with ready-count badge, menu `≡`.
- Scrim: linear gradient, `background` 0% → 62% over 88dp + status-bar inset. Text always on scrim,
  never on raw scene. No shadows.
- Never more than three items on the right; the menu absorbs the rest.

### 5.2 Middle (the world)

- 3D view fills the entire route, edge-to-edge, under transparent system bars.
- **Camera framing contract:** the followed resident must sit between 30% and 52% of viewport
  height. Because the bottom 232dp is chrome, the camera target is lifted (`TARGET_Y` −0.05 →
  −0.12 world units) and pitch default eased to ≈0.45 rad at distance ≈3.2 m on first open;
  persisted camera wins afterwards.
- Tap = nearest-resident hit test (single handler, not 12 invisible boxes) → resident quick sheet
  (existing `ResidentDialog`: needs, Play/Train/Feed/Rest, chat, group, follow, transfer, remove).
- Tap on the active partner → recenter camera (same as the old "Follow" affordance, now implicit).
- Long-press anywhere on the world is **not** used (hidden gestures are the classic game-HUD sin).
- Speech bubbles keep projecting as today (`DigifarmScreen.kt:500-515`), capped at two concurrent and
  suppressed when the sheet is at half/expanded.

### 5.3 Command deck (bottom)

- Five `PlaceButton`s: 56dp cut-corner (10dp) tiles, 24dp icon, `labelSmall` caps label under the
  tile inside the same 64dp cell, `InstrumentSpacing.ControlGap` (8dp) between. Selected place gets
  the cyan rim + 12% cyan fill; a place with an alert (unread DigiLine, ready quests, pending
  recruits) gets a 6dp cyan dot, not a red badge — color discipline from `DESIGN.md` (Vital Signal
  Rule).
- The deck and the slab share **one** bottom scrim and are drawn as one element: slab at the very
  bottom, deck floating 8dp above it. This is the difference between "two bars" and a game deck.
- Deck hides (250ms fade+slide) when the sheet reaches half height; returns on collapse.
- Entry: deck + slab slide up 12dp with 240ms `FastOutSlowIn` on first composition of the route only.

### 5.4 Partner slab (sheet peek)

- Full width, 88dp, cut-corner 18dp top, `surfaceElevated` 82% over the world, 1dp `stroke` rim.
- Content: 56dp portrait (idle frame, pixel-perfect), name + `stage · attribute`, level, vitals bar
  with numeric value, mood dot, chevron. Right side carries a 48dp chat button (tap = `Chat/{id}`,
  preserving today's "tap the portrait to chat" behaviour).
- The slab **is** the drag handle: vertical drag expands, horizontal swipe cycles favourites
  (preserving `HomeScreen.kt:120-133`), tap opens half, chevron opens half.
- Drag only ever starts on Compose chrome — never on the 3D surface. This is the arbitration rule
  that resolves the gesture conflict with `requestDisallowInterceptTouchEvent` in the viewport.

---

## 6. The partner sheet (where today's Home goes)

`BottomSheetScaffold` is **not** used: it wants the farm as scrollable body and would fight the
camera. Use a hand-rolled, non-modal, three-detent sheet inside the route `Box` — no scrim at
peek/half (the farm stays interactive and visible), scrim at expanded.

| Detent | Height | Content | Interaction with world |
|---|---|---|---|
| peek | 88dp (the slab) | portrait, name, level, vitals bar, mood, chat | full |
| half | 44% | + Stage/Attribute/Days column, mood/trophies/next-evolution row, win rates, action row (Transfer · Chat · Dex details) | world visible, gestures disabled |
| expanded | 96% | everything: weekly vitals chart, evolution history (with the 5000-bit degenerate action), BE/APP training blocks, special missions, favourites cycling, transfer chip | scrim, world paused |

**Every module is reused, not rewritten.** `VBDiMHomeScreen` / `BEHomeScreen` / `BEBEmHomeScreen`
become the expanded body (they already take `contentPadding = PaddingValues(0.dp)` and never own
insets, `HomeScreen.kt:312,337,366,395`), split into three composables by detent. The test-id
contract (`home_vbdim_*`, asserted by `app/src/androidTest/.../AppHomeParityTest.kt:26`) moves with
the composables, so parity coverage survives.

**Back contract:** expanded → half → peek → system exits the app. Implement with `BackHandler` at
each stage (predictive back not opted in today; keep it that way in Phase 1, revisit in Phase 4).

**States over the world** (replacing the full-page panels in `HomeStatePanels.kt`):
- no farm → centred `CyberEmptyState` card "Create your Digifarm" (`ui_digifarm_empty_*`) with the
  island rendering behind it, no residents (the viewport already handles an empty roster);
- farm but no active partner → slab replaced by a `ChoosePartnerCard` (opens Storage);
- loading → slab skeleton (shimmer on the scrim only, cut-corner geometry, no spinners on the world);
- profile unavailable → slab shows `PROFILE_UNAVAILABLE` panel with the Dex/Storage recovery action.

`Favourites`, `didAdventureMissionsFinish` and `itemObtained` dialogs stay blocking alerts, now drawn
over the world (they are decisions, not chrome).

---

## 7. Camera and presence

- **Follow by default.** On open: resolve `UserCharacter.isActive` →
  `UserCharacter.individualId` → is it a resident of this farm? If yes, seed `followId` and frame it.
  If the active partner is not a resident, fall back to the most recently interacted resident and
  offer an "Add partner to farm" action on the slab (never silently auto-add — that mutates the
  player's setup).
- **Manual override.** A one-finger drag or pinch sets `overrideUntil = now + 5s`; on expiry, ease
  the orbit target back to the followed resident over 600ms. Follow state is shown by a subtle ring
  on the target's projected marker, not by a button.
- **Hold the target while assets stream.** Today, if the followed id is missing from `residents`
  (GLB still loading) the target snaps to island centre (`:849-851`) — a visible drift on open. Hold
  the last target until the resident exists.
- **Persistence.** Reuse the existing `Farm.cameraYaw/Pitch/Distance/TargetX/Z` columns and
  `save3dCamera` (currently zero callers). Store `followIndividualId` in a DataStore preference, not
  a new column, to avoid a migration on a product whose database upgrades must preserve collections.
- **Reduced motion.** When `LocalMotionEnabled` is false: no island bob, no glow pulse, no pose
  cycling (single idle frame), no camera ease on recenter (cut), and place transitions become an 80ms
  crossfade. Today the 3D scene ignores the preference entirely — this must be fixed before the world
  becomes the home.
- **Pause and frame budget.** Gate the `Choreographer` loop on `RESUMED` + `isWorldActive` +
  `windowVisibility`: 30fps idle, 60fps during touch/animation, 0 while covered by a place route or
  the app is backgrounded. This is a **bug fix taken before launch**, not a later optimization: an
  always-mounted home with an unpaused loop is a battery regression.

---

## 8. Visual language for the game layer

Evolve the identity; add no new palettes.

- **Chrome material:** `surfaceElevated` at 62–82% over the world + 1dp `stroke` + a single-pixel
  inner cyan top edge at 30% for the "rim light". No drop shadows (Quiet Elevation Rule).
- **Shapes:** dock tiles 10dp cut corner, slab 18dp top corners, sheet 18dp. No M3 28dp rounds; the
  sheet's own top corners must use the cut-corner family so it doesn't look borrowed.
- **Iconography:** five new 24dp vector place icons (radar sweep, colosseum arch, waveform-thread,
  exchange hatch, vault locker) in the existing 2dp-stroke technical line weight. Material
  `baseline_*` glyphs stay for utilities (gear, menu, bag) so the dock reads as "the world", the rest
  as "tools".
- **Typography:** place labels `labelSmall` caps, 0.6sp tracking; slab name `titleSmall` bold; the
  vitals number uses the display role with tabular figures so it stops dancing; place headers use the
  existing `TopBanner`.
- **Progress instead of numbers where numbers don't matter:** the next-evolution timer, mood and
  vitals become a 3-segment activity arc on the slab; exact values stay in the half sheet (this is
  the `DESIGN.md` rule "value, state, name prevail over auxiliary text", applied at a glance).
- **Colour discipline:** cyan = live/selected/actionable; purple = atmosphere; yellow = recognition
  only; red never decorates. The world supplies the atmosphere, so the HUD stays quiet.
- **Motion inventory:** deck entry (240ms slide+fade), sheet detent (spring, damping 0.85), camera
  recenter (600ms), place transition (fade-through 260ms with the world scaling to 0.98 behind — the
  world never slides), resident selection ring (120ms). Haptics: one light tick on dock press, one
  soft tick on detent snap, both gated by the existing audio/haptics settings.
- **Legibility over 3D:** minimum 4.5:1 for label text against the *scrimmed* backdrop at the
  brightest theme (VB Arena light). Verify on the two light palettes first — they are where a HUD
  over a bright farm fails.

---

## 9. Place screens: making them feel like places

Presentation-only changes, no new features:

1. **Shared place header.** Every dock destination gets a lean header: farm-back chip (`⌂` 48dp) on
   the left instead of a generic back arrow, place name, and one flavour subtitle. Back always
   returns to the farm, one level, never to a phantom tab.
2. **Return continuity.** Pushing a place fades through with the world compressing behind; popping
   reverses. The farm is never rebuilt on return once Phase 3 lands.
3. **Badge honesty.** A dock badge means "there is something waiting", and it clears at the source
   (unread DigiLine, ready quests, pending recruits). No decorative badges.
4. **Arena** keeps its 3-mode hub (sparring / Tamer Arena / legacy PVP). The dock button lands on the
   hub, not on a silent redirect to Tamer Arena (`BattlesScreen.kt:2197-2242`). Radar battles stay
   inside the Radar place; the Arena hub gains a factual line saying so, because two battle entries
   with no explanation is a comprehension bug.
5. **Shop** opens `Items` on the store tab and shows bits in the same pill as the HUD. Title becomes
   "Exchange", subtitle "Items for bits"; the existing purchase flow, prices and wallet are
   untouched (`ItemsStore.kt:47-155`).
6. **Partners** opens Storage; Storage's banner gains a `Dex` chip so the collection has one door.
   Storage, Dex, Chat, Card and Transfer keep their own `TopBanner` + instrument styling — tool
   screens stay tools.

---

## 10. Engine lifetime and performance budget

The hard constraint (`farm-recon` §7): the farm is torn down and rebuilt on every navigation today,
and the loop never pauses. Two options:

- **Option A — rebuild per visit, hidden behind a poster frame (Phase 1).** Capture the last rendered
  frame, show it as a static bitmap under a 120ms fade while the engine rebuilds. ~533 KB island +
  resident GLBs re-streamed; acceptable for the first milestone, but a visible hitch on slow devices.
- **Option B — one persistent world surface (Phase 3, recommended).** Hoist the viewport and its
  Filament engine to shell level (`AppNavigation` `Box`), keep it mounted while the player is on any
  main-graph route, and pause the loop when a place covers it. This removes the rebuild *and* makes
  the place transition possible (world compresses behind the place instead of cutting).

Budgets to hold: ≤ 12 residents rendered (cap to 8 nearest + LOD if the device is warm), 30fps idle,
≤ 8 MB mesh cache (exists), theme texture decode staggered to 2 per frame, no frame work while
backgrounded, first-frame interactive < 1.2s on the reference device.

---

## 11. Nothing gets lost: module map

| Today (source) | New home | Register |
|---|---|---|
| Vitals header (`VitalsPanel.kt:50`) | slab + half sheet | always visible |
| Animated portrait, tap → chat (`ActiveDigimonCard`) | slab portrait, chat button | always visible |
| Latest unread AI line (`speechBubbleText`) | world speech bubble | contextual |
| Stage/Attribute/Days (`PartnerStatColumn`) | half sheet | one drag |
| Weekly vitals chart (`WeeklyVitalsChart`) | expanded sheet | two drags |
| Mood / trophies / next evolution (`ItemDisplay`) | slab dot + half sheet | glance + detail |
| Win rates (lifetime/current) | half sheet | detail |
| BE rank/training/held item + gains (`BEHomeScreen`) | expanded sheet | detail |
| APP partner block | expanded sheet | detail |
| Evolution history + degenerate (5000 bits) | expanded sheet | detail |
| Special missions | expanded sheet + toast on return | detail |
| Favourites cycling (swipe/a11y) | slab horizontal swipe | always |
| Dex details (long-press) | half sheet action + Dex chip | one tap |
| Transfer chip (`TopBanner`) | half sheet action + menu | one tap |
| Settings gear (`TopBanner`) | menu | always |
| Adventure/item dialogs | overlay over world | blocking |
| Farm switcher/create/archive/settings | farm chip sheet | always |
| Farm residents row, Add, Group, follow, offline summary | resident quick sheet + world | contextual |
| Farm empty state page | centred card over the island | first run |
| Radar (map, first-person, encounters, recruits, battles) | Radar place, unchanged | dock |
| Quests | quests chip + menu | always |
| Items/Store, Adventure, Transfer, Lorebook, Viewer, Credits | menu | one tap |
| Debug tools | menu, Debug-gated | off by default |

---

## 12. Phased delivery

**Phase 0 — plumbing (no visual change).** New `Radar` route + rewrite the three unguarded
`getBackStackEntry(World)` sites; extract `RadarScreen` out of `WorldScreen`; add `placeOf(route)`;
`Items?tab=store`; update `RadarTabNavigationTest`, `UiRefinementsTest`; keep every existing screen
rendering exactly as today. *Exit: tests green, app identical.*

**Phase 1 — farm as Home (the milestone you can judge).** `FarmHomeScreen` on the `Home` route;
full-bleed with transparent bars for this route only; top zone, command deck, slab, three-detent
sheet with all modules re-hosted; active-partner follow + persisted camera; world gesture
arbitration; render pause + 30fps idle + reduced-motion path; state cards. *Exit: the app opens onto
the farm, every partner module is reachable, nothing regressed, battery measured.*

**Phase 2 — presence and polish.** Place transitions, place headers, badges, resident quick sheet
re-pointing, poster frame, empty/loading/first-run, haptics, custom place icons, light-palette
contrast pass. *Exit: the app feels like a game rather than a panel with a render in it.*

**Phase 3 — persistent world surface.** Engine hoisting, texture-decode staggering, FPS tiers, warm
device behaviour, tablet/rail adaptation of the dock (rail becomes a vertical place column).
*Exit: no rebuild on navigation, measured budget held.*

**Phase 4 — cut.** The diegetic layer (world signposts, facility props) is **not built**: navigation
stays HUD-only per Q10. The Links facility assets remain authoring sources only. Phase 4 survives as
optional polish with no place promises: onboarding tutorial, quests journal restructure inside
DigiLine, and the 840dp rail adaptation of the dock (Q18).

---

## 13. Verification

- Compose instrumentation for: sheet detents and back contract, dock navigation, badge clearing,
  slab states, `home_vbdim_*` parity, large font scale, TalkBack pane titles.
- JVM tests for `placeOf`, active-partner resolution (resident / not resident / none), camera
  persistence round-trip, frame-gate state machine.
- `lintDebug`, unit suite, debug + release assembly.
- **Device capture is mandatory for the visual verdict** and there is no connected device or adb on
  this machine today (`adb` not installed). Camera framing, legibility over the two light palettes,
  sheet feel and battery cannot be accepted from source. I need a device or emulator to finish the
  job honestly.

---

## 14. Locked decisions (owner answers, 2026-10-30)

| # | Decision | Locked value |
|---|---|---|
| Q1 | Dock set | **Radar · Arena · DigiLine · Shop · Partners**, plus menu in the top util cluster |
| Q2 | Shop label | **"Shop" in the dock**, "Exchange" as place subtitle |
| Q3 | Place subtitles | **Yes** — subtitle only, dock labels stay literal |
| Q4 | Dex | **Chip inside Storage banner + menu**; no sixth dock slot |
| Q5 | Quests | **Top chip with ready badge + menu** |
| Q6 | Adventure | **Menu only** |
| Q7 | Follow fallback (active partner not a resident) | **Follow last-interacted + offer "Add to farm"**; never auto-add |
| Q8 | Orbit override | **Drag overrides, ease back to the partner after 5s idle** |
| Q9 | Resident tap | **Quick sheet** (existing `ResidentDialog`) |
| Q10 | Diegetic layer | **No signposts, no facility props — HUD only.** Phase 4 signpost work is cut |
| Q11 | Idle animation | **Yes, capped at 30fps, honours reduced motion** |
| Q12 | Haptics | **Two ticks: dock press and sheet detent** |
| Q13 | Sound | **Reuse the existing audio controller for a place-open cue** |
| Q14 | Partner sheet | **96% non-modal sheet with scrim at expanded** |
| Q15 | Orientation | **Portrait only. No landscape work at all** (no orientation lock unless asked; existing legacy screens keep their current behaviour) |
| Q16 | Verification | **Owner connects a phone via adb**; visual verdict happens on device |
| Q17 | Battery | Measure idle drain over 10 min on the farm; target ≤1.5%/10min (to be confirmed by the measurement) |
| Q18 | Expanded width | Keep the 840dp rail breakpoint and present the dock as a vertical column there |
| Q19 | Delivery | **Phase 0 + Phase 1 in one pass**, then on-device review, then Phase 2 |
| Q20 | Copy language | **English only** for new flavour strings |

Still genuinely open (owner input needed when it lands): the final visual verdict on a real screen,
the battery number on the reference phone, whether to lock the activity to portrait later, and
whether arena/digiline/shop headers adopt the subtitle flavour throughout the app or only on entry.

---

## 15. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Battery/thermal: always-on 3D with no pause | high | Phase 1 frame gate + 30fps idle, measured before merge |
| Engine rebuild hitch on every return | high | poster frame (P1), persistent surface (P3) |
| Gesture conflict: camera drag vs sheet vs back | high | drag only on chrome; single hit-test tap; explicit back ladder |
| Theme texture memory (~22 MB) resident for the session | medium | staggered decode, LruCache, warm-device cap |
| Light palettes: HUD legibility over bright farm | medium | contrast pass on VB Arena/Digimon.net first |
| Route surgery strands Scan/Storage/Radar savedStateHandle | medium | Phase 0 rewrite with tests before any visual work |
| Two battle entries (Arena vs Radar battle) | low | factual line in the Arena hub |
| DESIGN.md becomes wrong (it documents the 5-tab shell) | medium | update `DESIGN.md` §Navigation + §Layout in Phase 1 |

*Context note: `.impeccable/design.json` is stale relative to `DESIGN.md`; I did not run `document`
and am not repairing it as a side effect of this plan. Offer: run `impeccable document` to refresh
the sidecar when you want it.*

---

## 16. Implementation status — Phase 0 + Phase 1 (landed)

**Routes (Phase 0).** The Radar surface is a first-class Radar destination backed by
RadarPlaceScreen; World stays registered as a compatibility alias so the saved-state handoff sites
and older tests keep working. The Items?tab={tab} argument exists, with Items (bare) still resolving
to My items. The unguarded getBackStackEntry(World) calls now go through entryOrNull /
adarEntryOrNull helpers and fall back to navigating to Radar instead of throwing. A placeOf mapping
exists for HUD highlighting and badge ownership.

**Home (Phase 1).** screens/farmHome/ now owns the front door:

- FarmHomeScreen — edge-to-edge world, top HUD (farm chip, bits, quests, menu), five-place command
  deck, partner deck, back ladder, farm switcher/create, menu sheet, resident quick actions.
- HomeHud / PlaceDock / PlaceDestination — the five places are Radar · Arena · DigiLine · Shop ·
  Partners, with literal labels and flavour subtitles defined once.
- FarmHomeWorld (screens/digifarmScreen/) — chrome-free 3D surface, farm/active-partner resolution,
  persisted follow target and camera, tap→resident, double-tap→recenter, frame gate, reduced motion.
- PartnerDeck + PartnerModules / PartnerOverviewContent / PartnerOverviewState /
  PartnerDialogs — 96dp slab with peek/half/expanded detents reusing the existing partner-overview
  modules (the home_vbdim_* tag contract is preserved).
- Renderer: FarmFraming.Home preset (target lifted for the bottom chrome), SceneFrameBudget gate
  (paused / 30fps idle / 60fps interacting), static-motion path, camera persistence hooks, one resident
  hit test, drag override with a 5s expiry and 600ms recenter.
- Shell: the phone bottom navigation bar and the wide-screen rail are retired. Home is the only
  edge-to-edge destination and applies its own insets; entering or leaving Home cross-fades instead of
  sliding.

**Deviations accepted during implementation**

1. The deck lives inside the Home route, not above the NavHost. Returning from a place still rebuilds
   the farm engine; Phase 3 hoisting (one persistent world surface) remains the fix.
2. Places keep their existing instrument headers. The "farm-back chip on every place header" moved to
   Phase 2 because the bar removal already leaves system back as the single return path.
3. Only the selected farm's resident count is known to the HUD; the switcher therefore shows a count
   for the selected row only. A combined per-farm flow is the Phase 2 fix.
4. A real defect surfaced on device: swiping the slab upward was captured by the system Home gesture
   and left the app. The deck now claims systemGestureExclusion above the navigation-bar strip, which
   fixed drag-to-expand without taking the system bar itself.

**Device verification (Galaxy A34, Android 15, 1080×2340)**

Verified live: island renders; top HUD and bits; five-place deck; partner slab with portrait, level and
vitals; tap and drag detent ladder (peek→half→expanded, and back); the back ladder collapses before
exiting; Radar, Arena, Shop (opens the store tab) and Partners open and return; no fatal exceptions in
logcat. Captures are stored in .plan/home_peek.png, home_half.png, home_expanded.png.

Still unverified: idle battery drain (target ≤1.5%/10min), light-palette (VB Arena / Digimon.net)
contrast over a bright island, TalkBack order, 1.3 font scale, tablet/rail layout, and the aesthetic
verdict itself — that one needs the owner's eyes.

**Phase 2 backlog (ordered):** place headers + farm-back chip; dock badges that clear at the source;
poster frame for the return hitch; custom place icons; first-run onboarding over the empty island;
expanded-sheet content ordering; battery + light-theme passes.

---

## 17. Owner revision (2026-10-30): the farm is a diorama, not a simulation

**Decision.** The Digifarm system is completely reformulated. It carries **no simulation**: no needs,
energy, food, social or fun values; no activities; no autonomous farm dialogue; no care loop; no
progress writes of any kind. It is an aesthetic presence layer — Digimon walking around and doing
things.

**One home farm.** The multiple-farm model is retired from the home surface: there is no farm list, no
create, no switch, no archive. The farm is simply home.

**Roster rule.** The island shows the user's active partner first, then the favourites from Storage.
Above twelve the active partner stays and the rest are chosen at random; the selection is stable within
an hour (rotation bucket) so it does not reshuffle while the user watches. Adventure-only and
non-usable partners are excluded.

**Implemented in this revision**

- FarmHomeWorld.kt rewritten: it derives the roster from Storage, builds the extruded poses from the
  stored sprites (idle/idle2/run frames mapped across the pose families), and runs a **per-actor life
  loop**: every resident owns its own random source and timing, so nothing moves in lockstep. An actor
  wanders in one or two steps, then rests, trains, plays or idles for its own duration, and social
  beats make residents walk up to the nearest neighbour, close to talking distance, face each other,
  share a celebration pose and drift apart again. No Room writes, no session coordinator, no
  orchestrator.
- FarmHomeContract.kt simplified to resident/roster state plus "follow this resident" and recenter.
  Farm selection, farm creation and "add partner to farm" were removed from the contract.
- The HUD farm chip now opens **Who is in the Digifarm** (read-only roster with the followed resident
  and the active-partner tag) instead of a farm switcher, and the create-farm dialog was deleted.
- The resident quick actions sheet is cosmetic: name, attribute, "Follow with camera", "Chat". Needs,
  activities, grouping, transfer and remove were removed from the surface.
- Empty state: with no active partner and no favourites, Home shows "No partner in the Digifarm yet"
  with a direct route to Storage.

**Deliberately left alone:** the legacy farm tables, the World-tab DigifarmScreen, farm group chats
and the DigiLine farm threads. They are no longer reachable from Home and no longer drive the home
surface, but removing stored data or the routes is a separate, riskier change that the owner did not
ask for. Migration/deletion should be an explicit follow-up if wanted.

**Verification on device (Galaxy A34, Android 15):** roster resolves 12/12 (active partner plus
favourites); the residents' extruded meshes build; the island animates (≈68 mean absolute pixel
change in the world region over five seconds, with the HUD unchanged at 0.05); tapping a resident hits
the correct actor (arm-494) and opens the cosmetic sheet; no fatal exceptions. Captures:
.plan/final_home.png, inal_roster.png, inal_half.png, inal_expanded.png, inal_radar.png.

**Known limitation:** because the roster is derived from Storage rather than a farm row, the camera
follow selection is in-memory for the session (no farm row to persist it to). Persisting it would need
a small preference entry, which is deferred until the owner confirms the rotation behaviour.

---

## 18. Owner feedback round 2 (2026-10-30): top bar, persistent world, teleporting

**Top bar.** The floating-pill row was replaced by one solid shelf: a farm identity block on the left
(home glyph, 12/12 resident count, "who is here" chevron) and a utility cluster on the right
(bits with a coin glyph, quests flag, settings gear, menu). Everything sits in a single cut-corner
surface, so contrast no longer depends on whatever the 3D scene is doing behind it. The hand-drawn
three-bar menu glyph was removed entirely: it could fail to invalidate and appear to vanish, and it
was the reason Settings was hard to find. Utility actions now use Material vectors, and **Settings has
a dedicated gear button in the bar** as well as the first row of the menu sheet.

**Persistent world.** The Digifarm surface moved out of the Home route and into the app shell. The
Filament engine, island GLB and resident meshes are created once and then kept for the process
lifetime: the surface is paused (SceneFrameBudget Paused) and alpha-hidden while a place is on top.
Selecting Radar, Partners or Arena and coming back no longer re-streams anything. Verified on device:
one scene-ready and one asset-load in logcat across a Home → Radar → Home → Partners → Home round
trip.

**Teleporting residents.** The renderer only interpolates a position change while it stays under
MAX_INTERPOLATED_STEP_SQ (0.35 world units); anything longer snaps to the endpoint, which is exactly
the teleport the owner saw. Wander targets could be most of the island away, and the social drift step
moved 55% of the remaining distance at once. Movement now goes through stepToward, capped at 0.22
units per step (0.16 while closing a social gap), looping with walk delays until the target is
reached. Initial spawn is still a single placement, as intended.

**Verification:** compile + debug assembly pass; Settings opens from the new gear; a resident tap still
opens the sheet; the world animates while the bar stays static (bar diff 0.07, world diff 40); the
single-load lifecycle above. Capture: .plan/topbar_redesign.png.

---

## 19. Owner feedback round 3 (2026-10-30): farm settings, framing, collision, camera lock

**Farm settings (owner request).** The "who is here" sheet now carries the farm's own settings:

- **Digimon in the farm** — a 1..12 stepper. The value persists in the farm-world DataStore
  (arm_home_resident_limit); the active partner always keeps a place, and the rest of the roster is
  the rotating favourites selection. The sheet shows "showing N of M" using the residents actually
  rendered, so a strict artwork filter cannot silently hide the whole farm.
- **Sprite art** — Mixed / VB only / Dot only (arm_home_sprite_filter). Dot-only resolves each
  candidate's species against the bundled Dot catalog and draws those full animation frames
  (idle/walk/train/happy/sleep) instead of the four card frames; candidates whose species has no Dot
  art simply do not appear. Verified on device: switching Mixed → Dot rebuilt all 11 residents
  (11 → 22 extrusion logs) and the island kept animating.

**Default camera.** The farm-home framing moved from 3.20 to 4.20 units with pitch 0.42, and the
maximum zoom-out from 4.8 to **8.0** units, so the whole habitat can be taken in.

**Camera follow.** The target is now **always** locked to the followed resident: the previous "drag
suspends following for five seconds" override was deleted, and the follow response was raised from
5.0 to 8.0. Device measurement: the followed Digimon projects at x = 519–541 on a 1080-wide screen
(dead centre) and y ≈ 1040 (≈44% height, above the chrome) continuously while it walks.

**Collision (owner request).** Proposed positions are pushed out of any neighbour closer than 0.115
world units with three relaxation passes, seeded tableaux are relaxed the same way, and every walk
step is capped at 0.22 units (0.16 while closing a social gap). Combined with stepToward, residents
no longer overlap and no longer teleport.

**Partner sheet arrow (owner bug report).** The down arrow shown while the overview is full-screen did
nothing because the handler was one-way (Peek → Half, everything else → Expanded). It is now a real
detent control: Peek → Half → Expanded → Half, with the arrow's accessibility label switching between
expand and collapse.

**Frame-gate bug found while testing (important).** The farm could come up with a blank island: the
renderer's gate needs isAvailable to be true, but both init() and the first onSizeChanged()
evaluate it before the texture surface exists, and nothing re-checked afterwards, so the loop stayed
paused forever. The gate now retries every 150 ms while the surface is still coming up
(FRAME_START_RETRY_MILLIS). Verified: island visible, 11 residents created, world animating
(mean pixel delta ≈ 41 over five seconds).
