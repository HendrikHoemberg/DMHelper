# Adventure Module — Design

**Date:** 2026-07-09 · **Status:** Approved design, pre-implementation
**Goal:** Give DMHelper a first-class concept of an *adventure* — ordered chapters and keyed
scenes with read-aloud text, map pins, and linked encounters/statblocks/handouts — so a
published or AI-generated adventure imports as a *runnable structure*, not a pile of parts,
and the DM runs sessions from it without the adventure PDF.

## 1. Motivation

The campaign import pipeline (SPEC §4.1) already carries an adventure's *material* —
statblocks, maps, encounters, notes, handouts — but the app has no concept of the adventure
itself: no chapter/scene hierarchy, no keyed-location navigation ("Area 14"), no read-aloud
text, and imported encounters could not even reference their map. The DM re-assembles the
adventure from parts by hand. This module closes that gap and completes the
"DM needs nothing but this software and dice" promise for running published/AI-parsed
adventures.

## 2. Decisions (locked during brainstorming)

1. **Full in-app editing** — adventures are normal campaign data with htmx CRUD screens;
   JSON import is an additional path, not the only one.
2. **Fixed hierarchy: Adventure → Chapter → Scene** (ordered at every level). Deeper PDF
   nesting flattens into scene order.
3. **Read-aloud text is DM-screen-only** — rendered as distinct boxed blocks for the DM to
   read out loud. No new player-view presentation mode (revisit post-v1 if missed).
4. **Keyed map pins, DM-only** — scenes linked to a map may carry a pin; the battle map
   shows numbered DM-only markers that open the scene. Never in the player-safe projection.
5. **Full run mode, cursor-never-cage** — a campaign-global "current scene" pointer,
   per-scene status the DM toggles, one-click scene actions that *offer* and never
   auto-execute. No transition is ever enforced or locked.
6. **Multiple ordered adventures per campaign.**
7. **The encounter→map import gap is fixed as part of this work** (spec §4.1 promises
   `"map": "throne-room"`; `EncounterExportDto` never implemented it).

## 3. Data model

New feature module `adventure` (`data`/`service`/`web` sub-packages, per SPEC §2.2).

```
Campaign 1──* Adventure (name, description, sortOrder, sourceAttribution?)
                 1──* Chapter (title, sortOrder, intro? — Markdown)
                        1──* Scene (title, sceneKey?, sortOrder,
                                    body — Markdown incl. read-aloud blocks,
                                    status: UNVISITED | VISITED | DONE,
                                    mapRef?, pinX?, pinY?,
                                    encounterRef?,
                                    statblockRefs*, handoutRefs*)

Campaign ──?──> currentScene (nullable FK — the run-mode cursor, one per campaign)
```

- **`sceneKey`** — the book's area number ("14", "B3"); optional free text, shown on pins
  and in lists. Not required to be unique (books reuse numbers across maps/chapters).
- **Read-aloud blocks** are a Markdown convention inside `body`: a fenced block
  (```` ```read-aloud ````) rendered server-side (commonmark-java custom block renderer)
  as the boxed style. No separate entity; text flows between DM prose as in the book.
- **Pin position** (`pinX`/`pinY`, pixels, token coordinate convention) lives on Scene —
  relational game state, not drawing data (SPEC §2.3.2). `mapRef` without a pin is legal.
- **`statblockRefs`/`handoutRefs`** — ordered many-to-many join tables ("who's here",
  "props for this scene"). `encounterRef` is single: a scene ≈ one encounter; more fights
  means more scenes or a DM-side encounter swap.
- **`currentScene`** hangs off Campaign, not Adventure — one "where were we?" answer per
  campaign even with several adventures.
- **Status** is a plain enum with no enforced transitions.
- **DM-only by nature**: no adventure entity ever appears in the player-safe projection or
  WebSocket payloads.
- **Deletion contract**: deleting a linked encounter or map nulls the scene's reference;
  deleting a statblock or handout removes it from the scene's list (the scene survives
  either way). Deleting adventure/chapter/scene cascades downward only — never touches
  maps, encounters, handouts, or statblocks.

## 4. Screens & UI

All Thymeleaf + htmx per SPEC §2.3.6; the only canvas change is pin rendering.

### 4.1 Adventure overview
- Campaign dashboard section lists adventures in order, plus "continue at: <current scene>".
- Adventure page: collapsible ordered outline of chapters and scenes (the book's TOC) with
  status dots and scene keys; chapter headers show progress ("3/7 scenes done").
- Inline htmx editing: add/rename/reorder chapters and scenes, move scene between chapters,
  delete with confirm dialogs (deletes cascade over authored content).

### 4.2 Scene page (the heart)
- Left: rendered body, read-aloud blocks boxed distinctly.
- Right action rail:
  - **Go to map** (battle map, centered on the pin when set)
  - **Activate encounter** (existing endpoints; shows the existing difficulty badge)
  - **Present handout** per linked handout (existing present mechanic)
  - Linked statblock cards, expandable (existing renderer fragment)
  - Scene quicknotes strip (existing component)
  - **Mark visited / done**, **Set as current scene**, prev/next scene navigation
- Edit mode (htmx swap): title, key, body textarea, link pickers for map (+ click-to-place
  pin in a small map-preview modal), encounter, statblocks, handouts — existing search
  endpoints. Scene edits autosave like notes. The map editor itself stays adventure-agnostic.

### 4.3 Battle map & side panel
- **Pins**: numbered DM-only Konva markers on the annotations layer, fetched from
  `GET /api/v1/maps/{id}/pins`; hidden instantly by the DM Mode toggle; click opens the
  scene in the side panel. "Open" and "make current" are separate actions.
- **Side panel** gains a "Current scene" tab (beside session plan/notes): rendered scene
  body + action rail + prev/next — running the fight and reading the room text never leave
  the map screen.
- **Ctrl+K**: scenes are palette results ("14 · The Shrine · <adventure>").

## 5. Run mode semantics

- **Set as current** from scene page, side panel, pin click, or palette. Setting bumps
  UNVISITED → VISITED; never touches DONE. DONE is always an explicit click.
- **Prev/next** walk the adventure's global scene order across chapter boundaries, moving
  only the cursor. They stop at adventure edges (module transitions are deliberate).
- **Session start**: dashboard and the scene tab open on the current scene; if none is set,
  they show the adventure outline.
- **Session plans** are unchanged and complementary: `[[scene:…]]` wiki-links let tonight's
  runsheet sequence scenes without duplicating them. No automatic coupling.
- **Actions are offers**: activating encounters, presenting handouts, and map jumps are the
  same one-click operations available elsewhere; run mode adds zero new side effects
  (consistent with the optional-first rule, SPEC §2.3.9).

## 6. Import/export format

Additive changes to `.dmcampaign.json`; existing files stay valid, no `formatVersion` bump.

```json
{
  "encounters": [ { "key": "throne-ambush", "name": "Throne Ambush",
                    "map": "throne-room", "combatants": [ "…" ] } ],
  "adventures": [
    { "name": "Curse of the Amber Court",
      "sourceAttribution": "…",
      "chapters": [
        { "title": "Chapter 2: The Palace", "intro": "…markdown…",
          "scenes": [
            { "key": "14", "title": "The Throne Room",
              "body": "…markdown…\n```read-aloud\nGilded amber pillars…\n```\n…",
              "map": "throne-room", "pin": { "x": 576, "y": 240 },
              "encounter": "throne-ambush",
              "statblocks": ["srd:amber-golem", "amber-knight"],
              "handouts": ["The Regent's Letter"],
              "status": "UNVISITED" } ] } ] } ]
}
```

- **Reference conventions match the file's existing ones**: maps by `key`, statblocks by
  sourceKey (SRD or in-file custom), handouts by title. Encounters gain an optional `key`
  (fallback: unique name) so scenes can reference them.
- **Encounter→map fix**: `EncounterExportDto` gains the `map` key field, wired through
  export, import, and validation.
- **Read-aloud is plain Markdown** — the fenced convention is documented in the JSON
  Schema's `description` fields (schemas are the authoritative format definition), along
  with pin coordinate conventions (pixels, origin top-left, as for tokens).
- **`status` and campaign-level `currentScene` round-trip** for full mid-adventure export
  fidelity; generators omit them (defaults apply).
- **Dry-run validation** extends per-path: unresolvable scene→map/encounter/handout refs
  and out-of-bounds pins are reported; unresolvable statblock refs degrade to plain text
  with a warning (existing behavior). Scene keys are deliberately not uniqueness-checked.
- `GET /api/v1/schemas/campaign-format` serves the updated schema, so the AI
  generate → validate → fix loop covers adventures with no new tooling.

## 7. API & integration points

- CRUD/reorder fragment endpoints: `/campaigns/{id}/adventures`,
  `/adventures/{id}/chapters`, `/chapters/{id}/scenes` (incl. move-between-chapters),
  mirroring the notes/party controllers.
- Actions: `POST /scenes/{id}/status`, `POST /campaigns/{id}/current-scene` (set/clear).
- JSON for the map island: `GET /api/v1/maps/{id}/pins` →
  `[{ sceneId, sceneKey, x, y, title }]` — PIN-gated like every DM `/api/v1` route.
- **Wiki links**: new `[[scene:…]]` target type in the parser and autocomplete; backlinks
  work through the existing `NoteLink` machinery. Chapters/adventures are not link targets.
- **Quicknotes**: `SCENE` target type (new case in `QuickNoteService`); promote-to-note
  pre-links `[[scene:…]]`.
- **Search & palette**: scenes join campaign full-text search and Ctrl+K (title, key, body).
- **DM Mode**: pins and the scene tab consume the same frontend state as other DM-only
  components.
- Out of scope by design: player view, WebSocket protocol, sheet engine, and dice are
  untouched — the feature is DM-surface-only.

## 8. Testing, migration & data safety

- **Migration**: fully additive (three tables, join tables, nullable `current_scene_id` on
  campaign) — `ddl-auto=update` suffices; no migration bean; backups unchanged.
- **Service unit tests**: ordering + move-between-chapters; the deletion contract (refs
  nulled, cascades downward only, linked entities survive adventure deletion); cursor
  semantics (VISITED bump, DONE never downgraded, prev/next across chapters, stop at
  adventure edges).
- **Import/export round-trip** (flagship test) grows adventures: deep equality over
  chapters, scenes, statuses, currentScene, pins, scene refs, and encounter→map. Dry-run
  tests for each new validation problem.
- **Player-safe projection tests**: no adventure/scene/pin data in any WebSocket payload or
  `/player` response; `GET /api/v1/maps/{id}/pins` rejects PIN-less requests (same
  access-control suite as other DM routes).
- **Renderer test**: `read-aloud` fence → boxed HTML; bodies without it render unchanged.
- **Playwright smoke** extension: create adventure → scene with pin → click pin on battle
  map → scene opens in side panel → activate its encounter.
- **Data safety**: confirm dialogs on adventure/chapter delete; import stays strictly
  additive — a botched AI-generated adventure costs a delete.

## 9. Explicitly out of scope

- Presenting read-aloud text on the player view (new presentation mode) — possible later.
- Player-visible pins.
- Arbitrary-depth nesting.
- Statblock spell-list linking (separate roadmap item, SPEC §8.4).
- Any automation that executes scene actions without a DM click.
