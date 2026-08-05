# DM Safety, Encounter Seeding and Fixture Fidelity — Design Specification

**Date:** 2026-07-22
**Status:** Proposed, not yet approved
**Trigger:** A second DM walkthrough of the imported German *Die Verlorene Mine von Phandelver*
campaign, run against the repairs made in
`docs/superpowers/plans/2026-07-21-imported-campaign-presentation-repair.md`. The walkthrough
confirmed those repairs but surfaced a distinct class of defect: **content that is correctly
imported and correctly stored, but either invisible to the DM or visible to the players.**
**Scope:** Presentation, safety and test-fidelity. No changes to the package format, schema,
import pipeline or persistence model.

---

## 1. Purpose

The import pipeline is correct and richer than previously believed. The 9.9 MB package resolves
**65 of its 67 scene participants to a statblock** — 45 against the SRD catalog, 20 against its own
custom blocks — and every one of those links is present in the database. It carries 10 hand-written
German statblocks, 90 scenes, 13 quests, 30 NPCs, 11 locations, 9 factions, 5 traps, 5 hazards and
3 rollable tables.

Two commits have already landed against this campaign:

- `fab79f9` — renders participant statblocks in both rails; adds a DM Mode toggle to the cockpit;
  tags the primary DM-facing surfaces `dm-only`.
- `d1b1f41` — repairs defects found in the first walkthrough (dead dashboard links, missing
  disclosure affordance, page-header layout).

This specification records what remains, in four independently implementable work items.

### 1.1 The central problem: DM Mode is half-finished, and therefore lying

The application ships a **"PLAYER-SAFE"** badge and a DM Mode toggle whose tooltip reads
*"DM Mode toggles player-safe projection"*. Turning it off sets `body.dm-mode-off`, and
`src/main/resources/static/css/base.css:183-185` then hides `.dm-only` and `[data-dm-only]`.

Before `fab79f9` only two templates tagged anything, so a section labelled **Secret** stayed on
screen underneath that badge. `fab79f9` fixed the scene page, the cockpit and NPC detail. It did
**not** fix quest detail, location secrets or faction notes — verified untagged, see F1.

A half-covered safety mechanism is worse than none, because the badge now actively asserts safety.
This is the highest-priority item in this document.

### 1.2 Why the existing tests do not prevent recurrence

`fab79f9` added `DmModeCoverageTest`, which asserts tagging on the surfaces it names. It is a
**whitelist**: a new DM-facing field, or an untested existing one, ships exposed and the suite stays
green. F1 requires inverting this into a rule that fails on anything unlisted.

Separately, the participant-statblock defect survived because
`src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java` created its
participant with `statBlockId = null`, while the real package populates that field 65 times out of
67. The fixture was written from a prose checklist rather than from the shape of real data. F3
addresses this generally.

### 1.3 Reproduction environment

- Application on `localhost:8081`; start with `./mvnw spring-boot:run`.
- Real campaign package: `/home/hendrik/Documents/DnDCampaigns/lmop-de.dmcampaign` (9.9 MB,
  ZIP container, `manifest.json` + 7 PNG assets). Source PDF, for reference only:
  `/home/hendrik/Downloads/dnd-pdfs/dnd-pdfs/671866257-D-D-Die-verlorenen-Minen-von-Phandelver.pdf`
  (64 pages, OCR text layer).
- The package **cannot be committed** — it is a verbatim German translation of a copyrighted WotC
  adventure. F3 exists specifically to work around this.
- Tests run on in-memory H2 (`src/test/resources/application.properties`) and never touch the
  user's database at `~/.dmhelper/data`.
- Full suite: `./mvnw test`. Currently **2107 passing, 0 failures**.
- Browser verification: Playwright is not a project dependency; the walkthroughs used the
  Playwright-cached Chromium at
  `~/.cache/ms-playwright/chromium-1181/chrome-linux/chrome` driven by an ad-hoc Node script.

---

## 2. Findings

Ordered by priority. Each is independently implementable.

### F1 — DM Mode covers only some DM-facing content (P0, safety)

**Symptom.** With DM Mode off — the state a DM turns the laptop to the table in — the following
remain fully visible while the navbar displays **PLAYER-SAFE**:

| Surface | Field | Location |
|---|---|---|
| Quest detail | Prerequisites | `templates/quest/detail.html:39-42` |
| Quest detail | Rewards | `templates/quest/detail.html:43-46` |
| Quest detail | Outcome Notes | `templates/quest/detail.html:47-50` |
| Location detail | **Secrets** | `templates/world/locations-detail.html:50-53` |
| Faction detail | Reputation Notes | `templates/world/factions-detail.html:44-47` |
| Faction detail | Goals | `templates/world/factions-detail.html:36-39` |

Each is a bare `<div th:if="${…}" class="u-mb-md">` with an `<h4>` label and a `<p>` body, carrying
no `dm-only` class.

**Root cause.** Tagging is manual and opt-in, and `DmModeCoverageTest` (added in `fab79f9`) only
asserts the surfaces it explicitly names. Nothing forces a new or overlooked DM-facing field to be
tagged.

**Already tagged — do not re-tag.** These are correct as of `fab79f9`:
`adventure/_scene-sections.html:20` (non-READ_ALOUD sections), `adventure/_action-rail.html:200`
(checks), `:274` (participants), `:341` (transitions), `session/_story-rail.html:13` (scene
summary), `:15` (scene body), `:63` (DM sections), `:84` (participants), `:104` (checks), `:118`
(transitions), `session/_session-plan.html:40` (quest progress panel),
`world/npcs-detail.html:56` (motivation), `:60` (secret), `campaigns/detail.html:84,97`.

**Verified false positives — do NOT tag.** A naive grep flags these, but they sit inside an
already-tagged ancestor: `adventure/_action-rail.html:207-208` (check success/failure, inside the
`dm-only` check card opened at line 200) and `session/_story-rail.html:111-112` (same, inside the
`.scene-checks dm-only` block opened at line 104).
Tagging them again is harmless but noise; the point is that **a nesting-aware rule is required, not
a line-level one.**

**Policy to encode.** Player-visible: scene title, `READ_ALOUD` section bodies, the map/handout
surface, the party bar. Everything else that describes plot, mechanics, rewards, secrets, monster
statistics or placement is DM-facing. `READ_ALOUD` must **never** be tagged — it is the one kind a
DM is meant to show or read aloud, and tagging it defeats the feature. `DmModeCoverageTest` already
pins this in `readAloudStaysVisibleBecauseItIsMeantForThePlayers`.

**Target behavior.**
1. The six sites above carry `dm-only`.
2. A test enumerates DM-sensitive **entity fields** and fails the build when any template renders
   one outside a `dm-only` subtree. Candidate field list, from the domain model: `secret`/`secrets`,
   `motivation`, `dmNote`, `rewards`, `prerequisites`, `outcomeNotes`, `reputationNotes`,
   `success`, `failure`, `partial`, `dc`, `placementHint`, `statBlock`. Edit forms
   (`*-form.html`) are reachable only by deliberate DM navigation and may be excluded, but the
   exclusion must be explicit and commented, not accidental.
3. The check must be **nesting-aware** — walking ancestors, not scanning N preceding lines — or it
   will produce the false positives listed above and get disabled.

**Implementation direction.** Two viable shapes; the plan should pick one:
- *(a)* A template-source contract test in the style of the existing
  `SceneStructuredTemplateContractTest` / `ThreatTemplateContractTest`: parse each `.html` under
  `templates/`, and for every DM-sensitive expression, walk up the enclosing element stack for a
  `dm-only`. Fast, no Spring context, but needs a small HTML tokenizer.
- *(b)* A rendered-page test extending `DmModeCoverageTest`: render the fixture's pages and assert
  the same rule against real output. Catches what actually renders, but only covers pages the
  fixture reaches.
  Note the codebase has **no HTML parsing dependency** (no jsoup); `DmModeCoverageTest` uses a
  hand-rolled backwards scan. Adding jsoup as a test-scope dependency is acceptable and would make
  (a) substantially cleaner.

**Acceptance criteria.**
- With DM Mode off, no quest reward, quest prerequisite, outcome note, location secret or faction
  reputation note is present in the rendered output of its detail page.
- A newly added DM-sensitive field rendered without `dm-only` fails the suite.
- `READ_ALOUD` bodies remain visible with DM Mode off.
- The existing 16 `DmModeCoverageTest` assertions still pass.

---

### F2 — No handout can be presented (P1, blocks the core table workflow)

**Symptom.** The cockpit's *"Present handout…"* dropdown renders with **zero options** for the
imported campaign. `templates/session/cockpit.html:50` gates options on
`th:unless="${handout.dmOnly}"`, and all 7 imported handouts carry `dmOnly = true` — including
*"Regionalkarte: Schwertküste um Phandalin (S. 5)"*, which is player-facing in the printed book.

**Evidence.**
```
TITLE=Regionalkarte: Schwertküste um Phandalin (S. 5)  DM_ONLY=TRUE
TITLE=Karte: Cragmaw-Versteck (S. 9)                   DM_ONLY=TRUE
… all 7 rows DM_ONLY=TRUE
```
and the rendered dropdown contains only `<option value="">Present handout…`.

**Root cause — two independent parts.**
1. **Data.** The converter marked every handout `dmOnly: true`. The manifest confirms
   `handouts=7, dmOnlyTrue=7`.
2. **No recovery path in the UI.** `templates/handout/_card.html` displays a `DM only` badge but
   offers **no control to change it**. The only way to flip the flag is
   `PUT /api/v1/handouts/{id}/dm-only?dmOnly=false`
   (`handout/web/HandoutApiController.java:36-39`). A DM cannot fix their own import.

**Note on scope.** `HandoutService.setDmOnly` (`handout/service/HandoutService.java:193-201`)
already does the right thing on transition — when set to DM-only it detaches the handout from any
live session and clears `presented`. No service work is needed; this is a UI affordance plus a
converter default.

**Target behavior.**
- The handout card exposes a toggle for DM-only, wired to the existing endpoint, updating in place
  (the card fragment is already returned by `HandoutController.setPresented`, so the same
  `handout/_card :: card` return pattern applies).
- The cockpit picker shows every non-DM-only handout, and states clearly when all handouts are
  DM-only rather than rendering an empty dropdown — an empty picker is indistinguishable from a
  broken one.
- Optional, decide in the plan: a converter-side default so player-facing maps do not import as
  DM-only. This touches conversion tooling that lives outside this repository; if out of reach,
  the UI toggle alone satisfies the acceptance criteria.

**Acceptance criteria.**
- From the handouts page a DM can make a handout presentable without using the API directly.
- With at least one non-DM-only handout, the cockpit picker lists it and presenting it works.
- With zero non-DM-only handouts, the picker communicates that state rather than appearing empty.
- Toggling a presented handout to DM-only still detaches it from the session (existing behavior,
  must not regress).

---

### F3 — Test fixtures drift from the shape of real packages (P1, cross-cutting)

**Symptom.** Every defect in this document and the previous one shares a cause: the fixtures do not
resemble real imported data, so the suite cannot see the defect. The participant-statblock bug is
the clearest case — `PopulatedCampaignFixture` set `statBlockId = null` while real packages populate
it 65 times out of 67.

**Current coverage gap.** The synthetic fixture (post-`fab79f9`) still does not exercise:
- `TRAP` and `HAZARD` section kinds, and therefore never renders `threat/_mechanics-card.html`
  inside a scene — despite the real package having both.
- `SCALING` and `DEVELOPMENT` section kinds.
- Handouts at all (`handouts=0`), which is why F2 was invisible.
- Encounters and maps (`encounters=0`, `maps=0`) — though the real package also has none.

**The licensing constraint.** `lmop-de.dmcampaign` cannot be committed. `metadata` carries no
`licenseClassification` field, but the content is a verbatim translation of a copyrighted adventure
regardless.

**Target behavior — a committed shape profile.** Extract from the real package a JSON profile
containing **counts and field-population statistics only, no content strings**, commit it, and
assert the synthetic fixture covers every field the real package exercises. The profile derived
from the current package:

```
adventures=1  chapters=4  scenes=90
sections=169   kinds=[DEVELOPMENT, HAZARD, READ_ALOUD, SCALING, SECRET, TRAP, TREASURE]
participants=67  withStatblockRef=65
transitions=85   kinds=[CHOICE, EXIT]  withDmNote=2
checks=33  links=44
worldNpcs=30       withFaction=11  withLocation=14  withSecret=14
worldLocations=11  withParent=0    withSecrets=6
quests=13          withRewards=12  withPrereq=1
handouts=7         dmOnlyTrue=7
customStatBlocks=10  traps=5  hazards=5  tables=3
factions=9  relationships=10  magicItems=7  notes=4  annotations=9
encounters=0  maps=0  party=0  audioCues=0
```

The assertion is **coverage, not equality**: the fixture need not have 90 scenes, but for every
field the real package populates at least once, the fixture must populate it at least once. Fields
the real package leaves empty (encounters, maps, party) are not required.

**Note.** `worldLocations withParent=0` — the real package has no nested locations, yet
`world/locations-list.html:35` dereferences `loc.parentLocation.name`, which was an F1-class
truncation fixed in `2e67299`. The synthetic fixture *does* cover it (`childLocationId`). This is
evidence the profile must be treated as a **floor, not a ceiling**: the fixture may legitimately
exceed it, and existing coverage must not be removed to match it.

**Implementation direction.** A small extractor (script or test-scope utility) that reads a package
and emits the profile; the committed profile JSON; and a test asserting fixture ⊇ profile. The
extractor must be runnable against any future package so the profile can be refreshed. Decide in
the plan whether the extractor lives in `src/test/java` or as a standalone script.

**Acceptance criteria.**
- A committed profile file contains no content strings from the source adventure.
- A test fails when the fixture stops covering a field the profile marks populated.
- The fixture is extended to cover the currently-missing kinds: `TRAP`, `HAZARD`, `SCALING`,
  `DEVELOPMENT` sections, a section with a resolved `threatId` so `threat/_mechanics-card.html`
  renders in a scene, and at least two handouts — one DM-only, one not.
- Re-running the extractor against `lmop-de.dmcampaign` reproduces the committed profile.

---

### F4 — Encounters cannot be started from a scene (P2, completes the combat story)

**Symptom.** The imported campaign contains **0 encounters** (`encounters=0` in the manifest), so
the cockpit's ACTIVE ENCOUNTER and PLANNED ENCOUNTERS panels are permanently empty. 22 scenes carry
hostile participants. When combat starts, the DM must build the encounter by hand even though the
app knows every combatant, its count and its statblock.

**Why this is now small.** `EncounterService.addFromLibrary`
(`encounter/service/EncounterService.java:775`) already does the entire job:

```java
public record AddFromLibraryRequest(UUID statBlockId, int quantity, String groupName,
                                    UUID waveId, Integer startX, Integer startY,
                                    String placementRegionKey) {}
```

It resolves the statblock, parses HP via `parseHpAsInt`, creates `quantity` combatants, assigns a
shared `groupId`, marks a group leader and attaches them to the main wave. A `SceneParticipant`
carries exactly `statBlock` + `quantity` + `displayName`. Encounter creation itself is
`EncounterService.create(campaignId, new CreateRequest(name, mapId))`.

So the feature is: create an encounter named after the scene, then loop the scene's participants
that have a statblock and call `addFromLibrary` once per participant, passing `displayName` as
`groupName`.

**Target behavior.**
- A "Start encounter from this scene" action on the scene page and/or the cockpit story rail,
  visible only when the scene has at least one participant with a resolved statblock.
- Participants **without** a statblock (2 of 67 in the real package) must not silently vanish —
  either skip them with a visible count, or add them as statless combatants. Decide in the plan.
- The created encounter links back to the scene where the model allows it (`Scene.encounter`
  exists and `_action-rail.html:43` already renders a "Linked Encounter" block).
- Re-running the action on a scene that already has an encounter must not silently duplicate it.

**Acceptance criteria.**
- From a scene with hostile participants, one action produces an encounter whose combatants match
  the participants' statblocks, counts and HP.
- The action is absent or disabled for a scene with no statblock-linked participants.
- Running it twice does not produce two encounters or double the combatants.
- Existing encounter tests (`EncounterServiceTest`, `EncounterWaveServiceTest`,
  `ThreatEncounterIntegrationTest`, `EncounterPartyHpSyncTest`) still pass.

---

## 3. What is working and must not regress

Confirmed by walkthrough against the real campaign. These are the baseline.

- **Participant statblocks.** Both rails show `Bugbear Warrior · AC 14 · HP 33 (6d8+6)` inline,
  linking to `/library/statblocks/{id}`. `SceneParticipant.statBlock` is `LAZY` and both
  `AdventureService.findSceneDetailView` and `SessionWorkspaceService` now initialise it —
  **removing either initialisation reintroduces the F1 truncation from the previous spec.**
- **World pages render completely.** `/world/npcs`, `/world/npcs/{id}`, `/world/locations`,
  `/world/locations/{id}`, `/world/factions/{id}` all close with `</html>` for faction- and
  location-linked data. Guarded by `FullPageRenderSmokeTest` including a
  `LazyInitializationException` log assertion.
- **Scene page reads as a document.** Read-aloud renders full-length in the main column in
  `--font-book` with a gold rule; `.card p`'s two-line clamp is untouched and still applies to the
  notes, threat, rollable-table, audio and library card summaries.
- **Cockpit scene picker.** 90 scenes in 4 chapter-grouped `<optgroup>`s; selecting one switches the
  scene and reloads the rail.
- **Adventure detail density.** 4 collapsible chapters with gold chevrons, a filter that narrows 90
  scenes to 1, per-row affordance icons, a "STATUS KEY" legend.
- **Enum labels.** `#enums.label(...)` / `#enums.labelOf(...)` exposed via `EnumLabelDialect`.
  Note `labelOf` is required for DTO fields that flatten an enum to a `String`
  (`WaveDto.status`, `SheetResourceDto.resetRule`, `LedgerEntryDto.kind`,
  `SessionPlanBeat.type`) — `label()` silently no-ops on those. Pinned by
  `EnumLabelUtilTest.labelDoesNotConvertEnumNamesThatArriveAsStrings`.
- **Player view.** Retained deliberately. `/player`, the `live/` package and the `/ws/table`
  WebSocket stay as they are. A separate proposal to delete them was declined.
- **Internationalisation.** German content with umlauts and typographic quotes throughout.

---

## 4. Out of scope

- Campaign format, schema, validation, import/export. Verified correct at real scale — the package
  resolves 65/67 participant statblock references, both CATALOG and PACKAGE scope.
- Deleting the player view. Explicitly declined; it may become useful later.
- Battle maps with grid geometry. The package ships 7 flat PNGs (`maps=0`); deriving playable grid
  data from printed map art means inventing data.
- The cockpit's space allocation. Measured at story rail 21% / map surface 49% / encounter rail 25%
  of the viewport, with the last two empty for a map-less campaign. A real observation, but a design
  decision rather than a defect — raise separately.
- Character sheets, treasury, ledger, calendar, music. The campaign seeds no party, so these were
  never exercised.

---

## 5. Open decisions for the plan author

1. **F1 enforcement shape** — template-source contract test versus rendered-page test (§F1
   *Implementation direction*). Related: whether to add **jsoup as a test-scope dependency**. The
   repository currently has no HTML parser and `DmModeCoverageTest` hand-rolls a backwards scan,
   which is already at the edge of what is maintainable.
2. **F1 form-template policy** — whether `*-form.html` edit forms are excluded from the rule. They
   render the same DM-sensitive fields but are reachable only by deliberate DM navigation.
3. **F2 converter scope** — whether to fix the `dmOnly: true` default at conversion time, or ship
   the UI toggle only and let DMs correct existing imports.
4. **F3 extractor location** — test-scope Java utility versus standalone script, and whether the
   profile assertion runs on every build or only when the profile file changes.
5. **F4 unlinked participants** — skip with a visible count, or add as statless combatants.
6. **F4 sequencing** — F4 depends on F3 only if the plan wants encounter-seeding covered by the
   populated fixture. Recommended: land F3's fixture extensions first so F4's tests are written
   against data that mirrors reality.
