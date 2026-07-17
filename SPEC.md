# DMHelper — Specification

A local-first web application that replaces the "multiple PDFs and 7 spreadsheets" workflow of running
D&D 5.5e (2024 rules) campaigns with one integrated tool: campaign planning, rules-aware character sheets
for the party, an interactive battle map with a built-in map editor, combat tracking, a monster library
plus a full SRD 5.2 rules compendium (rules, conditions, items, character options), loot & treasury
tracking, an in-game calendar, handouts, a campaign wiki, and an optional dice roller. At the table the
DM needs nothing else — no PDFs, no spreadsheets.

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
  browser. No accounts, no internet dependency at the table; the only access control is the
  per-session DM PIN that keeps LAN devices out of the DM interface (§2.3.7).
- **Primary display: the DM's screen**, which may be shown to players at any time. The app has a
  global **DM Mode toggle**: one switch (with keyboard shortcut) that instantly hides all DM-only
  information — hidden tokens, monster HP, notes, upcoming encounter content — so the screen can be
  safely shown to players at any moment.
- **Optional player view.** Any device on the same network (TV, tablet, a player's phone or laptop)
  can open a read-only, always-player-safe URL served by the same app and see the live table state.
  This is strictly additive: every feature must work on the DM screen alone, so a table with no
  spare device loses nothing.
- **DM controls everything.** The player view is display-only; players never interact with the app
  directly in v1.

### 1.3 Explicit Non-Goals for v1

Deferred, but the architecture must not preclude them (see §8 Roadmap):

- Player *interaction* (players moving their own tokens, editing their own character sheets,
  rolling dice from their own devices, per-player identity/permissions) — the v1 player view is
  display-only and the DM operates everything, including the party's character sheets (§4.12)
- Fog of war and per-player vision
- Uploading map images as battle-map tokens (the background-image layer is in v1; image-based map *tokens* are not)
- Hosting for multiple DMs / user accounts

> Two former non-goals were deliberately promoted into v1 (see §9): **full rules-aware character
> sheets** (§4.12, DM-operated) and the **dice roller & rules quick-reference** (§4.11, §4.16 —
> always optional, never mandatory).

---

## 2. Architecture

### 2.1 Stack

| Layer | Choice | Rationale |
|---|---|---|
| Backend | **Spring Boot 4.1.0 (Java 25)** | DM's home turf; mature ecosystem; clean layering for a long-lived project |
| Persistence | **Spring Data JPA + H2 (file mode)** | Zero-install embedded DB stored in the user data dir; can swap to PostgreSQL later via config |
| Schema migrations | **Hibernate `ddl-auto=update`** with backup safety net | Additive schema evolution from JPA entities; destructive changes handled by one-off `@PostConstruct` migration beans; rotating DB backups + campaign export/import as escape hatch |
| UI (pages & panels) | **Thymeleaf + htmx** (vendored, single dependency-free JS file) | Server-rendered hypermedia UI for all CRUD screens — campaign, roster, library, notes, encounters — with no JS build step; partial page updates via HTML fragments |
| Interactive islands | **Vanilla JS (native ES modules) + Konva.js** (vendored, self-contained UMD file) | The map editor and battle map are self-contained canvas "islands" mounted into server-rendered pages; browsers load ES modules natively — no bundler, no transpiler, no Node |
| Client-side sprinkles | **Alpine.js** (vendored, zero dependencies) | Lightweight reactivity for toolbars, dialogs, and the initiative tracker where htmx round-trips would be clumsy |
| Type safety | **JSDoc annotations** on the JS islands | IDE-checked (IntelliJ/VS Code) without introducing a compiler toolchain; the discipline of TypeScript without its build step |
| Markdown | **commonmark-java** (Maven) | Notes render server-side — one less client library |
| API | **REST (JSON), Jackson** for the islands; HTML fragments for htmx | Jackson doubles as the campaign import/export engine |
| Live sync | **Plain WebSocket + JSON** (browser-native, no client library) | Pushes player-safe table state to optional player-view devices; the DM screen works entirely without it |
| Packaging | **Single runnable JAR, Maven-only build** | All frontend assets are static files in `src/main/resources/static/`; `java -jar dmhelper.jar` starts everything and opens the browser |

> **⚠ Spring Boot 4 — explicit version pin, especially for AI-assisted development.**
> This project uses **Spring Boot 4.1.0** (Spring Framework 7 / Jakarta EE 11 generation).
> Spring Boot 4 is **newer than the training data of most AI coding assistants**, whose baseline
> knowledge is Spring Boot 2/3. Every dependency, starter, configuration property, annotation,
> and code idiom must therefore be chosen **with Spring Boot 4 in mind** and verified against the
> Spring Boot 4.1 dependency BOM (`spring-boot-dependencies`) and current official documentation —
> never assumed from Boot-3-era memory or examples. Concretely:
>
> - Library versions come from the 4.1 BOM; **never downgrade Spring Boot or a managed dependency
>   to make an old example compile** — port the example forward instead.
> - Third-party libraries outside the BOM must be checked for a Spring Boot 4 / Spring Framework 7
>   compatible release before adoption.
> - Prefer current APIs over ones deprecated or removed since Boot 3; treat generated code that
>   references Boot-3-only artifacts, starters, or configuration properties as a bug to fix, not
>   a hint to downgrade.
> - **Jackson 3 (not Jackson 2).** Spring Boot 4 ships with Jackson 3 (`com.fasterxml.jackson.core:jackson-*`
>   under the `jackson-modules` BOM), which uses the `tools.jackson` Maven group instead of
>   `com.fasterxml.jackson.core`. AI assistants frequently default to Jackson 2 imports and
>   class names; any reference to `com.fasterxml.jackson` or Jackson 2 API patterns is a bug
>   — the correct dependency group for Boot 4 is `tools.jackson` with the Jackson 3 API.
> - When Boot 4 documentation and an AI suggestion conflict, **the documentation wins**.

### 2.2 High-Level Structure

```
┌────────────── DM's browser ──────────────┐   ┌── Player devices (optional) ──┐
│  Server-rendered pages (Thymeleaf+htmx)  │   │  TV / tablet / phone browser  │
│  ├─ Campaign mgr     ├─ Init. tracker    │   │  /player page: read-only,     │
│  ├─ Library / notes  ├─ Party, handouts  │   │  always player-safe; Konva    │
│  ├─ JS islands (vanilla ES modules):     │   │  map renderer fed over        │
│  │    map editor & battle map (Konva)    │   │  WebSocket                    │
│  └─ Global DM Mode toggle                │   └───────────▲───────────────────┘
└──────────────▲───────────────────────────┘               │ WebSocket
               │ HTML fragments (htmx)                     │ (JSON, player-safe)
               │ + JSON /api/v1 (map islands)              │
┌──────────────┴────────────────────────────────────────────┴──────────────────┐
│  Spring Boot                                                                 │
│  ├─ web:        Thymeleaf views + htmx fragments + JSON API controllers      │
│  ├─ live:       table-state broadcaster (filters dmOnly before publishing)   │
│  ├─ service:    campaign, party, sheets, map, encounter, library, notes,     │
│  │              handouts, quicknotes, ledger, calendar, dice                 │
│  ├─ data:       JPA entities & repositories                                  │
│  ├─ transfer:   campaign JSON import/export                                  │
│  └─ seed:       SRD 5.2 content loader — monsters, spells & compendium       │
│                 (conditions, rules, items, character options; first run)     │
└──────────────┬───────────────────────────────────────────────────────────────┘
               │ JPA
       H2 file DB  (~/.dmhelper/data)
```

Backend packages are organized **by feature module** (`campaign`, `party`, `sheet`, `gamemap`,
`encounter`, `library`, `notes`, `quicknote`, `handout`, `ledger`, `calendar`, `dice`, `transfer`),
each with its own `web/service/data` sub-packages, so modules stay decoupled and new ones
(journal, etc.) slot in cleanly.

### 2.3 Key Architectural Rules

1. **Everything belongs to a Campaign.** Every domain entity (except the shared SRD library)
   carries a campaign reference. Campaign export must be able to serialize a campaign fully from
   this graph.
2. **Editor documents are JSON blobs; live game state is relational.** Map drawing data
   (tiles/shapes/layers) is stored as a versioned JSON document per map — the editor evolves fast
   and shouldn't require schema migrations. Tokens, encounters, HP, and initiative are proper
   entities — they're queried and mutated individually at the table.
3. **DM-only visibility is data, not UI convention.** Anything hideable (tokens, notes, encounter
   entries) has an explicit `dmOnly`/`hidden` flag persisted on the entity. The same server-side
   filter drives both the DM Mode toggle and the player-view payloads — DM-only data is stripped
   **on the server** before it is ever published to a player-view connection, so a curious player
   opening browser dev-tools on their phone finds nothing.
4. **The player view is a pure projection.** It holds no state of its own and accepts no input; it
   renders whatever "table state" the server broadcasts (live map, tokens, initiative). If no
   player device is connected, nothing about the app changes — single-screen play is the baseline,
   not a degraded mode.
5. **SRD content is read-only seed data**; homebrew content is user data. Both share one statblock
   schema, distinguished by `source` (`SRD` vs `CUSTOM`). Custom content can be campaign-scoped or
   global (reusable across campaigns). All compendium types (spells, conditions, rules, equipment,
   magic items, classes, species, backgrounds, feats) also support campaign-scoped custom variants
   exported in the v2 manifest arrays (`customSpells` … `customFeats`). Spell entries are also
   read-only SRD seed data (no CRUD).
6. **Hypermedia by default, islands where it earns it.** Screens are server-rendered and updated
   via htmx fragments; only the map editor and battle map are client-side JS applications
   (Konva canvas islands talking JSON to `/api/v1`). New features must justify becoming an island
   rather than defaulting to one — this keeps the hand-written JS surface small and auditable.
7. **The LAN sees only the player surface.** The player view requires the server to listen on the
   network interface — which would otherwise expose the full DM interface (notes, hidden tokens,
   upcoming encounters) to every device on the same Wi-Fi; a curious player wouldn't need
   dev-tools, just the DM's URL. Therefore all DM routes — pages and every `/api/v1` endpoint
   except the player-safe ones — are gated behind a **per-session PIN**: generated fresh at each
   app start, printed to the terminal and shown in the DM header, entered once per browser
   (cookie-backed). `/player`, `/ws/table`, and files referenced by player-safe payloads remain
   open without a PIN — they only ever serve the player-safe projection (rule 3). This is the
   same invariant as rule 3 arriving through a different door, and it is tested with the same
   rigor (§6).
8. **Rules data provenance — nothing is ever guessed.** Every piece of D&D rules content in the
   app (statblocks, spells, conditions, rules text, items, classes, species, backgrounds, feats,
   progression tables, XP thresholds, …) comes exclusively from a verifiable 5.5e (2024 / SRD 5.2)
   source: the open5e API (`srd-2024` document) or, where open5e cannot serve a content type or
   field, verbatim transcription from the official SRD 5.2 document (CC-BY-4.0), committed as
   checked-in seed files whose header records the source and date. If neither source covers a
   value, the field stays empty and the feature degrades gracefully — an AI coding assistant (or
   human contributor) must **never** fill in rules data from memory to complete a task. This rule
   binds every implementation session, not just the initial seeding, and reviewers treat violations
   as bugs.
9. **The dice roller is never mandatory (optional-first).** Everywhere the app expects a roll —
   initiative, attacks, saves, checks, recharge, HP on level-up — the UI offers both a roll action
   and a plain numeric input, and no feature may depend on the digital roller being used. The DM
   and players rolling physical dice is the first-class path; the roller is a convenience layered
   on top (§4.16).

### 2.4 Dependency & Supply-Chain Policy

A deliberate response to the npm-ecosystem supply-chain attacks; also what makes the app work
with zero internet at the table:

- **No Node/npm toolchain, ever.** The build is Maven-only; there is no `package.json`,
  no lockfile, no install scripts, no transitive JS dependency tree.
- **Frontend libraries are vendored, not fetched.** htmx, Konva, and Alpine are committed to the
  repo under `static/vendor/` as single files at pinned versions. A `VENDOR.md` (Markdown table)
  records each file's name, version, upstream URL, and SHA-256 hash; upgrading a library is a
  deliberate, reviewed commit — never an automatic resolution. All three are dependency-free by
  design, so the entire third-party JS surface is three auditable files.
- **No runtime CDN.** CDNs are both an availability risk (no internet at the table) and a
  supply-chain risk in their own right (cf. the polyfill.io compromise). The app serves every
  byte itself.
- **Java dependencies** come from Maven Central, version-pinned via the **Spring Boot 4.1 BOM**
  with Maven checksum verification enabled, and are kept deliberately few. Anything outside the
  BOM must have a verified Spring Boot 4-compatible release (see the Spring Boot 4 note in §2.1).
  The same "upgrades are reviewed events" rule applies.

---

## 3. Data Model (v1)

```
Campaign 1──* GameMap 1──1 MapDocument (JSON blob: layers, tiles, shapes, grid config)
    │             └────1──* Token ──?──> StatBlock | PartyMember
    │
    ├──* PartyMember (combat projection) ──?──1 CharacterSheet (rules-aware, derived-with-override)
    ├──* Encounter 1──* Combatant ──?──> Token / StatBlock / PartyMember
    ├──* Handout (image file + metadata)
    ├──* Note (typed: NPC | LOCATION | QUEST | SESSION_LOG | SESSION_PLAN | GENERIC)
    ├──* QuickNote ──> Encounter | GameMap | StatBlock | PartyMember | Handout | Note | Campaign
    ├──* ItemAssignment (holder: PartyMember | party stash) ──?──> MagicItem | EquipmentItem
    ├──* LedgerEntry (append-only gold/item transactions)
    ├──* TimelineEvent (in-game date + title, ──?──> Note)
    └──* StatBlock (source=CUSTOM, campaign-scoped)   StatBlock (source=SRD, global)

Global read-only compendium (seeded, referenced by sourceKey — see §2.3.8 provenance):
    StatBlock(SRD) · Spell · Condition · RuleSection · EquipmentItem · MagicItem ·
    CharacterClass · Species · Background · Feat
```

| Entity | Key fields |
|---|---|
| **Campaign** | name, description, createdAt, settings (JSON: incl. calendar config, current in-game date, XP-vs-milestone leveling mode) |
| **PartyMember** | campaign, characterName, playerName, classAndLevel, ac, maxHp, initiativeBonus, speed, passivePerception/Insight/Investigation, notes, `active` (absent player / retired PC). When a CharacterSheet exists, these combat fields are derived from it (override-able); sheet-less members stay hand-edited |
| **CharacterSheet** | partyMember (1–1), abilityScores, classLevels[] (classRef + level, multiclass supported), speciesRef, backgroundRef, featRefs[], proficiencies (skills/saves/tools/languages), spellsKnown/prepared (Spell refs), hitDicePool, resources[] (name, max, current, reset-on-rest rule), xp, overrides (JSON: any derived value can be manually overridden) |
| **GameMap** | campaign, name, gridWidth/Height, cellSizePx, sortOrder, `movementMode` (GRID/FREEFORM), `showGrid` (boolean), `document` (JSON, versioned schema) |
| **Token** | map, name, kind (PC/NPC/MONSTER/OBJECT), positionX/Y (pixels — Konva-native; grid snapping is frontend-only), size (1×1 … 4×4 cells × cellSizePx), color/icon, `hidden` (DM-only), statBlockRef? / partyMemberRef?, currentHp?, maxHp?, notes |
| **StatBlock** | source (SRD/CUSTOM), campaign?, name, CR, xp, type, AC, HP, speeds, ability scores, saves, skills, senses, languages, traits/actions/reactions/legendary (structured JSON), searchable columns (name, CR, type) |
| **Spell** | sourceKey (unique), name, level, school, castingTime, range, components, duration, description, higherLevel, ritual, concentration — read-only reference data, no CRUD |
| **Encounter** | campaign, map?, name, status (PLANNED/ACTIVE/DONE), round, activeTurnIndex |
| **Combatant** | encounter, name, initiative, currentHp, maxHp, conditions[] (each with optional remaining-round duration), `concentrating`, legendaryActionsRemaining?, legendaryResistancesRemaining?, groupId? (identical monsters sharing one initiative entry), `hidden`, tokenRef?, statBlockRef? / partyMemberRef? |
| **CombatLogEntry** | encounter, round, sequence, type (DAMAGE / HEAL / CONDITION / TURN / INITIATIVE / …), payload (JSON) — append-only; drives combat undo and session-log capture (§4.5) |
| **Handout** | campaign, title, image file reference, tags[], `dmOnly` until presented |
| **Note** | campaign, type, title, body (Markdown), tags[], `dmOnly` (default true), wiki-links to other notes/statblocks/maps |
| **QuickNote** | campaign, targetType + targetId (encounter / map / statblock / party member / handout / note / campaign), body (plain text), createdAt — always DM-only; promotable to a full Note (§4.13) |
| **ItemAssignment** | campaign, holder (PartyMember or party stash), magicItemRef? / equipmentItemRef? / customText, quantity, `attuned` (max 3 attuned per PC, warned not enforced) |
| **LedgerEntry** | campaign, timestamp, inGameDate?, kind (GOLD / ITEM), direction (GAIN / SPEND), amount + currency?, itemAssignmentRef?, holder, note — append-only transaction history |
| **TimelineEvent** | campaign, inGameDate, title, body?, noteRef? |
| **Condition** | sourceKey, name, description — read-only compendium |
| **RuleSection** | sourceKey, name, parentKey/group, body (Markdown), sortOrder — read-only compendium |
| **EquipmentItem** | sourceKey, name, category (WEAPON / ARMOR / GEAR / TOOL), cost, weight, properties (JSON), description — read-only compendium |
| **MagicItem** | sourceKey, name, rarity, type, `requiresAttunement`, description — read-only compendium |
| **CharacterClass** | sourceKey, name, hitDie, savingThrows, proficiencies, spellcasting (ability + slot progression JSON), featuresByLevel (JSON), the SRD subclass (JSON) — read-only compendium |
| **Species** | sourceKey, name, size, speed, traits (JSON) — read-only compendium |
| **Background** | sourceKey, name, abilityScores, featRef, skills, tools, equipment, description — read-only compendium |
| **Feat** | sourceKey, name, category, prerequisite, benefit — read-only compendium |

Handout images live on the file system (`~/.dmhelper/files`), referenced by the entity — the
database stays small and backups copy both.

The `MapDocument` JSON schema carries a `schemaVersion` field from day one; the backend migrates
old documents forward on load. The relational schema evolves via Hibernate `ddl-auto=update` — additive changes
(JPA entities, new columns, new tables) are applied automatically on startup.
Destructive changes (column renames, type changes, drops) are handled with one-off
`@PostConstruct` data-migration beans shipped alongside the entity change and deleted
in the following release. Rotating DB backups on every app start (§6) and campaign
export/import provide a robust escape hatch if `ddl-auto` ever misbehaves — the
DM's data is never at risk.

---

## 4. Feature Modules

### 4.1 Campaign Management & JSON Import/Export

- CRUD for campaigns; a campaign dashboard listing its maps, encounters, notes, and custom content.
- **Export**: one click produces a single self-contained `*.dmcampaign.json` file containing the
  full campaign graph (party roster + character sheets, maps + documents, tokens, encounters,
  notes, quicknotes, item assignments, ledger, calendar + timeline, handouts with embedded images,
  campaign-scoped statblocks). SRD and compendium references are exported by stable SRD key,
  not duplicated.
- **Import**: upload a `*.dmcampaign.json`; the app validates it (schema version + referential
  integrity), reports problems clearly, and creates the campaign with fresh IDs (import never
  overwrites existing data). Unresolvable SRD references degrade to plain-text placeholders with a
  warning rather than failing the import.
- The JSON format is **documented and versioned** (`formatVersion`) so campaigns can be authored or
  generated externally (e.g., by scripts or LLMs) and imported.

**Generative tooling** — the import pipeline is the interface for AI-generated campaigns, so it
must support a tight generate → validate → fix loop:

- **Machine-readable JSON Schemas** for the campaign format and the map document ship with the
  app (served at `/api/v1/schemas/…` and checked into the repo), so an external agent can
  self-validate output before ever contacting the app. The schemas — not prose — are the
  authoritative format definition; coordinate conventions (origin at top-left, `[col, row]`
  order, sizes in cells) are encoded in their descriptions.
- **Dry-run import**: `POST /campaigns/import?dryRun=true` runs the complete validation —
  schema, referential integrity, and spatial checks (tokens inside grid bounds, primitives within
  map dimensions) — and returns the full problem report without creating anything.
- **SRD key catalog**: `GET /api/v1/library/srd-keys` lists every valid SRD reference key
  (auto-generated from seeded statblock data at runtime; also exportable as a checked-in file),
  so generators reference real content instead of hallucinating keys. Unknown keys still degrade
  gracefully on import (see above), but the catalog makes them avoidable.
- Import remains strictly additive (never overwrites), so a failed or mediocre generation
  costs nothing — delete the campaign and re-import.

Sketch of the format:

```json
{
  "formatVersion": 1,
  "campaign": { "name": "Curse of the Amber Court", "description": "..." },
  "party": [ { "characterName": "Thia", "playerName": "Anna", "classAndLevel": "Rogue 5",
               "ac": 16, "maxHp": 38, "initiativeBonus": 4, "passivePerception": 17 } ],
  "statBlocks": [ { "key": "amber-knight", "name": "Amber Knight", "cr": "5", "...": "..." } ],
  "handouts": [ { "title": "The Regent's Letter", "image": "data:image/png;base64,..." } ],
  "maps": [ { "key": "throne-room", "name": "Throne Room",
               "grid": { "w": 30, "h": 20, "cellPx": 48 },
               "movementMode": "GRID", "showGrid": true,
               "document": { "schemaVersion": 1, "layers": [ "..." ] },
               "tokens": [ { "name": "Amber Knight", "ref": "amber-knight", "x": 576, "y": 192, "hidden": true } ] } ],
  "encounters": [ { "name": "Throne Ambush", "map": "throne-room",
                    "combatants": [ { "ref": "amber-knight", "count": 2 } ] } ],
  "notes": [ { "type": "QUEST", "title": "Find the Regent", "body": "…links like [[Throne Room]]…" } ]
}
```

### 4.2 Party Roster & Encounter Difficulty

The DM-screen answer to "what's the rogue's passive Perception again?" — a lightweight roster of
the player characters. The roster is the **combat projection** of the party: from M9 onward its
stats are derived from the full character sheets (§4.12) where a sheet exists, but the roster
itself — and everything built on it in M4–M6 — works identically with or without sheets:

- Per campaign: character name, player name, class & level, AC, max HP, initiative bonus, speed,
  passive Perception/Insight/Investigation, free-text notes (e.g., "darkvision, fey ancestry").
  An `active` flag handles absent players and retired characters.
- **Party summary bar**: a compact, always-available strip (AC + passives per character) on the
  battle map and notes screens — the top row of a physical DM screen, digitized. DM Mode only.
- **One-click integration**: "add party to map" creates PC tokens for all active members;
  starting an encounter pre-fills PC combatants with names, HP, and initiative bonuses — no
  re-typing per fight.
- **Encounter difficulty calculator**: while building an encounter, the app shows the 2024-DMG
  XP budget rating (Low / Moderate / High) live, computed from the active roster's size and levels
  against the selected monsters' XP values. Prep-time guidance only — no hard limits.

### 4.3 Map Editor (built-in, tile/shape based)

Create battle maps inside the app — no external tools required. The editor is a Konva.js canvas
"island" with an Alpine.js toolbar, persisted as a versioned JSON document (optimistic locking)
and autosaved on every edit.

- **Grid-based canvas** (square grid, configurable dimensions and cell size).
- **Terrain painting**: brush-paint cells from a palette of terrain types (floor, wall, water,
  difficult terrain, lava, pit, …), each with a color/pattern. The brush supports adjustable size
  (1×1, 2×2, or 3×3 cells) and Bresenham stroke interpolation so fast mouse movements leave no
  gaps. Palette is extensible with custom entries (name + color + walkable flag). Right-click or
  the dedicated Erase palette entry erases cells back to floor without switching terrain.
- **Terrain area tools**: Terrain Rect drag-fills a rectangular area; Bucket (flood-fill) fills
  the entire 4-directionally connected region of the same terrain type — both with right-click
  erase.
- **Shape tools**: rectangles, circles, lines, and freehand polygons for rooms, furniture, area
  effects; snap-to-grid with an unsnapped option. Shapes can be **click-selected, moved, and
  resized** (with a Konva Transformer; rotation disabled as shapes have no rotation field), and
  their **labels edited** (double-click to open an inline text input) or changed via a shape
  properties panel in the sidebar.
- **Primitive authoring tools**: Room (drag a rectangle — walls the border, floors the interior),
  Door (click a cell), and Region (drag a rectangle filled with the active terrain) let the DM
  author semantic primitives directly in the editor, not just via JSON generation.
- **Bulk selection**: the Select tool (marquee drag) selects cells and shapes; the selection can
  be **dragged to a new position**, cut (Ctrl+X), copied (Ctrl+C), pasted (Ctrl+V — repeated
  pastes offset by one cell each, no stacking), or deleted (Delete). The selection marker follows
  the drag preview in real time.
- **Layers**: *terrain*, *objects*, *annotations (DM-only)*; layers can be hidden/locked via a
  right sidebar. The sidebar also shows a **Shape properties panel** (fill color, stroke color,
  label text, delete button) whenever a shape is selected.
- **Background image layer**: import a PNG or JPEG as a background reference image, positioned and
  scaled behind the grid. Select and transform it with the same Transformer handles used for
  shapes. The image is stored as a base64 data URL inside the map document — no separate file
  storage or upload endpoints.
- **Editing UX**: undo/redo, copy/cut/paste of selections, pan (space-drag, middle-mouse, or
  two-finger touch), zoom (scroll wheel or two-finger pinch), keyboard shortcuts for every tool
  (B/T/G/M/D/N/R/C/L/P/V), and a keyboard-shortcuts help panel. Undo/Redo buttons are disabled
  when their stacks are empty; adding custom terrain is undoable.
- **Save & data safety**: maps save automatically (debounced 2 seconds) into the `MapDocument` via
  a versioned `PUT` with optimistic locking. On a 409 conflict, a JSON backup can be downloaded
  before reloading. Unsaved changes are flushed with `keepalive` on navigation away, and the
  browser warns before leaving with unsaved work. The status bar shows color-coded save states
  (unsaved/saving/saved green; error/conflict red).
- **Cursor & coordinate feedback**: the brush and bucket show a translucent hover preview of the
  active terrain color snapped to cells. The cursor changes per tool (crosshair for drawing tools,
  default for select) and shows grab/grabbing during pan. The status bar shows live cell
  coordinates and zoom percentage.
- **PNG export**: one click exports the current map view as a 2x-resolution PNG (selection
  overlays hidden).
- The map document carries a `schemaVersion` field; the backend migrates old documents forward on
  load.
- **Semantic map primitives** in the document schema: `room` (rectangular area with walls),
  `corridor` (reserved, no visual), `door`, and `region` (terrain fill over an area) — expanded to
  cells on render, so generated maps (§4.1) can be expressed with few degrees of freedom instead
  of hundreds of coordinates.

### 4.4 Battle Map (live play)

The same canvas in "play" mode:

- **Tokens**: create from a statblock (drag from library), from a note (NPC), or ad hoc. Drag to
  move — behavior depends on the map's movement mode (see below); supports 1×1 to 4×4 sizes; color
  ring by kind (PC/ally/enemy/object); name label; optional HP bar (DM Mode only); duplicate
  ("add 4 goblins"); mark dead/remove.
- **Hidden tokens**: flagged tokens render only in DM Mode (used for ambushes, secret NPCs).
- **Bloodied indicator**: a token at or below half HP shows a player-visible "bloodied" state
  (e.g., a red-tinged ring) — answering the table's constant "does it look hurt?" without leaking
  numbers. The flag is computed server-side and included in the player-safe projection; exact HP
  remains DM-only.
- **Location swapping**: a map switcher lists all campaign maps; switching is instant and preserves
  each map's token state — walk out of the tavern mid-fight, come back later, everything is where
  it was. Maps can be grouped/ordered for session flow.
- **Annotations**: DM-only pings/markers/text on the annotation layer.
- **AoE spell templates**: drag-and-drop cone, sphere/circle, cube, and line overlays with 5.5e
  sizes (15-ft cone, 20-ft radius, …), semi-transparent. Templates are player-visible (they exist
  to be argued over), removable with one click, and cleared automatically when the encounter ends.
- **Measurement helper**: click-drag shows distance. In grid mode: cells and feet. In freeform
  mode: feet only (pixel distance ÷ cellSizePx × 5 ft per cell).

**Movement mode** — each map has its own mode, persisted on `GameMap.movementMode`:

| Mode | Token drag | AoE templates | Measurement | Token placement |
|---|---|---|---|---|
| **Grid** (default) | Snaps to nearest cell on release | Snap to grid | Cells + feet | Snaps to nearest cell |
| **Freeform** | Free pixel movement, no snap | Freely placeable, no snap | Feet only | Exact pixel position of click |

- **Mode switching**: toggling modes on a map with existing tokens keeps them where they are —
  positions are stored as pixel coordinates in both modes. Toggling from freeform → grid does
  NOT retroactively snap existing tokens (they'd jump); only newly moved tokens snap. The mode
  indicator is shown in the battle map toolbar.
- **Independent grid visibility**: `GameMap.showGrid` controls whether grid lines render on the
  battle map — purely visual, independent of movement mode. A freeform map can show the grid
  as a spatial reference, and a grid-mode map can hide it for a cleaner look. Default: `true`.
- The map editor (§4.3) is always grid-based regardless of movement mode.

### 4.5 Initiative & Combat Tracker

Docked panel beside the battle map, linked to tokens:

- Build an encounter from tokens on the current map (auto-pulls names/HP from statblocks and the
  party roster) and/or plan encounters ahead of time and activate them at the table.
- Roll or type initiative per combatant (auto-roll for monsters using their DEX; PCs typed in,
  with their roster initiative bonus shown); sort, tie-break, drag to reorder.
- **Monster groups**: identical monsters ("the 4 goblins") can share a single initiative entry —
  one roll, one turn slot — while HP is tracked per creature within the group. A combatant can be
  split out of its group when it matters (held action, banishment, the goblin that ran).
- Turn management: next/previous, round counter, active combatant highlighted **both** in the
  tracker and on the map.
- HP tracking: apply damage/healing with quick math (`-12`, `+5`); death handling for monsters
  (auto-mark dead on 0) vs. PCs (death-save reminder).
- Conditions: toggle 5.5e conditions per combatant; condition icons show on tokens.
- **Effect durations**: conditions and tracked effects take an optional duration in rounds
  ("Bless — 10 rounds", "stunned until the end of its next turn"); the tracker ticks them down
  at the appropriate point in the round and prompts on expiry instead of silently forgetting.
  Statblock **recharge abilities** ("Recharge 5–6") prompt a recharge roll at the start of the
  creature's turn.
- **Concentration**: flag a combatant as concentrating (with the spell's name); when they take
  damage the tracker prompts the save with the correct DC (10 or half damage). Losing it clears
  the flag.
- **Legendary & lair actions**: combatants whose statblock has legendary actions get a per-round
  counter (decrement on use, reset at the top of the round), and **legendary resistances** get a
  per-encounter counter alongside; encounters can include a **lair action** entry pinned at
  initiative 20.
- **Combat action log with undo**: every tracker mutation — damage, healing, conditions, turn
  advance, initiative edits — is appended to a per-encounter log, and **undo** steps back through
  it, so a misclicked "next turn" or 12 damage applied to the wrong goblin is one keystroke to
  fix. The editor gets undo/redo; the place where mistakes actually happen mid-session deserves
  it more (same table-robustness priority as autosave, §6). The log doubles as raw material for
  session records: when an encounter ends, its outcome (rounds, damage dealt, casualties) can be
  appended to the session log.
- In DM Mode off (player-safe), the tracker shows only names, order, and conditions — no monster
  HP or hidden combatants.

### 4.6 Monster / NPC Statblock Library

- **Bundled SRD 5.2 content** (CC-BY-4.0): all 331 SRD 5.2 monsters + 339 spells, fetched from
  the open5e community API (`srd-2024` document) and seeded into the library on first run, with
  attribution shown in the app's About screen as the license requires. Spells are read-only
  reference entries in the same library. The library UI also hosts the reference compendium
  sections — rules, conditions, items, character options (§4.11).
- Each statblock includes an `xp` field (from the SRD) to enable the encounter difficulty
  calculator (§4.2) without a later schema migration.
- Search and filter by name, CR, type, source; fast enough to use mid-combat.
- Full 5.5e-format statblock rendering (2024 layout: traits, actions, bonus actions, reactions,
  legendary actions).
- **Homebrew**: create custom statblocks with the same editor/renderer; clone-and-edit any SRD
  entry as a starting point. Custom entries are campaign-scoped by default, promotable to global.
- One-click paths: statblock → token on current map; statblock → combatant in encounter.

### 4.7 Session Notes & Campaign Wiki

The "kill the 7 spreadsheets" module:

- Typed, Markdown-based notes: **NPCs, Locations, Quests, Session Logs, Session Plans, Generic**.
- **Wiki-style linking** with `[[Note Title]]` autocompletion; links can also target maps and
  statblocks (e.g., an NPC note links to its statblock and home location map). Backlinks are shown
  on every note ("referenced by …").
- Tags + full-text search across the campaign.
- **Global quick-search**: a `Ctrl+K` command palette, available from every screen, searches
  notes, statblocks, maps, handouts, and encounters in one box and jumps straight to the result —
  the mid-session "take me to X" path, so navigation never means walking menus while the table
  waits.
- Session log template (date, attendance, summary, loot, XP) to encourage consistent records.
- **Session plan ("tonight's runsheet")**: a `SESSION_PLAN` note gathers everything prepared for
  the next session — planned scenes, the encounters to activate, handouts queued, NPCs likely to
  appear — as an ordered list of wiki-links with free text between. The current session plan is
  the default content of the quick-access side panel, making it the session's front page: the
  bridge between prep and play starts from one place instead of a search box.
- Quick-access side panel available from every screen (including the battle map) so notes are
  reachable mid-session without leaving the map.
- Notes are `dmOnly` by default and therefore invisible when DM Mode is off.
- Wiki notes are complemented by **entity-attached quicknotes** (§4.13) for mid-session jotting;
  a quicknote that grows up can be promoted into a full wiki note.

### 4.8 Handouts

The digital version of sliding a prop across the table:

- Upload images per campaign (letters, portraits, item cards, shop inventories, region maps) with
  a title and tags; browse them in a gallery. Handouts can be wiki-linked from notes
  (`[[handout:The Regent's Letter]]`) so they're findable in the moment they're relevant.
- **Present**: one click shows a handout full-screen — on the player view if devices are
  connected, and/or as a full-screen overlay on the DM screen for device-less tables (safe to
  rotate the laptop; the overlay hides everything else).
- Handouts are `dmOnly` until first presented; afterwards they can be marked as "given to
  players" so you remember what the party actually possesses.
- Supported formats: PNG/JPEG/WebP; stored on the file system (§3), included in backups and in
  campaign export (base64-embedded so the `.dmcampaign.json` stays a single self-contained file).

### 4.9 Player View (optional second display)

A read-only live view of the table, for any spare device on the local network:

- The DM screen shows a **"Player view" link/QR code** (e.g., `http://<dm-ip>:8080/player`);
  opening it on a TV, tablet, or player's phone joins the table display. Any number of devices can
  connect; none are required. Player routes need no PIN — only DM routes are gated (§2.3.7).
- **Always player-safe.** The player view has no DM Mode toggle — the server only ever sends it
  player-safe data (hidden tokens, monster HP, DM annotations, and notes are stripped server-side).
- **What it shows:** the live battle map (tokens, terrain, AoE templates, active-turn highlight,
  condition icons, bloodied states), the initiative order (names + conditions), or a presented
  handout (full-screen). Nothing else — no navigation, no menus.
- **The DM decides what's "on the table."** The player view does not blindly mirror the DM's
  screen. The DM explicitly presents a map or handout to the table ("Send to table" action); they
  can then freely browse other maps, notes, or prep on their own screen without the players seeing
  any of it. A **curtain mode** (splash screen with campaign name/artwork) lets the DM blank the
  table display during scene transitions or secret prep.
- **Sync behavior:** token moves, HP-driven token states, turn changes, and map presentation
  propagate over WebSocket within ~100 ms. A player view that loses its connection reconnects
  automatically and re-fetches the current table state — a flaky tablet must never require DM
  attention mid-fight.
- **View controls on the device itself:** pinch/scroll zoom and pan only (auto-fit by default),
  so a phone user can zoom into their corner of the fight.

### 4.10 DM Mode Toggle (global)

- One global switch in the app header + keyboard shortcut (default `Ctrl+Shift+D`), with an
  unmistakable visual state (e.g., colored border while player-safe mode is on).
- When toggled to **player-safe**: hidden tokens vanish, monster HP/bars disappear, DM annotations
  hide, the notes panel and party summary bar close and lock, statblock/encounter-prep views blank
  out, and the map switcher hides unvisited maps' names. The battle map (including AoE templates
  and bloodied states), initiative order (names + conditions), and any presented handout remain
  visible.
- Implemented as a single frontend state that every component consumes — but driven by the same
  persisted `dmOnly`/`hidden` flags and server-side filtering rules that feed the player view
  (§2.3.3), so what "player-safe" means is defined exactly once.
- DM Mode remains essential even with player views connected: it covers the "player walks behind
  the screen" case and tables with no second device at all.

### 4.11 Reference Compendium (rules, conditions, items, character options)

The rest of the "never open a PDF" promise — everything the SRD 5.2 defines beyond monsters and
spells, seeded on first run and browsable in the same Library UI as the statblocks:

- **Content types**: conditions, rules sections (combat, resting, travel, ability checks, …),
  equipment (weapons, armor, gear, tools), magic items, and character options (classes, species,
  backgrounds, feats). All read-only, global (not campaign-scoped), each with a stable `sourceKey`.
- **Sourcing & provenance (hard rule, §2.3.8)**: content comes from the open5e API (`srd-2024`
  document), like monsters and spells. open5e's srd-2024 coverage varies by content type, so
  coverage is **verified per endpoint at implementation time**; any type or field open5e cannot
  serve is transcribed **verbatim from the official SRD 5.2 document** (CC-BY-4.0) into
  checked-in JSON seed files with recorded provenance. Nothing is ever reconstructed from an AI
  assistant's memory; gaps degrade (empty field, skipped entry) rather than being guessed.
  The milestone never blocks on the API.
- **Search & lookup UX**: every compendium type is searchable with the same speed target as
  statblocks (§6); the `Ctrl+K` command palette (M12) covers compendium entries, making
  "how does Grappled work?" a two-keystroke answer mid-combat.
- **Renderers per type**: condition and rule pages render as clean reference text; items show
  cost/weight/properties or rarity/attunement; classes render their level table, features, and
  the SRD subclass; species/backgrounds/feats render their traits and benefits.
- **Integration points**: the combat tracker shows real condition text (tooltip/panel) when a
  condition is toggled — the compendium ships before the tracker (M3 < M6), so this works from
  day one. Magic and equipment items are referenced by the loot system (§4.14) and character
  sheets (§4.12); classes/species/backgrounds/feats feed the sheet engine.
- Compendium entries are covered by the same About-screen CC-BY-4.0 attribution as the SRD
  statblocks, and the SRD key catalog (§4.1) extends to all compendium types.

### 4.12 Character Sheets (rules-aware, DM-operated)

Full character sheets in-app — a deliberate reversal of the original non-goal (§9). The party can
be configured entirely in the app; nobody at the table needs a paper sheet or external tool
(though players are free to keep them — the app never requires player interaction in v1).

- **Structure**: a `CharacterSheet` hangs 1-to-1 off a `PartyMember` (§3). The PartyMember stays
  the combat projection — everything the battle map, encounter prefill, and difficulty calculator
  consume — so the map/combat modules (M4–M6) never couple to the sheet engine. Members without a
  sheet (guest PCs, quick one-shots) remain fully hand-editable exactly as in M2.
- **Rules-aware derivation**: ability scores are entered once; the engine derives modifiers,
  proficiency bonus, save and skill bonuses, passive scores, spell slots (including the
  multiclass spellcaster table), and max HP (rolled or average per level) from seeded compendium
  data — classes, species, backgrounds, feats (§4.11). **Multiclassing is supported**; progression
  data comes exclusively from compendium sources per the provenance rule (§2.3.8).
- **Derived-with-override everywhere**: every computed value has a manual override slot (e.g., AC
  from unusual items, homebrew features). The override is the escape valve that keeps the engine
  from having to model every rules interaction; overridden values are visibly marked.
- **Guided level-up**: pick the class to level, choose rolled-or-average HP (typed roll accepted —
  §2.3.9), see the new features from class data, take the ASI/feat choice at the right levels;
  slots and derived values update.
- **Rest & resources**: short rest (spend hit dice, typed or rolled), long rest (HP restored,
  slots reset, half hit dice back), and generic per-feature **resource counters** (name, max,
  current, reset-on-short/long-rest rule) for everything from Rage uses to magic item charges.
- **XP & leveling mode**: award XP to the party (equal split or custom), with level-up prompts at
  the 5.5e thresholds; a per-campaign **milestone mode** disables XP tracking and the DM levels
  members manually.
- **Spells**: sheets track spells known/prepared as references into the seeded spell library;
  the sheet renders a per-PC spell list with slot tracking.
- Sheets are DM-only data (never in the player-safe projection); player-editable sheets are
  post-v1 roadmap (§8).

### 4.13 Quicknotes (entity-attached jotting)

The margin scribbles of a paper DM screen — separate from, and lighter than, wiki notes (§4.7):

- A `QuickNote` is a timestamped line of plain text attached to an entity: an encounter, map,
  statblock, party member, handout, note, or the campaign itself ("goblin boss fled north",
  "party owes the innkeeper 5 gp").
- **Every entity screen shows its quicknotes strip** with a one-line input — jot without leaving
  context. The battle map side panel shows the current map's and active encounter's quicknotes,
  so mid-combat jotting never means navigation.
- Quicknotes appear in campaign full-text search and the `Ctrl+K` palette.
- **Promote to note**: one click converts a quicknote into a full wiki note, pre-linked to its
  target entity, for when a scribble turns out to matter.
- Always DM-only; never in the player-safe projection.

### 4.14 Loot, Treasury & Attunement

The loot spreadsheet, killed. Holdings are state; transactions are history:

- **Item assignments** are the current state: a magic item, equipment item (both compendium
  references), or free-text custom item is held by a PC or the **party stash**. Shown on the
  holder's character sheet and in a party-wide loot overview ("who took the +1 dagger?").
- **The ledger** is the append-only history: gold and items gained or spent, by whom, with a note
  and an optional in-game date (§4.15). Running gold balances per PC and for the party stash are
  derived from it.
- **Attunement tracking**: assigned magic items can be flagged as attuned; the app warns at the
  3-per-PC limit (warned, not enforced — table rulings win).
- Presenting an item's description to the table works through the existing handout/present
  mechanics where an image exists; the compendium text itself stays DM-side.

### 4.15 In-Game Calendar & Timeline

Where "the eclipse is in 12 days" stops living in the DM's head:

- **Campaign calendar config** (month names/lengths, weekday names) lives in campaign settings —
  a sensible default preset, fully customizable for homebrew worlds — plus a **current in-game
  date** and an "advance day(s)" action (downtime is just advancing days).
- **Timeline events**: dated entries (title, optional body, optional wiki-note link) form the
  campaign timeline, shown chronologically with "in N days" relative markers.
- Session logs and ledger entries can carry in-game dates, tying the records to world time.
- DM-only in v1.

### 4.16 Dice Roller (optional-first)

Digital rolling as a convenience, never a requirement — physical dice are first-class (§2.3.9):

- **Free-form roller**: dice expressions (`2d6+3`, `d20` with advantage/disadvantage), available
  from every screen, with a recent-rolls history.
- **Clickable rolls**: statblock entries (attack, damage, save DCs) and character sheet values
  (checks, saves, attacks) render as roll buttons with modifiers pre-applied.
- **Combat integration**: rolls made during an active encounter can log into the combat action
  log (§4.5); initiative auto-roll (already specced) becomes a special case of the same roller.
- Rolls execute **server-side** so the RNG and its log live in one place.
- DM-side only in v1; player-facing rolling belongs to post-v1 player interaction (§8).

---

## 5. API Conventions

- **Two styles, one backend**: CRUD screens are driven by htmx — controllers return Thymeleaf
  HTML fragments from view routes. The canvas islands, import/export, and anything scriptable use
  the JSON API under `/api/v1`. Entity IDs are UUIDs everywhere.
- Resource-oriented: `/api/v1/campaigns/{id}/maps`, `/maps/{id}/tokens`, `/encounters/{id}/combatants`,
  `/library/statblocks?search=&cr=&type=`, `/library/spells?search=&level=&school=`,
  `/library/statblocks/srd-keys` (JSON array of all SRD source keys, auto-generated from seeded data),
  `/campaigns/{id}/notes?type=&tag=`,
  `/campaigns/{id}/party`, `/campaigns/{id}/handouts` (multipart image upload; files served from
  `/files/{id}`).
- Compendium (read-only): `/library/conditions`, `/library/rules`, `/library/items?category=`,
  `/library/magic-items?rarity=`, `/library/classes`, `/library/species`, `/library/backgrounds`,
  `/library/feats` — all with `?search=`.
- Character sheets: `/campaigns/{id}/party/{memberId}/sheet` (1–1 subresource), with action
  endpoints `POST …/sheet/level-up`, `POST /campaigns/{id}/party/rest?type=SHORT|LONG`,
  `POST /campaigns/{id}/party/xp` (award & split).
- Bookkeeping: `/campaigns/{id}/quicknotes?target=`, `/campaigns/{id}/assignments`,
  `/campaigns/{id}/ledger`, `/campaigns/{id}/calendar` (config + current date),
  `/campaigns/{id}/timeline`.
- Dice: `POST /api/v1/roll` (dice expression → structured result; logs into the combat log when
  an encounter is active).
- Table presentation (what player views show) is set via `PUT /api/v1/table/presentation`
  with a body of `{ "mode": "MAP" | "HANDOUT" | "CURTAIN", "ref": "<id>" }`.
- Map documents saved via `PUT /maps/{id}/document` (whole-document replace with optimistic
  version check); token moves via small `PATCH` calls with pixel positions (`x`, `y`) so live play
  is snappy — grid snapping is frontend-only, the API is mode-agnostic.
- Map mode & grid visibility toggled via `PATCH /maps/{id}` accepting `movementMode` and `showGrid`.
- Import/export: `GET /campaigns/{id}/export` (streams the JSON file),
  `POST /campaigns/import` (multipart upload; `?dryRun=true` for validate-only).
- Generative tooling: `GET /api/v1/schemas/{name}` (JSON Schemas for the campaign format and map
  document), `GET /api/v1/library/srd-keys` (valid SRD reference keys, auto-generated from
  seeded statblock data).
- Errors follow **RFC 9457** problem+json (Problem Details for HTTP APIs — the current spec,
  obsoleting RFC 7807) with actionable messages (especially import validation).
- **Live sync (player view):** plain WebSocket at `/ws/table` (browser-native API, no client
  library, no STOMP). The server sends typed JSON messages: a full `TABLE_STATE` snapshot on
  connect/reconnect and on presentation/curtain changes, and incremental events (`TOKEN_MOVED`,
  `TURN_CHANGED`, `CONDITIONS_CHANGED`, …) during play. Every payload passes through the same
  server-side player-safe projection (§2.3.3); the socket is broadcast-only — client messages are
  ignored — and no DM-privileged channel exists in v1 because the DM screen uses REST.

---

## 6. Quality & Non-Functional Requirements

- **Polish comes from a hand-written design system**, not a component library: one CSS file of
  design tokens (CSS custom properties for color, spacing, type scale), a dark-first theme that
  looks right in a dim game room, and a small set of reusable Thymeleaf fragments (cards, dialogs,
  statblock layout) used everywhere. Modern CSS (grid, container queries, `dialog`, transitions)
  covers what UI frameworks used to be needed for.
- **Robustness at the table is the top priority**: autosave everything (no explicit save button
  outside the editor's debounced save); the app must survive a browser refresh mid-combat with zero
  data loss (encounter state is persisted server-side on every change).
- **Performance targets**: map editor smooth at 60fps on a 50×50 grid with ~200 shapes; token drag
  latency imperceptible; statblock search results < 100 ms.
- **Backups**: on every app start, copy the H2 database file and the handout files directory to a
  rotating backup folder (`~/.dmhelper/backups`, keep last 10).
- **Testing**: service-layer unit tests; import/export round-trip tests (export → import → deep
  equality, covering sheets, quicknotes, assignments, ledger, and timeline as they land) as the
  flagship integration test; **table-driven derivation tests for the sheet engine** (known
  builds → expected modifiers, save DCs, slots — including multiclass casters — with all expected
  values taken from compendium-seeded data per §2.3.8, never hand-computed from memory);
  **exhaustive tests for the player-safe projection**
  (no `dmOnly`/`hidden` field may ever reach a player topic) plus **access-control tests that
  every DM route rejects requests without the session PIN** — the two halves of the one
  security-like invariant in the app (§2.3.3, §2.3.7); frontend component tests for the initiative tracker and DM Mode
  filtering; a Playwright (**Java binding**, from Maven — keeping the no-npm rule) smoke test for
  the core session loop (create map → place token → start encounter → advance turns → verify on a
  second player-view page).
- **Licensing**: SRD 5.2 under CC-BY-4.0 with required attribution; no non-SRD WotC content is
  ever bundled.

---

## 7. Milestones

| # | Milestone | Contents | Definition of done |
|---|---|---|---|
| M1 ✅ | **Walking skeleton** | Spring Boot 4.1.0 + Thymeleaf/htmx + H2 in one JAR; `ddl-auto=update` + backup system wired in; vendored assets with `VENDOR.md`; base layout & design system; campaign CRUD; campaign JSON export/import (empty campaigns) | `java -jar` → create, export, import a campaign in the browser |
| M2 ✅ | **Statblock library & party roster** | Statblock schema + renderer (incl. xp); 331 SRD 5.2 monsters + 339 spells from open5e; seed on first run; search/filter; homebrew editor; party roster CRUD + summary bar; SRD key catalog endpoint | Find "Goblin" in <100 ms; create a custom monster; enter the party once; look up "Fireball" |
| M3 ✅ | **Reference compendium** | Conditions, rules sections, equipment, magic items, classes, species, backgrounds, feats seeded from open5e `srd-2024` with verbatim-SRD-5.2 fallback seed files (§2.3.8 provenance); library UI sections + per-type renderers; search; SRD key catalog extended | Look up "Grappled", "Bag of Holding", and the Fighter level table without a PDF |
| M4 ✅ | **Map editor** | Grid canvas, terrain painting, shapes, layers, undo/redo, autosave | Build a usable tavern map from scratch |
| M5 ✅ | **Battle map** | Play mode, tokens (create/move/hide/HP, add-party, bloodied state), per-map movement mode toggle (grid ↔ freeform), independent grid-visibility toggle, map switching with state, AoE templates, measurement | Run a mock fight by hand on a map, toggling between grid and freeform movement |
| M6 ✅ | **Combat tracker** | Encounters, initiative, monster groups, turns, HP math, conditions + effect durations (with compendium condition text), concentration, legendary/lair actions, combat log with undo, difficulty calculator, map + roster linkage | Run a full combat (incl. a boss) without touching paper |
| M7 ✅ | **Player view & handouts** | WebSocket broadcaster, server-side player-safe projection, DM-route PIN gate, `/player` route, send-to-table & curtain, QR join, auto-reconnect; handout upload/gallery/present | Phone + laptop show the fight live; a letter fills the TV |
| M8 ✅ | **Notes, wiki & quicknotes** | Typed notes, Markdown, wiki-links + backlinks, search, session plans, side panel; entity-attached quicknotes with promote-to-note | Replace the campaign spreadsheet; jot mid-fight without leaving the map |
| M9 ✅ | **Character sheets** | Rules-aware sheets on top of the compendium (multiclass, derived-with-override, guided level-up), rest actions, resource counters, XP awarding + milestone mode, roster fields derived from sheets | Recreate a real PC from its paper sheet, level it up, long-rest it — numbers all correct |
| M10 ✅ | **Loot, treasury & calendar** | Item assignments (PC/stash), gold + item ledger, attunement warnings, calendar config + current date + advance-days, timeline events, in-game dates on logs/ledger | Distribute a hoard to the party; "the eclipse is in 12 days" is a timeline entry |
| M11 ✅ | **Dice roller** | Expression roller + history, clickable statblock/sheet rolls, combat-log integration, server-side RNG — optional-first everywhere (§2.3.9) | Run a fight rolling digitally *and* typing physical rolls interchangeably |
| M12 ✅ | **Table polish & generative tooling** | DM Mode toggle everywhere, backups, error handling, keyboard shortcuts, `Ctrl+K` command palette (notes, compendium, quicknotes, everything), full export/import of everything; JSON Schemas, dry-run import, SRD key catalog | Run a real session start-to-finish; an AI-generated campaign imports cleanly |

Each milestone ends in a usable state — the app is session-worthy from M5 onward, with or without
player devices.

## 8. Post-v1 Roadmap (design for, don't build)

1. **Player interaction** — players move their own tokens, view/edit their own character sheets,
   and roll their own dice from their devices (adds per-device identity and permission rules on
   top of the read-only player view).
2. **Fog of war** — manual reveal first, vision-based later; renders on the player view.
3. **Image map layers** — upload battle map images under the editor's shape layers (slot reserved
   in the map document schema).
4. **Statblock spell-list linking, loot tables & random generators** — spellcasting monsters link
   their spells to library entries; random treasure/name/encounter tables (subject to the same
   provenance rule §2.3.8 for any rules content).
5. **Campaign templates** — export subsets (a dungeon + its monsters) as reusable modules.

---

## 9. Resolved Design Decisions

- **Hex grid support** — Square-only for v1. Hex grids are a post-v1 feature. The `GameMap` entity and map document schema should reserve a future `gridType` field.
- **Combat log → session log** — When an encounter ends, a **summary line** (rounds, casualties, damage totals) is appended to the session log. The full blow-by-blow remains in the per-encounter combat log for inspection.
- **Multi-campaign shared homebrew** — **Promote-to-global** per statblock is sufficient for v1. A proper homebrew compendium module can be added later if needed.
- **Character sheets promoted into v1** (2026-07, after M2) — originally a non-goal; reversed to fulfill the "DM needs nothing else" premise. Full **rules-aware** sheets (§4.12), DM-operated, derived-with-override. `PartyMember` deliberately remains the combat projection so the map/combat modules (M4–M6) never depend on the sheet engine, and sheet-less members keep working.
- **Rules data provenance** (§2.3.8) — no D&D rules content is ever reconstructed from an AI assistant's memory; open5e `srd-2024` first, verbatim SRD 5.2 seed files as fallback, graceful degradation otherwise. Binds all future implementation sessions.
- **Dice roller is optional-first** (§2.3.9) — promoted into v1 (M11), but every roll input must accept a typed value so physical dice remain first-class forever; no feature may require the digital roller.
- **New-feature milestone placement** (2026-07) — compendium lands directly after M2 (reuses the just-built open5e seeding machinery); map → battle → tracker → player view stay next so the app is table-ready early (M5); sheets (M9) follow the notes module and build on compendium data; bookkeeping (M10) and dice (M11) before final polish (M12).
- **Freeform movement toggle** (2026-07, before M5) — the battle map supports both grid-snapped and freeform pixel movement, toggled per-map via `GameMap.movementMode`. Token positions are stored as pixel coordinates in both modes; grid snapping is applied only on the frontend during drag. Grid visibility is an independent toggle (`showGrid`). Token sizes remain in cell increments (1×1..4×4) in both modes. The map editor stays grid-based always. AoE templates and measurement follow the active movement mode.

---

## 10. Delivery Item Tracking

| Item | Description | Status | Artifacts |
|------|-------------|--------|-----------|
| 1 | Campaign package v2 container format | `IMPLEMENTED` | CampaignManifestV2, ZIP/JSON containers, preview/import/export pipeline |
| 2 | Typed catalog & content references | `IMPLEMENTED` | CampaignContentType, ContentReference, catalog endpoints |
| 3 | Session cockpit | `IMPLEMENTED` | CampaignSession, session state DTO, determinic import recovery |
| 4 | Session log & scene visits | `IMPLEMENTED` | SessionSceneVisit, scene visit DTOs, draft body |
| 5 | Combat log & dice history opt-out | `IMPLEMENTED` | CombatLogEntry, DiceRoll, exclusions metadata |
| 6 | Structured scenes, quests, annotations | `IMPLEMENTED` | Flyway V5 migration; fixture `structured-adventure-quest.dmcampaign`; `CampaignCompleteRoundTripTest`, `CampaignPackageValidationPipelineTest`, `SessionDraftServiceTest`, `CoreSessionLoopSmokeTest`; AdventureSectionAdapter, QuestSectionAdapter, SourceAnnotationSectionAdapter, SessionSectionAdapter |
| 7 | Custom compendium expansion (non-statblock) | `IMPLEMENTED` | Custom spells, conditions, rules, equipment, magic items, classes, species, backgrounds, feats; `LibrarySectionAdapter`; provenance DTOs; `NON_CAMPAIGN_CUSTOM_DEPENDENCY` error; `CampaignManifestV2` custom arrays |
