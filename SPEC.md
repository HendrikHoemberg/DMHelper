# DMHelper — Specification

A local-first web application that replaces the "multiple PDFs and 7 spreadsheets" workflow of running
D&D 5.5e (2024 rules) campaigns with one integrated tool: campaign planning, an interactive battle map
with a built-in map editor, combat tracking, a monster library, and a campaign wiki.

**Status:** v1 specification · **Owner:** Hendrik (DM) · **Rules edition:** D&D 5.5e (2024 / SRD 5.2)

---

## 1. Product Overview

### 1.1 Vision

One neat package for the DM at the table. The DM prepares campaigns in the app ahead of time
(maps, encounters, NPCs, notes) and runs sessions from it live: swapping between locations,
moving tokens on a battle map, tracking initiative and HP, and looking up statblocks — without
ever alt-tabbing to a PDF or spreadsheet.

The app is a **framework for all campaigns**, not a single-campaign tool: any number of campaigns
can be created, exported, imported, and archived, and the data model must not assume anything
about a specific setting or adventure.

### 1.2 Usage Model (v1)

- **Single user (the DM), running locally.** The app starts as a local server; the DM opens it in a
  browser. No accounts, no auth, no internet dependency at the table.
- **One shared screen.** Players see the DM's screen (rotated laptop, TV mirroring, etc.).
  Therefore the app has a global **DM Mode toggle**: one switch (with keyboard shortcut) that
  instantly hides all DM-only information — hidden tokens, monster HP, notes, upcoming encounter
  content — so the screen can be safely shown to players at any moment.
- **DM controls everything.** Players never interact with the app directly in v1.

### 1.3 Explicit Non-Goals for v1

Deferred, but the architecture must not preclude them (see §9 Roadmap):

- Separate networked player view / multi-device sync (would add WebSockets)
- Fog of war and per-player vision
- Uploading map images as battle map backgrounds (v1 maps are built in the editor)
- Dice roller and rules quick-reference
- Character sheet management for players
- Hosting for multiple DMs / user accounts

---

## 2. Architecture

### 2.1 Stack

| Layer | Choice | Rationale |
|---|---|---|
| Backend | **Spring Boot 3.x (Java 21)** | DM's home turf; mature ecosystem; clean layering for a long-lived project |
| Persistence | **Spring Data JPA + H2 (file mode)** | Zero-install embedded DB stored in the user data dir; can swap to PostgreSQL later via config |
| API | **REST (JSON), Jackson** | Simple request/response fits the single-user model; Jackson doubles as the campaign import/export engine |
| Frontend | **React + TypeScript (Vite)** | Best ecosystem for the canvas-heavy map editor; TypeScript keeps the large frontend maintainable |
| Map canvas | **Konva.js (react-konva)** | Declarative 2D canvas with layers, drag & drop, snapping — exactly the battle map's needs |
| Frontend state | **Zustand** (app/map state) + **TanStack Query** (server data) | Lightweight; avoids Redux ceremony |
| Packaging | **Single runnable JAR** | Maven build compiles the React app (frontend-maven-plugin) into `static/`; `java -jar dmhelper.jar` starts everything and opens the browser |

### 2.2 High-Level Structure

```
┌──────────────────────── Browser ────────────────────────┐
│  React SPA                                              │
│  ├─ Campaign manager        ├─ Battle map (Konva)       │
│  ├─ Map editor (Konva)      ├─ Initiative tracker       │
│  ├─ Statblock library       └─ Notes / wiki             │
│  └─ Global DM Mode toggle (affects every view)          │
└───────────────▲──────────────────────────────────────────┘
                │ REST/JSON
┌───────────────┴──────────────────────────────────────────┐
│  Spring Boot                                             │
│  ├─ web:        controllers, DTOs                        │
│  ├─ service:    campaign, map, encounter, library, notes │
│  ├─ data:       JPA entities & repositories              │
│  ├─ transfer:   campaign JSON import/export              │
│  └─ seed:       SRD 5.2 content loader (first run)       │
└───────────────┬──────────────────────────────────────────┘
                │ JPA
        H2 file DB  (~/.dmhelper/data)
```

Backend packages are organized **by feature module** (`campaign`, `gamemap`, `encounter`,
`library`, `notes`, `transfer`), each with its own `web/service/data` sub-packages, so modules
stay decoupled and new ones (dice, journal, etc.) slot in cleanly.

### 2.3 Key Architectural Rules

1. **Everything belongs to a Campaign.** Every domain entity (except the shared SRD library)
   carries a campaign reference. Campaign export must be able to serialize a campaign fully from
   this graph.
2. **Editor documents are JSON blobs; live game state is relational.** Map drawing data
   (tiles/shapes/layers) is stored as a versioned JSON document per map — the editor evolves fast
   and shouldn't require schema migrations. Tokens, encounters, HP, and initiative are proper
   entities — they're queried and mutated individually at the table.
3. **DM-only visibility is data, not UI convention.** Anything hideable (tokens, notes, encounter
   entries) has an explicit `dmOnly`/`hidden` flag persisted on the entity, so DM Mode filtering
   is consistent everywhere and a future networked player view can reuse it server-side.
4. **SRD content is read-only seed data**; homebrew content is user data. Both share one statblock
   schema, distinguished by `source` (`SRD` vs `CUSTOM`). Custom content can be campaign-scoped or
   global (reusable across campaigns).

---

## 3. Data Model (v1)

```
Campaign 1──* GameMap 1──1 MapDocument (JSON blob: layers, tiles, shapes, grid config)
    │             └────1──* Token ──?──> StatBlock
    │
    ├──* Encounter 1──* Combatant ──?──> Token / StatBlock
    ├──* Note (typed: NPC | LOCATION | QUEST | SESSION_LOG | GENERIC)
    └──* StatBlock (source=CUSTOM, campaign-scoped)   StatBlock (source=SRD, global)
```

| Entity | Key fields |
|---|---|
| **Campaign** | name, description, createdAt, settings (JSON) |
| **GameMap** | campaign, name, gridWidth/Height, cellSizePx, sortOrder, `document` (JSON, versioned schema) |
| **Token** | map, name, kind (PC/NPC/MONSTER/OBJECT), position (col,row), size (1×1 … 4×4), color/icon, `hidden` (DM-only), statBlockRef?, currentHp?, maxHp?, notes |
| **StatBlock** | source (SRD/CUSTOM), campaign?, name, CR, type, AC, HP, speeds, ability scores, saves, skills, senses, languages, traits/actions/reactions/legendary (structured JSON), searchable columns (name, CR, type) |
| **Encounter** | campaign, map?, name, status (PLANNED/ACTIVE/DONE), round, activeTurnIndex |
| **Combatant** | encounter, name, initiative, currentHp, maxHp, conditions[], `hidden`, tokenRef?, statBlockRef? |
| **Note** | campaign, type, title, body (Markdown), tags[], `dmOnly` (default true), wiki-links to other notes/statblocks/maps |

The `MapDocument` JSON schema carries a `schemaVersion` field from day one; the backend migrates
old documents forward on load.

---

## 4. Feature Modules

### 4.1 Campaign Management & JSON Import/Export

- CRUD for campaigns; a campaign dashboard listing its maps, encounters, notes, and custom content.
- **Export**: one click produces a single self-contained `*.dmcampaign.json` file containing the
  full campaign graph (maps + documents, tokens, encounters, notes, campaign-scoped statblocks).
  SRD references are exported by stable SRD key, not duplicated.
- **Import**: upload a `*.dmcampaign.json`; the app validates it (schema version + referential
  integrity), reports problems clearly, and creates the campaign with fresh IDs (import never
  overwrites existing data). Unresolvable SRD references degrade to plain-text placeholders with a
  warning rather than failing the import.
- The JSON format is **documented and versioned** (`formatVersion`) so campaigns can be authored or
  generated externally (e.g., by scripts or LLMs) and imported.

Sketch of the format:

```json
{
  "formatVersion": 1,
  "campaign": { "name": "Curse of the Amber Court", "description": "..." },
  "statBlocks": [ { "key": "amber-knight", "name": "Amber Knight", "cr": "5", "...": "..." } ],
  "maps": [ { "key": "throne-room", "name": "Throne Room", "grid": { "w": 30, "h": 20, "cellPx": 48 },
              "document": { "schemaVersion": 1, "layers": [ "..." ] },
              "tokens": [ { "name": "Amber Knight", "ref": "amber-knight", "pos": [12, 4], "hidden": true } ] } ],
  "encounters": [ { "name": "Throne Ambush", "map": "throne-room",
                    "combatants": [ { "ref": "amber-knight", "count": 2 } ] } ],
  "notes": [ { "type": "QUEST", "title": "Find the Regent", "body": "…links like [[Throne Room]]…" } ]
}
```

### 4.2 Map Editor (built-in, tile/shape based)

Create battle maps inside the app — no external tools required.

- **Grid-based canvas** (square grid, configurable dimensions and cell size).
- **Terrain painting**: brush-paint cells from a palette of terrain types (floor, wall, water,
  difficult terrain, lava, pit, …), each with a color/pattern. Palette is extensible with custom
  entries (name + color + walkable flag).
- **Shape tools**: rectangles, circles, lines, and freehand polygons for rooms, furniture, area
  effects; snap-to-grid with an unsnapped option.
- **Layers**: at minimum *terrain*, *objects*, *annotations (DM-only)*; layers can be hidden/locked.
- **Editing UX**: undo/redo, copy/paste of selections, pan (space-drag) and zoom (wheel), keyboard
  shortcuts.
- Maps save automatically (debounced) into the map's `MapDocument`.
- The document model reserves an optional **image layer** slot so image-based backgrounds can be
  added post-v1 without a format break.

### 4.3 Battle Map (live play)

The same canvas in "play" mode:

- **Tokens**: create from a statblock (drag from library), from a note (NPC), or ad hoc. Drag to
  move with grid snapping; supports 1×1 to 4×4 sizes; color ring by kind (PC/ally/enemy/object);
  name label; optional HP bar (DM Mode only); duplicate ("add 4 goblins"); mark dead/remove.
- **Hidden tokens**: flagged tokens render only in DM Mode (used for ambushes, secret NPCs).
- **Location swapping**: a map switcher lists all campaign maps; switching is instant and preserves
  each map's token state — walk out of the tavern mid-fight, come back later, everything is where
  it was. Maps can be grouped/ordered for session flow.
- **Annotations**: DM-only pings/markers/text on the annotation layer.
- Measurement helper: click-drag shows distance in cells/feet.

### 4.4 Initiative & Combat Tracker

Docked panel beside the battle map, linked to tokens:

- Build an encounter from tokens on the current map (auto-pulls names/HP from statblocks) and/or
  plan encounters ahead of time and activate them at the table.
- Roll or type initiative per combatant (auto-roll for monsters using their DEX; PCs typed in);
  sort, tie-break, drag to reorder.
- Turn management: next/previous, round counter, active combatant highlighted **both** in the
  tracker and on the map.
- HP tracking: apply damage/healing with quick math (`-12`, `+5`); death handling for monsters
  (auto-mark dead on 0) vs. PCs (death-save reminder).
- Conditions: toggle 5.5e conditions per combatant; condition icons show on tokens.
- In DM Mode off (player-safe), the tracker shows only names, order, and conditions — no monster
  HP or hidden combatants.

### 4.5 Monster / NPC Statblock Library

- **Bundled SRD 5.2 content** (CC-BY-4.0): all SRD monsters, seeded into the read-only global
  library on first run, with attribution shown in the app's About screen as the license requires.
  (Spells and magic items from the SRD are bundled as reference entries in the same library;
  full spell/item tooling is post-v1.)
- Search and filter by name, CR, type, source; fast enough to use mid-combat.
- Full 5.5e-format statblock rendering (2024 layout: traits, actions, bonus actions, reactions,
  legendary actions).
- **Homebrew**: create custom statblocks with the same editor/renderer; clone-and-edit any SRD
  entry as a starting point. Custom entries are campaign-scoped by default, promotable to global.
- One-click paths: statblock → token on current map; statblock → combatant in encounter.

### 4.6 Session Notes & Campaign Wiki

The "kill the 7 spreadsheets" module:

- Typed, Markdown-based notes: **NPCs, Locations, Quests, Session Logs, Generic**.
- **Wiki-style linking** with `[[Note Title]]` autocompletion; links can also target maps and
  statblocks (e.g., an NPC note links to its statblock and home location map). Backlinks are shown
  on every note ("referenced by …").
- Tags + full-text search across the campaign.
- Session log template (date, attendance, summary, loot, XP) to encourage consistent records.
- Quick-access side panel available from every screen (including the battle map) so notes are
  reachable mid-session without leaving the map.
- Notes are `dmOnly` by default and therefore invisible when DM Mode is off.

### 4.7 DM Mode Toggle (global)

- One global switch in the app header + keyboard shortcut (default `Ctrl+Shift+D`), with an
  unmistakable visual state (e.g., colored border while player-safe mode is on).
- When toggled to **player-safe**: hidden tokens vanish, monster HP/bars disappear, DM annotations
  hide, the notes panel closes and locks, statblock/encounter-prep views blank out, and the map
  switcher hides unvisited maps' names. The battle map and initiative order (names + conditions)
  remain visible.
- Implemented as a single frontend state that every component consumes — but driven by the
  persisted `dmOnly`/`hidden` flags on the data (§2.3.3), so filtering rules live in one place.

---

## 5. REST API Conventions

- Base path `/api/v1`; JSON everywhere; entity IDs are UUIDs.
- Resource-oriented: `/api/v1/campaigns/{id}/maps`, `/maps/{id}/tokens`, `/encounters/{id}/combatants`,
  `/library/statblocks?search=&cr=&type=`, `/campaigns/{id}/notes?type=&tag=`.
- Map documents saved via `PUT /maps/{id}/document` (whole-document replace with optimistic
  version check); token moves via small `PATCH` calls so live play is snappy.
- Import/export: `GET /campaigns/{id}/export` (streams the JSON file),
  `POST /campaigns/import` (multipart upload).
- Errors follow RFC 7807 problem+json with actionable messages (especially import validation).

---

## 6. Quality & Non-Functional Requirements

- **Robustness at the table is the top priority**: autosave everything (no explicit save button
  outside the editor's debounced save); the app must survive a browser refresh mid-combat with zero
  data loss (encounter state is persisted server-side on every change).
- **Performance targets**: map editor smooth at 60fps on a 50×50 grid with ~200 shapes; token drag
  latency imperceptible; statblock search results < 100 ms.
- **Backups**: on every app start, copy the H2 database file to a rotating backup folder
  (`~/.dmhelper/backups`, keep last 10).
- **Testing**: service-layer unit tests; import/export round-trip tests (export → import → deep
  equality) as the flagship integration test; frontend component tests for the initiative tracker
  and DM Mode filtering; a Playwright smoke test for the core session loop (create map → place
  token → start encounter → advance turns).
- **Licensing**: SRD 5.2 under CC-BY-4.0 with required attribution; no non-SRD WotC content is
  ever bundled.

---

## 7. Milestones

| # | Milestone | Contents | Definition of done |
|---|---|---|---|
| M1 | **Walking skeleton** | Spring Boot + React + H2 wired into one JAR; campaign CRUD; campaign JSON export/import (empty campaigns) | `java -jar` → create, export, import a campaign in the browser |
| M2 | **Statblock library** | Statblock schema + renderer; SRD seed; search/filter; homebrew editor | Find "Goblin" in <100 ms; create a custom monster |
| M3 | **Map editor** | Grid canvas, terrain painting, shapes, layers, undo/redo, autosave | Build a usable tavern map from scratch |
| M4 | **Battle map** | Play mode, tokens (create/move/hide/HP), map switching with state, measurement | Run a mock fight by hand on a map |
| M5 | **Combat tracker** | Encounters, initiative, turns, HP math, conditions, map linkage | Run a full combat without touching paper |
| M6 | **Notes & wiki** | Typed notes, Markdown, wiki-links + backlinks, search, side panel | Replace the campaign spreadsheet |
| M7 | **Table polish** | DM Mode toggle everywhere, backups, error handling, keyboard shortcuts, full export/import of everything | Run a real session start-to-finish |

Each milestone ends in a usable state — the app is session-worthy from M4 onward.

## 8. Post-v1 Roadmap (design for, don't build)

1. **Networked player view** — second device/TV shows the player-safe map live (WebSocket/STOMP;
   the `dmOnly` data flags already define what it may see).
2. **Fog of war** — manual reveal first, vision-based later.
3. **Image map layers** — upload battle map images under the editor's shape layers (slot reserved
   in the map document schema).
4. **Dice roller & rules reference** — with conditions/actions quick-cards.
5. **Spell & item tooling** — spell lists per statblock, loot tables, item cards.
6. **Campaign templates** — export subsets (a dungeon + its monsters) as reusable modules.

---

## 9. Open Questions

- Hex grid support in the map editor (square-only for v1)?
- Should session logs auto-capture combat results (encounter outcomes, damage dealt)?
- Multi-campaign shared homebrew: is "promote to global library" enough, or is a proper
  homebrew compendium module needed?
