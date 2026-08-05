# Imported Campaign Presentation Repair — Design Specification

**Date:** 2026-07-21
**Status:** Proposed, not yet approved
**Trigger:** First end-to-end conversion of a real published adventure (64-page German
*Die Verlorene Mine von Phandelver*) into a campaign-format-v2 package, followed by a manual
DM walkthrough of the resulting campaign in a browser.
**Scope:** Presentation-layer defects and information-architecture gaps surfaced by running the
application against a *fully populated* campaign. No changes to the package format, import
pipeline, or persistence model are proposed.

## 1. Purpose

The import pipeline is correct. The 9.9 MB package produced from the source PDF validated with
zero errors and zero warnings, imported atomically, exported, and re-validated with every entity
count identical. Semantic content — verbatim boxed text, branching transitions, quest dependency
chains — survives the round trip intact.

The problem is what happens *after* import. When a DM opens a campaign carrying 90 scenes,
13 quests, 30 NPCs, 5 traps, 5 hazards and 3 rollable tables, the application presents that
content unevenly: one page is functionally broken, one inverts the content hierarchy so the
highest-value text is illegible, and several pages hide loaded content behind empty shells.

This specification records each defect with root cause, evidence, and target behavior, so an
implementation plan can be written without re-deriving the investigation.

### 1.1 Why existing tests did not catch this

Every defect below is invisible to a fixture-sized campaign. The repository's test corpus seeds
campaigns with one adventure, one chapter, one scene, and no world-graph entities. The defects
appear only at published-adventure scale, or only when an optional association is populated.
This is the central lesson: **the test fixtures need a populated campaign, not more assertions.**

### 1.2 Reproduction environment

- Application on `localhost:8081` with the imported campaign
  `Die Verlorene Mine von Phandelver`.
- Package artifact and build sources retained at
  `scratchpad/lmop/build/` (`out/lmop-de.dmcampaign`, 9.9 MB, ZIP container with 7 PNG assets).
- Screenshots of every screen discussed: `scratchpad/shots/01`–`10`.
- Browser used for capture: Playwright-cached Chromium at
  `~/.cache/ms-playwright/chromium-1181/chrome-linux/chrome`, headless, viewport 1440×900.

---

## 2. Findings

Ordered by severity. Each finding is independently implementable.

### F1 — World NPC pages truncate and hang the browser (P0, hard defect)

**Symptom.** `GET /campaigns/{id}/world/npcs` returns HTTP 200 with `Transfer-Encoding: chunked`,
emits roughly 22 KB, then stops mid-element. There is no `</html>`, no error page, and the
connection never terminates cleanly — a browser shows a partial list and spins indefinitely. A
`curl` without `--max-time` hangs; a headless Chromium screenshot attempt timed out after
120 seconds. `GET /campaigns/{id}/world/npcs/{npcId}` fails identically for affected NPCs.

**Root cause.** `WorldNpc` declares five `@ManyToOne(fetch = FetchType.LAZY)` associations
(`campaign`, `faction`, `location`, `note`, `statblock`) in
`src/main/java/dev/hendrikhoemberg/dmhelper/world/data/WorldNpc.java:18-46`.
`spring.jpa.open-in-view=false` is set in `src/main/resources/application.properties:12`.
`WorldService.getNpcs` (`world/service/WorldService.java:137-139`) delegates to
`WorldNpcRepository.findByCampaignIdOrderByNameAscIdAsc`, which issues no fetch join. The
transaction closes before rendering. Thymeleaf then dereferences the detached proxy:

- `templates/world/npcs-list.html:36` — `${npc.faction.name}`
- `templates/world/npcs-detail.html` — `${npc.faction.id}`, `${npc.faction.name}`,
  `${npc.location.id}`, `${npc.location.name}`

Hibernate throws `LazyInitializationException`. Because the response is already committed,
`GlobalExceptionHandler` cannot substitute an error page — the log records
`Response already committed. Ignoring: HttpMessageNotWritableException` — so the stream is
abandoned mid-write.

**Trigger condition — verified precisely.** The failure depends on the *data*, not the page:

| NPC | Faction link | Detail page result |
|---|---|---|
| Agatha | none | renders fully (22 354 bytes, closes) |
| Baumel | none | renders fully (22 185 bytes, closes) |
| Daran Edermath | Orden des Panzerhandschuhs | **truncated at 21 628 bytes** |

The list page dies at the first faction-linked NPC in alphabetical order, so 27 of 30 NPCs are
unreachable. In the imported campaign 9 NPCs carry `factionRef` and 11 carry `locationRef`;
every one of those detail pages is also broken.

**Why the suite misses it.** Two separate gaps:

1. `world/web/WorldControllerTest.java:61-68` is a `@WebMvcTest` with `@MockitoBean WorldService`
   returning `List.of()`. No entity, no proxy, no faction. It asserts the view name only.
2. `web/FullPageRenderSmokeTest.java` is a real `@SpringBootTest(RANDOM_PORT)`, **already
   requests `/world/npcs` (line 71)**, and **already asserts the exact failure mode** —
   lines 102-104 require `response.body().strip()` to end with `</html>`, carrying the message
   *"body for %s must end with </html> (truncation = lazy-init mid-render)"*. It passes only
   because `seed()` (lines 39-52) creates a Campaign, Adventure, Chapter and Scene and
   **no world-graph entities at all**.

The regression harness was designed for this bug and never given data that triggers it.

**Target behavior.** Both pages render completely for NPCs with any combination of faction,
location, note and statblock links, populated or null.

**Implementation direction.** Preferred: add an `@EntityGraph` or explicit `LEFT JOIN FETCH` for
`faction` and `location` on the list query, and a fetch-joined single-result query for the detail
view. Alternative: project to a read DTO in the service so templates never touch entities — this
matches the existing `structuredSceneView` pattern and is the more durable fix, but touches more
code. The plan should choose one and apply it consistently to `locations-detail.html`
(`${location.kind.name()}` is safe, but verify occupant/encounter collections) and any other
world template that dereferences an association.

**Acceptance criteria.**
- `/world/npcs` and `/world/npcs/{id}` return bodies ending in `</html>` for a campaign whose
  NPCs have factions and locations.
- No `LazyInitializationException` in logs during a full world-section walk.
- `FullPageRenderSmokeTest.seed()` is extended with at least one Faction, one WorldLocation, and
  one WorldNpc linked to both; the existing assertions then cover this permanently.

---

### F2 — Scene detail inverts the content hierarchy (P1, core workflow)

**Symptom.** On the prep-time scene page, read-aloud boxed text — the text a DM reads verbatim to
players — is rendered in a 300 px right rail and visually clamped to two lines. The Black Spider's
letter, the campaign's central reveal, displays as
`Auf dem Schreibtisch (zwischen Bestellungen für die Werkstatt) lie…`. Simultaneously the main
column holds only the DM summary prose and leaves roughly 60 % of the viewport empty below it.

**Root cause — two independent decisions compounding.**

1. **Layout.** `templates/adventure/scene-detail.html:33-52` is a two-column flex: `#sceneBody`
   (`flex: 1`) renders *only* `renderedBody`; `#actionRail` (`width: 300px`) receives the entire
   `_action-rail.html` fragment — sections, checks, participants, transitions, links, plus an
   editing form.
2. **Clamping.** `_action-rail.html:104-108` renders each section as
   `<div class="card card--compact">` containing `<p class="u-text-sm" th:text="${section.body}">`.
   The global rule `.card p` at `src/main/resources/static/css/components.css:63-71` applies
   `display: -webkit-box; -webkit-line-clamp: 2; overflow: hidden`. Full text is present in the
   DOM; CSS hides it, and no expand affordance is offered.

**The correct pattern already exists in the codebase.** `templates/session/_story-rail.html:52-66`
renders the same sections for the cockpit using `class="structured-block structured-read-aloud"`
— not `.card` — so the clamp never applies. It also splits `READ_ALOUD` sections out and labels
them with a friendly `Read Aloud` badge. That rail displays the identical content in full,
legibly, and is the model this page should follow.

**Aggravating factor.** `_action-rail.html:72-99` places the *Structured metadata* editing form
(Summary, Source Locator, Tags, Map Region Key, `Save metadata`) **above** the sections at
lines 101 ff. Prep-time admin fields occupy better real estate than the content.

**Target behavior.** The scene page reads as a document first and an editor second:
- Section content — at minimum `READ_ALOUD`, `SECRET`, `TREASURE`, `DM_ADVICE` — moves into the
  main column at full width and full length, with `READ_ALOUD` visually distinguished as in the
  story rail.
- The metadata form collapses behind a disclosure (`<details>`, already used elsewhere in this
  fragment) or moves to the existing scene edit route.
- The rail retains navigational and relational items: participants, transitions, links, quick
  notes, `Set as Current Scene`.

**Blast-radius warning for `.card p`.** 55 templates use `class="card`. The clamp is legitimate
for dashboard and index cards. **Do not delete the rule.** Scope it — e.g. introduce
`.card--clamp` and apply it where summarising is intended, or override with
`-webkit-line-clamp: none` on the structured-section class. The plan must enumerate which card
surfaces keep clamping and state how the change was verified on the quest list, campaign list and
notes list, which currently depend on it.

**Acceptance criteria.**
- Full text of every scene section is visible on the scene page without interaction.
- Read-aloud text is typographically distinct from DM prose.
- Quest list, campaign list and notes list card summaries remain clamped.

---

### F3 — The session cockpit cannot select a scene (P1, workflow dead end)

**Symptom.** With a session `RUNNING`, the STORY panel reads *"No current scene. Set one from an
adventure."* The cockpit offers a map dropdown but no scene selector. To begin the imported
adventure the DM must leave the cockpit, open Adventures, open the adventure, scroll a list of 90
scenes, open one, press `Set as Current Scene` in its rail, and navigate back. This happens
mid-session, at the table.

**Evidence that only the UI is missing.** `session/web/SessionApiController.java` already exposes
`PUT /current-scene` (line 97), `POST /current-scene/step` (line 102) and
`POST /current-scene/follow-transition` (line 111). Setting the scene by API call and reloading
produced a fully populated, well-rendered cockpit (screenshot `08-session-loaded.png`): full
read-aloud text, `← Prev` / `Next →`, and the imported `CHOICE` transition
*"Weiter mit Teil 3: Das Netz der Spinne"* correctly surfaced. `grep` for a picker in
`templates/session/cockpit.html` returns nothing.

**Target behavior.** A scene picker inside the STORY panel, usable without leaving the cockpit,
scoped to the campaign's adventures and grouped by chapter. It should be reachable when no scene
is set (replacing the current dead-end message) and when one is, for jumping. The `Ctrl+K`
palette already indexes scenes and is a viable second entry point, but should not be the only one.

**Acceptance criteria.**
- From a running session with no current scene, a DM can set any scene without navigating away.
- `Prev`/`Next` and `follow-transition` continue to work after selection.

---

### F4 — Loaded content is hidden behind empty shells (P2, information architecture)

Three surfaces under-report a fully populated campaign.

**Campaign dashboard** (`templates/campaign/detail.html`, screenshot `02`). Four cards, three
empty — Party ("No party members yet"), Session Plan ("No session plan yet"), Default Audio Cue
("None") — plus an inline *Edit Campaign* form occupying the space below. Nothing indicates that
90 scenes, 13 quests, 30 NPCs, 5 traps and 3 tables are loaded. Only "Recent Notes" shows imported
data. Target: surface campaign scale (counts and entry points for adventures, quests, world,
threats, tables) and demote the edit form to a disclosure or its own route.

**Adventures index** (screenshot `03`). Renders one row — title, description, ↑ ↓ Edit Delete —
for an adventure containing 90 scenes, with the remainder of the page blank. The control cluster
sits between the title and the description, breaking reading order. Target: show chapter and scene
counts and completion progress per adventure; repair the card's internal order.

**Adventure detail** (`templates/adventure/_chapter-list.html`, screenshot `04`). All 90 scenes
render as one flat list; Teil 3 alone is 33 consecutive rows. There is no collapse, filter, or
in-page search. Each row shows only a title and a single-character status badge —
`_chapter-list.html:26` renders `${s.status.name().charAt(0)}`, producing an unexplained `U`.
The chapter's ↑ ↓ Edit Delete controls render *after* the last scene row, so they appear to belong
to that scene rather than the chapter. The 85 imported transitions — the adventure's branch
structure — are not represented at all.

Target: collapsible chapters (default-collapsed beyond a threshold), a filter/search field, per-row
content affordances (has read-aloud / has combat participants / has trap or hazard / has checks),
a legend or word for the status badge, and chapter controls visually bound to the chapter header.
Progress counters (`0/12 done`) already work and should be kept.

---

### F5 — Raw enum identifiers leak into the interface (P3, polish)

`NOT_STARTED` appears 13 times on the quest list of the imported campaign, and the same pattern
recurs across the app: **33 occurrences of `.name()}` in `th:text` positions** across templates,
including `quest/list.html:31`, `quest/detail.html`, `quest/_objective-list.html`,
`session/_session-plan.html` (twice), `world/npcs-detail.html` (status and disposition),
`world/locations-detail.html`, `sheet/_inventory.html`, `sheet/_features.html`, `notes/list.html`,
and `adventure/_action-rail.html` (four: section kind, transition kind, link role, scene status).

Note that `quest/_form.html` already carries correct human labels in its `<option>` elements
("Not Started", "On Hold"), so display strings exist — they are simply not used for badges.

Target: a single presentation-layer mapping from enum constant to human label, applied uniformly.
A Thymeleaf utility bean or per-enum `displayName()` are both acceptable; the plan should pick one
and convert all 33 sites. `th:selected`/`value=` uses must keep raw constants.

Related smaller issue: on the quest list, long titles push the status badge onto a second line,
producing ragged card headers (screenshot `10`).

---

## 3. What is working and must not regress

The walkthrough confirmed these behave correctly; they are the baseline the repair must preserve.

- **Session cockpit, populated.** Story rail with full untruncated read-aloud, `Read Aloud` and
  `SECRET` badges, muted DM prose, Prev/Next, imported branch transitions, quest progress with
  live objective dropdowns, map toolbar, encounter rails. This is the strongest screen in the app.
- **Quest list.** Three-column card grid, scannable, `Source: S. …` provenance on every card.
  This is the presentational model other index pages should follow.
- **Player view.** Clean curtain, *"Waiting for the DM…"*, green `Connected` indicator, no leakage.
- **Handout delivery.** Imported PNGs serve at full resolution through `/files/{id}`
  (verified: 920×1193 Cragmaw map with legend and scale intact).
- **Visual identity.** Gold-on-dark grimoire treatment, serif smallcaps, drop caps on scene prose.
  Distinctive and appropriate; no redesign is proposed.
- **Internationalisation.** German content with umlauts and typographic quotes renders correctly
  throughout, including inside the cockpit and exported packages.

---

## 4. Cross-cutting requirement: a populated test fixture

Every finding above was invisible to the existing suite because no test exercises a campaign at
published-adventure scale. The plan should introduce one shared fixture and route the regression
tests through it.

**Recommended shape.** Extend `FullPageRenderSmokeTest.seed()` — it already owns the correct
assertion — to build a campaign containing at minimum: two chapters with enough scenes to trigger
list-density behavior; a scene with `READ_ALOUD`, `SECRET` and `TREASURE` sections plus a
participant, a transition and a link; one Faction; one WorldLocation; one WorldNpc linked to both;
one quest with objectives; one trap and one hazard; one rollable table.

An alternative worth evaluating is importing a checked-in fixture package through the real
package-v2 pipeline, which would additionally guard the import path. The converted LMoP package
cannot be committed — it is `licenseClassification: NON_REDISTRIBUTABLE` — so a synthetic
equivalent of comparable shape would be required.

**Assertion to add alongside the existing `</html>` check:** no `LazyInitializationException`
recorded during the page sweep, since a future template could reintroduce F1 in a page whose
truncation happens to fall after the closing tag.

---

## 5. Out of scope

- Campaign format, schema, validation, import/export. Verified correct at real scale.
- The map subsystem. The imported campaign ships handouts rather than battle maps by design
  (deriving grid geometry from printed map art would mean inventing data), so map editing and
  play were not exercised and no judgement is offered.
- Encounter tracker and combat flow — not exercised, because the source conversion created no
  encounters. Worth a separate walkthrough before release.
- Character sheets, treasury, ledger, calendar, music — not exercised in this pass.
- Visual redesign. The aesthetic is sound; the defects are structural.

## 6. Open decisions for the plan author

1. **F1 fix strategy** — fetch joins versus read DTOs. DTOs are more durable and match
   `structuredSceneView`, but touch more code. Pick one and apply it across the world module.
2. **F2 clamp strategy** — opt-in `.card--clamp` versus per-component override. Opt-in is
   cleaner but requires auditing all 55 card-using templates.
3. **F4 scope** — whether dashboard, adventures index and adventure detail are one work item or
   three. They share a cause (no aggregate counts, no density handling) but no code.
4. **Fixture strategy** — synthetic seed versus imported fixture package (§4).
