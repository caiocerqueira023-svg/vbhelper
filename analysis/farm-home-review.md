# Farm home — system review (second pass)

Scope: Digifarm membership, resident actions, swap flow, camera follow. Severity: P0 breaks trust,
P1 silent loss or wrong state, P2 confusion, P3 polish. Status: **fixed** / **open**.

## The model, as it is now

- **Membership is explicit and owned by one store.** `FarmWorldPreferences.farmMemberIds` (DataStore)
  holds the ids of the partners that live on the island; `FarmWorldPreferences.updateFarmMembers`
  is the single mutation. Favourites no longer decide who is on the island.
- **Migration is safe.** While `farmMemberIds` is `null` the island falls back to favourites once; the
  first membership edit seeds the explicit set from the current favourites, so nobody loses partners.
- **The leader is always a member.** Activating a partner adds it to membership, so a leader that
  steps down stays a resident instead of vanishing.
- **The island = active partner + members** (`orderedFarmCandidates`), then artwork filter, then the
  owner's resident limit with rotation when membership exceeds it (`buildFarmRoster`).

## Findings

### P0 — demoting the active partner deleted it from the farm — **fixed**
Promotion only flipped `isActive`; membership came from favourites, so an unfavourited leader vanished
the moment it stepped down. Now activating a partner adds it to membership, and stepping down changes
nothing else.

### P0 — favourites were immune to being swapped — **fixed / superseded**
The swap now exchanges membership (remove outgoing, add chosen) instead of touching favourites, which
is what the model needed anyway.

### P1 — an artwork filter could remove the leader — **fixed**
`buildFarmRoster` dropped characters whose artwork the filter could not resolve before the "leader keeps
a place" rule ran. The leader now falls back to whichever artwork exists.

### P1 — the followed partner appeared to change at random — **fixed**
The follow choice lived in composition state and was re-derived from the *shown* roster, so any roster
refresh (rotation, artwork change, DB emission) re-pointed the camera at the leader or the first
resident. The choice is now a persisted preference keyed by character id; only an explicit follow
changes it. While the chosen partner is off screen the camera falls back to the leader and returns to
the choice as soon as it is visible again.

### P1 — membership had no single owner — **mostly fixed**
One DataStore set and one mutation function now own membership (add / remove / seed). What remains
split is the *intent* layer: promotion lives in `StorageScreenControllerImpl`, swap/removal live in
Storage and Home. Recommendation: fold those three into a `FarmRosterCommands` facade.

### P2 — over-limit rotation is unexplained — **open**
When membership exceeds the resident limit, the surplus rotates hourly. The roster sheet shows
"showing N of M" but does not say partners rotate. Recommendation: say it, and offer the limit beside it.

### P2 — "Follow" reads like membership — **open**
Follow only moves the camera. Now that membership is explicit this is merely a naming problem; the
sheet's primary action should read as a camera focus.

### P2 — pick mode is invisible in Storage — **open**
With the card removed, pick mode is a snackbar and leaving silently cancels it. Recommendation: a quiet
subtitle in the Storage header instead of a card.

### P2 — away-to-game partners are only partly excluded — **open**
`orderedFarmCandidates` excludes `isInAdventure` but not partners sent to a game. Since membership is
curated this is now unlikely, but a stale member sent away can appear while its taps are refused.
Recommendation: exclude away partners from the roster.

## Where membership is curated

The "who is here" sheet (tap the resident count in the top bar) is the single place that decides how
the island is populated: the resident quantity, the artwork filter, and the member list. It lists only the
partners currently on the island (each removable, the active one read-only), and offers "Add member"
while the island is below its quantity. That button opens a picker of stored partners, so the sheet
never dumps the whole collection.

## Actions available to the visitor

- Resident sheet, non-leader: **Follow**, **Set as active**, **Swap for one in Storage**, **Remove from
  Digifarm**, **Chat**.
- Resident sheet, leader: all of the above except remove (the leader is always resident).
- Storage, swap mode: tapping a partner exchanges membership with the outgoing partner; if the outgoing
  was the leader, the chosen partner also becomes active.
