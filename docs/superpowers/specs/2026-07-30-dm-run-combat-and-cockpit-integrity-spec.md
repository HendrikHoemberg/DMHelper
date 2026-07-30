# DM Run — Combat and Cockpit Integrity Specification

**Date:** 2026-07-30

**Status:** Findings specification, not yet approved for implementation

**Origin:** Live browser run of the app at `127.0.0.1:8081` against the imported campaign
*Die Verlorene Mine von Phandelver* (`4160a434-8d06-4371-9825-b311444f8442`), driven with
Playwright/Chromium at 1512×950 on the code at commit `18b35871`. The run played the opening
of the adventure as a DM would: the wagon-escort scene, the Cragmaw ambush on the
Dreieber-Pfad (initiative, damage, conditions, four goblin deaths, end encounter), a second
encounter (Burg Cragmaw 12) started from the encounter rail, the Redbrand pit-trap scene, and
the session review flow.

**This document is a specification, not an implementation plan.** It states, for each defect
and gap, the observed symptom, the evidence, the verified or inferred cause, the behavior the
product must have instead, and the acceptance criteria that prove it. Selecting the change
set, sequencing, and file-level design is the job of the implementation plan that follows.

---

## 1. Purpose

Combat is the surface a DM touches most under time pressure, in front of players, with no
opportunity to debug. The run found that the *mechanics* are largely correct — damage lands on
the right creature, defeat and bloodied states are modelled, initiative persists — while the
*surfaces around* those mechanics fail in ways that either lose the DM's work or, worse,
silently apply it to the wrong creature.

The goal of the work this spec scopes is that a DM can run a prepared encounter from start to
finish, switch to the next encounter, and close the session, without:

- losing the tactical map,
- being unable to tell which creature a control affects,
- having a control apply to a creature other than the one it appears attached to,
- seeing a rules state silently expire that 5e says persists,
- or being told the session failed to save when nothing failed to save.

## 2. Scope

**In scope.** The session cockpit runtime: the Encounter module and combat tracker, the Map
module during a live encounter, the Reference module, session lifecycle transitions, the
runtime failure-signalling surface, and the presentation-level polish items enumerated in §14.

**Out of scope.** Campaign import and package format; the map editor and authoring surfaces;
the library CRUD screens; the campaign preparation screens; any change to the campaign data of
the evaluated campaign; adding new game-rule automation beyond what §8 requires.

**Explicitly not requested by this spec.** A visual redesign. Every item below is either a
correctness defect or a legibility/affordance defect with a concrete failure mode at the table.

## 3. How to reproduce the environment

The findings were produced against a running instance with an imported campaign that has:
90 scenes in 4 chapters, 7 maps (one regional hex map, five battle maps, one scene image),
49 planned encounters, 4 party members, 10 traps/hazards. Any campaign with **at least two
encounters bound to two different maps** and **at least one encounter containing a monster
group of two or more identical creatures** reproduces the P0 and P1 findings.

Screenshots referenced by number below were captured during the run. They are session
artifacts, not committed; the reproduction steps are sufficient to regenerate each.

## 4. Severity scale used in this document

| Level | Meaning |
|-------|---------|
| **P0** | Loses DM work or a core runtime surface mid-session; no in-app recovery. |
| **P1** | Silently applies an action to the wrong target, or misstates game or session state. |
| **P2** | Blocks a normal task or forces a workaround; no data harm. |
| **P3** | Legibility, affordance, or copy defect with a concrete cost at the table. |

---

## 5. Workstream A — Runtime map integrity

### A1 (P0) — Activating an encounter bound to a different map destroys the map surface

**Symptom.** With an encounter active on map X, running a planned encounter bound to map Y
leaves the Map module with its chrome intact (map switcher, tool row, participants list,
"Ready" status) and **no canvas at all**. Three stacked error toasts appear reading
"Could not load map tokens. The request was not valid. Check the entered values and try again.
Reference: `<uuid>`" each with a Retry button. The DM has no working map for the fight they
just started.

**Evidence.**
- `document.querySelectorAll('canvas').length` transitions from `6` to `0` across the
  activation.
- Network: `GET /api/v1/maps/44d37260-…/runtime-tokens?encounterId=a7d8362b-…` → **400**,
  three times. `44d37260` is the *previous* map (regional); `a7d8362b` is the *new*
  encounter, which is bound to `2e6e9316` (Burg Cragmaw battle map).
- Reproduced outside the browser: the same request returns 400 with
  `{"title":"Validation Error", …}`; the same request against the encounter's own map id
  returns 200.
- Screenshots 46, 47.

**Cause — verified.** `RuntimeTokenProjectionService.resolveEncounter` intentionally rejects a
(map, encounter) pair that does not belong together
(`throw new IllegalArgumentException("Encounter does not belong to map")`). The client reaches
that state because `session-cockpit.js#activateEncounter` updates `this.currentMapId` from the
activation response and calls `window.battleMap.setActiveEncounter(encounterId)` — which sets
`activeEncounterId` and immediately re-fetches tokens using the **stale** `bm.mapId` — but
never calls `bm.switchToMap(...)`.

**Cause — inferred, to be confirmed during planning.** The canvas disappears because
`activateEncounter` then calls `cockpitModules.load('map', { force: true, mapId })`, which
replaces the Map module body server-side, detaching the Konva stage; `initBattleMap` guards on
`if (this._battleMapInitStarted || window.battleMap) return;` so no BattleMap is ever attached
to the fresh container. The code comment at that call site already notes the Map module is in
`PRESERVED_KEYS` and is force-loaded anyway.

**Required behavior.**
1. Activating an encounter must move the map surface to that encounter's map as a single
   coherent transition: the rendered map, `bm.mapId`, `bm.activeEncounterId`, the map picker
   selection, the participants list, and the marker list must all describe the same encounter
   and map at every moment a user could observe them.
2. No request may be issued for a (map, encounter) pair the server is documented to reject.
   The client is responsible for ordering; the server guard stays as a guard.
3. If the map module body is re-rendered for any reason during a live session, the interactive
   map must be re-attached to the new container. A detached map is a bug, never a resting
   state.
4. Any residual failure of this transition must be recoverable **in-app**: a Retry that
   actually re-runs the whole transition, not a retry of one sub-request that cannot succeed.

**Acceptance criteria.**
- A browser test that activates encounter B (map Y) while encounter A (map X) is active
  asserts: a canvas exists, its map id equals Y, the participants list contains exactly B's
  combatants, and zero 4xx/5xx responses were recorded for the interaction
  (`BrowserFailureCollector.assertNoFailures()`).
- The same test asserts no error toast is present.
- A test asserts that force-reloading the map module body while a session is running leaves an
  attached, rendering map.

### A2 (P1) — Map participants and markers keep showing the previous encounter

**Symptom.** After the A1 transition, the "Encounter Participants" list in the Map module still
listed the *previous* encounter's combatants (four struck-through goblins, Sildar at 17/22)
while the Encounter module already showed the new encounter's hobgoblins. Two panels on screen
disagreed about who was in the fight. Manually re-selecting the correct map in the map switcher
corrected the participants and markers but did **not** restore the canvas, and produced a
further toast: "Could not update the player AoE overlay. The DM map was kept. The requested
item could not be found. Reload and try again."

**Evidence.** Screenshots 46 (stale list) and 48 (corrected list, still no canvas).

**Required behavior.** The Map module's participant list, marker list, and token layer are
projections of the active encounter and active map. They must never render a state older than
the tracker's. If a projection cannot be refreshed, the module must show that it is stale
rather than presenting old data as current.

**Acceptance criteria.** The A1 browser test additionally asserts the participants list
contains no combatant belonging to the previous encounter.

### A3 (P2) — Only a full page reload recovers the map

**Symptom.** Once A1 has occurred, no in-app action restores the map. Re-selecting the map,
ending the encounter, and switching presets all leave the canvas absent. `F5` restores it.

**Required behavior.** Every runtime module must have an in-app recovery path from its own
failure state. "Reload the page" is acceptable as *advice inside a message*, never as the only
mechanism. Where a module's error affordance says "Retry", retrying must be capable of
succeeding.

**Acceptance criteria.** A test drives the module into a failed load, clicks the module's own
Retry, and asserts the module reaches a ready state without a navigation.

---

## 6. Workstream B — Combat tracker identity and targeting safety

These four findings compound: the DM cannot read who a row is, cannot see which row a control
is bound to, and one control is bound to a different creature than the row it sits in.

### B1 (P1) — Monster group rows render with no name, and the group toggle is unreachable

**Symptom.** A grouped monster row in the tracker renders either as an empty name cell
(observed: a row reading only `— 11/11` for a two-hobgoblin group) or as a mid-string fragment
(observed: `n (Wache) …` at a 450px rail). The `+N more` expand button is never visible at any
rail width tested. Expanding the group is impossible with a mouse; during the run it could only
be triggered by calling `.click()` from script. Once expanded, the four goblins all render as
`├ Gob…` — indistinguishable from one another.

**Evidence.** Screenshots 19 (zoom of a blank group row), 21 (expanded group, four identical
rows), 57 (`n (Wache) …` at 450px), 63 (`— 11/11`). Measured geometry: `.combatant-name` was
53px wide with the `.group-count` button laid out at an x-origin *left of* the name box and
extending past its right edge.

**Cause — verified.** `components.css` sets
`.combatant-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }`
and `_tracker.html` places the `.group-count` toggle **inside** that element, after the name
text. Truncation therefore competes with an inline-block sibling: the ellipsis mechanism
applies to the text run while the button is clipped out of the box entirely, and in the
narrowest case neither survives.

**Required behavior.**
1. A combatant row must always render an identifying label. Truncation is acceptable;
   rendering nothing is not.
2. When several identical creatures are grouped, each member must be distinguishable from its
   siblings in the tracker. The distinguishing part of the name (the ordinal) must survive
   truncation — an ellipsis in the middle rather than at the end, or an explicit ordinal
   element, or another mechanism of the implementer's choosing.
3. The group expand/collapse control must be visible and clickable at the default rail width
   and at every width the layout permits. It must not participate in the name cell's
   truncation.
4. Expanding a group must reveal its members; collapsing must hide them. Both directions must
   be reachable by pointer and by keyboard, with correct `aria-expanded` state.

**Acceptance criteria.**
- A contract/render test asserts that for a group of N ≥ 2, the group row's accessible name is
  non-empty and the toggle's bounding box lies fully inside the visible module.
- A browser test at the default rail width clicks the toggle by pointer, asserts the member
  rows become visible, clicks again, asserts they hide.
- A browser test asserts that for a four-member group, the four rendered labels are pairwise
  distinct strings.

### B2 (P1) — The per-row "＋" condition button targets a different creature than its row

**Symptom.** Clicking the "＋" in **Grumbar Steinfaust's** row and then clicking "Grappled" in
the Conditions palette applied Grappled to **Sildar Brakk**. No error, no warning; the DM sees
the condition badge appear on a row they were not looking at.

**Evidence.** Verified in the DOM: after the sequence, `conds=1` on Sildar's row and `conds=0`
on Grumbar's. `Alpine.$data(...).selectedCombatantId` remained Sildar's id throughout.

**Cause — verified.** The row button calls `openConditionMenu(c.id)`, which assigns
`this.showConditionMenu = id`. **`showConditionMenu` is not referenced by any template in the
repository** — it is dead state. The button additionally carries `@click.stop`, which suppresses
the row's `selectCombatant(c.id)`. The visible Conditions palette lives in the selected-combatant
detail panel and is bound to `selected`, i.e. `selectedCombatantId`. So the button that appears
to target a row provably cannot, and the palette it appears to open belongs to a different
creature.

**Required behavior.** Either the per-row control opens a menu scoped to that row's combatant,
or it retargets the shared editor to that combatant, or it is removed. What must not remain is
a control whose apparent target and actual target differ. No control in the tracker may mutate
a combatant other than the one it is visually attached to.

**Acceptance criteria.**
- A browser test clicks the per-row condition control on row *i*, applies a condition, and
  asserts the condition is present on combatant *i* and absent on all others.
- A static check (extend the existing template/contract test family) asserts that every state
  key written by the tracker component is read by at least one template, so dead targeting
  state cannot reappear.

### B3 (P1) — Nothing indicates which combatant the HP and Conditions editor is editing

**Symptom.** The detail panel below the tracker edits `selectedCombatantId`. The tracker's only
row emphasis is the **active turn**. The selected row carries no marker, and the detail panel
carries no title — its first element is the HP field. After a few turns the DM is editing a
creature that is neither highlighted nor named anywhere on screen. Observed concretely: with
the turn on Nienna and the panel bound to Sildar, the visible highlight and the editor pointed
at different creatures.

**Evidence.** Screenshots 30, 31, 33; template `_tracker.html` — the `.combatant-detail` block
opens directly with the HP section and has no heading.

**Required behavior.**
1. The detail panel must name the combatant it is bound to, prominently enough to read at a
   glance.
2. The selected row must be visually distinguished, and distinguishable from the active-turn
   row, which is a different concept.
3. Selection must have a defined lifecycle: what happens to the selection when the turn
   advances, when a combatant is defeated, when a combatant is removed, and when the encounter
   ends must all be specified and implemented rather than emergent.

**Acceptance criteria.**
- A browser test asserts the detail panel's heading text equals the selected combatant's name,
  and that selecting a different row updates it.
- A browser test asserts that when the selected combatant differs from the active combatant,
  two visually distinct row treatments are present.

### B4 (P2) — The HP delta input is one shared model across all rows

**Symptom.** Typing `-6` into one row's `±HP` box makes `-6` appear simultaneously in every
combatant's `±HP` box. The screen states that eight creatures are about to take 6 damage.
Pressing Enter applies it only to the focused row — the behavior is correct, the display is not.

**Evidence.** Screenshot 22 (all eight rows showing `-6`). Template: every row's input carries
`x-model="hpDelta"` against a single component-level property.

**Required behavior.** A per-row entry field must display per-row state. Whether the value is
kept per combatant or cleared on blur is an implementation choice; what must not happen is one
row's in-progress entry appearing in another row.

**Acceptance criteria.** A browser test types into row *i*'s delta input and asserts every other
row's delta input is empty.

### B5 (P3) — Name truncation at the default rail width

**Symptom.** At the default 360px right rail, PC names render as `Pip Fi…`, `Nienn…`, `Grum…`,
`Sil…`. Each condition badge added to a row shortens the name further. Widening the rail to
450px resolves it for PCs (but not for group rows — see B1).

**Evidence.** Screenshots 17, 20, 34, 35; rail width measured at 360px by default.

**Contributing factor (P3, related).** Panel splitters only respond to pointer and keyboard
input while the workbench is in layout-edit mode
(`cockpit-layout.js#onSplitterKey` and the pointer handler both gate on
`layoutMode === 'edit'`). A DM who wants a wider tracker mid-fight must first discover
"Edit layout", resize, then leave edit mode through a confirm dialog. Nothing on the splitter
communicates that it is inert.

**Required behavior.**
1. At the default rail width, a combatant row must show enough of the name to identify the
   creature among the others present. The row's column budget — initiative badge, name, HP
   text, HP bar, delta input, condition icons, add-condition control — must be resolved in
   favour of identity.
2. Either splitters are interactive during a live session, or their inert state is
   communicated (cursor, tooltip, or affordance) rather than silent.

**Acceptance criteria.**
- A render test asserts that at the default rail width, for a roster of four PCs with
  characteristic Phandelver-length names, no rendered label is shorter than a stated minimum
  number of characters (value to be chosen during planning).
- A browser test asserts that a splitter interaction outside layout-edit mode either resizes
  the zone or surfaces an explanation.

---

## 7. Workstream C — Rules fidelity of conditions

### C1 (P1) — Every condition expires after one round

**Symptom.** Applying Prone from the quick palette and advancing one full round removed the
condition. The same default applies to every condition in the catalog.

**Evidence.** `combat-tracker.js` `COMMON_CONDITIONS` — all fourteen entries carry
`defaultDuration: 1`. Verified end-to-end: Sildar marked Prone in round 1, `cond-icon` count
`0` in round 2.

**Why it matters.** In 5e, Prone, Grappled, Restrained, Petrified, Incapacitated, Paralyzed,
Unconscious, Blinded, Deafened, Charmed, Frightened, Invisible, Poisoned and Stunned do not
share a one-round duration. Most persist until removed, until a save succeeds, or for a spell's
duration. A DM who marks a downed PC Unconscious and finds them unmarked next round has been
given wrong information by the tool.

**Required behavior.**
1. The default duration for a condition applied from the quick palette must reflect how that
   condition actually ends. Conditions with no inherent duration must default to indefinite,
   displayed as such (the tracker already renders `∞` when `durationRounds` is not positive).
2. Where a duration is set, its decrement and expiry must remain as they are — the mechanism is
   sound, only the defaults are wrong.
3. A DM must be able to set an explicit duration when applying a condition, and to end a
   condition explicitly.

**Acceptance criteria.**
- A unit test asserts the default duration of each catalogued condition against a table stated
  in the implementation plan and reviewed against SRD 5.2.
- A browser test applies Prone, advances two full rounds, and asserts it is still present.
- A browser test applies a condition with an explicit 1-round duration and asserts it expires.

### C2 (P3) — Condition badges are unreadable without hover

**Symptom.** A condition renders as a coloured dot containing the remaining round count. Prone
was a blue "1", Grappled an orange "1". The condition's identity is carried only by colour and
a `title` attribute. At the table nobody hovers.

**Required behavior.** A condition on a combatant row must be identifiable without pointer
hover — an abbreviation, a legend, or an expanded presentation at wider rail widths. Colour
alone must not be the sole carrier of meaning.

**Acceptance criteria.** A render test asserts each condition badge exposes a text carrier of
its identity, not only a colour and a numeric duration.

---

## 8. Workstream D — Reference and statblock availability during a fight

### D1 (P1) — Compendium search renders results as blank rows

**Symptom.** Searching `gob` in the cockpit Reference module returns five category headings —
`scene`, `encounter`, `statblock`, `trap`, `note` — with **nothing under them**. The rows exist
and are clickable, but render as invisible strips. Clicking blindly does open the right item.

**Evidence.** `Alpine.$data` on the reference component showed 20 results across five groups
(6 scenes, 5 encounters, 5 statblocks, 2 traps, 2 notes). `document.querySelectorAll('.reference-item').length === 20`.
Item shape: `{id, title, type, subtype, url}`. Template
`session/modules/_reference.html` binds `x-text="item.name"` and `x-text="item.source"` —
neither field exists on the payload. Screenshot 40.

**Required behavior.** Search results must render their title and a secondary qualifier. The
contract between the search payload and the template must be pinned by a test so a field rename
cannot silently blank the list again.

**Acceptance criteria.**
- A browser test searches a term with known matches and asserts every rendered result row has
  non-empty visible text.
- A test asserts the rendered text of at least one row equals the `title` returned by the
  search endpoint.

### D2 (P2) — The in-run statblock is AC/HP/XP only

**Symptom.** Opening a statblock inside the cockpit Reference module yields a card with the
name, type and CR, Armor Class, Hit Points and XP. The library page for the same creature shows
the complete 2024 SRD block: ability scores, speed, skills, senses, languages, actions
(Scimitar, Shortbow with the advantage rider) and bonus actions (Nimble Escape). The DM cannot
read the goblin's attack without leaving the cockpit.

**Evidence.** Cockpit payload observed as
`{id, name, cr, type, hp, ac, xp}`. Screenshots 41 (cockpit card) vs 42 (library page).

**Required behavior.** The statblock a DM opens during a live encounter must contain everything
needed to run the creature's turn: at minimum attacks with to-hit and damage, and traits or
bonus actions that change how the creature acts. Whether that is the full block or a defined
"run-time subset" is a product decision (§16, D-1), but AC/HP/XP alone is not runnable.

**Acceptance criteria.** A test asserts the cockpit statblock projection for a creature with
actions exposes those actions, and a browser test asserts they render.

### D3 (P2) — Reference and Encounter share one zone in the Combat preset

**Symptom.** In the Combat preset, `encounter` and `reference` are tabs of the same right-hand
zone (`CockpitBuiltInPresetCatalog`: `zone("encounter", "reference")`). Looking up a rule or a
statblock hides the initiative order and every HP bar.

**Required behavior.** During an active encounter, consulting a reference must not remove the
tracker from the screen. Any of: a different default placement, a reference surface that
overlays without replacing the tracker, or an inline statblock affordance within the tracker
satisfies this. The choice is a product decision (§16, D-2).

**Acceptance criteria.** A browser test opens a statblock during an active encounter and
asserts the initiative order is still visible.

### D4 (P3) — The dice drawer occludes the tracker

**Symptom.** The Dice Roller opens as a right-edge drawer that covers the entire right rail,
i.e. the combat tracker. The two surfaces a DM uses together cannot be seen together. The
drawer is also mostly empty space.

**Evidence.** Screenshots 25, 30.

**Required behavior.** Rolling dice during combat must not hide the initiative order or HP.

**Acceptance criteria.** A browser test opens the dice roller during an active encounter and
asserts the tracker remains visible.

---

## 9. Workstream E — Session lifecycle truthfulness

### E1 (P1) — "Cancel Review" pauses the session and discards the draft

**Symptom.** Opening **Review & Complete** moves the session `RUNNING → REVIEW`. Clicking
**Cancel Review** leaves it `PAUSED`, not `RUNNING`. A DM who opens the review to glance at the
draft and backs out has silently paused their session. The draft body is also discarded without
warning.

**Evidence.** Measured transitions: `1) RUNNING → 2) REVIEW → 3) PAUSED`.
`SessionLifecycleService.cancelReview` sets `Status.PAUSED`, `reviewStartedAt = null`, and
`draftBody = null`.

**Required behavior.** "Cancel" must mean "return to the state I was in". Either the prior
status is restored, or the action is renamed and its consequences stated before it runs. Draft
loss must be either prevented or explicitly confirmed. Which of the two (§16, D-3).

**Acceptance criteria.**
- A service test asserts that cancelling a review entered from `RUNNING` yields `RUNNING`, and
  from `PAUSED` yields `PAUSED` — or, if the product decision is to keep the destructive
  semantics, asserts that the UI names them and confirms them.
- A test asserts draft content is not lost without an explicit confirmation.

### E2 (P2) — The lifecycle dialog says "Session Running" while the session is paused

**Symptom.** With the badge reading `PAUSED` and the primary action reading `Resume`, the dialog
heading still reads **Session Running**.

**Evidence.** `_lifecycle-dialog.html` line 26: `<h3>Session Running</h3>` inside a block shown
for `sessionStatus === 'RUNNING' || sessionStatus === 'PAUSED'`. Screenshot 60.

**Required behavior.** The dialog heading must state the session's actual status.

**Acceptance criteria.** A browser test pauses a session, opens the dialog, and asserts the
heading reflects the paused state.

### E3 (P3) — Destructive lifecycle actions sit at equal weight beside benign ones

**Symptom.** `Discard session` renders inline with `Close` at identical visual weight, in both
the lifecycle dialog and the review dialog. `End encounter`'s confirm dialog, by contrast, is
well done — clear title, plain consequence statement, danger-styled confirm. That treatment is
the standard the others should meet.

**Evidence.** Screenshots 55, 56 vs 37.

**Required behavior.** Destructive session actions must be visually separated from dismissive
ones and must confirm with a statement of what is lost. Apply the existing `End active
encounter?` dialog pattern.

**Acceptance criteria.** A contract test asserts every destructive action in the lifecycle and
review dialogs carries the danger treatment and a confirmation step.

---

## 10. Workstream F — Failure signalling

### F1 (P1) — Invalid user input flips the global "saved" indicator to "Not saved"

**Symptom.** Typing an unsupported dice expression (`2d6+2 slashing`, `3d8 fire damage`) into
the dice roller turned the cockpit header's save indicator red and set it to **Not saved**. The
session had saved fine; only the input was rejected.

**Evidence.** `runtime-status.js#failed()` sets `setSave('error', 'Not saved')` for any failed
DM mutation, including a 400 from `POST /api/v1/roll`. Screenshot 29.

**Required behavior.** The persistence indicator must report persistence. A rejected input is
not a failed save. Validation failures (4xx caused by user input) and save failures
(5xx, network, conflict) must be distinguishable to the DM, and only the latter may change the
global indicator.

**Acceptance criteria.** A browser test submits an invalid dice expression and asserts the
header save indicator does not enter its error state, while a test that forces a genuine save
failure asserts that it does.

### F2 (P3) — Error toasts expose correlation UUIDs and offer futile retries

**Symptom.** The dice error toast reads "The dice roll was not saved. Invalid dice expression:
3d8 fire damage Reference: `240ed084-ecdc-4ae6-9ef9-487aad5b5853`." with a **Retry** button that
can only fail again. The map token toasts likewise print a UUID inline and stack three deep.

**Required behavior.**
1. A correlation id must remain available for support, but must not be primary body text.
2. Retry must be offered only where retrying can succeed. For a validation failure the
   remedy is to correct the input.
3. Repeated identical failures must coalesce rather than stack.

**Acceptance criteria.** A test asserts a validation-class failure toast offers no Retry, and
that N identical failures produce one toast.

### F3 (P3) — A module can hold an error state the DM can never see

**Symptom.** While the Encounter module sat in the module depot (not placed in the active
preset), it held the rendered error "Encounter could not refresh. Existing content was kept."
with a Retry — invisible, because the module was not on screen.

**Required behavior.** Either depot modules do not attempt refreshes, or a module that fails
while off-screen surfaces that fact when it is next shown, or via the module attention
indicator.

**Acceptance criteria.** A test asserts a module that failed while hidden shows its error state
when made visible.

---

## 11. Workstream G — Making the encounter and map surfaces discoverable

### G1 (P2) — Starting an encounter from the Exploration preset produces no visible change

**Symptom.** In the default Exploration preset, clicking the scene's primary action
**"Run this encounter"** produced a pixel-identical screen. The encounter *had* started; the
DM only discovers this by switching to the Combat preset. Likewise, the map chosen in the
**Start Session** dialog has no visible effect, because no map surface exists in that preset.

**Evidence.** `CockpitBuiltInPresetCatalog`: `builtin:exploration` contains
`story, session-plan, party, quick-notes, audio, session-log` — neither `map` nor `encounter`.
Screenshots 11 (before) and 11 (after) are identical; 12 shows the state after switching preset.

**Required behavior.** An action that changes runtime state must produce a visible result on
the screen where it was invoked. Acceptable resolutions include: switching the preset as part
of the action, surfacing the affected module, or telling the DM what happened and offering the
switch. Which one is a product decision (§16, D-4). The same requirement applies to the Start
Session map choice.

**Acceptance criteria.** A browser test in the Exploration preset runs an encounter from a
scene and asserts that within one interaction the initiative surface is visible.

### G2 (P3) — Two similar actions, two different labels, one slot

**Symptom.** The same position in the scene action row reads **"Start encounter from this
scene"** when the scene has no prepared encounter and **"Run this encounter"** when it does.
Adjacent to them, "Open" and "Map" render as low-affordance plain text.

**Required behavior.** The scene action row's labels must distinguish creating an ad-hoc
encounter from running a prepared one, and its controls must share a consistent affordance
level.

### G3 (P3) — Re-running a finished encounter silently resurrects it

**Symptom.** Clicking "Run this encounter" on a scene whose encounter has already been fought
reopens it at Round 0 with all monsters still dead and initiative preserved. No warning, no
offer to reset.

**Required behavior.** Re-running an encounter that has already concluded must state that it is
resuming a finished encounter and offer the reasonable alternatives (resume as-is, or reset).

**Acceptance criteria.** A browser test ends an encounter, re-runs it from the scene, and
asserts the DM is informed of the resumed state.

---

## 12. Workstream H — Combat close-out

### H1 (P2) — No XP, no rewards, no record of the fight

**Symptom.** Ending an encounter in which four goblins were defeated produced: a
"FINISHED THIS SESSION" rail row with a **Reopen** action, and nothing else. No XP total (the
ambush is 200 XP), no treasure or reward prompt, and no entry anywhere recording that the fight
happened. The trap scene similarly states "100 EP fürs Überwinden" as prose the app cannot
record.

**Evidence.** Screenshots 38, 45. Encounter payloads carry `xp` per statblock (observed
`xp: 25` for Goblin Minion), so the input data exists.

**Required behavior.** Concluding an encounter must produce a summary the DM can act on: at
minimum the XP earned from defeated combatants, and any rewards the encounter declares. Whether
the app *awards* XP to party members or only reports it is a product decision (§16, D-5).

### H2 (P2) — The session log draft omits encounters entirely

**Symptom.** The generated draft contains Session Date, In-Game Date, Attendance, Scenes and
Quest Progress. After a combat-heavy session it contains **no mention of any encounter**.

**Evidence.** Screenshot 56 — full draft body, two scenes listed, zero encounters.

**Required behavior.** The draft must include the encounters run during the session, their
outcome, and the XP total from H1. The draft is the DM's record of the session; a session whose
main event was a fight must not produce a log that omits it.

**Acceptance criteria.** A test runs and ends an encounter within a session, opens the review,
and asserts the draft names the encounter and states its outcome.

---

## 13. Workstream I — Encounter rail information architecture

### I1 (P2) — 49 encounters in one flat, lexicographically sorted list

**Symptom.** "Planned encounters" lists every encounter in the campaign as a flat list ordered
`Alter Eulenbrunnen…, Burg Cragmaw 12, Burg Cragmaw 13, Burg Cragmaw 14, Burg Cragmaw 3,
Burg Cragmaw 4, Burg Cragmaw 6, …`. The numeric ordering reads as broken, and nothing indicates
which encounters belong to the scene the DM is currently in.

**Evidence.** Screenshot 38; 49 `Run` controls counted in the DOM.

**Required behavior.**
1. Ordering must be natural (`3, 4, 6, 12, 13, 14`), not lexicographic.
2. The rail must foreground the encounters relevant to the current scene or chapter, with the
   full list available but not primary.
3. The existing filter box must remain.

**Acceptance criteria.** A test asserts natural ordering for numerically-suffixed names, and a
browser test asserts that with a current scene set, that scene's encounters appear before
unrelated ones.

### I2 (P3) — Rail row layout

**Symptom.** The finished-encounter row wraps its metadata (`Schwertküste um Phandalin —
Regionalkarte · 8 combatants`) across two ragged lines with the action floated mid-height,
reading as a three-column jumble.

**Note.** Commit `18b35871` already aligned the suspended/finished rows with the planned-row
grid. This finding is the residual: the meta line's own wrapping at rail width.

---

## 14. Workstream J — Map token legibility

### J1 (P2) — Tokens auto-place in a clipped row at the canvas origin

**Symptom.** On encounter start, all combatants are placed in a single row at grid row 0
starting at column 0. At the default view they are clipped by the top edge of the visible
canvas — only the lower half of each token is on screen. On the regional map they sit in an
unrelated corner of the world; on the battle map the party is parked outside the room.

**Evidence.** Screenshots 12, 13, 49, 51.

**Required behavior.**
1. Auto-placement must place combatants somewhere meaningful and fully visible: the map view
   must frame the placed tokens, or placement must respect a declared start area.
2. Whatever placement is chosen, no token may render clipped by the canvas edge on load.

### J2 (P2) — Adjacent tokens merge into one solid block

**Symptom.** Eight pre-authored markers in Burg Cragmaw render as flat red squares packed
edge-to-edge into a single 2×4 red slab covering a room. Labels truncate to `Ho 1`, `Ho`, `Go` —
the four distinct goblin archers are indistinguishable, as are the two hobgoblins.

**Evidence.** Screenshot 51.

**Required behavior.** Each token must read as a separate creature — separation, outline, or
shape. A token's label must carry enough of the creature's identity to tell it from its
siblings, or identity must be carried another way (an ordinal, an initiative number).

### J3 (P3) — Token affordances

**Symptom.** Tokens are flat colour-filled rectangles with a two-letter label and a small
`HP/HP` string beneath. PC and NPC differ only by fill colour. The active combatant is
indicated by a thin outline. No condition indication appears on the map.

**Required behavior.** To be settled during planning: at minimum, PC/NPC distinction that does
not rely on colour alone, and a clearer active-turn treatment.

### J4 (P3) — Konva layer count warning

**Symptom.** The console repeatedly logs
`Konva warning: The stage has 6 layers. Recommended maximum number of layers is 3-5.`

**Required behavior.** Either consolidate to the recommended layer count or record a deliberate
decision that six is intended, so the warning stops being noise that hides real console errors.

---

## 15. Workstream K — Copy, controls and presentation

Individually small, collectively the difference between a tool that feels finished and one that
does not. Each has a concrete cost at the table.

| ID | Severity | Finding | Required behavior |
|----|----------|---------|-------------------|
| K1 | P3 | `Add party` and `Roll unset NPCs` — the two primary initiative actions — render as bare text and read as labels, not controls. | Give the primary setup actions button affordance. |
| K2 | P3 | The initiative checkbox copy fragments into three visual pieces: `☐ Start with` / `8 unset` / `— they remain last in the displayed order`, wrapping badly at rail width. | One readable sentence. |
| K3 | P3 | The tie-breaker explanation paragraph is always expanded above the primary action. | Make it progressive disclosure or move it out of the primary path. |
| K4 | P3 | `Round 2` and the destructive `End` sit adjacent with `End` as small danger-coloured text. | Separate the encounter-ending control from the round readout. |
| K5 | P3 | The encounter header `◀ Prev / Active: <name> / Next ▶` wraps to three lines with long names, orphaning `Prev` and `▶`. | Lay out so long names do not break the turn controls. |
| K6 | P3 | Start Session and Session Lifecycle dialogs use unstyled native `<select>` and native checkboxes (system blue) inside the app's dark/gold theme. | Match the app's control styling, as the `End active encounter?` dialog already does. |
| K7 | P3 | The module attention badge renders with no separation, producing the tab label `Encounter1`. | Separate or reposition the count. |
| K8 | P3 | Raw enum leakage: trap damage renders `2d6 BLUDGEONING` inside an otherwise German campaign. | Present damage types as display text, localised where the campaign is. |
| K9 | P3 | Calendar dates render as `1 1. Monat 1491` in the cockpit header and in the session log draft. | Determine whether the defect is in rendering or in the imported calendar data, then render a date a human reads as a date. |
| K10 | P3 | Trap `Prefill 1d20` / `Prefill damage` controls render as low-contrast text and only reveal a border on hover; they are the key interaction of an otherwise excellent surface. | Give them a resting affordance. |
| K11 | P3 | The dice panel close control is `×` with no accessible name. | Give it a label. |
| K12 | P3 | Defeated monsters display negative HP (`-4/7`). | Confirm intent; if intended, keep — this is DM-useful — but state it. |
| K13 | P3 | The group leader's name changes as members die (`Goblin 1 +3 more` became `Goblin 3 +3 more`), so the group appears to change identity. | A group's label must be stable across member deaths. |
| K14 | P3 | Large unused regions: the campaign home's right half beside the description, the party rail below four cards, the dice drawer below the roll history. | Not a defect on its own; note as context for whoever revisits these layouts. |
| K15 | P3 | The party stat line `AC 14 HP 16/16 PP 14 / PI 10 PInv 11 Spd 30` uses unexplained abbreviations with no separators. | Make the passive scores legible; abbreviations need a legend or expansion. |

---

## 16. Product decisions required before planning

These cannot be resolved from the run; they need the product owner's call. The implementation
plan should not proceed past design on the affected items until they are settled.

| ID | Decision | Options observed |
|----|----------|------------------|
| D-1 | What does a statblock contain *during* a fight? | (a) Full library block. (b) A defined run-time subset — actions, traits, saves — with a link to the full block. |
| D-2 | How does reference material coexist with the tracker in Combat? | (a) Reference moves to another zone in the Combat preset. (b) Reference overlays without replacing. (c) Inline statblock inside the tracker row. |
| D-3 | What does "Cancel Review" mean? | (a) Non-destructive back-out restoring the prior status and keeping the draft. (b) Keep the destructive semantics but rename and confirm. |
| D-4 | How does a state-changing action behave when its surface is not in the active preset? | (a) Auto-switch preset. (b) Surface the module into the current layout. (c) Explain and offer the switch. |
| D-5 | Does the app award XP or only report it? | (a) Report only, in the encounter summary and session log. (b) Report and offer to apply to party members. |
| D-6 | Is there a minimum supported viewport? | The run was at 1512×950 and the tracker was already cramped. A 1366×768 laptop is a plausible DM machine; the answer changes the B5 column budget. |

---

## 17. Cross-cutting requirements

1. **Every behavioral fix in §5–§12 must be covered by a test that fails before the fix.** The
   repository already has the right vehicles: contract/render tests over templates, service
   tests, and Playwright browser tests with `BrowserFailureCollector`.
2. **Browser tests must assert the absence of failed network responses**, not only the presence
   of expected DOM. A1 would have been caught by that assertion alone.
3. **No new console errors or warnings** may be introduced; J4 asks for the existing one to be
   resolved or accepted deliberately.
4. **No regression in the surfaces the run found sound**: scene rendering with Read-Aloud and
   Development callouts, the trap surface and its dice prefill, damage application and bloodied
   /defeated states, group survival after all members are defeated, initiative persistence, the
   `End active encounter?` confirmation, the session review draft's existing sections, and the
   library statblock presentation.

## 18. What the run confirmed is working

Recorded so that the implementation plan does not "fix" it:

- Scene presentation: Read Aloud block, summary, DM notes, Development callouts, Participants
  line with compact stat summary, named transitions as buttons, linked entities.
- The trap surface: Trigger, Detection Check, Disarm Methods, Save, Damage, Additional Effect,
  Reset, References — with working dice prefill that opens the roller pre-loaded and labelled.
- Damage application targets the correct combatant; HP text, mini bar, bloodied flag and
  defeated strikethrough all update coherently, and the map token gains a red ✗.
- Monster groups remain on the tracker after every member is defeated (the `d7cca04b` fix
  holds).
- Initiative values persist across a full page reload; the order re-sorts correctly.
- The dice roller's expression parsing, breakdown display (`d20: [15] + 4`) and recent-roll
  history.
- Session review draft generation with real-world date, in-game date, attendance including
  player names, and scenes visited.
- The library statblock page — complete, correct 2024 SRD content, well set.
- `End active encounter?` confirmation copy and treatment.

## 19. State left on the evaluated instance

The run mutated runtime state on the developer instance and did not clean it up:

- A session is **running** on the Phandelver campaign (it was `IDLE` before the run).
- The Goblin-Hinterhalt encounter holds four defeated goblins.
- Sildar Brakk is at 17/22 HP and carries a Grappled marker.
- The Burg Cragmaw 12 encounter was started and ended.
- Layout preset was returned to Exploration and the rail-width change was discarded.

None of this is campaign content; all of it is session runtime state. Reset before using the
instance for a clean verification run.
