# Digiline quest extension

Mode: Operate. Extend the established Android Digiline surface and its existing
theme-aware typography, native tabs and action vocabulary.

## Direction contract

THESIS: Natural Digimon conversation leads the experience; saved, readable quest
facts explain what the app will actually count. Quests moved out of Digiline into
their own More-tab surface; chat offer messages deep-link there.

OWN-WORLD: Inherit DESIGN.md and Material theme roles. Use existing VitalButton
controls, ordinary text rows and structural dividers rather than nested panels.

STORY: A player notices an available favor, talks to its giver, accepts an exact
contract, sees progress, and returns for a registered reward or recruitment.

FIRST VIEWPORT: The Quests tab exposes current/history and category filters above
giver/objective rows. A private conversation keeps chat primary and reveals its
quest details through an expandable, height-bounded section.

FORM: A standalone Quests destination under More reusing the journal composition.
Offer chat messages carry a View quest button that focuses the quest there; radar
tracking is a direct destination handoff.

FINISH: Implementation and documentation only. Rendering checks, tests, accessibility
checks and review are explicitly deferred until the owner requests them.

## Second-slice extension

The journal exposes ordered current/locked steps and held quest-only objects. Target
contacts show their participant role and route back to the giver; giver-only actions
stay scoped to that conversation. Partner acceptance reuses the existing full-screen
Storage selector. Radar encounters expose explicit meeting/recovery/delivery actions
inside the incumbent encounter sheet and its location-gated command flow. All new
copy is supplied in English, Brazilian Portuguese and Japanese. Visual verification
and review remain deferred under the owner's instruction.

## Third-slice extension

Trial rules are explicit text beneath the objective and in the incumbent Radar
encounter sheet. The journal reports the latest failed requirement without replacing
the saved objective or implying that a victory alone completed it. Multi-recipient
work exposes a named tracking action for every current target and an exact inventory
handover label. Technique demonstrations route to the existing selected-partner
loadout screen. Native typography, theme roles and interaction controls remain the
surface vocabulary; testing, rendering checks and review remain deferred.

## Fourth-slice extension

Normal quests expose bounded chapter links and their planned next request. Completed
parents with unprepared continuations remain visible in the current view, naming
the missing prerequisite and offering retry/skip controls in the giver's conversation.
Next-request links choose current/history from the saved child state. Recruitment
challenge/service/rescue titles and ordered watch preparation inherit the existing
quest-entry structure. Rendering checks, testing and review remain deferred.
